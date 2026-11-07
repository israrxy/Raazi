package com.israrxy.raazi.data.ai

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.graphics.drawable.toBitmap
import coil.imageLoader
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/**
 * Decodes a Photo Picker URI with Coil (which honors EXIF rotation), scales it
 * down, and re-encodes it as a compact JPEG for Gemini's inline image payload.
 */
object ImagePayload {

    private const val MAX_DIMENSION = 1280
    private const val JPEG_QUALITY = 85
    private const val MAX_BYTES = 8 * 1024 * 1024

    suspend fun prepare(context: Context, uri: Uri): ByteArray = withContext(Dispatchers.IO) {
        val request = ImageRequest.Builder(context)
            .data(uri)
            .size(MAX_DIMENSION, MAX_DIMENSION)
            .allowHardware(false)
            .build()

        val drawable = context.imageLoader.execute(request).drawable
            ?: throw IllegalArgumentException("That image could not be opened. Try another photo.")

        val bitmap = drawable.toBitmap()
        val output = ByteArrayOutputStream()
        val compressed = bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)
        if (!compressed) {
            throw IllegalArgumentException("That image format is not supported. Try another photo.")
        }
        val bytes = output.toByteArray()
        if (bytes.isEmpty()) {
            throw IllegalArgumentException("That image could not be read. Try another photo.")
        }
        if (bytes.size > MAX_BYTES) {
            throw IllegalArgumentException("That photo is too large. Try a different one.")
        }
        bytes
    }
}
