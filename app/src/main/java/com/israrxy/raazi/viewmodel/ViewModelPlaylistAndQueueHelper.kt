package com.israrxy.raazi.viewmodel

import android.util.Log
import androidx.lifecycle.viewModelScope
import com.israrxy.raazi.data.account.YouTubeAccountSession
import com.israrxy.raazi.data.db.PlaylistEntity
import com.israrxy.raazi.model.MusicItem
import com.israrxy.raazi.model.Playlist
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal fun MusicPlayerViewModel.playMusicImpl(musicItem: MusicItem) {
    viewModelScope.launch {
        try {
            _isSearchQueue.value = false
            _currentTrackId.value = musicItem.id

            _playbackState.value = _playbackState.value.copy(
                playlist = listOf(musicItem),
                currentIndex = 0,
                currentTrack = musicItem,
                isLoading = true
            )

            addToHistoryInternal(musicItem)

            fetchAutoPlayQueue(musicItem)
            launch(Dispatchers.IO) {
                try {
                    repository.fetchAndSaveRelated(musicItem)
                } catch (e: Exception) {
                    Log.w("MusicVM", "Failed to fetch related", e)
                }
            }

            if (isServiceBound && playbackService != null) {
                playbackService?.playMusic(musicItem)
            } else {
                enqueuePendingSingle(musicItem)
                bindPlaybackService()
            }
        } catch (e: Exception) {
            Log.e("MusicVM", "Error playing music: ${musicItem.title}", e)
            _error.value = "Failed to play: ${e.message}"
        }
    }
}

internal fun MusicPlayerViewModel.addToHistoryInternal(musicItem: MusicItem) {
    _keepListening.value = (listOf(musicItem) + _keepListening.value.filter { it.id != musicItem.id }).take(30)
    rebuildHomeFeedState()
    viewModelScope.launch {
        try {
            repository.addToHistory(musicItem)
        } catch (e: Exception) {
            Log.w("MusicVM", "Failed to persist track to history", e)
        }
    }
}

internal fun MusicPlayerViewModel.playSearchQueueImpl(items: List<MusicItem>, startIndex: Int = 0) {
    if (items.isEmpty()) return
    val distinctItems = items.distinctBy { it.id }
    val targetItem = items.getOrNull(startIndex)
    val resolvedIndex = if (targetItem != null) {
        distinctItems.indexOfFirst { it.id == targetItem.id }.coerceAtLeast(0)
    } else {
        startIndex.coerceIn(0, distinctItems.lastIndex)
    }
    _isSearchQueue.value = true
    _autoPlayQueue.value = emptyList()
    playPlaylistInternal(distinctItems, resolvedIndex)
}

internal fun MusicPlayerViewModel.playPlaylistInternal(items: List<MusicItem>, startIndex: Int = 0) {
    if (items.isEmpty()) return
    val safeStartIndex = startIndex.coerceIn(0, items.lastIndex)
    val initialTrack = items.getOrNull(safeStartIndex)
    _currentTrackId.value = initialTrack?.id

    _playbackState.value = _playbackState.value.copy(
        playlist = items,
        currentIndex = safeStartIndex,
        currentTrack = initialTrack,
        isLoading = true
    )

    if (isServiceBound && playbackService != null) {
        playbackService?.playPlaylist(items, safeStartIndex)
    } else {
        enqueuePendingPlaylist(items, safeStartIndex)
        bindPlaybackService()
    }

    initialTrack?.let { currentTrack ->
        addToHistoryInternal(currentTrack)
        viewModelScope.launch(Dispatchers.IO) {
            repository.fetchAndSaveRelated(currentTrack)
        }
    }

    Log.d("MusicVM", "Playing playlist from index $safeStartIndex with ${items.size} total songs (isSearchQueue=${_isSearchQueue.value})")
}

