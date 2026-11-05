package com.israrxy.raazi.ui.player

import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerTransitionTest {

    @Test
    fun testArtworkFlightCoordinatesNeverClippedAndMatchMiniPlayerLockstep() {
        val screenHeight = 2400f
        val miniPlayerTop = 2100f
        val miniPlayerHeight = 150f
        val fromArtwork = Rect(left = 36f, top = 2120f, right = 156f, bottom = 2240f)
        val restX = 90f
        val restY = 320f
        val artWidth = 900f
        val artHeight = 900f

        val testProgresses = listOf(0.04f, 0.10f, 0.25f, 0.40f, 0.50f, 0.75f, 0.90f, 1.0f)

        for (p in testProgresses) {
            val flight = ((p - 0.04f) / 0.96f).coerceIn(0f, 1f)
            val cardTop = lerpFloat(miniPlayerTop, 0f, p)
            val cardHeight = lerpFloat(miniPlayerHeight, screenHeight, p)

            val startScale = fromArtwork.width / artWidth
            val scale = lerpFloat(startScale, 1f, flight)

            val startX = fromArtwork.left
            val currentX = lerpFloat(startX, restX, flight)

            val riseBy = miniPlayerTop
            val startY = fromArtwork.top - riseBy * p - cardTop
            val currentY = lerpFloat(startY, restY, flight)

            // In-capsule lockstep verification at flight start
            if (p == 0.04f) {
                val expectedScreenY = fromArtwork.top - riseBy * p
                val actualScreenY = cardTop + currentY
                assertEquals(expectedScreenY, actualScreenY, 0.01f)
                assertEquals(fromArtwork.left, currentX, 0.01f)
            }

            // Invariant: Artwork card-local top must NEVER be negative (never cut off by card top)
            assertTrue("Artwork top $currentY must be >= 0 at p=$p", currentY >= 0f)

            // Invariant: Artwork card-local bottom must be strictly inside card
            val drawnHeight = artHeight * scale
            assertTrue("Artwork bottom ${currentY + drawnHeight} must be <= cardHeight $cardHeight at p=$p", currentY + drawnHeight <= cardHeight + 1f)

            // Settle verification at p=1.0
            if (p == 1.0f) {
                assertEquals(restX, currentX, 0.001f)
                assertEquals(restY, currentY, 0.001f)
                assertEquals(1f, scale, 0.001f)
            }
        }
    }

    @Test
    fun testTitleFlightCoordinatesNeverClippedAndSettleAccurately() {
        val screenHeight = 2400f
        val miniPlayerTop = 2100f
        val miniPlayerHeight = 150f
        val fromTitle = Rect(left = 180f, top = 2130f, right = 800f, bottom = 2210f)
        val restX = 64f
        val restY = 1350f
        val titleWidth = 700f
        val titleHeight = 80f

        val testProgresses = listOf(0.04f, 0.10f, 0.25f, 0.40f, 0.50f, 0.75f, 0.90f, 1.0f)

        for (p in testProgresses) {
            val flight = ((p - 0.04f) / 0.96f).coerceIn(0f, 1f)
            val cardTop = lerpFloat(miniPlayerTop, 0f, p)
            val cardHeight = lerpFloat(miniPlayerHeight, screenHeight, p)

            val scale = lerpFloat(14f / 22f, 1f, flight)

            val startX = fromTitle.left
            val currentX = lerpFloat(startX, restX, flight)

            val riseBy = miniPlayerTop
            val startY = fromTitle.top - riseBy * p - cardTop
            val currentY = lerpFloat(startY, restY, flight)

            // In-capsule lockstep verification at flight start
            if (p == 0.04f) {
                val expectedScreenY = fromTitle.top - riseBy * p
                val actualScreenY = cardTop + currentY
                assertEquals(expectedScreenY, actualScreenY, 0.01f)
                assertEquals(fromTitle.left, currentX, 0.01f)
            }

            // Invariant: Title card-local top must NEVER be negative (never cut off by card top)
            assertTrue("Title top $currentY must be >= 0 at p=$p", currentY >= 0f)

            // Invariant: Title card-local bottom must be strictly inside card
            val drawnHeight = titleHeight * scale
            assertTrue("Title bottom ${currentY + drawnHeight} must be <= cardHeight $cardHeight at p=$p", currentY + drawnHeight <= cardHeight + 1f)

            // Settle verification at p=1.0
            if (p == 1.0f) {
                assertEquals(restX, currentX, 0.001f)
                assertEquals(restY, currentY, 0.001f)
                assertEquals(1f, scale, 0.001f)
            }
        }
    }
}
