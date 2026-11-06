package com.israrxy.raazi.viewmodel

import android.util.Log
import androidx.lifecycle.viewModelScope
import com.israrxy.raazi.data.db.HomeInteractionEntity
import com.israrxy.raazi.model.MusicItem
import com.israrxy.raazi.model.Playlist
import com.zionhuang.innertube.YouTube
import com.zionhuang.innertube.models.YTItem
import com.zionhuang.innertube.pages.HomePage
import kotlinx.coroutines.async
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
    val isLoading: Boolean = false,
    val strapline: String? = null,       // small text above title (e.g. "Similar to")
    val avatarUrl: String? = null,       // 40dp circle art in header
    val moreEndpoint: String? = null,    // playlist/artist ID for "More" pill
    val layout: HomeSectionLayout = HomeSectionLayout.CAROUSEL,
    val subtitle: String? = null,       // secondary line under the title
    val artistName: String? = null      // enables "Not interested" for artist shelves
)

enum class HomeSectionLayout {
    CAROUSEL,       // horizontal LazyRow (default)
    LIST_COLUMNS,   // 2-column grid of rectangle cards (like YTM "Listen again")
    GRID            // gradient mix cards
}

enum class HomeSectionType {
    STATUS,
    KEEP_LISTENING,
    LISTEN_AGAIN,       // NEW
    QUICK_PICKS,
    FORGOTTEN_FAVORITES,
    RECOMMENDATION,
    NEW_RELEASES,
    MOODS,
    MOOD_PLAYLISTS,
    YOUTUBE_RAIL,
    ALBUMS_FOR_YOU,     // NEW
    SIMILAR_TO,         // NEW
    MY_MIX,             // NEW
    BECAUSE_YOU_LISTENED, // NEW
    TASTEBUILDER_CARD   // NEW
}

sealed class HomeFeedItem {
    data class Music(val item: MusicItem) : HomeFeedItem()
    data class YouTube(val item: YTItem) : HomeFeedItem()
    data class Mood(val title: String) : HomeFeedItem()
    data class PlaylistResult(val playlist: Playlist) : HomeFeedItem()
}

internal var cachedDefaultHomePage: HomePage? = null

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
            try {
                _filteredPlaylists.value = repository.getMoodPlaylists(chip)
            } catch (e: Exception) {
                Log.e("MusicVM", "Failed to load $chip playlists", e)
                _filteredPlaylists.value = emptyList()
            } finally {
                rebuildHomeFeedState()
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
        if (cachedDefaultHomePage != null) {
            _homePage.value = cachedDefaultHomePage
            rebuildHomeFeedState(isRefreshing = false)
        } else {
            rebuildHomeFeedState(isRefreshing = false)
            viewModelScope.launch {
                try {
                    val existingChips = _homePage.value?.chips
                    YouTube.home(params = null).onSuccess { defaultPage ->
                        val finalPage = if (defaultPage.chips.isNullOrEmpty() && !existingChips.isNullOrEmpty()) {
                            defaultPage.copy(chips = existingChips)
                        } else {
                            defaultPage
                        }
                        _homePage.value = finalPage
                        cachedDefaultHomePage = finalPage
                    }
                } catch (_: Exception) {
                } finally {
                    rebuildHomeFeedState(isRefreshing = false)
                }
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

            if (builtSections.isEmpty()) {
                val (trackQuery, moodQuery, mixQuery) = when {
                    lowerTitle.contains("podcast") -> Triple("popular podcast episode", "Podcasts", "tech culture stories podcast")
                    lowerTitle.contains("party") -> Triple("party hits dance club anthems", "Party", "upbeat party remix club")
                    else -> Triple("$categoryTitle music hits", categoryTitle, "$categoryTitle vibes mix")
                }

                val tracksDeferred = async { runCatching { repository.searchMusic(trackQuery).items.take(14) }.getOrDefault(emptyList()) }
                val playlistsDeferred = async { runCatching { repository.getMoodPlaylists(moodQuery).take(12) }.getOrDefault(emptyList()) }
                val mixDeferred = async { runCatching { repository.searchMusic(mixQuery).items.take(12) }.getOrDefault(emptyList()) }

                val foundTracks = tracksDeferred.await()
                val foundPlaylists = playlistsDeferred.await()
                val foundMix = mixDeferred.await()

                if (foundTracks.isNotEmpty()) {
                    builtSections += HomeSection(
                        id = "cat_${lowerTitle}_tracks",
                        title = "Top $categoryTitle Picks",
                        type = HomeSectionType.QUICK_PICKS,
                        sourceType = HomeInteractionEntity.SOURCE_YOUTUBE,
                        items = foundTracks.map(HomeFeedItem::Music)
                    )
                }
                if (foundPlaylists.isNotEmpty()) {
                    builtSections += HomeSection(
                        id = "cat_${lowerTitle}_playlists",
                        title = "$categoryTitle Playlists",
                        type = HomeSectionType.MOOD_PLAYLISTS,
                        sourceType = HomeInteractionEntity.SOURCE_EXPLORE,
                        items = foundPlaylists.map(HomeFeedItem::PlaylistResult)
                    )
                }
                if (foundMix.isNotEmpty()) {
                    builtSections += HomeSection(
                        id = "cat_${lowerTitle}_mix",
                        title = "$categoryTitle Essentials",
                        type = HomeSectionType.RECOMMENDATION,
                        sourceType = HomeInteractionEntity.SOURCE_YOUTUBE,
                        items = foundMix.map(HomeFeedItem::Music)
                    )
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

/** Used by BackfillGenerator and rebuildHomeFeedState to get a stable ID from a YTItem. */
fun homeYouTubeItemIdOf(item: com.zionhuang.innertube.models.YTItem): String =
    when (item) {
        is com.zionhuang.innertube.models.SongItem -> item.id
        is com.zionhuang.innertube.models.AlbumItem -> item.id
        is com.zionhuang.innertube.models.ArtistItem -> item.id
        is com.zionhuang.innertube.models.PlaylistItem -> item.id
        else -> item.hashCode().toString()
    }
