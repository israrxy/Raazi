package com.israrxy.raazi.service

import com.israrxy.raazi.model.MusicContentType
import com.israrxy.raazi.model.MusicItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class YouTubeSearchRankerTest {

    private fun createItem(
        id: String,
        title: String,
        artist: String = "Various Artists",
        contentType: MusicContentType = MusicContentType.SONG,
        isPlaylist: Boolean = false,
        viewsText: String = "1M views"
    ): MusicItem {
        return MusicItem(
            id = id,
            title = title,
            artist = artist,
            duration = 180_000L,
            thumbnailUrl = "https://example.com/thumb.jpg",
            audioUrl = "",
            videoUrl = "",
            isPlaylist = isPlaylist,
            contentType = contentType,
            viewsText = viewsText
        )
    }

    @Test
    fun `selectDynamicTopResult selects playlist when query contains playlist keyword`() {
        val competingSong = createItem("song_1", "Lofi Chill Beat", "Lofi Producer", MusicContentType.SONG)
        val competingVideo = createItem("vid_1", "Lofi Study Session", "Chill Girl", MusicContentType.VIDEO)
        val playlistItem = createItem(
            id = "playlist_1",
            title = "Lofi Study Playlist",
            artist = "Lofi Girl",
            contentType = MusicContentType.PLAYLIST,
            isPlaylist = true
        )

        val selected = selectDynamicTopResult(
            query = "lofi study playlist",
            cardMainItem = competingSong,
            unfilteredTracks = listOf(competingSong),
            typedSongs = listOf(competingSong),
            typedPlaylists = listOf(playlistItem)
        )

        assertNotNull(selected)
        assertEquals("playlist_1", selected?.id)
        assertEquals(MusicContentType.PLAYLIST, selected?.contentType)
    }

    @Test
    fun `selectDynamicTopResult selects playlist when playlist title matches query strongly even without keyword`() {
        val competingSong = createItem("song_2", "Top Hits Song", "Hit Singer", MusicContentType.SONG)
        val playlistItem = createItem(
            id = "pl_today_top_hits",
            title = "Today's Top Hits",
            artist = "Spotify Hits",
            contentType = MusicContentType.PLAYLIST,
            isPlaylist = true,
            viewsText = "10M plays"
        )

        val selected = selectDynamicTopResult(
            query = "Today's Top Hits",
            cardMainItem = competingSong,
            typedSongs = listOf(competingSong),
            typedPlaylists = listOf(playlistItem)
        )

        assertNotNull(selected)
        assertEquals("pl_today_top_hits", selected?.id)
    }

    @Test
    fun `selectDynamicTopResult selects song when normal song query without playlist intent`() {
        val songItem = createItem("song_blinding", "Blinding Lights", "The Weeknd", MusicContentType.SONG)
        val playlistItem = createItem("pl_blinding", "Blinding Lights and Similar", "User", MusicContentType.PLAYLIST, isPlaylist = true)

        val selected = selectDynamicTopResult(
            query = "Blinding Lights The Weeknd",
            cardMainItem = songItem,
            typedSongs = listOf(songItem),
            typedPlaylists = listOf(playlistItem)
        )

        assertNotNull(selected)
        assertEquals("song_blinding", selected?.id)
        assertEquals(MusicContentType.SONG, selected?.contentType)
    }
}
