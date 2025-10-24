package com.memely.ui.components.nostr

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil.compose.rememberAsyncImagePainter
import com.google.accompanist.flowlayout.FlowRow
import com.memely.utils.HashtagHistoryManager

/**
 * Dialog for composing a Nostr note with an image.
 * Shows image preview and allows user to add a caption.
 */
@Composable
fun ComposeNoteDialog(
    imageUrl: String,
    initialCaption: String = "",
    isPosting: Boolean = false,
    onDismiss: () -> Unit,
    onPost: (caption: String) -> Unit
) {
    val context = LocalContext.current
    var caption by remember { mutableStateOf(initialCaption) }
    var hashtagHistory by remember { mutableStateOf<List<String>>(emptyList()) }

    // Initialize hashtag manager and load history
    LaunchedEffect(Unit) {
        HashtagHistoryManager.init(context)
        hashtagHistory = HashtagHistoryManager.getHashtagHistory()
    }

    Dialog(onDismissRequest = { if (!isPosting) onDismiss() }) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
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
                        text = "Post to Nostr",
                        style = MaterialTheme.typography.h6,
                        fontWeight = FontWeight.Bold
                    )
                    if (!isPosting) {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Scrollable content
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                ) {
                    // Image preview
                    Image(
                        painter = rememberAsyncImagePainter(imageUrl),
                        contentDescription = "Meme preview",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Caption input
                    OutlinedTextField(
                        value = caption,
                        onValueChange = { caption = it },
                        label = { Text("Add a caption") },
                        placeholder = { Text("What's on your mind?") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 100.dp),
                        maxLines = 5,
                        enabled = !isPosting
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Hashtag history section
                    if (hashtagHistory.isEmpty()) {
                        Text(
                            text = "💡 Tip: The hashtags you use like #memes #nostr will appear here for quick reuse",
                            style = MaterialTheme.typography.caption,
                            color = MaterialTheme.colors.onSurface.copy(alpha = 0.5f),
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                    } else {
                        Text(
                            text = "Quick hashtags:",
                            style = MaterialTheme.typography.caption,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f),
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                        )
                        
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            mainAxisSpacing = 8.dp,
                            crossAxisSpacing = 8.dp
                        ) {
                            hashtagHistory.forEach { hashtag ->
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colors.primary.copy(alpha = 0.1f),
                                    modifier = Modifier.clickable(enabled = !isPosting) {
                                        // Add hashtag to caption with a space if needed
                                        val currentText = caption.trimEnd()
                                        caption = if (currentText.isEmpty()) {
                                            hashtag
                                        } else {
                                            "$currentText $hashtag"
                                        }
                                    }
                                ) {
                                    Text(
                                        text = hashtag,
                                        style = MaterialTheme.typography.body2,
                                        color = MaterialTheme.colors.primary,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // URL display
                    Text(
                        text = imageUrl,
                        style = MaterialTheme.typography.caption,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isPosting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Posting...", style = MaterialTheme.typography.body2)
                    } else {
                        TextButton(onClick = onDismiss) {
                            Text("Cancel")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                // Save hashtags from caption to history
                                HashtagHistoryManager.saveHashtagsFromText(caption)
                                onPost(caption)
                            },
                            enabled = !isPosting
                        ) {
                            Text("Post")
                        }
                    }
                }
            }
        }
    }
}
