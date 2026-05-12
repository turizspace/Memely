package com.memely.ui.viewmodels

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.memely.blossom.BlossomClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * ViewModel for managing Blossom upload state and operations.
 */
class BlossomUploadViewModel(
    private val blossomClient: BlossomClient = BlossomClient()
) : ViewModel() {
    sealed class UploadState {
        object Idle : UploadState()
        data class Uploading(val bytesSent: Long, val totalBytes: Long) : UploadState()
        data class Success(val url: String) : UploadState()
        data class Error(val message: String?) : UploadState()
    }

    private val _uploadState = MutableStateFlow<UploadState>(UploadState.Idle)
    val uploadState: StateFlow<UploadState> = _uploadState.asStateFlow()

    /**
     * Upload a file to Blossom server.
     * 
     * @param file The file to upload
     * @param contentType MIME type of the file
     * @param pubkeyHex User's public key in hex format
     * @param signEventFunc Function that signs an event JSON and returns the signed event
     * @param endpoint Blossom endpoint (default: "upload")
     * @param onSuccess Callback with uploaded URL
     */
    fun uploadFile(
        file: File,
        contentType: String,
        pubkeyHex: String,
        signEventFunc: suspend (eventJson: String) -> String,
        endpoint: String = "upload",
        onSuccess: ((String) -> Unit)? = null
    ) {
        viewModelScope.launch {
            _uploadState.value = UploadState.Uploading(0, file.length())

            val result = withContext(Dispatchers.IO) {
                blossomClient.uploadFile(
                    file = file,
                    contentType = contentType,
                    pubkeyHex = pubkeyHex,
                    signEventFunc = signEventFunc,
                    endpoint = endpoint
                ) { sent, total ->
                    _uploadState.value = UploadState.Uploading(sent, total)
                }
            }

            if (result.ok) {
                val url = blossomClient.parseUploadUrl(result.body)
                    ?: "${blossomClient.baseUrl}/$endpoint/${file.name}"

                _uploadState.value = UploadState.Success(url)
                onSuccess?.invoke(url)
            } else {
                _uploadState.value = UploadState.Error(
                    "Upload failed: ${result.statusCode} ${result.body ?: "Unknown error"}"
                )
            }
        }
    }

    /**
     * Upload a file from content:// URI to Blossom server.
     * Handles scoped storage correctly on Android Q+.
     * 
     * @param context Android context for ContentResolver access
     * @param uri Content URI of the file to upload
     * @param contentType MIME type of the file
     * @param pubkeyHex User's public key in hex format
     * @param signEventFunc Function that signs an event JSON and returns the signed event
     * @param endpoint Blossom endpoint (default: "upload")
     * @param onSuccess Callback with uploaded URL
     */
    fun uploadFile(
        context: Context,
        uri: Uri,
        contentType: String,
        pubkeyHex: String,
        signEventFunc: suspend (eventJson: String) -> String,
        endpoint: String = "upload",
        onSuccess: ((String) -> Unit)? = null
    ) {
        viewModelScope.launch {
            val fileSize = try {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openFileDescriptor(uri, "r")?.use { fd ->
                        fd.statSize
                    } ?: 0L
                }
            } catch (e: Exception) {
                0L
            }

            _uploadState.value = UploadState.Uploading(0, fileSize)

            val result = withContext(Dispatchers.IO) {
                blossomClient.uploadFile(
                    context = context,
                    uri = uri,
                    contentType = contentType,
                    pubkeyHex = pubkeyHex,
                    signEventFunc = signEventFunc,
                    endpoint = endpoint
                ) { sent, total ->
                    _uploadState.value = UploadState.Uploading(sent, total)
                }
            }

            if (result.ok) {
                val url = blossomClient.parseUploadUrl(result.body)
                    ?: "${blossomClient.baseUrl}/$endpoint"

                _uploadState.value = UploadState.Success(url)
                onSuccess?.invoke(url)
            } else {
                _uploadState.value = UploadState.Error(
                    "Upload failed: ${result.statusCode} ${result.body ?: "Unknown error"}"
                )
            }
        }
    }

    fun reset() {
        _uploadState.value = UploadState.Idle
    }
}
