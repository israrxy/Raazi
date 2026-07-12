package com.israrxy.raazi.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.israrxy.raazi.RaaziApplication
import com.israrxy.raazi.data.db.SearchHistoryEntity
import com.zionhuang.innertube.YouTube
import com.zionhuang.innertube.models.YTItem
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class SearchViewModel(app: Application) : AndroidViewModel(app) {
    private val database = (app as RaaziApplication).database
    private val musicDao = database.musicDao()
    private val repository = (app as RaaziApplication).container.musicRepository

    val query = MutableStateFlow("")
    
    // 0 = YouTube, 1 = SoundCloud, 2 = Bandcamp
    private val _selectedService = MutableStateFlow(0)
    val selectedService = _selectedService.asStateFlow()
    
    fun selectService(serviceId: Int) {
        if (_selectedService.value != serviceId) {
            _selectedService.value = serviceId
            if (submittedQuery.isNotBlank()) {
                performSearch(submittedQuery)
            }
        }
    }

    private val _viewState = MutableStateFlow(SearchSuggestionViewState())
    val viewState = _viewState.asStateFlow()

    private val _searchResults = MutableStateFlow<com.israrxy.raazi.model.SearchResult?>(null)
    val searchResults = _searchResults.asStateFlow()
    private val _isSearching = MutableStateFlow(false)
    val isSearching = _isSearching.asStateFlow()

    var submittedQuery by androidx.compose.runtime.mutableStateOf("")
        private set
    
    val searchSuggestions: StateFlow<List<String>> = viewState.map { it.suggestions }.stateIn(
        viewModelScope,
        SharingStarted.Lazily,
        emptyList()
    )
    
    val searchHistory: StateFlow<List<SearchHistoryEntity>> = query
        .debounce(150L)
        .flatMapLatest { currentQuery ->
            musicDao.searchHistory(currentQuery.trim())
        }
        .stateIn(
            viewModelScope,
            SharingStarted.Lazily,
            emptyList()
        )

    val topResults: StateFlow<List<com.israrxy.raazi.model.MusicItem>> = viewState.map { state ->
        state.items.mapNotNull { ytItem ->
            when (ytItem) {
                is com.zionhuang.innertube.models.SongItem -> com.israrxy.raazi.model.MusicItem(
                    id = ytItem.id ?: return@mapNotNull null,
                    title = ytItem.title ?: "Unknown",
                    artist = ytItem.artists?.joinToString(", ") { it.name ?: "" } ?: "Unknown Artist",
                    duration = (ytItem.duration?.toLong() ?: 0) * 1000L,
                    thumbnailUrl = ytItem.thumbnail ?: "",
                    audioUrl = "",
                    videoUrl = ytItem.id ?: return@mapNotNull null,
                    isLive = false,
                    contentType = com.israrxy.raazi.model.MusicContentType.SONG,
                    setVideoId = ytItem.setVideoId
                )
                is com.zionhuang.innertube.models.ArtistItem -> com.israrxy.raazi.model.MusicItem(
                    id = ytItem.id,
                    title = ytItem.title,
                    artist = "Artist",
                    duration = 0L,
                    thumbnailUrl = ytItem.thumbnail ?: "",
                    audioUrl = "",
                    videoUrl = ytItem.shareLink.orEmpty(),
                    isLive = false,
                    isPlaylist = false,
                    artistId = ytItem.id,
                    contentType = com.israrxy.raazi.model.MusicContentType.ARTIST
                )
                is com.zionhuang.innertube.models.AlbumItem -> com.israrxy.raazi.model.MusicItem(
                    id = ytItem.browseId ?: return@mapNotNull null,
                    title = ytItem.title ?: "Unknown",
                    artist = ytItem.artists?.joinToString(", ") { it.name ?: "" } ?: "Unknown Artist",
                    duration = 0L,
                    thumbnailUrl = ytItem.thumbnail ?: "",
                    audioUrl = "",
                    videoUrl = ytItem.browseId ?: return@mapNotNull null,
                    isLive = false,
                    isPlaylist = true,
                    contentType = com.israrxy.raazi.model.MusicContentType.ALBUM
                )
                is com.zionhuang.innertube.models.PlaylistItem -> com.israrxy.raazi.model.MusicItem(
                    id = ytItem.id ?: return@mapNotNull null,
                    title = ytItem.title ?: "Unknown",
                    artist = ytItem.author?.name ?: "Unknown Artist",
                    duration = 0L,
                    thumbnailUrl = ytItem.thumbnail ?: "",
                    audioUrl = "",
                    videoUrl = ytItem.id ?: return@mapNotNull null,
                    isLive = false,
                    isPlaylist = true,
                    contentType = com.israrxy.raazi.model.MusicContentType.PLAYLIST
                )
                else -> null
            }
        }
    }.stateIn(
        viewModelScope,
        SharingStarted.Lazily,
        emptyList()
    )

    // Simple in-memory suggestion cache
    private val suggestionCache = LinkedHashMap<String, com.zionhuang.innertube.models.SearchSuggestions?>(16, 0.75f, true)

    init {
        viewModelScope.launch {
            query
                .debounce(150L)
                .flatMapLatest { query ->
                    if (query.isEmpty()) {
                        musicDao.searchHistory().map { history ->
                            SearchSuggestionViewState(history = history)
                        }
                    } else {
                        val cached = suggestionCache[query]
                        val result = if (cached != null) {
                            cached
                        } else {
                            try {
                                withContext(kotlinx.coroutines.Dispatchers.IO) {
                                    // Temporarily disable login-for-browse for suggestions
                                    // YouTube suggestion API doesn't handle authenticated requests
                                    val wasLoginForBrowse = YouTube.useLoginForBrowse
                                    try {
                                        YouTube.useLoginForBrowse = false
                                        YouTube.searchSuggestions(query).getOrNull()
                                    } finally {
                                        YouTube.useLoginForBrowse = wasLoginForBrowse
                                    }
                                }
                            } catch (e: Exception) {
                                null
                            }
                        }
                        if (result != null && query.length >= 2) {
                            suggestionCache[query] = result
                            // Keep cache size bounded
                            if (suggestionCache.size > 50) {
                                val eldest = suggestionCache.keys.first()
                                suggestionCache.remove(eldest)
                            }
                        }
                        musicDao.searchHistory(query)
                            .map { it.take(3) }
                            .map { history ->
                                SearchSuggestionViewState(
                                    history = history,
                                    suggestions = result?.queries
                                        ?.filter { suggestion ->
                                            history.none { it.query == suggestion }
                                        }.orEmpty(),
                                    items = result?.recommendedItems
                                        .orEmpty()
                                        .distinctBy { it.id }
                                )
                            }
                    }
                }.collect { state ->
                _viewState.value = state
            }
        }
    }

    fun performSearch(query: String) {
        val normalizedQuery = query.trim()
        if (normalizedQuery.isBlank()) return
        submittedQuery = normalizedQuery
        _isSearching.value = true
        
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val result = repository.searchMusic(normalizedQuery, _selectedService.value)
                _searchResults.value = result
                val top = result.items.firstOrNull { item ->
                    item.contentType == com.israrxy.raazi.model.MusicContentType.SONG ||
                        item.contentType == com.israrxy.raazi.model.MusicContentType.VIDEO ||
                        (item.contentType == com.israrxy.raazi.model.MusicContentType.UNKNOWN && !item.isPlaylist)
                }
                saveSearchHistory(
                    query = normalizedQuery,
                    thumbnailUrl = top?.thumbnailUrl,
                    resultTitle = top?.title,
                    resultArtist = top?.artist
                )
            } catch (e: Exception) {
                android.util.Log.e("SearchViewModel", "Search error", e)
                _searchResults.value = com.israrxy.raazi.model.SearchResult(normalizedQuery, emptyList())
                saveSearchHistory(normalizedQuery)
            } finally {
                _isSearching.value = false
            }
        }
    }

    fun saveSearchHistory(
        query: String,
        thumbnailUrl: String? = null,
        resultTitle: String? = null,
        resultArtist: String? = null
    ) {
        val normalizedQuery = query.trim()
        if (normalizedQuery.isBlank()) return
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            musicDao.upsertSearchHistory(
                query = normalizedQuery,
                thumbnailUrl = thumbnailUrl?.takeIf { it.isNotBlank() },
                resultTitle = resultTitle?.takeIf { it.isNotBlank() },
                resultArtist = resultArtist?.takeIf { it.isNotBlank() },
                timestamp = System.currentTimeMillis()
            )
            musicDao.trimSearchHistory(MAX_SEARCH_HISTORY_ITEMS)
        }
    }

    fun deleteSearchHistory(item: SearchHistoryEntity) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            musicDao.deleteSearchHistory(item)
        }
    }

    fun clearSearchHistory() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            musicDao.clearSearchHistory()
        }
    }
    
    fun clearSearchResults() {
        _searchResults.value = null
        _isSearching.value = false
        submittedQuery = ""
        query.value = ""
        suggestionCache.clear()
    }
}

private const val MAX_SEARCH_HISTORY_ITEMS = 20

data class SearchSuggestionViewState(
    val history: List<SearchHistoryEntity> = emptyList(),
    val suggestions: List<String> = emptyList(),
    val items: List<YTItem> = emptyList()
)
