package com.memely.ui.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.IntSize
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
     */
    private fun wrapText(text: String, paint: Paint, maxWidth: Float): List<String> {
        if (maxWidth <= 0) return listOf(text)
        
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
                val httpClient = OkHttpClient()
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

            // Calculate scale factor between displayed size and original size
            // Use uniform scale (both X and Y should be same for ContentScale.Fit)
            val scale = baseWidth.toFloat() / baseImageSize.width.toFloat()

            println("🔍 MemeFileSaver: Original=${baseWidth}x${baseHeight}, Displayed=${baseImageSize.width}x${baseImageSize.height}, Offset=(${imageOffsetX},${imageOffsetY}), Scale=${scale}")

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
                        val adjustedX = overlay.position.x - imageOffsetX
                        val adjustedY = overlay.position.y - imageOffsetY
                        val scaledX = adjustedX * scale
                        val scaledY = adjustedY * scale

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

                // First paint at 1x scale to measure text dimensions
                val textPaintForMeasure = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = text.color.toArgb()
                    textSize = text.fontSize.value * density  // 1x scale for measurement
                    isFakeBoldText = true
                    style = Paint.Style.FILL
                    textAlign = Paint.Align.LEFT
                }

                // Account for the 8.dp padding used in TextLayerBox which shifts drawn text inside the box.
                val textPaddingPx = 8f * density

                // Calculate maximum width for text wrapping 
                // Use a more conservative approach - don't wrap too aggressively
                // The editor probably allows text to flow more naturally
                val minLineWidth = textPaintForMeasure.textSize * 10f
                val maxTextWidth = (baseImageSize.width.toFloat() * 0.8f).coerceAtLeast(minLineWidth)
                
                // Wrap text into lines based on width
                val lines = wrapText(text.text, textPaintForMeasure, maxTextWidth)
                
                // Debug: Log wrapping info
                println("🔤 Text wrapping: maxWidth=${maxTextWidth}, lines=${lines.size}, text='${text.text.take(50)}...'")
                lines.forEachIndexed { i, line -> 
                    val lineWidth = textPaintForMeasure.measureText(line)
                    println("   Line $i: width=${lineWidth}, text='$line'")
                }
                
                // Measure text dimensions in display space (before scaling to original image)
                val fm = textPaintForMeasure.fontMetrics
                val lineHeight = fm.descent - fm.ascent
                val baselineOffset = -fm.ascent
                
                // Calculate total text height in display space
                val totalTextHeight = lineHeight * lines.size
                
                // Find the widest line for rotation center calculation
                val textWidth = lines.maxOfOrNull { line -> textPaintForMeasure.measureText(line) } ?: 0f
                
                // **COMPLEX TRANSFORM FIX: Account for text.scale AND text.rotation interaction**
                // 
                // In TextLayerBox, transforms are applied in this order:
                // 1. .offset(position) - moves box to position
                // 2. .graphicsLayer(scale, rotation) - scales and rotates around CENTER of box
                //
                // For the final rendered position, we need to calculate:
                // 1. Where the top-left corner ends up after scaling around center
                // 2. Where that scaled top-left ends up after rotation around center
                
                // Step 1: Calculate position after scaling (before rotation)
                // When scaled around center, top-left shifts by (1-scale) * size / 2
                val scaleOffsetX = (1f - text.scale) * textWidth / 2f
                val scaleOffsetY = (1f - text.scale) * totalTextHeight / 2f
                
                // Step 2: Account for rotation of the scaled offset
                // When the box is rotated, the scale offset vector also rotates
                val rotationRad = Math.toRadians(text.rotation.toDouble()).toFloat()
                val cosRot = kotlin.math.cos(rotationRad)
                val sinRot = kotlin.math.sin(rotationRad)
                
                // Rotate the scale offset vector
                val rotatedScaleOffsetX = scaleOffsetX * cosRot - scaleOffsetY * sinRot
                val rotatedScaleOffsetY = scaleOffsetX * sinRot + scaleOffsetY * cosRot
                
                // Apply both offsets and padding
                val adjustedX = text.position.x + rotatedScaleOffsetX + textPaddingPx
                val adjustedY = text.position.y + rotatedScaleOffsetY + textPaddingPx
                
                // Now scale to original image coordinates
                val scaledX = adjustedX * scale
                val scaledY = adjustedY * scale

                // Create final paint with full scale applied
                val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = text.color.toArgb()
                    textSize = textPaintForMeasure.textSize * text.scale * scale  // Apply both scales
                    isFakeBoldText = true
                    style = Paint.Style.FILL
                    textAlign = Paint.Align.LEFT
                }

                // Log computed values for debugging alignment
                println("🔎 MemeFileSaver text: text='${text.text}', pos=(${text.position.x},${text.position.y}), textDim=(${textWidth}x${totalTextHeight}), scaleOffset=(${scaleOffsetX},${scaleOffsetY}), rotatedOffset=(${rotatedScaleOffsetX},${rotatedScaleOffsetY}), adjusted=(${adjustedX},${adjustedY}), scaled=(${scaledX},${scaledY}), textScale=${text.scale}, rotation=${text.rotation}°, lines=${lines.size}, textSize=${textPaint.textSize}")

                // Calculate scaled dimensions for rotation center
                val scaledTextWidth = textWidth * text.scale * scale
                val scaledTotalTextHeight = totalTextHeight * text.scale * scale

                // Calculate the line height in the final scaled space
                // Since we measure at 1x scale but draw at textScale * imageScale,
                // we need to scale the line height accordingly
                val scaledLineHeight = lineHeight * text.scale * scale
                val scaledBaselineOffset = baselineOffset * text.scale * scale

                // Draw text rotated around its center to match Compose's graphicsLayer default transform origin
                canvas.save()
                canvas.translate(scaledX, scaledY)
                canvas.rotate(text.rotation, scaledTextWidth / 2f, scaledTotalTextHeight / 2f)
                
                // Draw each line using the final paint (with full scale)
                lines.forEachIndexed { lineIndex, line ->
                    val lineY = lineIndex * scaledLineHeight + scaledBaselineOffset
                    canvas.drawText(line, 0f, lineY, textPaint)
                }
                
                canvas.restore()
            }

            // Save to device
            val dir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                "Memely"
            )
            if (!dir.exists()) {
                dir.mkdirs()
            }

            val filename = "meme_${System.currentTimeMillis()}.jpg"
            val file = File(dir, filename)

            FileOutputStream(file).use { fos ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, fos)
                fos.flush()
            }

            bitmap.recycle()

            // Notify media scanner
            MediaScannerConnection.scanFile(
                context,
                arrayOf(file.absolutePath),
                arrayOf("image/jpeg"),
                null
            )

            println("✅ MemeEditor: Saved meme to ${file.absolutePath}")
            onSuccess(file.absolutePath)
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