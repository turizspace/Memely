package com.memely.ui.components.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/**
 * Custom rotation slider that visually fills from center (0°) to current rotation value.
 * Positive rotation fills to the right, negative fills to the left.
 */
@Composable
fun CenteredRotationSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float> = -180f..180f,
    modifier: Modifier = Modifier
) {
    var sliderSize by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current
    
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .onSizeChanged { sliderSize = it }
    ) {
        // Background track
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .align(Alignment.Center)
        ) {
            val trackHeight = size.height
            val trackWidth = size.width
            val centerX = trackWidth / 2f
            
            // Draw full track background (gray)
            drawLine(
                color = Color.Gray.copy(alpha = 0.3f),
                start = Offset(0f, trackHeight / 2f),
                end = Offset(trackWidth, trackHeight / 2f),
                strokeWidth = trackHeight
            )
            
            // Draw center marker (white line at 0°)
            drawLine(
                color = Color.White.copy(alpha = 0.6f),
                start = Offset(centerX, 0f),
                end = Offset(centerX, trackHeight),
                strokeWidth = 2f
            )
            
            // Calculate the filled portion from center
            val normalizedValue = (value - valueRange.start) / (valueRange.endInclusive - valueRange.start)
            val currentX = trackWidth * normalizedValue
            
            // Draw active track (filled from center to current position)
            if (value > 0) {
                // Positive rotation: fill from center to right
                drawLine(
                    color = Color(0xFF2196F3), // Blue color
                    start = Offset(centerX, trackHeight / 2f),
                    end = Offset(currentX, trackHeight / 2f),
                    strokeWidth = trackHeight
                )
            } else if (value < 0) {
                // Negative rotation: fill from center to left
                drawLine(
                    color = Color(0xFFFF9800), // Orange color
                    start = Offset(currentX, trackHeight / 2f),
                    end = Offset(centerX, trackHeight / 2f),
                    strokeWidth = trackHeight
                )
            }
        }
        
        // Slider thumb
        val normalizedValue = (value - valueRange.start) / (valueRange.endInclusive - valueRange.start)
        val thumbOffsetX = with(density) {
            (sliderSize.width * normalizedValue).toDp() - 12.dp
        }
        
        Box(
            modifier = Modifier
                .offset(x = thumbOffsetX)
                .size(24.dp)
                .align(Alignment.CenterStart)
                .clip(CircleShape)
                .background(MaterialTheme.colors.primary)
        )
        
        // Touch handling
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectHorizontalDragGestures { change, _ ->
                        val x = change.position.x.coerceIn(0f, sliderSize.width.toFloat())
                        val percent = x / sliderSize.width
                        val newValue = valueRange.start + (percent * (valueRange.endInclusive - valueRange.start))
                        onValueChange(newValue.coerceIn(valueRange))
                    }
                }
        )
    }
}

@Composable
fun ImageEditingPanel(
    cornerRadius: Float,
    alpha: Float,
    rotation: Float,
    scale: Float,
    onCornerRadiusChange: (Float) -> Unit,
    onAlphaChange: (Float) -> Unit,
    onRotationChange: (Float) -> Unit,
    onScaleChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colors.surface,
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
            )
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "Image Properties",
            style = MaterialTheme.typography.h6,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        // Corner Radius Slider
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Corner Radius", style = MaterialTheme.typography.body2)
                Text("${cornerRadius.toInt()}dp", style = MaterialTheme.typography.body2)
            }
            Slider(
                value = cornerRadius,
                onValueChange = onCornerRadiusChange,
                valueRange = 0f..50f,
                modifier = Modifier.fillMaxWidth()
            )
        }
        
        // Alpha/Opacity Slider
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Opacity", style = MaterialTheme.typography.body2)
                Text("${(alpha * 100).toInt()}%", style = MaterialTheme.typography.body2)
            }
            Slider(
                value = alpha,
                onValueChange = onAlphaChange,
                valueRange = 0f..1f,
                modifier = Modifier.fillMaxWidth()
            )
        }
        
        // Rotation Slider (centered at 0°, supports negative/positive rotation)
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Rotation", style = MaterialTheme.typography.body2)
                Text("${rotation.toInt()}°", style = MaterialTheme.typography.body2)
            }
            CenteredRotationSlider(
                value = rotation,
                onValueChange = onRotationChange,
                valueRange = -180f..180f,
                modifier = Modifier.fillMaxWidth()
            )
        }
        
        // Scale Slider
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Scale", style = MaterialTheme.typography.body2)
                Text("${(scale * 100).toInt()}%", style = MaterialTheme.typography.body2)
            }
            Slider(
                value = scale,
                onValueChange = onScaleChange,
                valueRange = 0.1f..3f,
                modifier = Modifier.fillMaxWidth()
            )
        }
        
        Divider(modifier = Modifier.padding(vertical = 8.dp))
        
        Text(
            "Tip: Use pinch gestures on the canvas to scale and rotate",
            style = MaterialTheme.typography.caption,
            color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
        )
    }
}
