package com.israrxy.raazi.viewmodel

import android.util.Log
import androidx.lifecycle.viewModelScope
import com.israrxy.raazi.data.account.YouTubeAccountSession
import com.israrxy.raazi.data.db.HomeInteractionEntity
import com.israrxy.raazi.data.engine.QuickPicksEngine
import com.israrxy.raazi.model.MusicItem
import com.israrxy.raazi.model.MusicContentType
import com.israrxy.raazi.service.convertYTItemToMusicItem
import com.zionhuang.innertube.YouTube
import com.zionhuang.innertube.models.SongItem
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

internal fun MusicPlayerViewModel.loadHomeContentImpl() {
    viewModelScope.launch {
        _isLoading.value = true
        _isHomeFeedLoading.value = true
        _error.value = null
        _homeFeedState.value = _homeFeedState.value.copy(isLoading = true, errorMessage = null)
        try {
            loadHomeInteractionSignals()
            refreshPersonalizedSections()
            loadDiscoverySources()

            launch {
                try {
                    _keepListening.value = repository.playbackHistory.first().take(30)
                } catch (e: Exception) {
                    Log.e("MusicVM", "Error loading keep listening", e)
                }
            }

            launch {
                try {
                    _forgottenFavorites.value = repository.getForgottenFavorites().first()
                } catch (e: Exception) {
                    Log.e("MusicVM", "Error loading forgotten favorites", e)
                }
            }

            // 1. Immediately hydrate Quick Picks from local Room storage (0 ms) so the user never sees blank space or generic picks
            launch {
                try {
                    if (_quickPicks.value.isEmpty()) {
                        val cached = (repository.quickPicks.first() + repository.favoriteTracks.first())
                            .filter { it.id.isNotBlank() && it.id !in dismissedHomeItemIds }
                            .distinctBy { it.id }
                            .take(20)
                        if (cached.isNotEmpty() && _quickPicks.value.isEmpty()) {
                            _quickPicks.value = cached
                            rebuildHomeFeedState()
                        }
                    }
                } catch (e: Exception) {
                    Log.d("MusicVM", "Instant quick picks pre-hydration skipped: ${e.message}")
                }

                // 2. Launch personalized Quick Picks generation (parallel discovery blend)
                try {
                    val engine = QuickPicksEngine(repository, musicDao, musicExtractor)
                    val generated = engine.generateQuickPicks(dismissedItemIds = dismissedHomeItemIds)
                    if (generated.isNotEmpty()) {
                        _quickPicks.value = generated
                        rebuildHomeFeedState()
                    }
                } catch (e: Exception) {
                    Log.e("MusicVM", "Error generating personalized Quick Picks on startup", e)
                }
            }

            try {
                YouTubeAccountSession.ensureBootstrapped(settingsDataStore)
            } catch (e: Exception) {
                Log.e("MusicVM", "YouTube session bootstrap failed", e)
            }
            try {
                val chipParams = _selectedInnerTubeChip.value?.endpoint?.params
                val homeResult = withTimeoutOrNull(8000L) {
                    YouTube.home(params = chipParams)
                }
                if (homeResult != null && homeResult.isSuccess) {
                    var finalPage = homeResult.getOrThrow()
                    val firstContinuation = finalPage.continuation
                    if (finalPage.sections.size < 8 && firstContinuation != null) {
                        YouTube.home(continuation = firstContinuation).onSuccess { nextPage ->
                            finalPage = finalPage.copy(
                                sections = finalPage.sections + nextPage.sections,
                                continuation = nextPage.continuation
                            )
                        }
                    }
                    _homePage.value = finalPage
                    cachedDefaultHomePage = finalPage
                    _error.value = null
                }
            } catch (e: Exception) {
                Log.e("MusicVM", "Error loading InnerTube home", e)
            }
        } catch (e: Exception) {
            Log.e("MusicVM", "Error loading home", e)
            if (isOnline.value && _keepListening.value.isEmpty() && _quickPicks.value.isEmpty()) {
                _error.value = "Failed to load content: ${e.message}"
            }
        } finally {
            _isLoading.value = false
            _isHomeFeedLoading.value = false
            rebuildHomeFeedState()
        }

        launch {
            try {
                YouTube.explore().onSuccess { page ->
                    _explorePage.value = page
                    rebuildHomeFeedState()
                }
            } catch (e: Exception) {
                Log.e("MusicVM", "Error loading explore page", e)
            }
        }

        launch {
            try {
                loadSimilarRecommendations()
                rebuildHomeFeedState()
            } catch (e: Exception) {
                Log.e("MusicVM", "Error loading similar recommendations", e)
            }
        }
    }
}

