package com.israrxy.raazi.ui.ringtone

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.israrxy.raazi.service.RingtoneType
import kotlin.math.roundToInt
import kotlin.random.Random
import kotlin.ranges.ClosedRange

@Composable
internal fun RingtoneTypeSelector(
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
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onSelect(type) },
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
                        .padding(vertical = 12.dp),
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
internal fun AnimatedWaveform(
    durationMs: Long,
    sliderRange: ClosedRange<Float>,
    currentPlaybackMs: Float,
    isPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    val safeDuration = durationMs.coerceAtLeast(1000L)
    val barCount = 60
    val barHeights = remember(safeDuration) {
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

    val playbackBarIndex by remember(currentPlaybackMs, safeDuration) {
        derivedStateOf {
            ((currentPlaybackMs / safeDuration) * barCount).roundToInt().coerceIn(0, barCount - 1)
        }
    }

    val tintPrimary = MaterialTheme.colorScheme.primary
    val tintTertiary = MaterialTheme.colorScheme.tertiary
    val tintOnSurface = MaterialTheme.colorScheme.onSurface
    val bgColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    val baselineColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f)

    Canvas(modifier = modifier.clip(RoundedCornerShape(14.dp))) {
        val canvasWidth = size.width
        val canvasHeight = size.height
        val barWidth = canvasWidth / barCount * 0.6f
        val gap = canvasWidth / barCount * 0.4f

        // Draw background
        drawRoundRect(
            color = bgColor,
            cornerRadius = CornerRadius(14.dp.toPx())
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

            val barStartMs = (index.toFloat() / barCount) * safeDuration
            val barEndMs = ((index + 1).toFloat() / barCount) * safeDuration
            val isInRange = barEndMs >= sliderRange.start && barStartMs <= sliderRange.endInclusive
            val isActive = isPlaying && index == playbackBarIndex

            val color = when {
                isActive -> tintTertiary
                isInRange -> tintPrimary
                else -> tintOnSurface.copy(alpha = 0.18f)
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
        val startX = ((sliderRange.start / safeDuration) * canvasWidth).coerceIn(0f, canvasWidth)
        val endX = ((sliderRange.endInclusive / safeDuration) * canvasWidth).coerceIn(0f, canvasWidth)

        drawLine(
            color = tintPrimary.copy(alpha = 0.65f),
            start = Offset(startX, 0f),
            end = Offset(startX, canvasHeight),
            strokeWidth = 2.dp.toPx()
        )
        drawLine(
            color = tintPrimary.copy(alpha = 0.65f),
            start = Offset(endX, 0f),
            end = Offset(endX, canvasHeight),
            strokeWidth = 2.dp.toPx()
        )

        // Draw playback position indicator
        if (isPlaying) {
            val playX = ((currentPlaybackMs / safeDuration) * canvasWidth).coerceIn(0f, canvasWidth)
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
internal fun TimeControl(
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
        Spacer(modifier = Modifier.height(2.dp))
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
                    contentDescription = "Decrease $label",
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
                    contentDescription = "Increase $label",
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

internal fun formatDuration(ms: Long): String {
    val totalSeconds = (ms.coerceAtLeast(0L) / 1000L).toInt()
    val m = totalSeconds / 60
    val s = totalSeconds % 60
    return String.format("%d:%02d", m, s)
}
