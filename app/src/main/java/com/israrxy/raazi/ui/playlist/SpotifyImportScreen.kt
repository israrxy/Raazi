package com.israrxy.raazi.ui.playlist

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.israrxy.raazi.model.MusicItem
import com.israrxy.raazi.viewmodel.MusicPlayerViewModel
import com.israrxy.raazi.viewmodel.SearchViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpotifyImportScreen(
    viewModel: MusicPlayerViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToPlaylist: (String) -> Unit
) {
    val importState by viewModel.spotifyImportState.collectAsStateWithLifecycle()
    val resolvedTracks by viewModel.resolvedSpotifyTracks.collectAsStateWithLifecycle()
    val playlistNameFlow by viewModel.spotifyPlaylistName.collectAsStateWithLifecycle()
    val isYouTubeLoggedIn by viewModel.isYouTubeLoggedIn.collectAsStateWithLifecycle()
    
    val context = LocalContext.current
    var playlistTitle by remember { mutableStateOf("") }
    var syncToYouTube by remember { mutableStateOf(false) }
    var searchReplaceIndex by remember { mutableStateOf<Int?>(null) }

    // Synchronize title flow to mutable state once resolved
    LaunchedEffect(playlistNameFlow) {
        if (playlistNameFlow.isNotBlank()) {
            playlistTitle = playlistNameFlow
        }
    }

    // Success Navigation
    LaunchedEffect(importState) {
        if (importState is MusicPlayerViewModel.SpotifyImportState.Success) {
            val playlistId = (importState as MusicPlayerViewModel.SpotifyImportState.Success).playlistId
            Toast.makeText(context, "Playlist imported successfully!", Toast.LENGTH_SHORT).show()
            viewModel.resetSpotifyImportState()
            onNavigateToPlaylist(playlistId)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.resetSpotifyImportState()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Import from Spotify", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            when (val state = importState) {
                is MusicPlayerViewModel.SpotifyImportState.Idle -> {
                    SpotifyInputLayout(
                        onImport = { url ->
                            viewModel.startSpotifyImport(url)
                        }
                    )
                }
                is MusicPlayerViewModel.SpotifyImportState.FetchingSpotify -> {
                    SpotifyLoadingLayout(
                        title = "Downloading Playlist",
                        subtitle = "Fetching details from Spotify public web embed..."
                    )
                }
                is MusicPlayerViewModel.SpotifyImportState.ResolvingTracks -> {
                    SpotifyResolvingLayout(
                        current = state.current,
                        total = state.total
                    )
                }
                is MusicPlayerViewModel.SpotifyImportState.Reviewing -> {
                    SpotifyReviewLayout(
                        tracks = resolvedTracks,
                        playlistTitle = playlistTitle,
                        onTitleChange = { playlistTitle = it },
                        syncToYouTube = syncToYouTube,
                        onSyncChange = { syncToYouTube = it },
                        canSync = isYouTubeLoggedIn,
                        onReplaceClick = { index -> searchReplaceIndex = index },
                        onPlayPreview = { track -> viewModel.playMusic(track) },
                        onSave = {
                            viewModel.finalizeSpotifyImport(playlistTitle, syncToYouTube)
                        }
                    )
                }
                is MusicPlayerViewModel.SpotifyImportState.Importing -> {
                    SpotifyLoadingLayout(
                        title = "Creating Playlist",
                        subtitle = "Saving tracks to database..."
                    )
                }
                is MusicPlayerViewModel.SpotifyImportState.Error -> {
                    SpotifyErrorLayout(
                        message = state.message,
                        onRetry = {
                            viewModel.resetSpotifyImportState()
                        }
                    )
                }
                else -> {}
            }

            // Search Replace Dialog
            if (searchReplaceIndex != null) {
                SearchReplaceDialog(
                    onDismiss = { searchReplaceIndex = null },
                    onSelectTrack = { selectedTrack ->
                        searchReplaceIndex?.let { index ->
                            viewModel.replaceResolvedTrack(index, selectedTrack)
                        }
                        searchReplaceIndex = null
                    }
                )
            }
        }
    }
}

