package com.memely.ui.components.editor

import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.memely.data.FavoritesManager
import com.memely.data.TemplateRepository
import com.memely.ui.components.SearchBar
import com.memely.ui.components.TemplateGrid
import com.memely.ui.components.TemplateTab
import com.memely.ui.components.TemplateTabBar
import kotlinx.coroutines.launch

@Composable
fun TemplateSelectorDialog(
    onDismiss: () -> Unit,
    pubkey: String?,
    signEvent: (suspend (String) -> String)?,
    onTemplateSelected: (Uri) -> Unit
) {
    val context = LocalContext.current
    val templates by TemplateRepository.templatesFlow.collectAsState()
    val isLoading by TemplateRepository.isLoadingFlow.collectAsState()
    val error by TemplateRepository.errorFlow.collectAsState()
    val favorites by FavoritesManager.favoritesFlow.collectAsState()
    
    var searchQuery by remember { mutableStateOf("") }
    var selectedTab by remember { mutableStateOf(TemplateTab.ALL) }
    val scope = rememberCoroutineScope()
    
    // Get templates based on selected tab - recomputes when favorites change
    val displayedTemplates = remember(templates, selectedTab, searchQuery, favorites) {
        when (selectedTab) {
            TemplateTab.ALL -> TemplateRepository.searchTemplates(searchQuery)
            TemplateTab.FAVORITES -> TemplateRepository.searchFavoriteTemplates(context, searchQuery)
        }
    }
    
    // Fetch templates on first composition if not already loaded
    LaunchedEffect(pubkey, signEvent) {
        if (templates.isEmpty() && !isLoading && !pubkey.isNullOrBlank() && signEvent != null) {
            TemplateRepository.fetchTemplates(pubkey, signEvent)
        }
        FavoritesManager.initialize(context)  // Initialize favorites from storage
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
                        text = "Your Blossom media",
                        style = MaterialTheme.typography.h6
                    )
                    Row {
                        IconButton(
                            enabled = !isLoading && !pubkey.isNullOrBlank() && signEvent != null,
                            onClick = {
                                scope.launch {
                                    TemplateRepository.fetchTemplates(pubkey!!, signEvent!!, forceRefresh = true)
                                }
                            }
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh Blossom media")
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    }
                }

                // Template tabs
                TemplateTabBar(
                    selectedTab = selectedTab,
                    onTabSelected = { newTab ->
                        selectedTab = newTab
                        searchQuery = ""  // Reset search when changing tabs
                    },
                    favoritesCount = favorites.size
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Search bar
                SearchBar(
                    query = searchQuery,
                    onQueryChanged = { searchQuery = it },
                    placeholder = "Search ${selectedTab.name.lowercase()}...",
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                if (pubkey.isNullOrBlank() || signEvent == null) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Sign in with a Nostr signer to browse your Blossom media.")
                    }
                } else {
                    TemplateGrid(
                        templates = displayedTemplates,
                        isLoading = isLoading,
                        error = error,
                        modifier = Modifier.fillMaxSize(),
                        onTemplateClick = { template -> onTemplateSelected(Uri.parse(template.url)) }
                    )
                }
            }
        }
    }
}
