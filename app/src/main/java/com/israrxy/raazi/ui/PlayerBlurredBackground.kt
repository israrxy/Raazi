package com.israrxy.raazi.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.israrxy.raazi.model.MusicItem
import com.israrxy.raazi.ui.theme.PureBlack
import com.israrxy.raazi.ui.theme.Zinc900

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
