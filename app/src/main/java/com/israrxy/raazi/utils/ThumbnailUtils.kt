package com.israrxy.raazi.utils

/**
 * Resizes YouTube thumbnail URLs to higher quality
 * YouTube supports sizing via URL parameters: =wXXX-hYYY
 */
object ThumbnailUtils {
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
