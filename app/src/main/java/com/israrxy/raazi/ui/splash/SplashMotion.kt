package com.israrxy.raazi.ui.splash

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.israrxy.raazi.R
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

internal val SplashBackground = Color(0xFF030303)
internal val SplashEmerald = Color(0xFF34D399)
internal val SplashMint = Color(0xFFA7F3D0)

/**
 * Slow drifting aurora glows near the screen edges. The centre is deliberately
 * left black so the logo bitmap's black background blends in seamlessly.
 */
@Composable
internal fun SplashBackdrop(
    phase: Float,
    modifier: Modifier = Modifier
) {
    Canvas(modifier.fillMaxSize()) {
        val t = phase * 2f * PI.toFloat()
        val w = size.width
        val h = size.height

        fun glow(x: Float, y: Float, radius: Float, color: Color) {
            val center = Offset(x, y)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(color, Color.Transparent),
                    center = center,
                    radius = radius
                ),
                radius = radius,
                center = center
            )
        }

        glow(
            x = w * (0.18f + 0.035f * sin(t.toDouble()).toFloat()),
            y = h * (0.20f + 0.025f * cos(t.toDouble()).toFloat()),
            radius = w * 0.62f,
            color = SplashEmerald.copy(alpha = 0.15f)
        )
        glow(
            x = w * (0.84f + 0.03f * cos((t + 1.2f).toDouble()).toFloat()),
            y = h * (0.80f + 0.03f * sin((t + 0.6f).toDouble()).toFloat()),
            radius = w * 0.58f,
            color = SplashEmerald.copy(alpha = 0.10f)
        )
        glow(
            x = w * (0.88f + 0.02f * sin((t + 2.4f).toDouble()).toFloat()),
            y = h * (0.16f + 0.02f * cos((t + 1.8f).toDouble()).toFloat()),
            radius = w * 0.34f,
            color = SplashMint.copy(alpha = 0.05f)
        )
    }
}

/**
 * Logo disc with an orbit ring, a rotating comet arc and expanding halo rings.
 * Grows from the size the system splash icon is shown at, so the handoff from
 * the Android splash into Compose feels like one continuous animation.
 */
@Composable
internal fun SplashLogo(
    logoScale: Float,
    haloPhase: Float,
    breath: Float,
    haloAlpha: Float,
    modifier: Modifier = Modifier
) {
    Box(modifier.size(344.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val orbit = size.minDimension * 0.384f // ~132dp
            val center = this.center
            val glowAlpha = haloAlpha * (0.55f + 0.45f * breath)

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        SplashEmerald.copy(alpha = 0.20f * glowAlpha),
                        Color.Transparent
                    ),
                    center = center,
                    radius = orbit * 1.55f
                ),
                radius = orbit * 1.55f,
                center = center
            )

            drawCircle(
                color = SplashEmerald.copy(alpha = 0.12f * haloAlpha),
                radius = orbit,
                center = center,
                style = Stroke(width = 1.dp.toPx())
            )

            // Two halo rings expanding outwards and fading away.
            repeat(2) { index ->
                val p = (haloPhase + index * 0.5f) % 1f
                drawCircle(
                    color = SplashEmerald.copy(alpha = 0.22f * (1f - p) * haloAlpha),
                    radius = orbit + 26.dp.toPx() * p,
                    center = center,
                    style = Stroke(width = 1.2.dp.toPx())
                )
            }

            rotate(haloPhase * 360f) {
                drawArc(
                    brush = Brush.sweepGradient(
                        listOf(
                            Color.Transparent,
                            SplashEmerald.copy(alpha = 0.9f * haloAlpha),
                            SplashMint.copy(alpha = haloAlpha),
                            Color.Transparent
                        )
                    ),
                    startAngle = 0f,
                    sweepAngle = 92f,
                    useCenter = false,
                    topLeft = Offset(center.x - orbit, center.y - orbit),
                    size = Size(orbit * 2f, orbit * 2f),
                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                )
            }

            // Comet head at the leading edge of the arc.
            val angle = haloPhase * 2f * PI.toFloat()
            drawCircle(
                color = SplashMint.copy(alpha = 0.95f * haloAlpha),
                radius = 2.4.dp.toPx(),
                center = Offset(
                    center.x + orbit * cos(angle.toDouble()).toFloat(),
                    center.y + orbit * sin(angle.toDouble()).toFloat()
                )
            )
        }

        Image(
            painter = painterResource(id = R.drawable.raazi_logo),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .size(180.dp)
                .graphicsLayer {
                    val scale = logoScale * (1f + 0.012f * breath)
                    scaleX = scale
                    scaleY = scale
                    compositingStrategy = CompositingStrategy.Offscreen
                }
                .drawWithContent {
                    drawContent()
                    // Feather the bitmap's black background into the backdrop so
                    // the glow behind the mark shows through the edges.
                    drawRect(
                        brush = Brush.radialGradient(
                            0f to Color.Black,
                            0.86f to Color.Black,
                            1f to Color.Transparent,
                            center = center,
                            radius = size.minDimension / 2f
                        ),
                        blendMode = BlendMode.DstIn
                    )
                }
        )
    }
}

/** Wordmark revealed letter by letter with a small upward slide. */
@Composable
internal fun SplashWordmark(
    progress: Float,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(0.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        "Raazi".forEachIndexed { index, letter ->
            val p = ((progress - index * 0.075f) / 0.62f).coerceIn(0f, 1f)
            Text(
                text = letter.toString(),
                color = Color.White,
                fontSize = 42.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.sp,
                modifier = Modifier.graphicsLayer {
                    alpha = p
                    translationY = (1f - p) * 26.dp.toPx()
                    scaleX = 0.9f + 0.1f * p
                    scaleY = 0.9f + 0.1f * p
                }
            )
        }
    }
}

/** Tagline that expands its tracking while fading in. */
@Composable
internal fun SplashTagline(
    progress: Float,
    modifier: Modifier = Modifier
) {
    Text(
        text = "MUSIC, YOUR WAY",
        color = SplashMint.copy(alpha = 0.86f),
        fontSize = 10.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (4.6f - 1.1f * progress).sp,
        modifier = modifier.graphicsLayer {
            alpha = progress
            translationY = (1f - progress) * 10.dp.toPx()
        }
    )
}

/** Five bars pulsing like the equalizer in the logo. Doubles as a loader. */
@Composable
internal fun SplashEqualizer(
    phase: Float,
    alpha: Float,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier
            .size(width = 35.dp, height = 18.dp)
            .graphicsLayer { this.alpha = alpha }
    ) {
        val barWidth = 3.dp.toPx()
        val gap = 4.dp.toPx()
        val minHeight = 4.dp.toPx()
        val maxHeight = 15.dp.toPx()
        val corner = barWidth / 2f

        repeat(5) { index ->
            val wave = sin((phase * 2f * PI.toFloat() + index * 0.85f).toDouble())
                .toFloat() * 0.5f + 0.5f
            val barHeight = minHeight + (maxHeight - minHeight) * wave
            drawRoundRect(
                color = SplashEmerald.copy(alpha = 0.55f + 0.35f * wave),
                topLeft = Offset(index * (barWidth + gap), size.height - barHeight),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(corner, corner)
            )
        }
    }
}