internal fun MusicPlayerViewModel.playFromAutoPlayImpl(autoPlayIndex: Int) {
    val currentQueue = _playbackState.value.playlist
    val queuedIds = currentQueue.map { it.id }.toSet()
    val currentTrackId = _playbackState.value.currentTrack?.id
    val extraTracks = _autoPlayQueue.value.filter { it.id !in queuedIds && it.id != currentTrackId }
    val target = extraTracks.getOrNull(autoPlayIndex) ?: return
    val remaining = extraTracks.drop(autoPlayIndex)
    addToQueue(remaining)
    val newPlaylist = playbackService?.getPlaybackState()?.playlist ?: (currentQueue + remaining)
    val targetIdx = newPlaylist.indexOfFirst { it.id == target.id }
    if (targetIdx >= 0) {
        jumpToQueueIndex(targetIdx)
    } else {
        playMusic(target)
    }
}

internal fun MusicPlayerViewModel.fetchAutoPlayQueueImpl(track: MusicItem) {
    viewModelScope.launch(Dispatchers.IO) {
        _isLoadingAutoPlay.value = true
        try {
            val upNextItems = musicExtractor.getUpNext(track.id)
                .filter { it.id.isNotBlank() && it.id != track.id }
                .distinctBy { it.id }
            if (upNextItems.isNotEmpty()) {
                _autoPlayQueue.value = upNextItems
                withContext(Dispatchers.Main) {
                    val state = _playbackState.value
                    if (state.currentTrack?.id == track.id && state.playlist.size <= 1) {
                        val toAdd = if (state.isShuffleEnabled) upNextItems.shuffled() else upNextItems
                        playbackService?.addToQueue(toAdd)
                    }
                }
            } else {
                val related = repository.getRelatedTracks(track.id).first()
                _autoPlayQueue.value = related.filter { it.id != track.id }
            }
        } catch (e: Exception) {
            Log.w("MusicVM", "fetchAutoPlayQueue failed", e)
        } finally {
            _isLoadingAutoPlay.value = false
        }
    }
}

internal fun MusicPlayerViewModel.startRadioImpl(track: MusicItem) {
    viewModelScope.launch {
        try {
            val related = currentTrackRelated.value.ifEmpty {
                repository.getRelatedTracks(track.id).first()
            }
            val mix = (listOf(track) + related).distinctBy { it.id }
            if (mix.isNotEmpty()) playPlaylist(mix, 0)
        } catch (e: Exception) {
            Log.w("MusicVM", "startRadio failed", e)
        }
    }
}

internal fun MusicPlayerViewModel.saveCurrentQueueAsPlaylistImpl(name: String? = null, tracks: List<MusicItem>? = null) {
    viewModelScope.launch {
        try {
            val finalTracks = tracks ?: run {
                val state = _playbackState.value
                state.playlist.drop(state.currentIndex)
            }.distinctBy { it.id }
            if (finalTracks.isEmpty()) return@launch
            val playlistName = name ?: "My Queue " + SimpleDateFormat(
                "MMM d, HH:mm", Locale.getDefault()
            ).format(Date())
            val entity = repository.createPlaylist(playlistName)
            repository.addToPlaylist(finalTracks, entity)
        } catch (e: Exception) {
            Log.w("MusicVM", "saveCurrentQueueAsPlaylist failed", e)
        }
    }
}

internal fun MusicPlayerViewModel.syncYouTubeLibraryImpl() {
    if (_isSyncingYouTubeLibrary.value) return

    viewModelScope.launch {
        _isSyncingYouTubeLibrary.value = true
        try {
            val result = repository.syncYouTubeLibrary()
            _youTubeSyncStatus.value = "Synced ${result.likedSongs} liked songs and ${result.playlists} playlists."
        } catch (e: Exception) {
            Log.e("MusicVM", "Error syncing YouTube library", e)
            _error.value = "Failed to sync YouTube Music: ${e.message}"
        } finally {
            _isSyncingYouTubeLibrary.value = false
        }
    }
}

