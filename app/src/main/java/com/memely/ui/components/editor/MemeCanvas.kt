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
import com.memely.ui.viewmodels.MemeEditorViewModel
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
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
    var localImageUri by remember { mutableStateOf(baseImageUri) }
    
    // Download remote image if needed
    LaunchedEffect(baseImageUri) {
        val uriString = baseImageUri.toString()
        if (uriString.startsWith("http")) {
            // Download from remote URL and save to cache
            withContext(Dispatchers.IO) {
                try {
                    val httpClient = OkHttpClient()
                    val request = Request.Builder().url(uriString).build()
                    
                    httpClient.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            val cacheDir = context.cacheDir
                            val tempFile = File(cacheDir, "template_${System.currentTimeMillis()}.jpg")
                            tempFile.outputStream().use { output ->
                                response.body?.bytes()?.let { output.write(it) }
                            }
                            localImageUri = Uri.fromFile(tempFile)
                            println("✅ MemeCanvas: Downloaded template to ${tempFile.absolutePath}")
                        } else {
                            println("❌ MemeCanvas: Failed to download template: ${response.code}")
                        }
                    }
                } catch (e: Exception) {
                    println("❌ MemeCanvas: Error downloading template: ${e.message}")
                }
            }
        }
    }
    
    // Get original image dimensions
    LaunchedEffect(localImageUri) {
        try {
            val inputStream = context.contentResolver.openInputStream(localImageUri)
            val options = android.graphics.BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            android.graphics.BitmapFactory.decodeStream(inputStream, null, options)
            originalImageWidth = options.outWidth
            originalImageHeight = options.outHeight
            inputStream?.close()
            viewModel.updateOriginalImageSize(originalImageWidth, originalImageHeight)
            println("✅ MemeCanvas: Image dimensions - ${originalImageWidth}x${originalImageHeight}")
        } catch (e: Exception) {
            println("❌ MemeCanvas: Error reading image dimensions: ${e.message}")
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
            
            println("📐 MemeCanvas: Container=${containerSize}, Original=${originalImageWidth}x${originalImageHeight}, Displayed=${displayedSize}, Offset=(${offsetX},${offsetY})")
            
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
            model = localImageUri,
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
                }
            )
        }
    }
}
