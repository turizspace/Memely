package com.memely.ui.components.editor

import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.memely.data.TemplateRepository
import com.memely.ui.components.SearchBar
import com.memely.ui.components.TemplateGrid

@Composable
fun TemplateSelectorDialog(
    onDismiss: () -> Unit,
    onTemplateSelected: (Uri) -> Unit
) {
    val templates by TemplateRepository.templatesFlow.collectAsState()
    val isLoading by TemplateRepository.isLoadingFlow.collectAsState()
    val error by TemplateRepository.errorFlow.collectAsState()
    
    var searchQuery by remember { mutableStateOf("") }
    val filteredTemplates = remember(templates, searchQuery) {
        TemplateRepository.searchTemplates(searchQuery)
    }
    
    // Fetch templates on first composition if not already loaded
    LaunchedEffect(Unit) {
        if (templates.isEmpty() && !isLoading) {
            println("📡 TemplateSelectorDialog: Fetching meme templates...")
            TemplateRepository.fetchTemplates()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.85f),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colors.surface,
            elevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Add Template Layer",
                        style = MaterialTheme.typography.h6
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close"
                        )
                    }
                }

                // Search bar
                SearchBar(
                    query = searchQuery,
                    onQueryChanged = { searchQuery = it },
                    placeholder = "Search templates...",
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                // Template grid
                TemplateGrid(
                    templates = filteredTemplates,
                    isLoading = isLoading,
                    error = error,
                    modifier = Modifier.fillMaxSize(),
                    onTemplateClick = { template ->
                        println("🎨 TemplateSelectorDialog: Selected template - ${template.name}")
                        
                        // Convert template URL to Uri and pass to callback
                        val templateUri = Uri.parse("https://turiz.space" + template.url)
                        onTemplateSelected(templateUri)
                    }
                )
            }
        }
    }
}
