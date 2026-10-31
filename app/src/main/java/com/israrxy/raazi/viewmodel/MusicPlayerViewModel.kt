package com.israrxy.raazi.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.Surface
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.media3.exoplayer.ExoPlayer
import com.israrxy.raazi.RaaziApplication
import com.israrxy.raazi.data.account.YouTubeAccountSession
import com.israrxy.raazi.data.db.DownloadEntity
import com.israrxy.raazi.data.db.FormatEntity
import com.israrxy.raazi.data.db.MusicDao
import com.israrxy.raazi.data.db.PlaylistEntity
import com.israrxy.raazi.data.db.PlaylistWithTracks
import com.israrxy.raazi.data.local.BluetoothDeviceManager
import com.israrxy.raazi.data.local.SearchHistoryManager
import com.israrxy.raazi.data.local.SettingsDataStore
import com.israrxy.raazi.data.lyrics.LyricsScriptFilter
import com.israrxy.raazi.data.remote.Lyrics
import com.israrxy.raazi.data.remote.LyricsSearchResult
import com.israrxy.raazi.data.repository.MusicRepository
import com.israrxy.raazi.model.MusicItem
import com.israrxy.raazi.model.PlaybackMediaMode
import com.israrxy.raazi.model.PlaybackState
import com.israrxy.raazi.model.PlaybackVideoQuality
import com.israrxy.raazi.model.Playlist
import com.israrxy.raazi.model.SavedCollectionItem
import com.israrxy.raazi.model.SearchResult
import com.israrxy.raazi.model.toSavedCollectionItem
import com.israrxy.raazi.model.toSavedCollectionItemOrNull
import com.israrxy.raazi.player.StreamResolver
import com.israrxy.raazi.service.AdvancedDownloadManager
import com.israrxy.raazi.service.MusicPlaybackService
import com.israrxy.raazi.service.RingtoneType
import com.israrxy.raazi.service.SpotifyTrack
import com.israrxy.raazi.service.YouTubeMusicExtractor
import com.israrxy.raazi.utils.ConnectivityObserver
import com.zionhuang.innertube.models.YTItem
import com.zionhuang.innertube.pages.ExplorePage
import com.zionhuang.innertube.pages.HomePage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentLinkedQueue

