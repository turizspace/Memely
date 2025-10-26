package com.memely.nostr

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Professional Relay Connection Manager
 * 
 * Responsibilities:
 * - Maintain persistent relay connections independent of screen lifecycle
 * - Keep relays connected during meme editing operations
 * - Survive configuration changes and back-navigation
 * - Provide connection state monitoring
 * - Handle graceful reconnection on failure
 * 
 * Architecture:
 * - Uses a persistent CoroutineScope (not tied to any composable)
 * - Ref-counted to track active users (MemeEditorScreen, etc)
 * - Auto-reconnects if connections drop
 * - Separate from NostrRepository which handles events
 */
object RelayConnectionManager {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val isInitialized = AtomicBoolean(false)
    private val activeReferences = AtomicInteger(0)
    private var reconnectJob: Job? = null
    
    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState = _connectionState.asStateFlow()
    
    enum class ConnectionState {
        Disconnected,
        Connecting,
        Connected,
        Reconnecting,
        Error
    }
    
    data class ConnectionInfo(
        val isConnected: Boolean,
        val connectedRelayCount: Int,
        val totalRelayCount: Int,
        val connectionTimestamp: Long = System.currentTimeMillis()
    )
    
    /**
     * Initialize relay connections.
     * Reference-counted - call release() when done.
     */
    fun initialize() {
        if (activeReferences.incrementAndGet() == 1) {
            scope.launch {
                doInitialize()
            }
        }
    }

    /**
     * Release a reference to the relay connections.
     * When all references are released, may disconnect after timeout.
     */
    fun release() {
        activeReferences.decrementAndGet()
    }

    /**
     * Get the number of active references
     */
    fun getActiveReferences(): Int = activeReferences.get()

    /**
     * Force keep connection alive (e.g., during critical operations)
     */
    fun acquire(): AutoCloseable {
        initialize()
        return AutoCloseable { release() }
    }

    /**
     * Get current connection info
     */
    suspend fun getConnectionInfo(): ConnectionInfo = withContext(Dispatchers.IO) {
        val relayPool = NostrRepository.relayPool
        val connectedCount = relayPool.getConnectedCount()
        val totalCount = relayPool.getCurrentRelays().size
        
        ConnectionInfo(
            isConnected = connectedCount > 0,
            connectedRelayCount = connectedCount,
            totalRelayCount = totalCount
        )
    }

    /**
     * Ensure relays are connected, reconnecting if necessary
     */
    suspend fun ensureConnected() {
        withContext(Dispatchers.IO) {
            try {
                _connectionState.value = ConnectionState.Connecting
                
                val info = getConnectionInfo()
                if (!info.isConnected) {
                    println("🔄 RelayConnectionManager: Reconnecting to relays...")
                    _connectionState.value = ConnectionState.Reconnecting
                    NostrRepository.connectAll()
                }
                
                _connectionState.value = ConnectionState.Connected
                println("✅ RelayConnectionManager: Relays connected (${info.connectedRelayCount}/${info.totalRelayCount})")
                
            } catch (e: Exception) {
                _connectionState.value = ConnectionState.Error
                println("❌ RelayConnectionManager: Connection error - ${e.message}")
                throw e
            }
        }
    }

    /**
     * Verify connection is healthy for publishing
     */
    suspend fun verifyConnectionHealth(): Boolean = withContext(Dispatchers.IO) {
        try {
            val info = getConnectionInfo()
            val isHealthy = info.connectedRelayCount >= info.totalRelayCount * 0.5f  // At least 50% connected
            
            if (!isHealthy) {
                println("⚠️ RelayConnectionManager: Connection health low - ${info.connectedRelayCount}/${info.totalRelayCount}")
                // Attempt reconnect
                ensureConnected()
                val recheckInfo = getConnectionInfo()
                return@withContext recheckInfo.connectedRelayCount > 0
            }
            
            true
        } catch (e: Exception) {
            println("❌ RelayConnectionManager: Health check failed - ${e.message}")
            false
        }
    }

    /**
     * Force disconnect (cleanup)
     */
    fun disconnect() {
        reconnectJob?.cancel()
        activeReferences.set(0)
        _connectionState.value = ConnectionState.Disconnected
        println("🔌 RelayConnectionManager: Disconnected")
    }

    /**
     * Internal initialization
     */
    private suspend fun doInitialize() {
        if (isInitialized.getAndSet(true)) {
            return  // Already initialized
        }
        
        try {
            println("🚀 RelayConnectionManager: Initializing persistent relay connections...")
            _connectionState.value = ConnectionState.Connecting
            
            NostrRepository.connectAll()
            
            _connectionState.value = ConnectionState.Connected
            println("✅ RelayConnectionManager: Persistent connections initialized")
            
            // Start monitoring for disconnections
            startConnectionMonitoring()
            
        } catch (e: Exception) {
            _connectionState.value = ConnectionState.Error
            println("❌ RelayConnectionManager: Initialization failed - ${e.message}")
        }
    }

    /**
     * Monitor connection and auto-reconnect if lost
     */
    private fun startConnectionMonitoring() {
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            while (isActive) {
                delay(30000)  // Check every 30 seconds
                
                val info = getConnectionInfo()
                if (info.totalRelayCount > 0 && info.connectedRelayCount == 0) {
                    println("⚠️ RelayConnectionManager: Connection lost, auto-reconnecting...")
                    _connectionState.value = ConnectionState.Reconnecting
                    try {
                        NostrRepository.connectAll()
                        _connectionState.value = ConnectionState.Connected
                        println("✅ RelayConnectionManager: Auto-reconnected")
                    } catch (e: Exception) {
                        _connectionState.value = ConnectionState.Error
                        println("❌ RelayConnectionManager: Auto-reconnect failed - ${e.message}")
                    }
                }
            }
        }
    }
}

/**
 * Usage example in Composable:
 * 
 * LaunchedEffect(Unit) {
 *     RelayConnectionManager.initialize()
 *     DisposableEffect(Unit) {
 *         onDispose {
 *             RelayConnectionManager.release()
 *         }
 *     }
 * }
 * 
 * Or for scoped usage:
 * 
 * val connection = RelayConnectionManager.acquire()
 * try {
 *     // Do critical operation
 * } finally {
 *     connection.close()
 * }
 */
