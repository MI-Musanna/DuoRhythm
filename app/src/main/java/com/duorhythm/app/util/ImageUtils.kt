package com.duorhythm.app.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import android.media.ExifInterface
import android.net.Uri
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import kotlin.math.max

object ImageUtils {

    /**
     * Decodes a downsampled Bitmap from a Content URI to prevent OutOfMemoryError on 4K/high-res photos.
     */
    fun decodeSampledBitmapFromUri(
        context: Context,
        uri: Uri,
        reqWidth: Int = 1080,
        reqHeight: Int = 1080
    ): Bitmap? {
        return try {
            // 1. Decode bounds only
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            } ?: return null

            if (options.outWidth <= 0 || options.outHeight <= 0) return null

            // 2. Calculate inSampleSize (power of 2)
            var inSampleSize = 1
            val halfHeight = options.outHeight / 2
            val halfWidth = options.outWidth / 2
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }

            // 3. Decode sampled bitmap with calculated inSampleSize
            val decodeOptions = BitmapFactory.Options().apply {
                this.inSampleSize = inSampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val sampledBitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOptions)
            } ?: return null

            // 4. Handle EXIF orientation
            fixOrientationFromUri(context, uri, sampledBitmap)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun fixOrientationFromUri(context: Context, uri: Uri, bitmap: Bitmap): Bitmap {
        return try {
            var orientation = ExifInterface.ORIENTATION_NORMAL
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val exif = ExifInterface(stream)
                orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            }
            val rotationAngle = when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
            if (rotationAngle != 0f) {
                val matrix = Matrix().apply { postRotate(rotationAngle) }
                val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                if (rotated != bitmap) {
                    bitmap.recycle()
                }
                rotated
            } else {
                bitmap
            }
        } catch (_: Exception) {
            bitmap
        }
    }

    /**
     * Masks any bitmap into an anti-aliased circular bitmap of targetSize x targetSize (default 512x512).
     */
    fun cropToCircle(source: Bitmap, targetSize: Int = 512): Bitmap {
        val output = Bitmap.createBitmap(targetSize, targetSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        val minDim = minOf(source.width, source.height)
        val x = (source.width - minDim) / 2
        val y = (source.height - minDim) / 2
        val squared = if (source.width == source.height) {
            source
        } else {
            Bitmap.createBitmap(source, x, y, minDim, minDim)
        }

        val shader = BitmapShader(squared, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        if (squared.width != targetSize || squared.height != targetSize) {
            val matrix = Matrix()
            val scale = targetSize.toFloat() / squared.width.toFloat()
            matrix.setScale(scale, scale)
            shader.setLocalMatrix(matrix)
        }
        paint.shader = shader
        val radius = targetSize / 2f
        canvas.drawCircle(radius, radius, radius, paint)

        if (squared != source && !squared.isRecycled) {
            squared.recycle()
        }
        return output
    }

    /**
     * Saves bitmap as JPEG at the specified quality (default 85%).
     */
    fun saveBitmapToFile(bitmap: Bitmap, destFile: File, quality: Int = 85): Boolean {
        return try {
            destFile.parentFile?.mkdirs()
            FileOutputStream(destFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
                out.flush()
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Encodes a bitmap to a Base64 string.
     */
    fun encodeBitmapToBase64(bitmap: Bitmap, quality: Int = 85): String {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
        return Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
    }

    /**
     * Encodes a file to a Base64 string.
     */
    fun encodeFileToBase64(file: File): String? {
        if (!file.exists() || !file.canRead()) return null
        return try {
            val bytes = file.readBytes()
            Base64.encodeToString(bytes, Base64.NO_WRAP)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Decodes a Base64 string directly to an image file.
     */
    fun decodeBase64ToFile(base64Str: String, destFile: File): Boolean {
        if (base64Str.isBlank()) return false
        return try {
            val bytes = Base64.decode(base64Str, Base64.DEFAULT)
            destFile.parentFile?.mkdirs()
            destFile.writeBytes(bytes)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
