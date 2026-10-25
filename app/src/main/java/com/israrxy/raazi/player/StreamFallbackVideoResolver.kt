package com.israrxy.raazi.player

import android.net.Uri
import android.util.Log
import com.israrxy.raazi.model.PlaybackMediaMode
import com.israrxy.raazi.model.PlaybackVideoQuality
import com.zionhuang.innertube.NewPipeUtils
import com.zionhuang.innertube.YouTube
import com.zionhuang.innertube.models.SongItem
import com.zionhuang.innertube.models.YouTubeClient
import com.zionhuang.innertube.models.response.PlayerResponse
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Request
import org.json.JSONObject
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.StreamInfo
import kotlin.math.abs

/**
 * Resolve Bandcamp stream using NewPipe
 */
internal fun StreamResolver.resolveBandcampStream(
    url: String,
    updateQualityState: Boolean = true
): StreamResolver.StreamResult {
    return runBlocking {
        withTimeoutOrNull(StreamResolver.NEWPIPE_TIMEOUT_MS) {
            Log.d(StreamResolver.TAG, "Fetching Bandcamp stream info for: $url")
            val streamInfo = StreamInfo.getInfo(ServiceList.Bandcamp, url)

            val audioStream = streamInfo.audioStreams.maxByOrNull { it.averageBitrate }

            if (audioStream != null) {
                Log.i(StreamResolver.TAG, "SUCCESS: Resolved Bandcamp audio stream")
                val quality = "MP3 ${(audioStream.averageBitrate / 1000)}kbps"
                if (updateQualityState) setCurrentStreamQuality(quality)
                StreamResolver.StreamResult(
                    url = audioStream.content,
                    userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
                    quality = quality,
                    hasVideo = false
                )
            } else {
                throw Exception("No audio streams found for Bandcamp track")
            }
        } ?: throw Exception("Bandcamp stream resolution timed out after ${StreamResolver.NEWPIPE_TIMEOUT_MS}ms")
    }
}

/**
 * Resolve SoundCloud stream using NewPipe
 */
internal fun StreamResolver.resolveSoundCloudStream(
    url: String,
    updateQualityState: Boolean = true
): StreamResolver.StreamResult {
    return runBlocking {
        withTimeoutOrNull(StreamResolver.NEWPIPE_TIMEOUT_MS) {
            Log.d(StreamResolver.TAG, "Fetching SoundCloud stream info for: $url")
            val streamInfo = StreamInfo.getInfo(ServiceList.SoundCloud, url)

            val audioStream = streamInfo.audioStreams
                .filter { !it.content.contains(".m3u8") }
                .maxByOrNull { it.averageBitrate }
                ?: streamInfo.audioStreams.maxByOrNull { it.averageBitrate }

            if (audioStream != null) {
                val format = audioStream.format?.name ?: "MP3"
                val bitrate = audioStream.averageBitrate / 1000
                val quality = "$format ${bitrate}kbps"
                if (updateQualityState) setCurrentStreamQuality(quality)
                StreamResolver.StreamResult(
                    url = audioStream.content,
                    userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
                    quality = quality,
                    hasVideo = false
                )
            } else {
                val videoStream = streamInfo.videoOnlyStreams.firstOrNull()
                    ?: streamInfo.videoStreams.firstOrNull()
                if (videoStream != null) {
                    StreamResolver.StreamResult(
                        url = videoStream.content,
                        userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
                        quality = "Video Stream",
                        hasVideo = false
                    )
                } else {
                    throw Exception("No streams found for SoundCloud track")
                }
            }
        } ?: throw Exception("SoundCloud stream resolution timed out after ${StreamResolver.NEWPIPE_TIMEOUT_MS}ms")
    }
}

