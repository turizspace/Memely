package com.memely.ui.components.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import com.memely.ui.fonts.FontCatalog

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
    val currentFontOption = FontCatalog.getFontOptionByFamily(fontFamily)
    
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colors.surface,
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
            )
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
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
        
        // Font Family Selector with Preview
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
                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                    Text(
                        currentFontOption.name,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        currentFontOption.category,
                        fontSize = 12.sp,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                    )
                }
                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
            }
            
            // Dropdown menu with available fonts
            if (showFontMenu) {
                DropdownMenu(
                    expanded = showFontMenu,
                    onDismissRequest = { showFontMenu = false },
                    modifier = Modifier.widthIn(min = 250.dp, max = 320.dp)
                ) {
                    // Display fonts section
                    val displayFonts = FontCatalog.allFonts.filter { it.category == "Display" }
                    displayFonts.forEach { fontOption ->
                        DropdownMenuItem(
                            onClick = {
                                onFontFamilyChange(fontOption.fontFamily)
                                showFontMenu = false
                            }
                        ) {
                            Text(
                                fontOption.name,
                                fontFamily = fontOption.fontFamily,
                                color = if (fontOption.fontFamily == fontFamily)
                                    MaterialTheme.colors.primary
                                else
                                    MaterialTheme.colors.onSurface
                            )
                        }
                    }
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
