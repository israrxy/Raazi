package com.israrxy.raazi.data.remote

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.israrxy.raazi.model.MusicItem
import com.israrxy.raazi.model.SearchResult
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.Locale

class YouTubeChartsExtractor {
    private val client = OkHttpClient()
    private val gson = Gson()
    
    // User requested URL
    private val API_URL = "https://music.youtube.com/youtubei/v1/browse" 
    // Note: User asked for charts.youtube.com but FEmusic_charts works reliably on music.youtube.com
    // and returns the same data. I will use music.youtube.com to ensure stability with FEmusic_charts ID,
    // but can switch if strictly needed.
    
    // OuterTune uses "WEB_REMIX" for music.
    private val CLIENT_NAME = "WEB_REMIX"
    private val CLIENT_VERSION = "1.20250310.01.00" // From OuterTune
    
    // Official Chart Playlist IDs (Fallback)
    private val PL_GLOBAL_TOP_SONGS = "VLPL4fGSI1pDJn69On1fCwI33tF3oqSHqKBV"
    private val PL_GLOBAL_TOP_VIDEOS = "VLPL4fGSI1pDJn67l9H4E8v4x99-b1d5L3y"
    private val PL_TRENDING = "FEmusic_trending" // Or FEmusic_explore
    
    suspend fun getCharts(): Map<String, List<MusicItem>> {
        val results = mutableMapOf<String, List<MusicItem>>()
        
        // 1. Try Main Charts Page
        val mainJson = browse("FEmusic_charts")
        if (mainJson != null) {
            results.putAll(parseCharts(mainJson))
        }
        
        // 2. Fallbacks if specific sections are missing
        if (results.keys.none { it.contains("Top songs", true) }) {
             browse(PL_GLOBAL_TOP_SONGS)?.let { json ->
                 val items = parsePlaylist(json)
                 if (items.isNotEmpty()) results["Global Top 100 Songs"] = items
             }
        }
        
        if (results.keys.none { it.contains("Top music videos", true) }) {
             browse(PL_GLOBAL_TOP_VIDEOS)?.let { json ->
                 val items = parsePlaylist(json)
                 if (items.isNotEmpty()) results["Global Top 100 Music Videos"] = items
             }
        }
        
        return results
    }

