package com.memely.ui.components.editor

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.Text
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.ui.unit.dp
import com.memely.ui.viewmodels.MemeText

@Composable
fun TextLayerBox(
    text: MemeText,
    index: Int,
    onTextChange: (String) -> Unit,
    onTransformChange: (Offset, Float, Float) -> Unit,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Sync with external state changes
    var offset by remember(text.position) { mutableStateOf(text.position) }
    var scale by remember(text.scale) { mutableStateOf(text.scale) }
    var rotation by remember(text.rotation) { mutableStateOf(text.rotation) }
    var textValue by remember(text.text) { mutableStateOf(text.text) }

    Box(
        modifier = modifier
            .offset { IntOffset(offset.x.roundToInt(), offset.y.roundToInt()) }
            .graphicsLayer(scaleX = scale, scaleY = scale, rotationZ = rotation)
            .border(
                width = if (text.selected) 2.dp else 0.dp,
                color = if (text.selected) Color.Red else Color.Transparent,
                shape = RoundedCornerShape(4.dp)
            )
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, rotationDelta ->
                    offset += pan
                    scale = (scale * zoom).coerceIn(0.5f, 3f)
                    rotation += rotationDelta
                    onTransformChange(offset, scale, rotation)
                    println("✏️ TextLayerBox transform idx=$index offset=$offset scale=$scale rotation=$rotation")
                }
            }
            .clickable {
                onSelect()
            }
            .padding(8.dp)
    ) {
        if (text.selected) {
            // Editable when selected
            BasicTextField(
                value = textValue,
                onValueChange = {
                    textValue = it
                    onTextChange(it)
                },
                textStyle = TextStyle(
                    color = text.color,
                    fontSize = text.fontSize,
                    fontFamily = text.fontFamily,
                    fontWeight = text.fontWeight,
                    fontStyle = text.fontStyle,
                    textAlign = text.textAlign
                ),
                modifier = Modifier.wrapContentSize()
            )
        } else {
            // Display-only when not selected
            Text(
                text = text.text,
                style = TextStyle(
                    color = text.color,
                    fontSize = text.fontSize,
                    fontFamily = text.fontFamily,
                    fontWeight = text.fontWeight,
                    fontStyle = text.fontStyle,
                    textAlign = text.textAlign
                ),
                modifier = Modifier.wrapContentSize()
            )
        }
    }
}
