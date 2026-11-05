package com.israrxy.raazi.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * Sleek, draw-phase driven lyrics timeline.
 * 3dp track (white 25%), grows to 6dp with thumb while dragging. Touch target >= 48dp.
 * Progress is drawn directly on Canvas from positionProvider.
 * Time labels update at most once per second with tabular numerals.
 * Scrubbing and seek release match PlayerScreen logic to prevent snap-back.
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
        targetValue = if (isDragging) 6.dp else 3.dp,
        animationSpec = tween(140),
        label = "timeline_height"
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp) // Minimum 48dp touch target
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

                // Background track: 3dp (or 6dp when dragging), white 25% alpha
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.25f),
                    size = size,
                    cornerRadius = corner
                )

                // Active progress: drawn in draw phase from positionProvider
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

                // Thumb only visible while dragging
                if (isDragging) {
                    drawCircle(
                        color = Color.White,
                        radius = 7.dp.toPx(),
                        center = Offset(activeWidth, trackHeight / 2f)
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .offset(y = (-8).dp)
                .padding(horizontal = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = formatSecondsToTime(elapsedSeconds),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFeatureSettings = "tnum",
                    fontSize = 11.sp
                ),
                color = Color.White.copy(alpha = 0.60f)
            )
            Text(
                text = formatSecondsToTime((duration / 1000L).coerceAtLeast(0L)),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFeatureSettings = "tnum",
                    fontSize = 11.sp
                ),
                color = Color.White.copy(alpha = 0.60f)
            )
        }
    }
}

internal fun formatSecondsToTime(totalSec: Long): String {
    val m = totalSec / 60
    val s = totalSec % 60
    return String.format("%d:%02d", m, s)
}
