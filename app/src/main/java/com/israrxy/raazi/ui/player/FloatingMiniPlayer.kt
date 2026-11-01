package com.israrxy.raazi.ui.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
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

    val bubbleSize = 58.dp
    val bubbleSizePx = with(density) { bubbleSize.toPx() }
    val marginPx = with(density) { 16.dp.toPx() }
    val topInsetPx = with(density) { 72.dp.toPx() }
    val bottomInsetPx = with(density) { 110.dp.toPx() }

    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }

    val minX = marginPx
    val maxX = (screenWidthPx - bubbleSizePx - marginPx).coerceAtLeast(minX)
    val minY = topInsetPx
    val maxY = (screenHeightPx - bubbleSizePx - bottomInsetPx).coerceAtLeast(minY)

    // Initial position: Bottom-Right corner
    val offsetX = remember { Animatable(maxX) }
    val offsetY = remember { Animatable(maxY) }

    LaunchedEffect(maxX, maxY) {
        if (offsetX.value > maxX) offsetX.snapTo(maxX)
        if (offsetY.value > maxY) offsetY.snapTo(maxY)
    }

    Box(
        modifier = modifier
            .offset { IntOffset(offsetX.value.roundToInt(), offsetY.value.roundToInt()) }
            .size(bubbleSize)
            .pointerInput(track.id, minX, maxX, minY, maxY) {
                detectDragGestures(
                    onDrag = { change, dragAmount ->
                        change.consume()
                        scope.launch {
                            offsetX.snapTo(offsetX.value + dragAmount.x)
                            offsetY.snapTo(offsetY.value + dragAmount.y)
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
            .shadow(12.dp, CircleShape)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface)
            .border(2.5.dp, dominantColor, CircleShape)
            .clickable(onClick = onExpand),
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = ThumbnailUtils.getHighQualityThumbnail(track.thumbnailUrl),
            contentDescription = track.title,
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape),
            contentScale = ContentScale.Crop
        )

        Surface(
            modifier = Modifier
                .size(22.dp)
                .align(Alignment.BottomEnd)
                .offset(x = (-2).dp, y = (-2).dp),
            shape = CircleShape,
            color = dominantColor,
            shadowElevation = 4.dp
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable {
                        if (isPlaying) viewModel.pause() else viewModel.resume()
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = Color.White,
                    modifier = Modifier.size(13.dp)
                )
            }
        }
    }
}
