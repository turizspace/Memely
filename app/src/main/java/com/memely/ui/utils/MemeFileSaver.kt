package com.memely.ui.utils

import android.content.Context
import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.IntSize
import com.memely.network.SecureHttpClient
import com.memely.ui.viewmodels.MemeOverlayImage
import com.memely.ui.viewmodels.MemeText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

object MemeFileSaver {
    
    /**
     * Wraps text to fit within a maximum width by breaking it into lines at word boundaries.
     * This matches Compose's text wrapping behavior for WYSIWYG rendering.
     */
    private fun wrapText(text: String, paint: Paint, maxWidth: Float): List<String> {
        if (maxWidth <= 0) return text.split("\n")
        
        val lines = mutableListOf<String>()
        
        // First split by manual newlines
        val paragraphs = text.split("\n")
        
        paragraphs.forEach { paragraph ->
            if (paragraph.isEmpty()) {
                lines.add("")
                return@forEach
            }
            
            val words = paragraph.split(" ")
            var currentLine = ""
            
            words.forEach { word ->
                val testLine = if (currentLine.isEmpty()) word else "$currentLine $word"
                val testWidth = paint.measureText(testLine)
                
                if (testWidth <= maxWidth) {
                    currentLine = testLine
                } else {
                    // Current line is full, start a new line
                    if (currentLine.isNotEmpty()) {
                        lines.add(currentLine)
                    }
                    currentLine = word
                    
                    // If single word is too long, we still add it (can't break further)
                    if (paint.measureText(word) > maxWidth) {
                        lines.add(currentLine)
                        currentLine = ""
                    }
                }
            }
            
            // Add the last line
            if (currentLine.isNotEmpty()) {
                lines.add(currentLine)
            }
        }
        
        return lines.ifEmpty { listOf(text) }
    }
    
