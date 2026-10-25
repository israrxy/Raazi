package com.israrxy.raazi.viewmodel

import android.util.Log
import androidx.lifecycle.viewModelScope
import com.israrxy.raazi.data.db.HomeInteractionEntity
import com.israrxy.raazi.model.MusicItem
import com.israrxy.raazi.model.Playlist
import com.zionhuang.innertube.YouTube
import com.zionhuang.innertube.models.YTItem
import com.zionhuang.innertube.pages.HomePage
import kotlinx.coroutines.launch

data class HomeFeedUiState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val sections: List<HomeSection> = emptyList(),
    val errorMessage: String? = null
)

data class HomeSection(
    val id: String,
    val title: String,
    val type: HomeSectionType,
    val sourceType: String,
    val items: List<HomeFeedItem> = emptyList(),
    val actionLabel: String? = null,
    val isLoading: Boolean = false
)

enum class HomeSectionType {
    STATUS,
    KEEP_LISTENING,
    QUICK_PICKS,
    FORGOTTEN_FAVORITES,
    RECOMMENDATION,
    NEW_RELEASES,
    MOODS,
    MOOD_PLAYLISTS,
    YOUTUBE_RAIL
}

sealed class HomeFeedItem {
    data class Music(val item: MusicItem) : HomeFeedItem()
    data class YouTube(val item: YTItem) : HomeFeedItem()
    data class Mood(val title: String) : HomeFeedItem()
    data class PlaylistResult(val playlist: Playlist) : HomeFeedItem()
}

internal fun MusicPlayerViewModel.onChipSelectedImpl(chip: String) {
    if (_selectedChip.value == chip) {
        _selectedChip.value = null
        _filteredPlaylists.value = null
        rebuildHomeFeedState()
    } else {
        _selectedChip.value = chip
        _filteredPlaylists.value = null
        rebuildHomeFeedState()
        viewModelScope.launch {
            _isLoading.value = true
            try {
                _filteredPlaylists.value = repository.getMoodPlaylists(chip)
                rebuildHomeFeedState()
            } catch (_: Exception) {
                _error.value = "Failed to load $chip playlists"
                rebuildHomeFeedState()
            } finally {
                _isLoading.value = false
            }
        }
    }
}

