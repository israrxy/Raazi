package com.israrxy.raazi.service

import android.net.Uri
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import com.israrxy.raazi.model.MusicItem
import com.israrxy.raazi.model.PlaybackMediaMode
import com.israrxy.raazi.player.StreamResolver
import com.zionhuang.innertube.models.YouTubeClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

internal fun MusicPlaybackService.createMediaItem(musicItem: MusicItem): MediaItem {
    val rawSource = musicItem.videoUrl.ifBlank { musicItem.id }
    val isExternalPlatform = rawSource.contains("soundcloud.com", ignoreCase = true) ||
        rawSource.contains("bandcamp.com", ignoreCase = true)
    val isYouTubeId = musicItem.id.matches(Regex("^[a-zA-Z0-9_-]{11}$"))
    val source = when {
        isExternalPlatform -> rawSource
        isYouTubeId && (rawSource.isBlank() || rawSource == "watch" || !rawSource.startsWith("http")) -> musicItem.id
        else -> rawSource.ifBlank { musicItem.id }
    }

    val sourceUri = when {
        musicItem.localPath != null && File(musicItem.localPath).exists() -> {
            Uri.fromFile(File(musicItem.localPath)).toString()
        }
        else -> {
            buildResolverUri(source, musicItem.id, musicItem.title, musicItem.artist, musicItem.isrc)
        }
    }

    return MediaItem.Builder()
        .setUri(sourceUri)
        .setMediaId(musicItem.id.ifBlank { source })
        .setTag(musicItem)
        .build()
}

internal fun MusicPlaybackService.buildResolverUri(
    source: String,
    trackId: String,
    title: String,
    artist: String,
    isrc: String?
): String {
    return Uri.Builder()
        .scheme("raazi")
        .authority("youtube")
        .appendPath(source)
        .appendQueryParameter("id", trackId)
        .appendQueryParameter("title", title)
        .appendQueryParameter("artist", artist)
        .appendQueryParameter("mode", mediaMode.name)
        .appendQueryParameter("quality", videoQuality.wireName)
        .apply {
            if (!isrc.isNullOrBlank()) {
                appendQueryParameter("isrc", isrc)
            }
        }
        .build()
        .toString()
}

internal fun MusicPlaybackService.preloadAdjacentTracks(centerIndex: Int) {
    if (currentPlaylist.isEmpty()) return

    listOf(centerIndex - 1, centerIndex + 1, centerIndex + 2)
        .distinct()
        .forEach { index ->
            val track = currentPlaylist.getOrNull(index) ?: return@forEach
            if (track.localPath != null && File(track.localPath).exists()) return@forEach
            val sourceId = track.videoUrl.ifBlank { track.id }
            if (sourceId.isBlank()) return@forEach
            StreamResolver.preloadStream(
                videoIdInput = sourceId,
                title = track.title,
                artist = track.artist
            )
        }
}

