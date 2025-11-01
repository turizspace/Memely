package com.memely.nostr

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

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

    suspend fun connectAll() = withContext(connectionScope.coroutineContext) {
        if (relays.isEmpty()) {
            println("⚠️ RelayPool: No relays to connect to")
            return@withContext
        }
        
        println("🔗 RelayPool: Attempting to connect to ${relays.size} relays...")
        
        // Reset counters
        successful.set(0)
        _connectedRelaysFlow.value = 0
        clients.clear()
        
        val connectionJobs = relays.map { url ->
            connectionScope.launch {
                try {
                    val client = NostrClient(url)
                    clients += client
                    val ok = client.connect()
                    if (ok) {
                        val count = successful.incrementAndGet()
                        _connectedRelaysFlow.value = count
                        println("✅ Connected to relay: $url ($count/${relays.size})")
                        
                        // Start listening for messages
                        launch {
                            try {
                                for (msg in client.incoming) {
                                    _incomingMessagesFlow.emit(msg)
                                }
                            } catch (e: Exception) {
                                println("❌ Error in message stream for $url: ${e.message}")
                            }
                        }
                    } else {
                        println("❌ Failed to connect: $url")
                        clients.remove(client)
                    }
                } catch (e: Exception) {
                    println("❌ Exception connecting to $url: ${e.message}")
                }
            }
        }
        
        // Wait for all connection attempts with timeout
        try {
            withTimeout(15000) {
                connectionJobs.forEach { it.join() }
            }
            println("🎯 RelayPool: Connection attempts completed. Successfully connected to ${successful.get()}/${relays.size} relays")
        } catch (e: TimeoutCancellationException) {
            println("⏰ RelayPool: Some connection attempts timed out. Connected to ${successful.get()}/${relays.size} relays so far")
        }
    }

    suspend fun updateRelays(newRelays: List<String>) {
        // FIX: Compare content using sorted lists to handle same relays in different order
        if (newRelays.sorted() == relays.sorted()) {
            println("🔄 RelayPool: Relays unchanged (same relays), skipping update")
            return
        }
        
        println("🔄 RelayPool: Updating relays from ${relays.size} to ${newRelays.size} relays")
        println("📋 RelayPool: Old relays: ${relays.take(3)}...")
        println("📋 RelayPool: New relays: ${newRelays.take(3)}...")
        
        // Close current connections
        val previousCount = clients.size
        clients.forEach { it.close() }
        clients.clear()
        successful.set(0)
        _connectedRelaysFlow.value = 0
        
        println("🔌 RelayPool: Closed $previousCount previous connections")
        
        // Update relay list
        relays = newRelays
        
        // Reconnect with new relays using the persistent scope
        connectAll()
    }

    fun broadcast(message: String) {
        val connectedCount = successful.get()
        if (connectedCount == 0) {
            println("⚠️ RelayPool: No connected clients available to broadcast message")
            return
        }
        
        println("📡 RelayPool: Broadcasting to $connectedCount connected relays")
        
        clients.forEach { c -> 
            connectionScope.launch { 
                try {
                    // Add a small delay to ensure websocket is truly ready
                    delay(100)
                    val success = c.publish(message)
                    if (!success) {
                        println("⚠️ RelayPool: Publish returned false for relay: ${c.url}")
                    }
                } catch (e: Exception) {
                    println("❌ RelayPool: Failed to broadcast to relay: ${e.message}")
                }
            } 
        }
    }

    /**
     * Broadcast with retry logic - ensures message gets to all connected relays
     */
    suspend fun broadcastWithRetry(message: String, maxRetries: Int = 3) {
        val connectedCount = successful.get()
        if (connectedCount == 0) {
            println("⚠️ RelayPool: No connected clients available for broadcast with retry")
            return
        }
        
        println("📡 RelayPool: Broadcasting with retry to $connectedCount relays (max $maxRetries attempts)")
        
        var attempt = 0
        var successCount = 0
        var failedRelays = mutableListOf<NostrClient>()
        
        while (attempt < maxRetries && failedRelays.size < clients.size) {
            if (attempt > 0) {
                println("🔄 RelayPool: Broadcast retry attempt ${attempt + 1}/$maxRetries")
                delay(500)  // Wait before retry
            }
            
            val toTry = if (attempt == 0) clients else failedRelays
            failedRelays.clear()
            
            for (client in toTry) {
                try {
                    val success = client.publish(message)
                    if (success) {
                        successCount++
                        println("✅ RelayPool: Event sent to relay: ${client.url}")
                    } else {
                        println("⚠️ RelayPool: Publish failed for relay: ${client.url}, will retry")
                        failedRelays.add(client)
                    }
                } catch (e: Exception) {
                    println("❌ RelayPool: Exception broadcasting to relay: ${e.message}")
                    failedRelays.add(client)
                }
            }
            
            if (failedRelays.isEmpty()) break
            attempt++
        }
        
        println("📊 RelayPool: Broadcast complete - $successCount/${clients.size} relays accepted")
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