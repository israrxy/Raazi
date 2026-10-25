package com.israrxy.raazi.player

import android.util.Log
import com.israrxy.raazi.data.db.FormatEntity
import com.israrxy.raazi.model.PlaybackMediaMode
import com.israrxy.raazi.model.PlaybackVideoQuality
import com.israrxy.raazi.service.YouTubeMusicExtractor
import com.zionhuang.innertube.NewPipeUtils
import com.zionhuang.innertube.YouTube
import com.zionhuang.innertube.models.YouTubeClient
import com.zionhuang.innertube.models.response.PlayerResponse
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.StreamInfo

/**
 * Primary resolver: Resolve YouTube stream directly using NewPipeExtractor with timeout.
 */
internal fun StreamResolver.resolveYouTubeWithNewPipe(
    videoId: String,
    mode: PlaybackMediaMode = PlaybackMediaMode.AUDIO,
    preferredVideoQuality: PlaybackVideoQuality = PlaybackVideoQuality.AUTO,
    preferAac: Boolean = false,
    updateQualityState: Boolean = true
): StreamResolver.StreamResult {
    return runBlocking {
        withTimeoutOrNull(StreamResolver.NEWPIPE_TIMEOUT_MS) {
            YouTubeMusicExtractor.getInstance()

            val normalizedId = extractYouTubeVideoId(videoId) ?: videoId
            val streamInfo = getCachedStreamInfo(normalizedId) ?: run {
                Log.d(StreamResolver.TAG, "Fetching YouTube stream info using NewPipeExtractor for: $normalizedId (mode=$mode)")
                val url = if (videoId.contains("youtube.com") || videoId.contains("youtu.be")) {
                    videoId
                } else {
                    "https://www.youtube.com/watch?v=$normalizedId"
                }
                StreamInfo.getInfo(ServiceList.YouTube, url).also { fetched ->
                    cacheStreamInfo(normalizedId, fetched)
                }
            }

            val muxedVideoStreams = streamInfo.videoStreams
                .filter { it.content.isNotBlank() && !it.isVideoOnly }

            val orderedVideos = preferredVideoQuality.maxHeight?.let { maxHeight ->
                val atOrBelow = muxedVideoStreams.filter { it.height in 1..maxHeight }.sortedByDescending { it.height }
                val above = muxedVideoStreams.filter { it.height > maxHeight }.sortedBy { it.height }
                val unknown = muxedVideoStreams.filter { it.height <= 0 }
                atOrBelow + above + unknown
            } ?: muxedVideoStreams.sortedByDescending { it.height }

            val sortedAudioStreams = streamInfo.audioStreams
                .filter { it.content.isNotBlank() }
                .sortedByDescending { audio ->
                    var score = (audio.averageBitrate.takeIf { it > 0 } ?: 128).toLong() * 1000L
                    val mime = audio.format?.mimeType?.lowercase().orEmpty()
                    val codec = audio.codec?.lowercase().orEmpty()
                    if (preferAac) {
                        if (mime.contains("mp4") || mime.contains("m4a") || codec.contains("mp4a") || codec.contains("aac")) {
                            score += 100_000L
                        }
                    } else {
                        if (mime.contains("webm") || codec.contains("opus")) {
                            score += 10_000L
                        }
                    }
                    score
                }

            if (mode == PlaybackMediaMode.VIDEO) {
                val videoStream = orderedVideos.firstOrNull {
                    verifyStreamUrl(it.content, userAgentForStreamUrl(it.content))
                } ?: orderedVideos.firstOrNull()

                if (videoStream != null) {
                    val streamUa = userAgentForStreamUrl(videoStream.content)
                    val quality = "${videoStream.resolution.ifBlank { "360p" }} Video"
                    if (updateQualityState) setCurrentStreamQuality(quality)
                    runCatching {
                        musicDao?.upsertFormat(
                            FormatEntity(
                                id = normalizedId,
                                mimeType = videoStream.format?.mimeType ?: "video/mp4",
                                codecs = videoStream.codec ?: "avc1.42001E, mp4a.40.2",
                                bitrate = videoStream.bitrate.takeIf { it > 0 } ?: 500000,
                                sampleRate = 44100,
                                contentLength = 0L
                            )
                        )
                    }
                    val audioCacheKey = normalizeCacheKey(normalizedId, PlaybackMediaMode.AUDIO, PlaybackVideoQuality.AUTO, false)
                    if (audioCacheKey != null && getCachedStream(audioCacheKey) == null) {
                        sortedAudioStreams.firstOrNull()?.let { bestAudio ->
                            val aMime = bestAudio.format?.mimeType ?: "audio/mp4"
                            val aCodec = bestAudio.codec ?: if (aMime.contains("webm")) "opus" else "mp4a.40.2"
                            val aBitrate = bestAudio.averageBitrate.takeIf { it > 0 } ?: 128
                            val aLabel = if (aCodec.contains("opus", true) || aMime.contains("webm", true)) "Opus" else "M4A"
                            cacheResolvedStream(
                                audioCacheKey,
                                StreamResolver.StreamResult(
                                    url = bestAudio.content,
                                    userAgent = userAgentForStreamUrl(bestAudio.content),
                                    quality = "$aLabel ${aBitrate}kbps",
                                    mode = PlaybackMediaMode.AUDIO,
                                    hasVideo = false
                                )
                            )
                        }
                    }
                    return@withTimeoutOrNull StreamResolver.StreamResult(
                        url = videoStream.content,
                        userAgent = streamUa,
                        quality = quality,
                        mode = PlaybackMediaMode.VIDEO,
                        hasVideo = true
                    )
                } else if (streamInfo.hlsUrl.isNotBlank()) {
                    val streamUa = userAgentForStreamUrl(streamInfo.hlsUrl)
                    val quality = "HLS Video"
                    if (updateQualityState) setCurrentStreamQuality(quality)
                    return@withTimeoutOrNull StreamResolver.StreamResult(
                        url = streamInfo.hlsUrl,
                        userAgent = streamUa,
                        quality = quality,
                        mode = PlaybackMediaMode.VIDEO,
                        hasVideo = true
                    )
                }
                throw Exception("No playable video streams found via NewPipeExtractor for $videoId")
            }

            val audioStream = sortedAudioStreams.take(4).firstOrNull {
                verifyStreamUrl(it.content, userAgentForStreamUrl(it.content))
            } ?: sortedAudioStreams.firstOrNull()

            if (audioStream != null) {
                val streamUa = userAgentForStreamUrl(audioStream.content)
                val mimeType = audioStream.format?.mimeType ?: "audio/mp4"
                val codec = audioStream.codec ?: if (mimeType.contains("webm")) "opus" else "mp4a.40.2"
                val bitrateKbps = audioStream.averageBitrate.takeIf { it > 0 } ?: 128
                val formatLabel = when {
                    codec.contains("opus", ignoreCase = true) || mimeType.contains("webm", ignoreCase = true) -> "Opus"
                    else -> "M4A"
                }
                val quality = "$formatLabel ${bitrateKbps}kbps"

                if (updateQualityState) setCurrentStreamQuality(quality)
                runCatching {
                    musicDao?.upsertFormat(
                        FormatEntity(
                            id = normalizedId,
                            mimeType = mimeType,
                            codecs = codec,
                            bitrate = bitrateKbps * 1000,
                            sampleRate = 48000,
                            contentLength = 0L
                        )
                    )
                }
                val videoCacheKey = normalizeCacheKey(normalizedId, PlaybackMediaMode.VIDEO, preferredVideoQuality, false)
                if (videoCacheKey != null && getCachedStream(videoCacheKey) == null) {
                    orderedVideos.firstOrNull()?.let { bestVideo ->
                        cacheResolvedStream(
                            videoCacheKey,
                            StreamResolver.StreamResult(
                                url = bestVideo.content,
                                userAgent = userAgentForStreamUrl(bestVideo.content),
                                quality = "${bestVideo.resolution.ifBlank { "360p" }} Video",
                                mode = PlaybackMediaMode.VIDEO,
                                hasVideo = true
                            )
                        )
                    }
                }
                Log.i(StreamResolver.TAG, "SUCCESS: Resolved YouTube audio directly via NewPipeExtractor ($quality) for $videoId")
                return@withTimeoutOrNull StreamResolver.StreamResult(
                    url = audioStream.content,
                    userAgent = streamUa,
                    quality = quality,
                    mode = PlaybackMediaMode.AUDIO,
                    hasVideo = false
                )
            }

            val progressiveFallback = orderedVideos.firstOrNull {
                verifyStreamUrl(it.content, userAgentForStreamUrl(it.content))
            } ?: orderedVideos.firstOrNull()

            if (progressiveFallback != null) {
                val streamUa = userAgentForStreamUrl(progressiveFallback.content)
                val quality = "M4A 128kbps"
                if (updateQualityState) setCurrentStreamQuality(quality)
                return@withTimeoutOrNull StreamResolver.StreamResult(
                    url = progressiveFallback.content,
                    userAgent = streamUa,
                    quality = quality,
                    mode = PlaybackMediaMode.AUDIO,
                    hasVideo = true
                )
            }

            throw Exception("No matching $mode streams found via NewPipeExtractor")
        } ?: throw Exception("NewPipe resolution timed out after ${StreamResolver.NEWPIPE_TIMEOUT_MS}ms")
    }
}

