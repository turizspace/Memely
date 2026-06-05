package com.memely.ui.viewmodels

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.memely.util.SecureLog
import java.io.File

// Data classes for editor layers
data class MemeText(
    var text: String,
    var position: Offset,
    var fontSize: TextUnit = 32.sp,
    var color: Color = Color.White,
    var alpha: Float = 1f,
    var scale: Float = 1f,
    var rotation: Float = 0f,
    var selected: Boolean = false,
    var locked: Boolean = false,
    var fontFamily: androidx.compose.ui.text.font.FontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif,
    var fontWeight: androidx.compose.ui.text.font.FontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
    var fontStyle: androidx.compose.ui.text.font.FontStyle = androidx.compose.ui.text.font.FontStyle.Normal,
    var textAlign: androidx.compose.ui.text.style.TextAlign = androidx.compose.ui.text.style.TextAlign.Start,
    var maxWidth: androidx.compose.ui.unit.Dp = 300.dp,  // Max width for text wrapping in dp
    var measuredWidthPx: Float = 0f,  // Actual measured width in pixels (used for accurate save)
    var outlineWidth: androidx.compose.ui.unit.Dp = 0.dp,  // Text outline/stroke width
    var outlineColor: Color = Color.Black,  // Outline color (default black for contrast)
    var shadowEnabled: Boolean = false,
    var shadowColor: Color = Color.Black,
    var shadowBlur: androidx.compose.ui.unit.Dp = 4.dp,
    var shadowOffsetX: androidx.compose.ui.unit.Dp = 2.dp,
    var shadowOffsetY: androidx.compose.ui.unit.Dp = 2.dp
)

data class MemeOverlayImage(
    val uri: Uri,
    var originalWidth: Int = 200,
    var originalHeight: Int = 200,
    var displayWidth: androidx.compose.ui.unit.Dp = 150.dp,
    var displayHeight: androidx.compose.ui.unit.Dp = 150.dp,
    var position: Offset,
    var scale: Float = 1f,
    var rotation: Float = 0f,
    var selected: Boolean = false,
    var locked: Boolean = false,
    var cornerRadius: androidx.compose.ui.unit.Dp = 0.dp,
    var alpha: Float = 1f,
    var flipX: Boolean = false,
    var flipY: Boolean = false
)

// ViewModel for managing meme editor state
class MemeEditorViewModel : ViewModel() {
    private val temporaryCacheFiles = linkedSetOf<String>()
    private var sourceImageUri by mutableStateOf<Uri?>(null)

    var texts by mutableStateOf(listOf<MemeText>())
    var overlays by mutableStateOf(listOf<MemeOverlayImage>())
    var selectedLayerIndex by mutableStateOf<Int?>(null)
    var selectedIsText by mutableStateOf(true)
    var isSaving by mutableStateOf(false)
    var baseImageSize by mutableStateOf(IntSize.Zero) // Displayed size on screen
    var originalImageWidth by mutableStateOf(0) // Original image width
    var originalImageHeight by mutableStateOf(0) // Original image height
    var imageOffsetX by mutableStateOf(0f) // Offset from left edge when centered
    var imageOffsetY by mutableStateOf(0f) // Offset from top edge when centered
    var localImageUri by mutableStateOf<Uri?>(null) // Cached local URI for remote images

    fun syncBaseImageUri(uri: Uri) {
        if (sourceImageUri == uri) {
            return
        }

        sourceImageUri = uri
        if (!uri.isRemote()) {
            localImageUri = uri
        } else {
            localImageUri = null
        }
    }

    fun updateBaseImageSize(size: IntSize) {
        baseImageSize = size
    }
    
    fun updateLocalImageUri(uri: Uri?) {
        localImageUri = uri
    }

    fun registerTemporaryCacheFile(file: File) {
        temporaryCacheFiles += file.absolutePath
    }

    fun updateOriginalImageSize(width: Int, height: Int) {
        originalImageWidth = width
        originalImageHeight = height
    }
    
    fun updateImageOffset(offsetX: Float, offsetY: Float) {
        imageOffsetX = offsetX
        imageOffsetY = offsetY
    }

    fun updateSavingState(saving: Boolean) {
        isSaving = saving
    }

