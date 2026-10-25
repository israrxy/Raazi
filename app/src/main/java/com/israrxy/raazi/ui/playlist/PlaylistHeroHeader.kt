package com.israrxy.raazi.ui.playlist

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.israrxy.raazi.model.Playlist
import com.israrxy.raazi.ui.theme.Emerald500

@Composable
internal fun PlaylistHeroHeader(
    playlist: Playlist,
    displayDescription: String,
    coverUrl: String,
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
    downloadCount: Int,
    showHeaderMenu: Boolean,
    onShowHeaderMenuChange: (Boolean) -> Unit,
    onNavigateBack: () -> Unit,
    onSearchToggle: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onClearSearch: () -> Unit,
    onPlayPauseClick: () -> Unit,
    onShuffleClick: () -> Unit,
    onDownloadAllClick: () -> Unit,
    onToggleSaveClick: () -> Unit,
    onSyncToYouTubeClick: () -> Unit,
    onToggleReorderClick: () -> Unit,
    onRenameClick: () -> Unit,
    onSelectTracksClick: () -> Unit,
    onDeletePlaylistClick: () -> Unit,
    onSharePlaylistClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            // Top bar with Back, Search, and More menu
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onNavigateBack,
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.55f))
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onSearchToggle,
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(
                                if (showSearchBar) Emerald500.copy(alpha = 0.22f)
                                else MaterialTheme.colorScheme.surface.copy(alpha = 0.55f)
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = "Search in playlist",
                            tint = if (showSearchBar) Emerald500 else MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Box {
                        IconButton(
                            onClick = { onShowHeaderMenuChange(true) },
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.55f))
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

            AnimatedVisibility(
                visible = showSearchBar,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    placeholder = { Text("Find in playlist...") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = onClearSearch) {
                                Icon(Icons.Filled.Close, contentDescription = "Clear search")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onClearSearch() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Elevated Album/Playlist Cover Art
            Box(
                modifier = Modifier
                    .size(216.dp)
                    .align(Alignment.CenterHorizontally)
                    .shadow(24.dp, RoundedCornerShape(20.dp))
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center
            ) {
                if (coverUrl.isNotBlank()) {
                    AsyncImage(
                        model = coverUrl,
                        contentDescription = playlist.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.LibraryMusic,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(72.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(22.dp))

            // Playlist Title
            Text(
                text = playlist.title,
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.5).sp
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

            // Metadata badges (YouTube Music / Local badge + Track count + Duration)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = if (isSyncedWithYouTube) {
                        Emerald500.copy(alpha = 0.16f)
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHighest
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = if (isSyncedWithYouTube) Icons.Filled.CloudDone else Icons.AutoMirrored.Filled.QueueMusic,
                            contentDescription = null,
                            tint = if (isSyncedWithYouTube) Emerald500 else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = if (isSyncedWithYouTube) "YouTube Music" else if (isLocalPlaylist) "Your Playlist" else "Playlist",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isSyncedWithYouTube) Emerald500 else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Text(
                    text = buildString {
                        append("$trackCount ${if (trackCount == 1) "song" else "songs"}")
                        if (durationSummary.isNotBlank()) {
                            append(" • ")
                            append(durationSummary)
                        }
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Spotify / YT Music Action Bar Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left Utility Action Icons (Download, Save/Sync, Reorder, Share)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Download All Button
                    BadgedBox(
                        badge = {
                            if (downloadCount > 0) {
                                Badge(containerColor = Emerald500, contentColor = Color.White) {
                                    Text(downloadCount.toString())
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

                    // Save remote playlist OR Sync local playlist to YouTube Music
                    if (!isLocalPlaylist) {
                        IconButton(
                            onClick = onToggleSaveClick,
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isSaved) Emerald500.copy(alpha = 0.18f)
                                    else MaterialTheme.colorScheme.surfaceContainerHigh
                                )
                        ) {
                            Icon(
                                imageVector = if (isSaved) Icons.Filled.BookmarkAdded else Icons.Outlined.BookmarkAdd,
                                contentDescription = if (isSaved) "Saved in Library" else "Save to Library",
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

                    // Reorder Tracks Toggle (for editable playlists)
                    if (isEditablePlaylist && trackCount > 1) {
                        IconButton(
                            onClick = onToggleReorderClick,
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isReorderMode) Emerald500.copy(alpha = 0.2f)
                                    else MaterialTheme.colorScheme.surfaceContainerHigh
                                )
                        ) {
                            Icon(
                                imageVector = Icons.Filled.SwapVert,
                                contentDescription = "Reorder tracks",
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

                // Right Playback Controls (Distinct Shuffle Button + Big Play/Pause FAB)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Shuffle Button with distinct active color & dot indicator
                    Box(contentAlignment = Alignment.Center) {
                        IconButton(
                            onClick = onShuffleClick,
                            enabled = trackCount > 0,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isShuffleEnabled) Emerald500.copy(alpha = 0.22f)
                                    else MaterialTheme.colorScheme.surfaceContainerHigh
                                )
                                .then(
                                    if (isShuffleEnabled) {
                                        Modifier.border(1.5.dp, Emerald500, CircleShape)
                                    } else {
                                        Modifier
                                    }
                                )
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Shuffle,
                                contentDescription = "Shuffle play",
                                tint = if (isShuffleEnabled) Emerald500 else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        if (isShuffleEnabled) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = 5.dp)
                                    .size(5.dp)
                                    .clip(CircleShape)
                                    .background(Emerald500)
                            )
                        }
                    }

                    // Large Primary Play/Pause Button (Spotify / YT Music signature button)
                    FloatingActionButton(
                        onClick = onPlayPauseClick,
                        shape = CircleShape,
                        containerColor = Emerald500,
                        contentColor = Color.Black,
                        modifier = Modifier.size(60.dp)
                    ) {
                        Icon(
                            imageVector = if (isThisPlaylistPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (isThisPlaylistPlaying) "Pause playlist" else "Play playlist",
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}
