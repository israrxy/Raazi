package com.israrxy.raazi.ui

import android.os.Build
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.CubicBezierEasing
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

/**
 * Unified Spotify + YouTube Music + Musixmatch style Lyrics Screen.
 * Fully optimized architecture:
 * - Text precomputation on Dispatchers.Default (no main thread transliteration)
 * - Wall-clock position provider with withFrameNanos (zero recomposition on time ticks)
 * - Draw-phase word wipe clipRect (0 layout remeasures per frame)
 * - Derived active line index with binary search
 * - Sleek timeline under song info, bottom controls removed for maximal lyrics viewport
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun LyricsScreen(
    viewModel: MusicPlayerViewModel,
    onOpenLyricsBrowser: () -> Unit = {},
    topBar: @Composable () -> Unit = {}
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

    LaunchedEffect(currentTrack?.id) {
        if (currentTrack != null && lyrics == null && !isLoading) {
            viewModel.retryLyricsFetch()
        }
    }

    // Fix 2: Anchored position provider replacing the delay(33) recomposition loop
    var anchorPos by remember { mutableLongStateOf(currentPosition) }
    var anchorTime by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    var isAnchorPlaying by remember { mutableStateOf(isPlaying) }

    LaunchedEffect(currentPosition, isPlaying) {
        val now = SystemClock.elapsedRealtime()
        val predicted = if (isAnchorPlaying) anchorPos + (now - anchorTime) else anchorPos
        if (abs(currentPosition - predicted) > 150L || isPlaying != isAnchorPlaying) {
            anchorPos = currentPosition
            anchorTime = now
            isAnchorPlaying = isPlaying
        }
    }

    val frame = remember { mutableLongStateOf(0L) }
    var hasActiveWords by remember { mutableStateOf(false) }

    LaunchedEffect(isPlaying, hasActiveWords) {
        while (isPlaying && hasActiveWords) {
            withFrameNanos { frame.longValue = it }
        }
    }

    val positionProvider: () -> Long = remember(duration) {
        {
            if (hasActiveWords) {
                val _ = frame.longValue
            }
            val now = SystemClock.elapsedRealtime()
            val elapsed = if (isAnchorPlaying) (now - anchorTime) else 0L
            val pos = anchorPos + elapsed
            if (duration > 0L) pos.coerceIn(0L, duration) else pos.coerceAtLeast(0L)
        }
    }

    val isTrackLiked = remember(currentTrack?.id, favoriteTracks) {
        currentTrack != null && favoriteTracks.any { it.id == currentTrack.id }
    }

    val settingsDataStore = remember { SettingsDataStore(context) }
    val coroutineScope = rememberCoroutineScope()
    val translateEnabledPref by settingsDataStore.lyricsTranslateEnabled.collectAsStateWithLifecycle(initialValue = false)
    val translateLang by settingsDataStore.lyricsTranslateLang.collectAsStateWithLifecycle(initialValue = "en")
    val hideBracketsPref by settingsDataStore.lyricsHideBracketsEnabled.collectAsStateWithLifecycle(initialValue = true)
    val preferredLyricsTypePref by settingsDataStore.preferredLyricsType.collectAsStateWithLifecycle(initialValue = "LAST_USED")
    val lastSwitchedLyricsTypePref by settingsDataStore.lastSwitchedLyricsType.collectAsStateWithLifecycle(initialValue = "ROMANIZED")

    var showTranslation by remember(translateEnabledPref) { mutableStateOf(translateEnabledPref) }
    var hideBrackets by remember(hideBracketsPref) { mutableStateOf(hideBracketsPref) }
    var isTranslating by remember { mutableStateOf(false) }
    val translatedLines = remember { mutableStateListOf<String>() }
    var translatedPlain by remember { mutableStateOf<String?>(null) }

    var showOptionsMenu by remember { mutableStateOf(false) }
    var showTrackDetails by remember { mutableStateOf(false) }
    var selectedVariant by remember { mutableStateOf(LyricsViewVariant.ORIGINAL) }

    val rawSourceText = remember(lyrics) {
        (lyrics?.syncedLyrics ?: lyrics?.plainLyrics.orEmpty()).replace(STRIP_WORD_TAGS_REGEX, "")
    }
    val availableVariants = remember(rawSourceText) {
        LyricsTextVariants.availableVariants(rawSourceText)
    }
    LaunchedEffect(preferredLyricsTypePref, lastSwitchedLyricsTypePref, availableVariants) {
        val effectiveType = if (preferredLyricsTypePref == "LAST_USED") {
            lastSwitchedLyricsTypePref
        } else {
            preferredLyricsTypePref
        }
        val wantsRomanized = effectiveType == "ROMANIZED"
        selectedVariant = if (wantsRomanized && LyricsViewVariant.ROMANIZED in availableVariants) {
            LyricsViewVariant.ROMANIZED
        } else {
            LyricsViewVariant.ORIGINAL
        }
    }

    val handleSelectVariant: (LyricsViewVariant) -> Unit = { variant ->
        selectedVariant = variant
        coroutineScope.launch {
            settingsDataStore.setLastSwitchedLyricsType(variant.name)
            if (preferredLyricsTypePref != "LAST_USED") {
                settingsDataStore.setPreferredLyricsType(variant.name)
            }
        }
    }

    val parsedPairs = remember(lyrics?.syncedLyrics, hideBrackets) {
        parseLrcLines(lyrics?.syncedLyrics, hideBrackets = hideBrackets)
    }

    // Fix 1: Precompute display text and warm up Transliterator in Dispatchers.Default
    var syncedLines by remember { mutableStateOf<List<LyricLine>>(emptyList()) }
    LaunchedEffect(parsedPairs, duration, translatedLines.toList(), showTranslation, selectedVariant) {
        withContext(Dispatchers.Default) {
            LyricsTextVariants.transform("warmup", selectedVariant)
            val built = buildRichLyricLines(
                rawPairs = parsedPairs,
                durationMs = duration,
                translations = translatedLines.toList(),
                showTranslation = showTranslation,
                variant = selectedVariant
            )
            syncedLines = built
        }
    }

    val isSynced = remember(syncedLines) { syncedLines.isNotEmpty() }
    val hasWordSync = remember(syncedLines) { syncedLines.any { it.hasExplicitWordSync } }

    LaunchedEffect(lyrics?.syncedLyrics, lyrics?.plainLyrics, showTranslation, translateLang, hideBrackets) {
        if (!showTranslation) {
            translatedLines.clear()
            translatedPlain = null
            return@LaunchedEffect
        }
        val source = lyrics?.syncedLyrics ?: lyrics?.plainLyrics
        if (source.isNullOrBlank()) return@LaunchedEffect

        isTranslating = true
        try {
            withContext(Dispatchers.Default) {
                if (parsedPairs.isNotEmpty()) {
                    val originalTexts = parsedPairs.map { it.second.replace(STRIP_WORD_TAGS_REGEX, "").trim() }
                    val translations = LyricsTranslator.translateLines(originalTexts, translateLang)
                    withContext(Dispatchers.Main) {
                        translatedLines.clear()
                        translatedLines.addAll(translations)
                    }
                } else if (!lyrics?.plainLyrics.isNullOrBlank()) {
                    val plainLines = lyrics!!.plainLyrics!!.lines()
                    val translations = LyricsTranslator.translateLines(plainLines, translateLang)
                    withContext(Dispatchers.Main) {
                        translatedPlain = translations.joinToString("\n")
                    }
                }
            }
        } catch (_: Exception) {
        } finally {
            isTranslating = false
        }
    }

    // Derived active line index with binary search: invalidates readers ONLY when line changes
    val activeLineIndex by remember(syncedLines) {
        derivedStateOf {
            binarySearchActiveLine(syncedLines, positionProvider())
        }
    }

    LaunchedEffect(syncedLines, activeLineIndex) {
        hasActiveWords = isSynced && activeLineIndex in syncedLines.indices &&
            syncedLines[activeLineIndex].words.isNotEmpty()
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
            delay(3000)
            if (lastManualScrollTime == captured && !isDragged) {
                isUserScrolling = false
            }
        }
    }

    LaunchedEffect(currentTrack?.id) {
        isUserScrolling = false
    }

    // Fix 6: Safe auto-scroll with ~1/3 viewport target, animateScrollBy when close, scrollToItem when far
    LaunchedEffect(activeLineIndex, isUserScrolling) {
        if (!isUserScrolling && activeLineIndex >= 0 && syncedLines.isNotEmpty()) {
            val firstLineTimestamp = syncedLines.firstOrNull()?.timestamp ?: 0L
            val headerOffset = if (firstLineTimestamp > 4000L) 1 else 0
            val targetLazyIndex = (activeLineIndex + headerOffset).coerceAtLeast(0)

            if (listState.layoutInfo.totalItemsCount == 0) {
                kotlinx.coroutines.yield()
            }

            try {
                val layoutInfo = listState.layoutInfo
                val totalCount = layoutInfo.totalItemsCount
                if (totalCount > 0) {
                    val safeTargetIndex = targetLazyIndex.coerceIn(0, totalCount - 1)
                    val targetItem = layoutInfo.visibleItemsInfo.find { it.index == safeTargetIndex }
                    val viewportHeight = layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset

                    if (targetItem != null && viewportHeight > 0) {
                        val desiredY = (viewportHeight * 0.33f).toInt()
                        val itemCenter = targetItem.offset + (targetItem.size / 2)
                        val diff = itemCenter - desiredY
                        if (abs(diff) > 8) {
                            listState.animateScrollBy(
                                value = diff.toFloat(),
                                animationSpec = tween(durationMillis = 480, easing = CubicBezierEasing(0.2f, 0f, 0f, 1f))
                            )
                        }
                    } else {
                        val anchorIndex = (safeTargetIndex - 1).coerceAtLeast(0)
                        listState.scrollToItem(
                            index = anchorIndex,
                            scrollOffset = 0
                        )
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Throwable) {
                android.util.Log.w("LyricsScreen", "Failed auto-scrolling lyrics to index $targetLazyIndex", e)
            }
        }
    }

    val lyricsStatusBadge = remember(isLoading, lyrics, isSynced, hasWordSync) {
        val rawProvider = lyrics?.source?.removePrefix("Saved ")?.trim()?.uppercase().orEmpty()
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
            topBar()

            // Header with Now Playing, sleek timeline, and play/pause button
            LyricsNowPlayingStrip(
                currentTrack = currentTrack,
                lyricsSource = lyrics?.source,
                isSynced = isSynced,
                lyricsStatusBadge = lyricsStatusBadge,
                isTrackLiked = isTrackLiked,
                isPlaying = isPlaying,
                duration = duration,
                positionProvider = positionProvider,
                onPlayPause = {
                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    if (isPlaying) viewModel.pause() else viewModel.resume()
                },
                onSeek = { viewModel.seekTo(it) },
                showOptionsMenu = showOptionsMenu,
                availableVariants = availableVariants,
                selectedVariant = selectedVariant,
                onSelectVariant = handleSelectVariant,
                showTranslation = showTranslation,
                isTranslating = isTranslating,
                translateLang = translateLang,
                onToggleTranslation = { showTranslation = !showTranslation },
                hideBrackets = hideBrackets,
                onToggleHideBrackets = {
                    hideBrackets = !hideBrackets
                    coroutineScope.launch { settingsDataStore.setLyricsHideBracketsEnabled(hideBrackets) }
                },
                lyricsScriptFilter = lyricsScriptFilter,
                onToggleOptionsMenu = { showOptionsMenu = !showOptionsMenu },
                onOpenLyricsBrowser = onOpenLyricsBrowser,
                onOpenTrackDetails = { showTrackDetails = true },
                onToggleFavorite = { viewModel.toggleFavorite(it) }
            )

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                LyricsSettingsDropdownMenu(
                    visible = showOptionsMenu,
                    lyrics = lyrics,
                    hasWordSync = hasWordSync,
                    isSynced = isSynced,
                    availableVariants = availableVariants,
                    selectedVariant = selectedVariant,
                    onSelectVariant = handleSelectVariant,
                    hasTranslationSupport = true,
                    showTranslation = showTranslation,
                    translateLang = translateLang,
                    onToggleTranslation = { showTranslation = !showTranslation },
                    hideBrackets = hideBrackets,
                    onToggleHideBrackets = {
                        hideBrackets = !hideBrackets
                        coroutineScope.launch { settingsDataStore.setLyricsHideBracketsEnabled(hideBrackets) }
                    },
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
                    positionProvider = positionProvider,
                    duration = duration,
                    listState = listState,
                    isUserScrolling = isUserScrolling,
                    onResetUserScrolling = { isUserScrolling = false },
                    selectedVariant = selectedVariant,
                    translatedPlain = translatedPlain,
                    hideBrackets = hideBrackets,
                    currentTrack = currentTrack,
                    onOpenLyricsBrowser = onOpenLyricsBrowser,
                    onRetryLyricsFetch = { viewModel.retryLyricsFetch() },
                    onSeekTo = { viewModel.seekTo(it) }
                )
            }
        }

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
