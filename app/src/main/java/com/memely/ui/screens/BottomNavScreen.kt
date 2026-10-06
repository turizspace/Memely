package com.memely.ui.screens

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

enum class BottomNavScreen(val title: String, val icon: ImageVector) {
    Home("Home", Icons.Default.Home),
    Friends("Friends", Icons.Default.Groups),
    Explore("Explore", Icons.Default.EmojiEmotions),
    Upload("Upload", Icons.Default.Add),
    Profile("Profile", Icons.Default.Person)
}
