package com.israrxy.raazi.ui

import android.widget.Toast
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Downloading
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.israrxy.raazi.data.db.DownloadEntity
import com.israrxy.raazi.model.MusicItem
import com.israrxy.raazi.ui.theme.Emerald500
import com.israrxy.raazi.viewmodel.MusicPlayerViewModel

@Composable
internal fun PlayerActionRow(
    viewModel: MusicPlayerViewModel,
    currentTrack: MusicItem?,
    hasQueueOrRelated: Boolean,
    sleepTimerActive: Boolean,
    onShowQueueSheet: () -> Unit,
    onShowSleepTimer: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
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

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Column 1: Download button (aligned under Shuffle)
        Box(
            modifier = Modifier
                .weight(1f)
                .height(48.dp),
            contentAlignment = Alignment.Center
        ) {
            val dlInteraction = remember { MutableInteractionSource() }
            val dlPressed by dlInteraction.collectIsPressedAsState()
            val dlScale by animateFloatAsState(
                targetValue = if (dlPressed) 0.92f else 1f,
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                label = "dlScale"
            )
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .graphicsLayer {
                        scaleX = dlScale
                        scaleY = dlScale
                    }
                    .clip(CircleShape)
                    .clickable(
                        interactionSource = dlInteraction,
                        indication = null
                    ) {
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
                        modifier = Modifier.size(44.dp),
                        color = Emerald500,
                        strokeWidth = 2.dp,
                        trackColor = Color.Transparent
                    )
                }
                Icon(downloadIcon, contentDescription = "Download", tint = dlAccent, modifier = Modifier.size(24.dp))
            }
        }

        // Column 2: Empty Spacer
        Spacer(modifier = Modifier.weight(1f))

        // Column 3: Sleep Timer (centered under Play/Pause) when active
        Box(
            modifier = Modifier
                .size(72.dp)
                .height(48.dp),
            contentAlignment = Alignment.Center
        ) {
            if (sleepTimerActive) {
                val timerInteraction = remember { MutableInteractionSource() }
                val timerPressed by timerInteraction.collectIsPressedAsState()
                val timerScale by animateFloatAsState(
                    targetValue = if (timerPressed) 0.92f else 1f,
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                    label = "timerScale"
                )
                IconButton(
                    onClick = onShowSleepTimer,
                    interactionSource = timerInteraction,
                    modifier = Modifier
                        .size(48.dp)
                        .graphicsLayer {
                            scaleX = timerScale
                            scaleY = timerScale
                        }
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Bedtime,
                        contentDescription = "Sleep Timer",
                        tint = Emerald500,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        // Column 4: Empty Spacer
        Spacer(modifier = Modifier.weight(1f))

        // Column 5: Queue button (aligned under Repeat)
        Box(
            modifier = Modifier
                .weight(1f)
                .height(48.dp),
            contentAlignment = Alignment.Center
        ) {
            val queueInteraction = remember { MutableInteractionSource() }
            val queuePressed by queueInteraction.collectIsPressedAsState()
            val queueScale by animateFloatAsState(
                targetValue = if (queuePressed) 0.92f else 1f,
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                label = "queueScale"
            )
            val playNextAccent = if (hasQueueOrRelated) Color.White.copy(alpha = 0.9f) else Color.White.copy(alpha = 0.5f)
            IconButton(
                onClick = onShowQueueSheet,
                interactionSource = queueInteraction,
                modifier = Modifier
                    .size(48.dp)
                    .graphicsLayer {
                        scaleX = queueScale
                        scaleY = queueScale
                    }
            ) {
                Icon(
                    imageVector = Icons.Default.QueueMusic,
                    contentDescription = "Queue",
                    tint = playNextAccent,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}
