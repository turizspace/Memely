package com.memely.nostr

import com.memely.util.SecureLog
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.max
import kotlin.math.min

/**
 * ConnectionStatusMonitor - Tracks relay connection health and network conditions
 * 
 * Provides:
 * - Real-time connection status
 * - Network quality metrics
 * - Automatic reconnection for poor/lost connections
 * - Bandwidth adaptation
 */

data class ConnectionMetrics(
    val connectedRelays: Int,
    val totalRelays: Int,
    val connectionRate: Float,          // 0-1, percentage of connected relays
    val averageLatency: Long,           // milliseconds
    val lastSuccessfulPost: Long,       // timestamp
    val isHealthy: Boolean,             // True if connection rate >= 50%
    val networkQuality: NetworkQuality,
    val lastUpdate: Long = System.currentTimeMillis()
)

enum class NetworkQuality {
    EXCELLENT,    // 80%+ connection rate, <100ms latency
    GOOD,         // 60-80% connection rate, 100-300ms latency
    MODERATE,     // 40-60% connection rate, 300-1000ms latency
    POOR,         // 20-40% connection rate, >1000ms latency
    CRITICAL,     // <20% connection rate or no connections
    OFFLINE       // No connections at all
}

object ConnectionStatusMonitor {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    private val _metricsFlow = MutableStateFlow<ConnectionMetrics?>(null)
    val metricsFlow: StateFlow<ConnectionMetrics?> = _metricsFlow
    
    private val _networkQualityFlow = MutableStateFlow(NetworkQuality.OFFLINE)
    val networkQualityFlow: StateFlow<NetworkQuality> = _networkQualityFlow
    
    private val _isReconnectingFlow = MutableStateFlow(false)
    val isReconnectingFlow: StateFlow<Boolean> = _isReconnectingFlow
    
    // Track relay latencies for quality assessment
    private val relayLatencies = mutableMapOf<String, Long>()
    private val relaySuccessRates = mutableMapOf<String, Float>()
    
    private var lastSuccessfulPost = 0L
    private var monitoringJob: Job? = null
    private var isMonitoring = false

    /**
     * Start monitoring connection status
     */
    fun startMonitoring() {
        if (isMonitoring) return
        
        isMonitoring = true
        monitoringJob = scope.launch {
            while (isActive) {
                updateMetrics()
                delay(5000)  // Update every 5 seconds
            }
        }
        SecureLog.d("ConnectionStatusMonitor: Started monitoring")
    }

    /**
     * Stop monitoring
     */
    fun stopMonitoring() {
        isMonitoring = false
        monitoringJob?.cancel()
        SecureLog.d("ConnectionStatusMonitor: Stopped monitoring")
    }

    /**
     * Record a successful post
     */
    fun recordSuccessfulPost() {
        lastSuccessfulPost = System.currentTimeMillis()
    }

    /**
     * Record relay latency
     */
    fun recordRelayLatency(relayUrl: String, latencyMs: Long) {
        relayLatencies[relayUrl] = latencyMs
    }

    /**
     * Record relay success/failure
     */
    fun recordRelayResult(relayUrl: String, success: Boolean) {
        val currentRate = relaySuccessRates[relayUrl] ?: 0.5f
        val newRate = if (success) {
            min(currentRate + 0.1f, 1f)
        } else {
            max(currentRate - 0.2f, 0f)
        }
        relaySuccessRates[relayUrl] = newRate
    }

    /**
     * Update and emit metrics
     */
    private suspend fun updateMetrics() {
        try {
            val connectedCount = NostrRepository.relayPool.getConnectedCount()
            val totalRelays = NostrRepository.relayPool.getCurrentRelays().size
            
            val connectionRate = if (totalRelays > 0) connectedCount.toFloat() / totalRelays else 0f
            val avgLatency = if (relayLatencies.isNotEmpty()) {
                relayLatencies.values.average().toLong()
            } else {
                0L
            }

            val networkQuality = determineNetworkQuality(connectionRate, avgLatency, connectedCount)
            val isHealthy = connectionRate >= 0.5f

            val metrics = ConnectionMetrics(
                connectedRelays = connectedCount,
                totalRelays = totalRelays,
                connectionRate = connectionRate,
                averageLatency = avgLatency,
                lastSuccessfulPost = lastSuccessfulPost,
                isHealthy = isHealthy,
                networkQuality = networkQuality
            )

            _metricsFlow.value = metrics
            _networkQualityFlow.value = networkQuality

            // Handle poor connections automatically
            when (networkQuality) {
                NetworkQuality.OFFLINE -> handleOfflineState()
                NetworkQuality.CRITICAL -> handleCriticalConnection()
                NetworkQuality.POOR -> handlePoorConnection()
                else -> resetReconnectionState()
            }

            // Log status
            when (networkQuality) {
                NetworkQuality.EXCELLENT, NetworkQuality.GOOD -> {
                    // Don't spam logs for good connections
                }
                else -> {
                    SecureLog.d("ConnectionStatusMonitor: $networkQuality - Connected: $connectedCount/$totalRelays, Latency: ${avgLatency}ms")
                }
            }

        } catch (e: Exception) {
            SecureLog.e("ConnectionStatusMonitor: Error updating metrics: ${e.message}")
        }
    }

