package com.israrxy.raazi.ui.player

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.israrxy.raazi.model.MusicItem
import com.israrxy.raazi.utils.ThumbnailUtils
import com.israrxy.raazi.viewmodel.MusicPlayerViewModel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private data class MiniPlayerUiState(
    val track: MusicItem? = null,
    val isPlaying: Boolean = false,
    val isLoading: Boolean = false,
    val isBuffering: Boolean = false,
    val canSkipNext: Boolean = false
)

private data class MiniPlayerProgressState(
    val currentPosition: Long = 0L,
    val duration: Long = 0L
)

@Composable
fun MiniPlayer(
    viewModel: MusicPlayerViewModel,
    onNavigateToPlayer: () -> Unit,
    modifier: Modifier = Modifier
) {
    val miniPlayerUiStateFlow = remember(viewModel) {
        viewModel.playbackState
            .map { state ->
                MiniPlayerUiState(
                    track = state.currentTrack,
                    isPlaying = state.isPlaying,
                    isLoading = state.isLoading,
                    isBuffering = state.isBuffering,
                    canSkipNext = state.playlist.size > 1
                )
            }
            .distinctUntilChanged()
    }
    val miniPlayerUiState by miniPlayerUiStateFlow.collectAsStateWithLifecycle(initialValue = MiniPlayerUiState())
    val track = miniPlayerUiState.track ?: return
    val isPlaying = miniPlayerUiState.isPlaying
    val isBusy = miniPlayerUiState.isLoading || miniPlayerUiState.isBuffering

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val compactLayout = maxWidth < 360.dp
        val cardHeight = if (compactLayout) 64.dp else 68.dp
        val artworkSize = if (compactLayout) 44.dp else 48.dp
        val playButtonSize = if (compactLayout) 38.dp else 40.dp
        val statusText = when {
            miniPlayerUiState.isBuffering -> "Buffering"
            miniPlayerUiState.isLoading -> "Loading track"
            track.isLive -> "Live"
            isPlaying -> ""
            else -> "Paused"
        }
        val supportingText = track.artist.ifBlank { statusText }.ifBlank { "Playing now" }

        val cardInteractionSource = remember { MutableInteractionSource() }
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(cardHeight)
                .clip(RoundedCornerShape(18.dp))
                .clickable(
                    interactionSource = cardInteractionSource,
                    indication = LocalIndication.current
                ) { onNavigateToPlayer() }
                .animateContentSize(),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.18f)
            ),
            tonalElevation = 4.dp,
            shadowElevation = 8.dp
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = 10.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        modifier = Modifier.size(artworkSize),
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        AsyncImage(
                            model = ThumbnailUtils.getHighQualityThumbnail(track.thumbnailUrl),
                            contentDescription = track.title,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }

                    Spacer(modifier = Modifier.width(if (compactLayout) 10.dp else 12.dp))

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = track.title,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(modifier = Modifier.height(3.dp))

                        Text(
                            text = supportingText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (!compactLayout && miniPlayerUiState.canSkipNext) {
                        IconButton(
                            onClick = { viewModel.addToQueue(track) },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.QueueMusic,
                                contentDescription = "Add to queue",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        IconButton(
                            onClick = { viewModel.next() },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipNext,
                                contentDescription = "Next",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else if (!compactLayout) {
                        IconButton(
                            onClick = { viewModel.addToQueue(track) },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.QueueMusic,
                                contentDescription = "Add to queue",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    IconButton(
                        onClick = {
                            if (isPlaying) viewModel.pause() else viewModel.resume()
                        },
                        enabled = !miniPlayerUiState.isLoading || isPlaying,
                        modifier = Modifier
                            .size(playButtonSize)
                            .background(
                                color = MaterialTheme.colorScheme.primary,
                                shape = CircleShape
                            )
                    ) {
                        if (isBusy && !isPlaying) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }

                MiniPlayerProgress(
                    viewModel = viewModel,
                    isLive = track.isLive,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun MiniPlayerProgress(
    viewModel: MusicPlayerViewModel,
    isLive: Boolean,
    modifier: Modifier = Modifier
) {
    if (isLive) return

    val miniPlayerProgressFlow = remember(viewModel) {
        viewModel.playbackState
            .map { state ->
                MiniPlayerProgressState(
                    currentPosition = state.currentPosition,
                    duration = state.duration
                )
            }
            .distinctUntilChanged()
    }
    val miniPlayerProgress by miniPlayerProgressFlow.collectAsStateWithLifecycle(initialValue = MiniPlayerProgressState())
    val duration = miniPlayerProgress.duration
    if (duration <= 0L) return

    val progressFraction =
        (miniPlayerProgress.currentPosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f)

    Box(
        modifier = modifier
            .height(2.dp)
            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f))
            .pointerInput(duration) {
                if (duration <= 0L) return@pointerInput
                detectTapGestures { offset ->
                    val fraction = (offset.x / size.width).coerceIn(0f, 1f)
                    viewModel.seekTo((fraction * duration).toLong())
                }
            }
            .pointerInput(duration) {
                if (duration <= 0L) return@pointerInput
                detectHorizontalDragGestures { change, _ ->
                    change.consume()
                    val fraction = (change.position.x / size.width).coerceIn(0f, 1f)
                    viewModel.seekTo((fraction * duration).toLong())
                }
            }
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(progressFraction)
                .background(MaterialTheme.colorScheme.primary)
        )
    }
}

@Composable
fun MiniVisualizer(modifier: Modifier = Modifier) {
    val barCount = 4
    val infiniteTransition = rememberInfiniteTransition(label = "visualizer")

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(barCount) { index ->
            val heightPercent by infiniteTransition.animateFloat(
                initialValue = 0.2f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(
                        durationMillis = 300 + (index * 100),
                        delayMillis = index * 50,
                        easing = FastOutSlowInEasing
                    ),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "bar_$index"
            )

            Box(
                modifier = Modifier
                    .width(3.dp)
                    .fillMaxHeight(heightPercent)
                    .background(MaterialTheme.colorScheme.primary, CircleShape)
            )
        }
    }
}

private fun formatMiniPlayerTime(ms: Long): String {
    val totalSeconds = (ms / 1000L).coerceAtLeast(0L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return String.format("%d:%02d", minutes, seconds)
}
