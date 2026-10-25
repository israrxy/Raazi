package com.israrxy.raazi.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import android.view.HapticFeedbackConstants
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.israrxy.raazi.data.local.SettingsDataStore
import com.israrxy.raazi.data.lyrics.LyricsScriptFilter
import com.israrxy.raazi.data.lyrics.LyricsTextVariants
import com.israrxy.raazi.data.lyrics.LyricsViewVariant
import com.israrxy.raazi.model.RepeatMode as PlaybackRepeatMode
import com.israrxy.raazi.service.LyricsTranslator
import com.israrxy.raazi.ui.theme.*
import com.israrxy.raazi.utils.BlurTransformation
import com.israrxy.raazi.utils.ThumbnailUtils
import com.israrxy.raazi.viewmodel.MusicPlayerViewModel
import kotlinx.coroutines.delay
import kotlin.math.abs

data class LyricLine(
    val timestamp: Long,
    val text: String,
    val translation: String? = null
)

/**
 * Robust LRC parser supporting [mm:ss], [mm:ss.x], [mm:ss.xx], [mm:ss.xxx]
 * and multiple timestamps per line.
 */
fun parseLrcLines(syncedLyrics: String?): List<Pair<Long, String>> {
    if (syncedLyrics.isNullOrBlank()) return emptyList()
    val timestampRegex = Regex("\\[(\\d+):(\\d+(?:\\.\\d+)?)\\]")
    val result = mutableListOf<Pair<Long, String>>()

    syncedLyrics.lines().forEach { rawLine ->
        val matches = timestampRegex.findAll(rawLine).toList()
        if (matches.isNotEmpty()) {
            val text = rawLine.substring(matches.last().range.last + 1).trim()
            if (text.isNotEmpty()) {
                matches.forEach { match ->
                    val min = match.groupValues[1].toLongOrNull() ?: 0L
                    val sec = match.groupValues[2].toDoubleOrNull() ?: 0.0
                    val timeMs = (min * 60 * 1000 + sec * 1000).toLong()
                    result.add(timeMs to text)
                }
            }
        }
    }
    return result.sortedBy { it.first }
}

