package com.memely.ui.components.editor

import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.memely.ui.viewmodels.MemeText
import kotlin.math.roundToInt
import androidx.compose.ui.text.style.TextAlign


@Composable
fun TextLayerBox(
    text: MemeText,
    index: Int,
    onTextChange: (String) -> Unit,
    onTransformChange: (Offset, Float, Float) -> Unit,
    onSelect: () -> Unit,
    onMeasuredWidthChange: (Float) -> Unit = {}, // NEW callback
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    var offset by remember { mutableStateOf(text.position) }
    var scale by remember { mutableStateOf(text.scale) }
    var rotation by remember { mutableStateOf(text.rotation) }
    var textValue by remember { mutableStateOf(text.text) }
    var lastMeasuredWidthPx by remember { mutableStateOf(text.measuredWidthPx) }
    var measuredSize by remember { mutableStateOf(IntSize.Zero) }

    // keep state synced with external updates
    LaunchedEffect(text.position) { if (text.position != offset) offset = text.position }
    LaunchedEffect(text.scale) { if (text.scale != scale) scale = text.scale }
    LaunchedEffect(text.rotation) { if (text.rotation != rotation) rotation = text.rotation }
    
    // Sync text value when external text changes
    LaunchedEffect(text.text) { if (text.text != textValue) textValue = text.text }
    val textColor = text.color.copy(alpha = text.alpha)
    val outlineColor = text.outlineColor.copy(alpha = text.alpha)
    val textShadow = if (text.shadowEnabled) {
        with(density) {
            Shadow(
                color = text.shadowColor.copy(alpha = text.alpha),
                offset = Offset(text.shadowOffsetX.toPx(), text.shadowOffsetY.toPx()),
                blurRadius = text.shadowBlur.toPx()
            )
        }
    } else {
        null
    }

    // Get alignment based on text align property
    val contentAlignment = when (text.textAlign) {
        TextAlign.Left, TextAlign.Start -> Alignment.CenterStart
        TextAlign.Right, TextAlign.End -> Alignment.CenterEnd
        TextAlign.Center -> Alignment.Center
        else -> Alignment.CenterStart
    }
    
    // Render the actual text content in a separate composable to ensure consistent sizing
    @Composable
    fun TextContent() {
        Box(
            modifier = Modifier
                .widthIn(max = text.maxWidth)
                .wrapContentSize(),
            contentAlignment = contentAlignment
        ) {
            // Outline layer - render text with outline color offset
            if (text.outlineWidth > 0.dp) {
                for (offsetX in -1..1) {
                    for (offsetY in -1..1) {
                        if (offsetX != 0 || offsetY != 0) {
                            if (text.selected) {
                                BasicTextField(
                                    value = textValue,
                                    onValueChange = {},
                                    textStyle = TextStyle(
                                        color = outlineColor,
                                        fontSize = text.fontSize,
                                        fontFamily = text.fontFamily,
                                        fontWeight = text.fontWeight,
                                        fontStyle = text.fontStyle,
                                        textAlign = text.textAlign,
                                        shadow = textShadow
                                    ),
                                    modifier = Modifier.offset(
                                        x = (offsetX * text.outlineWidth.value / 2).dp,
                                        y = (offsetY * text.outlineWidth.value / 2).dp
                                    ),
                                    singleLine = false,
                                    maxLines = Int.MAX_VALUE,
                                    decorationBox = { inner -> inner() },
                                    enabled = false
                                )
                            } else {
                                Text(
                                    text = text.text,
                                    style = TextStyle(
                                        color = outlineColor,
                                        fontSize = text.fontSize,
                                        fontFamily = text.fontFamily,
                                        fontWeight = text.fontWeight,
                                        fontStyle = text.fontStyle,
                                        textAlign = text.textAlign,
                                        shadow = textShadow
                                    ),
                                    modifier = Modifier.offset(
                                        x = (offsetX * text.outlineWidth.value / 2).dp,
                                        y = (offsetY * text.outlineWidth.value / 2).dp
                                    ),
                                    maxLines = Int.MAX_VALUE
                                )
                            }
                        }
                    }
                }
            }
            
            // Main text on top
            if (text.selected) {
                BasicTextField(
                    value = textValue,
                    onValueChange = {
                        textValue = it
                        onTextChange(it)
                    },
                    textStyle = TextStyle(
                        color = textColor,
                        fontSize = text.fontSize,
                        fontFamily = text.fontFamily,
                        fontWeight = text.fontWeight,
                        fontStyle = text.fontStyle,
                        textAlign = text.textAlign,
                        shadow = textShadow
                    ),
                    modifier = Modifier
                        .widthIn(max = text.maxWidth)
                        .wrapContentSize(),
                    singleLine = false,
                    maxLines = Int.MAX_VALUE,
                    decorationBox = { inner -> inner() }
                )
            } else {
                Text(
                    text = text.text,
                    style = TextStyle(
                        color = textColor,
                        fontSize = text.fontSize,
                        fontFamily = text.fontFamily,
                        fontWeight = text.fontWeight,
                        fontStyle = text.fontStyle,
                        textAlign = text.textAlign,
                        shadow = textShadow
                    ),
                    maxLines = Int.MAX_VALUE
                )
            }
        }
    }

    // Main text box container - size is determined by TextContent but stays consistent
    Box(
        modifier = modifier
            .graphicsLayer(
                rotationZ = rotation,
                scaleX = scale,
                scaleY = scale,
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0f) // Transform from top-left
            )
            .offset { IntOffset(offset.x.roundToInt(), offset.y.roundToInt()) }
            .border(
                width = if (text.selected) 2.dp else 0.dp,
                color = if (text.selected) {
                    if (text.locked) Color.Yellow else Color.Red
                } else {
                    Color.Transparent
                },
                shape = RoundedCornerShape(4.dp)
            )
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, rotationDelta ->
                    if (text.locked) return@detectTransformGestures
                    offset += pan
                    scale = (scale * zoom).coerceIn(0.5f, 3f)
                    rotation = ((rotation + rotationDelta + 180f) % 360f - 180f)
                    onTransformChange(offset, scale, rotation)
                }
            }
            .clickable { onSelect() }
            .padding(8.dp) // Fixed padding, not scaled
            .onGloballyPositioned { coordinates ->
                measuredSize = coordinates.size
                // Report width for saving purposes (includes padding)
                if (coordinates.size.width > 0) {
                    val measuredWidthPx = coordinates.size.width.toFloat()
                    if (measuredWidthPx != lastMeasuredWidthPx) {
                        lastMeasuredWidthPx = measuredWidthPx
                        onMeasuredWidthChange(measuredWidthPx)
                    }
                }
            }
    ) {
        TextContent()
        
        // Render selection handles outside the text content to not affect sizing
        if (text.selected) {
            SelectionHandle(Modifier.align(Alignment.TopStart))
            SelectionHandle(Modifier.align(Alignment.TopEnd))
            SelectionHandle(Modifier.align(Alignment.BottomStart))
            SelectionHandle(Modifier.align(Alignment.BottomEnd))
        }
    }
}

@Composable
private fun SelectionHandle(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .offset(x = (-4).dp, y = (-4).dp)
            .size(8.dp)
            .background(Color.White, RoundedCornerShape(2.dp))
            .border(1.dp, Color.Black.copy(alpha = 0.65f), RoundedCornerShape(2.dp))
    )
}
