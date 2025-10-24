package com.memely.ui.components.editor

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
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
    // Sync with external state changes
    var offset by remember(overlay.position) { mutableStateOf(overlay.position) }
    var scale by remember(overlay.scale) { mutableStateOf(overlay.scale) }
    var rotation by remember(overlay.rotation) { mutableStateOf(overlay.rotation) }

    // Calculate aspect ratio preserving size
    val aspectRatio = overlay.originalWidth.toFloat() / overlay.originalHeight.toFloat()
    val width: Dp = (overlay.displayWidth.value * scale).dp
    val height: Dp = (width.value / aspectRatio).dp

    Box(
        modifier = modifier
            .offset { IntOffset(offset.x.roundToInt(), offset.y.roundToInt()) }
            .size(width = width, height = height)
            .graphicsLayer(rotationZ = rotation)
            .border(
                width = if (overlay.selected) 2.dp else 0.dp,
                color = if (overlay.selected) Color.Green else Color.Transparent,
                shape = RoundedCornerShape(4.dp)
            )
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    offset += dragAmount
                    onTransformChange(offset, scale, rotation)
                    println("🖼 ImageLayerBox drag idx=$index offset=$offset scale=$scale rotation=$rotation")
                }
            }
            .clickable {
                onSelect()
            }
    ) {
        AsyncImage(
            model = overlay.uri,
            contentDescription = "Overlay Image",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit
        )
    }
}
