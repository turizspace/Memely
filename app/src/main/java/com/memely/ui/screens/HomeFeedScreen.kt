package com.memely.ui.screens

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.memely.data.TemplateRepository
import com.memely.ui.components.TemplateGrid

@Composable
fun HomeFeedScreen(
    onTemplateSelected: (Uri) -> Unit
) {
    val templates by TemplateRepository.templatesFlow.collectAsState()
    val isLoading by TemplateRepository.isLoadingFlow.collectAsState()
    val error by TemplateRepository.errorFlow.collectAsState()
    
    // Fetch templates on first composition
    LaunchedEffect(Unit) {
        println("📡 HomeFeedScreen: Fetching meme templates...")
        TemplateRepository.fetchTemplates()
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp)
    ) {
        TemplateGrid(
            templates = templates,
            isLoading = isLoading,
            error = error,
            modifier = Modifier.fillMaxSize(),
            onTemplateClick = { template ->
                println("🎨 HomeFeedScreen: Selected template - ${template.name}")
                // Convert template URL to Uri and pass to editor
                val templateUri = Uri.parse("https://turiz.space" + template.url)
                onTemplateSelected(templateUri)
            }
        )
    }
}