package com.israrxy.raazi.viewmodel

import androidx.lifecycle.viewModelScope
import com.israrxy.raazi.data.db.HomeInteractionEntity
import com.israrxy.raazi.model.MusicItem
import com.zionhuang.innertube.YouTube
import com.zionhuang.innertube.models.AlbumItem
import com.zionhuang.innertube.models.ArtistItem
import com.zionhuang.innertube.models.PlaylistItem
import com.zionhuang.innertube.models.SongItem
import com.zionhuang.innertube.models.YTItem
import kotlinx.coroutines.launch

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
            isLoading = false,
            isRefreshing = _isCategoryLoading.value || isRefreshing,
            sections = sections,
            errorMessage = _error.value
        )
        return
    }

    // Global cross-section dedupe so the same track never appears twice in the feed.
    val seenIds = mutableSetOf<String>()

    fun takeMusic(items: List<MusicItem>, limit: Int): List<HomeFeedItem> {
        val result = mutableListOf<HomeFeedItem>()
        for (item in items) {
            if (result.size >= limit) break
            if (item.id.isBlank() || item.id in seenIds || item.id in dismissedHomeItemIds) continue
            seenIds += item.id
            result += HomeFeedItem.Music(item)
        }
        return result
    }

    fun takeYouTube(items: List<YTItem>, limit: Int): List<HomeFeedItem> {
        val result = mutableListOf<HomeFeedItem>()
        for (item in items) {
            if (result.size >= limit) break
            val id = homeYouTubeItemId(item)
            if (id in seenIds || id in dismissedHomeItemIds) continue
            seenIds += id
            result += HomeFeedItem.YouTube(item)
        }
        return result
    }

    fun addSection(id: String, title: String, type: HomeSectionType, source: String, items: List<HomeFeedItem>, actionLabel: String? = null) {
        if (items.isNotEmpty()) {
            sections += HomeSection(
                id = id,
                title = title,
                type = type,
                sourceType = source,
                actionLabel = actionLabel,
                items = items
            )
        }
    }

    val activeTrack = _playbackState.value.currentTrack
    val orderedHistory = if (activeTrack != null) {
        listOf(activeTrack) + _keepListening.value.filter { it.id != activeTrack.id }
    } else {
        _keepListening.value
    }
    val keepListeningItems = takeMusic(orderedHistory, 14)
    if (keepListeningItems.isNotEmpty()) {
        sections += HomeSection(
            id = "keep_listening",
            title = "Keep Listening",
            type = HomeSectionType.KEEP_LISTENING,
            sourceType = HomeInteractionEntity.SOURCE_LOCAL,
            actionLabel = "Refresh",
            items = keepListeningItems
        )
    }

    val quickPickItems = takeMusic(_quickPicks.value, 20)
    if (quickPickItems.isNotEmpty()) {
        sections += HomeSection(
            id = "quick_picks",
            title = "Quick Picks",
            type = HomeSectionType.QUICK_PICKS,
            sourceType = HomeInteractionEntity.SOURCE_LOCAL,
            actionLabel = "Refresh",
            items = quickPickItems
        )
    }

    // "More from <artist>" rails stay; the generic legacy rails are replaced by
    // the local personalization engine below.
    val allowLegacyRecommendations = personalizedSections.isEmpty()
    _similarRecommendations.value.forEachIndexed { index, recommendation ->
        val isArtistRail = recommendation.title.startsWith("More from", ignoreCase = true)
        if (!isArtistRail && !allowLegacyRecommendations) return@forEachIndexed

        val items = mutableListOf<HomeFeedItem>()
        for (item in recommendation.items) {
            if (items.size >= 14) break
            when (item) {
                is MusicPlayerViewModel.RecommendationItem.FromMusicItem -> {
                    val music = item.musicItem
                    if (music.id.isBlank() || music.id in seenIds || music.id in dismissedHomeItemIds) continue
                    seenIds += music.id
                    items += HomeFeedItem.Music(music)
                }
                is MusicPlayerViewModel.RecommendationItem.FromYTItem -> {
                    val ytItem = item.ytItem
                    val id = homeYouTubeItemId(ytItem)
                    if (id in seenIds || id in dismissedHomeItemIds) continue
                    seenIds += id
                    items += HomeFeedItem.YouTube(ytItem)
                }
            }
        }
        addSection(
            id = "recommendation_$index",
            title = recommendation.title,
            type = HomeSectionType.RECOMMENDATION,
            source = HomeInteractionEntity.SOURCE_YOUTUBE,
            items = items
        )
    }

    personalizedSections.forEach { section ->
        val items = mutableListOf<HomeFeedItem>()
        for (feedItem in section.items) {
            if (items.size >= 16) break
            val music = (feedItem as? HomeFeedItem.Music)?.item ?: continue
            if (music.id in seenIds || music.id in dismissedHomeItemIds) continue
            seenIds += music.id
            items += HomeFeedItem.Music(music)
        }
        if (items.size >= 3) {
            addSection(
                id = section.id,
                title = section.title,
                type = section.type,
                source = section.sourceType,
                items = items
            )
        }
    }

    val forgottenItems = takeMusic(rankHomeMusicItems(_forgottenFavorites.value), 10)
    if (forgottenItems.isNotEmpty()) {
        sections += HomeSection(
            id = "forgotten_favorites",
            title = "Rediscover",
            type = HomeSectionType.FORGOTTEN_FAVORITES,
            sourceType = HomeInteractionEntity.SOURCE_LOCAL,
            items = forgottenItems
        )
    }

    // Anonymous YouTube shelves are intentionally skipped: for Drive-only users
    // they are generic and would bury the local personalized rails.
    if (YouTube.cookie?.contains("SAPISID") == true) {
        _homePage.value?.sections.orEmpty().forEachIndexed { index, section ->
            addSection(
                id = "youtube_$index",
                title = section.title,
                type = HomeSectionType.YOUTUBE_RAIL,
                source = HomeInteractionEntity.SOURCE_YOUTUBE,
                items = takeYouTube(section.items, 14)
            )
        }
    }

    _explorePage.value?.newReleaseAlbums?.takeIf { it.isNotEmpty() }?.let { albums ->
        addSection(
            id = "new_releases",
            title = "New Releases",
            type = HomeSectionType.NEW_RELEASES,
            source = HomeInteractionEntity.SOURCE_EXPLORE,
            items = takeYouTube(albums, 12)
        )
    }

    _explorePage.value?.moodAndGenres?.takeIf { it.isNotEmpty() }?.let { moods ->
        addSection(
            id = "moods",
            title = "Moods & Genres",
            type = HomeSectionType.MOODS,
            source = HomeInteractionEntity.SOURCE_EXPLORE,
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

    _homeFeedState.value = HomeFeedUiState(
        isLoading = _isHomeFeedLoading.value && !isRefreshing,
        isRefreshing = isRefreshing,
        sections = sections,
        errorMessage = _error.value
    )
}
