package com.memely.ui.viewmodels

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.net.Uri

// Data classes for editor layers
data class MemeText(
    var text: String,
    var position: Offset,
    var fontSize: TextUnit = 32.sp,
    var color: Color = Color.White,
    var scale: Float = 1f,
    var rotation: Float = 0f,
    var selected: Boolean = false,
    var fontFamily: androidx.compose.ui.text.font.FontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif,
    var fontWeight: androidx.compose.ui.text.font.FontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
    var fontStyle: androidx.compose.ui.text.font.FontStyle = androidx.compose.ui.text.font.FontStyle.Normal,
    var textAlign: androidx.compose.ui.text.style.TextAlign = androidx.compose.ui.text.style.TextAlign.Center
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
    var cornerRadius: androidx.compose.ui.unit.Dp = 0.dp,
    var alpha: Float = 1f
)

// ViewModel for managing meme editor state
class MemeEditorViewModel {
    var texts by mutableStateOf(listOf<MemeText>())
    var overlays by mutableStateOf(listOf<MemeOverlayImage>())
    var selectedLayerIndex by mutableStateOf<Int?>(null)
    var selectedIsText by mutableStateOf(true)
    var showColorPicker by mutableStateOf(false)
    var isSaving by mutableStateOf(false)
    var baseImageSize by mutableStateOf(IntSize.Zero) // Displayed size on screen
    var originalImageWidth by mutableStateOf(0) // Original image width
    var originalImageHeight by mutableStateOf(0) // Original image height
    var imageOffsetX by mutableStateOf(0f) // Offset from left edge when centered
    var imageOffsetY by mutableStateOf(0f) // Offset from top edge when centered
    var localImageUri by mutableStateOf<Uri?>(null) // Cached local URI for remote images

    fun updateBaseImageSize(size: IntSize) {
        baseImageSize = size
    }
    
    fun updateLocalImageUri(uri: Uri) {
        localImageUri = uri
    }

    fun updateOriginalImageSize(width: Int, height: Int) {
        originalImageWidth = width
        originalImageHeight = height
    }
    
    fun updateImageOffset(offsetX: Float, offsetY: Float) {
        imageOffsetX = offsetX
        imageOffsetY = offsetY
    }

    fun addText(position: Offset) {
        // Deselect all existing
        texts = texts.map { it.copy(selected = false) }
        overlays = overlays.map { it.copy(selected = false) }
        
        println("➕ Adding text at position: $position, baseImageSize: $baseImageSize, offset: ($imageOffsetX, $imageOffsetY)")
        
        val newText = MemeText(
            text = "New Text",
            position = position,
            selected = true
        )
        texts = texts + newText
        selectedLayerIndex = texts.size - 1
        selectedIsText = true
    }

    fun addOverlay(uri: Uri, originalWidth: Int, originalHeight: Int, initialPosition: androidx.compose.ui.geometry.Offset? = null) {
        // Deselect all existing
        texts = texts.map { it.copy(selected = false) }
        overlays = overlays.map { it.copy(selected = false) }
        // Start overlay at provided position (top-left), or center of image if none provided
        val centerX = initialPosition?.x ?: (imageOffsetX + (baseImageSize.width / 2f))
        val centerY = initialPosition?.y ?: (imageOffsetY + (baseImageSize.height / 2f))

        println("➕ Adding overlay at position: ($centerX, $centerY)")

        val overlayImage = MemeOverlayImage(
            uri = uri,
            originalWidth = originalWidth,
            originalHeight = originalHeight,
            position = Offset(centerX, centerY),
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
                texts = texts.filterIndexed { i, _ -> i != idx }
            } else {
                overlays = overlays.filterIndexed { i, _ -> i != idx }
            }
            selectedLayerIndex = null
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

    fun updateText(idx: Int, text: String) {
        texts = texts.mapIndexed { i, t ->
            if (i == idx) t.copy(text = text) else t
        }
    }

    fun updateTextTransform(idx: Int, offset: Offset, scale: Float, rotation: Float) {
        texts = texts.mapIndexed { i, t ->
            if (i == idx) t.copy(position = offset, scale = scale, rotation = rotation) else t
        }
    }

    fun updateOverlayTransform(idx: Int, offset: Offset, scale: Float, rotation: Float) {
        overlays = overlays.mapIndexed { i, o ->
            if (i == idx) o.copy(position = offset, scale = scale, rotation = rotation) else o
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
}
