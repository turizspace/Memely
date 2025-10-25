package com.memely.nostr

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.json.JSONArray
import org.json.JSONObject

object NostrRepository {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // Start with empty list - we'll update it with proper relays
    private val relayPool = RelayPool(emptyList())
    
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
    
    // FIX: Track connection state to prevent duplicates
    private var isConnecting = false
    private var lastConnectedRelays: List<String> = emptyList()

    suspend fun connectAll() {
        val currentRelays = RelayManager.effectiveRelays.value
        
        // FIX: Prevent duplicate connections to same relays
        if (isConnecting) {
            println("⏭️ NostrRepository: Already connecting, skipping")
            return
        }
        
        if (currentRelays.sorted() == lastConnectedRelays.sorted()) {
            println("⏭️ NostrRepository: Already connected to these relays, skipping")
            return
        }
        
        isConnecting = true
        try {
            println("🔄 NostrRepository: Connecting to ${currentRelays.size} relays: ${currentRelays.take(3)}...")
            relayPool.updateRelays(currentRelays)
            lastConnectedRelays = currentRelays
            isInitialConnectionDone = true
        } finally {
            isConnecting = false
        }
    }

    // Main function to fetch both metadata and relays
    suspend fun fetchUserProfile(pubkey: String): Pair<MetadataParser.UserMetadata?, List<String>> {
        if (pubkey.isBlank()) {
            println("❌ NostrRepository: Invalid pubkey")
            return null to emptyList()
        }
        
        println("🔍 NostrRepository: Fetching profile and relays for ${pubkey.take(8)}...")
        
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
                                    println("✅ NostrRepository: Found metadata: ${parsedMetadata.name}")
                                }
                            }
                            msg.contains("\"kind\":10002") && msg.contains(pubkey) -> {
                                println("🎯 NostrRepository: Processing potential relay list message")
                                val parsedRelays = parseRelayListFromMessage(msg, pubkey)
                                if (parsedRelays.isNotEmpty()) {
                                    relays.addAll(parsedRelays)
                                    _userRelaysState.value = parsedRelays
                                    RelayManager.updateUserRelays(parsedRelays)
                                    println("✅ NostrRepository: Found ${parsedRelays.size} user relays: ${parsedRelays.take(3)}...")
                                    
                                    // CRITICAL: Update relay pool with user's preferred relays
                                    updateRelayPoolWithUserRelays(parsedRelays)
                                } else {
                                    println("⚠️ NostrRepository: Relay list parsed but empty - check parsing logic")
                                }
                            }
                        }

                        // REMOVED: Don't cancel early, let the timeout handle it
                        // This prevents the coroutine scope cancellation from affecting relay connections
                    } catch (e: Exception) {
                        println("❌ NostrRepository: Error processing message: ${e.message}")
                    }
                }
            }
        } catch (e: TimeoutCancellationException) {
            println("⏰ NostrRepository: Timeout waiting for profile data from $pubkey")
        }

        // ONLY create basic metadata if we never found real metadata
        if (metadata == null && !hasRealMetadata) {
            metadata = createBasicMetadata()
            _metadataState.value = metadata
            println("📝 NostrRepository: Created basic metadata for user (no real data found)")
        } else if (metadata == null) {
            println("📊 NostrRepository: Using existing real metadata from continuous listener")
            metadata = _metadataState.value
        }

        // Log results
        println("📊 NostrRepository: Fetch completed - metadata: ${metadata?.name ?: "null"}, relays: ${relays.size}")
        
        return metadata to relays
    }

    // FIXED: Better metadata fetching with proper timeout and request issuance
    suspend fun fetchProfileMetadata(pubkey: String): MetadataParser.UserMetadata? {
        if (pubkey.isBlank()) {
            println("❌ NostrRepository: Invalid pubkey for metadata fetch")
            return null
        }
        
        println("🔍 NostrRepository: Fetching metadata for ${pubkey.take(8)}...")
        
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
                                println("✅ NostrRepository: Found metadata for ${pubkey.take(8)}: ${parsedMetadata.name}")
                                cancel() // Stop collection once we found the metadata
                            }
                        }
                    } catch (e: Exception) {
                        println("❌ NostrRepository: Error processing metadata message: ${e.message}")
                    }
                }
            }
        } catch (e: TimeoutCancellationException) {
            println("⏰ NostrRepository: Timeout waiting for metadata from ${pubkey.take(8)}")
        }

        return metadata
    }

    // CRITICAL FIX: Update relay pool with user's preferred relays
    private suspend fun updateRelayPoolWithUserRelays(newRelays: List<String>) {
        if (newRelays.isNotEmpty()) {
            println("🔄 NostrRepository: Updating relay pool with ${newRelays.size} user relays")
            println("📋 NostrRepository: User relays: ${newRelays.take(5)}...")
            try {
                relayPool.updateRelays(newRelays)
                println("✅ NostrRepository: Successfully updated relay pool to user's preferred relays")
                
                // Log the current state for debugging
                println("📊 NostrRepository: RelayManager effective relays: ${RelayManager.effectiveRelays.value.size}")
                println("📊 NostrRepository: RelayPool current relays: ${relayPool.getCurrentRelays().size}")
            } catch (e: Exception) {
                println("❌ NostrRepository: Failed to update relay pool: ${e.message}")
            }
        } else {
            println("⚠️ NostrRepository: No relays to update - empty list")
        }
    }

    private fun requestRelayList(pubkey: String) {
        val subscriptionId = "relays-${pubkey.take(8)}"
        val req = """["REQ","$subscriptionId",{"kinds":[10002],"authors":["$pubkey"]}]"""
        
        println("📤 NostrRepository: Requesting relay list: $req")
        relayPool.broadcast(req)
    }

    private fun parseRelayListFromMessage(msg: String, targetPubkey: String): List<String> {
        return try {
            println("🔍 NostrRepository: Attempting to parse relay list from message: ${msg.take(300)}")
            
            if (msg.trim().startsWith("[")) {
                val arr = JSONArray(msg)
                println("📊 NostrRepository: JSON array length: ${arr.length()}")
                
                if (arr.length() >= 3) {
                    val messageType = arr.optString(0)
                    println("📨 NostrRepository: Message type: $messageType")
                    
                    if (messageType == "EVENT") {
                        val eventObj = arr.getJSONObject(2)
                        val kind = eventObj.optInt("kind", -1)
                        val eventPubkey = eventObj.optString("pubkey", "")
                        val content = eventObj.optString("content", "{}")
                        val tags = eventObj.optJSONArray("tags")
                        
                        println("🎯 NostrRepository: Found EVENT - kind: $kind, pubkey: ${eventPubkey.take(8)}")
                        
                        if (kind == 10002 && eventPubkey == targetPubkey) {
                            println("✅ NostrRepository: Found matching NIP-65 relay list!")
                            
                            // Try parsing from tags first (new NIP-65 format)
                            val relaysFromTags = parseRelaysFromTags(tags)
                            if (relaysFromTags.isNotEmpty()) {
                                println("🏷️ NostrRepository: Found ${relaysFromTags.size} relays from tags: ${relaysFromTags.take(3)}...")
                                return relaysFromTags
                            }
                            
                            // Fallback to content parsing (old format)
                            val parsedRelays = MetadataParser.parseRelayList(content)
                            println("🌐 NostrRepository: Parsed ${parsedRelays.size} relays from content: ${parsedRelays.take(3)}...")
                            return parsedRelays
                        } else {
                            println("❌ NostrRepository: Event doesn't match - kind: $kind (expected 10002), pubkey: ${eventPubkey.take(8)} (expected ${targetPubkey.take(8)})")
                        }
                    } else {
                        println("❌ NostrRepository: Not an EVENT message, type: $messageType")
                    }
                } else {
                    println("❌ NostrRepository: JSON array too short, length: ${arr.length()}")
                }
            } else {
                println("❌ NostrRepository: Message doesn't start with '[' - not a JSON array")
            }
            emptyList()
        } catch (e: Exception) {
            println("❌ NostrRepository: Error parsing relay list: ${e.message}")
            emptyList()
        }
    }

    private fun parseRelaysFromTags(tags: org.json.JSONArray?): List<String> {
        val relays = mutableListOf<String>()
        
        if (tags == null) {
            println("⚠️ NostrRepository: Tags array is null")
            return emptyList()
        }
        
        println("🏷️ NostrRepository: Processing ${tags.length()} tags")
        
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
                println("⚠️ NostrRepository: Error processing tag #$i: ${e.message}")
            }
        }
        
        println("🌐 NostrRepository: Found ${relays.size} relays from tags")
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
            println("❌ NostrRepository: Failed to parse message as JSON: ${e.message}")
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
            println("🎯 NostrRepository: Starting relay listener for ${pubkey.take(8)}")
            incomingMessagesFlow.collect { msg ->
                try {
                    if (msg.contains("\"kind\":10002") && msg.contains(pubkey)) {
                        println("📨 NostrRepository: Relay listener processing message")
                        val relays = parseRelayListFromMessage(msg, pubkey)
                        if (relays.isNotEmpty()) {
                            _userRelaysState.value = relays
                            RelayManager.updateUserRelays(relays)
                            updateRelayPoolWithUserRelays(relays)
                        }
                    }
                } catch (e: Exception) {
                    println("❌ NostrRepository: Error in relay listener: ${e.message}")
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
     * Publish a signed event to all connected relays.
     * @param eventMessage The full EVENT message: ["EVENT", <signedEvent>]
     */
    fun publishEvent(eventMessage: String) {
        val connectedCount = relayPool.getConnectedCount()
        println("📤 NostrRepository: Publishing event to $connectedCount connected relays")
        
        if (connectedCount == 0) {
            println("⚠️ NostrRepository: No relays connected! Event will not be published.")
            println("💡 NostrRepository: Call connectAll() first to establish relay connections")
            return
        }
        
        println("📝 NostrRepository: Event message preview: ${eventMessage.take(200)}...")
        
        // Extract event ID for monitoring responses
        try {
            val eventIdRegex = """"id":"([a-f0-9]{64})"""".toRegex()
            val match = eventIdRegex.find(eventMessage)
            val eventId = match?.groupValues?.get(1)
            
            if (eventId != null) {
                // Monitor relay responses for this event
                scope.launch {
                    var okCount = 0
                    var failCount = 0
                    val timeout = System.currentTimeMillis() + 5000 // 5 second timeout
                    
                    incomingMessagesFlow.collect { msg ->
                        if (System.currentTimeMillis() > timeout) return@collect
                        
                        if (msg.contains(eventId)) {
                            when {
                                msg.contains("\"OK\"") && msg.contains("true") -> {
                                    okCount++
                                    println("✅ Relay accepted event: $eventId")
                                }
                                msg.contains("\"OK\"") && msg.contains("false") -> {
                                    failCount++
                                    println("❌ Relay rejected event: $msg")
                                }
                                msg.contains("\"NOTICE\"") -> {
                                    println("⚠️ Relay notice: $msg")
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            println("⚠️ Could not monitor relay responses: ${e.message}")
        }
        
        relayPool.broadcast(eventMessage)
        println("✅ NostrRepository: Event broadcast complete")
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
        println("📡 NostrRepository: Subscribed to interactions for event $eventId with subscription $subId")
        
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
                    println("❌ NostrRepository: Error parsing interaction message: ${e.message}")
                }
            }
        }
    }
    
    fun closeSubscription(subscriptionId: String) {
        val req = """["CLOSE","$subscriptionId"]"""
        relayPool.broadcast(req)
        println("🔚 NostrRepository: Sent CLOSE for subscription $subscriptionId")
    }
}