internal data class InnerTubeResolutionAttempt(
    val result: StreamResolver.StreamResult?,
    val botBlockedCount: Int,
    val lastError: Throwable?
)

internal fun StreamResolver.resolveYouTubeWithInnerTubeClients(
    videoId: String,
    mode: PlaybackMediaMode,
    preferredVideoQuality: PlaybackVideoQuality,
    preferAac: Boolean,
    updateQualityState: Boolean,
    isLoggedIn: Boolean,
    isRecentlyBotBlocked: Boolean,
    initialBotBlockedCount: Int
): InnerTubeResolutionAttempt {
    var botBlockedCount = initialBotBlockedCount
    var lastError: Throwable? = null

    val clients = if (isRecentlyBotBlocked) {
        listOf(YouTubeClient.ANDROID_VR_NO_AUTH)
    } else {
        listOf(
            YouTubeClient.ANDROID_VR_NO_AUTH,
            YouTubeClient.IOS,
            YouTubeClient.WEB_REMIX
        )
    }

    var cachedSignatureTimestamp: Int? = null
    var signatureTimestampAttempted = false

    for (client in clients) {
        if (!isLoggedIn && botBlockedCount >= 2) {
            youtubeBotBlockedUntilMs = System.currentTimeMillis() + 5 * 60_000L
            Log.w(StreamResolver.TAG, "YouTube IP bot-block detected; skipping remaining InnerTube clients")
            break
        }
        try {
            val result = runBlocking {
                withTimeoutOrNull(StreamResolver.RESOLVE_TIMEOUT_MS) {
                    Log.d(StreamResolver.TAG, "Attempting InnerTube resolve with client: ${client.clientName}")

                    val signatureTimestamp = if (client.useSignatureTimestamp) {
                        if (!signatureTimestampAttempted) {
                            signatureTimestampAttempted = true
                            cachedSignatureTimestamp = NewPipeUtils
                                .getSignatureTimestamp(videoId)
                                .getOrNull()
                        }
                        cachedSignatureTimestamp
                    } else {
                        null
                    }

                    val playerResult = YouTube.player(
                        videoId,
                        client = client,
                        signatureTimestamp = signatureTimestamp,
                        webPlayerPot = null
                    )

                    val response = playerResult.getOrThrow()
                    if (response.playabilityStatus.status != "OK") {
                        val status = response.playabilityStatus.status
                        val reason = response.playabilityStatus.reason.orEmpty()
                        if (status == "LOGIN_REQUIRED" || reason.contains("not a bot", ignoreCase = true)) {
                            botBlockedCount++
                            if (!isLoggedIn && botBlockedCount >= 2) {
                                youtubeBotBlockedUntilMs = System.currentTimeMillis() + 5 * 60_000L
                            }
                        }
                        throw Exception("Playability status not OK for ${client.clientName}: $status ($reason)")
                    }

                    youtubeBotBlockedUntilMs = 0L

                    val streamingData = response.streamingData
                        ?: throw Exception("Missing streaming data for client ${client.clientName}")

                    val muxedVideoFormats = streamingData.formats
                        .orEmpty()
                        .filter { format ->
                            format.mimeType.startsWith("video/") &&
                                (format.url != null || format.signatureCipher != null)
                        }
                    val adaptiveFormats = streamingData.adaptiveFormats
                    val audioFormats = adaptiveFormats.filter {
                        it.mimeType.startsWith("audio/") &&
                            (it.url != null || it.signatureCipher != null)
                    }

                    val candidateFormats: List<PlayerResponse.StreamingData.Format> = when (mode) {
                        PlaybackMediaMode.AUDIO -> {
                            val sortedAudio = audioFormats.sortedByDescending { format ->
                                var score = format.bitrate.toLong()
                                if (preferAac) {
                                    if (format.mimeType.contains("mp4") || format.mimeType.contains("aac")) {
                                        score += 100000
                                    }
                                } else {
                                    if (format.mimeType.contains("webm") || format.mimeType.contains("opus")) {
                                        score += 10000
                                    }
                                }
                                score
                            }
                            sortedAudio + muxedVideoFormats
                        }
                        PlaybackMediaMode.VIDEO -> {
                            listOfNotNull(
                                selectPreferredMuxedVideoFormat(
                                    muxedVideoFormats,
                                    preferredVideoQuality
                                )
                            ) + muxedVideoFormats
                        }
                    }.distinct()

                    var verifiedPair: Pair<PlayerResponse.StreamingData.Format, String>? = null
                    val formatsToTry = if (mode == PlaybackMediaMode.AUDIO) {
                        candidateFormats.filter { it.mimeType.startsWith("audio/") }.take(4) +
                            candidateFormats.filter { !it.mimeType.startsWith("audio/") }.take(2)
                    } else {
                        candidateFormats.take(4)
                    }

                    for (candidate in formatsToTry) {
                        val candidateUrl = resolveFormatStreamUrl(
                            format = candidate,
                            videoId = videoId,
                            client = client,
                            webStreamingPot = null
                        ) ?: continue

                        if (verifyStreamUrl(candidateUrl, client.userAgent)) {
                            verifiedPair = candidate to candidateUrl
                            break
                        }
                    }

                    if (verifiedPair != null) {
                        val (selectedFormat, url) = verifiedPair
                        Log.i(StreamResolver.TAG, "SUCCESS: Resolved & verified YouTube stream using client: ${client.clientName}")

                        val codecs = try {
                            selectedFormat.mimeType.substringAfter("codecs=").removeSurrounding("\"")
                        } catch (_: Exception) { "unknown" }
                        val quality = when (mode) {
                            PlaybackMediaMode.AUDIO -> {
                                if (selectedFormat.mimeType.contains("opus")) {
                                    "Opus ${selectedFormat.bitrate / 1000}kbps"
                                } else if (selectedFormat.mimeType.contains("mp4")) {
                                    "M4A ${selectedFormat.bitrate / 1000}kbps"
                                } else {
                                    "YouTube ${selectedFormat.bitrate / 1000}kbps"
                                }
                            }
                            PlaybackMediaMode.VIDEO -> {
                                selectedFormat.qualityLabel
                                    ?: listOfNotNull(
                                        selectedFormat.height?.let { "${it}p" },
                                        "Video"
                                    ).joinToString(" ")
                            }
                        }
                        if (updateQualityState) setCurrentStreamQuality(quality)

                        try {
                            musicDao?.upsertFormat(
                                FormatEntity(
                                    id = videoId,
                                    mimeType = selectedFormat.mimeType.split(";")[0],
                                    codecs = codecs,
                                    bitrate = selectedFormat.bitrate,
                                    sampleRate = selectedFormat.audioSampleRate,
                                    contentLength = selectedFormat.contentLength ?: 0L
                                )
                            )
                        } catch (e: Exception) {
                            Log.w(StreamResolver.TAG, "Failed to save format info", e)
                        }

                        StreamResolver.StreamResult(
                            url = url,
                            userAgent = client.userAgent,
                            quality = quality,
                            mode = mode,
                            hasVideo = selectedFormat.mimeType.startsWith("video/")
                        )
                    } else {
                        throw Exception(
                            if (mode == PlaybackMediaMode.VIDEO) {
                                "No playable video formats found with client ${client.clientName}"
                            } else {
                                "No playable audio formats found with client ${client.clientName}"
                            }
                        )
                    }
                }
            }
            if (result != null) {
                return InnerTubeResolutionAttempt(result, botBlockedCount, null)
            } else {
                throw Exception("Resolution timed out for client ${client.clientName}")
            }
        } catch (e: Exception) {
            Log.w(StreamResolver.TAG, "Failed to resolve with client ${client.clientName}: ${e.message}")
            lastError = e
        }
    }

    return InnerTubeResolutionAttempt(null, botBlockedCount, lastError)
}