internal fun StreamResolver.resolveFormatStreamUrl(
    format: PlayerResponse.StreamingData.Format,
    videoId: String,
    client: YouTubeClient,
    webStreamingPot: String?
): String? {
    val rawUrl = if (!client.useSignatureTimestamp && !format.url.isNullOrBlank()) {
        format.url
    } else {
        NewPipeUtils.getStreamUrl(format, videoId).getOrNull()
            ?: format.url
    } ?: return null

    return if (client.useWebPoTokens && !webStreamingPot.isNullOrBlank()) {
        "$rawUrl&pot=$webStreamingPot"
    } else {
        rawUrl
    }
}

internal fun StreamResolver.selectPreferredMuxedVideoFormat(
    formats: List<PlayerResponse.StreamingData.Format>,
    preferredVideoQuality: PlaybackVideoQuality
): PlayerResponse.StreamingData.Format? {
    val safeFormats = formats.filter { it.url != null || it.signatureCipher != null }
    if (safeFormats.isEmpty()) return null

    fun pickHighestCandidate(candidates: List<PlayerResponse.StreamingData.Format>): PlayerResponse.StreamingData.Format? {
        return candidates.maxWithOrNull(
            compareBy<PlayerResponse.StreamingData.Format>(
                { if (it.mimeType.contains("mp4")) 1 else 0 },
                { it.height ?: 0 },
                { it.bitrate }
            )
        )
    }

    return preferredVideoQuality.maxHeight?.let { maxHeight ->
        val atOrBelowTarget = safeFormats.filter { (it.height ?: Int.MAX_VALUE) <= maxHeight }
        if (atOrBelowTarget.isNotEmpty()) {
            pickHighestCandidate(atOrBelowTarget)
        } else {
            safeFormats
                .filter { (it.height ?: 0) > 0 }
                .minWithOrNull(
                    compareBy<PlayerResponse.StreamingData.Format>(
                        { abs((it.height ?: Int.MAX_VALUE) - maxHeight) },
                        { it.height ?: Int.MAX_VALUE },
                        { if (it.mimeType.contains("mp4")) 0 else 1 }
                    )
                ) ?: pickHighestCandidate(safeFormats)
        }
    } ?: pickHighestCandidate(safeFormats)
}

/**
 * Resolves a playable H.264/AAC MP4 Music Video stream when a song is an audio-only Art Track
 * or when YouTube's /player endpoint blocks anonymous IPs.
 * Strictly verifies candidate video title + artist so an unrelated video is NEVER played.
 */
