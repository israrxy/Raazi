package com.israrxy.raazi.ui.playlist

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

/**
 * Spotify-style Hero Cover Artwork with dynamic scroll-driven fade out,
 * parallax translation, scale effect, and bottom gradient fade mask.
 */
@Composable
internal fun PlaylistHeroCover(
    coverUrl: String,
    playlistTitle: String,
    dominantColor: Color,
    scrollOffset: Float,
    fadeThresholdPx: Float = 550f,
    modifier: Modifier = Modifier
) {
    // Calculate scroll progress (0.0 at top, 1.0 when fully scrolled past threshold)
    val scrollProgress = (scrollOffset / fadeThresholdPx).coerceIn(0f, 1f)

    // Spotify-like fade out: starts fading out immediately and reaches 0 opacity
    val targetAlpha = (1f - (scrollProgress * 1.35f)).coerceIn(0f, 1f)
    val animatedAlpha by animateFloatAsState(
        targetValue = targetAlpha,
        animationSpec = tween(durationMillis = 150),
        label = "cover_fade_alpha"
    )

    // Spotify-like subtle scale-down as you scroll (from 1.0 down to 0.75)
    val scale = 1f - (scrollProgress * 0.25f)

    // Parallax vertical movement (drifts upwards at half scroll speed)
    val parallaxY = -(scrollOffset * 0.40f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(260.dp)
            .graphicsLayer {
                alpha = animatedAlpha
                scaleX = scale
                scaleY = scale
                translationY = parallaxY
            },
        contentAlignment = Alignment.Center
    ) {
        // Ambient soft atmospheric glow behind artwork
        Box(
            modifier = Modifier
                .size(240.dp)
                .clip(RoundedCornerShape(32.dp))
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            dominantColor.copy(alpha = 0.55f),
                            dominantColor.copy(alpha = 0.20f),
                            Color.Transparent
                        )
                    )
                )
        )

        // Main Spotify Album Cover Container
        Box(
            modifier = Modifier
                .size(210.dp)
                .shadow(
                    elevation = 28.dp,
                    shape = RoundedCornerShape(20.dp),
                    spotColor = dominantColor.copy(alpha = 0.6f),
                    ambientColor = Color.Black.copy(alpha = 0.5f)
                )
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center
        ) {
            if (coverUrl.isNotBlank()) {
                AsyncImage(
                    model = coverUrl,
                    contentDescription = playlistTitle,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                // Spotify signature bottom subtle fade-out scrim on the artwork itself
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.28f),
                                    Color.Black.copy(alpha = 0.60f)
                                )
                            )
                        )
                )
            } else {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(76.dp)
                )
            }
        }
    }
}
