package com.israrxy.raazi.ui.library

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Album
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.QueueMusic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.clip
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.israrxy.raazi.data.playlist.isYouTubeEditablePlaylist
import com.israrxy.raazi.data.playlist.isYouTubeSyncedPlaylist
import com.israrxy.raazi.model.MusicContentType
import com.israrxy.raazi.model.MusicItem
import com.israrxy.raazi.model.SavedCollectionItem
import com.israrxy.raazi.ui.theme.*
import com.israrxy.raazi.viewmodel.MusicPlayerViewModel

@Composable
fun LibraryScreen(
    viewModel: MusicPlayerViewModel,
    onNavigateToPlayer: () -> Unit,
    onNavigateToPlaylist: (String) -> Unit,
    onNavigateToDownloads: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onNavigateToArtist: (String, String) -> Unit = { _, _ -> }
) {
    var selectedTab by remember { mutableIntStateOf(1) }
    val favoriteTracks by viewModel.favoriteTracks.collectAsStateWithLifecycle()
    val downloadedTracks by viewModel.downloadedTracks.collectAsStateWithLifecycle()
    val playbackHistory by viewModel.playbackHistory.collectAsStateWithLifecycle(initialValue = emptyList())
    val dbActiveDownloads by viewModel.dbActiveDownloads.collectAsStateWithLifecycle()
    val dbCompletedDownloads by viewModel.dbCompletedDownloads.collectAsStateWithLifecycle()
    val userPlaylists by viewModel.userPlaylists.collectAsStateWithLifecycle(initialValue = emptyList())
    val savedCollections by viewModel.savedCollections.collectAsStateWithLifecycle()
    val isYouTubeLoggedIn by viewModel.isYouTubeLoggedIn.collectAsStateWithLifecycle()
    val youTubeAccountName by viewModel.youTubeAccountName.collectAsStateWithLifecycle()
    val isSyncingYouTubeLibrary by viewModel.isSyncingYouTubeLibrary.collectAsStateWithLifecycle()
    val youTubeSyncStatus by viewModel.youTubeSyncStatus.collectAsStateWithLifecycle()

    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }
    var createSyncedPlaylist by remember { mutableStateOf(false) }
    var libraryQuery by remember { mutableStateOf("") }
    var selectedSavedFilter by remember { mutableStateOf<MusicContentType?>(null) }
    val context = LocalContext.current

    val normalizedLibraryQuery = remember(libraryQuery) { libraryQuery.trim() }
    val filteredPlaylists = remember(userPlaylists, normalizedLibraryQuery) {
        userPlaylists.filter { playlist ->
            normalizedLibraryQuery.isBlank() ||
                playlist.title.contains(normalizedLibraryQuery, ignoreCase = true) ||
                playlist.description.contains(normalizedLibraryQuery, ignoreCase = true)
        }
    }
    val filteredSavedCollections = remember(savedCollections, normalizedLibraryQuery, selectedSavedFilter) {
        savedCollections.filter { item ->
            val matchesQuery = normalizedLibraryQuery.isBlank() ||
                item.title.contains(normalizedLibraryQuery, ignoreCase = true) ||
                item.subtitle.contains(normalizedLibraryQuery, ignoreCase = true)
            val matchesType = selectedSavedFilter == null || item.contentType == selectedSavedFilter
            matchesQuery && matchesType
        }
    }
    val groupedSavedCollections = remember(filteredSavedCollections) {
        filteredSavedCollections.groupBy { it.contentType }
    }
    val savedArtistsCount = remember(savedCollections) {
        savedCollections.count { it.contentType == MusicContentType.ARTIST }
    }
    val savedAlbumsCount = remember(savedCollections) {
        savedCollections.count { it.contentType == MusicContentType.ALBUM }
    }
    val savedPlaylistCount = remember(userPlaylists, savedCollections) {
        userPlaylists.size + savedCollections.count { it.contentType == MusicContentType.PLAYLIST }
    }
    val hasSavedResults = filteredPlaylists.isNotEmpty() || filteredSavedCollections.isNotEmpty()

    LaunchedEffect(youTubeSyncStatus) {
        val status = youTubeSyncStatus ?: return@LaunchedEffect
        Toast.makeText(context, status, Toast.LENGTH_SHORT).show()
        viewModel.clearYouTubeSyncStatus()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        // HEADER
        LibraryHeader(onNavigateToSettings = onNavigateToSettings)

        // TABS
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            LibraryTab("Saved", selectedTab == 1) { selectedTab = 1 }
            LibraryTab("Downloads", selectedTab == 0) { selectedTab = 0 }
            LibraryTab("History", selectedTab == 2) { selectedTab = 2 }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 140.dp)
        ) {
            if (selectedTab == 1) {
                item {
                    LibraryOverviewCard(
                        likedSongsCount = favoriteTracks.size,
                        playlistCount = savedPlaylistCount,
                        artistCount = savedArtistsCount,
                        albumCount = savedAlbumsCount,
                        downloadsCount = downloadedTracks.size
                    )
                }

                item {
                    YouTubeAccountCard(
                        isLoggedIn = isYouTubeLoggedIn,
                        accountName = youTubeAccountName,
                        isSyncing = isSyncingYouTubeLibrary,
                        onSync = { viewModel.syncYouTubeLibrary() },
                        onOpenSettings = onNavigateToSettings
                    )
                }

                item {
                    LikedSongsBanner(
                        count = favoriteTracks.size,
                        thumbnailUrl = favoriteTracks.firstOrNull()?.thumbnailUrl,
                        onClick = { onNavigateToPlaylist("favorites") }
                    )
                }

                item {
                    CreatePlaylistButton(onClick = { showCreatePlaylistDialog = true })
                }

                item {
                    LibrarySearchBar(
                        query = libraryQuery,
                        onQueryChange = { libraryQuery = it }
                    )
                }

                item {
                    SavedLibraryFilters(
                        selectedType = selectedSavedFilter,
                        onSelectType = { selectedSavedFilter = it },
                        playlistCount = savedPlaylistCount,
                        artistCount = savedArtistsCount,
                        albumCount = savedAlbumsCount
                    )
                }

                if ((selectedSavedFilter == null || selectedSavedFilter == MusicContentType.PLAYLIST) && filteredPlaylists.isNotEmpty()) {
                    item { SectionHeader("Your Playlists") }
                    items(filteredPlaylists) { playlist ->
                        PlaylistItemCard(
                            playlist = playlist,
                            onClick = { onNavigateToPlaylist(playlist.id) }
                        )
                    }
                }

                if ((selectedSavedFilter == null || selectedSavedFilter == MusicContentType.PLAYLIST) &&
                    groupedSavedCollections[MusicContentType.PLAYLIST].orEmpty().isNotEmpty()
                ) {
                    item { SectionHeader("Saved Playlists") }
                    items(groupedSavedCollections[MusicContentType.PLAYLIST].orEmpty()) { savedItem ->
                        SavedCollectionCard(
                            item = savedItem,
                            onClick = { onNavigateToPlaylist(savedItem.sourceId) },
                            onRemove = {
                                viewModel.toggleSavedCollection(
                                    MusicItem(
                                        id = savedItem.sourceId,
                                        title = savedItem.title,
                                        artist = savedItem.subtitle,
                                        duration = 0L,
                                        thumbnailUrl = savedItem.thumbnailUrl,
                                        audioUrl = "",
                                        videoUrl = "",
                                        isPlaylist = true,
                                        contentType = MusicContentType.PLAYLIST
                                    )
                                )
                            }
                        )
                    }
                }

                if ((selectedSavedFilter == null || selectedSavedFilter == MusicContentType.ALBUM) &&
                    groupedSavedCollections[MusicContentType.ALBUM].orEmpty().isNotEmpty()
                ) {
                    item { SectionHeader("Saved Albums") }
                    items(groupedSavedCollections[MusicContentType.ALBUM].orEmpty()) { savedItem ->
                        SavedCollectionCard(
                            item = savedItem,
                            onClick = { onNavigateToPlaylist(savedItem.sourceId) },
                            onRemove = {
                                viewModel.toggleSavedCollection(
                                    MusicItem(
                                        id = savedItem.sourceId,
                                        title = savedItem.title,
                                        artist = savedItem.subtitle,
                                        duration = 0L,
                                        thumbnailUrl = savedItem.thumbnailUrl,
                                        audioUrl = "",
                                        videoUrl = "",
                                        contentType = MusicContentType.ALBUM
                                    )
                                )
                            }
                        )
                    }
                }

                if ((selectedSavedFilter == null || selectedSavedFilter == MusicContentType.ARTIST) &&
                    groupedSavedCollections[MusicContentType.ARTIST].orEmpty().isNotEmpty()
                ) {
                    item { SectionHeader("Saved Artists") }
                    items(groupedSavedCollections[MusicContentType.ARTIST].orEmpty()) { savedItem ->
                        SavedCollectionCard(
                            item = savedItem,
                            onClick = { onNavigateToArtist(savedItem.sourceId, savedItem.title) },
                            onRemove = {
                                viewModel.toggleSavedArtist(
                                    artistId = savedItem.sourceId,
                                    artistName = savedItem.title,
                                    thumbnailUrl = savedItem.thumbnailUrl
                                )
                            }
                        )
                    }
                }

                if (!hasSavedResults) {
                    item {
                        EmptyState(
                            icon = Icons.Default.Bookmark,
                            message = if (normalizedLibraryQuery.isBlank()) "Nothing saved yet" else "No library matches",
                            subMessage = if (normalizedLibraryQuery.isBlank()) {
                                "Save artists, albums, and playlists from search or detail screens."
                            } else {
                                "Try a different search or filter."
                            }
                        )
                    }
                }
            }

            if (selectedTab == 0) {
                if (dbActiveDownloads.isNotEmpty()) {
                    item { SectionHeader("Downloading") }
                    items(dbActiveDownloads) { download ->
                        DownloadItem(
                            com.israrxy.raazi.model.MusicItem(
                                id = download.trackId,
                                title = download.title,
                                artist = download.artist,
                                thumbnailUrl = download.thumbnailUrl,
                                audioUrl = download.audioUrl,
                                videoUrl = download.videoUrl,
                                duration = download.duration
                            ),
                            download.progress
                        )
                    }
                }

                if (dbCompletedDownloads.isNotEmpty()) {
                    item { SectionHeader("Downloaded") }
                    items(dbCompletedDownloads) { download ->
                        val track = com.israrxy.raazi.model.MusicItem(
                            id = download.trackId,
                            title = download.title,
                            artist = download.artist,
                            thumbnailUrl = download.thumbnailUrl,
                            audioUrl = download.audioUrl,
                            videoUrl = download.videoUrl,
                            duration = download.duration,
                            localPath = download.filePath
                        )
                        LibraryTrackItem(
                            track = track,
                            onClick = {
                                viewModel.playMusic(track)
                                onNavigateToPlayer()
                            },
                            onAction = { },
                            actionIcon = Icons.Default.CheckCircle
                        )
                    }
                } else if (dbActiveDownloads.isEmpty()) {
                    item {
                        EmptyState(
                            icon = Icons.Default.Download,
                            message = "No downloads yet",
                            subMessage = "Downloaded songs will appear here"
                        )
                    }
                }

                // Manage Downloads button
                if (dbActiveDownloads.isNotEmpty() || dbCompletedDownloads.isNotEmpty()) {
                    item {
                        androidx.compose.material3.TextButton(
                            onClick = onNavigateToDownloads,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Icon(Icons.Default.OpenInNew, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Manage Downloads")
                        }
                    }
                }
            }

            if (selectedTab == 2) {
                if (playbackHistory.isNotEmpty()) {
                    itemsIndexed(playbackHistory) { index, track ->
                        LibraryTrackItem(
                            track = track,
                            onClick = {
                                viewModel.playFromHistory(index)
                                onNavigateToPlayer()
                            },
                            onAction = { viewModel.downloadTrack(track) },
                            actionIcon = Icons.Default.Download
                        )
                    }
                } else {
                    item {
                        EmptyState(
                            icon = Icons.Default.History,
                            message = "No history yet",
                            subMessage = "Songs you play will appear here"
                        )
                    }
                }
            }
        }
    }

    if (showCreatePlaylistDialog) {
        CreatePlaylistDialog(
            name = newPlaylistName,
            onNameChange = { newPlaylistName = it },
            createSyncedPlaylist = createSyncedPlaylist,
            onCreateSyncedChange = { createSyncedPlaylist = it },
            canCreateSyncedPlaylist = isYouTubeLoggedIn,
            onDismiss = {
                showCreatePlaylistDialog = false
                newPlaylistName = ""
                createSyncedPlaylist = false
            },
            onCreate = {
                viewModel.createPlaylist(newPlaylistName, syncedToYouTube = createSyncedPlaylist)
                newPlaylistName = ""
                createSyncedPlaylist = false
                showCreatePlaylistDialog = false
                Toast.makeText(context, "Playlist created", Toast.LENGTH_SHORT).show()
            }
        )
    }
}

