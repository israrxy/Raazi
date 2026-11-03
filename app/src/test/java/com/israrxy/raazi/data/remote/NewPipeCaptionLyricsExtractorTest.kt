package com.israrxy.raazi.data.remote

import com.israrxy.raazi.ui.parseLrcLines
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NewPipeCaptionLyricsExtractorTest {

    private val extractor = NewPipeCaptionLyricsExtractor()

    @Test
    fun parseWebVttToLrc_stripsNoiseAndFormatsTimestamps() {
        val vtt = """
            WEBVTT
            Kind: captions
            Language: en

            00:00:01.200 --> 00:00:03.500
            [Music]

            00:00:03.500 --> 00:00:07.100
            I've been on my own for long enough

            00:00:07.100 --> 00:00:11.450
            Maybe you can show me how to love, <b>maybe</b>

            00:00:11.450 --> 00:00:15.000
            ♪ I'm going through withdrawals ♪
        """.trimIndent()

        val lrc = extractor.parseCaptionsToLrc(vtt)
        assertNotNull(lrc)

        val pairs = parseLrcLines(lrc!!)
        assertEquals(3, pairs.size)
        assertEquals("I've been on my own for long enough", pairs[0].second)
        assertEquals(3_500L, pairs[0].first)

        assertEquals("Maybe you can show me how to love, maybe", pairs[1].second)
        assertEquals(7_100L, pairs[1].first)

        assertEquals("I'm going through withdrawals", pairs[2].second)
        assertEquals(11_450L, pairs[2].first)
    }

    @Test
    fun parseXmlTranscriptToLrc_handlesNumericTimestamps() {
        val xml = """
            <transcript>
                <text start="2.45" dur="3.1">First lyric line</text>
                <text start="5.8" dur="4.0">Second lyric line with &amp; entity</text>
                <text start="10.12" dur="2.0">[Applause]</text>
                <text start="12.5" dur="3.0">Third lyric line</text>
            </transcript>
        """.trimIndent()

        val lrc = extractor.parseCaptionsToLrc(xml)
        assertNotNull(lrc)

        val pairs = parseLrcLines(lrc!!)
        assertEquals(3, pairs.size)
        assertEquals("First lyric line", pairs[0].second)
        assertEquals(2_450L, pairs[0].first)

        assertEquals("Second lyric line with & entity", pairs[1].second)
        assertEquals(5_800L, pairs[1].first)

        assertEquals("Third lyric line", pairs[2].second)
        assertEquals(12_500L, pairs[2].first)
    }

    @Test
    fun parseJson3ToLrc_handlesEventsAndSegments() {
        val json = """
            {
              "wireMagic": "pb3",
              "events": [
                {
                  "tStartMs": 1500,
                  "dDurationMs": 2000,
                  "segs": [
                    { "utf8": "Hello " },
                    { "utf8": "world" }
                  ]
                },
                {
                  "tStartMs": 4000,
                  "dDurationMs": 3000,
                  "segs": [
                    { "utf8": "Never gonna give you up" }
                  ]
                },
                {
                  "tStartMs": 8000,
                  "dDurationMs": 2000,
                  "segs": [
                    { "utf8": "♪" }
                  ]
                }
              ]
            }
        """.trimIndent()

        val lrc = extractor.parseCaptionsToLrc(json)
        assertNotNull(lrc)

        val pairs = parseLrcLines(lrc!!)
        assertEquals(2, pairs.size)
        assertEquals("Hello world", pairs[0].second)
        assertEquals(1_500L, pairs[0].first)

        assertEquals("Never gonna give you up", pairs[1].second)
        assertEquals(4_000L, pairs[1].first)
    }
}
