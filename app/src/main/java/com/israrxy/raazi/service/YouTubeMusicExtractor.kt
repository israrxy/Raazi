package com.israrxy.raazi.service

import com.israrxy.raazi.model.MusicItem
import com.israrxy.raazi.model.Playlist
import com.israrxy.raazi.model.SearchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import android.util.Log
import okhttp3.RequestBody.Companion.toRequestBody

private const val TAG = "YouTubeMusicExtractor"


class YouTubeMusicExtractor {
    private val client: okhttp3.OkHttpClient

    init {
        client = okhttp3.OkHttpClient.Builder().build()
        
        val country = java.util.Locale.getDefault().country
        val language = java.util.Locale.getDefault().language
        val localization = org.schabi.newpipe.extractor.localization.Localization(country, language)
        val contentCountry = org.schabi.newpipe.extractor.localization.ContentCountry(country)

        NewPipe.init(
            object : org.schabi.newpipe.extractor.downloader.Downloader() {
                override fun execute(request: org.schabi.newpipe.extractor.downloader.Request): org.schabi.newpipe.extractor.downloader.Response {
                    val httpMethod = request.httpMethod()
                    val url = request.url()
                    val headers = request.headers()
                    val dataToSend = request.dataToSend()
                    
                    val requestBuilder = okhttp3.Request.Builder()
                        .url(url)
                        .method(httpMethod, dataToSend?.toRequestBody(null))

                    headers.forEach { (key, values) ->
                        values.forEach { value ->
                            requestBuilder.addHeader(key, value)
                        }
                    }
                    
                    // Add default headers if not present
                    if (headers["User-Agent"].isNullOrEmpty()) {
                        requestBuilder.addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                    }

                    Log.d(TAG, "Executing request: ${requestBuilder.build().url}")
                    val response = client.newCall(requestBuilder.build()).execute()
                    val responseBody = response.body?.string() ?: ""
                    val responseHeaders = response.headers.toMultimap()
                    val responseCode = response.code
                    val responseMessage = response.message
                    val latestUrl = response.request.url.toString()

                    Log.d(TAG, "Response code: $responseCode, Body length: ${responseBody.length}")
                    
                    return org.schabi.newpipe.extractor.downloader.Response(
                        responseCode,
                        responseMessage,
                        responseHeaders,
                        responseBody,
                        latestUrl
                    )
                }
            },
            localization,
            contentCountry
        )
        Log.d(TAG, "Localization set to: $country / $language")
        
        // Set InnerTube Locale for Home Feed
        com.zionhuang.innertube.YouTube.locale = com.zionhuang.innertube.models.YouTubeLocale(
            gl = country,
            hl = language
        )
    }

    suspend fun getSearchSuggestions(query: String): List<String> = withContext(Dispatchers.IO) {
        try {
            val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
            val url = "http://suggestqueries.google.com/complete/search?client=youtube&ds=yt&q=$encodedQuery"
            
            val request = okhttp3.Request.Builder()
                .url(url)
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string()

            if (response.isSuccessful && !body.isNullOrEmpty()) {
                // Response format: ["query", ["suggestion1", "suggestion2", ...], ...]
                // We need to parse this somewhat manually or use regex since it might be loose JSON
                val start = body.indexOf("[", body.indexOf("[") + 1)
                val end = body.lastIndexOf("]")
                
                if (start != -1 && end != -1) {
                    val arrayString = body.substring(start, end + 1) // Should be ["s1", "s2"]
                    // Simple cleaning for list of strings
                    return@withContext arrayString
                        .replace("[", "")
                        .replace("]", "")
                        .split(",")
                        .map { it.trim().replace("\"", "") }
                        .filter { it.isNotEmpty() }
                }
            }
            emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching suggestions", e)
            emptyList()
        }
    }

