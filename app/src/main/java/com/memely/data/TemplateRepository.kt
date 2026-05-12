package com.memely.data

import android.content.Context
import com.memely.network.SecureHttpClient
import com.memely.util.SecureLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import com.memely.nostr.Constants

data class MemeTemplate(
    val name: String,
    val url: String,
    val size: Long,
    val width: Int? = null,
    val height: Int? = null,
    val mimeType: String = "image/jpeg"
)

object TemplateRepository {
    private val httpClient = SecureHttpClient.createDownloadClient()
    private val fetchMutex = Mutex()
    
    private val _templatesFlow = MutableStateFlow<List<MemeTemplate>>(emptyList())
    val templatesFlow: StateFlow<List<MemeTemplate>> = _templatesFlow
    
    private val _isLoadingFlow = MutableStateFlow(false)
    val isLoadingFlow: StateFlow<Boolean> = _isLoadingFlow
    
    private val _errorFlow = MutableStateFlow<String?>(null)
    val errorFlow: StateFlow<String?> = _errorFlow
    
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
    
    suspend fun fetchTemplates(forceRefresh: Boolean = false) {
        fetchMutex.withLock {
            if (_isLoadingFlow.value) {
                return
            }
            if (!forceRefresh && _templatesFlow.value.isNotEmpty()) {
                return
            }

            _isLoadingFlow.value = true
            _errorFlow.value = null

            try {
                val templates = withContext(Dispatchers.IO) {
                    val request = Request.Builder()
                        .url(Constants.MEME_TEMPLATES_API)
                        .build()

                    httpClient.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) {
                            throw Exception("API error: ${response.code}")
                        }

                        val body = response.body?.string() ?: "{}"
                        val json = JSONObject(body)
                        if (!json.optBoolean("success", false)) {
                            throw Exception("API returned success=false")
                        }

                        val templatesArray = json.optJSONArray("templates") ?: org.json.JSONArray()
                        buildList {
                            for (i in 0 until templatesArray.length()) {
                                val templateObj = templatesArray.getJSONObject(i)
                                val rawUrl = templateObj.optString("url", "").trim()
                                if (rawUrl.isBlank()) {
                                    continue
                                }

                                val widthValue = if (templateObj.has("width")) templateObj.getInt("width") else null
                                val heightValue = if (templateObj.has("height")) templateObj.getInt("height") else null

                                add(
                                    MemeTemplate(
                                        name = templateObj.optString("name", "Unknown").trim().ifBlank { "Unknown" },
                                        url = rawUrl,
                                        size = templateObj.optLong("size", 0),
                                        width = widthValue,
                                        height = heightValue,
                                        mimeType = templateObj.optString("mime_type", "image/jpeg")
                                    )
                                )
                            }
                        }.distinctBy { it.url }
                    }
                }

                _templatesFlow.value = templates
                SecureLog.i("TemplateRepository: Loaded ${templates.size} templates")
            } catch (e: Exception) {
                val errorMsg = "Failed to load templates: ${e.message}"
                _errorFlow.value = errorMsg
                SecureLog.e("TemplateRepository: $errorMsg", e)
            } finally {
                _isLoadingFlow.value = false
            }
        }
    }
}
