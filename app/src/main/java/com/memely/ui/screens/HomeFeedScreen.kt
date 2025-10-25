package com.memely.ui.screens

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.memely.data.TemplateRepository
import com.memely.ui.components.SearchBar
import com.memely.ui.components.TemplateGrid

@Composable
fun HomeFeedScreen(
    onTemplateSelected: (Uri) -> Unit
) {
    val templates by TemplateRepository.templatesFlow.collectAsState()
    val isLoading by TemplateRepository.isLoadingFlow.collectAsState()
    val error by TemplateRepository.errorFlow.collectAsState()
    
    var searchQuery by remember { mutableStateOf("") }
    val filteredTemplates = remember(templates, searchQuery) {
        TemplateRepository.searchTemplates(searchQuery)
    }
    
    // Fetch templates on first composition
    LaunchedEffect(Unit) {
        println("📡 HomeFeedScreen: Fetching meme templates...")
        TemplateRepository.fetchTemplates()
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp)
    ) {
        // Search bar
        SearchBar(
            query = searchQuery,
            onQueryChanged = { searchQuery = it },
            placeholder = "Search templates...",
            modifier = Modifier.padding(bottom = 12.dp)
        )
        
        // Template grid
        Box(
            modifier = Modifier
                .fillMaxSize()
        ) {
            TemplateGrid(
                templates = filteredTemplates,
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
}