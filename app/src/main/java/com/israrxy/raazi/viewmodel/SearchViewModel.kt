package com.israrxy.raazi.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.israrxy.raazi.RaaziApplication
import com.israrxy.raazi.data.db.SearchHistoryEntity
import com.zionhuang.innertube.YouTube
import com.zionhuang.innertube.models.YTItem
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

@OptIn(FlowPreview::class)
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

    // ...

    // Changed to use generic SearchResult from repository which supports mixed types via NewPipe/InnerTube wrapper
    private val _searchResults = MutableStateFlow<com.israrxy.raazi.model.SearchResult?>(null)
    val searchResults = _searchResults.asStateFlow()

    // Track the query that generated the current results
    var submittedQuery by androidx.compose.runtime.mutableStateOf("")
        private set
    
    // Expose individual flows for UI
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

    init {
        // Listen to query changes with debounce
        viewModelScope.launch {
            query
                .debounce(300L)
                .flatMapLatest { query ->
                    if (query.isEmpty()) {
                        // Show all search history when query is empty
                        musicDao.searchHistory().map { history ->
                            SearchSuggestionViewState(history = history)
                        }
                    } else {
                        // Fetch suggestions + filter history
                        val result = YouTube.searchSuggestions(query).getOrNull()
                        musicDao.searchHistory(query)
                            .map { it.take(3) }  // Limit history to 3
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
        
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                // Use repository to get mixed results (Songs, Artists, Playlists)
                val result = repository.searchMusic(query, _selectedService.value)
                _searchResults.value = result
                saveSearchHistory(query)
            } catch (e: Exception) {
                android.util.Log.e("SearchViewModel", "Search error", e)
                _searchResults.value = null
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
        submittedQuery = ""
        query.value = "" // Also clear query to reset UI state
    }
}

data class SearchSuggestionViewState(
    val history: List<SearchHistoryEntity> = emptyList(),
    val suggestions: List<String> = emptyList(),
    val items: List<YTItem> = emptyList()
)
