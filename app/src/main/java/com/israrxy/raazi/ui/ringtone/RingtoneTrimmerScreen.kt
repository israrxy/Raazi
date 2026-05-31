package com.israrxy.raazi.ui.ringtone

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Loop
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Loop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.israrxy.raazi.service.RingtoneType
import com.israrxy.raazi.viewmodel.MusicPlayerViewModel
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import kotlin.random.Random
import kotlin.ranges.ClosedRange

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RingtoneTrimmerScreen(
    viewModel: MusicPlayerViewModel,
    onClose: () -> Unit
) {
    val state by viewModel.ringtoneState.collectAsStateWithLifecycle()
    val track by viewModel.ringtoneTrack.collectAsStateWithLifecycle()
    val ringtoneType by viewModel.ringtoneType.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showSuccessDialog by remember { mutableStateOf(false) }
    var successMessage by remember { mutableStateOf("") }
    var showErrorDialog by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }

    LaunchedEffect(state) {
        when (val s = state) {
            is MusicPlayerViewModel.RingtoneState.Done -> {
                showSuccessDialog = true
                successMessage = s.message
            }
            is MusicPlayerViewModel.RingtoneState.Error -> {
                showErrorDialog = true
                errorMessage = s.message
            }
            else -> {}
        }
    }

    if (showSuccessDialog) {
        AlertDialog(
            onDismissRequest = {
                showSuccessDialog = false
                viewModel.resetRingtoneState()
            },
            icon = {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp)
                )
            },
            title = { Text("Success!", textAlign = TextAlign.Center) },
            text = {
                Text(
                    successMessage,
                    textAlign = TextAlign.Center
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showSuccessDialog = false
                        viewModel.resetRingtoneState()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Done")
                }
            }
        )
    }

    if (showErrorDialog) {
        AlertDialog(
            onDismissRequest = {
                showErrorDialog = false
                viewModel.resetRingtoneState()
            },
            icon = {
                Icon(
                    Icons.Default.Error,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(48.dp)
                )
            },
            title = { Text("Error", textAlign = TextAlign.Center) },
            text = {
                Text(
                    errorMessage,
                    textAlign = TextAlign.Center
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showErrorDialog = false
                        viewModel.resetRingtoneState()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Close")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Set Ringtone", style = MaterialTheme.typography.titleLarge)
                        if (track != null) {
                            Text(
                                track!!.title,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.resetRingtoneState()
                    }) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            when (val currentState = state) {
                is MusicPlayerViewModel.RingtoneState.Downloading -> {
                    DownloadingContent(currentState.progress)
                }
                is MusicPlayerViewModel.RingtoneState.Trimming,
                is MusicPlayerViewModel.RingtoneState.Setting -> {
                    ProcessingContent(state is MusicPlayerViewModel.RingtoneState.Setting)
                }
                is MusicPlayerViewModel.RingtoneState.Ready -> {
                    TrimmerContent(
                        trackTitle = track?.title ?: "Unknown",
                        filePath = currentState.filePath,
                        durationMs = currentState.durationMs,
                        ringtoneType = ringtoneType,
                        onRingtoneTypeChange = { viewModel.setRingtoneType(it) },
                        onSetRingtone = { startMs, endMs ->
                            viewModel.setAsRingtone(currentState.filePath, startMs, endMs)
                        }
                    )
                }
                else -> {}
            }
        }
    }
}

@Composable
private fun DownloadingContent(progress: Int) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.padding(32.dp)
    ) {
        CircularProgressIndicator(modifier = Modifier.size(64.dp))
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            "Downloading track...",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (progress > 0) {
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { progress / 100f },
                modifier = Modifier
                    .fillMaxWidth(0.6f)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
            )
        }
    }
}

@Composable
private fun ProcessingContent(isSetting: Boolean) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.padding(32.dp)
    ) {
        CircularProgressIndicator(modifier = Modifier.size(64.dp))
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            if (isSetting) "Setting ringtone..." else "Trimming audio...",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Please wait a moment",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        )
    }
}

