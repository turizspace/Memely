package com.memely.ui.components.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.Slider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.memely.ui.tutorial.tutorialTarget

@Composable
fun EditorControls(
    canAddText: Boolean,
    onAddText: () -> Unit,
    onAddImage: () -> Unit,
    onNavigateToHomeFeed: () -> Unit,
    canChangeColor: Boolean,
    onChangeColor: () -> Unit,
    canDelete: Boolean,
    onDelete: () -> Unit,
    isSaving: Boolean,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
    onPostToNostr: (() -> Unit)? = null,
    isPostingToNostr: Boolean = false,
    onSaveToDevice: (() -> Unit)? = null,
    isSavingToDevice: Boolean = false,
    onShowTextFormatting: (() -> Unit)? = null,
    onShowImageEditing: (() -> Unit)? = null,
    selectedIsText: Boolean = false,
    selectedIsImage: Boolean = false,
    onGloballyPositioned: (LayoutCoordinates) -> Unit = {},
    onOutlineWidthChange: ((androidx.compose.ui.unit.Dp) -> Unit)? = null,
    outlineWidth: androidx.compose.ui.unit.Dp = 0.dp
) {
    var showImageMenu by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(8.dp)
            .onGloballyPositioned(onGloballyPositioned)
            .tutorialTarget("editor_controls"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Scrollable tools row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Add Text button
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.tutorialTarget("btn_add_text")
            ) {
                IconButton(
                    onClick = onAddText,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(Icons.Default.TextFields, contentDescription = "Add Text", modifier = Modifier.size(20.dp))
                }
                Text(
                    text = "Text",
                    fontSize = 10.sp,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f)
                )
            }

            // Image button with floating menu
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.tutorialTarget("btn_add_image")
            ) {
                Box {
                    IconButton(
                        onClick = { showImageMenu = true },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(Icons.Default.Image, contentDescription = "Add Image", modifier = Modifier.size(20.dp))
                    }

                    // Floating menu for image selection
                    if (showImageMenu) {
                        ImageSelectionMenu(
                            onFromDevice = {
                                onAddImage()
                                showImageMenu = false
                            },
                            onFromTemplates = {
                                onNavigateToHomeFeed()
                                showImageMenu = false
                            },
                            onDismiss = { showImageMenu = false }
                        )
                    }
                }
                Text(
                    text = "Image",
                    fontSize = 10.sp,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f)
                )
            }

            // Show text formatting button when text is selected
            if (selectedIsText && onShowTextFormatting != null) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.tutorialTarget("btn_text_format")
                ) {
                    IconButton(
                        onClick = onShowTextFormatting,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(Icons.Default.EditNote, contentDescription = "Text Formatting", modifier = Modifier.size(20.dp))
                    }
                    Text(
                        text = "Format",
                        fontSize = 10.sp,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f)
                    )
                }
            }
            
            // Show image editing button when image is selected
            if (selectedIsImage && onShowImageEditing != null) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    IconButton(
                        onClick = onShowImageEditing,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = "Image Properties", modifier = Modifier.size(20.dp))
                    }
                    Text(
                        text = "Edit",
                        fontSize = 10.sp,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f)
                    )
                }
            }

            // Color picker button
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                IconButton(
                    onClick = onChangeColor,
                    modifier = Modifier.size(40.dp),
                    enabled = canChangeColor
                ) {
                    Icon(
                        Icons.Default.ColorLens, 
                        contentDescription = "Text Color", 
                        modifier = Modifier.size(20.dp),
                        tint = if (canChangeColor) MaterialTheme.colors.onSurface else MaterialTheme.colors.onSurface.copy(alpha = 0.3f)
                    )
                }
                Text(
                    text = "Color",
                    fontSize = 10.sp,
                    color = if (canChangeColor) MaterialTheme.colors.onSurface.copy(alpha = 0.7f) else MaterialTheme.colors.onSurface.copy(alpha = 0.3f)
                )
            }

            // Delete button
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(40.dp),
                    enabled = canDelete
                ) {
                    Icon(
                        Icons.Default.Delete, 
                        contentDescription = "Delete Layer", 
                        modifier = Modifier.size(20.dp),
                        tint = if (canDelete) MaterialTheme.colors.onSurface else MaterialTheme.colors.onSurface.copy(alpha = 0.3f)
                    )
                }
                Text(
                    text = "Delete",
                    fontSize = 10.sp,
                    color = if (canDelete) MaterialTheme.colors.onSurface.copy(alpha = 0.7f) else MaterialTheme.colors.onSurface.copy(alpha = 0.3f)
                )
            }

            // Outline width control (only for text)
            if (selectedIsText && onOutlineWidthChange != null) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.width(IntrinsicSize.Max)
                ) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colors.surface),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Decrease button
                        IconButton(
                            onClick = {
                                val newValue = (outlineWidth.value - 1f).coerceAtLeast(0f)
                                onOutlineWidthChange(androidx.compose.ui.unit.Dp(newValue))
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Text("-", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                        
                        // Value display
                        Text(
                            text = outlineWidth.value.toInt().toString(),
                            modifier = Modifier
                                .width(24.dp)
                                .align(Alignment.CenterVertically),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colors.onSurface,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        
                        // Increase button
                        IconButton(
                            onClick = {
                                val newValue = (outlineWidth.value + 1f).coerceAtMost(4f)
                                onOutlineWidthChange(androidx.compose.ui.unit.Dp(newValue))
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Text("+", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Text(
                        text = "Outline",
                        fontSize = 10.sp,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f)
                    )
                }
            }
        }

        // Action buttons row
        Row(
            modifier = Modifier
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Save to Device button
            if (onSaveToDevice != null) {
                Button(
                    onClick = onSaveToDevice,
                    enabled = !isSavingToDevice && !isSaving && !isPostingToNostr,
                    modifier = Modifier
                        .height(36.dp)
                        .weight(1f)
                        .tutorialTarget("btn_save"),
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = MaterialTheme.colors.surface
                    ),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    if (isSavingToDevice) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 1.5.dp,
                            color = MaterialTheme.colors.primary
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Saving...", fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    } else {
                        Icon(
                            Icons.Default.Save,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Save", fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }

            // Post to Nostr button
            if (onPostToNostr != null) {
                Button(
                    onClick = onPostToNostr,
                    enabled = !isPostingToNostr && !isSaving && !isSavingToDevice,
                    modifier = Modifier
                        .height(36.dp)
                        .weight(1f)
                        .tutorialTarget("btn_post_nostr"),
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = Color(0xFF8B5CF6) // Purple color for Nostr
                    ),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    if (isPostingToNostr) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 1.5.dp,
                            color = Color.White
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Posting...", color = Color.White, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    } else {
                        Icon(
                            Icons.Default.CloudUpload,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = Color.White
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Post Nostr", color = Color.White, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
private fun ImageSelectionMenu(
    onFromDevice: () -> Unit,
    onFromTemplates: () -> Unit,
    onDismiss: () -> Unit
) {
    Popup(
        onDismissRequest = onDismiss,
        properties = PopupProperties(
            focusable = true,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Column(
            modifier = Modifier
                .offset(x = (-8).dp, y = (-12).dp) // Position above the button
                .shadow(
                    elevation = 8.dp,
                    shape = RoundedCornerShape(12.dp)
                )
                .background(
                    color = MaterialTheme.colors.surface,
                    shape = RoundedCornerShape(12.dp)
                )
                .clip(RoundedCornerShape(12.dp))
                .width(IntrinsicSize.Max)
                .padding(4.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            // From Templates option
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onFromTemplates() }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.GridView,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colors.primary
                )
                Text(
                    text = "Browse Templates",
                    style = MaterialTheme.typography.body2,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colors.onSurface
                )
            }

            // Divider
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(MaterialTheme.colors.onSurface.copy(alpha = 0.1f))
                    .padding(horizontal = 16.dp)
            )

            // From Device option
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onFromDevice() }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.PhotoLibrary,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colors.primary
                )
                Text(
                    text = "From Device",
                    style = MaterialTheme.typography.body2,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colors.onSurface
                )
            }
        }
    }
}
