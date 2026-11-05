package com.israrxy.raazi.ui.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Outline
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/**
 * Shared transition state for player expansion, morphing the MiniPlayer into full PlayerScreen.
 */
@Stable
class PlayerTransition {
    val expansion: Animatable<Float, AnimationVector1D> = Animatable(0f)

    var dockPlayer: Rect? = null
    var dockArtwork: Rect? = null
    var dockTitle: Rect? = null

    var origin by mutableStateOf<Origin?>(null)
        internal set

    class Origin(val player: Rect?, val artwork: Rect?, val title: Rect?)

    fun captureOrigin() {
        if (origin == null) origin = Origin(dockPlayer, dockArtwork, dockTitle)
    }

    fun clearOrigin() {
        origin = null
    }

    fun stage(from: Float, to: Float): Float =
        if (to <= from) 0f else ((expansion.value - from) / (to - from)).coerceIn(0f, 1f)
}

internal fun lerpFloat(start: Float, stop: Float, fraction: Float): Float =
    start + (stop - start) * fraction

internal fun easeOutCubic(t: Float): Float {
    val inv = 1f - t
    return 1f - inv * inv * inv
}

/**
 * Clips the player card overlay to expand from the MiniPlayer capsule into full screen.
 */
class RevealCardShape(
    private val side: Float,
    private val height: Float,
    private val radius: Float,
    private val right: Float = side
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Rounded(
            RoundRect(
                left = side,
                top = 0f,
                right = (size.width - right).coerceAtLeast(side),
                bottom = height.coerceAtMost(size.height),
                cornerRadius = CornerRadius(radius)
            )
        )
}

/**
 * Morphs and flies the album artwork from the MiniPlayer's thumbnail position and scale
 * into its resting position inside the expanded PlayerScreen.
 */
fun GraphicsLayerScope.flyArtwork(
    from: Rect?,
    restX: Float,
    restY: Float,
    progress: Float,
    cardTop: Float,
    startCornerDp: Dp = 10.dp,
    endCornerDp: Dp = 20.dp
): Boolean {
    if (from == null || progress >= 0.999f || restX.isNaN() || size.width <= 0f) {
        scaleX = 1f
        scaleY = 1f
        translationX = 0f
        translationY = 0f
        return false
    }
    val flight = ((progress - 0.02f) / 0.98f).coerceIn(0f, 1f)
    val startScale = (from.width / size.width).coerceIn(0.01f, 1f)
    val scale = lerpFloat(startScale, 1f, flight)
    transformOrigin = TransformOrigin(0f, 0f)
    scaleX = scale
    scaleY = scale
    translationX = lerpFloat(from.left, restX, flight) - restX
    translationY = lerpFloat(from.top - cardTop, restY, flight) - restY
    shape = androidx.compose.foundation.shape.RoundedCornerShape(
        lerpFloat(startCornerDp.toPx(), endCornerDp.toPx(), flight) / scale
    )
    clip = true
    return true
}
