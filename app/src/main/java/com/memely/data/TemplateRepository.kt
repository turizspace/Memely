package com.memely.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
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
    private val httpClient = OkHttpClient()
    
    private val _templatesFlow = MutableStateFlow<List<MemeTemplate>>(emptyList())
    val templatesFlow: StateFlow<List<MemeTemplate>> = _templatesFlow
    
    private val _isLoadingFlow = MutableStateFlow(false)
    val isLoadingFlow: StateFlow<Boolean> = _isLoadingFlow
    
    private val _errorFlow = MutableStateFlow<String?>(null)
    val errorFlow: StateFlow<String?> = _errorFlow
    
    suspend fun fetchTemplates() {
        _isLoadingFlow.value = true
        _errorFlow.value = null
        
        try {
            withContext(Dispatchers.IO) {
                val request = Request.Builder()
                    .url(Constants.MEME_TEMPLATES_API)
                    .build()
                
                httpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: "{}"
                        val json = JSONObject(body)
                        
                        if (json.optBoolean("success", false)) {
                            val templatesArray = json.optJSONArray("templates") ?: org.json.JSONArray()
                            val templates = mutableListOf<MemeTemplate>()
                            
                            for (i in 0 until templatesArray.length()) {
                                val templateObj = templatesArray.getJSONObject(i)
                                val widthValue = if (templateObj.has("width")) templateObj.getInt("width") else null
                                val heightValue = if (templateObj.has("height")) templateObj.getInt("height") else null
                                
                                templates.add(
                                    MemeTemplate(
                                        name = templateObj.optString("name", "Unknown"),
                                        url = templateObj.optString("url", ""),
                                        size = templateObj.optLong("size", 0),
                                        width = widthValue,
                                        height = heightValue,
                                        mimeType = templateObj.optString("mime_type", "image/jpeg")
                                    )
                                )
                            }
                            
                            _templatesFlow.value = templates
                            println("✅ TemplateRepository: Loaded ${templates.size} templates")
                        } else {
                            throw Exception("API returned success=false")
                        }
                    } else {
                        throw Exception("API error: ${response.code}")
                    }
                }
            }
        } catch (e: Exception) {
            val errorMsg = "Failed to load templates: ${e.message}"
            _errorFlow.value = errorMsg
            println("❌ TemplateRepository: $errorMsg")
        } finally {
            _isLoadingFlow.value = false
        }
    }
}
