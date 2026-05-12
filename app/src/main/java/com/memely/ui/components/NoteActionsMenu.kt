package com.memely.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.memely.nostr.NostrIdentifierFormatter
import android.widget.Toast as AndroidToast

@Composable
fun NoteActionsMenu(
    noteId: String,
    pubkey: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val expanded = remember { mutableStateOf(false) }
    
    Box(modifier = modifier) {
        IconButton(onClick = { expanded.value = true }) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = "More options",
                tint = MaterialTheme.colors.onSurface
            )
        }
        
        DropdownMenu(
            expanded = expanded.value,
            onDismissRequest = { expanded.value = false },
            modifier = Modifier.background(MaterialTheme.colors.surface)
        ) {
            // Copy npub
            DropdownMenuItem(
                onClick = {
                    val npub = NostrIdentifierFormatter.toNpub(pubkey)
                    copyToClipboard(context, npub, "npub copied")
                    expanded.value = false
                }
            ) {
                Column {
                    Text("Copy npub", style = MaterialTheme.typography.body2)
                    Text(
                        NostrIdentifierFormatter.toNpub(pubkey).take(20) + "...",
                        style = MaterialTheme.typography.caption,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f),
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
            
            // Copy pubkey (hex)
            DropdownMenuItem(
                onClick = {
                    copyToClipboard(context, pubkey, "Pubkey copied")
                    expanded.value = false
                }
            ) {
                Column {
                    Text("Copy pubkey", style = MaterialTheme.typography.body2)
                    Text(
                        pubkey.take(20) + "...",
                        style = MaterialTheme.typography.caption,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f),
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
            
            // Copy note ID
            DropdownMenuItem(
                onClick = {
                    val noteIdBech32 = NostrIdentifierFormatter.toNoteId(noteId)
                    copyToClipboard(context, noteIdBech32, "Note ID copied")
                    expanded.value = false
                }
            ) {
                Column {
                    Text("Copy note ID", style = MaterialTheme.typography.body2)
                    Text(
                        NostrIdentifierFormatter.toNoteId(noteId).take(20) + "...",
                        style = MaterialTheme.typography.caption,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f),
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

/**
 * Helper function to copy sensitive text (pubkeys/npubs) to clipboard with security measures
 * - Marks data as sensitive on Android 13+ to prevent clipboard history sync
 * - Auto-clears clipboard after 60 seconds
 * - Shows user warning about clipboard access
 */
private fun copyToClipboard(context: Context, text: String, message: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("Sensitive Data", text)
    
    // Mark as sensitive on Android 13+ (API 33+) to prevent history/sync
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        try {
            val extras = android.os.PersistableBundle().apply {
                putBoolean("android.content.extra.IS_SENSITIVE", true)
            }
            clip.description.extras = extras
        } catch (e: Exception) {
            // Fallback if extras not supported
        }
    }
    
    clipboard.setPrimaryClip(clip)
    
    // Show toast warning that data was copied
    AndroidToast.makeText(
        context,
        "$message (will be cleared in 60s)",
        AndroidToast.LENGTH_LONG
    ).show()
    
    // Auto-clear clipboard after 60 seconds to prevent unauthorized access
    Handler(Looper.getMainLooper()).postDelayed({
        try {
            val emptyClip = ClipData.newPlainText("", "")
            clipboard.setPrimaryClip(emptyClip)
        } catch (e: Exception) {
            // Silently fail if unable to clear
        }
    }, 60_000)  // 60 seconds
}
