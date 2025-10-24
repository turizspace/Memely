package com.memely.ui.viewmodels

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.memely.blossom.BlossomClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

/**
 * ViewModel for managing Blossom upload state and operations.
 */
class BlossomUploadViewModel(
    private val blossomClient: BlossomClient = BlossomClient()
) {
    sealed class UploadState {
        object Idle : UploadState()
        data class Uploading(val bytesSent: Long, val totalBytes: Long) : UploadState()
        data class Success(val url: String) : UploadState()
        data class Error(val message: String?) : UploadState()
    }

    var uploadState by mutableStateOf<UploadState>(UploadState.Idle)
        private set

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
        coroutineScope: CoroutineScope,
        onSuccess: ((String) -> Unit)? = null
    ) {
        coroutineScope.launch(Dispatchers.IO) {
            uploadState = UploadState.Uploading(0, file.length())
            
            val result = blossomClient.uploadFile(
                file = file,
                contentType = contentType,
                pubkeyHex = pubkeyHex,
                signEventFunc = signEventFunc,
                endpoint = endpoint
            ) { sent, total ->
                uploadState = UploadState.Uploading(sent, total)
            }

            if (result.ok) {
                // Parse URL from response
                val url = blossomClient.parseUploadUrl(result.body) 
                    ?: "${blossomClient.baseUrl}/$endpoint/${file.name}"
                
                uploadState = UploadState.Success(url)
                onSuccess?.invoke(url)
            } else {
                uploadState = UploadState.Error(
                    "Upload failed: ${result.statusCode} ${result.body ?: "Unknown error"}"
                )
            }
        }
    }

    fun reset() {
        uploadState = UploadState.Idle
    }
}
