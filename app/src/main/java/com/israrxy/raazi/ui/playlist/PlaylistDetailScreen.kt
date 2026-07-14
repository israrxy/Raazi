package com.israrxy.raazi.ui.playlist

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close

import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.israrxy.raazi.data.playlist.isYouTubeSyncedPlaylist
import com.israrxy.raazi.model.MusicItem
import com.israrxy.raazi.model.toSavedCollectionItem
import com.israrxy.raazi.ui.theme.*
import com.israrxy.raazi.viewmodel.MusicPlayerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailScreen(
    playlistId: String,
    viewModel: MusicPlayerViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToPlayer: () -> Unit
) {
    val currentPlaylist by viewModel.currentPlaylist.collectAsState(initial = null)
    val isLoading by viewModel.isLoading.collectAsState(initial = false)
    val error by viewModel.error.collectAsState(initial = null)
    val savedCollectionIds by viewModel.savedCollectionIds.collectAsStateWithLifecycle()
    
    // Selection state
    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedTracks by remember { mutableStateOf(setOf<MusicItem>()) }
    
    // Add To Playlist Dialog State
    var showAddToPlaylistDialog by remember { mutableStateOf(false) }
    val userPlaylists by viewModel.userPlaylists.collectAsStateWithLifecycle()
    val isYouTubeLoggedIn by viewModel.isYouTubeLoggedIn.collectAsStateWithLifecycle()
    val isLocalPlaylist = remember(playlistId, userPlaylists) {
        playlistId != "favorites" && userPlaylists.any { it.id == playlistId && !it.isYouTubeSyncedPlaylist() }
    }
    val isEditable = remember(playlistId, userPlaylists) {
        playlistId == "favorites" || userPlaylists.any { it.id == playlistId }
    }
    var tracksToAddToPlaylist by remember { mutableStateOf<List<MusicItem>?>(null) }
    var showBulkDeleteConfirmation by remember { mutableStateOf(false) }

    // Reorder mode
    var isReorderMode by remember { mutableStateOf(false) }
    var orderedItems by remember { mutableStateOf<List<MusicItem>>(emptyList()) }
    
    // Auto-exit selection mode if empty
    LaunchedEffect(selectedTracks) {
        if (selectedTracks.isEmpty() && isSelectionMode) {
            isSelectionMode = false
        }
    }

    // Playlist context menu state
    var showPlaylistMenu by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var renamePlaylistName by remember { mutableStateOf("") }
    var showDeleteDialog by remember { mutableStateOf(false) }

    LaunchedEffect(currentPlaylist?.title) {
        if (!showRenameDialog) {
            renamePlaylistName = currentPlaylist?.title ?: ""
        }
    }
    
    LaunchedEffect(playlistId) {
        viewModel.loadPlaylist(playlistId)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Box(modifier = Modifier.statusBarsPadding()) {
                TopAppBar(
                    title = { 
                        Text(
                            text = currentPlaylist?.title ?: "Playlist",
                            color = MaterialTheme.colorScheme.onBackground,
                            fontWeight = FontWeight.Bold
                        ) 
                    },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onBackground)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    ),
                    actions = {
                        currentPlaylist?.let { playlist ->
                            val isSavedPlaylist = playlist.toSavedCollectionItem().id in savedCollectionIds
                            IconButton(onClick = { viewModel.toggleSavedPlaylist(playlist) }) {
                                Icon(
                                    if (isSavedPlaylist) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                                    contentDescription = if (isSavedPlaylist) "Remove from library" else "Save to library",
                                    tint = if (isSavedPlaylist) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground
                                )
                            }
                        }
                        IconButton(onClick = {
                            currentPlaylist?.items?.let { items ->
                                if (items.isNotEmpty()) {
                                    viewModel.playPlaylist(items)
                                    onNavigateToPlayer()
                                }
                            }
                        }) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = "Play Playlist", tint = MaterialTheme.colorScheme.onBackground)
                        }
                        if (isReorderMode) {
                            IconButton(onClick = { isReorderMode = false }) {
                                Icon(Icons.Filled.Check, contentDescription = "Done reordering", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                        Box {
                            IconButton(onClick = { showPlaylistMenu = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "Options", tint = MaterialTheme.colorScheme.onBackground)
                            }
                            DropdownMenu(
                                expanded = showPlaylistMenu,
                                onDismissRequest = { showPlaylistMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Rename") },
                                    onClick = {
                                        showPlaylistMenu = false
                                        showRenameDialog = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Delete") },
                                    onClick = {
                                        showPlaylistMenu = false
                                        showDeleteDialog = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Download All") },
                                    onClick = {
                                        showPlaylistMenu = false
                                        currentPlaylist?.items?.let { viewModel.downloadPlaylist(it) }
                                    }
                                )
                                if (isYouTubeLoggedIn && isLocalPlaylist) {
                                    DropdownMenuItem(
                                        text = { Text("Sync to YouTube Music") },
                                        onClick = {
                                            showPlaylistMenu = false
                                            viewModel.syncLocalPlaylistToYouTube(playlistId)
                                        }
                                    )
                                }
                                if (isLocalPlaylist && isEditable && !isSelectionMode && currentPlaylist != null) {
                                    DropdownMenuItem(
                                        text = { Text("Reorder songs") },
                                        onClick = {
                                            showPlaylistMenu = false
                                            orderedItems = currentPlaylist!!.items.toList()
                                            isReorderMode = true
                                        }
                                    )
                                }
                            }
                        }
                    }
                )

                AnimatedVisibility(
                    visible = isSelectionMode,
                    enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.95f),
                        tonalElevation = 6.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val playlistItems = currentPlaylist?.items ?: emptyList()
                            val allSelected = playlistItems.isNotEmpty() && playlistItems.all { selectedTracks.contains(it) }
                            Checkbox(
                                checked = allSelected,
                                onCheckedChange = { checkAll ->
                                    if (checkAll) {
                                        selectedTracks = selectedTracks + playlistItems
                                    } else {
                                        selectedTracks = selectedTracks - playlistItems.toSet()
                                    }
                                },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    uncheckedColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
                                )
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                "${selectedTracks.size} selected",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = {
                                if (selectedTracks.isNotEmpty()) {
                                    val list = selectedTracks.toList()
                                    viewModel.playPlaylist(list, 0)
                                    isSelectionMode = false
                                    selectedTracks = emptySet()
                                    onNavigateToPlayer()
                                }
                            }) {
                                Icon(Icons.Default.PlayArrow, "Play Selected", tint = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                            IconButton(onClick = {
                                if (selectedTracks.isNotEmpty()) {
                                    viewModel.addToQueue(selectedTracks.toList())
                                    isSelectionMode = false
                                    selectedTracks = emptySet()
                                }
                            }) {
                                Icon(Icons.Default.QueueMusic, "Add to Queue", tint = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                            IconButton(onClick = {
                                if (selectedTracks.isNotEmpty()) {
                                    tracksToAddToPlaylist = selectedTracks.toList()
                                }
                            }) {
                                Icon(Icons.Default.PlaylistAdd, "Add to Playlist", tint = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                            IconButton(onClick = {
                                selectedTracks.forEach { viewModel.downloadTrack(it) }
                                isSelectionMode = false
                                selectedTracks = emptySet()
                            }) {
                                Icon(Icons.Default.Download, "Download", tint = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                            if (isEditable) {
                                IconButton(onClick = {
                                    if (selectedTracks.isNotEmpty()) {
                                        showBulkDeleteConfirmation = true
                                    }
                                }) {
                                    Icon(Icons.Default.Delete, "Remove from Playlist", tint = MaterialTheme.colorScheme.onPrimaryContainer)
                                }
                            }
                            IconButton(onClick = {
                                isSelectionMode = false
                                selectedTracks = emptySet()
                            }) {
                                Icon(Icons.Default.Close, "Cancel", tint = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                        }
                    }
                }
            }
        },
        floatingActionButton = {
            AnimatedVisibility(
                visible = currentPlaylist?.items?.isNotEmpty() == true,
                enter = scaleIn() + fadeIn(),
                exit = scaleOut() + fadeOut()
            ) {
                FloatingActionButton(
                    onClick = {
                        currentPlaylist?.let { playlist ->
                            if (playlist.items.isNotEmpty()) {
                                viewModel.playPlaylist(playlist.items)
                                onNavigateToPlayer()
                            }
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Play All")
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            when {
                isLoading && currentPlaylist == null -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Loading playlist...",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                error != null -> {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Outlined.MusicNote,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Failed to load playlist",
                            color = MaterialTheme.colorScheme.onBackground,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = error!!,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = { viewModel.loadPlaylist(playlistId) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Text("Retry")
                        }
                    }
                }
                currentPlaylist != null -> {
                    val playlist = currentPlaylist!!
                    Box(modifier = Modifier.fillMaxSize()) {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize()
                        ) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(280.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(
                                                Brush.verticalGradient(
                                                    colors = listOf(
                                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                                                        MaterialTheme.colorScheme.background
                                                    )
                                                )
                                            )
                                    )
                                    
                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(24.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        AsyncImage(
                                            model = playlist.thumbnailUrl,
                                            contentDescription = playlist.title,
                                            modifier = Modifier
                                                .size(180.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(MaterialTheme.colorScheme.surfaceVariant),
                                            contentScale = ContentScale.Crop
                                        )
                                        
                                        Spacer(modifier = Modifier.height(20.dp))
                                        
                                        Text(
                                            text = playlist.title,
                                            style = MaterialTheme.typography.headlineMedium,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = MaterialTheme.colorScheme.onBackground,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        
                                        Spacer(modifier = Modifier.height(8.dp))
                                        
                                        Text(
                                            text = "${playlist.items.size} songs",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            val displayItems = if (isReorderMode) orderedItems else playlist.items
                            itemsIndexed(items = displayItems) { index, item ->
                                val canMoveUp = index > 0
                                val canMoveDown = index < displayItems.size - 1
                                PremiumPlaylistItemCard(
                                    index = index + 1,
                                    item = item,
                                    isSelected = selectedTracks.contains(item),
                                    isSelectionMode = isSelectionMode,
                                    reorderMode = isReorderMode,
                                    canMoveUp = canMoveUp,
                                    canMoveDown = canMoveDown,
                                    onMoveUp = {
                                        if (canMoveUp) {
                                            val newOrder = orderedItems.toMutableList()
                                            val moved = newOrder.removeAt(index)
                                            newOrder.add(index - 1, moved)
                                            orderedItems = newOrder
                                            viewModel.reorderPlaylistTracks(playlist.id, newOrder.map { it.id })
                                        }
                                    },
                                    onMoveDown = {
                                        if (canMoveDown) {
                                            val newOrder = orderedItems.toMutableList()
                                            val moved = newOrder.removeAt(index)
                                            newOrder.add(index + 1, moved)
                                            orderedItems = newOrder
                                            viewModel.reorderPlaylistTracks(playlist.id, newOrder.map { it.id })
                                        }
                                    },
                                    onRemoveFromPlaylist = {
                                        viewModel.removeTrackFromPlaylist(playlist.id, item.id, item.setVideoId)
                                    },
                                    onDownloadForRingtone = {
                                        viewModel.downloadForRingtone(item)
                                    },
                                    onSelectionChange = { selected ->
                                        if (selected) {
                                            selectedTracks = selectedTracks + item
                                        } else {
                                            selectedTracks = selectedTracks - item
                                        }
                                    },
                                    onLongClick = {
                                        isSelectionMode = true
                                        selectedTracks = selectedTracks + item
                                    },
                                    onClick = {
                                        if (isSelectionMode) {
                                            val isCurrentlySelected = selectedTracks.contains(item)
                                            if (isCurrentlySelected) {
                                                selectedTracks = selectedTracks - item
                                            } else {
                                                selectedTracks = selectedTracks + item
                                            }
                                        } else {
                                            viewModel.playPlaylist(playlist.items, index)
                                            onNavigateToPlayer()
                                        }
                                    }
                                )
                            }
                            
                            item {
                                Spacer(modifier = Modifier.height(100.dp))
                            }
                        }

                        if (isLoading) {
                            LinearProgressIndicator(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .align(Alignment.TopCenter),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }

    if (showRenameDialog && currentPlaylist != null) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rename Playlist") },
            text = {
                TextField(
                    value = renamePlaylistName,
                    onValueChange = { renamePlaylistName = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.renamePlaylist(currentPlaylist!!.id, renamePlaylistName)
                        showRenameDialog = false
                    },
                    enabled = renamePlaylistName.isNotBlank()
                ) { Text("Rename") }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showDeleteDialog && currentPlaylist != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Playlist") },
            text = { Text("Are you sure you want to delete '${currentPlaylist?.title}'? This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deletePlaylist(currentPlaylist!!.id)
                        showDeleteDialog = false
                        onNavigateBack()
                    }
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (tracksToAddToPlaylist != null) {
        com.israrxy.raazi.ui.components.AddToPlaylistDialog(
            viewModel = viewModel,
            onDismiss = { tracksToAddToPlaylist = null },
            onPlaylistSelected = { playlist ->
                viewModel.addToPlaylist(tracksToAddToPlaylist!!, playlist)
                tracksToAddToPlaylist = null
                isSelectionMode = false
                selectedTracks = emptySet()
            }
        )
    }

    if (showBulkDeleteConfirmation && currentPlaylist != null) {
        AlertDialog(
            onDismissRequest = { showBulkDeleteConfirmation = false },
            title = { Text("Remove Songs") },
            text = { Text("Are you sure you want to remove the selected ${selectedTracks.size} songs from this playlist?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showBulkDeleteConfirmation = false
                        if (playlistId == "favorites") {
                            selectedTracks.forEach { viewModel.toggleFavorite(it) }
                        } else {
                            viewModel.removeTracksFromPlaylist(playlistId, selectedTracks.toList())
                        }
                        isSelectionMode = false
                        selectedTracks = emptySet()
                    }
                ) {
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showBulkDeleteConfirmation = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun PremiumPlaylistItemCard(
    index: Int,
    item: MusicItem,
    isSelected: Boolean = false,
    isSelectionMode: Boolean = false,
    reorderMode: Boolean = false,
    canMoveUp: Boolean = true,
    canMoveDown: Boolean = true,
    onMoveUp: () -> Unit = {},
    onMoveDown: () -> Unit = {},
    onSelectionChange: (Boolean) -> Unit = {},
    onLongClick: () -> Unit = {},
    onRemoveFromPlaylist: () -> Unit = {},
    onDownloadForRingtone: () -> Unit = {},
    onClick: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = if (reorderMode) ({}) else onClick,
                onLongClick = if (reorderMode) ({}) else onLongClick
            )
            .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else Color.Transparent)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isSelectionMode) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onSelectionChange(it) },
                colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary),
                modifier = Modifier.padding(end = 12.dp)
            )
        }
        Text(
            text = index.toString(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(32.dp)
        )

        AsyncImage(
            model = item.thumbnailUrl,
            contentDescription = null,
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentScale = ContentScale.Crop
        )

        Spacer(modifier = Modifier.width(16.dp))

        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = item.artist,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (reorderMode) {
            IconButton(
                onClick = onMoveUp,
                enabled = canMoveUp,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    Icons.Filled.KeyboardArrowUp,
                    contentDescription = "Move up",
                    tint = if (canMoveUp) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                )
            }
            IconButton(
                onClick = onMoveDown,
                enabled = canMoveDown,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    Icons.Filled.KeyboardArrowDown,
                    contentDescription = "Move down",
                    tint = if (canMoveDown) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                )
            }
        } else {
            if (item.duration > 0) {
                val totalSeconds = item.duration / 1000
                val minutes = totalSeconds / 60
                val seconds = totalSeconds % 60
                Text(
                    text = String.format("%d:%02d", minutes, seconds),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.padding(start = 8.dp)
                )
            }

            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Options")
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Remove from Playlist") },
                        onClick = {
                            showMenu = false
                            onRemoveFromPlaylist()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Set as Ringtone") },
                        onClick = {
                            showMenu = false
                            onDownloadForRingtone()
                        }
                    )
                }
            }
        }
    }
}
