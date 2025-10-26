package com.memely.nostr

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * Tracks the acceptance/rejection status of events across all connected relays.
 * 
 * This provides detailed feedback on:
 * - Which relays accepted a specific event
 * - Which relays rejected it and why
 * - Relay acceptance rates over time
 * - Real-time status updates for publishing operations
 * 
 * Professional use cases:
 * - Show users which relays successfully received their note
 * - Retry failures for relays that rejected
 * - Monitor relay reliability metrics
 * - Provide feedback on network quality
 */
data class RelayEventStatus(
    val relayUrl: String,
    val eventId: String,
    val status: EventStatus,
    val message: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

enum class EventStatus {
    PENDING,      // Awaiting response from relay
    ACCEPTED,     // Relay sent OK: true response
    REJECTED,     // Relay sent OK: false response
    TIMEOUT,      // No response within timeout
    CONNECTION_ERROR  // Relay not connected
}

data class PublishResult(
    val eventId: String,
    val acceptedRelays: List<String>,
    val rejectedRelays: List<String>,
    val timedOutRelays: List<String>,
    val totalRelays: Int,
    val acceptanceRate: Float,
    val timestamp: Long = System.currentTimeMillis(),
    val details: Map<String, RelayEventStatus> = emptyMap()
) {
    fun isSuccessful(): Boolean = acceptanceRate >= 0.5f  // At least 50% of relays accepted
    fun allAccepted(): Boolean = acceptedRelays.size == totalRelays
    fun none(): Boolean = acceptedRelays.isEmpty()
}

object RelayEventTracker {
    // Store the latest status for each event at each relay
    private val eventStatuses = ConcurrentHashMap<String, MutableMap<String, RelayEventStatus>>()
    
    // Track completed publishes for history
    private val _publishHistory = MutableStateFlow<List<PublishResult>>(emptyList())
    val publishHistory: StateFlow<List<PublishResult>> = _publishHistory.asStateFlow()
    
    // Track current publishing operation
    private val _currentPublish = MutableStateFlow<PublishResult?>(null)
    val currentPublish: StateFlow<PublishResult?> = _currentPublish.asStateFlow()

    /**
     * Initialize tracking for a new event across all known relays
     */
    fun initializeEventTracking(eventId: String, relayUrls: List<String>) {
        val statusMap = ConcurrentHashMap<String, RelayEventStatus>()
        relayUrls.forEach { relay ->
            statusMap[relay] = RelayEventStatus(
                relayUrl = relay,
                eventId = eventId,
                status = EventStatus.PENDING
            )
        }
        eventStatuses[eventId] = statusMap
        
        println("📊 RelayEventTracker: Initialized tracking for event $eventId across ${relayUrls.size} relays")
    }

    /**
     * Record that a relay accepted the event (OK: true)
     */
    fun recordAcceptance(eventId: String, relayUrl: String) {
        val statusMap = eventStatuses.getOrPut(eventId) { ConcurrentHashMap() }
        statusMap[relayUrl] = RelayEventStatus(
            relayUrl = relayUrl,
            eventId = eventId,
            status = EventStatus.ACCEPTED,
            message = "Successfully accepted"
        )
        println("✅ RelayEventTracker: Relay $relayUrl ACCEPTED event $eventId")
        updateCurrentPublishState(eventId)
    }

    /**
     * Record that a relay rejected the event (OK: false)
     */
    fun recordRejection(eventId: String, relayUrl: String, reason: String = "") {
        val statusMap = eventStatuses.getOrPut(eventId) { ConcurrentHashMap() }
        statusMap[relayUrl] = RelayEventStatus(
            relayUrl = relayUrl,
            eventId = eventId,
            status = EventStatus.REJECTED,
            message = reason.takeIf { it.isNotBlank() } ?: "Rejected by relay"
        )
        println("❌ RelayEventTracker: Relay $relayUrl REJECTED event $eventId - $reason")
        updateCurrentPublishState(eventId)
    }

    /**
     * Record that relay didn't respond in time
     */
    fun recordTimeout(eventId: String, relayUrl: String) {
        val statusMap = eventStatuses.getOrPut(eventId) { ConcurrentHashMap() }
        statusMap[relayUrl] = RelayEventStatus(
            relayUrl = relayUrl,
            eventId = eventId,
            status = EventStatus.TIMEOUT,
            message = "No response from relay"
        )
        println("⏰ RelayEventTracker: Relay $relayUrl TIMEOUT for event $eventId")
        updateCurrentPublishState(eventId)
    }

    /**
     * Record that relay wasn't connected when publishing
     */
    fun recordConnectionError(eventId: String, relayUrl: String) {
        val statusMap = eventStatuses.getOrPut(eventId) { ConcurrentHashMap() }
        statusMap[relayUrl] = RelayEventStatus(
            relayUrl = relayUrl,
            eventId = eventId,
            status = EventStatus.CONNECTION_ERROR,
            message = "Relay not connected"
        )
        println("🔌 RelayEventTracker: Relay $relayUrl NOT CONNECTED for event $eventId")
        updateCurrentPublishState(eventId)
    }

    /**
     * Get current status of all relays for an event
     */
    fun getEventStatus(eventId: String): Map<String, RelayEventStatus> {
        return eventStatuses[eventId] ?: emptyMap()
    }

    /**
     * Get detailed summary of event publication results
     */
    fun getPublishResult(eventId: String): PublishResult {
        val statuses = eventStatuses[eventId] ?: emptyMap()
        
        val accepted = statuses.values.filter { it.status == EventStatus.ACCEPTED }.map { it.relayUrl }
        val rejected = statuses.values.filter { it.status == EventStatus.REJECTED }.map { it.relayUrl }
        val timedOut = statuses.values.filter { it.status == EventStatus.TIMEOUT }.map { it.relayUrl }
        
        val total = statuses.size
        val acceptanceRate = if (total > 0) accepted.size.toFloat() / total else 0f
        
        return PublishResult(
            eventId = eventId,
            acceptedRelays = accepted,
            rejectedRelays = rejected,
            timedOutRelays = timedOut,
            totalRelays = total,
            acceptanceRate = acceptanceRate,
            details = statuses
        )
    }

    /**
     * Check if publishing is complete (all relays responded or timed out)
     */
    fun isPublishComplete(eventId: String): Boolean {
        val statuses = eventStatuses[eventId] ?: return false
        return statuses.values.all { it.status != EventStatus.PENDING }
    }

    /**
     * Get relays that are still waiting for responses
     */
    fun getPendingRelays(eventId: String): List<String> {
        val statuses = eventStatuses[eventId] ?: return emptyList()
        return statuses.values
            .filter { it.status == EventStatus.PENDING }
            .map { it.relayUrl }
    }

    /**
     * Clear old event tracking data (keep last 100 for history)
     */
    fun cleanup() {
        if (eventStatuses.size > 100) {
            val sortedKeys = eventStatuses.keys.sorted()
            val toRemove = sortedKeys.dropLast(100)
            toRemove.forEach { eventStatuses.remove(it) }
            println("🧹 RelayEventTracker: Cleaned up old event tracking data")
        }
    }

    /**
     * Get relay performance metrics
     */
    fun getRelayMetrics(relayUrl: String): RelayMetrics {
        val allEvents = eventStatuses.values.flatMap { it.values }
        val relayEvents = allEvents.filter { it.relayUrl == relayUrl }
        
        val accepted = relayEvents.count { it.status == EventStatus.ACCEPTED }
        val rejected = relayEvents.count { it.status == EventStatus.REJECTED }
        val timedOut = relayEvents.count { it.status == EventStatus.TIMEOUT }
        val total = relayEvents.size
        
        val reliability = if (total > 0) accepted.toFloat() / total else 0f
        
        return RelayMetrics(
            relayUrl = relayUrl,
            totalEvents = total,
            acceptedCount = accepted,
            rejectedCount = rejected,
            timeoutCount = timedOut,
            reliabilityScore = reliability
        )
    }

    /**
     * Get all relay metrics
     */
    fun getAllRelayMetrics(): List<RelayMetrics> {
        val allEvents = eventStatuses.values.flatMap { it.values }
        return allEvents.groupBy { it.relayUrl }
            .map { (relay, events) ->
                val accepted = events.count { it.status == EventStatus.ACCEPTED }
                val rejected = events.count { it.status == EventStatus.REJECTED }
                val timedOut = events.count { it.status == EventStatus.TIMEOUT }
                val total = events.size
                val reliability = if (total > 0) accepted.toFloat() / total else 0f
                
                RelayMetrics(
                    relayUrl = relay,
                    totalEvents = total,
                    acceptedCount = accepted,
                    rejectedCount = rejected,
                    timeoutCount = timedOut,
                    reliabilityScore = reliability
                )
            }
            .sortedByDescending { it.reliabilityScore }
    }

    /**
     * Update the current publish state flow
     */
    private fun updateCurrentPublishState(eventId: String) {
        _currentPublish.value = getPublishResult(eventId)
    }

    /**
     * Complete a publish operation and add to history
     */
    fun completePublish(eventId: String) {
        val result = getPublishResult(eventId)
        val history = _publishHistory.value.toMutableList()
        history.add(0, result)  // Add to beginning for latest first
        if (history.size > 50) {
            _publishHistory.value = history.take(50)  // Keep last 50
        } else {
            _publishHistory.value = history
        }
        _currentPublish.value = null
        println("✅ RelayEventTracker: Completed tracking for event $eventId - ${result.acceptedRelays.size}/${result.totalRelays} relays accepted")
    }

    /**
     * Clear all tracking data
     */
    fun clear() {
        eventStatuses.clear()
        _publishHistory.value = emptyList()
        _currentPublish.value = null
        println("🗑️ RelayEventTracker: Cleared all tracking data")
    }
}

data class RelayMetrics(
    val relayUrl: String,
    val totalEvents: Int,
    val acceptedCount: Int,
    val rejectedCount: Int,
    val timeoutCount: Int,
    val reliabilityScore: Float  // 0-1, where 1 is perfect
)