    private suspend fun downloadImageToCache(context: Context, url: String): Uri? {
        return try {
            withContext(Dispatchers.IO) {
                val httpClient = SecureHttpClient.createDownloadClient()
                val request = Request.Builder().url(url).build()
                httpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val tempFile = File(context.cacheDir, "meme_template_${System.currentTimeMillis()}.jpg")
                        tempFile.outputStream().use { output ->
                            response.body?.bytes()?.let { output.write(it) }
                        }
                        Uri.fromFile(tempFile)
                    } else {
                        null
                    }
                }
            }
        } catch (e: Exception) {
            println("❌ Failed to download image: ${e.message}")
            null
        }
    }
    
    fun saveMeme(
        context: Context,
        imageUri: Uri,
        texts: List<MemeText>,
        overlays: List<MemeOverlayImage>,
        baseImageSize: IntSize,
        originalImageWidth: Int = 0,
        originalImageHeight: Int = 0,
        imageOffsetX: Float = 0f,
        imageOffsetY: Float = 0f,
        onSuccess: (String) -> Unit,
        onError: () -> Unit
    ) {
        try {
            // For HTTP(S) URLs, try to download to cache first, but with timeout
            val resolvedUri = if (imageUri.toString().startsWith("http")) {
                try {
                    runBlocking {
                        withContext(Dispatchers.IO) {
                            // Use timeout to prevent hanging
                            kotlinx.coroutines.withTimeoutOrNull(5000L) {
                                downloadImageToCache(context, imageUri.toString())
                            } ?: imageUri
                        }
                    }
                } catch (e: Exception) {
                    println("⚠️ Failed to download image, using original URI: ${e.message}")
                    imageUri
                }
            } else {
                imageUri
            }
            
            // Get base image dimensions (use provided or decode)
            var baseWidth = originalImageWidth
            var baseHeight = originalImageHeight
            
            if (baseWidth == 0 || baseHeight == 0) {
                val baseOptions = android.graphics.BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                context.contentResolver.openInputStream(resolvedUri)?.use { baseStream ->
                    android.graphics.BitmapFactory.decodeStream(baseStream, null, baseOptions)
                }
                baseWidth = baseOptions.outWidth
                baseHeight = baseOptions.outHeight
            }

            // Create bitmap with original image dimensions for high quality
            val bitmap = Bitmap.createBitmap(baseWidth, baseHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)

            // Draw base image
            context.contentResolver.openInputStream(resolvedUri)?.use {
                android.graphics.BitmapFactory.decodeStream(it)?.let { baseBmp ->
                    canvas.drawBitmap(baseBmp, 0f, 0f, paint)
                    baseBmp.recycle()
                }
            }

            // Calculate scale factors between displayed size and original size
            // Use separate X and Y scales to handle any aspect ratio differences
            val scaleX = baseWidth.toFloat() / baseImageSize.width.toFloat()
            val scaleY = baseHeight.toFloat() / baseImageSize.height.toFloat()
            
            // For ContentScale.Fit, both scales should be the same, but calculate separately for robustness
            val scale = scaleX  // Use scaleX for uniform scaling (should equal scaleY for Fit)

            println("🔍 MemeFileSaver: Original=${baseWidth}x${baseHeight}, Displayed=${baseImageSize.width}x${baseImageSize.height}, Offset=(${imageOffsetX},${imageOffsetY}), ScaleX=${scaleX}, ScaleY=${scaleY}")

            // Draw overlay images - FIXED POSITIONING
            overlays.forEach { overlay ->
                context.contentResolver.openInputStream(overlay.uri)?.use {
                    android.graphics.BitmapFactory.decodeStream(it)?.let { overlayBmp ->
                        // Calculate the display size in pixels (convert dp to pixels first)
                        val density = context.resources.displayMetrics.density
                        val displayWidthPx = overlay.displayWidth.value * density
                        val displayHeightPx = displayWidthPx * overlayBmp.height / overlayBmp.width
                        
                        // Apply user's scale and convert to original image coordinates
                        val finalWidth = (displayWidthPx * overlay.scale * scale).toInt()
                        val finalHeight = (displayHeightPx * overlay.scale * scale).toInt()

                        var scaledBmp = Bitmap.createScaledBitmap(
                            overlayBmp,
                            finalWidth,
                            finalHeight,
                            true
                        )
                        
                        // Apply corner radius if specified
                        if (overlay.cornerRadius.value > 0) {
                            val radiusPx = (overlay.cornerRadius.value * density).toInt()
                            scaledBmp = createRoundedBitmap(scaledBmp, radiusPx)
                        }

                        // Apply position scaling, accounting for image offset
                        // Use separate X/Y scales for accurate positioning
                        val adjustedX = overlay.position.x - imageOffsetX
                        val adjustedY = overlay.position.y - imageOffsetY
                        val scaledX = adjustedX * scaleX
                        val scaledY = adjustedY * scaleY

                        // Apply alpha to paint
                        paint.alpha = (overlay.alpha * 255).toInt()

                        // FIX: Draw at exact position without extra translations
                        println("🔎 MemeFileSaver overlay: uri=${overlay.uri}, pos=(${overlay.position.x},${overlay.position.y}), adjusted=(${adjustedX},${adjustedY}), scaled=(${scaledX},${scaledY}), final=(${finalWidth}x${finalHeight}), userScale=${overlay.scale}, rotation=${overlay.rotation}, alpha=${overlay.alpha}, cornerRadius=${overlay.cornerRadius.value}")
                        canvas.save()
                        canvas.translate(scaledX, scaledY) // Position to top-left corner
                        canvas.rotate(overlay.rotation, finalWidth / 2f, finalHeight / 2f) // Rotate around center
                        canvas.drawBitmap(scaledBmp, 0f, 0f, paint)
                        canvas.restore()
                        
                        // Reset paint alpha
                        paint.alpha = 255

                        scaledBmp.recycle()
                        overlayBmp.recycle()
                    }
                }
            }

            // Draw text layers - ACCOUNTING FOR SCALE/ROTATION TRANSFORMS
            texts.forEach { text ->
                // Skip empty text
                if (text.text.isBlank()) {
                    println("⚠️ MemeFileSaver: Skipping empty text")
                    return@forEach
                }
                
                val density = context.resources.displayMetrics.density

                // First paint at display scale to measure text dimensions in screen space
                val textPaintForMeasure = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = text.color.toArgb()
                    textSize = text.fontSize.value * density  // Base fontSize only (scale handled separately)
                    isFakeBoldText = true
                    style = Paint.Style.FILL
                    // Convert Compose TextAlign to Paint.Align
                    textAlign = when (text.textAlign) {
                        androidx.compose.ui.text.style.TextAlign.Left,
                        androidx.compose.ui.text.style.TextAlign.Start -> Paint.Align.LEFT
                        androidx.compose.ui.text.style.TextAlign.Center -> Paint.Align.CENTER
                        androidx.compose.ui.text.style.TextAlign.Right,
                        androidx.compose.ui.text.style.TextAlign.End -> Paint.Align.RIGHT
                        else -> Paint.Align.LEFT  // Default for Justify or unspecified
                    }
                }

                // Fixed padding of 8.dp (not scaled with text scale anymore)
                val textPaddingPx = 8f * density

                // Use the measured width from the editor if available, otherwise calculate it
                val maxTextWidthPx = if (text.measuredWidthPx > 0) {
                    text.measuredWidthPx
                } else {
                    text.maxWidth.value * density  // Base maxWidth (scale handled separately)
                }
                
                // Wrap text to match Compose's text wrapping behavior
                val lines = wrapText(text.text, textPaintForMeasure, maxTextWidthPx)
                
                // Measure text dimensions in display space (before scaling to original image)
                val fm = textPaintForMeasure.fontMetrics
                val lineHeight = fm.descent - fm.ascent
                val baselineOffset = -fm.ascent
                
                // Calculate total text height in display space
                val totalTextHeight = lineHeight * lines.size
                
                // Find the widest line for rotation center calculation
                val textWidth = lines.maxOfOrNull { line -> textPaintForMeasure.measureText(line) } ?: 0f
                
                // Debug: Log text layout info
                println("🔤 Text layout: maxWidth=${maxTextWidthPx}, lines=${lines.size}, widest=${textWidth}, text='${text.text.take(50)}...'")
                lines.forEachIndexed { i, line -> 
                    val lineWidth = textPaintForMeasure.measureText(line)
                    println("   Line $i: width=${lineWidth}, text='$line'")
                }
                
                // COORDINATE SYSTEM:
                // text.position.x/y = screen/canvas coordinates (where user placed it)
                // imageOffsetX/Y = where the image is centered in the canvas
                // To get image coordinates: position - imageOffset
                // Then add padding offset to account for the 8.dp internal padding
                
                val posRelativeToImage = Offset(
                    text.position.x - imageOffsetX,
                    text.position.y - imageOffsetY
                )
                
                // Add padding offset (where text actually starts inside the box)
                val adjustedX = posRelativeToImage.x + textPaddingPx
                val adjustedY = posRelativeToImage.y + textPaddingPx
                
                // Now scale to original image coordinates using separate X/Y scales
                // Also apply user's scale transform to match graphicsLayer scaling
                val scaledX = adjustedX * scaleX
                val scaledY = adjustedY * scaleY

                // Create final paint with full scale applied
                // Apply text.scale (user's gesture scale) and scaleX (display to image scale)
                val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = text.color.toArgb()
                    textSize = textPaintForMeasure.textSize * text.scale * scaleX  // Base size * user scale * image scale
                    isFakeBoldText = true
                    style = Paint.Style.FILL
                    // Match the alignment from measure paint
                    textAlign = textPaintForMeasure.textAlign
                }

                // Calculate scaled dimensions for rotation center
                // Apply both user scale and image scale
                val scaledTextWidth = textWidth * text.scale * scaleX
                val scaledTotalTextHeight = totalTextHeight * text.scale * scaleY

                // Calculate the line height in the final scaled space
                val scaledLineHeight = lineHeight * text.scale * scaleY
                val scaledBaselineOffset = baselineOffset * text.scale * scaleY

                // Log computed values for debugging alignment
                println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                println("🔎 MemeFileSaver TEXT DEBUG:")
                println("  Text: '${text.text.take(30)}...'")
                println("  Screen Position: (${text.position.x}, ${text.position.y})")
                println("  Image Offset: (${imageOffsetX}, ${imageOffsetY})")
                println("  Relative to Image: (${posRelativeToImage.x}, ${posRelativeToImage.y})")
                println("  Padding: ${textPaddingPx}px (8dp * density, fixed)")
                println("  Adjusted (with padding): (${adjustedX}, ${adjustedY})")
                println("  Scale Factors: scaleX=${scaleX}, scaleY=${scaleY}, userScale=${text.scale}")
                println("  Scaled to Image: (${scaledX}, ${scaledY})")
                println("  ⚠️ EXPECTED in editor: text box outer edge at (${text.position.x}, ${text.position.y})")
                println("  ⚠️ ACTUAL in saved image: text content starts at (${scaledX}, ${scaledY})")
                println("  User Scale: ${text.scale}x, Rotation: ${text.rotation}°")
                println("  Display Font Size (base): ${textPaintForMeasure.textSize}px")
                println("  Final Font Size (with scales): ${textPaint.textSize}px")
                println("  Text Dimensions (display, base): ${textWidth}x${totalTextHeight}px")
                println("  Text Dimensions (final, scaled): ${scaledTextWidth}x${scaledTotalTextHeight}px")
                println("  Rotation Center: (${scaledTextWidth / 2f}, ${scaledTotalTextHeight / 2f})")
                println("  Lines: ${lines.size}, Max Width: ${maxTextWidthPx}px")
                println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")

                // Draw text rotated around its center to match Compose's graphicsLayer default transform origin
                canvas.save()
                canvas.translate(scaledX, scaledY)
                canvas.rotate(text.rotation, scaledTextWidth / 2f, scaledTotalTextHeight / 2f)
                
                // Calculate X offset based on text alignment
                // Paint.Align handles the positioning: LEFT draws from x, CENTER from x-width/2, RIGHT from x-width
                val alignmentX = when (textPaint.textAlign) {
                    Paint.Align.LEFT -> 0f
                    Paint.Align.CENTER -> maxTextWidthPx * text.scale * scaleX / 2f  // Center within max width (with all scales)
                    Paint.Align.RIGHT -> maxTextWidthPx * text.scale * scaleX  // Right-align to max width (with all scales)
                    else -> 0f
                }
                
                // Draw each line using the final paint (with alignment)
                lines.forEachIndexed { lineIndex, line ->
                    val lineY = lineIndex * scaledLineHeight + scaledBaselineOffset
                    canvas.drawText(line, alignmentX, lineY, textPaint)
                }
                
                canvas.restore()
            }

            // Save to device using scoped storage (Android 10+) or legacy storage (pre-Android 10)
            val filename = "meme_${System.currentTimeMillis()}.jpg"
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10+ - Use MediaStore (scoped storage)
                val contentValues = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/Memely")
                }
                
                val memeUri = context.contentResolver.insert(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    contentValues
                )
                
                if (memeUri != null) {
                    context.contentResolver.openOutputStream(memeUri)?.use { output ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 95, output)
                    }
                    bitmap.recycle()
                    println("✅ MemeEditor: Saved meme to scoped storage: $memeUri")
                    onSuccess(memeUri.toString())
                } else {
                    println("❌ MemeEditor: Failed to create media store entry")
                    bitmap.recycle()
                    onError()
                }
            } else {
                // Pre-Android 10 - Legacy external storage (deprecated but necessary for older devices)
                @Suppress("DEPRECATION")
                val dir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                    "Memely"
                )
                if (!dir.exists()) {
                    dir.mkdirs()
                }
                
                val file = File(dir, filename)
                FileOutputStream(file).use { fos ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 95, fos)
                    fos.flush()
                }
                
                bitmap.recycle()
                
                // Notify media scanner for legacy storage
                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(file.absolutePath),
                    arrayOf("image/jpeg"),
                    null
                )
                println("✅ MemeEditor: Saved meme to legacy storage: ${file.absolutePath}")
                onSuccess(file.absolutePath)
            }
        } catch (e: Exception) {
            println("❌ MemeEditor: Error saving meme: ${e.message}")
            e.printStackTrace()
            onError()
        }
    }
    
    /**
     * Create a bitmap with rounded corners
     */
    private fun createRoundedBitmap(bitmap: Bitmap, radius: Int): Bitmap {
        val output = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.BLACK
        }
        
        val rect = android.graphics.RectF(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat())
        canvas.drawRoundRect(rect, radius.toFloat(), radius.toFloat(), paint)
        
        paint.xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        
        return output
    }
}