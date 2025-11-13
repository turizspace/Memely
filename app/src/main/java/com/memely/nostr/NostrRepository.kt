package com.memely.nostr

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import com.memely.utils.SecureJsonParser
import com.memely.util.SecureLog

object NostrRepository {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // Start with empty list - we'll update it with proper relays
    val relayPool = RelayPool(emptyList())
    
    val connectedRelaysFlow: StateFlow<Int> get() = relayPool.connectedRelaysFlow
    val incomingMessagesFlow: SharedFlow<String> get() = relayPool.incomingMessagesFlow
    
    private val _metadataState = MutableStateFlow<MetadataParser.UserMetadata?>(null)
    val metadataState: StateFlow<MetadataParser.UserMetadata?> = _metadataState.asStateFlow()

    private val _userRelaysState = MutableStateFlow<List<String>>(emptyList())
    val userRelaysState: StateFlow<List<String>> = _userRelaysState

    private var isMetadataListenerActive = false
    private var isRelayListenerActive = false
    private var hasRealMetadata = false
    private var isInitialConnectionDone = false
    
    // FIX: Track connection state to prevent duplicates using a Mutex
    private val connectMutex = Mutex()
    private var lastConnectedRelays: List<String> = emptyList()

    suspend fun connectAll() {
        val currentRelays = RelayManager.effectiveRelays.value

        // Use a Mutex to prevent concurrent connectAll executions which could double-connect
        connectMutex.withLock {
            if (currentRelays.sorted() == lastConnectedRelays.sorted()) {
                println("NostrRepository: Already connected to these relays, skipping")
                return@withLock
            }

            println("NostrRepository: Connecting to ${currentRelays.size} relays: ${currentRelays.take(3)}...")
            relayPool.updateRelays(currentRelays)
            lastConnectedRelays = currentRelays
            isInitialConnectionDone = true
        }
    }

    // Main function to fetch both metadata and relays
    suspend fun fetchUserProfile(pubkey: String): Pair<MetadataParser.UserMetadata?, List<String>> {
        if (pubkey.isBlank()) {
            SecureLog.e("NostrRepository: Invalid pubkey")
            return null to emptyList()
        }
        
        SecureLog.d("NostrRepository: Fetching profile and relays for ${pubkey.take(8)}...")
        
        var metadata: MetadataParser.UserMetadata? = null
        val relays = mutableListOf<String>()

        // Request both metadata and relay list
        requestMetadata(pubkey)
        requestRelayList(pubkey)

        try {
            // INCREASED TIMEOUT from 15s to 30s
            withTimeout(30000) {
                incomingMessagesFlow.collect { msg ->
                    try {
                        when {
                            msg.contains("\"kind\":0") && msg.contains(pubkey) -> {
                                val parsedMetadata = parseMetadataFromMessage(msg, pubkey)
                                if (parsedMetadata != null) {
                                    metadata = parsedMetadata
                                    hasRealMetadata = true
                                    _metadataState.value = parsedMetadata
                                    // CRITICAL: Cache metadata immediately so it persists even if coroutines are cancelled
                                    UserMetadataCache.cacheMetadata(pubkey, parsedMetadata)
                                    SecureLog.d("NostrRepository: Found metadata: ${parsedMetadata.name}")
                                }
                            }
                            msg.contains("\"kind\":10002") && msg.contains(pubkey) -> {
                                SecureLog.d("NostrRepository: Processing potential relay list message")
                                val parsedRelays = parseRelayListFromMessage(msg, pubkey)
                                if (parsedRelays.isNotEmpty()) {
                                    relays.addAll(parsedRelays)
                                    _userRelaysState.value = parsedRelays
                                    RelayManager.updateUserRelays(parsedRelays)
                                    SecureLog.d("NostrRepository: Found ${parsedRelays.size} user relays: ${parsedRelays.take(3)}...")
                                    
                                    // CRITICAL: Update relay pool with user's preferred relays
                                    updateRelayPoolWithUserRelays(parsedRelays)
                                } else {
                                    SecureLog.w("NostrRepository: Relay list parsed but empty - check parsing logic")
                                }
                            }
                        }

                        // REMOVED: Don't cancel early, let the timeout handle it
                        // This prevents the coroutine scope cancellation from affecting relay connections
                    } catch (e: Exception) {
                        SecureLog.e("NostrRepository: Error processing message: ${e.message}")
                    }
                }
            }
        } catch (e: TimeoutCancellationException) {
            SecureLog.w("NostrRepository: Timeout waiting for profile data from $pubkey")
        }

        // ONLY create basic metadata if we never found real metadata
        if (metadata == null && !hasRealMetadata) {
            metadata = createBasicMetadata()
            _metadataState.value = metadata
            SecureLog.d("NostrRepository: Created basic metadata for user (no real data found)")
        } else if (metadata == null) {
            SecureLog.d("NostrRepository: Using existing real metadata from continuous listener")
            metadata = _metadataState.value
        }

        // Log results
        SecureLog.d("NostrRepository: Fetch completed - metadata: ${metadata?.name ?: "null"}, relays: ${relays.size}")
        
        return metadata to relays
    }