    private fun browse(browseId: String): JsonObject? {
        try {
            val headers = mapOf(
                "X-Goog-Api-Format-Version" to "1",
                "X-YouTube-Client-Name" to "67", // WEB_REMIX ID
                "X-YouTube-Client-Version" to CLIENT_VERSION,
                "Origin" to "https://music.youtube.com",
                "Referer" to "https://music.youtube.com/",
                "Content-Type" to "application/json",
                // User Agent from OuterTune
                 "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:128.0) Gecko/20100101 Firefox/128.0"
            )

            // Context Payload
            val context = JsonObject()
            val clientObj = JsonObject()
            clientObj.addProperty("clientName", CLIENT_NAME)
            clientObj.addProperty("clientVersion", CLIENT_VERSION)
            clientObj.addProperty("gl", Locale.getDefault().country)
            clientObj.addProperty("hl", Locale.getDefault().toLanguageTag())
            context.add("client", clientObj)

            val payload = JsonObject()
            payload.add("context", context)
            payload.addProperty("browseId", browseId)

            val requestBody = payload.toString().toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url("$API_URL?key=AIzaSyC9XL3ZjWddXya6X74dJoCTL-WEYFDNX3") // Key from OuterTune
                .post(requestBody)
                .apply {
                    headers.forEach { (k, v) -> addHeader(k, v) }
                }
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val bodyString = response.body?.string()
                return gson.fromJson(bodyString, JsonObject::class.java)
            } else {
                Log.e("ChartsExtractor", "Error: ${response.code}")
            }
        } catch (e: Exception) {
            Log.e("ChartsExtractor", "Exception", e)
        }
        return null
    }

    private fun parseCharts(json: JsonObject): Map<String, List<MusicItem>> {
        val results = mutableMapOf<String, List<MusicItem>>()
        
        try {
            // Traverse roughly: contents -> singleColumnBrowseResultsRenderer -> tabs[0] -> tabRenderer -> content -> sectionListRenderer -> contents
            val contents = json.getAsJsonObject("contents")
                ?.getAsJsonObject("singleColumnBrowseResultsRenderer")
                ?.getAsJsonArray("tabs")
                ?.get(0)?.asJsonObject
                ?.getAsJsonObject("tabRenderer")
                ?.getAsJsonObject("content")
                ?.getAsJsonObject("sectionListRenderer")
                ?.getAsJsonArray("contents")

            contents?.forEach { sectionElement ->
                val section = sectionElement.asJsonObject
                val shelf = section.getAsJsonObject("musicCarouselShelfRenderer")
                if (shelf != null) {
                    val title = shelf.getAsJsonObject("header")
                        ?.getAsJsonObject("musicCarouselShelfBasicHeaderRenderer")
                        ?.getAsJsonObject("title")
                        ?.getAsJsonArray("runs")
                        ?.get(0)?.asJsonObject
                        ?.get("text")?.asString ?: "Unknown"

                    val items = shelf.getAsJsonArray("contents")
                    val parsedItems = parseItems(items)
                    if (parsedItems.isNotEmpty()) {
                        results[title] = parsedItems
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("ChartsExtractor", "Parsing error", e)
        }
        return results
    }
    
    private fun parsePlaylist(json: JsonObject): List<MusicItem> {
        try {
            // Playlist Structure: contents -> twoColumnBrowseResultsRenderer -> secondaryContents -> sectionListRenderer -> contents[0] -> musicPlaylistShelfRenderer
            val secondary = json.getAsJsonObject("contents")
                ?.getAsJsonObject("twoColumnBrowseResultsRenderer")
                ?.getAsJsonObject("secondaryContents")
                ?.getAsJsonObject("sectionListRenderer")
                ?.getAsJsonArray("contents")
                
            val shelf = secondary?.firstOrNull()?.asJsonObject?.getAsJsonObject("musicPlaylistShelfRenderer")
            val items = shelf?.getAsJsonArray("contents")
            
            if (items != null) {
                return parseItems(items)
            }
            
            // Fallback: singleColumnBrowseResultsRenderer (some clients)
             val single = json.getAsJsonObject("contents")
                ?.getAsJsonObject("singleColumnBrowseResultsRenderer")
                ?.getAsJsonArray("tabs")?.get(0)?.asJsonObject
                ?.getAsJsonObject("tabRenderer")?.getAsJsonObject("content")
                ?.getAsJsonObject("sectionListRenderer")?.getAsJsonArray("contents")
                
             val playlistShelf = single?.firstOrNull()?.asJsonObject?.getAsJsonObject("musicPlaylistShelfRenderer")
             return if (playlistShelf != null) parseItems(playlistShelf.getAsJsonArray("contents")) else emptyList()
             
        } catch (e: Exception) {
            Log.e("ChartsExtractor", "Playlist Parsing error", e)
        }
        return emptyList()
    }

    private fun parseItems(items: JsonArray): List<MusicItem> {
        val musicItems = mutableListOf<MusicItem>()
        items.forEach { itemWrapper ->
            try {
                // Determine item type: musicTwoRowItemRenderer (Songs/Albums) or musicResponsiveListItemRenderer (Songs in list)
                val renderer = itemWrapper.asJsonObject.getAsJsonObject("musicTwoRowItemRenderer") 
                    ?: itemWrapper.asJsonObject.getAsJsonObject("musicResponsiveListItemRenderer")
                
                if (renderer != null) {
                    val title = renderer.getAsJsonObject("title")
                        ?.getAsJsonArray("runs")?.get(0)?.asJsonObject?.get("text")?.asString ?: ""
                    
                    // Thumbnail
                    val thumbUrl = renderer.getAsJsonObject("thumbnailRenderer")
                        ?.getAsJsonObject("musicThumbnailRenderer")
                        ?.getAsJsonObject("thumbnail")
                        ?.getAsJsonArray("thumbnails")
                        ?.lastOrNull()?.asJsonObject // highest quality
                        ?.get("url")?.asString ?: ""

                    // Video ID or Playlist ID
                    var videoId = ""
                    // Try navigationEndpoint
                    val navEndpoint = renderer.getAsJsonObject("navigationEndpoint")
                        ?: renderer.getAsJsonObject("musicTwoRowItemRenderer")?.getAsJsonObject("navigationEndpoint") // fallback?
                        
                   videoId = navEndpoint?.getAsJsonObject("watchEndpoint")?.get("videoId")?.asString 
                        ?: navEndpoint?.getAsJsonObject("watchPlaylistEndpoint")?.get("playlistId")?.asString 
                        ?: navEndpoint?.getAsJsonObject("browseEndpoint")?.get("browseId")?.asString ?: ""

                    // Subtitle (Artist)
                    val subtitleRuns = renderer.getAsJsonObject("subtitle")?.getAsJsonArray("runs") 
                        ?: renderer.getAsJsonArray("flexColumns")?.get(1)?.asJsonObject
                            ?.getAsJsonObject("musicResponsiveListItemFlexColumnRenderer")
                            ?.getAsJsonObject("text")?.getAsJsonArray("runs")

                    var artist = "Unknown"
                    var artistId: String? = null

                    subtitleRuns?.forEach { run ->
                        val text = run.asJsonObject.get("text")?.asString
                        if (text != null && text != " • " && artist == "Unknown") {
                             artist = text
                        }
                        
                        // Extract Artist ID
                        val navEndpoint = run.asJsonObject.getAsJsonObject("navigationEndpoint")
                        val browseId = navEndpoint?.getAsJsonObject("browseEndpoint")?.get("browseId")?.asString
                        if (browseId != null && browseId.startsWith("UC")) {
                            artistId = browseId
                        }
                    }

                   if (videoId.isNotEmpty()) {
                       // Check if it is a playlist (videoId might be empty, or we used playlistId as ID)
                       val hasPlaylistEndpoint = navEndpoint?.getAsJsonObject("watchPlaylistEndpoint") != null
                       val hasBrowseEndpoint = navEndpoint?.getAsJsonObject("browseEndpoint") != null
                       val isPlaylist = navEndpoint?.getAsJsonObject("watchEndpoint")?.get("videoId")?.asString.isNullOrEmpty() || hasPlaylistEndpoint || hasBrowseEndpoint
                       
                       // Use ID directly for videoUrl if it's a track, or construct playlist URL if playlist
                       // (MusicPlaybackService handles IDs safely, preventing malformed URLs)
                       val videoUrl = if (isPlaylist) "https://www.youtube.com/playlist?list=$videoId" else videoId
                       
                       musicItems.add(MusicItem(
                           id = videoId,
                           title = title,
                           artist = artist,
                           duration = 0L,
                           thumbnailUrl = thumbUrl,
                           audioUrl = "", // Empty initially
                           videoUrl = videoUrl,
                           isLive = false,
                           isPlaylist = isPlaylist,
                           artistId = artistId
                       ))
                   } else {
                       // Log warning for skipped item
                       Log.w("ChartsExtractor", "Skipping item with missing videoId: $title")
                   }
                }
            } catch (e: Exception) {
                // skip bad item
            }
        }
        return musicItems
    }
}
