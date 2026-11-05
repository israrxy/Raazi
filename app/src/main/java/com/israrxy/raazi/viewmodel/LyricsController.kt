package com.israrxy.raazi.viewmodel

import android.util.Log
import com.israrxy.raazi.data.local.SettingsDataStore
import com.israrxy.raazi.data.lyrics.LyricsScriptDetector
import com.israrxy.raazi.data.lyrics.LyricsScriptFilter
import com.israrxy.raazi.data.lyrics.LyricsUiState
import com.israrxy.raazi.data.lyrics.hasUsableText
import com.israrxy.raazi.data.lyrics.lyricsOrNull
import com.israrxy.raazi.data.remote.Lyrics
import com.israrxy.raazi.data.remote.LyricsSearchResult
import com.israrxy.raazi.data.repository.MusicRepository
import com.israrxy.raazi.model.MusicItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Owns the entire lyrics lifecycle: fetching, multi-provider fallback, in-memory
 * provider caching and script filtering.
 *
 * Design rules:
 *  - [LyricsUiState.Loading] stays active until every provider/extractor has
 *    finished. The UI never sees an intermediate empty state.
 *  - Only the newest request may publish a result. Cancelled jobs are ignored by
 *    comparing request sequence numbers, so stale emissions can never overwrite
 *    the current song.
 *  - Provider results are cached per track so switching sources or re-opening the
 *    lyrics browser never triggers a redundant network search.
 */