class MusicPlayerViewModel(
    internal val app: Application,
    internal val repository: MusicRepository,
    internal val musicDao: MusicDao
) : AndroidViewModel(app) {

    internal val searchHistoryManager = SearchHistoryManager(app)
    val searchHistory: StateFlow<List<String>> = searchHistoryManager.history
    internal val musicExtractor = YouTubeMusicExtractor.getInstance()
    internal val settingsDataStore = SettingsDataStore(app)
    val advancedDownloadManager: AdvancedDownloadManager = AdvancedDownloadManager(app, musicDao, musicExtractor)

    val isYouTubeLoggedIn: StateFlow<Boolean> = settingsDataStore.innerTubeCookie
        .map { cookie -> cookie?.contains("SAPISID") == true }
        .stateIn(viewModelScope, SharingStarted.Lazily, false)
    val youTubeAccountName: StateFlow<String?> = settingsDataStore.accountName.stateIn(viewModelScope, SharingStarted.Lazily, null)
    val youTubeAccountEmail: StateFlow<String?> = settingsDataStore.accountEmail.stateIn(viewModelScope, SharingStarted.Lazily, null)
    val useLoginForBrowse: StateFlow<Boolean> = settingsDataStore.useLoginForBrowse.stateIn(viewModelScope, SharingStarted.Lazily, true)
    val blurPlayerBackground: StateFlow<Boolean> = settingsDataStore.blurPlayerBackground.stateIn(viewModelScope, SharingStarted.Lazily, true)
    val videoQuality: StateFlow<PlaybackVideoQuality> = settingsDataStore.videoQuality
        .map { wire -> PlaybackVideoQuality.values().firstOrNull { it.wireName == wire } ?: PlaybackVideoQuality.AUTO }
        .stateIn(viewModelScope, SharingStarted.Lazily, PlaybackVideoQuality.AUTO)

    internal var playbackStateListener: ((PlaybackState) -> Unit)? = null
    internal var trackChangedListener: ((MusicItem) -> Unit)? = null
    internal var visualizerListener: ((ByteArray) -> Unit)? = null
    internal var playbackService: MusicPlaybackService? = null
    internal var isServiceBound = false

    private sealed class PendingPlayback {
        data class Single(val item: MusicItem) : PendingPlayback()
        data class Playlist(val items: List<MusicItem>, val startIndex: Int) : PendingPlayback()
    }
    private val pendingPlaybackQueue = ConcurrentLinkedQueue<PendingPlayback>()
    internal fun enqueuePendingSingle(item: MusicItem) { pendingPlaybackQueue.add(PendingPlayback.Single(item)) }
    internal fun enqueuePendingPlaylist(items: List<MusicItem>, startIndex: Int) { pendingPlaybackQueue.add(PendingPlayback.Playlist(items, startIndex)) }
    internal fun processPendingPlayback() {
        while (true) {
            when (val pending = pendingPlaybackQueue.poll()) {
                is PendingPlayback.Single -> playMusic(pending.item)
                is PendingPlayback.Playlist -> playPlaylist(pending.items, pending.startIndex)
                null -> return
            }
        }
    }

    internal val _searchResults = MutableStateFlow<SearchResult?>(null)
    val searchResults: StateFlow<SearchResult?> = _searchResults.asStateFlow()
    private val _searchQueryForSuggestions = MutableStateFlow("")
    private val _searchSuggestions = MutableStateFlow<List<String>>(emptyList())
    @OptIn(ExperimentalCoroutinesApi::class)
    val searchSuggestions: StateFlow<List<String>> = _searchQueryForSuggestions
        .debounce(300).filter { it.length >= 2 }
        .flatMapLatest { query -> flow { emit(runCatching { repository.getSearchSuggestions(query) }.getOrDefault(emptyList())) } }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    fun fetchSearchSuggestions(query: String) {
        _searchQueryForSuggestions.value = query
        if (query.length < 2) _searchSuggestions.value = emptyList()
    }
    internal val _trendingResults = MutableStateFlow<SearchResult?>(null)
    val trendingResults: StateFlow<SearchResult?> = _trendingResults.asStateFlow()
    private val _trendingVideosResults = MutableStateFlow<SearchResult?>(null)
    val trendingVideosResults: StateFlow<SearchResult?> = _trendingVideosResults.asStateFlow()
    private val _allCharts = MutableStateFlow<Map<String, List<MusicItem>>>(emptyMap())
    val allCharts: StateFlow<Map<String, List<MusicItem>>> = _allCharts.asStateFlow()
    internal val _homePage = MutableStateFlow<HomePage?>(null)
    val homePage: StateFlow<HomePage?> = _homePage.asStateFlow()
    private val _topChartsResults = MutableStateFlow<SearchResult?>(null)
    val topChartsResults: StateFlow<SearchResult?> = _topChartsResults.asStateFlow()
    private val _remixResults = MutableStateFlow<SearchResult?>(null)
    val remixResults: StateFlow<SearchResult?> = _remixResults.asStateFlow()
    val favoriteTracks: StateFlow<List<MusicItem>> = repository.favoriteTracks.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val downloadedTracks: StateFlow<List<MusicItem>> = repository.downloadedTracks.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val savedCollections: StateFlow<List<SavedCollectionItem>> = repository.savedCollections.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val userPlaylistsWithTracks: StateFlow<List<PlaylistWithTracks>> = repository.userPlaylistsWithTracks.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val savedCollectionIds: StateFlow<Set<String>> = savedCollections
        .map { collections -> collections.mapTo(mutableSetOf()) { it.id } }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptySet())
    internal val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()
    internal val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    internal val _homeFeedState = MutableStateFlow(HomeFeedUiState(isLoading = true))
    val homeFeedState: StateFlow<HomeFeedUiState> = _homeFeedState.asStateFlow()
    internal var dismissedHomeItemIds: Set<String> = emptySet()
    internal var engagedHomeItemIds: List<String> = emptyList()
    internal val _isSearchLoading = MutableStateFlow(false)
    val isSearchLoading: StateFlow<Boolean> = _isSearchLoading.asStateFlow()
    internal val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error
    internal val _downloadResult = MutableStateFlow<String?>(null)
    val downloadResult: StateFlow<String?> = _downloadResult.asStateFlow()
    internal val _isSyncingYouTubeLibrary = MutableStateFlow(false)
    val isSyncingYouTubeLibrary: StateFlow<Boolean> = _isSyncingYouTubeLibrary.asStateFlow()
    internal val _youTubeSyncStatus = MutableStateFlow<String?>(null)
    val youTubeSyncStatus: StateFlow<String?> = _youTubeSyncStatus.asStateFlow()
    internal val _lastfmStatus = MutableStateFlow<String?>(null)
    val lastfmStatus: StateFlow<String?> = _lastfmStatus.asStateFlow()
    internal val _lastfmMessage = MutableStateFlow<String?>(null)
    val lastfmMessage: StateFlow<String?> = _lastfmMessage.asStateFlow()
    val dbActiveDownloads: StateFlow<List<DownloadEntity>> = advancedDownloadManager.activeDownloads.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val dbCompletedDownloads: StateFlow<List<DownloadEntity>> = advancedDownloadManager.completedDownloads.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val dbFailedDownloads: StateFlow<List<DownloadEntity>> = advancedDownloadManager.failedDownloads.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val dbAllDownloads: StateFlow<List<DownloadEntity>> = advancedDownloadManager.allDownloads.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val activeDownloadCount: StateFlow<Int> = advancedDownloadManager.activeDownloadCount.stateIn(viewModelScope, SharingStarted.Lazily, 0)
    private val _likedSongsCount = MutableStateFlow(0)
    val likedSongsCount: StateFlow<Int> = _likedSongsCount.asStateFlow()
    val streamQuality: StateFlow<String?> = StreamResolver.currentStreamQuality
    internal val _currentTrackId = MutableStateFlow<String?>(null)
    @OptIn(ExperimentalCoroutinesApi::class)
    val currentFormat: StateFlow<FormatEntity?> = _currentTrackId
        .flatMapLatest { trackId -> if (trackId != null) musicDao.getFormatFlow(trackId) else flowOf(null) }
        .stateIn(viewModelScope, SharingStarted.Lazily, null)
    @OptIn(ExperimentalCoroutinesApi::class)
    val currentTrackRelated: StateFlow<List<MusicItem>> = playbackState
        .map { it.currentTrack?.id }
        .flatMapLatest { trackId -> if (trackId != null) repository.getRelatedTracks(trackId) else flowOf(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    internal val _currentArtistSongs = MutableStateFlow<List<MusicItem>>(emptyList())
    val currentArtistSongs: StateFlow<List<MusicItem>> = _currentArtistSongs.asStateFlow()
    internal val _autoPlayQueue = MutableStateFlow<List<MusicItem>>(emptyList())
    val autoPlayQueue: StateFlow<List<MusicItem>> = _autoPlayQueue.asStateFlow()
    internal val _isLoadingAutoPlay = MutableStateFlow(false)
    val isLoadingAutoPlay: StateFlow<Boolean> = _isLoadingAutoPlay.asStateFlow()
    internal val _selectedInnerTubeChip = MutableStateFlow<HomePage.Chip?>(null)
    val selectedInnerTubeChip: StateFlow<HomePage.Chip?> = _selectedInnerTubeChip.asStateFlow()
    internal val _categorySections = MutableStateFlow<List<HomeSection>>(emptyList())
    internal val _isCategoryLoading = MutableStateFlow(false)

    internal val connectivityObserver = ConnectivityObserver(app)
    val isOnline: StateFlow<Boolean> = connectivityObserver.isOnline.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    internal val _justReconnected = MutableStateFlow(false)
    val justReconnected: StateFlow<Boolean> = _justReconnected.asStateFlow()
    internal val _isSearchQueue = MutableStateFlow(false)
    val isSearchQueue: StateFlow<Boolean> = _isSearchQueue.asStateFlow()

    data class EqualizerState(
        val bands: Short = 0, val minLevel: Short = 0, val maxLevel: Short = 0,
        val currentLevels: List<Short> = emptyList(), val centerFreqs: List<Int> = emptyList(),
        val presets: List<String> = emptyList(), val currentPreset: String = "Custom",
        val bassBoostStrength: Short = 0, val virtualizerStrength: Short = 0, val reverbPreset: Int = 0,
        val bassBoostSupported: Boolean = false, val virtualizerSupported: Boolean = false, val reverbSupported: Boolean = false
    )
    data class CustomPreset(val name: String, val levels: List<Short>, val bassBoost: Short = 0, val virtualizer: Short = 0, val reverb: Int = 0)
    internal val _equalizerState = MutableStateFlow(EqualizerState())
    val equalizerState: StateFlow<EqualizerState> = _equalizerState.asStateFlow()
    internal val _customPresets = MutableStateFlow<List<CustomPreset>>(emptyList())
    val customPresets: StateFlow<List<CustomPreset>> = _customPresets.asStateFlow()
    internal val _visualizerData = MutableStateFlow<ByteArray?>(null)
    val visualizerData: StateFlow<ByteArray?> = _visualizerData.asStateFlow()
    val connectedAudioDevice: StateFlow<BluetoothDeviceManager.AudioDevice?> =
        RaaziApplication.instance.bluetoothDeviceManager.connectedDevice
    internal val _lyrics = MutableStateFlow<Lyrics?>(null)
    val lyrics: StateFlow<Lyrics?> = _lyrics.asStateFlow()
    internal val _isLyricsLoading = MutableStateFlow(false)
    val isLyricsLoading: StateFlow<Boolean> = _isLyricsLoading.asStateFlow()
    internal val _lyricsScriptFilter = MutableStateFlow(LyricsScriptFilter.ALL)
    val lyricsScriptFilter: StateFlow<LyricsScriptFilter> = _lyricsScriptFilter.asStateFlow()
    internal val _lyricsSearchResults = MutableStateFlow<List<LyricsSearchResult>>(emptyList())
    val lyricsSearchResults: StateFlow<List<LyricsSearchResult>> = _lyricsSearchResults.asStateFlow()
    internal val _isLyricsSearchLoading = MutableStateFlow(false)
    val isLyricsSearchLoading: StateFlow<Boolean> = _isLyricsSearchLoading.asStateFlow()
    internal var lyricsFetchJob: Job? = null
    internal var lastLyricsTrack: MusicItem? = null
    internal val _songCredits = MutableStateFlow<String?>(null)
    val songCredits: StateFlow<String?> = _songCredits.asStateFlow()
    internal val _isSongCreditsLoading = MutableStateFlow(false)
    val isSongCreditsLoading: StateFlow<Boolean> = _isSongCreditsLoading.asStateFlow()
    internal var songCreditsFetchJob: Job? = null
    internal val scrobbledTrackIds = mutableSetOf<String>()
    internal var lastFmEnabled = false
    internal var lastFmTrackId: String? = null
    internal var lastFmNowPlayingSent = false
    internal val _quickPicks = MutableStateFlow<List<MusicItem>>(emptyList())
    val quickPicks: StateFlow<List<MusicItem>> = _quickPicks.asStateFlow()
    internal val _forgottenFavorites = MutableStateFlow<List<MusicItem>>(emptyList())
    val forgottenFavorites: StateFlow<List<MusicItem>> = _forgottenFavorites.asStateFlow()
    internal val _keepListening = MutableStateFlow<List<MusicItem>>(emptyList())
    val keepListening: StateFlow<List<MusicItem>> = _keepListening.asStateFlow()

    sealed class RecommendationItem {
        data class FromYTItem(val ytItem: YTItem) : RecommendationItem()
        data class FromMusicItem(val musicItem: MusicItem) : RecommendationItem()
    }
    data class SimilarRecommendation(val title: String, val thumbnailUrl: String?, val items: List<RecommendationItem>)

    internal val _similarRecommendations = MutableStateFlow<List<SimilarRecommendation>>(emptyList())
    val similarRecommendations: StateFlow<List<SimilarRecommendation>> = _similarRecommendations.asStateFlow()
    internal val _explorePage = MutableStateFlow<ExplorePage?>(null)
    val explorePage: StateFlow<ExplorePage?> = _explorePage.asStateFlow()
    internal val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()
    internal val _selectedChip = MutableStateFlow<String?>(null)
    val selectedChip: StateFlow<String?> = _selectedChip.asStateFlow()
    internal val _filteredPlaylists = MutableStateFlow<List<Playlist>?>(null)
    val filteredPlaylists: StateFlow<List<Playlist>?> = _filteredPlaylists.asStateFlow()
    val playbackHistory: StateFlow<List<MusicItem>> = repository.playbackHistory.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    internal val _currentPlaylist = MutableStateFlow<Playlist?>(null)
    val currentPlaylist: StateFlow<Playlist?> = _currentPlaylist.asStateFlow()
    val userPlaylists: StateFlow<List<PlaylistEntity>> = musicDao.getAllPlaylists().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    private val _activeDownloads = MutableStateFlow<Map<Long, Pair<MusicItem, Int>>>(emptyMap())
    val activeDownloads: StateFlow<Map<Long, Pair<MusicItem, Int>>> = _activeDownloads.asStateFlow()
    private val _audioSourceLabel = MutableStateFlow("YouTube")
    val audioSourceLabel: StateFlow<String> = _audioSourceLabel.asStateFlow()

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

    internal val _ringtoneState = MutableStateFlow<RingtoneState>(RingtoneState.Idle)
    val ringtoneState: StateFlow<RingtoneState> = _ringtoneState.asStateFlow()
    internal val _ringtoneTrack = MutableStateFlow<MusicItem?>(null)
    val ringtoneTrack: StateFlow<MusicItem?> = _ringtoneTrack.asStateFlow()
    internal val _ringtoneType = MutableStateFlow(RingtoneType.RINGTONE)
    val ringtoneType: StateFlow<RingtoneType> = _ringtoneType.asStateFlow()

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
        val spotifyTrack: SpotifyTrack, val resolvedItem: MusicItem?,
        val isResolving: Boolean = false, val isError: Boolean = false
    )

    internal val _spotifyImportState = MutableStateFlow<SpotifyImportState>(SpotifyImportState.Idle)
    val spotifyImportState: StateFlow<SpotifyImportState> = _spotifyImportState.asStateFlow()
    internal val _resolvedSpotifyTracks = MutableStateFlow<List<ResolvedSpotifyTrack>>(emptyList())
    val resolvedSpotifyTracks: StateFlow<List<ResolvedSpotifyTrack>> = _resolvedSpotifyTracks.asStateFlow()
    internal val _spotifyPlaylistName = MutableStateFlow("")
    val spotifyPlaylistName: StateFlow<String> = _spotifyPlaylistName.asStateFlow()

    sealed interface DeepLinkEvent {
        data object ExpandPlayer : DeepLinkEvent
        data class OpenPlaylist(val playlistId: String) : DeepLinkEvent
        data class OpenArtist(val artistId: String, val artistName: String?) : DeepLinkEvent
    }

    internal val _pendingDeepLinkEvent = MutableStateFlow<DeepLinkEvent?>(null)
    val pendingDeepLinkEvent: StateFlow<DeepLinkEvent?> = _pendingDeepLinkEvent.asStateFlow()
    internal val serviceConnection: ServiceConnection = createServiceConnection()

    init {
        StreamResolver.musicDao = musicDao
        StreamResolver.sessionFlacEnabled = false
        bindPlaybackService()
        loadHomeContent()
        observeDownloadEvents()
        syncDownloadSettings()
        initLastFmScrobbling()
        observeHistoryUpdates()
        observeNetworkConnectivity()
    }

    internal fun bindPlaybackService() {
        runCatching { app.bindService(Intent(app, MusicPlaybackService::class.java), serviceConnection, Context.BIND_AUTO_CREATE) }
            .onFailure { Log.e("MusicVM", "Failed to bind playback service", it) }
    }

    fun loadEqualizerState() = loadEqualizerStateImpl()
    fun setBandLevel(band: Short, level: Short) = setBandLevelImpl(band, level)
    fun usePreset(presetName: String) = usePresetImpl(presetName)
    fun saveEqualizerSettings() = saveEqualizerSettingsImpl()
    fun applyDeviceEqProfileIfAny() = applyDeviceEqProfileIfAnyImpl()
    fun setBassBoostStrength(strength: Short) = setBassBoostStrengthImpl(strength)
    fun setVirtualizerStrength(strength: Short) = setVirtualizerStrengthImpl(strength)
    fun setReverbPreset(preset: Int) = setReverbPresetImpl(preset)
    fun saveCustomPreset(name: String) = saveCustomPresetImpl(name)
    fun loadCustomPreset(preset: CustomPreset) = loadCustomPresetImpl(preset)
    fun deleteCustomPreset(preset: CustomPreset) = deleteCustomPresetImpl(preset)
    fun loadCustomPresets() = loadCustomPresetsImpl()
    fun exportEqualizerSettings(): String = exportEqualizerSettingsImpl()
    fun isVisualizerSupported(): Boolean = playbackService?.isVisualizerSupported() ?: false
    fun enableVisualizer(enable: Boolean) { playbackService?.enableVisualizer(enable) }

    fun loadHomeContent() = loadHomeContentImpl()
    fun refreshHomeContent() = refreshHomeContentImpl()
    fun shuffleHomeFeed(): Boolean = shuffleHomeFeedImpl()
    fun recordHomeInteraction(itemId: String, sectionId: String, action: String, sourceType: String) = recordHomeInteractionImpl(itemId, sectionId, action, sourceType)
    fun dismissHomeItem(itemId: String, sectionId: String, sourceType: String) = dismissHomeItemImpl(itemId, sectionId, sourceType)
    fun refreshHomeSection(sectionId: String) = refreshHomeSectionImpl(sectionId)
    fun loadMoreSections() = loadMoreSectionsImpl()
    fun selectFilterChip(chip: HomePage.Chip?) = selectFilterChipImpl(chip)
    fun onChipSelected(chip: String) = onChipSelectedImpl(chip)
    fun searchMusic(query: String) {
        viewModelScope.launch {
            _isSearchLoading.value = true; _error.value = null
            try { _searchResults.value = repository.searchMusic(query); searchHistoryManager.addQuery(query) }
            catch (e: Exception) { _error.value = "Search failed: ${e.message}" }
            finally { _isSearchLoading.value = false }
        }
    }

    fun playMusic(musicItem: MusicItem) = playMusicImpl(musicItem)
    fun playFromSearch(index: Int) { _searchResults.value?.items?.getOrNull(index)?.let(::playMusic) }
    fun playFromQuickPicks(index: Int) { if (_quickPicks.value.isNotEmpty()) playPlaylist(_quickPicks.value, index) }
    fun playFromKeepListening(index: Int) { if (_keepListening.value.isNotEmpty()) playPlaylist(_keepListening.value, index) }
    fun playFromHistory(index: Int) { viewModelScope.launch { if (playbackHistory.value.isNotEmpty()) playPlaylist(playbackHistory.value, index) } }
    fun playFromTrending(index: Int) { _trendingResults.value?.items?.takeIf { it.isNotEmpty() }?.let { playPlaylist(it, index) } }
    fun playFromPlaylist(index: Int) { _currentPlaylist.value?.items?.takeIf { index in it.indices }?.let { playPlaylist(it, index) } }
    fun playPlaylist(items: List<MusicItem>, startIndex: Int = 0) { if (items.isNotEmpty()) { _isSearchQueue.value = false; playPlaylistInternal(items, startIndex) } }
    fun playSearchQueue(items: List<MusicItem>, startIndex: Int = 0) = playSearchQueueImpl(items, startIndex)
    fun pause() { runCatching { playbackService?.pause() } }
    fun resume() { runCatching { playbackService?.resume() } }
    fun stop() { runCatching { playbackService?.stop() } }
    fun next() { runCatching { playbackService?.next() } }
    fun previous() { runCatching { playbackService?.previous() } }
    fun seekTo(position: Long) { runCatching { playbackService?.seekTo(position) } }
    fun toggleShuffle() { runCatching { playbackService?.toggleShuffle() } }
    fun toggleRepeat() { runCatching { playbackService?.toggleRepeat() } }
    fun addToQueue(items: List<MusicItem>) { runCatching { playbackService?.addToQueue(items) } }
    fun addToQueue(item: MusicItem) = addToQueue(listOf(item))
    fun playNext(items: List<MusicItem>) { runCatching { playbackService?.playNext(items) } }
    fun playPause() { if (_playbackState.value.isPlaying) pause() else resume() }
    fun playFromQueue(upcomingIndex: Int) = jumpToQueueIndex(_playbackState.value.currentIndex + 1 + upcomingIndex)
    fun jumpToQueueIndex(absoluteIndex: Int) { runCatching { playbackService?.jumpToIndex(absoluteIndex) } }
    fun removeFromUpcoming(upcomingIndex: Int) = removeFromQueue(_playbackState.value.currentIndex + 1 + upcomingIndex)
    fun removeFromQueue(absoluteIndex: Int) { runCatching { playbackService?.removeFromQueue(absoluteIndex) } }
    fun moveUpcoming(fromUpcoming: Int, toUpcoming: Int) = moveQueueItem(_playbackState.value.currentIndex + 1 + fromUpcoming, _playbackState.value.currentIndex + 1 + toUpcoming)
    fun moveQueueItem(fromAbsolute: Int, toAbsolute: Int) { runCatching { playbackService?.moveQueueItem(fromAbsolute, toAbsolute) } }
    fun dismissQueue() { runCatching { playbackService?.clearUpcoming() } }
    fun clearUpcomingQueue() = dismissQueue()
    fun setQueueOrder(newOrder: List<MusicItem>) { runCatching { playbackService?.setQueueOrder(newOrder) } }
    fun playFromAutoPlay(autoPlayIndex: Int) = playFromAutoPlayImpl(autoPlayIndex)
    fun fetchAutoPlayQueue() { _playbackState.value.currentTrack?.let(::fetchAutoPlayQueueImpl) }
    fun fetchAutoPlayQueue(track: MusicItem) = fetchAutoPlayQueueImpl(track)
    fun startRadio(track: MusicItem) = startRadioImpl(track)
    fun saveCurrentQueueAsPlaylist(name: String? = null, tracks: List<MusicItem>? = null) = saveCurrentQueueAsPlaylistImpl(name, tracks)

    fun toggleFavorite(musicItem: MusicItem) {
        viewModelScope.launch {
            if (favoriteTracks.value.any { it.id == musicItem.id }) repository.removeFromFavorites(musicItem) else repository.addToFavorites(musicItem)
        }
    }
    fun fetchSongCredits(track: MusicItem) = fetchSongCreditsImpl(track)
    fun toggleSavedCollection(item: MusicItem) { viewModelScope.launch { repository.toggleSavedCollection(item) } }
    fun toggleSavedArtist(artistId: String, artistName: String, thumbnailUrl: String = "") { viewModelScope.launch { repository.toggleSavedArtist(artistId, artistName, thumbnailUrl) } }
    fun toggleSavedPlaylist(playlist: Playlist) { viewModelScope.launch { repository.toggleSavedCollection(playlist) } }
    fun isSavedCollection(item: MusicItem): Boolean = item.toSavedCollectionItemOrNull()?.id in savedCollectionIds.value
    fun isSavedCollection(playlist: Playlist): Boolean = playlist.toSavedCollectionItem().id in savedCollectionIds.value
    fun retryLyricsFetch() = retryLyricsFetchImpl()
    fun searchLyricsOptions(titleOverride: String? = null, artistOverride: String? = null) = searchLyricsOptionsImpl(titleOverride, artistOverride)
    fun clearLyricsSearchResults() { _lyricsSearchResults.value = emptyList() }
    fun saveLyricsSelection(result: LyricsSearchResult) = saveLyricsSelectionImpl(result)
    fun clearSavedLyricsSelection() = clearSavedLyricsSelectionImpl()
    fun setLyricsScriptFilter(filter: LyricsScriptFilter) = setLyricsScriptFilterImpl(filter)
    fun clearError() { _error.value = null }
    fun clearSearchHistory() = searchHistoryManager.clearHistory()
    fun clearYouTubeSyncStatus() { _youTubeSyncStatus.value = null }
    fun syncYouTubeLibrary() = syncYouTubeLibraryImpl()
    fun logoutFromYouTube() = logoutFromYouTubeImpl()
    fun setUseLoginForBrowse(enabled: Boolean) { viewModelScope.launch { YouTubeAccountSession.setUseLoginForBrowse(settingsDataStore, enabled) } }
    suspend fun connectLastFm(username: String, password: String): Boolean = connectLastFmImpl(username, password)
    fun clearLastFmMessage() { _lastfmMessage.value = null }

    fun loadPlaylist(playlistId: String) = loadPlaylistImpl(playlistId)
    fun createPlaylist(name: String, syncedToYouTube: Boolean = false) = createPlaylistImpl(name, syncedToYouTube)
    fun addToPlaylist(playlistId: String, track: MusicItem) = addToPlaylistByIdImpl(playlistId, track)
    fun addToPlaylist(track: MusicItem, playlist: PlaylistEntity) = addToPlaylistEntityImpl(track, playlist)
    fun addToPlaylist(tracks: List<MusicItem>, playlist: PlaylistEntity) = addTracksToPlaylistEntityImpl(tracks, playlist)
    fun deletePlaylist(playlistId: String) = deletePlaylistImpl(playlistId)
    fun syncLocalPlaylistToYouTube(playlistId: String) = syncLocalPlaylistToYouTubeImpl(playlistId)
    fun renamePlaylist(playlistId: String, newName: String) = renamePlaylistImpl(playlistId, newName)
    fun removeTrackFromPlaylist(playlistId: String, trackId: String, setVideoId: String? = null) = removeTrackFromPlaylistImpl(playlistId, trackId, setVideoId)
    fun removeTracksFromPlaylist(playlistId: String, tracks: List<MusicItem>) = removeTracksFromPlaylistImpl(playlistId, tracks)
    fun reorderPlaylistTracks(playlistId: String, trackIds: List<String>) = reorderPlaylistTracksImpl(playlistId, trackIds)
    fun downloadTrack(track: MusicItem) = advancedDownloadManager.downloadTrack(track)
    fun downloadPlaylist(tracks: List<MusicItem>) = advancedDownloadManager.downloadPlaylist(tracks)
    fun cancelDownload(trackId: String) = advancedDownloadManager.cancelDownload(trackId)
    fun retryDownload(trackId: String) = advancedDownloadManager.retryDownload(trackId)
    fun deleteDownloadedFile(trackId: String) = advancedDownloadManager.deleteDownloadedFile(trackId)
    fun isTrackDownloaded(trackId: String): Boolean = advancedDownloadManager.isTrackDownloaded(trackId)
    fun getDownloadStorageUsed(): Long = advancedDownloadManager.getDownloadStorageUsed()
    fun getAvailableStorage(): Long = advancedDownloadManager.getAvailableStorage()
    fun deleteAllDownloads() = advancedDownloadManager.deleteAllDownloads()
    fun getPlayer(): ExoPlayer? = playbackService?.getPlayer()
    fun togglePlaybackMode(): Boolean = playbackService?.togglePlaybackMode() ?: false
    fun setPlaybackMediaMode(mode: PlaybackMediaMode): Boolean = playbackService?.setPlaybackMediaMode(mode) ?: false
    fun setPlaybackVideoQuality(quality: PlaybackVideoQuality): Boolean {
        viewModelScope.launch { settingsDataStore.setVideoQuality(quality.wireName) }
        return playbackService?.setPlaybackVideoQuality(quality) ?: false
    }
    fun toggleAudioSource() { StreamResolver.sessionFlacEnabled = false; _audioSourceLabel.value = "YouTube" }
    fun setAudioSource(mode: String) { StreamResolver.sessionFlacEnabled = false; _audioSourceLabel.value = "YouTube" }
    fun setVideoSurface(surface: Surface?) { playbackService?.setVideoSurface(surface) }
    fun clearVideoSurface() { playbackService?.clearVideoSurface() }
    fun setRingtoneType(type: RingtoneType) { _ringtoneType.value = type }
    fun checkWriteSettingsPermission(): Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Settings.System.canWrite(app) else true
    fun downloadForRingtone(track: MusicItem) = downloadForRingtoneImpl(track)
    fun setAsRingtone(filePath: String, startMs: Long, endMs: Long) = setAsRingtoneImpl(filePath, startMs, endMs)
    fun restoreRingtoneReadyState(filePath: String, durationMs: Long) { _ringtoneState.value = RingtoneState.Ready(filePath, durationMs) }
    fun resetRingtoneState() { _ringtoneState.value = RingtoneState.Idle; _ringtoneTrack.value = null; _ringtoneType.value = RingtoneType.RINGTONE }
    fun startSpotifyImport(playlistUrl: String) = startSpotifyImportImpl(playlistUrl)
    fun replaceResolvedTrack(index: Int, newItem: MusicItem) = updateResolvedTrack(index) { it.copy(resolvedItem = newItem, isError = false, isResolving = false) }
    fun finalizeSpotifyImport(playlistTitle: String, syncedToYouTube: Boolean) = finalizeSpotifyImportImpl(playlistTitle, syncedToYouTube)
    fun resetSpotifyImportState() { _spotifyImportState.value = SpotifyImportState.Idle; _resolvedSpotifyTracks.value = emptyList(); _spotifyPlaylistName.value = "" }
    fun consumeDeepLinkEvent() { _pendingDeepLinkEvent.value = null }
    fun getShareLink(): String? = playbackState.value.currentTrack?.let { "https://raazi.pages.dev/?video=${android.net.Uri.encode(it.id)}" }
    fun handleDeepLink(uri: Uri) = handleDeepLinkImpl(uri)
    override fun onCleared() { super.onCleared(); onClearedImpl() }

    companion object {
        fun provideFactory(application: Application): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
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
