package com.memely.nostr

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicBoolean

class RelayPool(
    private var relays: List<String> = emptyList()
) {
    // Use a persistent coroutine scope that won't be cancelled
    private val connectionScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val clients = CopyOnWriteArrayList<NostrClient>()
    private val _connectedRelaysFlow = MutableStateFlow(0)
    val connectedRelaysFlow: StateFlow<Int> get() = _connectedRelaysFlow
    private val _incomingMessagesFlow = MutableSharedFlow<String>(extraBufferCapacity = 100)
    val incomingMessagesFlow: SharedFlow<String> get() = _incomingMessagesFlow
    private val successful = AtomicInteger(0)
    
    // Use AtomicBoolean for thread-safe connection state tracking
    private val isConnecting = AtomicBoolean(false)

    suspend fun connectAll() = withContext(connectionScope.coroutineContext) {
        if (relays.isEmpty()) {
            return@withContext
        }
        
        // Atomic compare-and-set: Only proceed if we can transition from false -> true
        if (!isConnecting.compareAndSet(false, true)) {
            println("🔗 RelayPool: Connection already in progress, skipping duplicate attempt")
            return@withContext
        }
        
        try {
            // Reset counters for fresh connection attempt
            successful.set(0)
            clients.clear()
            
            val connectionJobs = relays.map { url ->
                connectionScope.launch {
                    try {
                        val client = NostrClient(url)
                        clients += client
                        val ok = client.connect()
                        if (ok) {
                            successful.incrementAndGet()
                            // Do NOT update flow during cascade - only at the end
                            
                            // Start listening for messages
                            launch {
                                try {
                                    for (msg in client.incoming) {
                                        _incomingMessagesFlow.emit(msg)
                                    }
                                } catch (e: Exception) {
                                }
                            }
                        } else {
                            clients.remove(client)
                        }
                    } catch (e: Exception) {
                    }
                }
            }
            
            // Wait for all connection attempts with timeout
            try {
                withTimeout(15000) {
                    connectionJobs.forEach { it.join() }
                }
            } catch (e: TimeoutCancellationException) {
            }
            
            // UPDATE ONCE after all connections complete - single atomic state update
            val finalCount = successful.get()
            _connectedRelaysFlow.value = finalCount
            println("🔗 RelayPool: Connected to $finalCount/${relays.size} relays")
        } finally {
            // Always release the connection lock
            isConnecting.set(false)
        }
    }

    suspend fun updateRelays(newRelays: List<String>) {
        // FIX: Compare content using sorted lists to handle same relays in different order
        if (newRelays.sorted() == relays.sorted()) {
            return
        }
        
        // Close current connections
        clients.forEach { it.close() }
        clients.clear()
        
        // Update relay list
        relays = newRelays
        
        // Reset counter but DON'T update flow yet - only update after new connections establish
        successful.set(0)
        
        // Reconnect with new relays using the persistent scope
        connectAll()
    }

    fun broadcast(message: String) {
        val connectedCount = successful.get()
        if (connectedCount == 0) {
            return
        }
        
        clients.forEach { c -> 
            connectionScope.launch { 
                try {
                    // Add a small delay to ensure websocket is truly ready
                    delay(100)
                    val success = c.publish(message)
                    if (!success) {
                    }
                } catch (e: Exception) {
                }
            } 
        }
    }

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
        
        for ((index, client) in sortedClients.withIndex()) {
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