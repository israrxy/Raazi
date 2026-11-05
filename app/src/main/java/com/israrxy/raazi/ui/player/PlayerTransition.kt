package com.israrxy.raazi.ui.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Outline
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

import androidx.compose.ui.layout.LayoutCoordinates

/**
 * Shared transition state for player expansion, morphing the MiniPlayer into full PlayerScreen.
 */
@Stable
class PlayerTransition {
    val expansion: Animatable<Float, AnimationVector1D> = Animatable(0f)

    var dockPlayer: Rect? = null
    var dockArtwork: Rect? = null
    var dockTitle: Rect? = null

    var cardCoordinates by mutableStateOf<LayoutCoordinates?>(null)

    var origin by mutableStateOf<Origin?>(null)
        internal set

    class Origin(val player: Rect?, val artwork: Rect?, val title: Rect?)

    fun captureOrigin() {
        if (origin == null || origin?.artwork == null) {
            val p = dockPlayer
            val a = dockArtwork
            val t = dockTitle
            if (p != null || a != null) {
                origin = Origin(p, a, t)
            }
        }
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
 * Reusable mutable shape to prevent per-frame allocations during drag.
 */
class RevealCardShape(
    var side: Float = 0f,
    var height: Float = 0f,
    var radius: Float = 0f,
    var right: Float = side
) : Shape {
    fun update(side: Float, height: Float, radius: Float, right: Float): RevealCardShape {
        this.side = side
        this.height = height
        this.radius = radius
        this.right = right
        return this
    }

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

    override fun equals(other: Any?): Boolean = false
    override fun hashCode(): Int = System.identityHashCode(this)
}

/**
 * Morphs and flies the album artwork from the MiniPlayer's thumbnail position and scale
 * into its resting position inside the expanded PlayerScreen.
 * Uses card-local resting coordinates and handles both width and height scaling.
 */
/**
 * Morphs and flies a layer (artwork or title) from the MiniPlayer's position and scale
 * into its resting position inside the expanded PlayerScreen.
 */
fun GraphicsLayerScope.flyFrom(
    from: Rect?,
    playerOrigin: Rect?,
    restX: Float,
    restY: Float,
    progress: Float,
    cardTop: Float,
    startScale: Float? = null,
    cornerDp: Dp? = null
): Boolean {
    if (from == null || restX.isNaN() || restY.isNaN() || size.width <= 0f || size.height <= 0f) {
        alpha = if (progress < 0.999f) ((progress - 0.3f) / 0.7f).coerceIn(0f, 1f) else 1f
        scaleX = 1f; scaleY = 1f; translationX = 0f; translationY = 0f
        return false
    }
    if (progress >= 0.999f) {
        alpha = 1f
        scaleX = 1f; scaleY = 1f; translationX = 0f; translationY = 0f
        return false
    }
    alpha = 1f
    val flight = ((progress - 0.04f) / 0.96f).coerceIn(0f, 1f)
    val scaleXVal = lerpFloat(startScale ?: (from.width / size.width).coerceIn(0.01f, 1f), 1f, flight)
    val scaleYVal = lerpFloat(startScale ?: (from.height / size.height).coerceIn(0.01f, 1f), 1f, flight)
    transformOrigin = TransformOrigin(0f, 0f)
    scaleX = scaleXVal
    scaleY = scaleYVal

    val riseBy = playerOrigin?.top ?: 0f
    val startX = from.left
    val currentX = lerpFloat(startX, restX, flight)
    translationX = currentX - restX

    val startY = from.top - riseBy * progress - cardTop
    val currentY = lerpFloat(startY, restY, flight)
    translationY = currentY - restY

    if (cornerDp != null) {
        val cornerProgress = lerpFloat(10.dp.toPx(), cornerDp.toPx(), flight)
        shape = androidx.compose.foundation.shape.RoundedCornerShape(cornerProgress / scaleXVal)
        clip = true
    }
    return true
}

fun GraphicsLayerScope.flyArtwork(
    from: Rect?,
    playerOrigin: Rect?,
    restX: Float,
    restY: Float,
    progress: Float,
    cardTop: Float,
    startCornerDp: Dp = 10.dp,
    endCornerDp: Dp = 20.dp
): Boolean = flyFrom(from, playerOrigin, restX, restY, progress, cardTop, null, endCornerDp)

fun GraphicsLayerScope.flyTitle(
    from: Rect?,
    playerOrigin: Rect?,
    restX: Float,
    restY: Float,
    progress: Float,
    cardTop: Float,
    startScale: Float = 14f / 22f
): Boolean = flyFrom(from, playerOrigin, restX, restY, progress, cardTop, startScale, null)
