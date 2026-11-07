package com.israrxy.raazi.ui

import android.os.Build
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.israrxy.raazi.utils.BlurTransformation
import com.israrxy.raazi.utils.ThumbnailUtils
import com.israrxy.raazi.viewmodel.MusicPlayerViewModel
import kotlinx.coroutines.delay
import kotlin.math.abs

/**
 * Unified Spotify + YouTube Music + Musixmatch style Lyrics Screen.
 *
 * All presentation state (variant, translation, brackets, processed lines) is
 * supplied by a single [LyricsPresentation] shared with the mini player card.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
internal fun LyricsScreen(
    viewModel: MusicPlayerViewModel,
    presentation: LyricsPresentation,
    onOpenLyricsBrowser: () -> Unit = {},
    showOptionsMenu: Boolean = false,
    onDismissOptionsMenu: () -> Unit = {},
    topBar: @Composable () -> Unit = {}
) {
    val context = LocalContext.current
    val view = LocalView.current

    val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()
    val lyricsScriptFilter by viewModel.lyricsScriptFilter.collectAsStateWithLifecycle()
    val favoriteTracks by viewModel.favoriteTracks.collectAsStateWithLifecycle()

    val lyrics = presentation.lyrics
    val isLoading = presentation.isLoading
    val isUnavailable = presentation.isUnavailable
    val syncedLines = presentation.syncedLines
    val isSynced = presentation.isSynced
    val hasWordSync = presentation.hasWordSync
    val availableVariants = presentation.availableVariants
    val selectedVariant = presentation.selectedVariant
    val showTranslation = presentation.showTranslation
    val isTranslating = presentation.isTranslating
    val translateLang = presentation.translateLang
    val hideBrackets = presentation.hideBrackets
    val translatedPlain = presentation.translatedPlain

    val currentTrack = playbackState.currentTrack
    val currentPosition = playbackState.currentPosition
    val duration = playbackState.duration
    val isPlaying = playbackState.isPlaying

    // Smooth position tracker for active lyric line and timeline
    var smoothPositionMs by remember { mutableLongStateOf(currentPosition) }
    LaunchedEffect(currentPosition, isPlaying, duration) {
        smoothPositionMs = currentPosition
        if (isPlaying) {
            val basePosition = currentPosition
            val startWallClock = SystemClock.elapsedRealtime()
            while (true) {
                delay(33L)
                val elapsed = SystemClock.elapsedRealtime() - startWallClock
                val maxPos = if (duration > 0L) duration else Long.MAX_VALUE
                smoothPositionMs = (basePosition + elapsed).coerceAtMost(maxPos)
            }
        }
    }

    val isTrackLiked = remember(currentTrack?.id, favoriteTracks) {
        currentTrack != null && favoriteTracks.any { it.id == currentTrack.id }
    }

    var showTrackDetails by remember { mutableStateOf(false) }

    // Active line index using binary search
    val activeLineIndex = remember(smoothPositionMs, syncedLines) {
        if (syncedLines.isEmpty()) -1
        else binarySearchActiveLine(syncedLines, smoothPositionMs)
    }

    val listState = rememberLazyListState()
    val isDragged by listState.interactionSource.collectIsDraggedAsState()
    var isUserScrolling by remember { mutableStateOf(false) }
    var lastManualScrollTime by remember { mutableLongStateOf(0L) }
    var hasPerformedInitialSnap by remember(lyrics?.syncedLyrics) { mutableStateOf(false) }

    LaunchedEffect(isDragged) {
        if (isDragged) {
            isUserScrolling = true
            lastManualScrollTime = System.currentTimeMillis()
        } else if (isUserScrolling) {
            val captured = lastManualScrollTime
            delay(3000)
            if (lastManualScrollTime == captured && !isDragged) {
                isUserScrolling = false
            }
        }
    }

    LaunchedEffect(currentTrack?.id) {
        isUserScrolling = false
    }

    // Snap instantly on first mount, then smoothly follow the active line.
    LaunchedEffect(activeLineIndex, isUserScrolling, syncedLines) {
        if (!isUserScrolling && activeLineIndex >= 0 && syncedLines.isNotEmpty()) {
            val firstLineTimestamp = syncedLines.firstOrNull()?.timestamp ?: 0L
            val headerOffset = if (firstLineTimestamp > 4000L) 1 else 0
            val targetLazyIndex = (activeLineIndex + headerOffset).coerceAtLeast(0)

            try {
                withFrameNanos { }
                val layoutInfo = listState.layoutInfo
                val totalCount = layoutInfo.totalItemsCount
                if (totalCount > 0) {
                    val safeTargetIndex = targetLazyIndex.coerceIn(0, totalCount - 1)
                    if (!hasPerformedInitialSnap) {
                        listState.scrollToItem(safeTargetIndex)
                        hasPerformedInitialSnap = true
                        return@LaunchedEffect
                    }
                    val targetItem = layoutInfo.visibleItemsInfo.find { it.index == safeTargetIndex }
                    val viewportHeight = layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset

                    if (targetItem != null && viewportHeight > 0) {
                        val desiredY = (viewportHeight * 0.35f).toInt()
                        val itemCenter = targetItem.offset + (targetItem.size / 2)
                        val diff = itemCenter - desiredY
                        if (abs(diff) > 10) {
                            listState.animateScrollBy(
                                value = diff.toFloat(),
                                animationSpec = tween(durationMillis = 380, easing = FastOutSlowInEasing)
                            )
                        }
                    } else {
                        listState.animateScrollToItem(
                            index = safeTargetIndex,
                            scrollOffset = 0
                        )
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Throwable) {
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF09090B))) {
        if (currentTrack != null) {
            val isAndroid12 = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
            AsyncImage(
                model = if (isAndroid12) {
                    ThumbnailUtils.getHighQualityThumbnail(currentTrack.thumbnailUrl)
                } else {
                    ImageRequest.Builder(context)
                        .data(ThumbnailUtils.getHighQualityThumbnail(currentTrack.thumbnailUrl))
                        .transformations(BlurTransformation(context, radius = 25, sampling = 6f))
                        .build()
                },
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = 1.25f
                        scaleY = 1.25f
                    }
                    .then(if (isAndroid12) Modifier.blur(radius = 75.dp) else Modifier),
                contentScale = ContentScale.Crop
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.0f to Color.Black.copy(alpha = 0.58f),
                            0.22f to Color.Black.copy(alpha = 0.34f),
                            0.62f to Color.Black.copy(alpha = 0.52f),
                            1.0f to Color.Black.copy(alpha = 0.84f)
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
        ) {
            topBar()

            LyricsNowPlayingStrip(
                currentTrack = currentTrack,
                isTrackLiked = isTrackLiked,
                isPlaying = isPlaying,
                currentPosition = smoothPositionMs,
                duration = duration,
                onPlayPause = {
                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    if (isPlaying) viewModel.pause() else viewModel.resume()
                },
                onSeek = { viewModel.seekTo(it) },
                onToggleFavorite = { viewModel.toggleFavorite(it) }
            )

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                LyricsMainViewportContent(
                    isLoading = isLoading,
                    isUnavailable = isUnavailable,
                    lyrics = lyrics,
                    isSynced = isSynced,
                    syncedLines = syncedLines,
                    activeLineIndex = activeLineIndex,
                    currentPosition = smoothPositionMs,
                    duration = duration,
                    listState = listState,
                    isUserScrolling = isUserScrolling,
                    onResetUserScrolling = { isUserScrolling = false },
                    plainLyrics = presentation.plainLyrics,
                    translatedPlain = translatedPlain,
                    currentTrack = currentTrack,
                    onOpenLyricsBrowser = onOpenLyricsBrowser,
                    onRetryLyricsFetch = { viewModel.retryLyricsFetch() },
                    onSeekTo = { viewModel.seekTo(it) }
                )
            }
        }

        LyricsOptionsSheet(
            visible = showOptionsMenu,
            lyrics = lyrics,
            hasWordSync = hasWordSync,
            isSynced = isSynced,
            availableVariants = availableVariants,
            selectedVariant = selectedVariant,
            onSelectVariant = presentation.onSelectVariant,
            hasTranslationSupport = true,
            showTranslation = showTranslation,
            translateLang = translateLang,
            onToggleTranslation = presentation.onToggleTranslation,
            hideBrackets = hideBrackets,
            onToggleHideBrackets = presentation.onToggleHideBrackets,
            lyricsScriptFilter = lyricsScriptFilter,
            onDismiss = onDismissOptionsMenu,
            onOpenLyricsBrowser = onOpenLyricsBrowser,
            onOpenTrackDetails = { showTrackDetails = true },
            onRetryLyricsFetch = { viewModel.retryLyricsFetch() },
            onClearSavedLyricsSelection = { viewModel.clearSavedLyricsSelection() },
            onSelectScriptFilter = { viewModel.setLyricsScriptFilter(it) }
        )

        SongDetailsDialog(
            visible = showTrackDetails,
            currentTrack = currentTrack,
            lyricsSource = lyrics?.source,
            onDismiss = { showTrackDetails = false }
        )
    }
}

internal fun binarySearchActiveLine(lines: List<LyricLine>, positionMs: Long): Int {
    if (lines.isEmpty()) return -1
    var low = 0
    var high = lines.size - 1
    var result = -1
    while (low <= high) {
        val mid = (low + high).ushr(1)
        if (lines[mid].timestamp <= positionMs + 120L) {
            result = mid
            low = mid + 1
        } else {
            high = mid - 1
        }
    }
    return result
}
