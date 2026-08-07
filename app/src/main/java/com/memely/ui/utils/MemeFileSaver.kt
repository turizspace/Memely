package com.memely.ui.utils

import android.content.Context
import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.compose.ui.unit.dp
import android.os.Environment
import android.provider.MediaStore
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
import kotlin.math.max
import kotlin.math.roundToInt

object MemeFileSaver {
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
        return OrientedImageDecoder.bounds(context, uri)
    }

    private fun decodeBitmap(context: Context, uri: Uri): Bitmap? {
        return OrientedImageDecoder.decode(context, uri)
    }

    private fun resolveImageUri(context: Context, uri: Uri): Uri? {
        return if (uri.isRemote()) downloadImageToCache(context, uri.toString()) else uri
    }

    private fun cropToContentScaleCrop(bitmap: Bitmap, targetWidth: Int, targetHeight: Int): Bitmap {
        val sourceAspect = bitmap.width.toFloat() / bitmap.height.coerceAtLeast(1)
        val targetAspect = targetWidth.toFloat() / targetHeight.coerceAtLeast(1)
        if (kotlin.math.abs(sourceAspect - targetAspect) < 0.0001f) return bitmap

        val cropWidth: Int
        val cropHeight: Int
        if (sourceAspect > targetAspect) {
            cropHeight = bitmap.height
            cropWidth = (cropHeight * targetAspect).roundToInt().coerceIn(1, bitmap.width)
        } else {
            cropWidth = bitmap.width
            cropHeight = (cropWidth / targetAspect).roundToInt().coerceIn(1, bitmap.height)
        }
        val left = ((bitmap.width - cropWidth) / 2f).roundToInt().coerceAtLeast(0)
        val top = ((bitmap.height - cropHeight) / 2f).roundToInt().coerceAtLeast(0)
        return Bitmap.createBitmap(bitmap, left, top, cropWidth, cropHeight)
    }

    private fun Uri.isRemote(): Boolean {
        return scheme.equals("http", ignoreCase = true) || scheme.equals("https", ignoreCase = true)
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
            val resolvedUri = resolveImageUri(context, imageUri)

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

            // Map the editor's screen-space canvas to the saved bitmap. Keep this
            // transform on the canvas instead of baking it into each layer before
            // rotation: ContentScale.Fit can differ by a pixel on each axis, and
            // pre-scaling a layer makes rotated layers visibly skew or drift.
            val scaleX = baseWidth.toFloat() / baseImageSize.width.toFloat()
            val scaleY = baseHeight.toFloat() / baseImageSize.height.toFloat()

            canvas.save()
            canvas.scale(scaleX, scaleY)
            canvas.translate(-imageOffsetX, -imageOffsetY)

            // Draw overlay images in the exact coordinate system used by ImageLayerBox:
            // layout size (which already includes user scale), then a top-left
            // rotation/flip graphics layer, then the rounded screen-space offset.
            overlays.forEach { overlay ->
                val overlayUri = resolveImageUri(context, overlay.uri)
                if (overlayUri == null) {
                    SecureLog.e("MemeFileSaver: Unable to resolve overlay URI ${overlay.uri}")
                    return@forEach
                }
                decodeBitmap(context, overlayUri)?.let { overlayBmp ->
                        val density = context.resources.displayMetrics.density
                        val displayWidthPx =
                            (overlay.displayWidth.value * density * overlay.scale).roundToInt()
                                .coerceAtLeast(1)
                        val aspectRatio = overlay.originalWidth.toFloat()
                            .div(overlay.originalHeight.coerceAtLeast(1))
                            .takeIf { it > 0f }
                            ?: overlayBmp.width.toFloat() / overlayBmp.height.coerceAtLeast(1)
                        val displayHeightPx =
                            (displayWidthPx / aspectRatio).roundToInt().coerceAtLeast(1)

                        val croppedBmp = cropToContentScaleCrop(
                            overlayBmp,
                            displayWidthPx,
                            displayHeightPx
                        )
                        var scaledBmp = Bitmap.createScaledBitmap(
                            croppedBmp,
                            displayWidthPx,
                            displayHeightPx,
                            true
                        )
                        if (croppedBmp !== overlayBmp) {
                            croppedBmp.recycle()
                        }

                        // ImageLayerBox clips the already-sized image, so its radius
                        // belongs in editor pixels and is transformed with the layer.
                        if (overlay.cornerRadius.value > 0) {
                            val radiusPx = (overlay.cornerRadius.value * density).roundToInt()
                            val roundedBitmap = createRoundedBitmap(scaledBmp, radiusPx)
                            if (roundedBitmap != scaledBmp) {
                                scaledBmp.recycle()
                            }
                            scaledBmp = roundedBitmap
                        }

                        paint.alpha = (overlay.alpha * 255).toInt()
                        canvas.save()
                        canvas.translate(
                            overlay.position.x.roundToInt().toFloat(),
                            overlay.position.y.roundToInt().toFloat()
                        )
                        if (overlay.rotation != 0f) canvas.rotate(overlay.rotation)
                        canvas.scale(
                            if (overlay.flipX) -1f else 1f,
                            if (overlay.flipY) -1f else 1f
                        )
                        // A top-left graphicsLayer with scale=-1 moves pixels into
                        // negative local coordinates; do not compensate the draw
                        // origin or the saved flip will differ from the editor.
                        canvas.drawBitmap(scaledBmp, 0f, 0f, paint)
                        canvas.restore()
                        paint.alpha = 255

                        scaledBmp.recycle()
                        overlayBmp.recycle()
                }
            }

            // Draw text in editor pixels, then let the same canvas mapping above
            // convert the complete local transform to bitmap pixels. This matches
            // TextLayerBox's graphicsLayer(transformOrigin = top-left).
            texts.forEach { text ->
                if (text.text.isBlank()) {
                    return@forEach
                }

                val density = context.resources.displayMetrics.density
                val scaledDensity = density * context.resources.configuration.fontScale

                val typefaceStyle = when {
                    text.fontWeight == androidx.compose.ui.text.font.FontWeight.Bold &&
                        text.fontStyle == androidx.compose.ui.text.font.FontStyle.Italic -> Typeface.BOLD_ITALIC
                    text.fontWeight == androidx.compose.ui.text.font.FontWeight.Bold -> Typeface.BOLD
                    text.fontStyle == androidx.compose.ui.text.font.FontStyle.Italic -> Typeface.ITALIC
                    else -> Typeface.NORMAL
                }
                val resolvedTypeface = Typeface.create(
                    FontCatalog.getFontTypeface(text.fontFamily, context),
                    typefaceStyle
                )
                val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = text.color.copy(alpha = text.alpha).toArgb()
                    textSize = text.fontSize.value * scaledDensity
                    typeface = resolvedTypeface
                    isSubpixelText = true
                    // StaticLayout computes each line's left edge from its own
                    // Layout.Alignment. Paint must remain LEFT or CENTER/RIGHT
                    // shifts the already-positioned line a second time.
                    textAlign = Paint.Align.LEFT
                    if (text.shadowEnabled) {
                        setShadowLayer(
                            text.shadowBlur.value * density,
                            text.shadowOffsetX.value * density,
                            text.shadowOffsetY.value * density,
                            text.shadowColor.copy(alpha = text.alpha).toArgb()
                        )
                    }
                }

                val paddingPx = 8f * density
                val contentWidthPx = if (text.measuredWidthPx > 0f) {
                    (text.measuredWidthPx - (paddingPx * 2f)).roundToInt().coerceAtLeast(1)
                } else {
                    (text.maxWidth.value * density).roundToInt().coerceAtLeast(1)
                }
                val layoutAlignment = when (text.textAlign) {
                    androidx.compose.ui.text.style.TextAlign.Center -> Layout.Alignment.ALIGN_CENTER
                    androidx.compose.ui.text.style.TextAlign.Right,
                    androidx.compose.ui.text.style.TextAlign.End -> Layout.Alignment.ALIGN_OPPOSITE
                    else -> Layout.Alignment.ALIGN_NORMAL
                }

                val textLayout = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    val builder = StaticLayout.Builder.obtain(text.text, 0, text.text.length, textPaint, contentWidthPx)
                        .setAlignment(layoutAlignment)
                        .setLineSpacing(0f, 1f)
                        .setIncludePad(false)

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        @Suppress("WrongConstant")
                        builder.setBreakStrategy(Layout.BREAK_STRATEGY_SIMPLE)
                            .setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE)
                    }

                    builder.build()
                } else {
                    @Suppress("DEPRECATION")
                    StaticLayout(
                        text.text,
                        textPaint,
                        contentWidthPx,
                        layoutAlignment,
                        1f,
                        0f,
                        false
                    )
                }

                // Compose permits the outline and shadow to paint outside the Box.
                // Capture that overflow while retaining the Box's (0, 0) transform
                // origin, so a rotated text layer stays attached to the same point.
                val outlineOverflow = if (text.outlineWidth > 0.dp) {
                    text.outlineWidth.value * density / 2f
                } else {
                    0f
                }
                val shadowOverflow = if (text.shadowEnabled) {
                    text.shadowBlur.value * density + max(
                        kotlin.math.abs(text.shadowOffsetX.value * density),
                        kotlin.math.abs(text.shadowOffsetY.value * density)
                    )
                } else {
                    0f
                }
                val overflow = max(outlineOverflow, shadowOverflow)
                val localLeft = minOf(0f, paddingPx - overflow)
                val localTop = minOf(0f, paddingPx - overflow)
                val localRight = textLayout.width + (paddingPx * 2f) + overflow
                val localBottom = textLayout.height + (paddingPx * 2f) + overflow
                val bitmapWidth = (localRight - localLeft).roundToInt().coerceAtLeast(1)
                val bitmapHeight = (localBottom - localTop).roundToInt().coerceAtLeast(1)

                val textBitmap = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888)
                val textCanvas = Canvas(textBitmap)
                val contentX = paddingPx - localLeft
                val contentY = paddingPx - localTop
                if (text.outlineWidth > 0.dp) {
                    val outlineOffset = outlineOverflow
                    textPaint.color = text.outlineColor.copy(alpha = text.alpha).toArgb()
                    for (offsetX in -1..1) {
                        for (offsetY in -1..1) {
                            if (offsetX != 0 || offsetY != 0) {
                                textCanvas.save()
                                textCanvas.translate(
                                    contentX + (offsetX * outlineOffset),
                                    contentY + (offsetY * outlineOffset)
                                )
                                textLayout.draw(textCanvas)
                                textCanvas.restore()
                            }
                        }
                    }
                }
                textPaint.color = text.color.copy(alpha = text.alpha).toArgb()
                textCanvas.save()
                textCanvas.translate(contentX, contentY)
                textLayout.draw(textCanvas)
                textCanvas.restore()

                canvas.save()
                canvas.translate(
                    text.position.x.roundToInt().toFloat(),
                    text.position.y.roundToInt().toFloat()
                )
                if (text.rotation != 0f) canvas.rotate(text.rotation)
                canvas.scale(text.scale, text.scale)
                canvas.drawBitmap(textBitmap, localLeft, localTop, paint)
                canvas.restore()

                textBitmap.recycle()
            }
            canvas.restore()

            // Draw small rounded watermark (app icon) at bottom-right
            try {
                val res = context.resources
                val pkg = context.packageName
                // Prefer round launcher icon if available
                var logoResId = res.getIdentifier("ic_launcher_round", "mipmap", pkg)
                if (logoResId == 0) {
                    logoResId = res.getIdentifier("ic_launcher", "mipmap", pkg)
                }
                // Try decoding resource; if that fails, attempt to get application icon drawable
                var logoBmp: Bitmap? = null
                if (logoResId != 0) {
                    logoBmp = try {
                        android.graphics.BitmapFactory.decodeResource(res, logoResId)
                    } catch (_: Exception) { null }
                }

                if (logoBmp == null) {
                    try {
                        val appIcon = context.packageManager.getApplicationIcon(pkg)
                        logoBmp = drawableToBitmap(appIcon)
                    } catch (_: Exception) {
                        logoBmp = null
                    }
                }

                if (logoBmp != null) {
                    // Watermark size ~8% of image width, margin ~3%
                    val wmSize = (baseWidth * 0.08f).toInt().coerceAtLeast(24)
                    val margin = (baseWidth * 0.03f)
                    val scaledLogo = Bitmap.createScaledBitmap(logoBmp, wmSize, wmSize, true)
                    val rounded = createRoundedBitmap(scaledLogo, (wmSize / 2))

                    // Label text to the left of the logo
                    val label = "Made with"
                    val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = android.graphics.Color.WHITE
                        alpha = (0.9f * 255).toInt()
                        textSize = wmSize * 0.33f
                        typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
                        setShadowLayer(2f, 0f, 0f, android.graphics.Color.BLACK)
                    }
                    val labelWidth = labelPaint.measureText(label)
                    val spacing = (wmSize * 0.25f)
                    val totalWidth = labelWidth + spacing + wmSize

                    // Position at bottom-right inside image bounds
                    val posX = (baseWidth - totalWidth - margin).toFloat().coerceAtLeast(0f)
                    val iconX = posX + labelWidth + spacing
                    val posY = (baseHeight - wmSize - margin).toFloat().coerceAtLeast(0f)
                    val textBaseline = posY + wmSize - ((wmSize - labelPaint.textSize) / 2f)

                    // Draw the label text and logo icon
                    canvas.drawText(label, posX, textBaseline, labelPaint)
                    val wmPaint = Paint(Paint.ANTI_ALIAS_FLAG)
                    wmPaint.alpha = (0.8f * 255).toInt()
                    canvas.drawBitmap(rounded, iconX, posY, wmPaint)

                    rounded.recycle()
                    scaledLogo.recycle()
                    logoBmp.recycle()
                }
            } catch (e: Exception) {
                SecureLog.e("MemeFileSaver: Failed to draw watermark", e)
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

    private fun drawableToBitmap(drawable: android.graphics.drawable.Drawable): Bitmap {
        if (drawable is android.graphics.drawable.BitmapDrawable) {
            drawable.bitmap?.let { return it.copy(it.config ?: Bitmap.Config.ARGB_8888, true) }
        }

        val width = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 48
        val height = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 48
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }
}
