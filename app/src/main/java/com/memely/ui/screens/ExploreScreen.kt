package com.memely.ui.screens

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.memely.ui.components.MemeFeed

@Composable
fun ExploreScreen() {
    MemeFeed(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp)
    )
}