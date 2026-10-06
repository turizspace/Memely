package com.memely.nostr

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONArray
import org.json.JSONObject

data class FollowMediaItem(
    val id: String,
    val content: String,
    val url: String,
    val imageUrls: List<String>,
    val thumbnailUrl: String? = null,
    val mimeType: String? = null,
    val authorPubkey: String,
    val authorName: String,
    val createdAt: Long
)

/** Loads kind-1 notes from the NIP-02 follow list, with optional inline image previews. */
object FollowMediaRepository {
    private const val MEDIA_SEARCH_TIMEOUT_MS = 30_000L
    private const val ITEMS_PUBLISH_DELAY_MS = 120L
    private val _items = MutableStateFlow<List<FollowMediaItem>>(emptyList())
    val items: StateFlow<List<FollowMediaItem>> = _items.asStateFlow()
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()
    private val itemsLock = Any()
    private val notesById = linkedMapOf<String, FollowMediaItem>()
    private var publishItemsJob: Job? = null

    private var activePubkey: String? = null
    private var followsSubscription: String? = null
    private var followEoseRelays = mutableSetOf<String>()
    private var followExpectedEoseRelays = mutableSetOf<String>()
    private var followRequestGeneration: Long? = null
    private var metadataSubscription: String? = null
    private var metadataEoseRelays = mutableSetOf<String>()
    private var metadataExpectedEoseRelays = mutableSetOf<String>()
    private val mediaSubscriptions = mutableSetOf<String>()
    private val mediaEoseRelays = mutableMapOf<String, MutableSet<String>>()
    private val mediaExpectedEoseRelays = mutableMapOf<String, Set<String>>()
    private var listenerStarted = false
    private var followsLoaded = false
    private var newestFollowTags: JSONArray? = null
    private var newestFollowCreatedAt = Long.MIN_VALUE
    private val petnames = mutableMapOf<String, String>()
    private var followTimeoutJob: Job? = null
    private var metadataTimeoutJob: Job? = null
    private val mediaTimeoutJobs = mutableMapOf<String, Job>()
    private var fallbackInProgress = false
    private var mediaRequestGeneration: Long? = null

    fun refresh(pubkey: String, force: Boolean = false) {
        if (!pubkey.matches(Regex("[0-9a-fA-F]{64}"))) return
        if (!force && activePubkey == pubkey && (_items.value.isNotEmpty() || _isLoading.value)) return
        val normalizedPubkey = pubkey.lowercase()
        val preserveExistingItems = force && activePubkey == normalizedPubkey
        closeActiveSubscriptions()
        activePubkey = normalizedPubkey
        followsLoaded = false
        newestFollowTags = null
        newestFollowCreatedAt = Long.MIN_VALUE
        fallbackInProgress = false
        followTimeoutJob?.cancel()
        petnames.clear()
        if (!preserveExistingItems) {
            synchronized(itemsLock) {
                notesById.clear()
                publishItemsJob?.cancel()
                publishItemsJob = null
                _items.value = emptyList()
            }
        }
        _message.value = null
        _isLoading.value = true
        startListener()
        requestFollowList()
        followTimeoutJob = NostrRepository.getScope().launch {
            delay(12_000)
            if (!followsLoaded) loadFollowListFromFallbackRelays()
        }
    }

    private fun startListener() {
        if (listenerStarted) return
        listenerStarted = true
        NostrRepository.getScope().launch {
            NostrRepository.relayPool.relayMessagesFlow.collect { incoming ->
                handleMessage(incoming.raw, incoming.relayUrl)
            }
        }
        NostrRepository.getScope().launch {
            NostrRepository.relayPool.connectionGenerationFlow.collect(::handleRelayGeneration)
        }
    }

    private fun handleRelayGeneration(generation: Long) {
        if (!_isLoading.value) return
        val connectedRelays = NostrRepository.relayPool.getConnectedRelayUrls()

        if (!followsLoaded && followsSubscription != null && followRequestGeneration != generation) {
            followExpectedEoseRelays.clear()
            followExpectedEoseRelays.addAll(connectedRelays)
            followRequestGeneration = generation
            println("🔄 FollowMedia: Follow-list subscription restored on ${connectedRelays.size} relays")
        }

        if (followsLoaded && mediaRequestGeneration != generation) {
            metadataExpectedEoseRelays.clear()
            metadataExpectedEoseRelays.addAll(connectedRelays)
            mediaExpectedEoseRelays.keys.forEach { id ->
                mediaExpectedEoseRelays[id] = connectedRelays
            }
            mediaRequestGeneration = generation
            println("🔄 FollowMedia: Active follow subscriptions restored on ${connectedRelays.size} relays")
        }
    }

