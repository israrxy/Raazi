package com.israrxy.raazi.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.israrxy.raazi.data.lyrics.LyricsTextVariants
import com.israrxy.raazi.data.lyrics.LyricsViewVariant
import com.israrxy.raazi.model.RepeatMode
import com.israrxy.raazi.ui.theme.Emerald400
import com.israrxy.raazi.ui.theme.Emerald500
import kotlin.math.abs

/**
 * Spotify-style fullscreen lyrics overlay.
 *
 * - Heavily blurred album art background with dark gradient
 * - Top bar: collapse, song/artist info, menu, source chip
 * - Center: synced lyrics with a smooth scale/glow on the active line
 * - Bottom: progress bar with seek + transport controls
 * - Swipe down to dismiss
 */
@Composable
fun LyricsScreen(
    title: String,
    artist: String,
    thumbnailUrl: String?,
    isPlaying: Boolean,
    isShuffleEnabled: Boolean,
    repeatMode: RepeatMode,
    progressMs: Long,
    durationMs: Long,
    syncedLyrics: String?,
    plainLyrics: String?,
    isLoading: Boolean,
    lyricsSourceLabel: String,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onCollapse: () -> Unit,
    onOpenMenu: () -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val lyricsMode = when {
        isLoading -> "Searching"
        !lyricsSourceLabel.isNullOrBlank() && lyricsSourceLabel.startsWith("Saved") -> "Saved"
        !syncedLyrics.isNullOrEmpty() -> "Synced"
        !plainLyrics.isNullOrEmpty() -> "Plain"
        else -> "No Lyrics"
    }
    val showSynced = !syncedLyrics.isNullOrEmpty()

    var lyricsDismissDrag by remember { mutableFloatStateOf(0f) }
    var viewVariant by remember(title + artist) { mutableStateOf(LyricsViewVariant.ORIGINAL) }
    val lyricsDismissThresholdPx = with(density) { 120.dp.toPx() }
    val lyricsDismissTranslation by animateFloatAsState(
        targetValue = if (lyricsDismissDrag > 0f) lyricsDismissDrag * 0.32f else 0f,
        animationSpec = tween(durationMillis = 180),
        label = "lyrics_dismiss_translation"
    )

    val dismissConnection = remember(showSynced) {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if (source != NestedScrollSource.UserInput) {
                    lyricsDismissDrag = 0f
                    return Offset.Zero
                }
                if (available.y > 0f) {
                    lyricsDismissDrag = (lyricsDismissDrag + available.y).coerceAtMost(lyricsDismissThresholdPx * 1.4f)
                    if (lyricsDismissDrag >= lyricsDismissThresholdPx) {
                        lyricsDismissDrag = 0f
                        onCollapse()
                    }
                    return Offset(0f, available.y)
                }
                if (consumed.y < 0f || available.y < 0f) {
                    lyricsDismissDrag = 0f
                }
                return Offset.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                lyricsDismissDrag = 0f
                return Velocity.Zero
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF030507))
    ) {
        // 1) Blurred album art background
        if (!thumbnailUrl.isNullOrBlank()) {
            AsyncImage(
                model = thumbnailUrl,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(radius = 90.dp),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFF0A1F1A), Color(0xFF030507))
                        )
                    )
            )
        }

        // 2) Strong dark gradient overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.00f to Color.Black.copy(alpha = 0.35f),
                        0.35f to Color.Black.copy(alpha = 0.55f),
                        0.75f to Color.Black.copy(alpha = 0.78f),
                        1.00f to Color.Black.copy(alpha = 0.92f)
                    )
                )
        )

        // 3) Main content
        Box(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(dismissConnection)
                .graphicsLayer { translationY = lyricsDismissTranslation }
                .statusBarsPadding()
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                LyricsTopBar(
                    title = title,
                    artist = artist,
                    lyricsMode = lyricsMode,
                    onCollapse = onCollapse,
                    onOpenMenu = onOpenMenu,
                    onVerticalDragDelta = { delta ->
                        if (delta > 0f) {
                            lyricsDismissDrag = (lyricsDismissDrag + delta)
                                .coerceAtMost(lyricsDismissThresholdPx * 1.4f)
                            if (lyricsDismissDrag >= lyricsDismissThresholdPx) {
                                lyricsDismissDrag = 0f
                                onCollapse()
                            }
                        } else {
                            lyricsDismissDrag = 0f
                        }
                    }
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    if (isLoading) {
                        LyricsLoading()
                    } else if (showSynced) {
                        SyncedLyricsList(
                            syncedLyrics = syncedLyrics,
                            progressMs = progressMs,
                            viewVariant = viewVariant,
                            onVariantChange = { viewVariant = it },
                            onSeek = onSeek
                        )
                    } else if (!plainLyrics.isNullOrEmpty()) {
                        PlainLyricsList(
                            plainLyrics = plainLyrics,
                            viewVariant = viewVariant,
                            onVariantChange = { viewVariant = it }
                        )
                    } else {
                        NoLyrics()
                    }
                }

                LyricsBottomControls(
                    isPlaying = isPlaying,
                    progressMs = progressMs,
                    durationMs = durationMs,
                    onPlayPause = onPlayPause,
                    onNext = onNext,
                    onPrevious = onPrevious,
                    onSeek = onSeek
                )
            }
        }
    }
}

