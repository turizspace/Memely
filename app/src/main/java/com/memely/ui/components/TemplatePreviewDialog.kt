package com.memely.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.memely.data.MemeTemplate

@Composable
fun TemplatePreviewDialog(
    template: MemeTemplate,
    onDismiss: () -> Unit,
    onConfirm: (MemeTemplate) -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colors.surface,
            elevation = 8.dp
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header with close button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Template Preview",
                        style = MaterialTheme.typography.h6,
                        fontWeight = FontWeight.SemiBold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close preview"
                        )
                    }
                }

                // Template image preview
                val fullUrl = if (template.url.startsWith("http")) {
                    template.url
                } else {
                    "https://turiz.space" + template.url
                }

                AsyncImage(
                    model = fullUrl,
                    contentDescription = template.name,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp)
                        .padding(16.dp)
                        .background(
                            Color.Black.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(12.dp)
                        ),
                    contentScale = ContentScale.Fit
                )

                // Template name
                Text(
                    text = template.name
                        .replace(".jpg", "")
                        .replace(".png", "")
                        .replace(".gif", "")
                        .replace(".webp", "")
                        .replace(".jpeg", ""),
                    style = MaterialTheme.typography.body1,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Action buttons
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancel")
                    }

                    Button(
                        onClick = { onConfirm(template) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = MaterialTheme.colors.primary,
                            contentColor = Color.White
                        )
                    ) {
                        Text("Edit")
                    }
                }
            }
        }
    }
}