// COMPOSABLES

@Composable
fun LibraryHeader(onNavigateToSettings: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Library",
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.weight(1f))
        IconButton(onClick = onNavigateToSettings) {
            Icon(Icons.Default.Settings, contentDescription = "Settings", tint = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
fun LibraryTab(text: String, isSelected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(100))
            .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun LikedSongsBanner(count: Int, thumbnailUrl: String?, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.horizontalGradient(
                    colors = listOf(Color(0xFF450af5), Color(0xFFc4ef19))
                )
            )
            .clickable(onClick = onClick)
            .height(100.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Liked Songs",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "$count songs",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.8f)
                )
            }
            if (!thumbnailUrl.isNullOrEmpty()) {
                AsyncImage(
                    model = thumbnailUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .size(60.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White.copy(alpha = 0.2f)),
                    contentScale = ContentScale.Crop
                )
            }
        }
    }
}

@Composable
fun CreatePlaylistButton(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = "Create New Playlist",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun YouTubeAccountCard(
    isLoggedIn: Boolean,
    accountName: String?,
    isSyncing: Boolean,
    onSync: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (isLoggedIn) {
                    accountName ?: "YouTube Music Connected"
                } else {
                    "Connect YouTube Music"
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = if (isLoggedIn) {
                    "Sync likes and playlists from your account."
                } else {
                    "Sign in from Settings to sync likes and create cloud playlists."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (isLoggedIn) {
            TextButton(
                onClick = onSync,
                enabled = !isSyncing
            ) {
                Text(if (isSyncing) "Syncing..." else "Sync")
            }
        } else {
            TextButton(onClick = onOpenSettings) {
                Text("Open Settings")
            }
        }
    }
}

@Composable
fun LibraryOverviewCard(
    likedSongsCount: Int,
    playlistCount: Int,
    artistCount: Int,
    albumCount: Int,
    downloadsCount: Int
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                        MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f),
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                    )
                )
            )
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = "Your Library",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = "Keep the stuff you come back to in one place.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            LibraryStatChip(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.Favorite,
                label = "Liked",
                value = likedSongsCount.toString()
            )
            LibraryStatChip(
                modifier = Modifier.weight(1f),
                icon = Icons.Outlined.QueueMusic,
                label = "Playlists",
                value = playlistCount.toString()
            )
            LibraryStatChip(
                modifier = Modifier.weight(1f),
                icon = Icons.Outlined.Person,
                label = "Artists",
                value = artistCount.toString()
            )
            LibraryStatChip(
                modifier = Modifier.weight(1f),
                icon = Icons.Outlined.Album,
                label = "Albums",
                value = albumCount.toString()
            )
        }
        Text(
            text = "$downloadsCount offline tracks ready",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun LibraryStatChip(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String,
    value: String
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun LibrarySearchBar(
    query: String,
    onQueryChange: (String) -> Unit
) {
    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 12.dp),
        placeholder = { Text("Search saved artists, albums, and playlists") },
        leadingIcon = { Icon(Icons.Default.Search, null, tint = MaterialTheme.colorScheme.primary) },
        trailingIcon = {
            if (query.isNotBlank()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Default.Close, null)
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(18.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent
        )
    )
}

@Composable
fun SavedLibraryFilters(
    selectedType: MusicContentType?,
    onSelectType: (MusicContentType?) -> Unit,
    playlistCount: Int,
    artistCount: Int,
    albumCount: Int
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 10.dp)
            .wrapContentHeight()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            selected = selectedType == null,
            onClick = { onSelectType(null) },
            label = { Text("All") }
        )
        FilterChip(
            selected = selectedType == MusicContentType.PLAYLIST,
            onClick = { onSelectType(if (selectedType == MusicContentType.PLAYLIST) null else MusicContentType.PLAYLIST) },
            label = { Text("Playlists ($playlistCount)") }
        )
        FilterChip(
            selected = selectedType == MusicContentType.ARTIST,
            onClick = { onSelectType(if (selectedType == MusicContentType.ARTIST) null else MusicContentType.ARTIST) },
            label = { Text("Artists ($artistCount)") }
        )
        FilterChip(
            selected = selectedType == MusicContentType.ALBUM,
            onClick = { onSelectType(if (selectedType == MusicContentType.ALBUM) null else MusicContentType.ALBUM) },
            label = { Text("Albums ($albumCount)") }
        )
    }
}

