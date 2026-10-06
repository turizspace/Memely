package com.memely.nostr

import com.memely.util.SecureLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONArray
import org.json.JSONObject

data class FollowMediaItem(
    val id: String,
    val url: String,
    val thumbnailUrl: String? = null,
    val mimeType: String? = null,
    val authorPubkey: String,
    val authorName: String,
    val createdAt: Long
)

/**
 * A social media feed: follows are obtained from NIP-02, while images are read only
 * from Nostr events a follow chose to publish. It deliberately does not enumerate
 * other people's Blossom storage inventories.
 */
object FollowMediaRepository {
    private const val FOLLOW_LIMIT = 150
    private const val MEDIA_LIMIT = 500
    private val _items = MutableStateFlow<List<FollowMediaItem>>(emptyList())
    val items: StateFlow<List<FollowMediaItem>> = _items.asStateFlow()
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private var activePubkey: String? = null
    private var followsSubscription: String? = null
    private var mediaSubscription: String? = null
    private var listenerStarted = false
    private var followsLoaded = false
    private var newestFollowTags: JSONArray? = null
    private var newestFollowCreatedAt = Long.MIN_VALUE
    private val petnames = mutableMapOf<String, String>()

    fun refresh(pubkey: String, force: Boolean = false) {
        if (!pubkey.matches(Regex("[0-9a-fA-F]{64}"))) return
        if (!force && activePubkey == pubkey && (_items.value.isNotEmpty() || _isLoading.value)) return
        closeActiveSubscriptions()
        activePubkey = pubkey.lowercase()
        followsLoaded = false
        newestFollowTags = null
        newestFollowCreatedAt = Long.MIN_VALUE
        petnames.clear()
        _items.value = emptyList()
        _message.value = null
        _isLoading.value = true
        startListener()
        followsSubscription = "following-${System.currentTimeMillis()}"
        NostrRepository.broadcast(
            "[\"REQ\",\"${followsSubscription}\",{\"kinds\":[3],\"authors\":[\"${activePubkey}\"],\"limit\":1}]"
        )
    }

    private fun startListener() {
        if (listenerStarted) return
        listenerStarted = true
        NostrRepository.getScope().launch {
            NostrRepository.incomingMessagesFlow.collect { raw -> handleMessage(raw) }
        }
    }

    private fun handleMessage(raw: String) {
        val message = runCatching { JSONArray(raw) }.getOrNull() ?: return
        when (message.optString(0)) {
            "EVENT" -> message.optJSONObject(2)?.let(::handleEvent)
            "EOSE" -> handleEose(message.optString(1))
        }
    }

    private fun handleEvent(event: JSONObject) {
        val owner = activePubkey ?: return
        val author = event.optString("pubkey", "").lowercase()
        when (event.optInt("kind")) {
            3 -> if (author == owner && !followsLoaded) {
                val createdAt = event.optLong("created_at", 0L)
                if (createdAt >= newestFollowCreatedAt) {
                    newestFollowCreatedAt = createdAt
                    newestFollowTags = event.optJSONArray("tags") ?: JSONArray()
                }
            }
            0 -> if (author in petnames) {
                val displayName = runCatching { JSONObject(event.optString("content")).optString("display_name") }
                    .getOrDefault("").ifBlank {
                        runCatching { JSONObject(event.optString("content")).optString("name") }.getOrDefault("")
                    }
                if (displayName.isNotBlank()) replaceAuthorName(author, displayName)
            }
            1063, 1 -> if (author in petnames) parseMedia(event, author)
        }
    }

