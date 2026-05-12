package com.memely.data

import com.memely.nostr.MemeNote
import com.memely.nostr.NostrRepository
import com.memely.util.SecureLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/**
 * Repository for fetching replies, reactions, and reposts for a given event.
 * Exposes a per-event StateFlow and uses structured relay subscriptions
 * instead of callback mutation plus busy-wait polling.
 */
class InteractionRepository(
    private val appScope: CoroutineScope,
    private val fetchTimeoutMs: Long = 3_000L
) {
    data class InteractionCounts(
        val eventId: String,
        val replyCount: Int = 0,
        val likeCount: Int = 0,
        val dislikeCount: Int = 0,
        val emojiReactions: Map<String, Int> = emptyMap(),
        val repostCount: Int = 0,
        val replies: List<MemeNote> = emptyList()
    )

    data class InteractionUiState(
        val counts: InteractionCounts? = null,
        val isLoading: Boolean = false,
        val error: String? = null
    )

    private val cachedCounts = ConcurrentHashMap<String, InteractionCounts>()
    private val interactionStates = ConcurrentHashMap<String, MutableStateFlow<InteractionUiState>>()
    private val refreshMutexes = ConcurrentHashMap<String, Mutex>()

    fun observeInteractions(eventId: String): StateFlow<InteractionUiState> {
        return interactionStates.getOrPut(eventId) {
            MutableStateFlow(
                InteractionUiState(
                    counts = cachedCounts[eventId],
                    isLoading = cachedCounts[eventId] == null
                )
            )
        }.asStateFlow()
    }

    suspend fun fetchInteractions(eventId: String): InteractionCounts? {
        refreshInteractions(eventId)
        return cachedCounts[eventId]
    }

    suspend fun refreshInteractions(eventId: String, forceRefresh: Boolean = false) {
        val state = interactionStates.getOrPut(eventId) {
            MutableStateFlow(InteractionUiState())
        }
        val mutex = refreshMutexes.getOrPut(eventId) { Mutex() }

        mutex.withLock {
            val cached = cachedCounts[eventId]
            if (cached != null && !forceRefresh) {
                state.value = InteractionUiState(counts = cached)
                SecureLog.d("InteractionRepository: Using cached interactions for ${SecureLog.truncateHex(eventId)}")
                return
            }

            state.value = state.value.copy(isLoading = true, error = null)

            runCatching {
                fetchFromRelays(eventId)
            }.onSuccess { counts ->
                cachedCounts[eventId] = counts
                state.value = InteractionUiState(counts = counts)
                SecureLog.d(
                    "InteractionRepository: Loaded interactions for ${SecureLog.truncateHex(eventId)} " +
                        "replies=${counts.replyCount} likes=${counts.likeCount} reposts=${counts.repostCount}"
                )
            }.onFailure { throwable ->
                state.value = state.value.copy(
                    isLoading = false,
                    error = "Failed to fetch interactions: ${throwable.message}"
                )
                SecureLog.e("InteractionRepository: Failed to fetch interactions for ${SecureLog.truncateHex(eventId)}", throwable)
            }
        }
    }

    fun invalidateCache(eventId: String) {
        cachedCounts.remove(eventId)
        interactionStates[eventId]?.value = interactionStates[eventId]?.value?.copy(error = null)
            ?: InteractionUiState()
        SecureLog.d("InteractionRepository: Invalidated cache for ${SecureLog.truncateHex(eventId)}")

        if (interactionStates.containsKey(eventId)) {
            appScope.launch {
                refreshInteractions(eventId, forceRefresh = true)
            }
        }
    }

    fun getCachedInteractions(eventId: String): InteractionCounts? = cachedCounts[eventId]

    private suspend fun fetchFromRelays(eventId: String): InteractionCounts {
        val repliesById = linkedMapOf<String, MemeNote>()
        val reactionsById = mutableSetOf<String>()
        val repostsById = mutableSetOf<String>()
        val emojiReactions = mutableMapOf<String, Int>()

        withTimeoutOrNull(fetchTimeoutMs) {
            NostrRepository.interactionEvents(eventId).collect { eventJson ->
                processInteractionEvent(
                    eventId = eventId,
                    eventJson = eventJson,
                    repliesById = repliesById,
                    reactionsById = reactionsById,
                    repostsById = repostsById,
                    emojiReactions = emojiReactions
                )
            }
        }

        val replies = repliesById.values.sortedBy { it.createdAt }
        return InteractionCounts(
            eventId = eventId,
            replyCount = replies.size,
            likeCount = emojiReactions["+"] ?: 0,
            dislikeCount = emojiReactions["-"] ?: 0,
            emojiReactions = emojiReactions.toMap(),
            repostCount = repostsById.size,
            replies = replies
        )
    }

    private fun processInteractionEvent(
        eventId: String,
        eventJson: String,
        repliesById: MutableMap<String, MemeNote>,
        reactionsById: MutableSet<String>,
        repostsById: MutableSet<String>,
        emojiReactions: MutableMap<String, Int>
    ) {
        try {
            val json = JSONObject(eventJson)
            val kind = json.optInt("kind", 0)
            val noteId = json.optString("id", "")
            if (noteId.isBlank()) {
                return
            }

            when (kind) {
                1 -> {
                    if (isReplyToEvent(json.optJSONArray("tags"), eventId) && repliesById.putIfAbsent(noteId, parseMemeNoteFromJson(json)) == null) {
                        SecureLog.d("InteractionRepository: Added reply ${SecureLog.truncateHex(noteId)}")
                    }
                }
                7 -> {
                    if (reactionsById.add(noteId)) {
                        val content = json.optString("content", "+")
                        emojiReactions[content] = (emojiReactions[content] ?: 0) + 1
                    }
                }
                6 -> repostsById.add(noteId)
            }
        } catch (e: Exception) {
            SecureLog.e("InteractionRepository: Error parsing interaction event", e)
        }
    }

    private fun isReplyToEvent(tags: JSONArray?, eventId: String): Boolean {
        if (tags == null) {
            return false
        }

        for (i in 0 until tags.length()) {
            val tag = tags.optJSONArray(i) ?: continue
            if (tag.optString(0) == "e" && tag.optString(1) == eventId) {
                return true
            }
        }

        return false
    }

    private fun parseMemeNoteFromJson(json: JSONObject): MemeNote {
        val tagsArray = json.optJSONArray("tags") ?: JSONArray()
        val tags = mutableListOf<List<String>>()
        for (i in 0 until tagsArray.length()) {
            val tagArray = tagsArray.optJSONArray(i)
            val tag = mutableListOf<String>()
            if (tagArray != null) {
                for (j in 0 until tagArray.length()) {
                    tag.add(tagArray.optString(j, ""))
                }
            }
            tags.add(tag)
        }

        return MemeNote(
            id = json.optString("id", ""),
            pubkey = json.optString("pubkey", ""),
            content = json.optString("content", ""),
            createdAt = json.optLong("created_at", 0),
            tags = tags
        )
    }
}
