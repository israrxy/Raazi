package com.israrxy.raazi.data.remote

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.israrxy.raazi.model.MusicContentType
import com.israrxy.raazi.model.MusicItem
import com.israrxy.raazi.model.SearchResult
import com.zionhuang.innertube.models.BrowseEndpoint.BrowseEndpointContextSupportedConfigs.BrowseEndpointContextMusicConfig.Companion.MUSIC_PAGE_TYPE_ARTIST
import com.zionhuang.innertube.models.BrowseEndpoint.BrowseEndpointContextSupportedConfigs.BrowseEndpointContextMusicConfig.Companion.MUSIC_PAGE_TYPE_PLAYLIST
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
    private val CLIENT_VERSION = "1.20260530.01.00" // From OuterTune
    
    // Official Chart Playlist IDs (Fallback)
    private val PL_GLOBAL_TOP_SONGS = "VLPL4fGSI1pDJn6puJdseH2Rt9sMvt9E2M4i"
    private val PL_GLOBAL_TOP_VIDEOS = "VLPL4fGSI1pDJn3-A5n37Ua2u14s141v09fG"
    private val PL_TRENDING = "FEmusic_trending" // Or FEmusic_explore
    
    suspend fun getCharts(targetCountry: String? = null): Map<String, List<MusicItem>> = withContext(Dispatchers.IO) {
        val results = mutableMapOf<String, List<MusicItem>>()

        // 1. Try Main Charts Page
        val mainJson = browse("FEmusic_charts", targetCountry)
        if (mainJson != null) {
            results.putAll(parseCharts(mainJson))
        }

        // 2. Fallbacks if specific sections are missing
        if (results.keys.none { it.contains("Top songs", true) }) {
             browse(PL_GLOBAL_TOP_SONGS, targetCountry)?.let { json ->
                 val items = parsePlaylist(json)
                 if (items.isNotEmpty()) results["Global Top 100 Songs"] = items
             }
        }

        if (results.keys.none { it.contains("Top music videos", true) }) {
             browse(PL_GLOBAL_TOP_VIDEOS, targetCountry)?.let { json ->
                 val items = parsePlaylist(json)
                 if (items.isNotEmpty()) results["Global Top 100 Music Videos"] = items
             }
        }

        results
    }

    private fun browse(browseId: String, targetCountry: String? = null): JsonObject? {
        try {
            val headers = mapOf(
                "X-Goog-Api-Format-Version" to "1",
                "X-YouTube-Client-Name" to "67", // WEB_REMIX ID
                "X-YouTube-Client-Version" to CLIENT_VERSION,
                "Origin" to "https://music.youtube.com",
                "Referer" to "https://music.youtube.com/",
                "Content-Type" to "application/json",
                // User Agent from OuterTune
                 "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:138.0) Gecko/20100101 Firefox/138.0"
            )

            val countryCode = targetCountry?.takeIf { it.isNotBlank() }
                ?: com.israrxy.raazi.data.region.RegionManager.getCountryCode().takeIf { it.isNotBlank() }
                ?: Locale.getDefault().country.takeIf { it.isNotBlank() } ?: "US"
            val languageCode = com.israrxy.raazi.data.region.RegionManager.getLanguageCode().takeIf { it.isNotBlank() }
                ?: Locale.getDefault().toLanguageTag().takeIf { it.isNotBlank() } ?: "en-US"

            // Context Payload
            val context = JsonObject()
            val clientObj = JsonObject()
            clientObj.addProperty("clientName", CLIENT_NAME)
            clientObj.addProperty("clientVersion", CLIENT_VERSION)
            clientObj.addProperty("gl", countryCode)
            clientObj.addProperty("hl", languageCode)
            context.add("client", clientObj)

            val payload = JsonObject()
            payload.add("context", context)
            payload.addProperty("browseId", browseId)

            if (browseId == "FEmusic_charts") {
                val formData = JsonObject()
                val selectedValues = JsonArray()
                selectedValues.add(countryCode)
                formData.add("selectedValues", selectedValues)
                payload.add("formData", formData)
            }

            val requestBody = payload.toString().toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url("$API_URL?key=${PublicClientKeys.INNER_TUBE_KEY}") // Public key for charts API
                .post(requestBody)
                .apply {
                    headers.forEach { (k, v) -> addHeader(k, v) }
                }
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyString = response.body?.string()
                    return gson.fromJson(bodyString, JsonObject::class.java)
                } else {
                    Log.e("ChartsExtractor", "Error: ${response.code}")
                }
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
                    ?: section.getAsJsonObject("musicShelfRenderer")
                if (shelf != null) {
                    val title = shelf.getAsJsonObject("header")
                        ?.getAsJsonObject("musicCarouselShelfBasicHeaderRenderer")
                        ?.getAsJsonObject("title")
                        ?.getAsJsonArray("runs")
                        ?.get(0)?.asJsonObject
                        ?.get("text")?.asString
                        ?: shelf.getAsJsonObject("title")
                            ?.getAsJsonArray("runs")
                            ?.get(0)?.asJsonObject
                            ?.get("text")?.asString
                        ?: "Unknown"

                    val items = shelf.getAsJsonArray("contents")
                    if (items != null) {
                        val parsedItems = parseItems(items)
                        if (parsedItems.isNotEmpty()) {
                            results[title] = parsedItems
                        }
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

    /**
     * Parses a chart shelf. Entries may be songs/videos (`watchEndpoint`),
     * real playlists (`VL...` browse ids) or artists
     * (`MUSIC_PAGE_TYPE_ARTIST`). Everything else - podcast shows, albums,
     * genre hubs - is skipped: treating any browse id as a playlist produced
     * cards whose "play" did nothing.
     */
    private fun parseItems(items: JsonArray): List<MusicItem> {
        val musicItems = mutableListOf<MusicItem>()
        items.forEach { itemWrapper ->
            try {
                val wrapper = itemWrapper.asJsonObject
                val renderer = wrapper.getAsJsonObject("musicTwoRowItemRenderer")
                    ?: wrapper.getAsJsonObject("musicResponsiveListItemRenderer")
                    ?: return@forEach

                val title = parseTitle(renderer)
                if (title.isBlank()) return@forEach

                val thumbnailUrl = parseThumbnail(renderer)
                val navEndpoint = renderer.getAsJsonObject("navigationEndpoint")
                val videoId = navEndpoint
                    ?.getAsJsonObject("watchEndpoint")
                    ?.get("videoId")?.asString
                val browseEndpoint = navEndpoint?.getAsJsonObject("browseEndpoint")
                val browseId = browseEndpoint?.get("browseId")?.asString
                val pageType = browseEndpoint
                    ?.getAsJsonObject("browseEndpointContextSupportedConfigs")
                    ?.getAsJsonObject("browseEndpointContextMusicConfig")
                    ?.get("pageType")?.asString
                val subtitleRuns = parseSubtitleRuns(renderer)
                val artistId = subtitleRuns?.firstNotNullOfOrNull { run ->
                    run.asJsonObject
                        .getAsJsonObject("navigationEndpoint")
                        ?.getAsJsonObject("browseEndpoint")
                        ?.get("browseId")?.asString
                        ?.takeIf { it.startsWith("UC") }
                }

                when {
                    !videoId.isNullOrBlank() -> {
                        musicItems += MusicItem(
                            id = videoId,
                            title = title,
                            artist = subtitleText(subtitleRuns) ?: "Unknown",
                            duration = 0L,
                            thumbnailUrl = thumbnailUrl,
                            audioUrl = "",
                            videoUrl = videoId,
                            isLive = false,
                            artistId = artistId
                        )
                    }

                    pageType == MUSIC_PAGE_TYPE_ARTIST && !browseId.isNullOrBlank() -> {
                        musicItems += MusicItem(
                            id = browseId,
                            title = title,
                            artist = "Artist",
                            duration = 0L,
                            thumbnailUrl = thumbnailUrl,
                            audioUrl = "",
                            videoUrl = "",
                            isLive = false,
                            artistId = browseId,
                            contentType = MusicContentType.ARTIST
                        )
                    }

                    pageType == MUSIC_PAGE_TYPE_PLAYLIST && !browseId.isNullOrBlank() -> {
                        // Browse ids for playlists carry a "VL" prefix that the
                        // playlist endpoint does not accept.
                        val playlistId = browseId.removePrefix("VL")
                        if (playlistId.startsWith("PL") ||
                            playlistId.startsWith("OLAK") ||
                            playlistId.startsWith("RD")
                        ) {
                            musicItems += MusicItem(
                                id = playlistId,
                                title = title,
                                artist = subtitleText(subtitleRuns) ?: "Playlist",
                                duration = 0L,
                                thumbnailUrl = thumbnailUrl,
                                audioUrl = "",
                                videoUrl = "",
                                isLive = false,
                                isPlaylist = true,
                                contentType = MusicContentType.PLAYLIST
                            )
                        }
                    }

                    else -> Log.w(
                        "ChartsExtractor",
                        "Skipping unsupported chart item: $title ($pageType)"
                    )
                }
            } catch (e: Exception) {
                // skip bad item
            }
        }
        return musicItems
    }

    private fun parseTitle(renderer: JsonObject): String {
        renderer.getAsJsonObject("title")
            ?.getAsJsonArray("runs")
            ?.firstOrNull()?.asJsonObject
            ?.get("text")?.asString
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }

        return renderer.getAsJsonArray("flexColumns")
            ?.firstOrNull()?.asJsonObject
            ?.getAsJsonObject("musicResponsiveListItemFlexColumnRenderer")
            ?.getAsJsonObject("text")
            ?.getAsJsonArray("runs")
            ?.firstOrNull()?.asJsonObject
            ?.get("text")?.asString
            .orEmpty()
    }

    private fun parseThumbnail(renderer: JsonObject): String {
        // Two-row items expose `thumbnailRenderer`, responsive rows `thumbnail`.
        val thumbnailNode = renderer.getAsJsonObject("thumbnailRenderer")
            ?: renderer.getAsJsonObject("thumbnail")
            ?: return ""
        return thumbnailNode
            .getAsJsonObject("musicThumbnailRenderer")
            ?.getAsJsonObject("thumbnail")
            ?.getAsJsonArray("thumbnails")
            ?.lastOrNull()?.asJsonObject
            ?.get("url")?.asString
            .orEmpty()
    }

    private fun parseSubtitleRuns(renderer: JsonObject): JsonArray? =
        renderer.getAsJsonObject("subtitle")?.getAsJsonArray("runs")
            ?: renderer.getAsJsonArray("flexColumns")
                ?.takeIf { it.size() > 1 }
                ?.get(1)?.asJsonObject
                ?.getAsJsonObject("musicResponsiveListItemFlexColumnRenderer")
                ?.getAsJsonObject("text")
                ?.getAsJsonArray("runs")

    private fun subtitleText(runs: JsonArray?): String? =
        runs?.firstOrNull()?.asJsonObject
            ?.get("text")?.asString
            ?.takeIf { it.isNotBlank() && it != " • " }
}