    fun addText(position: Offset) {
        // Deselect all existing
        texts = texts.map { it.copy(selected = false) }
        overlays = overlays.map { it.copy(selected = false) }
        
        SecureLog.d("MemeEditorViewModel: Adding text at position=$position")
        
        val newText = MemeText(
            text = "New Text",
            position = position,
            selected = true
        )
        texts = texts + newText
        selectedLayerIndex = texts.size - 1
        selectedIsText = true
    }

    fun addTopText(density: Float) {
        addPresetText(
            text = "TOP TEXT",
            y = imageOffsetY + (18f * density),
            density = density
        )
    }

    fun addBottomText(density: Float) {
        val fontSizePx = 34f * density
        addPresetText(
            text = "BOTTOM TEXT",
            y = imageOffsetY + baseImageSize.height - (fontSizePx * 1.8f),
            density = density
        )
    }

    private fun addPresetText(text: String, y: Float, density: Float) {
        if (baseImageSize == IntSize.Zero) {
            addText(Offset(24f * density, 24f * density))
            return
        }

        texts = texts.map { it.copy(selected = false) }
        overlays = overlays.map { it.copy(selected = false) }

        val horizontalPadding = 18.dp
        val maxWidth = (baseImageSize.width / density).dp - (horizontalPadding * 2)
        val newText = MemeText(
            text = text,
            position = Offset(imageOffsetX + (horizontalPadding.value * density), y),
            fontSize = 34.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            maxWidth = maxWidth,
            outlineWidth = 3.dp,
            shadowEnabled = true,
            shadowBlur = 1.dp,
            shadowOffsetX = 1.dp,
            shadowOffsetY = 1.dp,
            selected = true
        )
        texts = texts + newText
        selectedLayerIndex = texts.lastIndex
        selectedIsText = true
    }

    fun addOverlay(uri: Uri, originalWidth: Int, originalHeight: Int, initialPosition: androidx.compose.ui.geometry.Offset? = null) {
        // Deselect all existing
        texts = texts.map { it.copy(selected = false) }
        overlays = overlays.map { it.copy(selected = false) }
        
        // Calculate center position accounting for overlay dimensions
        val centerImageX = imageOffsetX + (baseImageSize.width / 2f)
        val centerImageY = imageOffsetY + (baseImageSize.height / 2f)
        
        // Default overlay display size (150dp)
        val defaultDisplayWidth = 150f // in dp units, will be converted in the Box
        val aspectRatio = originalWidth.toFloat() / originalHeight.toFloat()
        val displayHeight = defaultDisplayWidth / aspectRatio
        
        // For initial add, position should be top-left, so subtract half the size to center
        // Note: needs density to convert from dp to pixels, but we'll use the displayWidth value directly
        val posX = if (initialPosition != null) {
            initialPosition.x
        } else {
            // Center the overlay: subtract half of display width (in approximate pixels)
            centerImageX - (defaultDisplayWidth / 2f)
        }
        
        val posY = if (initialPosition != null) {
            initialPosition.y
        } else {
            // Center the overlay: subtract half of display height
            centerImageY - (displayHeight / 2f)
        }

        SecureLog.d("MemeEditorViewModel: Adding overlay at position=($posX, $posY), size=$defaultDisplayWidth x $displayHeight")

        val overlayImage = MemeOverlayImage(
            uri = uri,
            originalWidth = originalWidth,
            originalHeight = originalHeight,
            displayWidth = 150f.dp,
            position = Offset(posX, posY),
            selected = true
        )
        overlays = overlays + overlayImage
        selectedLayerIndex = overlays.size - 1
        selectedIsText = false
    }

    fun selectText(idx: Int) {
        texts = texts.mapIndexed { i, m -> m.copy(selected = i == idx) }
        overlays = overlays.map { it.copy(selected = false) }
        selectedLayerIndex = idx
        selectedIsText = true
    }

    fun selectOverlay(idx: Int) {
        overlays = overlays.mapIndexed { i, o ->
            o.copy(selected = i == idx)
        }
        texts = texts.map { it.copy(selected = false) }
        selectedLayerIndex = idx
        selectedIsText = false
    }

    fun deselectAll() {
        texts = texts.map { it.copy(selected = false) }
        overlays = overlays.map { it.copy(selected = false) }
        selectedLayerIndex = null
    }

