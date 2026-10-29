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
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF141417)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
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
                        text = "Lyrics",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                    if (syncedLines.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color.White.copy(alpha = 0.08f)
                        ) {
                            Text(
                                text = "Synced",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium,
                                color = Color.White.copy(alpha = 0.72f),
                                fontSize = 10.sp,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        onClick = onSearchLyrics,
                        shape = RoundedCornerShape(8.dp),
                        color = Color.White.copy(alpha = 0.07f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Switch lyrics source",
                                tint = Color.White.copy(alpha = 0.75f),
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = "Source",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium,
                                color = Color.White.copy(alpha = 0.82f)
                            )
                        }
                    }

                    Surface(
                        onClick = onExpandLyrics,
                        shape = RoundedCornerShape(8.dp),
                        color = Color.White.copy(alpha = 0.11f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.OpenInFull,
                                contentDescription = "Expand lyrics",
                                tint = Color.White,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = "Expand",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
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
                            .padding(vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = Color.White.copy(alpha = 0.78f)
                        )
                        Text(
                            text = "Loading lyrics...",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.56f)
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
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (prevLine != null) {
                            Text(
                                text = prevLine.second,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = Color.White.copy(alpha = 0.30f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        if (currLine != null) {
                            AnimatedContent(
                                targetState = currLine,
                                transitionSpec = {
                                    (fadeIn(tween(220)) + slideInVertically { it / 3 }) togetherWith
                                        (fadeOut(tween(160)) + slideOutVertically { -it / 3 })
                                },
                                label = "preview_active_lyric"
                            ) { activePair ->
                                Text(
                                    text = activePair.second,
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontSize = 20.sp,
                                        lineHeight = 26.sp
                                    ),
                                    fontWeight = FontWeight.Bold,
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
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White.copy(alpha = 0.52f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        if (nextLine2 != null) {
                            Text(
                                text = nextLine2.second,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Normal,
                                color = Color.White.copy(alpha = 0.30f),
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
                                style = if (idx == 0) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodyMedium,
                                fontWeight = if (idx == 0) FontWeight.SemiBold else FontWeight.Normal,
                                color = Color.White.copy(alpha = if (idx == 0) 0.88f else 0.48f),
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
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "No lyrics loaded",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.50f)
                        )
                        TextButton(onClick = onSearchLyrics) {
                            Text(
                                text = "Choose Source",
                                color = Color.White.copy(alpha = 0.88f),
                                fontWeight = FontWeight.Medium
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
