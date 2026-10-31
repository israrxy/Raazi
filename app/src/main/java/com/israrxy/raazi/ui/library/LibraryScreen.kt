package com.israrxy.raazi.ui.library

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.israrxy.raazi.data.db.PlaylistEntity
import com.israrxy.raazi.model.MusicContentType
import com.israrxy.raazi.model.MusicItem
import com.israrxy.raazi.viewmodel.MusicPlayerViewModel

/**
 * Library screen.
 *
 * Responsibilities kept here (and nowhere else): collecting flows, holding user intent
 * (query / filter / sort / layout / dialogs), and building one immutable [LibraryUiState].
 * What gets drawn lives in [LibraryGridContent].
 */
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

    val context = LocalContext.current

    // User intent - survives configuration changes and process recreation.
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(LibraryFilter.ALL) }
    var sort by rememberSaveable { mutableStateOf(LibrarySort.RECENT) }
    var layout by rememberSaveable { mutableStateOf(LibraryLayout.GRID) }

    // Dialog state.
    var showCreateDialog by remember { mutableStateOf(false) }
    var playlistName by remember { mutableStateOf("") }
    var createSynced by remember { mutableStateOf(false) }

    var playlistToRename by remember { mutableStateOf<PlaylistEntity?>(null) }
    var renamePlaylistName by remember { mutableStateOf("") }
    var showRenameDialog by remember { mutableStateOf(false) }

    var playlistToDelete by remember { mutableStateOf<PlaylistEntity?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    val state = remember(liked, history, downloads, playlists, saved, query, filter, sort, layout) {
        LibraryUiState(
            query = query.trim(),
            filter = filter,
            sort = sort,
            layout = layout,
            likedSongs = liked,
            history = history,
            userPlaylists = playlists,
            savedPlaylists = saved.filter { it.contentType == MusicContentType.PLAYLIST },
            albums = saved.filter { it.contentType == MusicContentType.ALBUM },
            artists = saved.filter { it.contentType == MusicContentType.ARTIST },
            downloadCount = downloads.size
        )
    }

    val actions = LibraryActions(
        search = { query = it },
        selectFilter = { filter = it },
        selectSort = { sort = it },
        selectLayout = { layout = it },
        openPlaylist = onNavigateToPlaylist,
        openArtist = onNavigateToArtist,
        openDownloads = onNavigateToDownloads,
        openSettings = onNavigateToSettings,
        openSpotifyImport = onNavigateToSpotifyImport,
        syncLibrary = { viewModel.syncYouTubeLibrary() },
        createPlaylist = {
            createSynced = loggedIn
            playlistName = ""
            showCreateDialog = true
        },
        renamePlaylist = { playlistId ->
            playlists.firstOrNull { it.playlist.id == playlistId }?.playlist?.let { playlist ->
                playlistToRename = playlist
                renamePlaylistName = playlist.displayTitle
                showRenameDialog = true
            }
        },
        deletePlaylist = { playlistId ->
            playlists.firstOrNull { it.playlist.id == playlistId }?.playlist?.let { playlist ->
                playlistToDelete = playlist
                showDeleteDialog = true
            }
        },
        removeSavedCollection = { savedItem ->
            viewModel.toggleSavedCollection(
                MusicItem(
                    id = savedItem.sourceId,
                    title = savedItem.title,
                    artist = savedItem.subtitle,
                    duration = 0L,
                    thumbnailUrl = savedItem.thumbnailUrl,
                    audioUrl = "",
                    videoUrl = "",
                    isPlaylist = savedItem.contentType == MusicContentType.PLAYLIST,
                    contentType = savedItem.contentType
                )
            )
        },
        removeSavedArtist = { savedItem ->
            viewModel.toggleSavedArtist(
                artistId = savedItem.sourceId,
                artistName = savedItem.title,
                thumbnailUrl = savedItem.thumbnailUrl
            )
        },
        playTracks = { tracks, startIndex ->
            viewModel.playPlaylist(tracks, startIndex)
            onNavigateToPlayer()
        }
    )

    val sync = LibrarySyncState(loggedIn = loggedIn, accountName = accountName, syncing = syncing)

    // First open on an empty library: pull the YouTube Music library down once.
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

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 132.dp),
        state = rememberLazyGridState(),
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 150.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        LibraryGridContent(state = state, actions = actions, sync = sync)
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
        RenamePlaylistDialog(
            name = renamePlaylistName,
            onNameChange = { renamePlaylistName = it },
            onDismiss = { showRenameDialog = false },
            onRename = {
                viewModel.renamePlaylist(playlistToRename!!.id, renamePlaylistName)
                showRenameDialog = false
            }
        )
    }

    if (showDeleteDialog && playlistToDelete != null) {
        DeletePlaylistDialog(
            playlistTitle = playlistToDelete?.displayTitle.orEmpty(),
            onDismiss = { showDeleteDialog = false },
            onDelete = {
                viewModel.deletePlaylist(playlistToDelete!!.id)
                showDeleteDialog = false
            }
        )
    }
}
