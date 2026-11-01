package com.israrxy.raazi.ui.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.israrxy.raazi.ui.playlist.rememberPlaylistDominantColor
import com.israrxy.raazi.utils.ThumbnailUtils
import com.israrxy.raazi.viewmodel.MusicPlayerViewModel
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun FloatingMiniPlayer(
    viewModel: MusicPlayerViewModel,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier
) {
    val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()
    val track = playbackState.currentTrack ?: return
    val isPlaying = playbackState.isPlaying

    val dominantColor = rememberPlaylistDominantColor(
        coverUrl = track.thumbnailUrl,
        fallbackColor = MaterialTheme.colorScheme.primary
    )

    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    val tileSize = 74.dp
    val tileSizePx = with(density) { tileSize.toPx() }
    val marginPx = with(density) { 16.dp.toPx() }
    val topInsetPx = with(density) { 72.dp.toPx() }
    val bottomInsetPx = with(density) { 110.dp.toPx() }

    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }

    val minX = marginPx
    val maxX = (screenWidthPx - tileSizePx - marginPx).coerceAtLeast(minX)
    val minY = topInsetPx
    val maxY = (screenHeightPx - tileSizePx - bottomInsetPx).coerceAtLeast(minY)

    val offsetX = remember { Animatable(maxX) }
    val offsetY = remember { Animatable(maxY) }
    val animScale = remember { Animatable(0.7f) }

    LaunchedEffect(Unit) {
        animScale.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium
            )
        )
    }

    LaunchedEffect(maxX, maxY) {
        if (offsetX.value > maxX) offsetX.snapTo(maxX)
        if (offsetY.value > maxY) offsetY.snapTo(maxY)
    }

    val tileShape = RoundedCornerShape(14.dp)

    Box(
        modifier = modifier
            .offset { IntOffset(offsetX.value.roundToInt(), offsetY.value.roundToInt()) }
            .size(tileSize)
            .graphicsLayer {
                scaleX = animScale.value
                scaleY = animScale.value
            }
            .shadow(10.dp, tileShape)
            .clip(tileShape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.5.dp, dominantColor.copy(alpha = 0.85f), tileShape)
            .pointerInput(track.id, minX, maxX, minY, maxY) {
                detectDragGestures(
                    onDrag = { change, dragAmount ->
                        change.consume()
                        scope.launch {
                            offsetX.snapTo((offsetX.value + dragAmount.x).coerceIn(minX, maxX))
                            offsetY.snapTo((offsetY.value + dragAmount.y).coerceIn(minY, maxY))
                        }
                    },
                    onDragEnd = {
                        val currentX = offsetX.value
                        val currentY = offsetY.value

                        val corners = listOf(
                            minX to minY,
                            maxX to minY,
                            minX to maxY,
                            maxX to maxY
                        )
                        val closest = corners.minByOrNull { (cx, cy) ->
                            val dx = currentX - cx
                            val dy = currentY - cy
                            dx * dx + dy * dy
                        } ?: (maxX to maxY)

                        scope.launch {
                            launch {
                                offsetX.animateTo(
                                    targetValue = closest.first,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessMedium
                                    )
                                )
                            }
                            launch {
                                offsetY.animateTo(
                                    targetValue = closest.second,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessMedium
                                    )
                                )
                            }
                        }
                    }
                )
            }
            .clickable(onClick = onExpand)
    ) {
        AsyncImage(
            model = ThumbnailUtils.getHighQualityThumbnail(track.thumbnailUrl),
            contentDescription = track.title,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.4f),
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.55f)
                        )
                    )
                )
        )

        IconButton(
            onClick = onExpand,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(3.dp)
                .size(26.dp)
                .background(Color.Black.copy(alpha = 0.6f), CircleShape)
        ) {
            Icon(
                imageVector = Icons.Default.OpenInFull,
                contentDescription = "Expand to normal",
                tint = Color.White,
                modifier = Modifier.size(13.dp)
            )
        }

        IconButton(
            onClick = {
                if (isPlaying) viewModel.pause() else viewModel.resume()
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(3.dp)
                .size(26.dp)
                .background(dominantColor, CircleShape)
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (isPlaying) "Pause" else "Play",
                tint = Color.White,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}