    private fun loadFollows(tags: JSONArray) {
        followsLoaded = true
        for (i in 0 until tags.length()) {
            val tag = tags.optJSONArray(i) ?: continue
            if (tag.optString(0) != "p") continue
            val pubkey = tag.optString(1).lowercase()
            if (!pubkey.matches(Regex("[0-9a-f]{64}"))) continue
            val petname = tag.optString(3).takeIf { it.matches(Regex("[A-Za-z0-9_]{1,64}")) }
            petnames.putIfAbsent(pubkey, petname ?: pubkey.take(8))
        }
        val authors = petnames.keys.take(FOLLOW_LIMIT)
        if (authors.isEmpty()) {
            _isLoading.value = false
            _message.value = "You are not following anyone yet."
            return
        }
        authors.forEach(NostrRepository::requestMetadata)
        mediaSubscription = "follow-media-${System.currentTimeMillis()}"
        val filter = JSONObject().apply {
            put("kinds", JSONArray().put(1063).put(1))
            put("authors", JSONArray(authors))
            put("since", (System.currentTimeMillis() / 1000L) - 90L * 24L * 60L * 60L)
            put("limit", MEDIA_LIMIT)
        }
        NostrRepository.broadcast("[\"REQ\",\"${mediaSubscription}\",$filter]")
    }

    private fun parseMedia(event: JSONObject, author: String) {
        val kind = event.optInt("kind")
        val tags = event.optJSONArray("tags") ?: JSONArray()
        val candidates = if (kind == 1063) {
            listOfNotNull(tagValue(tags, "url"))
        } else {
            imageUrlsIn(event.optString("content"))
        }
        val thumbnail = tagValue(tags, "thumb") ?: tagValue(tags, "image")
        val mimeType = tagValue(tags, "m")
        candidates.forEachIndexed { index, url ->
            if (!isSafeImageUrl(url, mimeType)) return@forEachIndexed
            val item = FollowMediaItem(
                id = "${event.optString("id")}:$index",
                url = url,
                thumbnailUrl = thumbnail?.takeIf { isSafeImageUrl(it, null) },
                mimeType = mimeType,
                authorPubkey = author,
                authorName = petnames[author] ?: author.take(8),
                createdAt = event.optLong("created_at")
            )
            val updated = (_items.value.filterNot { it.id == item.id || it.url == item.url } + item)
                .sortedByDescending { it.createdAt }.take(MEDIA_LIMIT)
            _items.value = updated
        }
    }

    private fun replaceAuthorName(pubkey: String, name: String) {
        petnames[pubkey] = name
        _items.value = _items.value.map { if (it.authorPubkey == pubkey) it.copy(authorName = name) else it }
    }

    private fun handleEose(subscriptionId: String) {
        if (subscriptionId == followsSubscription && !followsLoaded) {
            newestFollowTags?.let(::loadFollows) ?: run {
                _isLoading.value = false
                _message.value = "Could not find your follow list on the connected relays."
            }
        } else if (subscriptionId == mediaSubscription) {
            _isLoading.value = false
            if (_items.value.isEmpty()) _message.value = "No shared images from people you follow in the last 90 days."
        }
    }

    private fun closeActiveSubscriptions() {
        listOfNotNull(followsSubscription, mediaSubscription).forEach { id ->
            NostrRepository.broadcast("[\"CLOSE\",\"$id\"]")
        }
        followsSubscription = null
        mediaSubscription = null
    }

    private fun tagValue(tags: JSONArray, name: String): String? = (0 until tags.length()).firstNotNullOfOrNull { index ->
        tags.optJSONArray(index)?.takeIf { it.optString(0) == name }?.optString(1)?.takeIf { it.isNotBlank() }
    }

    private fun imageUrlsIn(content: String): List<String> =
        Regex("https?://[^\\s<>\\\"]+", RegexOption.IGNORE_CASE).findAll(content).map { it.value }
            .filter { isSafeImageUrl(it, null) }.toList()

    private fun isSafeImageUrl(url: String, mimeType: String?): Boolean {
        val parsed = url.toHttpUrlOrNull() ?: return false
        if (!parsed.isHttps) return false
        return mimeType?.startsWith("image/", true) == true ||
            parsed.encodedPath.lowercase().matches(Regex(".*\\.(png|jpe?g|gif|webp|bmp|avif)$"))
    }
}
