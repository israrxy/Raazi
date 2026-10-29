package com.israrxy.raazi.ui.library

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.israrxy.raazi.data.db.PlaylistEntity
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

    var showActionSheet by remember { mutableStateOf(false) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var playlistName by remember { mutableStateOf("") }
    var createSynced by remember { mutableStateOf(false) }
    val context = LocalContext.current

    var playlistToRename by remember { mutableStateOf<PlaylistEntity?>(null) }
    var renamePlaylistName by remember { mutableStateOf("") }
    var showRenameDialog by remember { mutableStateOf(false) }

    var playlistToDelete by remember { mutableStateOf<PlaylistEntity?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    // Modern Search, Filter, Sort, and Grid/List Mode state
    var showSearchField by remember { mutableStateOf(false) }
    var libraryQuery by remember { mutableStateOf("") }
    var libraryFilter by remember { mutableStateOf("All") }
    var sortOrder by remember { mutableStateOf(LibrarySortOrder.RECENTLY_ADDED) }
    var isGridView by remember { mutableStateOf(true) }

    val normalizedQuery = libraryQuery.trim().lowercase()

    // Automatically sync YouTube Music library once on first load if logged in
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

    fun List<SavedCollectionItem>.applyQueryAndSort(): List<SavedCollectionItem> {
        val filtered = if (normalizedQuery.isBlank()) this
        else filter { it.title.lowercase().contains(normalizedQuery) || it.subtitle.lowercase().contains(normalizedQuery) }
        return when (sortOrder) {
            LibrarySortOrder.RECENTLY_ADDED -> filtered
            LibrarySortOrder.TITLE_ASC -> filtered.sortedBy { it.title.lowercase() }
            LibrarySortOrder.TITLE_DESC -> filtered.sortedByDescending { it.title.lowercase() }
        }
    }

    fun List<MusicItem>.applyTrackQueryAndSort(): List<MusicItem> {
        val filtered = if (normalizedQuery.isBlank()) this
        else filter { it.title.lowercase().contains(normalizedQuery) || it.artist.lowercase().contains(normalizedQuery) }
        return when (sortOrder) {
            LibrarySortOrder.RECENTLY_ADDED -> filtered
            LibrarySortOrder.TITLE_ASC -> filtered.sortedBy { it.title.lowercase() }
            LibrarySortOrder.TITLE_DESC -> filtered.sortedByDescending { it.title.lowercase() }
        }
    }

    val filteredUserPlaylists = playlists.filter {
        normalizedQuery.isBlank() || it.playlist.displayTitle.lowercase().contains(normalizedQuery)
    }.let { list ->
        when (sortOrder) {
            LibrarySortOrder.RECENTLY_ADDED -> list
            LibrarySortOrder.TITLE_ASC -> list.sortedBy { it.playlist.displayTitle.lowercase() }
            LibrarySortOrder.TITLE_DESC -> list.sortedByDescending { it.playlist.displayTitle.lowercase() }
        }
    }

    val filteredSavedPlaylists = savedPlaylists.applyQueryAndSort()
    val filteredAlbums = albums.applyQueryAndSort()
    val filteredArtists = artists.applyQueryAndSort()
    val filteredLikedSongs = remember(liked, normalizedQuery, sortOrder) { liked.applyTrackQueryAndSort() }
    val filteredHistory = remember(history, normalizedQuery, sortOrder) { history.distinctBy { it.id }.applyTrackQueryAndSort() }

    val hasAnyContent = liked.isNotEmpty() || history.isNotEmpty() || downloads.isNotEmpty() ||
        filteredUserPlaylists.isNotEmpty() || filteredSavedPlaylists.isNotEmpty() || filteredAlbums.isNotEmpty() || filteredArtists.isNotEmpty()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        // Modern Top Bar
        LibraryTopBar(
            isLoggedIn = loggedIn,
            accountName = accountName,
            onOpenSearch = {
                showSearchField = !showSearchField
                if (!showSearchField) libraryQuery = ""
            },
            onAddClick = { showActionSheet = true },
            onSettingsClick = onNavigateToSettings
        )

        // Expandable In-Library Search
        LibrarySearchBar(
            query = libraryQuery,
            onQueryChange = { libraryQuery = it },
            onClear = { libraryQuery = "" },
            visible = showSearchField
        )

        // Modern Filter Pills (Playlists, Songs, Albums, Artists, Downloaded)
        LibraryFilterChips(
            selectedFilter = libraryFilter,
            onFilterSelect = { libraryFilter = it }
        )

        // Sort Selector & Grid/List View Toggle Bar
        LibrarySortAndViewBar(
            currentSort = sortOrder,
            onSortChange = { sortOrder = it },
            isGridView = isGridView,
            onToggleGridView = { isGridView = !isGridView }
        )

        // Main Library Content (Grid View vs List View)
        if (!hasAnyContent) {
            EmptyLibrary()
        } else if (isGridView) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 140.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Liked Songs Card (Pinned in All, Songs, or Downloaded)
                if (libraryFilter == "All" || libraryFilter == "Songs") {
                    item(span = { GridItemSpan(2) }) {
                        LikedSongsGridCard(
                            songCount = liked.size,
                            onClick = { onNavigateToPlaylist("favorites") }
                        )
                    }
                }

                // User-Created & Synced Playlists
                if (libraryFilter == "All" || libraryFilter == "Playlists") {
                    items(filteredUserPlaylists, key = { "grid_user_pl_${it.playlist.id}" }) { playlist ->
                        PlaylistGridCard(
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

                // Saved Remote Playlists
                if (libraryFilter == "All" || libraryFilter == "Playlists") {
                    items(filteredSavedPlaylists, key = { "grid_saved_pl_${it.id}" }) { item ->
                        SavedCollectionGridCard(
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

                // Albums
                if (libraryFilter == "All" || libraryFilter == "Albums") {
                    items(filteredAlbums, key = { "grid_album_${it.id}" }) { item ->
                        SavedCollectionGridCard(
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

                // Artists
                if (libraryFilter == "All" || libraryFilter == "Artists") {
                    items(filteredArtists, key = { "grid_artist_${it.id}" }) { item ->
                        SavedCollectionGridCard(
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

                // Downloaded Shortcut
                if (libraryFilter == "All" || libraryFilter == "Downloaded") {
                    item(span = { GridItemSpan(2) }) {
                        LibraryRow(
                            icon = Icons.Default.Download,
                            title = "Downloaded Music",
                            subtitle = "${downloads.size} tracks available offline",
                            onClick = onNavigateToDownloads
                        )
                    }
                }
            }
        } else {
            // Dense, Modern List View
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 4.dp, bottom = 140.dp)
            ) {
                // Quick Collections
                if (libraryFilter == "All" || libraryFilter == "Songs") {
                    item {
                        LibraryRow(
                            icon = Icons.Default.Favorite,
                            title = "Liked Songs",
                            subtitle = "${liked.size} songs",
                            onClick = { onNavigateToPlaylist("favorites") }
                        )
                    }
                }

                if (libraryFilter == "All" || libraryFilter == "Downloaded") {
                    item {
                        LibraryRow(
                            icon = Icons.Default.Download,
                            title = "Downloads",
                            subtitle = "${downloads.size} offline tracks",
                            onClick = onNavigateToDownloads
                        )
                    }
                }

                if (libraryFilter == "All") {
                    item {
                        LibraryRow(
                            icon = Icons.Default.History,
                            title = "Recently Played",
                            subtitle = "${history.size} tracks",
                            onClick = { onNavigateToPlaylist("history") }
                        )
                    }
                }

                // User Playlists
                if ((libraryFilter == "All" || libraryFilter == "Playlists") && filteredUserPlaylists.isNotEmpty()) {
                    item { Section("Playlists") }
                    items(filteredUserPlaylists, key = { "list_user_pl_${it.playlist.id}" }) { playlist ->
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

                // Saved Playlists
                if ((libraryFilter == "All" || libraryFilter == "Playlists") && filteredSavedPlaylists.isNotEmpty()) {
                    item { Section("Saved Playlists") }
                    items(filteredSavedPlaylists, key = { "list_saved_pl_${it.id}" }) { item ->
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

                // Albums
                if ((libraryFilter == "All" || libraryFilter == "Albums") && filteredAlbums.isNotEmpty()) {
                    item { Section("Albums") }
                    items(filteredAlbums, key = { "list_album_${it.id}" }) { item ->
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

                // Artists
                if ((libraryFilter == "All" || libraryFilter == "Artists") && filteredArtists.isNotEmpty()) {
                    item { Section("Artists") }
                    items(filteredArtists, key = { "list_artist_${it.id}" }) { item ->
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
            }
        }
    }

    // Modern Action Bottom Sheet (+)
    if (showActionSheet) {
        LibraryActionBottomSheet(
            onNewPlaylist = {
                createSynced = loggedIn
                showCreateDialog = true
            },
            onImportSpotify = onNavigateToSpotifyImport,
            onDismiss = { showActionSheet = false }
        )
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
