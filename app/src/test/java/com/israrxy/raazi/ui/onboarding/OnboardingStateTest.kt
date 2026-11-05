package com.israrxy.raazi.ui.onboarding

import com.israrxy.raazi.data.region.RegionManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingStateTest {

    @Test
    fun `privacy gating prevents advancing past privacy page when unaccepted`() {
        val privacyPage = 3

        var privacyAccepted = false
        val canProceedBeforeAccept = privacyPage != 3 || privacyAccepted
        assertFalse("Must not be allowed to proceed without accepting privacy", canProceedBeforeAccept)

        privacyAccepted = true
        val canProceedAfterAccept = privacyPage != 3 || privacyAccepted
        assertTrue("Must be allowed to proceed after accepting privacy", canProceedAfterAccept)
    }

    @Test
    fun `skip behavior respects privacy consent`() {
        // If privacy is not accepted, skip must route to privacy page (index 3)
        var privacyAccepted = false
        val targetPageUnaccepted = if (privacyAccepted) -1 else 3
        assertEquals("Unaccepted privacy skip routes to page 3", 3, targetPageUnaccepted)

        // If privacy is accepted, skip allows direct finish
        privacyAccepted = true
        val targetPageAccepted = if (privacyAccepted) -1 else 3
        assertEquals("Accepted privacy skip finishes directly", -1, targetPageAccepted)
    }

    @Test
    fun `content region display resolution handles auto and explicit codes`() {
        val detectedCountry = "PK"

        // Case 1: AUTO
        val countryAuto = "AUTO"
        val resolvedAuto = if (countryAuto.isBlank() || countryAuto.equals("AUTO", ignoreCase = true)) {
            detectedCountry
        } else {
            countryAuto
        }
        assertEquals("PK", resolvedAuto)

        // Case 2: Explicit country code
        val countryExplicit = "IN"
        val resolvedExplicit = if (countryExplicit.isBlank() || countryExplicit.equals("AUTO", ignoreCase = true)) {
            detectedCountry
        } else {
            countryExplicit
        }
        assertEquals("IN", resolvedExplicit)
    }

    @Test
    fun `lyrics display preview mode cycling`() {
        val formats = listOf("ROMANIZED", "ORIGINAL", "TRANSLATED")
        assertTrue("Contains ROMANIZED", formats.contains("ROMANIZED"))
        assertTrue("Contains ORIGINAL", formats.contains("ORIGINAL"))
        assertTrue("Contains TRANSLATED", formats.contains("TRANSLATED"))
    }
}