    /**
     * Determine network quality based on metrics
     */
    private fun determineNetworkQuality(
        connectionRate: Float,
        avgLatency: Long,
        connectedCount: Int
    ): NetworkQuality {
        return when {
            connectedCount == 0 -> NetworkQuality.OFFLINE
            connectionRate < 0.2f || avgLatency > 10000L -> NetworkQuality.CRITICAL
            connectionRate < 0.4f || avgLatency > 1000L -> NetworkQuality.POOR
            connectionRate < 0.6f || avgLatency > 300L -> NetworkQuality.MODERATE
            connectionRate < 0.8f || avgLatency > 100L -> NetworkQuality.GOOD
            else -> NetworkQuality.EXCELLENT
        }
    }

    /**
     * Handle offline state - attempt reconnection
     */
    private suspend fun handleOfflineState() {
        if (_isReconnectingFlow.value) return
        
        _isReconnectingFlow.value = true
        SecureLog.w("ConnectionStatusMonitor: Network offline, attempting reconnection")
        
        delay(5000)  // Wait before reconnecting
        try {
            NostrRepository.connectAll()
            SecureLog.d("ConnectionStatusMonitor: Reconnection attempt completed")
        } catch (e: Exception) {
            SecureLog.e("ConnectionStatusMonitor: Reconnection failed: ${e.message}")
        } finally {
            _isReconnectingFlow.value = false
        }
    }

    /**
     * Handle critical connection (very few relays)
     */
    private suspend fun handleCriticalConnection() {
        if (_isReconnectingFlow.value) return
        
        SecureLog.w("ConnectionStatusMonitor: Critical connection state")
        
        // Increase timeout for message processing
        delay(2000)  // Give time for messages to queue
    }

    /**
     * Handle poor connection
     */
    private suspend fun handlePoorConnection() {
        // Reduce broadcast frequency in PostingManager
        SecureLog.w("ConnectionStatusMonitor: Poor connection detected")
    }

    /**
     * Reset reconnection state
     */
    private fun resetReconnectionState() {
        _isReconnectingFlow.value = false
    }

    /**
     * Get current network quality
     */
    fun getCurrentNetworkQuality(): NetworkQuality {
        return _networkQualityFlow.value
    }

    /**
     * Get current metrics
     */
    fun getCurrentMetrics(): ConnectionMetrics? {
        return _metricsFlow.value
    }

    /**
     * Check if connection is healthy enough for posting
     */
    fun isConnectionHealthy(): Boolean {
        val metrics = _metricsFlow.value ?: return false
        return metrics.isHealthy && metrics.connectedRelays > 0
    }

    /**
     * Get recommended retry strategy based on network quality
     */
    fun getRetryStrategy(): RetryStrategy {
        return when (getCurrentNetworkQuality()) {
            NetworkQuality.EXCELLENT -> RetryStrategy(maxRetries = 3, initialDelayMs = 200)
            NetworkQuality.GOOD -> RetryStrategy(maxRetries = 4, initialDelayMs = 500)
            NetworkQuality.MODERATE -> RetryStrategy(maxRetries = 5, initialDelayMs = 1000)
            NetworkQuality.POOR -> RetryStrategy(maxRetries = 6, initialDelayMs = 2000)
            NetworkQuality.CRITICAL -> RetryStrategy(maxRetries = 8, initialDelayMs = 3000)
            NetworkQuality.OFFLINE -> RetryStrategy(maxRetries = 10, initialDelayMs = 5000, shouldQueue = true)
        }
    }

    data class RetryStrategy(
        val maxRetries: Int,
        val initialDelayMs: Long,
        val shouldQueue: Boolean = false
    )

    /**
     * Cleanup
     */
    fun cleanup() {
        stopMonitoring()
        scope.cancel()
    }
}
