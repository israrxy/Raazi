package com.israrxy.raazi.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.israrxy.raazi.ui.theme.Emerald400
import com.israrxy.raazi.ui.theme.Emerald500

/**
 * Spotify-style Inline Lyrics Preview Card shown below the main player controls
 * on PlayerScreen. Shows live synced lines in real-time and expands into full Lyrics mode.
 */
@Composable
fun SpotifyLyricsPreviewCard(
    syncedLines: List<Pair<Long, String>>,
    plainLyrics: String?,
    currentPosition: Long,
    isLyricsLoading: Boolean,
    onExpandLyrics: () -> Unit,
    onSearchLyrics: () -> Unit,
    onSeekToLine: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val activeIdx = remember(currentPosition, syncedLines) {
        if (syncedLines.isEmpty()) -1
        else syncedLines.indexOfLast { it.first <= currentPosition }.coerceAtLeast(0)
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onExpandLyrics() },
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF141C24).copy(alpha = 0.92f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Emerald500.copy(alpha = 0.14f),
                            Color.Transparent
                        )
                    )
                )
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "LYRICS",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White.copy(alpha = 0.85f),
                        letterSpacing = 1.2.sp
                    )
                    if (syncedLines.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = Emerald500.copy(alpha = 0.2f)
                        ) {
                            Text(
                                text = "SYNCED",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Emerald400,
                                fontSize = 9.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = Color.White.copy(alpha = 0.10f),
                        modifier = Modifier.clickable { onSearchLyrics() }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Match lyrics",
                                tint = Color.White.copy(alpha = 0.8f),
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "Match",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White.copy(alpha = 0.85f)
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = Color.White.copy(alpha = 0.14f),
                        modifier = Modifier.clickable { onExpandLyrics() }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.OpenInFull,
                                contentDescription = "Expand lyrics",
                                tint = Color.White,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = "Full Lyrics",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }
            }

            when {
                isLyricsLoading -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = Emerald400
                        )
                        Text(
                            text = "Fetching synced lyrics...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.65f)
                        )
                    }
                }

                syncedLines.isNotEmpty() && activeIdx >= 0 -> {
                    val prevLine = syncedLines.getOrNull(activeIdx - 1)
                    val currLine = syncedLines.getOrNull(activeIdx)
                    val nextLine1 = syncedLines.getOrNull(activeIdx + 1)
                    val nextLine2 = syncedLines.getOrNull(activeIdx + 2)

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (prevLine != null) {
                            Text(
                                text = prevLine.second,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White.copy(alpha = 0.32f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        if (currLine != null) {
                            AnimatedContent(
                                targetState = currLine,
                                transitionSpec = {
                                    (fadeIn(tween(240)) + slideInVertically { it / 3 }) togetherWith
                                        (fadeOut(tween(180)) + slideOutVertically { -it / 3 })
                                },
                                label = "preview_active_lyric"
                            ) { activePair ->
                                Text(
                                    text = activePair.second,
                                    style = MaterialTheme.typography.headlineSmall.copy(
                                        fontSize = 22.sp,
                                        lineHeight = 28.sp
                                    ),
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.White,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.clickable { onSeekToLine(activePair.first) }
                                )
                            }
                        }

                        if (nextLine1 != null) {
                            Text(
                                text = nextLine1.second,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White.copy(alpha = 0.58f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        if (nextLine2 != null) {
                            Text(
                                text = nextLine2.second,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                color = Color.White.copy(alpha = 0.35f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                !plainLyrics.isNullOrBlank() -> {
                    val previewLines = remember(plainLyrics) {
                        plainLyrics.lines().map { it.trim() }.filter { it.isNotBlank() }.take(4)
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        previewLines.forEachIndexed { idx, line ->
                            Text(
                                text = line,
                                style = if (idx == 0) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
                                fontWeight = if (idx == 0) FontWeight.Bold else FontWeight.Medium,
                                color = Color.White.copy(alpha = if (idx == 0) 0.9f else 0.55f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                else -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "No synced lyrics loaded yet",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.55f)
                        )
                        TextButton(onClick = onSearchLyrics) {
                            Text(
                                text = "Find Lyrics",
                                color = Emerald400,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

internal fun shareOrCopyLyricLine(
    context: Context,
    lyricText: String,
    trackTitle: String?,
    artistName: String?
) {
    val cleanLyric = lyricText.trim()
    if (cleanLyric.isEmpty()) return
    val quote = buildString {
        append("“")
        append(cleanLyric)
        append("”")
        if (!trackTitle.isNullOrBlank()) {
            append("\n— ")
            append(trackTitle)
            if (!artistName.isNullOrBlank()) {
                append(" • ")
                append(artistName)
            }
        }
    }

    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    clipboard?.setPrimaryClip(ClipData.newPlainText("Lyric Quote", quote))

    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, quote)
    }
    runCatching {
        context.startActivity(Intent.createChooser(shareIntent, "Share lyric"))
    }.onFailure {
        Toast.makeText(context, "Lyric copied to clipboard", Toast.LENGTH_SHORT).show()
    }
}

internal fun formatLyricsTimestamp(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0L)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%d:%02d", minutes, seconds)
}