internal fun StreamResolver.resolveFallbackVideoStream(
    videoId: String,
    titleInput: String?,
    artistInput: String?,
    allowYouTubeSearch: Boolean,
    updateQualityState: Boolean = true
): StreamResolver.StreamResult? {
    val meta = resolveCanonicalMetadata(videoId, titleInput, artistInput)
    val cleanTitle = meta.primaryTitle
    val cleanArtist = meta.primaryArtist
    if (cleanTitle.isBlank()) return null

    val userAgent = StreamResolver.DEFAULT_WEB_USER_AGENT

    fun evaluateScore(candTitle: String, candArtist: String): Int {
        val s1 = scoreTrackMatch(cleanTitle, cleanArtist, candTitle, candArtist)
        val s2 = if (!meta.secondaryTitle.isNullOrBlank()) {
            scoreTrackMatch(meta.secondaryTitle, meta.secondaryArtist ?: cleanArtist, candTitle, candArtist)
        } else 0
        return maxOf(s1, s2)
    }

    // Tier 1: If YouTube is not bot-blocked, find official YouTube Music Video ID via InnerTube and play via NewPipeExtractor
    if (allowYouTubeSearch) {
        runCatching {
            val mvVideoId = runBlocking {
                withTimeoutOrNull(4_500L) {
                    val searchQuery = "$cleanTitle $cleanArtist official video".trim()
                    val searchRes = YouTube.search(
                        searchQuery,
                        YouTube.SearchFilter.FILTER_VIDEO
                    ).getOrNull()
                    searchRes?.items
                        ?.filterIsInstance<SongItem>()
                        ?.firstOrNull { item ->
                            item.id != videoId &&
                                evaluateScore(
                                    item.title,
                                    item.artists.joinToString(", ") { it.name }
                                ) >= 55
                        }
                        ?.id
                }
            }
            if (!mvVideoId.isNullOrBlank()) {
                runCatching {
                    return resolveYouTubeWithNewPipe(
                        videoId = mvVideoId,
                        mode = PlaybackMediaMode.VIDEO,
                        updateQualityState = updateQualityState
                    )
                }
                val ytClients = listOf(
                    YouTubeClient.ANDROID_VR_NO_AUTH,
                    YouTubeClient.IOS,
                    YouTubeClient.WEB_REMIX
                )
                for (client in ytClients) {
                    val mvStream = runBlocking {
                        withTimeoutOrNull(4_500L) {
                            val resp = YouTube.player(mvVideoId, client = client).getOrNull()
                            val formats = resp?.streamingData?.formats.orEmpty()
                            val selected = selectPreferredMuxedVideoFormat(formats, PlaybackVideoQuality.AUTO)
                            val url = selected?.let { fmt ->
                                resolveFormatStreamUrl(fmt, mvVideoId, client, null)
                            }
                            if (selected != null && !url.isNullOrBlank() && verifyStreamUrl(url, client.userAgent)) {
                                val qLabel = selected.qualityLabel ?: "${selected.height ?: 360}p Video"
                                if (updateQualityState) setCurrentStreamQuality(qLabel)
                                StreamResolver.StreamResult(
                                    url = url,
                                    userAgent = client.userAgent,
                                    quality = qLabel,
                                    mode = PlaybackMediaMode.VIDEO,
                                    hasVideo = true
                                )
                            } else null
                        }
                    }
                    if (mvStream != null) return mvStream
                }
            }
        }
    }

    // Tier 2: Apple Music Video CDN with strict JSON parsing & Title+Artist verification
    val queries = listOfNotNull(
        if (cleanArtist.isNotBlank() && !isGenericOrChannelArtist(normalizeForComparison(cleanArtist))) "$cleanTitle $cleanArtist".trim() else null,
        cleanTitle
    ).distinct()

    for (query in queries) {
        try {
            val encoded = Uri.encode(query)
            val itunesVideoUrl = "https://itunes.apple.com/search?term=$encoded&entity=musicVideo&limit=10"
            val request = Request.Builder()
                .url(itunesVideoUrl)
                .header("User-Agent", userAgent)
                .get()
                .build()

            val body = probeHttpClient.newCall(request).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string().orEmpty() else ""
            }
            if (body.isNotBlank()) {
                val root = JSONObject(body)
                val results = root.optJSONArray("results")
                if (results != null && results.length() > 0) {
                    val verifiedVideos = mutableListOf<Pair<Int, String>>()
                    for (i in 0 until results.length()) {
                        val item = results.optJSONObject(i) ?: continue
                        val trackName = item.optString("trackName")
                        val artistName = item.optString("artistName")
                        val previewUrl = item.optString("previewUrl").replace("\\/", "/")
                        if (previewUrl.isBlank()) continue
                        val score = evaluateScore(trackName, artistName)
                        if (score >= 60) {
                            verifiedVideos.add(score to previewUrl)
                        }
                    }
                    for ((score, candidateUrl) in verifiedVideos.sortedByDescending { it.first }.take(2)) {
                        if (verifyStreamUrl(candidateUrl, userAgent)) {
                            val quality = "Music Video HD"
                            if (updateQualityState) setCurrentStreamQuality(quality)
                            Log.i(StreamResolver.TAG, "SUCCESS: Resolved verified Apple Music Video (score=$score) for '$cleanTitle'")
                            return StreamResolver.StreamResult(
                                url = candidateUrl,
                                userAgent = userAgent,
                                quality = quality,
                                mode = PlaybackMediaMode.VIDEO,
                                hasVideo = true
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(StreamResolver.TAG, "Apple Music Video fallback failed for '$query': ${e.message}")
        }
    }

    return null
}
