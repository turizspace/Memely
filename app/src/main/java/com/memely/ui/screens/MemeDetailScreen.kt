package com.memely.ui.screens

import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.Composable

import com.memely.data.models.Meme
import com.memely.ui.components.ZapButton

@Composable
fun MemeDetailScreen(meme: Meme) {
    // Show meme image and caption (placeholder)
    Text("Meme: ${meme.caption}")
    meme.imageUrl?.let {
        Text("Image: $it")
    }

    ZapButton(lud16 = null) // In real flow, pass the author's lud16
}
