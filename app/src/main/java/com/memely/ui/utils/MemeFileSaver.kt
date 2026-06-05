package com.memely.ui.utils

import android.content.Context
import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import androidx.compose.ui.unit.dp
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.IntSize
import com.memely.network.SecureHttpClient
import com.memely.ui.viewmodels.MemeOverlayImage
import com.memely.ui.viewmodels.MemeText
import com.memely.ui.fonts.FontCatalog
import com.memely.util.SecureLog
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
    
    private fun downloadImageToCache(context: Context, url: String): Uri? {
        return try {
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
        } catch (e: Exception) {
            SecureLog.e("MemeFileSaver: Failed to download remote image", e)
            null
        }
    }

    private fun decodeImageBounds(context: Context, uri: Uri): IntSize? {
        val options = android.graphics.BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }

        context.contentResolver.openInputStream(uri)?.use { input ->
            android.graphics.BitmapFactory.decodeStream(input, null, options)
        }

        return if (options.outWidth > 0 && options.outHeight > 0) {
            IntSize(options.outWidth, options.outHeight)
        } else {
            null
        }
    }

    private fun decodeBitmap(context: Context, uri: Uri): Bitmap? {
        return context.contentResolver.openInputStream(uri)?.use { input ->
            android.graphics.BitmapFactory.decodeStream(input)
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
            val resolvedUri = if (imageUri.toString().startsWith("http")) {
                downloadImageToCache(context, imageUri.toString())
            } else {
                imageUri
            }

            if (resolvedUri == null) {
                SecureLog.e("MemeFileSaver: Unable to resolve source image URI for saving")
                onError()
                return
            }

            if (baseImageSize.width <= 0 || baseImageSize.height <= 0) {
                SecureLog.e("MemeFileSaver: Invalid displayed base image size ${baseImageSize.width}x${baseImageSize.height}")
                onError()
                return
            }
            
            // Get base image dimensions (use provided or decode)
            var baseWidth = originalImageWidth
            var baseHeight = originalImageHeight
            
            if (baseWidth == 0 || baseHeight == 0) {
                val bounds = decodeImageBounds(context, resolvedUri)
                if (bounds != null) {
                    baseWidth = bounds.width
                    baseHeight = bounds.height
                }
            }

            if (baseWidth <= 0 || baseHeight <= 0) {
                SecureLog.e("MemeFileSaver: Invalid source image dimensions ${baseWidth}x${baseHeight}")
                onError()
                return
            }

            // Create bitmap with original image dimensions for high quality
            val bitmap = Bitmap.createBitmap(baseWidth, baseHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)

            // Draw base image
            val baseBmp = decodeBitmap(context, resolvedUri)
            if (baseBmp == null) {
                SecureLog.e("MemeFileSaver: Failed to decode source image for saving")
                bitmap.recycle()
                onError()
                return
            }
            canvas.drawBitmap(baseBmp, 0f, 0f, paint)
            baseBmp.recycle()

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
                            val radiusPx = (overlay.cornerRadius.value * density * scaleX).toInt()
                            val roundedBitmap = createRoundedBitmap(scaledBmp, radiusPx)
                            if (roundedBitmap != scaledBmp) {
                                scaledBmp.recycle()
                            }
                            scaledBmp = roundedBitmap
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
                        canvas.scale(
                            if (overlay.flipX) -1f else 1f,
                            if (overlay.flipY) -1f else 1f,
                            finalWidth / 2f,
                            finalHeight / 2f
                        )
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

                // Get the typeface for the selected font
                val selectedTypeface = FontCatalog.getFontTypeface(text.fontFamily, context)
                
                // First paint at display scale to measure text dimensions in screen space
                val textPaintForMeasure = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = text.color.toArgb()
                    textSize = text.fontSize.value * density  // Base fontSize only (scale handled separately)
                    isFakeBoldText = text.fontWeight == androidx.compose.ui.text.font.FontWeight.Bold
                    style = Paint.Style.FILL
                    typeface = selectedTypeface  // Apply selected font
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
                // text.position.x/y = screen/canvas coordinates (where user placed the OUTER Box)
                // The TextLayerBox has padding(8.dp) INSIDE the graphicsLayer transforms
                // This means the actual text content starts at position + 8.dp (in display space)
                // But padding is applied BEFORE scale, so we need to add the padding amount
                
                val textPaddingDisplayPx = 8f * density  // Match the 8.dp from TextLayerBox
                
                val posRelativeToImage = Offset(
                    text.position.x - imageOffsetX + textPaddingDisplayPx,
                    text.position.y - imageOffsetY + textPaddingDisplayPx
                )
                
                // Now scale to original image coordinates using separate X/Y scales
                // Also apply user's scale transform to match graphicsLayer scaling
                val scaledX = posRelativeToImage.x * scaleX
                val scaledY = posRelativeToImage.y * scaleY

                // Create final paint - USE ONLY BASE SIZE, NOT SCALED
                // The canvas.scale() will handle the scaling transformation
                val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = text.color.copy(alpha = text.alpha).toArgb()
                    textSize = textPaintForMeasure.textSize * scaleX  // Only image scale, NOT user scale (canvas.scale will do that)
                    isFakeBoldText = text.fontWeight == androidx.compose.ui.text.font.FontWeight.Bold
                    style = Paint.Style.FILL
                    typeface = selectedTypeface  // Apply selected font to final paint
                    // Match the alignment from measure paint
                    textAlign = textPaintForMeasure.textAlign
                    if (text.shadowEnabled) {
                        setShadowLayer(
                            text.shadowBlur.value * density * scaleX,
                            text.shadowOffsetX.value * density * scaleX,
                            text.shadowOffsetY.value * density * scaleY,
                            text.shadowColor.copy(alpha = text.alpha).toArgb()
                        )
                    }
                }

                // Dimensions in display/screen space (before any saves to disk scaling)
                val unscaledTextWidth = textWidth  // Text width at base fontSize
                val unscaledTotalTextHeight = totalTextHeight  // Total height of all lines
                val unscaledLineHeight = lineHeight  // Height of one line
                val unscaledBaselineOffset = baselineOffset  // Baseline offset
                
                // Calculate the center point of the text BEFORE scaling (for rotation center)
                val unscaledCenterX = unscaledTextWidth / 2f
                val unscaledCenterY = unscaledTotalTextHeight / 2f
                
                // Scale these to image coordinates for the rotation center
                val scaledUnscaledCenterX = unscaledCenterX * scaleX
                val scaledUnscaledCenterY = unscaledCenterY * scaleY
                
                // Calculate scaled dimensions for rotation center
                // Apply only image scale (NOT user scale, that's done by canvas.scale())
                val scaledTextWidth = unscaledTextWidth * scaleX
                val scaledTotalTextHeight = unscaledTotalTextHeight * scaleY

                // Calculate the line height in the final scaled space
                val scaledLineHeight = unscaledLineHeight * scaleY
                val scaledBaselineOffset = unscaledBaselineOffset * scaleY

                // Log computed values for debugging alignment
                println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                println("🔎 MemeFileSaver TEXT DEBUG:")
                println("  Text: '${text.text.take(30)}...'")
                println("  Screen Position: (${text.position.x}, ${text.position.y})")
                println("  Image Offset: (${imageOffsetX}, ${imageOffsetY})")
                println("  Relative to Image: (${posRelativeToImage.x}, ${posRelativeToImage.y})")
                println("  Scale Factors: scaleX=${scaleX}, scaleY=${scaleY}, userScale=${text.scale}")
                println("  Scaled to Image: (${scaledX}, ${scaledY})")
                println("  ⚠️ EXPECTED in editor: text box outer edge at (${text.position.x}, ${text.position.y})")
                println("  ⚠️ ACTUAL in saved image: text content starts at (${scaledX}, ${scaledY})")
                println("  User Scale: ${text.scale}x, Rotation: ${text.rotation}°")
                println("  Display Font Size (base): ${textPaintForMeasure.textSize}px")
                println("  Final Font Size (image scaled): ${textPaint.textSize}px")
                println("  Text Dimensions (unscaled): ${unscaledTextWidth}x${unscaledTotalTextHeight}px")
                println("  Text Dimensions (image scaled): ${scaledTextWidth}x${scaledTotalTextHeight}px")
                println("  Unscaled Center: (${unscaledCenterX}, ${unscaledCenterY})")
                println("  Image-Scaled Center for rotation: (${scaledUnscaledCenterX}, ${scaledUnscaledCenterY})")
                println("  Lines: ${lines.size}, Max Width: ${maxTextWidthPx}px")
                println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")

                // Draw text: translate -> rotate around center -> apply user scale
                // This matches Compose's graphicsLayer behavior: translate, rotate around center, then scale around center
                canvas.save()
                // Translate to the text's position on the final image
                canvas.translate(scaledX, scaledY)
                // Rotate around the text's center point
                canvas.rotate(text.rotation, scaledUnscaledCenterX, scaledUnscaledCenterY)
                // Apply user's scale transformation around the center
                canvas.scale(text.scale, text.scale, scaledUnscaledCenterX, scaledUnscaledCenterY)
                
                // Calculate X offset based on text alignment
                // Paint.Align handles the positioning: LEFT draws from x, CENTER from x-width/2, RIGHT from x-width
                // Use unscaled maxWidth since canvas.scale() will handle the actual scaling
                val alignmentX = when (textPaint.textAlign) {
                    Paint.Align.LEFT -> 0f
                    Paint.Align.CENTER -> maxTextWidthPx * scaleX / 2f  // Center within max width (image scaled only)
                    Paint.Align.RIGHT -> maxTextWidthPx * scaleX  // Right-align to max width (image scaled only)
                    else -> 0f
                }
                
                // Draw outline if configured
                if (text.outlineWidth > 0.dp) {
                    // Outline width is scaled by image scale (user scale will be applied by canvas.scale())
                    val outlineWidthPx = text.outlineWidth.value * density * scaleX
                    val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = text.outlineColor.copy(alpha = text.alpha).toArgb()
                        textSize = textPaint.textSize
                        textAlign = textPaint.textAlign
                        style = Paint.Style.FILL
                        typeface = selectedTypeface  // CRITICAL: Apply the SAME font as main text
                        isFakeBoldText = text.fontWeight == androidx.compose.ui.text.font.FontWeight.Bold
                        if (text.shadowEnabled) {
                            setShadowLayer(
                                text.shadowBlur.value * density * scaleX,
                                text.shadowOffsetX.value * density * scaleX,
                                text.shadowOffsetY.value * density * scaleY,
                                text.shadowColor.copy(alpha = text.alpha).toArgb()
                            )
                        }
                    }
                    
                    // Draw outline text multiple times with offset
                    for (offsetX in -1..1) {
                        for (offsetY in -1..1) {
                            if (offsetX != 0 || offsetY != 0) {
                                val outlineOffsetX = offsetX * (outlineWidthPx / 2f)
                                val outlineOffsetY = offsetY * (outlineWidthPx / 2f)
                                lines.forEachIndexed { lineIndex, line ->
                                    val lineY = lineIndex * scaledLineHeight + scaledBaselineOffset
                                    canvas.drawText(line, alignmentX + outlineOffsetX, lineY + outlineOffsetY, outlinePaint)
                                }
                            }
                        }
                    }
                }
                
                // Draw each line using the final paint (with alignment)
                lines.forEachIndexed { lineIndex, line ->
                    val lineY = lineIndex * scaledLineHeight + scaledBaselineOffset
                    canvas.drawText(line, alignmentX, lineY, textPaint)
                }
                
                canvas.restore()  // Restore canvas state (removes translate + rotate + scale)
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
            SecureLog.e("MemeFileSaver: Error saving meme", e)
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
