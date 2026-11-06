package com.israrxy.raazi.ui.lockscreen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.israrxy.raazi.model.MusicItem
import com.israrxy.raazi.viewmodel.LockScreenBrowserViewModel
import kotlinx.coroutines.delay

/**
 * Lock-screen overlay browser.
 *
 * Rendered inside [LockScreenBrowserActivity] above the keyguard, so the user
 * can search, browse the queue/history/likes/offline tracks and start playback
 * without unlocking. Everything shown here is playback data only.
 */
@Composable
fun LockScreenBrowserScreen(
    viewModel: LockScreenBrowserViewModel,
    onClose: () -> Unit,
    onOpenApp: () -> Unit
) {
    val playback by viewModel.playbackState.collectAsStateWithLifecycle()
    val isConnected by viewModel.isConnected.collectAsStateWithLifecycle()
    val recentTracks by viewModel.recentTracks.collectAsStateWithLifecycle()
    val favoriteTracks by viewModel.favoriteTracks.collectAsStateWithLifecycle()
    val downloadedTracks by viewModel.downloadedTracks.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()
    val isSearching by viewModel.isSearching.collectAsStateWithLifecycle()
    val searchError by viewModel.searchError.collectAsStateWithLifecycle()

    var query by rememberSaveable { mutableStateOf("") }
    var tab by rememberSaveable { mutableStateOf(LockScreenTab.RECENT) }

    LaunchedEffect(query) {
        if (query.trim().length < 2) {
            viewModel.clearSearch()
        } else {
            delay(350)
            viewModel.search(query)
        }
    }

    val currentTrackId = playback.currentTrack?.id
    val favoriteIds = remember(favoriteTracks) { favoriteTracks.mapTo(mutableSetOf()) { it.id } }
    val showingSearch = query.isNotBlank()
    val showPlaylists = !showingSearch && tab == LockScreenTab.PLAYLISTS

    val tracks: List<MusicItem> = when {
        showingSearch -> searchResults
        tab == LockScreenTab.QUEUE -> playback.playlist
        tab == LockScreenTab.RECENT -> recentTracks
        tab == LockScreenTab.LIKED -> favoriteTracks
        tab == LockScreenTab.OFFLINE -> downloadedTracks
        else -> emptyList()
    }

    val header = when {
        showingSearch -> "Search results"
        tab == LockScreenTab.QUEUE -> "Up next"
        tab == LockScreenTab.RECENT -> "Recently played"
        tab == LockScreenTab.LIKED -> "Liked songs"
        tab == LockScreenTab.OFFLINE -> "Downloaded"
        else -> "Your playlists"
    }

    val emptyMessage = when {
        showingSearch -> "No songs matched \u201C${query.trim()}\u201D."
        tab == LockScreenTab.QUEUE -> "Nothing queued yet. Pick a song below."
        tab == LockScreenTab.RECENT -> "Nothing played yet. Search for a song to start."
        tab == LockScreenTab.LIKED -> "No liked songs yet."
        tab == LockScreenTab.OFFLINE -> "No downloaded songs yet."
        else -> "No playlists yet."
    }

    val background = MaterialTheme.colorScheme.background
    val accent = MaterialTheme.colorScheme.primary

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0f to accent.copy(alpha = 0.2f),
                    0.4f to background,
                    1f to background
                )
            )
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                LockScreenTopBar(
                    isConnected = isConnected,
                    onOpenApp = onOpenApp,
                    onClose = onClose
                )
            }

            item {
                NowPlayingPanel(
                    state = playback,
                    isFavorite = playback.currentTrack?.id?.let { it in favoriteIds } == true,
                    onPlayPause = viewModel::playPause,
                    onPrevious = viewModel::previous,
                    onNext = viewModel::next,
                    onToggleFavorite = { playback.currentTrack?.let(viewModel::toggleFavorite) },
                    onAddToQueue = { playback.currentTrack?.let(viewModel::addToQueue) }
                )
            }

            item {
                BrowserSearchField(query = query, onQueryChange = { query = it })
            }

            if (!showingSearch) {
                item {
                    LockScreenTabRow(selected = tab, onSelect = { tab = it })
                }
            }

            item {
                LockScreenSectionHeader(title = header, count = if (showPlaylists) playlists.size else tracks.size)
            }

            when {
                showingSearch && isSearching -> item {
                    Box(modifier = Modifier.padding(vertical = 24.dp)) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                showingSearch && searchError != null -> item {
                    LockScreenEmptyState(searchError.orEmpty())
                }

                showPlaylists -> {
                    if (playlists.isEmpty()) {
                        item { LockScreenEmptyState(emptyMessage) }
                    } else {
                        items(playlists, key = { it.id }) { playlist ->
                            LockScreenPlaylistRow(
                                title = playlist.title,
                                subtitle = "${playlist.tracks.size} song${if (playlist.tracks.size == 1) "" else "s"}",
                                thumbnailUrl = playlist.thumbnailUrl,
                                onClick = { viewModel.playPlaylist(playlist.tracks) }
                            )
                        }
                    }
                }

                tracks.isEmpty() -> item { LockScreenEmptyState(emptyMessage) }

                else -> itemsIndexed(
                    tracks,
                    key = { index, track -> "${track.id}#$index" }
                ) { index, track ->
                    LockScreenTrackRow(
                        track = track,
                        isCurrent = track.id == currentTrackId,
                        isPlaying = playback.isPlaying,
                        isFavorite = track.id in favoriteIds,
                        showFavorite = showingSearch || tab != LockScreenTab.LIKED,
                        onClick = {
                            if (!showingSearch && tab == LockScreenTab.QUEUE) {
                                viewModel.jumpToQueueIndex(index)
                            } else {
                                viewModel.play(track, tracks)
                            }
                        },
                        onToggleFavorite = { viewModel.toggleFavorite(track) },
                        onAddToQueue = { viewModel.addToQueue(track) }
                    )
                }
            }

            item {
                Text(
                    text = "Playback only \u00B7 nothing personal is accessible without unlocking",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
                )
            }
        }
    }
}