    // FIXED: Better metadata fetching with proper timeout and request issuance
    suspend fun fetchProfileMetadata(pubkey: String): MetadataParser.UserMetadata? {
        if (pubkey.isBlank()) {
            SecureLog.e("NostrRepository: Invalid pubkey for metadata fetch")
            return null
        }
        
        SecureLog.d("NostrRepository: Fetching metadata for ${pubkey.take(8)}...")
        
        var metadata: MetadataParser.UserMetadata? = null
        
        // Request metadata from relays
        requestMetadata(pubkey)

        try {
            // Timeout for metadata-only fetch (increased to handle network delays)
            withTimeout(15000) {
                incomingMessagesFlow.collect { msg ->
                    try {
                        if (msg.contains("\"kind\":0") && msg.contains(pubkey)) {
                            val parsedMetadata = parseMetadataFromMessage(msg, pubkey)
                            if (parsedMetadata != null) {
                                metadata = parsedMetadata
                                // CRITICAL: Cache metadata immediately so it persists even if coroutines are cancelled
                                UserMetadataCache.cacheMetadata(pubkey, parsedMetadata)
                                SecureLog.d("NostrRepository: Found metadata for ${pubkey.take(8)}: ${parsedMetadata.name}")
                                cancel() // Stop collection once we found the metadata
                            }
                        }
                    } catch (e: Exception) {
                        SecureLog.e("NostrRepository: Error processing metadata message: ${e.message}")
                    }
                }
            }
        } catch (e: TimeoutCancellationException) {
            SecureLog.w("NostrRepository: Timeout waiting for metadata from ${pubkey.take(8)}")
        }

        return metadata
    }

    // CRITICAL FIX: Update relay pool with user's preferred relays
    private suspend fun updateRelayPoolWithUserRelays(newRelays: List<String>) {
        if (newRelays.isNotEmpty()) {
            SecureLog.d("NostrRepository: Updating relay pool with ${newRelays.size} user relays")
            SecureLog.d("NostrRepository: User relays: ${newRelays.take(5)}...")
            try {
                relayPool.updateRelays(newRelays)
                SecureLog.d("NostrRepository: Successfully updated relay pool to user's preferred relays")
                
                // Log the current state for debugging
                SecureLog.d("NostrRepository: RelayManager effective relays: ${RelayManager.effectiveRelays.value.size}")
                SecureLog.d("NostrRepository: RelayPool current relays: ${relayPool.getCurrentRelays().size}")
            } catch (e: Exception) {
                SecureLog.e("NostrRepository: Failed to update relay pool: ${e.message}")
            }
        } else {
            SecureLog.w("NostrRepository: No relays to update - empty list")
        }
    }

