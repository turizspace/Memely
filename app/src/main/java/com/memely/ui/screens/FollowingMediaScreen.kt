package com.memely.ui.screens

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Card
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.compose.rememberAsyncImagePainter
import com.memely.nostr.FollowMediaItem
import com.memely.nostr.FollowMediaRepository
import com.memely.nostr.MetadataParser
import com.memely.nostr.UserMetadataCache
import com.memely.ui.components.SearchBar

@Composable
fun FollowingMediaScreen(
    pubkey: String?,
    connectedRelays: Int,
    onMediaSelected: (Uri) -> Unit
) {
    val media by FollowMediaRepository.items.collectAsState()
    val isLoading by FollowMediaRepository.isLoading.collectAsState()
    val message by FollowMediaRepository.message.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    val displayed = remember(media, searchQuery) {
        if (searchQuery.isBlank()) media else media.filter {
            it.authorName.contains(searchQuery, ignoreCase = true) ||
                it.authorPubkey.contains(searchQuery, ignoreCase = true) ||
                it.content.contains(searchQuery, ignoreCase = true)
        }
    }

    // A REQ sent before the relay pool is connected is dropped. Key this effect to
    // connection state so the initial load starts as soon as a relay is available.
    LaunchedEffect(pubkey, connectedRelays) {
        if (!pubkey.isNullOrBlank() && connectedRelays > 0) {
            FollowMediaRepository.refresh(pubkey)
        }
    }

    Column(Modifier.fillMaxSize().padding(12.dp)) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(Modifier.weight(1f)) {
                Text("Following media", style = MaterialTheme.typography.h6)
                Text(
                    "Image notes shared by people you follow",
                    style = MaterialTheme.typography.caption,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.65f)
                )
            }
            IconButton(
                onClick = { pubkey?.let { FollowMediaRepository.refresh(it, force = true) } },
                enabled = !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh following media")
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        SearchBar(searchQuery, { searchQuery = it }, placeholder = "Search notes and people")
        Spacer(Modifier.height(10.dp))

        when {
            isLoading && media.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            displayed.isNotEmpty() -> LazyColumn(Modifier.fillMaxSize()) {
                items(displayed, key = { it.id }) { item ->
                    FollowNoteCard(item) { url -> onMediaSelected(Uri.parse(url)) }
                }
            }
            else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    message ?: if (connectedRelays <= 0) "Connecting to Nostr relays…" else "Loading media from people you follow…",
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.65f)
                )
            }
        }
    }
}

@Composable
private fun FollowNoteCard(item: FollowMediaItem, onImageClick: (String) -> Unit) {
    val cached = UserMetadataCache.getCachedMetadata(item.authorPubkey)
    var authorMetadata by remember(item.authorPubkey) {
        mutableStateOf<MetadataParser.UserMetadata?>(cached)
    }
    val cacheUpdate by UserMetadataCache.cacheUpdateFlow.collectAsState(initial = null)

    LaunchedEffect(cacheUpdate, item.authorPubkey) {
        if (cacheUpdate?.first == item.authorPubkey) {
            authorMetadata = cacheUpdate?.second
        }
    }
    LaunchedEffect(item.authorPubkey) {
        if (UserMetadataCache.getCachedMetadata(item.authorPubkey) == null) {
            UserMetadataCache.requestMetadataAsync(item.authorPubkey)
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
        elevation = 2.dp,
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colors.primary.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    if (!authorMetadata?.picture.isNullOrBlank()) {
                        Image(
                            painter = rememberAsyncImagePainter(authorMetadata?.picture),
                            contentDescription = "${authorMetadata?.name ?: item.authorName}'s profile picture",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Text(
                            text = (authorMetadata?.name ?: item.authorName).take(1).uppercase(),
                            style = MaterialTheme.typography.h6,
                            color = MaterialTheme.colors.primary
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        text = authorMetadata?.name ?: item.authorName,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.subtitle1,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!authorMetadata?.nip05.isNullOrBlank()) {
                        Text(
                            text = authorMetadata?.nip05.orEmpty(),
                            style = MaterialTheme.typography.caption,
                            color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            if (item.content.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(text = item.content, style = MaterialTheme.typography.body2)
            }
            Spacer(Modifier.height(8.dp))
            item.imageUrls.forEachIndexed { index, url ->
                AsyncImage(
                    model = if (index == 0) item.thumbnailUrl ?: url else url,
                    contentDescription = "Image shared by ${authorMetadata?.name ?: item.authorName}",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp)
                        .clickable { onImageClick(url) },
                    contentScale = ContentScale.Crop
                )
                if (index < item.imageUrls.lastIndex) Spacer(Modifier.height(8.dp))
            }
        }
    }
}
