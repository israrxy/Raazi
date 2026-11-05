package com.israrxy.raazi.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.israrxy.raazi.data.lyrics.LyricsTextVariants
import com.israrxy.raazi.data.lyrics.LyricsViewVariant

/**
 * TeleMusic & Apple Music style flowing liquid word-by-word synced lyrics renderer.
 * As vocals progress across syllables, words sweep white light across character glyphs
 * using a hardware-accelerated horizontal gradient shader brush.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FlowingLyricLine(
    words: List<LyricWordSegment>,
    currentPositionMs: Long,
    selectedVariant: LyricsViewVariant,
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
                currentPositionMs = currentPositionMs,
                selectedVariant = selectedVariant,
                textStyle = textStyle
            )
        }
    }
}

@Composable
internal fun FlowingLyricWord(
    word: LyricWordSegment,
    currentPositionMs: Long,
    selectedVariant: LyricsViewVariant,
    textStyle: TextStyle
) {
    val transformed = remember(word.text, selectedVariant) {
        LyricsTextVariants.transform(word.text, selectedVariant)
    }

    when {
        currentPositionMs >= word.endTimeMs -> {
            // Already sung -> solid active white
            Text(
                text = transformed,
                style = textStyle,
                color = Color.White
            )
        }
        currentPositionMs < word.startTimeMs -> {
            // Future word -> dimmed 35% white
            Text(
                text = transformed,
                style = textStyle,
                color = Color.White.copy(alpha = 0.35f)
            )
        }
        else -> {
            // Actively being sung -> smooth progressive gradient wipe across the letters
            val duration = (word.endTimeMs - word.startTimeMs).coerceAtLeast(1L)
            val rawProgress = (currentPositionMs - word.startTimeMs).toFloat() / duration.toFloat()
            val progress = rawProgress.coerceIn(0.001f, 0.999f)

            // Strictly monotonically increasing color stops (0.0f < p1 < p2 < 1.0f)
            val p1 = progress.coerceIn(0.01f, 0.94f)
            val p2 = (p1 + 0.05f).coerceIn(p1 + 0.01f, 0.99f)

            val flowingBrush = remember(p1, p2) {
                Brush.horizontalGradient(
                    0.0f to Color.White,
                    p1 to Color.White,
                    p2 to Color.White.copy(alpha = 0.35f),
                    1.0f to Color.White.copy(alpha = 0.35f)
                )
            }

            Text(
                text = transformed,
                style = textStyle.copy(brush = flowingBrush),
                modifier = Modifier.graphicsLayer {
                    val scale = 1f + 0.035f * (1f - kotlin.math.abs(progress - 0.5f) * 2f)
                    scaleX = scale
                    scaleY = scale
                }
            )
        }
    }
}
