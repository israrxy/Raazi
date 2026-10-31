package com.israrxy.raazi.ui.library

import com.israrxy.raazi.model.MusicItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LibrarySmartMixesTest {

    @Test
    fun `builds on repeat rediscover and discover mixes from likes and history`() {
        val liked = listOf("a", "b", "c", "d", "e", "f").map(::track)
        val history = listOf("a", "b", "c", "g", "h", "i").map(::track)

        val mixes = buildLibrarySmartMixes(liked, history)

        assertEquals(
            listOf("smart_on_repeat", "smart_rediscover", "smart_discover"),
            mixes.map { it.key }
        )
        assertEquals(listOf("a", "b", "c"), mixes[0].tracks.map { it.id })
        assertEquals(listOf("d", "e", "f"), mixes[1].tracks.map { it.id })
        assertEquals(listOf("g", "h", "i"), mixes[2].tracks.map { it.id })
    }

    @Test
    fun `skips mixes with too few tracks`() {
        val mixes = buildLibrarySmartMixes(
            likedSongs = listOf(track("a"), track("b")),
            history = listOf(track("a"), track("c"))
        )

        assertTrue(mixes.isEmpty())
    }

    @Test
    fun `deduplicates repeated history entries`() {
        val liked = listOf("a", "b", "c").map(::track)
        val history = listOf("a", "a", "b", "b", "c").map(::track)

        val mixes = buildLibrarySmartMixes(liked, history)

        assertEquals(listOf("a", "b", "c"), mixes.single().tracks.map { it.id })
    }

    private fun track(id: String) = MusicItem(
        id = id,
        title = "Track $id",
        artist = "Artist",
        duration = 180_000L,
        thumbnailUrl = "",
        audioUrl = "",
        videoUrl = ""
    )
}