@Composable
fun SavedCollectionCard(
    item: SavedCollectionItem,
    onClick: () -> Unit,
    onRemove: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            if (item.thumbnailUrl.isNotBlank()) {
                AsyncImage(
                    model = item.thumbnailUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    imageVector = when (item.contentType) {
                        MusicContentType.ARTIST -> Icons.Outlined.Person
                        MusicContentType.ALBUM -> Icons.Outlined.Album
                        else -> Icons.Outlined.Bookmark
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = item.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = onRemove) {
            Icon(Icons.Default.Close, contentDescription = "Remove from library", tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
fun PlaylistItemCard(playlist: com.israrxy.raazi.data.db.PlaylistEntity, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Zinc800),
            contentAlignment = Alignment.Center
        ) {
            if (playlist.thumbnailUrl.isNotEmpty()) {
                AsyncImage(
                    model = playlist.thumbnailUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(Icons.Default.QueueMusic, contentDescription = null, tint = Zinc500)
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(
                text = playlist.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = when {
                    playlist.isYouTubeSyncedPlaylist() && playlist.isYouTubeEditablePlaylist() -> "Synced with YouTube Music"
                    playlist.isYouTubeSyncedPlaylist() -> "Saved from YouTube Music"
                    else -> "Playlist"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun LibraryTrackItem(
    track: MusicItem,
    onClick: () -> Unit,
    onAction: () -> Unit,
    actionIcon: ImageVector
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = track.thumbnailUrl,
            contentDescription = null,
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Zinc800),
            contentScale = ContentScale.Crop
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = track.artist,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onAction) {
            Icon(actionIcon, contentDescription = null, tint = Zinc500)
        }
    }
}

@Composable
fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
    )
}

@Composable
fun EmptyState(icon: ImageVector, message: String, subMessage: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 80.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.surfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = subMessage,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun DownloadItem(track: MusicItem, progress: Int) {
     Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Zinc800)
        ) {
              AsyncImage(
                model = track.thumbnailUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    progress = { progress / 100f },
                    modifier = Modifier.size(24.dp),
                    color = Color.White,
                    trackColor = Color.White.copy(alpha = 0.3f)
                )
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Downloading...",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
fun CreatePlaylistDialog(
    name: String,
    onNameChange: (String) -> Unit,
    createSyncedPlaylist: Boolean,
    onCreateSyncedChange: (Boolean) -> Unit,
    canCreateSyncedPlaylist: Boolean,
    onDismiss: () -> Unit,
    onCreate: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Playlist", color = MaterialTheme.colorScheme.onSurface) },
        text = {
            TextField(
                value = name,
                onValueChange = onNameChange,
                placeholder = { Text("Playlist name") },
                colors = TextFieldDefaults.colors(
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    cursorColor = MaterialTheme.colorScheme.primary,
                    focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            if (canCreateSyncedPlaylist) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Create In YouTube Music",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "This playlist will be available in your account and Raazi.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Switch(
                        checked = createSyncedPlaylist,
                        onCheckedChange = onCreateSyncedChange
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onCreate,
                enabled = name.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("Create", color = MaterialTheme.colorScheme.onPrimary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(24.dp)
    )
}
