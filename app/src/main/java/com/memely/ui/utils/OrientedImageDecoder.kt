package com.memely.ui.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.compose.ui.unit.IntSize

/**
 * Decodes an image in the orientation shown by Coil/Compose.
 *
 * BitmapFactory exposes JPEG pixels in their encoded orientation, whereas the
 * editor preview honours the EXIF orientation. Keeping the dimensions and
 * decoded bitmap in this one helper prevents portrait camera images from using
 * two different coordinate spaces during edit and export.
 */
object OrientedImageDecoder {
    fun bounds(context: Context, uri: Uri): IntSize? {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input, null, options)
        }
        if (options.outWidth <= 0 || options.outHeight <= 0) return null

        return if (orientationSwapsDimensions(readOrientation(context, uri))) {
            IntSize(options.outHeight, options.outWidth)
        } else {
            IntSize(options.outWidth, options.outHeight)
        }
    }

    fun decode(context: Context, uri: Uri): Bitmap? {
        val bitmap = context.contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input)
        } ?: return null

        val matrix = orientationMatrix(readOrientation(context, uri)) ?: return bitmap
        val transformed = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (transformed !== bitmap) {
            bitmap.recycle()
        }
        return transformed
    }

    private fun readOrientation(context: Context, uri: Uri): Int {
        return runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                ExifInterface(input).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            } ?: ExifInterface.ORIENTATION_NORMAL
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
    }

    private fun orientationSwapsDimensions(orientation: Int): Boolean {
        return orientation == ExifInterface.ORIENTATION_ROTATE_90 ||
            orientation == ExifInterface.ORIENTATION_ROTATE_270 ||
            orientation == ExifInterface.ORIENTATION_TRANSPOSE ||
            orientation == ExifInterface.ORIENTATION_TRANSVERSE
    }

    private fun orientationMatrix(orientation: Int): Matrix? {
        return Matrix().apply {
            when (orientation) {
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> setScale(-1f, 1f)
                ExifInterface.ORIENTATION_ROTATE_180 -> setRotate(180f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> {
                    setRotate(180f)
                    postScale(-1f, 1f)
                }
                ExifInterface.ORIENTATION_TRANSPOSE -> {
                    setRotate(90f)
                    postScale(-1f, 1f)
                }
                ExifInterface.ORIENTATION_ROTATE_90 -> setRotate(90f)
                ExifInterface.ORIENTATION_TRANSVERSE -> {
                    setRotate(-90f)
                    postScale(-1f, 1f)
                }
                ExifInterface.ORIENTATION_ROTATE_270 -> setRotate(-90f)
                else -> return null
            }
        }
    }
}
