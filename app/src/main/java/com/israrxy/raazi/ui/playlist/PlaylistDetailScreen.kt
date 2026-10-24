package com.israrxy.raazi.ui.playlist

import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.israrxy.raazi.data.playlist.isYouTubeEditablePlaylist
import com.israrxy.raazi.data.playlist.isYouTubeSyncedPlaylist
import com.israrxy.raazi.data.playlist.parseYouTubePlaylistMetadata
import com.israrxy.raazi.model.MusicItem
import com.israrxy.raazi.model.Playlist
import com.israrxy.raazi.ui.components.AddToPlaylistDialog
import com.israrxy.raazi.ui.theme.*
import com.israrxy.raazi.utils.ThumbnailUtils
import com.israrxy.raazi.viewmodel.MusicPlayerViewModel
import java.util.Locale

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
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    tonalElevation = 8.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    viewModel.downloadPlaylist(selectedTracks.toList())
                                    Toast.makeText(
                                        context,
                                        "Downloading ${selectedTracks.size} tracks",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    isSelectionMode = false
                                    selectedTracks.clear()
                                }
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                Icons.Outlined.Download,
                                contentDescription = "Download selected",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "Download",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    viewModel.addToQueue(selectedTracks.toList())
                                    Toast.makeText(
                                        context,
                                        "Added ${selectedTracks.size} tracks to queue",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    isSelectionMode = false
                                    selectedTracks.clear()
                                }
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.QueueMusic,
                                contentDescription = "Add selected to queue",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "Queue",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        if (isEditablePlaylist && playlist != null) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
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
                                    .padding(horizontal = 16.dp, vertical = 6.dp)
                            ) {
                                Icon(
                                    Icons.Filled.Delete,
                                    contentDescription = "Remove selected",
                                    tint = MaterialTheme.colorScheme.error
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "Remove",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
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
                                    "https://music.youtube.com/playlist?list=${playlist.id.removePrefix("VL")}"
                                } else {
                                    "${playlist.title} (${items.size} tracks)"
                                }
                                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, "Check out ${playlist.title}: $shareUrl")
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
                                        if (isSelected) selectedTracks.remove(item)
                                        else selectedTracks.add(item)
                                    } else {
                                        viewModel.playPlaylist(items, actualIndex)
                                        onNavigateToPlayer()
                                    }
                                },
                                onLongClick = {
                                    if (!isSelectionMode) {
                                        isSelectionMode = true
                                        selectedTracks.add(item)
                                    }
                                },
                                onMoveUp = {
                                    if (actualIndex > 0) {
                                        val mutableIds = items.map { it.id }.toMutableList()
                                        val moved = mutableIds.removeAt(actualIndex)
                                        mutableIds.add(actualIndex - 1, moved)
                                        viewModel.reorderPlaylistTracks(playlist.id, mutableIds)
                                    }
                                },
                                onMoveDown = {
                                    if (actualIndex < items.lastIndex) {
                                        val mutableIds = items.map { it.id }.toMutableList()
                                        val moved = mutableIds.removeAt(actualIndex)
                                        mutableIds.add(actualIndex + 1, moved)
                                        viewModel.reorderPlaylistTracks(playlist.id, mutableIds)
                                    }
                                },
                                onPlayNext = {
                                    viewModel.playNext(listOf(item))
                                    Toast.makeText(context, "Playing next: ${item.title}", Toast.LENGTH_SHORT).show()
                                },
                                onAddToQueue = {
                                    viewModel.addToQueue(item)
                                    Toast.makeText(context, "Added to queue", Toast.LENGTH_SHORT).show()
                                },
                                onAddToPlaylist = {
                                    selectedTrackForAdd = item
                                    showAddToPlaylistDialog = true
                                },
                                onDownload = {
                                    viewModel.downloadTrack(item)
                                    Toast.makeText(context, "Downloading ${item.title}", Toast.LENGTH_SHORT).show()
                                },
                                onShareTrack = {
                                    val shareText = "${item.title} - ${item.artist}\nhttps://music.youtube.com/watch?v=${item.id}"
                                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, shareText)
                                    }
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
        var newTitle by remember(playlist.title) { mutableStateOf(playlist.title) }
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rename Playlist", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = newTitle,
                    onValueChange = { newTitle = it },
                    label = { Text("Playlist name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = newTitle.trim()
                        if (trimmed.isNotEmpty()) {
                            viewModel.renamePlaylist(playlist.id, trimmed)
                            showRenameDialog = false
                        }
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showDeleteConfirmDialog && playlist != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("Delete Playlist?", fontWeight = FontWeight.Bold) },
            text = {
                Text("Are you sure you want to delete \"${playlist.title}\"? This action cannot be undone.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deletePlaylist(playlist.id)
                        showDeleteConfirmDialog = false
                        onNavigateBack()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showAddToPlaylistDialog && selectedTrackForAdd != null) {
        AddToPlaylistDialog(
            viewModel = viewModel,
            onDismiss = {
                showAddToPlaylistDialog = false
                selectedTrackForAdd = null
            },
            onPlaylistSelected = { targetPlaylist ->
                viewModel.addToPlaylist(selectedTrackForAdd!!, targetPlaylist)
                Toast.makeText(context, "Added to ${targetPlaylist.title}", Toast.LENGTH_SHORT).show()
                showAddToPlaylistDialog = false
                selectedTrackForAdd = null
            }
        )
    }
}

@Composable
private fun PlaylistHeroHeader(
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SpotifyStylePlaylistTrackRow(
    index: Int,
    item: MusicItem,
    isPlayingTrack: Boolean,
    isActivelyPlaying: Boolean,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    isReorderMode: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    isDownloaded: Boolean,
    isDownloading: Boolean,
    isEditablePlaylist: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onDownload: () -> Unit,
    onShareTrack: () -> Unit,
    onRemoveFromPlaylist: () -> Unit
) {
    var showTrackMenu by remember { mutableStateOf(false) }

    val rowContainerColor = when {
        isSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
        isPlayingTrack -> Emerald500.copy(alpha = 0.12f)
        else -> Color.Transparent
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(rowContainerColor)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Track Index / Active Indicator / Checkbox
        Box(
            modifier = Modifier.width(30.dp),
            contentAlignment = Alignment.Center
        ) {
            when {
                isSelectionMode -> {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = { onClick() },
                        modifier = Modifier.size(20.dp)
                    )
                }
                isPlayingTrack -> {
                    Icon(
                        imageVector = if (isActivelyPlaying) Icons.Filled.GraphicEq else Icons.Filled.Pause,
                        contentDescription = "Now playing",
                        tint = Emerald500,
                        modifier = Modifier.size(20.dp)
                    )
                }
                else -> {
                    Text(
                        text = index.toString(),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Track Artwork
        AsyncImage(
            model = ThumbnailUtils.getListThumbnail(item.thumbnailUrl),
            contentDescription = item.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
        )

        Spacer(modifier = Modifier.width(14.dp))

        // Title + Artist + Downloaded badge
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isPlayingTrack) FontWeight.Bold else FontWeight.SemiBold,
                color = if (isPlayingTrack) Emerald500 else MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(3.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (isDownloaded) {
                    Icon(
                        imageVector = Icons.Filled.DownloadDone,
                        contentDescription = "Downloaded",
                        tint = Emerald500,
                        modifier = Modifier.size(14.dp)
                    )
                } else if (isDownloading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(12.dp),
                        strokeWidth = 1.5.dp,
                        color = Emerald500
                    )
                }
                Text(
                    text = buildString {
                        append(item.artist.ifBlank { "Unknown Artist" })
                        if (item.duration > 0L) {
                            append(" • ")
                            append(formatTrackDuration(item.duration))
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Right Actions: Reorder Controls OR 3-dot Menu
        if (isReorderMode) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onMoveUp,
                    enabled = canMoveUp,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowUp,
                        contentDescription = "Move up"
                    )
                }
                IconButton(
                    onClick = onMoveDown,
                    enabled = canMoveDown,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowDown,
                        contentDescription = "Move down"
                    )
                }
            }
        } else if (!isSelectionMode) {
            Box {
                IconButton(
                    onClick = { showTrackMenu = true },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = "Track options",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                DropdownMenu(
                    expanded = showTrackMenu,
                    onDismissRequest = { showTrackMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Play next") },
                        leadingIcon = { Icon(Icons.Filled.QueuePlayNext, contentDescription = null) },
                        onClick = {
                            showTrackMenu = false
                            onPlayNext()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Add to queue") },
                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = null) },
                        onClick = {
                            showTrackMenu = false
                            onAddToQueue()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Add to playlist") },
                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = null) },
                        onClick = {
                            showTrackMenu = false
                            onAddToPlaylist()
                        }
                    )
                    if (!isDownloaded) {
                        DropdownMenuItem(
                            text = { Text("Download") },
                            leadingIcon = { Icon(Icons.Outlined.Download, contentDescription = null) },
                            onClick = {
                                showTrackMenu = false
                                onDownload()
                            }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Share") },
                        leadingIcon = { Icon(Icons.Outlined.Share, contentDescription = null) },
                        onClick = {
                            showTrackMenu = false
                            onShareTrack()
                        }
                    )
                    if (isEditablePlaylist) {
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = {
                                Text(
                                    "Remove from playlist",
                                    color = MaterialTheme.colorScheme.error
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Outlined.DeleteOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                            },
                            onClick = {
                                showTrackMenu = false
                                onRemoveFromPlaylist()
                            }
                        )
                    }
                }
            }
        }
    }
}

private fun formatTrackDuration(durationMs: Long): String {
    val totalSeconds = (durationMs / 1000L).coerceAtLeast(0L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
}

private fun formatPlaylistTotalDuration(durationMs: Long): String {
    if (durationMs <= 0L) return ""
    val totalMinutes = (durationMs / 1000L / 60L).coerceAtLeast(1L)
    val hours = totalMinutes / 60L
    val minutes = totalMinutes % 60L
    return if (hours > 0L) {
        "${hours}h ${minutes}m"
    } else {
        "${minutes} min"
    }
}
