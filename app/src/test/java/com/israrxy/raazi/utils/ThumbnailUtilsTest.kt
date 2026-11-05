package com.israrxy.raazi.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class ThumbnailUtilsTest {

    @Test
    fun testYouTubeVideoHighQualityThumbnailReturnsBarFreeMaxRes() {
        val original = "https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg"
        val hq = ThumbnailUtils.getHighQualityThumbnail(original)
        assertEquals("https://i.ytimg.com/vi/dQw4w9WgXcQ/maxresdefault.jpg", hq)
    }

    @Test
    fun testYouTubeVideoMediumQualityThumbnailReturnsBarFreeMqDefault() {
        val original = "https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg"
        val mq = ThumbnailUtils.getMediumQualityThumbnail(original)
        assertEquals("https://i.ytimg.com/vi/dQw4w9WgXcQ/mqdefault.jpg", mq)
    }

    @Test
    fun testVideoThumbnailCandidatesReturnsInPriorityOrder() {
        val original = "https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg"
        val candidates = ThumbnailUtils.getVideoThumbnailCandidates(original)
        val expected = listOf(
            "https://i.ytimg.com/vi/dQw4w9WgXcQ/maxresdefault.jpg",
            "https://i.ytimg.com/vi/dQw4w9WgXcQ/hq720.jpg",
            "https://i.ytimg.com/vi/dQw4w9WgXcQ/mqdefault.jpg"
        )
        assertEquals(expected, candidates)
    }

    @Test
    fun testGoogleCdnThumbnailResizing() {
        val original = "https://lh3.googleusercontent.com/some_hash=w120-h120-l90-rj"
        val hq = ThumbnailUtils.getHighQualityThumbnail(original)
        assertEquals("https://lh3.googleusercontent.com/some_hash=w544-h544-l90-rj", hq)
    }

    @Test
    fun testLocalAndEmptyUrlsUntouched() {
        assertEquals("", ThumbnailUtils.getHighQualityThumbnail(null))
        assertEquals("", ThumbnailUtils.getHighQualityThumbnail(""))
        val local = "/storage/emulated/0/Music/song.mp3"
        assertEquals(local, ThumbnailUtils.getHighQualityThumbnail(local))
    }
}
