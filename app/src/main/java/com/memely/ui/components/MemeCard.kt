package com.memely.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Card
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.memely.nostr.MemeNote
import com.memely.nostr.MetadataParser
import com.memely.nostr.UserMetadataCache
import com.memely.nostr.NostrRepository
import com.memely.data.InteractionRepository
import com.memely.nostr.InteractionController
import com.memely.nostr.InteractionPublisher
import com.memely.nostr.AmberSignerManager
import org.json.JSONObject
import org.json.JSONArray
import kotlinx.coroutines.launch

@Composable
fun MemeCard(
    memeNote: MemeNote,
    modifier: Modifier = Modifier
) {
    // Debug the incoming note
    println("🎨 MemeCard: Rendering note from ${memeNote.pubkey.take(8)} - ID: ${memeNote.id.take(8)}")
    
    val scope = rememberCoroutineScope()
    
    // Interaction dialogs state
    var showReplyDialog by remember { mutableStateOf(false) }
    var showThreadView by remember { mutableStateOf(false) }
    var isSubmittingReply by remember { mutableStateOf(false) }
    var isPublishingReaction by remember { mutableStateOf(false) }
    var isPublishingRepost by remember { mutableStateOf(false) }
    
    // Try to get cached metadata immediately
    val cached = UserMetadataCache.getCachedMetadata(memeNote.pubkey)
    var userMetadata by remember(memeNote.pubkey) { 
        mutableStateOf<MetadataParser.UserMetadata?>(cached)
    }
    
    // Observe cache updates - this will trigger recomposition when new metadata arrives
    val cacheUpdate by UserMetadataCache.cacheUpdateFlow.collectAsState(initial = null)
    
    // Update when cache gets new data for this pubkey
    LaunchedEffect(cacheUpdate, memeNote.pubkey) {
        if (cacheUpdate?.first == memeNote.pubkey) {
            val newMetadata = cacheUpdate?.second
            if (newMetadata != null) {
                userMetadata = newMetadata
                println("🔔 MemeCard: Cache updated for ${memeNote.pubkey.take(8)} - ${newMetadata.name}")
            }
        }
    }
    
    // Fetch metadata synchronously on first composition - ensures card sees the result
    LaunchedEffect(memeNote.pubkey) {
        val cachedNow = UserMetadataCache.getCachedMetadata(memeNote.pubkey)
        if (cachedNow == null) {
            println("🔄 MemeCard: Fetching metadata synchronously for ${memeNote.pubkey.take(8)}...")
            try {
                // Fetch directly instead of background - ensures card sees the result
                val fetchedMetadata = NostrRepository.fetchProfileMetadata(memeNote.pubkey)
                if (fetchedMetadata != null) {
                    userMetadata = fetchedMetadata
                    println("✅ MemeCard: Fetched metadata for ${memeNote.pubkey.take(8)} - ${fetchedMetadata.name}")
                }
            } catch (e: Exception) {
                println("❌ MemeCard: Failed to fetch metadata: ${e.message}")
            }
        } else {
            println("💾 MemeCard: Using cached metadata for ${memeNote.pubkey.take(8)} - ${cachedNow.name}")
            userMetadata = cachedNow
        }
    }
    
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(8.dp),
        elevation = 4.dp,
        shape = RoundedCornerShape(12.dp),
        backgroundColor = MaterialTheme.colors.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // User info row with actions menu
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Profile picture
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colors.primary.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (!userMetadata?.picture.isNullOrBlank()) {
                            Image(
                                painter = rememberAsyncImagePainter(userMetadata?.picture),
                                contentDescription = "Profile picture",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Text(
                                text = (userMetadata?.name ?: "?").take(1),
                                style = MaterialTheme.typography.h6,
                                color = MaterialTheme.colors.primary
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.width(12.dp))
                    
                    // User name and info
                    Column {
                        Text(
                            text = userMetadata?.name ?: "Anonymous",
                            style = MaterialTheme.typography.subtitle1,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colors.onSurface
                        )
                        if (!userMetadata?.nip05.isNullOrBlank()) {
                            Text(
                                text = userMetadata?.nip05 ?: "",
                                style = MaterialTheme.typography.caption,
                                color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
                
                // 3-dot menu
                NoteActionsMenu(
                    noteId = memeNote.id,
                    pubkey = memeNote.pubkey
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Meme image - with safe aspect ratio handling
            val imageUrl = extractImageUrl(memeNote.content)
            if (!imageUrl.isNullOrBlank()) {
                println("🖼️ MemeCard: Displaying image from $imageUrl")
                
                val painter = rememberAsyncImagePainter(
                    model = imageUrl
                )
                
                // Safe aspect ratio calculation - handle unspecified size
                val intrinsicSize = painter.intrinsicSize
                val aspectRatio = try {
                    if (intrinsicSize.width > 0 && intrinsicSize.height > 0) {
                        intrinsicSize.width / intrinsicSize.height
                    } else {
                        1f // Default to square
                    }
                } catch (e: Exception) {
                    1f // Fallback to square on any error
                }
                
                // Display image with adaptive aspect ratio
                Image(
                    painter = painter,
                    contentDescription = "Meme",
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(aspectRatio)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFF3F4F6))
                        .clickable { showThreadView = true },
                    contentScale = ContentScale.Crop
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Optional: Show first line of text if available
            val textContent = extractTextContent(memeNote.content)
            if (!textContent.isNullOrBlank()) {
                Text(
                    text = textContent,
                    style = MaterialTheme.typography.body2,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.8f),
                    maxLines = 2
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Interaction controls
            InteractionControlsBox(
                eventId = memeNote.id,
                authorPubkey = memeNote.pubkey,
                onReplyClick = { showReplyDialog = true },
                onReactClick = { emoji ->
                    // Handle reaction - publish to Nostr
                    scope.launch {
                        try {
                            isPublishingReaction = true
                            println("❤️ MemeCard: Publishing reaction: $emoji")
                            InteractionPublisher.publishReaction(
                                targetEventId = memeNote.id,
                                targetPubkey = memeNote.pubkey,
                                content = emoji
                            )
                            println("✅ MemeCard: Reaction published successfully")
                            InteractionRepository.invalidateCache(memeNote.id)
                        } catch (e: Exception) {
                            println("❌ MemeCard: Failed to publish reaction: ${e.message}")
                        } finally {
                            isPublishingReaction = false
                        }
                    }
                },
                onRepostClick = {
                    // Handle repost - publish to Nostr
                    scope.launch {
                        try {
                            isPublishingRepost = true
                            println("🔄 MemeCard: Publishing repost")
                            InteractionPublisher.publishRepost(
                                targetEventId = memeNote.id,
                                targetPubkey = memeNote.pubkey,
                                originalEventJson = memeNote.toJson()
                            )
                            println("✅ MemeCard: Repost published successfully")
                            InteractionRepository.invalidateCache(memeNote.id)
                        } catch (e: Exception) {
                            println("❌ MemeCard: Failed to publish repost: ${e.message}")
                        } finally {
                            isPublishingRepost = false
                        }
                    }
                }
            )
        }
    }
    
    // Reply dialog
    if (showReplyDialog) {
        ReplyDialog(
            onDismiss = { showReplyDialog = false },
            onSubmit = { replyText ->
                scope.launch {
                    try {
                        isSubmittingReply = true
                        println("💬 MemeCard: Submitting reply: $replyText")
                        
                        // Publish reply to Nostr
                        InteractionPublisher.publishReply(
                            content = replyText,
                            replyToEventId = memeNote.id,
                            replyToPubkey = memeNote.pubkey
                        )
                        
                        println("✅ MemeCard: Reply published successfully")
                        
                        // Close dialog and refresh interactions
                        showReplyDialog = false
                        InteractionRepository.invalidateCache(memeNote.id)
                    } catch (e: Exception) {
                        println("❌ MemeCard: Failed to publish reply: ${e.message}")
                        // Keep dialog open on error so user can retry
                    } finally {
                        isSubmittingReply = false
                    }
                }
            },
            isLoading = isSubmittingReply,
            targetAuthor = userMetadata?.name ?: memeNote.pubkey.take(16)
        )
    }
    
    // Thread view dialog
    if (showThreadView) {
        ThreadViewDialog(
            eventId = memeNote.id,
            onDismiss = { showThreadView = false }
        )
    }
}

// Helper functions
private fun MemeNote.toJson(): String {
    val json = JSONObject().apply {
        put("id", id)
        put("pubkey", pubkey)
        put("content", content)
        put("created_at", createdAt)
        put("kind", 1)
        val tagsArray = JSONArray()
        tags.forEach { tagList ->
            val tagArray = JSONArray()
            tagList.forEach { tag -> tagArray.put(tag) }
            tagsArray.put(tagArray)
        }
        put("tags", tagsArray)
    }
    return json.toString()
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