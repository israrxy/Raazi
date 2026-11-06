package com.israrxy.raazi.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.israrxy.raazi.ui.splash.SplashBackdrop
import com.israrxy.raazi.ui.splash.SplashBackground
import com.israrxy.raazi.ui.splash.SplashEqualizer
import com.israrxy.raazi.ui.splash.SplashLogo
import com.israrxy.raazi.ui.splash.SplashTagline
import com.israrxy.raazi.ui.splash.SplashWordmark
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Motion splash:
 *
 * 1. Continues the system splash icon at the same size (scale starts at 0.86).
 * 2. The logo lifts into place while the aurora backdrop fades in.
 * 3. Wordmark letters rise in sequence, tagline tracking expands, equalizer appears.
 * 4. The equalizer keeps dancing while settings load, then the whole scene fades
 *    and scales away once [isReady] is true.
 */
@Composable
fun SplashScreen(
    isReady: Boolean,
    onSplashFinished: () -> Unit
) {
    val backdropIn = remember { Animatable(0f) }
    val logoScale = remember { Animatable(0.86f) }
    val lift = remember { Animatable(60f) }
    val wordmark = remember { Animatable(0f) }
    val tagline = remember { Animatable(0f) }
    val equalizerIn = remember { Animatable(0f) }
    val exit = remember { Animatable(0f) }
    var entranceDone by remember { mutableStateOf(false) }

    val infinite = rememberInfiniteTransition(label = "splash-motion")
    val drift by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(12000, easing = LinearEasing)),
        label = "splash-drift"
    )
    val haloPhase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2800, easing = LinearEasing)),
        label = "splash-halo"
    )
    val breath by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "splash-breath"
    )
    val eqPhase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1000, easing = LinearEasing)),
        label = "splash-equalizer"
    )

    LaunchedEffect(Unit) {
        launch { backdropIn.animateTo(1f, tween(600, easing = FastOutSlowInEasing)) }
        launch {
            logoScale.animateTo(1.06f, tween(520, easing = FastOutSlowInEasing))
            logoScale.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            )
        }
        launch { lift.animateTo(0f, tween(760, delayMillis = 60, easing = FastOutSlowInEasing)) }
        launch { wordmark.animateTo(1f, tween(720, delayMillis = 140, easing = FastOutSlowInEasing)) }
        launch { tagline.animateTo(1f, tween(560, delayMillis = 420, easing = FastOutSlowInEasing)) }
        launch { equalizerIn.animateTo(1f, tween(420, delayMillis = 620)) }
        delay(1150)
        entranceDone = true
    }

    LaunchedEffect(isReady, entranceDone) {
        if (isReady && entranceDone) {
            exit.animateTo(1f, tween(340, easing = FastOutSlowInEasing))
            onSplashFinished()
        }
    }

    val haloReveal = ((logoScale.value - 0.87f) / 0.19f).coerceIn(0f, 1f) * backdropIn.value

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SplashBackground)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = 1f - exit.value
                    scaleX = 1f + 0.05f * exit.value
                    scaleY = 1f + 0.05f * exit.value
                }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = backdropIn.value * (1f - 0.35f * exit.value) }
            ) {
                SplashBackdrop(phase = drift)
            }

            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .graphicsLayer { translationY = lift.value.dp.toPx() },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                SplashLogo(
                    logoScale = logoScale.value,
                    haloPhase = haloPhase,
                    breath = breath,
                    haloAlpha = haloReveal
                )
                Spacer(Modifier.height(14.dp))
                SplashWordmark(progress = wordmark.value)
                Spacer(Modifier.height(10.dp))
                SplashTagline(progress = tagline.value)
                Spacer(Modifier.height(22.dp))
                SplashEqualizer(phase = eqPhase, alpha = equalizerIn.value)
            }
        }
    }
}
