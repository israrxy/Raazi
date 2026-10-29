package com.israrxy.raazi.ui.playlist

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.israrxy.raazi.model.Playlist
import com.israrxy.raazi.ui.theme.Emerald500

/**
 * Spotify-style Hero Header for Playlists featuring dynamic atmospheric background,
 * fading cover artwork, metadata badges, quick in-line search, and signature playback controls.
 */
@Composable
internal fun PlaylistHeroHeader(
    playlist: Playlist,
    displayDescription: String,
    coverUrl: String,
    dominantColor: Color,
    scrollOffset: Float,
    trackCount: Int,
    durationSummary: String,
    isLocalPlaylist: Boolean,
    isSyncedWithYouTube: Boolean,
    isEditablePlaylist: Boolean,
    isSaved: Boolean,
    isYouTubeLoggedIn: Boolean,
    isThisPlaylistPlaying: Boolean,
    isShuffleEnabled: Boolean,
    isReorderMode: Boolean,
    showSearchBar: Boolean,
    searchQuery: String,
    currentSortOrder: PlaylistSortOrder,
    downloadCount: Int,
    onSearchToggle: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onClearSearch: () -> Unit,
    onPlayPauseClick: () -> Unit,
    onShuffleClick: () -> Unit,
    onDownloadAllClick: () -> Unit,
    onToggleSaveClick: () -> Unit,
    onSyncToYouTubeClick: () -> Unit,
    onToggleReorderClick: () -> Unit,
    onOpenSortSheet: () -> Unit,
    onSharePlaylistClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        dominantColor.copy(alpha = 0.55f),
                        dominantColor.copy(alpha = 0.22f),
                        MaterialTheme.colorScheme.background.copy(alpha = 0.85f),
                        MaterialTheme.colorScheme.background
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(top = 48.dp, start = 20.dp, end = 20.dp, bottom = 8.dp)
        ) {
            // Spotify-style Fading Cover Art with parallax & bottom blend mask
            PlaylistHeroCover(
                coverUrl = coverUrl,
                playlistTitle = playlist.title,
                dominantColor = dominantColor,
                scrollOffset = scrollOffset,
                fadeThresholdPx = 520f
            )

            Spacer(modifier = Modifier.height(16.dp))

            // In-line Expandable Search Bar
            AnimatedVisibility(
                visible = showSearchBar,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    placeholder = { Text("Find songs in playlist...") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = Emerald500) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = onClearSearch) {
                                Icon(Icons.Filled.Close, contentDescription = "Clear search")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Emerald500,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.7f),
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onClearSearch() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                )
            }

            // Playlist Title
            Text(
                text = playlist.title,
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.6).sp
                ),
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            if (displayDescription.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = displayDescription,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Metadata Badges (YouTube Sync / Song Count / Total Duration)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = if (isSyncedWithYouTube) {
                        Emerald500.copy(alpha = 0.18f)
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHighest
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = if (isSyncedWithYouTube) Icons.Filled.CloudDone else Icons.AutoMirrored.Filled.QueueMusic,
                            contentDescription = null,
                            tint = if (isSyncedWithYouTube) Emerald500 else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = if (isSyncedWithYouTube) "YouTube Music" else if (isLocalPlaylist) "Local Playlist" else "Playlist",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isSyncedWithYouTube) Emerald500 else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Text(
                    text = buildString {
                        append("$trackCount ${if (trackCount == 1) "track" else "tracks"}")
                        if (durationSummary.isNotBlank()) {
                            append(" • ")
                            append(durationSummary)
                        }
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Spotify Signature Action Bar Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left Utility Buttons
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Download All Button
                    BadgedBox(
                        badge = {
                            if (downloadCount > 0) {
                                Badge(containerColor = Emerald500, contentColor = Color.Black) {
                                    Text(downloadCount.toString(), fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    ) {
                        IconButton(
                            onClick = onDownloadAllClick,
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.DownloadForOffline,
                                contentDescription = "Download playlist",
                                tint = if (downloadCount > 0) Emerald500 else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    // Save to Library or Sync with YouTube
                    if (!isLocalPlaylist) {
                        IconButton(
                            onClick = onToggleSaveClick,
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isSaved) Emerald500.copy(alpha = 0.2f)
                                    else MaterialTheme.colorScheme.surfaceContainerHigh
                                )
                        ) {
                            Icon(
                                imageVector = if (isSaved) Icons.Filled.BookmarkAdded else Icons.Outlined.BookmarkAdd,
                                contentDescription = if (isSaved) "Saved" else "Save to library",
                                tint = if (isSaved) Emerald500 else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    } else if (isYouTubeLoggedIn && !isSyncedWithYouTube) {
                        IconButton(
                            onClick = onSyncToYouTubeClick,
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.CloudUpload,
                                contentDescription = "Sync to YouTube Music",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    // Sort Order Button
                    IconButton(
                        onClick = onOpenSortSheet,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(
                                if (currentSortOrder != PlaylistSortOrder.DEFAULT) Emerald500.copy(alpha = 0.2f)
                                else MaterialTheme.colorScheme.surfaceContainerHigh
                            )
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Sort,
                            contentDescription = "Sort tracks",
                            tint = if (currentSortOrder != PlaylistSortOrder.DEFAULT) Emerald500 else MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // Reorder Tracks (if editable)
                    if (isEditablePlaylist && trackCount > 1) {
                        IconButton(
                            onClick = onToggleReorderClick,
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isReorderMode) Emerald500.copy(alpha = 0.22f)
                                    else MaterialTheme.colorScheme.surfaceContainerHigh
                                )
                        ) {
                            Icon(
                                imageVector = Icons.Filled.SwapVert,
                                contentDescription = "Reorder mode",
                                tint = if (isReorderMode) Emerald500 else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    // Share Button
                    IconButton(
                        onClick = onSharePlaylistClick,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Share,
                            contentDescription = "Share playlist",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // Right Playback Action Buttons
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Shuffle Toggle
                    IconButton(
                        onClick = onShuffleClick,
                        enabled = trackCount > 0,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Shuffle,
                            contentDescription = "Shuffle",
                            tint = if (isShuffleEnabled) Emerald500 else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    // Large Signature Spotify Play/Pause Button
                    FloatingActionButton(
                        onClick = onPlayPauseClick,
                        shape = CircleShape,
                        containerColor = Emerald500,
                        contentColor = Color.Black,
                        elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 8.dp),
                        modifier = Modifier
                            .size(62.dp)
                            .shadow(16.dp, CircleShape, spotColor = Emerald500.copy(alpha = 0.6f))
                    ) {
                        Icon(
                            imageVector = if (isThisPlaylistPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (isThisPlaylistPlaying) "Pause" else "Play",
                            modifier = Modifier.size(34.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}

@Composable
internal fun PlaylistEmptyPlaceholder(
    searchQuery: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 56.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = if (searchQuery.isNotBlank()) Icons.Outlined.SearchOff else Icons.Outlined.MusicNote,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.size(48.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = if (searchQuery.isNotBlank()) "No songs matching \"$searchQuery\"" else "This playlist is empty",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (searchQuery.isBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Search for songs and tap 'Add to Playlist' to build your collection.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

