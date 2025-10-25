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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun TextFormattingPanel(
    fontSize: Float,
    fontFamily: FontFamily,
    fontWeight: FontWeight,
    fontStyle: FontStyle,
    textAlign: TextAlign,
    onFontSizeChange: (Float) -> Unit,
    onFontFamilyChange: (FontFamily) -> Unit,
    onFontWeightChange: (FontWeight) -> Unit,
    onFontStyleChange: (FontStyle) -> Unit,
    onTextAlignChange: (TextAlign) -> Unit,
    modifier: Modifier = Modifier
) {
    var showFontMenu by remember { mutableStateOf(false) }
    
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
            "Text Formatting",
            style = MaterialTheme.typography.h6,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        // Font Size Slider
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Font Size", style = MaterialTheme.typography.body2)
                Text("${fontSize.toInt()}sp", style = MaterialTheme.typography.body2)
            }
            Slider(
                value = fontSize,
                onValueChange = onFontSizeChange,
                valueRange = 12f..120f,
                modifier = Modifier.fillMaxWidth()
            )
        }
        
        // Font Family Selector
        Box {
            Button(
                onClick = { showFontMenu = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = MaterialTheme.colors.surface
                )
            ) {
                Icon(Icons.Default.TextFields, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(
                    when (fontFamily) {
                        FontFamily.SansSerif -> "Sans Serif"
                        FontFamily.Serif -> "Serif"
                        FontFamily.Monospace -> "Monospace"
                        FontFamily.Cursive -> "Cursive"
                        else -> "Default"
                    }
                )
                Spacer(Modifier.weight(1f))
                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
            }
            
            DropdownMenu(
                expanded = showFontMenu,
                onDismissRequest = { showFontMenu = false }
            ) {
                DropdownMenuItem(onClick = {
                    onFontFamilyChange(FontFamily.SansSerif)
                    showFontMenu = false
                }) {
                    Text("Sans Serif")
                }
                DropdownMenuItem(onClick = {
                    onFontFamilyChange(FontFamily.Serif)
                    showFontMenu = false
                }) {
                    Text("Serif")
                }
                DropdownMenuItem(onClick = {
                    onFontFamilyChange(FontFamily.Monospace)
                    showFontMenu = false
                }) {
                    Text("Monospace")
                }
                DropdownMenuItem(onClick = {
                    onFontFamilyChange(FontFamily.Cursive)
                    showFontMenu = false
                }) {
                    Text("Cursive")
                }
            }
        }
        
        // Font Style Toggle Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Bold toggle
            Button(
                onClick = {
                    onFontWeightChange(
                        if (fontWeight == FontWeight.Bold) FontWeight.Normal
                        else FontWeight.Bold
                    )
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = if (fontWeight == FontWeight.Bold)
                        MaterialTheme.colors.primary
                    else MaterialTheme.colors.surface
                )
            ) {
                Icon(Icons.Default.FormatBold, contentDescription = "Bold")
            }
            
            // Italic toggle
            Button(
                onClick = {
                    onFontStyleChange(
                        if (fontStyle == FontStyle.Italic) FontStyle.Normal
                        else FontStyle.Italic
                    )
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = if (fontStyle == FontStyle.Italic)
                        MaterialTheme.colors.primary
                    else MaterialTheme.colors.surface
                )
            ) {
                Icon(Icons.Default.FormatItalic, contentDescription = "Italic")
            }
        }
        
        // Text Alignment
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { onTextAlignChange(TextAlign.Left) },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = if (textAlign == TextAlign.Left)
                        MaterialTheme.colors.primary
                    else MaterialTheme.colors.surface
                )
            ) {
                Icon(Icons.Default.FormatAlignLeft, contentDescription = "Align Left")
            }
            
            Button(
                onClick = { onTextAlignChange(TextAlign.Center) },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = if (textAlign == TextAlign.Center)
                        MaterialTheme.colors.primary
                    else MaterialTheme.colors.surface
                )
            ) {
                Icon(Icons.Default.FormatAlignCenter, contentDescription = "Align Center")
            }
            
            Button(
                onClick = { onTextAlignChange(TextAlign.Right) },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = if (textAlign == TextAlign.Right)
                        MaterialTheme.colors.primary
                    else MaterialTheme.colors.surface
                )
            ) {
                Icon(Icons.Default.FormatAlignRight, contentDescription = "Align Right")
            }
        }
    }
}
