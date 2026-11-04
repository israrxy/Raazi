package com.israrxy.raazi.ui.player

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import com.israrxy.raazi.ui.PlayerScreen
import com.israrxy.raazi.viewmodel.MusicPlayerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpandingPlayer(
    viewModel: MusicPlayerViewModel,
    navController: androidx.navigation.NavController,
    isExpanded: Boolean,
    onExpand: () -> Unit,
    onCollapse: () -> Unit,
    onCollapseToFloating: (() -> Unit)?,
    isDocked: Boolean,
    collapsedBottomPadding: Dp,
    isLyricsVisible: Boolean,
    onLyricsVisibilityChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val progress by animateFloatAsState(
        targetValue = if (isExpanded) 1f else 0f,
        animationSpec = spring(
            dampingRatio = 0.88f,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "expanding_player_progress"
    )

    if (progress <= 0.001f && !isExpanded) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = if (isDocked) 0.dp else 12.dp)
                .padding(bottom = collapsedBottomPadding)
                .pointerInput(Unit) {
                    var totalDrag = 0f
                    detectVerticalDragGestures(
                        onDragStart = { totalDrag = 0f },
                        onVerticalDrag = { _, dragAmount ->
                            totalDrag += dragAmount
                            if (totalDrag < -20f) {
                                onExpand()
                            }
                        }
                    )
                }
        ) {
            MiniPlayer(
                viewModel = viewModel,
                onNavigateToPlayer = onExpand,
                onCollapseToFloating = onCollapseToFloating,
                isDocked = isDocked,
                modifier = Modifier.fillMaxWidth()
            )
        }
        return
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val screenHeight = maxHeight
        val density = LocalDensity.current
        val screenHeightPx = with(density) { screenHeight.toPx() }
        val collapsedBottomPaddingPx = with(density) { collapsedBottomPadding.toPx() }
        val miniPlayerHeightPx = with(density) { 64.dp.toPx() }

        val miniPlayerTopY = screenHeightPx - collapsedBottomPaddingPx - miniPlayerHeightPx

        var manualDragOffset by remember(isExpanded) { mutableFloatStateOf(0f) }
        val baseTranslationY = (miniPlayerTopY * (1f - progress)).coerceAtLeast(0f)
        val currentTranslationY = (baseTranslationY + manualDragOffset).coerceIn(0f, screenHeightPx)

        val cornerRadius = lerp(if (isDocked) 0.dp else 16.dp, 0.dp, progress)
        val sheetShape = RoundedCornerShape(cornerRadius)

        // MiniPlayer crossfade during early transition
        if (progress < 0.5f) {
            val miniAlpha = (1f - progress * 2.2f).coerceIn(0f, 1f)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = if (isDocked) 0.dp else 12.dp)
                    .padding(bottom = collapsedBottomPadding)
                    .graphicsLayer { alpha = miniAlpha }
            ) {
                MiniPlayer(
                    viewModel = viewModel,
                    onNavigateToPlayer = onExpand,
                    onCollapseToFloating = onCollapseToFloating,
                    isDocked = isDocked,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Full player sheet: Always fillMaxSize(), translated via graphicsLayer (YouTube Music architecture)
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationY = currentTranslationY
                }
                .clip(sheetShape),
            shape = sheetShape,
            color = MaterialTheme.colorScheme.background,
            tonalElevation = 0.dp,
            shadowElevation = if (progress < 1f) 12.dp else 0.dp
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                PlayerScreen(
                    viewModel = viewModel,
                    navController = navController,
                    onCollapse = onCollapse,
                    isLyricsVisible = isLyricsVisible,
                    onLyricsVisibilityChange = onLyricsVisibilityChange
                )

                if (isExpanded) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp)
                            .align(Alignment.TopCenter)
                            .pointerInput(Unit) {
                                detectVerticalDragGestures(
                                    onDragStart = { manualDragOffset = 0f },
                                    onDragEnd = {
                                        if (manualDragOffset > 80f) {
                                            onCollapse()
                                        }
                                        manualDragOffset = 0f
                                    },
                                    onDragCancel = { manualDragOffset = 0f },
                                    onVerticalDrag = { _, dragAmount ->
                                        if (dragAmount > 0f || manualDragOffset > 0f) {
                                            manualDragOffset = (manualDragOffset + dragAmount).coerceAtLeast(0f)
                                        }
                                    }
                                )
                            }
                    )
                }
            }
        }
    }
}
