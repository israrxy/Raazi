package com.israrxy.raazi.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp

/**
 * TeleMusic & Apple Music style flowing liquid word-by-word synced lyrics renderer.
 * Operates purely in the draw & graphicsLayer phase:
 * Words are laid out once. A dimmed base Text (white 35%) is stacked beneath an active
 * white Text clipped via clipRect to the word's progress from the positionProvider.
 * Zero composition-time time reads, zero brush/TextStyle allocations per frame.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FlowingLyricLine(
    words: List<LyricWordSegment>,
    positionProvider: () -> Long,
    textStyle: TextStyle,
    modifier: Modifier = Modifier
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        words.forEach { word ->
            FlowingLyricWord(
                word = word,
                positionProvider = positionProvider,
                textStyle = textStyle
            )
        }
    }
}

@Composable
internal fun FlowingLyricWord(
    word: LyricWordSegment,
    positionProvider: () -> Long,
    textStyle: TextStyle
) {
    val duration = (word.endTimeMs - word.startTimeMs).coerceAtLeast(1L)

    Box(
        modifier = Modifier.graphicsLayer {
            val currentPos = positionProvider()
            val p = ((currentPos - word.startTimeMs).toFloat() / duration.toFloat()).coerceIn(0f, 1f)
            val scale = if (p > 0f && p < 1f) {
                1f + 0.035f * (1f - kotlin.math.abs(p - 0.5f) * 2f)
            } else {
                1f
            }
            scaleX = scale
            scaleY = scale
        }
    ) {
        // Base dimmed text (white 35%)
        Text(
            text = word.displayText,
            style = textStyle,
            color = Color.White.copy(alpha = 0.35f)
        )

        // Active white text clipped in draw phase to word progress
        Text(
            text = word.displayText,
            style = textStyle,
            color = Color.White,
            modifier = Modifier.drawWithContent {
                val currentPos = positionProvider()
                val p = ((currentPos - word.startTimeMs).toFloat() / duration.toFloat()).coerceIn(0f, 1f)
                if (p > 0f) {
                    clipRect(right = size.width * p) {
                        this@drawWithContent.drawContent()
                    }
                }
            }
        )
    }
}
