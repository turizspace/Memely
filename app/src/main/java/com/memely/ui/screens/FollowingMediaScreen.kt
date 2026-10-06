package com.memely.ui.screens

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.memely.nostr.FollowMediaItem
import com.memely.nostr.FollowMediaRepository
import com.memely.ui.components.SearchBar

@Composable
fun FollowingMediaScreen(pubkey: String?, onMediaSelected: (Uri) -> Unit) {
    val media by FollowMediaRepository.items.collectAsState()
    val isLoading by FollowMediaRepository.isLoading.collectAsState()
    val message by FollowMediaRepository.message.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    val displayed = remember(media, searchQuery) {
        if (searchQuery.isBlank()) media else media.filter {
            it.authorName.contains(searchQuery, ignoreCase = true) ||
                it.authorPubkey.contains(searchQuery, ignoreCase = true)
        }
    }

    LaunchedEffect(pubkey) {
        if (!pubkey.isNullOrBlank()) FollowMediaRepository.refresh(pubkey)
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
                    "Images people you follow shared on Nostr",
                    style = MaterialTheme.typography.caption,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.65f)
                )
            }
            IconButton(onClick = { pubkey?.let { FollowMediaRepository.refresh(it, force = true) } }) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh following media")
            }
        }
        Spacer(Modifier.height(10.dp))
        SearchBar(searchQuery, { searchQuery = it }, placeholder = "Search people you follow")
        Spacer(Modifier.height(10.dp))

        when {
            isLoading && displayed.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            displayed.isNotEmpty() -> LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(displayed, key = { it.id }) { item ->
                    FollowMediaCard(item) { onMediaSelected(Uri.parse(item.url)) }
                }
            }
            else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(message ?: "Loading media from people you follow…", color = MaterialTheme.colors.onSurface.copy(alpha = 0.65f))
            }
        }
    }
}

@Composable
private fun FollowMediaCard(item: FollowMediaItem, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().aspectRatio(1f).clip(MaterialTheme.shapes.medium).clickable(onClick = onClick)
    ) {
        AsyncImage(
            model = item.thumbnailUrl ?: item.url,
            contentDescription = "Shared by ${item.authorName}",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        Text(
            text = "@${item.authorName}",
            modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().background(Color.Black.copy(alpha = .58f)).padding(8.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            style = MaterialTheme.typography.caption
        )
    }
}
