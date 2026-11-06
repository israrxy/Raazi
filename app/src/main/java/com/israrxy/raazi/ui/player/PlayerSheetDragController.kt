package com.israrxy.raazi.ui.player

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Owns the drag-to-collapse gesture for the full-screen player sheet.
 *
 * The same controller is driven from two places:
 * - the player header (direct pointer drag, works at any scroll position), and
 * - the player content scroll container (nested scroll, collapses once the
 *   content reaches the top).
 */
internal class PlayerSheetDragController(
    private val expansion: Animatable<Float, AnimationVector1D>,
    private val heightPx: Float,
    private val scope: CoroutineScope,
    private val onCollapse: () -> Unit,
    private val onExpand: () -> Unit
) {
    private var dragProgress by mutableFloatStateOf(0f)
    private var totalDragPx = 0f
    private var gestureStartMs = 0L
    private var snapJob: Job? = null

    private var isDragging = false

    fun startDrag() {
        isDragging = true
        gestureStartMs = SystemClock.uptimeMillis()
        totalDragPx = 0f
        dragProgress = expansion.value
    }

    fun dragBy(deltaPx: Float) {
        if (!isDragging) startDrag()
        totalDragPx += deltaPx
        dragProgress = (dragProgress - deltaPx / heightPx).coerceIn(0f, 1f)
        snapJob?.cancel()
        snapJob = scope.launch { expansion.snapTo(dragProgress) }
    }

    fun endDrag(velocityPxPerSecond: Float? = null) {
        if (!isDragging) return
        isDragging = false
        snapJob?.cancel()
        snapJob = null
        val elapsedMs = (SystemClock.uptimeMillis() - gestureStartMs).coerceAtLeast(1L)
        val velocity = velocityPxPerSecond ?: (totalDragPx / (elapsedMs / 1000f))
        if (totalDragPx > 0f && (dragProgress < 0.70f || velocity > 500f)) {
            onCollapse()
        } else {
            onExpand()
        }
    }

    fun cancelDrag() {
        if (!isDragging) return
        isDragging = false
        snapJob?.cancel()
        snapJob = null
        onExpand()
    }

    /** Drag gesture for the player header, which works at any scroll offset. */
    fun dismissModifier(): Modifier = Modifier.pointerInput(heightPx) {
        detectVerticalDragGestures(
            onDragStart = { startDrag() },
            onDragEnd = { endDrag() },
            onDragCancel = { cancelDrag() },
            onVerticalDrag = { change, dragAmount ->
                if (dragProgress >= 0.999f && dragAmount < 0f) {
                    return@detectVerticalDragGestures
                }
                change.consume()
                dragBy(dragAmount)
            }
        )
    }
}

/**
 * Turns downward overscroll at the top of the player content into sheet collapse.
 * Upward scrolling keeps working normally; once a collapse drag has started the
 * same gesture can be reversed to restore the sheet.
 */
internal class PlayerSheetNestedScrollConnection(
    private val isAtTop: () -> Boolean,
    private val onDragStart: () -> Unit,
    private val onDrag: (Float) -> Unit,
    private val onDragEnd: (Float) -> Unit
) : NestedScrollConnection {

    private var collapsing = false

    private fun startIfNeeded() {
        if (!collapsing) {
            collapsing = true
            onDragStart()
        }
    }

    private fun stop(velocityY: Float) {
        if (!collapsing) return
        collapsing = false
        onDragEnd(velocityY)
    }

    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        if (source != NestedScrollSource.Drag) return Offset.Zero
        val dy = available.y
        if (dy > 0f && isAtTop()) {
            startIfNeeded()
            onDrag(dy)
            return Offset(0f, dy)
        }
        if (collapsing && dy < 0f) {
            onDrag(dy)
            return Offset(0f, dy)
        }
        return Offset.Zero
    }

    override fun onPostScroll(
        consumed: Offset,
        available: Offset,
        source: NestedScrollSource
    ): Offset {
        if (source != NestedScrollSource.Drag) return Offset.Zero
        val dy = available.y
        if (dy > 0f && isAtTop()) {
            startIfNeeded()
            onDrag(dy)
            return Offset(0f, dy)
        }
        return Offset.Zero
    }

    override suspend fun onPreFling(available: Velocity): Velocity {
        if (!collapsing) return Velocity.Zero
        stop(available.y)
        return available
    }
}
