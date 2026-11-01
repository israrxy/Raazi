package com.israrxy.raazi.ui.library

import com.israrxy.raazi.model.MusicItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryStateTest {

    @Test
    fun `history preview is capped at ten while browsing`() {
        val state = libraryState(history = (1..14).map { track(it) })

        assertEquals(14, state.visibleHistory.size)
        assertEquals(10, state.historyPreview.size)
        assertTrue(state.historyPreviewCapped)
    }

    @Test
    fun `search shows every matching history item without a preview cap`() {
        val state = libraryState(
            history = (1..14).map { track(it) },
            query = "Song"
        )

        assertEquals(14, state.historyPreview.size)
        assertFalse(state.historyPreviewCapped)
    }

    @Test
    fun `songs filter hides playlists and keeps liked songs`() {
        val state = libraryState(
            likedSongs = listOf(track(1)),
            filter = LibraryFilter.SONGS
        )

        assertEquals(listOf("liked-1"), state.visibleLikedSongs.map { it.id })
        assertTrue(state.visibleUserPlaylists.isEmpty())
    }

    @Test
    fun `playlist artwork uses the four most played tracks`() {
        val tracks = listOf(
            "a" to "a.jpg",
            "b" to "b.jpg",
            "c" to "c.jpg",
            "d" to "d.jpg",
            "e" to "e.jpg"
        )

        val artwork = topPlayedArtwork(
            tracks = tracks,
            playCounts = mapOf("a" to 2, "b" to 5, "c" to 9, "d" to 1, "e" to 4)
        )

        assertEquals(listOf("c.jpg", "b.jpg", "e.jpg", "a.jpg"), artwork)
    }

    private fun libraryState(
        query: String = "",
        filter: LibraryFilter = LibraryFilter.ALL,
        likedSongs: List<MusicItem> = emptyList(),
        history: List<MusicItem> = emptyList()
    ) = LibraryUiState(
        query = query,
        filter = filter,
        sort = LibrarySort.RECENT,
        likedSongs = likedSongs,
        history = history,
        userPlaylists = emptyList(),
        savedPlaylists = emptyList(),
        albums = emptyList(),
        artists = emptyList(),
        downloadCount = 0
    )

    private fun track(index: Int) = MusicItem(
        id = "liked-$index",
        title = "Song $index",
        artist = "Artist",
        duration = 180_000L,
        thumbnailUrl = "",
        audioUrl = "",
        videoUrl = ""
    )
}
