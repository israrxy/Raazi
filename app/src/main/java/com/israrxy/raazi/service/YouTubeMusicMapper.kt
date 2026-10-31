package com.israrxy.raazi.service

import android.util.Log
import com.israrxy.raazi.model.MusicContentType
import com.israrxy.raazi.model.MusicItem
import com.israrxy.raazi.model.Playlist
import com.israrxy.raazi.model.SearchResult
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem

private const val MAPPER_TAG = "YouTubeMusicMapper"

internal fun normalizeYouTubePlaylistId(playlistIdOrUrl: String): String {
    val trimmed = playlistIdOrUrl.trim()
    val listId = Regex("[?&]list=([^&#]+)").find(trimmed)?.groupValues?.getOrNull(1)
    return listId?.let { java.net.URLDecoder.decode(it, java.nio.charset.StandardCharsets.UTF_8.name()) }
        ?: trimmed.removePrefix("VL")
}

internal fun initializeNewPipeAndLocale(client: OkHttpClient) {
    val country = java.util.Locale.getDefault().country
    val language = java.util.Locale.getDefault().language
    val localization = org.schabi.newpipe.extractor.localization.Localization(country, language)
    val contentCountry = org.schabi.newpipe.extractor.localization.ContentCountry(country)

    try {
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

                    if (headers["User-Agent"].isNullOrEmpty()) {
                        requestBuilder.addHeader(
                            "User-Agent",
                            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/136.0.0.0 Safari/537.36"
                        )
                    }

                    val response = client.newCall(requestBuilder.build()).execute()
                    val responseBody = response.body?.string() ?: ""
                    val responseHeaders = response.headers.toMultimap()
                    val responseCode = response.code
                    val responseMessage = response.message
                    val latestUrl = response.request.url.toString()

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
    } catch (e: Exception) {
        Log.w(MAPPER_TAG, "NewPipe may already be initialized", e)
    }

    com.zionhuang.innertube.YouTube.locale = com.zionhuang.innertube.models.YouTubeLocale(
        gl = country,
        hl = language
    )
}

internal fun executeNewPipeSearch(query: String, serviceId: Int): SearchResult {
    val service = when (serviceId) {
        1 -> ServiceList.SoundCloud
        2 -> ServiceList.Bandcamp
        else -> ServiceList.YouTube
    }

    Log.d(MAPPER_TAG, "NewPipe searching for: $query on ${service.serviceInfo.name}")
    val searchResult = SearchInfo.getInfo(service, service.getSearchQHFactory().fromQuery(query))

    val results = searchResult.relatedItems
        .mapNotNull { item ->
            when (item) {
                is StreamInfoItem -> {
                    if (item.streamType == org.schabi.newpipe.extractor.stream.StreamType.AUDIO_STREAM ||
                        item.streamType == org.schabi.newpipe.extractor.stream.StreamType.VIDEO_STREAM ||
                        serviceId == 1 || serviceId == 2
                    ) {
                        val url = item.getUrl()
                        if (url.isNullOrEmpty()) return@mapNotNull null

                        val (itemId, videoUrl) = when {
                            serviceId == 1 || serviceId == 2 -> Pair(url, url)
                            url.contains("youtube.com") || url.contains("youtu.be") -> {
                                val extractedId = when {
                                    url.contains("v=") -> url.substringAfter("v=").substringBefore("&")
                                    url.contains("youtu.be/") -> url.substringAfter("youtu.be/").substringBefore("?")
                                    else -> null
                                }
                                if (extractedId.isNullOrEmpty()) return@mapNotNull null
                                Pair(extractedId, extractedId)
                            }
                            else -> Pair(url, url)
                        }
                        val contentType = when {
                            serviceId == 1 || serviceId == 2 -> MusicContentType.SONG
                            item.streamType == org.schabi.newpipe.extractor.stream.StreamType.VIDEO_STREAM -> MusicContentType.VIDEO
                            else -> MusicContentType.SONG
                        }

                        MusicItem(
                            id = itemId,
                            title = item.name,
                            artist = item.uploaderName ?: "Unknown Artist",
                            duration = item.duration * 1000,
                            thumbnailUrl = item.thumbnails.firstOrNull()?.getUrl() ?: "",
                            audioUrl = "",
                            videoUrl = videoUrl,
                            isLive = false,
                            isPlaylist = false,
                            contentType = contentType
                        )
                    } else null
                }
                is org.schabi.newpipe.extractor.playlist.PlaylistInfoItem -> {
                    MusicItem(
                        id = item.url,
                        title = item.name,
                        artist = item.uploaderName ?: "Unknown",
                        duration = 0,
                        thumbnailUrl = item.thumbnails.firstOrNull()?.getUrl() ?: "",
                        audioUrl = "",
                        videoUrl = item.url,
                        isLive = false,
                        isPlaylist = true,
                        contentType = MusicContentType.PLAYLIST
                    )
                }
                is org.schabi.newpipe.extractor.channel.ChannelInfoItem -> {
                    val url = item.url
                    val id = when {
                        url.contains("/channel/") -> url.substringAfter("/channel/")
                        url.contains("/user/") -> url.substringAfter("/user/")
                        url.contains("/c/") -> url.substringAfter("/c/")
                        else -> url
                    }

                    MusicItem(
                        id = id,
                        title = item.name,
                        artist = "Artist",
                        duration = 0,
                        thumbnailUrl = item.thumbnails.firstOrNull()?.getUrl() ?: "",
                        audioUrl = "",
                        videoUrl = item.url,
                        isLive = false,
                        isPlaylist = false,
                        artistId = id,
                        contentType = MusicContentType.ARTIST
                    )
                }
                else -> null
            }
        }

    return SearchResult(query, results)
}

