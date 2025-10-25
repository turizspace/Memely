package com.memely.ui.components.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.MaterialTheme
import androidx.compose.material.ModalBottomSheetLayout
import androidx.compose.material.ModalBottomSheetValue
import androidx.compose.material.Text
import androidx.compose.material.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun ColorPickerDialog(
    onDismiss: () -> Unit,
    onColorSelected: (Color) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(
        initialValue = ModalBottomSheetValue.Expanded
    )
    
    // Dismiss when state changes to hidden
    if (!sheetState.isVisible) {
        LaunchedEffect(Unit) {
            onDismiss()
        }
    }
    
    ModalBottomSheetLayout(
        sheetContent = {
            ColorPickerContent(
                onColorSelected = { color ->
                    onColorSelected(color)
                    onDismiss()
                }
            )
        },
        sheetState = sheetState,
        scrimColor = Color.Black.copy(alpha = 0.32f)
    ) {}
}

@Composable
private fun ColorPickerContent(onColorSelected: (Color) -> Unit) {
    val colors = listOf(
        Color.White,
        Color.Black,
        Color.Red,
        Color.Green,
        Color.Blue,
        Color.Yellow,
        Color.Cyan,
        Color.Magenta,
        Color(0xFFFF6B6B),
        Color(0xFF4ECDC4),
        Color(0xFFFFB347),
        Color(0xFF95E1D3)
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colors.surface,
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
            )
            .padding(16.dp)
    ) {
        Text(
            "Pick Text Color",
            style = MaterialTheme.typography.h6,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        LazyVerticalGrid(
            columns = GridCells.Fixed(6),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            items(colors.size) { idx ->
                val color = colors[idx]
                Box(
                    modifier = Modifier
                        .size(50.dp)
                        .background(color, shape = RoundedCornerShape(8.dp))
                        .border(width = 2.dp, color = Color.Gray, shape = RoundedCornerShape(8.dp))
                        .clickable {
                            onColorSelected(color)
                        }
                )
            }
        }
    }
}
