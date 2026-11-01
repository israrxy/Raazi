package com.israrxy.raazi.ui

import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.israrxy.raazi.data.db.DownloadEntity
import com.israrxy.raazi.model.MusicItem
import com.israrxy.raazi.model.PlaybackMediaMode
import com.israrxy.raazi.model.PlaybackState
import com.israrxy.raazi.model.RepeatMode
import com.israrxy.raazi.ui.theme.Emerald500
import com.israrxy.raazi.ui.theme.Zinc500
import com.israrxy.raazi.viewmodel.MusicPlayerViewModel

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
    onShowSleepTimer: () -> Unit = {}
) {
    val context = LocalContext.current
    val currentTrack = playbackState.currentTrack
    val isPlaying = playbackState.isPlaying

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
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(
                        text = track?.title ?: "Not Playing",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
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

                IconButton(onClick = { track?.let { viewModel.toggleFavorite(it) } }) {
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

    Spacer(modifier = Modifier.height(24.dp))

    Column(modifier = Modifier.fillMaxWidth()) {
        val sliderFraction = when {
            isDraggingSlider -> dragFraction
            duration > 0L -> (progress.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
            else -> 0f
        }
        Slider(
            value = sliderFraction,
            onValueChange = onSliderChange,
            onValueChangeFinished = onSliderFinished,
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Color.White,
                inactiveTrackColor = Color.White.copy(alpha = 0.2f)
            ),
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
                style = MaterialTheme.typography.labelSmall,
                color = Zinc500
            )
            Text(
                text = formatPlayerTime(duration),
                style = MaterialTheme.typography.labelSmall,
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
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val shuffleEnabled = playbackState.isShuffleEnabled
        Box(
            modifier = Modifier
                .weight(1f)
                .height(72.dp),
            contentAlignment = Alignment.Center
        ) {
            IconButton(
                onClick = { viewModel.toggleShuffle() },
                modifier = Modifier.size(44.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Shuffle,
                    contentDescription = "Shuffle",
                    tint = if (shuffleEnabled) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.78f),
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .height(72.dp),
            contentAlignment = Alignment.Center
        ) {
            IconButton(
                onClick = { viewModel.previous() },
                modifier = Modifier.size(44.dp)
            ) {
                Icon(Icons.Default.SkipPrevious, null, tint = Color.White, modifier = Modifier.size(34.dp))
            }
        }

        val isTrackBusy = (playbackState.isLoading || playbackState.isBuffering) && !isPlaying
        Box(
            modifier = Modifier.size(72.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .shadow(16.dp, CircleShape)
                    .clip(CircleShape)
                    .background(Color.White)
                    .clickable {
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
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Play/Pause",
                        tint = Color.Black,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .height(72.dp),
            contentAlignment = Alignment.Center
        ) {
            IconButton(
                onClick = { viewModel.next() },
                modifier = Modifier.size(44.dp)
            ) {
                Icon(Icons.Default.SkipNext, null, tint = Color.White, modifier = Modifier.size(34.dp))
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .height(72.dp),
            contentAlignment = Alignment.Center
        ) {
            IconButton(
                onClick = { viewModel.toggleRepeat() },
                modifier = Modifier.size(44.dp)
            ) {
                Icon(
                    imageVector = when (playbackState.repeatMode) {
                        RepeatMode.ONE -> Icons.Default.RepeatOne
                        else -> Icons.Default.Repeat
                    },
                    contentDescription = "Repeat",
                    tint = when (playbackState.repeatMode) {
                        RepeatMode.OFF -> Color.White.copy(alpha = 0.78f)
                        else -> MaterialTheme.colorScheme.primary
                    },
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(32.dp))

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 32.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        val allDownloads by viewModel.dbAllDownloads.collectAsStateWithLifecycle()
        val downloadState = currentTrack?.let { track ->
            allDownloads.find { it.trackId == track.id }
        }
        val downloadIcon = when (downloadState?.status) {
            DownloadEntity.STATUS_COMPLETED -> Icons.Default.CheckCircle
            DownloadEntity.STATUS_DOWNLOADING,
            DownloadEntity.STATUS_PENDING -> Icons.Default.Downloading
            DownloadEntity.STATUS_FAILED -> Icons.Default.ErrorOutline
            else -> Icons.Default.Download
        }
        val isDownloaded = downloadState?.status == DownloadEntity.STATUS_COMPLETED
        val isFailed = downloadState?.status == DownloadEntity.STATUS_FAILED

        val dlAccent = when {
            isDownloaded -> Emerald500
            isFailed -> Color(0xFFFF5252)
            else -> Color.White.copy(alpha = 0.9f)
        }

        Box(
            modifier = Modifier
                .size(50.dp)
                .clip(CircleShape)
                .clickable {
                    currentTrack?.let { track ->
                        when (downloadState?.status) {
                            DownloadEntity.STATUS_COMPLETED -> {
                                Toast.makeText(context, "Already downloaded", Toast.LENGTH_SHORT).show()
                            }
                            DownloadEntity.STATUS_DOWNLOADING,
                            DownloadEntity.STATUS_PENDING -> {
                                Toast.makeText(context, "Download in progress", Toast.LENGTH_SHORT).show()
                            }
                            DownloadEntity.STATUS_FAILED -> {
                                viewModel.retryDownload(track.id)
                            }
                            else -> {
                                viewModel.downloadTrack(track)
                            }
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            if (downloadState?.status == DownloadEntity.STATUS_DOWNLOADING) {
                CircularProgressIndicator(
                    progress = downloadState.progress / 100f,
                    modifier = Modifier.size(50.dp),
                    color = Emerald500,
                    strokeWidth = 2.dp,
                    trackColor = Color.Transparent
                )
            }
            Icon(downloadIcon, null, tint = dlAccent, modifier = Modifier.size(24.dp))
        }

        Spacer(modifier = Modifier.width(24.dp))

        val playNextAccent = if (hasQueueOrRelated) Color.White.copy(alpha = 0.9f) else Color.White.copy(alpha = 0.5f)
        Box(
            modifier = Modifier
                .size(50.dp)
                .clip(CircleShape)
                .clickable(onClick = onShowQueueSheet),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.QueueMusic, contentDescription = "Queue", tint = playNextAccent, modifier = Modifier.size(24.dp))
        }

        // Sleep timer and share actions are available from the player options menu.
    }
}

internal fun formatPlayerTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%d:%02d", minutes, seconds)
}
