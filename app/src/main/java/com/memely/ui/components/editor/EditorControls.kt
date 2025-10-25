package com.memely.ui.components.editor

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun EditorControls(
    canAddText: Boolean,
    onAddText: () -> Unit,
    onAddImage: () -> Unit,
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
    selectedIsImage: Boolean = false
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Scrollable tools row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onAddText,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(Icons.Default.TextFields, contentDescription = "Add Text", modifier = Modifier.size(20.dp))
            }

            IconButton(
                onClick = onAddImage,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(Icons.Default.Image, contentDescription = "Add Image", modifier = Modifier.size(20.dp))
            }

            // Show text formatting button when text is selected
            if (selectedIsText && onShowTextFormatting != null) {
                IconButton(
                    onClick = onShowTextFormatting,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(Icons.Default.EditNote, contentDescription = "Text Formatting", modifier = Modifier.size(20.dp))
                }
            }
            
            // Show image editing button when image is selected
            if (selectedIsImage && onShowImageEditing != null) {
                IconButton(
                    onClick = onShowImageEditing,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(Icons.Default.Edit, contentDescription = "Image Properties", modifier = Modifier.size(20.dp))
                }
            }

            IconButton(
                onClick = onChangeColor,
                modifier = Modifier.size(40.dp),
                enabled = canChangeColor
            ) {
                Icon(Icons.Default.ColorLens, contentDescription = "Text Color", modifier = Modifier.size(20.dp))
            }

            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(40.dp),
                enabled = canDelete
            ) {
                Icon(Icons.Default.Delete, contentDescription = "Delete Layer", modifier = Modifier.size(20.dp))
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
                        .weight(1f),
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
                        .weight(1f),
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
