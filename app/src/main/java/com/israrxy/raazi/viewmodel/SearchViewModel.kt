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
    
    val searchHistory: StateFlow<List<SearchHistoryEntity>> = viewState.map { it.history }.stateIn(
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
                                    YouTube.searchSuggestions(query).getOrNull()
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
        if (query.isBlank()) return
        submittedQuery = query
        _isSearching.value = true
        
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val result = repository.searchMusic(query, _selectedService.value)
                _searchResults.value = result
                saveSearchHistory(query)
            } catch (e: Exception) {
                android.util.Log.e("SearchViewModel", "Search error", e)
                _searchResults.value = com.israrxy.raazi.model.SearchResult(query, emptyList())
            } finally {
                _isSearching.value = false
            }
        }
    }

    fun saveSearchHistory(query: String) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            musicDao.insertSearchHistory(SearchHistoryEntity(query = query))
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

data class SearchSuggestionViewState(
    val history: List<SearchHistoryEntity> = emptyList(),
    val suggestions: List<String> = emptyList(),
    val items: List<YTItem> = emptyList()
)
