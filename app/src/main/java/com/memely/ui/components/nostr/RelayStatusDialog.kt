package com.memely.ui.components.nostr

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.memely.nostr.*

/**
 * Professional dialog showing relay acceptance status for published events.
 * 
 * Displays:
 * - Overall success rate (X/Y relays accepted)
 * - List of accepting relays with timestamps
 * - List of rejecting relays with reasons
 * - List of timing out relays
 * - Status indicators for each relay
 * - Option to close and exit editor
 */
@Composable
fun RelayStatusDialog(
    publishResult: PublishResult?,
    onDismiss: () -> Unit,
    onExitEditor: () -> Unit = {}
) {
    if (publishResult == null) return

    var expandedSection by remember { mutableStateOf("accepted") }
    
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .heightIn(max = 600.dp),
            shape = RoundedCornerShape(16.dp),
            elevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Relay Status",
                        style = MaterialTheme.typography.h6,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Divider()
                Spacer(modifier = Modifier.height(12.dp))

                // Overall Status Card
                OverallStatusCard(publishResult)

                Spacer(modifier = Modifier.height(16.dp))

                // Detailed Relay Status Sections
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    // Accepted Relays
                    if (publishResult.acceptedRelays.isNotEmpty()) {
                        item {
                            RelayStatusSection(
                                title = "✅ Accepted (${publishResult.acceptedRelays.size})",
                                relays = publishResult.acceptedRelays,
                                status = EventStatus.ACCEPTED,
                                isExpanded = expandedSection == "accepted",
                                onToggle = { expandedSection = if (expandedSection == "accepted") "" else "accepted" },
                                details = publishResult.details
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }

                    // Rejected Relays
                    if (publishResult.rejectedRelays.isNotEmpty()) {
                        item {
                            RelayStatusSection(
                                title = "❌ Rejected (${publishResult.rejectedRelays.size})",
                                relays = publishResult.rejectedRelays,
                                status = EventStatus.REJECTED,
                                isExpanded = expandedSection == "rejected",
                                onToggle = { expandedSection = if (expandedSection == "rejected") "" else "rejected" },
                                details = publishResult.details
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }

                    // Timed Out Relays
                    if (publishResult.timedOutRelays.isNotEmpty()) {
                        item {
                            RelayStatusSection(
                                title = "⏰ Timed Out (${publishResult.timedOutRelays.size})",
                                relays = publishResult.timedOutRelays,
                                status = EventStatus.TIMEOUT,
                                isExpanded = expandedSection == "timeout",
                                onToggle = { expandedSection = if (expandedSection == "timeout") "" else "timeout" },
                                details = publishResult.details
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Divider()
                Spacer(modifier = Modifier.height(12.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Keep Editing")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            onDismiss()
                            onExitEditor()
                        },
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = MaterialTheme.colors.primary
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Done")
                    }
                }
            }
        }
    }
}

@Composable
private fun OverallStatusCard(result: PublishResult) {
    val backgroundColor = when {
        result.allAccepted() -> Color(0xFFE8F5E9)  // Green
        result.acceptanceRate >= 0.5f -> Color(0xFFFFF3E0)  // Orange
        else -> Color(0xFFFFEBEE)  // Red
    }
    
    val statusColor = when {
        result.allAccepted() -> Color(0xFF4CAF50)
        result.acceptanceRate >= 0.5f -> Color(0xFFFFA726)
        else -> Color(0xFFF44336)
    }
    
    val statusText = when {
        result.allAccepted() -> "✅ Successfully Posted"
        result.acceptanceRate >= 0.5f -> "⚠️ Partially Posted"
        else -> "❌ Failed to Post"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        backgroundColor = backgroundColor,
        shape = RoundedCornerShape(12.dp),
        elevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = statusText,
                style = MaterialTheme.typography.h6,
                color = statusColor,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "${result.acceptedRelays.size} of ${result.totalRelays} relays accepted",
                style = MaterialTheme.typography.body2,
                color = statusColor
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            LinearProgressIndicator(
                progress = result.acceptanceRate,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
                color = statusColor,
                backgroundColor = Color.Gray.copy(alpha = 0.2f)
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "${(result.acceptanceRate * 100).toInt()}% Success Rate",
                style = MaterialTheme.typography.caption,
                color = statusColor
            )
        }
    }
}

@Composable
private fun RelayStatusSection(
    title: String,
    relays: List<String>,
    status: EventStatus,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    details: Map<String, RelayEventStatus>
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        shape = RoundedCornerShape(8.dp),
        elevation = 1.dp,
        backgroundColor = when (status) {
            EventStatus.ACCEPTED -> Color(0xFFF1F8E9)
            EventStatus.REJECTED -> Color(0xFFFFEBEE)
            EventStatus.TIMEOUT -> Color(0xFFFFF3E0)
            else -> MaterialTheme.colors.surface
        }
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle)
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.body1,
                    fontWeight = FontWeight.SemiBold
                )
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Expanded Content
            if (isExpanded) {
                Divider(modifier = Modifier.padding(horizontal = 12.dp))
                Column(modifier = Modifier.padding(12.dp)) {
                    relays.forEach { relay ->
                        val relayStatus = details[relay]
                        RelayStatusItem(relay, relayStatus, status)
                        if (relay != relays.last()) {
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RelayStatusItem(
    relayUrl: String,
    relayStatus: RelayEventStatus?,
    status: EventStatus
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start
    ) {
        // Status Icon
        Icon(
            imageVector = when (status) {
                EventStatus.ACCEPTED -> Icons.Default.CheckCircle
                EventStatus.REJECTED -> Icons.Default.Error
                EventStatus.TIMEOUT -> Icons.Default.Schedule
                else -> Icons.Default.Help
            },
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = when (status) {
                EventStatus.ACCEPTED -> Color(0xFF4CAF50)
                EventStatus.REJECTED -> Color(0xFFF44336)
                EventStatus.TIMEOUT -> Color(0xFFFFA726)
                else -> Color.Gray
            }
        )

        Spacer(modifier = Modifier.width(8.dp))

        // Relay Info
        Column(modifier = Modifier.weight(1f)) {
            // Extract relay domain for display
            val displayName = relayUrl.removePrefix("wss://").removePrefix("ws://").takeWhile { it != '/' }
            
            Text(
                text = displayName,
                style = MaterialTheme.typography.body2,
                fontWeight = FontWeight.Medium,
                maxLines = 1
            )
            
            if (relayStatus?.message?.isNotBlank() == true) {
                Text(
                    text = relayStatus.message,
                    style = MaterialTheme.typography.caption,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f),
                    maxLines = 1
                )
            }
        }
    }
}
