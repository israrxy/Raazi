package com.israrxy.raazi.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.israrxy.raazi.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SplashScreen(onSplashFinished: () -> Unit) {
    val logoScale = remember { Animatable(0.82f) }
    val logoAlpha = remember { Animatable(0f) }
    val textAlpha = remember { Animatable(0f) }
    val textOffset = remember { Animatable(18f) }

    LaunchedEffect(Unit) {
        launch {
            logoScale.animateTo(
                targetValue = 1.04f,
                animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing)
            )
            logoScale.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            )
        }
        launch {
            logoAlpha.animateTo(targetValue = 1f, animationSpec = tween(320))
        }
        launch {
            textAlpha.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 380, delayMillis = 120)
            )
            textOffset.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 380, easing = FastOutSlowInEasing)
            )
        }
        delay(620)
        onSplashFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(Color(0xFF17211F), Color(0xFF08090B)),
                    radius = 900f
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(300.dp)
                .graphicsLayer(alpha = logoAlpha.value * 0.55f)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.28f),
                            Color.Transparent
                        )
                    ),
                    CircleShape
                )
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.raazi_logo),
                contentDescription = "Raazi Logo",
                modifier = Modifier
                    .size(184.dp)
                    .scale(logoScale.value)
                    .clip(CircleShape)
                    .graphicsLayer(alpha = logoAlpha.value),
                contentScale = ContentScale.Crop
            )

            Spacer(Modifier.height(22.dp))

            Text(
                text = "Raazi",
                color = Color.White,
                fontSize = 34.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = (-1).sp,
                modifier = Modifier
                    .offset(y = textOffset.value.dp)
                    .graphicsLayer(alpha = textAlpha.value)
            )

            Spacer(Modifier.height(6.dp))

            Text(
                text = "MUSIC, YOUR WAY",
                color = Color(0xFFA7F3D0).copy(alpha = 0.78f),
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 3.5.sp,
                modifier = Modifier
                    .offset(y = textOffset.value.dp)
                    .graphicsLayer(alpha = textAlpha.value)
            )
        }
    }
}
