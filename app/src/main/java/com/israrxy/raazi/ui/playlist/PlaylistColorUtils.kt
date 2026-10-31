package com.israrxy.raazi.ui.playlist

import android.content.Context
import android.graphics.drawable.BitmapDrawable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.palette.graphics.Palette
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.israrxy.raazi.ui.theme.Emerald500
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Extracts a dominant, vibrant ambient color from a playlist cover URL using Android Palette.
 * Falls back gracefully to Emerald500 / Theme primary if the image fails or has no swatches.
 */
@Composable
internal fun rememberPlaylistDominantColor(
    coverUrl: String,
    fallbackColor: Color = Emerald500
): Color {
    val context = LocalContext.current
    var dominantColor by remember(coverUrl) { mutableStateOf(fallbackColor) }

    LaunchedEffect(coverUrl) {
        if (coverUrl.isBlank()) {
            dominantColor = fallbackColor
            return@LaunchedEffect
        }

        withContext(Dispatchers.IO) {
            try {
                val imageLoader = ImageLoader(context)
                val request = ImageRequest.Builder(context)
                    .data(coverUrl)
                    .allowHardware(false)
                    .build()

                val result = (imageLoader.execute(request) as? SuccessResult)?.drawable
                val bitmap = (result as? BitmapDrawable)?.bitmap
                if (bitmap != null && !bitmap.isRecycled) {
                    val palette = Palette.from(bitmap)
                        .maximumColorCount(16)
                        .generate()

                    val targetRgb = palette.vibrantSwatch?.rgb
                        ?: palette.darkVibrantSwatch?.rgb
                        ?: palette.dominantSwatch?.rgb
                        ?: palette.mutedSwatch?.rgb
                        ?: palette.darkMutedSwatch?.rgb

                    if (targetRgb != null) {
                        withContext(Dispatchers.Main) {
                            dominantColor = Color(targetRgb)
                        }
                    }
                }
            } catch (_: Throwable) {
                // Keep fallback on error
            }
        }
    }

    return dominantColor
}
