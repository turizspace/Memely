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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.memely.blossom.signBlossomAuthorization
import com.memely.di.appContainer
import com.memely.di.viewModelFactory
import com.memely.data.FavoritesManager
import com.memely.data.TemplateRepository
import com.memely.nostr.AmberSignerManager
import com.memely.nostr.KeyStoreManager
import com.memely.nostr.KeyUtils
import com.memely.nostr.NostrEventSigner
import com.memely.nostr.PublishResult
import com.memely.nostr.RelayEventTracker
import com.memely.util.SecureLog
import com.memely.ui.components.nostr.ComposeNoteDialog
import com.memely.ui.components.nostr.RelayStatusDialog
import com.memely.ui.components.SearchBar
import com.memely.ui.components.TemplateGrid
import com.memely.ui.components.TemplateTab
import com.memely.ui.components.TemplateTabBar
import com.memely.ui.tutorial.TutorialOverlay
import com.memely.ui.tutorial.TutorialScreen
import com.memely.ui.tutorial.TutorialManager
import com.memely.ui.tutorial.tutorialTarget
import com.memely.ui.viewmodels.TemplateGridScrollState
import com.memely.ui.viewmodels.NostrPostViewModel
import com.memely.nostr.Constants
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun HomeFeedScreen(
    pubkeyHex: String?,
    onTemplateSelected: (Uri) -> Unit
) {
    val context = LocalContext.current
    val appContainer = remember(context) { context.appContainer }
    val nostrPostViewModel: NostrPostViewModel = viewModel(
        factory = remember(appContainer) {
            viewModelFactory { NostrPostViewModel(appContainer.nostrNotePublisher) }
        }
    )
    val postState by nostrPostViewModel.postState.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    val templates by TemplateRepository.templatesFlow.collectAsState()
    val isLoading by TemplateRepository.isLoadingFlow.collectAsState()
    val error by TemplateRepository.errorFlow.collectAsState()
    val favorites by FavoritesManager.favoritesFlow.collectAsState()
    
    var searchQuery by remember { mutableStateOf("") }
    var selectedTab by remember { mutableStateOf(TemplateTab.ALL) }
    var shareUrl by remember { mutableStateOf<String?>(null) }
    var publishResult by remember { mutableStateOf<PublishResult?>(null) }
    
    // Get templates based on selected tab - recomputes when favorites change
    val displayedTemplates = remember(templates, selectedTab, searchQuery, favorites) {
        when (selectedTab) {
            TemplateTab.ALL -> TemplateRepository.searchTemplates(searchQuery)
            TemplateTab.FAVORITES -> TemplateRepository.searchFavoriteTemplates(context, searchQuery)
        }
    }
    
    LaunchedEffect(pubkeyHex) {
        FavoritesManager.initialize(context)
        if (pubkeyHex.isNullOrBlank()) {
            SecureLog.i("HomeFeedScreen: Blossom media fetch skipped; no signed-in pubkey")
        } else {
            SecureLog.i("HomeFeedScreen: Starting Blossom media fetch for ${pubkeyHex.take(8)}")
            TemplateRepository.fetchTemplates(
                pubkey = pubkeyHex,
                signEvent = { eventJson -> signBlossomAuthorization(eventJson, pubkeyHex) }
            )
        }
    }
    
    // Reset scroll position when switching tabs
    LaunchedEffect(selectedTab) {
        TemplateGridScrollState.reset()
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
                    onShare = { template -> shareUrl = template.url },
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
                        val templateUri = Uri.parse(Constants.getTemplateImageUrl(template.url))
                        onTemplateSelected(templateUri)
                    }
                )
            }
        }
        
        // Tutorial overlay for Home screen
        TutorialOverlay(currentScreen = TutorialScreen.HOME_FEED)
    }

    shareUrl?.let { imageUrl ->
        val posting = postState is NostrPostViewModel.PostState.Posting
        val errorMessage = (postState as? NostrPostViewModel.PostState.Error)?.message
        ComposeNoteDialog(
            imageUrl = imageUrl,
            isPosting = posting,
            errorMessage = errorMessage,
            onDismiss = {
                shareUrl = null
                nostrPostViewModel.reset()
            },
            onPost = { caption ->
                val activePubkey = KeyStoreManager.getPubkeyHex()
                if (activePubkey.isNullOrBlank()) {
                    nostrPostViewModel.setErrorState("Sign in before sharing media.")
                    return@ComposeNoteDialog
                }
                val useAmber = KeyStoreManager.isUsingAmber()
                val signEvent: suspend (String) -> String = if (useAmber) {
                    val packageName = KeyStoreManager.getAmberPackageName()
                    if (packageName.isNullOrBlank()) {
                        nostrPostViewModel.setErrorState("No external signer is configured.")
                        return@ComposeNoteDialog
                    }
                    AmberSignerManager.configure(activePubkey, packageName)
                    val amberSigner: suspend (String) -> String = { eventJson ->
                        val eventId = NostrEventSigner.calculateEventId(eventJson)
                        AmberSignerManager.signEvent(eventJson, eventId).event
                            ?: throw IllegalStateException("External signer did not return a signed event")
                    }
                    amberSigner
                } else {
                    val privateKeyHex = KeyStoreManager.exportNsecHex()
                    if (privateKeyHex.isNullOrBlank()) {
                        nostrPostViewModel.setErrorState("No local signing key is available.")
                        return@ComposeNoteDialog
                    }
                    val privateKey = privateKeyHex.hexToBytes()
                    if (!KeyUtils.publicKeyXOnlyHexFromPrivate(privateKey)
                            .equals(activePubkey, ignoreCase = true)
                    ) {
                        nostrPostViewModel.setErrorState("The local signing key does not match this account.")
                        return@ComposeNoteDialog
                    }
                    val localSigner: suspend (String) -> String = { eventJson ->
                        NostrEventSigner.signEventJson(eventJson, privateKey)
                    }
                    localSigner
                }
                nostrPostViewModel.publishNote(
                    content = caption,
                    imageUrl = imageUrl,
                    pubkeyHex = activePubkey,
                    signEvent = signEvent
                ) { eventId ->
                    // Keep the composer visible while relay acknowledgements are collected.
                    nostrPostViewModel.setPostingState()
                    coroutineScope.launch {
                        val deadline = System.currentTimeMillis() + 6_000L
                        while (!RelayEventTracker.isPublishComplete(eventId) &&
                            System.currentTimeMillis() < deadline
                        ) {
                            delay(200L)
                        }
                        RelayEventTracker.getPendingRelays(eventId).forEach { relay ->
                            RelayEventTracker.recordTimeout(eventId, relay)
                        }
                        val result = RelayEventTracker.getPublishResult(eventId)
                        RelayEventTracker.completePublish(eventId)
                        shareUrl = null
                        publishResult = result
                        nostrPostViewModel.reset()
                    }
                }
            }
        )
    }

    if (publishResult != null) {
        RelayStatusDialog(
            publishResult = publishResult,
            title = "Shared Note Status",
            dismissLabel = "Close",
            doneLabel = "Done",
            onDismiss = { publishResult = null },
            onExitEditor = { publishResult = null }
        )
    }
}

private fun String.hexToBytes(): ByteArray {
    require(length % 2 == 0) { "Private key hex must have an even number of characters" }
    return ByteArray(length / 2) { index ->
        substring(index * 2, index * 2 + 2).toInt(16).toByte()
    }
}