    private fun requestRelayList(pubkey: String) {
        val subscriptionId = "relays-${pubkey.take(8)}"
        val req = """["REQ","$subscriptionId",{"kinds":[10002],"authors":["$pubkey"]}]"""
        
        SecureLog.d("NostrRepository: Requesting relay list: $req")
        relayPool.broadcast(req)
    }

    private fun parseRelayListFromMessage(msg: String, targetPubkey: String): List<String> {
        return try {
            SecureLog.d("NostrRepository: Attempting to parse relay list from message: ${msg.take(300)}")
            
            if (msg.trim().startsWith("[")) {
                val arr = JSONArray(msg)
                SecureLog.d("NostrRepository: JSON array length: ${arr.length()}")
                
                if (arr.length() >= 3) {
                    val messageType = arr.optString(0)
                    SecureLog.d("NostrRepository: Message type: $messageType")
                    
                    if (messageType == "EVENT") {
                        val eventObj = arr.getJSONObject(2)
                        val kind = eventObj.optInt("kind", -1)
                        val eventPubkey = eventObj.optString("pubkey", "")
                        val content = eventObj.optString("content", "{}")
                        val tags = eventObj.optJSONArray("tags")
                        
                        SecureLog.d("NostrRepository: Found EVENT - kind: $kind, pubkey: ${eventPubkey.take(8)}")
                        
                        if (kind == 10002 && eventPubkey == targetPubkey) {
                            SecureLog.d("NostrRepository: Found matching NIP-65 relay list!")
                            
                            // Try parsing from tags first (new NIP-65 format)
                            val relaysFromTags = parseRelaysFromTags(tags)
                            if (relaysFromTags.isNotEmpty()) {
                                SecureLog.d("NostrRepository: Found ${relaysFromTags.size} relays from tags: ${relaysFromTags.take(3)}...")
                                return relaysFromTags
                            }
                            
                            // Fallback to content parsing (old format)
                            val parsedRelays = MetadataParser.parseRelayList(content)
                            SecureLog.d("NostrRepository: Parsed ${parsedRelays.size} relays from content: ${parsedRelays.take(3)}...")
                            return parsedRelays
                        } else {
                            SecureLog.w("NostrRepository: Event doesn't match - kind: $kind (expected 10002), pubkey: ${eventPubkey.take(8)} (expected ${targetPubkey.take(8)})")
                        }
                    } else {
                        SecureLog.w("NostrRepository: Not an EVENT message, type: $messageType")
                    }
                } else {
                    SecureLog.w("NostrRepository: JSON array too short, length: ${arr.length()}")
                }
            } else {
                SecureLog.w("NostrRepository: Message doesn't start with '[' - not a JSON array")
            }
            emptyList()
        } catch (e: Exception) {
            SecureLog.e("NostrRepository: Error parsing relay list: ${e.message}")
            emptyList()
        }
    }

    private fun parseRelaysFromTags(tags: org.json.JSONArray?): List<String> {
        val relays = mutableListOf<String>()
        
        if (tags == null) {
            SecureLog.w("NostrRepository: Tags array is null")
            return emptyList()
        }
        
        SecureLog.d("NostrRepository: Processing ${tags.length()} tags")
        
        for (i in 0 until tags.length()) {
            try {
                val tag = tags.optJSONArray(i)
                if (tag != null && tag.length() >= 2) {
                    val tagType = tag.optString(0, "")
                    val relayUrl = tag.optString(1, "")
                    
                    if (tagType == "r" && (relayUrl.startsWith("wss://") || relayUrl.startsWith("ws://"))) {
                        relays.add(relayUrl)
                    }
                }
            } catch (e: Exception) {
                SecureLog.w("NostrRepository: Error processing tag #$i: ${e.message}")
            }
        }
        
        SecureLog.d("NostrRepository: Found ${relays.size} relays from tags")
        return relays
    }