internal class LyricsController(
    private val repository: MusicRepository,
    private val settingsDataStore: SettingsDataStore,
    private val scope: CoroutineScope
) {
    private val _state = MutableStateFlow<LyricsUiState>(LyricsUiState.Idle)
    val state: StateFlow<LyricsUiState> = _state.asStateFlow()

    private val _scriptFilter = MutableStateFlow(LyricsScriptFilter.ALL)
    val scriptFilter: StateFlow<LyricsScriptFilter> = _scriptFilter.asStateFlow()

    private val _searchResults = MutableStateFlow<List<LyricsSearchResult>>(emptyList())
    val searchResults: StateFlow<List<LyricsSearchResult>> = _searchResults.asStateFlow()

    private val _isSearchLoading = MutableStateFlow(false)
    val isSearchLoading: StateFlow<Boolean> = _isSearchLoading.asStateFlow()

    var lastTrack: MusicItem? = null
        private set

    private var fetchJob: Job? = null
    private var searchJob: Job? = null
    private var requestSequence = 0L
    private var searchSequence = 0L
    private var searchCacheKey: String? = null
    private val providerCache = LinkedHashMap<String, Lyrics>()

    fun load(track: MusicItem, forceRefresh: Boolean = false) {
        val resolved = track
        val previousTrackId = lastTrack?.id
        if (previousTrackId != resolved.id) {
            clearTrackCache()
        }
        lastTrack = resolved

        if (forceRefresh) {
            searchCacheKey = null
        } else {
            when (val current = _state.value) {
                is LyricsUiState.Success -> if (current.trackId == resolved.id) return
                is LyricsUiState.Loading -> if (current.trackId == resolved.id) return
                else -> Unit
            }
        }

        val requestId = ++requestSequence
        fetchJob?.cancel()
        _state.value = LyricsUiState.Loading(resolved.id)
        fetchJob = scope.launch {
            try {
                val result = repository.getLyrics(
                    item = resolved,
                    preferredScript = _scriptFilter.value,
                    forceRefresh = forceRefresh
                )
                if (requestId != requestSequence) return@launch
                if (result != null && result.hasUsableText()) {
                    cacheProvider(result)
                    _state.value = LyricsUiState.Success(
                        trackId = resolved.id,
                        lyrics = result,
                        providerCache = providerSnapshot()
                    )
                } else {
                    _state.value = LyricsUiState.Unavailable(resolved.id)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Lyrics fetch failed for ${resolved.title}", e)
                if (requestId == requestSequence) {
                    _state.value = LyricsUiState.Unavailable(resolved.id)
                }
            }
        }
    }

    fun retry(forceRefresh: Boolean = true) {
        val track = lastTrack ?: return
        load(track, forceRefresh = forceRefresh)
    }

    /**
     * Returns true while the current track is being fetched or already resolved.
     * Used to avoid duplicate fetches when the real duration arrives late.
     */
    fun hasResolvedOrIsLoading(): Boolean {
        val current = _state.value
        return current is LyricsUiState.Loading || current.lyricsOrNull != null
    }

    fun searchOptions(
        titleOverride: String? = null,
        artistOverride: String? = null,
        force: Boolean = false
    ) {
        val track = lastTrack ?: return
        val title = titleOverride ?: track.title
        val artist = artistOverride ?: track.artist
        val cacheKey = "${track.id}|${title.trim().lowercase()}|${artist.trim().lowercase()}"

        if (!force) {
            if (searchCacheKey == cacheKey) return
            if (_isSearchLoading.value) return
        }

        val sequence = ++searchSequence
        searchJob?.cancel()
        _isSearchLoading.value = true
        searchJob = scope.launch {
            try {
                val results = repository.searchLyricsOptions(
                    item = track,
                    titleOverride = title,
                    artistOverride = artist,
                    preferredScript = _scriptFilter.value
                )
                if (sequence != searchSequence) return@launch
                results.forEach { cacheProvider(it.lyrics) }
                _searchResults.value = results
                searchCacheKey = cacheKey
                val current = _state.value
                if (current is LyricsUiState.Success && current.trackId == track.id) {
                    _state.value = current.copy(providerCache = providerSnapshot())
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Lyrics provider search failed", e)
                if (sequence == searchSequence) {
                    _searchResults.value = emptyList()
                }
            } finally {
                if (sequence == searchSequence) {
                    _isSearchLoading.value = false
                }
            }
        }
    }

    /**
     * Applies a provider result instantly from memory and persists the choice in
     * the background. No loading state and no network request are involved.
     */
    fun selectResult(result: LyricsSearchResult) {
        val track = lastTrack ?: return
        cacheProvider(result.lyrics)
        val savedLyrics = result.lyrics.copy(source = "Saved ${result.lyrics.source}")
        _state.value = LyricsUiState.Success(
            trackId = track.id,
            lyrics = savedLyrics,
            providerCache = providerSnapshot()
        )
        scope.launch {
            runCatching { repository.saveLyricsForTrack(track, result.lyrics) }
                .onFailure { Log.w(TAG, "Could not persist selected lyrics", it) }
        }
    }

    fun clearSavedSelection() {
        val track = lastTrack ?: return
        scope.launch {
            runCatching { repository.clearSavedLyricsForTrack(track) }
                .onFailure { Log.w(TAG, "Could not clear saved lyrics", it) }
            load(track, forceRefresh = true)
        }
    }

    fun setScriptFilter(filter: LyricsScriptFilter) {
        if (_scriptFilter.value == filter) return
        _scriptFilter.value = filter
        val track = lastTrack ?: return

        // 1. The active lyrics already satisfy the requested script: nothing to do.
        val current = _state.value.lyricsOrNull
        if (current != null && LyricsScriptDetector.matches(filter, current)) return

        // 2. Pick a matching provider result straight from the in-memory cache.
        val cached = providerCache.values.firstOrNull { LyricsScriptDetector.matches(filter, it) }
        if (cached != null) {
            _state.value = LyricsUiState.Success(
                trackId = track.id,
                lyrics = cached,
                providerCache = providerSnapshot()
            )
            return
        }

        // 3. Fall back to the last browser search results (already ranked by script).
        val searched = _searchResults.value.firstOrNull {
            LyricsScriptDetector.matches(filter, it.lyrics)
        }
        if (searched != null) {
            cacheProvider(searched.lyrics)
            _state.value = LyricsUiState.Success(
                trackId = track.id,
                lyrics = searched.lyrics,
                providerCache = providerSnapshot()
            )
            return
        }

        // 4. A manually pinned source must not be replaced by an automatic fetch.
        if (current?.source?.startsWith("Saved") == true) return

        // 5. Nothing cached for this script: query providers once.
        load(track, forceRefresh = true)
    }

    fun clearSearchResults() {
        searchSequence++
        searchJob?.cancel()
        searchJob = null
        searchCacheKey = null
        _searchResults.value = emptyList()
        _isSearchLoading.value = false
    }

    fun clearAll() {
        requestSequence++
        fetchJob?.cancel()
        fetchJob = null
        clearSearchResults()
        clearTrackCache()
        lastTrack = null
        _state.value = LyricsUiState.Idle
    }

    private fun clearTrackCache() {
        providerCache.clear()
        searchCacheKey = null
        searchSequence++
        searchJob?.cancel()
        searchJob = null
        _searchResults.value = emptyList()
        _isSearchLoading.value = false
    }

    private fun cacheProvider(lyrics: Lyrics) {
        if (!lyrics.hasUsableText()) return
        val key = candidateKey(lyrics)
        providerCache.remove(key)
        providerCache[key] = lyrics
        while (providerCache.size > MAX_CACHED_PROVIDERS) {
            val oldest = providerCache.keys.firstOrNull() ?: break
            providerCache.remove(oldest)
        }
    }

    private fun providerSnapshot(): Map<String, Lyrics> = providerCache.toMap()

    private fun candidateKey(lyrics: Lyrics): String {
        val provider = lyrics.source.removePrefix("Saved ").trim().lowercase()
        val script = when {
            !lyrics.syncedLyrics.isNullOrBlank() -> lyrics.syncedLyrics.hashCode()
            else -> lyrics.plainLyrics.hashCode()
        }
        return "$provider|${lyrics.id}|${lyrics.trackName.trim().lowercase()}|$script"
    }

    companion object {
        private const val TAG = "LyricsController"
        private const val MAX_CACHED_PROVIDERS = 40
    }
}
