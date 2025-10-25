package com.memely.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import coil.compose.rememberAsyncImagePainter
import com.memely.data.InteractionRepository
import com.memely.nostr.MemeNote
import com.memely.nostr.UserMetadataCache
import com.memely.nostr.MetadataParser
import com.memely.nostr.InteractionPublisher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch

@Composable
fun ThreadViewDialog(
    eventId: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isLoading = remember { mutableStateOf(true) }
    val replies = remember { mutableStateOf<List<MemeNote>>(emptyList()) }
    val error = remember { mutableStateOf<String?>(null) }
    
    LaunchedEffect(eventId) {
        try {
            isLoading.value = true
            val interactions = InteractionRepository.fetchInteractions(eventId)
            replies.value = interactions?.replies ?: emptyList()
            println("📖 ThreadViewDialog: Loaded ${replies.value.size} replies for $eventId")
        } catch (e: Exception) {
            error.value = "Failed to load thread: ${e.message}"
            println("❌ ThreadViewDialog: Error fetching replies: ${e.message}")
        } finally {
            isLoading.value = false
        }
    }
    
    Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.9f)
                .background(
                    color = MaterialTheme.colors.surface,
                    shape = RoundedCornerShape(16.dp)
                )
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Header with close button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = MaterialTheme.colors.primary.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
                        )
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Thread Replies",
                            style = MaterialTheme.typography.h6,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colors.onSurface
                        )
                        Text(
                            text = "${replies.value.size} ${if (replies.value.size == 1) "reply" else "replies"}",
                            style = MaterialTheme.typography.caption,
                            color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f)
                        )
                    }
                    
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colors.onSurface
                        )
                    }
                }
                
                // Content
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    when {
                        isLoading.value -> {
                            CircularProgressIndicator(
                                modifier = Modifier.align(Alignment.Center)
                            )
                        }
                        error.value != null -> {
                            Text(
                                text = error.value ?: "Unknown error",
                                color = MaterialTheme.colors.error,
                                modifier = Modifier.align(Alignment.Center),
                                textAlign = TextAlign.Center
                            )
                        }
                        replies.value.isEmpty() -> {
                            Text(
                                text = "No replies yet\nBe the first to reply!",
                                color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f),
                                modifier = Modifier.align(Alignment.Center),
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.body2
                            )
                        }
                        else -> {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(replies.value) { reply ->
                                    ReplyThreadItem(reply = reply)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReplyThreadItem(reply: MemeNote) {
    val authorMetadata = remember { mutableStateOf<MetadataParser.UserMetadata?>(null) }
    val scope = rememberCoroutineScope()
    
    // Interaction state
    var showReplyDialog by remember { mutableStateOf(false) }
    var isSubmittingReply by remember { mutableStateOf(false) }
    var isPublishingReaction by remember { mutableStateOf(false) }
    var isPublishingRepost by remember { mutableStateOf(false) }
    var showNestedThread by remember { mutableStateOf(false) }
    
    // Observe cache updates
    val cacheUpdates = UserMetadataCache.cacheUpdateFlow.collectAsState()
    
    LaunchedEffect(reply.pubkey, cacheUpdates.value) {
        // Check if this update is for our pubkey
        if (cacheUpdates.value?.first == reply.pubkey) {
            authorMetadata.value = cacheUpdates.value?.second
        }
        
        // Also check cache directly on composition
        val cached = UserMetadataCache.getCachedMetadata(reply.pubkey)
        if (cached != null) {
            authorMetadata.value = cached
            println("📖 ThreadViewDialog: Loaded metadata for ${reply.pubkey.take(8)}: ${cached.name}")
        } else {
            // Request async fetch if not cached
            println("🔍 ThreadViewDialog: Requesting metadata for ${reply.pubkey.take(8)}")
            UserMetadataCache.requestMetadataAsync(reply.pubkey)
        }
    }
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colors.background,
                shape = RoundedCornerShape(8.dp)
            )
            .padding(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { 
                    showNestedThread = true 
                    println("🔗 Opening nested thread for reply ${reply.id.take(8)}")
                }
        ) {
            // Author section with avatar and metadata
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Avatar
                val avatarUrl = authorMetadata.value?.picture ?: ""
                if (avatarUrl.isNotBlank()) {
                    AsyncImage(
                        model = avatarUrl,
                        contentDescription = "Author avatar",
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    // Placeholder avatar
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colors.primary.copy(alpha = 0.3f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = reply.pubkey.take(2).uppercase(),
                            style = MaterialTheme.typography.caption,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colors.primary
                        )
                    }
                }
                
                // Author name and pubkey
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    val displayName = authorMetadata.value?.name 
                        ?.takeIf { it.isNotBlank() } 
                        ?: reply.pubkey.take(8) + "..."
                    
                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.body2,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colors.onSurface,
                        maxLines = 1
                    )
                }
                
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Timestamp
                    Text(
                        text = formatTimestamp(reply.createdAt),
                        style = MaterialTheme.typography.caption,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.5f)
                    )
                    
                    // 3-dot actions menu
                    NoteActionsMenu(
                        noteId = reply.id,
                        pubkey = reply.pubkey
                    )
                }
            }
            
            Divider(color = MaterialTheme.colors.surface.copy(alpha = 0.5f), thickness = 0.5.dp)
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Reply content
            val textContent = extractTextContent(reply.content)
            if (!textContent.isNullOrBlank()) {
                Text(
                    text = textContent,
                    style = MaterialTheme.typography.body2,
                    color = MaterialTheme.colors.onSurface,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            
            // Image if present in reply
            val imageUrl = extractImageUrl(reply.content)
            if (!imageUrl.isNullOrBlank()) {
                val painter = rememberAsyncImagePainter(model = imageUrl)
                
                // Calculate aspect ratio safely
                val intrinsicSize = painter.intrinsicSize
                val aspectRatio = try {
                    if (intrinsicSize.width > 0 && intrinsicSize.height > 0) {
                        intrinsicSize.width / intrinsicSize.height
                    } else {
                        16f / 9f // Default widescreen
                    }
                } catch (e: Exception) {
                    16f / 9f
                }
                
                Image(
                    painter = painter,
                    contentDescription = "Reply image",
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(aspectRatio)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFF3F4F6)),
                    contentScale = ContentScale.Crop
                )
                
                Spacer(modifier = Modifier.height(8.dp))
            }
            
            // Interaction controls - allow users to reply to replies
            InteractionControlsBox(
                eventId = reply.id,
                authorPubkey = reply.pubkey,
                onReplyClick = {
                    showReplyDialog = true
                },
                onReactClick = { emoji ->
                    scope.launch {
                        try {
                            isPublishingReaction = true
                            println("❤️ ThreadViewDialog: Publishing reaction $emoji to ${reply.id.take(8)}")
                            InteractionPublisher.publishReaction(
                                targetEventId = reply.id,
                                targetPubkey = reply.pubkey,
                                content = emoji
                            )
                            println("✅ ThreadViewDialog: Reaction published")
                            InteractionRepository.invalidateCache(reply.id)
                        } catch (e: Exception) {
                            println("❌ ThreadViewDialog: Failed to publish reaction: ${e.message}")
                        } finally {
                            isPublishingReaction = false
                        }
                    }
                },
                onRepostClick = {
                    scope.launch {
                        try {
                            isPublishingRepost = true
                            println("🔄 ThreadViewDialog: Publishing repost of ${reply.id.take(8)}")
                            InteractionPublisher.publishRepost(
                                targetEventId = reply.id,
                                targetPubkey = reply.pubkey,
                                originalEventJson = reply.toJson()
                            )
                            println("✅ ThreadViewDialog: Repost published")
                            InteractionRepository.invalidateCache(reply.id)
                        } catch (e: Exception) {
                            println("❌ ThreadViewDialog: Failed to publish repost: ${e.message}")
                        } finally {
                            isPublishingRepost = false
                        }
                    }
                }
            )
        }
    }
    
    // Reply dialog for replying to replies
    if (showReplyDialog) {
        ReplyDialog(
            onDismiss = { showReplyDialog = false },
            onSubmit = { replyText ->
                scope.launch {
                    try {
                        isSubmittingReply = true
                        println("💬 ThreadViewDialog: Submitting reply to ${reply.id.take(8)}: $replyText")
                        
                        InteractionPublisher.publishReply(
                            content = replyText,
                            replyToEventId = reply.id,
                            replyToPubkey = reply.pubkey
                        )
                        
                        println("✅ ThreadViewDialog: Reply published successfully")
                        showReplyDialog = false
                        InteractionRepository.invalidateCache(reply.id)
                    } catch (e: Exception) {
                        println("❌ ThreadViewDialog: Failed to publish reply: ${e.message}")
                    } finally {
                        isSubmittingReply = false
                    }
                }
            },
            isLoading = isSubmittingReply,
            targetAuthor = authorMetadata.value?.name ?: reply.pubkey.take(16)
        )
    }
    
    // Nested thread dialog for viewing replies to this reply
    if (showNestedThread) {
        ThreadViewDialog(
            eventId = reply.id,
            onDismiss = { showNestedThread = false }
        )
    }
}