internal fun MusicPlayerViewModel.selectFilterChipImpl(chip: HomePage.Chip?) {
    val targetChip = if (chip == null || chip.title.equals("All", ignoreCase = true) || _selectedInnerTubeChip.value?.title == chip.title) {
        null
    } else {
        chip
    }
    _selectedInnerTubeChip.value = targetChip

    if (targetChip == null) {
        _categorySections.value = emptyList()
        _isCategoryLoading.value = false
        rebuildHomeFeedState(isRefreshing = true)
        viewModelScope.launch {
            try {
                val existingChips = _homePage.value?.chips
                YouTube.home(params = null).onSuccess { defaultPage ->
                    _homePage.value = if (defaultPage.chips.isNullOrEmpty() && !existingChips.isNullOrEmpty()) {
                        defaultPage.copy(chips = existingChips)
                    } else {
                        defaultPage
                    }
                }
            } catch (_: Exception) {
            } finally {
                rebuildHomeFeedState(isRefreshing = false)
            }
        }
        return
    }

    _categorySections.value = emptyList()
    _isCategoryLoading.value = true
    rebuildHomeFeedState(isRefreshing = true)

    viewModelScope.launch {
        val builtSections = mutableListOf<HomeSection>()
        val categoryTitle = targetChip.title.trim()
        val lowerTitle = categoryTitle.lowercase()

        try {
            val chipParams = targetChip.endpoint?.params
            if (!chipParams.isNullOrBlank()) {
                runCatching {
                    YouTube.home(params = chipParams).onSuccess { filteredPage ->
                        filteredPage.sections.forEachIndexed { idx, section ->
                            val items = section.items
                                .filter { homeYouTubeItemId(it) !in dismissedHomeItemIds }
                                .take(14)
                                .map(HomeFeedItem::YouTube)
                            if (items.isNotEmpty()) {
                                builtSections += HomeSection(
                                    id = "cat_yt_${lowerTitle}_$idx",
                                    title = section.title,
                                    type = HomeSectionType.YOUTUBE_RAIL,
                                    sourceType = HomeInteractionEntity.SOURCE_YOUTUBE,
                                    items = items
                                )
                            }
                        }
                    }
                }
            }

            when {
                lowerTitle.contains("podcast") -> {
                    runCatching {
                        val topEpisodes = repository.searchMusic("popular podcast episode").items.take(14)
                        if (topEpisodes.isNotEmpty()) {
                            builtSections += HomeSection(
                                id = "cat_podcast_top",
                                title = "Top Podcast Episodes",
                                type = HomeSectionType.QUICK_PICKS,
                                sourceType = HomeInteractionEntity.SOURCE_YOUTUBE,
                                items = topEpisodes.map(HomeFeedItem::Music)
                            )
                        }
                    }
                    runCatching {
                        val podcastShows = repository.getMoodPlaylists("Podcasts").take(12)
                        if (podcastShows.isNotEmpty()) {
                            builtSections += HomeSection(
                                id = "cat_podcast_shows",
                                title = "Featured Podcast Shows",
                                type = HomeSectionType.MOOD_PLAYLISTS,
                                sourceType = HomeInteractionEntity.SOURCE_EXPLORE,
                                items = podcastShows.map(HomeFeedItem::PlaylistResult)
                            )
                        }
                    }
                    runCatching {
                        val talkEpisodes = repository.searchMusic("tech culture stories podcast").items.take(12)
                        if (talkEpisodes.isNotEmpty()) {
                            builtSections += HomeSection(
                                id = "cat_podcast_stories",
                                title = "Stories, Tech & Conversations",
                                type = HomeSectionType.RECOMMENDATION,
                                sourceType = HomeInteractionEntity.SOURCE_YOUTUBE,
                                items = talkEpisodes.map(HomeFeedItem::Music)
                            )
                        }
                    }
                }

                lowerTitle.contains("party") -> {
                    runCatching {
                        val partyHits = repository.searchMusic("party hits dance club anthems").items.take(14)
                        if (partyHits.isNotEmpty()) {
                            builtSections += HomeSection(
                                id = "cat_party_hits",
                                title = "Party Anthems",
                                type = HomeSectionType.QUICK_PICKS,
                                sourceType = HomeInteractionEntity.SOURCE_YOUTUBE,
                                items = partyHits.map(HomeFeedItem::Music)
                            )
                        }
                    }
                    runCatching {
                        val partyPlaylists = repository.getMoodPlaylists("Party").take(12)
                        if (partyPlaylists.isNotEmpty()) {
                            builtSections += HomeSection(
                                id = "cat_party_playlists",
                                title = "Party Mixes & Playlists",
                                type = HomeSectionType.MOOD_PLAYLISTS,
                                sourceType = HomeInteractionEntity.SOURCE_EXPLORE,
                                items = partyPlaylists.map(HomeFeedItem::PlaylistResult)
                            )
                        }
                    }
                    runCatching {
                        val clubRemixes = repository.searchMusic("upbeat party remix club").items.take(12)
                        if (clubRemixes.isNotEmpty()) {
                            builtSections += HomeSection(
                                id = "cat_party_remixes",
                                title = "Club & Remix Energy",
                                type = HomeSectionType.RECOMMENDATION,
                                sourceType = HomeInteractionEntity.SOURCE_YOUTUBE,
                                items = clubRemixes.map(HomeFeedItem::Music)
                            )
                        }
                    }
                }

                else -> {
                    runCatching {
                        val moodTracks = repository.searchMusic("$categoryTitle music hits").items.take(14)
                        if (moodTracks.isNotEmpty()) {
                            builtSections += HomeSection(
                                id = "cat_${lowerTitle}_tracks",
                                title = "Top $categoryTitle Picks",
                                type = HomeSectionType.QUICK_PICKS,
                                sourceType = HomeInteractionEntity.SOURCE_YOUTUBE,
                                items = moodTracks.map(HomeFeedItem::Music)
                            )
                        }
                    }
                    runCatching {
                        val moodPlaylists = repository.getMoodPlaylists(categoryTitle).take(12)
                        if (moodPlaylists.isNotEmpty()) {
                            builtSections += HomeSection(
                                id = "cat_${lowerTitle}_playlists",
                                title = "$categoryTitle Playlists",
                                type = HomeSectionType.MOOD_PLAYLISTS,
                                sourceType = HomeInteractionEntity.SOURCE_EXPLORE,
                                items = moodPlaylists.map(HomeFeedItem::PlaylistResult)
                            )
                        }
                    }
                    runCatching {
                        val moodMix = repository.searchMusic("$categoryTitle vibes mix").items.take(12)
                        if (moodMix.isNotEmpty()) {
                            builtSections += HomeSection(
                                id = "cat_${lowerTitle}_mix",
                                title = "$categoryTitle Essentials",
                                type = HomeSectionType.RECOMMENDATION,
                                sourceType = HomeInteractionEntity.SOURCE_YOUTUBE,
                                items = moodMix.map(HomeFeedItem::Music)
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("MusicVM", "Error loading category feed for $categoryTitle", e)
        } finally {
            _categorySections.value = builtSections
            _isCategoryLoading.value = false
            rebuildHomeFeedState(isRefreshing = false)
        }
    }
}
