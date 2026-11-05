package com.israrxy.raazi.data.region

import com.zionhuang.innertube.YouTube
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RegionManagerTest {

    @Test
    fun `supported regions list contains essential countries and auto option`() {
        val codes = RegionManager.SUPPORTED_REGIONS.map { it.code }
        assertTrue("Must include AUTO", codes.contains("AUTO"))
        assertTrue("Must include PK", codes.contains("PK"))
        assertTrue("Must include IN", codes.contains("IN"))
        assertTrue("Must include US", codes.contains("US"))
        assertTrue("Must include GB", codes.contains("GB"))
    }

    @Test
    fun `applyLocale sets country, language and InnerTube YouTube locale correctly`() {
        RegionManager.applyLocale("PK", "ur-PK")

        assertEquals("PK", RegionManager.getCountryCode())
        assertEquals("ur-PK", RegionManager.getLanguageCode())
        assertEquals("PK", YouTube.locale.gl)
        assertEquals("ur-PK", YouTube.locale.hl)

        RegionManager.applyLocale("in", "hi-IN")
        assertEquals("IN", RegionManager.getCountryCode())
        assertEquals("hi-IN", RegionManager.getLanguageCode())
        assertEquals("IN", YouTube.locale.gl)
        assertEquals("hi-IN", YouTube.locale.hl)
    }

    @Test
    fun `applyLocale falls back gracefully on invalid country input`() {
        RegionManager.applyLocale("", "")
        assertEquals("US", RegionManager.getCountryCode())
        assertEquals("en-US", RegionManager.getLanguageCode())
    }

    @Test
    fun `getRegionName resolves names correctly`() {
        assertEquals("Pakistan", RegionManager.getRegionName("PK"))
        assertEquals("India", RegionManager.getRegionName("IN"))
        assertEquals("Auto-detect", RegionManager.getRegionName("AUTO"))
    }
}
