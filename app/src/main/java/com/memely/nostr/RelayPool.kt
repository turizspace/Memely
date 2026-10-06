package com.memely.nostr

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

data class RelayIncomingMessage(val relayUrl: String, val raw: String)

class RelayPool(
    private var relays: List<String> = emptyList()
) {
    // Use a persistent coroutine scope that won't be cancelled
    private val connectionScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val clients = CopyOnWriteArrayList<NostrClient>()
    private val relayMessageQueue = Channel<RelayIncomingMessage>(Channel.UNLIMITED)
    private val rawMessageQueue = Channel<String>(Channel.UNLIMITED)
    private val _connectedRelaysFlow = MutableStateFlow(0)
    val connectedRelaysFlow: StateFlow<Int> get() = _connectedRelaysFlow
    private val _connectionGenerationFlow = MutableStateFlow(0L)
    val connectionGenerationFlow: StateFlow<Long> get() = _connectionGenerationFlow
    private val _incomingMessagesFlow = MutableSharedFlow<String>(extraBufferCapacity = 100)
    val incomingMessagesFlow: SharedFlow<String> get() = _incomingMessagesFlow
    private val _relayMessagesFlow = MutableSharedFlow<RelayIncomingMessage>(extraBufferCapacity = 1000)
    val relayMessagesFlow: SharedFlow<RelayIncomingMessage> get() = _relayMessagesFlow
    private val successful = AtomicInteger(0)
    private val connectedRelayUrls = ConcurrentHashMap.newKeySet<String>()
    private val activeSubscriptions = ConcurrentHashMap<String, String>()
    private val connectionMutex = Mutex()

    init {
        connectionScope.launch {
            for (message in relayMessageQueue) _relayMessagesFlow.emit(message)
        }
        connectionScope.launch {
            for (message in rawMessageQueue) _incomingMessagesFlow.emit(message)
        }
    }

    suspend fun connectAll() = connectionMutex.withLock {
        if (connectedRelayUrls.isNotEmpty()) {
            println("🔗 RelayPool: Already connected to ${connectedRelayUrls.size} relays")
            return@withLock
        }
        connectAllLocked()
    }

    private suspend fun connectAllLocked() {
        if (relays.isEmpty()) {
            successful.set(0)
            connectedRelayUrls.clear()
            _connectedRelaysFlow.value = 0
            _connectionGenerationFlow.value += 1
            return
        }

        closeClients()
        successful.set(0)
        connectedRelayUrls.clear()
        _connectedRelaysFlow.value = 0
        val targetRelays = relays.toList()
        val connectionJobs = targetRelays.map { url ->
            connectionScope.launch {
                val client = NostrClient(url)
                clients += client
                try {
                    if (client.connect()) {
                        connectedRelayUrls += url
                        successful.incrementAndGet()
                        activeSubscriptions.values.forEach { request ->
                            if (!client.publishNow(request)) {
                                println("⚠️ RelayPool: Failed to restore subscription on $url")
                            }
                        }
                        connectionScope.launch {
                            for (raw in client.incoming) {
                                relayMessageQueue.trySend(RelayIncomingMessage(url, raw))
                                rawMessageQueue.trySend(raw)
                            }
                        }
                    } else {
                        clients.remove(client)
                        client.close()
                    }
                } catch (e: Exception) {
                    clients.remove(client)
                    connectedRelayUrls.remove(url)
                    client.close()
                    if (e is CancellationException) throw e
                }
            }
        }

        try {
            withTimeout(15_000) { connectionJobs.joinAll() }
        } catch (_: TimeoutCancellationException) {
            connectionJobs.forEach { it.cancel() }
            connectionJobs.joinAll()
        }

        val finalCount = connectedRelayUrls.size
        successful.set(finalCount)
        _connectedRelaysFlow.value = finalCount
        _connectionGenerationFlow.value += 1
        println("🔗 RelayPool: Connected to $finalCount/${targetRelays.size} relays")
    }

    suspend fun updateRelays(newRelays: List<String>) = connectionMutex.withLock {
        val distinctRelays = newRelays.distinct()
        if (distinctRelays.sorted() == relays.sorted()) return@withLock
        closeClients()
        relays = distinctRelays
        successful.set(0)
        connectedRelayUrls.clear()
        _connectedRelaysFlow.value = 0
        connectAllLocked()
    }

    private fun closeClients() {
        clients.forEach { client ->
            client.close()
            client.incoming.cancel()
        }
        clients.clear()
    }

    fun broadcast(message: String) {
        if (successful.get() == 0) return

        clients.toList()
            .filter { it.url in connectedRelayUrls }
            .forEach { client ->
                if (!client.publishNow(message)) {
                    println("⚠️ RelayPool: Failed to send message to ${client.url}")
                }
        }
    }

    fun subscribe(subscriptionId: String, request: String) {
        activeSubscriptions[subscriptionId] = request
        broadcast(request)
    }

    fun closeSubscription(subscriptionId: String) {
        activeSubscriptions.remove(subscriptionId)
        broadcast("[\"CLOSE\",\"$subscriptionId\"]")
    }

    fun getConnectedRelayUrls(): Set<String> = connectedRelayUrls.toSet()

    /**
     * Broadcast with retry logic - ensures message gets to all connected relays
     * Optimized for poor connections with adaptive delays
     */
    suspend fun broadcastWithRetry(message: String, maxRetries: Int = 3) {
        val connectedCount = successful.get()
        if (connectedCount == 0) {
            return
        }
        
        var attempt = 0
        var failedRelays = mutableListOf<NostrClient>()
        
        while (attempt < maxRetries && failedRelays.size < clients.size) {
            if (attempt > 0) {
                // Adaptive delay: longer waits for poor connections
                val delayMs = when {
                    connectedCount <= 2 -> 1000L  // Very slow for poor connections
                    connectedCount <= 4 -> 750L   // Slow for moderate connections
                    else -> 500L                   // Normal delay for good connections
                }
                delay(delayMs)
            }
            
            val toTry = if (attempt == 0) clients.toList() else failedRelays.toList()
            failedRelays.clear()
            
            for (client in toTry) {
                try {
                    // Add per-relay delay for low bandwidth - avoid flooding
                    delay(50)
                    val success = client.publish(message)
                    if (!success) {
                        failedRelays.add(client)
                    }
                } catch (e: Exception) {
                    failedRelays.add(client)
                }
            }
            
            if (failedRelays.isEmpty()) break
            attempt++
        }
    }

    /**
     * Broadcast a message with per-relay response tracking
     * Returns map of relay URL to success status
     */
    suspend fun broadcastWithTracking(message: String): Map<String, Boolean> {
        val connectedCount = successful.get()
        if (connectedCount == 0) {
            return emptyMap()
        }

        val results = mutableMapOf<String, Boolean>()
        
        for (client in clients) {
            try {
                delay(50)  // Small delay for low bandwidth
                val success = client.publish(message)
                results[client.url] = success
            } catch (e: Exception) {
                results[client.url] = false
            }
        }
        
        return results
    }

    /**
     * Broadcast with connection health awareness
     * Prioritizes relays with better health scores
     */
    suspend fun broadcastAdaptive(message: String, healthScores: Map<String, Float> = emptyMap()) {
        val connectedCount = successful.get()
        if (connectedCount == 0) {
            return
        }

        // Sort clients by health score (highest first)
        val sortedClients = clients.toList().sortedByDescending { healthScores[it.url] ?: 0.5f }
        
        for (client in sortedClients) {
            try {
                // Progressive delay: faster for healthy relays, slower for unhealthy
                val healthScore = healthScores[client.url] ?: 0.5f
                val delayMs = (50 * (1.0 - healthScore)).toLong()
                delay(delayMs)
                
                client.publish(message)
            } catch (e: Exception) {
                // Continue with next relay
            }
        }
    }

    fun fetchUserMetadata(pubkey: String) {
        val subscriptionId = "meta-${System.currentTimeMillis()}"
        val req = """["REQ","$subscriptionId",{"kinds":[0],"authors":["$pubkey"]}]"""
        broadcast(req)
    }
    
    fun getCurrentRelays(): List<String> {
        return relays
    }
    
    fun getConnectedCount(): Int {
        return successful.get()
    }
    
    fun close() {
        connectionScope.cancel()
        clients.forEach { it.close() }
    }
}