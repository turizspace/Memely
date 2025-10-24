package com.memely.ui.components.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

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
    isPostingToNostr: Boolean = false
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(8.dp),
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

        Spacer(Modifier.weight(1f))

        // Post to Nostr button (optional)
        if (onPostToNostr != null) {
            Button(
                onClick = onPostToNostr,
                enabled = !isPostingToNostr && !isSaving,
                modifier = Modifier.height(40.dp),
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = Color(0xFF8B5CF6) // Purple color for Nostr
                )
            ) {
                if (isPostingToNostr) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = Color.White
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Posting...", color = Color.White)
                } else {
                    Icon(
                        Icons.Default.CloudUpload,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = Color.White
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("Post to Nostr", color = Color.White)
                }
            }
            Spacer(Modifier.width(4.dp))
        }

        Button(
            onClick = onSave,
            enabled = !isSaving,
            modifier = Modifier.height(40.dp)
        ) {
            if (isSaving) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                Spacer(Modifier.width(8.dp))
                Text("Saving...")
            } else {
                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(4.dp))
                Text("Save")
            }
        }
    }
}
