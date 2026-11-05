package com.israrxy.raazi.player.flac

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FlacStreamSwitchTest {

    @Test
    fun testExactTrackMatchReturnsHighConfidence() {
        val candidate = HifiTrack(
            id = 12345L,
            title = "Starboy",
            artistName = "The Weeknd",
            albumTitle = "Starboy",
            isrc = "USUM71607007"
        )
        val (matched, score) = FlacStreamResolver.matchTrackDetailed(
            candidates = listOf(candidate),
            title = "Starboy",
            artist = "The Weeknd"
        )

        assertNotNull(matched)
        assertEquals(12345L, matched?.id)
        assertTrue("Exact match score should be >= 100f, was $score", score >= 100f)
    }

    @Test
    fun testLowConfidenceCandidateIsRejectedHonestly() {
        val candidate = HifiTrack(
            id = 99999L,
            title = "Completely Different Song",
            artistName = "Someone Else",
            albumTitle = "Unknown Album"
        )
        val (matched, score) = FlacStreamResolver.matchTrackDetailed(
            candidates = listOf(candidate),
            title = "Starboy",
            artist = "The Weeknd"
        )

        assertNull("Unrelated candidate should not match", matched)
        assertTrue("Score should be below confidence threshold (30f), was $score", score < 30f)
    }

    @Test
    fun testEmptyCandidatesReturnsNullAndZeroScore() {
        val (matched, score) = FlacStreamResolver.matchTrackDetailed(
            candidates = emptyList(),
            title = "Any Title",
            artist = "Any Artist"
        )

        assertNull(matched)
        assertEquals(0f, score, 0.01f)
    }

    @Test
    fun testFlacResolutionResultSuccessProperties() {
        val flacResult = FlacResult(
            localPath = "/cache/flac/12345.flac",
            qualityLabel = "FLAC Hi-Res",
            title = "Blinding Lights",
            artist = "The Weeknd",
            album = "After Hours",
            durationMs = 200000L,
            tidalTrackId = 12345L
        )
        val result: FlacResolutionResult = FlacResolutionResult.Success(flacResult)

        assertTrue(result is FlacResolutionResult.Success)
        val success = result as FlacResolutionResult.Success
        assertEquals("/cache/flac/12345.flac", success.flacResult.localPath)
        assertEquals("FLAC Hi-Res", success.flacResult.qualityLabel)
    }

    @Test
    fun testFlacResolutionResultFailureReportsHonestReason() {
        val reasons = listOf(
            "Track not found in HiFi catalog for \"Obscure Indie Song\"",
            "HiFi servers unreachable (DNS lookup failed)",
            "Track requires DRM decryption (WIDEVINE) which is unsupported",
            "FLAC stream download failed (HTTP 404)"
        )

        for (reason in reasons) {
            val result: FlacResolutionResult = FlacResolutionResult.Failure(reason)
            assertTrue(result is FlacResolutionResult.Failure)
            val failure = result as FlacResolutionResult.Failure
            assertEquals(reason, failure.reason)
            assertTrue(failure.reason.isNotBlank())
        }
    }

    @Test
    fun testFlacResultProgressiveStreamingProperty() {
        val streamResult = FlacResult(
            localPath = "https://cdn.example.com/audio/track_123.flac",
            qualityLabel = "FLAC CD",
            title = "Starboy",
            artist = "The Weeknd",
            isStreamUrl = true
        )
        assertTrue("isStreamUrl should be true for progressive HTTP streams", streamResult.isStreamUrl)
        assertTrue(streamResult.localPath.startsWith("https://"))
        assertEquals("FLAC CD", streamResult.qualityLabel)
    }
}
