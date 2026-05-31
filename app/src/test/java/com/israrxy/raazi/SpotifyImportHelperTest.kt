package com.israrxy.raazi

import com.israrxy.raazi.service.SpotifyImportHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SpotifyImportHelperTest {

    @Test
    fun testExtractPlaylistId() {
        val url1 = "https://open.spotify.com/playlist/37i9dQZF1DXcBWIGxy7T71"
        assertEquals("37i9dQZF1DXcBWIGxy7T71", SpotifyImportHelper.extractPlaylistId(url1))

        val url2 = "https://open.spotify.com/playlist/37i9dQZF1DXcBWIGxy7T71?si=abc"
        assertEquals("37i9dQZF1DXcBWIGxy7T71", SpotifyImportHelper.extractPlaylistId(url2))

        val url3 = "spotify:playlist:37i9dQZF1DXcBWIGxy7T71"
        assertEquals("37i9dQZF1DXcBWIGxy7T71", SpotifyImportHelper.extractPlaylistId(url3))

        val invalidUrl = "https://open.spotify.com/album/37i9dQZF1DXcBWIGxy7T71"
        assertNull(SpotifyImportHelper.extractPlaylistId(invalidUrl))
    }

    @Test
    fun testParsePlaylistHtml() {
        val sampleHtml = """
            <!DOCTYPE html><html>
            <body>
            <img class="CoverArtBase" alt="Acoustic Hits cover" src="https://image-cdn.com/123" />
            <ol>
              <li class="TracklistRow_trackListRow" data-testid="tracklist-row-0">
                <h3 class="TracklistRow_title">Track One</h3>
                <h4 class="TracklistRow_subtitle">Artist A</h4>
                <div class="TracklistRow_durationCell">03:45</div>
              </li>
              <li class="TracklistRow_trackListRow" data-testid="tracklist-row-1">
                <h3 class="TracklistRow_title">Track Two &amp; Three</h3>
                <h4 class="TracklistRow_subtitle">Artist B, Artist C</h4>
                <div class="TracklistRow_durationCell">04:20</div>
              </li>
            </ol>
            </body></html>
        """.trimIndent()

        val playlist = SpotifyImportHelper.parsePlaylistHtml(sampleHtml)
        assertEquals("Acoustic Hits", playlist.title)
        assertEquals(2, playlist.tracks.size)

        val track1 = playlist.tracks[0]
        assertEquals(0, track1.index)
        assertEquals("Track One", track1.title)
        assertEquals("Artist A", track1.artist)
        assertEquals(225000L, track1.durationMs) // 3m 45s = 225s = 225000ms

        val track2 = playlist.tracks[1]
        assertEquals(1, track2.index)
        assertEquals("Track Two & Three", track2.title)
        assertEquals("Artist B, Artist C", track2.artist)
        assertEquals(260000L, track2.durationMs) // 4m 20s = 260s = 260000ms
    }
}