@Composable
private fun LyricsTopBar(
    title: String,
    artist: String,
    lyricsMode: String,
    onCollapse: () -> Unit,
    onOpenMenu: () -> Unit,
    onVerticalDragDelta: (Float) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = { },
                    onDragCancel = { }
                ) { _, dragAmount ->
                    onVerticalDragDelta(dragAmount)
                }
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = CircleShape,
            color = Color.White.copy(alpha = 0.10f),
            modifier = Modifier
                .size(42.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onCollapse
                )
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = "Close",
                    tint = Color.White,
                    modifier = Modifier.size(26.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title.ifBlank { "Unknown Track" },
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = artist.ifBlank { "Unknown Artist" },
                color = Color.White.copy(alpha = 0.72f),
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Surface(
            shape = RoundedCornerShape(999.dp),
            color = Color.White.copy(alpha = 0.10f)
        ) {
            Text(
                text = lyricsMode.uppercase(),
                color = Emerald400,
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.sp,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Surface(
            shape = CircleShape,
            color = Color.White.copy(alpha = 0.10f),
            modifier = Modifier
                .size(42.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onOpenMenu
                )
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = "Options",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

@Composable
private fun LyricsLoading() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            CircularProgressIndicator(color = Emerald400)
            Text(
                text = "Finding lyrics",
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp
            )
            Text(
                text = "Hang tight, we're matching this song across lyric sources.",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp)
            )
        }
    }
}

