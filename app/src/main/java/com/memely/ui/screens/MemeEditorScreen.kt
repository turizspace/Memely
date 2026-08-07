package com.memely.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.ModalBottomSheetLayout
import androidx.compose.material.ModalBottomSheetValue
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.rememberModalBottomSheetState
import androidx.compose.material.AlertDialog
import androidx.compose.material.Button
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.memely.di.appContainer
import com.memely.di.viewModelFactory
import com.memely.ui.viewmodels.MemeOverlayImage
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.memely.ui.components.editor.ColorPickerDialog
import com.memely.ui.components.editor.EditorControls
import com.memely.ui.components.editor.MemeCanvas
import com.memely.ui.components.editor.TextFormattingPanel
import com.memely.ui.components.editor.ImageEditingPanel
import com.memely.ui.components.editor.TemplateSelectorDialog
import com.memely.ui.utils.MemeFileSaver
import com.memely.ui.utils.OrientedImageDecoder
import com.memely.ui.viewmodels.MemeEditorViewModel
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import coil.compose.AsyncImage
import com.memely.ui.components.nostr.ComposeNoteDialog
import com.memely.ui.components.nostr.RelayStatusDialog
import com.memely.ui.viewmodels.BlossomUploadViewModel
import com.memely.ui.viewmodels.NostrPostViewModel
import com.memely.nostr.KeyStoreManager
import com.memely.nostr.NostrEventSigner
import com.memely.nostr.AmberSignerManager
import com.memely.nostr.NostrRepository
import com.memely.nostr.RelayEventTracker
import com.memely.nostr.RelayConnectionManager
import com.memely.nostr.PublishResult
import com.memely.util.SecureLog
import kotlinx.coroutines.delay
import java.io.File
import com.memely.ui.tutorial.TutorialOverlay
import com.memely.ui.tutorial.TutorialScreen
import com.memely.ui.tutorial.TutorialTargetRegistry
import com.memely.ui.tutorial.tutorialTarget

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun MemeEditorScreen(
    imageUri: Uri,
    onDone: (savedPath: String) -> Unit,
    onNavigateToHomeFeed: () -> Unit = {}
) {
    val context = LocalContext.current
    val appContainer = remember(context) { context.appContainer }
    val coroutineScope = rememberCoroutineScope()
    val blossomViewModel: BlossomUploadViewModel = viewModel(
        factory = remember(appContainer) {
            viewModelFactory { BlossomUploadViewModel(appContainer.blossomClient) }
        }
    )
    val nostrPostViewModel: NostrPostViewModel = viewModel(
        factory = remember(appContainer) {
            viewModelFactory { NostrPostViewModel(appContainer.nostrNotePublisher) }
        }
    )
    val editorViewModel: MemeEditorViewModel = viewModel()

    // Manage persistent relay connections during meme editing
    var showRelayStatus by remember { mutableStateOf(false) }
    var publishResult: PublishResult? by remember { mutableStateOf(null) }
    
    LaunchedEffect(Unit) {
        // Initialize persistent relay connections
        RelayConnectionManager.initialize()
        
        // Ensure relays are connected
        try {
            RelayConnectionManager.ensureConnected()
        } catch (e: Exception) {
        }
    }
    
    DisposableEffect(Unit) {
        onDispose {
            // Release relay connection reference when leaving meme editor
            RelayConnectionManager.release()
        }
    }

    val screenDensity = context.resources.displayMetrics.density

    var showColorPicker by remember { mutableStateOf(false) }
    var showComposeDialog by remember { mutableStateOf(false) }
    var showTextFormattingPanel by remember { mutableStateOf(false) }
    var showImageEditingPanel by remember { mutableStateOf(false) }
    var showTemplateSelector by remember { mutableStateOf(false) }
    var isSavingToDevice by remember { mutableStateOf(false) }
    var uploadedImageUrl by remember { mutableStateOf<String?>(null) }
    var savedMemeFile by remember { mutableStateOf<File?>(null) }
    var showExitConfirmation by remember { mutableStateOf(false) }
    
    // Retry support state - store posting context for retries
    var lastMemeCaption by remember { mutableStateOf<String?>(null) }
    var lastMemeUrl by remember { mutableStateOf<String?>(null) }
    var lastPubkeyHex by remember { mutableStateOf<String?>(null) }
    var lastPrivKeyBytes by remember { mutableStateOf<ByteArray?>(null) }
    var lastIsUsingAmber by remember { mutableStateOf(false) }
    var isRetryingPost by remember { mutableStateOf(false) }
    
    // Track if any panel is currently open
    val isPanelOpen = showColorPicker || showTextFormattingPanel || showImageEditingPanel || 
                      showTemplateSelector || showComposeDialog || showRelayStatus
    
    // BackHandler to close panels before exiting editor
    BackHandler(enabled = isPanelOpen) {
        when {
            showColorPicker -> showColorPicker = false
            showTextFormattingPanel -> showTextFormattingPanel = false
            showImageEditingPanel -> showImageEditingPanel = false
            showTemplateSelector -> showTemplateSelector = false
            showComposeDialog -> {
                showComposeDialog = false
                uploadedImageUrl = null
                nostrPostViewModel.reset()
            }
            showRelayStatus -> showRelayStatus = false
        }
    }
    
    // BackHandler to show exit confirmation when no panels are open
    BackHandler(enabled = !isPanelOpen) {
        showExitConfirmation = true
    }

    // Track Blossom upload state separately
    val blossomUploadState by blossomViewModel.uploadState.collectAsState()
    val isUploadingToBlossom = blossomUploadState is BlossomUploadViewModel.UploadState.Uploading
    
    // Handle Blossom upload errors
    LaunchedEffect(blossomUploadState) {
        if (blossomUploadState is BlossomUploadViewModel.UploadState.Error) {
            // Reset state
            blossomViewModel.reset()
        }
    }

    // Overlay image picker
    val overlayLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            val dimensions = OrientedImageDecoder.bounds(context, uri) ?: return@let

            // Calculate an initial top-left position so the overlay appears centered over the displayed image.
            // The ViewModel stores layer.position as the top-left offset in container coordinates.

            // Use the model's default display width (so we don't duplicate the literal 150.dp)
            val defaultDisplayDpValue = MemeOverlayImage(
                uri = uri,
                originalWidth = dimensions.width,
                originalHeight = dimensions.height,
                position = androidx.compose.ui.geometry.Offset(0f, 0f)
            ).displayWidth.value

            val displayWidthPx = defaultDisplayDpValue * screenDensity

            // Compute center of displayed image in container coordinates
            val centerX = editorViewModel.imageOffsetX + (editorViewModel.baseImageSize.width / 2f)
            val centerY = editorViewModel.imageOffsetY + (editorViewModel.baseImageSize.height / 2f)

            // Convert center to top-left by subtracting half of display size (so overlay center aligns)
            val initialX = centerX - (displayWidthPx / 2f)
            val displayHeightPx = displayWidthPx * (dimensions.height.toFloat() / dimensions.width.toFloat())
            val initialY = centerY - (displayHeightPx / 2f)

            editorViewModel.addOverlay(
                uri,
                dimensions.width,
                dimensions.height,
                initialPosition = androidx.compose.ui.geometry.Offset(initialX, initialY),
                density = screenDensity
            )
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                // Title bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Meme Editor", style = MaterialTheme.typography.h5)
                    IconButton(onClick = {
                        onDone("")
                    }) {
                        Icon(Icons.Default.Close, contentDescription = "Close Editor")
                    }
                }
            },
            bottomBar = {
                EditorControls(
                    canAddText = true,
                    onAddTopText = { editorViewModel.addTopText(screenDensity) },
                    onAddBottomText = { editorViewModel.addBottomText(screenDensity) },
                    onAddImage = {
                        overlayLauncher.launch("image/*")
                    },
                    onNavigateToHomeFeed = {
                        // Show template selector dialog to add template as layer
                        showTemplateSelector = true
                    },
                    canChangeColor = editorViewModel.selectedIsText && editorViewModel.selectedLayerIndex != null,
                    onChangeColor = {
                        showColorPicker = true
                    },
                    canDelete = editorViewModel.selectedLayerIndex != null,
                    onDelete = {
                        editorViewModel.deleteSelected()
                    },
                    isSaving = editorViewModel.isSaving,
                    onSave = {
                        editorViewModel.updateSavingState(true)
                        coroutineScope.launch(Dispatchers.IO) {
                            MemeFileSaver.saveMeme(
                                context = context,
                                imageUri = editorViewModel.localImageUri ?: imageUri, // Use cached local URI if available
                                texts = editorViewModel.texts,
                                overlays = editorViewModel.overlays,
                                baseImageSize = editorViewModel.baseImageSize,
                                originalImageWidth = editorViewModel.originalImageWidth,
                                originalImageHeight = editorViewModel.originalImageHeight,
                                imageOffsetX = editorViewModel.imageOffsetX,
                                imageOffsetY = editorViewModel.imageOffsetY,
                                onSuccess = { path ->
                                    coroutineScope.launch(Dispatchers.Main) {
                                        editorViewModel.updateSavingState(false)
                                        onDone(path)
                                    }
                                },
                                onError = {
                                    coroutineScope.launch(Dispatchers.Main) {
                                        editorViewModel.updateSavingState(false)
                                        onDone("")
                                    }
                                }
                            )
                        }
                    },
                    onPostToNostr = {
                        // Start the Post to Nostr workflow with relay connection management
                        editorViewModel.updateSavingState(true)
                        
                        coroutineScope.launch(Dispatchers.IO) {
                            // Verify connection health before starting
                            try {
                                if (!RelayConnectionManager.verifyConnectionHealth()) {
                                    RelayConnectionManager.ensureConnected()
                            }
                            } catch (e: Exception) {
                            }
                            
                            // Step 1: Save the meme
                            MemeFileSaver.saveMeme(
                                context = context,
                                imageUri = editorViewModel.localImageUri ?: imageUri, // Use cached local URI if available
                                texts = editorViewModel.texts,
                                overlays = editorViewModel.overlays,
                                baseImageSize = editorViewModel.baseImageSize,
                                originalImageWidth = editorViewModel.originalImageWidth,
                                originalImageHeight = editorViewModel.originalImageHeight,
                                imageOffsetX = editorViewModel.imageOffsetX,
                                imageOffsetY = editorViewModel.imageOffsetY,
                                onSuccess = { path ->
                                    coroutineScope.launch(Dispatchers.Main) uploadWorkflow@{
                                        editorViewModel.updateSavingState(false)
                                        val savedUri = android.net.Uri.parse(path)
                                        savedMemeFile = null // Clear file reference since we're using URI
                                        
                                        // Step 2: Upload to Blossom
                                        val pubkeyHex = KeyStoreManager.getPubkeyHex()
                                        val isUsingAmber = KeyStoreManager.isUsingAmber()
                                        val privKeyHex = if (!isUsingAmber) KeyStoreManager.exportNsecHex() else null
                                        
                                        if (pubkeyHex.isNullOrBlank()) {
                                            // TODO: Show error to user
                                            return@uploadWorkflow
                                        }
                                        
                                        if (!isUsingAmber && privKeyHex.isNullOrBlank()) {
                                            // TODO: Show error to user
                                            return@uploadWorkflow
                                        }
                                        
                                        // Configure Amber if using external signer
                                        if (isUsingAmber) {
                                            val packageName = KeyStoreManager.getAmberPackageName()
                                            if (packageName != null) {
                                                AmberSignerManager.configure(pubkeyHex, packageName)
                                            } else {
                                                return@uploadWorkflow
                                            }
                                        }
                                        
                                        
                                        blossomViewModel.uploadFile(
                                            context = context,
                                            uri = savedUri,
                                            contentType = "image/jpeg",
                                            pubkeyHex = pubkeyHex,
                                            signEventFunc = { eventJson ->
                                                if (isUsingAmber) {
                                                    // Calculate event ID from unsigned event
                                                    val eventId = NostrEventSigner.calculateEventId(eventJson)
                                                    
                                                    // Add the ID to the event JSON before sending to Amber
                                                    val jsonObj = org.json.JSONObject(eventJson)
                                                    jsonObj.put("id", eventId)
                                                    val eventWithId = jsonObj.toString()
                                                    
                                                    
                                                    
                                                    try {
                                                        val result = AmberSignerManager.signEvent(eventWithId, eventId)
                                                        if (result.event.isNullOrBlank()) {
                                                            throw Exception("Amber did not return a signed event")
                                                        }
                                                        result.event
                                                    } catch (e: Exception) {
                                                        throw e
                                                    }
                                                } else {
                                                    // Use local nsec to sign
                                                    val privKeyBytes = privKeyHex!!.hexToBytes()
                                                    val jsonObj = org.json.JSONObject(eventJson)
                                                    NostrEventSigner.signEvent(
                                                        kind = 24242,
                                                        content = jsonObj.optString("content", ""),
                                                        tags = jsonObj.optJSONArray("tags")?.let { arr ->
                                                            (0 until arr.length()).map { i ->
                                                                val tagArr = arr.getJSONArray(i)
                                                                (0 until tagArr.length()).map { j ->
                                                                    tagArr.getString(j)
                                                                }
                                                            }
                                                        } ?: emptyList(),
                                                        pubkeyHex = pubkeyHex,
                                                        privKeyBytes = privKeyBytes
                                                    )
                                                }
                                            },
                                            onSuccess = { url ->
                                                // Step 3: Show compose dialog
                                                uploadedImageUrl = url
                                                showComposeDialog = true
                                            }
                                        )
                                    }
                                },
                                onError = {
                                    coroutineScope.launch(Dispatchers.Main) {
                                        editorViewModel.updateSavingState(false)
                                    }
                                }
                            )
                        }
                    },
                    isPostingToNostr = editorViewModel.isSaving || isUploadingToBlossom,
                    onSaveToDevice = {
                        isSavingToDevice = true
                        coroutineScope.launch(Dispatchers.IO) {
                            MemeFileSaver.saveMeme(
                                context = context,
                                imageUri = editorViewModel.localImageUri ?: imageUri, // Use cached local URI if available
                                texts = editorViewModel.texts,
                                overlays = editorViewModel.overlays,
                                baseImageSize = editorViewModel.baseImageSize,
                                originalImageWidth = editorViewModel.originalImageWidth,
                                originalImageHeight = editorViewModel.originalImageHeight,
                                imageOffsetX = editorViewModel.imageOffsetX,
                                imageOffsetY = editorViewModel.imageOffsetY,
                                onSuccess = { _ ->
                                    coroutineScope.launch(Dispatchers.Main) {
                                        isSavingToDevice = false
                                    }
                                },
                                onError = {
                                    coroutineScope.launch(Dispatchers.Main) {
                                        isSavingToDevice = false
                                    }
                                }
                            )
                        }
                    },
                    isSavingToDevice = isSavingToDevice,
                    onShowTextFormatting = { showTextFormattingPanel = true },
                    onShowImageEditing = { showImageEditingPanel = true },
                    selectedIsText = editorViewModel.selectedIsText && editorViewModel.selectedLayerIndex != null,
                    selectedIsImage = !editorViewModel.selectedIsText && editorViewModel.selectedLayerIndex != null,
                    onDuplicateLayer = { editorViewModel.duplicateSelected() },
                    onBringLayerForward = { editorViewModel.bringSelectedForward() },
                    onSendLayerBackward = { editorViewModel.sendSelectedBackward() },
                    onToggleLayerLock = { editorViewModel.toggleSelectedLock() },
                    selectedLayerLocked = editorViewModel.getSelectedText()?.locked
                        ?: editorViewModel.getSelectedImage()?.locked
                        ?: false,
                    onGloballyPositioned = {
                        // When the controls are laid out, re-register all tutorial targets
                        // to ensure their positions are correctly captured.
                        TutorialTargetRegistry.repositionAllTargets()
                    },
                    outlineWidth = editorViewModel.getSelectedText()?.outlineWidth ?: 0.dp,
                    onOutlineWidthChange = { editorViewModel.updateSelectedTextOutlineWidth(it) }
                )
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                MemeCanvas(
                    baseImageUri = imageUri,
                    viewModel = editorViewModel,
                    modifier = Modifier
                        .fillMaxSize()
                        .tutorialTarget("meme_canvas")
                )
            }
        }

        // Tutorial overlay for Meme Editor - placed outside Scaffold to see all targets
        TutorialOverlay(currentScreen = TutorialScreen.MEME_EDITOR)
    }

    // Color picker dialog
    if (showColorPicker && editorViewModel.selectedIsText && editorViewModel.selectedLayerIndex != null) {
        ColorPickerDialog(
            onDismiss = { showColorPicker = false },
            onColorSelected = { color ->
                editorViewModel.updateSelectedTextColor(color)
                showColorPicker = false
            }
        )
    }

    // Text Formatting Panel
    if (showTextFormattingPanel) {
        val selectedText = editorViewModel.getSelectedText()
        if (selectedText != null) {
            val textFormattingSheetState = rememberModalBottomSheetState(
                initialValue = ModalBottomSheetValue.Expanded
            )
            
            // Dismiss when state changes to hidden
            if (!textFormattingSheetState.isVisible) {
                LaunchedEffect(Unit) {
                    showTextFormattingPanel = false
                }
            }
            
            ModalBottomSheetLayout(
                sheetContent = {
                    TextFormattingPanel(
                        fontSize = selectedText.fontSize.value,
                        fontFamily = selectedText.fontFamily,
                        fontWeight = selectedText.fontWeight,
                        fontStyle = selectedText.fontStyle,
                        textAlign = selectedText.textAlign,
                        alpha = selectedText.alpha,
                        shadowEnabled = selectedText.shadowEnabled,
                        shadowBlur = selectedText.shadowBlur.value,
                        shadowOffset = selectedText.shadowOffsetX.value,
                        onFontSizeChange = { editorViewModel.updateSelectedTextFontSize(it.sp) },
                        onFontFamilyChange = { editorViewModel.updateSelectedTextFontFamily(it) },
                        onFontWeightChange = { editorViewModel.updateSelectedTextFontWeight(it) },
                        onFontStyleChange = { editorViewModel.updateSelectedTextFontStyle(it) },
                        onTextAlignChange = { editorViewModel.updateSelectedTextAlign(it) },
                        onAlphaChange = { editorViewModel.updateSelectedTextAlpha(it) },
                        onShadowEnabledChange = { editorViewModel.updateSelectedTextShadowEnabled(it) },
                        onShadowBlurChange = { editorViewModel.updateSelectedTextShadowBlur(it.dp) },
                        onShadowOffsetChange = { editorViewModel.updateSelectedTextShadowOffset(it.dp) }
                    )
                },
                sheetState = textFormattingSheetState,
                scrimColor = Color.Black.copy(alpha = 0.32f)
            ) {}
        }
    }

    // Image Editing Panel
    if (showImageEditingPanel) {
        val selectedImage = editorViewModel.getSelectedImage()
        if (selectedImage != null) {
            val imageEditingSheetState = rememberModalBottomSheetState(
                initialValue = ModalBottomSheetValue.Expanded
            )
            
            // Dismiss when state changes to hidden
            if (!imageEditingSheetState.isVisible) {
                LaunchedEffect(Unit) {
                    showImageEditingPanel = false
                }
            }
            
            ModalBottomSheetLayout(
                sheetContent = {
                    ImageEditingPanel(
                        cornerRadius = selectedImage.cornerRadius.value,
                        alpha = selectedImage.alpha,
                        rotation = selectedImage.rotation,
                        scale = selectedImage.scale,
                        onCornerRadiusChange = { editorViewModel.updateSelectedImageCornerRadius(it.dp) },
                        onAlphaChange = { editorViewModel.updateSelectedImageAlpha(it) },
                        onRotationChange = { editorViewModel.updateSelectedImageRotation(it) },
                        onScaleChange = { editorViewModel.updateSelectedImageScale(it) },
                        onFlipHorizontal = { editorViewModel.flipSelectedImageHorizontal() },
                        onFlipVertical = { editorViewModel.flipSelectedImageVertical() }
                    )
                },
                sheetState = imageEditingSheetState,
                scrimColor = Color.Black.copy(alpha = 0.32f)
            ) {}
        }
    }

    // Compose note dialog for posting to Nostr
    if (showComposeDialog && uploadedImageUrl != null) {
        val postState by nostrPostViewModel.postState.collectAsState()
        val isPosting = postState is NostrPostViewModel.PostState.Posting
        
        ComposeNoteDialog(
            imageUrl = uploadedImageUrl!!,
            initialCaption = "",
            isPosting = isPosting,
            onDismiss = {
                showComposeDialog = false
                uploadedImageUrl = null
                nostrPostViewModel.reset()
            },
            onPost = { caption ->
                // Get keys
                val pubkeyHex = KeyStoreManager.getPubkeyHex()
                val isUsingAmber = KeyStoreManager.isUsingAmber()
                val privKeyHex = if (!isUsingAmber) KeyStoreManager.exportNsecHex() else null
                
                if (pubkeyHex.isNullOrBlank()) {
                    return@ComposeNoteDialog
                }
                
                if (!isUsingAmber && privKeyHex.isNullOrBlank()) {
                    return@ComposeNoteDialog
                }
                
                // Store posting context for retry support
                lastMemeCaption = caption
                lastMemeUrl = uploadedImageUrl
                lastPubkeyHex = pubkeyHex
                lastPrivKeyBytes = if (!isUsingAmber && privKeyHex != null) privKeyHex.hexToBytes() else null
                lastIsUsingAmber = isUsingAmber
                
                // Configure Amber if using external signer
                if (isUsingAmber) {
                    val packageName = KeyStoreManager.getAmberPackageName()
                    if (packageName != null) {
                        AmberSignerManager.configure(pubkeyHex, packageName)
                    } else {
                        return@ComposeNoteDialog
                    }
                }
                
                if (isUsingAmber) {
                    // Use Amber to sign and publish
                    coroutineScope.launch(Dispatchers.IO) {
                        try {
                            nostrPostViewModel.setPostingState()
                            
                            // Build content with image URL
                            val fullContent = if (caption.isNotBlank()) {
                                "$caption\n\n${uploadedImageUrl!!}"
                            } else {
                                uploadedImageUrl!!
                            }
                            
                            // Build tags with image URL
                            val tags = mutableListOf<List<String>>()
                            tags.add(listOf("imeta", "url ${uploadedImageUrl!!}"))
                            tags.add(listOf("url", uploadedImageUrl!!))
                            tags.add(listOf("client", "Memely"))
                            tags.add(listOf("t", "meme"))
                            tags.add(listOf("t", "memely"))
                            
                            // Create unsigned event for Amber
                            val unsignedEvent = org.json.JSONObject().apply {
                                put("kind", 1)
                                put("created_at", System.currentTimeMillis() / 1000L)
                                put("tags", org.json.JSONArray(tags.map { org.json.JSONArray(it) }))
                                put("content", fullContent)
                                put("pubkey", pubkeyHex)
                            }
                            
                            // Calculate event ID before sending to Amber
                            val eventId = NostrEventSigner.calculateEventId(unsignedEvent.toString())
                            
                            // Sign with Amber (send without id, let Amber calculate it)
                            val result = AmberSignerManager.signEvent(
                                unsignedEvent.toString(),
                                eventId
                            )
                            
                            if (result.event.isNullOrBlank()) {
                                throw Exception("Amber signing failed")
                            }
                            
                            // Extract the actual event ID from the signed event
                            val signedEventJson = org.json.JSONObject(result.event)
                            val actualEventId = signedEventJson.getString("id")
                            
                            
                            
                            // Initialize relay tracking with the ACTUAL event ID from signed event
                            val relayUrls = NostrRepository.relayPool.getCurrentRelays()
                            RelayEventTracker.initializeEventTracking(actualEventId, relayUrls)
                            
                            // Publish to relays and track responses
                            val eventMessage = """["EVENT",${result.event}]"""
                            com.memely.nostr.NostrRepository.publishEvent(eventMessage)
                            
                            // Wait for relay responses (timeout after 5 seconds)
                            delay(6000)
                            
                            // Mark any remaining pending relays as timed out
                            RelayEventTracker.getPendingRelays(actualEventId).forEach { relay ->
                                RelayEventTracker.recordTimeout(actualEventId, relay)
                            }
                            
                            // Get the publish result using the ACTUAL event ID
                            val result_final = RelayEventTracker.getPublishResult(actualEventId)
                            RelayEventTracker.completePublish(actualEventId)
                            
                            coroutineScope.launch(Dispatchers.Main) {
                                nostrPostViewModel.setSuccessState(actualEventId)
                                publishResult = result_final
                                showRelayStatus = true
                                showComposeDialog = false
                                uploadedImageUrl = null
                                nostrPostViewModel.reset()
                            }
                        } catch (e: Exception) {
                            coroutineScope.launch(Dispatchers.Main) {
                                nostrPostViewModel.setErrorState("Amber signing failed: ${e.message}")
                            }
                        }
                    }
                } else {
                    // Use local nsec to sign and publish
                    val privKeyBytes = privKeyHex!!.hexToBytes()
                    
                    nostrPostViewModel.publishNote(
                        content = caption,
                        imageUrl = uploadedImageUrl!!,
                        pubkeyHex = pubkeyHex,
                        privKeyBytes = privKeyBytes,
                        onSuccess = { actualEventId ->
                            coroutineScope.launch(Dispatchers.IO) {
                                delay(5000)
                                
                                // Mark any remaining pending relays as timed out
                                RelayEventTracker.getPendingRelays(actualEventId).forEach { relay ->
                                    RelayEventTracker.recordTimeout(actualEventId, relay)
                                }
                                
                                val result_final = RelayEventTracker.getPublishResult(actualEventId)
                                RelayEventTracker.completePublish(actualEventId)

                                coroutineScope.launch(Dispatchers.Main) {
                                    publishResult = result_final
                                    showRelayStatus = true
                                    showComposeDialog = false
                                    uploadedImageUrl = null
                                    nostrPostViewModel.reset()
                                }
                            }
                        }
                    )
                }
            }
        )
    }

    // Relay status dialog
    if (showRelayStatus && publishResult != null) {
        RelayStatusDialog(
            publishResult = publishResult,
            onDismiss = {
                showRelayStatus = false
            },
            onRetry = if (publishResult?.acceptanceRate!! < 1.0f) {
                {
                    showRelayStatus = false
                    isRetryingPost = true
                    
                    // Retry posting with stored context
                    if (lastMemeCaption != null && lastMemeUrl != null && lastPubkeyHex != null) {
                        if (lastIsUsingAmber) {
                            coroutineScope.launch(Dispatchers.IO) {
                                try {
                                    nostrPostViewModel.setPostingState()
                                    
                                    // Build content with image URL
                                    val fullContent = if (lastMemeCaption!!.isNotBlank()) {
                                        "${lastMemeCaption!!}\n\n${lastMemeUrl!!}"
                                    } else {
                                        lastMemeUrl!!
                                    }
                                    
                                    // Build tags with image URL
                                    val tags = mutableListOf<List<String>>()
                                    tags.add(listOf("imeta", "url ${lastMemeUrl!!}"))
                                    tags.add(listOf("url", lastMemeUrl!!))
                                    tags.add(listOf("client", "Memely"))
                                    tags.add(listOf("t", "meme"))
                                    tags.add(listOf("t", "memely"))
                                    
                                    // Create unsigned event for Amber
                                    val unsignedEvent = org.json.JSONObject().apply {
                                        put("kind", 1)
                                        put("created_at", System.currentTimeMillis() / 1000L)
                                        put("tags", org.json.JSONArray(tags.map { org.json.JSONArray(it) }))
                                        put("content", fullContent)
                                        put("pubkey", lastPubkeyHex)
                                    }
                                    
                                    // Calculate event ID before sending to Amber
                                    val eventId = NostrEventSigner.calculateEventId(unsignedEvent.toString())
                                    
                                    // Sign with Amber
                                    val result = AmberSignerManager.signEvent(
                                        unsignedEvent.toString(),
                                        eventId
                                    )
                                    
                                    if (result.event.isNullOrBlank()) {
                                        throw Exception("Amber signing failed")
                                    }
                                    
                                    // Extract the actual event ID from the signed event
                                    val signedEventJson = org.json.JSONObject(result.event)
                                    val actualEventId = signedEventJson.getString("id")
                                    
                                    // Initialize relay tracking with the ACTUAL event ID from signed event
                                    val relayUrls = NostrRepository.relayPool.getCurrentRelays()
                                    RelayEventTracker.initializeEventTracking(actualEventId, relayUrls)
                                    
                                    // Publish to relays and track responses
                                    val eventMessage = """["EVENT",${result.event}]"""
                                    com.memely.nostr.NostrRepository.publishEvent(eventMessage)
                                    
                                    // Wait for relay responses (timeout after 5 seconds)
                                    delay(6000)
                                    
                                    // Mark any remaining pending relays as timed out
                                    RelayEventTracker.getPendingRelays(actualEventId).forEach { relay ->
                                        RelayEventTracker.recordTimeout(actualEventId, relay)
                                    }
                                    
                                    // Get the publish result using the ACTUAL event ID
                                    val result_final = RelayEventTracker.getPublishResult(actualEventId)
                                    RelayEventTracker.completePublish(actualEventId)
                                    
                                    coroutineScope.launch(Dispatchers.Main) {
                                        nostrPostViewModel.setSuccessState(actualEventId)
                                        publishResult = result_final
                                        isRetryingPost = false
                                        nostrPostViewModel.reset()
                                    }
                                } catch (e: Exception) {
                                    coroutineScope.launch(Dispatchers.Main) {
                                        nostrPostViewModel.setErrorState("Retry failed: ${e.message}")
                                        isRetryingPost = false
                                    }
                                }
                            }
                        } else {
                            // Use local nsec to sign and publish
                            if (lastPrivKeyBytes != null) {
                                nostrPostViewModel.publishNote(
                                    content = lastMemeCaption!!,
                                    imageUrl = lastMemeUrl!!,
                                    pubkeyHex = lastPubkeyHex!!,
                                    privKeyBytes = lastPrivKeyBytes!!,
                                    onSuccess = { actualEventId ->
                                        coroutineScope.launch(Dispatchers.IO) {
                                            delay(5000)
                                            
                                            // Mark any remaining pending relays as timed out
                                            RelayEventTracker.getPendingRelays(actualEventId).forEach { relay ->
                                                RelayEventTracker.recordTimeout(actualEventId, relay)
                                            }
                                            
                                            val result_final = RelayEventTracker.getPublishResult(actualEventId)
                                            RelayEventTracker.completePublish(actualEventId)

                                            coroutineScope.launch(Dispatchers.Main) {
                                                publishResult = result_final
                                                isRetryingPost = false
                                                nostrPostViewModel.reset()
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            } else null,
            onExitEditor = {
                // Exit the editor and return to previous screen
                onDone(savedMemeFile?.absolutePath ?: "")
            }
        )
    }

    // Template selector dialog for adding templates as layers
    if (showTemplateSelector) {
        TemplateSelectorDialog(
            onDismiss = { showTemplateSelector = false },
            onTemplateSelected = { templateUri ->
                coroutineScope.launch(Dispatchers.IO) {
                    try {
                        // Check if it's a network URL or local content URI
                        val isNetworkUrl = templateUri.scheme == "http" || templateUri.scheme == "https"
                        
                        val dimensions = if (isNetworkUrl) {
                            // For network URLs, use default dimensions (Coil will handle actual loading)
                            // Most meme templates are roughly 500x500 to 1000x1000
                            Pair(800, 800)
                        } else {
                            // For local content URIs, use the same EXIF-aware size
                            // that Coil shows in the editor and the saver exports.
                            OrientedImageDecoder.bounds(context, templateUri)
                                ?.let { Pair(it.width, it.height) }
                                ?: Pair(800, 800)
                        }

                        coroutineScope.launch(Dispatchers.Main) {
                            // Calculate initial position centered on canvas
                            val defaultDisplayDpValue = MemeOverlayImage(
                                uri = templateUri,
                                originalWidth = dimensions.first,
                                originalHeight = dimensions.second,
                                position = androidx.compose.ui.geometry.Offset(0f, 0f)
                            ).displayWidth.value

                            val displayWidthPx = defaultDisplayDpValue * screenDensity

                            val centerX = editorViewModel.imageOffsetX + (editorViewModel.baseImageSize.width / 2f)
                            val centerY = editorViewModel.imageOffsetY + (editorViewModel.baseImageSize.height / 2f)

                            val initialX = centerX - (displayWidthPx / 2f)
                            val displayHeightPx = displayWidthPx * (dimensions.second.toFloat() / dimensions.first.toFloat())
                            val initialY = centerY - (displayHeightPx / 2f)

                            // Add template as overlay layer
                            editorViewModel.addOverlay(
                                templateUri,
                                dimensions.first,
                                dimensions.second,
                                initialPosition = androidx.compose.ui.geometry.Offset(initialX, initialY),
                                density = screenDensity
                            )
                            
                            showTemplateSelector = false
                        }
                    } catch (e: Exception) {
                        SecureLog.e("MemeEditorScreen: Failed to add template overlay", e)
                        coroutineScope.launch(Dispatchers.Main) {
                            showTemplateSelector = false
                        }
                    }
                }
            }
        )
    }
    
    // Exit confirmation dialog
    if (showExitConfirmation) {
        AlertDialog(
            onDismissRequest = { showExitConfirmation = false },
            title = { Text("Exit Meme Editor?") },
            text = { Text("Are you sure you want to exit editing this meme? Any unsaved changes will be lost.") },
            confirmButton = {
                Button(
                    onClick = {
                        showExitConfirmation = false
                        onDone("")
                    }
                ) {
                    Text("Discard")
                }
            },
            dismissButton = {
                Button(
                    onClick = { showExitConfirmation = false }
                ) {
                    Text("Keep Editing")
                }
            }
        )
    }
}

// Helper extension to convert hex string to bytes
private fun String.hexToBytes(): ByteArray {
    val clean = this.trim().removePrefix("0x")
    val out = ByteArray(clean.length / 2)
    for (i in out.indices) {
        val idx = i * 2
        out[i] = clean.substring(idx, idx + 2).toInt(16).toByte()
    }
    return out
}
