package com.memely.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.memely.nostr.NostrNotePublisher
import com.memely.util.SecureLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for publishing Nostr events (Kind 1 notes with images).
 */
class NostrPostViewModel(
    private val notePublisher: NostrNotePublisher
) : ViewModel() {
    sealed class PostState {
        object Idle : PostState()
        object Posting : PostState()
        data class Success(val eventId: String) : PostState()
        data class Error(val message: String) : PostState()
    }

    private val _postState = MutableStateFlow<PostState>(PostState.Idle)
    val postState: StateFlow<PostState> = _postState.asStateFlow()

    /**
     * Set the posting state (for external use when using Amber).
     */
    fun setPostingState() {
        _postState.value = PostState.Posting
    }

    /**
     * Set success state with event ID.
     */
    fun setSuccessState(eventId: String) {
        _postState.value = PostState.Success(eventId)
    }

    /**
     * Set error state with message.
     */
    fun setErrorState(message: String) {
        _postState.value = PostState.Error(message)
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
        onSuccess: ((String) -> Unit)? = null
    ) {
        viewModelScope.launch {
            _postState.value = PostState.Posting

            runCatching {
                notePublisher.publishNote(
                    content = content,
                    imageUrl = imageUrl,
                    pubkeyHex = pubkeyHex,
                    privKeyBytes = privKeyBytes
                )
            }.onSuccess { publishedNote ->
                SecureLog.d("NostrPostViewModel: Published note ${SecureLog.truncateHex(publishedNote.eventId)}")
                _postState.value = PostState.Success(publishedNote.eventId)
                onSuccess?.invoke(publishedNote.eventId)
            }.onFailure { throwable ->
                SecureLog.e("NostrPostViewModel: Failed to publish note", throwable)
                _postState.value = PostState.Error("Error: ${throwable.message}")
            }
        }
    }

    fun reset() {
        _postState.value = PostState.Idle
    }
}