/**
 * Unified Spotify + YouTube Music style Lyrics Screen.
 * Supports smooth auto-scrolling, manual scroll with "Re-sync" pill, timestamp tap-to-seek,
 * musical interlude pulsing dots, Romanization/Script switching, AI Translation, and line sharing.
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
    val isTrackLiked = remember(currentTrack?.id, favoriteTracks) {
        currentTrack != null && favoriteTracks.any { it.id == currentTrack.id }
    }

    // Settings for translation
    val settingsDataStore = remember { SettingsDataStore(context) }
    val geminiApiKey by settingsDataStore.geminiApiKey.collectAsStateWithLifecycle(initialValue = "")
    val translateEnabled by settingsDataStore.lyricsTranslateEnabled.collectAsStateWithLifecycle(initialValue = false)
    val translateLang by settingsDataStore.lyricsTranslateLang.collectAsStateWithLifecycle(initialValue = "en")

    var showTranslation by remember { mutableStateOf(false) }
    var isTranslating by remember { mutableStateOf(false) }
    var translationError by remember { mutableStateOf<String?>(null) }
    val translatedLines = remember { mutableStateListOf<String>() }
    var translatedPlain by remember { mutableStateOf<String?>(null) }

    var showOptionsMenu by remember { mutableStateOf(false) }
    var selectedVariant by remember(currentTrack?.id) { mutableStateOf(LyricsViewVariant.ORIGINAL) }

    val rawSourceText = remember(lyrics) {
        lyrics?.syncedLyrics ?: lyrics?.plainLyrics.orEmpty()
    }
    val availableVariants = remember(rawSourceText) {
        LyricsTextVariants.availableVariants(rawSourceText)
    }
    LaunchedEffect(availableVariants) {
        if (selectedVariant !in availableVariants) {
            selectedVariant = LyricsViewVariant.ORIGINAL
        }
    }

    // Parse synced lyrics
    val parsedPairs = remember(lyrics?.syncedLyrics) {
        parseLrcLines(lyrics?.syncedLyrics)
    }

    val syncedLines = remember(parsedPairs, translatedLines.toList(), showTranslation) {
        parsedPairs.mapIndexed { idx, (timeMs, text) ->
            LyricLine(
                timestamp = timeMs,
                text = text,
                translation = if (showTranslation && idx < translatedLines.size) translatedLines[idx] else null
            )
        }
    }

    val isSynced = remember(syncedLines) { syncedLines.isNotEmpty() }

    // Trigger translation when toggled
    LaunchedEffect(lyrics?.syncedLyrics, lyrics?.plainLyrics, showTranslation, translateLang, geminiApiKey) {
        if (!showTranslation || geminiApiKey.isNullOrBlank()) {
            translatedLines.clear()
            translatedPlain = null
            translationError = null
            return@LaunchedEffect
        }
        val source = lyrics?.syncedLyrics ?: lyrics?.plainLyrics
        if (source.isNullOrBlank()) return@LaunchedEffect

        isTranslating = true
        translationError = null
        try {
            if (parsedPairs.isNotEmpty()) {
                val originalTexts = parsedPairs.map { it.second }
                val translations = LyricsTranslator.translateLines(geminiApiKey!!, originalTexts, translateLang)
                translatedLines.clear()
                translatedLines.addAll(translations)
            } else if (!lyrics?.plainLyrics.isNullOrBlank()) {
                val translations = LyricsTranslator.translateLines(geminiApiKey!!, listOf(lyrics!!.plainLyrics!!), translateLang)
                translatedPlain = translations.firstOrNull()
            }
        } catch (e: Exception) {
            translationError = "Translation failed"
        } finally {
            isTranslating = false
        }
    }

    // Active line index (add slight 120ms lookahead so lyric highlights right on vocal onset)
    val activeLineIndex = remember(currentPosition, syncedLines, isSynced) {
        if (!isSynced || syncedLines.isEmpty()) -1
        else syncedLines.indexOfLast { it.timestamp <= currentPosition + 120L }
    }

    val listState = rememberLazyListState()
    val isDragged by listState.interactionSource.collectIsDraggedAsState()
    var isUserScrolling by remember { mutableStateOf(false) }
    var lastManualScrollTime by remember { mutableLongStateOf(0L) }

    // Detect ONLY manual user finger drags (never programmatic auto-scrolls) to pause auto-scroll & show "Sync" pill
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

    // Reset manual scroll lock when track changes
    LaunchedEffect(currentTrack?.id) {
        isUserScrolling = false
    }

    // Smoothly center-top the active lyric line when not manually scrolling
    LaunchedEffect(activeLineIndex, isUserScrolling, syncedLines.size) {
        if (!isUserScrolling && activeLineIndex >= 0 && syncedLines.isNotEmpty()) {
            val firstLineTimestamp = syncedLines.firstOrNull()?.timestamp ?: 0L
            val headerOffset = if (firstLineTimestamp > 4000L) 1 else 0
            val targetLazyIndex = (activeLineIndex + headerOffset).coerceAtLeast(0)

            // Wait a frame if layout hasn't measured yet
            if (listState.layoutInfo.totalItemsCount == 0) {
                kotlinx.coroutines.yield()
            }

            val layoutInfo = listState.layoutInfo
            val targetItem = layoutInfo.visibleItemsInfo.find { it.index == targetLazyIndex }
            val viewportHeight = layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset

            if (targetItem != null && viewportHeight > 0) {
                // Anchor active line ~28% down from top of viewport (Spotify / YT Music feel)
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

    val lyricsStatusBadge = remember(isLoading, lyrics, isSynced) {
        when {
            isLoading -> "SEARCHING"
            lyrics?.source?.startsWith("Saved") == true -> "SAVED"
            isSynced -> "SYNCED"
            !lyrics?.plainLyrics.isNullOrEmpty() -> "PLAIN"
            else -> "NO LYRICS"
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // 1. Dynamic Blurred Artwork & Ambient Gradient Background
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

        // Rich multi-stop dark scrim so lyrics pop with high contrast
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

        // Tap-outside overlay when options menu is open
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
            // 2. Top Navigation Bar (YouTube Music Audio/Lyrics Pill + Actions)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Back to player",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }

                // Center Audio / Lyrics Pill Switcher
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    val pillSegmentWidth = 56.dp
                    Box(
                        modifier = Modifier
                            .height(38.dp)
                            .width(pillSegmentWidth * 2)
                            .clip(RoundedCornerShape(19.dp))
                            .background(Color.White.copy(alpha = 0.12f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(pillSegmentWidth)
                                .offset(x = pillSegmentWidth)
                                .clip(RoundedCornerShape(19.dp))
                                .background(Color.White)
                        )
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) { onClose() },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Headphones,
                                    contentDescription = "Song View",
                                    tint = Color.White.copy(alpha = 0.72f),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Subject,
                                    contentDescription = "Lyrics View",
                                    tint = Color.Black,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                // Search / Match Lyrics Button
                IconButton(
                    onClick = {
                        showOptionsMenu = false
                        onOpenLyricsBrowser()
                    },
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ManageSearch,
                        contentDescription = "Search or change lyrics",
                        tint = Color.White.copy(alpha = 0.9f),
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Lyrics Options Menu Button
                IconButton(
                    onClick = { showOptionsMenu = !showOptionsMenu },
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = "Lyrics options",
                        tint = if (showOptionsMenu || selectedVariant != LyricsViewVariant.ORIGINAL || showTranslation) Emerald400 else Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            // 3. Compact Now Playing Strip (Album Art + Track Info + Favorite + Quick Chips)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                        .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(18.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AsyncImage(
                        model = ThumbnailUtils.getHighQualityThumbnail(currentTrack?.thumbnailUrl),
                        contentDescription = currentTrack?.title,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Zinc800),
                        contentScale = ContentScale.Crop
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = currentTrack?.title ?: "No Track Playing",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = currentTrack?.artist ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.65f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Status Pill
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = if (isSynced) Emerald500.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.1f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            if (isSynced) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(Emerald400)
                                )
                            }
                            Text(
                                text = lyricsStatusBadge,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isSynced) Emerald400 else Color.White.copy(alpha = 0.7f),
                                fontSize = 10.sp
                            )
                        }
                    }

                    if (currentTrack != null) {
                        IconButton(
                            onClick = { viewModel.toggleFavorite(currentTrack) },
                            modifier = Modifier.size(38.dp)
                        ) {
                            Icon(
                                imageVector = if (isTrackLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                contentDescription = "Favorite",
                                tint = if (isTrackLiked) Emerald400 else Color.White.copy(alpha = 0.75f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                // Quick Interactive Customization Pills (Romanized / Translate / Sync Match)
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Variant pills (Original / Romanized)
                    if (availableVariants.size > 1) {
                        availableVariants.forEach { variant ->
                            val selected = selectedVariant == variant
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = if (selected) Color.White else Color.White.copy(alpha = 0.1f),
                                modifier = Modifier.clickable { selectedVariant = variant }
                            ) {
                                Text(
                                    text = variant.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (selected) Color.Black else Color.White.copy(alpha = 0.85f),
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }

                    // Translation pill (if Gemini key configured)
                    if (!geminiApiKey.isNullOrBlank()) {
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = if (showTranslation) Emerald500.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.1f),
                            modifier = Modifier
                                .border(
                                    width = 1.dp,
                                    color = if (showTranslation) Emerald400.copy(alpha = 0.6f) else Color.Transparent,
                                    shape = RoundedCornerShape(999.dp)
                                )
                                .clickable { showTranslation = !showTranslation }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                if (isTranslating) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(12.dp),
                                        strokeWidth = 1.5.dp,
                                        color = Emerald400
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Translate,
                                        contentDescription = null,
                                        tint = if (showTranslation) Emerald400 else Color.White.copy(alpha = 0.8f),
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                                Text(
                                    text = if (showTranslation) "Translated (${translateLang.uppercase()})" else "Translate",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (showTranslation) Emerald400 else Color.White.copy(alpha = 0.85f)
                                )
                            }
                        }
                    }

                    // Script filter quick indicator
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = Color.White.copy(alpha = 0.08f),
                        modifier = Modifier.clickable { showOptionsMenu = !showOptionsMenu }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Language,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.7f),
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = "Script: ${lyricsScriptFilter.label}",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.8f)
                            )
                        }
                    }

                    // Wrong lyrics? Browse alternatives pill
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = Color.White.copy(alpha = 0.08f),
                        modifier = Modifier.clickable { onOpenLyricsBrowser() }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SwapHoriz,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.7f),
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "Switch Match",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
            }

            // 4. Main Lyrics Content Area + Floating Options Sheet
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                // Dropdown Options Menu
                androidx.compose.animation.AnimatedVisibility(
                    visible = showOptionsMenu,
                    enter = slideInVertically(initialOffsetY = { -it / 3 }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { -it / 3 }) + fadeOut(),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                        .zIndex(10f)
                ) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        color = Color(0xFF121820).copy(alpha = 0.98f),
                        shadowElevation = 20.dp,
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "Lyrics Settings & Source",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )

                            // Browse LrcLib matches
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        showOptionsMenu = false
                                        onOpenLyricsBrowser()
                                    },
                                shape = RoundedCornerShape(16.dp),
                                color = Color.White.copy(alpha = 0.06f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Search, contentDescription = null, tint = Emerald400)
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Search & select synced lyrics",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color.White
                                        )
                                        Text(
                                            text = "Browse alternative LrcLib timestamps for this track",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color.White.copy(alpha = 0.6f)
                                        )
                                    }
                                }
                            }

                            // Retry auto search
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        showOptionsMenu = false
                                        viewModel.retryLyricsFetch()
                                    },
                                shape = RoundedCornerShape(16.dp),
                                color = Color.White.copy(alpha = 0.06f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = null, tint = Color.White)
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Retry automatic match",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color.White
                                        )
                                        Text(
                                            text = "Re-fetch lyrics from online providers",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color.White.copy(alpha = 0.6f)
                                        )
                                    }
                                }
                            }

                            if (lyrics?.source?.startsWith("Saved") == true) {
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            showOptionsMenu = false
                                            viewModel.clearSavedLyricsSelection()
                                        },
                                    shape = RoundedCornerShape(16.dp),
                                    color = Color.White.copy(alpha = 0.06f)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.AutoFixHigh, contentDescription = null, tint = Color.White)
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Reset to automatic lyrics",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                color = Color.White
                                            )
                                            Text(
                                                text = "Clear pinned manual lyrics for this song",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = Color.White.copy(alpha = 0.6f)
                                            )
                                        }
                                    }
                                }
                            }

                            Text(
                                text = "Preferred Script",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White.copy(alpha = 0.6f)
                            )

                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                LyricsScriptFilter.visibleFilters.forEach { filter ->
                                    FilterChip(
                                        selected = lyricsScriptFilter == filter,
                                        onClick = {
                                            viewModel.setLyricsScriptFilter(filter)
                                            showOptionsMenu = false
                                        },
                                        label = { Text(filter.label) },
                                        border = null,
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = Emerald500.copy(alpha = 0.25f),
                                            selectedLabelColor = Emerald400,
                                            containerColor = Color.White.copy(alpha = 0.06f),
                                            labelColor = Color.White.copy(alpha = 0.85f)
                                        )
                                    )
                                }
                            }
                        }
                    }
                }

                // Main Viewport States
                when {
                    isLoading -> {
                        LyricsLoadingPlaceholder()
                    }

                    lyrics == null || (lyrics?.syncedLyrics.isNullOrBlank() && lyrics?.plainLyrics.isNullOrBlank()) -> {
                        LyricsEmptyPlaceholder(
                            onSearchManually = onOpenLyricsBrowser,
                            onRetry = { viewModel.retryLyricsFetch() }
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
                                // Musical Intro Indicator ("• • •") before first vocal line
                                if (firstLineTimestamp > 4000L) {
                                    item(key = "intro_interlude") {
                                        androidx.compose.animation.AnimatedVisibility(
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
                                    // Show interlude dots between lines if there's a long instrumental break (> 7s)
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
                                        isCurrent = isCurrent,
                                        isPassed = isPassed,
                                        distance = distance,
                                        showTimestampBadge = isUserScrolling,
                                        onClick = {
                                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                            isUserScrolling = false
                                            viewModel.seekTo(line.timestamp)
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

                                // Footer attribution
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
                                            text = "Lyrics provided by ${lyrics?.source ?: "LrcLib"} • Long-press any line to share",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.White.copy(alpha = 0.38f)
                                        )
                                    }
                                }
                            }

                            // Top & Bottom Edge Fade Vignettes
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(44.dp)
                                    .align(Alignment.TopCenter)
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(
                                                Color.Black.copy(alpha = 0.45f),
                                                Color.Transparent
                                            )
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
                                            colors = listOf(
                                                Color.Transparent,
                                                Color.Black.copy(alpha = 0.55f)
                                            )
                                        )
                                    )
                            )

                            // Spotify / YT Music Floating "Sync" Button when user scrolls manually
                            androidx.compose.animation.AnimatedVisibility(
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
                                        isUserScrolling = false
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
                        // Unsynced Plain Lyrics Mode (Spotify editorial style)
                        val displayedPlain = remember(lyrics?.plainLyrics, selectedVariant) {
                            LyricsTextVariants.transform(lyrics?.plainLyrics.orEmpty(), selectedVariant)
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
                                        text = translatedPlain!!,
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            lineHeight = 28.sp
                                        ),
                                        color = Emerald400.copy(alpha = 0.9f),
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

            // 5. Bottom Compact Transport & Scrubbing Controls
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SpotifyYoutubeLyricItem(
    text: String,
    translation: String?,
    timestampMs: Long,
    isCurrent: Boolean,
    isPassed: Boolean,
    distance: Int,
    showTimestampBadge: Boolean,
    onClick: () -> Unit,
    onShareLine: () -> Unit
) {
    val targetAlpha = when {
        isCurrent -> 1f
        showTimestampBadge -> 0.78f
        isPassed -> 0.28f
        distance == 1 -> 0.64f
        distance == 2 -> 0.46f
        else -> 0.30f
    }

    val animatedAlpha by animateFloatAsState(
        targetValue = targetAlpha,
        animationSpec = tween(durationMillis = 300),
        label = "lyric_alpha"
    )

    val targetScale = when {
        isCurrent -> 1.02f
        distance == 1 -> 0.98f
        else -> 0.95f
    }

    val animatedScale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "lyric_scale"
    )

    val containerAlpha by animateFloatAsState(
        targetValue = if (isCurrent) 0.10f else 0f,
        animationSpec = tween(260),
        label = "lyric_bg_alpha"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = animatedScale
                scaleY = animatedScale
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f)
            }
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = containerAlpha))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onShareLine
            )
            .padding(
                horizontal = if (isCurrent) 14.dp else 8.dp,
                vertical = if (isCurrent) 12.dp else 8.dp
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Active vertical accent bar (Spotify / YT Music signature touch)
        AnimatedVisibility(
            visible = isCurrent,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut()
        ) {
            Box(
                modifier = Modifier
                    .padding(end = 12.dp)
                    .width(4.dp)
                    .height(28.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(Emerald400)
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = text.ifBlank { "♪" },
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontSize = if (isCurrent) 26.sp else 22.sp,
                    fontWeight = if (isCurrent) FontWeight.ExtraBold else FontWeight.Bold,
                    lineHeight = if (isCurrent) 34.sp else 30.sp,
                    letterSpacing = (-0.3).sp
                ),
                color = Color.White.copy(alpha = animatedAlpha),
                textAlign = TextAlign.Start
            )

            if (!translation.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = translation,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = if (isCurrent) 16.sp else 14.sp,
                        fontWeight = FontWeight.Medium,
                        lineHeight = 22.sp
                    ),
                    color = if (isCurrent) Emerald400 else Color.White.copy(alpha = (animatedAlpha * 0.75f).coerceIn(0.15f, 0.85f)),
                    textAlign = TextAlign.Start
                )
            }
        }

        // Timestamp pill during manual scroll OR subtle share button on active line
        if (showTimestampBadge) {
            Spacer(modifier = Modifier.width(8.dp))
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = Color.White.copy(alpha = 0.14f)
            ) {
                Text(
                    text = formatLyricsTimestamp(timestampMs),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White.copy(alpha = 0.9f),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        } else if (isCurrent) {
            IconButton(
                onClick = onShareLine,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = "Share lyric line",
                    tint = Color.White.copy(alpha = 0.45f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

/**
 * Animated 3-dot musical interlude indicator ("• • •") like Spotify / Apple Music / YT Music.
 */
