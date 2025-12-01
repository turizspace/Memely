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
    val coroutineScope = rememberCoroutineScope()
    val viewModel = remember { MemeEditorViewModel() }
    val blossomViewModel = remember { BlossomUploadViewModel() }
    val nostrPostViewModel = remember { NostrPostViewModel() }

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

    // Screen density and shared padding (8.dp -> px) computed in composable scope
    val screenDensity = context.resources.displayMetrics.density
    val textPaddingPx = 8f * screenDensity

    var showColorPicker by remember { mutableStateOf(false) }
    var showComposeDialog by remember { mutableStateOf(false) }
    var showTextFormattingPanel by remember { mutableStateOf(false) }
    var showImageEditingPanel by remember { mutableStateOf(false) }
    var showTemplateSelector by remember { mutableStateOf(false) }
    var isSavingToDevice by remember { mutableStateOf(false) }
    var uploadedImageUrl by remember { mutableStateOf<String?>(null) }
    var savedMemeFile by remember { mutableStateOf<File?>(null) }
    var showExitConfirmation by remember { mutableStateOf(false) }
    
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
    val blossomUploadState = blossomViewModel.uploadState
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
            // Get original image dimensions
            val inputStream = context.contentResolver.openInputStream(uri)
            val options = android.graphics.BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            android.graphics.BitmapFactory.decodeStream(inputStream, null, options)
            inputStream?.close()

            // Calculate an initial top-left position so the overlay appears centered over the displayed image.
            // The ViewModel stores layer.position as the top-left offset in container coordinates.

            // Use the model's default display width (so we don't duplicate the literal 150.dp)
            val defaultDisplayDpValue = MemeOverlayImage(
                uri = uri,
                originalWidth = options.outWidth,
                originalHeight = options.outHeight,
                position = androidx.compose.ui.geometry.Offset(0f, 0f)
            ).displayWidth.value

            val displayWidthPx = defaultDisplayDpValue * screenDensity

            // Compute center of displayed image in container coordinates
            val centerX = viewModel.imageOffsetX + (viewModel.baseImageSize.width / 2f)
            val centerY = viewModel.imageOffsetY + (viewModel.baseImageSize.height / 2f)

            // Convert center to top-left by subtracting half of display size (so overlay center aligns)
            val initialX = centerX - (displayWidthPx / 2f)
            val displayHeightPx = displayWidthPx * (options.outHeight.toFloat() / options.outWidth.toFloat())
            val initialY = centerY - (displayHeightPx / 2f)

            viewModel.addOverlay(
                uri,
                options.outWidth,
                options.outHeight,
                androidx.compose.ui.geometry.Offset(initialX, initialY)
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
                    onAddText = {
                        // Add text at center of the displayed image (accounting for offset)
                        val centerX = viewModel.imageOffsetX + (viewModel.baseImageSize.width / 2f)
                        val centerY = viewModel.imageOffsetY + (viewModel.baseImageSize.height / 2f)

                        // Account for the 8.dp padding used inside TextLayerBox so stored top-left
                        // results in the visible text content appearing at the center.
                        viewModel.addText(
                            androidx.compose.ui.geometry.Offset(
                                centerX - textPaddingPx,
                                centerY - textPaddingPx
                            )
                        )
                    },
                    onAddImage = {
                        overlayLauncher.launch("image/*")
                    },
                    onNavigateToHomeFeed = {
                        // Show template selector dialog to add template as layer
                        showTemplateSelector = true
                    },
                    canChangeColor = viewModel.selectedIsText && viewModel.selectedLayerIndex != null,
                    onChangeColor = {
                        showColorPicker = true
                    },
                    canDelete = viewModel.selectedLayerIndex != null,
                    onDelete = {
                        viewModel.deleteSelected()
                    },
                    isSaving = viewModel.isSaving,
                    onSave = {
                        viewModel.isSaving = true
                        coroutineScope.launch(Dispatchers.IO) {
                            MemeFileSaver.saveMeme(
                                context = context,
                                imageUri = viewModel.localImageUri ?: imageUri, // Use cached local URI if available
                                texts = viewModel.texts,
                                overlays = viewModel.overlays,
                                baseImageSize = viewModel.baseImageSize,
                                originalImageWidth = viewModel.originalImageWidth,
                                originalImageHeight = viewModel.originalImageHeight,
                                imageOffsetX = viewModel.imageOffsetX,
                                imageOffsetY = viewModel.imageOffsetY,
                                onSuccess = { path ->
                                    coroutineScope.launch(Dispatchers.Main) {
                                        viewModel.isSaving = false
                                        onDone(path)
                                    }
                                },
                                onError = {
                                    coroutineScope.launch(Dispatchers.Main) {
                                        viewModel.isSaving = false
                                        onDone("")
                                    }
                                }
                            )
                        }
                    },
                    onPostToNostr = {
                        // Start the Post to Nostr workflow with relay connection management
                        viewModel.isSaving = true
                        
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
                                imageUri = viewModel.localImageUri ?: imageUri, // Use cached local URI if available
                                texts = viewModel.texts,
                                overlays = viewModel.overlays,
                                baseImageSize = viewModel.baseImageSize,
                                originalImageWidth = viewModel.originalImageWidth,
                                originalImageHeight = viewModel.originalImageHeight,
                                imageOffsetX = viewModel.imageOffsetX,
                                imageOffsetY = viewModel.imageOffsetY,
                                onSuccess = { path ->
                                    coroutineScope.launch(Dispatchers.Main) {
                                        viewModel.isSaving = false
                                        val savedUri = android.net.Uri.parse(path)
                                        savedMemeFile = null // Clear file reference since we're using URI
                                        
                                        // Step 2: Upload to Blossom
                                        val pubkeyHex = KeyStoreManager.getPubkeyHex()
                                        val isUsingAmber = KeyStoreManager.isUsingAmber()
                                        val privKeyHex = if (!isUsingAmber) KeyStoreManager.exportNsecHex() else null
                                        
                                        if (pubkeyHex.isNullOrBlank()) {
                                            // TODO: Show error to user
                                            return@launch
                                        }
                                        
                                        if (!isUsingAmber && privKeyHex.isNullOrBlank()) {
                                            // TODO: Show error to user
                                            return@launch
                                        }
                                        
                                        // Configure Amber if using external signer
                                        if (isUsingAmber) {
                                            val packageName = KeyStoreManager.getAmberPackageName()
                                            if (packageName != null) {
                                                AmberSignerManager.configure(pubkeyHex, packageName)
                                            } else {
                                                return@launch
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
                                            coroutineScope = coroutineScope,
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
                                        viewModel.isSaving = false
                                    }
                                }
                            )
                        }
                    },
                    isPostingToNostr = viewModel.isSaving || isUploadingToBlossom,
                    onSaveToDevice = {
                        isSavingToDevice = true
                        coroutineScope.launch(Dispatchers.IO) {
                            MemeFileSaver.saveMeme(
                                context = context,
                                imageUri = viewModel.localImageUri ?: imageUri, // Use cached local URI if available
                                texts = viewModel.texts,
                                overlays = viewModel.overlays,
                                baseImageSize = viewModel.baseImageSize,
                                originalImageWidth = viewModel.originalImageWidth,
                                originalImageHeight = viewModel.originalImageHeight,
                                imageOffsetX = viewModel.imageOffsetX,
                                imageOffsetY = viewModel.imageOffsetY,
                                onSuccess = { path ->
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
                    selectedIsText = viewModel.selectedIsText && viewModel.selectedLayerIndex != null,
                    selectedIsImage = !viewModel.selectedIsText && viewModel.selectedLayerIndex != null,
                    onGloballyPositioned = {
                        // When the controls are laid out, re-register all tutorial targets
                        // to ensure their positions are correctly captured.
                        TutorialTargetRegistry.repositionAllTargets()
                    },
                    outlineWidth = viewModel.getSelectedText()?.outlineWidth ?: 0.dp,
                    onOutlineWidthChange = { viewModel.updateSelectedTextOutlineWidth(it) }
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
                    viewModel = viewModel,
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
    if (showColorPicker && viewModel.selectedIsText && viewModel.selectedLayerIndex != null) {
        ColorPickerDialog(
            onDismiss = { showColorPicker = false },
            onColorSelected = { color ->
                viewModel.updateSelectedTextColor(color)
                showColorPicker = false
            }
        )
    }

    // Text Formatting Panel
    if (showTextFormattingPanel) {
        val selectedText = viewModel.getSelectedText()
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
                        onFontSizeChange = { viewModel.updateSelectedTextFontSize(it.sp) },
                        onFontFamilyChange = { viewModel.updateSelectedTextFontFamily(it) },
                        onFontWeightChange = { viewModel.updateSelectedTextFontWeight(it) },
                        onFontStyleChange = { viewModel.updateSelectedTextFontStyle(it) },
                        onTextAlignChange = { viewModel.updateSelectedTextAlign(it) }
                    )
                },
                sheetState = textFormattingSheetState,
                scrimColor = Color.Black.copy(alpha = 0.32f)
            ) {}
        }
    }

    // Image Editing Panel
    if (showImageEditingPanel) {
        val selectedImage = viewModel.getSelectedImage()
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
                        onCornerRadiusChange = { viewModel.updateSelectedImageCornerRadius(it.dp) },
                        onAlphaChange = { viewModel.updateSelectedImageAlpha(it) },
                        onRotationChange = { viewModel.updateSelectedImageRotation(it) },
                        onScaleChange = { viewModel.updateSelectedImageScale(it) }
                    )
                },
                sheetState = imageEditingSheetState,
                scrimColor = Color.Black.copy(alpha = 0.32f)
            ) {}
        }
    }

    // Compose note dialog for posting to Nostr
    if (showComposeDialog && uploadedImageUrl != null) {
        val isPosting = nostrPostViewModel.postState is NostrPostViewModel.PostState.Posting
        
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
                            unsignedEvent.put("id", eventId)
                            
                            
                            
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
                            delay(5000)
                            
                            // Get the publish result using the ACTUAL event ID
                            val result_final = RelayEventTracker.getPublishResult(actualEventId)
                            RelayEventTracker.completePublish(actualEventId)
                            
                            coroutineScope.launch(Dispatchers.Main) {
                                nostrPostViewModel.setSuccessState(actualEventId)
                                
                                // Show relay status
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
                        coroutineScope = coroutineScope,
                        onSuccess = { actualEventId ->
                            
                            
                            // Wait for relay responses (timeout after 5 seconds)
                            coroutineScope.launch(Dispatchers.IO) {
                                delay(5000)
                                
                                // Get the publish result
                                val result_final = RelayEventTracker.getPublishResult(actualEventId)
                                RelayEventTracker.completePublish(actualEventId)
                                
                                coroutineScope.launch(Dispatchers.Main) {
                                    // Show relay status
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
                            // For local content URIs, get actual dimensions
                            val inputStream = context.contentResolver.openInputStream(templateUri)
                            val options = android.graphics.BitmapFactory.Options().apply {
                                inJustDecodeBounds = true
                            }
                            android.graphics.BitmapFactory.decodeStream(inputStream, null, options)
                            inputStream?.close()
                            Pair(options.outWidth, options.outHeight)
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

                            val centerX = viewModel.imageOffsetX + (viewModel.baseImageSize.width / 2f)
                            val centerY = viewModel.imageOffsetY + (viewModel.baseImageSize.height / 2f)

                            val initialX = centerX - (displayWidthPx / 2f)
                            val displayHeightPx = displayWidthPx * (dimensions.second.toFloat() / dimensions.first.toFloat())
                            val initialY = centerY - (displayHeightPx / 2f)

                            // Add template as overlay layer
                            viewModel.addOverlay(
                                templateUri,
                                dimensions.first,
                                dimensions.second,
                                androidx.compose.ui.geometry.Offset(initialX, initialY)
                            )
                            
                            showTemplateSelector = false
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
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