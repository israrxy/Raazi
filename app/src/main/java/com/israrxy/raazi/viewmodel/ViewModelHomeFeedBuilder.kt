package com.israrxy.raazi.viewmodel

import androidx.lifecycle.viewModelScope
import com.israrxy.raazi.data.db.HomeInteractionEntity
import com.israrxy.raazi.model.MusicItem
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

    val recentlyPlayedIds = _keepListening.value.take(12).mapTo(mutableSetOf()) { it.id }
    val ranking = HomeFeedRanking(
        dismissedIds = dismissedHomeItemIds,
        artistAffinity = artistAffinity,
        recentlyPlayedIds = recentlyPlayedIds,
        blockedArtistKeys = homeTaste.profile?.dismissedArtistKeys.orEmpty()
    )
    val addedTitles = mutableSetOf<String>()
    val usedCollections = mutableSetOf<String>()

    fun idOf(item: HomeFeedItem): String? = when (item) {
        is HomeFeedItem.Music -> item.item.id
        is HomeFeedItem.YouTube -> homeYouTubeItemId(item.item)
        is HomeFeedItem.PlaylistResult -> item.playlist.id
        is HomeFeedItem.Mood -> null
    }

    fun addSection(
        id: String,
        title: String,
        type: HomeSectionType,
        source: String,
        items: List<HomeFeedItem>,
        actionLabel: String? = null,
        avoidIds: Set<String> = emptySet(),
        floor: Int = 0
    ) {
        if (items.isEmpty()) return
        if (!addedTitles.add(title.lowercase())) return
        val filtered = if (avoidIds.isEmpty()) items else items.filterNot { idOf(it) in avoidIds }
        val finalItems = if (floor > 0 && filtered.size < floor) items else filtered
        if (finalItems.isEmpty()) return
        sections += HomeSection(
            id = id,
            title = title,
            type = type,
            sourceType = source,
            actionLabel = actionLabel,
            items = finalItems
        )
    }

    fun addMusicSection(
        id: String,
        title: String,
        type: HomeSectionType,
        source: String,
        items: List<MusicItem>,
        actionLabel: String? = null,
        avoidIds: Set<String> = emptySet(),
        floor: Int = 0
    ) {
        addSection(
            id = id,
            title = title,
            type = type,
            source = source,
            items = items.map(HomeFeedItem::Music),
            actionLabel = actionLabel,
            avoidIds = avoidIds,
            floor = floor
        )
    }

    fun addGeneratedShelf(section: HomeSection) {
        if (section.items.isEmpty()) return
        if (!addedTitles.add(section.title.lowercase())) return
        val kept = section.items.mapNotNull { item ->
            when (item) {
                is HomeFeedItem.Music -> ranking.familiarMusic(listOf(item.item), 1)
                    .firstOrNull()
                    ?.let(HomeFeedItem::Music)
                is HomeFeedItem.PlaylistResult -> {
                    val key = "${item.playlist.id}:${item.playlist.title.lowercase()}"
                    if (usedCollections.add(key)) item else null
                }
                else -> item
            }
        }
        val finalItems = if (kept.size >= 3) kept else section.items
        if (finalItems.isEmpty()) return
        sections += section.copy(
            items = finalItems,
            sourceType = HomeInteractionEntity.SOURCE_LOCAL
        )
    }

    val taste = homeTaste.profile
    if (taste != null && taste.isColdStart && !homeTaste.builderDismissed.value) {
        sections += HomeSection(
            id = "tastebuilder_card",
            title = "Pick artists you like",
            type = HomeSectionType.TASTEBUILDER_CARD,
            sourceType = HomeInteractionEntity.SOURCE_LOCAL,
            subtitle = "Choose at least three artists to build your feed"
        )
    }

    val generatedShelves = homeTaste.shelves.value
    val hasGeneratedShelves = generatedShelves.isNotEmpty()

    if (hasGeneratedShelves) {
        generatedShelves
            .filter { it.type == HomeSectionType.LISTEN_AGAIN }
            .forEach(::addGeneratedShelf)
    } else {
        val activeTrack = _playbackState.value.currentTrack
        val orderedHistory = if (activeTrack != null) {
            listOf(activeTrack) + _keepListening.value.filter { it.id != activeTrack.id }
        } else {
            _keepListening.value
        }
        addMusicSection(
            id = "keep_listening",
            title = "Keep Listening",
            type = HomeSectionType.KEEP_LISTENING,
            source = HomeInteractionEntity.SOURCE_LOCAL,
            items = ranking.familiarMusic(orderedHistory, 14),
            actionLabel = "Refresh"
        )
    }

    // The existing Quick Picks engine stays the source for this shelf.
    addMusicSection(
        id = "quick_picks",
        title = "Quick Picks",
        type = HomeSectionType.QUICK_PICKS,
        source = HomeInteractionEntity.SOURCE_LOCAL,
        items = ranking.familiarMusic(_quickPicks.value, 20),
        actionLabel = "Refresh"
    )

    if (hasGeneratedShelves) {
        generatedShelves
            .filterNot { it.type == HomeSectionType.LISTEN_AGAIN }
            .forEach(::addGeneratedShelf)
    } else {
        // Legacy personalization is the fallback if every new generator failed.
        val allowLegacyRecommendations = personalizedSections.isEmpty()
        _similarRecommendations.value.forEachIndexed { index, recommendation ->
            val isArtistRail = recommendation.title.startsWith("More from", ignoreCase = true)
            if (!isArtistRail && !allowLegacyRecommendations) return@forEachIndexed

            val items = mutableListOf<HomeFeedItem>()
            for (item in recommendation.items) {
                if (items.size >= 14) break
                when (item) {
                    is MusicPlayerViewModel.RecommendationItem.FromMusicItem -> {
                        val picked = ranking.familiarMusic(listOf(item.musicItem), 1)
                        if (picked.isNotEmpty()) items += HomeFeedItem.Music(picked.first())
                    }
                    is MusicPlayerViewModel.RecommendationItem.FromYTItem -> {
                        val picked = ranking.discoveryYouTube(listOf(item.ytItem), 1, ::homeYouTubeItemId)
                        if (picked.isNotEmpty()) items += HomeFeedItem.YouTube(picked.first())
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

        val (familiarPersonalized, discoveryPersonalized) = personalizedSections.partition {
            !it.id.startsWith("personalized_trending")
        }
        familiarPersonalized.forEach { section ->
            val items = section.items.mapNotNull { (it as? HomeFeedItem.Music)?.item }
            addMusicSection(
                id = section.id,
                title = section.title,
                type = section.type,
                source = section.sourceType,
                items = ranking.familiarMusic(items, 16)
            )
        }

        addMusicSection(
            id = "forgotten_favorites",
            title = "Rediscover",
            type = HomeSectionType.FORGOTTEN_FAVORITES,
            source = HomeInteractionEntity.SOURCE_LOCAL,
            items = ranking.familiarMusic(rankHomeMusicItems(_forgottenFavorites.value), 10)
        )

        discoveryPersonalized.forEach { section ->
            val items = section.items.mapNotNull { (it as? HomeFeedItem.Music)?.item }
            addMusicSection(
                id = section.id,
                title = section.title,
                type = section.type,
                source = section.sourceType,
                items = ranking.discoveryMusic(items, 16)
            )
        }
    }

    // Backfill never repeats a track already shown in a personal shelf, but a
    // shelf is never cut below its floor of six just to satisfy dedupe.
    val personalIds = buildSet {
        sections.forEach { section ->
            section.items.forEach { item -> idOf(item)?.let { add(it) } }
        }
    }

    addSection(
        id = "community_playlists",
        title = "Community Playlists",
        type = HomeSectionType.RECOMMENDATION,
        source = HomeInteractionEntity.SOURCE_YOUTUBE,
        items = ranking.discoveryYouTube(_communityPlaylists.value, 20, ::homeYouTubeItemId)
            .map(HomeFeedItem::YouTube),
        avoidIds = personalIds,
        floor = 6
    )

    _allCharts.value.entries
        .sortedByDescending { (_, items) -> items.size }
        .take(3)
        .forEachIndexed { index, (chartTitle, chartItems) ->
            val ranked = ranking.rankMusic(chartItems)
            addMusicSection(
                id = "chart_$index",
                title = chartTitle,
                type = HomeSectionType.RECOMMENDATION,
                source = HomeInteractionEntity.SOURCE_EXPLORE,
                items = ranking.discoveryMusic(ranked, 16),
                avoidIds = personalIds,
                floor = 6
            )
        }

    _homePage.value?.sections.orEmpty().forEachIndexed { index, section ->
        val items = ranking.personalizedShelf(
            items = section.items,
            limit = 14,
            floor = 6,
            idOf = ::homeYouTubeItemId
        )
        addSection(
            id = "youtube_$index",
            title = section.title,
            type = HomeSectionType.YOUTUBE_RAIL,
            source = HomeInteractionEntity.SOURCE_YOUTUBE,
            items = items.map(HomeFeedItem::YouTube),
            avoidIds = personalIds,
            floor = 6
        )
    }

    _explorePage.value?.sections.orEmpty().forEachIndexed { index, section ->
        if (section.title.isBlank()) return@forEachIndexed
        val items = ranking.personalizedShelf(
            items = section.items,
            limit = 14,
            floor = 6,
            idOf = ::homeYouTubeItemId
        )
        addSection(
            id = "explore_$index",
            title = section.title,
            type = HomeSectionType.YOUTUBE_RAIL,
            source = HomeInteractionEntity.SOURCE_EXPLORE,
            items = items.map(HomeFeedItem::YouTube),
            avoidIds = personalIds,
            floor = 6
        )
    }

    _explorePage.value?.newReleaseAlbums?.takeIf { it.isNotEmpty() }?.let { albums ->
        addSection(
            id = "new_releases",
            title = "New Releases",
            type = HomeSectionType.NEW_RELEASES,
            source = HomeInteractionEntity.SOURCE_EXPLORE,
            items = ranking.discoveryYouTube(ranking.rankYouTube(albums, ::homeYouTubeItemId), 12, ::homeYouTubeItemId)
                .map(HomeFeedItem::YouTube),
            avoidIds = personalIds,
            floor = 6
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
        addSection(
            id = "mood_playlists",
            title = "$selectedMood Playlists",
            type = HomeSectionType.MOOD_PLAYLISTS,
            source = HomeInteractionEntity.SOURCE_EXPLORE,
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
