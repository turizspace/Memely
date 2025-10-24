package com.memely.ui.viewmodels

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.memely.nostr.NostrRepository
import com.memely.nostr.NostrEventSigner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/**
 * ViewModel for publishing Nostr events (Kind 1 notes with images).
 */
class NostrPostViewModel {
    sealed class PostState {
        object Idle : PostState()
        object Posting : PostState()
        data class Success(val eventId: String) : PostState()
        data class Error(val message: String) : PostState()
    }

    var postState by mutableStateOf<PostState>(PostState.Idle)
        private set

    /**
     * Set the posting state (for external use when using Amber).
     */
    fun setPostingState() {
        postState = PostState.Posting
    }

    /**
     * Set success state with event ID.
     */
    fun setSuccessState(eventId: String) {
        postState = PostState.Success(eventId)
    }

    /**
     * Set error state with message.
     */
    fun setErrorState(message: String) {
        postState = PostState.Error(message)
    }

    /**
     * Publish a Kind 1 note with image to Nostr relays.
     * 
     * @param content The note content/caption
     * @param imageUrl The URL of the uploaded image
     * @param pubkeyHex User's public key in hex format
     * @param privKeyBytes User's private key bytes (32 bytes)
     * @param coroutineScope Coroutine scope for async operations
     * @param onSuccess Callback with event ID
     */
    fun publishNote(
        content: String,
        imageUrl: String,
        pubkeyHex: String,
        privKeyBytes: ByteArray,
        coroutineScope: CoroutineScope,
        onSuccess: ((String) -> Unit)? = null
    ) {
        coroutineScope.launch(Dispatchers.IO) {
            try {
                postState = PostState.Posting

                // Build content with image URL
                val fullContent = if (content.isNotBlank()) {
                    "$content\n\n$imageUrl"
                } else {
                    imageUrl
                }

                // Build tags with image URL (NIP-94 style)
                val tags = mutableListOf<List<String>>()
                
                // Add image metadata tag
                tags.add(listOf("imeta", "url $imageUrl"))
                
                // Also add as regular URL tag for compatibility
                tags.add(listOf("url", imageUrl))
                
                // Add client tag
                tags.add(listOf("client", "Memely"))
                
                // Add hashtags
                tags.add(listOf("t", "meme"))
                tags.add(listOf("t", "memely"))

                // Sign the event
                val signedEventJson = NostrEventSigner.signEvent(
                    kind = 1,
                    content = fullContent,
                    tags = tags,
                    pubkeyHex = pubkeyHex,
                    privKeyBytes = privKeyBytes
                )

                // Parse to get event ID
                val eventObj = JSONObject(signedEventJson)
                val eventId = eventObj.getString("id")

                // Publish to relays
                val published = publishToRelays(signedEventJson)

                if (published) {
                    postState = PostState.Success(eventId)
                    onSuccess?.invoke(eventId)
                } else {
                    postState = PostState.Error("Failed to publish to relays")
                }
            } catch (e: Exception) {
                postState = PostState.Error("Error: ${e.message}")
            }
        }
    }

    /**
     * Publish a signed event to connected Nostr relays.
     */
    private suspend fun publishToRelays(signedEventJson: String): Boolean {
        return try {
            // Format as EVENT message
            val eventMessage = """["EVENT",$signedEventJson]"""
            
            println("📝 NostrPostViewModel: Signed event: $signedEventJson")
            println("📤 NostrPostViewModel: Publishing EVENT message")
            
            // Broadcast to all connected relays via NostrRepository
            NostrRepository.publishEvent(eventMessage)
            
            println("✅ NostrPostViewModel: Publish complete")
            true
        } catch (e: Exception) {
            println("❌ NostrPostViewModel: Failed to publish: ${e.message}")
            e.printStackTrace()
            false
        }
    }

    fun reset() {
        postState = PostState.Idle
    }
}
