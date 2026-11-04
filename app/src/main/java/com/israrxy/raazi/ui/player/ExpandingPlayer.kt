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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
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
            dampingRatio = 0.86f,
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
        val currentHeight = lerp(64.dp, screenHeight, progress)
        val currentBottomPadding = lerp(collapsedBottomPadding, 0.dp, progress)
        val currentHorizontalPadding = lerp(if (isDocked) 0.dp else 12.dp, 0.dp, progress)
        val currentCornerRadius = lerp(if (isDocked) 0.dp else 16.dp, 0.dp, progress)
        val currentShape = RoundedCornerShape(currentCornerRadius)

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(currentHeight)
                .align(Alignment.BottomCenter)
                .padding(horizontal = currentHorizontalPadding)
                .padding(bottom = currentBottomPadding)
                .clip(currentShape),
            shape = currentShape,
            color = MaterialTheme.colorScheme.background,
            tonalElevation = if (progress < 0.5f) 6.dp else 0.dp,
            shadowElevation = if (progress < 0.5f) (if (isDocked) 0.dp else 8.dp) else 0.dp
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                if (progress < 0.65f) {
                    val miniAlpha = (1f - progress * 2.0f).coerceIn(0f, 1f)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp)
                            .align(Alignment.TopCenter)
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

                if (progress > 0.12f) {
                    val playerAlpha = ((progress - 0.12f) / 0.88f).coerceIn(0f, 1f)
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { alpha = playerAlpha }
                    ) {
                        PlayerScreen(
                            viewModel = viewModel,
                            navController = navController,
                            onCollapse = onCollapse,
                            isLyricsVisible = isLyricsVisible,
                            onLyricsVisibilityChange = onLyricsVisibilityChange
                        )
                    }
                }
            }
        }
    }
}
