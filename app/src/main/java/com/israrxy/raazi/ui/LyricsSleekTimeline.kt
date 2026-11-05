package com.israrxy.raazi.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Compact, rock-solid lyrics timeline in a single unified row:
 * [ 0:42 ] ────●──────────────────────── [ 3:15 ]
 * Uses Material3 Slider for crash-proof, native dragging and tap-to-seek.
 * Updates smoothly as playback progresses, without awkward layout stretching.
 */
@Composable
internal fun LyricsSleekTimeline(
    currentPosition: Long,
    duration: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var isDraggingSlider by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }

    val sliderFraction = when {
        isDraggingSlider -> dragFraction
        duration > 0L -> (currentPosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
        else -> 0f
    }

    val displayedPosition = when {
        isDraggingSlider && duration > 0L -> (dragFraction * duration).toLong().coerceIn(0L, duration)
        else -> currentPosition
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(26.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = formatLyricsTimestamp(displayedPosition),
            style = MaterialTheme.typography.labelSmall.copy(
                fontFeatureSettings = "tnum",
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            ),
            color = Color.White.copy(alpha = 0.60f)
        )

        Slider(
            value = sliderFraction,
            onValueChange = { fraction ->
                isDraggingSlider = true
                dragFraction = fraction
            },
            onValueChangeFinished = {
                if (duration > 0L) {
                    onSeek((dragFraction * duration).toLong().coerceIn(0L, duration))
                }
                isDraggingSlider = false
            },
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Color.White,
                inactiveTrackColor = Color.White.copy(alpha = 0.20f)
            ),
            modifier = Modifier
                .weight(1f)
                .height(22.dp)
        )

        Text(
            text = formatLyricsTimestamp(duration),
            style = MaterialTheme.typography.labelSmall.copy(
                fontFeatureSettings = "tnum",
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            ),
            color = Color.White.copy(alpha = 0.60f)
        )
    }
}
