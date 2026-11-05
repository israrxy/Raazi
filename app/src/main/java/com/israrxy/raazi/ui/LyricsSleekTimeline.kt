package com.israrxy.raazi.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * Compact, sleek lyrics timeline.
 * Horizontally aligned in a single row: [elapsed] ────●──────── [duration].
 * 3dp track at rest (white 25%), expands to 5dp with thumb when dragging.
 * Direct canvas draw-phase rendering with tabular timecodes and tap-to-seek.
 */
@Composable
internal fun LyricsSleekTimeline(
    duration: Long,
    positionProvider: () -> Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var isDragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }
    var pendingSeekPositionMs by remember { mutableStateOf<Long?>(null) }
    var pendingSeekTimestampMs by remember { mutableLongStateOf(0L) }

    LaunchedEffect(pendingSeekPositionMs) {
        val target = pendingSeekPositionMs ?: return@LaunchedEffect
        while (true) {
            delay(100L)
            val current = positionProvider()
            val now = android.os.SystemClock.elapsedRealtime()
            if (kotlin.math.abs(current - target) <= 1_200L || (now - pendingSeekTimestampMs) > 2_500L) {
                pendingSeekPositionMs = null
                break
            }
        }
    }

    var elapsedSeconds by remember { mutableLongStateOf(0L) }
    LaunchedEffect(duration) {
        while (true) {
            val current = if (isDragging) {
                (dragFraction * duration).toLong()
            } else {
                pendingSeekPositionMs ?: positionProvider()
            }
            elapsedSeconds = (current / 1000L).coerceAtLeast(0L)
            delay(500L)
        }
    }

    val animatedTrackHeight by animateDpAsState(
        targetValue = if (isDragging) 5.dp else 3.dp,
        animationSpec = tween(140),
        label = "timeline_height"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(28.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = formatSecondsToTime(elapsedSeconds),
            style = MaterialTheme.typography.labelSmall.copy(
                fontFeatureSettings = "tnum",
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            ),
            color = Color.White.copy(alpha = 0.60f)
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .height(28.dp)
                .pointerInput(duration) {
                    detectTapGestures { offset ->
                        if (size.width > 0 && duration > 0L) {
                            val fraction = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                            val seekPos = (fraction * duration).toLong().coerceIn(0L, duration)
                            pendingSeekPositionMs = seekPos
                            pendingSeekTimestampMs = android.os.SystemClock.elapsedRealtime()
                            onSeek(seekPos)
                        }
                    }
                }
                .pointerInput(duration) {
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            if (size.width > 0) {
                                isDragging = true
                                dragFraction = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                            }
                        },
                        onDragEnd = {
                            if (duration > 0L) {
                                val seekPos = (dragFraction * duration).toLong().coerceIn(0L, duration)
                                pendingSeekPositionMs = seekPos
                                pendingSeekTimestampMs = android.os.SystemClock.elapsedRealtime()
                                onSeek(seekPos)
                            }
                            isDragging = false
                        },
                        onDragCancel = {
                            isDragging = false
                        },
                        onHorizontalDrag = { change, _ ->
                            change.consume()
                            if (size.width > 0) {
                                dragFraction = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                            }
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(animatedTrackHeight)
            ) {
                val trackWidth = size.width
                val trackHeight = size.height
                val corner = CornerRadius(trackHeight / 2f, trackHeight / 2f)

                // Background track
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.22f),
                    size = size,
                    cornerRadius = corner
                )

                // Active progress bar
                val progressFraction = if (isDragging) {
                    dragFraction
                } else if (pendingSeekPositionMs != null && duration > 0L) {
                    (pendingSeekPositionMs!!.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
                } else if (duration > 0L) {
                    (positionProvider().toFloat() / duration.toFloat()).coerceIn(0f, 1f)
                } else {
                    0f
                }

                val activeWidth = (trackWidth * progressFraction).coerceIn(0f, trackWidth)
                if (activeWidth > 0f) {
                    drawRoundRect(
                        color = Color.White,
                        size = Size(activeWidth, trackHeight),
                        cornerRadius = corner
                    )
                }

                // Active scrubbing thumb
                if (isDragging) {
                    drawCircle(
                        color = Color.White,
                        radius = 6.dp.toPx(),
                        center = Offset(activeWidth, trackHeight / 2f)
                    )
                }
            }
        }

        Text(
            text = formatSecondsToTime((duration / 1000L).coerceAtLeast(0L)),
            style = MaterialTheme.typography.labelSmall.copy(
                fontFeatureSettings = "tnum",
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            ),
            color = Color.White.copy(alpha = 0.60f)
        )
    }
}

internal fun formatSecondsToTime(totalSec: Long): String {
    val m = totalSec / 60
    val s = totalSec % 60
    return String.format("%d:%02d", m, s)
}
