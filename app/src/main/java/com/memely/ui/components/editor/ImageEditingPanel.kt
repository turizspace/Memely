package com.memely.ui.components.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

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
        
        // Rotation Slider
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Rotation", style = MaterialTheme.typography.body2)
                Text("${rotation.toInt()}°", style = MaterialTheme.typography.body2)
            }
            Slider(
                value = rotation,
                onValueChange = onRotationChange,
                valueRange = 0f..360f,
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
