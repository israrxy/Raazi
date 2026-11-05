package com.israrxy.raazi.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.OndemandVideo
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.israrxy.raazi.model.PlaybackMediaMode
import com.israrxy.raazi.model.PlaybackState
import com.israrxy.raazi.ui.theme.Emerald500

@Composable
internal fun PlayerTopModeHeader(
    playbackState: PlaybackState,
    isLyricsVisible: Boolean,
    sleepTimerActive: Boolean,
    onCollapse: () -> Unit,
    onLyricsVisibilityChange: (Boolean) -> Unit,
    onSetPlaybackMediaMode: (PlaybackMediaMode) -> Unit,
    onShowOptionsSheet: () -> Unit,
    modifier: Modifier = Modifier
) {
    Spacer(modifier = Modifier.height(8.dp))
    Row(
        modifier = Modifier.fillMaxWidth().then(modifier),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onCollapse,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = "Collapse",
                tint = Color.White,
                modifier = Modifier.size(28.dp)
            )
        }

        // AUDIO / VIDEO / LYRICS — Icon-only minimal mode toggle
        val isVideoMode = playbackState.mediaMode == PlaybackMediaMode.VIDEO && !isLyricsVisible
        val playerModeIndex = when {
            isLyricsVisible -> 2
            isVideoMode -> 1
            else -> 0
        }
        val pillSegmentWidth = 52.dp
        val pillOffset by animateDpAsState(
            targetValue = pillSegmentWidth * playerModeIndex,
            animationSpec = tween(durationMillis = 220),
            label = "pill_offset"
        )

        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .height(38.dp)
                    .width(pillSegmentWidth * 3)
                    .clip(RoundedCornerShape(19.dp))
                    .background(Color.White.copy(alpha = 0.12f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(pillSegmentWidth)
                        .offset(x = pillOffset)
                        .clip(RoundedCornerShape(19.dp))
                        .background(Color.White)
                )
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Audio (Song) Icon
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                if (isLyricsVisible) onLyricsVisibilityChange(false)
                                onSetPlaybackMediaMode(PlaybackMediaMode.AUDIO)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = "Audio Mode",
                            tint = if (playerModeIndex == 0) Color.Black else Color.White.copy(alpha = 0.78f),
                            modifier = Modifier.size(19.dp)
                        )
                    }
                    // Video Icon
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                if (isLyricsVisible) onLyricsVisibilityChange(false)
                                onSetPlaybackMediaMode(PlaybackMediaMode.VIDEO)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.OndemandVideo,
                            contentDescription = "Video Mode",
                            tint = if (playerModeIndex == 1) Color.Black else Color.White.copy(alpha = 0.78f),
                            modifier = Modifier.size(19.dp)
                        )
                    }
                    // Lyrics Icon
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                onLyricsVisibilityChange(!isLyricsVisible)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lyrics,
                            contentDescription = "Lyrics Mode",
                            tint = if (playerModeIndex == 2) Color.Black else Color.White.copy(alpha = 0.78f),
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }
            }
        }

        // Menu Button -> opens the YouTube Music style action sheet
        IconButton(
            onClick = onShowOptionsSheet,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = "Options",
                tint = if (sleepTimerActive) Emerald500 else Color.White,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