@Composable
private fun TrimmerContent(
    trackTitle: String,
    filePath: String,
    durationMs: Long,
    ringtoneType: RingtoneType,
    onRingtoneTypeChange: (RingtoneType) -> Unit,
    onSetRingtone: (Long, Long) -> Unit
) {
    val maxDuration = 30_000L
    var sliderRange by remember {
        mutableStateOf(0f..minOf(durationMs.toFloat(), maxDuration.toFloat()))
    }
    var isPlaying by remember { mutableStateOf(false) }
    var currentPlaybackMs by remember { mutableStateOf(0f) }
    var isLooping by remember { mutableStateOf(true) }
    var isPrepared by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build()
    }

    DisposableEffect(filePath) {
        val mediaItem = MediaItem.fromUri(Uri.parse("file://$filePath"))
        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()
        exoPlayer.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    isPrepared = true
                }
            }
        })
        onDispose {
            exoPlayer.release()
            isPrepared = false
        }
    }

    var hasWriteSettings by remember { mutableStateOf(Settings.System.canWrite(context)) }

    // Re-check permission periodically (so it updates after returning from system settings)
    LaunchedEffect(Unit) {
        while (true) {
            delay(2000)
            val current = Settings.System.canWrite(context)
            if (current != hasWriteSettings) hasWriteSettings = current
        }
    }

    LaunchedEffect(isPlaying, sliderRange.start) {
        if (isPlaying && isPrepared) {
            exoPlayer.seekTo(sliderRange.start.toLong())
            exoPlayer.play()
            currentPlaybackMs = sliderRange.start
            while (isPlaying) {
                delay(50)
                val pos = exoPlayer.currentPosition.toFloat()
                currentPlaybackMs = pos
                if (pos >= sliderRange.endInclusive) {
                    if (isLooping) {
                        exoPlayer.seekTo(sliderRange.start.toLong())
                        exoPlayer.play()
                        currentPlaybackMs = sliderRange.start
                    } else {
                        exoPlayer.pause()
                        isPlaying = false
                        currentPlaybackMs = sliderRange.start
                        break
                    }
                }
                if (!exoPlayer.isPlaying) break
            }
        } else if (!isPlaying) {
            exoPlayer.pause()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(4.dp))

        if (!hasWriteSettings) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Permission Required",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            "Raazi needs permission to modify system settings to set ringtones.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                        )
                    }
                }
                Button(
                    onClick = {
                        context.startActivity(
                            Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                                data = Uri.parse("package:${context.packageName}")
                            }
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 12.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Grant Permission")
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // --- Ringtone Type Selector ---
        Text(
            "Ringtone Type",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        RingtoneTypeSelector(
            selected = ringtoneType,
            onSelect = onRingtoneTypeChange
        )

        Spacer(modifier = Modifier.height(20.dp))

        // --- Waveform Visualizer ---
        AnimatedWaveform(
            durationMs = durationMs,
            sliderRange = sliderRange,
            currentPlaybackMs = currentPlaybackMs,
            isPlaying = isPlaying,
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        // --- Range Slider ---
        RangeSlider(
            value = sliderRange,
            onValueChange = { range ->
                val diff = range.endInclusive - range.start
                if (diff <= maxDuration) {
                    sliderRange = range
                } else {
                    if (range.start != sliderRange.start) {
                        sliderRange = range.start..(range.start + maxDuration)
                    } else {
                        sliderRange = (range.endInclusive - maxDuration)..range.endInclusive
                    }
                }
            },
            valueRange = 0f..durationMs.toFloat(),
            modifier = Modifier.fillMaxWidth(),
            colors = SliderDefaults.colors(
            thumbColor = MaterialTheme.colorScheme.primary,
            activeTrackColor = MaterialTheme.colorScheme.primary,
            inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
        )
        )

        // --- Time Labels ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TimeControl(
                label = "Start",
                timeMs = sliderRange.start.toLong(),
                onIncrease = {
                    val newStart = (sliderRange.start + 100).coerceAtMost(sliderRange.endInclusive - 1000)
                    sliderRange = newStart..sliderRange.endInclusive
                },
                onDecrease = {
                    val newStart = (sliderRange.start - 100).coerceAtLeast(0f)
                    sliderRange = newStart..sliderRange.endInclusive
                }
            )
            Text(
                formatDuration((sliderRange.endInclusive - sliderRange.start).toLong()),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            TimeControl(
                label = "End",
                timeMs = sliderRange.endInclusive.toLong(),
                onIncrease = {
                    val newEnd = (sliderRange.endInclusive + 100).coerceAtMost(durationMs.toFloat())
                    sliderRange = sliderRange.start..newEnd
                },
                onDecrease = {
                    val newEnd = (sliderRange.endInclusive - 100).coerceAtLeast(sliderRange.start + 1000)
                    sliderRange = sliderRange.start..newEnd
                }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                formatDuration(sliderRange.start.toLong()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                formatDuration(sliderRange.endInclusive.toLong()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // --- Playback Controls ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Loop toggle
            IconButton(onClick = { isLooping = !isLooping }) {
                Icon(
                    if (isLooping) Icons.Default.Loop else Icons.Outlined.Loop,
                    contentDescription = "Loop",
                    tint = if (isLooping) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(24.dp))

            // Play/Pause button
            FilledIconButton(
                onClick = {
                    if (isPlaying) {
                        exoPlayer.pause()
                        isPlaying = false
                    } else {
                        exoPlayer.seekTo(sliderRange.start.toLong())
                        currentPlaybackMs = sliderRange.start
                        isPlaying = true
                    }
                },
                modifier = Modifier.size(56.dp),
                shape = CircleShape
            ) {
                Icon(
                    if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    modifier = Modifier.size(32.dp)
                )
            }

            Spacer(modifier = Modifier.width(24.dp))

            // Reset to default selection
            IconButton(onClick = {
                if (!isPlaying) {
                    sliderRange = 0f..minOf(durationMs.toFloat(), maxDuration.toFloat())
                    currentPlaybackMs = sliderRange.start
                }
            }) {
                Icon(
                    Icons.Default.Replay,
                    contentDescription = "Reset",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            if (isPlaying) "Previewing selection" else "Preview your ringtone",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        )

        Spacer(modifier = Modifier.height(24.dp))

        // --- Set Button ---
        Button(
            onClick = {
                if (isPlaying) {
                    exoPlayer.pause()
                    isPlaying = false
                }
                onSetRingtone(sliderRange.start.toLong(), sliderRange.endInclusive.toLong())
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            val typeLabel = when (ringtoneType) {
                RingtoneType.RINGTONE -> "Ringtone"
                RingtoneType.NOTIFICATION -> "Notification"
                RingtoneType.ALARM -> "Alarm"
            }
            val durationLabel = formatDuration((sliderRange.endInclusive - sliderRange.start).toLong())
            Text(
                "Set as $typeLabel ($durationLabel)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun RingtoneTypeSelector(
    selected: RingtoneType,
    onSelect: (RingtoneType) -> Unit
) {
    val types = listOf(
        Triple(RingtoneType.RINGTONE, "Ringtone", Icons.Default.Phone),
        Triple(RingtoneType.NOTIFICATION, "Notification", Icons.Default.Notifications),
        Triple(RingtoneType.ALARM, "Alarm", Icons.Default.Alarm)
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        types.forEach { (type, label, icon) ->
            val isSelected = selected == type
            Card(
                modifier = Modifier
                    .weight(1f)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onSelect(type) },
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected)
                        MaterialTheme.colorScheme.primaryContainer
                    else
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                border = if (isSelected)
                    androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                else null
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        icon,
                        contentDescription = label,
                        tint = if (isSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
    }
}

@Composable
private fun AnimatedWaveform(
    durationMs: Long,
    sliderRange: ClosedRange<Float>,
    currentPlaybackMs: Float,
    isPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    val barCount = 60
    val barHeights = remember(durationMs) {
        val random = Random(42)
        List(barCount) { random.nextFloat() * 0.8f + 0.2f }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "waveform")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    val playbackBarIndex by remember {
        derivedStateOf {
            ((currentPlaybackMs / durationMs) * barCount).roundToInt().coerceIn(0, barCount - 1)
        }
    }

    val tintPrimary = MaterialTheme.colorScheme.primary
    val tintTertiary = MaterialTheme.colorScheme.tertiary
    val tintOnSurface = MaterialTheme.colorScheme.onSurface
    val tintSurfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    val bgColor = Color.Black.copy(alpha = 0.08f)
    val baselineColor = Color.Black.copy(alpha = 0.1f)

    Canvas(modifier = modifier.clip(RoundedCornerShape(12.dp))) {
        val canvasWidth = size.width
        val canvasHeight = size.height
        val barWidth = canvasWidth / barCount * 0.6f
        val gap = canvasWidth / barCount * 0.4f

        // Draw background
        drawRoundRect(
            color = bgColor,
            cornerRadius = CornerRadius(12.dp.toPx())
        )

        // Draw baseline
        drawLine(
            color = baselineColor,
            start = Offset(0f, canvasHeight / 2),
            end = Offset(canvasWidth, canvasHeight / 2),
            strokeWidth = 1f
        )

        barHeights.forEachIndexed { index, height ->
            val barX = index * (barWidth + gap) + gap / 2
            val barHeight = canvasHeight * height * 0.8f
            val barY = (canvasHeight - barHeight) / 2

            val barStartMs = (index.toFloat() / barCount) * durationMs
            val barEndMs = ((index + 1).toFloat() / barCount) * durationMs
            val isInRange = barEndMs >= sliderRange.start && barStartMs <= sliderRange.endInclusive
            val isActive = isPlaying && index == playbackBarIndex

            val color = when {
                isActive -> tintTertiary
                isInRange -> tintPrimary
                else -> tintOnSurface.copy(alpha = 0.15f)
            }

            val animatedHeight = if (isPlaying && index <= playbackBarIndex && isInRange) {
                barHeight * (0.8f + pulse * 0.2f)
            } else {
                barHeight
            }

            val animBarY = (canvasHeight - animatedHeight) / 2

            drawRoundRect(
                color = color,
                topLeft = Offset(barX, animBarY),
                size = androidx.compose.ui.geometry.Size(barWidth, animatedHeight),
                cornerRadius = CornerRadius(barWidth / 2)
            )
        }

        // Draw range indicator lines
        val startX = (sliderRange.start / durationMs) * canvasWidth
        val endX = (sliderRange.endInclusive / durationMs) * canvasWidth

        drawLine(
            color = tintPrimary.copy(alpha = 0.5f),
            start = Offset(startX, 0f),
            end = Offset(startX, canvasHeight),
            strokeWidth = 2.dp.toPx()
        )
        drawLine(
            color = tintPrimary.copy(alpha = 0.5f),
            start = Offset(endX, 0f),
            end = Offset(endX, canvasHeight),
            strokeWidth = 2.dp.toPx()
        )

        // Draw playback position indicator
        if (isPlaying) {
            val playX = (currentPlaybackMs / durationMs) * canvasWidth
            drawLine(
                color = tintTertiary,
                start = Offset(playX, 0f),
                end = Offset(playX, canvasHeight),
                strokeWidth = 2.dp.toPx()
            )
        }
    }
}

@Composable
private fun TimeControl(
    label: String,
    timeMs: Long,
    onIncrease: () -> Unit,
    onDecrease: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilledIconButton(
                onClick = onDecrease,
                modifier = Modifier.size(32.dp),
                shape = CircleShape,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Icon(
                    Icons.Default.Remove,
                    contentDescription = "Decrease",
                    modifier = Modifier.size(18.dp)
                )
            }
            Text(
                formatDuration(timeMs),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
            FilledIconButton(
                onClick = onIncrease,
                modifier = Modifier.size(32.dp),
                shape = CircleShape,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "Increase",
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

private fun formatDuration(ms: Long): String {
    val totalSeconds = (ms / 1000).toInt()
    val m = totalSeconds / 60
    val s = totalSeconds % 60
    return String.format("%d:%02d", m, s)
}