internal fun MusicPlaybackService.createDataSourceFactory(): DataSource.Factory {
    val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .retryOnConnectionFailure(true)
        .build()
    val okHttpDataSourceFactory = OkHttpDataSource.Factory(okHttpClient)
        .setDefaultRequestProperties(
            mapOf("User-Agent" to YouTubeClient.ANDROID_VR_NO_AUTH.userAgent)
        )

    val upstreamFactory = DefaultDataSource.Factory(this, okHttpDataSourceFactory)

    return ResolvingDataSource.Factory(upstreamFactory) { dataSpec ->
        val isRaaziScheme = dataSpec.uri.scheme == "raazi" && dataSpec.uri.host == "youtube"
        val queryTrackId = dataSpec.uri.getQueryParameter("id")?.takeIf { it.isNotBlank() }
        var videoId = dataSpec.key ?: if (isRaaziScheme) {
            val encodedId = dataSpec.uri.encodedPath?.removePrefix("/")
                ?: dataSpec.uri.lastPathSegment
            encodedId?.let { Uri.decode(it) }
        } else null

        if ((videoId.isNullOrBlank() || videoId == "watch") && !queryTrackId.isNullOrBlank()) {
            videoId = queryTrackId
        }

        Log.d(MusicPlaybackService.TAG, "Extracted videoId from dataSpec: $videoId")

        if (!videoId.isNullOrEmpty()) {
            if (videoId.contains("soundcloud.com") || videoId.contains("bandcamp.com")) {
                Log.d(MusicPlaybackService.TAG, "Detected SoundCloud/Bandcamp URL, keeping full URL: $videoId")
            }
        } else if (videoId.isNullOrEmpty()) {
            val uriString = dataSpec.uri.toString()
            if (uriString.startsWith("http")) {
                videoId = uriString
                Log.d(MusicPlaybackService.TAG, "No key found, using URI as videoId: $videoId")
            }
        }

        val host = dataSpec.uri.host?.lowercase() ?: ""
        val isResolvableUrl = videoId?.contains("youtube.com") == true || videoId?.contains("youtu.be") == true ||
            host.contains("youtube.com") || host.contains("youtu.be") ||
            host.contains("soundcloud.com") || host.contains("bandcamp.com")

        if (!videoId.isNullOrEmpty() && videoId != "watch" && (isRaaziScheme || isResolvableUrl)) {
            try {
                val title = dataSpec.uri.getQueryParameter("title")
                val artist = dataSpec.uri.getQueryParameter("artist")
                val isrc = dataSpec.uri.getQueryParameter("isrc")
                val modeParam = dataSpec.uri.getQueryParameter("mode")
                val effectiveMode = PlaybackMediaMode.values()
                    .firstOrNull { it.name.equals(modeParam, ignoreCase = true) }
                    ?: mediaMode
                val isActiveTrack = queryTrackId == null || currentPlaylist.getOrNull(currentIndex)?.id == queryTrackId

                val result = StreamResolver.resolveStreamUrl(
                    videoIdInput = videoId!!,
                    title = title,
                    artist = artist,
                    mode = effectiveMode,
                    preferredVideoQuality = videoQuality,
                    isrc = isrc,
                    updateQualityState = isActiveTrack
                )
                Log.d(MusicPlaybackService.TAG, "Resolved stream for $videoId (mode=$effectiveMode, hasVideo=${result.hasVideo}, active=$isActiveTrack): ${result.url}")

                if (isActiveTrack) {
                    serviceScope.launch(Dispatchers.Main) {
                        if (!isDestroyed) {
                            currentStreamHasVideo = result.hasVideo
                            isVideoAvailable = result.hasVideo && mediaMode == PlaybackMediaMode.VIDEO
                            if (result.hasVideo && mediaMode == PlaybackMediaMode.VIDEO && videoSurface != null && isPlayerInitialized()) {
                                exoPlayer.setVideoSurface(videoSurface)
                            }
                            notifyPlaybackState()
                        }
                    }
                }

                val headers = HashMap(dataSpec.httpRequestHeaders)
                headers["User-Agent"] = result.userAgent

                return@Factory dataSpec.buildUpon()
                    .setUri(Uri.parse(result.url))
                    .setHttpRequestHeaders(headers)
                    .build()
            } catch (e: Exception) {
                Log.e(MusicPlaybackService.TAG, "Failed to resolve stream for $videoId", e)
                throw PlaybackException(
                    "Failed to resolve stream for $videoId",
                    e,
                    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED
                )
            }
        } else if (videoId == "watch") {
            val originalUri = dataSpec.uri.toString()
            val extractedId = when {
                !queryTrackId.isNullOrBlank() -> queryTrackId
                originalUri.contains("v=") -> originalUri.substringAfter("v=").substringBefore("&")
                else -> ""
            }
            if (extractedId.isNotEmpty()) {
                try {
                    val title = dataSpec.uri.getQueryParameter("title")
                    val artist = dataSpec.uri.getQueryParameter("artist")
                    val isrc = dataSpec.uri.getQueryParameter("isrc")
                    val result = StreamResolver.resolveStreamUrl(extractedId, title, artist, mediaMode, videoQuality, isrc = isrc)

                    serviceScope.launch(Dispatchers.Main) {
                        if (!isDestroyed) {
                            currentStreamHasVideo = result.hasVideo
                            isVideoAvailable = result.hasVideo && mediaMode == PlaybackMediaMode.VIDEO
                            notifyPlaybackState()
                        }
                    }

                    val headers = HashMap(dataSpec.httpRequestHeaders)
                    headers["User-Agent"] = result.userAgent

                    return@Factory dataSpec.buildUpon()
                        .setUri(Uri.parse(result.url))
                        .setHttpRequestHeaders(headers)
                        .build()
                } catch (e: Exception) {
                    Log.e(MusicPlaybackService.TAG, "Failed to resolve fallback watch ID: $extractedId", e)
                }
            } else {
                Log.e(MusicPlaybackService.TAG, "Invalid 'watch' URL without ID: ${dataSpec.uri}")
                throw PlaybackException(
                    "Invalid watch URL: ${dataSpec.uri}",
                    null,
                    PlaybackException.ERROR_CODE_IO_UNSPECIFIED
                )
            }
        }
        return@Factory dataSpec
    }
}
