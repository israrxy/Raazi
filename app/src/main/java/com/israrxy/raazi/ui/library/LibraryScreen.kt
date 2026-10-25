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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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

    // Library browsing: search, filter and sort
    var libraryQuery by remember { mutableStateOf("") }
    var libraryFilter by remember { mutableStateOf("All") } // All, Songs, Playlists, Albums, Artists
    var librarySortAsc by remember { mutableStateOf(true) }

    val normalizedLibraryQuery = libraryQuery.trim().lowercase()

    // Automatically sync YouTube Music library on first open if logged in and library is empty
    var hasAutoSynced by remember { mutableStateOf(false) }
    LaunchedEffect(loggedIn) {
        if (loggedIn && !hasAutoSynced && liked.isEmpty() && playlists.isEmpty() && !syncing) {
            hasAutoSynced = true
            viewModel.syncYouTubeLibrary()
        }
    }

    LaunchedEffect(syncStatus) {
        val message = syncStatus ?: return@LaunchedEffect
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        viewModel.clearYouTubeSyncStatus()
    }

    val savedPlaylists = saved.filter { it.contentType == MusicContentType.PLAYLIST }
    val albums = saved.filter { it.contentType == MusicContentType.ALBUM }
    val artists = saved.filter { it.contentType == MusicContentType.ARTIST }

    fun List<SavedCollectionItem>.applyLibraryQuery(): List<SavedCollectionItem> =
        if (normalizedLibraryQuery.isBlank()) this
        else filter { it.title.lowercase().contains(normalizedLibraryQuery) || it.subtitle.lowercase().contains(normalizedLibraryQuery) }

    fun List<SavedCollectionItem>.applyLibrarySort(): List<SavedCollectionItem> =
        if (librarySortAsc) sortedBy { it.title.lowercase() } else sortedByDescending { it.title.lowercase() }

    fun List<MusicItem>.applyTrackQueryAndSort(): List<MusicItem> {
        val queried = if (normalizedLibraryQuery.isBlank()) this
        else filter {
            it.title.lowercase().contains(normalizedLibraryQuery) ||
                it.artist.lowercase().contains(normalizedLibraryQuery)
        }
        return if (librarySortAsc) queried else queried.reversed()
    }

    val filteredPlaylists = savedPlaylists.applyLibraryQuery().applyLibrarySort()
    val filteredAlbums = albums.applyLibraryQuery().applyLibrarySort()
    val filteredArtists = artists.applyLibraryQuery().applyLibrarySort()
    val filteredUserPlaylists = playlists.filter {
        normalizedLibraryQuery.isBlank() || it.playlist.displayTitle.lowercase().contains(normalizedLibraryQuery)
    }.let { if (librarySortAsc) it else it.reversed() }

    val filteredLikedSongs = remember(liked, normalizedLibraryQuery, librarySortAsc) {
        liked.applyTrackQueryAndSort()
    }
    val filteredHistorySongs = remember(history, normalizedLibraryQuery, librarySortAsc) {
        history.distinctBy { it.id }.applyTrackQueryAndSort()
    }

    val hasAnyLibraryContent = liked.isNotEmpty() ||
        history.isNotEmpty() ||
        downloads.isNotEmpty() ||
        filteredUserPlaylists.isNotEmpty() ||
        filteredPlaylists.isNotEmpty() ||
        filteredAlbums.isNotEmpty() ||
        filteredArtists.isNotEmpty()

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
            // Search + filter + sort controls
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                OutlinedTextField(
                    value = libraryQuery,
                    onValueChange = { libraryQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search your library") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (libraryQuery.isNotEmpty()) {
                            IconButton(onClick = { libraryQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("All", "Songs", "Playlists", "Albums", "Artists").forEach { filter ->
                        FilterChip(
                            selected = libraryFilter == filter,
                            onClick = { libraryFilter = filter },
                            label = { Text(filter) }
                        )
                    }
                    Spacer(Modifier.width(4.dp))
                    IconButton(onClick = { librarySortAsc = !librarySortAsc }) {
                        Icon(
                            if (librarySortAsc) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                            contentDescription = "Sort order"
                        )
                    }
                }
            }
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

        item { Section("Collections") }
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

        if ((libraryFilter == "All" || libraryFilter == "Playlists") && filteredUserPlaylists.isNotEmpty()) {
            item { Section("Playlists") }
            items(filteredUserPlaylists, key = { "user_pl_${it.playlist.id}" }) { playlist ->
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

        if ((libraryFilter == "All" || libraryFilter == "Playlists") && filteredPlaylists.isNotEmpty()) {
            item { Section("Saved Playlists") }
            items(filteredPlaylists, key = { "saved_pl_${it.id}" }) { item ->
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

        if ((libraryFilter == "All" || libraryFilter == "Albums") && filteredAlbums.isNotEmpty()) {
            item { Section("Albums") }
            items(filteredAlbums, key = { "album_${it.id}" }) { item ->
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

        if ((libraryFilter == "All" || libraryFilter == "Artists") && filteredArtists.isNotEmpty()) {
            item { Section("Artists") }
            items(filteredArtists, key = { "artist_${it.id}" }) { item ->
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

        if ((libraryFilter == "All" || libraryFilter == "Songs") && filteredLikedSongs.isNotEmpty()) {
            val displayedLiked = if (libraryFilter == "Songs" || normalizedLibraryQuery.isNotBlank()) {
                filteredLikedSongs
            } else {
                filteredLikedSongs.take(15)
            }
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(end = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        Section("Liked Songs")
                    }
                    if (filteredLikedSongs.size > 15 && libraryFilter == "All") {
                        TextButton(onClick = { onNavigateToPlaylist("favorites") }) {
                            Text("See all (${filteredLikedSongs.size})")
                        }
                    }
                }
            }
            items(displayedLiked, key = { "liked_song_${it.id}" }) { track ->
                LibraryRow(
                    icon = Icons.Default.Favorite,
                    title = track.title,
                    subtitle = track.artist,
                    thumbnailUrl = track.thumbnailUrl,
                    showChevron = false,
                    onClick = {
                        viewModel.playPlaylist(filteredLikedSongs, filteredLikedSongs.indexOf(track).coerceAtLeast(0))
                        onNavigateToPlayer()
                    }
                )
            }
        }

        if ((libraryFilter == "All" || libraryFilter == "Songs") && filteredHistorySongs.isNotEmpty()) {
            val displayedHistory = if (libraryFilter == "Songs" || normalizedLibraryQuery.isNotBlank()) {
                filteredHistorySongs.take(30)
            } else {
                filteredHistorySongs.take(10)
            }
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(end = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        Section("Recently Played")
                    }
                    if (filteredHistorySongs.size > 10 && libraryFilter == "All") {
                        TextButton(onClick = { onNavigateToPlaylist("history") }) {
                            Text("See all")
                        }
                    }
                }
            }
            items(displayedHistory, key = { "history_song_${it.id}" }) { track ->
                LibraryRow(
                    icon = Icons.Default.History,
                    title = track.title,
                    subtitle = track.artist,
                    thumbnailUrl = track.thumbnailUrl,
                    showChevron = false,
                    onClick = {
                        viewModel.playPlaylist(filteredHistorySongs, filteredHistorySongs.indexOf(track).coerceAtLeast(0))
                        onNavigateToPlayer()
                    }
                )
            }
        }

        if (!hasAnyLibraryContent) {
            item { EmptyLibrary() }
        }
    }

    if (showCreateDialog) {
        CreatePlaylistDialog(
            name = playlistName,
            onNameChange = { playlistName = it },
            createSynced = createSynced,
            onCreateSyncedChange = { createSynced = it },
            canCreateSynced = loggedIn,
            onDismiss = { showCreateDialog = false; playlistName = ""; createSynced = false },
            onCreate = {
                viewModel.createPlaylist(playlistName, syncedToYouTube = createSynced)
                showCreateDialog = false; playlistName = ""; createSynced = false
            }
        )
    }

    if (showRenameDialog && playlistToRename != null) {
        RenamePlaylistDialog(
            name = renamePlaylistName,
            onNameChange = { renamePlaylistName = it },
            onDismiss = { showRenameDialog = false },
            onRename = { viewModel.renamePlaylist(playlistToRename!!.id, renamePlaylistName); showRenameDialog = false }
        )
    }

    if (showDeleteDialog && playlistToDelete != null) {
        DeletePlaylistDialog(
            playlistTitle = playlistToDelete?.displayTitle.orEmpty(),
            onDismiss = { showDeleteDialog = false },
            onDelete = { viewModel.deletePlaylist(playlistToDelete!!.id); showDeleteDialog = false }
        )
    }
}

