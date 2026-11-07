package com.israrxy.raazi.utils

/**
 * Resizes YouTube thumbnail URLs to higher quality
 * YouTube supports sizing via URL parameters: =wXXX-hYYY
 */
object ThumbnailUtils {
    private val YOUTUBE_ID_REGEX = Regex("[A-Za-z0-9_-]{11}")

    /**
     * Resize thumbnail URL to specified dimensions
     * @param url Original thumbnail URL
     * @param width Target width (default 544 for high quality)
     * @param height Target height (default 544 for high quality)
     */
    fun resizeThumbnailUrl(url: String?, width: Int = 544, height: Int = 544): String {
        if (url.isNullOrEmpty()) return ""
        
        // Local storage paths don't need resizing
        if (url.startsWith("/storage") || url.startsWith("file:")) return url
        
        // YouTube Music album art (Google CDN supports dynamic sizing parameters)
        if (url.contains("googleusercontent.com")) {
            val baseUrl = url.substringBefore("=w").substringBefore("=s")
            return "$baseUrl=w$width-h$height-l90-rj"
        }

        // Standard YouTube video thumbnails (ytimg.com does not support query resize params)
        if (url.contains("ytimg.com/vi/")) {
            val videoId = url.substringAfter("ytimg.com/vi/").substringBefore("/")
            if (videoId.isNotBlank() && videoId != url) {
                return if (width >= 400) {
                    "https://i.ytimg.com/vi/$videoId/maxresdefault.jpg"
                } else {
                    "https://i.ytimg.com/vi/$videoId/mqdefault.jpg"
                }
            }
        }
        
        return url
    }

    /**
     * Resolves a usable image URL for list rows. Video results sometimes carry
     * a watch page URL or no thumbnail at all, which Coil renders as a black
     * box. Fall back to the video id so every row has artwork.
     */
    fun resolveThumbnail(
        url: String?,
        videoId: String? = null,
        width: Int = 180,
        height: Int = 180
    ): String {
        val raw = url?.trim().orEmpty()
        val imageUrl = when {
            raw.startsWith("/storage") || raw.startsWith("file:") -> raw
            raw.startsWith("http") && looksLikeImage(raw) -> resizeThumbnailUrl(raw, width, height)
            else -> ""
        }
        if (imageUrl.isNotBlank()) return imageUrl

        val id = videoId?.trim().orEmpty()
        return if (id.matches(YOUTUBE_ID_REGEX)) {
            "https://i.ytimg.com/vi/$id/mqdefault.jpg"
        } else {
            ""
        }
    }

    private fun looksLikeImage(url: String): Boolean =
        url.contains("googleusercontent.com", ignoreCase = true) ||
            url.contains("ytimg.com", ignoreCase = true) ||
            url.contains("ggpht.com", ignoreCase = true) ||
            url.contains(".jpg", ignoreCase = true) ||
            url.contains(".jpeg", ignoreCase = true) ||
            url.contains(".png", ignoreCase = true) ||
            url.contains(".webp", ignoreCase = true)

    
    /**
     * Get high-quality thumbnail (544x544)
     */
    fun getHighQualityThumbnail(url: String?) = resizeThumbnailUrl(url, 544, 544)
    
    /**
     * Get medium-quality thumbnail (300x300) 
     */
    fun getMediumQualityThumbnail(url: String?) = resizeThumbnailUrl(url, 300, 300)
    
    /**
     * Get list item thumbnail (180x180)
     */
    fun getListThumbnail(url: String?) = resizeThumbnailUrl(url, 180, 180)
}
