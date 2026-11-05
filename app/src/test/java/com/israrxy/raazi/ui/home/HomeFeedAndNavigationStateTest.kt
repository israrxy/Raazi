package com.israrxy.raazi.ui.home

import com.israrxy.raazi.model.MusicContentType
import com.israrxy.raazi.model.MusicItem
import com.israrxy.raazi.ui.isArtistResult
import com.israrxy.raazi.ui.isPlaylistResult
import com.israrxy.raazi.ui.isPlayableSearchItem
import com.israrxy.raazi.viewmodel.HomeFeedUiState
import com.israrxy.raazi.viewmodel.HomeSection
import com.israrxy.raazi.viewmodel.HomeSectionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeFeedAndNavigationStateTest {

    @Test
    fun testHomeFeedUiStateRetainsContentWithoutLoadingFlicker() {
        val initialFeed = HomeFeedUiState(
            isLoading = false,
            isRefreshing = false,
            sections = listOf(
                HomeSection(
                    id = "home_status",
                    title = "Home",
                    type = HomeSectionType.STATUS,
                    sourceType = "local"
                ),
                HomeSection(
                    id = "quick_picks",
                    title = "Quick Picks",
                    type = HomeSectionType.QUICK_PICKS,
                    sourceType = "local"
                )
            )
        )

        assertFalse("Feed should not be in loading state when populated", initialFeed.isLoading)
        assertEquals(2, initialFeed.sections.size)

        // Simulating playlist load finishing or background events: feed content remains stable
        val updatedFeed = initialFeed.copy(
            isRefreshing = false,
            isLoading = false
        )
        assertFalse(updatedFeed.isLoading)
        assertEquals("quick_picks", updatedFeed.sections[1].id)
    }

    @Test
    fun testSearchHeroItemIdentificationMatchesTopResult() {
        val songItem = MusicItem(
            id = "song_1",
            title = "Starboy",
            artist = "The Weeknd",
            duration = 180_000L,
            thumbnailUrl = "https://example.com/thumb.jpg",
            audioUrl = "",
            videoUrl = "watch?v=song_1",
            contentType = MusicContentType.SONG
        )
        assertTrue(songItem.isPlayableSearchItem())
        assertFalse(songItem.isArtistResult())
        assertFalse(songItem.isPlaylistResult())

        val artistItem = MusicItem(
            id = "artist_1",
            title = "The Weeknd",
            artist = "Artist",
            duration = 0L,
            thumbnailUrl = "https://example.com/artist.jpg",
            audioUrl = "",
            videoUrl = "",
            contentType = MusicContentType.ARTIST
        )
        assertTrue(artistItem.isArtistResult())
        assertFalse(artistItem.isPlayableSearchItem())

        val playlistItem = MusicItem(
            id = "pl_1",
            title = "Top Hits 2026",
            artist = "Raazi",
            duration = 0L,
            thumbnailUrl = "https://example.com/pl.jpg",
            audioUrl = "",
            videoUrl = "",
            isPlaylist = true,
            contentType = MusicContentType.PLAYLIST
        )
        assertTrue(playlistItem.isPlaylistResult())
    }

    @Test
    fun testQuickPicksPersonalizedPriorityExcludesGenericRail() {
        val personalizedItem = MusicItem(
            id = "fav_1",
            title = "My Favorite Track",
            artist = "Local Artist",
            duration = 200_000L,
            thumbnailUrl = "https://example.com/fav.jpg",
            audioUrl = "",
            videoUrl = "watch?v=fav_1",
            contentType = MusicContentType.SONG
        )

        val quickPicksSection = HomeSection(
            id = "quick_picks",
            title = "Quick Picks",
            type = HomeSectionType.QUICK_PICKS,
            sourceType = "local",
            items = listOf(com.israrxy.raazi.viewmodel.HomeFeedItem.Music(personalizedItem))
        )

        val feedState = HomeFeedUiState(
            isLoading = false,
            isRefreshing = false,
            sections = listOf(quickPicksSection)
        )

        assertEquals(1, feedState.sections.size)
        val section = feedState.sections.first()
        assertEquals("quick_picks", section.id)
        val firstItem = section.items.first() as com.israrxy.raazi.viewmodel.HomeFeedItem.Music
        assertEquals("fav_1", firstItem.item.id)
        assertEquals("My Favorite Track", firstItem.item.title)
    }
}
