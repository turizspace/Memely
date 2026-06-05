package com.memely.workflow

import com.memely.blossom.BlossomClient
import com.memely.nostr.NostrNotePublisher
import com.memely.nostr.PostingManager
import com.memely.nostr.NostrRepository
import com.memely.util.SecureLog
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject
import java.io.File

/**
 * MemelyWorkflowManager - Orchestrates Blossom upload → Nostr posting workflow
 * 
 * Optimized for:
 * - Reliable uploads to Blossom with retries
 * - Seamless posting to Nostr after upload
 * - Handling poor connections and timeouts
 * - Low-bandwidth operation
 */

data class WorkflowStep(
    val name: String,
    val status: StepStatus,
    val message: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

enum class StepStatus {
    PENDING,
    IN_PROGRESS,
    COMPLETED,
    FAILED,
    RETRYING
}

data class MemePublishResult(
    val success: Boolean,
    val memeUrl: String? = null,
    val noteId: String? = null,
    val blossomUrl: String? = null,
    val uploadStatus: String = "",
    val postingStatus: String = "",
    val totalTimeMs: Long = 0,
    val steps: List<WorkflowStep> = emptyList(),
    val errorMessage: String? = null,
    // Retry support
    val canRetry: Boolean = false,
    val retryMemeCaption: String? = null,
    val retryMemeUrl: String? = null,
    val relayStatus: String? = null  // Current relay connection status
)

object MemelyWorkflowManager {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    private val _workflowSteps = MutableStateFlow<List<WorkflowStep>>(emptyList())
    val workflowSteps: StateFlow<List<WorkflowStep>> = _workflowSteps
    
    // Configuration for upload retries (optimized for poor connections)
    private const val UPLOAD_MAX_RETRIES = 5
    private const val UPLOAD_TIMEOUT_MS = 120000L  // 2 minutes for large images
    private const val POSTING_TIMEOUT_MS = 30000L  // 30 seconds for posting

    /**
     * Publish a meme to Blossom and then to Nostr
     * Complete workflow with automatic retries
     */
    suspend fun publishMeme(
        memeFile: File,
        memeCaption: String,
        pubkeyHex: String,
        privKeyBytes: ByteArray,
        blossomClient: BlossomClient,
        onProgress: ((step: String, progress: Int) -> Unit)? = null,
        isRetry: Boolean = false
    ): MemePublishResult {
        val startTime = System.currentTimeMillis()
        val steps = mutableListOf<WorkflowStep>()

        return try {
            // Step 1: Check relay connection status
            if (!isRetry) {
                addStep(steps, "Checking relay connection", StepStatus.IN_PROGRESS)
                onProgress?.invoke("Checking relay connection...", 5)
                
                val relayStatus = RelayConnectionValidator.getCurrentStatus()
                
                if (!relayStatus.isConnected) {
                    SecureLog.w("MemelyWorkflowManager: No relays connected, attempting to wait...")
                    addStep(steps, "Waiting for relay connection", StepStatus.IN_PROGRESS)
                    onProgress?.invoke(relayStatus.message, 5)
                    
                    // Wait up to 10 seconds for relay connection
                    val connected = RelayConnectionValidator.waitForRelayConnection(
                        timeoutMs = 10000L,
                        minRelays = 1
                    )
                    
                    if (!connected) {
                        val currentStatus = RelayConnectionValidator.getCurrentStatus()
                        addStep(
                            steps, 
                            "Unable to connect to relays - ${currentStatus.message}", 
                            StepStatus.FAILED
                        )
                        
                        return MemePublishResult(
                            success = false,
                            uploadStatus = "Pending",
                            postingStatus = "No relay connection",
                            errorMessage = "Unable to establish relay connection. ${currentStatus.message}",
                            canRetry = true,
                            retryMemeCaption = memeCaption,
                            relayStatus = currentStatus.message,
                            steps = steps,
                            totalTimeMs = System.currentTimeMillis() - startTime
                        )
                    }
                }
                addStep(steps, "Relay connection established", StepStatus.COMPLETED)
            }
            
            // Step 2: Validate inputs
            addStep(steps, "Validating inputs", StepStatus.IN_PROGRESS)
            if (!memeFile.exists()) {
                throw IllegalArgumentException("Meme file does not exist")
            }
            if (pubkeyHex.isBlank()) {
                throw IllegalArgumentException("Invalid pubkey")
            }
            addStep(steps, "Inputs validated", StepStatus.COMPLETED)

            // Step 3: Upload to Blossom
            addStep(steps, "Uploading to Blossom", StepStatus.IN_PROGRESS)
            onProgress?.invoke("Uploading to Blossom...", 10)
            
            val uploadResult = uploadToBlossom(
                memeFile,
                pubkeyHex,
                privKeyBytes,
                blossomClient,
                onProgress
            )

            if (!uploadResult.success || uploadResult.memeUrl == null) {
                addStep(steps, "Upload failed: ${uploadResult.error}", StepStatus.FAILED)
                return MemePublishResult(
                    success = false,
                    uploadStatus = "Upload failed",
                    errorMessage = uploadResult.error,
                    canRetry = false,
                    relayStatus = RelayConnectionValidator.getStatusMessage(),
                    steps = steps,
                    totalTimeMs = System.currentTimeMillis() - startTime
                )
            }

            addStep(steps, "Upload successful: ${uploadResult.memeUrl}", StepStatus.COMPLETED)
            onProgress?.invoke("Upload complete, posting to Nostr...", 60)

            // Step 4: Post to Nostr
            addStep(steps, "Posting to Nostr", StepStatus.IN_PROGRESS)
            
            val postResult = postToNostr(
                memeCaption,
                uploadResult.memeUrl,
                pubkeyHex,
                privKeyBytes,
                onProgress
            )

            if (!postResult.success || postResult.noteId == null) {
                addStep(steps, "Post failed: ${postResult.error}", StepStatus.FAILED)
                
                // Upload succeeded but posting failed - can retry with stored URL
                val currentRelayStatus = RelayConnectionValidator.getCurrentStatus()
                return MemePublishResult(
                    success = false,
                    memeUrl = uploadResult.memeUrl,
                    uploadStatus = "Successful",
                    postingStatus = "Queued for retry",
                    errorMessage = postResult.error,
                    canRetry = true,  // Allow retry for posting
                    retryMemeCaption = memeCaption,
                    retryMemeUrl = uploadResult.memeUrl,
                    relayStatus = currentRelayStatus.message,
                    steps = steps,
                    totalTimeMs = System.currentTimeMillis() - startTime
                )
            }

            addStep(steps, "Post successful: ${postResult.noteId}", StepStatus.COMPLETED)
            onProgress?.invoke("Meme published successfully!", 100)

            MemePublishResult(
                success = true,
                memeUrl = uploadResult.memeUrl,
                noteId = postResult.noteId,
                blossomUrl = uploadResult.blossomUrl,
                uploadStatus = "Successful",
                postingStatus = "Successful",
                relayStatus = RelayConnectionValidator.getStatusMessage(),
                steps = steps,
                totalTimeMs = System.currentTimeMillis() - startTime
            )

        } catch (e: Exception) {
            SecureLog.e("MemelyWorkflowManager: Workflow failed: ${e.message}")
            addStep(steps, "Workflow error: ${e.message}", StepStatus.FAILED)

            MemePublishResult(
                success = false,
                uploadStatus = "Error",
                postingStatus = "Error",
                errorMessage = e.message ?: "Unknown error",
                steps = steps,
                totalTimeMs = System.currentTimeMillis() - startTime
            )
        }
    }

    /**
     * Upload meme to Blossom with retry logic
     */
    private suspend fun uploadToBlossom(
        file: File,
        pubkeyHex: String,
        privKeyBytes: ByteArray,
        blossomClient: BlossomClient,
        onProgress: ((step: String, progress: Int) -> Unit)?
    ): UploadResult {
        var attempt = 0
        var lastError: String? = null

        while (attempt < UPLOAD_MAX_RETRIES) {
            try {
                if (attempt > 0) {
                    val delayMs = (500 * Math.pow(1.5, (attempt - 1).toDouble())).toLong()
                    SecureLog.d("MemelyWorkflowManager: Upload retry attempt $attempt after ${delayMs}ms")
                    onProgress?.invoke("Retrying upload (attempt ${attempt + 1}/$UPLOAD_MAX_RETRIES)...", 10 + (attempt * 5))
                    delay(delayMs)
                }

                SecureLog.d("MemelyWorkflowManager: Uploading meme (${file.length()} bytes)")
                
                val uploadResult = withTimeout(UPLOAD_TIMEOUT_MS) {
                    blossomClient.uploadFile(
                        file = file,
                        contentType = "image/jpeg",
                        pubkeyHex = pubkeyHex,
                        signEventFunc = { eventJson ->
                            // Sign the event using Nostr signer
                            signBlossomAuthEvent(eventJson, privKeyBytes)
                        },
                        endpoint = "upload",
                        onProgress = { sent, total ->
                            val percentage = (sent * 100 / total).toInt()
                            onProgress?.invoke("Uploading: $percentage%", 10 + percentage / 5)
                        }
                    )
                }

                if (uploadResult.ok) {
                    // Parse the response to get the URL
                    val url = try {
                        JSONObject(uploadResult.body ?: "{}").getString("url")
                    } catch (e: Exception) {
                        uploadResult.body
                    }

                    SecureLog.d("MemelyWorkflowManager: Upload successful: $url")
                    return UploadResult(
                        success = true,
                        memeUrl = url,
                        blossomUrl = url
                    )
                } else {
                    lastError = "Upload failed with status ${uploadResult.statusCode}: ${uploadResult.body}"
                    SecureLog.w("MemelyWorkflowManager: $lastError")
                }

            } catch (e: TimeoutCancellationException) {
                lastError = "Upload timeout (${UPLOAD_TIMEOUT_MS}ms)"
                SecureLog.w("MemelyWorkflowManager: $lastError")
            } catch (e: Exception) {
                lastError = "Upload error: ${e.message}"
                SecureLog.w("MemelyWorkflowManager: $lastError")
            }

            attempt++
        }

        return UploadResult(
            success = false,
            error = lastError ?: "Upload failed after $UPLOAD_MAX_RETRIES attempts"
        )
    }

    /**
     * Post meme to Nostr with retry via PostingManager
     */
    private suspend fun postToNostr(
        caption: String,
        imageUrl: String,
        pubkeyHex: String,
        privKeyBytes: ByteArray,
        onProgress: ((step: String, progress: Int) -> Unit)?
    ): PostResult {
        return try {
            withTimeout(POSTING_TIMEOUT_MS) {
                SecureLog.d("MemelyWorkflowManager: Posting to Nostr")
                
                val result = PostingManager.postWithRetry(
                    content = caption,
                    imageUrl = imageUrl,
                    pubkeyHex = pubkeyHex,
                    privKeyBytes = privKeyBytes
                )

                if (result != null && result.acceptanceRate > 0f) {
                    SecureLog.d("MemelyWorkflowManager: Post successful - acceptance rate: ${result.acceptanceRate}")
                    onProgress?.invoke("Posted successfully!", 100)
                    
                    PostResult(
                        success = true,
                        noteId = result.eventId,
                        acceptanceRate = result.acceptanceRate
                    )
                } else {
                    PostResult(
                        success = false,
                        error = "No relay acceptance"
                    )
                }
            }
        } catch (e: TimeoutCancellationException) {
            SecureLog.w("MemelyWorkflowManager: Post timeout")
            PostResult(
                success = false,
                error = "Posting timeout - message queued for retry"
            )
        } catch (e: Exception) {
            SecureLog.w("MemelyWorkflowManager: Post error: ${e.message}")
            PostResult(
                success = false,
                error = e.message ?: "Unknown error"
            )
        }
    }

    /**
     * Retry posting a meme that has already been uploaded to Blossom
     * Skips the upload step and only retries posting to Nostr
     */
    suspend fun retryPosting(
        memeCaption: String,
        memeUrl: String,
        pubkeyHex: String,
        privKeyBytes: ByteArray,
        onProgress: ((step: String, progress: Int) -> Unit)? = null
    ): MemePublishResult {
        val startTime = System.currentTimeMillis()
        val steps = mutableListOf<WorkflowStep>()

        return try {
            // Check relay connection before retrying
            addStep(steps, "Checking relay connection", StepStatus.IN_PROGRESS)
            onProgress?.invoke("Checking relay connection...", 10)
            
            val relayStatus = RelayConnectionValidator.getCurrentStatus()
            
            if (!relayStatus.isConnected) {
                SecureLog.w("MemelyWorkflowManager: Retry - No relays connected")
                addStep(steps, "No relay connection available", StepStatus.FAILED)
                
                return MemePublishResult(
                    success = false,
                    memeUrl = memeUrl,
                    uploadStatus = "Successful",
                    postingStatus = "Retry failed - no relay connection",
                    errorMessage = "Unable to connect to relays. ${relayStatus.message}",
                    canRetry = true,
                    retryMemeCaption = memeCaption,
                    retryMemeUrl = memeUrl,
                    relayStatus = relayStatus.message,
                    steps = steps,
                    totalTimeMs = System.currentTimeMillis() - startTime
                )
            }
            
            addStep(steps, "Relay connection verified", StepStatus.COMPLETED)
            addStep(steps, "Retrying post to Nostr", StepStatus.IN_PROGRESS)
            onProgress?.invoke("Posting to Nostr (retry)...", 60)
            
            val postResult = postToNostr(
                memeCaption,
                memeUrl,
                pubkeyHex,
                privKeyBytes,
                onProgress
            )

            if (!postResult.success || postResult.noteId == null) {
                addStep(steps, "Post retry failed: ${postResult.error}", StepStatus.FAILED)
                
                return MemePublishResult(
                    success = false,
                    memeUrl = memeUrl,
                    uploadStatus = "Successful",
                    postingStatus = "Retry failed",
                    errorMessage = postResult.error,
                    canRetry = true,
                    retryMemeCaption = memeCaption,
                    retryMemeUrl = memeUrl,
                    relayStatus = RelayConnectionValidator.getStatusMessage(),
                    steps = steps,
                    totalTimeMs = System.currentTimeMillis() - startTime
                )
            }

            addStep(steps, "Post successful: ${postResult.noteId}", StepStatus.COMPLETED)
            onProgress?.invoke("Meme published successfully!", 100)

            MemePublishResult(
                success = true,
                memeUrl = memeUrl,
                noteId = postResult.noteId,
                uploadStatus = "Successful",
                postingStatus = "Successful (Retry)",
                relayStatus = RelayConnectionValidator.getStatusMessage(),
                steps = steps,
                totalTimeMs = System.currentTimeMillis() - startTime
            )

        } catch (e: Exception) {
            SecureLog.e("MemelyWorkflowManager: Retry workflow failed: ${e.message}")
            addStep(steps, "Retry workflow error: ${e.message}", StepStatus.FAILED)

            MemePublishResult(
                success = false,
                memeUrl = memeUrl,
                uploadStatus = "Successful",
                postingStatus = "Error",
                errorMessage = e.message ?: "Unknown error",
                canRetry = true,
                retryMemeCaption = memeCaption,
                retryMemeUrl = memeUrl,
                relayStatus = RelayConnectionValidator.getStatusMessage(),
                steps = steps,
                totalTimeMs = System.currentTimeMillis() - startTime
            )
        }
    }

    /**
     * Sign Blossom authorization event
     */
    private suspend fun signBlossomAuthEvent(eventJson: String, privKeyBytes: ByteArray): String {
        return try {
            // This is a simplified version - in production, use proper NIP-98 signing
            eventJson
        } catch (e: Exception) {
            SecureLog.e("MemelyWorkflowManager: Error signing Blossom auth: ${e.message}")
            eventJson
        }
    }

    /**
     * Add workflow step for tracking
     */
    private fun addStep(steps: MutableList<WorkflowStep>, name: String, status: StepStatus) {
        val step = WorkflowStep(name = name, status = status)
        steps.add(step)
        _workflowSteps.value = steps.toList()
        SecureLog.d("MemelyWorkflowManager: $name - $status")
    }

    /**
     * Data class for upload results
     */
    private data class UploadResult(
        val success: Boolean,
        val memeUrl: String? = null,
        val blossomUrl: String? = null,
        val error: String? = null
    )

    /**
     * Data class for posting results
     */
    private data class PostResult(
        val success: Boolean,
        val noteId: String? = null,
        val acceptanceRate: Float = 0f,
        val error: String? = null
    )

    /**
     * Get pending messages in queue
     */
    fun getPendingMessages(): Int {
        return PostingManager.getQueueSize()
    }

    /**
     * Retry posting queued messages when connection improves
     */
    fun retryQueuedMessages() {
        scope.launch {
            SecureLog.d("MemelyWorkflowManager: Attempting to post queued messages")
            // PostingManager automatically processes queue when relays connect
        }
    }
}
