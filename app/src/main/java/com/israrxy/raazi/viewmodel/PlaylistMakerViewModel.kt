package com.israrxy.raazi.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.israrxy.raazi.RaaziApplication
import com.israrxy.raazi.data.account.RaaziAccountManager
import com.israrxy.raazi.data.db.PlaylistEntity
import com.israrxy.raazi.model.MusicItem
import com.israrxy.raazi.model.canPlayAsTrack
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * State holder for the search -> review -> name playlist wizard.
 *
 * Search and playlist creation live here so the Compose screens only render
 * state and forward user actions.
 */
@OptIn(FlowPreview::class)
class PlaylistMakerViewModel(app: Application) : AndroidViewModel(app) {

    private val raaziApp = app as RaaziApplication
    private val repository = raaziApp.container.musicRepository
    private val settingsDataStore = raaziApp.settingsDataStore

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _results = MutableStateFlow<List<MusicItem>>(emptyList())
    val results: StateFlow<List<MusicItem>> = _results.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _searchError = MutableStateFlow<String?>(null)
    val searchError: StateFlow<String?> = _searchError.asStateFlow()

    private val _selectedIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedIds: StateFlow<Set<String>> = _selectedIds.asStateFlow()

    private val _draft = MutableStateFlow<List<MusicItem>>(emptyList())
    val draft: StateFlow<List<MusicItem>> = _draft.asStateFlow()

    private val _isCreating = MutableStateFlow(false)
    val isCreating: StateFlow<Boolean> = _isCreating.asStateFlow()

    private val _createError = MutableStateFlow<String?>(null)
    val createError: StateFlow<String?> = _createError.asStateFlow()

    private val _createdPlaylist = MutableStateFlow<PlaylistEntity?>(null)
    val createdPlaylist: StateFlow<PlaylistEntity?> = _createdPlaylist.asStateFlow()

    val isYouTubeLoggedIn: StateFlow<Boolean> = settingsDataStore.innerTubeCookie
        .map { cookie -> cookie?.contains("SAPISID") == true }
        .stateIn(viewModelScope, SharingStarted.Lazily, false)

    init {
        viewModelScope.launch {
            _query
                .debounce(300L)
                .map { it.trim() }
                .distinctUntilChanged()
                .collectLatest { trimmed ->
                    if (trimmed.isBlank()) {
                        _results.value = emptyList()
                        _searchError.value = null
                        _isSearching.value = false
                        _selectedIds.value = emptySet()
                    } else {
                        performSearch(trimmed)
                    }
                }
        }
    }

    fun onQueryChange(value: String) {
        _query.value = value
    }

    fun clearQuery() {
        _query.value = ""
    }

    fun retrySearch() {
        val trimmed = _query.value.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch { performSearch(trimmed) }
    }

    fun toggleSelection(item: MusicItem) {
        if (!item.canPlayAsTrack()) return
        if (_draft.value.any { it.id == item.id }) return
        _selectedIds.value = if (item.id in _selectedIds.value) {
            _selectedIds.value - item.id
        } else {
            _selectedIds.value + item.id
        }
    }

    fun clearSelection() {
        _selectedIds.value = emptySet()
    }

    /** Appends the current selection to the draft and returns how many were added. */
    fun addSelectedToDraft(): Int {
        val existingIds = _draft.value.mapTo(mutableSetOf()) { it.id }
        val added = _results.value.filter { item ->
            item.id in _selectedIds.value && item.id !in existingIds
        }
        if (added.isEmpty()) return 0
        _draft.value = _draft.value + added
        _query.value = ""
        _results.value = emptyList()
        _selectedIds.value = emptySet()
        // Let the user search for the next batch straight away.
        return added.size
    }

    fun removeFromDraft(trackId: String) {
        _draft.value = _draft.value.filterNot { it.id == trackId }
    }

    fun moveInDraft(trackId: String, direction: Int) {
        val current = _draft.value
        val index = current.indexOfFirst { it.id == trackId }
        if (index < 0) return
        val target = (index + direction).coerceIn(0, current.lastIndex)
        if (target == index) return
        val reordered = current.toMutableList()
        val item = reordered.removeAt(index)
        reordered.add(target, item)
        _draft.value = reordered
    }

    fun createPlaylist(name: String, syncedToYouTube: Boolean) {
        if (_isCreating.value) return
        val tracks = _draft.value
        if (tracks.isEmpty()) {
            _createError.value = "Add at least one song before creating the playlist."
            return
        }
        viewModelScope.launch {
            _isCreating.value = true
            _createError.value = null
            try {
                val playlist = repository.createPlaylist(
                    name = name.trim().ifBlank { "My Playlist" },
                    syncedToYouTube = syncedToYouTube
                )
                repository.addToPlaylist(tracks, playlist)
                RaaziAccountManager.scheduleAutoSync(raaziApp)
                _createdPlaylist.value = playlist
            } catch (e: Exception) {
                _createError.value = e.message ?: "Could not create the playlist. Try again."
            } finally {
                _isCreating.value = false
            }
        }
    }

    fun dismissCreateError() {
        _createError.value = null
    }

    private suspend fun performSearch(query: String) {
        _isSearching.value = true
        _searchError.value = null
        _results.value = emptyList()
        _selectedIds.value = emptySet()
        try {
            val result = repository.searchMusic(query)
            val candidates = buildList {
                result.topResult?.let { add(it) }
                addAll(result.topTracks)
                addAll(result.items)
                addAll(result.moreFromYouTube)
            }
            _results.value = candidates
                .filter { it.canPlayAsTrack() }
                .distinctBy { it.id }
        } catch (e: Exception) {
            _results.value = emptyList()
            _searchError.value = e.message ?: "Search failed. Check your connection and try again."
        } finally {
            _isSearching.value = false
        }
    }
}
