package com.memely.ui.screens

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.memely.data.FavoritesManager
import com.memely.data.TemplateRepository
import com.memely.ui.components.SearchBar
import com.memely.ui.components.TemplateGrid
import com.memely.ui.components.TemplateTab
import com.memely.ui.components.TemplateTabBar
import com.memely.ui.tutorial.TutorialOverlay
import com.memely.ui.tutorial.TutorialScreen
import com.memely.ui.tutorial.TutorialManager
import com.memely.ui.tutorial.tutorialTarget

@Composable
fun HomeFeedScreen(
    onTemplateSelected: (Uri) -> Unit
) {
    val context = LocalContext.current
    val templates by TemplateRepository.templatesFlow.collectAsState()
    val isLoading by TemplateRepository.isLoadingFlow.collectAsState()
    val error by TemplateRepository.errorFlow.collectAsState()
    val favorites by FavoritesManager.favoritesFlow.collectAsState()
    
    var searchQuery by remember { mutableStateOf("") }
    var selectedTab by remember { mutableStateOf(TemplateTab.ALL) }
    
    // Get templates based on selected tab - recomputes when favorites change
    val displayedTemplates = remember(templates, selectedTab, searchQuery, favorites) {
        when (selectedTab) {
            TemplateTab.ALL -> TemplateRepository.searchTemplates(searchQuery)
            TemplateTab.FAVORITES -> TemplateRepository.searchFavoriteTemplates(context, searchQuery)
        }
    }
    
    // Fetch templates on first composition
    LaunchedEffect(Unit) {
        println("📡 HomeFeedScreen: Fetching meme templates...")
        FavoritesManager.initialize(context)  // Initialize favorites from storage
        TemplateRepository.fetchTemplates()
    }
    
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp)
                .tutorialTarget("home_screen")
        ) {
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
                modifier = Modifier
                    .padding(bottom = 12.dp)
                    .tutorialTarget("search_bar")
            )
            
            // Template grid
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .tutorialTarget("template_grid")
            ) {
                TemplateGrid(
                    templates = displayedTemplates,
                    isLoading = isLoading,
                    error = error,
                    modifier = Modifier.fillMaxSize(),
                    onTemplateClick = { template ->
                        println("🎨 HomeFeedScreen: Selected template - ${template.name}")
                        
                        // Check if tutorial is active and waiting for template selection
                        val currentStep = TutorialManager.getCurrentStep()
                        val isActive = TutorialManager.isActive.value
                        
                        if (isActive && currentStep?.id == "home_select_template" && currentStep.actionRequired) {
                            // Advance tutorial when template is selected during tutorial
                            TutorialManager.nextStep()
                            // Don't navigate to editor yet during tutorial
                            return@TemplateGrid
                        }
                        
                        // Normal behavior: Convert template URL to Uri and pass to editor
                        val templateUri = Uri.parse("https://turiz.space" + template.url)
                        onTemplateSelected(templateUri)
                    }
                )
            }
        }
        
        // Tutorial overlay for Home screen
        TutorialOverlay(currentScreen = TutorialScreen.HOME_FEED)
    }
}