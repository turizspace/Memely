package com.memely.nostr

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import com.memely.util.SecureLog


data class MemeNote(
    val id: String,
    val pubkey: String,
    val content: String,
    val createdAt: Long,
    val tags: List<List<String>>
)

object MemeRepository {
    private val _memeNotesFlow = MutableStateFlow<List<MemeNote>>(emptyList())
    val memeNotesFlow: StateFlow<List<MemeNote>> = _memeNotesFlow.asStateFlow()
    
    private val _isLoadingFlow = MutableStateFlow(true)
    val isLoadingFlow: StateFlow<Boolean> = _isLoadingFlow.asStateFlow()
    
    // Meme-related tags to filter for
    private val memeTags = listOf("meme", "memestr", "memes", "funny")
    
    fun fetchMemes() {
        // Subscribe to kind 1 notes with meme tags
        val subscriptionId = "memes-${System.currentTimeMillis()}"
        
        // Create a filter for kind 1 notes with meme-related tags
        val filter = JSONObject().apply {
            put("kinds", JSONArray().put(1))
            put("#t", JSONArray().apply {
                memeTags.forEach { tag -> put(tag) }
            })
            // Limit to recent notes (last 24 hours)
            put("since", (System.currentTimeMillis() / 1000) - (24 * 60 * 60))
        }
        
        val req = """["REQ","$subscriptionId",$filter]"""
        SecureLog.d("MemeRepository: Requesting memes: $req")
        NostrRepository.broadcast(req)
        
        // Start listening for meme notes
        startMemeListener(subscriptionId)
    }
    
    private fun startMemeListener(subscriptionId: String) {
        // Use NostrRepository's scope to launch the listener
        NostrRepository.getScope().launch {
            NostrRepository.incomingMessagesFlow.collect { msg ->
                try {
                    if (msg.contains("\"kind\":1") && (msg.contains("meme") || msg.contains("memestr"))) {
                        val memeNote = parseMemeNote(msg, subscriptionId)
                        if (memeNote != null) {
                            // Add to the list and sort by most recent (descending)
                            val currentNotes = _memeNotesFlow.value.toMutableList()
                            if (currentNotes.none { it.id == memeNote.id }) {
                                currentNotes.add(memeNote)
                                // Sort by createdAt in descending order (newest first)
                                currentNotes.sortByDescending { it.createdAt }
                                _memeNotesFlow.value = currentNotes.take(100) // Limit to 100 memes
                                SecureLog.d("MemeRepository: Added meme note from ${memeNote.pubkey.take(8)}, total: ${_memeNotesFlow.value.size}")
                                // Mark loading as complete once we have at least one meme
                                if (_memeNotesFlow.value.isNotEmpty() && _isLoadingFlow.value) {
                                    _isLoadingFlow.value = false
                                    SecureLog.d("MemeRepository: Initial memes loaded")
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    SecureLog.e("MemeRepository: Error processing message: ${e.message}")
                }
            }
        }
    }
    
    private fun parseMemeNote(msg: String, subscriptionId: String): MemeNote? {
        return try {
            if (msg.trim().startsWith("[")) {
                val arr = JSONArray(msg)
                if (arr.length() >= 3) {
                    val messageType = arr.optString(0)
                    if (messageType == "EVENT") {
                        val eventObj = arr.getJSONObject(2)
                        val id = eventObj.optString("id", "")
                        val pubkey = eventObj.optString("pubkey", "")
                        val createdAt = eventObj.optLong("created_at", 0)
                        val content = eventObj.optString("content", "")
                        val tags = eventObj.optJSONArray("tags") ?: JSONArray()
                        
                        // Convert tags to list
                        val tagsList = mutableListOf<List<String>>()
                        for (i in 0 until tags.length()) {
                            val tag = tags.optJSONArray(i)
                            if (tag != null) {
                                val tagItems = mutableListOf<String>()
                                for (j in 0 until tag.length()) {
                                    tagItems.add(tag.optString(j, ""))
                                }
                                tagsList.add(tagItems)
                            }
                        }
                        
                        // Check if it has meme-related tags
                        val hasMemeTag = tagsList.any { tag ->
                            tag.isNotEmpty() && 
                            (tag[0] == "t" || tag[0] == "hashtag") && 
                            memeTags.any { memeTag -> 
                                tag.any { it.lowercase().contains(memeTag) }
                            }
                        }
                        
                        // Also check content for meme keywords
                        val contentHasMeme = memeTags.any { 
                            content.lowercase().contains(it) 
                        }
                        
                        if (hasMemeTag || contentHasMeme) {
                            return MemeNote(
                                id = id,
                                pubkey = pubkey,
                                content = content,
                                createdAt = createdAt,
                                tags = tagsList
                            )
                        }
                    }
                }
            }
            null
        } catch (e: Exception) {
            SecureLog.e("MemeRepository: Error parsing meme note: ${e.message}")
            null
        }
    }
}