internal fun MusicPlayerViewModel.shuffleHomeFeedImpl(): Boolean {
    val tracks = _homeFeedState.value.sections
        .flatMap { section -> section.items.flatMap { item ->
            when (item) {
                is HomeFeedItem.Music -> listOf(item.item)
                is HomeFeedItem.YouTube -> (item.item as? SongItem)
                    ?.let { convertYTItemToMusicItem(it, MusicContentType.SONG) }
                    ?.let(::listOf).orEmpty()
                is HomeFeedItem.PlaylistResult -> item.playlist.items
                is HomeFeedItem.Mood -> emptyList()
            }
        } }
        .distinctBy { it.id }
    if (tracks.isEmpty()) return false
    playPlaylist(tracks.shuffled(), 0)
    return true
}

internal fun MusicPlayerViewModel.refreshHomeContentImpl() {
    viewModelScope.launch {
        rebuildHomeFeedState(isRefreshing = true)
        try {
            loadHomeInteractionSignals()
            advanceHomeShelfSeed()
            refreshPersonalizedSections(force = true)
            loadDiscoverySources(force = true)

            launch {
                try {
                    val engine = QuickPicksEngine(repository, musicDao, musicExtractor)
                    val refreshedPicks = engine.generateQuickPicks(
                        dismissedItemIds = dismissedHomeItemIds,
                        forceRefresh = true
                    )
                    if (refreshedPicks.isNotEmpty()) {
                        _quickPicks.value = refreshedPicks
                        rebuildHomeFeedState()
                    }
                } catch (e: Exception) {
                    Log.e("MusicVM", "Error refreshing Quick Picks", e)
                }
            }

            val history = repository.playbackHistory.first().take(30)
            _keepListening.value = history

            val forgotten = repository.getForgottenFavorites().first()
            _forgottenFavorites.value = forgotten

            launch {
                try {
                    loadSimilarRecommendations()
                    rebuildHomeFeedState()
                } catch (e: Exception) {
                    Log.e("MusicVM", "Error refreshing similar recommendations", e)
                }
            }

            launch {
                try {
                    YouTube.explore().onSuccess { page ->
                        _explorePage.value = page
                        rebuildHomeFeedState()
                    }
                } catch (e: Exception) {
                    Log.e("MusicVM", "Error refreshing explore page", e)
                }
            }

            try {
                YouTubeAccountSession.ensureBootstrapped(settingsDataStore)
                val chipParams = _selectedInnerTubeChip.value?.endpoint?.params
                YouTube.home(params = chipParams).onSuccess { page ->
                    _homePage.value = page
                }
            } catch (e: Exception) {
                Log.e("MusicVM", "Error refreshing YouTube home", e)
            }

            rebuildHomeFeedState(isRefreshing = false)
        } catch (e: Exception) {
            Log.e("MusicVM", "Error refreshing home content", e)
            rebuildHomeFeedState(isRefreshing = false)
        }
    }
}

internal fun MusicPlayerViewModel.refreshHomeSectionImpl(sectionId: String) {
    viewModelScope.launch {
        repository.recordHomeInteraction(
            itemId = sectionId,
            sectionId = sectionId,
            action = HomeInteractionEntity.ACTION_REFRESH,
            sourceType = HomeInteractionEntity.SOURCE_LOCAL
        )
    }
    if (sectionId == "quick_picks") {
        viewModelScope.launch {
            try {
                val engine = QuickPicksEngine(repository, musicDao, musicExtractor)
                val refreshed = engine.generateQuickPicks(
                    dismissedItemIds = dismissedHomeItemIds,
                    forceRefresh = true
                )
                if (refreshed.isNotEmpty()) {
                    _quickPicks.value = refreshed
                    rebuildHomeFeedState()
                } else {
                    refreshHomeContentImpl()
                }
            } catch (e: Exception) {
                refreshHomeContentImpl()
            }
        }
    } else if (sectionId == "keep_listening") {
        viewModelScope.launch {
            try {
                val history = repository.playbackHistory.first().take(30)
                _keepListening.value = history
                rebuildHomeFeedState()
            } catch (e: Exception) {
                Log.e("MusicVM", "Error refreshing keep listening", e)
            }
        }
    } else {
        refreshHomeContentImpl()
    }
}

internal fun MusicPlayerViewModel.loadMoreSectionsImpl() {
    val continuation = _homePage.value?.continuation
    if (continuation == null || _isLoadingMore.value) return

    viewModelScope.launch {
        _isLoadingMore.value = true
        try {
            YouTube.home(continuation).onSuccess { nextPage ->
                _homePage.value = _homePage.value?.copy(
                    sections = _homePage.value?.sections.orEmpty() + nextPage.sections,
                    continuation = nextPage.continuation
                )
                rebuildHomeFeedState()
            }.onFailure {
                Log.e("MusicVM", "Pagination failed", it)
                _homePage.value = _homePage.value?.copy(continuation = null)
            }
        } catch (e: Exception) {
            Log.e("MusicVM", "Error loading more sections", e)
            _homePage.value = _homePage.value?.copy(continuation = null)
        } finally {
            _isLoadingMore.value = false
        }
    }
}
