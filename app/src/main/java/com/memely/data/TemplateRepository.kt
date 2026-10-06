package com.memely.data

import android.content.Context
import com.memely.blossom.BlossomConfig
import com.memely.blossom.BlossomListingClient
import com.memely.util.SecureLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class MemeTemplate(
    val name: String,
    val url: String,
    val size: Long,
    val width: Int? = null,
    val height: Int? = null,
    val mimeType: String = "image/jpeg"
)

object TemplateRepository {
    private val blossomListingClient = BlossomListingClient()
    private val fetchMutex = Mutex()
    
    private val _templatesFlow = MutableStateFlow<List<MemeTemplate>>(emptyList())
    val templatesFlow: StateFlow<List<MemeTemplate>> = _templatesFlow
    
    private val _isLoadingFlow = MutableStateFlow(false)
    val isLoadingFlow: StateFlow<Boolean> = _isLoadingFlow
    
    private val _errorFlow = MutableStateFlow<String?>(null)
    val errorFlow: StateFlow<String?> = _errorFlow
    private var loadedForPubkey: String? = null
    
    /**
     * Search/filter templates by name
     */
    fun searchTemplates(query: String): List<MemeTemplate> {
        val allTemplates = _templatesFlow.value
        
        if (query.isBlank()) {
            return allTemplates
        }
        
        val lowerQuery = query.lowercase()
        return allTemplates.filter { template ->
            template.name.lowercase().contains(lowerQuery)
        }
    }
    
    /**
     * Get only favorite templates
     */
    fun getFavoriteTemplates(context: Context): List<MemeTemplate> {
        val favorites = FavoritesManager.getFavorites(context)
        return _templatesFlow.value.filter { template ->
            favorites.contains(template.url)
        }
    }
    
    /**
     * Search within favorites
     */
    fun searchFavoriteTemplates(context: Context, query: String): List<MemeTemplate> {
        val favorites = getFavoriteTemplates(context)
        
        if (query.isBlank()) {
            return favorites
        }
        
        val lowerQuery = query.lowercase()
        return favorites.filter { template ->
            template.name.lowercase().contains(lowerQuery)
        }
    }
    
    /**
     * Loads image blobs owned by the active Nostr identity from its configured Blossom server.
     * The caller supplies signing so external signers never require exporting an nsec.
     */
    suspend fun fetchTemplates(
        pubkey: String,
        signEvent: suspend (String) -> String,
        forceRefresh: Boolean = false,
        serverUrl: String = BlossomConfig.baseUrl
    ) {
        fetchMutex.withLock {
            if (_isLoadingFlow.value) {
                SecureLog.w("TemplateRepository: Skipping fetch; another Blossom request is loading")
                return
            }
            if (!forceRefresh && loadedForPubkey == pubkey && _templatesFlow.value.isNotEmpty()) {
                SecureLog.d(
                    "TemplateRepository: Using ${_templatesFlow.value.size} cached blobs for ${pubkey.take(8)}"
                )
                return
            }

            SecureLog.i(
                "TemplateRepository: Fetching Blossom media for ${pubkey.take(8)} from $serverUrl " +
                    "(forceRefresh=$forceRefresh)"
            )
            // Results are identity-scoped. Never show a previous account's media while loading.
            if (loadedForPubkey != pubkey) {
                _templatesFlow.value = emptyList()
                loadedForPubkey = null
                SecureLog.d("TemplateRepository: Cleared templates belonging to a different identity")
            }

            _isLoadingFlow.value = true
            _errorFlow.value = null

            try {
                val blobs = blossomListingClient.listFiles(serverUrl, pubkey, signEvent)
                val images = blobs.filter { it.mimeType.startsWith("image/", ignoreCase = true) }
                SecureLog.i(
                    "TemplateRepository: Blossom list returned ${blobs.size} valid blobs; " +
                        "${images.size} are images"
                )
                val templates = images.map { blob ->
                    MemeTemplate(
                        name = "Blossom ${blob.sha256.take(12)}",
                        url = blob.url,
                        size = blob.size,
                        mimeType = blob.mimeType
                    )
                }

                _templatesFlow.value = templates
                loadedForPubkey = pubkey
                SecureLog.i(
                    "TemplateRepository: Published ${templates.size} image templates for ${pubkey.take(8)}"
                )
            } catch (e: Exception) {
                val errorMsg =
                    "Failed to load Blossom media for ${pubkey.take(8)} from $serverUrl: ${e.message}"
                _errorFlow.value = errorMsg
                SecureLog.e("TemplateRepository: $errorMsg", e)
            } finally {
                _isLoadingFlow.value = false
            }
        }
    }
}