    private fun parseMetadataFromMessage(msg: String, targetPubkey: String): MetadataParser.UserMetadata? {
        return try {
            if (msg.trim().startsWith("[")) {
                val arr = JSONArray(msg)
                if (arr.length() >= 3) {
                    val messageType = arr.optString(0)
                    if (messageType == "EVENT") {
                        val eventObj = arr.getJSONObject(2)
                        return parseEventObject(eventObj, targetPubkey)
                    }
                }
            }
            
            if (msg.trim().startsWith("{")) {
                val eventObj = JSONObject(msg)
                return parseEventObject(eventObj, targetPubkey)
            }
            
            null
        } catch (e: Exception) {
            SecureLog.e("NostrRepository: Failed to parse message as JSON: ${e.message}")
            null
        }
    }

    private fun parseEventObject(eventObj: JSONObject, targetPubkey: String): MetadataParser.UserMetadata? {
        return try {
            val kind = eventObj.optInt("kind", -1)
            val eventPubkey = eventObj.optString("pubkey", "")
            val content = eventObj.optString("content", "{}")
            
            if (kind == 0 && eventPubkey == targetPubkey) {
                val parsed = MetadataParser.parseMetadata(content)
                if (parsed != null) {
                    return parsed
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    private fun createBasicMetadata(): MetadataParser.UserMetadata {
        return MetadataParser.UserMetadata(
            name = "Memely User",
            about = "Welcome to Memely!",
            picture = null,
            nip05 = null
        )
    }

    fun startProfileListener(pubkey: String) {
        startMetadataListener(pubkey)
        startRelayListener(pubkey)
    }

    private fun startRelayListener(pubkey: String) {
        if (isRelayListenerActive) return
        
        scope.launch {
            isRelayListenerActive = true
            SecureLog.d("NostrRepository: Starting relay listener for ${pubkey.take(8)}")
            incomingMessagesFlow.collect { msg ->
                try {
                    if (msg.contains("\"kind\":10002") && msg.contains(pubkey)) {
                        SecureLog.d("NostrRepository: Relay listener processing message")
                        val relays = parseRelayListFromMessage(msg, pubkey)
                        if (relays.isNotEmpty()) {
                            _userRelaysState.value = relays
                            RelayManager.updateUserRelays(relays)
                            updateRelayPoolWithUserRelays(relays)
                        }
                    }
                } catch (e: Exception) {
                    SecureLog.e("NostrRepository: Error in relay listener: ${e.message}")
                }
            }
        }
    }

    fun startMetadataListener(pubkey: String) {
        if (isMetadataListenerActive) return
        
        scope.launch {
            isMetadataListenerActive = true
            incomingMessagesFlow.collect { msg ->
                try {
                    if (msg.contains("\"kind\":0") && msg.contains(pubkey)) {
                        val parsed = parseMetadataFromMessage(msg, pubkey)
                        if (parsed != null) {
                            hasRealMetadata = true
                            _metadataState.value = parsed
                        }
                    }
                } catch (e: Exception) {
                    // Ignore errors in continuous listener
                }
            }
        }
    }

    fun getScope(): CoroutineScope = scope
    
    fun broadcast(message: String) {
        relayPool.broadcast(message)
    }

    /**
     * Publish a signed event to all connected relays with improved reliability.
     * @param eventMessage The full EVENT message: ["EVENT", <signedEvent>]
     */
    fun publishEvent(eventMessage: String) {
        val connectedCount = relayPool.getConnectedCount()
        SecureLog.d("NostrRepository: Publishing event to $connectedCount connected relays")
        
        if (connectedCount == 0) {
            SecureLog.w("NostrRepository: No relays connected! Event will not be published.")
            SecureLog.w("NostrRepository: Call connectAll() first to establish relay connections")
            return
        }
        
        SecureLog.d("NostrRepository: Event message preview: ${eventMessage.take(200)}...")
        
        // Extract event ID for monitoring responses
        try {
            val eventIdRegex = """"id":"([a-f0-9]{64})"""".toRegex()
            val match = eventIdRegex.find(eventMessage)
            val eventId = match?.groupValues?.get(1)
            
            if (eventId != null) {
                // Monitor relay responses for this event with extended timeout
                scope.launch {
                    var okCount = 0
                    var failCount = 0
                    val timeout = System.currentTimeMillis() + 15000 // 15 second timeout for relay responses
                    val startTime = System.currentTimeMillis()
                    
                    try {
                        incomingMessagesFlow.collect { msg ->
                            val now = System.currentTimeMillis()
                            if (now > timeout) {
                                SecureLog.d("NostrRepository: Relay response monitoring timeout after ${now - startTime}ms")
                                return@collect
                            }
                            
                            // Check for relay responses mentioning this event
                            if (msg.contains(eventId)) {
                                when {
                                    msg.contains("\"OK\"") && msg.contains("true") -> {
                                        okCount++
                                        SecureLog.d("NostrRepository: Relay accepted event: $eventId (OK: $okCount)")
                                    }
                                    msg.contains("\"OK\"") && msg.contains("false") -> {
                                        failCount++
                                        SecureLog.w("NostrRepository: Relay rejected event: ${msg.take(150)}")
                                    }
                                    msg.contains("\"NOTICE\"") -> {
                                        SecureLog.w("NostrRepository: Relay notice: ${msg.take(150)}")
                                    }
                                }
                                
                                // Log current state periodically
                                if ((okCount + failCount) % 5 == 0) {
                                    SecureLog.d("NostrRepository: Current tally - OK: $okCount, Failed: $failCount")
                                }
                            }
                        }
                    } catch (e: Exception) {
                        SecureLog.w("NostrRepository: Error monitoring relay responses: ${e.message}")
                    }
                }
            }
        } catch (e: Exception) {
            SecureLog.w("NostrRepository: Could not monitor relay responses: ${e.message}")
        }
        
        relayPool.broadcast(eventMessage)
        SecureLog.d("NostrRepository: Event broadcast initiated")
    }

    fun requestMetadata(pubkey: String) {
        val subscriptionId = "meta-${pubkey.take(8)}"
        val req = """["REQ","$subscriptionId",{"kinds":[0],"authors":["$pubkey"]}]"""
        relayPool.broadcast(req)
    }

    fun refreshProfile(pubkey: String) {
        scope.launch {
            fetchUserProfile(pubkey)
        }
    }

    fun refreshMetadata(pubkey: String) {
        refreshProfile(pubkey)
    }
    
    /**
     * Subscribe to interactions (replies, reactions, reposts) for an event
     * Fetches kind 1 (replies), kind 7 (reactions), and kind 6 (reposts)
     */
    fun subscribeToReplies(eventId: String, subscriptionId: String = "interactions-${eventId.take(8)}", onMessage: (String) -> Unit) {
        val subId = subscriptionId
        
        // Create a subscription for all interaction kinds
        // Kind 1: replies with e-tag matching eventId
        // Kind 7: reactions with e-tag matching eventId
        // Kind 6: reposts with e-tag matching eventId
        val req = """["REQ","$subId",{"kinds":[1,6,7],"#e":["$eventId"],"limit":100}]"""
        
        relayPool.broadcast(req)
        
        
        // Listen for messages
        scope.launch {
            incomingMessagesFlow.collect { message ->
                try {
                    val json = JSONArray(message)
                    val msgType = json.optString(0, "")
                    val msgSubId = json.optString(1, "")
                    
                    if (msgType == "EVENT" && msgSubId == subId) {
                        val eventJson = json.optJSONObject(2)
                        if (eventJson != null) {
                            onMessage(eventJson.toString())
                        }
                    }
                } catch (e: Exception) {
                    
                }
            }
        }
    }
    
    fun closeSubscription(subscriptionId: String) {
        val req = """["CLOSE","$subscriptionId"]"""
        relayPool.broadcast(req)
        
    }
}
