package com.israrxy.raazi.ui.ringtone

import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Loop
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Loop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.israrxy.raazi.service.RingtoneType
import kotlinx.coroutines.delay

@Composable
internal fun TrimmerContent(
    trackTitle: String,
    filePath: String,
    durationMs: Long,
    ringtoneType: RingtoneType,
    onRingtoneTypeChange: (RingtoneType) -> Unit,
    onSetRingtone: (Long, Long) -> Unit,
    onRequestPermission: () -> Unit
) {
    val maxDuration = 30_000L
    var effectiveDurationMs by remember(durationMs) {
        mutableLongStateOf(if (durationMs >= 2000L) durationMs else 30_000L)
    }
    var sliderRange by remember(effectiveDurationMs) {
        mutableStateOf(0f..minOf(effectiveDurationMs.toFloat(), maxDuration.toFloat()))
    }
    var isPlaying by remember { mutableStateOf(false) }
    var currentPlaybackMs by remember { mutableFloatStateOf(0f) }
    var isLooping by remember { mutableStateOf(true) }
    var isPrepared by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build()
    }

    DisposableEffect(filePath) {
        val mediaItem = MediaItem.fromUri(Uri.fromFile(java.io.File(filePath)))
        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    isPrepared = true
                    val playerDur = exoPlayer.duration
                    if (playerDur > 1000L && (durationMs <= 1000L || kotlin.math.abs(playerDur - effectiveDurationMs) > 2000L)) {
                        effectiveDurationMs = playerDur
                        sliderRange = 0f..minOf(playerDur.toFloat(), maxDuration.toFloat())
                    }
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
            isPrepared = false
        }
    }

    var hasWriteSettings by remember { mutableStateOf(Settings.System.canWrite(context)) }

    // Immediately re-check permission when user returns from system settings
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        hasWriteSettings = Settings.System.canWrite(context)
    }

    // Also poll periodically in case activity lifecycle doesn't pause on overlay settings
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            val current = Settings.System.canWrite(context)
            if (current != hasWriteSettings) hasWriteSettings = current
        }
    }

    LaunchedEffect(isPlaying, isPrepared, sliderRange.start, sliderRange.endInclusive) {
        if (isPlaying && isPrepared) {
            if (exoPlayer.currentPosition < sliderRange.start.toLong() || exoPlayer.currentPosition >= sliderRange.endInclusive.toLong()) {
                exoPlayer.seekTo(sliderRange.start.toLong())
                currentPlaybackMs = sliderRange.start
            }
            exoPlayer.play()
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
                if (!exoPlayer.isPlaying && exoPlayer.playbackState == Player.STATE_ENDED) {
                    if (isLooping) {
                        exoPlayer.seekTo(sliderRange.start.toLong())
                        exoPlayer.play()
                    } else {
                        isPlaying = false
                        break
                    }
                }
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
        Spacer(modifier = Modifier.height(8.dp))

        if (!hasWriteSettings) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
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
                            "Allow Raazi to modify system settings so it can set your ringtone.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.85f)
                        )
                    }
                }
                Button(
                    onClick = onRequestPermission,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 12.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Grant Permission")
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // --- Ringtone Type Selector ---
        Text(
            "Assign To",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
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
            durationMs = effectiveDurationMs,
            sliderRange = sliderRange,
            currentPlaybackMs = currentPlaybackMs,
            isPlaying = isPlaying,
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        // --- Range Slider ---
        val safeValueRange = 0f..effectiveDurationMs.toFloat().coerceAtLeast(1000f)
        RangeSlider(
            value = sliderRange.start.coerceIn(safeValueRange)..sliderRange.endInclusive.coerceIn(safeValueRange),
            onValueChange = { range ->
                val diff = range.endInclusive - range.start
                if (diff <= maxDuration) {
                    sliderRange = range
                } else {
                    if (range.start != sliderRange.start) {
                        sliderRange = range.start..(range.start + maxDuration).coerceAtMost(safeValueRange.endInclusive)
                    } else {
                        sliderRange = (range.endInclusive - maxDuration).coerceAtLeast(0f)..range.endInclusive
                    }
                }
            },
            valueRange = safeValueRange,
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
                    val newStart = (sliderRange.start + 500f).coerceAtMost(sliderRange.endInclusive - 1000f)
                    sliderRange = newStart..sliderRange.endInclusive
                },
                onDecrease = {
                    val newStart = (sliderRange.start - 500f).coerceAtLeast(0f)
                    sliderRange = newStart..sliderRange.endInclusive
                }
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "Clip Length",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    formatDuration((sliderRange.endInclusive - sliderRange.start).toLong()),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            TimeControl(
                label = "End",
                timeMs = sliderRange.endInclusive.toLong(),
                onIncrease = {
                    val maxEnd = minOf(effectiveDurationMs.toFloat(), sliderRange.start + maxDuration)
                    val newEnd = (sliderRange.endInclusive + 500f).coerceAtMost(maxEnd)
                    sliderRange = sliderRange.start..newEnd
                },
                onDecrease = {
                    val newEnd = (sliderRange.endInclusive - 500f).coerceAtLeast(sliderRange.start + 1000f)
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
                "0:00",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "Max 30s clip • Total ${formatDuration(effectiveDurationMs)}",
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
                if (isPlaying) {
                    exoPlayer.pause()
                    isPlaying = false
                }
                sliderRange = 0f..minOf(effectiveDurationMs.toFloat(), maxDuration.toFloat())
                currentPlaybackMs = sliderRange.start
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
            if (isPlaying) "Previewing ${formatDuration(currentPlaybackMs.toLong())}" else "Tap play to preview selected clip",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )

        Spacer(modifier = Modifier.height(24.dp))

        // --- Set Button ---
        val isSelectionValid = (sliderRange.endInclusive - sliderRange.start) >= 1000f
        Button(
            onClick = {
                if (!hasWriteSettings) {
                    onRequestPermission()
                    return@Button
                }
                if (isPlaying) {
                    exoPlayer.pause()
                    isPlaying = false
                }
                onSetRingtone(sliderRange.start.toLong(), sliderRange.endInclusive.toLong())
            },
            enabled = isSelectionValid,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            val typeLabel = when (ringtoneType) {
                RingtoneType.RINGTONE -> "Ringtone"
                RingtoneType.NOTIFICATION -> "Notification"
                RingtoneType.ALARM -> "Alarm"
            }
            val durationLabel = formatDuration((sliderRange.endInclusive - sliderRange.start).toLong())
            val text = when {
                !hasWriteSettings -> "Grant Permission to Set $typeLabel"
                !isSelectionValid -> "Pick at least 1 second"
                else -> "Set as $typeLabel ($durationLabel)"
            }
            Text(
                text,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(modifier = Modifier.height(20.dp))
    }
}
