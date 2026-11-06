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
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.israrxy.raazi.data.lyrics.LyricsTextVariants
import com.israrxy.raazi.data.lyrics.LyricsViewVariant
import com.israrxy.raazi.ui.theme.Emerald400

@Immutable
data class LyricWordSegment(
    val startTimeMs: Long,
    val endTimeMs: Long,
    val text: String,
    val displayText: String = text
)

@Immutable
data class LyricLine(
    val timestamp: Long,
    val endTimestamp: Long = timestamp + 3000L,
    val text: String,
    val displayText: String = text,
    val words: List<LyricWordSegment> = emptyList(),
    val hasExplicitWordSync: Boolean = false,
    val translation: String? = null
)

internal val LINE_TIMESTAMP_REGEX = Regex("\\[(\\d+):(\\d+(?:\\.\\d+)?)\\]")
internal val WORD_TIMESTAMP_REGEX = Regex("<(\\d+):(\\d+(?:\\.\\d+)?)>([^<]+)")
internal val STRIP_WORD_TAGS_REGEX = Regex("<\\d+:\\d+(?:\\.\\d+)?>")
val BRACKETED_ANNOTATION_REGEX = Regex("""\[[^\]]*\]""")

fun cleanBracketedText(text: String): String {
    return text.replace(BRACKETED_ANNOTATION_REGEX, "")
        .replace(Regex("""\s+"""), " ")
        .trim()
}

/**
 * Robust LRC & Enhanced LRC parser supporting [mm:ss], [mm:ss.xx], [mm:ss.xxx]
 * and inline word timestamps <mm:ss.xx>word (Musixmatch RichSync / Enhanced LRC).
 */
fun parseLrcLines(syncedLyrics: String?, hideBrackets: Boolean = false): List<Pair<Long, String>> = runCatching {
    if (syncedLyrics.isNullOrBlank()) return@runCatching emptyList()
    val result = mutableListOf<Pair<Long, String>>()

    syncedLyrics.lines().forEach { rawLine ->
        val matches = LINE_TIMESTAMP_REGEX.findAll(rawLine).toList()
        if (matches.isNotEmpty()) {
            val lastMatchEnd = matches.last().range.last + 1
            val rawBody = if (lastMatchEnd < rawLine.length) rawLine.substring(lastMatchEnd).trim() else ""
            val processedBody = if (hideBrackets) cleanBracketedText(rawBody) else rawBody
            val cleanText = processedBody.replace(STRIP_WORD_TAGS_REGEX, "").trim()
            if (cleanText.isNotEmpty()) {
                matches.forEach { match ->
                    val min = match.groupValues[1].toLongOrNull() ?: 0L
                    val sec = match.groupValues[2].toDoubleOrNull() ?: 0.0
                    val timeMs = (min * 60 * 1000 + sec * 1000).toLong()
                    result.add(timeMs to processedBody)
                }
            }
        }
    }
    result.sortedBy { it.first }
}.getOrDefault(emptyList())

fun buildRichLyricLines(
    rawPairs: List<Pair<Long, String>>,
    durationMs: Long,
    translations: List<String>,
    showTranslation: Boolean,
    variant: LyricsViewVariant = LyricsViewVariant.ORIGINAL
): List<LyricLine> = runCatching {
    if (rawPairs.isEmpty()) return@runCatching emptyList()
    rawPairs.mapIndexed { idx, (lineStartMs, rawBody) ->
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
                val safeStart = wStart.coerceAtLeast(0L)
                val safeEnd = wEnd.coerceAtLeast(safeStart + 80L)
                LyricWordSegment(
                    startTimeMs = safeStart,
                    endTimeMs = safeEnd,
                    text = token,
                    displayText = LyricsTextVariants.transform(token, variant)
                )
            }
            lineEndMs = words.lastOrNull()?.endTimeMs ?: (lineStartMs + rawGap.coerceAtMost(5000L))
        } else {
            // Line-synced only. Do not fabricate word timings by slicing the
            // line duration across its words - that produces a fake karaoke
            // sweep that never matches the vocal. The whole line is shown at
            // once when its timestamp is reached.
            hasExplicitWords = false
            val tokenCount = cleanText.split(" ").count { it.isNotBlank() }.coerceAtLeast(1)
            val activeSingDuration = rawGap.coerceIn(900L, (tokenCount * 420L).coerceIn(1400L, 5200L))
                .coerceAtMost((rawGap - 180L).coerceAtLeast(500L))
            lineEndMs = lineStartMs + activeSingDuration
            words = emptyList()
        }

        LyricLine(
            timestamp = lineStartMs,
            endTimestamp = lineEndMs,
            text = cleanText,
            displayText = LyricsTextVariants.transform(cleanText, variant),
            words = words,
            hasExplicitWordSync = hasExplicitWords,
            translation = if (showTranslation && idx < translations.size) translations[idx] else null
        )
    }
}.getOrElse {
    rawPairs.map { (t, body) ->
        val clean = body.replace(STRIP_WORD_TAGS_REGEX, "").trim()
        LyricLine(timestamp = t, endTimestamp = t + 3000L, text = clean, displayText = clean)
    }
}

fun withPrecomputedVariant(lines: List<LyricLine>, variant: LyricsViewVariant): List<LyricLine> = runCatching {
    if (lines.isEmpty()) return@runCatching emptyList()
    lines.map { line ->
        line.copy(
            displayText = LyricsTextVariants.transform(line.text, variant),
            words = line.words.map { w ->
                w.copy(displayText = LyricsTextVariants.transform(w.text, variant))
            }
        )
    }
}.getOrDefault(lines)

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
            Surface(
                shape = CircleShape,
                color = Color.White.copy(alpha = 0.08f),
                modifier = Modifier.size(64.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Lyrics,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.size(30.dp)
                    )
                }
            }

            Text(
                text = "No lyrics found",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )

            Text(
                text = "Try switching to another source or search lyrics manually",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.5f),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(6.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = onRetry,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                ) {
                    Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "Retry", style = MaterialTheme.typography.labelMedium)
                }

                Button(
                    onClick = onSearchManually,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Emerald400,
                        contentColor = Color.Black
                    )
                ) {
                    Icon(imageVector = Icons.Default.Search, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Switch match",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
