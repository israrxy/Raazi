package com.israrxy.raazi.viewmodel

import android.util.Log
import androidx.lifecycle.viewModelScope
import com.israrxy.raazi.data.db.HomeInteractionEntity
import com.israrxy.raazi.model.MusicItem
import com.zionhuang.innertube.YouTube
import com.zionhuang.innertube.models.AlbumItem
import com.zionhuang.innertube.models.ArtistItem
import com.zionhuang.innertube.models.PlaylistItem
import com.zionhuang.innertube.models.SongItem
import com.zionhuang.innertube.models.YTItem
import com.zionhuang.innertube.pages.HomePage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

internal fun MusicPlayerViewModel.loadHomeContentImpl() {
    viewModelScope.launch {
        _isLoading.value = true
        _error.value = null
        _homeFeedState.value = _homeFeedState.value.copy(isLoading = true, errorMessage = null)
        try {
            loadHomeInteractionSignals()
            launch {
                try {
                    val quickPicks = repository.quickPicks.first().shuffled().take(50)
                    _quickPicks.value = quickPicks
                    rebuildHomeFeedState()
                } catch (e: Exception) {
                    Log.e("MusicVM", "Error loading quick picks", e)
                }
            }

            launch {
                try {
                    val history = repository.playbackHistory.first().take(30)
                    _keepListening.value = history
                    rebuildHomeFeedState()
                } catch (e: Exception) {
                    Log.e("MusicVM", "Error loading keep listening", e)
                }
            }

            launch {
                try {
                    val forgotten = repository.getForgottenFavorites().first()
                    _forgottenFavorites.value = forgotten
                    rebuildHomeFeedState()
                } catch (e: Exception) {
                    Log.e("MusicVM", "Error loading forgotten favorites", e)
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

            launch {
                try {
                    YouTube.explore().onSuccess { page ->
                        _explorePage.value = page
                        rebuildHomeFeedState()
                    }.onFailure {
                        Log.e("MusicVM", "Explore page failed", it)
                    }
                } catch (e: Exception) {
                    Log.e("MusicVM", "Error loading explore page", e)
                }
            }

            try {
                val chipParams = _selectedInnerTubeChip.value?.endpoint?.params
                YouTube.home(params = chipParams).onSuccess { page ->
                    var finalPage = page
                    val firstContinuation = page.continuation
                    if (page.sections.size < 6 && firstContinuation != null) {
                        YouTube.home(continuation = firstContinuation).onSuccess { nextPage ->
                            finalPage = page.copy(
                                sections = page.sections + nextPage.sections,
                                continuation = nextPage.continuation
                            )
                        }
                    }
                    _homePage.value = finalPage
                    _error.value = null
                    rebuildHomeFeedState()
                }.onFailure {
                    Log.e("MusicVM", "InnerTube home failed", it)
                    if (isOnline.value && _keepListening.value.isEmpty() && _quickPicks.value.isEmpty()) {
                        _error.value = "Failed to load home feed"
                    }
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
            rebuildHomeFeedState()
        }
    }
}

internal fun MusicPlayerViewModel.refreshHomeContentImpl() {
    viewModelScope.launch {
        rebuildHomeFeedState(isRefreshing = true)
        try {
            val quickPicks = repository.quickPicks.first().shuffled().take(50)
            _quickPicks.value = quickPicks

            val history = repository.playbackHistory.first().take(30)
            _keepListening.value = history

            val forgotten = repository.getForgottenFavorites().first()
            _forgottenFavorites.value = forgotten

            loadHomeInteractionSignals()
            rebuildHomeFeedState(isRefreshing = false)
        } catch (e: Exception) {
            Log.e("MusicVM", "Error refreshing home content", e)
            rebuildHomeFeedState(isRefreshing = false)
        }
    }
}

internal suspend fun MusicPlayerViewModel.loadHomeInteractionSignals() {
    dismissedHomeItemIds = repository.getDismissedHomeItemIds()
    engagedHomeItemIds = repository.getEngagedHomeItemIds().distinct()
}

internal fun MusicPlayerViewModel.rankHomeMusicItems(items: List<MusicItem>): List<MusicItem> {
    val engagedOrder = engagedHomeItemIds.withIndex().associate { it.value to it.index }
    return items
        .filter { it.id !in dismissedHomeItemIds }
        .sortedWith(
            compareBy<MusicItem> { engagedOrder[it.id] ?: Int.MAX_VALUE }
                .thenBy { it.title.lowercase() }
        )
}

internal fun MusicPlayerViewModel.rebuildHomeFeedState(isRefreshing: Boolean = _homeFeedState.value.isRefreshing) {
    val sections = mutableListOf<HomeSection>()
    val activeCategoryChip = _selectedInnerTubeChip.value

    sections += HomeSection(
        id = "home_status",
        title = activeCategoryChip?.title ?: "Home",
        type = HomeSectionType.STATUS,
        sourceType = HomeInteractionEntity.SOURCE_LOCAL,
        actionLabel = "Refresh"
    )

    if (activeCategoryChip != null) {
        sections += _categorySections.value
        _homeFeedState.value = HomeFeedUiState(
            isLoading = _isCategoryLoading.value && _categorySections.value.isEmpty(),
            isRefreshing = isRefreshing || _isCategoryLoading.value,
            sections = sections,
            errorMessage = _error.value
        )
        return
    }

    val activeTrack = _playbackState.value.currentTrack
    val orderedHistory = if (activeTrack != null) {
        listOf(activeTrack) + _keepListening.value.filter { it.id != activeTrack.id }
    } else {
        _keepListening.value
    }
    val keepListeningItems = orderedHistory
        .filter { it.id !in dismissedHomeItemIds }
        .distinctBy { it.id }
        .take(14)
    if (keepListeningItems.isNotEmpty()) {
        sections += HomeSection(
            id = "keep_listening",
            title = "Keep Listening",
            type = HomeSectionType.KEEP_LISTENING,
            sourceType = HomeInteractionEntity.SOURCE_LOCAL,
            actionLabel = "Refresh",
            items = keepListeningItems.map(HomeFeedItem::Music)
        )
    }

    val quickPickItems = rankHomeMusicItems(_quickPicks.value).take(12)
    if (quickPickItems.isNotEmpty()) {
        sections += HomeSection(
            id = "quick_picks",
            title = "Quick Picks",
            type = HomeSectionType.QUICK_PICKS,
            sourceType = HomeInteractionEntity.SOURCE_LOCAL,
            actionLabel = "Refresh",
            items = quickPickItems.map(HomeFeedItem::Music)
        )
    }

    val forgottenItems = rankHomeMusicItems(_forgottenFavorites.value).take(10)
    if (forgottenItems.isNotEmpty()) {
        sections += HomeSection(
            id = "forgotten_favorites",
            title = "Rediscover",
            type = HomeSectionType.FORGOTTEN_FAVORITES,
            sourceType = HomeInteractionEntity.SOURCE_LOCAL,
            items = forgottenItems.map(HomeFeedItem::Music)
        )
    }

    _similarRecommendations.value.forEachIndexed { index, recommendation ->
        val items = recommendation.items
            .mapNotNull { item ->
                when (item) {
                    is MusicPlayerViewModel.RecommendationItem.FromMusicItem ->
                        item.musicItem.takeIf { it.id !in dismissedHomeItemIds }?.let(HomeFeedItem::Music)
                    is MusicPlayerViewModel.RecommendationItem.FromYTItem ->
                        item.ytItem.takeIf { homeYouTubeItemId(it) !in dismissedHomeItemIds }?.let(HomeFeedItem::YouTube)
                }
            }
            .take(14)

        if (items.isNotEmpty()) {
            sections += HomeSection(
                id = "recommendation_$index",
                title = recommendation.title,
                type = HomeSectionType.RECOMMENDATION,
                sourceType = HomeInteractionEntity.SOURCE_YOUTUBE,
                items = items
            )
        }
    }

    _explorePage.value?.newReleaseAlbums?.takeIf { it.isNotEmpty() }?.let { albums ->
        sections += HomeSection(
            id = "new_releases",
            title = "New Releases",
            type = HomeSectionType.NEW_RELEASES,
            sourceType = HomeInteractionEntity.SOURCE_EXPLORE,
            items = albums
                .filter { homeYouTubeItemId(it) !in dismissedHomeItemIds }
                .take(12)
                .map(HomeFeedItem::YouTube)
        )
    }

    _explorePage.value?.moodAndGenres?.takeIf { it.isNotEmpty() }?.let { moods ->
        sections += HomeSection(
            id = "moods",
            title = "Moods & Genres",
            type = HomeSectionType.MOODS,
            sourceType = HomeInteractionEntity.SOURCE_EXPLORE,
            items = moods.take(12).map { HomeFeedItem.Mood(it.title) }
        )
    }

    val selectedMood = _selectedChip.value
    if (selectedMood != null) {
        sections += HomeSection(
            id = "mood_playlists",
            title = "$selectedMood Playlists",
            type = HomeSectionType.MOOD_PLAYLISTS,
            sourceType = HomeInteractionEntity.SOURCE_EXPLORE,
            isLoading = _filteredPlaylists.value == null,
            items = _filteredPlaylists.value.orEmpty()
                .filter { it.id !in dismissedHomeItemIds }
                .take(12)
                .map(HomeFeedItem::PlaylistResult)
        )
    }

    _homePage.value?.sections.orEmpty().forEachIndexed { index, section ->
        val items = section.items
            .filter { homeYouTubeItemId(it) !in dismissedHomeItemIds }
            .take(14)
            .map(HomeFeedItem::YouTube)

        if (items.isNotEmpty()) {
            sections += HomeSection(
                id = "youtube_$index",
                title = section.title,
                type = HomeSectionType.YOUTUBE_RAIL,
                sourceType = HomeInteractionEntity.SOURCE_YOUTUBE,
                items = items
            )
        }
    }

    _homeFeedState.value = HomeFeedUiState(
        isLoading = _isLoading.value && sections.size <= 1,
        isRefreshing = isRefreshing,
        sections = sections,
        errorMessage = _error.value
    )
}

internal fun MusicPlayerViewModel.homeYouTubeItemId(item: YTItem): String {
    return when (item) {
        is SongItem -> item.id
        is AlbumItem -> item.id
        is ArtistItem -> item.id
        is PlaylistItem -> item.id
        else -> item.hashCode().toString()
    }
}

internal fun MusicPlayerViewModel.recordHomeInteractionImpl(
    itemId: String,
    sectionId: String,
    action: String,
    sourceType: String
) {
    viewModelScope.launch {
        repository.recordHomeInteraction(itemId, sectionId, action, sourceType)
        loadHomeInteractionSignals()
        rebuildHomeFeedState()
    }
}

internal fun MusicPlayerViewModel.dismissHomeItemImpl(itemId: String, sectionId: String, sourceType: String) {
    viewModelScope.launch {
        repository.recordHomeInteraction(
            itemId = itemId,
            sectionId = sectionId,
            action = HomeInteractionEntity.ACTION_DISMISS,
            sourceType = sourceType
        )
        loadHomeInteractionSignals()
        rebuildHomeFeedState()
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
    refreshHomeContentImpl()
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
            }
        } catch (e: Exception) {
            Log.e("MusicVM", "Error loading more sections", e)
        } finally {
            _isLoadingMore.value = false
        }
    }
}
