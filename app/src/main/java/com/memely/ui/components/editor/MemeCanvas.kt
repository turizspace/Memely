package com.memely.ui.components.editor

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import coil.compose.AsyncImage
import android.net.Uri
import com.memely.util.SecureLog
import com.memely.ui.viewmodels.MemeEditorViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File

@Composable
fun MemeCanvas(
    baseImageUri: Uri,
    viewModel: MemeEditorViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    
    // Track original image dimensions
    var originalImageWidth by remember { mutableStateOf(0) }
    var originalImageHeight by remember { mutableStateOf(0) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    val displayImageUri = viewModel.localImageUri ?: baseImageUri
    val dimensionImageUri = viewModel.localImageUri ?: baseImageUri.takeUnless { it.isRemote() }
    
    // Download remote image if needed
    LaunchedEffect(baseImageUri) {
        viewModel.syncBaseImageUri(baseImageUri)

        val uriString = baseImageUri.toString()
        if (uriString.startsWith("http") && viewModel.localImageUri == null) {
            // Download from remote URL and save to cache
            val tempFile = withContext(Dispatchers.IO) {
                try {
                    val httpClient = com.memely.network.SecureHttpClient.createDownloadClient()
                    val request = Request.Builder().url(uriString).build()
                    
                    httpClient.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            val cacheDir = context.cacheDir
                            val tempFile = File(cacheDir, "template_${System.currentTimeMillis()}.jpg")
                            tempFile.outputStream().use { output ->
                                response.body?.bytes()?.let { output.write(it) }
                            }
                            tempFile
                        } else {
                            SecureLog.w("MemeCanvas: Failed to download template code=${response.code}")
                            null
                        }
                    }
                } catch (e: Exception) {
                    SecureLog.e("MemeCanvas: Error downloading template", e)
                    null
                }
            }

            if (tempFile != null) {
                viewModel.registerTemporaryCacheFile(tempFile)
                viewModel.updateLocalImageUri(Uri.fromFile(tempFile))
                SecureLog.d("MemeCanvas: Downloaded template to ${tempFile.name}")
            }
        }
    }
    
    // Get original image dimensions
    LaunchedEffect(dimensionImageUri) {
        val resolvedImageUri = dimensionImageUri ?: return@LaunchedEffect
        try {
            val inputStream = context.contentResolver.openInputStream(resolvedImageUri)
            val options = android.graphics.BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            android.graphics.BitmapFactory.decodeStream(inputStream, null, options)
            originalImageWidth = options.outWidth
            originalImageHeight = options.outHeight
            inputStream?.close()
            viewModel.updateOriginalImageSize(originalImageWidth, originalImageHeight)
            SecureLog.d("MemeCanvas: Image dimensions ${originalImageWidth}x${originalImageHeight}")
        } catch (e: Exception) {
            SecureLog.e("MemeCanvas: Error reading image dimensions", e)
        }
    }
    
    // Calculate actual displayed image size based on ContentScale.Fit
    LaunchedEffect(containerSize, originalImageWidth, originalImageHeight) {
        if (containerSize != IntSize.Zero && originalImageWidth > 0 && originalImageHeight > 0) {
            val containerAspect = containerSize.width.toFloat() / containerSize.height.toFloat()
            val imageAspect = originalImageWidth.toFloat() / originalImageHeight.toFloat()
            
            val displayedSize = if (imageAspect > containerAspect) {
                // Image is wider - width constrained
                IntSize(
                    width = containerSize.width,
                    height = (containerSize.width / imageAspect).toInt()
                )
            } else {
                // Image is taller - height constrained
                IntSize(
                    width = (containerSize.height * imageAspect).toInt(),
                    height = containerSize.height
                )
            }
            
            // Calculate offset (image is centered in container)
            val offsetX = (containerSize.width - displayedSize.width) / 2f
            val offsetY = (containerSize.height - displayedSize.height) / 2f
            
            SecureLog.d(
                "MemeCanvas: displayed=$displayedSize container=$containerSize " +
                    "offset=($offsetX,$offsetY)"
            )
            
            viewModel.updateBaseImageSize(displayedSize)
            viewModel.updateImageOffset(offsetX, offsetY)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { coordinates ->
                containerSize = coordinates.size
            }
            .pointerInput(Unit) {
                detectTapGestures {
                    viewModel.deselectAll()
                }
            }
    ) {
        // Base image - fill available space and fit inside
        AsyncImage(
            model = displayImageUri,
            contentDescription = "Base Image",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit
        )

        // Render overlay images
        viewModel.overlays.forEachIndexed { idx, overlay ->
            ImageLayerBox(
                overlay = overlay,
                index = idx,
                onTransformChange = { offset, scale, rotation ->
                    viewModel.updateOverlayTransform(idx, offset, scale, rotation)
                },
                onSelect = {
                    viewModel.selectOverlay(idx)
                }
            )
        }

        // Render text layers
        viewModel.texts.forEachIndexed { idx, text ->
            TextLayerBox(
                text = text,
                index = idx,
                onTextChange = { newText ->
                    viewModel.updateText(idx, newText)
                },
                onTransformChange = { offset, scale, rotation ->
                    viewModel.updateTextTransform(idx, offset, scale, rotation)
                },
                onSelect = {
                    viewModel.selectText(idx)
                },
                onMeasuredWidthChange = { measuredWidthPx ->
                    viewModel.updateTextMeasuredWidth(idx, measuredWidthPx)
                }
            )
        }
    }
}

private fun Uri.isRemote(): Boolean {
    val scheme = scheme?.lowercase()
    return scheme == "http" || scheme == "https"
}
