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
import com.israrxy.raazi.data.account.YouTubeAccountSession
import com.israrxy.raazi.data.db.HomeInteractionEntity
import com.israrxy.raazi.data.repository.MusicRepository
import com.israrxy.raazi.data.db.MusicDao
import com.israrxy.raazi.data.db.PlaylistEntity
import com.israrxy.raazi.data.db.TrackEntity
import com.israrxy.raazi.data.db.PlaylistTrackCrossRef
import com.israrxy.raazi.data.lyrics.LyricsScriptFilter
import com.israrxy.raazi.model.MusicItem
import com.israrxy.raazi.model.PlaybackState
import com.israrxy.raazi.model.MusicContentType
import com.israrxy.raazi.model.Playlist
import com.israrxy.raazi.model.SavedCollectionItem
import com.israrxy.raazi.model.SearchResult
import com.israrxy.raazi.model.toSavedCollectionItem
import com.israrxy.raazi.model.toSavedCollectionItemOrNull
import com.israrxy.raazi.service.MusicPlaybackService
import com.israrxy.raazi.service.AdvancedDownloadManager
import com.israrxy.raazi.data.db.DownloadEntity
import com.israrxy.raazi.service.YouTubeMusicExtractor
import com.israrxy.raazi.data.local.SettingsDataStore
import com.israrxy.raazi.data.remote.LyricsSearchResult
import android.os.Build
import android.provider.Settings
import com.israrxy.raazi.service.RingtoneType
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
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
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.Job
import kotlinx.coroutines.sync.withPermit
import android.util.Log

data class HomeFeedUiState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val sections: List<HomeSection> = emptyList(),
    val errorMessage: String? = null
)

data class HomeSection(
    val id: String,
    val title: String,
    val type: HomeSectionType,
    val sourceType: String,
    val items: List<HomeFeedItem> = emptyList(),
    val actionLabel: String? = null,
    val isLoading: Boolean = false
)

enum class HomeSectionType {
    STATUS,
    KEEP_LISTENING,
    QUICK_PICKS,
    FORGOTTEN_FAVORITES,
    RECOMMENDATION,
    NEW_RELEASES,
    MOODS,
    MOOD_PLAYLISTS,
    YOUTUBE_RAIL
}

sealed class HomeFeedItem {
    data class Music(val item: MusicItem) : HomeFeedItem()
    data class YouTube(val item: com.zionhuang.innertube.models.YTItem) : HomeFeedItem()
    data class Mood(val title: String) : HomeFeedItem()
    data class PlaylistResult(val playlist: Playlist) : HomeFeedItem()
}