    fun deleteSelected() {
        selectedLayerIndex?.let { idx ->
            if (selectedIsText) {
                if (idx < texts.size && texts[idx].locked) return
                texts = texts.filterIndexed { i, _ -> i != idx }
            } else {
                if (idx < overlays.size && overlays[idx].locked) return
                overlays = overlays.filterIndexed { i, _ -> i != idx }
            }
            selectedLayerIndex = null
        }
    }

    fun duplicateSelected() {
        selectedLayerIndex?.let { idx ->
            val duplicateOffset = Offset(24f, 24f)
            texts = texts.map { it.copy(selected = false) }
            overlays = overlays.map { it.copy(selected = false) }

            if (selectedIsText && idx < texts.size) {
                val duplicatedText = texts[idx].copy(
                    position = texts[idx].position + duplicateOffset,
                    selected = true
                )
                texts = texts + duplicatedText
                selectedLayerIndex = texts.lastIndex
            } else if (!selectedIsText && idx < overlays.size) {
                val duplicatedOverlay = overlays[idx].copy(
                    position = overlays[idx].position + duplicateOffset,
                    selected = true
                )
                overlays = overlays + duplicatedOverlay
                selectedLayerIndex = overlays.lastIndex
            }
        }
    }

    fun bringSelectedForward() {
        selectedLayerIndex?.let { idx ->
            if (selectedIsText && idx in 0 until texts.lastIndex) {
                texts = texts.swap(idx, idx + 1)
                selectedLayerIndex = idx + 1
            } else if (!selectedIsText && idx in 0 until overlays.lastIndex) {
                overlays = overlays.swap(idx, idx + 1)
                selectedLayerIndex = idx + 1
            }
        }
    }

    fun sendSelectedBackward() {
        selectedLayerIndex?.let { idx ->
            if (selectedIsText && idx > 0 && idx < texts.size) {
                texts = texts.swap(idx, idx - 1)
                selectedLayerIndex = idx - 1
            } else if (!selectedIsText && idx > 0 && idx < overlays.size) {
                overlays = overlays.swap(idx, idx - 1)
                selectedLayerIndex = idx - 1
            }
        }
    }

    fun centerSelected(density: Float) {
        if (baseImageSize == IntSize.Zero) {
            return
        }

        val idx = selectedLayerIndex ?: return
        val centerX = imageOffsetX + (baseImageSize.width / 2f)
        val centerY = imageOffsetY + (baseImageSize.height / 2f)

        if (selectedIsText && idx < texts.size) {
            texts = texts.mapIndexed { i, text ->
                if (i == idx) {
                    // Use measured width if available, otherwise estimate
                    val baseWidth = text.measuredWidthPx.takeIf { it > 0f } ?: (text.maxWidth.value * density)
                    // Account for outline width affecting total size
                    val outlineAdjustment = if (text.outlineWidth > 0.dp) text.outlineWidth.value * density else 0f
                    val totalWidth = baseWidth + (outlineAdjustment * 2f)
                    
                    // Account for scale
                    val scaledWidth = totalWidth * text.scale
                    val scaledHeight = (text.fontSize.value * density * 1.4f) * text.scale
                    
                    // Position at center, accounting for scale origin (0,0 at top-left)
                    text.copy(position = Offset(centerX - (scaledWidth / 2f), centerY - (scaledHeight / 2f)))
                } else {
                    text
                }
            }
        } else if (!selectedIsText && idx < overlays.size) {
            overlays = overlays.mapIndexed { i, overlay ->
                if (i == idx) {
                    // Calculate actual displayed size with scale
                    val baseWidth = overlay.displayWidth.value * density
                    val scaledWidth = baseWidth * overlay.scale
                    
                    val aspectRatio = overlay.originalWidth.toFloat() / overlay.originalHeight.toFloat()
                    val scaledHeight = scaledWidth / aspectRatio
                    
                    // Position at center, accounting for scale origin (0,0 at top-left)
                    overlay.copy(position = Offset(centerX - (scaledWidth / 2f), centerY - (scaledHeight / 2f)))
                } else {
                    overlay
                }
            }
        }
    }

    fun nudgeSelected(deltaX: Float, deltaY: Float) {
        selectedLayerIndex?.let { idx ->
            if (selectedIsText && idx < texts.size) {
                texts = texts.mapIndexed { i, text ->
                    if (i == idx && !text.locked) {
                        text.copy(position = text.position + Offset(deltaX, deltaY))
                    } else {
                        text
                    }
                }
            } else if (!selectedIsText && idx < overlays.size) {
                overlays = overlays.mapIndexed { i, overlay ->
                    if (i == idx && !overlay.locked) {
                        overlay.copy(position = overlay.position + Offset(deltaX, deltaY))
                    } else {
                        overlay
                    }
                }
            }
        }
    }

