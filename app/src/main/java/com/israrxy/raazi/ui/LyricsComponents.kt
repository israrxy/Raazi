package com.israrxy.raazi.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.israrxy.raazi.model.RepeatMode as PlaybackRepeatMode
import com.israrxy.raazi.ui.theme.Emerald400

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun SpotifyYoutubeLyricItem(
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
                transformOrigin = TransformOrigin(0f, 0.5f)
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
        }
    }
}

/**
 * Animated 3-dot musical interlude indicator ("• • •") like Spotify / Apple Music / YT Music.
 */
@Composable
internal fun MusicalInterludeDots(
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
internal fun LyricsBottomTransport(
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
