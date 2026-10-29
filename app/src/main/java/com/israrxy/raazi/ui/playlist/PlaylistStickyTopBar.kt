package com.israrxy.raazi.ui.playlist

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.israrxy.raazi.ui.theme.Emerald500

/**
 * Spotify-style Sticky Top Bar that seamlessly morphs from transparent
 * to an atmospheric surface as the user scrolls down the tracklist.
 * Includes a collapsed fading playlist title and a mini floating play button.
 */
@Composable
internal fun PlaylistStickyTopBar(
    playlistTitle: String,
    dominantColor: Color,
    scrollProgress: Float,
    isScrolledPastHero: Boolean,
    isThisPlaylistPlaying: Boolean,
    showSearchBar: Boolean,
    showHeaderMenu: Boolean,
    isEditablePlaylist: Boolean,
    isLocalPlaylist: Boolean,
    onNavigateBack: () -> Unit,
    onPlayPauseClick: () -> Unit,
    onSearchToggle: () -> Unit,
    onShowHeaderMenuChange: (Boolean) -> Unit,
    onSelectTracksClick: () -> Unit,
    onSharePlaylistClick: () -> Unit,
    onRenameClick: () -> Unit,
    onDeletePlaylistClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Smooth background opacity transition from transparent to solid/scrimmed
    val backgroundAlpha = (scrollProgress * 1.2f).coerceIn(0f, 0.95f)
    val barColor = MaterialTheme.colorScheme.surface.copy(alpha = backgroundAlpha)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        barColor,
                        barColor.copy(alpha = (backgroundAlpha * 0.9f).coerceIn(0f, 1f))
                    )
                )
            )
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Back Button
            IconButton(
                onClick = onNavigateBack,
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(
                        MaterialTheme.colorScheme.surfaceContainerHighest.copy(
                            alpha = if (isScrolledPastHero) 0.6f else 0.85f
                        )
                    )
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }

            // Collapsed Sticky Title (Fades and slides in like Spotify)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                androidx.compose.animation.AnimatedVisibility(
                    visible = isScrolledPastHero,
                    enter = fadeIn() + slideInVertically { it / 2 },
                    exit = fadeOut() + slideOutVertically { it / 2 }
                ) {
                    Text(
                        text = playlistTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Right Action Buttons: Mini Play FAB, Search toggle, and More menu
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Spotify-style Mini Play/Pause FAB appearing in top bar when scrolled
                AnimatedVisibility(
                    visible = isScrolledPastHero,
                    enter = fadeIn() + scaleIn(initialScale = 0.6f),
                    exit = fadeOut() + scaleOut(targetScale = 0.6f)
                ) {
                    IconButton(
                        onClick = onPlayPauseClick,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Emerald500)
                    ) {
                        Icon(
                            imageVector = if (isThisPlaylistPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (isThisPlaylistPlaying) "Pause" else "Play",
                            tint = Color.Black,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                // Search in playlist toggle button
                IconButton(
                    onClick = onSearchToggle,
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(
                            if (showSearchBar) Emerald500.copy(alpha = 0.22f)
                            else MaterialTheme.colorScheme.surfaceContainerHighest.copy(
                                alpha = if (isScrolledPastHero) 0.6f else 0.85f
                            )
                        )
                ) {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = "Search in playlist",
                        tint = if (showSearchBar) Emerald500 else MaterialTheme.colorScheme.onSurface
                    )
                }

                // More Options Menu
                Box {
                    IconButton(
                        onClick = { onShowHeaderMenuChange(true) },
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(
                                MaterialTheme.colorScheme.surfaceContainerHighest.copy(
                                    alpha = if (isScrolledPastHero) 0.6f else 0.85f
                                )
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Filled.MoreVert,
                            contentDescription = "More options",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    DropdownMenu(
                        expanded = showHeaderMenu,
                        onDismissRequest = { onShowHeaderMenuChange(false) }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Select tracks") },
                            leadingIcon = { Icon(Icons.Filled.Checklist, contentDescription = null) },
                            onClick = onSelectTracksClick
                        )
                        DropdownMenuItem(
                            text = { Text("Share playlist") },
                            leadingIcon = { Icon(Icons.Filled.Share, contentDescription = null) },
                            onClick = onSharePlaylistClick
                        )
                        if (isEditablePlaylist) {
                            DropdownMenuItem(
                                text = { Text("Rename playlist") },
                                leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                                onClick = onRenameClick
                            )
                        }
                        if (isLocalPlaylist) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        "Delete playlist",
                                        color = MaterialTheme.colorScheme.error
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Filled.Delete,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                },
                                onClick = onDeletePlaylistClick
                            )
                        }
                    }
                }
            }
        }
    }
}
