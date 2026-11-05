package com.israrxy.raazi.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.israrxy.raazi.model.RepeatMode as PlaybackRepeatMode

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun SpotifyYoutubeLyricItem(
    text: String,
    translation: String?,
    timestampMs: Long,
    words: List<LyricWordSegment> = emptyList(),
    positionProvider: (() -> Long)? = null,
    isCurrent: Boolean,
    isPassed: Boolean,
    distance: Int,
    showTimestampBadge: Boolean,
    onClick: () -> Unit,
    onShareLine: () -> Unit
) {
    val targetAlpha = when {
        isCurrent -> 1f
        showTimestampBadge -> 0.72f
        isPassed -> 0.24f
        distance == 1 -> 0.52f
        distance == 2 -> 0.36f
        else -> 0.24f
    }

    val animatedAlpha by animateFloatAsState(
        targetValue = targetAlpha,
        animationSpec = tween(durationMillis = 280),
        label = "lyric_alpha"
    )

    val targetScale = when {
        isCurrent -> 1.01f
        distance == 1 -> 0.99f
        else -> 0.97f
    }

    val animatedScale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "lyric_scale"
    )

    val containerAlpha by animateFloatAsState(
        targetValue = if (isCurrent) 0.10f else 0f,
        animationSpec = tween(240),
        label = "lyric_bg_alpha"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = animatedAlpha
                scaleX = animatedScale
                scaleY = animatedScale
                transformOrigin = TransformOrigin(0f, 0.5f)
            }
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = containerAlpha))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onShareLine
            )
            .padding(
                horizontal = 12.dp,
                vertical = if (isCurrent) 10.dp else 7.dp
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            val lyricTextStyle = MaterialTheme.typography.headlineMedium.copy(
                fontSize = if (isCurrent) 25.sp else 21.sp,
                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.SemiBold,
                lineHeight = if (isCurrent) 33.sp else 29.sp,
                letterSpacing = (-0.3).sp
            )

            if (isCurrent && words.isNotEmpty() && positionProvider != null) {
                FlowingLyricLine(
                    words = words,
                    positionProvider = positionProvider,
                    textStyle = lyricTextStyle
                )
            } else {
                Text(
                    text = text.ifBlank { "♪" },
                    style = lyricTextStyle,
                    color = Color.White,
                    textAlign = TextAlign.Start
                )
            }

            if (!translation.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = translation,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = if (isCurrent) 15.sp else 14.sp,
                        fontWeight = FontWeight.Normal,
                        lineHeight = 21.sp
                    ),
                    color = Color.White.copy(alpha = if (isCurrent) 0.68f else 0.40f),
                    textAlign = TextAlign.Start
                )
            }
        }

        if (showTimestampBadge) {
            Spacer(modifier = Modifier.width(8.dp))
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = Color.White.copy(alpha = 0.10f)
            ) {
                Text(
                    text = formatLyricsTimestamp(timestampMs),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    color = Color.White.copy(alpha = 0.82f),
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                )
            }
        }
    }
}

@Composable
internal fun MusicalInterludeDots(
    progress: Float,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "interlude_pulse")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.88f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot_pulse"
    )

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Color.White.copy(alpha = 0.07f))
            .padding(horizontal = 14.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(3) { dotIndex ->
            val threshold = (dotIndex + 1) / 3f
            val isLit = progress >= (dotIndex / 3f)
            val dotAlpha = when {
                progress >= threshold -> 0.95f
                isLit -> 0.45f + ((progress - (dotIndex / 3f)) * 1.5f).coerceIn(0f, 0.5f)
                else -> 0.22f
            }
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .graphicsLayer {
                        val s = if (isLit) pulse else 0.92f
                        scaleX = s
                        scaleY = s
                    }
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = dotAlpha))
            )
        }
    }
}