    fun updateSelectedTextColor(color: Color) {
        selectedLayerIndex?.let { idx ->
            if (selectedIsText) {
                texts = texts.mapIndexed { i, t ->
                    if (i == idx) t.copy(color = color) else t
                }
            }
        }
    }

    fun updateSelectedTextAlpha(alpha: Float) {
        selectedLayerIndex?.let { idx ->
            if (selectedIsText) {
                texts = texts.mapIndexed { i, t ->
                    if (i == idx) t.copy(alpha = alpha) else t
                }
            }
        }
    }

    fun updateText(idx: Int, text: String) {
        texts = texts.mapIndexed { i, t ->
            if (i == idx) t.copy(text = text) else t
        }
    }

    fun updateTextTransform(idx: Int, offset: Offset, scale: Float, rotation: Float) {
        texts = texts.mapIndexed { i, t ->
            if (i == idx && !t.locked) t.copy(position = offset, scale = scale, rotation = rotation) else t
        }
    }

    fun updateOverlayTransform(idx: Int, offset: Offset, scale: Float, rotation: Float) {
        overlays = overlays.mapIndexed { i, o ->
            if (i == idx && !o.locked) o.copy(position = offset, scale = scale, rotation = rotation) else o
        }
    }

    fun toggleSelectedLock() {
        selectedLayerIndex?.let { idx ->
            if (selectedIsText && idx < texts.size) {
                texts = texts.mapIndexed { i, text ->
                    if (i == idx) text.copy(locked = !text.locked) else text
                }
            } else if (!selectedIsText && idx < overlays.size) {
                overlays = overlays.mapIndexed { i, overlay ->
                    if (i == idx) overlay.copy(locked = !overlay.locked) else overlay
                }
            }
        }
    }
    
    fun updateTextMeasuredWidth(idx: Int, widthPx: Float) {
        texts = texts.mapIndexed { i, t ->
            if (i == idx) t.copy(measuredWidthPx = widthPx) else t
        }
    }
    
    // New text formatting methods
    fun updateSelectedTextFontSize(fontSize: TextUnit) {
        selectedLayerIndex?.let { idx ->
            if (selectedIsText) {
                texts = texts.mapIndexed { i, t ->
                    if (i == idx) t.copy(fontSize = fontSize) else t
                }
            }
        }
    }
    
    fun updateSelectedTextFontFamily(fontFamily: androidx.compose.ui.text.font.FontFamily) {
        selectedLayerIndex?.let { idx ->
            if (selectedIsText) {
                texts = texts.mapIndexed { i, t ->
                    if (i == idx) t.copy(fontFamily = fontFamily) else t
                }
            }
        }
    }
    
    fun updateSelectedTextFontWeight(fontWeight: androidx.compose.ui.text.font.FontWeight) {
        selectedLayerIndex?.let { idx ->
            if (selectedIsText) {
                texts = texts.mapIndexed { i, t ->
                    if (i == idx) t.copy(fontWeight = fontWeight) else t
                }
            }
        }
    }
    
    fun updateSelectedTextFontStyle(fontStyle: androidx.compose.ui.text.font.FontStyle) {
        selectedLayerIndex?.let { idx ->
            if (selectedIsText) {
                texts = texts.mapIndexed { i, t ->
                    if (i == idx) t.copy(fontStyle = fontStyle) else t
                }
            }
        }
    }
    
    fun updateSelectedTextAlign(textAlign: androidx.compose.ui.text.style.TextAlign) {
        selectedLayerIndex?.let { idx ->
            if (selectedIsText) {
                texts = texts.mapIndexed { i, t ->
                    if (i == idx) t.copy(textAlign = textAlign) else t
                }
            }
        }
    }
    
    fun updateSelectedTextOutlineWidth(outlineWidth: androidx.compose.ui.unit.Dp) {
        selectedLayerIndex?.let { idx ->
            if (selectedIsText) {
                texts = texts.mapIndexed { i, t ->
                    if (i == idx) t.copy(outlineWidth = outlineWidth) else t
                }
            }
        }
    }
    
