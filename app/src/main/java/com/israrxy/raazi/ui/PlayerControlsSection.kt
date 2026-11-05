package com.israrxy.raazi.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.israrxy.raazi.model.MusicItem
import com.israrxy.raazi.model.PlaybackMediaMode
import com.israrxy.raazi.model.PlaybackState
import com.israrxy.raazi.model.RepeatMode
import com.israrxy.raazi.ui.player.PlayerTransition
import com.israrxy.raazi.ui.player.easeOutCubic
import com.israrxy.raazi.ui.player.flyTitle
import com.israrxy.raazi.ui.playlist.rememberPlaylistDominantColor
import com.israrxy.raazi.ui.theme.Emerald500
import com.israrxy.raazi.ui.theme.Zinc500
import com.israrxy.raazi.viewmodel.MusicPlayerViewModel

@Composable
private fun rememberPressScale(interactionSource: MutableInteractionSource): State<Float> {
    val isPressed by interactionSource.collectIsPressedAsState()
    return animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "pressScale"
    )
}

@Composable
internal fun PlayerControlsAndActionsSection(
    viewModel: MusicPlayerViewModel,
    playbackState: PlaybackState,
    favoriteTracks: List<MusicItem>,
    progress: Long,
    duration: Long,
    isDraggingSlider: Boolean,
    dragFraction: Float,
    onSliderChange: (Float) -> Unit,
    onSliderFinished: () -> Unit,
    hasQueueOrRelated: Boolean,
    onShowQueueSheet: () -> Unit,
    onShowSleepTimer: () -> Unit = {},
    sleepTimerActive: Boolean = false,
    transition: PlayerTransition? = null,
    cardTop: () -> Float = { 0f }
) {
    val context = LocalContext.current
    val currentTrack = playbackState.currentTrack
    val songAccentColor = rememberPlaylistDominantColor(
        coverUrl = currentTrack?.thumbnailUrl.orEmpty(),
        fallbackColor = MaterialTheme.colorScheme.primary
    )
    val isPlaying = playbackState.isPlaying

    val titleRest = remember { floatArrayOf(Float.NaN, Float.NaN) }
    fun updateTitleRest(coords: LayoutCoordinates) {
        val card = transition?.cardCoordinates
        if (card != null && card.isAttached && coords.isAttached) {
            val local = card.localPositionOf(coords, Offset.Zero)
            titleRest[0] = local.x; titleRest[1] = local.y
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        AnimatedContent(
            targetState = currentTrack,
            transitionSpec = {
                (fadeIn(animationSpec = tween(300)) + slideInVertically { height -> height / 4 })
                    .togetherWith(fadeOut(animationSpec = tween(300)) + slideOutVertically { height -> -height / 4 })
            },
            label = "SongInfoTransition"
        ) { track ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .onPlaced { if (it.isAttached) updateTitleRest(it) }
                        .onGloballyPositioned { updateTitleRest(it) }
                        .graphicsLayer {
                            if (transition != null) {
                                flyTitle(
                                    from = transition.origin?.title,
                                    playerOrigin = transition.origin?.player,
                                    restX = titleRest[0],
                                    restY = titleRest[1],
                                    progress = transition.expansion.value,
                                    cardTop = cardTop()
                                )
                            }
                        },
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(
                        text = track?.title ?: "Not Playing",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = track?.artist ?: "Unknown Artist",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                val isLiked = track?.let { t -> favoriteTracks.any { it.id == t.id } } == true

                IconButton(
                    onClick = { track?.let { viewModel.toggleFavorite(it) } },
                    modifier = Modifier.graphicsLayer {
                        if (transition != null) {
                            alpha = easeOutCubic(transition.stage(0.28f, 1f))
                        }
                    }
                ) {
                    Icon(
                        imageVector = if (isLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = if (isLiked) "Unlike" else "Like",
                        tint = if (isLiked) Color.Red else Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                if (transition != null) {
                    val shown = easeOutCubic(transition.stage(0.25f, 1f))
                    alpha = shown
                    translationY = 16.dp.toPx() * (1f - shown)
                }
            }
    ) {
        Spacer(modifier = Modifier.height(16.dp))

    Column(modifier = Modifier.fillMaxWidth()) {
        val sliderFraction = when {
            isDraggingSlider -> dragFraction
            duration > 0L -> (progress.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
            else -> 0f
        }
        val sliderInteractionSource = remember { MutableInteractionSource() }
        Slider(
            value = sliderFraction,
            onValueChange = onSliderChange,
            onValueChangeFinished = onSliderFinished,
            interactionSource = sliderInteractionSource,
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = songAccentColor,
                inactiveTrackColor = Color.White.copy(alpha = 0.2f)
            ),
            thumb = {
                val thumbSize by animateDpAsState(
                    targetValue = if (isDraggingSlider) 16.dp else 10.dp,
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                    label = "thumbSize"
                )
                Box(
                    modifier = Modifier
                        .size(thumbSize)
                        .shadow(4.dp, CircleShape)
                        .clip(CircleShape)
                        .background(Color.White)
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(20.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = formatPlayerTime(progress),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 12.sp,
                    fontFeatureSettings = "tnum"
                ),
                color = Zinc500
            )
            Text(
                text = formatPlayerTime(duration),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 12.sp,
                    fontFeatureSettings = "tnum"
                ),
                color = Zinc500
            )
        }
    }

    val currentFormat by viewModel.currentFormat.collectAsStateWithLifecycle()
    val streamQuality by viewModel.streamQuality.collectAsStateWithLifecycle()
    val codecLabel = remember(currentFormat, streamQuality, playbackState.videoQuality, playbackState.mediaMode) {
        when {
            streamQuality != null && streamQuality!!.contains("FLAC") -> streamQuality!!
            currentFormat != null -> {
                val f = currentFormat!!
                val codec = f.codecs.uppercase().replace("MP4A.40.2", "AAC").replace("MP4A.40.5", "AAC-HE")
                val bitrate = "${f.bitrate / 1000}kbps"
                val sampleRate = if (f.sampleRate != null) " · ${f.sampleRate / 1000}kHz" else ""
                val videoInfo = if (playbackState.mediaMode == PlaybackMediaMode.VIDEO) " · ${playbackState.videoQuality.label}" else ""
                "$codec · $bitrate$sampleRate$videoInfo"
            }
            streamQuality != null -> streamQuality!!
            playbackState.mediaMode == PlaybackMediaMode.VIDEO -> playbackState.videoQuality.label
            else -> null
        }
    }
    val isFlacLabel = codecLabel?.contains("FLAC") == true
    if (codecLabel != null) {
        Text(
            text = codecLabel,
            style = MaterialTheme.typography.labelSmall,
            color = if (isFlacLabel) Color(0xFFE0B0FF).copy(alpha = 0.8f) else Color.White.copy(alpha = 0.35f),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp),
            textAlign = TextAlign.Center,
            letterSpacing = 0.8.sp
        )
    }

    Spacer(modifier = Modifier.height(24.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val shuffleEnabled = playbackState.isShuffleEnabled
        val shuffleInteraction = remember { MutableInteractionSource() }
        val shuffleScale by rememberPressScale(shuffleInteraction)
        Box(
            modifier = Modifier
                .weight(1f)
                .height(72.dp),
            contentAlignment = Alignment.Center
        ) {
            IconButton(
                onClick = { viewModel.toggleShuffle() },
                interactionSource = shuffleInteraction,
                modifier = Modifier
                    .size(48.dp)
                    .graphicsLayer {
                        scaleX = shuffleScale
                        scaleY = shuffleScale
                    }
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Shuffle,
                        contentDescription = "Shuffle",
                        tint = if (shuffleEnabled) songAccentColor else Color.White.copy(alpha = 0.78f),
                        modifier = Modifier.size(24.dp)
                    )
                    if (shuffleEnabled) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Box(
                            modifier = Modifier
                                .size(4.dp)
                                .clip(CircleShape)
                                .background(songAccentColor)
                        )
                    }
                }
            }
        }

        val prevInteraction = remember { MutableInteractionSource() }
        val prevScale by rememberPressScale(prevInteraction)
        Box(
            modifier = Modifier
                .weight(1f)
                .height(72.dp),
            contentAlignment = Alignment.Center
        ) {
            IconButton(
                onClick = { viewModel.previous() },
                interactionSource = prevInteraction,
                modifier = Modifier
                    .size(48.dp)
                    .graphicsLayer {
                        scaleX = prevScale
                        scaleY = prevScale
                    }
            ) {
                Icon(Icons.Default.SkipPrevious, contentDescription = "Previous", tint = Color.White, modifier = Modifier.size(34.dp))
            }
        }

        val isTrackBusy = (playbackState.isLoading || playbackState.isBuffering) && !isPlaying
        val playInteraction = remember { MutableInteractionSource() }
        val playScale by rememberPressScale(playInteraction)
        Box(
            modifier = Modifier
                .size(72.dp)
                .graphicsLayer {
                    scaleX = playScale
                    scaleY = playScale
                },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .shadow(16.dp, CircleShape)
                    .clip(CircleShape)
                    .background(Color.White)
                    .border(1.5.dp, songAccentColor.copy(alpha = 0.5f), CircleShape)
                    .clickable(
                        interactionSource = playInteraction,
                        indication = null
                    ) {
                        if (isPlaying) viewModel.pause() else viewModel.resume()
                    },
                contentAlignment = Alignment.Center
            ) {
                if (isTrackBusy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(26.dp),
                        color = Color.Black,
                        strokeWidth = 2.5.dp
                    )
                } else {
                    Crossfade(
                        targetState = isPlaying,
                        animationSpec = tween(200),
                        label = "PlayPauseCrossfade"
                    ) { playing ->
                        Icon(
                            imageVector = if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (playing) "Pause" else "Play",
                            tint = Color.Black,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            }
        }

        val nextInteraction = remember { MutableInteractionSource() }
        val nextScale by rememberPressScale(nextInteraction)
        Box(
            modifier = Modifier
                .weight(1f)
                .height(72.dp),
            contentAlignment = Alignment.Center
        ) {
            IconButton(
                onClick = { viewModel.next() },
                interactionSource = nextInteraction,
                modifier = Modifier
                    .size(48.dp)
                    .graphicsLayer {
                        scaleX = nextScale
                        scaleY = nextScale
                    }
            ) {
                Icon(Icons.Default.SkipNext, contentDescription = "Next", tint = Color.White, modifier = Modifier.size(34.dp))
            }
        }

        val repeatInteraction = remember { MutableInteractionSource() }
        val repeatScale by rememberPressScale(repeatInteraction)
        val isRepeatActive = playbackState.repeatMode != RepeatMode.OFF
        val isRepeatOne = playbackState.repeatMode == RepeatMode.ONE
        Box(
            modifier = Modifier
                .weight(1f)
                .height(72.dp),
            contentAlignment = Alignment.Center
        ) {
            IconButton(
                onClick = { viewModel.toggleRepeat() },
                interactionSource = repeatInteraction,
                modifier = Modifier
                    .size(48.dp)
                    .graphicsLayer {
                        scaleX = repeatScale
                        scaleY = repeatScale
                    }
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = if (isRepeatOne) Icons.Default.RepeatOne else Icons.Default.Repeat,
                        contentDescription = "Repeat",
                        tint = if (isRepeatActive) songAccentColor else Color.White.copy(alpha = 0.78f),
                        modifier = Modifier.size(24.dp)
                    )
                    if (isRepeatActive) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Box(
                            modifier = Modifier
                                .size(4.dp)
                                .clip(CircleShape)
                                .background(songAccentColor)
                        )
                    }
                }
            }
        }
    }

    Spacer(modifier = Modifier.height(24.dp))

    PlayerActionRow(
        viewModel = viewModel,
        currentTrack = currentTrack,
        hasQueueOrRelated = hasQueueOrRelated,
        sleepTimerActive = sleepTimerActive,
        onShowQueueSheet = onShowQueueSheet,
        onShowSleepTimer = onShowSleepTimer
    )
    }
}

internal fun formatPlayerTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%d:%02d", minutes, seconds)
}
