package com.israrxy.raazi.data.account

import com.israrxy.raazi.data.db.PlaybackHistoryEntity
import com.israrxy.raazi.data.db.PlaylistEntity
import com.israrxy.raazi.data.db.PlaylistWithTracks
import com.israrxy.raazi.data.db.SavedCollectionEntity
import com.israrxy.raazi.data.db.TrackEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GoogleDriveSyncPayloadTest {

    @Test
    fun `buildUnifiedStateJson builds valid version 2 schema with taste profile`() {
        val favorites = listOf(
            TrackEntity(
                id = "fav-1",
                title = "Starboy",
                artist = "The Weeknd",
                duration = 230L,
                thumbnailUrl = "thumb1.jpg",
                audioUrl = "",
                videoUrl = "fav-1",
                isLive = false,
                isFavorite = true
            )
        )

        val playlists = listOf(
            PlaylistWithTracks(
                playlist = PlaylistEntity(id = "pl-1", title = "Hits", description = "Best songs", thumbnailUrl = "pl.jpg"),
                tracks = favorites
            )
        )

        val history = listOf(
            PlaybackHistoryEntity(
                id = "track-1",
                title = "Starboy",
                artist = "The Weeknd",
                thumbnailUrl = "thumb1.jpg",
                audioUrl = "",
                videoUrl = "track-1",
                duration = 230L,
                timestamp = 1000L,
                playCount = 10
            ),
            PlaybackHistoryEntity(
                id = "track-2",
                title = "Yellow",
                artist = "Coldplay",
                thumbnailUrl = "thumb2.jpg",
                audioUrl = "",
                videoUrl = "track-2",
                duration = 260L,
                timestamp = 2000L,
                playCount = 5
            )
        )

        val collections = listOf(
            SavedCollectionEntity(
                id = "artist-1",
                sourceId = "src-1",
                title = "Coldplay",
                subtitle = "Artist",
                thumbnailUrl = "coldplay.jpg",
                contentType = "ARTIST",
                timestamp = 1500L
            )
        )

        val json = GoogleDriveSyncPayload.buildUnifiedStateJson(
            favorites = favorites,
            playlistsWithTracks = playlists,
            history = history,
            savedCollections = collections,
            lastfmSessionKey = "sess123",
            lastfmUsername = "user456"
        )

        assertEquals(2, json.getInt("version"))
        assertEquals("sess123", json.getString("lastfmSessionKey"))
        assertEquals("user456", json.getString("lastfmUsername"))

        assertEquals(1, json.getJSONArray("favorites").length())
        assertEquals(1, json.getJSONArray("playlists").length())
        assertEquals(2, json.getJSONArray("history").length())
        assertEquals(1, json.getJSONArray("savedCollections").length())

        val profile = json.getJSONObject("feedProfile")
        assertNotNull(profile)
        assertEquals(15, profile.getInt("totalPlayCount"))

        val topArtists = profile.getJSONArray("topArtists")
        assertTrue(topArtists.length() >= 2)
        assertEquals("The Weeknd", topArtists.getString(0))
        assertEquals("Coldplay", topArtists.getString(1))
    }
}
