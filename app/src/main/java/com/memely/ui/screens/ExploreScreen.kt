package com.memely.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.memely.ui.components.MemeFeed
import com.memely.ui.tutorial.TutorialOverlay
import com.memely.ui.tutorial.TutorialScreen
import com.memely.ui.tutorial.tutorialTarget

@Composable
fun ExploreScreen() {
    Box(modifier = Modifier.fillMaxSize()) {
        MemeFeed(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp)
                .tutorialTarget("meme_feed")
        )
        
        // Invisible tutorial target for interaction controls (positioned over the feed area)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .tutorialTarget("interaction_controls")
        )
        
        // Tutorial overlay for Explore screen
        TutorialOverlay(currentScreen = TutorialScreen.EXPLORE)
    }
}