package com.memely.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Person
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.memely.nostr.KeyStoreManager
import com.memely.nostr.MetadataParser
import com.memely.nostr.NostrRepository
import com.memely.ui.theme.ThemeManager
import com.memely.ui.theme.ThemePreference

@Composable
fun UserTopBar(
    connectedRelays: Int,
    totalRelays: Int,
    modifier: Modifier = Modifier,
    onThemeChange: ((ThemePreference) -> Unit)? = null
) {
    // Collect metadata directly from NostrRepository (same source as ProfileScreen)
    val userMetadata by NostrRepository.metadataState.collectAsState()
    val context = LocalContext.current
    
    // Load theme preference
    var currentTheme by remember {
        mutableStateOf(ThemeManager.getThemePreference(context))
    }
    
    TopAppBar(
        modifier = modifier.fillMaxWidth(),
        backgroundColor = MaterialTheme.colors.surface,
        elevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp)
            ) {
                Text(
                    text = "Welcome,",
                    style = MaterialTheme.typography.caption
                )

                // Display name logic - use what ProfileScreen uses
                val displayName = when {
                    !userMetadata?.name.isNullOrBlank() && userMetadata?.name != "Memely User" -> 
                        userMetadata?.name ?: "Anonymous"
                    !userMetadata?.nip05.isNullOrBlank() -> 
                        userMetadata?.nip05 ?: "Anonymous"
                    else -> "Anonymous"
                }

                Text(
                    text = displayName,
                    style = MaterialTheme.typography.subtitle1,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Theme toggle button
                ThemeToggleButton(
                    currentTheme = currentTheme,
                    onThemeChange = { newTheme ->
                        currentTheme = newTheme
                        ThemeManager.saveThemePreference(context, newTheme)
                        onThemeChange?.invoke(newTheme)
                    },
                    tint = MaterialTheme.colors.onSurface.copy(alpha = 0.7f)
                )
                
                // Relay status
                val isOnline = connectedRelays > 0
                Icon(
                    imageVector = if (isOnline) Icons.Default.Cloud else Icons.Default.CloudOff,
                    contentDescription = null,
                    tint = if (isOnline)
                        MaterialTheme.colors.primary
                    else
                        MaterialTheme.colors.onSurface.copy(alpha = 0.5f)
                )
                Text(
                    "$connectedRelays/$totalRelays",
                    style = MaterialTheme.typography.body2
                )
            }

            val profileUrl = userMetadata?.picture
            Box(
                modifier = Modifier
                    .padding(start = 10.dp)
                    .size(40.dp)
                    .clip(CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (!profileUrl.isNullOrBlank()) {
                    Image(
                        painter = rememberAsyncImagePainter(profileUrl),
                        contentDescription = "Profile",
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = "Default Avatar",
                        tint = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}