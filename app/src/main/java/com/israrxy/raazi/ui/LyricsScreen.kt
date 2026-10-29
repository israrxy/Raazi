package com.israrxy.raazi.ui

import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.israrxy.raazi.data.local.SettingsDataStore
import com.israrxy.raazi.data.lyrics.LyricsTextVariants
import com.israrxy.raazi.data.lyrics.LyricsViewVariant
import com.israrxy.raazi.service.LyricsTranslator
import com.israrxy.raazi.utils.BlurTransformation
import com.israrxy.raazi.utils.ThumbnailUtils
import com.israrxy.raazi.viewmodel.MusicPlayerViewModel
import kotlinx.coroutines.delay
import kotlin.math.abs

/**
 * Unified Spotify + YouTube Music + Musixmatch style Lyrics Screen.
 * Supports word-by-word and line-by-line karaoke highlight, smooth auto-scrolling,
 * manual scroll with "Re-sync" pill, timestamp tap-to-seek, musical interlude pulsing dots,
 * Romanization/Script switching, AI Translation, provider badge, and line sharing.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun LyricsScreen(
    viewModel: MusicPlayerViewModel,
    onClose: () -> Unit,
    onOpenLyricsBrowser: () -> Unit = {}
) {
    val context = LocalContext.current
    val view = LocalView.current

    val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()
    val lyrics by viewModel.lyrics.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLyricsLoading.collectAsStateWithLifecycle()
    val lyricsScriptFilter by viewModel.lyricsScriptFilter.collectAsStateWithLifecycle()
    val favoriteTracks by viewModel.favoriteTracks.collectAsStateWithLifecycle()

    val currentTrack = playbackState.currentTrack
    val currentPosition = playbackState.currentPosition
    val duration = playbackState.duration
    val isPlaying = playbackState.isPlaying

    var smoothPositionMs by remember { mutableLongStateOf(currentPosition) }
    LaunchedEffect(currentPosition, isPlaying, duration) {
        smoothPositionMs = currentPosition
        if (isPlaying) {
            val basePosition = currentPosition
            val startWallClock = android.os.SystemClock.elapsedRealtime()
            while (true) {
                delay(33L)
                val elapsed = android.os.SystemClock.elapsedRealtime() - startWallClock
                val maxPos = if (duration > 0L) duration else Long.MAX_VALUE
                smoothPositionMs = (basePosition + elapsed).coerceAtMost(maxPos)
            }
        }
    }

    val isTrackLiked = remember(currentTrack?.id, favoriteTracks) {
        currentTrack != null && favoriteTracks.any { it.id == currentTrack.id }
    }

    val settingsDataStore = remember { SettingsDataStore(context) }
    val geminiApiKey by settingsDataStore.geminiApiKey.collectAsStateWithLifecycle(initialValue = "")
    val translateLang by settingsDataStore.lyricsTranslateLang.collectAsStateWithLifecycle(initialValue = "en")

    var showTranslation by remember { mutableStateOf(false) }
    var isTranslating by remember { mutableStateOf(false) }
    val translatedLines = remember { mutableStateListOf<String>() }
    var translatedPlain by remember { mutableStateOf<String?>(null) }

    var showOptionsMenu by remember { mutableStateOf(false) }
    var selectedVariant by remember(currentTrack?.id) { mutableStateOf(LyricsViewVariant.ORIGINAL) }

    val rawSourceText = remember(lyrics) {
        (lyrics?.syncedLyrics ?: lyrics?.plainLyrics.orEmpty()).replace(STRIP_WORD_TAGS_REGEX, "")
    }
    val availableVariants = remember(rawSourceText) {
        LyricsTextVariants.availableVariants(rawSourceText)
    }
    LaunchedEffect(availableVariants) {
        if (selectedVariant !in availableVariants) {
            selectedVariant = LyricsViewVariant.ORIGINAL
        }
    }

    val parsedPairs = remember(lyrics?.syncedLyrics) {
        parseLrcLines(lyrics?.syncedLyrics)
    }

    val syncedLines = remember(parsedPairs, duration, translatedLines.toList(), showTranslation) {
        buildRichLyricLines(
            rawPairs = parsedPairs,
            durationMs = duration,
            translations = translatedLines.toList(),
            showTranslation = showTranslation
        )
    }

    val isSynced = remember(syncedLines) { syncedLines.isNotEmpty() }
    val hasWordSync = remember(syncedLines) { syncedLines.any { it.hasExplicitWordSync } }

    LaunchedEffect(lyrics?.syncedLyrics, lyrics?.plainLyrics, showTranslation, translateLang, geminiApiKey) {
        if (!showTranslation || geminiApiKey.isNullOrBlank()) {
            translatedLines.clear()
            translatedPlain = null
            return@LaunchedEffect
        }
        val source = lyrics?.syncedLyrics ?: lyrics?.plainLyrics
        if (source.isNullOrBlank()) return@LaunchedEffect

        isTranslating = true
        try {
            if (parsedPairs.isNotEmpty()) {
                val originalTexts = parsedPairs.map { it.second.replace(STRIP_WORD_TAGS_REGEX, "").trim() }
                val translations = LyricsTranslator.translateLines(geminiApiKey!!, originalTexts, translateLang)
                translatedLines.clear()
                translatedLines.addAll(translations)
            } else if (!lyrics?.plainLyrics.isNullOrBlank()) {
                val translations = LyricsTranslator.translateLines(geminiApiKey!!, listOf(lyrics!!.plainLyrics!!), translateLang)
                translatedPlain = translations.firstOrNull()
            }
        } catch (_: Exception) {
        } finally {
            isTranslating = false
        }
    }

    val activeLineIndex = remember(smoothPositionMs, syncedLines, isSynced) {
        if (!isSynced || syncedLines.isEmpty()) -1
        else syncedLines.indexOfLast { it.timestamp <= smoothPositionMs + 120L }
    }

    val listState = rememberLazyListState()
    val isDragged by listState.interactionSource.collectIsDraggedAsState()
    var isUserScrolling by remember { mutableStateOf(false) }
    var lastManualScrollTime by remember { mutableLongStateOf(0L) }

    LaunchedEffect(isDragged) {
        if (isDragged) {
            isUserScrolling = true
            lastManualScrollTime = System.currentTimeMillis()
        } else if (isUserScrolling) {
            val captured = lastManualScrollTime
            delay(4000)
            if (lastManualScrollTime == captured && !isDragged) {
                isUserScrolling = false
            }
        }
    }

    LaunchedEffect(currentTrack?.id) {
        isUserScrolling = false
    }

    LaunchedEffect(activeLineIndex, isUserScrolling, syncedLines.size) {
        if (!isUserScrolling && activeLineIndex >= 0 && syncedLines.isNotEmpty()) {
            val firstLineTimestamp = syncedLines.firstOrNull()?.timestamp ?: 0L
            val headerOffset = if (firstLineTimestamp > 4000L) 1 else 0
            val targetLazyIndex = (activeLineIndex + headerOffset).coerceAtLeast(0)

            if (listState.layoutInfo.totalItemsCount == 0) {
                kotlinx.coroutines.yield()
            }

            val layoutInfo = listState.layoutInfo
            val targetItem = layoutInfo.visibleItemsInfo.find { it.index == targetLazyIndex }
            val viewportHeight = layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset

            if (targetItem != null && viewportHeight > 0) {
                val desiredY = (viewportHeight * 0.28f).toInt()
                val itemCenter = targetItem.offset + (targetItem.size / 2)
                val diff = itemCenter - desiredY
                if (abs(diff) > 10) {
                    listState.animateScrollBy(
                        value = diff.toFloat(),
                        animationSpec = tween(durationMillis = 380, easing = FastOutSlowInEasing)
                    )
                }
            } else {
                val anchorOffset = if (viewportHeight > 0) -(viewportHeight * 0.25f).toInt() else -160
                listState.animateScrollToItem(
                    index = targetLazyIndex,
                    scrollOffset = anchorOffset
                )
            }
        }
    }

    val lyricsStatusBadge = remember(isLoading, lyrics, isSynced, hasWordSync) {
        val rawProvider = lyrics?.source
            ?.removePrefix("Saved ")
            ?.trim()
            ?.uppercase()
            .orEmpty()
        val isSaved = lyrics?.source?.startsWith("Saved") == true
        when {
            isLoading -> "SEARCHING..."
            lyrics == null || (lyrics?.syncedLyrics.isNullOrBlank() && lyrics?.plainLyrics.isNullOrBlank()) -> "NO LYRICS"
            hasWordSync -> buildString {
                if (isSaved) append("PINNED • ")
                if (rawProvider.isNotBlank()) append("$rawProvider • ")
                append("WORD SYNC")
            }
            isSynced -> buildString {
                if (isSaved) append("PINNED • ")
                if (rawProvider.isNotBlank()) append("$rawProvider • ")
                append("SYNCED")
            }
            else -> buildString {
                if (isSaved) append("PINNED • ")
                if (rawProvider.isNotBlank()) append("$rawProvider • ")
                append("PLAIN")
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
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
                            0.0f to Color.Black.copy(alpha = 0.72f),
                            0.25f to Color.Black.copy(alpha = 0.54f),
                            0.75f to Color.Black.copy(alpha = 0.66f),
                            1.0f to Color.Black.copy(alpha = 0.88f)
                        )
                    )
                )
        )

        if (showOptionsMenu) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(5f)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { showOptionsMenu = false }
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            LyricsTopNavigationBar(
                showOptionsMenu = showOptionsMenu,
                selectedVariant = selectedVariant,
                showTranslation = showTranslation,
                onClose = onClose,
                onOpenLyricsBrowser = {
                    showOptionsMenu = false
                    onOpenLyricsBrowser()
                },
                onToggleOptionsMenu = { showOptionsMenu = !showOptionsMenu }
            )

            LyricsNowPlayingStrip(
                currentTrack = currentTrack,
                lyricsSource = lyrics?.source,
                isSynced = isSynced,
                lyricsStatusBadge = lyricsStatusBadge,
                isTrackLiked = isTrackLiked,
                showOptionsMenu = showOptionsMenu,
                availableVariants = availableVariants,
                selectedVariant = selectedVariant,
                onSelectVariant = { selectedVariant = it },
                geminiApiKey = geminiApiKey,
                showTranslation = showTranslation,
                isTranslating = isTranslating,
                translateLang = translateLang,
                onToggleTranslation = { showTranslation = !showTranslation },
                lyricsScriptFilter = lyricsScriptFilter,
                onToggleOptionsMenu = { showOptionsMenu = !showOptionsMenu },
                onOpenLyricsBrowser = onOpenLyricsBrowser,
                onToggleFavorite = { viewModel.toggleFavorite(it) }
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                LyricsSettingsDropdownMenu(
                    visible = showOptionsMenu,
                    lyrics = lyrics,
                    hasWordSync = hasWordSync,
                    isSynced = isSynced,
                    availableVariants = availableVariants,
                    selectedVariant = selectedVariant,
                    onSelectVariant = { selectedVariant = it },
                    hasTranslationSupport = !geminiApiKey.isNullOrBlank(),
                    showTranslation = showTranslation,
                    translateLang = translateLang,
                    onToggleTranslation = { showTranslation = !showTranslation },
                    lyricsScriptFilter = lyricsScriptFilter,
                    onDismiss = { showOptionsMenu = false },
                    onOpenLyricsBrowser = onOpenLyricsBrowser,
                    onRetryLyricsFetch = { viewModel.retryLyricsFetch() },
                    onClearSavedLyricsSelection = { viewModel.clearSavedLyricsSelection() },
                    onSelectScriptFilter = { viewModel.setLyricsScriptFilter(it) }
                )

                LyricsMainViewportContent(
                    isLoading = isLoading,
                    lyrics = lyrics,
                    isSynced = isSynced,
                    syncedLines = syncedLines,
                    activeLineIndex = activeLineIndex,
                    currentPosition = smoothPositionMs,
                    duration = duration,
                    listState = listState,
                    isUserScrolling = isUserScrolling,
                    onResetUserScrolling = { isUserScrolling = false },
                    selectedVariant = selectedVariant,
                    translatedPlain = translatedPlain,
                    currentTrack = currentTrack,
                    onOpenLyricsBrowser = onOpenLyricsBrowser,
                    onRetryLyricsFetch = { viewModel.retryLyricsFetch() },
                    onSeekTo = { viewModel.seekTo(it) }
                )
            }

            LyricsBottomTransport(
                currentPosition = currentPosition,
                duration = duration,
                isPlaying = isPlaying,
                shuffleEnabled = playbackState.isShuffleEnabled,
                repeatMode = playbackState.repeatMode,
                onSeek = { viewModel.seekTo(it) },
                onPlayPause = {
                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    if (isPlaying) viewModel.pause() else viewModel.resume()
                },
                onPrevious = {
                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    viewModel.previous()
                },
                onNext = {
                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    viewModel.next()
                },
                onToggleShuffle = { viewModel.toggleShuffle() },
                onToggleRepeat = { viewModel.toggleRepeat() }
            )
        }
    }
}