@Composable
private fun NoLyrics() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(horizontal = 32.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.GraphicEq,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.65f),
                modifier = Modifier.size(64.dp)
            )
            Text(
                text = "No lyrics for this track",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                textAlign = TextAlign.Center
            )
            Text(
                text = "Tap the menu to browse other versions, switch script, or retry.",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 13.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun SyncedLyricsList(
    syncedLyrics: String,
    progressMs: Long,
    viewVariant: LyricsViewVariant,
    onVariantChange: (LyricsViewVariant) -> Unit,
    onSeek: (Long) -> Unit
) {
    val lines = remember(syncedLyrics) {
        syncedLyrics.lines().mapNotNull { line ->
            val match = Regex("\\[(\\d+):(\\d+\\.\\d+)\\](.*)").find(line)
            if (match != null) {
                val min = match.groupValues[1].toLong()
                val sec = match.groupValues[2].toDouble()
                val timeMs = (min * 60 * 1000 + sec * 1000).toLong()
                val text = match.groupValues[3].trim()
                if (text.isBlank()) null else timeMs to text
            } else null
        }
    }

    if (lines.isEmpty()) {
        PlainLyricsList(plainLyrics = syncedLyrics, viewVariant = viewVariant, onVariantChange = onVariantChange)
        return
    }

    val activeIndex by remember(progressMs, lines) {
        derivedStateOf {
            lines.indexOfLast { it.first <= progressMs }.coerceAtLeast(0)
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val centerPadding = (maxHeight / 2 - 32.dp).coerceAtLeast(80.dp)
        val listState = rememberLazyListState()

        LaunchedEffect(activeIndex) {
            if (lines.isNotEmpty()) {
                listState.animateScrollToItem(
                    index = activeIndex.coerceIn(0, lines.lastIndex),
                    scrollOffset = 0
                )
            }
        }

        val availableVariants = remember(syncedLyrics) {
            LyricsTextVariants.availableVariants(syncedLyrics)
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                horizontal = 24.dp,
                vertical = centerPadding
            )
        ) {
            if (availableVariants.size > 1) {
                item("variant_chip") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        availableVariants.forEach { v ->
                            val selected = viewVariant == v
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = if (selected) Emerald500.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.08f),
                                modifier = Modifier
                                    .padding(horizontal = 4.dp)
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) { onVariantChange(v) }
                            ) {
                                Text(
                                    text = v.label,
                                    color = if (selected) Color.White else Color.White.copy(alpha = 0.78f),
                                    fontSize = 12.sp,
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }
            }

            itemsIndexed(items = lines) { index, (time, text) ->
                val distance = abs(activeIndex - index)
                val isCurrent = index == activeIndex
                val targetAlpha = when {
                    isCurrent -> 1f
                    distance == 1 -> 0.55f
                    distance == 2 -> 0.35f
                    else -> 0.18f
                }
                val targetScale = when {
                    isCurrent -> 1f
                    distance == 1 -> 0.94f
                    else -> 0.88f
                }
                val animatedAlpha by animateFloatAsState(
                    targetValue = targetAlpha,
                    animationSpec = tween(durationMillis = 320),
                    label = "lyrics_alpha"
                )
                val animatedScale by animateFloatAsState(
                    targetValue = targetScale,
                    animationSpec = tween(durationMillis = 320),
                    label = "lyrics_scale"
                )
                val animatedColor by animateColorAsState(
                    targetValue = if (isCurrent) Color.White else Color.White.copy(alpha = 0.95f),
                    animationSpec = tween(durationMillis = 320),
                    label = "lyrics_color"
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            scaleX = animatedScale
                            scaleY = animatedScale
                        }
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onSeek(time) }
                        .padding(vertical = 12.dp)
                ) {
                    Text(
                        text = LyricsTextVariants.transform(text, viewVariant),
                        color = animatedColor.copy(alpha = animatedAlpha),
                        fontSize = if (isCurrent) 26.sp else 21.sp,
                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        lineHeight = if (isCurrent) 34.sp else 30.sp,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun PlainLyricsList(
    plainLyrics: String,
    viewVariant: LyricsViewVariant,
    onVariantChange: (LyricsViewVariant) -> Unit
) {
    val availableVariants = remember(plainLyrics) {
        LyricsTextVariants.availableVariants(plainLyrics)
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp, vertical = 24.dp)
    ) {
        if (availableVariants.size > 1) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                availableVariants.forEach { v ->
                    val selected = viewVariant == v
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = if (selected) Emerald500.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.08f),
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { onVariantChange(v) }
                    ) {
                        Text(
                            text = v.label,
                            color = if (selected) Color.White else Color.White.copy(alpha = 0.78f),
                            fontSize = 12.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }
        Text(
            text = LyricsTextVariants.transform(plainLyrics, viewVariant),
            color = Color.White.copy(alpha = 0.95f),
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            lineHeight = 30.sp,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(80.dp))
    }
}

@Composable
private fun LyricsBottomControls(
    isPlaying: Boolean,
    progressMs: Long,
    durationMs: Long,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit
) {
    var isDragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    val animatedProgress by animateFloatAsState(
        targetValue = if (isDragging) dragValue else progressMs.toFloat(),
        animationSpec = tween(durationMillis = 80),
        label = "lyrics_progress"
    )

    val safeDuration = durationMs.coerceAtLeast(0L)
    val displayProgress = if (isDragging) {
        (dragValue * safeDuration / 1000f).toLong()
    } else {
        progressMs
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(top = 8.dp, bottom = 20.dp)
    ) {
        Slider(
            value = if (safeDuration > 0) animatedProgress / safeDuration else 0f,
            onValueChange = {
                isDragging = true
                dragValue = (it * 1000f).coerceIn(0f, 1000f)
            },
            onValueChangeFinished = {
                isDragging = false
                if (safeDuration > 0) {
                    onSeek(((dragValue / 1000f) * safeDuration).toLong())
                }
            },
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Color.White,
                inactiveTrackColor = Color.White.copy(alpha = 0.22f)
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = formatLyricsTime(displayProgress),
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 12.sp
            )
            Text(
                text = formatLyricsTime(safeDuration),
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 12.sp
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = Color.White.copy(alpha = 0.10f),
                modifier = Modifier
                    .size(52.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onPrevious
                    )
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = "Previous",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(20.dp))

            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onPlayPause
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = Color.Black,
                    modifier = Modifier.size(36.dp)
                )
            }

            Spacer(modifier = Modifier.width(20.dp))

            Surface(
                shape = CircleShape,
                color = Color.White.copy(alpha = 0.10f),
                modifier = Modifier
                    .size(52.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onNext
                    )
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Next",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }
    }
}

private fun formatLyricsTime(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%d:%02d", minutes, seconds)
}
