package com.israrxy.raazi.viewmodel

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.israrxy.raazi.RaaziApplication
import com.israrxy.raazi.data.repository.MusicRepository
import com.israrxy.raazi.data.db.MusicDao
import com.israrxy.raazi.data.db.PlaylistEntity
import com.israrxy.raazi.data.db.TrackEntity
import com.israrxy.raazi.data.db.PlaylistTrackCrossRef
import com.israrxy.raazi.model.MusicItem
import com.israrxy.raazi.model.PlaybackState
import com.israrxy.raazi.model.Playlist
import com.israrxy.raazi.model.SearchResult
import com.israrxy.raazi.service.MusicPlaybackService
import com.israrxy.raazi.service.SimpleDownloadManager
import com.israrxy.raazi.service.YouTubeMusicExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import android.util.Log

class MusicPlayerViewModel(
    private val app: Application,
    private val repository: MusicRepository,
    private val musicDao: MusicDao
) : AndroidViewModel(app) {
    
    private val searchHistoryManager = com.israrxy.raazi.data.local.SearchHistoryManager(app)
    val searchHistory: StateFlow<List<String>> = searchHistoryManager.history
    
    private val downloadManager = SimpleDownloadManager(app)
    private val musicExtractor = YouTubeMusicExtractor()
    
    private var playbackService: MusicPlaybackService? = null
    private var isServiceBound = false

    private val _searchResults = MutableStateFlow<SearchResult?>(null)
    val searchResults: StateFlow<SearchResult?> = _searchResults.asStateFlow()

    private val _searchSuggestions = MutableStateFlow<List<String>>(emptyList())
    val searchSuggestions: StateFlow<List<String>> = _searchSuggestions.asStateFlow()
    
    private val _trendingResults = MutableStateFlow<SearchResult?>(null)
    val trendingResults: StateFlow<SearchResult?> = _trendingResults.asStateFlow()

    private val _trendingVideosResults = MutableStateFlow<SearchResult?>(null)
    val trendingVideosResults: StateFlow<SearchResult?> = _trendingVideosResults.asStateFlow()

    private val _allCharts = MutableStateFlow<Map<String, List<MusicItem>>>(emptyMap())
    val allCharts: StateFlow<Map<String, List<MusicItem>>> = _allCharts.asStateFlow()

    // InnerTube HomePage (like OuterTune)
    private val _homePage = MutableStateFlow<com.zionhuang.innertube.pages.HomePage?>(null)
    val homePage: StateFlow<com.zionhuang.innertube.pages.HomePage?> = _homePage.asStateFlow()

    private val _topChartsResults = MutableStateFlow<SearchResult?>(null)
    val topChartsResults: StateFlow<SearchResult?> = _topChartsResults.asStateFlow()

    private val _remixResults = MutableStateFlow<SearchResult?>(null)
    val remixResults: StateFlow<SearchResult?> = _remixResults.asStateFlow()

    val favoriteTracks: StateFlow<List<MusicItem>> = repository.favoriteTracks
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
        
    val downloadedTracks: StateFlow<List<MusicItem>> = repository.downloadedTracks
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _playbackState = MutableStateFlow(PlaybackState())

    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error
    
    private val _downloadResult = MutableStateFlow<String?>(null)
    val downloadResult: StateFlow<String?> = _downloadResult.asStateFlow()

    private val _lyrics = MutableStateFlow<com.israrxy.raazi.data.remote.Lyrics?>(null)
    val lyrics: StateFlow<com.israrxy.raazi.data.remote.Lyrics?> = _lyrics.asStateFlow()

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as MusicPlaybackService.MusicBinder
            playbackService = binder.getService()
            isServiceBound = true
            
            playbackService?.addPlaybackStateListener { state ->
                val oldState = _playbackState.value
                _playbackState.value = state
                
                // Fetch lyrics if track changed
                if (state.currentTrack?.id != oldState.currentTrack?.id) {
                    state.currentTrack?.let { track ->
                        fetchLyrics(track)
                    }
                }
            }
            
            // Update initial state
            _playbackState.value = playbackService?.getPlaybackState() ?: PlaybackState()
            _playbackState.value.currentTrack?.let { fetchLyrics(it) }
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            isServiceBound = false
            playbackService = null
        }
    }

    init {
        bindPlaybackService()
        loadHomeContent()
    }

    private fun bindPlaybackService() {
        val intent = Intent(app, MusicPlaybackService::class.java)
        app.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }
    
    fun loadHomeContent() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                // Load local database content (like OuterTune)
                launch {
                    try {
                        // Quick Picks - recently played favorites
                        val quickPicks = repository.quickPicks.first().shuffled().take(20)
                        _quickPicks.value = quickPicks
                        android.util.Log.d("MusicVM", "Loaded ${quickPicks.size} quick picks from DB")
                    } catch (e: Exception) {
                        android.util.Log.e("MusicVM", "Error loading quick picks", e)
                    }
                }
                
                launch {
                    try {
                        // Keep Listening - recent activity
                        val playbackList = repository.playbackHistory.first().take(10)
                        _keepListening.value = playbackList
                        android.util.Log.d("MusicVM", "Loaded ${playbackList.size} keep listening from DB")
                    } catch (e: Exception) {
                        android.util.Log.e("MusicVM", "Error loading keep listening", e)
                    }
                }
                
                // Similar Recommendations (THE BIG FEATURE!)
                launch {
                    try {
                        loadSimilarRecommendations()
                    } catch (e: Exception) {
                        android.util.Log.e("MusicVM", "Error loading similar recommendations", e)
                    }
                }
                
                // Explore Page
                launch {
                    try {
                        com.zionhuang.innertube.YouTube.explore().onSuccess { page ->
                            _explorePage.value = page
                            android.util.Log.d("MusicVM", "Loaded explore page with ${page.newReleaseAlbums.size} new releases")
                        }.onFailure {
                            android.util.Log.e("MusicVM", "Explore page failed", it)
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("MusicVM", "Error loading explore page", e)
                    }
                }
                
                // Load YouTube Music sections (like OuterTune - don't paginate initially)
                com.zionhuang.innertube.YouTube.home().onSuccess { page ->
                    _homePage.value = page
                    android.util.Log.d("MusicVM", "Loaded ${page.sections.size} sections from InnerTube")
                }.onFailure {
                    android.util.Log.e("MusicVM", "InnerTube home failed", it)
                    _error.value = "Failed to load home feed"
                }
                
            } catch (e: Exception) {
                android.util.Log.e("MusicVM", "Error loading home", e)
                _error.value = "Failed to load content: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    private suspend fun loadSimilarRecommendations() {
        val recommendations = mutableListOf<SimilarRecommendation>()
        
        try {
            val playbackList = repository.playbackHistory.first()
            
            // Artist-based recommendations (top 3 artists)
            val topArtists = playbackList
                .groupBy { it.artist }
                .entries.sortedByDescending { it.value.size }
                .take(3)
            
            topArtists.forEach { (artistName, songs) ->
                try {
                    // Search for artist to get ID
                    val searchResult = com.zionhuang.innertube.YouTube.search(artistName, filter = com.zionhuang.innertube.YouTube.SearchFilter.FILTER_ARTIST).getOrNull()
                    val artistItem = searchResult?.items?.firstOrNull() as? com.zionhuang.innertube.models.ArtistItem
                    
                    if (artistItem != null) {
                        // Get artist page
                        com.zionhuang.innertube.YouTube.artist(artistItem.id).onSuccess { artistPage ->
                            val items = artistPage.sections.takeLast(2).flatMap { it.items }.shuffled()
                            if (items.isNotEmpty()) {
                                recommendations.add(
                                    SimilarRecommendation(
                                        title = "Similar to $artistName",
                                        thumbnailUrl = songs.firstOrNull()?.thumbnailUrl,
                                        items = items
                                    )
                                )
                                android.util.Log.d("MusicVM", "Added artist recommendation: $artistName with ${items.size} items")
                            }
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("MusicVM", "Error loading artist recommendation for $artistName", e)
                }
            }
            
            // Song-based recommendations (top 2 songs)
            val topSongs = playbackList.take(5).distinctBy { it.videoUrl }
            
            topSongs.take(2).forEach { song ->
                try {
                    if (song.videoUrl.isNotEmpty()) {
                        val nextPage = com.zionhuang.innertube.YouTube.next(
                            com.zionhuang.innertube.models.WatchEndpoint(videoId = song.videoUrl)
                        ).getOrNull()
                        
                        val relatedEndpoint = nextPage?.relatedEndpoint
                        if (relatedEndpoint != null) {
                            com.zionhuang.innertube.YouTube.related(relatedEndpoint).onSuccess { relatedPage ->
                                val items = (
                                    relatedPage.songs.shuffled().take(10) +
                                    relatedPage.albums.shuffled().take(4) +
                                    relatedPage.playlists.shuffled().take(3)
                                ).shuffled()
                                
                                if (items.isNotEmpty()) {
                                    recommendations.add(
                                        SimilarRecommendation(
                                            title = "Similar to ${song.title}",
                                            thumbnailUrl = song.thumbnailUrl,
                                            items = items
                                        )
                                    )
                                    android.util.Log.d("MusicVM", "Added song recommendation: ${song.title} with ${items.size} items")
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("MusicVM", "Error loading song recommendation for ${song.title}", e)
                }
            }
            
            _similarRecommendations.value = recommendations.shuffled()
            android.util.Log.d("MusicVM", "Loaded ${recommendations.size} similar recommendation sections")
            
        } catch (e: Exception) {
            android.util.Log.e("MusicVM", "Error in loadSimilarRecommendations", e)
        }
    }

    fun loadMoreSections() {
        val continuation = _homePage.value?.continuation
        if (continuation == null || _isLoadingMore.value) return
        
        viewModelScope.launch {
            _isLoadingMore.value = true
            try {
                com.zionhuang.innertube.YouTube.home(continuation).onSuccess { nextPage ->
                    _homePage.value = _homePage.value?.copy(
                        sections = _homePage.value?.sections.orEmpty() + nextPage.sections,
                        continuation = nextPage.continuation
                    )
                    android.util.Log.d("MusicVM", "Loaded ${nextPage.sections.size} more sections via pagination")
                }.onFailure {
                    android.util.Log.e("MusicVM", "Pagination failed", it)
                }
            } catch (e: Exception) {
                android.util.Log.e("MusicVM", "Error loading more sections", e)
            } finally {
                _isLoadingMore.value = false
            }
        }
    }

    fun selectFilterChip(chip: com.zionhuang.innertube.pages.HomePage.Chip?) {
        viewModelScope.launch {
            try {
                com.zionhuang.innertube.YouTube.home(params = chip?.endpoint?.params).onSuccess { filteredPage ->
                    _homePage.value = filteredPage
                    android.util.Log.d("MusicVM", "Filtered home with chip: ${chip?.title}")
                }.onFailure {
                    android.util.Log.e("MusicVM", "Filter chip failed", it)
                }
            } catch (e: Exception) {
                android.util.Log.e("MusicVM", "Error selecting filter chip", e)
            }
        }
    }

    fun searchMusic(query: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val result = repository.searchMusic(query)
                _searchResults.value = result
                searchHistoryManager.addQuery(query)
            } catch (e: Exception) {
                _error.value = "Search failed: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    // Home Screen Sections
    private val _quickPicks = MutableStateFlow<List<MusicItem>>(emptyList())
    val quickPicks: StateFlow<List<MusicItem>> = _quickPicks.asStateFlow()

    private val _forgottenFavorites = MutableStateFlow<List<MusicItem>>(emptyList())
    val forgottenFavorites: StateFlow<List<MusicItem>> = _forgottenFavorites.asStateFlow()

    private val _keepListening = MutableStateFlow<List<MusicItem>>(emptyList())
    val keepListening: StateFlow<List<MusicItem>> = _keepListening.asStateFlow()
    
    // Similar Recommendations
    data class SimilarRecommendation(
        val title: String,
        val thumbnailUrl: String?,
        val items: List<com.zionhuang.innertube.models.YTItem>
    )
    
    private val _similarRecommendations = MutableStateFlow<List<SimilarRecommendation>>(emptyList())
    val similarRecommendations: StateFlow<List<SimilarRecommendation>> = _similarRecommendations.asStateFlow()
    
    // Explore Page
    private val _explorePage = MutableStateFlow<com.zionhuang.innertube.pages.ExplorePage?>(null)
    val explorePage: StateFlow<com.zionhuang.innertube.pages.ExplorePage?> = _explorePage.asStateFlow()
    
    // Pagination
    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()
    
    // Filter Chips
    private val _selectedChip = MutableStateFlow<String?>(null)
    val selectedChip: StateFlow<String?> = _selectedChip.asStateFlow()

    private val _filteredPlaylists = MutableStateFlow<List<Playlist>?>(null)
    val filteredPlaylists: StateFlow<List<Playlist>?> = _filteredPlaylists.asStateFlow()

    fun onChipSelected(chip: String) {
        if (_selectedChip.value == chip) {
            // Deselect
            _selectedChip.value = null
            _filteredPlaylists.value = null
        } else {
            // Select & Fetch
            _selectedChip.value = chip
            viewModelScope.launch {
                _isLoading.value = true
                try {
                     _filteredPlaylists.value = repository.getMoodPlaylists(chip)
                } catch (e: Exception) {
                    _error.value = "Failed to load $chip playlists"
                } finally {
                    _isLoading.value = false
                }
            }
        }
    }

    val playbackHistory: StateFlow<List<MusicItem>> = repository.playbackHistory
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // ... (rest of code)

    fun playMusic(musicItem: MusicItem) {
        viewModelScope.launch {
            // Add to history
            repository.addToHistory(musicItem)

            // Trigger background fetch for recommendation engine
            launch(Dispatchers.IO) {
                repository.fetchAndSaveRelated(musicItem)
            }

            // Delegate completely to service which handles local files and Raazi resolution
            playbackService?.playMusic(musicItem)
        }
    }

    fun playFromSearch(index: Int) {
        _searchResults.value?.items?.getOrNull(index)?.let { musicItem ->
            playMusic(musicItem)
        }
    }
    
    fun playFromTrending(index: Int) {
        _trendingResults.value?.items?.getOrNull(index)?.let { musicItem ->
            playMusic(musicItem)
        }
    }

    fun toggleFavorite(musicItem: MusicItem) {
        viewModelScope.launch {
            // Check if already in favorites (this is naive, better to check ID)
            val isFavorite = favoriteTracks.value.any { it.id == musicItem.id }
            if (isFavorite) {
                repository.removeFromFavorites(musicItem)
            } else {
                repository.addToFavorites(musicItem)
            }
        }
    }

    private fun fetchLyrics(track: MusicItem) {
        viewModelScope.launch {
            try {
                _lyrics.value = null // Clear old lyrics
                val result = repository.getLyrics(track.title, track.artist, track.duration)
                _lyrics.value = result
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun fetchSearchSuggestions(query: String) {
        if (query.length < 2) {
            _searchSuggestions.value = emptyList()
            return
        }
        viewModelScope.launch {
            try {
                // Should debounce this in UI or use a Flow, but for now simple launch is okay
                val suggestions = repository.getSearchSuggestions(query)
                _searchSuggestions.value = suggestions
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun playFromPlaylist(index: Int) {
        _currentPlaylist.value?.items?.getOrNull(index)?.let { musicItem ->
            playMusic(musicItem)
        }
    }

    fun pause() {
        playbackService?.pause()
    }

    fun resume() {
        playbackService?.resume()
    }

    fun stop() {
        playbackService?.stop()
    }

    fun next() {
        playbackService?.next()
    }

    fun previous() {
        playbackService?.previous()
    }

    fun seekTo(position: Long) {
        playbackService?.seekTo(position)
    }
    
    fun toggleShuffle() {
        playbackService?.toggleShuffle()
    }
    
    fun toggleRepeat() {
        playbackService?.toggleRepeat()
    }
    


    fun clearError() {
        _error.value = null
    }

    fun clearSearchHistory() {
        searchHistoryManager.clearHistory()
    }

    // Playlist functionality
    private val _currentPlaylist = MutableStateFlow<Playlist?>(null)
    val currentPlaylist: StateFlow<Playlist?> = _currentPlaylist.asStateFlow()

    fun loadPlaylist(playlistId: String) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                _error.value = null
                android.util.Log.d("MusicVM", "Loading playlist: $playlistId")
                
                val playlist = repository.getPlaylist(playlistId)
                _currentPlaylist.value = playlist
                android.util.Log.d("MusicVM", "Loaded playlist with ${playlist.items.size} songs")
            } catch (e: Exception) {
                android.util.Log.e("MusicVM", "Error loading playlist", e)
                _error.value = "Error loading playlist: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }



    fun playPlaylist(items: List<MusicItem>, startIndex: Int = 0) {
        if (items.isEmpty()) return
        
        // Delegate to service which now handles stream extraction internally
        playbackService?.playPlaylist(items, startIndex)
        
        // Optimistically update UI state
        _playbackState.value = _playbackState.value.copy(
            playlist = items,
            currentIndex = startIndex,
            currentTrack = items.getOrNull(startIndex)
        )
        android.util.Log.d("MusicVM", "Playing playlist from index $startIndex with ${items.size} total songs")
    }

    // User Playlist Management
    val userPlaylists = musicDao.getAllPlaylists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun createPlaylist(name: String) {
        viewModelScope.launch {
            val validName = name.ifBlank { "My Playlist" }
            val playlist = PlaylistEntity(
                id = java.util.UUID.randomUUID().toString(),
                title = validName,
                description = "Created locally",
                thumbnailUrl = "" 
            )
            musicDao.insertPlaylist(playlist)
        }
    }

    fun addToPlaylist(playlistId: String, track: MusicItem) {
        viewModelScope.launch {
            val trackEntity = TrackEntity(
                id = track.id,
                title = track.title,
                artist = track.artist,
                duration = track.duration,
                thumbnailUrl = track.thumbnailUrl ?: "",
                audioUrl = track.audioUrl ?: "",
                videoUrl = track.videoUrl ?: "",
                isLive = track.isLive,
                timestamp = System.currentTimeMillis()
            )
            musicDao.insertTrack(trackEntity)

            val crossRef = PlaylistTrackCrossRef(
                playlistId = playlistId,
                trackId = track.id,
                position = System.currentTimeMillis().toInt()
            )
            musicDao.insertPlaylistTrackCrossRef(crossRef)
        }
    }

    private val _activeDownloads = MutableStateFlow<Map<Long, Pair<MusicItem, Int>>>(emptyMap())
    val activeDownloads: StateFlow<Map<Long, Pair<MusicItem, Int>>> = _activeDownloads.asStateFlow()

    fun downloadTrack(track: MusicItem) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                val audioUrl = withContext(Dispatchers.IO) {
                    musicExtractor.getAudioStreamUrl(track.videoUrl)
                }
                
                if (audioUrl.isNotEmpty()) {
                    val downloadId = downloadManager.downloadTrack(track, audioUrl)
                    if (downloadId != null) {
                        _downloadResult.value = "Starting download for ${track.title}"
                        trackDownloadProgress(downloadId, track)
                    } else {
                        _error.value = "Failed to start download"
                    }
                } else {
                    _error.value = "Could not find streamable audio"
                }
            } catch (e: Exception) {
                Log.e("MusicPlayerViewModel", "Download failed", e)
                _error.value = "Download failed: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun trackDownloadProgress(downloadId: Long, track: MusicItem) {
        viewModelScope.launch {
            _activeDownloads.value = _activeDownloads.value + (downloadId to (track to 0))
            
            var isDownloading = true
            while (isDownloading) {
                delay(1000)
                val status = downloadManager.getDownloadStatus(downloadId)
                
                when (status.status) {
                    android.app.DownloadManager.STATUS_SUCCESSFUL -> {
                        _activeDownloads.value = _activeDownloads.value - downloadId
                        _downloadResult.value = "Downloaded ${track.title}"
                        isDownloading = false
                    }
                    android.app.DownloadManager.STATUS_FAILED -> {
                        _activeDownloads.value = _activeDownloads.value - downloadId
                        _error.value = "Failed to download ${track.title}"
                        isDownloading = false
                    }
                    else -> {
                        _activeDownloads.value = _activeDownloads.value + (downloadId to (track to status.progress))
                    }
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        if (isServiceBound) {
            playbackService?.removePlaybackStateListener { }
            app.unbindService(serviceConnection)
            isServiceBound = false
        }
    }

    companion object {
        fun provideFactory(application: Application): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    if (modelClass.isAssignableFrom(MusicPlayerViewModel::class.java)) {
                        val raaziApp = application as RaaziApplication
                        @Suppress("UNCHECKED_CAST")
                        return MusicPlayerViewModel(raaziApp, raaziApp.container.musicRepository, raaziApp.database.musicDao()) as T
                    }
                    throw IllegalArgumentException("Unknown ViewModel class")
                }
            }
        }
    }
}