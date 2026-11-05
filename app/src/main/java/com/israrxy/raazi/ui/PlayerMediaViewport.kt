package com.israrxy.raazi.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.israrxy.raazi.model.MusicItem
import com.israrxy.raazi.model.PlaybackMediaMode
import com.israrxy.raazi.model.PlaybackState
import com.israrxy.raazi.ui.components.shimmerEffect
import com.israrxy.raazi.ui.player.PlayerArtworkImage
import com.israrxy.raazi.ui.player.PlayerTransition
import com.israrxy.raazi.ui.player.flyArtwork
import com.israrxy.raazi.ui.theme.Emerald500
import com.israrxy.raazi.ui.theme.PureBlack
import com.israrxy.raazi.ui.theme.Zinc800
import com.israrxy.raazi.ui.theme.Zinc900
import com.israrxy.raazi.utils.ThumbnailUtils
import com.israrxy.raazi.viewmodel.MusicPlayerViewModel

@Composable
internal fun PlayerBlurredBackground(
    currentTrack: MusicItem?,
    isLoading: Boolean
) {
    AnimatedVisibility(
        visible = true,
        enter = fadeIn(tween(500)),
        exit = fadeOut(tween(250))
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AnimatedContent(
                targetState = currentTrack,
                transitionSpec = {
                    fadeIn(animationSpec = tween(700)) togetherWith fadeOut(animationSpec = tween(700))
                },
                label = "BackgroundTransition"
            ) { track ->
                if (track != null) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        val isAndroid12 = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S

                        AsyncImage(
                            model = if (isAndroid12) {
                                track.thumbnailUrl
                            } else {
                                coil.request.ImageRequest.Builder(LocalContext.current)
                                    .data(track.thumbnailUrl)
                                    .transformations(
                                        com.israrxy.raazi.utils.BlurTransformation(LocalContext.current, radius = 25, sampling = 6f)
                                    )
                                    .build()
                            },
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxSize()
                                .then(if (isAndroid12) Modifier.blur(radius = 60.dp) else Modifier),
                            contentScale = ContentScale.Crop
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.6f))
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        0.0f to Color.Transparent,
                                        0.40f to Color.Black.copy(alpha = 0.25f),
                                        1.0f to Color.Black.copy(alpha = 0.65f)
                                    )
                                )
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                brush = Brush.verticalGradient(
                                    colors = listOf(Zinc900, PureBlack)
                                )
                            )
                    )
                }
            }

            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.4f))
                ) {
                    com.israrxy.raazi.ui.components.SkeletonPlayer()
                }
            }
        }
    }
}

