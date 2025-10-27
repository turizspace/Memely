package com.memely.ui.components.editor

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.memely.ui.viewmodels.MemeOverlayImage

@Composable
fun ImageLayerBox(
    overlay: MemeOverlayImage,
    index: Int,
    onTransformChange: (Offset, Float, Float) -> Unit,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Local state for interactive transformations
    var offset by remember { mutableStateOf(overlay.position) }
    var scale by remember { mutableStateOf(overlay.scale) }
    var rotation by remember { mutableStateOf(overlay.rotation) }
    
    // Sync external changes to local state (from sliders/external updates)
    // Only sync when values actually differ to avoid unnecessary updates
    LaunchedEffect(overlay.position) {
        if (overlay.position != offset) {
            offset = overlay.position
        }
    }
    
    LaunchedEffect(overlay.scale) {
        if (overlay.scale != scale) {
            scale = overlay.scale
        }
    }
    
    LaunchedEffect(overlay.rotation) {
        if (overlay.rotation != rotation) {
            rotation = overlay.rotation
        }
    }

    // Calculate aspect ratio preserving size
    val aspectRatio = overlay.originalWidth.toFloat() / overlay.originalHeight.toFloat()
    val width: Dp = (overlay.displayWidth.value * scale).dp
    val height: Dp = (width.value / aspectRatio).dp

    Box(
        modifier = modifier
            .offset { IntOffset(offset.x.roundToInt(), offset.y.roundToInt()) }
            .size(width = width, height = height)
            .graphicsLayer(
                rotationZ = rotation,
                alpha = overlay.alpha
            )
            .border(
                width = if (overlay.selected) 2.dp else 0.dp,
                color = if (overlay.selected) Color.Green else Color.Transparent,
                shape = RoundedCornerShape(overlay.cornerRadius)
            )
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, rotationDelta ->
                    offset += pan
                    scale = (scale * zoom).coerceIn(0.5f, 3f)
                    
                    // Normalize rotation to stay within -180° to +180° range
                    rotation = ((rotation + rotationDelta + 180f) % 360f - 180f).let {
                        if (it < -180f) it + 360f else it
                    }
                    
                    onTransformChange(offset, scale, rotation)
                    println("🖼 ImageLayerBox transform idx=$index offset=$offset scale=$scale rotation=$rotation")
                }
            }
            .clickable {
                onSelect()
            }
    ) {
        AsyncImage(
            model = overlay.uri,
            contentDescription = "Overlay Image",
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    clip = true,
                    shape = RoundedCornerShape(overlay.cornerRadius)
                ),
            contentScale = ContentScale.Crop
        )
    }
}