    suspend fun searchMusic(query: String): SearchResult = withContext(Dispatchers.IO) {
        try {
            val service = ServiceList.YouTube
            Log.d(TAG, "Searching for: $query")
            val searchResult = SearchInfo.getInfo(service, service.getSearchQHFactory().fromQuery(query))
            Log.d(TAG, "Search finished, processing results")
            
            val results = searchResult.relatedItems
                .mapNotNull { item ->
                    when (item) {
                        is StreamInfoItem -> {
                            if (item.streamType == org.schabi.newpipe.extractor.stream.StreamType.AUDIO_STREAM || 
                                item.streamType == org.schabi.newpipe.extractor.stream.StreamType.VIDEO_STREAM) {
                                MusicItem(
                                    id = item.getUrl(),
                                    title = item.name,
                                    artist = item.uploaderName ?: "Unknown Artist",
                                    duration = item.duration * 1000,
                                    thumbnailUrl = item.thumbnails.firstOrNull()?.getUrl() ?: "",
                                    audioUrl = "",
                                    videoUrl = item.getUrl(),
                                    isLive = false,
                                    isPlaylist = false
                                )
                            } else null
                        }
                        is org.schabi.newpipe.extractor.playlist.PlaylistInfoItem -> {
                            MusicItem(
                                id = item.url, // Playlist URL or ID
                                title = item.name,
                                artist = item.uploaderName ?: "Unknown",
                                duration = 0, // Playlist duration not always available easily
                                thumbnailUrl = item.thumbnails.firstOrNull()?.getUrl() ?: "",
                                audioUrl = "",
                                videoUrl = item.url,
                                isLive = false,
                                isPlaylist = true
                            )
                        }
                        is org.schabi.newpipe.extractor.channel.ChannelInfoItem -> {
                            // Treat Artist/Channel as a special item, or just link to their "Uploads" playlist if possible?
                            // For simplicity, we can treat it as a container or just ignore for now if UI doesn't support it strictly.
                            // But user asked for Artist search.
                            // Let's mark it as a playlist-like item or special type if we had one.
                            // For now, mapping as playlist (container) is safest without changing data model too much.
                            MusicItem(
                                id = item.url,
                                title = item.name,
                                artist = "Artist",
                                duration = 0,
                                thumbnailUrl = item.thumbnails.firstOrNull()?.getUrl() ?: "",
                                audioUrl = "",
                                videoUrl = item.url,
                                isLive = false,
                                isPlaylist = true // Opening channel usually lists videos, treat as playlist
                            )
                        }
                        else -> null
                    }
                }
            
            Log.d(TAG, "Found ${results.size} results")
            SearchResult(query, results)
        } catch (e: Exception) {
            Log.e(TAG, "Search failed", e)
            SearchResult(query, emptyList())
        }
    }

    suspend fun getPlaylist(playlistId: String): Playlist = withContext(Dispatchers.IO) {
        try {
            val service = ServiceList.YouTube
            val playlistUrl = "https://www.youtube.com/playlist?list=$playlistId"
            val playlistInfo = PlaylistInfo.getInfo(service, playlistUrl)
            
            val items = playlistInfo.relatedItems
                .filterIsInstance<StreamInfoItem>()
                .map { streamItem ->
                    MusicItem(
                        id = streamItem.getUrl(),
                        title = streamItem.name,
                        artist = streamItem.uploaderName ?: "Unknown Artist",
                        duration = streamItem.duration * 1000,
                        thumbnailUrl = streamItem.thumbnails.firstOrNull()?.getUrl() ?: "",
                        audioUrl = "",
                        videoUrl = streamItem.getUrl(),
                        isLive = false // Placeholder
                    )
                }
            
            Playlist(
                id = playlistId,
                title = playlistInfo.name,
                description = playlistInfo.description?.content ?: "",
                thumbnailUrl = playlistInfo.thumbnails.firstOrNull()?.url ?: "",
                items = items
            )
        } catch (e: Exception) {
            Playlist(
                id = playlistId,
                title = "Error Playlist",
                description = "Could not load playlist: ${e.message}",
                thumbnailUrl = "",
                items = emptyList()
            )
        }
    }

    suspend fun getAudioStreamUrl(videoUrl: String): String = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Extracting audio URL for: $videoUrl")
            
            // Convert video ID to full URL if needed
            val fullUrl = if (videoUrl.startsWith("http")) {
                videoUrl
            } else {
                "https://www.youtube.com/watch?v=$videoUrl"
            }
            
            val service = ServiceList.YouTube
            val streamInfo = StreamInfo.getInfo(service, fullUrl)
            
            // Get the best audio stream
            val audioStreams = streamInfo.audioStreams
            Log.d(TAG, "Found ${audioStreams.size} audio streams")
            
