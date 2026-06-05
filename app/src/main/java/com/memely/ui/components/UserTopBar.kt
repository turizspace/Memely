package com.memely.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.memely.nostr.MetadataParser
import com.memely.ui.theme.ThemeManager
import com.memely.ui.theme.ThemePreference

@Composable
fun UserTopBar(
    userMetadata: MetadataParser.UserMetadata? = null,
    modifier: Modifier = Modifier,
    onThemeChange: ((ThemePreference) -> Unit)? = null
) {
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
                        userMetadata?.name ?: "Loading profile..."
                    !userMetadata?.nip05.isNullOrBlank() -> 
                        userMetadata?.nip05 ?: "Loading profile..."
                    else -> "Loading profile..."
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
                horizontalArrangement = Arrangement.spacedBy(8.dp)
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
