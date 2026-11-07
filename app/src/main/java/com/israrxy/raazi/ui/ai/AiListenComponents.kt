package com.israrxy.raazi.ui.ai

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.israrxy.raazi.ui.theme.Emerald400
import com.israrxy.raazi.ui.theme.Emerald500
import com.israrxy.raazi.ui.theme.Emerald600
import java.util.Locale

/**
 * Idle listen state: a single large tap target instead of a form-like card.
 */
@Composable
internal fun AiListenHero(onStart: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.size(224.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(200.dp)
                    .background(Emerald500.copy(alpha = 0.05f), CircleShape)
            )
            Box(
                modifier = Modifier
                    .size(160.dp)
                    .background(Emerald500.copy(alpha = 0.09f), CircleShape)
            )
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(listOf(Emerald400, Emerald600))
                    )
                    .clickable(onClick = onStart),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Mic,
                    contentDescription = "Start listening",
                    tint = Color.White,
                    modifier = Modifier.size(48.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "Tap to listen",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Play the song near your phone. Raazi listens for up to " +
                "15 seconds, then asks Gemini to name it.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 18.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AiListenHint(icon = Icons.Filled.GraphicEq, text = "15 second clip")
            AiListenHint(icon = Icons.Filled.Mic, text = "Mic only")
        }
    }
}

@Composable
private fun AiListenHint(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            .padding(horizontal = 11.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(14.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Active listen state: pulsing orb, circular countdown, live RMS waveform.
 */
@Composable
internal fun AiListeningHero(
    elapsedMs: Long,
    maxMs: Long,
    levels: List<Float>,
    onStop: () -> Unit
) {
    val progress = if (maxMs <= 0L) {
        0f
    } else {
        (elapsedMs.toFloat() / maxMs.toFloat()).coerceIn(0f, 1f)
    }
    val finishing = progress >= 1f
    val trackColor = MaterialTheme.colorScheme.surfaceVariant

    val transition = rememberInfiniteTransition(label = "listenPulse")
    val pulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse"
    )
    val level = levels.lastOrNull() ?: 0f
    val orbScale by animateFloatAsState(
        targetValue = 1f + level * 0.06f,
        animationSpec = tween(durationMillis = 120),
        label = "orbScale"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.size(236.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size((176f + 44f * pulse).dp)
                    .background(
                        Emerald500.copy(alpha = 0.16f * (1f - pulse)),
                        CircleShape
                    )
            )
            Canvas(modifier = Modifier.size(206.dp)) {
                val strokeWidth = 8.dp.toPx()
                drawArc(
                    color = trackColor,
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
                if (progress > 0f) {
                    drawArc(
                        color = Emerald500,
                        startAngle = -90f,
                        sweepAngle = 360f * progress,
                        useCenter = false,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )
                }
            }
            Box(
                modifier = Modifier
                    .size(150.dp)
                    .graphicsLayer {
                        scaleX = orbScale
                        scaleY = orbScale
                    }
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(Emerald400, Emerald600))),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Filled.GraphicEq,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(30.dp)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = formatElapsed(elapsedMs),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = if (finishing) "Wrapping up…" else "Listening…",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Play the song near your phone",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(10.dp))
        AiWaveform(levels = levels)
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = "${(elapsedMs / 1000).coerceAtLeast(0)}s of ${maxMs / 1000}s",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = onStop,
            enabled = !finishing,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(percent = 50)
        ) {
            Text(
                text = if (finishing) "Identifying…" else "Stop & identify",
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun AiWaveform(levels: List<Float>) {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(46.dp)
            .padding(horizontal = 24.dp)
    ) {
        val barCount = 34
        val visible = when {
            levels.isEmpty() -> List(barCount) { 0f }
            levels.size >= barCount -> levels.takeLast(barCount)
            else -> List(barCount - levels.size) { 0f } + levels
        }
        val gap = 4.dp.toPx()
        val barWidth = 3.dp.toPx()
        val totalWidth = barCount * barWidth + (barCount - 1) * gap
        val startX = (size.width - totalWidth) / 2f
        val minHeight = 5.dp.toPx()
        val maxHeight = size.height

        visible.forEachIndexed { index, raw ->
            val level = raw.coerceIn(0f, 1f)
            val barHeight = minHeight + (maxHeight - minHeight) * level
            val x = startX + index * (barWidth + gap)
            drawRoundRect(
                color = Emerald500.copy(alpha = 0.35f + 0.65f * level),
                topLeft = Offset(x, (size.height - barHeight) / 2f),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
            )
        }
    }
}

@Composable
internal fun AiListenAgainButton(onStart: () -> Unit) {
    OutlinedButton(
        onClick = onStart,
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp),
        shape = RoundedCornerShape(percent = 50)
    ) {
        Icon(
            imageVector = Icons.Filled.Refresh,
            contentDescription = null,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text("Listen again")
    }
}

private fun formatElapsed(elapsedMs: Long): String {
    val totalSeconds = (elapsedMs / 1000).coerceAtLeast(0L)
    return String.format(
        Locale.US,
        "%d:%02d",
        totalSeconds / 60,
        totalSeconds % 60
    )
}