internal fun MusicPlayerViewModel.logoutFromYouTubeImpl() {
    viewModelScope.launch {
        try {
            YouTubeAccountSession.clear(settingsDataStore)
            repository.clearYouTubePlaylistsCache()
            _youTubeSyncStatus.value = "Signed out of YouTube Music."
        } catch (e: Exception) {
            Log.e("MusicVM", "Error signing out of YouTube", e)
            _error.value = "Failed to sign out: ${e.message}"
        }
    }
}

internal fun MusicPlayerViewModel.loadPlaylistImpl(playlistId: String) {
    viewModelScope.launch {
        try {
            _isLoading.value = true
            _error.value = null
            Log.d("MusicVM", "Loading playlist: $playlistId")

            if (playlistId == "favorites") {
                if (isYouTubeLoggedIn.value) {
                    try {
                        val likedSongsPlaylist = repository.getLikedSongsPlaylist()
                        _currentPlaylist.value = likedSongsPlaylist
                    } catch (e: Exception) {
                        val favorites = favoriteTracks.value
                        _currentPlaylist.value = Playlist(
                            id = "favorites",
                            title = "Liked Songs",
                            description = "${favorites.size} songs",
                            thumbnailUrl = favorites.firstOrNull()?.thumbnailUrl ?: "",
                            items = favorites
                        )
                        Log.w("MusicVM", "Falling back to local liked songs playlist", e)
                    }
                } else {
                    val favorites = favoriteTracks.value
                    _currentPlaylist.value = Playlist(
                        id = "favorites",
                        title = "Liked Songs",
                        description = "${favorites.size} songs",
                        thumbnailUrl = favorites.firstOrNull()?.thumbnailUrl ?: "",
                        items = favorites
                    )
                }
            } else if (playlistId == "history") {
                val historyItems = repository.playbackHistory.first()
                _currentPlaylist.value = Playlist(
                    id = "history",
                    title = "History",
                    description = "${historyItems.size} recently played",
                    thumbnailUrl = historyItems.firstOrNull()?.thumbnailUrl ?: "",
                    items = historyItems
                )
            } else {
                val playlist = repository.getPlaylist(playlistId)
                _currentPlaylist.value = playlist
            }
        } catch (e: Exception) {
            Log.e("MusicVM", "Error loading playlist", e)
            _error.value = "Error loading playlist: ${e.message}"
        } finally {
            _isLoading.value = false
        }
    }
}

internal fun MusicPlayerViewModel.createPlaylistImpl(name: String, syncedToYouTube: Boolean = false) {
    viewModelScope.launch {
        try {
            val playlist = repository.createPlaylist(name, syncedToYouTube = syncedToYouTube)
            _youTubeSyncStatus.value = if (syncedToYouTube) {
                "Created synced playlist \"${playlist.title}\"."
            } else {
                "Created playlist \"${playlist.title}\"."
            }
        } catch (e: Exception) {
            Log.e("MusicVM", "Error creating playlist", e)
            _error.value = "Failed to create playlist: ${e.message}"
        }
    }
}

internal fun MusicPlayerViewModel.addToPlaylistByIdImpl(playlistId: String, track: MusicItem) {
    viewModelScope.launch {
        try {
            repository.addToPlaylist(playlistId, track)
        } catch (e: Exception) {
            Log.e("MusicVM", "Error adding track to playlist", e)
            _error.value = e.message ?: "Failed to add song to playlist."
        }
    }
}

internal fun MusicPlayerViewModel.addToPlaylistEntityImpl(track: MusicItem, playlist: PlaylistEntity) {
    viewModelScope.launch {
        try {
            repository.addToPlaylist(track, playlist)
        } catch (e: Exception) {
            Log.e("MusicVM", "Error adding track to playlist", e)
            _error.value = e.message ?: "Failed to add song to playlist."
        }
    }
}

internal fun MusicPlayerViewModel.addTracksToPlaylistEntityImpl(tracks: List<MusicItem>, playlist: PlaylistEntity) {
    viewModelScope.launch {
        try {
            repository.addToPlaylist(tracks, playlist)
        } catch (e: Exception) {
            Log.e("MusicVM", "Error adding tracks to playlist", e)
            _error.value = e.message ?: "Failed to add songs to playlist."
        }
    }
}