internal fun fetchNewPipePlaylistFallback(normalizedPlaylistId: String): Playlist {
    return try {
        val service = ServiceList.YouTube
        val playlistUrl = "https://www.youtube.com/playlist?list=$normalizedPlaylistId"
        val playlistInfo = PlaylistInfo.getInfo(service, playlistUrl)

        val items = playlistInfo.relatedItems
            .filterIsInstance<StreamInfoItem>()
            .filter { item ->
                val url = item.getUrl()
                !url.equals("https://www.youtube.com/watch", ignoreCase = true) &&
                    !url.equals("https://m.youtube.com/watch", ignoreCase = true) &&
                    url.length > 25
            }
            .map { streamItem ->
                MusicItem(
                    id = streamItem.getUrl(),
                    title = streamItem.name,
                    artist = streamItem.uploaderName ?: "Unknown Artist",
                    duration = streamItem.duration * 1000,
                    thumbnailUrl = streamItem.thumbnails.firstOrNull()?.getUrl() ?: "",
                    audioUrl = "",
                    videoUrl = streamItem.getUrl(),
                    isLive = false
                )
            }

        Playlist(
            id = normalizedPlaylistId,
            title = playlistInfo.name,
            description = playlistInfo.description?.content ?: "",
            thumbnailUrl = playlistInfo.thumbnails.firstOrNull()?.url ?: "",
            items = items
        )
    } catch (e: Exception) {
        Playlist(
            id = normalizedPlaylistId,
            title = "Error Playlist",
            description = "Could not load playlist: ${e.message}",
            thumbnailUrl = "",
            items = emptyList()
        )
    }
}

internal fun convertYTItemToMusicItem(
    item: com.zionhuang.innertube.models.YTItem,
    overrideContentType: MusicContentType? = null
): MusicItem? {
    return when (item) {
        is com.zionhuang.innertube.models.SongItem -> {
            var id = item.id ?: return null
            if (id.startsWith("http") ||
                id.contains("youtube.com") ||
                id.contains("/watch") ||
                item.title.equals("Start Radio", ignoreCase = true)
            ) {
                val thumbnail = item.thumbnail
                var rescuedId: String? = null
                if (thumbnail != null && thumbnail.contains("/vi/")) {
                    val pattern = "vi/([a-zA-Z0-9_-]{11})".toRegex()
                    val match = pattern.find(thumbnail)
                    if (match != null) {
                        rescuedId = match.groupValues[1]
                        id = rescuedId
                    }
                }
                if (rescuedId == null) return null
            }
            MusicItem(
                id = id,
                title = item.title ?: "Unknown",
                artist = item.artists.takeIf { it.isNotEmpty() }?.joinToString(", ") { it.name } ?: "Unknown Artist",
                duration = (item.duration?.toLong() ?: 0) * 1000L,
                thumbnailUrl = item.thumbnail ?: "",
                audioUrl = "",
                videoUrl = id,
                isLive = false,
                artistId = item.artists.firstOrNull()?.id,
                contentType = overrideContentType ?: if (item.isVideo) MusicContentType.VIDEO else MusicContentType.SONG,
                setVideoId = item.setVideoId,
                viewsText = item.viewsText,
                albumTitle = item.album?.name
            )
        }
        is com.zionhuang.innertube.models.AlbumItem -> MusicItem(
            id = item.browseId ?: return null,
            title = item.title ?: "Unknown",
            artist = item.artists?.joinToString(", ") { it.name ?: "" } ?: "Unknown",
            duration = 0,
            thumbnailUrl = item.thumbnail ?: "",
            audioUrl = "",
            videoUrl = item.browseId ?: return null,
            isLive = false,
            isPlaylist = true,
            contentType = overrideContentType ?: MusicContentType.ALBUM
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
            isPlaylist = true,
            contentType = overrideContentType ?: MusicContentType.PLAYLIST
        )
        is com.zionhuang.innertube.models.ArtistItem -> MusicItem(
            id = item.id,
            title = item.title,
            artist = "Artist",
            duration = 0L,
            thumbnailUrl = item.thumbnail ?: "",
            audioUrl = "",
            videoUrl = item.shareLink,
            isLive = false,
            isPlaylist = false,
            artistId = item.id,
            contentType = overrideContentType ?: MusicContentType.ARTIST
        )
        else -> null
    }
}



