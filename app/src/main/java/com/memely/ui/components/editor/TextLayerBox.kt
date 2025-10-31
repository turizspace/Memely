package com.memely.ui.components.editor

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.memely.ui.viewmodels.MemeText
import kotlin.math.roundToInt

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
    var offset by remember { mutableStateOf(text.position) }
    var scale by remember { mutableStateOf(text.scale) }
    var rotation by remember { mutableStateOf(text.rotation) }
    var textValue by remember { mutableStateOf(text.text) }
    var hasReportedWidth by remember { mutableStateOf(false) }

    // keep state synced with external updates
    LaunchedEffect(text.position) { if (text.position != offset) offset = text.position }
    LaunchedEffect(text.scale) { if (text.scale != scale) scale = text.scale }
    LaunchedEffect(text.rotation) { if (text.rotation != rotation) rotation = text.rotation }
    
    // Reset width reporting when text or scale changes
    LaunchedEffect(text.text, text.scale) {
        hasReportedWidth = false
    }

    Box(
        modifier = modifier
            .offset { IntOffset(offset.x.roundToInt(), offset.y.roundToInt()) }
            .graphicsLayer(rotationZ = rotation)
            .border(
                width = if (text.selected) 2.dp else 0.dp,
                color = if (text.selected) Color.Red else Color.Transparent,
                shape = RoundedCornerShape(4.dp)
            )
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, rotationDelta ->
                    offset += pan
                    scale = (scale * zoom).coerceIn(0.5f, 3f)
                    rotation = ((rotation + rotationDelta + 180f) % 360f - 180f)
                    onTransformChange(offset, scale, rotation)
                }
            }
            .clickable { onSelect() }
            .padding((8 * scale).dp)
            .onGloballyPositioned { coordinates ->
                // Report width once after layout stabilizes to avoid recomposition loops
                if (!hasReportedWidth && coordinates.size.width > 0) {
                    hasReportedWidth = true
                    val measuredWidthPx = coordinates.size.width.toFloat()
                    onMeasuredWidthChange(measuredWidthPx)
                    println("📏 TextLayerBox measured width: ${measuredWidthPx}px for text='${text.text.take(20)}'")
                }
            }
    ) {
        if (text.selected) {
            BasicTextField(
                value = textValue,
                onValueChange = {
                    textValue = it
                    onTextChange(it)
                },
                textStyle = TextStyle(
                    color = text.color,
                    fontSize = text.fontSize * scale,
                    fontFamily = text.fontFamily,
                    fontWeight = text.fontWeight,
                    fontStyle = text.fontStyle,
                    textAlign = text.textAlign
                ),
                modifier = Modifier
                    .widthIn(max = text.maxWidth * scale)
                    .wrapContentSize(),
                singleLine = false,
                maxLines = Int.MAX_VALUE,
                decorationBox = { inner -> inner() }
            )
        } else {
            Text(
                text = text.text,
                style = TextStyle(
                    color = text.color,
                    fontSize = text.fontSize * scale,
                    fontFamily = text.fontFamily,
                    fontWeight = text.fontWeight,
                    fontStyle = text.fontStyle,
                    textAlign = text.textAlign
                ),
                modifier = Modifier
                    .widthIn(max = text.maxWidth * scale)
                    .wrapContentSize(),
                maxLines = Int.MAX_VALUE
            )
        }
    }
}