internal fun MusicPlayerViewModel.deletePlaylistImpl(playlistId: String) {
    viewModelScope.launch {
        try {
            repository.deletePlaylist(playlistId)
            _youTubeSyncStatus.value = "Playlist deleted."
        } catch (e: Exception) {
            Log.e("MusicVM", "Error deleting playlist", e)
            _error.value = e.message ?: "Failed to delete playlist."
        }
    }
}

internal fun MusicPlayerViewModel.syncLocalPlaylistToYouTubeImpl(playlistId: String) {
    viewModelScope.launch {
        try {
            _isLoading.value = true
            _error.value = null
            val remotePlaylistId = repository.syncLocalPlaylistToYouTube(playlistId)
            loadPlaylist(remotePlaylistId)
            _youTubeSyncStatus.value = "Playlist synced to YouTube Music successfully!"
        } catch (e: Exception) {
            Log.e("MusicVM", "Error syncing playlist to YouTube", e)
            _error.value = e.message ?: "Failed to sync playlist to YouTube."
        } finally {
            _isLoading.value = false
        }
    }
}

internal fun MusicPlayerViewModel.renamePlaylistImpl(playlistId: String, newName: String) {
    viewModelScope.launch {
        try {
            repository.renamePlaylist(playlistId, newName)
            if (_currentPlaylist.value?.id == playlistId) {
                _currentPlaylist.value = _currentPlaylist.value?.copy(title = newName)
            }
            _youTubeSyncStatus.value = "Playlist renamed."
        } catch (e: Exception) {
            Log.e("MusicVM", "Error renaming playlist", e)
            _error.value = e.message ?: "Failed to rename playlist."
        }
    }
}

internal fun MusicPlayerViewModel.removeTrackFromPlaylistImpl(playlistId: String, trackId: String, setVideoId: String? = null) {
    viewModelScope.launch {
        try {
            repository.removeTrackFromPlaylist(playlistId, trackId, setVideoId)
            if (_currentPlaylist.value?.id == playlistId) {
                _currentPlaylist.value = _currentPlaylist.value?.copy(
                    items = _currentPlaylist.value?.items?.filter { it.id != trackId } ?: emptyList()
                )
            }
        } catch (e: Exception) {
            Log.e("MusicVM", "Error removing track from playlist", e)
            _error.value = e.message ?: "Failed to remove song."
        }
    }
}

internal fun MusicPlayerViewModel.removeTracksFromPlaylistImpl(playlistId: String, tracks: List<MusicItem>) {
    viewModelScope.launch {
        try {
            repository.removeTracksFromPlaylist(playlistId, tracks)
            val trackIds = tracks.map { it.id }.toSet()
            if (_currentPlaylist.value?.id == playlistId) {
                _currentPlaylist.value = _currentPlaylist.value?.copy(
                    items = _currentPlaylist.value?.items?.filter { it.id !in trackIds } ?: emptyList()
                )
            }
        } catch (e: Exception) {
            Log.e("MusicVM", "Error removing tracks from playlist", e)
            _error.value = e.message ?: "Failed to remove songs."
        }
    }
}

internal fun MusicPlayerViewModel.reorderPlaylistTracksImpl(playlistId: String, trackIds: List<String>) {
    viewModelScope.launch {
        try {
            val current = _currentPlaylist.value
            if (current != null) {
                val byId = current.items.associateBy { it.id }
                val reordered = trackIds.mapNotNull { byId[it] } + current.items.filter { it.id !in trackIds }
                _currentPlaylist.value = current.copy(items = reordered)
            }
            repository.reorderPlaylistTracks(playlistId, trackIds)
        } catch (e: Exception) {
            Log.e("MusicVM", "Error reordering playlist", e)
            _error.value = e.message ?: "Failed to reorder playlist."
            loadPlaylist(playlistId)
        }
    }
}
