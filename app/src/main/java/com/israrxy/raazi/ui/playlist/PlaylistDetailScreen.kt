package com.israrxy.raazi.ui.playlist

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.israrxy.raazi.data.playlist.isYouTubeEditablePlaylist
import com.israrxy.raazi.data.playlist.isYouTubeSyncedPlaylist
import com.israrxy.raazi.data.playlist.parseYouTubePlaylistMetadata
import com.israrxy.raazi.model.MusicItem
import com.israrxy.raazi.ui.components.AddToPlaylistDialog
import com.israrxy.raazi.utils.ThumbnailUtils
import com.israrxy.raazi.viewmodel.MusicPlayerViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PlaylistDetailScreen(
    playlistId: String,
    viewModel: MusicPlayerViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToPlayer: () -> Unit
) {
    val currentPlaylist by viewModel.currentPlaylist.collectAsStateWithLifecycle()
    val savedPlaylists by viewModel.userPlaylists.collectAsStateWithLifecycle()
    val savedCollectionIds by viewModel.savedCollectionIds.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()
    val isYouTubeLoggedIn by viewModel.isYouTubeLoggedIn.collectAsStateWithLifecycle()
    val activeDownloads by viewModel.dbActiveDownloads.collectAsStateWithLifecycle()
    val downloadedTracks by viewModel.downloadedTracks.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    var isSelectionMode by remember { mutableStateOf(false) }
    val selectedTracks = remember { mutableStateListOf<MusicItem>() }
    var isReorderMode by remember { mutableStateOf(false) }
    var showSearchBar by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showAddToPlaylistDialog by remember { mutableStateOf(false) }
    var selectedTrackForAdd by remember { mutableStateOf<MusicItem?>(null) }
    var showHeaderMenu by remember { mutableStateOf(false) }

    LaunchedEffect(playlistId) {
        viewModel.loadPlaylist(playlistId)
        isSelectionMode = false
        selectedTracks.clear()
        isReorderMode = false
        showSearchBar = false
        searchQuery = ""
    }

    val playlist = currentPlaylist
    val items = playlist?.items ?: emptyList()
    val filteredItems = remember(items, searchQuery) {
        if (searchQuery.isBlank()) {
            items
        } else {
            val query = searchQuery.trim().lowercase()
            items.filter {
                it.title.lowercase().contains(query) || it.artist.lowercase().contains(query)
            }
        }
    }

    val matchingLocalEntity = remember(playlistId, savedPlaylists) {
        savedPlaylists.find { it.id == playlistId }
    }
    val isLocalPlaylist = matchingLocalEntity != null
    val isSyncedWithYouTube = remember(playlist, matchingLocalEntity) {
        matchingLocalEntity?.isYouTubeSyncedPlaylist() == true ||
            (playlist != null && parseYouTubePlaylistMetadata(playlist.description) != null)
    }
    val isEditablePlaylist = remember(matchingLocalEntity, isLocalPlaylist, isYouTubeLoggedIn) {
        isLocalPlaylist && (
            matchingLocalEntity?.isYouTubeSyncedPlaylist() != true ||
                (isYouTubeLoggedIn && matchingLocalEntity.isYouTubeEditablePlaylist())
            )
    }
    val isSaved = remember(playlist, isLocalPlaylist, savedCollectionIds) {
        if (playlist == null) false
        else isLocalPlaylist || viewModel.isSavedCollection(playlist)
    }

    val isThisPlaylistPlaying = remember(items, playbackState.currentTrack, playbackState.isPlaying) {
        val currentTrackId = playbackState.currentTrack?.id
        currentTrackId != null && items.any { it.id == currentTrackId } && playbackState.isPlaying
    }

    val coverUrl = remember(playlist, items) {
        val candidate = playlist?.thumbnailUrl?.takeIf { it.isNotBlank() }
            ?: items.firstOrNull()?.thumbnailUrl.orEmpty()
        ThumbnailUtils.getHighQualityThumbnail(candidate)
    }

    val totalDurationMs = remember(items) {
        items.sumOf { it.duration.coerceAtLeast(0L) }
    }
    val durationSummary = remember(totalDurationMs) {
        formatPlaylistTotalDuration(totalDurationMs)
    }

    val downloadCount = remember(items, activeDownloads) {
        val itemIds = items.map { it.id }.toSet()
        activeDownloads.count { itemIds.contains(it.trackId) }
    }

    val displayDescription = remember(playlist?.description) {
        val raw = playlist?.description.orEmpty()
        if (parseYouTubePlaylistMetadata(raw) != null) "" else raw
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            if (isSelectionMode) {
                TopAppBar(
                    title = {
                        Text(
                            text = "${selectedTracks.size} selected",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            isSelectionMode = false
                            selectedTracks.clear()
                        }) {
                            Icon(Icons.Filled.Close, contentDescription = "Cancel selection")
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = {
                                if (selectedTracks.size == items.size) {
                                    selectedTracks.clear()
                                } else {
                                    selectedTracks.clear()
                                    selectedTracks.addAll(items)
                                }
                            }
                        ) {
                            Icon(Icons.Filled.SelectAll, contentDescription = "Select all")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    )
                )
            }
        },
        bottomBar = {
            if (isSelectionMode && selectedTracks.isNotEmpty()) {
                PlaylistSelectionBottomBar(
                    selectedCount = selectedTracks.size,
                    isEditablePlaylist = isEditablePlaylist && playlist != null,
                    onDownloadSelected = {
                        viewModel.downloadPlaylist(selectedTracks.toList())
                        Toast.makeText(
                            context,
                            "Downloading ${selectedTracks.size} tracks",
                            Toast.LENGTH_SHORT
                        ).show()
                        isSelectionMode = false
                        selectedTracks.clear()
                    },
                    onQueueSelected = {
                        viewModel.addToQueue(selectedTracks.toList())
                        Toast.makeText(
                            context,
                            "Added ${selectedTracks.size} tracks to queue",
                            Toast.LENGTH_SHORT
                        ).show()
                        isSelectionMode = false
                        selectedTracks.clear()
                    },
                    onRemoveSelected = {
                        if (playlist != null) {
                            viewModel.removeTracksFromPlaylist(
                                playlist.id,
                                selectedTracks.toList()
                            )
                            Toast.makeText(
                                context,
                                "Removed ${selectedTracks.size} tracks",
                                Toast.LENGTH_SHORT
                            ).show()
                            isSelectionMode = false
                            selectedTracks.clear()
                        }
                    }
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (isLoading && playlist == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            } else if (playlist == null) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Playlist not found",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    FilledTonalButton(onClick = onNavigateBack) {
                        Text("Go Back")
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 128.dp)
                ) {
                    // Spotify / YouTube Music style Hero Header
                    item {
                        PlaylistHeroHeader(
                            playlist = playlist,
                            displayDescription = displayDescription,
                            coverUrl = coverUrl,
                            trackCount = items.size,
                            durationSummary = durationSummary,
                            isLocalPlaylist = isLocalPlaylist,
                            isSyncedWithYouTube = isSyncedWithYouTube,
                            isEditablePlaylist = isEditablePlaylist,
                            isSaved = isSaved,
                            isYouTubeLoggedIn = isYouTubeLoggedIn,
                            isThisPlaylistPlaying = isThisPlaylistPlaying,
                            isShuffleEnabled = playbackState.isShuffleEnabled,
                            isReorderMode = isReorderMode,
                            showSearchBar = showSearchBar,
                            searchQuery = searchQuery,
                            downloadCount = downloadCount,
                            showHeaderMenu = showHeaderMenu,
                            onShowHeaderMenuChange = { showHeaderMenu = it },
                            onNavigateBack = onNavigateBack,
                            onSearchToggle = {
                                showSearchBar = !showSearchBar
                                if (!showSearchBar) {
                                    searchQuery = ""
                                }
                            },
                            onSearchQueryChange = { searchQuery = it },
                            onClearSearch = {
                                searchQuery = ""
                                focusManager.clearFocus()
                            },
                            onPlayPauseClick = {
                                if (items.isNotEmpty()) {
                                    if (isThisPlaylistPlaying) {
                                        viewModel.playPause()
                                    } else {
                                        viewModel.playPlaylist(items, 0)
                                        onNavigateToPlayer()
                                    }
                                }
                            },
                            onShuffleClick = {
                                if (items.isNotEmpty()) {
                                    if (!playbackState.isShuffleEnabled) {
                                        viewModel.toggleShuffle()
                                    }
                                    val shuffled = items.shuffled()
                                    viewModel.playPlaylist(shuffled, 0)
                                    onNavigateToPlayer()
                                }
                            },
                            onDownloadAllClick = {
                                if (items.isNotEmpty()) {
                                    viewModel.downloadPlaylist(items)
                                    Toast.makeText(
                                        context,
                                        "Downloading ${items.size} tracks...",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            },
                            onToggleSaveClick = {
                                viewModel.toggleSavedPlaylist(playlist)
                                Toast.makeText(
                                    context,
                                    if (isSaved) "Removed from Library" else "Saved to Your Library",
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                            onSyncToYouTubeClick = {
                                viewModel.syncLocalPlaylistToYouTube(playlist.id)
                                Toast.makeText(
                                    context,
                                    "Syncing playlist with YouTube Music...",
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                            onToggleReorderClick = {
                                isReorderMode = !isReorderMode
                            },
                            onRenameClick = {
                                showHeaderMenu = false
                                showRenameDialog = true
                            },
                            onSelectTracksClick = {
                                showHeaderMenu = false
                                isSelectionMode = true
                            },
                            onDeletePlaylistClick = {
                                showHeaderMenu = false
                                showDeleteConfirmDialog = true
                            },
                            onSharePlaylistClick = {
                                showHeaderMenu = false
                                val shareUrl = if (!isLocalPlaylist || isSyncedWithYouTube) {
                                    "https://raazi.pages.dev/playlist?list=${playlist.id.removePrefix("VL")}"
                                } else {
                                    "${playlist.title} (${items.size} tracks)"
                                }
                                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, "Check out ${playlist.title} on Raazi: $shareUrl")
                                }
                                context.startActivity(Intent.createChooser(sendIntent, "Share Playlist"))
                            }
                        )
                    }

                    if (filteredItems.isEmpty()) {
                        item {
                            Column(
                                modifier = Modifier
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
                                    text = if (searchQuery.isNotBlank()) {
                                        "No songs matching \"$searchQuery\""
                                    } else {
                                        "This playlist is empty"
                                    },
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
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    } else {
                        itemsIndexed(
                            items = filteredItems,
                            key = { index, item -> "${item.id}_$index" }
                        ) { index, item ->
                            val actualIndex = items.indexOfFirst { it.id == item.id }.takeIf { it >= 0 } ?: index
                            val isSelected = selectedTracks.contains(item)
                            val isPlayingTrack = playbackState.currentTrack?.id == item.id
                            val isDownloaded = downloadedTracks.any {
                                it.id == item.id && !it.localPath.isNullOrBlank()
                            }
                            val isDownloading = activeDownloads.any { it.trackId == item.id }

                            SpotifyStylePlaylistTrackRow(
                                index = actualIndex + 1,
                                item = item,
                                isPlayingTrack = isPlayingTrack,
                                isActivelyPlaying = isPlayingTrack && playbackState.isPlaying,
                                isSelectionMode = isSelectionMode,
                                isSelected = isSelected,
                                isReorderMode = isReorderMode && searchQuery.isBlank(),
                                canMoveUp = actualIndex > 0,
                                canMoveDown = actualIndex < items.lastIndex,
                                isDownloaded = isDownloaded,
                                isDownloading = isDownloading,
                                isEditablePlaylist = isEditablePlaylist,
                                onClick = {
                                    if (isSelectionMode) {
                                        if (isSelected) selectedTracks.remove(item) else selectedTracks.add(item)
                                    } else {
                                        viewModel.playPlaylist(items, actualIndex)
                                        onNavigateToPlayer()
                                    }
                                },
                                onLongClick = { if (!isSelectionMode) { isSelectionMode = true; selectedTracks.add(item) } },
                                onMoveUp = {
                                    if (actualIndex > 0) {
                                        val mutableIds = items.map { it.id }.toMutableList()
                                        mutableIds.add(actualIndex - 1, mutableIds.removeAt(actualIndex))
                                        viewModel.reorderPlaylistTracks(playlist.id, mutableIds)
                                    }
                                },
                                onMoveDown = {
                                    if (actualIndex < items.lastIndex) {
                                        val mutableIds = items.map { it.id }.toMutableList()
                                        mutableIds.add(actualIndex + 1, mutableIds.removeAt(actualIndex))
                                        viewModel.reorderPlaylistTracks(playlist.id, mutableIds)
                                    }
                                },
                                onPlayNext = { viewModel.playNext(listOf(item)); Toast.makeText(context, "Playing next: ${item.title}", Toast.LENGTH_SHORT).show() },
                                onAddToQueue = { viewModel.addToQueue(item); Toast.makeText(context, "Added to queue", Toast.LENGTH_SHORT).show() },
                                onAddToPlaylist = { selectedTrackForAdd = item; showAddToPlaylistDialog = true },
                                onDownload = { viewModel.downloadTrack(item); Toast.makeText(context, "Downloading ${item.title}", Toast.LENGTH_SHORT).show() },
                                onShareTrack = {
                                    val shareText = "${item.title} - ${item.artist}\nhttps://raazi.pages.dev/play?id=${item.id}"
                                    val sendIntent = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, shareText) }
                                    context.startActivity(Intent.createChooser(sendIntent, "Share Song"))
                                },
                                onRemoveFromPlaylist = {
                                    viewModel.removeTrackFromPlaylist(playlist.id, item.id, item.setVideoId)
                                    Toast.makeText(context, "Removed from playlist", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showRenameDialog && playlist != null) {
        PlaylistRenameDialog(playlist.title, { showRenameDialog = false }) { trimmed -> viewModel.renamePlaylist(playlist.id, trimmed); showRenameDialog = false }
    }
    if (showDeleteConfirmDialog && playlist != null) {
        PlaylistDeleteConfirmDialog(playlist.title, { showDeleteConfirmDialog = false }) { viewModel.deletePlaylist(playlist.id); showDeleteConfirmDialog = false; onNavigateBack() }
    }
    if (showAddToPlaylistDialog && selectedTrackForAdd != null) {
        AddToPlaylistDialog(viewModel, { showAddToPlaylistDialog = false; selectedTrackForAdd = null }) { targetPlaylist ->
            viewModel.addToPlaylist(selectedTrackForAdd!!, targetPlaylist)
            Toast.makeText(context, "Added to ${targetPlaylist.title}", Toast.LENGTH_SHORT).show()
            showAddToPlaylistDialog = false; selectedTrackForAdd = null
        }
    }
}