    private fun requestFollowList() {
        val pubkey = activePubkey ?: return
        val subscriptionId = "following-${System.currentTimeMillis()}"
        followsSubscription = subscriptionId
        followEoseRelays.clear()
        followExpectedEoseRelays = NostrRepository.relayPool.getConnectedRelayUrls().toMutableSet()
        followRequestGeneration = NostrRepository.relayPool.connectionGenerationFlow.value
        NostrRepository.relayPool.subscribe(
            subscriptionId,
            "[\"REQ\",\"$subscriptionId\",{\"kinds\":[3],\"authors\":[\"$pubkey\"],\"limit\":1}]"
        )
    }

    private fun handleMessage(raw: String, relayUrl: String) {
        val message = runCatching { JSONArray(raw) }.getOrNull() ?: return
        when (message.optString(0)) {
            "EVENT" -> message.optJSONObject(2)?.let(::handleEvent)
            "EOSE" -> handleEose(message.optString(1), relayUrl)
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
                val metadata = MetadataParser.parseMetadata(event.optString("content"))
                if (metadata != null) {
                    val createdAt = event.optLong("created_at", 0L)
                    UserMetadataCache.cacheMetadataIfNewer(author, metadata.copy(createdAt = createdAt))
                    val displayName = runCatching {
                        JSONObject(event.optString("content")).optString("display_name")
                    }.getOrDefault("").ifBlank { metadata.name.orEmpty() }
                    if (displayName.isNotBlank()) replaceAuthorName(author, displayName)
                }
            }
            1 -> if (author in petnames) parseNote(event, author)
        }
    }

    private fun loadFollows(tags: JSONArray, source: String = "connected relays") {
        followsLoaded = true
        followTimeoutJob?.cancel()
        followsSubscription?.let(NostrRepository.relayPool::closeSubscription)
        followsSubscription = null
        var followTagCount = 0
        for (i in 0 until tags.length()) {
            val tag = tags.optJSONArray(i) ?: continue
            if (tag.optString(0) != "p") continue
            followTagCount++
            val pubkey = tag.optString(1).lowercase()
            if (!pubkey.matches(Regex("[0-9a-f]{64}"))) continue
            val petname = tag.optString(3).takeIf { it.matches(Regex("[A-Za-z0-9_]{1,64}")) }
            petnames.putIfAbsent(pubkey, petname ?: pubkey.take(8))
        }
        val authors = petnames.keys.toList()
        println(
            "📡 FollowMedia: Follow-list result from $source: " +
                "$followTagCount p tags, ${petnames.size} valid unique follows, requesting ${authors.size} authors"
        )
        if (authors.isEmpty()) {
            _isLoading.value = false
            _message.value = "You are not following anyone yet."
            return
        }
        requestAuthorMetadata(authors)
        requestFollowedMedia(authors)
    }

    private fun parseNote(event: JSONObject, author: String) {
        val id = event.optString("id")
        if (id.isBlank()) return
        val content = event.optString("content")
        val tags = event.optJSONArray("tags") ?: JSONArray()
        val media = mutableListOf<Pair<String, String?>>()
        media += imageUrlsIn(content).map { it to null }

        val mimeType = tagValue(tags, "m")
        tagValue(tags, "url")?.let { media += it to mimeType }

        for (i in 0 until tags.length()) {
            val tag = tags.optJSONArray(i) ?: continue
            if (tag.optString(0) != "imeta") continue
            val metadata = (1 until tag.length()).map { tag.optString(it) }
            val imageMimeType = metadata.firstNotNullOfOrNull { value ->
                value.takeIf { it.startsWith("m ", ignoreCase = true) }?.substringAfter(' ')
            } ?: mimeType
            metadata.forEach { value ->
                if (value.startsWith("url ", ignoreCase = true)) {
                    media += value.substringAfter(' ') to imageMimeType
                }
            }
        }
        val thumbnail = tagValue(tags, "thumb") ?: tagValue(tags, "image")
        val imageUrls = media.distinct()
            .filter { (url, type) -> isSafeImageUrl(url, type) }
            .map { it.first }
            .distinctBy(::imageUrlIdentity)
        val image = imageUrls.firstOrNull()
            ?: return
        val item = FollowMediaItem(
            id = id,
            content = contentWithoutImageLinks(content, imageUrls),
            url = image,
            imageUrls = imageUrls,
            thumbnailUrl = thumbnail?.takeIf { isSafeImageUrl(it, null) },
            mimeType = media.firstOrNull { it.first == image }?.second,
            authorPubkey = author,
            authorName = petnames[author] ?: author.take(8),
            createdAt = event.optLong("created_at")
        )
        synchronized(itemsLock) {
            notesById[id] = item
            scheduleItemsPublishLocked()
        }
    }

    private fun replaceAuthorName(pubkey: String, name: String) {
        petnames[pubkey] = name
        synchronized(itemsLock) {
            notesById.replaceAll { _, item ->
                if (item.authorPubkey == pubkey) item.copy(authorName = name) else item
            }
            scheduleItemsPublishLocked()
        }
    }

    private fun scheduleItemsPublishLocked() {
        if (publishItemsJob?.isActive == true) return
        publishItemsJob = NostrRepository.getScope().launch {
            delay(ITEMS_PUBLISH_DELAY_MS)
            synchronized(itemsLock) {
                _items.value = notesById.values.sortedByDescending { it.createdAt }
                publishItemsJob = null
            }
        }
    }

    private fun flushItems() {
        synchronized(itemsLock) {
            publishItemsJob?.cancel()
            publishItemsJob = null
            _items.value = notesById.values.sortedByDescending { it.createdAt }
        }
    }

    private fun handleEose(subscriptionId: String, relayUrl: String) {
        if (subscriptionId == followsSubscription && !followsLoaded) {
            followEoseRelays += relayUrl
            if (followExpectedEoseRelays.isNotEmpty() &&
                followEoseRelays.containsAll(followExpectedEoseRelays)
            ) {
                newestFollowTags?.let { loadFollows(it, "connected relays") } ?: run {
                    println("📡 FollowMedia: Connected relays returned no kind-3 follow list; trying bootstrap relays")
                    loadFollowListFromFallbackRelays()
                }
            }
        } else if (subscriptionId == metadataSubscription) {
            metadataEoseRelays += relayUrl
            if (metadataExpectedEoseRelays.isNotEmpty() &&
                metadataEoseRelays.containsAll(metadataExpectedEoseRelays)
            ) {
                metadataTimeoutJob?.cancel()
                metadataTimeoutJob = null
                metadataSubscription = null
                metadataEoseRelays.clear()
                metadataExpectedEoseRelays.clear()
                NostrRepository.relayPool.closeSubscription(subscriptionId)
            }
        } else if (subscriptionId in mediaSubscriptions) {
            val expected = mediaExpectedEoseRelays[subscriptionId] ?: emptySet()
            val completed = mediaEoseRelays[subscriptionId] ?: mutableSetOf()
            completed += relayUrl
            mediaEoseRelays[subscriptionId] = completed
            println("📡 FollowMedia: Media query EOSE ${completed.size}/${expected.size}")
            if (expected.isNotEmpty() && completed.containsAll(expected)) {
                mediaSubscriptions.remove(subscriptionId)
                mediaEoseRelays.remove(subscriptionId)
                mediaExpectedEoseRelays.remove(subscriptionId)
                mediaTimeoutJobs.remove(subscriptionId)?.cancel()
                NostrRepository.relayPool.closeSubscription(subscriptionId)
                println("📡 FollowMedia: Media query completed")
                finishMediaSearchIfComplete()
            }
        }
    }

    private fun closeActiveSubscriptions() {
        followTimeoutJob?.cancel()
        metadataTimeoutJob?.cancel()
        mediaTimeoutJobs.values.forEach { it.cancel() }
        mediaTimeoutJobs.clear()
        (listOfNotNull(followsSubscription, metadataSubscription) + mediaSubscriptions.toList()).forEach { id ->
            NostrRepository.relayPool.closeSubscription(id)
        }
        followsSubscription = null
        followEoseRelays.clear()
        followExpectedEoseRelays.clear()
        followRequestGeneration = null
        metadataSubscription = null
        metadataEoseRelays.clear()
        metadataExpectedEoseRelays.clear()
        mediaSubscriptions.clear()
        mediaEoseRelays.clear()
        mediaExpectedEoseRelays.clear()
        mediaRequestGeneration = null
    }

    private fun cancelAuthorRequests() {
        metadataTimeoutJob?.cancel()
        metadataTimeoutJob = null
        mediaTimeoutJobs.values.forEach { it.cancel() }
        mediaTimeoutJobs.clear()
        (listOfNotNull(metadataSubscription) + mediaSubscriptions.toList()).forEach { id ->
            NostrRepository.relayPool.closeSubscription(id)
        }
        metadataSubscription = null
        metadataEoseRelays.clear()
        metadataExpectedEoseRelays.clear()
        mediaSubscriptions.clear()
        mediaEoseRelays.clear()
        mediaExpectedEoseRelays.clear()
    }

    private fun requestAuthorMetadata(authors: List<String>) {
        val subscriptionId = "follow-metadata-${System.currentTimeMillis()}"
        metadataSubscription = subscriptionId
        metadataEoseRelays.clear()
        metadataExpectedEoseRelays = NostrRepository.relayPool.getConnectedRelayUrls().toMutableSet()
        val filter = JSONObject().apply {
            put("kinds", JSONArray().put(0))
            put("authors", JSONArray(authors))
            put("limit", authors.size)
        }
        NostrRepository.relayPool.subscribe(subscriptionId, "[\"REQ\",\"$subscriptionId\",$filter]")
        metadataTimeoutJob = NostrRepository.getScope().launch {
            delay(MEDIA_SEARCH_TIMEOUT_MS)
            if (metadataSubscription == subscriptionId) {
                metadataSubscription = null
                metadataEoseRelays.clear()
                metadataExpectedEoseRelays.clear()
                NostrRepository.relayPool.closeSubscription(subscriptionId)
            }
        }
    }

    /** Keep this to one relay request; fan-out batches were causing relays to stall or omit EOSE. */
    private fun requestFollowedMedia(authors: List<String>) {
        val subscriptionId = "follow-media-${System.currentTimeMillis()}"
        mediaSubscriptions += subscriptionId
        val expectedRelays = NostrRepository.relayPool.getConnectedRelayUrls()
        mediaEoseRelays[subscriptionId] = mutableSetOf()
        mediaExpectedEoseRelays[subscriptionId] = expectedRelays
        mediaRequestGeneration = NostrRepository.relayPool.connectionGenerationFlow.value
        val filter = JSONObject().apply {
            put("kinds", JSONArray().put(1))
            put("authors", JSONArray(authors))
        }
        println("📡 FollowMedia: Requesting all kind-1 notes from ${authors.size} authors")
        NostrRepository.relayPool.subscribe(subscriptionId, "[\"REQ\",\"$subscriptionId\",$filter]")
        mediaTimeoutJobs[subscriptionId] = NostrRepository.getScope().launch {
            delay(MEDIA_SEARCH_TIMEOUT_MS)
            if (mediaSubscriptions.remove(subscriptionId)) {
                val expected = mediaExpectedEoseRelays[subscriptionId].orEmpty()
                val completed = mediaEoseRelays[subscriptionId].orEmpty()
                mediaEoseRelays.remove(subscriptionId)
                mediaExpectedEoseRelays.remove(subscriptionId)
                NostrRepository.relayPool.closeSubscription(subscriptionId)
                println(
                    "⚠️ FollowMedia: Media query timed out after EOSE from " +
                        "${completed.size}/${expected.size} relays; kept ${_items.value.size} unique notes"
                )
                finishMediaSearchIfComplete(timedOut = true)
            }
        }
    }

    private fun finishMediaSearchIfComplete(timedOut: Boolean = false) {
        if (mediaSubscriptions.isNotEmpty()) return
        flushItems()
        _isLoading.value = false
        _message.value = when {
            _items.value.isNotEmpty() -> null
            timedOut -> "Notes search timed out. Pull refresh to try again."
            else -> "No notes found from people you follow."
        }
    }

    /**
     * The signed-in user's current relay set may not retain their kind-3 event.
     * Query bootstrap relays in a short-lived, isolated connection rather than
     * replacing the app's main relay pool.
     */
    private fun loadFollowListFromFallbackRelays() {
        if (fallbackInProgress || followsLoaded) return
        val expectedPubkey = activePubkey ?: return
        fallbackInProgress = true
        println("📡 FollowMedia: Requesting kind-3 follow list from bootstrap relays")
        NostrRepository.getScope().launch {
            val tags = queryFallbackRelays(expectedPubkey)
            fallbackInProgress = false
            if (activePubkey != expectedPubkey || followsLoaded) return@launch
            if (tags != null) {
                println("📡 FollowMedia: Bootstrap relays returned a kind-3 follow list")
                loadFollows(tags, "bootstrap relays")
            } else {
                println("⚠️ FollowMedia: No kind-3 follow list found on connected or bootstrap relays")
                _isLoading.value = false
                _message.value = "Your follow list was not found. Make sure your kind-3 follow list is published to a relay."
            }
        }
    }

    private suspend fun queryFallbackRelays(pubkey: String): JSONArray? = coroutineScope {
        val relayUrls = (Constants.DEFAULT_RELAYS + RelayManager.FALLBACK_RELAYS).distinct().take(5)
        val messages = Channel<String>(Channel.BUFFERED)
        val clients = relayUrls.map { NostrClient(it) }
        val connected = clients.filter { client -> runCatching { client.connect() }.getOrDefault(false) }
        println("🔗 FollowMedia: Connected to ${connected.size}/${relayUrls.size} bootstrap relays for follow-list lookup")
        if (connected.isEmpty()) {
            messages.close()
            return@coroutineScope null
        }
        val subscriptionId = "follow-fallback-${System.currentTimeMillis()}"
        val request = "[\"REQ\",\"$subscriptionId\",{\"kinds\":[3],\"authors\":[\"$pubkey\"],\"limit\":1}]"
        val readers = connected.map { client ->
            launch {
                for (message in client.incoming) messages.trySend(message)
            }
        }
        connected.forEach { it.publish(request) }

        var newestTags: JSONArray? = null
        var newestCreatedAt = Long.MIN_VALUE
        var endOfStoredEvents = 0
        withTimeoutOrNull(10_000) {
            while (endOfStoredEvents < connected.size) {
                val message = messages.receive()
                val array = runCatching { JSONArray(message) }.getOrNull() ?: continue
                when (array.optString(0)) {
                    "EVENT" -> {
                        val event = array.optJSONObject(2) ?: continue
                        if (event.optInt("kind") == 3 && event.optString("pubkey", "").equals(pubkey, true)) {
                            val createdAt = event.optLong("created_at", 0L)
                            if (createdAt >= newestCreatedAt) {
                                newestCreatedAt = createdAt
                                newestTags = event.optJSONArray("tags")
                            }
                        }
                    }
                    "EOSE" -> if (array.optString(1) == subscriptionId) endOfStoredEvents++
                }
            }
        }
        connected.forEach {
            it.publish("[\"CLOSE\",\"$subscriptionId\"]")
            it.close()
        }
        readers.forEach { it.cancel() }
        messages.close()
        newestTags
    }

    private fun tagValue(tags: JSONArray, name: String): String? = (0 until tags.length()).firstNotNullOfOrNull { index ->
        tags.optJSONArray(index)?.takeIf { it.optString(0) == name }?.optString(1)?.takeIf { it.isNotBlank() }
    }

    private fun imageUrlsIn(content: String): List<String> =
        Regex("https?://[^\\s<>\\\"]+", RegexOption.IGNORE_CASE).findAll(content).map {
            it.value.trimEnd('.', ',', '!', '?', ';', ':', ')', ']', '}')
        }
            .filter { isSafeImageUrl(it, null) }.toList()

    private fun contentWithoutImageLinks(content: String, imageUrls: List<String>): String {
        val imageUrlSet = imageUrls.toSet()
        return Regex("https?://[^\\s<>\\\"]+", RegexOption.IGNORE_CASE)
            .replace(content) { match ->
                val token = match.value
                val url = token.trimEnd('.', ',', '!', '?', ';', ':', ')', ']', '}')
                if (url in imageUrlSet) token.removePrefix(url) else token
            }
            .replace(Regex("[ \\t]{2,}"), " ")
            .replace(Regex(" *\\n *"), "\n")
            .trim()
    }

    private fun isSafeImageUrl(url: String, mimeType: String?): Boolean {
        val parsed = url.toHttpUrlOrNull() ?: return false
        if (!parsed.isHttps) return false
        return mimeType?.startsWith("image/", true) == true ||
            parsed.encodedPath.lowercase().matches(Regex(".*\\.(png|jpe?g|gif|webp|bmp|avif)$"))
    }

    private fun imageUrlIdentity(url: String): String {
        val parsed = url.toHttpUrlOrNull() ?: return url
        return parsed.newBuilder().fragment(null).build().toString()
    }
}
