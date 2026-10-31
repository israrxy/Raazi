package com.israrxy.raazi.ui

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.israrxy.raazi.data.remote.Lyrics
import com.israrxy.raazi.data.lyrics.LyricsScriptFilter
import com.israrxy.raazi.data.lyrics.LyricsTextVariants
import com.israrxy.raazi.data.lyrics.LyricsViewVariant
import com.israrxy.raazi.model.MusicItem
import com.israrxy.raazi.ui.theme.Emerald400
import com.israrxy.raazi.ui.theme.Emerald500
import kotlin.math.abs

@Composable
internal fun LyricsMainViewportContent(
    isLoading: Boolean,
    lyrics: Lyrics?,
    isSynced: Boolean,
    syncedLines: List<LyricLine>,
    activeLineIndex: Int,
    currentPosition: Long,
    duration: Long,
    listState: LazyListState,
    isUserScrolling: Boolean,
    onResetUserScrolling: () -> Unit,
    selectedVariant: LyricsViewVariant,
    translatedPlain: String?,
    currentTrack: MusicItem?,
    onOpenLyricsBrowser: () -> Unit,
    onRetryLyricsFetch: () -> Unit,
    onSeekTo: (Long) -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current

    when {
        isLoading -> {
            LyricsLoadingPlaceholder()
        }

        lyrics == null || (lyrics.syncedLyrics.isNullOrBlank() && lyrics.plainLyrics.isNullOrBlank()) -> {
            LyricsEmptyPlaceholder(
                onSearchManually = onOpenLyricsBrowser,
                onRetry = onRetryLyricsFetch
            )
        }

        isSynced -> {
            val firstLineTimestamp = syncedLines.firstOrNull()?.timestamp ?: 0L
            val showIntroInterlude = firstLineTimestamp > 4000L && currentPosition < firstLineTimestamp

            Box(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        top = 72.dp,
                        bottom = 180.dp,
                        start = 20.dp,
                        end = 20.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (firstLineTimestamp > 4000L) {
                        item(key = "intro_interlude") {
                            AnimatedVisibility(
                                visible = showIntroInterlude,
                                enter = fadeIn() + scaleIn(),
                                exit = fadeOut() + scaleOut()
                            ) {
                                val introProgress = (currentPosition.toFloat() / firstLineTimestamp.toFloat()).coerceIn(0f, 1f)
                                MusicalInterludeDots(
                                    progress = introProgress,
                                    modifier = Modifier.padding(vertical = 12.dp)
                                )
                            }
                        }
                    }

                    itemsIndexed(
                        items = syncedLines,
                        key = { index, item -> "${index}_${item.timestamp}" }
                    ) { index, line ->
                        val isCurrent = index == activeLineIndex
                        val isPassed = index < activeLineIndex
                        val distance = if (activeLineIndex >= 0) abs(index - activeLineIndex) else index

                        val nextTimestamp = syncedLines.getOrNull(index + 1)?.timestamp ?: duration
                        val gapToNext = nextTimestamp - line.timestamp
                        val isInInstrumentalGap = isCurrent &&
                            gapToNext > 7500L &&
                            (currentPosition - line.timestamp) > 3500L &&
                            currentPosition < nextTimestamp - 500L

                        val displayLineText = remember(line.text, selectedVariant) {
                            LyricsTextVariants.transform(line.text, selectedVariant)
                        }

                        SpotifyYoutubeLyricItem(
                            text = displayLineText,
                            translation = line.translation,
                            timestampMs = line.timestamp,
                            lineEndTimestampMs = line.endTimestamp,
                            words = line.words,
                            currentPositionMs = currentPosition,
                            selectedVariant = selectedVariant,
                            isCurrent = isCurrent,
                            isPassed = isPassed,
                            distance = distance,
                            showTimestampBadge = isUserScrolling,
                            onClick = {
                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                onResetUserScrolling()
                                onSeekTo(line.timestamp)
                            },
                            onShareLine = {
                                shareOrCopyLyricLine(
                                    context = context,
                                    lyricText = displayLineText,
                                    trackTitle = currentTrack?.title,
                                    artistName = currentTrack?.artist
                                )
                            }
                        )

                        if (isInInstrumentalGap) {
                            val gapElapsed = (currentPosition - (line.timestamp + 3500L)).coerceAtLeast(0L)
                            val gapTotal = (gapToNext - 4000L).coerceAtLeast(1L)
                            MusicalInterludeDots(
                                progress = (gapElapsed.toFloat() / gapTotal.toFloat()).coerceIn(0f, 1f),
                                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                            )
                        }
                    }

                    item(key = "lyrics_footer") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 36.dp, bottom = 16.dp),
                            horizontalAlignment = Alignment.Start
                        ) {
                            HorizontalDivider(
                                color = Color.White.copy(alpha = 0.1f),
                                modifier = Modifier.width(64.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Lyrics provided by ${lyrics.source} • Long-press any line to share",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.38f)
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .align(Alignment.TopCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Black.copy(alpha = 0.45f), Color.Transparent)
                            )
                        )
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f))
                            )
                        )
                )

                AnimatedVisibility(
                    visible = isUserScrolling && activeLineIndex >= 0,
                    enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }) + scaleIn(),
                    exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 2 }) + scaleOut(),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 16.dp)
                ) {
                    Surface(
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            onResetUserScrolling()
                        },
                        shape = RoundedCornerShape(999.dp),
                        color = Color.White,
                        shadowElevation = 12.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MyLocation,
                                contentDescription = "Re-sync lyrics",
                                tint = Color.Black,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Sync to music",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black
                            )
                        }
                    }
                }
            }
        }

        else -> {
            val displayedPlain = remember(lyrics.plainLyrics, selectedVariant) {
                LyricsTextVariants.transform(lyrics.plainLyrics.orEmpty(), selectedVariant)
            }
            Box(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp, vertical = 32.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = Color.White.copy(alpha = 0.1f),
                        modifier = Modifier.padding(bottom = 20.dp)
                    ) {
                        Text(
                            text = "Unsynced lyrics • Tap 'Switch Match' above to find synced timecodes",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }

                    Text(
                        text = displayedPlain,
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontSize = 22.sp,
                            lineHeight = 36.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = Color.White.copy(alpha = 0.92f),
                        textAlign = TextAlign.Start,
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (!translatedPlain.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(24.dp))
                        HorizontalDivider(color = Color.White.copy(alpha = 0.15f))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = translatedPlain,
                            style = MaterialTheme.typography.titleMedium.copy(lineHeight = 28.sp),
                            color = Color.White.copy(alpha = 0.72f),
                            textAlign = TextAlign.Start,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Spacer(modifier = Modifier.height(120.dp))
                }
            }
        }
    }
}
