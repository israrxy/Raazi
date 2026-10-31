package com.israrxy.raazi.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.israrxy.raazi.ui.theme.Emerald400

data class LyricWordSegment(
    val startTimeMs: Long,
    val endTimeMs: Long,
    val text: String
)

data class LyricLine(
    val timestamp: Long,
    val endTimestamp: Long = timestamp + 3000L,
    val text: String,
    val words: List<LyricWordSegment> = emptyList(),
    val hasExplicitWordSync: Boolean = false,
    val translation: String? = null
)

internal val LINE_TIMESTAMP_REGEX = Regex("\\[(\\d+):(\\d+(?:\\.\\d+)?)\\]")
internal val WORD_TIMESTAMP_REGEX = Regex("<(\\d+):(\\d+(?:\\.\\d+)?)>([^<]+)")
internal val STRIP_WORD_TAGS_REGEX = Regex("<\\d+:\\d+(?:\\.\\d+)?>")

/**
 * Robust LRC & Enhanced LRC parser supporting [mm:ss], [mm:ss.xx], [mm:ss.xxx]
 * and inline word timestamps <mm:ss.xx>word (Musixmatch RichSync / Enhanced LRC).
 */
fun parseLrcLines(syncedLyrics: String?): List<Pair<Long, String>> {
    if (syncedLyrics.isNullOrBlank()) return emptyList()
    val result = mutableListOf<Pair<Long, String>>()

    syncedLyrics.lines().forEach { rawLine ->
        val matches = LINE_TIMESTAMP_REGEX.findAll(rawLine).toList()
        if (matches.isNotEmpty()) {
            val rawBody = rawLine.substring(matches.last().range.last + 1).trim()
            val cleanText = rawBody.replace(STRIP_WORD_TAGS_REGEX, "").trim()
            if (cleanText.isNotEmpty()) {
                matches.forEach { match ->
                    val min = match.groupValues[1].toLongOrNull() ?: 0L
                    val sec = match.groupValues[2].toDoubleOrNull() ?: 0.0
                    val timeMs = (min * 60 * 1000 + sec * 1000).toLong()
                    result.add(timeMs to rawBody)
                }
            }
        }
    }
    return result.sortedBy { it.first }
}

fun buildRichLyricLines(
    rawPairs: List<Pair<Long, String>>,
    durationMs: Long,
    translations: List<String>,
    showTranslation: Boolean
): List<LyricLine> {
    if (rawPairs.isEmpty()) return emptyList()
    return rawPairs.mapIndexed { idx, (lineStartMs, rawBody) ->
        val nextLineStartMs = rawPairs.getOrNull(idx + 1)?.first
            ?: durationMs.takeIf { it > lineStartMs }
            ?: (lineStartMs + 4000L)
        val rawGap = (nextLineStartMs - lineStartMs).coerceAtLeast(600L)

        val wordMatches = WORD_TIMESTAMP_REGEX.findAll(rawBody).toList()
        val cleanText = rawBody.replace(STRIP_WORD_TAGS_REGEX, "")
            .replace(Regex("\\s+"), " ")
            .trim()

        val words: List<LyricWordSegment>
        val hasExplicitWords: Boolean
        val lineEndMs: Long

        if (wordMatches.isNotEmpty()) {
            hasExplicitWords = true
            val parsedWords = wordMatches.mapNotNull { match ->
                val min = match.groupValues[1].toLongOrNull() ?: 0L
                val sec = match.groupValues[2].toDoubleOrNull() ?: 0.0
                val wordStart = (min * 60 * 1000 + sec * 1000).toLong()
                val token = match.groupValues[3].trim()
                if (token.isEmpty()) null else wordStart to token
            }
            words = parsedWords.mapIndexed { wIdx, (wStart, token) ->
                val wEnd = parsedWords.getOrNull(wIdx + 1)?.first
                    ?: (wStart + 420L).coerceAtMost(nextLineStartMs)
                LyricWordSegment(
                    startTimeMs = wStart,
                    endTimeMs = wEnd.coerceAtLeast(wStart + 80L),
                    text = token
                )
            }
            lineEndMs = words.lastOrNull()?.endTimeMs ?: (lineStartMs + rawGap.coerceAtMost(5000L))
        } else {
            hasExplicitWords = false
            val splitTokens = cleanText.split(" ").filter { it.isNotBlank() }
            val activeSingDuration = rawGap.coerceIn(900L, (splitTokens.size * 420L).coerceIn(1400L, 5200L))
                .coerceAtMost((rawGap - 180L).coerceAtLeast(500L))
            lineEndMs = lineStartMs + activeSingDuration
            val totalWeight = splitTokens.sumOf { it.length.coerceAtLeast(2) }.coerceAtLeast(1)
            var cursorMs = lineStartMs
            words = splitTokens.mapIndexed { wIdx, token ->
                val weight = token.length.coerceAtLeast(2)
                val sliceMs = if (wIdx == splitTokens.lastIndex) {
                    (lineEndMs - cursorMs).coerceAtLeast(80L)
                } else {
                    ((activeSingDuration * weight) / totalWeight).coerceAtLeast(80L)
                }
                val seg = LyricWordSegment(
                    startTimeMs = cursorMs,
                    endTimeMs = (cursorMs + sliceMs).coerceAtMost(lineEndMs),
                    text = token
                )
                cursorMs += sliceMs
                seg
            }
        }

        LyricLine(
            timestamp = lineStartMs,
            endTimestamp = lineEndMs,
            text = cleanText,
            words = words,
            hasExplicitWordSync = hasExplicitWords,
            translation = if (showTranslation && idx < translations.size) translations[idx] else null
        )
    }
}

@Composable
internal fun LyricsLoadingPlaceholder() {
    val infiniteTransition = rememberInfiniteTransition(label = "lyrics_shimmer")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.15f,
        targetValue = 0.40f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shimmer_alpha"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 48.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            CircularProgressIndicator(
                color = Color.White.copy(alpha = 0.78f),
                strokeWidth = 2.dp,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = "Loading lyrics...",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.65f)
            )
        }

        val widths = listOf(0.75f, 0.92f, 0.60f, 0.85f, 0.70f, 0.50f, 0.82f)
        widths.forEach { fraction ->
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .height(28.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.White.copy(alpha = alpha))
            )
        }
    }
}

@Composable
internal fun LyricsEmptyPlaceholder(
    onSearchManually: () -> Unit,
    onRetry: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.08f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Lyrics,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.45f),
                    modifier = Modifier.size(34.dp)
                )
            }

            Text(
                text = "Couldn't find lyrics automatically",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center
            )

            Text(
                text = "Search manually across Musixmatch, LRCLIB, Genius, Lyrica & YT Music to pick a matching version.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.6f),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onSearchManually,
                    shape = RoundedCornerShape(999.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color.Black
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Search Lyrics",
                        fontWeight = FontWeight.Bold
                    )
                }

                OutlinedButton(
                    onClick = onRetry,
                    shape = RoundedCornerShape(999.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f))
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Retry")
                }
            }
        }
    }
}
