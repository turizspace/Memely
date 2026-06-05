package com.memely.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.memely.nostr.MemeRepository
import com.memely.nostr.UserMetadataCache

@Composable
fun MemeFeed(
    modifier: Modifier = Modifier
) {
    val memeNotes by MemeRepository.memeNotesFlow.collectAsState()
    val isLoading by MemeRepository.isLoadingFlow.collectAsState()
    val authorPubkeys by remember {
        derivedStateOf { memeNotes.map { it.pubkey }.distinct() }
    }
    
    // Start fetching memes when component is composed
    LaunchedEffect(Unit) {
        MemeRepository.fetchMemes()
    }
    
    // Pre-fetch metadata for all memes as they arrive
    LaunchedEffect(authorPubkeys) {
        authorPubkeys.forEach { pubkey ->
            val cached = UserMetadataCache.getCachedMetadata(pubkey)
            if (cached == null) {
                UserMetadataCache.requestMetadataAsync(pubkey)
            }
        }
    }
    
    Box(
        modifier = modifier
    ) {
        if (isLoading && memeNotes.isEmpty()) {
            // Show loading spinner when initially loading
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize()
            ) {
                items(
                    items = memeNotes,
                    key = { it.id },
                    contentType = { "meme-card" }
                ) { memeNote ->
                    MemeCard(
                        memeNote = memeNote,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}