@Composable
internal fun PlayerArtworkOrVideoSection(
    viewModel: MusicPlayerViewModel,
    playbackState: PlaybackState,
    modifier: Modifier = Modifier,
    transition: PlayerTransition? = null,
    cardTop: () -> Float = { 0f },
    artworkSize: Dp = 350.dp,
    dominantColor: Color = Zinc800
) {
    var dragOffset by remember { mutableFloatStateOf(0f) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .then(modifier)
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (dragOffset < -100f) {
                            viewModel.next()
                        } else if (dragOffset > 100f) {
                            viewModel.previous()
                        }
                        dragOffset = 0f
                    }
                ) { change: PointerInputChange, dragAmount: Float ->
                    change.consume()
                    dragOffset += dragAmount
                }
            },
        contentAlignment = Alignment.Center
    ) {
        val track = playbackState.currentTrack
        if (track != null && playbackState.mediaMode == PlaybackMediaMode.VIDEO) {
            var activeSurface by remember { mutableStateOf<android.view.Surface?>(null) }
            var activeTextureView by remember { mutableStateOf<android.view.TextureView?>(null) }
            var videoRatio by remember(track.id) { mutableFloatStateOf(16f / 9f) }
            var hasRenderedFrame by remember(track.id) { mutableStateOf(false) }

            fun applyVideoTransform(textureView: android.view.TextureView?, ratio: Float) {
                val view = textureView ?: return
                val viewWidth = view.width.toFloat()
                val viewHeight = view.height.toFloat()
                if (viewWidth <= 0f || viewHeight <= 0f || ratio <= 0f) return

                val viewAspect = viewWidth / viewHeight
                val scaleX = if (ratio > viewAspect) 1f else ratio / viewAspect
                val scaleY = if (ratio > viewAspect) viewAspect / ratio else 1f
                val matrix = android.graphics.Matrix()
                matrix.setScale(scaleX, scaleY, viewWidth / 2f, viewHeight / 2f)
                view.setTransform(matrix)
                view.invalidate()
            }

            DisposableEffect(viewModel, track.id) {
                val player = viewModel.getPlayer()
                val initialSize = player?.videoSize
                if (initialSize != null && initialSize.width > 0 && initialSize.height > 0) {
                    videoRatio = (initialSize.width.toFloat() * initialSize.pixelWidthHeightRatio) / initialSize.height.toFloat()
                    hasRenderedFrame = true
                    applyVideoTransform(activeTextureView, videoRatio)
                }
                val listener = object : androidx.media3.common.Player.Listener {
                    override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                        if (videoSize.width > 0 && videoSize.height > 0) {
                            videoRatio = (videoSize.width.toFloat() * videoSize.pixelWidthHeightRatio) / videoSize.height.toFloat()
                            applyVideoTransform(activeTextureView, videoRatio)
                        }
                    }

                    override fun onRenderedFirstFrame() {
                        hasRenderedFrame = true
                        applyVideoTransform(activeTextureView, videoRatio)
                    }
                }
                player?.addListener(listener)
                onDispose {
                    player?.removeListener(listener)
                    viewModel.clearVideoSurface()
                    activeSurface?.release()
                    activeSurface = null
                    activeTextureView = null
                }
            }

            Box(
                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).shadow(20.dp, RoundedCornerShape(18.dp), spotColor = Color.Black.copy(alpha = 0.45f)).clip(RoundedCornerShape(18.dp)).background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                val showVideoFrame = playbackState.isVideoAvailable && hasRenderedFrame
                AsyncImage(
                    model = ThumbnailUtils.getHighQualityThumbnail(track.thumbnailUrl),
                    contentDescription = track.title,
                    modifier = Modifier
                        .fillMaxSize()
                        .alpha(if (showVideoFrame) 0f else 0.72f),
                    contentScale = ContentScale.Crop
                )

                AndroidView(
                    factory = { ctx ->
                        android.view.TextureView(ctx).apply {
                            isOpaque = false
                            activeTextureView = this
                            surfaceTextureListener = object : android.view.TextureView.SurfaceTextureListener {
                                override fun onSurfaceTextureAvailable(
                                    surfaceTexture: android.graphics.SurfaceTexture,
                                    width: Int,
                                    height: Int
                                ) {
                                    activeSurface?.release()
                                    val newSurface = android.view.Surface(surfaceTexture)
                                    activeSurface = newSurface
                                    viewModel.setVideoSurface(newSurface)
                                    applyVideoTransform(this@apply, videoRatio)
                                }

                                override fun onSurfaceTextureSizeChanged(
                                    surfaceTexture: android.graphics.SurfaceTexture,
                                    width: Int,
                                    height: Int
                                ) {
                                    applyVideoTransform(this@apply, videoRatio)
                                }

                                override fun onSurfaceTextureDestroyed(
                                    surfaceTexture: android.graphics.SurfaceTexture
                                ): Boolean {
                                    viewModel.clearVideoSurface()
                                    activeSurface?.release()
                                    activeSurface = null
                                    return true
                                }

                                override fun onSurfaceTextureUpdated(
                                    surfaceTexture: android.graphics.SurfaceTexture
                                ) {
                                    if (!hasRenderedFrame) {
                                        hasRenderedFrame = true
                                    }
                                }
                            }
                            surfaceTexture?.let { st ->
                                activeSurface?.release()
                                val newSurface = android.view.Surface(st)
                                activeSurface = newSurface
                                viewModel.setVideoSurface(newSurface)
                                applyVideoTransform(this, videoRatio)
                            }
                        }
                    },
                    update = { view ->
                        activeTextureView = view
                        applyVideoTransform(view, videoRatio)
                        if (playbackState.isVideoAvailable && activeSurface != null) {
                            viewModel.setVideoSurface(activeSurface)
                        }
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .alpha(if (showVideoFrame) 1f else 0.01f)
                )

                if (playbackState.isLoading || playbackState.isBuffering) {
                    Box(
                        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = if (showVideoFrame) 0.22f else 0.35f)),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(34.dp), strokeWidth = 2.5.dp)
                    }
                }
            }
        } else {
            val coverRest = remember { floatArrayOf(Float.NaN, Float.NaN) }
            fun updateRest(coords: LayoutCoordinates) {
                val card = transition?.cardCoordinates
                if (card != null && card.isAttached && coords.isAttached) {
                    val local = card.localPositionOf(coords, Offset.Zero)
                    coverRest[0] = local.x; coverRest[1] = local.y
                }
            }
            AnimatedContent(
                targetState = track,
                transitionSpec = {
                    fadeIn(animationSpec = tween(350)) togetherWith fadeOut(animationSpec = tween(350))
                },
                label = "AlbumArtTransition",
                modifier = Modifier
                    .onPlaced { if (it.isAttached) updateRest(it) }
                    .onGloballyPositioned { updateRest(it) }
                    .graphicsLayer {
                        flyArtwork(transition?.origin?.artwork, transition?.origin?.player, coverRest[0], coverRest[1], transition?.expansion?.value ?: 1f, cardTop())
                    }
            ) { targetTrack ->
                if (targetTrack != null) {
                    PlayerArtworkImage(
                        thumbnailUrl = targetTrack.thumbnailUrl,
                        contentDescription = "Album Art",
                        modifier = Modifier
                            .size(artworkSize)
                            .shadow(20.dp, RoundedCornerShape(20.dp))
                            .clip(RoundedCornerShape(20.dp))
                            .background(Zinc800),
                        shape = RoundedCornerShape(20.dp),
                        dominantColor = dominantColor
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(artworkSize)
                            .clip(RoundedCornerShape(20.dp))
                            .shimmerEffect()
                    )
                }
            }
        }
    }
}
