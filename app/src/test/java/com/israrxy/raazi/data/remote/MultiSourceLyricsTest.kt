package com.israrxy.raazi.data.remote

import com.israrxy.raazi.ui.buildRichLyricLines
import com.israrxy.raazi.ui.parseLrcLines
import com.zionhuang.innertube.YouTube
import com.zionhuang.innertube.models.WatchEndpoint
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MultiSourceLyricsTest {

    @Test
    fun verifyEnhancedLrcWordAndLineParsing() {
        val sampleEnhancedLrc = """
            [00:12.00]<00:12.00>I <00:12.40>said <00:12.90>ooh <00:13.50>I'm <00:14.00>blinded <00:14.70>by <00:15.10>the <00:15.50>lights
            [00:16.50]No I can't sleep until I feel your touch
        """.trimIndent()

        val rawPairs = parseLrcLines(sampleEnhancedLrc)
        val richLines = buildRichLyricLines(
            rawPairs = rawPairs,
            durationMs = 200_000L,
            translations = emptyList(),
            showTranslation = false
        )

        assertEquals(2, richLines.size)
        val firstLine = richLines[0]
        assertTrue(firstLine.hasExplicitWordSync)
        assertEquals("I said ooh I'm blinded by the lights", firstLine.text)
        assertEquals(8, firstLine.words.size)
        assertEquals("I", firstLine.words[0].text)
        assertEquals(12_000L, firstLine.words[0].startTimeMs)
        assertEquals(12_400L, firstLine.words[0].endTimeMs)

        val secondLine = richLines[1]
        assertEquals(9, secondLine.words.size)
    }

    @Test
    fun verifyGeniusHtmlContainerExtraction() {
        val sampleGeniusHtml = """
            <html><body>
            <div data-lyrics-container="true" class="Lyrics__Container-sc-1ynbvzw-1">
              145 ContributorsTranslationsBlinding Lights Lyrics<br/>
              <div data-exclude-from-selection="true">Header note</div>
              [Verse 1]<br/>
              <a class="ReferentFragment"><span>I&#x27;ve been tryna call</span></a><br/>
              I&#x27;ve been on my own for long enough
            </div>
            <div data-lyrics-container="true" class="Lyrics__Container-sc-1ynbvzw-1">
              [Chorus]<br/>
              I said, ooh, I&#x27;m blinded by the lights
            </div>
            </body></html>
        """.trimIndent()

        val extracted = GeniusLyricsExtractor.extractLyricsFromGeniusHtml(sampleGeniusHtml)
        assertNotNull(extracted)
        assertTrue(extracted!!.contains("[Verse 1]"))
        assertTrue(extracted.contains("I've been tryna call"))
        assertTrue(extracted.contains("I said, ooh, I'm blinded by the lights"))
        assertTrue(!extracted.contains("145 Contributors"))
    }

    @Test
    fun verifyProviderFilterMatching() {
        assertTrue(LyricsProviderFilter.ALL.matches("Musixmatch"))
        assertTrue(LyricsProviderFilter.MUSIXMATCH.matches("Saved Musixmatch"))
        assertTrue(LyricsProviderFilter.GENIUS.matches("Genius"))
        assertTrue(LyricsProviderFilter.LYRICA.matches("Lyrica"))
        assertTrue(LyricsProviderFilter.YT_MUSIC.matches("YT Music"))
        assertTrue(LyricsProviderFilter.LRCLIB.matches("LRCLIB"))
    }

    @Test
    fun verifyLiveYouTubeMusicLyricsFetch() = runBlocking {
        val nextResult = YouTube.next(WatchEndpoint(videoId = "fHI8X4OXluQ")).getOrNull()
        val lyricsEndpoint = nextResult?.lyricsEndpoint
        if (lyricsEndpoint != null) {
            val ytLyrics = YouTube.lyrics(lyricsEndpoint).getOrNull()
            println("Live YT Music lyrics length: ${ytLyrics?.length}")
            assertTrue(!ytLyrics.isNullOrBlank())
        }
    }

    @Test
    fun verifyMusixmatchTtmlSyllableJoiningAndLineParsing() {
        val sampleTtml = """
            <tt xmlns="http://www.w3.org/ns/ttml" itunes:timing="Word">
              <body>
                <div>
                  <p begin="00:27.206" end="00:29.520"><span begin="00:27.206" end="00:27.603">I've </span><span begin="00:27.603" end="00:27.991">been </span><span begin="00:27.991" end="00:28.395">try</span><span begin="00:28.395" end="00:28.768">na </span><span begin="00:28.768" end="00:29.520">call</span></p>
                  <p begin="00:29.935" end="00:32.357"><span begin="00:29.935" end="00:30.335">I've </span><span begin="00:30.335" end="00:31.835">e</span><span begin="00:31.835" end="00:32.357">nough</span></p>
                </div>
              </body>
            </tt>
        """.trimIndent()

        val parsed = MusixmatchTtmlParser.parseTtmlXml(
            ttml = sampleTtml,
            trackName = "Blinding Lights",
            artistName = "The Weeknd",
            fallbackDurationSeconds = 200.0
        )

        assertNotNull(parsed)
        assertEquals("Musixmatch", parsed!!.source)
        assertTrue(parsed.plainLyrics!!.contains("I've been tryna call"))
        assertTrue(parsed.plainLyrics!!.contains("I've enough"))
        assertTrue(MultiSourceLyricsAggregator.hasWordSync(parsed.syncedLyrics))

        val richLines = buildRichLyricLines(
            rawPairs = parseLrcLines(parsed.syncedLyrics!!),
            durationMs = 200_000L,
            translations = emptyList(),
            showTranslation = false
        )
        assertEquals(2, richLines.size)
        assertEquals("I've been tryna call", richLines[0].text)
        assertEquals(4, richLines[0].words.size)
        assertEquals("tryna", richLines[0].words[2].text)
        assertEquals("enough", richLines[1].words[1].text)
    }

    @Test
    fun verifyLiveMusixmatchAndYouTubeSearchSuggestions() = runBlocking {
        val extractor = MusixmatchLyricsExtractor()
        val liveMxm = extractor.searchTracks("Blinding Lights", "The Weeknd", 200.0)
        assertTrue("Expected live Musixmatch lyrics results", liveMxm.isNotEmpty())
        val top = liveMxm.first()
        assertEquals("Musixmatch", top.source)
        assertTrue(!top.syncedLyrics.isNullOrBlank() || !top.plainLyrics.isNullOrBlank())

        val ytSuggest = YouTube.searchSuggestions("blinding").getOrNull()
        assertNotNull(ytSuggest)
        assertTrue(ytSuggest!!.queries.isNotEmpty())
    }
}