    fun updateSelectedTextOutlineColor(outlineColor: Color) {
        selectedLayerIndex?.let { idx ->
            if (selectedIsText) {
                texts = texts.mapIndexed { i, t ->
                    if (i == idx) t.copy(outlineColor = outlineColor) else t
                }
            }
        }
    }

    fun updateSelectedTextShadowEnabled(enabled: Boolean) {
        selectedLayerIndex?.let { idx ->
            if (selectedIsText) {
                texts = texts.mapIndexed { i, t ->
                    if (i == idx) t.copy(shadowEnabled = enabled) else t
                }
            }
        }
    }

    fun updateSelectedTextShadowBlur(blur: androidx.compose.ui.unit.Dp) {
        selectedLayerIndex?.let { idx ->
            if (selectedIsText) {
                texts = texts.mapIndexed { i, t ->
                    if (i == idx) t.copy(shadowBlur = blur) else t
                }
            }
        }
    }

    fun updateSelectedTextShadowOffset(offset: androidx.compose.ui.unit.Dp) {
        selectedLayerIndex?.let { idx ->
            if (selectedIsText) {
                texts = texts.mapIndexed { i, t ->
                    if (i == idx) t.copy(shadowOffsetX = offset, shadowOffsetY = offset) else t
                }
            }
        }
    }
    
    // New image editing methods
    fun updateSelectedImageCornerRadius(cornerRadius: androidx.compose.ui.unit.Dp) {
        selectedLayerIndex?.let { idx ->
            if (!selectedIsText) {
                overlays = overlays.mapIndexed { i, o ->
                    if (i == idx) o.copy(cornerRadius = cornerRadius) else o
                }
            }
        }
    }
    
    fun updateSelectedImageAlpha(alpha: Float) {
        selectedLayerIndex?.let { idx ->
            if (!selectedIsText) {
                overlays = overlays.mapIndexed { i, o ->
                    if (i == idx) o.copy(alpha = alpha) else o
                }
            }
        }
    }
    
    fun updateSelectedImageRotation(rotation: Float) {
        selectedLayerIndex?.let { idx ->
            if (!selectedIsText) {
                overlays = overlays.mapIndexed { i, o ->
                    if (i == idx) o.copy(rotation = rotation) else o
                }
            }
        }
    }
    
    fun updateSelectedImageScale(scale: Float) {
        selectedLayerIndex?.let { idx ->
            if (!selectedIsText) {
                overlays = overlays.mapIndexed { i, o ->
                    if (i == idx) o.copy(scale = scale) else o
                }
            }
        }
    }

    fun flipSelectedImageHorizontal() {
        selectedLayerIndex?.let { idx ->
            if (!selectedIsText) {
                overlays = overlays.mapIndexed { i, o ->
                    if (i == idx) o.copy(flipX = !o.flipX) else o
                }
            }
        }
    }

    fun flipSelectedImageVertical() {
        selectedLayerIndex?.let { idx ->
            if (!selectedIsText) {
                overlays = overlays.mapIndexed { i, o ->
                    if (i == idx) o.copy(flipY = !o.flipY) else o
                }
            }
        }
    }
    
    // Helper to get selected text
    fun getSelectedText(): MemeText? {
        return selectedLayerIndex?.let { idx ->
            if (selectedIsText && idx < texts.size) texts[idx] else null
        }
    }
    
    // Helper to get selected image
    fun getSelectedImage(): MemeOverlayImage? {
        return selectedLayerIndex?.let { idx ->
            if (!selectedIsText && idx < overlays.size) overlays[idx] else null
        }
    }

    override fun onCleared() {
        temporaryCacheFiles.forEach { path ->
            runCatching {
                val file = File(path)
                if (file.exists() && !file.delete()) {
                    SecureLog.w("MemeEditorViewModel: Failed to delete cached file ${file.name}")
                }
            }.onFailure { throwable ->
                SecureLog.e("MemeEditorViewModel: Error deleting cached image", throwable)
            }
        }
        temporaryCacheFiles.clear()
    }
}

private fun <T> List<T>.swap(fromIndex: Int, toIndex: Int): List<T> {
    return toMutableList().also { list ->
        val item = list[fromIndex]
        list[fromIndex] = list[toIndex]
        list[toIndex] = item
    }
}

private fun Uri.isRemote(): Boolean {
    val scheme = scheme?.lowercase()
    return scheme == "http" || scheme == "https"
}
