package com.israrxy.raazi.viewmodel

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.israrxy.raazi.RaaziApplication
import com.israrxy.raazi.data.account.RaaziAccountManager
import com.israrxy.raazi.data.repository.MusicRepository
import com.israrxy.raazi.data.repository.mapTrackToDomain
import com.israrxy.raazi.model.MusicItem
import com.israrxy.raazi.model.PlaybackState
import com.israrxy.raazi.service.MusicPlaybackService
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Lightweight playback/browse model used by the lock-screen overlay.
 *
 * It binds to the already-running [MusicPlaybackService] (or creates it in the
 * bound state) and exposes only what the lock-screen browser needs: the current
 * queue, local listening history, liked songs, offline tracks and playlists.
 * Nothing account-specific or destructive is surfaced here because the screen
 * is reachable without unlocking the device.
 */
class LockScreenBrowserViewModel(
    application: Application,
    private val repository: MusicRepository
) : AndroidViewModel(application) {

    data class BrowserPlaylist(
        val id: String,
        val title: String,
        val thumbnailUrl: String,
        val tracks: List<MusicItem>
    )

    private var playbackService: MusicPlaybackService? = null

    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _searchResults = MutableStateFlow<List<MusicItem>>(emptyList())
    val searchResults: StateFlow<List<MusicItem>> = _searchResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _searchError = MutableStateFlow<String?>(null)
    val searchError: StateFlow<String?> = _searchError.asStateFlow()

    val recentTracks: StateFlow<List<MusicItem>> = repository.playbackHistory
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val favoriteTracks: StateFlow<List<MusicItem>> = repository.favoriteTracks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val downloadedTracks: StateFlow<List<MusicItem>> = repository.downloadedTracks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val playlists: StateFlow<List<BrowserPlaylist>> = repository.userPlaylistsWithTracks
        .map { rows ->
            rows.map { row ->
                BrowserPlaylist(
                    id = row.playlist.id,
                    title = row.playlist.displayTitle,
                    thumbnailUrl = row.playlist.thumbnailUrl,
                    tracks = row.tracks.map(::mapTrackToDomain)
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private var searchJob: Job? = null

    private val stateListener: (PlaybackState) -> Unit = { _playbackState.value = it }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val service = (binder as? MusicPlaybackService.MusicBinder)?.getService() ?: return
            playbackService = service
            service.addPlaybackStateListener(stateListener)
            _playbackState.value = service.getPlaybackState()
            _isConnected.value = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            playbackService?.removePlaybackStateListener(stateListener)
            playbackService = null
            _isConnected.value = false
        }
    }

    init {
        runCatching {
            application.bindService(
                Intent(application, MusicPlaybackService::class.java),
                serviceConnection,
                Context.BIND_AUTO_CREATE
            )
        }.onFailure { Log.e(TAG, "Could not bind playback service", it) }
    }

    fun search(rawQuery: String) {
        val query = rawQuery.trim()
        searchJob?.cancel()
        if (query.length < 2) {
            _searchResults.value = emptyList()
            _searchError.value = null
            _isSearching.value = false
            return
        }
        searchJob = viewModelScope.launch {
            _isSearching.value = true
            _searchError.value = null
            runCatching { repository.searchMusic(query) }
                .onSuccess { _searchResults.value = it.items }
                .onFailure {
                    _searchResults.value = emptyList()
                    _searchError.value = "Couldn't search right now. Check your connection."
                }
            _isSearching.value = false
        }
    }

    fun clearSearch() {
        searchJob?.cancel()
        _searchResults.value = emptyList()
        _searchError.value = null
        _isSearching.value = false
    }

    fun play(track: MusicItem, from: List<MusicItem>) {
        val service = playbackService ?: return
        val index = from.indexOfFirst { it.id == track.id }
        if (index >= 0 && from.size > 1) {
            service.playPlaylist(from, index)
        } else {
            service.playMusic(track)
        }
    }

    fun playPlaylist(tracks: List<MusicItem>) {
        if (tracks.isNotEmpty()) playbackService?.playPlaylist(tracks, 0)
    }

    fun jumpToQueueIndex(index: Int) {
        playbackService?.jumpToIndex(index)
    }

    fun playPause() {
        val service = playbackService ?: return
        if (service.isPlaying()) service.pause() else service.resume()
    }

    fun next() {
        playbackService?.next()
    }

    fun previous() {
        playbackService?.previous()
    }

    fun seekTo(fraction: Float) {
        val service = playbackService ?: return
        val duration = _playbackState.value.duration
            .takeIf { it > 0L }
            ?: _playbackState.value.currentTrack?.duration
            ?: return
        service.seekTo((duration * fraction.coerceIn(0f, 1f)).toLong())
    }

    fun addToQueue(track: MusicItem) {
        playbackService?.addToQueue(listOf(track))
    }

    fun toggleFavorite(track: MusicItem) {
        viewModelScope.launch {
            val isFavorite = favoriteTracks.value.any { it.id == track.id }
            if (isFavorite) repository.removeFromFavorites(track) else repository.addToFavorites(track)
            RaaziAccountManager.scheduleAutoSync(getApplication<Application>())
        }
    }

    override fun onCleared() {
        super.onCleared()
        runCatching {
            playbackService?.removePlaybackStateListener(stateListener)
            getApplication<Application>().unbindService(serviceConnection)
        }
        playbackService = null
    }

    companion object {
        private const val TAG = "LockScreenBrowserVM"

        fun provideFactory(application: Application): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    if (modelClass.isAssignableFrom(LockScreenBrowserViewModel::class.java)) {
                        val app = application as RaaziApplication
                        return LockScreenBrowserViewModel(app, app.container.musicRepository) as T
                    }
                    throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
                }
            }
    }
}