            if (audioStreams.isNotEmpty()) {
                val bestStream = audioStreams.maxByOrNull { it.averageBitrate }
                val url = bestStream?.url ?: ""
                Log.d(TAG, "Selected audio stream URL: ${url.take(100)}...")
                return@withContext url
            } else {
                Log.e(TAG, "No audio streams available for: $videoUrl")
                ""
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract audio URL for: $videoUrl", e)
            ""
        }
    }

    suspend fun getTopSongs(): SearchResult = withContext(Dispatchers.IO) {
        try {
            // Global Top Songs Playlist
            val playlistId = "PL4fGSI1pDJn69On1fCwI33tF3oqSHqKBV" 
            val playlist = getPlaylist(playlistId)
            
            if (playlist.items.isNotEmpty()) {
                return@withContext SearchResult("Top Songs Global", playlist.items)
            }
            throw Exception("Empty Top Songs playlist")
        } catch (e: Exception) {
            Log.w(TAG, "Top Songs fetch failed, trying fallback", e)
            try {
                return@withContext searchMusic("Top 100 Songs Global")
            } catch (e2: Exception) {
                 return@withContext SearchResult("Top Songs Global", emptyList())
            }
        }
    }

    suspend fun getTrendingVideos(): SearchResult = withContext(Dispatchers.IO) {
        try {
            // Trending Music Videos Playlist
            // Alternative: PLrEnWoR732-BHrPp_Pm8_VleD92Pxi9vE (Trending)
            // Or PL4fGSI1pDJn5kI81J1tGMfH_PIShG6P_m (Top Music Videos Global)
            val playlistId = "PL4fGSI1pDJn5kI81J1tGMfH_PIShG6P_m" 
            val playlist = getPlaylist(playlistId)
            
            if (playlist.items.isNotEmpty()) {
                return@withContext SearchResult("Trending Music Videos", playlist.items)
            }
            throw Exception("Empty Trending playlist")
        } catch (e: Exception) {
             Log.w(TAG, "Trending fetch failed, trying fallback", e)
            try {
                return@withContext searchMusic("Trending Music Videos")
            } catch (e2: Exception) {
                 return@withContext SearchResult("Trending Music Videos", emptyList())
            }
        }
    }

    // Deprecated wrapper, points to Top Songs
    suspend fun getTrending(): SearchResult {
        return getTopSongs()
    }
    
    // Moods
    suspend fun getMoodPlaylists(mood: String): List<Playlist> = withContext(Dispatchers.IO) {
        val query = when (mood.lowercase()) {
            "relax" -> "Relaxing music playlist"
            "energize" -> "High energy music playlist"
            "workout" -> "Workout music playlist"
            "focus" -> "Focus music playlist"
            "commute" -> "Commute songs playlist"
            "party" -> "Party music playlist"
            "romance" -> "Romance music playlist"
            "sleep" -> "Sleep music playlist"
            else -> "$mood music playlist"
        }
        
        // 1. Try generic search for playlists
        try {
            val service = ServiceList.YouTube
            val search = SearchInfo.getInfo(service, service.getSearchQHFactory().fromQuery(query))
            
            // Get top 5 playlists
            val playlists = search.relatedItems
                .filterIsInstance<org.schabi.newpipe.extractor.playlist.PlaylistInfoItem>()
                .take(5)
                .map { item ->
                     // Convert to light Playlist object (without tracks initially to save bandwidth)
                     Playlist(
                        id = item.url,
                        title = item.name,
                        description = "By ${item.uploaderName}",
                        thumbnailUrl = item.thumbnails.firstOrNull()?.getUrl() ?: "",
                        items = emptyList() // Will be loaded when clicked
                     )
                }
            
            return@withContext playlists
        } catch(e: Exception) {
             Log.e(TAG, "Mood search failed for $mood", e)
        }
        return@withContext emptyList()
    }
    
    suspend fun getNewReleaseAlbums(): List<Playlist> = withContext(Dispatchers.IO) {
        try {
            val service = ServiceList.YouTube
            // Search for "New Music Albums" to simulate "Albums for you"
            val search = SearchInfo.getInfo(service, service.getSearchQHFactory().fromQuery("New Music Albums"))
            
            return@withContext search.relatedItems
                .filterIsInstance<org.schabi.newpipe.extractor.playlist.PlaylistInfoItem>()
                .take(10)
                .map { item ->
                    Playlist(
                        id = item.url,
                        title = item.name,
                        description = item.uploaderName ?: "Unknown Artist",
                        thumbnailUrl = item.thumbnails.firstOrNull()?.getUrl() ?: "",
                        items = emptyList()
                    )
                }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch new albums", e)
            emptyList()
        }
    }
    
    suspend fun getUpNext(videoId: String): List<MusicItem> = withContext(Dispatchers.IO) {
        try {
            val service = ServiceList.YouTube
            // StreamInfo.getInfo expects a URL, not just ID, and positional args
            val url = if (videoId.startsWith("http")) videoId else "https://www.youtube.com/watch?v=$videoId" 
            val streamInfo = StreamInfo.getInfo(service, url)
            
            return@withContext streamInfo.relatedItems
                .filterIsInstance<StreamInfoItem>()
                .map { item ->
                    MusicItem(
                        id = item.getUrl(),
                        title = item.name,
                        artist = item.uploaderName ?: "Unknown",
                        duration = item.duration * 1000,
                        thumbnailUrl = item.thumbnails.firstOrNull()?.getUrl() ?: "",
                        audioUrl = "",
                        videoUrl = item.getUrl(),
                        isLive = false
                    )
                }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch Up Next for $videoId", e)
            emptyList()
        }
    }

    // Alias for backward compatibility if needed, or remove
    suspend fun getGenrePlaylists(genre: String): List<Playlist> {
        return getMoodPlaylists(genre)
    }

    // ============================================================================================
    // Real Home Feed Integration (InnerTube)
    // ============================================================================================

    suspend fun getHomeSections(): List<SearchResult> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Fetching Home Feed via InnerTube...")
            
            // Fetch from InnerTube (like OuterTune does)
            val homePage = com.zionhuang.innertube.YouTube.home().getOrNull()
            
            if (homePage == null) {
                Log.e(TAG, "InnerTube.home() returned null")
                return@withContext emptyList()
            }
            
            Log.d(TAG, "InnerTube returned ${homePage.sections.size} sections")
            
            // Map all sections to SearchResult
            val sections = homePage.sections.mapNotNull { section ->
                val items = section.items.mapNotNull { mapYTItemToMusicItem(it) }
                if (items.isNotEmpty()) {
                    SearchResult(section.title, items)
                } else {
                    null
                }
            }
            
            Log.d(TAG, "Returning ${sections.size} non-empty sections")
            sections
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching home sections", e)
            emptyList()
        }
    }

    private fun mapYTItemToMusicItem(item: com.zionhuang.innertube.models.YTItem): MusicItem? {
        // Import models locally or use fully qualified names if conflicts exist
        return when (item) {
            is com.zionhuang.innertube.models.SongItem -> MusicItem(
                id = item.id ?: return null,
                title = item.title ?: "Unknown",
                artist = item.artists?.joinToString(", ") { it.name ?: "" } ?: "Unknown Artist",
                duration = (item.duration?.toLong() ?: 0) * 1000L,
                thumbnailUrl = item.thumbnail ?: "",
                audioUrl = "",
                videoUrl = item.id ?: return null,
                isLive = false
            )
            is com.zionhuang.innertube.models.AlbumItem -> MusicItem(
                id = item.browseId ?: return null,
                title = item.title ?: "Unknown",
                artist = item.artists?.joinToString(", ") { it.name ?: "" } ?: "Unknown",
                duration = 0,
                thumbnailUrl = item.thumbnail ?: "",
                audioUrl = "",
                videoUrl = item.browseId ?: return null,
                isLive = false,
                isPlaylist = true
            )
            is com.zionhuang.innertube.models.PlaylistItem -> MusicItem(
                id = item.id ?: return null,
                title = item.title ?: "Unknown",
                artist = item.author?.name ?: "Unknown",
                duration = 0,
                thumbnailUrl = item.thumbnail ?: "",
                audioUrl = "",
                videoUrl = item.id ?: return null,
                isLive = false,
                isPlaylist = true
            )
            else -> null
        }
    }
}