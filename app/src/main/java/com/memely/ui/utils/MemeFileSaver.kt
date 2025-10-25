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
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

object MemeFileSaver {
    
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
        // Convert HTTPS URLs to local files first
        val resolvedUri = runBlocking {
            val imageUriString = imageUri.toString()
            if (imageUriString.startsWith("http")) {
                downloadImageToCache(context, imageUriString) ?: imageUri
            } else {
                imageUri
            }
        }
        
        try {
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

            // Draw text layers - FIXED POSITIONING
            texts.forEach { text ->
                val density = context.resources.displayMetrics.density

                val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = text.color.toArgb()
                    // Convert sp to px, apply user scale, then scale to original image
                    textSize = text.fontSize.value * density * text.scale * scale
                    isFakeBoldText = true
                    style = Paint.Style.FILL
                    textAlign = Paint.Align.LEFT // Keep LEFT alignment for precise positioning
                }

                // Account for the 8.dp padding used in TextLayerBox which shifts drawn text inside the box.
                // Convert padding dp -> px (8.dp) into pixels using density
                val textPaddingPx = 8f * density

                // Apply position scaling, accounting for image offset and internal padding
                val adjustedX = (text.position.x - imageOffsetX) + textPaddingPx
                val adjustedY = (text.position.y - imageOffsetY) + textPaddingPx
                val scaledX = adjustedX * scale
                val scaledY = adjustedY * scale

                // Log computed values for debugging alignment
                println("🔎 MemeFileSaver text: text='${text.text}', pos=(${text.position.x},${text.position.y}), adjusted=(${adjustedX},${adjustedY}), scaled=(${scaledX},${scaledY}), textSizePx=${textPaint.textSize}, rotation=${text.rotation}")

                // Split text into lines for multi-line support
                val lines = text.text.split("\n")
                
                // Measure text dimensions
                val fm = textPaint.fontMetrics
                val lineHeight = fm.descent - fm.ascent
                val baselineOffset = -fm.ascent // drawText y-position to make top = 0
                
                // Calculate total text height
                val totalTextHeight = lineHeight * lines.size
                
                // Find the widest line for rotation center calculation
                val textWidth = lines.maxOfOrNull { line -> textPaint.measureText(line) } ?: 0f

                // Draw text rotated around its center to match Compose's graphicsLayer default transform origin
                canvas.save()
                canvas.translate(scaledX, scaledY)
                canvas.rotate(text.rotation, textWidth / 2f, totalTextHeight / 2f)
                
                // Draw each line
                lines.forEachIndexed { lineIndex, line ->
                    val lineY = lineIndex * lineHeight + baselineOffset
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