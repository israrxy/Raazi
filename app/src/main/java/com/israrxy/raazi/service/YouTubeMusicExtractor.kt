package com.israrxy.raazi.service

import android.util.Log
import com.israrxy.raazi.model.MusicContentType
import com.israrxy.raazi.model.MusicItem
import com.israrxy.raazi.model.Playlist
import com.israrxy.raazi.model.SearchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.search.SearchInfo

private const val TAG = "YouTubeMusicExtractor"

class YouTubeMusicExtractor private constructor() {
    private val client: okhttp3.OkHttpClient = okhttp3.OkHttpClient.Builder().build()

    companion object {
        @Volatile
        private var INSTANCE: YouTubeMusicExtractor? = null

        fun getInstance(): YouTubeMusicExtractor {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: YouTubeMusicExtractor().also { INSTANCE = it }
            }
        }
    }

    init {
        initializeNewPipeAndLocale(client)
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
                val start = body.indexOf("[", body.indexOf("[") + 1)
                val end = body.lastIndexOf("]")

                if (start != -1 && end != -1) {
                    val arrayString = body.substring(start, end + 1)
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

    suspend fun searchMusic(query: String, serviceId: Int = 0): SearchResult = withContext(Dispatchers.IO) {
        try {
            if (serviceId == 0) {
                val summaryJob = async {
                    com.zionhuang.innertube.YouTube.searchSummary(query).getOrNull()
                }
                val songResults = async {
                    com.zionhuang.innertube.YouTube.search(
                        query,
                        filter = com.zionhuang.innertube.YouTube.SearchFilter.FILTER_SONG
                    ).getOrNull()?.items.orEmpty()
                        .mapNotNull { convertYTItemToMusicItem(it, overrideContentType = MusicContentType.SONG) }
                }
                val videoResults = async {
                    com.zionhuang.innertube.YouTube.search(
                        query,
                        filter = com.zionhuang.innertube.YouTube.SearchFilter.FILTER_VIDEO
                    ).getOrNull()?.items.orEmpty()
                        .mapNotNull { convertYTItemToMusicItem(it, overrideContentType = MusicContentType.VIDEO) }
                }
                val artistResults = async {
                    com.zionhuang.innertube.YouTube.search(
                        query,
                        filter = com.zionhuang.innertube.YouTube.SearchFilter.FILTER_ARTIST
                    ).getOrNull()?.items.orEmpty()
                        .mapNotNull { convertYTItemToMusicItem(it, overrideContentType = MusicContentType.ARTIST) }
                }
                val albumResults = async {
                    com.zionhuang.innertube.YouTube.search(
                        query,
                        filter = com.zionhuang.innertube.YouTube.SearchFilter.FILTER_ALBUM
                    ).getOrNull()?.items.orEmpty()
                        .mapNotNull { convertYTItemToMusicItem(it, overrideContentType = MusicContentType.ALBUM) }
                }
                val playlistResults = async {
                    (
                        com.zionhuang.innertube.YouTube.search(
                            query,
                            filter = com.zionhuang.innertube.YouTube.SearchFilter.FILTER_FEATURED_PLAYLIST
                        ).getOrNull()?.items.orEmpty() +
                            com.zionhuang.innertube.YouTube.search(
                                query,
                                filter = com.zionhuang.innertube.YouTube.SearchFilter.FILTER_COMMUNITY_PLAYLIST
                            ).getOrNull()?.items.orEmpty()
                        )
                        .mapNotNull { convertYTItemToMusicItem(it, overrideContentType = MusicContentType.PLAYLIST) }
                        .distinctBy { it.id }
                }

                val summary = summaryJob.await()
                val summaryTopTracks = mutableListOf<MusicItem>()
                val summaryAllItems = mutableListOf<MusicItem>()

                if (summary != null) {
                    summary.summaries.forEach { section ->
                        val isSongsSection = section.title.equals("Songs", ignoreCase = true)
                        val isTopResultSection = section.title.contains("Top result", ignoreCase = true) || section.title.isBlank()
                        val overrideType = when {
                            isSongsSection -> MusicContentType.SONG
                            section.title.equals("Videos", ignoreCase = true) -> MusicContentType.VIDEO
                            section.title.equals("Artists", ignoreCase = true) -> MusicContentType.ARTIST
                            section.title.equals("Albums", ignoreCase = true) -> MusicContentType.ALBUM
                            section.title.contains("playlist", ignoreCase = true) -> MusicContentType.PLAYLIST
                            else -> null
                        }

                        val mappedItems = section.items.mapNotNull { ytItem ->
                            convertYTItemToMusicItem(ytItem, overrideContentType = overrideType)
                        }

                        if (isTopResultSection || isSongsSection) {
                            mappedItems.forEach { item ->
                                if (item.contentType == MusicContentType.SONG ||
                                    (item.contentType == MusicContentType.UNKNOWN && !item.isPlaylist)
                                ) {
                                    summaryTopTracks.add(item)
                                }
                            }
                        }

                        summaryAllItems.addAll(mappedItems)
                    }
                }

                val typedSongs = songResults.await()
                val typedVideos = videoResults.await()
                val typedArtists = artistResults.await()
                val typedAlbums = albumResults.await()
                val typedPlaylists = playlistResults.await()

                val finalTopTracks = (summaryTopTracks.ifEmpty { typedSongs.take(10) })
                    .distinctBy { it.id }

                val combinedResults = (summaryAllItems + typedSongs + typedVideos + typedArtists + typedAlbums + typedPlaylists)
                    .distinctBy { "${it.contentType}:${it.id}" }

                Log.d(TAG, "InnerTube search returned ${combinedResults.size} results, ${finalTopTracks.size} top tracks")
                if (combinedResults.isNotEmpty()) {
                    return@withContext SearchResult(
                        query = query,
                        items = combinedResults,
                        topTracks = finalTopTracks
                    )
                }

                Log.w(TAG, "InnerTube search empty for '$query', falling back to NewPipe")
                val newpipeResults = executeNewPipeSearch(query, serviceId)
                if (newpipeResults.items.isNotEmpty()) {
                    return@withContext newpipeResults
                }
            }

            executeNewPipeSearch(query, serviceId)
        } catch (e: Exception) {
            Log.e(TAG, "Search failed", e)
            SearchResult(query, emptyList())
        }
    }

    suspend fun getPlaylist(playlistId: String): Playlist = withContext(Dispatchers.IO) {
        val normalizedPlaylistId = normalizeYouTubePlaylistId(playlistId)
        try {
            if (normalizedPlaylistId.startsWith("MPREb")) {
                val album = com.zionhuang.innertube.YouTube.album(normalizedPlaylistId).getOrThrow()
                val items = album.songs.mapNotNull { song ->
                    MusicItem(
                        id = song.id ?: return@mapNotNull null,
                        title = song.title ?: "Unknown",
                        artist = song.artists?.joinToString(", ") { it.name ?: "" } ?: "Unknown",
                        duration = (song.duration?.toLong() ?: 0) * 1000L,
                        thumbnailUrl = song.thumbnail ?: "",
                        audioUrl = "",
                        videoUrl = song.id ?: return@mapNotNull null,
                        isLive = false
                    )
                }
                return@withContext Playlist(
                    id = normalizedPlaylistId,
                    title = items.firstOrNull()?.artist?.let { "$it Album" } ?: "Album",
                    description = "",
                    thumbnailUrl = items.firstOrNull()?.thumbnailUrl ?: "",
                    items = items
                )
            } else if (normalizedPlaylistId.startsWith("PL") || normalizedPlaylistId.startsWith("OLAK") ||
                normalizedPlaylistId.startsWith("UU") || normalizedPlaylistId.startsWith("FL") ||
                normalizedPlaylistId.startsWith("RD")
            ) {
                val playlist = com.zionhuang.innertube.YouTube.playlist(normalizedPlaylistId).getOrThrow()
                val items = playlist.songs.mapNotNull { convertYTItemToMusicItem(it) }
                return@withContext Playlist(
                    id = normalizedPlaylistId,
                    title = "Playlist",
                    description = "",
                    thumbnailUrl = items.firstOrNull()?.thumbnailUrl ?: "",
                    items = items
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "InnerTube failed for $normalizedPlaylistId", e)
        }

        fetchNewPipePlaylistFallback(normalizedPlaylistId)
    }

    suspend fun getAudioStreamUrl(videoUrl: String): String = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Extracting audio URL for: $videoUrl")
            val resolved = com.israrxy.raazi.player.StreamResolver.resolveStreamUrl(videoUrl)
            if (resolved.url.isNotBlank()) {
                return@withContext resolved.url
            }
            ""
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract audio URL for: $videoUrl", e)
            ""
        }
    }

    suspend fun getTopSongs(): SearchResult = withContext(Dispatchers.IO) {
        try {
            val playlistId = "PL4fGSI1pDJn6puJdseH2Rt9sMvt9E2M4i"
            val playlist = getPlaylist(playlistId)
            if (playlist.items.isNotEmpty()) {
                return@withContext SearchResult("Top Songs Global", playlist.items, topTracks = playlist.items)
            }
            throw Exception("Empty Top Songs playlist")
        } catch (e: Exception) {
            Log.w(TAG, "Top Songs fetch failed, trying fallback", e)
            try {
                searchMusic("Top 100 Songs Global")
            } catch (_: Exception) {
                SearchResult("Top Songs Global", emptyList())
            }
        }
    }

    suspend fun getTrendingVideos(): SearchResult = withContext(Dispatchers.IO) {
        try {
            val playlistId = "PL4fGSI1pDJn3-A5n37Ua2u14s141v09fG"
            val playlist = getPlaylist(playlistId)
            if (playlist.items.isNotEmpty()) {
                return@withContext SearchResult("Trending Music Videos", playlist.items)
            }
            throw Exception("Empty Trending playlist")
        } catch (e: Exception) {
            Log.w(TAG, "Trending fetch failed, trying fallback", e)
            try {
                searchMusic("Trending Music Videos")
            } catch (_: Exception) {
                SearchResult("Trending Music Videos", emptyList())
            }
        }
    }

    suspend fun getTrending(): SearchResult = getTopSongs()

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

        try {
            val service = ServiceList.YouTube
            val search = SearchInfo.getInfo(service, service.getSearchQHFactory().fromQuery(query))

            return@withContext search.relatedItems
                .filterIsInstance<org.schabi.newpipe.extractor.playlist.PlaylistInfoItem>()
                .take(5)
                .map { item ->
                    Playlist(
                        id = normalizeYouTubePlaylistId(item.url),
                        title = item.name,
                        description = "By ${item.uploaderName}",
                        thumbnailUrl = item.thumbnails.firstOrNull()?.getUrl() ?: "",
                        items = emptyList()
                    )
                }
        } catch (e: Exception) {
            Log.e(TAG, "Mood search failed for $mood", e)
            emptyList()
        }
    }

    suspend fun getNewReleaseAlbums(): List<Playlist> = withContext(Dispatchers.IO) {
        try {
            val service = ServiceList.YouTube
            val search = SearchInfo.getInfo(service, service.getSearchQHFactory().fromQuery("New Music Albums"))

            search.relatedItems
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
            val cleanId = videoId.trim().let { raw ->
                when {
                    raw.matches(Regex("^[a-zA-Z0-9_-]{11}$")) -> raw
                    raw.contains("v=") -> raw.substringAfter("v=").substringBefore("&").take(11)
                    raw.contains("youtu.be/") -> raw.substringAfter("youtu.be/").substringBefore("?").take(11)
                    else -> raw
                }
            }

            val nextResult = com.zionhuang.innertube.YouTube.next(
                com.zionhuang.innertube.models.WatchEndpoint(
                    videoId = cleanId,
                    playlistId = "RDAMVM$cleanId"
                )
            ).getOrNull() ?: com.zionhuang.innertube.YouTube.next(
                com.zionhuang.innertube.models.WatchEndpoint(videoId = cleanId)
            ).getOrNull()

            if (nextResult != null && nextResult.items.isNotEmpty()) {
                val mapped = nextResult.items
                    .mapNotNull { convertYTItemToMusicItem(it) }
                    .filter { it.id != cleanId }
                if (mapped.isNotEmpty()) {
                    return@withContext mapped
                }
            }

            val currentSongTitle = nextResult?.items?.firstOrNull()?.title
            val currentSongArtist = nextResult?.items?.firstOrNull()?.artists?.firstOrNull()?.name
            if (!currentSongTitle.isNullOrBlank()) {
                val query = listOfNotNull(currentSongArtist, currentSongTitle, "mix").joinToString(" ")
                val searchRes = searchMusic(query).items.filter { it.id != cleanId }
                if (searchRes.isNotEmpty()) {
                    return@withContext searchRes
                }
            }

            emptyList()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to fetch Up Next for $videoId: ${e.message}")
            emptyList()
        }
    }

    suspend fun getGenrePlaylists(genre: String): List<Playlist> = getMoodPlaylists(genre)

    suspend fun getHomeSections(): List<SearchResult> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Fetching Home Feed via InnerTube...")
            val homePage = com.zionhuang.innertube.YouTube.home().getOrNull()
            if (homePage == null) {
                Log.e(TAG, "InnerTube.home() returned null")
                return@withContext emptyList()
            }

            homePage.sections.mapNotNull { section ->
                val items = section.items.mapNotNull { convertYTItemToMusicItem(it) }
                if (items.isNotEmpty()) {
                    SearchResult(section.title, items)
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching home sections", e)
            emptyList()
        }
    }
}