class MusicPlayerViewModel(
    private val app: Application,
    private val repository: MusicRepository,
    private val musicDao: MusicDao
) : AndroidViewModel(app) {
    
    private val searchHistoryManager = com.israrxy.raazi.data.local.SearchHistoryManager(app)
    val searchHistory: StateFlow<List<String>> = searchHistoryManager.history
    
    val advancedDownloadManager: AdvancedDownloadManager
    private val musicExtractor = YouTubeMusicExtractor.getInstance()
    private val settingsDataStore = com.israrxy.raazi.data.local.SettingsDataStore(app)
    val isYouTubeLoggedIn: StateFlow<Boolean> = settingsDataStore.innerTubeCookie
        .map { cookie -> cookie?.contains("SAPISID") == true }
        .stateIn(viewModelScope, SharingStarted.Lazily, false)
    val youTubeAccountName: StateFlow<String?> = settingsDataStore.accountName
        .stateIn(viewModelScope, SharingStarted.Lazily, null)
    val youTubeAccountEmail: StateFlow<String?> = settingsDataStore.accountEmail
        .stateIn(viewModelScope, SharingStarted.Lazily, null)
    val useLoginForBrowse: StateFlow<Boolean> = settingsDataStore.useLoginForBrowse
        .stateIn(viewModelScope, SharingStarted.Lazily, true)
    val blurPlayerBackground: StateFlow<Boolean> = settingsDataStore.blurPlayerBackground
        .stateIn(viewModelScope, SharingStarted.Lazily, true)

    // Stored listener references for proper cleanup
    private var playbackStateListener: ((PlaybackState) -> Unit)? = null
    private var trackChangedListener: ((MusicItem) -> Unit)? = null
    private var visualizerListener: ((ByteArray) -> Unit)? = null

    init {
        advancedDownloadManager = AdvancedDownloadManager(app, musicDao, musicExtractor)
        // Inject DAO into StreamResolver for format info saving
        com.israrxy.raazi.player.StreamResolver.musicDao = musicDao
    }
    
    private var playbackService: MusicPlaybackService? = null
    private var isServiceBound = false

    private val _searchResults = MutableStateFlow<SearchResult?>(null)
    val searchResults: StateFlow<SearchResult?> = _searchResults.asStateFlow()

    private val _searchQueryForSuggestions = MutableStateFlow("")
    private val _searchSuggestions = MutableStateFlow<List<String>>(emptyList())
    val searchSuggestions: StateFlow<List<String>> = _searchQueryForSuggestions
        .debounce(300)
        .filter { it.length >= 2 }
        .flatMapLatest { query ->
            kotlinx.coroutines.flow.flow {
                try {
                    emit(repository.getSearchSuggestions(query))
                } catch (e: Exception) {
                    emit(emptyList())
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun fetchSearchSuggestions(query: String) {
        _searchQueryForSuggestions.value = query
        if (query.length < 2) {
            _searchSuggestions.value = emptyList()
        }
    }
    
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

    val savedCollections: StateFlow<List<SavedCollectionItem>> = repository.savedCollections
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val userPlaylistsWithTracks: StateFlow<List<com.israrxy.raazi.data.db.PlaylistWithTracks>> = repository.userPlaylistsWithTracks
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val savedCollectionIds: StateFlow<Set<String>> = savedCollections
        .map { collections -> collections.mapTo(mutableSetOf()) { it.id } }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptySet())

    private val _playbackState = MutableStateFlow(PlaybackState())

    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _homeFeedState = MutableStateFlow(HomeFeedUiState(isLoading = true))
    val homeFeedState: StateFlow<HomeFeedUiState> = _homeFeedState.asStateFlow()
    private var dismissedHomeItemIds: Set<String> = emptySet()
    private var engagedHomeItemIds: List<String> = emptyList()

    // Separate loading state for search to avoid conflicts with home loading
    private val _isSearchLoading = MutableStateFlow(false)
    val isSearchLoading: StateFlow<Boolean> = _isSearchLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error
    
    private val _downloadResult = MutableStateFlow<String?>(null)
    val downloadResult: StateFlow<String?> = _downloadResult.asStateFlow()
    private val _isSyncingYouTubeLibrary = MutableStateFlow(false)
    val isSyncingYouTubeLibrary: StateFlow<Boolean> = _isSyncingYouTubeLibrary.asStateFlow()
    private val _youTubeSyncStatus = MutableStateFlow<String?>(null)
    val youTubeSyncStatus: StateFlow<String?> = _youTubeSyncStatus.asStateFlow()

    // Download state from DB (reactive)
    val dbActiveDownloads: StateFlow<List<DownloadEntity>> = advancedDownloadManager.activeDownloads
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val dbCompletedDownloads: StateFlow<List<DownloadEntity>> = advancedDownloadManager.completedDownloads
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val dbFailedDownloads: StateFlow<List<DownloadEntity>> = advancedDownloadManager.failedDownloads
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val dbAllDownloads: StateFlow<List<DownloadEntity>> = advancedDownloadManager.allDownloads
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val activeDownloadCount: StateFlow<Int> = advancedDownloadManager.activeDownloadCount
        .stateIn(viewModelScope, SharingStarted.Lazily, 0)

    private val _likedSongsCount = MutableStateFlow(0)
    val likedSongsCount: StateFlow<Int> = _likedSongsCount.asStateFlow()

    // Stream Quality
    val streamQuality: StateFlow<String?> = com.israrxy.raazi.player.StreamResolver.currentStreamQuality

    // Current track format info (codec, bitrate, etc.)
    private val _currentTrackId = MutableStateFlow<String?>(null)
    val currentFormat: StateFlow<com.israrxy.raazi.data.db.FormatEntity?> = _currentTrackId
        .flatMapLatest { trackId ->
            if (trackId != null) musicDao.getFormatFlow(trackId)
            else kotlinx.coroutines.flow.flowOf(null)
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, null)

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val currentTrackRelated: StateFlow<List<MusicItem>> = playbackState
        .map { it.currentTrack?.id }
        .flatMapLatest { trackId ->
            if (trackId != null) {
                repository.getRelatedTracks(trackId)
            } else {
                kotlinx.coroutines.flow.flowOf(emptyList())
            }
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _currentArtistSongs = MutableStateFlow<List<MusicItem>>(emptyList())
    val currentArtistSongs: StateFlow<List<MusicItem>> = _currentArtistSongs.asStateFlow()

    data class EqualizerState(
        val bands: Short = 0,
        val minLevel: Short = 0,
        val maxLevel: Short = 0,
        val currentLevels: List<Short> = emptyList(),
        val centerFreqs: List<Int> = emptyList(),
        val presets: List<String> = emptyList(),
        val currentPreset: String = "Custom",
        // Advanced Effects
        val bassBoostStrength: Short = 0,
        val virtualizerStrength: Short = 0,
        val reverbPreset: Int = 0,
        val bassBoostSupported: Boolean = false,
        val virtualizerSupported: Boolean = false,
        val reverbSupported: Boolean = false
    )

    data class CustomPreset(
        val name: String,
        val levels: List<Short>,
        val bassBoost: Short = 0,
        val virtualizer: Short = 0,
        val reverb: Int = 0
    )

    private val _equalizerState = MutableStateFlow(EqualizerState())
    val equalizerState: StateFlow<EqualizerState> = _equalizerState.asStateFlow()
    
    private val _customPresets = MutableStateFlow<List<CustomPreset>>(emptyList())
    val customPresets: StateFlow<List<CustomPreset>> = _customPresets.asStateFlow()
    
    // Visualizer data for spectrum visualization
    private val _visualizerData = MutableStateFlow<ByteArray?>(null)
    val visualizerData: StateFlow<ByteArray?> = _visualizerData.asStateFlow()

    // Genre-based presets with optimized frequency curves
    private val genrePresets = mapOf(
        "Rock" to listOf(300, 500, 800, 1200, 2000, 3000, 4000, 6000, 8000, 12000),
        "Pop" to listOf(200, 400, 600, 1000, 1500, 2500, 3500, 5000, 7000, 10000),
        "Jazz" to listOf(100, 300, 500, 800, 1200, 2000, 3000, 4500, 6000, 8000),
        "Classical" to listOf(0, 0, 100, 200, 300, 400, 500, 600, 700, 800),
        "Electronic" to listOf(500, 600, 700, 800, 1000, 1500, 2500, 4000, 6000, 8000),
        "Hip-Hop" to listOf(600, 700, 600, 300, 200, 300, 400, 500, 600, 700),
        "Acoustic" to listOf(200, 300, 400, 500, 600, 700, 800, 900, 1000, 1100),
        "Blues" to listOf(300, 400, 500, 600, 700, 800, 900, 1000, 1100, 1200),
        "Metal" to listOf(500, 600, 800, 1000, 1200, 2000, 3000, 4000, 6000, 8000),
        "Podcast" to listOf(-300, -200, -100, 0, 100, 200, 300, 400, 500, 600)
    )

    private val _lyrics = MutableStateFlow<com.israrxy.raazi.data.remote.Lyrics?>(null)
    val lyrics: StateFlow<com.israrxy.raazi.data.remote.Lyrics?> = _lyrics.asStateFlow()
    private val _isLyricsLoading = MutableStateFlow(false)
    val isLyricsLoading: StateFlow<Boolean> = _isLyricsLoading.asStateFlow()
    private val _lyricsScriptFilter = MutableStateFlow(LyricsScriptFilter.ALL)
    val lyricsScriptFilter: StateFlow<LyricsScriptFilter> = _lyricsScriptFilter.asStateFlow()
    private val _lyricsSearchResults = MutableStateFlow<List<LyricsSearchResult>>(emptyList())
    val lyricsSearchResults: StateFlow<List<LyricsSearchResult>> = _lyricsSearchResults.asStateFlow()
    private val _isLyricsSearchLoading = MutableStateFlow(false)
    val isLyricsSearchLoading: StateFlow<Boolean> = _isLyricsSearchLoading.asStateFlow()
    private var lyricsFetchJob: Job? = null
    private var lastLyricsTrack: MusicItem? = null

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            try {
                val binder = service as MusicPlaybackService.MusicBinder
                playbackService = binder.getService()
                isServiceBound = true
                
                // Create and store listener reference for proper cleanup
                val stateListener: (PlaybackState) -> Unit = { state ->
                    val oldState = _playbackState.value
                    _playbackState.value = state
                    
                    // Fetch lyrics if track changed
                    if (state.currentTrack?.id != oldState.currentTrack?.id) {
                        state.currentTrack?.let { track ->
                            fetchLyrics(track)
                        }
                    }
                }
                playbackStateListener = stateListener
                playbackService?.addPlaybackStateListener(stateListener)
                
                // Create and store track changed listener
                val trackListener: (MusicItem) -> Unit = { track ->
                    addToHistoryInternal(track)
                    viewModelScope.launch(Dispatchers.IO) {
                        try {
                            repository.fetchAndSaveRelated(track)
                        } catch (e: Exception) {
                            Log.w("MusicVM", "Failed to fetch related for ${track.title}", e)
                        }

                        // Fetch artist songs
                        try {
                            val artistQuery = track.artist.split(",").first().trim()
                            if (artistQuery.isNotBlank() && artistQuery != "Unknown Artist") {
                                val result = com.zionhuang.innertube.YouTube.search(
                                    artistQuery,
                                    filter = com.zionhuang.innertube.YouTube.SearchFilter.FILTER_SONG
                                ).getOrNull()
                                val songs = result?.items?.mapNotNull { item ->
                                    when (item) {
                                        is com.zionhuang.innertube.models.SongItem -> MusicItem(
                                            id = item.id ?: return@mapNotNull null,
                                            title = item.title ?: "Unknown",
                                            artist = item.artists?.joinToString(", ") { it.name ?: "" } ?: "Unknown Artist",
                                            duration = (item.duration?.toLong() ?: 0) * 1000L,
                                            thumbnailUrl = item.thumbnail ?: "",
                                            audioUrl = "",
                                            videoUrl = item.id ?: return@mapNotNull null,
                                            isLive = false,
                                            contentType = MusicContentType.SONG,
                                            setVideoId = item.setVideoId
                                        )
                                        else -> null
                                    }
                                }?.take(5) ?: emptyList()
                                _currentArtistSongs.value = songs
                            } else {
                                _currentArtistSongs.value = emptyList()
                            }
                        } catch (e: Exception) {
                            Log.w("MusicVM", "Failed to fetch artist songs for ${track.artist}", e)
                            _currentArtistSongs.value = emptyList()
                        }
                    }
                }
                trackChangedListener = trackListener
                playbackService?.addTrackChangedListener(trackListener)
                
                // Update initial state
                _playbackState.value = playbackService?.getPlaybackState() ?: PlaybackState()
                _playbackState.value.currentTrack?.let { initialTrack ->
                    fetchLyrics(initialTrack)
                    viewModelScope.launch(Dispatchers.IO) {
                        try {
                            repository.fetchAndSaveRelated(initialTrack)
                        } catch (_: Exception) {}
                        try {
                            val artistQuery = initialTrack.artist.split(",").first().trim()
                            if (artistQuery.isNotBlank() && artistQuery != "Unknown Artist") {
                                val result = com.zionhuang.innertube.YouTube.search(
                                    artistQuery,
                                    filter = com.zionhuang.innertube.YouTube.SearchFilter.FILTER_SONG
                                ).getOrNull()
                                val songs = result?.items?.mapNotNull { item ->
                                    when (item) {
                                        is com.zionhuang.innertube.models.SongItem -> MusicItem(
                                            id = item.id ?: return@mapNotNull null,
                                            title = item.title ?: "Unknown",
                                            artist = item.artists?.joinToString(", ") { it.name ?: "" } ?: "Unknown Artist",
                                            duration = (item.duration?.toLong() ?: 0) * 1000L,
                                            thumbnailUrl = item.thumbnail ?: "",
                                            audioUrl = "",
                                            videoUrl = item.id ?: return@mapNotNull null,
                                            isLive = false,
                                            contentType = MusicContentType.SONG,
                                            setVideoId = item.setVideoId
                                        )
                                        else -> null
                                    }
                                }?.take(5) ?: emptyList()
                                _currentArtistSongs.value = songs
                            }
                        } catch (_: Exception) {}
                    }
                }
                
                // Load Equalizer State
                loadEqualizerState()
                loadCustomPresets()
                
                // Create and store visualizer listener
                playbackService?.let { musicService ->
                    if (musicService.isVisualizerSupported()) {
                        val vizListener: (ByteArray) -> Unit = { data ->
                            _visualizerData.value = data
                        }
                        visualizerListener = vizListener
                        musicService.addVisualizerListener(vizListener)
                    }
                }
            } catch (e: Exception) {
                Log.e("MusicVM", "Error in onServiceConnected", e)
            }
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            isServiceBound = false
            // Don't try to remove listeners here - service is already gone
            playbackService = null
        }
    }
    
    fun loadEqualizerState() {
        playbackService?.let { service ->
            try {
                val bands = service.getEqualizerBands()
                if (bands > 0) {
                    val range = service.getBandLevelRange()
                    val systemPresets = service.getPresetNames()
                    
                    // Add genre presets to the list
                    val allPresets = systemPresets + genrePresets.keys
                    
                    viewModelScope.launch {
                        val savedLevelsStr = settingsDataStore.equalizerLevels.first()
                        val savedPreset = settingsDataStore.equalizerPreset.first() ?: "Custom"
                        val savedBassBoost = settingsDataStore.bassBoostStrength.first()
                        val savedVirtualizer = settingsDataStore.virtualizerStrength.first()
                        val savedReverb = settingsDataStore.reverbPreset.first()
                        
                        val levels = mutableListOf<Short>()
                        val freqs = mutableListOf<Int>()
                        
                        if (savedLevelsStr != null) {
                            try {
                                val savedLevels = savedLevelsStr.split(",").map { it.toShort() }
                                for (i in 0 until bands) {
                                    val level = savedLevels.getOrElse(i) { service.getBandLevel(i.toShort()) }
                                    service.setBandLevel(i.toShort(), level)
                                    levels.add(level)
                                    freqs.add(service.getCenterFreq(i.toShort()))
                                }
                            } catch (e: Exception) {
                                // Fallback to current
                                for (i in 0 until bands) {
                                    levels.add(service.getBandLevel(i.toShort()))
                                    freqs.add(service.getCenterFreq(i.toShort()))
                                }
                            }
                        } else {
                            for (i in 0 until bands) {
                                levels.add(service.getBandLevel(i.toShort()))
                                freqs.add(service.getCenterFreq(i.toShort()))
                            }
                        }
                        
                        // Load saved effect settings
                        savedBassBoost?.let { service.setBassBoostStrength(it) }
                        savedVirtualizer?.let { service.setVirtualizerStrength(it) }
                        savedReverb?.let { service.setReverbPreset(it) }
                        
                        _equalizerState.value = EqualizerState(
                            bands = bands,
                            minLevel = range[0],
                            maxLevel = range[1],
                            currentLevels = levels,
                            centerFreqs = freqs,
                            presets = allPresets,
                            currentPreset = savedPreset,
                            bassBoostStrength = savedBassBoost ?: 0,
                            virtualizerStrength = savedVirtualizer ?: 0,
                            reverbPreset = savedReverb ?: 0,
                            bassBoostSupported = service.isBassBoostSupported(),
                            virtualizerSupported = service.isVirtualizerSupported(),
                            reverbSupported = service.isReverbSupported()
                        )
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("MusicVM", "Error loading EQ", e)
            }
        }
    }

    fun setBandLevel(band: Short, level: Short) {
        playbackService?.setBandLevel(band, level)
        // Update local state optimistically
        val current = _equalizerState.value
        val newLevels = current.currentLevels.toMutableList()
        if (band < newLevels.size) {
            newLevels[band.toInt()] = level
            _equalizerState.value = current.copy(currentLevels = newLevels, currentPreset = "Custom")
        }
    }

    fun usePreset(presetName: String) {
        playbackService?.let { service ->
            val current = _equalizerState.value
            
            // Check if it's a genre preset
            if (genrePresets.containsKey(presetName)) {
                val genreLevels = genrePresets[presetName]!!
                val adjustedLevels = mutableListOf<Short>()
                
                val range = service.getBandLevelRange()
                val minLevel = range[0]
                val maxLevel = range[1]
                
                for (i in 0 until current.bands) {
                    val freqValue = if (i < genreLevels.size) {
                        genreLevels[i]
                    } else {
                        0
                    }
                    // Convert frequency value to band level (normalized to device's min/max range)
                    val level = ((freqValue.toFloat() / 12000f) * (maxLevel - minLevel) + minLevel).toInt().toShort()
                        .coerceIn(minLevel, maxLevel)
                    service.setBandLevel(i.toShort(), level)
                    adjustedLevels.add(level)
                }
                
                _equalizerState.value = _equalizerState.value.copy(
                    currentLevels = adjustedLevels,
                    currentPreset = presetName
                )
            } else {
                // System preset
                val systemPresets = service.getPresetNames()
                val index = systemPresets.indexOf(presetName)
                if (index != -1) {
                    service.usePreset(index.toShort())
                    // Reload levels
                    val bands = service.getEqualizerBands()
                    val levels = mutableListOf<Short>()
                    for (i in 0 until bands) {
                        levels.add(service.getBandLevel(i.toShort()))
                    }
                    _equalizerState.value = _equalizerState.value.copy(
                        currentLevels = levels,
                        currentPreset = presetName
                    )
                }
            }
        }
    }

    fun saveEqualizerSettings() {
        val current = _equalizerState.value
        val levelsStr = current.currentLevels.joinToString(",")
        viewModelScope.launch {
            settingsDataStore.setEqualizerPreset(current.currentPreset)
            settingsDataStore.setEqualizerLevels(levelsStr)
            settingsDataStore.setBassBoostStrength(current.bassBoostStrength)
            settingsDataStore.setVirtualizerStrength(current.virtualizerStrength)
            settingsDataStore.setReverbPreset(current.reverbPreset)
        }
    }

    // Advanced Effects Methods
    fun setBassBoostStrength(strength: Short) {
        playbackService?.setBassBoostStrength(strength)
        _equalizerState.value = _equalizerState.value.copy(
            bassBoostStrength = strength,
            currentPreset = "Custom"
        )
    }

    fun setVirtualizerStrength(strength: Short) {
        playbackService?.setVirtualizerStrength(strength)
        _equalizerState.value = _equalizerState.value.copy(
            virtualizerStrength = strength,
            currentPreset = "Custom"
        )
    }

    fun setReverbPreset(preset: Int) {
        playbackService?.setReverbPreset(preset)
        _equalizerState.value = _equalizerState.value.copy(
            reverbPreset = preset,
            currentPreset = "Custom"
        )
    }

    // Custom Preset Methods
    fun saveCustomPreset(name: String) {
        val current = _equalizerState.value
        val newPreset = CustomPreset(
            name = name,
            levels = current.currentLevels,
            bassBoost = current.bassBoostStrength,
            virtualizer = current.virtualizerStrength,
            reverb = current.reverbPreset
        )
        
        val updatedPresets = _customPresets.value.toMutableList()
        updatedPresets.add(newPreset)
        _customPresets.value = updatedPresets
        
        viewModelScope.launch {
            // Convert to JSON and save
            val serializablePresets = updatedPresets.map { preset ->
                Json {
                    ignoreUnknownKeys = true
                }.encodeToString(
                    SettingsDataStore.SerializableCustomPreset(
                        name = preset.name,
                        levels = preset.levels.joinToString(","),
                        bassBoost = preset.bassBoost,
                        virtualizer = preset.virtualizer,
                        reverb = preset.reverb
                    )
                )
            }
            settingsDataStore.updateCustomPresets("[${serializablePresets.joinToString(",")}]")
        }
    }

    fun loadCustomPreset(preset: CustomPreset) {
        playbackService?.let { service ->
            // Apply equalizer bands
            preset.levels.forEachIndexed { index, level ->
                if (index < _equalizerState.value.bands) {
                    service.setBandLevel(index.toShort(), level)
                }
            }
            
            // Apply effects
            service.setBassBoostStrength(preset.bassBoost)
            service.setVirtualizerStrength(preset.virtualizer)
            service.setReverbPreset(preset.reverb)
            
            // Update state
            _equalizerState.value = _equalizerState.value.copy(
                currentLevels = preset.levels,
                bassBoostStrength = preset.bassBoost,
                virtualizerStrength = preset.virtualizer,
                reverbPreset = preset.reverb,
                currentPreset = preset.name
            )
        }
    }

    fun deleteCustomPreset(preset: CustomPreset) {
        val updatedPresets = _customPresets.value.filter { it.name != preset.name }
        _customPresets.value = updatedPresets
        
        viewModelScope.launch {
            settingsDataStore.deleteCustomPreset(preset.name)
        }
    }

    fun loadCustomPresets() {
        viewModelScope.launch {
            val presetsStr = settingsDataStore.customPresets.first()
            if (!presetsStr.isNullOrEmpty()) {
                try {
                    val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
                    val serializablePresets = json.decodeFromString<List<SettingsDataStore.SerializableCustomPreset>>(presetsStr)
                    val customPresets = serializablePresets.map { sp ->
                        CustomPreset(
                            name = sp.name,
                            levels = sp.levels.split(",").map { it.trim().toShort() },
                            bassBoost = sp.bassBoost,
                            virtualizer = sp.virtualizer,
                            reverb = sp.reverb
                        )
                    }
                    _customPresets.value = customPresets
                } catch (e: Exception) {
                    android.util.Log.e("MusicVM", "Error loading custom presets", e)
                }
            }
        }
    }

    // Export/Import Methods
    fun exportEqualizerSettings(): String {
        val current = _equalizerState.value
        val exportData = mapOf(
            "preset" to current.currentPreset,
            "levels" to current.currentLevels.joinToString(","),
            "bassBoost" to current.bassBoostStrength,
            "virtualizer" to current.virtualizerStrength,
            "reverb" to current.reverbPreset,
            "customPresets" to _customPresets.value.map { 
                mapOf(
                    "name" to it.name,
                    "levels" to it.levels.joinToString(","),
                    "bassBoost" to it.bassBoost,
                    "virtualizer" to it.virtualizer,
                    "reverb" to it.reverb
                )
            }
        )
        return android.util.Base64.encodeToString(
            org.json.JSONObject(exportData).toString().toByteArray(),
            android.util.Base64.NO_WRAP
        )
    }

    // Visualizer Management Methods
    fun isVisualizerSupported(): Boolean {
        return playbackService?.isVisualizerSupported() ?: false
    }

    fun enableVisualizer(enable: Boolean) {
        playbackService?.enableVisualizer(enable)
    }

    init {
        bindPlaybackService()
        loadHomeContent()
        observeDownloadEvents()
        syncDownloadSettings()
        // Observe flows continuously for real-time updates (separate from loading)
        observeHistoryUpdates()
    }

    private fun observeHistoryUpdates() {
        viewModelScope.launch {
            try {
                repository.playbackHistory.collect { history ->
                    _keepListening.value = history.take(30)
                }
            } catch (e: Exception) {
                Log.w("MusicVM", "History observation stopped", e)
            }
        }
        viewModelScope.launch {
            try {
                repository.getForgottenFavorites().collect { forgotten ->
                    _forgottenFavorites.value = forgotten
                }
            } catch (e: Exception) {
                Log.w("MusicVM", "Forgotten favorites observation stopped", e)
            }
        }
    }

    private fun observeDownloadEvents() {
        viewModelScope.launch {
            advancedDownloadManager.downloadEvents.collect { event ->
                when (event) {
                    is AdvancedDownloadManager.DownloadEvent.Started ->
                        _downloadResult.value = "Downloading ${event.title}"
                    is AdvancedDownloadManager.DownloadEvent.Completed ->
                        _downloadResult.value = "Downloaded ${event.title}"
                    is AdvancedDownloadManager.DownloadEvent.Failed ->
                        _error.value = "Download failed: ${event.error}"
                    is AdvancedDownloadManager.DownloadEvent.Cancelled ->
                        _downloadResult.value = "Cancelled ${event.title}"
                    is AdvancedDownloadManager.DownloadEvent.Queued ->
                        _downloadResult.value = "Queued ${event.title}"
                }
            }
        }
    }

    private fun syncDownloadSettings() {
        viewModelScope.launch {
            settingsDataStore.downloadWifiOnly.collect { wifiOnly ->
                advancedDownloadManager.wifiOnly = wifiOnly
            }
        }
        viewModelScope.launch {
            settingsDataStore.maxConcurrentDownloads.collect { max ->
                advancedDownloadManager.maxConcurrentDownloads = max.toIntOrNull() ?: 2
            }
        }
    }

    private fun bindPlaybackService() {
        try {
            val intent = Intent(app, MusicPlaybackService::class.java)
            app.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        } catch (e: Exception) {
            Log.e("MusicVM", "Failed to bind playback service", e)
        }
    }
    
    fun loadHomeContent() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            _homeFeedState.value = _homeFeedState.value.copy(isLoading = true, errorMessage = null)
            try {
                loadHomeInteractionSignals()
                // Load local database content — use first() for one-shot loads
                launch {
                    try {
                        val quickPicks = repository.quickPicks.first().shuffled().take(50)
                        _quickPicks.value = quickPicks
                        rebuildHomeFeedState()
                    } catch (e: Exception) {
                        Log.e("MusicVM", "Error loading quick picks", e)
                    }
                }
                
                launch {
                    try {
                        // One-shot load for initial content
                        val history = repository.playbackHistory.first().take(30)
                        _keepListening.value = history
                        rebuildHomeFeedState()
                    } catch (e: Exception) {
                        Log.e("MusicVM", "Error loading keep listening", e)
                    }
                }
                
                launch {
                    try {
                        val forgotten = repository.getForgottenFavorites().first()
                        _forgottenFavorites.value = forgotten
                        rebuildHomeFeedState()
                    } catch (e: Exception) {
                        Log.e("MusicVM", "Error loading forgotten favorites", e)
                    }
                }
                
                // Similar Recommendations
                launch {
                    try {
                        loadSimilarRecommendations()
                        rebuildHomeFeedState()
                    } catch (e: Exception) {
                        Log.e("MusicVM", "Error loading similar recommendations", e)
                    }
                }
                
                // Explore Page
                launch {
                    try {
                        com.zionhuang.innertube.YouTube.explore().onSuccess { page ->
                            _explorePage.value = page
                            rebuildHomeFeedState()
                        }.onFailure {
                            Log.e("MusicVM", "Explore page failed", it)
                        }
                    } catch (e: Exception) {
                        Log.e("MusicVM", "Error loading explore page", e)
                    }
                }
                
                // Load YouTube Music sections
                try {
                    com.zionhuang.innertube.YouTube.home().onSuccess { page ->
                        _homePage.value = page
                        rebuildHomeFeedState()
                    }.onFailure {
                        Log.e("MusicVM", "InnerTube home failed", it)
                        _error.value = "Failed to load home feed"
                    }
                } catch (e: Exception) {
                    Log.e("MusicVM", "Error loading InnerTube home", e)
                }
                
            } catch (e: Exception) {
                Log.e("MusicVM", "Error loading home", e)
                _error.value = "Failed to load content: ${e.message}"
            } finally {
                _isLoading.value = false
                rebuildHomeFeedState()
            }
        }
    }
    
    fun refreshHomeContent() {
        // Refresh just the local DB content without reloading YouTube data
        viewModelScope.launch {
            rebuildHomeFeedState(isRefreshing = true)
            try {
                val quickPicks = repository.quickPicks.first().shuffled().take(50)
                _quickPicks.value = quickPicks
                android.util.Log.d("MusicVM", "Refreshed quick picks: ${quickPicks.size} items")
                
                val history = repository.playbackHistory.first().take(30)
                _keepListening.value = history
                android.util.Log.d("MusicVM", "Refreshed keep listening: ${history.size} items")
                
                val forgotten = repository.getForgottenFavorites().first()
                _forgottenFavorites.value = forgotten
                android.util.Log.d("MusicVM", "Refreshed forgotten favorites: ${forgotten.size} items")
                loadHomeInteractionSignals()
                rebuildHomeFeedState(isRefreshing = false)
            } catch (e: Exception) {
                android.util.Log.e("MusicVM", "Error refreshing home content", e)
                rebuildHomeFeedState(isRefreshing = false)
            }
        }
    }

    private suspend fun loadHomeInteractionSignals() {
        dismissedHomeItemIds = repository.getDismissedHomeItemIds()
        engagedHomeItemIds = repository.getEngagedHomeItemIds().distinct()
    }

    private fun rankHomeMusicItems(items: List<MusicItem>): List<MusicItem> {
        val engagedOrder = engagedHomeItemIds.withIndex().associate { it.value to it.index }
        return items
            .filter { it.id !in dismissedHomeItemIds }
            .sortedWith(
                compareBy<MusicItem> { engagedOrder[it.id] ?: Int.MAX_VALUE }
                    .thenBy { it.title.lowercase() }
            )
    }

    private fun rebuildHomeFeedState(isRefreshing: Boolean = _homeFeedState.value.isRefreshing) {
        val sections = mutableListOf<HomeSection>()

        sections += HomeSection(
            id = "home_status",
            title = "Home",
            type = HomeSectionType.STATUS,
            sourceType = HomeInteractionEntity.SOURCE_LOCAL,
            actionLabel = "Refresh"
        )

        val keepListeningItems = rankHomeMusicItems(_keepListening.value).take(7)
        if (keepListeningItems.isNotEmpty()) {
            sections += HomeSection(
                id = "keep_listening",
                title = "Keep Listening",
                type = HomeSectionType.KEEP_LISTENING,
                sourceType = HomeInteractionEntity.SOURCE_LOCAL,
                actionLabel = "Refresh",
                items = keepListeningItems.map(HomeFeedItem::Music)
            )
        }

        val quickPickItems = rankHomeMusicItems(_quickPicks.value).take(11)
        if (quickPickItems.isNotEmpty()) {
            sections += HomeSection(
                id = "quick_picks",
                title = "Quick Picks",
                type = HomeSectionType.QUICK_PICKS,
                sourceType = HomeInteractionEntity.SOURCE_LOCAL,
                actionLabel = "Refresh",
                items = quickPickItems.map(HomeFeedItem::Music)
            )
        }

        val forgottenItems = rankHomeMusicItems(_forgottenFavorites.value).take(10)
        if (forgottenItems.isNotEmpty()) {
            sections += HomeSection(
                id = "forgotten_favorites",
                title = "Rediscover",
                type = HomeSectionType.FORGOTTEN_FAVORITES,
                sourceType = HomeInteractionEntity.SOURCE_LOCAL,
                items = forgottenItems.map(HomeFeedItem::Music)
            )
        }

        _similarRecommendations.value.forEachIndexed { index, recommendation ->
            val items = recommendation.items
                .mapNotNull { item ->
                    when (item) {
                        is RecommendationItem.FromMusicItem ->
                            item.musicItem.takeIf { it.id !in dismissedHomeItemIds }?.let(HomeFeedItem::Music)
                        is RecommendationItem.FromYTItem ->
                            item.ytItem.takeIf { homeYouTubeItemId(it) !in dismissedHomeItemIds }?.let(HomeFeedItem::YouTube)
                    }
                }
                .take(14)

            if (items.isNotEmpty()) {
                sections += HomeSection(
                    id = "recommendation_$index",
                    title = recommendation.title,
                    type = HomeSectionType.RECOMMENDATION,
                    sourceType = HomeInteractionEntity.SOURCE_YOUTUBE,
                    items = items
                )
            }
        }

        _explorePage.value?.newReleaseAlbums?.takeIf { it.isNotEmpty() }?.let { albums ->
            sections += HomeSection(
                id = "new_releases",
                title = "New Releases",
                type = HomeSectionType.NEW_RELEASES,
                sourceType = HomeInteractionEntity.SOURCE_EXPLORE,
                items = albums
                    .filter { homeYouTubeItemId(it) !in dismissedHomeItemIds }
                    .take(12)
                    .map(HomeFeedItem::YouTube)
            )
        }

        _explorePage.value?.moodAndGenres?.takeIf { it.isNotEmpty() }?.let { moods ->
            sections += HomeSection(
                id = "moods",
                title = "Moods & Genres",
                type = HomeSectionType.MOODS,
                sourceType = HomeInteractionEntity.SOURCE_EXPLORE,
                items = moods.take(12).map { HomeFeedItem.Mood(it.title) }
            )
        }

        val selectedMood = _selectedChip.value
        if (selectedMood != null) {
            sections += HomeSection(
                id = "mood_playlists",
                title = "$selectedMood Playlists",
                type = HomeSectionType.MOOD_PLAYLISTS,
                sourceType = HomeInteractionEntity.SOURCE_EXPLORE,
                isLoading = _filteredPlaylists.value == null,
                items = _filteredPlaylists.value.orEmpty()
                    .filter { it.id !in dismissedHomeItemIds }
                    .take(12)
                    .map(HomeFeedItem::PlaylistResult)
            )
        }

        _homePage.value?.sections.orEmpty().forEachIndexed { index, section ->
            val items = section.items
                .filter { homeYouTubeItemId(it) !in dismissedHomeItemIds }
                .take(14)
                .map(HomeFeedItem::YouTube)

            if (items.isNotEmpty()) {
                sections += HomeSection(
                    id = "youtube_$index",
                    title = section.title,
                    type = HomeSectionType.YOUTUBE_RAIL,
                    sourceType = HomeInteractionEntity.SOURCE_YOUTUBE,
                    items = items
                )
            }
        }

        _homeFeedState.value = HomeFeedUiState(
            isLoading = _isLoading.value && sections.size <= 1,
            isRefreshing = isRefreshing,
            sections = sections,
            errorMessage = _error.value
        )
    }

    private fun homeYouTubeItemId(item: com.zionhuang.innertube.models.YTItem): String {
        return when (item) {
            is com.zionhuang.innertube.models.SongItem -> item.id
            is com.zionhuang.innertube.models.AlbumItem -> item.id
            is com.zionhuang.innertube.models.ArtistItem -> item.id
            is com.zionhuang.innertube.models.PlaylistItem -> item.id
            else -> item.hashCode().toString()
        }
    }

    fun recordHomeInteraction(
        itemId: String,
        sectionId: String,
        action: String,
        sourceType: String
    ) {
        viewModelScope.launch {
            repository.recordHomeInteraction(itemId, sectionId, action, sourceType)
            loadHomeInteractionSignals()
            rebuildHomeFeedState()
        }
    }

    fun dismissHomeItem(itemId: String, sectionId: String, sourceType: String) {
        viewModelScope.launch {
            repository.recordHomeInteraction(
                itemId = itemId,
                sectionId = sectionId,
                action = HomeInteractionEntity.ACTION_DISMISS,
                sourceType = sourceType
            )
            loadHomeInteractionSignals()
            rebuildHomeFeedState()
        }
    }

    fun refreshHomeSection(sectionId: String) {
        viewModelScope.launch {
            repository.recordHomeInteraction(
                itemId = sectionId,
                sectionId = sectionId,
                action = HomeInteractionEntity.ACTION_REFRESH,
                sourceType = HomeInteractionEntity.SOURCE_LOCAL
            )
        }
        refreshHomeContent()
    }

    private suspend fun loadSimilarRecommendations() {
        val recommendations = mutableListOf<SimilarRecommendation>()
        
        try {
            val playbackList = repository.playbackHistory.first()
            
            if (playbackList.isEmpty()) {
                loadFallbackRecommendations(recommendations)
                _similarRecommendations.value = recommendations
                return
            }
            
            // Artist-based recommendations (top 5 artists for more variety)
            val topArtists = playbackList
                .groupBy { it.artist }
                .entries.sortedByDescending { it.value.size }
                .take(5)
            
            for ((artistName, songs) in topArtists) {
                try {
                    val searchResult = com.zionhuang.innertube.YouTube.search(
                        artistName, 
                        filter = com.zionhuang.innertube.YouTube.SearchFilter.FILTER_ARTIST
                    ).getOrNull()
                    val artistItem = searchResult?.items?.firstOrNull() as? com.zionhuang.innertube.models.ArtistItem
                    
                    if (artistItem != null) {
                        val artistPage = com.zionhuang.innertube.YouTube.artist(artistItem.id).getOrNull()
                        artistPage?.let { page ->
                            val items = page.sections.flatMap { it.items }.shuffled().take(15)
                                .map { RecommendationItem.FromYTItem(it) }
                            if (items.isNotEmpty() && recommendations.size < 3) {
                                recommendations.add(
                                    SimilarRecommendation(
                                        title = "More from $artistName",
                                        thumbnailUrl = songs.firstOrNull()?.thumbnailUrl,
                                        items = items
                                    )
                                )
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w("MusicVM", "Artist recommendation failed for $artistName: ${e.message}")
                }
            }
            
            // Song-based recommendations (top 5 songs, not just 2)
            val topSongs = playbackList.take(10).distinctBy { it.videoUrl }
            
            for (song in topSongs.take(5)) {
                try {
                    if (song.videoUrl.isNotEmpty()) {
                        val nextPage = com.zionhuang.innertube.YouTube.next(
                            com.zionhuang.innertube.models.WatchEndpoint(videoId = song.videoUrl)
                        ).getOrNull()
                        
                        val relatedEndpoint = nextPage?.relatedEndpoint
                        if (relatedEndpoint != null) {
                            val relatedPage = com.zionhuang.innertube.YouTube.related(relatedEndpoint).getOrNull()
                            relatedPage?.let { page ->
                                val items = (
                                    page.songs.shuffled().take(8).map { RecommendationItem.FromYTItem(it) } +
                                    page.albums.shuffled().take(4).map { RecommendationItem.FromYTItem(it) } +
                                    page.playlists.shuffled().take(3).map { RecommendationItem.FromYTItem(it) }
                                ).shuffled()
                                
                                if (items.isNotEmpty() && recommendations.size < 5) {
                                    recommendations.add(
                                        SimilarRecommendation(
                                            title = "Because you listened to ${song.title}",
                                            thumbnailUrl = song.thumbnailUrl,
                                            items = items
                                        )
                                    )
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w("MusicVM", "Song recommendation failed for ${song.title}: ${e.message}")
                }
            }
            
            // Add genre-based recommendations from favorite artists
            try {
                val allFavoriteTracks = repository.favoriteTracks.first()
                if (allFavoriteTracks.isNotEmpty()) {
                    val randomFavorite = allFavoriteTracks.random()
                    val searchResult = com.zionhuang.innertube.YouTube.search(
                        "${randomFavorite.title} ${randomFavorite.artist}",
                        filter = com.zionhuang.innertube.YouTube.SearchFilter.FILTER_SONG
                    ).getOrNull()
                    
                    searchResult?.items?.firstOrNull()?.let { item ->
                        if (item is com.zionhuang.innertube.models.SongItem) {
                            val nextPage = com.zionhuang.innertube.YouTube.next(
                                com.zionhuang.innertube.models.WatchEndpoint(videoId = item.id)
                            ).getOrNull()
                            
                            val relatedEndpoint = nextPage?.relatedEndpoint
                            if (relatedEndpoint != null) {
                                val relatedPage = com.zionhuang.innertube.YouTube.related(relatedEndpoint).getOrNull()
                                relatedPage?.let { page ->
                                    val recItems = page.songs.shuffled().take(12)
                                        .map { RecommendationItem.FromYTItem(it) }
                                    if (recItems.isNotEmpty() && recommendations.size < 6) {
                                        recommendations.add(
                                            SimilarRecommendation(
                                                title = "More like ${item.title}",
                                                thumbnailUrl = null,
                                                items = recItems
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w("MusicVM", "Genre recommendation failed: ${e.message}")
            }
            
            // If we still don't have enough recommendations, add trending
            if (recommendations.size < 2) {
                loadTrendingRecommendations(recommendations)
            }
            
            _similarRecommendations.value = recommendations.shuffled()
            
        } catch (e: Exception) {
            Log.e("MusicVM", "Error in loadSimilarRecommendations", e)
            // Load fallback on error
            val fallbackRecs = mutableListOf<SimilarRecommendation>()
            loadFallbackRecommendations(fallbackRecs)
            _similarRecommendations.value = fallbackRecs
        }
    }
    
    private suspend fun loadFallbackRecommendations(recommendations: MutableList<SimilarRecommendation>) {
        try {
            // Load trending as fallback
            val trending = repository.getTrending()
            if (trending.items.isNotEmpty()) {
                recommendations.add(
                    SimilarRecommendation(
                        title = "Trending Now",
                        thumbnailUrl = trending.items.firstOrNull()?.thumbnailUrl,
                        items = trending.items.take(15).map { RecommendationItem.FromMusicItem(it) }
                    )
                )
            }
            
            // Load charts
            val charts = repository.getAllCharts()
            charts.entries.firstOrNull()?.let { (chartName, items) ->
                if (items.isNotEmpty()) {
                    recommendations.add(
                        SimilarRecommendation(
                            title = chartName,
                            thumbnailUrl = items.firstOrNull()?.thumbnailUrl,
                            items = items.take(15).map { RecommendationItem.FromMusicItem(it) }
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("MusicVM", "Fallback recommendations failed", e)
        }
    }
    
    private suspend fun loadTrendingRecommendations(recommendations: MutableList<SimilarRecommendation>) {
        try {
            val trending = repository.getTrending()
            if (trending.items.isNotEmpty()) {
                recommendations.add(
                    SimilarRecommendation(
                        title = "Trending",
                        thumbnailUrl = trending.items.firstOrNull()?.thumbnailUrl,
                        items = trending.items.take(15).map { RecommendationItem.FromMusicItem(it) }
                    )
                )
            }
        } catch (e: Exception) {
            Log.w("MusicVM", "Trending recommendations failed", e)
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
                    rebuildHomeFeedState()
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
                    rebuildHomeFeedState()
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
            _isSearchLoading.value = true
            _error.value = null
            try {
                val result = repository.searchMusic(query)
                _searchResults.value = result
                searchHistoryManager.addQuery(query)
            } catch (e: Exception) {
                _error.value = "Search failed: ${e.message}"
            } finally {
                _isSearchLoading.value = false
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
    
    // Similar Recommendations — type-safe wrapper
    sealed class RecommendationItem {
        data class FromYTItem(val ytItem: com.zionhuang.innertube.models.YTItem) : RecommendationItem()
        data class FromMusicItem(val musicItem: MusicItem) : RecommendationItem()
    }

    data class SimilarRecommendation(
        val title: String,
        val thumbnailUrl: String?,
        val items: List<RecommendationItem>
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
            rebuildHomeFeedState()
        } else {
            // Select & Fetch
            _selectedChip.value = chip
            _filteredPlaylists.value = null
            rebuildHomeFeedState()
            viewModelScope.launch {
                _isLoading.value = true
                try {
                     _filteredPlaylists.value = repository.getMoodPlaylists(chip)
                     rebuildHomeFeedState()
                } catch (e: Exception) {
                    _error.value = "Failed to load $chip playlists"
                    rebuildHomeFeedState()
                } finally {
                    _isLoading.value = false
                }
            }
        }
    }

    val playbackHistory: StateFlow<List<MusicItem>> = repository.playbackHistory
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())


    
    fun playMusic(musicItem: MusicItem) {
        viewModelScope.launch {
            try {
                // Update current track ID for format info tracking
                _currentTrackId.value = musicItem.id

                // Add to history
                addToHistoryInternal(musicItem)

                // Trigger background fetch for recommendation engine
                launch(Dispatchers.IO) {
                    try {
                        repository.fetchAndSaveRelated(musicItem)
                    } catch (e: Exception) {
                        Log.w("MusicVM", "Failed to fetch related", e)
                    }
                }

                // Delegate completely to service which handles local files and Raazi resolution
                playbackService?.playMusic(musicItem)
            } catch (e: Exception) {
                Log.e("MusicVM", "Error playing music: ${musicItem.title}", e)
                _error.value = "Failed to play: ${e.message}"
            }
        }
    }
    
    private fun addToHistoryInternal(musicItem: MusicItem) {
        viewModelScope.launch {
            repository.addToHistory(musicItem)
        }
    }

    fun playFromSearch(index: Int) {
        val items = _searchResults.value?.items ?: return
        if (items.isNotEmpty()) {
            playPlaylist(items, index)
        }
    }
    
    fun playFromQuickPicks(index: Int) {
        val items = _quickPicks.value
        if (items.isNotEmpty()) {
            playPlaylist(items, index)
        }
    }

    fun playFromKeepListening(index: Int) {
        val items = _keepListening.value
        if (items.isNotEmpty()) {
            playPlaylist(items, index)
        }
    }

    fun playFromHistory(index: Int) {
        viewModelScope.launch {
            val items = playbackHistory.value
            if (items.isNotEmpty()) {
                playPlaylist(items, index)
            }
        }
    }

    fun playFromTrending(index: Int) {
        val items = _trendingResults.value?.items ?: return
        if (items.isNotEmpty()) {
            playPlaylist(items, index)
        }
    }

    fun toggleFavorite(musicItem: MusicItem) {
        viewModelScope.launch {
            val isFavorite = favoriteTracks.value.any { it.id == musicItem.id }
            if (isFavorite) {
                repository.removeFromFavorites(musicItem)
            } else {
                repository.addToFavorites(musicItem)
            }
        }
    }

    private fun fetchLyrics(track: MusicItem) {
        lastLyricsTrack = track
        lyricsFetchJob?.cancel()
        lyricsFetchJob = viewModelScope.launch {
            try {
                _isLyricsLoading.value = true
                _lyrics.value = null
                val result = repository.getLyrics(track, preferredScript = _lyricsScriptFilter.value)
                _lyrics.value = result
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isLyricsLoading.value = false
            }
        }
    }

    fun toggleSavedCollection(item: MusicItem) {
        viewModelScope.launch {
            repository.toggleSavedCollection(item)
        }
    }

    fun toggleSavedArtist(
        artistId: String,
        artistName: String,
        thumbnailUrl: String = ""
    ) {
        viewModelScope.launch {
            repository.toggleSavedArtist(artistId = artistId, artistName = artistName, thumbnailUrl = thumbnailUrl)
        }
    }

    fun toggleSavedPlaylist(playlist: Playlist) {
        viewModelScope.launch {
            repository.toggleSavedCollection(playlist)
        }
    }

    fun isSavedCollection(item: MusicItem): Boolean {
        val savedCollection = item.toSavedCollectionItemOrNull() ?: return false
        return savedCollection.id in savedCollectionIds.value
    }

    fun isSavedCollection(playlist: Playlist): Boolean {
        return playlist.toSavedCollectionItem().id in savedCollectionIds.value
    }

    fun retryLyricsFetch() {
        lastLyricsTrack?.let(::fetchLyrics)
    }

    fun searchLyricsOptions(
        titleOverride: String? = null,
        artistOverride: String? = null
    ) {
        val track = lastLyricsTrack ?: playbackState.value.currentTrack ?: return
        viewModelScope.launch {
            try {
                _isLyricsSearchLoading.value = true
                _lyricsSearchResults.value = repository.searchLyricsOptions(
                    item = track,
                    titleOverride = titleOverride ?: track.title,
                    artistOverride = artistOverride ?: track.artist,
                    preferredScript = _lyricsScriptFilter.value
                )
            } catch (e: Exception) {
                Log.e("MusicVM", "Lyrics search failed", e)
                _error.value = "Lyrics search failed: ${e.message}"
            } finally {
                _isLyricsSearchLoading.value = false
            }
        }
    }

    fun clearLyricsSearchResults() {
        _lyricsSearchResults.value = emptyList()
    }

    fun saveLyricsSelection(result: LyricsSearchResult) {
        val track = lastLyricsTrack ?: playbackState.value.currentTrack ?: return
        viewModelScope.launch {
            try {
                repository.saveLyricsForTrack(track, result.lyrics)
                _lyrics.value = result.lyrics.copy(source = "Saved ${result.lyrics.source}")
            } catch (e: Exception) {
                Log.e("MusicVM", "Saving lyrics failed", e)
                _error.value = "Couldn't save lyrics: ${e.message}"
            }
        }
    }

    fun clearSavedLyricsSelection() {
        val track = lastLyricsTrack ?: playbackState.value.currentTrack ?: return
        viewModelScope.launch {
            try {
                repository.clearSavedLyricsForTrack(track)
                fetchLyrics(track)
            } catch (e: Exception) {
                Log.e("MusicVM", "Clearing saved lyrics failed", e)
                _error.value = "Couldn't clear saved lyrics: ${e.message}"
            }
        }
    }

    fun setLyricsScriptFilter(filter: LyricsScriptFilter) {
        if (_lyricsScriptFilter.value == filter) return
        _lyricsScriptFilter.value = filter

        val track = lastLyricsTrack ?: playbackState.value.currentTrack ?: return
        if (_lyrics.value?.source?.startsWith("Saved") == true) {
            return
        }
        fetchLyrics(track)
    }

    fun playFromPlaylist(index: Int) {
        _currentPlaylist.value?.items?.getOrNull(index)?.let { musicItem ->
            playMusic(musicItem)
        }
    }

    fun pause() {
        try { playbackService?.pause() } catch (e: Exception) { Log.w("MusicVM", "pause failed", e) }
    }

    fun resume() {
        try { playbackService?.resume() } catch (e: Exception) { Log.w("MusicVM", "resume failed", e) }
    }

    fun stop() {
        try { playbackService?.stop() } catch (e: Exception) { Log.w("MusicVM", "stop failed", e) }
    }

    fun next() {
        try { playbackService?.next() } catch (e: Exception) { Log.w("MusicVM", "next failed", e) }
    }

    fun previous() {
        try { playbackService?.previous() } catch (e: Exception) { Log.w("MusicVM", "previous failed", e) }
    }

    fun seekTo(position: Long) {
        try { playbackService?.seekTo(position) } catch (e: Exception) { Log.w("MusicVM", "seekTo failed", e) }
    }
    
    fun toggleShuffle() {
        try { playbackService?.toggleShuffle() } catch (e: Exception) { Log.w("MusicVM", "toggleShuffle failed", e) }
    }
    
    fun toggleRepeat() {
        try { playbackService?.toggleRepeat() } catch (e: Exception) { Log.w("MusicVM", "toggleRepeat failed", e) }
    }

    fun addToQueue(items: List<MusicItem>) {
        try { playbackService?.addToQueue(items) } catch (e: Exception) { Log.w("MusicVM", "addToQueue failed", e) }
    }

    fun playNext(items: List<MusicItem>) {
        try { playbackService?.playNext(items) } catch (e: Exception) { Log.w("MusicVM", "playNext failed", e) }
    }
    


    fun clearError() {
        _error.value = null
    }

    fun clearSearchHistory() {
        searchHistoryManager.clearHistory()
    }

    fun clearYouTubeSyncStatus() {
        _youTubeSyncStatus.value = null
    }

    fun syncYouTubeLibrary() {
        if (_isSyncingYouTubeLibrary.value) return

        viewModelScope.launch {
            _isSyncingYouTubeLibrary.value = true
            try {
                val result = repository.syncYouTubeLibrary()
                _youTubeSyncStatus.value = "Synced ${result.likedSongs} liked songs and ${result.playlists} playlists."
            } catch (e: Exception) {
                android.util.Log.e("MusicVM", "Error syncing YouTube library", e)
                _error.value = "Failed to sync YouTube Music: ${e.message}"
            } finally {
                _isSyncingYouTubeLibrary.value = false
            }
        }
    }

    fun logoutFromYouTube() {
        viewModelScope.launch {
            try {
                YouTubeAccountSession.clear(settingsDataStore)
                repository.clearYouTubePlaylistsCache()
                _youTubeSyncStatus.value = "Signed out of YouTube Music."
            } catch (e: Exception) {
                android.util.Log.e("MusicVM", "Error signing out of YouTube", e)
                _error.value = "Failed to sign out: ${e.message}"
            }
        }
    }

    fun setUseLoginForBrowse(enabled: Boolean) {
        viewModelScope.launch {
            YouTubeAccountSession.setUseLoginForBrowse(settingsDataStore, enabled)
        }
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
                
                if (playlistId == "favorites") {
                    if (isYouTubeLoggedIn.value) {
                        try {
                            val likedSongsPlaylist = repository.getLikedSongsPlaylist()
                            _currentPlaylist.value = likedSongsPlaylist
                            android.util.Log.d("MusicVM", "Loaded remote liked songs playlist with ${likedSongsPlaylist.items.size} songs")
                        } catch (e: Exception) {
                            val favorites = favoriteTracks.value
                            _currentPlaylist.value = Playlist(
                                id = "favorites",
                                title = "Liked Songs",
                                description = "${favorites.size} songs",
                                thumbnailUrl = favorites.firstOrNull()?.thumbnailUrl ?: "",
                                items = favorites
                            )
                            android.util.Log.w("MusicVM", "Falling back to local liked songs playlist", e)
                        }
                    } else {
                        val favorites = favoriteTracks.value
                        val playlist = Playlist(
                            id = "favorites",
                            title = "Liked Songs",
                            description = "${favorites.size} songs",
                            thumbnailUrl = favorites.firstOrNull()?.thumbnailUrl ?: "",
                            items = favorites
                        )
                        _currentPlaylist.value = playlist
                        android.util.Log.d("MusicVM", "Loaded favorites playlist with ${favorites.size} songs")
                    }
                } else {
                    val playlist = repository.getPlaylist(playlistId)
                    _currentPlaylist.value = playlist
                    android.util.Log.d("MusicVM", "Loaded playlist with ${playlist.items.size} songs")
                }
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
        
        // Add currently playing track to history
        items.getOrNull(startIndex)?.let { currentTrack ->
            addToHistoryInternal(currentTrack)
            // Trigger background fetch for recommendations
            viewModelScope.launch(Dispatchers.IO) {
                repository.fetchAndSaveRelated(currentTrack)
            }
        }
        
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

    fun createPlaylist(name: String, syncedToYouTube: Boolean = false) {
        viewModelScope.launch {
            try {
                val playlist = repository.createPlaylist(name, syncedToYouTube = syncedToYouTube)
                _youTubeSyncStatus.value = if (syncedToYouTube) {
                    "Created synced playlist \"${playlist.title}\"."
                } else {
                    "Created playlist \"${playlist.title}\"."
                }
            } catch (e: Exception) {
                android.util.Log.e("MusicVM", "Error creating playlist", e)
                _error.value = "Failed to create playlist: ${e.message}"
            }
        }
    }

    fun addToPlaylist(playlistId: String, track: MusicItem) {
        viewModelScope.launch {
            try {
                repository.addToPlaylist(playlistId, track)
            } catch (e: Exception) {
                android.util.Log.e("MusicVM", "Error adding track to playlist", e)
                _error.value = e.message ?: "Failed to add song to playlist."
            }
        }
    }

    // Overload for AddToPlaylistDialog usage
    fun addToPlaylist(track: MusicItem, playlist: PlaylistEntity) {
        viewModelScope.launch {
            try {
                repository.addToPlaylist(track, playlist)
            } catch (e: Exception) {
                android.util.Log.e("MusicVM", "Error adding track to playlist", e)
                _error.value = e.message ?: "Failed to add song to playlist."
            }
        }
    }

    fun addToPlaylist(tracks: List<MusicItem>, playlist: PlaylistEntity) {
        viewModelScope.launch {
            try {
                repository.addToPlaylist(tracks, playlist)
            } catch (e: Exception) {
                android.util.Log.e("MusicVM", "Error adding tracks to playlist", e)
                _error.value = e.message ?: "Failed to add songs to playlist."
            }
        }
    }

    // --- Playlist Management ---

    fun deletePlaylist(playlistId: String) {
        viewModelScope.launch {
            try {
                repository.deletePlaylist(playlistId)
                _youTubeSyncStatus.value = "Playlist deleted."
            } catch (e: Exception) {
                android.util.Log.e("MusicVM", "Error deleting playlist", e)
                _error.value = e.message ?: "Failed to delete playlist."
            }
        }
    }

    fun syncLocalPlaylistToYouTube(playlistId: String) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                _error.value = null
                val remotePlaylistId = repository.syncLocalPlaylistToYouTube(playlistId)
                loadPlaylist(remotePlaylistId)
                _youTubeSyncStatus.value = "Playlist synced to YouTube Music successfully!"
            } catch (e: Exception) {
                android.util.Log.e("MusicVM", "Error syncing playlist to YouTube", e)
                _error.value = e.message ?: "Failed to sync playlist to YouTube."
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun renamePlaylist(playlistId: String, newName: String) {
        viewModelScope.launch {
            try {
                repository.renamePlaylist(playlistId, newName)
                if (_currentPlaylist.value?.id == playlistId) {
                    _currentPlaylist.value = _currentPlaylist.value?.copy(title = newName)
                }
                _youTubeSyncStatus.value = "Playlist renamed."
            } catch (e: Exception) {
                android.util.Log.e("MusicVM", "Error renaming playlist", e)
                _error.value = e.message ?: "Failed to rename playlist."
            }
        }
    }

    fun removeTrackFromPlaylist(playlistId: String, trackId: String, setVideoId: String? = null) {
        viewModelScope.launch {
            try {
                repository.removeTrackFromPlaylist(playlistId, trackId, setVideoId)
                if (_currentPlaylist.value?.id == playlistId) {
                    _currentPlaylist.value = _currentPlaylist.value?.copy(
                        items = _currentPlaylist.value?.items?.filter { it.id != trackId } ?: emptyList()
                    )
                }
            } catch (e: Exception) {
                android.util.Log.e("MusicVM", "Error removing track from playlist", e)
                _error.value = e.message ?: "Failed to remove song."
            }
        }
    }

    fun removeTracksFromPlaylist(playlistId: String, tracks: List<MusicItem>) {
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
                android.util.Log.e("MusicVM", "Error removing tracks from playlist", e)
                _error.value = e.message ?: "Failed to remove songs."
            }
        }
    }

    fun reorderPlaylistTracks(playlistId: String, trackIds: List<String>) {
        viewModelScope.launch {
            try {
                repository.reorderPlaylistTracks(playlistId, trackIds)
            } catch (e: Exception) {
                android.util.Log.e("MusicVM", "Error reordering playlist", e)
                _error.value = e.message ?: "Failed to reorder playlist."
            }
        }
    }

    // Legacy compatibility — maps DB downloads to old format for LibraryScreen
    private val _activeDownloads = MutableStateFlow<Map<Long, Pair<MusicItem, Int>>>(emptyMap())
    val activeDownloads: StateFlow<Map<Long, Pair<MusicItem, Int>>> = _activeDownloads.asStateFlow()

    fun downloadTrack(track: MusicItem) {
        advancedDownloadManager.downloadTrack(track)
    }

    fun downloadPlaylist(tracks: List<MusicItem>) {
        advancedDownloadManager.downloadPlaylist(tracks)
    }

    fun cancelDownload(trackId: String) {
        advancedDownloadManager.cancelDownload(trackId)
    }

    fun retryDownload(trackId: String) {
        advancedDownloadManager.retryDownload(trackId)
    }

    fun deleteDownloadedFile(trackId: String) {
        advancedDownloadManager.deleteDownloadedFile(trackId)
    }

    fun isTrackDownloaded(trackId: String): Boolean {
        return advancedDownloadManager.isTrackDownloaded(trackId)
    }

    fun getDownloadStorageUsed(): Long {
        return advancedDownloadManager.getDownloadStorageUsed()
    }

    fun getAvailableStorage(): Long {
        return advancedDownloadManager.getAvailableStorage()
    }

    fun deleteAllDownloads() {
        advancedDownloadManager.deleteAllDownloads()
    }

    fun getPlayer(): androidx.media3.exoplayer.ExoPlayer? {
        return playbackService?.getPlayer()
    }

    fun togglePlaybackMode(): Boolean {
        return playbackService?.togglePlaybackMode() ?: false
    }

    fun setPlaybackVideoQuality(quality: com.israrxy.raazi.model.PlaybackVideoQuality): Boolean {
        return playbackService?.setPlaybackVideoQuality(quality) ?: false
    }

    // --- Ringtone Support ---

    sealed class RingtoneState {
        object Idle : RingtoneState()
        data class Downloading(val progress: Int = 0) : RingtoneState()
        data class Ready(val filePath: String, val durationMs: Long) : RingtoneState()
        object Trimming : RingtoneState()
        object Setting : RingtoneState()
        data class Done(val message: String) : RingtoneState()
        data class Error(val message: String) : RingtoneState()
        object NeedsPermission : RingtoneState()
    }

    private val _ringtoneState = MutableStateFlow<RingtoneState>(RingtoneState.Idle)
    val ringtoneState: StateFlow<RingtoneState> = _ringtoneState.asStateFlow()

    private val _ringtoneTrack = MutableStateFlow<MusicItem?>(null)
    val ringtoneTrack: StateFlow<MusicItem?> = _ringtoneTrack.asStateFlow()

    private val _ringtoneType = MutableStateFlow(RingtoneType.RINGTONE)
    val ringtoneType: StateFlow<RingtoneType> = _ringtoneType.asStateFlow()

    fun setRingtoneType(type: RingtoneType) {
        _ringtoneType.value = type
    }

    fun checkWriteSettingsPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.System.canWrite(app)
        } else true
    }

    fun downloadForRingtone(track: MusicItem) {
        _ringtoneTrack.value = track
        _ringtoneState.value = RingtoneState.Downloading()
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val ringtoneHelper = com.israrxy.raazi.service.RingtoneHelper(app)
                val extractor = com.israrxy.raazi.service.YouTubeMusicExtractor.getInstance()
                val result = ringtoneHelper.downloadAudio(track, extractor) { progress ->
                    _ringtoneState.value = RingtoneState.Downloading(progress)
                }
                _ringtoneState.value = RingtoneState.Ready(result.first, result.second)
            } catch (e: Exception) {
                android.util.Log.e("MusicVM", "Ringtone download failed", e)
                _ringtoneState.value = RingtoneState.Error("Download failed: ${e.message}")
            }
        }
    }

    fun setAsRingtone(filePath: String, startMs: Long, endMs: Long) {
        _ringtoneState.value = RingtoneState.Trimming
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val ringtoneHelper = com.israrxy.raazi.service.RingtoneHelper(app)
                _ringtoneState.value = RingtoneState.Setting
                val trackTitle = _ringtoneTrack.value?.title ?: "Raazi Ringtone"
                val type = _ringtoneType.value
                ringtoneHelper.trimAndSetRingtone(filePath, startMs, endMs, trackTitle, type)
                val typeLabel = when (type) {
                    RingtoneType.RINGTONE -> "Ringtone"
                    RingtoneType.NOTIFICATION -> "Notification"
                    RingtoneType.ALARM -> "Alarm"
                }
                _ringtoneState.value = RingtoneState.Done("$typeLabel set successfully!")
            } catch (e: Exception) {
                android.util.Log.e("MusicVM", "Ringtone set failed", e)
                _ringtoneState.value = RingtoneState.Error("Failed to set ringtone: ${e.message}")
            }
        }
    }

    fun resetRingtoneState() {
        _ringtoneState.value = RingtoneState.Idle
        _ringtoneTrack.value = null
        _ringtoneType.value = RingtoneType.RINGTONE
    }

    // --- Spotify Import Support ---

    sealed class SpotifyImportState {
        object Idle : SpotifyImportState()
        object FetchingSpotify : SpotifyImportState()
        data class ResolvingTracks(val current: Int, val total: Int) : SpotifyImportState()
        object Reviewing : SpotifyImportState()
        object Importing : SpotifyImportState()
        data class Success(val playlistId: String) : SpotifyImportState()
        data class Error(val message: String) : SpotifyImportState()
    }

    data class ResolvedSpotifyTrack(
        val spotifyTrack: com.israrxy.raazi.service.SpotifyTrack,
        val resolvedItem: MusicItem?,
        val isResolving: Boolean = false,
        val isError: Boolean = false
    )

    private val _spotifyImportState = MutableStateFlow<SpotifyImportState>(SpotifyImportState.Idle)
    val spotifyImportState: StateFlow<SpotifyImportState> = _spotifyImportState.asStateFlow()

    private val _resolvedSpotifyTracks = MutableStateFlow<List<ResolvedSpotifyTrack>>(emptyList())
    val resolvedSpotifyTracks: StateFlow<List<ResolvedSpotifyTrack>> = _resolvedSpotifyTracks.asStateFlow()

    private val _spotifyPlaylistName = MutableStateFlow("")
    val spotifyPlaylistName: StateFlow<String> = _spotifyPlaylistName.asStateFlow()

    fun startSpotifyImport(playlistUrl: String) {
        _spotifyImportState.value = SpotifyImportState.FetchingSpotify
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val spotifyPlaylist = com.israrxy.raazi.service.SpotifyImportHelper.fetchPlaylist(playlistUrl)
                _spotifyPlaylistName.value = spotifyPlaylist.title

                val tracksToResolve = spotifyPlaylist.tracks.map { track ->
                    ResolvedSpotifyTrack(track, null, isResolving = true)
                }
                _resolvedSpotifyTracks.value = tracksToResolve
                _spotifyImportState.value = SpotifyImportState.ResolvingTracks(0, tracksToResolve.size)

                // Fetch Gemini configurations
                val useGemini = settingsDataStore.useGeminiImport.first()
                val geminiKey = settingsDataStore.geminiApiKey.first()

                val geminiQueries = if (useGemini && !geminiKey.isNullOrBlank()) {
                    try {
                        com.israrxy.raazi.service.GeminiResolverHelper.generateOptimizedQueries(geminiKey, spotifyPlaylist.tracks)
                    } catch (e: Exception) {
                        android.util.Log.e("MusicVM", "Gemini pre-resolution failed, falling back to legacy cleaner", e)
                        null
                    }
                } else {
                    null
                }

                val semaphore = kotlinx.coroutines.sync.Semaphore(5)
                val total = tracksToResolve.size
                var completedCount = 0

                kotlinx.coroutines.coroutineScope {
                    tracksToResolve.mapIndexed { index, resolvedTrack ->
                        launch {
                            semaphore.withPermit {
                                try {
                                    val geminiInfo = geminiQueries?.get(index)
                                    val cleanedTitle = com.israrxy.raazi.service.SpotifyImportHelper.cleanTrackTitleForSearch(resolvedTrack.spotifyTrack.title)
                                    
                                    val query = geminiInfo?.query ?: "$cleanedTitle ${resolvedTrack.spotifyTrack.artist}"
                                    val preference = geminiInfo?.preference ?: "SONG"

                                    val searchResult = musicExtractor.searchMusic(query, 0)
                                    
                                    val bestMatch = if (geminiInfo != null) {
                                        if (preference == "VIDEO") {
                                            searchResult.items.firstOrNull { 
                                                it.contentType == com.israrxy.raazi.model.MusicContentType.VIDEO 
                                            } ?: searchResult.items.firstOrNull {
                                                it.contentType == com.israrxy.raazi.model.MusicContentType.SONG
                                            } ?: searchResult.items.firstOrNull()
                                        } else {
                                            searchResult.items.firstOrNull { 
                                                it.contentType == com.israrxy.raazi.model.MusicContentType.SONG 
                                            } ?: searchResult.items.firstOrNull {
                                                it.contentType == com.israrxy.raazi.model.MusicContentType.VIDEO
                                            } ?: searchResult.items.firstOrNull()
                                        }
                                    } else {
                                        // Legacy resolver improvements
                                        val songs = searchResult.items.filter { it.contentType == com.israrxy.raazi.model.MusicContentType.SONG }
                                        val firstSong = songs.firstOrNull()
                                        if (firstSong != null && com.israrxy.raazi.service.SpotifyImportHelper.checkRelaxedMatch(cleanedTitle, firstSong.title)) {
                                            firstSong
                                        } else {
                                            // Fallback to video sections
                                            val videos = searchResult.items.filter { it.contentType == com.israrxy.raazi.model.MusicContentType.VIDEO }
                                            videos.firstOrNull() ?: firstSong ?: searchResult.items.firstOrNull()
                                        }
                                    }

                                    updateResolvedTrack(index) {
                                        it.copy(resolvedItem = bestMatch, isResolving = false)
                                    }
                                } catch (e: Exception) {
                                    android.util.Log.e("MusicVM", "Error resolving track at index $index", e)
                                    updateResolvedTrack(index) {
                                        it.copy(isResolving = false, isError = true)
                                    }
                                } finally {
                                    synchronized(this@MusicPlayerViewModel) {
                                        completedCount++
                                        _spotifyImportState.value = SpotifyImportState.ResolvingTracks(completedCount, total)
                                    }
                                }
                            }
                        }
                    }
                }

                _spotifyImportState.value = SpotifyImportState.Reviewing
            } catch (e: Exception) {
                android.util.Log.e("MusicVM", "Spotify import failed", e)
                _spotifyImportState.value = SpotifyImportState.Error(e.message ?: "Unknown error")
            }
        }
    }

    private fun updateResolvedTrack(index: Int, transform: (ResolvedSpotifyTrack) -> ResolvedSpotifyTrack) {
        val currentList = _resolvedSpotifyTracks.value.toMutableList()
        if (index in currentList.indices) {
            currentList[index] = transform(currentList[index])
            _resolvedSpotifyTracks.value = currentList
        }
    }

    fun replaceResolvedTrack(index: Int, newItem: MusicItem) {
        updateResolvedTrack(index) {
            it.copy(resolvedItem = newItem, isError = false, isResolving = false)
        }
    }

    fun finalizeSpotifyImport(playlistTitle: String, syncedToYouTube: Boolean) {
        _spotifyImportState.value = SpotifyImportState.Importing
        viewModelScope.launch {
            try {
                val playlistEntity = repository.createPlaylist(playlistTitle, syncedToYouTube = syncedToYouTube)
                val resolvedItems = _resolvedSpotifyTracks.value.mapNotNull { it.resolvedItem }
                
                if (resolvedItems.isNotEmpty()) {
                    repository.addToPlaylist(resolvedItems, playlistEntity)
                }

                _spotifyImportState.value = SpotifyImportState.Success(playlistEntity.id)
            } catch (e: Exception) {
                android.util.Log.e("MusicVM", "Error finalizing import", e)
                _spotifyImportState.value = SpotifyImportState.Error(e.message ?: "Failed to save playlist.")
            }
        }
    }

    fun resetSpotifyImportState() {
        _spotifyImportState.value = SpotifyImportState.Idle
        _resolvedSpotifyTracks.value = emptyList()
        _spotifyPlaylistName.value = ""
    }

    override fun onCleared() {
        super.onCleared()
        try {
            if (isServiceBound) {
                // Remove stored listener references (not new empty lambdas)
                playbackStateListener?.let { playbackService?.removePlaybackStateListener(it) }
                trackChangedListener?.let { playbackService?.removeTrackChangedListener(it) }
                visualizerListener?.let { playbackService?.removeVisualizerListener(it) }
                playbackStateListener = null
                trackChangedListener = null
                visualizerListener = null
                try {
                    app.unbindService(serviceConnection)
                } catch (e: IllegalArgumentException) {
                    Log.w("MusicVM", "Service not registered", e)
                }
                isServiceBound = false
            }
        } catch (e: Exception) {
            Log.e("MusicVM", "Error in onCleared", e)
        }
        try {
            advancedDownloadManager.destroy()
        } catch (e: Exception) {
            Log.e("MusicVM", "Error destroying download manager", e)
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
