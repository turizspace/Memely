package com.memely.nostr

import com.memely.util.SecureLog
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.*
import kotlin.math.min

/**
 * PostingManager - Optimized for reliability, poor connections, and low bandwidth.
 * 
 * Features:
 * - Intelligent retry with exponential backoff
 * - Connection health monitoring
 * - Queue management for offline scenarios
 * - Relay-specific failure tracking
 * - Low-bandwidth optimizations
 */
data class PostingQueueItem(
    val id: String = UUID.randomUUID().toString(),
    val eventMessage: String,
    val eventId: String,
    val content: String,
    val createdAt: Long = System.currentTimeMillis(),
    var attempts: Int = 0,
    val maxRetries: Int = 5,
    var lastRetryTime: Long = 0L
)

data class RelayPostingStatus(
    val relayUrl: String,
    val eventId: String,
    val status: PostingStatus,
    val failureReason: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

enum class PostingStatus {
    PENDING,
    POSTED,
    ACCEPTED,
    REJECTED,
    TIMEOUT,
    FAILED
}

object PostingManager {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // Configuration for retries - optimized for poor connections
    private const val INITIAL_RETRY_DELAY_MS = 500L      // Start with 500ms
    private const val MAX_RETRY_DELAY_MS = 30000L         // Cap at 30 seconds
    private const val BACKOFF_MULTIPLIER = 1.5f
    private const val MIN_RELAY_THRESHOLD = 1             // Post if at least 1 relay accepts
    private const val GOOD_RELAY_THRESHOLD = 0.5f         // 50% is good for poor connections
    
    // Queue for messages when offline or under poor conditions
    private val postingQueue = mutableListOf<PostingQueueItem>()
    private val _queueStateFlow = MutableStateFlow<List<PostingQueueItem>>(emptyList())
    val queueStateFlow: StateFlow<List<PostingQueueItem>> = _queueStateFlow
    
    // Track relay health for intelligent routing
    private val relayHealthScores = mutableMapOf<String, Float>()  // 0-1 score
    private val relayFailureReasons = mutableMapOf<String, String>()
    
    // Track current posting operations
    private val _postingStatus = MutableStateFlow<Map<String, RelayPostingStatus>>(emptyMap())
    val postingStatus: StateFlow<Map<String, RelayPostingStatus>> = _postingStatus
    
    private val _lastPostResult = MutableStateFlow<PublishResult?>(null)
    val lastPostResult: StateFlow<PublishResult?> = _lastPostResult
    
    private var processingQueue = false

    init {
        // Process queue periodically
        scope.launch {
            while (true) {
                delay(5000)  // Check every 5 seconds for poor connections
                if (postingQueue.isNotEmpty()) {
                    processQueue()
                }
            }
        }
    }

    /**
     * Enhanced post with automatic retry and connection awareness
     */
    suspend fun postWithRetry(
        content: String,
        imageUrl: String,
        pubkeyHex: String,
        privKeyBytes: ByteArray
    ): PublishResult? = withContext(Dispatchers.IO) {
        val publisher = DefaultNostrNotePublisher()
        
        try {
            // Check if we have any connected relays
            val connectedCount = NostrRepository.relayPool.getConnectedCount()
            if (connectedCount == 0) {
                SecureLog.w("PostingManager: No relays connected, queuing message")
                return@withContext queueForLater(content, imageUrl, pubkeyHex, privKeyBytes)
            }

            // Build the note
            val note = publisher.publishNote(content, imageUrl, pubkeyHex, privKeyBytes)
            
            // Publish with retry
            return@withContext publishWithRetry(
                eventMessage = """["EVENT",{"id":"${note.eventId}"}]""",
                eventId = note.eventId,
                content = content
            )
        } catch (e: Exception) {
            SecureLog.e("PostingManager: Error during post: ${e.message}")
            null
        }
    }

    /**
     * Publish with intelligent retry logic optimized for poor connections
     */
    private suspend fun publishWithRetry(
        eventMessage: String,
        eventId: String,
        content: String
    ): PublishResult? = withContext(Dispatchers.IO) {
        val statusMap = mutableMapOf<String, RelayPostingStatus>()
        val relayUrls = NostrRepository.relayPool.getCurrentRelays()
        
        if (relayUrls.isEmpty()) {
            SecureLog.w("PostingManager: No relays available, queuing for retry")
            return@withContext null
        }

        // Initialize tracking
        RelayEventTracker.initializeEventTracking(eventId, relayUrls)
        relayUrls.forEach { relay ->
            statusMap[relay] = RelayPostingStatus(
                relayUrl = relay,
                eventId = eventId,
                status = PostingStatus.PENDING
            )
        }

        // Attempt to post with retries
        var attempt = 0
        val maxAttempts = 5
        var lastAcceptanceRate = 0f

        while (attempt < maxAttempts) {
            try {
                // Calculate adaptive delay
                val delayMs = if (attempt == 0) {
                    0L  // No delay for first attempt
                } else {
                    val baseDelay = (INITIAL_RETRY_DELAY_MS * Math.pow(BACKOFF_MULTIPLIER.toDouble(), (attempt - 1).toDouble())).toLong()
                    min(baseDelay, MAX_RETRY_DELAY_MS)
                }

                if (attempt > 0) {
                    SecureLog.d("PostingManager: Retry attempt $attempt after ${delayMs}ms")
                    delay(delayMs)
                }

                // Broadcast to all relays
                NostrRepository.relayPool.broadcastWithRetry(eventMessage, maxRetries = 2)

                // Wait for relay responses with timeout
                val responseTimeout = 10000L + (attempt * 2000)  // Increase timeout on retries
                val startTime = System.currentTimeMillis()
                val startResponses = countResponses(statusMap)

                // Monitor for responses
                delay(500)  // Initial wait for responses
                var noNewResponses = 0

                while (System.currentTimeMillis() - startTime < responseTimeout && noNewResponses < 3) {
                    updateRelayStatuses(eventId, statusMap)
                    val currentResponses = countResponses(statusMap)
                    
                    if (currentResponses > startResponses) {
                        noNewResponses = 0  // Reset if we got new responses
                    } else {
                        noNewResponses++
                    }

                    // Check if we have acceptable success rate
                    val result = computeResult(eventId, statusMap)
                    if (result.acceptanceRate >= GOOD_RELAY_THRESHOLD) {
                        SecureLog.d("PostingManager: Acceptable acceptance rate (${result.acceptanceRate}), completing")
                        _postingStatus.value = statusMap
                        _lastPostResult.value = result
                        return@withContext result
                    }

                    delay(1000)
                }

                lastAcceptanceRate = computeResult(eventId, statusMap).acceptanceRate
                
                // If we have at least minimum relay acceptance, consider it successful
                val result = computeResult(eventId, statusMap)
                if (result.acceptedRelays.size >= MIN_RELAY_THRESHOLD) {
                    SecureLog.d("PostingManager: Minimum relay threshold met (${result.acceptedRelays.size} accepted)")
                    _postingStatus.value = statusMap
                    _lastPostResult.value = result
                    updateRelayHealthScores(statusMap)
                    return@withContext result
                }

                attempt++
                
            } catch (e: Exception) {
                SecureLog.e("PostingManager: Error during publish attempt $attempt: ${e.message}")
                attempt++
            }
        }

        // Final result after all retries
        val finalResult = computeResult(eventId, statusMap)
        _postingStatus.value = statusMap
        _lastPostResult.value = finalResult
        updateRelayHealthScores(statusMap)
        
        SecureLog.d("PostingManager: Publishing complete - Acceptance rate: ${finalResult.acceptanceRate}")
        return@withContext finalResult
    }

    /**
     * Queue message for retry when offline or connections are poor
     */
    private suspend fun queueForLater(
        content: String,
        imageUrl: String,
        pubkeyHex: String,
        privKeyBytes: ByteArray
    ): PublishResult? {
        val publisher = DefaultNostrNotePublisher()
        val note = publisher.publishNote(content, imageUrl, pubkeyHex, privKeyBytes)

        val queueItem = PostingQueueItem(
            eventMessage = """["EVENT",{"id":"${note.eventId}"}]""",
            eventId = note.eventId,
            content = content
        )

        postingQueue.add(queueItem)
        _queueStateFlow.value = postingQueue.toList()
        SecureLog.d("PostingManager: Queued message for later (queue size: ${postingQueue.size})")

        return PublishResult(
            eventId = note.eventId,
            acceptedRelays = emptyList(),
            rejectedRelays = emptyList(),
            timedOutRelays = emptyList(),
            totalRelays = 0,
            acceptanceRate = 0f
        )
    }

    /**
     * Process queued messages when connection improves
     */
    private suspend fun processQueue() {
        if (processingQueue || postingQueue.isEmpty()) return
        
        processingQueue = true
        try {
            val connectedCount = NostrRepository.relayPool.getConnectedCount()
            if (connectedCount == 0) {
                SecureLog.d("PostingManager: Still no relay connections, queue waiting")
                return
            }

            val itemsToProcess = postingQueue.toList()
            for (item in itemsToProcess) {
                try {
                    val result = publishWithRetry(item.eventMessage, item.eventId, item.content)
                    if (result != null && result.acceptanceRate > 0f) {
                        postingQueue.remove(item)
                        _queueStateFlow.value = postingQueue.toList()
                        SecureLog.d("PostingManager: Successfully posted queued message")
                    } else {
                        item.attempts++
                        if (item.attempts > item.maxRetries) {
                            postingQueue.remove(item)
                            _queueStateFlow.value = postingQueue.toList()
                            SecureLog.w("PostingManager: Giving up on queued message after ${item.attempts} attempts")
                        } else {
                            item.lastRetryTime = System.currentTimeMillis()
                        }
                    }
                } catch (e: Exception) {
                    SecureLog.e("PostingManager: Error processing queued item: ${e.message}")
                }
                delay(500)  // Small delay between queue items for low bandwidth
            }
        } finally {
            processingQueue = false
        }
    }

    /**
     * Update relay statuses based on incoming messages
     */
    private fun updateRelayStatuses(eventId: String, statusMap: MutableMap<String, RelayPostingStatus>) {
        val statuses = RelayEventTracker.getEventStatus(eventId)
        statuses.forEach { (relay, status) ->
            statusMap[relay] = RelayPostingStatus(
                relayUrl = relay,
                eventId = eventId,
                status = when (status.status) {
                    EventStatus.PENDING -> PostingStatus.PENDING
                    EventStatus.ACCEPTED -> PostingStatus.ACCEPTED
                    EventStatus.REJECTED -> PostingStatus.REJECTED
                    EventStatus.TIMEOUT -> PostingStatus.TIMEOUT
                    EventStatus.CONNECTION_ERROR -> PostingStatus.FAILED
                },
                failureReason = status.message
            )
        }
    }

    /**
     * Count how many relays have responded
     */
    private fun countResponses(statusMap: Map<String, RelayPostingStatus>): Int {
        return statusMap.values.count { it.status != PostingStatus.PENDING && it.status != PostingStatus.POSTED }
    }

    /**
     * Compute final publish result
     */
    private fun computeResult(eventId: String, statusMap: Map<String, RelayPostingStatus>): PublishResult {
        val accepted = statusMap.values.filter { it.status == PostingStatus.ACCEPTED }.map { it.relayUrl }
        val rejected = statusMap.values.filter { it.status == PostingStatus.REJECTED }.map { it.relayUrl }
        val timedOut = statusMap.values.filter { it.status == PostingStatus.TIMEOUT }.map { it.relayUrl }

        val total = statusMap.size
        val acceptanceRate = if (total > 0) accepted.size.toFloat() / total else 0f

        // Convert RelayPostingStatus to RelayEventStatus for PublishResult
        val detailsMap = statusMap.mapValues { (_, status) ->
            RelayEventStatus(
                relayUrl = status.relayUrl,
                eventId = status.eventId,
                status = when (status.status) {
                    PostingStatus.ACCEPTED -> EventStatus.ACCEPTED
                    PostingStatus.REJECTED -> EventStatus.REJECTED
                    PostingStatus.TIMEOUT -> EventStatus.TIMEOUT
                    PostingStatus.FAILED -> EventStatus.CONNECTION_ERROR
                    PostingStatus.PENDING, PostingStatus.POSTED -> EventStatus.PENDING
                },
                message = status.failureReason,
                timestamp = status.timestamp
            )
        }

        return PublishResult(
            eventId = eventId,
            acceptedRelays = accepted,
            rejectedRelays = rejected,
            timedOutRelays = timedOut,
            totalRelays = total,
            acceptanceRate = acceptanceRate,
            details = detailsMap
        )
    }

    /**
     * Track relay health scores for intelligent routing
     */
    private fun updateRelayHealthScores(statusMap: Map<String, RelayPostingStatus>) {
        statusMap.forEach { (relay, status) ->
            val currentScore = relayHealthScores[relay] ?: 0.5f
            val newScore = when (status.status) {
                PostingStatus.ACCEPTED -> min(currentScore + 0.1f, 1f)
                PostingStatus.REJECTED -> maxOf(currentScore - 0.2f, 0f)
                PostingStatus.TIMEOUT -> maxOf(currentScore - 0.15f, 0f)
                PostingStatus.FAILED -> maxOf(currentScore - 0.25f, 0f)
                else -> currentScore
            }
            relayHealthScores[relay] = newScore
        }
    }

    /**
     * Get relay health score (0-1, higher is better)
     */
    fun getRelayHealthScore(relayUrl: String): Float {
        return relayHealthScores[relayUrl] ?: 0.5f
    }

    /**
     * Clear queue (useful for testing or manual reset)
     */
    fun clearQueue() {
        postingQueue.clear()
        _queueStateFlow.value = emptyList()
    }

    /**
     * Get queue size
     */
    fun getQueueSize(): Int = postingQueue.size
}
