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
import kotlinx.coroutines.async
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

    private val _searchError = MutableStateFlow<String?>(null)
    val searchError = _searchError.asStateFlow()

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
        state.musicItems
    }.stateIn(
        viewModelScope,
        SharingStarted.Lazily,
        emptyList()
    )

    private val suggestionCache = java.util.concurrent.ConcurrentHashMap<String, CachedSuggestionBundle>()

    init {
        viewModelScope.launch {
            query
                .debounce(180L)
                .flatMapLatest { rawQuery ->
                    val trimmed = rawQuery.trim()
                    if (trimmed.isEmpty()) {
                        musicDao.searchHistory().map { history ->
                            SearchSuggestionViewState(history = history)
                        }
                    } else {
                        val cached = suggestionCache[trimmed.lowercase()]
                        val bundle = if (cached != null) {
                            cached
                        } else {
                            try {
                                withContext(kotlinx.coroutines.Dispatchers.IO) {
                                    kotlinx.coroutines.coroutineScope {
                                        val wasLoginForBrowse = YouTube.useLoginForBrowse
                                        try {
                                            YouTube.useLoginForBrowse = false
                                            val suggestionsDeferred = async {
                                                YouTube.searchSuggestions(trimmed).getOrNull()
                                            }
                                            val summaryDeferred = async {
                                                if (trimmed.length >= 2) {
                                                    YouTube.searchSummary(trimmed).getOrNull()
                                                } else null
                                            }
                                            val suggestionsRes = suggestionsDeferred.await()
                                            val summaryRes = summaryDeferred.await()
                                            val resolvedQueries = suggestionsRes?.queries
                                                ?.takeIf { it.isNotEmpty() }
                                                ?: runCatching { repository.getSearchSuggestions(trimmed) }.getOrDefault(emptyList())
                                            val dynamicItems = com.israrxy.raazi.service.buildDynamicSuggestionItems(
                                                query = trimmed,
                                                queries = resolvedQueries,
                                                summaryPage = summaryRes,
                                                recommendedItems = suggestionsRes?.recommendedItems.orEmpty()
                                            )
                                            CachedSuggestionBundle(
                                                queries = resolvedQueries,
                                                musicItems = dynamicItems
                                            )
                                        } finally {
                                            YouTube.useLoginForBrowse = wasLoginForBrowse
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                null
                            }
                        }
                        if (bundle != null && trimmed.length >= 2) {
                            if (suggestionCache.size > 50) {
                                suggestionCache.keys.firstOrNull()?.let { suggestionCache.remove(it) }
                            }
                            suggestionCache[trimmed.lowercase()] = bundle
                        }
                        musicDao.searchHistory(trimmed)
                            .map { it.take(3) }
                            .map { history ->
                                SearchSuggestionViewState(
                                    history = history,
                                    suggestions = bundle?.queries
                                        ?.filter { suggestion ->
                                            history.none { it.query.equals(suggestion, ignoreCase = true) }
                                        }.orEmpty(),
                                    musicItems = bundle?.musicItems.orEmpty()
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
        _searchError.value = null

        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val result = repository.searchMusic(normalizedQuery, _selectedService.value)
                _searchError.value = null
                _searchResults.value = result
                val top = result.topResult ?: result.topTracks.firstOrNull() ?: result.items.firstOrNull { item ->
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
                _searchError.value = e.message ?: "Search failed. Check your connection and try again."
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

private data class CachedSuggestionBundle(
    val queries: List<String> = emptyList(),
    val musicItems: List<com.israrxy.raazi.model.MusicItem> = emptyList()
)

data class SearchSuggestionViewState(
    val history: List<SearchHistoryEntity> = emptyList(),
    val suggestions: List<String> = emptyList(),
    val items: List<YTItem> = emptyList(),
    val musicItems: List<com.israrxy.raazi.model.MusicItem> = emptyList()
)
