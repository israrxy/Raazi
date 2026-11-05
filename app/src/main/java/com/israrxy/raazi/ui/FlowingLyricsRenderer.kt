package com.israrxy.raazi.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp

/**
 * Supporting flowing lyrics components for word-by-word synced rendering.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FlowingLyricLine(
    words: List<LyricWordSegment>,
    currentPositionMs: Long,
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
                textStyle = textStyle
            )
        }
    }
}

@Composable
internal fun FlowingLyricWord(
    word: LyricWordSegment,
    currentPositionMs: Long,
    textStyle: TextStyle
) {
    val wordColor = when {
        currentPositionMs >= word.endTimeMs -> Color.White.copy(alpha = 0.95f)
        currentPositionMs >= word.startTimeMs -> Color.White
        else -> Color.White.copy(alpha = 0.35f)
    }

    Text(
        text = word.displayText,
        style = textStyle,
        color = wordColor
    )
}
