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
import com.israrxy.raazi.service.AdvancedDownloadManager
import com.israrxy.raazi.data.db.DownloadEntity
import com.israrxy.raazi.service.YouTubeMusicExtractor
import com.israrxy.raazi.data.local.SettingsDataStore
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flatMapLatest
import android.util.Log

class MusicPlayerViewModel(
    private val app: Application,
    private val repository: MusicRepository,
    private val musicDao: MusicDao
) : AndroidViewModel(app) {
    
    private val searchHistoryManager = com.israrxy.raazi.data.local.SearchHistoryManager(app)
    val searchHistory: StateFlow<List<String>> = searchHistoryManager.history
    
    val advancedDownloadManager: AdvancedDownloadManager
    private val musicExtractor = YouTubeMusicExtractor()
    private val settingsDataStore = com.israrxy.raazi.data.local.SettingsDataStore(app)

    init {
        advancedDownloadManager = AdvancedDownloadManager(app, musicDao, musicExtractor)
        // Inject DAO into StreamResolver for format info saving
        com.israrxy.raazi.player.StreamResolver.musicDao = musicDao
    }
    
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
            
            // Load Equalizer State
            loadEqualizerState()
            loadCustomPresets()
            
            playbackService?.let { musicService ->
                if (musicService.isVisualizerSupported()) {
                    musicService.addVisualizerListener { data: ByteArray ->
                        _visualizerData.value = data
                    }
                }
            }
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            isServiceBound = false
            playbackService?.removeVisualizerListener { data ->
                _visualizerData.value = data
            }
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
                
                for (i in 0 until current.bands) {
                    val level = if (i < genreLevels.size) {
                        genreLevels[i].toShort()
                    } else {
                        0.toShort()
                    }
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
                        // Quick Picks - recently played favorites (increased limit)
                        val quickPicks = repository.quickPicks.first().shuffled().take(50)
                        _quickPicks.value = quickPicks
                        android.util.Log.d("MusicVM", "Loaded ${quickPicks.size} quick picks from DB")
                    } catch (e: Exception) {
                        android.util.Log.e("MusicVM", "Error loading quick picks", e)
                    }
                }
                
                launch {
                    try {
                        // Keep Listening - recent activity (increased limit)
                        val playbackList = repository.playbackHistory.first().take(30)
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


    
    fun playMusic(musicItem: MusicItem) {
        viewModelScope.launch {
            // Update current track ID for format info tracking
            _currentTrackId.value = musicItem.id

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
                
                if (playlistId == "favorites") {
                    // Synthetic playlist for Favorites
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
            try {
                val validName = name.ifBlank { "My Playlist" }
                val playlist = PlaylistEntity(
                    id = java.util.UUID.randomUUID().toString(),
                    title = validName,
                    description = "Created locally",
                    thumbnailUrl = "" 
                )
                val id = withContext(Dispatchers.IO) {
                    musicDao.insertPlaylist(playlist)
                }
                android.util.Log.d("MusicVM", "Created playlist '$validName' with internal ID: $id")
            } catch (e: Exception) {
                android.util.Log.e("MusicVM", "Error creating playlist", e)
                _error.value = "Failed to create playlist: ${e.message}"
            }
        }
    }

    fun addToPlaylist(playlistId: String, track: MusicItem) {
        viewModelScope.launch(Dispatchers.IO) {
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

    // Overload for AddToPlaylistDialog usage
    fun addToPlaylist(track: MusicItem, playlist: PlaylistEntity) {
        addToPlaylist(playlist.id, track)
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

    override fun onCleared() {
        super.onCleared()
        if (isServiceBound) {
            playbackService?.removePlaybackStateListener { }
            app.unbindService(serviceConnection)
            isServiceBound = false
        }
        advancedDownloadManager.destroy()
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