// Helper function to convert MemeNote to JSON string
private fun MemeNote.toJson(): String {
    val json = org.json.JSONObject().apply {
        put("id", id)
        put("pubkey", pubkey)
        put("content", content)
        put("created_at", createdAt)
        put("kind", 1)
        val tagsArray = org.json.JSONArray()
        tags.forEach { tagList ->
            val tagArray = org.json.JSONArray()
            tagList.forEach { tag -> tagArray.put(tag) }
            tagsArray.put(tagArray)
        }
        put("tags", tagsArray)
    }
    return json.toString()
}

private fun formatTimestamp(seconds: Long): String {
    val now = System.currentTimeMillis() / 1000
    val diff = now - seconds
    return when {
        diff < 60 -> "now"
        diff < 3600 -> "${diff / 60}m ago"
        diff < 86400 -> "${diff / 3600}h ago"
        else -> "${diff / 86400}d ago"
    }
}

private fun extractImageUrl(content: String): String? {
    val imagePatterns = listOf(
        Regex("""https?://[^\s]+\.(jpg|jpeg|png|gif|webp)(\?[^\s]*)?"""),
        Regex("""https?://[^\s]*(imgur|gyazo|imgbb)[^\s]*"""),
        Regex("""https?://i\.imgur\.com/[^\s]+""")
    )
    
    for (pattern in imagePatterns) {
        val match = pattern.find(content)
        if (match != null) {
            return match.value
        }
    }
    
    if (content.contains("nostr:file") || content.contains("nostr:image")) {
        val urlMatch = Regex("""https?://[^\s]+""").find(content)
        if (urlMatch != null) {
            return urlMatch.value
        }
    }
    
    return null
}

private fun extractTextContent(content: String): String? {
    val textWithoutUrls = content.replace(Regex("""https?://[^\s]+"""), "").trim()
    return if (textWithoutUrls.isNotBlank()) textWithoutUrls else null
}
