package com.israrxy.raazi.ui.library

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.israrxy.raazi.RaaziApplication
import com.israrxy.raazi.data.account.RaaziAccountManager
import com.israrxy.raazi.data.db.PlaylistEntity
import com.israrxy.raazi.model.MusicContentType
import com.israrxy.raazi.model.MusicItem
import com.israrxy.raazi.ui.downloads.toMusicItem
import com.israrxy.raazi.viewmodel.FeedRecoveryEngine
import com.israrxy.raazi.viewmodel.MusicPlayerViewModel
import kotlinx.coroutines.launch

/**
 * Library screen.
 *
 * Responsibilities kept here (and nowhere else): collecting flows, holding user intent
 * (query / filter / sort / dialogs), and building one immutable [LibraryUiState].
 * What gets drawn lives in [LibraryListContent].
 */
@Composable
fun LibraryScreen(
    viewModel: MusicPlayerViewModel,
    onNavigateToPlayer: () -> Unit,
    onNavigateToPlaylist: (String) -> Unit,
    onNavigateToSettings: () -> Unit = {},
    onNavigateToArtist: (String, String) -> Unit = { _, _ -> },
    onNavigateToSpotifyImport: () -> Unit = {},
    onNavigateToYouTubeLogin: () -> Unit = onNavigateToSettings,
    onNavigateToPlaylistMaker: () -> Unit = {}
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
    val playCounts by viewModel.trackPlayCounts.collectAsStateWithLifecycle()
    val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()
    val dismissYoutubePrompt by viewModel.settingsDataStore.dismissYoutubeConnectPrompt.collectAsStateWithLifecycle(initialValue = false)

    val raaziEmail by viewModel.settingsDataStore.raaziUserEmail.collectAsStateWithLifecycle(initialValue = null)
    val raaziName by viewModel.settingsDataStore.raaziUserName.collectAsStateWithLifecycle(initialValue = null)
    var isRaaziSyncing by remember { mutableStateOf(false) }

    val isRaaziLoggedIn = !raaziEmail.isNullOrBlank()
    val isYouTubeLoggedIn = loggedIn

    val syncType = remember(isRaaziLoggedIn, isYouTubeLoggedIn) {
        when {
            isRaaziLoggedIn && isYouTubeLoggedIn -> LibrarySyncType.DUAL
            isRaaziLoggedIn -> LibrarySyncType.GOOGLE_DRIVE
            isYouTubeLoggedIn -> LibrarySyncType.YOUTUBE_MUSIC
            else -> LibrarySyncType.NONE
        }
    }
    val isSyncing = syncing || isRaaziSyncing

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // User intent - survives configuration changes and process recreation.
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(LibraryFilter.ALL) }
    var sort by rememberSaveable { mutableStateOf(LibrarySort.RECENT) }
    var syncCardDismissed by rememberSaveable { mutableStateOf(false) }

    // Dialog state.
    var showCreateChoiceDialog by remember { mutableStateOf(false) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var playlistName by remember { mutableStateOf("") }
    var createSynced by remember { mutableStateOf(false) }

    var playlistToRename by remember { mutableStateOf<PlaylistEntity?>(null) }
    var renamePlaylistName by remember { mutableStateOf("") }
    var showRenameDialog by remember { mutableStateOf(false) }

    var playlistToDelete by remember { mutableStateOf<PlaylistEntity?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    val selectedTracks = remember { mutableStateListOf<MusicItem>() }
    val selectedPlaylists = remember { mutableStateListOf<String>() }
    var isTrackSelectionMode by remember { mutableStateOf(false) }
    var isPlaylistSelectionMode by remember { mutableStateOf(false) }
    var showAddToPlaylistDialog by remember { mutableStateOf(false) }
    var tracksForPlaylistAdd by remember { mutableStateOf<List<MusicItem>>(emptyList()) }

    LaunchedEffect(isTrackSelectionMode, isPlaylistSelectionMode) {
        viewModel.setSelectionModeActive(isTrackSelectionMode || isPlaylistSelectionMode)
    }
    DisposableEffect(Unit) {
        onDispose { viewModel.setSelectionModeActive(false) }
    }

    val state = remember(liked, history, downloads, playlists, saved, playCounts, query, filter, sort) {
        LibraryUiState(
            query = query.trim(),
            filter = filter,
            sort = sort,
            likedSongs = liked,
            history = history,
            userPlaylists = playlists,
            savedPlaylists = saved.filter { it.contentType == MusicContentType.PLAYLIST },
            albums = saved.filter { it.contentType == MusicContentType.ALBUM },
            artists = saved.filter { it.contentType == MusicContentType.ARTIST },
            downloadCount = downloads.size,
            downloadedSongs = downloads.map { it.toMusicItem() },
            playCounts = playCounts
        )
    }

    val actions = LibraryActions(
        search = { query = it },
        selectFilter = { filter = it },
        selectSort = { sort = it },
        openPlaylist = onNavigateToPlaylist,
        openArtist = onNavigateToArtist,
        openSettings = onNavigateToSettings,
        openSpotifyImport = onNavigateToSpotifyImport,
        openYouTubeLogin = onNavigateToYouTubeLogin,
        syncLibrary = {
            when (syncType) {
                LibrarySyncType.DUAL -> {
                    if (!syncing) {
                        viewModel.syncYouTubeLibrary(isManual = true)
                    }
                    if (!isRaaziSyncing) {
                        isRaaziSyncing = true
                        scope.launch {
                            val res = RaaziAccountManager.syncWithDrive(
                                context = context,
                                settingsDataStore = viewModel.settingsDataStore,
                                database = RaaziApplication.instance.database
                            )
                            isRaaziSyncing = false
                            res.fold(
                                onSuccess = { msg ->
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                    scope.launch {
                                        FeedRecoveryEngine.recoverPersonalizedFeed(viewModel, context, showFeedback = false)
                                    }
                                },
                                onFailure = { Toast.makeText(context, "Drive sync: ${it.message}", Toast.LENGTH_SHORT).show() }
                            )
                        }
                    }
                }
                LibrarySyncType.GOOGLE_DRIVE -> {
                    if (!isRaaziSyncing) {
                        isRaaziSyncing = true
                        scope.launch {
                            val res = RaaziAccountManager.syncWithDrive(
                                context = context,
                                settingsDataStore = viewModel.settingsDataStore,
                                database = RaaziApplication.instance.database
                            )
                            isRaaziSyncing = false
                            res.fold(
                                onSuccess = { msg ->
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                    scope.launch {
                                        FeedRecoveryEngine.recoverPersonalizedFeed(viewModel, context, showFeedback = false)
                                    }
                                },
                                onFailure = { Toast.makeText(context, "Drive sync: ${it.message}", Toast.LENGTH_SHORT).show() }
                            )
                        }
                    }
                }
                LibrarySyncType.YOUTUBE_MUSIC -> {
                    if (!syncing) {
                        viewModel.syncYouTubeLibrary(isManual = true)
                    }
                }
                LibrarySyncType.NONE -> {
                    onNavigateToSettings()
                    Toast.makeText(context, "Connect Google Drive or YouTube to sync", Toast.LENGTH_SHORT).show()
                }
            }
        },
        dismissSyncCard = { syncCardDismissed = true },
        dismissSyncCardPermanently = {
            syncCardDismissed = true
            scope.launch { viewModel.settingsDataStore.setDismissYoutubeConnectPrompt(true) }
        },
        createPlaylist = {
            showCreateChoiceDialog = true
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
        removeHistoryTrack = { track -> viewModel.removeTracksFromHistory(listOf(track)) },
        playTracks = { tracks, startIndex ->
            viewModel.playPlaylist(tracks, startIndex)
            onNavigateToPlayer()
        },
        addToPlaylist = { track ->
            tracksForPlaylistAdd = listOf(track)
            showAddToPlaylistDialog = true
        },
        onTrackLongClick = { track ->
            if (!isTrackSelectionMode) {
                isTrackSelectionMode = true
                selectedTracks.clear()
                selectedTracks.add(track)
            } else {
                if (selectedTracks.contains(track)) {
                    selectedTracks.remove(track)
                    if (selectedTracks.isEmpty()) isTrackSelectionMode = false
                } else {
                    selectedTracks.add(track)
                }
            }
        },
        isTrackSelected = { track -> selectedTracks.contains(track) },
        isTrackSelectionMode = isTrackSelectionMode,
        onPlaylistLongClick = { playlistId ->
            if (!isPlaylistSelectionMode) {
                isPlaylistSelectionMode = true
                selectedPlaylists.clear()
                selectedPlaylists.add(playlistId)
            } else {
                if (selectedPlaylists.contains(playlistId)) {
                    selectedPlaylists.remove(playlistId)
                    if (selectedPlaylists.isEmpty()) isPlaylistSelectionMode = false
                } else {
                    selectedPlaylists.add(playlistId)
                }
            }
        },
        isPlaylistSelected = { playlistId -> selectedPlaylists.contains(playlistId) },
        isPlaylistSelectionMode = isPlaylistSelectionMode
    )

    val activeAccountName = when (syncType) {
        LibrarySyncType.DUAL -> raaziName ?: accountName ?: "Drive & YouTube"
        LibrarySyncType.GOOGLE_DRIVE -> raaziName ?: raaziEmail
        LibrarySyncType.YOUTUBE_MUSIC -> accountName
        LibrarySyncType.NONE -> null
    }

    val sync = LibrarySyncState(
        syncType = syncType,
        accountName = activeAccountName,
        syncing = isSyncing,
        dismissed = syncCardDismissed || (!syncType.isConnected && dismissYoutubePrompt)
    )

    // Auto-sync library when visiting screen
    var hasAutoSyncedThisVisit by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(syncType) {
        if (syncType.isConnected && !hasAutoSyncedThisVisit && !isSyncing) {
            hasAutoSyncedThisVisit = true
            if (isYouTubeLoggedIn) {
                viewModel.syncYouTubeLibrary()
            }
            if (isRaaziLoggedIn) {
                scope.launch {
                    val res = RaaziAccountManager.syncWithDrive(
                        context = context,
                        settingsDataStore = viewModel.settingsDataStore,
                        database = RaaziApplication.instance.database
                    )
                    if (res.isSuccess) {
                        FeedRecoveryEngine.recoverPersonalizedFeed(viewModel, context, showFeedback = false)
                    }
                }
            }
        }
    }

    LaunchedEffect(syncStatus) {
        val message = syncStatus ?: return@LaunchedEffect
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        viewModel.clearYouTubeSyncStatus()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = rememberLazyListState(),
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .statusBarsPadding(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 150.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            LibraryListContent(state = state, actions = actions, sync = sync)
        }

        if (!isTrackSelectionMode && !isPlaylistSelectionMode) {
            FloatingActionButton(
                onClick = actions.createPlaylist,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(
                        end = 20.dp,
                        bottom = if (playbackState.currentTrack != null) 90.dp else 20.dp
                    )
            ) {
                Icon(Icons.Default.Add, contentDescription = "New playlist")
            }
        }

        AnimatedVisibility(
            visible = isTrackSelectionMode,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(tween(150)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 12.dp)
        ) {
            LibrarySelectionBottomBar(
                selectedCount = selectedTracks.size,
                onCancel = {
                    selectedTracks.clear()
                    isTrackSelectionMode = false
                },
                onAddToPlaylist = {
                    tracksForPlaylistAdd = selectedTracks.toList()
                    showAddToPlaylistDialog = true
                },
                onDelete = {
                    val count = selectedTracks.size
                    viewModel.removeTracksFromHistory(selectedTracks.toList())
                    Toast.makeText(context, "Removed $count tracks from history", Toast.LENGTH_SHORT).show()
                    selectedTracks.clear()
                    isTrackSelectionMode = false
                },
                deleteLabel = "Delete from history"
            )
        }

        AnimatedVisibility(
            visible = isPlaylistSelectionMode,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(tween(150)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 12.dp)
        ) {
            LibrarySelectionBottomBar(
                selectedCount = selectedPlaylists.size,
                onCancel = {
                    selectedPlaylists.clear()
                    isPlaylistSelectionMode = false
                },
                onDelete = {
                    val count = selectedPlaylists.size
                    selectedPlaylists.forEach { id -> viewModel.deletePlaylist(id) }
                    Toast.makeText(context, "Deleted $count playlists", Toast.LENGTH_SHORT).show()
                    selectedPlaylists.clear()
                    isPlaylistSelectionMode = false
                },
                deleteLabel = "Delete playlists"
            )
        }
    }

    LibraryScreenDialogs(
        viewModel = viewModel,
        showAddToPlaylistDialog = showAddToPlaylistDialog,
        tracksForPlaylistAdd = tracksForPlaylistAdd,
        onDismissAddToPlaylist = {
            showAddToPlaylistDialog = false
            tracksForPlaylistAdd = emptyList()
        },
        onPlaylistSelectedForAdd = { targetPlaylist ->
            viewModel.addToPlaylist(tracksForPlaylistAdd, targetPlaylist)
            Toast.makeText(context, "Added to ${targetPlaylist.displayTitle}", Toast.LENGTH_SHORT).show()
            showAddToPlaylistDialog = false
            tracksForPlaylistAdd = emptyList()
            if (isTrackSelectionMode) {
                selectedTracks.clear()
                isTrackSelectionMode = false
            }
        },
        showCreateChoiceDialog = showCreateChoiceDialog,
        onDismissCreateChoice = { showCreateChoiceDialog = false },
        onCreateEmptyPlaylist = {
            showCreateChoiceDialog = false
            playlistName = ""
            createSynced = loggedIn
            showCreateDialog = true
        },
        onBuildPlaylistWithSearch = {
            showCreateChoiceDialog = false
            onNavigateToPlaylistMaker()
        },
        showCreateDialog = showCreateDialog,
        playlistName = playlistName,
        onPlaylistNameChange = { playlistName = it },
        createSynced = createSynced,
        onCreateSyncedChange = { createSynced = it },
        canCreateSynced = loggedIn,
        onDismissCreate = {
            showCreateDialog = false
            playlistName = ""
            createSynced = false
        },
        onCreatePlaylist = {
            viewModel.createPlaylist(playlistName, syncedToYouTube = createSynced)
            showCreateDialog = false
            playlistName = ""
            createSynced = false
        },
        showRenameDialog = showRenameDialog,
        playlistToRename = playlistToRename,
        renamePlaylistName = renamePlaylistName,
        onRenamePlaylistNameChange = { renamePlaylistName = it },
        onDismissRename = { showRenameDialog = false },
        onRenamePlaylist = {
            playlistToRename?.let { viewModel.renamePlaylist(it.id, renamePlaylistName) }
            showRenameDialog = false
        },
        showDeleteDialog = showDeleteDialog,
        playlistToDelete = playlistToDelete,
        onDismissDelete = { showDeleteDialog = false },
        onDeletePlaylist = {
            playlistToDelete?.let { viewModel.deletePlaylist(it.id) }
            showDeleteDialog = false
        }
    )
}
