package com.memely.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Reply
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.memely.data.InteractionRepository
import com.memely.ui.tutorial.tutorialTarget

@Composable
fun InteractionControlsBox(
    eventId: String,
    authorPubkey: String,
    onReplyClick: () -> Unit,
    onThreadClick: (() -> Unit)? = null,
    onReactClick: (String) -> Unit = {},
    onRepostClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val interactions = remember { mutableStateOf<InteractionRepository.InteractionCounts?>(null) }
    val userLiked = remember { mutableStateOf(false) }
    val userReposted = remember { mutableStateOf(false) }
    
    // Fetch interaction counts on composition
    LaunchedEffect(eventId) {
        try {
            val counts = InteractionRepository.fetchInteractions(eventId)
            interactions.value = counts
            println("🎯 InteractionControlsBox: Loaded interactions for $eventId")
        } catch (e: Exception) {
            println("❌ InteractionControlsBox: Failed to fetch interactions: ${e.message}")
        }
    }
    
    val counts = interactions.value
    
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .tutorialTarget("interaction_controls"),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Reply button
        InteractionButton(
            icon = Icons.Default.Reply,
            count = counts?.replyCount ?: 0,
            label = "Reply",
            onClick = onReplyClick,
            modifier = Modifier.weight(1f)
        )
        
        // Like/Reaction button
        InteractionButton(
            icon = if (userLiked.value) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
            count = counts?.likeCount ?: 0,
            label = "Like",
            isActive = userLiked.value,
            onClick = {
                userLiked.value = !userLiked.value
                onReactClick(if (userLiked.value) "+" else "-")
            },
            modifier = Modifier.weight(1f)
        )
        
        // Repost button
        InteractionButton(
            icon = Icons.Default.Repeat,
            count = counts?.repostCount ?: 0,
            label = "Repost",
            isActive = userReposted.value,
            onClick = {
                userReposted.value = !userReposted.value
                onRepostClick()
            },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun InteractionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    count: Int,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isActive: Boolean = false
) {
    Row(
        modifier = modifier
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        IconButton(
            onClick = onClick,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = when {
                    isActive && label == "Like" -> Color.Red
                    isActive && label == "Repost" -> Color(0xFF00C853) // Green for repost
                    else -> MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                },
                modifier = Modifier.size(20.dp)
            )
        }
        
        // Always show count (or 0) to maintain consistent spacing
        Text(
            text = when {
                count >= 1000 -> "${count / 1000}k"
                count > 0 -> count.toString()
                else -> "" // Empty string when 0 to avoid showing 0
            },
            style = MaterialTheme.typography.caption,
            fontSize = 12.sp,
            color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f),
            modifier = Modifier.widthIn(min = 16.dp) // Minimum width to maintain alignment
        )
    }
}
