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

@OptIn(FlowPreview::class)
class SearchViewModel(app: Application) : AndroidViewModel(app) {
    private val database = (app as RaaziApplication).database
    private val musicDao = database.musicDao()

    val query = MutableStateFlow("")
    
    private val _viewState = MutableStateFlow(SearchSuggestionViewState())
    val viewState = _viewState.asStateFlow()

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
    
    private val _searchResults = MutableStateFlow<com.zionhuang.innertube.pages.SearchResult?>(null)
    val searchResults = _searchResults.asStateFlow()
    
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
    
    fun performSearch(query: String) {
        if (query.isBlank()) return
        
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val result = YouTube.search(query, YouTube.SearchFilter.FILTER_SONG).getOrNull()
                _searchResults.value =result
                saveSearchHistory(query)
            } catch (e: Exception) {
                android.util.Log.e("SearchViewModel", "Search error", e)
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
}

data class SearchSuggestionViewState(
    val history: List<SearchHistoryEntity> = emptyList(),
    val suggestions: List<String> = emptyList(),
    val items: List<YTItem> = emptyList()
)