@Composable
private fun MusicalInterludeDots(
    progress: Float,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "interlude_pulse")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.18f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot_pulse"
    )

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Color.White.copy(alpha = 0.10f))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(3) { dotIndex ->
            val threshold = (dotIndex + 1) / 3f
            val isLit = progress >= (dotIndex / 3f)
            val dotAlpha = when {
                progress >= threshold -> 1f
                isLit -> 0.5f + ((progress - (dotIndex / 3f)) * 1.5f).coerceIn(0f, 0.5f)
                else -> 0.28f
            }
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .graphicsLayer {
                        val s = if (isLit) pulse else 0.9f
                        scaleX = s
                        scaleY = s
                    }
                    .clip(CircleShape)
                    .background(if (isLit) Emerald400.copy(alpha = dotAlpha) else Color.White.copy(alpha = dotAlpha))
            )
        }
    }
}

@Composable
private fun LyricsBottomTransport(
    currentPosition: Long,
    duration: Long,
    isPlaying: Boolean,
    shuffleEnabled: Boolean,
    repeatMode: PlaybackRepeatMode,
    onSeek: (Long) -> Unit,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToggleShuffle: () -> Unit,
    onToggleRepeat: () -> Unit
) {
    var isDraggingSlider by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }

    val sliderValue = if (isDraggingSlider) {
        dragFraction
    } else if (duration > 0) {
        (currentPosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
    } else 0f

    val displayedPosition = if (isDraggingSlider && duration > 0) {
        (dragFraction * duration).toLong()
    } else currentPosition

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp)
    ) {
        Slider(
            value = sliderValue,
            onValueChange = {
                isDraggingSlider = true
                dragFraction = it
            },
            onValueChangeFinished = {
                if (duration > 0) {
                    onSeek((dragFraction * duration).toLong())
                }
                isDraggingSlider = false
            },
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Emerald400,
                inactiveTrackColor = Color.White.copy(alpha = 0.18f)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(22.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = formatLyricsTimestamp(displayedPosition),
                style = MaterialTheme.typography.labelSmall,
                color = if (isDraggingSlider) Emerald400 else Color.White.copy(alpha = 0.55f)
            )
            Text(
                text = formatLyricsTimestamp(duration),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.55f)
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onToggleShuffle) {
                Icon(
                    imageVector = Icons.Default.Shuffle,
                    contentDescription = "Shuffle",
                    tint = if (shuffleEnabled) Emerald400 else Color.White.copy(alpha = 0.55f),
                    modifier = Modifier.size(22.dp)
                )
            }

            IconButton(onClick = onPrevious) {
                Icon(
                    imageVector = Icons.Default.SkipPrevious,
                    contentDescription = "Previous",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }

            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .clickable(onClick = onPlayPause),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = Color.Black,
                    modifier = Modifier.size(28.dp)
                )
            }

            IconButton(onClick = onNext) {
                Icon(
                    imageVector = Icons.Default.SkipNext,
                    contentDescription = "Next",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }

            IconButton(onClick = onToggleRepeat) {
                val repeatIcon = when (repeatMode) {
                    PlaybackRepeatMode.ONE -> Icons.Default.RepeatOne
                    else -> Icons.Default.Repeat
                }
                val isRepeatActive = repeatMode != PlaybackRepeatMode.OFF
                Icon(
                    imageVector = repeatIcon,
                    contentDescription = "Repeat",
                    tint = if (isRepeatActive) Emerald400 else Color.White.copy(alpha = 0.55f),
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

@Composable
private fun LyricsLoadingPlaceholder() {
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
                color = Emerald400,
                strokeWidth = 2.dp,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = "Syncing lyrics...",
                style = MaterialTheme.typography.labelLarge,
                color = Emerald400
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
private fun LyricsEmptyPlaceholder(
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
                text = "Couldn't find synced lyrics",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center
            )

            Text(
                text = "Search LrcLib manually to pick a matching version or retry automatic lookup.",
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
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.25f))
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
            // Card Header
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

            // Card Body: Live 3-line Spotify preview
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

private fun shareOrCopyLyricLine(
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

private fun formatLyricsTimestamp(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0L)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%d:%02d", minutes, seconds)
}
