package com.israrxy.raazi.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.animation.core.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.israrxy.raazi.ui.theme.*
import com.israrxy.raazi.viewmodel.MusicPlayerViewModel

@Composable
fun MiniPlayer(
    viewModel: MusicPlayerViewModel,
    onNavigateToPlayer: () -> Unit
) {
    val playbackState by viewModel.playbackState.collectAsState()
    val track = playbackState.currentTrack
    val progress = playbackState.currentPosition
    val duration = playbackState.duration
    
    // Animated Visualizer State
    val isPlaying = playbackState.isPlaying

    if (track != null) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .clickable { onNavigateToPlayer() }
        ) {
            // Simplified background - removed heavy dark gradient and shadow as requested
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Zinc900.copy(alpha = 0.9f)) // "Blurry glass" look (high opacity)
                    .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(20.dp))
            ) {
                 // Content Row
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = 12.dp, end = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Album Art with glow effect
                    Surface(
                        modifier = Modifier.size(50.dp),
                        shape = RoundedCornerShape(10.dp),
                        color = Color.Black,
                        shadowElevation = 12.dp
                    ) {
                        AsyncImage(
                            model = track.thumbnailUrl?.replace("w120-h120", "w544-h544")?.replace("=w60-h60", "=w544-h544"),
                            contentDescription = track.title,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    // Track Info & Visualizer
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = track.title,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = track.artist,
                                style = MaterialTheme.typography.bodySmall,
                                color = Zinc400,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            if (isPlaying) {
                                Spacer(modifier = Modifier.width(8.dp))
                                MiniVisualizer(modifier = Modifier.height(12.dp).width(24.dp))
                            }
                        }
                    }

                    // Controls
                    Row(verticalAlignment = Alignment.CenterVertically) {
                         IconButton(
                            onClick = { 
                                if (isPlaying) viewModel.pause() else viewModel.resume() 
                            },
                            modifier = Modifier
                                .size(44.dp)
                                .background(
                                    brush = androidx.compose.ui.graphics.Brush.radialGradient(
                                        colors = listOf(
                                            Color.White.copy(alpha = 0.2f),
                                            Color.White.copy(alpha = 0.05f)
                                        )
                                    ),
                                    shape = androidx.compose.foundation.shape.CircleShape
                                )
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                tint = Color.White,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }
                }
                
                // Bottom Progress Bar with gradient
                if (duration > 0) {
                     Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .height(3.dp)
                            .background(Color.White.copy(alpha = 0.15f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(progress.toFloat() / duration.toFloat())
                                .background(
                                    brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                                        colors = listOf(ElectricViolet, ElectricViolet.copy(alpha = 0.8f))
                                    )
                                )
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MiniVisualizer(modifier: Modifier = Modifier) {
    val barCount = 4
    // Simple random animation for now (simulating audio bars)
    val infiniteTransition = androidx.compose.animation.core.rememberInfiniteTransition(label = "visualizer")
    
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(barCount) { index ->
            val heightPercent by infiniteTransition.animateFloat(
                initialValue = 0.2f,
                targetValue = 1f,
                animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                    animation = androidx.compose.animation.core.tween(
                        durationMillis = 300 + (index * 100),
                        delayMillis = index * 50,
                        easing = androidx.compose.animation.core.FastOutSlowInEasing
                    ),
                    repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
                ), 
                label = "bar_$index"
            )
            
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .fillMaxHeight(heightPercent)
                    .background(ElectricViolet, androidx.compose.foundation.shape.CircleShape)
            )
        }
    }
}
