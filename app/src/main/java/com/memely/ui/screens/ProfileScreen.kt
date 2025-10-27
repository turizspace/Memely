package com.memely.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.memely.nostr.MetadataParser
import com.memely.ui.components.LogoutButton
import com.memely.ui.tutorial.TutorialOverlay
import com.memely.ui.tutorial.TutorialScreen
import com.memely.ui.tutorial.tutorialTarget

@Composable
fun ProfileScreen(
    user: MetadataParser.UserMetadata? = null,
    onLogout: (() -> Unit)? = null
) {
    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .tutorialTarget("profile_content")
            ) {

                // Banner
                user?.banner?.let { bannerUrl ->
                    Image(
                        painter = rememberAsyncImagePainter(bannerUrl),
                        contentDescription = "Banner",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(Modifier.height(8.dp))
                }

                // Avatar
                user?.picture?.let { avatarUrl ->
                    Image(
                        painter = rememberAsyncImagePainter(avatarUrl),
                        contentDescription = "Profile Picture",
                        modifier = Modifier
                            .size(96.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(Modifier.height(8.dp))
                }

                // Name
                Text(
                    text = user?.name ?: "Unknown User",
                    style = MaterialTheme.typography.h6
                )

                // About
                user?.about?.let {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = it,
                        style = MaterialTheme.typography.body2,
                        textAlign = TextAlign.Center
                    )
                }

                // NIP-05
                user?.nip05?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "🔹 $it",
                        style = MaterialTheme.typography.caption
                    )
                }

                // LUD-16 (Lightning Address)
                user?.lud16?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "⚡ $it",
                        style = MaterialTheme.typography.caption
                    )
                }

                // Website
                user?.website?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "🌐 $it",
                        style = MaterialTheme.typography.caption
                    )
                }

                // Divider before logout section
                if (onLogout != null) {
                    Spacer(Modifier.height(24.dp))
                    Divider(
                        modifier = Modifier
                            .fillMaxWidth(0.8f)
                            .padding(vertical = 12.dp)
                    )

                    // Logout Button Section
                    Spacer(Modifier.height(12.dp))
                    LogoutButton(onLogout = onLogout)
                    Spacer(Modifier.height(12.dp))
                }
            }
        }
        
        // Tutorial overlay for Profile screen
        TutorialOverlay(currentScreen = TutorialScreen.PROFILE)
    }
}
