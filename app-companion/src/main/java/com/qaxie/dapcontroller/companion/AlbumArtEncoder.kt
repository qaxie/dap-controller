package com.qaxie.dapcontroller.companion

import android.graphics.Bitmap
import android.util.Base64
import java.io.ByteArrayOutputStream

object AlbumArtEncoder {

    private const val MAX_SIZE = 256
    private const val JPEG_QUALITY = 80

    fun encode(bitmap: Bitmap): String {
        val scaled = scale(bitmap)
        val stream = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, stream)
        return Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
    }

    fun encodeOrNull(bitmap: Bitmap?): String? {
        bitmap ?: return null
        return try { encode(bitmap) } catch (e: Exception) { null }
    }

    private fun scale(bitmap: Bitmap): Bitmap {
        val maxDim = maxOf(bitmap.width, bitmap.height)
        if (maxDim <= MAX_SIZE) return bitmap
        val scale = MAX_SIZE.toFloat() / maxDim
        val w = (bitmap.width * scale).toInt().coerceAtLeast(1)
        val h = (bitmap.height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, w, h, true)
    }
}
