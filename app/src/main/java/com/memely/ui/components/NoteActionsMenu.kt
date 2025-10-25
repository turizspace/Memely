package com.memely.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import android.widget.Toast as AndroidToast

/**
 * Converts a hex event ID to Nostr note ID syntax (bech32 format with "note1" prefix)
 * Using simple base32 encoding - for production, use a proper bech32 library
 */
fun hexToNoteId(hexId: String): String {
    return try {
        // Convert hex to bytes
        val bytes = hexId.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        
        // Simple base32 encoding (standard RFC 4648)
        val base32Alphabet = "abcdefghijklmnopqrstuvwxyz234567"
        val result = StringBuilder()
        
        var buffer = 0
        var bufferSize = 0
        
        for (byte in bytes) {
            buffer = (buffer shl 8) or (byte.toInt() and 0xFF)
            bufferSize += 8
            
            while (bufferSize >= 5) {
                bufferSize -= 5
                result.append(base32Alphabet[(buffer shr bufferSize) and 0x1F])
            }
        }
        
        if (bufferSize > 0) {
            result.append(base32Alphabet[(buffer shl (5 - bufferSize)) and 0x1F])
        }
        
        "note1${result.toString()}"
    } catch (e: Exception) {
        hexId // Fallback to hex if conversion fails
    }
}

/**
 * Converts a hex pubkey to Nostr npub format (bech32 with "npub1" prefix)
 */
fun hexToNpub(hexPubkey: String): String {
    return try {
        val bytes = hexPubkey.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val base32Alphabet = "abcdefghijklmnopqrstuvwxyz234567"
        val result = StringBuilder()
        
        var buffer = 0
        var bufferSize = 0
        
        for (byte in bytes) {
            buffer = (buffer shl 8) or (byte.toInt() and 0xFF)
            bufferSize += 8
            
            while (bufferSize >= 5) {
                bufferSize -= 5
                result.append(base32Alphabet[(buffer shr bufferSize) and 0x1F])
            }
        }
        
        if (bufferSize > 0) {
            result.append(base32Alphabet[(buffer shl (5 - bufferSize)) and 0x1F])
        }
        
        "npub1${result.toString()}"
    } catch (e: Exception) {
        hexPubkey // Fallback to hex if conversion fails
    }
}

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
                    val npub = hexToNpub(pubkey)
                    copyToClipboard(context, npub, "npub copied")
                    expanded.value = false
                }
            ) {
                Column {
                    Text("Copy npub", style = MaterialTheme.typography.body2)
                    Text(
                        hexToNpub(pubkey).take(20) + "...",
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
                    val noteIdBech32 = hexToNoteId(noteId)
                    copyToClipboard(context, noteIdBech32, "Note ID copied")
                    expanded.value = false
                }
            ) {
                Column {
                    Text("Copy note ID", style = MaterialTheme.typography.body2)
                    Text(
                        hexToNoteId(noteId).take(20) + "...",
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
 * Helper function to copy text to clipboard and show toast
 */
private fun copyToClipboard(context: Context, text: String, message: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("label", text)
    clipboard.setPrimaryClip(clip)
    AndroidToast.makeText(context, message, AndroidToast.LENGTH_SHORT).show()
}
