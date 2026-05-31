package com.israrxy.raazi.ui.library

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Album
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.QueueMusic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.israrxy.raazi.data.db.PlaylistEntity
import com.israrxy.raazi.data.playlist.isYouTubeEditablePlaylist
import com.israrxy.raazi.data.playlist.isYouTubeSyncedPlaylist
import com.israrxy.raazi.model.MusicContentType
import com.israrxy.raazi.model.MusicItem
import com.israrxy.raazi.model.SavedCollectionItem
import com.israrxy.raazi.viewmodel.MusicPlayerViewModel

@Composable
fun LibraryScreen(
    viewModel: MusicPlayerViewModel,
    onNavigateToPlayer: () -> Unit,
    onNavigateToPlaylist: (String) -> Unit,
    onNavigateToDownloads: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onNavigateToArtist: (String, String) -> Unit = { _, _ -> },
    onNavigateToSpotifyImport: () -> Unit = {}
) {
    val liked by viewModel.favoriteTracks.collectAsStateWithLifecycle()
    val history by viewModel.playbackHistory.collectAsStateWithLifecycle(initialValue = emptyList())
    val downloads by viewModel.dbCompletedDownloads.collectAsStateWithLifecycle()
    val playlists by viewModel.userPlaylistsWithTracks.collectAsStateWithLifecycle(initialValue = emptyList())
    val saved by viewModel.savedCollections.collectAsStateWithLifecycle()
    val loggedIn by viewModel.isYouTubeLoggedIn.collectAsStateWithLifecycle()
    val accountName by viewModel.youTubeAccountName.collectAsStateWithLifecycle()
    val syncing by viewModel.isSyncingYouTubeLibrary.collectAsStateWithLifecycle()
    val syncStatus by viewModel.youTubeSyncStatus.collectAsStateWithLifecycle()

    var showCreateDialog by remember { mutableStateOf(false) }
    var playlistName by remember { mutableStateOf("") }
    var createSynced by remember { mutableStateOf(false) }
    val context = LocalContext.current

    var playlistToRename by remember { mutableStateOf<PlaylistEntity?>(null) }
    var renamePlaylistName by remember { mutableStateOf("") }
    var showRenameDialog by remember { mutableStateOf(false) }

    var playlistToDelete by remember { mutableStateOf<PlaylistEntity?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    LaunchedEffect(syncStatus) {
        val message = syncStatus ?: return@LaunchedEffect
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        viewModel.clearYouTubeSyncStatus()
    }

    val savedPlaylists = saved.filter { it.contentType == MusicContentType.PLAYLIST }
    val albums = saved.filter { it.contentType == MusicContentType.ALBUM }
    val artists = saved.filter { it.contentType == MusicContentType.ARTIST }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding(),
        contentPadding = PaddingValues(bottom = 140.dp)
    ) {
        item {
            Header(onNavigateToSettings)
        }

        item {
            LibraryRow(
                icon = if (loggedIn) Icons.Default.CloudDone else Icons.Default.CloudOff,
                title = if (loggedIn) accountName ?: "YouTube Music connected" else "Connect YouTube Music",
                subtitle = if (loggedIn) "Sync likes and playlists" else "Sign in for account sync",
                onClick = if (loggedIn) ({ viewModel.syncYouTubeLibrary() }) else onNavigateToSettings,
                trailing = {
                    if (syncing) {
                        LinearProgressIndicator(modifier = Modifier.width(72.dp))
                    } else {
                        Text(
                            text = if (loggedIn) "Sync" else "Settings",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            )
            DividerInset()
        }

        item { Section("Library") }
        item {
            LibraryRow(
                icon = Icons.Default.Favorite,
                title = "Liked Songs",
                subtitle = "${liked.size} songs",
                onClick = { onNavigateToPlaylist("favorites") }
            )
            LibraryRow(
                icon = Icons.Default.History,
                title = "History",
                subtitle = "${history.size} recently played",
                onClick = { onNavigateToPlaylist("history") }
            )
            LibraryRow(
                icon = Icons.Default.Download,
                title = "Downloads",
                subtitle = "${downloads.size} offline tracks",
                onClick = onNavigateToDownloads
            )
            LibraryRow(
                icon = Icons.Default.Add,
                title = "New Playlist",
                subtitle = if (loggedIn) "Local or YouTube Music" else "Local playlist",
                onClick = {
                    createSynced = loggedIn
                    showCreateDialog = true
                },
                showChevron = false
            )
            LibraryRow(
                icon = Icons.Default.CloudDownload,
                title = "Import from Spotify",
                subtitle = "Paste a link to import public playlists",
                onClick = onNavigateToSpotifyImport,
                showChevron = true
            )
        }

        if (playlists.isNotEmpty()) {
            item { Section("Playlists") }
            items(playlists, key = { it.playlist.id }) { playlist ->
                PlaylistRow(
                    playlist = playlist,
                    onClick = { onNavigateToPlaylist(playlist.playlist.id) },
                    onRename = { 
                        playlistToRename = playlist.playlist
                        renamePlaylistName = playlist.playlist.displayTitle
                        showRenameDialog = true
                    },
                    onDelete = {
                        playlistToDelete = playlist.playlist
                        showDeleteDialog = true
                    }
                )
            }
        }

        if (savedPlaylists.isNotEmpty()) {
            item { Section("Saved Playlists") }
            items(savedPlaylists, key = { it.id }) { item ->
                SavedRow(
                    item = item,
                    onClick = { onNavigateToPlaylist(item.sourceId) },
                    onRemove = {
                        viewModel.toggleSavedCollection(
                            MusicItem(
                                id = item.sourceId,
                                title = item.title,
                                artist = item.subtitle,
                                duration = 0L,
                                thumbnailUrl = item.thumbnailUrl,
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

        if (albums.isNotEmpty()) {
            item { Section("Albums") }
            items(albums, key = { it.id }) { item ->
                SavedRow(
                    item = item,
                    onClick = { onNavigateToPlaylist(item.sourceId) },
                    onRemove = {
                        viewModel.toggleSavedCollection(
                            MusicItem(
                                id = item.sourceId,
                                title = item.title,
                                artist = item.subtitle,
                                duration = 0L,
                                thumbnailUrl = item.thumbnailUrl,
                                audioUrl = "",
                                videoUrl = "",
                                contentType = MusicContentType.ALBUM
                            )
                        )
                    }
                )
            }
        }

        if (artists.isNotEmpty()) {
            item { Section("Artists") }
            items(artists, key = { it.id }) { item ->
                SavedRow(
                    item = item,
                    onClick = { onNavigateToArtist(item.sourceId, item.title) },
                    onRemove = {
                        viewModel.toggleSavedArtist(
                            artistId = item.sourceId,
                            artistName = item.title,
                            thumbnailUrl = item.thumbnailUrl
                        )
                    }
                )
            }
        }

        if (playlists.isEmpty() && saved.isEmpty()) {
            item {
                EmptyLibrary()
            }
        }
    }

    if (showCreateDialog) {
        CreatePlaylistDialog(
            name = playlistName,
            onNameChange = { playlistName = it },
            createSynced = createSynced,
            onCreateSyncedChange = { createSynced = it },
            canCreateSynced = loggedIn,
            onDismiss = {
                showCreateDialog = false
                playlistName = ""
                createSynced = false
            },
            onCreate = {
                viewModel.createPlaylist(playlistName, syncedToYouTube = createSynced)
                showCreateDialog = false
                playlistName = ""
                createSynced = false
            }
        )
    }

    if (showRenameDialog && playlistToRename != null) {
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
                        viewModel.renamePlaylist(playlistToRename!!.id, renamePlaylistName)
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

    if (showDeleteDialog && playlistToDelete != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Playlist") },
            text = { Text("Are you sure you want to delete '${playlistToDelete?.displayTitle}'? This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deletePlaylist(playlistToDelete!!.id)
                        showDeleteDialog = false
                    }
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun Header(onSettings: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Library",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onSettings) {
            Icon(Icons.Default.Settings, contentDescription = "Settings")
        }
    }
}

@Composable
private fun Section(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
    )
}

@Composable
private fun PlaylistRow(
    playlist: com.israrxy.raazi.data.db.PlaylistWithTracks,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    LibraryRow(
        icon = Icons.Outlined.QueueMusic,
        title = playlist.playlist.displayTitle,
        subtitle = buildString {
            if (playlist.playlist.isYouTubeSyncedPlaylist()) {
                append("Synced playlist")
            } else {
                append("${playlist.tracks.size} songs")
            }
            if (playlist.playlist.isYouTubeSyncedPlaylist()) {
                if (playlist.playlist.isYouTubeEditablePlaylist()) {
                    append(" • Synced with YouTube Music")
                } else {
                    append(" • Saved from YouTube Music")
                }
            } else {
                append(" • Local Playlist")
            }
        },
        thumbnailUrl = playlist.playlist.thumbnailUrl,
        onClick = onClick,
        onLongClick = { showMenu = true },
        trailing = {
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(androidx.compose.material.icons.Icons.Default.MoreVert, contentDescription = "Options")
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Rename") },
                        onClick = {
                            showMenu = false
                            onRename()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete") },
                        onClick = {
                            showMenu = false
                            onDelete()
                        }
                    )
                }
            }
        }
    )
}

@Composable
private fun SavedRow(
    item: SavedCollectionItem,
    onClick: () -> Unit,
    onRemove: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    LibraryRow(
        icon = when (item.contentType) {
            MusicContentType.ARTIST -> Icons.Outlined.Person
            MusicContentType.ALBUM -> Icons.Outlined.Album
            else -> Icons.Outlined.Bookmark
        },
        title = item.title,
        subtitle = item.subtitle,
        thumbnailUrl = item.thumbnailUrl,
        onClick = onClick,
        onLongClick = { showMenu = true },
        trailing = {
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Options")
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Remove from Library") },
                        onClick = {
                            showMenu = false
                            onRemove()
                        }
                    )
                }
            }
        }
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LibraryRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    thumbnailUrl: String? = null,
    showChevron: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    val isCustomIcon = icon == Icons.Default.Favorite || 
                       icon == Icons.Default.History || 
                       icon == Icons.Default.Download || 
                       icon == Icons.Default.Add ||
                       icon == Icons.Default.CloudDownload

    val iconBackgroundModifier = if (!thumbnailUrl.isNullOrBlank()) {
        Modifier
    } else if (isCustomIcon) {
        val colors = when (icon) {
            Icons.Default.Favorite -> listOf(Color(0xFFE91E63), Color(0xFF8E24AA)) // Pink to Purple
            Icons.Default.History -> listOf(Color(0xFF1E88E5), Color(0xFF1565C0))    // Blue to Dark Blue
            Icons.Default.Download -> listOf(Color(0xFF00897B), Color(0xFF004D40))   // Teal to Dark Teal
            Icons.Default.Add -> listOf(Color(0xFFF4511E), Color(0xFFBF360C))        // Deep Orange
            Icons.Default.CloudDownload -> listOf(Color(0xFF1DB954), Color(0xFF191414)) // Spotify Green
            else -> listOf(Color.Gray, Color.DarkGray)
        }
        Modifier.background(Brush.linearGradient(colors))
    } else {
        Modifier.background(MaterialTheme.colorScheme.surfaceVariant)
    }

    val iconTint = if (isCustomIcon) Color.White else MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(12.dp))
                .then(iconBackgroundModifier),
            contentAlignment = Alignment.Center
        ) {
            if (!thumbnailUrl.isNullOrBlank()) {
                AsyncImage(
                    model = thumbnailUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(icon, contentDescription = null, tint = iconTint)
            }
        }

        Spacer(Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        when {
            trailing != null -> trailing()
            showChevron -> Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun DividerInset() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 82.dp, end = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    )
}

@Composable
private fun EmptyLibrary() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 56.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(Icons.Outlined.Bookmark, contentDescription = null, modifier = Modifier.size(52.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Nothing saved yet", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(
            text = "Liked songs, playlists, albums, and artists will show up here.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun CreatePlaylistDialog(
    name: String,
    onNameChange: (String) -> Unit,
    createSynced: Boolean,
    onCreateSyncedChange: (Boolean) -> Unit,
    canCreateSynced: Boolean,
    onDismiss: () -> Unit,
    onCreate: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Playlist") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                TextField(
                    value = name,
                    onValueChange = onNameChange,
                    placeholder = { Text("Playlist name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (canCreateSynced) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Create in YouTube Music", fontWeight = FontWeight.SemiBold)
                            Text(
                                text = "Available in your account and Raazi.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(checked = createSynced, onCheckedChange = onCreateSyncedChange)
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onCreate, enabled = name.isNotBlank()) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
