package com.israrxy.raazi.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.israrxy.raazi.viewmodel.VoiceSearchUiState

/**
 * Custom speech popup. The system recognizer runs behind it; this composable is
 * only the visual layer, with a pulsing mic and a live level waveform.
 */
@Composable
internal fun VoiceSearchDialog(
    state: VoiceSearchUiState,
    onCancel: () -> Unit,
    onRetry: () -> Unit
) {
    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Spacer(modifier = Modifier.weight(1f))
                    IconButton(onClick = onCancel, modifier = Modifier.size(36.dp)) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Close voice search",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                PulsingMic(active = state.isListening && state.error == null)
                Spacer(modifier = Modifier.height(18.dp))
                VoiceWaveform(
                    level = state.rms,
                    active = state.isListening && state.error == null
                )
                Spacer(modifier = Modifier.height(18.dp))

                val headline = when {
                    state.error != null -> "Couldn't hear you"
                    state.partialText.isNotBlank() -> state.partialText
                    else -> "Listening…"
                }
                Text(
                    text = headline,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = state.error ?: "Say a song, artist, or lyric",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                if (state.error != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    TextButton(onClick = onRetry) {
                        Text("Try again")
                    }
                }
            }
        }
    }
}

@Composable
private fun PulsingMic(active: Boolean) {
    val transition = rememberInfiniteTransition(label = "micPulse")
    val pulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse"
    )
    val scale = if (active) 1f + pulse * 0.18f else 1f
    val ringAlpha = if (active) (1f - pulse) * 0.35f else 0f
    val ringColor = MaterialTheme.colorScheme.primary

    Box(
        modifier = Modifier.size(132.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(132.dp)) {
            val radius = size.minDimension / 2f
            drawCircle(
                color = ringColor.copy(alpha = ringAlpha),
                radius = radius * (0.55f + pulse * 0.45f)
            )
        }
        Box(
            modifier = Modifier
                .size(84.dp)
                .scale(scale)
                .background(
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Mic,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(38.dp)
            )
        }
    }
}

@Composable
private fun VoiceWaveform(level: Float, active: Boolean) {
    val transition = rememberInfiniteTransition(label = "wave")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )
    val bars = 9
    val base = if (active) level.coerceIn(0f, 1f) else 0f

    Row(
        modifier = Modifier.height(34.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(bars) { index ->
            val wave = if (active) {
                val offset = (index / bars.toFloat() + phase) % 1f
                val bump = 1f - kotlin.math.abs(offset - 0.5f) * 2f
                (0.18f + base * 0.7f + bump * 0.28f).coerceIn(0.15f, 1f)
            } else {
                0.15f
            }
            Box(
                modifier = Modifier
                    .width(5.dp)
                    .height(34.dp * wave)
                    .graphicsLayer { alpha = if (active) 0.85f else 0.4f }
                    .background(
                        MaterialTheme.colorScheme.primary,
                        RoundedCornerShape(50)
                    )
            )
        }
    }
}
