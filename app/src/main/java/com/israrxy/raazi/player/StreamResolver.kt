package com.israrxy.raazi.player

import com.zionhuang.innertube.YouTube
import com.zionhuang.innertube.models.YouTubeClient
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import android.net.Uri
import android.util.Log
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.StreamInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.israrxy.raazi.data.db.MusicDao
import com.israrxy.raazi.data.db.FormatEntity
import com.israrxy.raazi.model.PlaybackMediaMode
import com.israrxy.raazi.model.PlaybackVideoQuality
import com.zionhuang.innertube.models.response.PlayerResponse

object StreamResolver {
    private const val TAG = "StreamResolver"
    private const val CACHE_TTL_MS = 10 * 60 * 1000L
    private const val MAX_CACHE_SIZE = 12
    private const val RESOLVE_TIMEOUT_MS = 30_000L
    private const val NEWPIPE_TIMEOUT_MS = 20_000L
    
    // Injected DAO for saving format info
    var musicDao: MusicDao? = null

    // Session-level FLAC override: default to false (YouTube)
    var sessionFlacEnabled: Boolean? = false

    data class StreamResult(
        val url: String,
        val userAgent: String,
        val quality: String? = null,
        val mode: PlaybackMediaMode = PlaybackMediaMode.AUDIO,
        val hasVideo: Boolean = false,
        val isFlac: Boolean = false
    )

    private val _currentStreamQuality = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    val currentStreamQuality = _currentStreamQuality.asStateFlow()

    private val poTokenGenerator = com.israrxy.raazi.player.potoken.PoTokenGenerator.getInstance()
    private val preloadScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val cacheLock = Any()
    private val preloadLock = Any()
    private val inFlightPreloads = mutableSetOf<String>()
    private val resolutionCache = object : LinkedHashMap<String, CachedStreamResult>(MAX_CACHE_SIZE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CachedStreamResult>?): Boolean {
            return size > MAX_CACHE_SIZE
        }
    }

    private data class CachedStreamResult(
        val result: StreamResult,
        val cachedAtMs: Long
    )

    /**
     * Public entry point — wraps internal resolution with a global safety net.
     */
    fun resolveStreamUrl(
        videoIdInput: String,
        title: String? = null,
        artist: String? = null,
        mode: PlaybackMediaMode = PlaybackMediaMode.AUDIO,
        preferredVideoQuality: PlaybackVideoQuality = PlaybackVideoQuality.AUTO,
        preferAac: Boolean = false,
        isrc: String? = null,
        updateQualityState: Boolean = true
    ): StreamResult {
        if (updateQualityState) {
            _currentStreamQuality.value = null
        }
        try {
            return resolveStreamUrlInternal(
                videoIdInput = videoIdInput,
                title = title,
                artist = artist,
                mode = mode,
                preferredVideoQuality = preferredVideoQuality,
                preferAac = preferAac,
                isrc = isrc,
                updateQualityState = updateQualityState
            )
        } catch (e: Exception) {
            Log.e(TAG, "CRITICAL: Stream resolution failed completely for: $videoIdInput", e)
            throw e
        }
    }

    private fun resolveStreamUrlInternal(
        videoIdInput: String,
        title: String? = null,
        artist: String? = null,
        mode: PlaybackMediaMode = PlaybackMediaMode.AUDIO,
        preferredVideoQuality: PlaybackVideoQuality = PlaybackVideoQuality.AUTO,
        preferAac: Boolean = false,
        isrc: String? = null,
        updateQualityState: Boolean = true
    ): StreamResult {
        var videoId = videoIdInput.trim()
        Log.d(TAG, "Resolving stream for input: $videoId (title=$title, artist=$artist, mode=$mode)")

        // Robustly extract ID if input is a URL
        extractYouTubeVideoId(videoId)?.let { extractedId ->
            videoId = extractedId
        }

        if ((videoId.isBlank() || videoId == "watch" ||
                ((videoId.contains("youtube.com") || videoId.contains("youtu.be")) && videoId.startsWith("http")))
        ) {
            Log.w(TAG, "Need metadata search to recover valid YouTube ID for input: '$videoIdInput'")
            if (!title.isNullOrBlank()) {
                val fallbackId = runBlocking {
                    withTimeoutOrNull(RESOLVE_TIMEOUT_MS) {
                        try {
                            val extractor = com.israrxy.raazi.service.YouTubeMusicExtractor.getInstance()
                            val query = if (!artist.isNullOrBlank()) "$title $artist" else title
                            val searchResult = extractor.searchMusic(query)
                            searchResult.items.firstOrNull { it.id.matches(Regex("^[a-zA-Z0-9_-]{11}$")) }?.id
                                ?: searchResult.items.firstOrNull()?.id
                        } catch (e: Exception) {
                            Log.e(TAG, "Fallback search failed", e)
                            null
                        }
                    }
                }

                if (!fallbackId.isNullOrBlank()) {
                    Log.i(TAG, "Recovered track ID via search: $fallbackId")
                    videoId = fallbackId
                } else if (videoId.isBlank() || videoId == "watch") {
                    // Resolve directly via verified metadata fallback
                    return resolveFallbackAudioStream(
                        videoId = videoIdInput.ifBlank { "${title}_${artist}" },
                        titleInput = title,
                        artistInput = artist,
                        isrc = isrc,
                        updateQualityState = updateQualityState
                    )
                } else {
                    throw IllegalArgumentException("Invalid YouTube URL and fallback failed: $videoIdInput")
                }
            } else {
                throw IllegalArgumentException("Invalid YouTube URL and no metadata for fallback: $videoIdInput")
            }
        }

        // Detect if this is a Bandcamp URL
        if (videoId.contains("bandcamp.com")) {
            Log.i(TAG, "Detected Bandcamp URL, using NewPipe extractor")
            val cacheKey = normalizeCacheKey(videoId, mode, preferredVideoQuality, preferAac)
            if (cacheKey != null) {
                getCachedStream(cacheKey)?.let { cached ->
                    if (updateQualityState) _currentStreamQuality.value = cached.quality
                    return cached
                }
            }
            return resolveBandcampStream(videoId, updateQualityState).also { resolved ->
                if (cacheKey != null) cacheResolvedStream(cacheKey, resolved)
            }
        }

        val cacheKey = normalizeCacheKey(videoId, mode, preferredVideoQuality, preferAac)
        if (cacheKey != null) {
            getCachedStream(cacheKey)?.let { cached ->
                if (updateQualityState) _currentStreamQuality.value = cached.quality
                return cached
            }
        }

        // Attempt FLAC resolution if enabled and in AUDIO mode (skip when preferAac is requested for MediaMuxer compatibility)
        if (mode == PlaybackMediaMode.AUDIO && !preferAac && title != null && artist != null) {
            val flacPrefs = try {
                kotlinx.coroutines.runBlocking {
                    val settings = com.israrxy.raazi.data.local.SettingsDataStore(
                        com.israrxy.raazi.RaaziApplication.instance
                    )
                    Pair(settings.flacEnabled.first(), settings.flacQuality.first())
                }
            } catch (e: Exception) { Pair(false, "HI_RES_LOSSLESS") }

            val flacEnabled = sessionFlacEnabled ?: flacPrefs.first
            val flacQuality = flacPrefs.second

            if (flacEnabled) {
                try {
                    val flacResult = kotlinx.coroutines.runBlocking {
                        com.israrxy.raazi.player.flac.FlacStreamResolver.resolveFlacStream(
                            context = com.israrxy.raazi.RaaziApplication.instance,
                            isrc = isrc,
                            title = title,
                            artist = artist,
                            quality = flacQuality
                        )
                    }
                    if (flacResult != null) {
                        Log.i(TAG, "FLAC resolved successfully: ${flacResult.qualityLabel}")
                        val quality = flacResult.qualityLabel
                        if (updateQualityState) _currentStreamQuality.value = quality
                        return StreamResult(
                            url = "file://${flacResult.localPath}",
                            userAgent = "Raazi/FLAC",
                            quality = quality,
                            mode = mode,
                            hasVideo = false,
                            isFlac = true
                        ).also { if (cacheKey != null) cacheResolvedStream(cacheKey, it) }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "FLAC resolution failed, falling back to YouTube: ${e.message}")
                }
            }
        }

        val resolved = when {
            videoId.contains("soundcloud.com") -> {
                Log.i(TAG, "Detected SoundCloud URL, using NewPipe extractor")
                resolveSoundCloudStream(videoId, updateQualityState)
            }
            else -> resolveYouTubeStream(
                videoId = videoId,
                mode = mode,
                preferredVideoQuality = preferredVideoQuality,
                preferAac = preferAac,
                title = title,
                artist = artist,
                isrc = isrc,
                updateQualityState = updateQualityState
            )
        }
        if (cacheKey != null) {
            cacheResolvedStream(cacheKey, resolved)
        }
        return resolved
    }

    fun preloadStream(videoIdInput: String, title: String? = null, artist: String? = null) {
        val trimmed = videoIdInput.trim()
        if (trimmed.isBlank() || trimmed == "watch") return
        val cacheKey = normalizeCacheKey(
            trimmed,
            PlaybackMediaMode.AUDIO,
            PlaybackVideoQuality.AUTO
        ) ?: return
        if (getCachedStream(cacheKey) != null) return

        synchronized(preloadLock) {
            if (!inFlightPreloads.add(cacheKey)) return
        }

        preloadScope.launch {
            try {
                resolveStreamUrl(
                    videoIdInput = trimmed,
                    title = title,
                    artist = artist,
                    updateQualityState = false
                )
                Log.d(TAG, "Preloaded stream for $cacheKey")
            } catch (e: Exception) {
                Log.w(TAG, "Preload failed for $cacheKey: ${e.message}")
            } finally {
                synchronized(preloadLock) {
                    inFlightPreloads.remove(cacheKey)
                }
            }
        }
    }

    private fun extractYouTubeVideoId(input: String): String? {
        val trimmed = input.trim()
        if (trimmed.matches(Regex("^[a-zA-Z0-9_-]{11}$"))) return trimmed
        if (!trimmed.contains("youtube.com") && !trimmed.contains("youtu.be")) return null

        return try {
            val uri = Uri.parse(trimmed)
            val host = uri.host.orEmpty().lowercase()
            when {
                host == "youtu.be" -> uri.pathSegments.firstOrNull()
                uri.getQueryParameter("v")?.isNotBlank() == true -> uri.getQueryParameter("v")
                uri.pathSegments.firstOrNull() in setOf("shorts", "embed", "live") -> uri.pathSegments.getOrNull(1)
                uri.pathSegments.firstOrNull() == "watch" -> uri.getQueryParameter("v")
                else -> null
            }?.takeIf { it.matches(Regex("^[a-zA-Z0-9_-]{11}$")) }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse YouTube URL: $input", e)
            null
        }
    }

    private fun getCachedStream(cacheKey: String): StreamResult? {
        synchronized(cacheLock) {
            val cached = resolutionCache[cacheKey] ?: return null
            val isFresh = System.currentTimeMillis() - cached.cachedAtMs <= CACHE_TTL_MS
            if (!isFresh) {
                resolutionCache.remove(cacheKey)
                return null
            }
            return cached.result
        }
    }

    private fun cacheResolvedStream(cacheKey: String, result: StreamResult) {
        synchronized(cacheLock) {
            resolutionCache[cacheKey] = CachedStreamResult(
                result = result,
                cachedAtMs = System.currentTimeMillis()
            )
        }
    }

    fun clearCacheForVideoId(videoId: String) {
        if (videoId.isBlank()) return
        synchronized(cacheLock) {
            resolutionCache.keys.removeAll { it.contains(videoId) }
        }
    }

    private fun normalizeCacheKey(
        videoIdInput: String,
        mode: PlaybackMediaMode,
        preferredVideoQuality: PlaybackVideoQuality,
        preferAac: Boolean = false
    ): String? {
        val trimmed = videoIdInput.trim()
        if (trimmed.isBlank() || trimmed == "watch") return null
        val normalized = when {
            trimmed.contains("soundcloud.com") || trimmed.contains("bandcamp.com") -> trimmed
            else -> extractYouTubeVideoId(trimmed) ?: trimmed
        }
        if (normalized.isBlank() || normalized == "watch") return null
        val qualityKey = if (mode == PlaybackMediaMode.VIDEO) {
            preferredVideoQuality.wireName
        } else {
            PlaybackVideoQuality.AUTO.wireName
        }
        return "${mode.name}:$qualityKey:$normalized${if (preferAac) ":aac" else ""}"
    }

    /**
     * Resolve Bandcamp stream using NewPipe
     */
    private fun resolveBandcampStream(url: String, updateQualityState: Boolean = true): StreamResult {
        return runBlocking {
            withTimeoutOrNull(NEWPIPE_TIMEOUT_MS) {
                Log.d(TAG, "Fetching Bandcamp stream info for: $url")
                val streamInfo = StreamInfo.getInfo(ServiceList.Bandcamp, url)

                val audioStream = streamInfo.audioStreams.maxByOrNull { it.averageBitrate }

                if (audioStream != null) {
                    Log.i(TAG, "SUCCESS: Resolved Bandcamp audio stream")
                    val quality = "MP3 ${(audioStream.averageBitrate / 1000)}kbps"
                    if (updateQualityState) _currentStreamQuality.value = quality
                    StreamResult(
                        url = audioStream.content,
                        userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
                        quality = quality,
                        hasVideo = false
                    )
                } else {
                    throw Exception("No audio streams found for Bandcamp track")
                }
            } ?: throw Exception("Bandcamp stream resolution timed out after ${NEWPIPE_TIMEOUT_MS}ms")
        }
    }

    /**
     * Resolve SoundCloud stream using NewPipe
     */
    private fun resolveSoundCloudStream(url: String, updateQualityState: Boolean = true): StreamResult {
        return runBlocking {
            withTimeoutOrNull(NEWPIPE_TIMEOUT_MS) {
                Log.d(TAG, "Fetching SoundCloud stream info for: $url")
                val streamInfo = StreamInfo.getInfo(ServiceList.SoundCloud, url)

                val audioStream = streamInfo.audioStreams
                    .filter { !it.content.contains(".m3u8") }
                    .maxByOrNull { it.averageBitrate }
                    ?: streamInfo.audioStreams.maxByOrNull { it.averageBitrate }

                if (audioStream != null) {
                    val format = audioStream.format?.name ?: "MP3"
                    val bitrate = audioStream.averageBitrate / 1000
                    val quality = "$format ${bitrate}kbps"
                    if (updateQualityState) _currentStreamQuality.value = quality
                    StreamResult(
                        url = audioStream.content,
                        userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
                        quality = quality,
                        hasVideo = false
                    )
                } else {
                    // Fallback to video streams
                    val videoStream = streamInfo.videoOnlyStreams.firstOrNull()
                        ?: streamInfo.videoStreams.firstOrNull()
                    if (videoStream != null) {
                        StreamResult(
                            url = videoStream.content,
                            userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
                            quality = "Video Stream",
                            hasVideo = false
                        )
                    } else {
                        throw Exception("No streams found for SoundCloud track")
                    }
                }
            } ?: throw Exception("SoundCloud stream resolution timed out after ${NEWPIPE_TIMEOUT_MS}ms")
        }
    }

    private val probeHttpClient = okhttp3.OkHttpClient.Builder()
        .connectTimeout(4, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(4, java.util.concurrent.TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private fun verifyStreamUrl(url: String, userAgent: String): Boolean {
        if (url.startsWith("file://")) return true
        return try {
            val request = okhttp3.Request.Builder()
                .url(url)
                .get()
                .header("User-Agent", userAgent)
                .header("Range", "bytes=0-1")
                .build()
            probeHttpClient.newCall(request).execute().use { response ->
                val ok = response.isSuccessful || response.code == 206 || response.code == 200
                if (!ok) {
                    Log.w(TAG, "Stream probe failed with HTTP ${response.code} for url")
                }
                ok
            }
        } catch (e: Exception) {
            Log.w(TAG, "Stream probe exception: ${e.message}")
            false
        }
    }

    private fun resolveFormatStreamUrl(
        format: PlayerResponse.StreamingData.Format,
        videoId: String,
        client: YouTubeClient,
        webStreamingPot: String?
    ): String? {
        val rawUrl = if (!client.useSignatureTimestamp && !format.url.isNullOrBlank()) {
            format.url
        } else {
            com.zionhuang.innertube.NewPipeUtils.getStreamUrl(format, videoId).getOrNull()
                ?: format.url
        } ?: return null

        return if (client.useWebPoTokens && !webStreamingPot.isNullOrBlank()) {
            "$rawUrl&pot=$webStreamingPot"
        } else {
            rawUrl
        }
    }

    /**
     * Resolve YouTube stream using InnerTube with timeout protection
     */
    private fun resolveYouTubeStream(
        videoId: String,
        mode: PlaybackMediaMode,
        preferredVideoQuality: PlaybackVideoQuality,
        preferAac: Boolean = false,
        title: String? = null,
        artist: String? = null,
        isrc: String? = null,
        updateQualityState: Boolean = true
    ): StreamResult {
        val clients = if (mode == PlaybackMediaMode.VIDEO) {
            listOf(
                YouTubeClient.ANDROID_VR_NO_AUTH,
                YouTubeClient.IOS,
                YouTubeClient.ANDROID,
                YouTubeClient.VISIONOS,
                YouTubeClient.TVHTML5_SIMPLY_EMBEDDED_PLAYER,
                YouTubeClient.WEB_REMIX,
                YouTubeClient.TVHTML5
            )
        } else {
            listOf(
                YouTubeClient.ANDROID_VR_NO_AUTH,
                YouTubeClient.IOS,
                YouTubeClient.ANDROID,
                YouTubeClient.VISIONOS,
                YouTubeClient.TVHTML5_SIMPLY_EMBEDDED_PLAYER,
                YouTubeClient.WEB_REMIX,
                YouTubeClient.TVHTML5
            )
        }

        var lastError: Throwable? = null
        var botBlockedCount = 0

        Log.i(TAG, "Resolving YouTube stream for videoId: $videoId")

        if (YouTube.visitorData.isNullOrBlank()) {
            runCatching {
                runBlocking {
                    withTimeoutOrNull(4_000L) {
                        YouTube.visitorData().getOrNull()
                    }
                }?.takeIf { it.isNotBlank() }?.let { fresh ->
                    YouTube.visitorData = fresh
                }
            }
        }

        val isLoggedIn = YouTube.cookie != null
        val sessionId = if (isLoggedIn) YouTube.dataSyncId else YouTube.visitorData

        var cachedSignatureTimestamp: Int? = null
        var signatureTimestampAttempted = false

        for (client in clients) {
            if (!isLoggedIn && botBlockedCount >= 3) {
                Log.w(TAG, "YouTube IP bot-block detected across primary clients; skipping remaining unauthenticated YT clients")
                break
            }
            try {
                val result = runBlocking {
                    withTimeoutOrNull(RESOLVE_TIMEOUT_MS) {
                        Log.d(TAG, "Attempting resolve with client: ${client.clientName}")
                        var webPlayerPot: String? = null
                        var webStreamingPot: String? = null

                        // Only generate WebView PoTokens when logged in or when IP is not bot-blocked
                        if (client.useWebPoTokens && sessionId != null && (isLoggedIn || botBlockedCount == 0)) {
                            try {
                                val potResult = withTimeoutOrNull(4_000L) {
                                    poTokenGenerator.getWebClientPoToken(videoId, sessionId)
                                }
                                if (potResult != null) {
                                    webPlayerPot = potResult.playerRequestPoToken
                                    webStreamingPot = potResult.streamingDataPoToken
                                }
                            } catch (e: Throwable) {
                                Log.w(TAG, "PoToken generation skipped/failed: ${e.message}")
                            }
                        }

                        val signatureTimestamp = if (client.useSignatureTimestamp) {
                            if (!signatureTimestampAttempted) {
                                signatureTimestampAttempted = true
                                cachedSignatureTimestamp = com.zionhuang.innertube.NewPipeUtils
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
                            webPlayerPot = webPlayerPot
                        )

                        val response = playerResult.getOrThrow()
                        if (response.playabilityStatus.status != "OK") {
                            val status = response.playabilityStatus.status
                            val reason = response.playabilityStatus.reason.orEmpty()
                            if (status == "LOGIN_REQUIRED" || reason.contains("not a bot", ignoreCase = true)) {
                                botBlockedCount++
                            }
                            throw Exception("Playability status not OK for ${client.clientName}: $status ($reason)")
                        }

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

                        val hasVideo = muxedVideoFormats.isNotEmpty()

                        Log.i(
                            TAG,
                            "CLIENT=${client.clientName} progressive=${streamingData.formats?.size ?: 0} " +
                                "muxedVideo=${muxedVideoFormats.size} " +
                                "adaptive=${adaptiveFormats.size} " +
                                "adaptiveAudio=${audioFormats.size} hasVideo=$hasVideo"
                        )

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
                                // Include muxed progressive formats as reliable fallback since they contain AAC audio
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
                                webStreamingPot = webStreamingPot
                            ) ?: continue

                            if (verifyStreamUrl(candidateUrl, client.userAgent)) {
                                verifiedPair = candidate to candidateUrl
                                break
                            } else {
                                Log.w(TAG, "Rejected non-playable URL from client ${client.clientName} (itag=${candidate.itag})")
                            }
                        }

                        if (verifiedPair != null) {
                            val (selectedFormat, url) = verifiedPair
                            Log.i(TAG, "SUCCESS: Resolved & verified YouTube stream using client: ${client.clientName}")

                            val codecs = try {
                                selectedFormat.mimeType.substringAfter("codecs=").removeSurrounding("\"")
                            } catch (e: Exception) { "unknown" }
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
                            if (updateQualityState) _currentStreamQuality.value = quality

                            try {
                                musicDao?.upsertFormat(FormatEntity(
                                    id = videoId,
                                    mimeType = selectedFormat.mimeType.split(";")[0],
                                    codecs = codecs,
                                    bitrate = selectedFormat.bitrate,
                                    sampleRate = selectedFormat.audioSampleRate,
                                    contentLength = selectedFormat.contentLength ?: 0L
                                ))
                            } catch (e: Exception) {
                                Log.w(TAG, "Failed to save format info", e)
                            }

                            StreamResult(
                                url = url,
                                userAgent = client.userAgent,
                                quality = quality,
                                mode = mode,
                                hasVideo = hasVideo
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
                    } // end withTimeoutOrNull
                } // end runBlocking
                if (result != null) return result
                else throw Exception("Resolution timed out for client ${client.clientName}")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to resolve with client ${client.clientName}: ${e.message}")
                lastError = e
            }
        }

        // Only try NewPipe YouTube if not already blocked by IP-level bot check
        if (botBlockedCount == 0) {
            Log.w(TAG, "All InnerTube clients failed, attempting fallback to NewPipe for YouTube")
            try {
                return resolveYouTubeWithNewPipe(videoId, mode, updateQualityState)
            } catch (e: Exception) {
                Log.w(TAG, "NewPipe YouTube fallback failed: ${e.message}")
                lastError = e
            }
        }

        // Try exact-videoId Piped API instances before falling back to metadata search
        resolveExactVideoIdViaPiped(videoId, mode, updateQualityState)?.let { pipedResult ->
            return pipedResult
        }

        // If VIDEO mode was requested, resolve verified music video stream
        if (mode == PlaybackMediaMode.VIDEO) {
            try {
                val fallbackVideo = resolveFallbackVideoStream(
                    videoId = videoId,
                    titleInput = title,
                    artistInput = artist,
                    allowYouTubeSearch = botBlockedCount == 0,
                    updateQualityState = updateQualityState
                )
                if (fallbackVideo != null) {
                    return fallbackVideo
                }
            } catch (e: Exception) {
                Log.w(TAG, "Fallback video resolution failed for $videoId: ${e.message}")
            }
        }

        // Fallback to strictly verified high-quality audio CDN resolution (Tidal HiFi / JioSaavn)
        try {
            return resolveFallbackAudioStream(
                videoId = videoId,
                titleInput = title,
                artistInput = artist,
                isrc = isrc,
                updateQualityState = updateQualityState
            )
        } catch (e: Exception) {
            Log.e(TAG, "Fallback audio CDN resolution also failed for $videoId", e)
            lastError = e
        }

        // Final safety net: if audio CDN failed, use progressive video stream for audio playback
        if (mode == PlaybackMediaMode.AUDIO) {
            try {
                val fallbackVideoAsAudio = resolveFallbackVideoStream(
                    videoId = videoId,
                    titleInput = title,
                    artistInput = artist,
                    allowYouTubeSearch = botBlockedCount == 0,
                    updateQualityState = updateQualityState
                )
                if (fallbackVideoAsAudio != null) {
                    return fallbackVideoAsAudio.copy(mode = PlaybackMediaMode.AUDIO)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Fallback video-as-audio resolution failed for $videoId: ${e.message}")
            }
        }

        Log.e(TAG, "CRITICAL: All resolution methods failed for $videoId", lastError)
        throw lastError ?: Exception("Failed to resolve stream after trying all sources")
    }

    /**
     * Resolves the exact YouTube videoId via public Piped API instances so the stream is
     * guaranteed to be the exact same videoId when YouTube blocks datacenter IPs directly.
     */
    private fun resolveExactVideoIdViaPiped(
        videoId: String,
        mode: PlaybackMediaMode,
        updateQualityState: Boolean
    ): StreamResult? {
        if (!videoId.matches(Regex("^[a-zA-Z0-9_-]{11}$"))) return null
        val instances = listOf(
            "https://pipedapi.kavin.rocks",
            "https://pipedapi.tokhmi.xyz",
            "https://pipedapi.moomoo.me"
        )
        val userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/136.0.0.0 Safari/537.36"

        for (instance in instances) {
            try {
                val request = okhttp3.Request.Builder()
                    .url("$instance/streams/$videoId")
                    .header("User-Agent", userAgent)
                    .get()
                    .build()
                val body = probeHttpClient.newCall(request).execute().use { resp ->
                    if (resp.isSuccessful) resp.body?.string().orEmpty() else ""
                }
                if (body.isBlank()) continue
                val json = org.json.JSONObject(body)

                if (mode == PlaybackMediaMode.VIDEO) {
                    val videoStreams = json.optJSONArray("videoStreams")
                    if (videoStreams != null) {
                        for (i in 0 until videoStreams.length()) {
                            val item = videoStreams.optJSONObject(i) ?: continue
                            val videoOnly = item.optBoolean("videoOnly", true)
                            val url = item.optString("url")
                            if (!videoOnly && url.isNotBlank() && verifyStreamUrl(url, userAgent)) {
                                val quality = "${item.optString("quality", "360p")} Video"
                                if (updateQualityState) _currentStreamQuality.value = quality
                                Log.i(TAG, "SUCCESS: Resolved exact videoId $videoId video via Piped ($instance)")
                                return StreamResult(
                                    url = url,
                                    userAgent = userAgent,
                                    quality = quality,
                                    mode = PlaybackMediaMode.VIDEO,
                                    hasVideo = true
                                )
                            }
                        }
                    }
                }

                val audioStreams = json.optJSONArray("audioStreams")
                if (audioStreams != null && audioStreams.length() > 0) {
                    val candidates = mutableListOf<Pair<Int, String>>()
                    for (i in 0 until audioStreams.length()) {
                        val item = audioStreams.optJSONObject(i) ?: continue
                        val url = item.optString("url")
                        val bitrate = item.optInt("bitrate", 128000)
                        if (url.isNotBlank()) {
                            candidates.add(bitrate to url)
                        }
                    }
                    for ((bitrate, url) in candidates.sortedByDescending { it.first }.take(3)) {
                        if (verifyStreamUrl(url, userAgent)) {
                            val quality = "Audio ${(bitrate / 1000).coerceAtLeast(128)}kbps"
                            if (updateQualityState) _currentStreamQuality.value = quality
                            Log.i(TAG, "SUCCESS: Resolved exact videoId $videoId audio via Piped ($instance)")
                            return StreamResult(
                                url = url,
                                userAgent = userAgent,
                                quality = quality,
                                mode = PlaybackMediaMode.AUDIO,
                                hasVideo = false
                            )
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }
        return null
    }

    private fun decodeHtmlEntities(input: String): String {
        return input
            .replace("&quot;", "\"")
            .replace("&#039;", "'")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
    }

    private fun cleanMetadataForSearch(raw: String): String {
        return decodeHtmlEntities(raw)
            .replace(Regex("\\([^)]*(official|video|audio|lyric|lyrics|visualizer|hd|hq|4k|remaster|full song|mv|from\\s+)[^)]*\\)", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("\\[[^\\]]*(official|video|audio|lyric|lyrics|visualizer|hd|hq|4k|remaster|full song|mv|from\\s+)[^\\]]*\\]", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("(?i)\\b(official\\s+music\\s+video|official\\s+video|official\\s+audio|lyric\\s+video|full\\s+video|full\\s+song|video\\s+song|audio\\s+song|lyrical\\s+video)\\b"), " ")
            .replace(Regex("(?i)\\s+-\\s+topic$"), "")
            .replace(Regex("[-|•]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun normalizeForComparison(raw: String): String {
        return cleanMetadataForSearch(raw)
            .lowercase()
            .replace(Regex("\\([^)]*\\)"), " ")
            .replace(Regex("\\[[^\\]]*\\]"), " ")
            .replace(Regex("(?i)\\b(feat|ft|featuring)\\b.*$"), " ")
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private val genericChannelOrLabelNames = setOf(
        "unknown",
        "unknown artist",
        "artist",
        "various artists",
        "t series",
        "tseries",
        "zee music company",
        "sony music india",
        "sony music",
        "yrf",
        "yash raj films",
        "saregama",
        "saregama music",
        "tips official",
        "speed records",
        "desi melodies",
        "white hill music",
        "eros now",
        "aditya music",
        "think music india",
        "lahari music",
        "ishtar music"
    )

    private fun isGenericOrChannelArtist(normArtist: String): Boolean {
        if (normArtist.isBlank()) return true
        if (normArtist in genericChannelOrLabelNames) return true
        if (normArtist.endsWith("vevo") || normArtist.endsWith("topic") || normArtist.endsWith("records") || normArtist.endsWith("music") || normArtist.endsWith("official")) {
            return true
        }
        return false
    }

    /**
     * Strictly scores whether a candidate track from a fallback CDN (Tidal HiFi, JioSaavn, iTunes)
     * actually matches the requested track's title and artist.
     * Returns a score in 0..100. Candidates with score < 55 MUST be rejected so a wrong song is never played.
     */
    private fun scoreTrackMatch(
        targetTitle: String,
        targetArtist: String,
        candidateTitle: String,
        candidateArtist: String
    ): Int {
        val normTargetTitle = normalizeForComparison(targetTitle)
        val normCandTitle = normalizeForComparison(candidateTitle)
        if (normTargetTitle.isBlank() || normCandTitle.isBlank()) return 0

        val normTargetArtist = normalizeForComparison(targetArtist)
        val normCandArtist = normalizeForComparison(candidateArtist)

        // Reject unwanted variants (karaoke, instrumental, ringtone, 8d, slowed, lofi) unless requested
        val unwantedVariantTokens = listOf("karaoke", "instrumental", "ringtone", "8d", "nightcore", "slowed", "reverb", "cover")
        for (variant in unwantedVariantTokens) {
            val candHas = candidateTitle.lowercase().contains(variant)
            val targetHas = targetTitle.lowercase().contains(variant)
            if (candHas && !targetHas) {
                return 0
            }
        }

        val targetTitleWords = normTargetTitle.split(" ").filter { it.length >= 2 }
        val candTitleWords = normCandTitle.split(" ").filter { it.length >= 2 }
        if (targetTitleWords.isEmpty() || candTitleWords.isEmpty()) return 0

        val titleIntersection = targetTitleWords.intersect(candTitleWords.toSet())
        val targetRecall = titleIntersection.size.toFloat() / targetTitleWords.size.toFloat()
        val candPrecision = titleIntersection.size.toFloat() / candTitleWords.size.toFloat()

        val titleScore = when {
            normCandTitle == normTargetTitle -> 65
            normTargetTitle.contains(normCandTitle) && normCandTitle.length >= 4 && candTitleWords.size >= 1 && candPrecision >= 0.8f -> {
                // e.g. targetTitle="Artist Song Name", candTitle="Song Name"
                if (normTargetTitle.contains(normCandArtist) || targetRecall >= 0.5f) 58 else 42
            }
            normCandTitle.contains(normTargetTitle) && normTargetTitle.length >= 4 && targetRecall >= 0.8f -> 56
            targetRecall >= 0.75f && candPrecision >= 0.6f -> 52
            targetRecall >= 0.6f && candPrecision >= 0.5f && titleIntersection.any { it.length >= 4 } -> 40
            else -> 0
        }

        if (titleScore == 0) return 0

        val targetArtistWords = normTargetArtist
            .split(" ")
            .filter { it.length >= 2 && it !in setOf("the", "and", "feat", "ft", "with", "official", "music", "vevo", "topic") }
        val candArtistWords = normCandArtist
            .split(" ")
            .filter { it.length >= 2 && it !in setOf("the", "and", "feat", "ft", "with") }

        val artistMatchesTitle = candArtistWords.isNotEmpty() && candArtistWords.any { it.length >= 3 && normTargetTitle.contains(it) }
        val artistIntersection = targetArtistWords.intersect(candArtistWords.toSet())

        val artistScore = when {
            normTargetArtist.isNotBlank() && normCandArtist.isNotBlank() &&
                (normCandArtist == normTargetArtist || normCandArtist.contains(normTargetArtist) || normTargetArtist.contains(normCandArtist)) -> 35
            artistIntersection.isNotEmpty() -> 30
            artistMatchesTitle -> 25
            isGenericOrChannelArtist(normTargetArtist) -> 15
            else -> {
                // Target had a specific artist, and candidate artist did NOT match at all!
                // Reject unless the title has >= 3 distinct words and is an exact title match
                if (normCandTitle == normTargetTitle && targetTitleWords.size >= 3) 5 else -30
            }
        }

        return (titleScore + artistScore).coerceIn(0, 100)
    }

    private data class CanonicalTrackMetadata(
        val primaryTitle: String,
        val primaryArtist: String,
        val secondaryTitle: String? = null,
        val secondaryArtist: String? = null
    )

    /**
     * Retrieves authoritative song title & artist from YouTube.next(videoId) (which is not blocked
     * by /player bot checks) and combines it with caller-supplied title/artist metadata.
     */
    private fun resolveCanonicalMetadata(
        videoId: String,
        titleInput: String?,
        artistInput: String?
    ): CanonicalTrackMetadata {
        val callerTitle = titleInput?.takeIf { it.isNotBlank() && !it.equals("Unknown", ignoreCase = true) }
        val callerArtist = artistInput?.takeIf {
            it.isNotBlank() && !it.equals("Unknown", ignoreCase = true) && !it.equals("Unknown Artist", ignoreCase = true)
        }

        var ytNextTitle: String? = null
        var ytNextArtist: String? = null

        if (videoId.matches(Regex("^[a-zA-Z0-9_-]{11}$"))) {
            val needAuthoritativeLookup = callerTitle.isNullOrBlank() ||
                callerArtist.isNullOrBlank() ||
                isGenericOrChannelArtist(normalizeForComparison(callerArtist)) ||
                callerTitle.contains("|") ||
                callerTitle.contains(" - ")

            if (needAuthoritativeLookup) {
                runCatching {
                    runBlocking {
                        withTimeoutOrNull(4_500L) {
                            val nextResult = YouTube.next(
                                com.zionhuang.innertube.models.WatchEndpoint(
                                    videoId = videoId,
                                    playlistId = "RDAMVM$videoId"
                                )
                            ).getOrNull() ?: YouTube.next(
                                com.zionhuang.innertube.models.WatchEndpoint(videoId = videoId)
                            ).getOrNull()

                            val matchedItem = nextResult?.items?.firstOrNull { it.id == videoId }
                                ?: nextResult?.items?.firstOrNull()
                            if (matchedItem != null && (matchedItem.id == videoId || callerTitle.isNullOrBlank())) {
                                ytNextTitle = matchedItem.title.takeIf { it.isNotBlank() }
                                ytNextArtist = matchedItem.artists
                                    .mapNotNull { it.name.takeIf { n -> n.isNotBlank() } }
                                    .joinToString(", ")
                                    .takeIf { it.isNotBlank() }
                            }
                        }
                    }
                }
            }
        }

        // If callerTitle looks like "Artist - Song Title (Official Video)" and artist is a channel/label
        var splitArtist: String? = null
        var splitTitle: String? = null
        if (!callerTitle.isNullOrBlank() && callerTitle.contains(" - ")) {
            val parts = callerTitle.split(" - ", limit = 2)
            if (parts.size == 2) {
                val left = cleanMetadataForSearch(parts[0].substringBefore("|"))
                val right = cleanMetadataForSearch(parts[1].substringBefore("|"))
                if (left.isNotBlank() && right.isNotBlank()) {
                    splitArtist = left
                    splitTitle = right
                }
            }
        } else if (!callerTitle.isNullOrBlank() && callerTitle.contains("|")) {
            val firstSegment = cleanMetadataForSearch(callerTitle.substringBefore("|"))
            if (firstSegment.isNotBlank()) {
                splitTitle = firstSegment
            }
        }

        val bestTitle = cleanMetadataForSearch(ytNextTitle ?: splitTitle ?: callerTitle ?: "")
        val bestArtist = cleanMetadataForSearch(
            (ytNextArtist ?: callerArtist?.takeIf { !isGenericOrChannelArtist(normalizeForComparison(it)) } ?: splitArtist ?: callerArtist ?: "")
                .substringBefore(",")
                .substringBefore("&")
                .substringBefore("feat")
        )

        val altTitle = callerTitle?.let { cleanMetadataForSearch(it) }?.takeIf { it.isNotBlank() && !it.equals(bestTitle, ignoreCase = true) }
        val altArtist = callerArtist?.let {
            cleanMetadataForSearch(it.substringBefore(",").substringBefore("&").substringBefore("feat"))
        }?.takeIf { it.isNotBlank() }

        return CanonicalTrackMetadata(
            primaryTitle = bestTitle.ifBlank { altTitle.orEmpty() },
            primaryArtist = bestArtist,
            secondaryTitle = altTitle,
            secondaryArtist = altArtist
        )
    }

    private fun decryptSaavnMediaUrl(encrypted: String): String? {
        return try {
            val cipher = javax.crypto.Cipher.getInstance("DES/ECB/PKCS5Padding")
            val keySpec = javax.crypto.spec.SecretKeySpec("38346591".toByteArray(Charsets.UTF_8), "DES")
            cipher.init(javax.crypto.Cipher.DECRYPT_MODE, keySpec)
            val decoded = android.util.Base64.decode(encrypted.trim(), android.util.Base64.DEFAULT)
            String(cipher.doFinal(decoded), Charsets.UTF_8).trim()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to decrypt Saavn URL: ${e.message}")
            null
        }
    }

    /**
     * Resolves high-quality 320kbps AAC/M4A audio streams when YouTube's /player endpoint blocks
     * anonymous datacenter/emulator IPs with LOGIN_REQUIRED ("Sign in to confirm you're not a bot").
     * Strictly verifies candidate track title + artist before accepting any stream URL so a
     * wrong/random song is NEVER played.
     */
    private fun resolveFallbackAudioStream(
        videoId: String,
        titleInput: String?,
        artistInput: String?,
        isrc: String? = null,
        updateQualityState: Boolean = true
    ): StreamResult {
        val meta = resolveCanonicalMetadata(videoId, titleInput, artistInput)
        val cleanTitle = meta.primaryTitle
        val cleanArtist = meta.primaryArtist

        if (cleanTitle.isBlank()) {
            throw Exception("Cannot resolve fallback stream for $videoId without track title")
        }

        val userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/136.0.0.0 Safari/537.36"

        fun evaluateScore(candTitle: String, candArtist: String): Int {
            val s1 = scoreTrackMatch(cleanTitle, cleanArtist, candTitle, candArtist)
            val s2 = if (!meta.secondaryTitle.isNullOrBlank()) {
                scoreTrackMatch(meta.secondaryTitle, meta.secondaryArtist ?: cleanArtist, candTitle, candArtist)
            } else 0
            return maxOf(s1, s2)
        }

        // Tier 1: Global Tidal HiFi Catalog (HifiApiClient - 320kbps AAC/M4A direct HTTP stream)
        runCatching {
            val hifiResult = runBlocking {
                withTimeoutOrNull(8_000L) {
                    var candidates = emptyList<com.israrxy.raazi.player.flac.HifiTrack>()
                    if (!isrc.isNullOrBlank()) {
                        candidates = com.israrxy.raazi.player.flac.HifiApiClient.searchByISRC(isrc)
                    }
                    if (candidates.isEmpty()) {
                        val q = if (cleanArtist.isNotBlank() && !isGenericOrChannelArtist(normalizeForComparison(cleanArtist))) {
                            "$cleanTitle $cleanArtist"
                        } else {
                            cleanTitle
                        }
                        candidates = com.israrxy.raazi.player.flac.HifiApiClient.searchByText(q, limit = 10)
                    }

                    val scoredCandidates = candidates
                        .map { track ->
                            val score = evaluateScore(track.title.orEmpty(), track.displayArtist)
                            track to score
                        }
                        .filter { it.second >= 55 }
                        .sortedByDescending { it.second }

                    for ((matchedTrack, score) in scoredCandidates.take(2)) {
                        for (tierQuality in listOf("HIGH", "LOW")) {
                            val streamResp = com.israrxy.raazi.player.flac.HifiApiClient.getStreamUrl(matchedTrack.id, tierQuality)
                            val directUrl = streamResp?.urls?.firstOrNull { it.isNotBlank() }
                            if (!directUrl.isNullOrBlank() && verifyStreamUrl(directUrl, "TIDAL_ANDROID/1039 okhttp/3.14.9")) {
                                val bitrate = if (tierQuality == "HIGH") 320000 else 96000
                                val qualityLabel = "M4A ${bitrate / 1000}kbps"
                                if (updateQualityState) _currentStreamQuality.value = qualityLabel
                                runCatching {
                                    musicDao?.upsertFormat(
                                        FormatEntity(
                                            id = videoId,
                                            mimeType = "audio/mp4",
                                            codecs = "mp4a.40.2",
                                            bitrate = bitrate,
                                            sampleRate = 44100,
                                            contentLength = 0L
                                        )
                                    )
                                }
                                Log.i(
                                    TAG,
                                    "SUCCESS: Resolved verified HiFi AAC stream ($qualityLabel, score=$score) " +
                                        "for '$cleanTitle' by '$cleanArtist' -> '${matchedTrack.title}' by '${matchedTrack.displayArtist}'"
                                )
                                return@withTimeoutOrNull StreamResult(
                                    url = directUrl,
                                    userAgent = "TIDAL_ANDROID/1039 okhttp/3.14.9",
                                    quality = qualityLabel,
                                    mode = PlaybackMediaMode.AUDIO,
                                    hasVideo = false
                                )
                            }
                        }
                    }
                    null
                }
            }
            if (hifiResult != null) return hifiResult
        }

        val queries = listOfNotNull(
            if (cleanArtist.isNotBlank() && !isGenericOrChannelArtist(normalizeForComparison(cleanArtist))) "$cleanTitle $cleanArtist".trim() else null,
            if (!meta.secondaryTitle.isNullOrBlank() && !meta.secondaryArtist.isNullOrBlank() && !isGenericOrChannelArtist(normalizeForComparison(meta.secondaryArtist))) {
                "${meta.secondaryTitle} ${meta.secondaryArtist}".trim()
            } else null,
            cleanTitle,
            meta.secondaryTitle
        ).distinct()

        // Tier 2: Direct JioSaavn API with JSON parsing & strict Title+Artist verification
        for (query in queries) {
            try {
                val encodedQuery = Uri.encode(query)
                val apiUrl = "https://www.jiosaavn.com/api.php?p=1&q=$encodedQuery&_format=json&_marker=0&api_version=4&ctx=web6dot0&n=10&__call=search.getResults"
                val request = okhttp3.Request.Builder()
                    .url(apiUrl)
                    .header("User-Agent", userAgent)
                    .get()
                    .build()

                val body = probeHttpClient.newCall(request).execute().use { resp ->
                    if (resp.isSuccessful) resp.body?.string().orEmpty() else ""
                }

                if (body.isNotBlank()) {
                    val root = org.json.JSONObject(body)
                    val results = root.optJSONArray("results")
                    if (results != null && results.length() > 0) {
                        data class SaavnCandidate(
                            val title: String,
                            val artist: String,
                            val encryptedUrl: String,
                            val score: Int
                        )
                        val verifiedCandidates = mutableListOf<SaavnCandidate>()
                        for (i in 0 until results.length()) {
                            val item = results.optJSONObject(i) ?: continue
                            val itemTitle = decodeHtmlEntities(
                                item.optString("title").ifBlank { item.optString("song") }
                            )
                            val moreInfo = item.optJSONObject("more_info")
                            val encryptedUrl = moreInfo?.optString("encrypted_media_url")
                                .orEmpty()
                                .ifBlank { item.optString("encrypted_media_url") }
                            if (encryptedUrl.isBlank()) continue

                            val artistBuilder = mutableListOf<String>()
                            val artistMap = moreInfo?.optJSONObject("artistMap")
                            val primaryArtistsArr = artistMap?.optJSONArray("primary_artists")
                                ?: artistMap?.optJSONArray("artists")
                            if (primaryArtistsArr != null) {
                                for (j in 0 until primaryArtistsArr.length()) {
                                    primaryArtistsArr.optJSONObject(j)?.optString("name")?.takeIf { it.isNotBlank() }?.let {
                                        artistBuilder.add(it)
                                    }
                                }
                            }
                            moreInfo?.optString("music")?.takeIf { it.isNotBlank() }?.let { artistBuilder.add(it) }
                            moreInfo?.optString("singers")?.takeIf { it.isNotBlank() }?.let { artistBuilder.add(it) }
                            item.optString("subtitle")?.takeIf { it.isNotBlank() }?.let { artistBuilder.add(it) }

                            val itemArtist = decodeHtmlEntities(artistBuilder.joinToString(", "))
                            val score = evaluateScore(itemTitle, itemArtist)
                            if (score >= 55) {
                                verifiedCandidates.add(
                                    SaavnCandidate(
                                        title = itemTitle,
                                        artist = itemArtist,
                                        encryptedUrl = encryptedUrl.replace("\\/", "/"),
                                        score = score
                                    )
                                )
                            } else {
                                Log.d(TAG, "Rejected Saavn mismatch (score=$score): '$itemTitle' by '$itemArtist' vs target '$cleanTitle' by '$cleanArtist'")
                            }
                        }

                        for (cand in verifiedCandidates.sortedByDescending { it.score }.take(3)) {
                            val baseDecrypted = decryptSaavnMediaUrl(cand.encryptedUrl) ?: continue
                            val urlVariants = listOf(
                                baseDecrypted.replace("_96.mp4", "_320.mp4").replace("_160.mp4", "_320.mp4"),
                                baseDecrypted.replace("_96.mp4", "_160.mp4"),
                                baseDecrypted
                            ).distinct()

                            for (candidateUrl in urlVariants) {
                                if (verifyStreamUrl(candidateUrl, userAgent)) {
                                    val bitrate = when {
                                        candidateUrl.contains("_320.") -> 320000
                                        candidateUrl.contains("_160.") -> 160000
                                        else -> 96000
                                    }
                                    val quality = "M4A ${bitrate / 1000}kbps"
                                    if (updateQualityState) _currentStreamQuality.value = quality
                                    runCatching {
                                        musicDao?.upsertFormat(
                                            FormatEntity(
                                                id = videoId,
                                                mimeType = "audio/mp4",
                                                codecs = "mp4a.40.2",
                                                bitrate = bitrate,
                                                sampleRate = 44100,
                                                contentLength = 0L
                                            )
                                        )
                                    }
                                    Log.i(
                                        TAG,
                                        "SUCCESS: Resolved verified Saavn stream ($quality, score=${cand.score}) " +
                                            "for '$cleanTitle' -> '${cand.title}' by '${cand.artist}'"
                                    )
                                    return StreamResult(
                                        url = candidateUrl,
                                        userAgent = userAgent,
                                        quality = quality,
                                        mode = PlaybackMediaMode.AUDIO,
                                        hasVideo = false
                                    )
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Tier 2 Saavn fallback failed for query '$query': ${e.message}")
            }
        }

        // Tier 3: Saavn API Mirror (saavn.sumit.co) with JSON parsing & strict Title+Artist verification
        for (query in queries) {
            try {
                val encodedQuery = Uri.encode(query)
                val apiUrl = "https://saavn.sumit.co/api/search/songs?query=$encodedQuery&limit=10"
                val request = okhttp3.Request.Builder()
                    .url(apiUrl)
                    .header("User-Agent", userAgent)
                    .get()
                    .build()

                val body = probeHttpClient.newCall(request).execute().use { resp ->
                    if (resp.isSuccessful) resp.body?.string().orEmpty() else ""
                }

                if (body.isNotBlank()) {
                    val root = org.json.JSONObject(body)
                    val results = root.optJSONObject("data")?.optJSONArray("results")
                    if (results != null && results.length() > 0) {
                        data class MirrorCandidate(
                            val title: String,
                            val artist: String,
                            val urls: List<String>,
                            val score: Int
                        )
                        val verifiedMirror = mutableListOf<MirrorCandidate>()
                        for (i in 0 until results.length()) {
                            val item = results.optJSONObject(i) ?: continue
                            val itemTitle = decodeHtmlEntities(item.optString("name"))
                            val artistsObj = item.optJSONObject("artists")
                            val primaryArr = artistsObj?.optJSONArray("primary") ?: artistsObj?.optJSONArray("all")
                            val artistNames = mutableListOf<String>()
                            if (primaryArr != null) {
                                for (j in 0 until primaryArr.length()) {
                                    primaryArr.optJSONObject(j)?.optString("name")?.takeIf { it.isNotBlank() }?.let {
                                        artistNames.add(it)
                                    }
                                }
                            }
                            val itemArtist = decodeHtmlEntities(artistNames.joinToString(", "))
                            val score = evaluateScore(itemTitle, itemArtist)
                            if (score < 55) continue

                            val downloadUrlArr = item.optJSONArray("downloadUrl") ?: continue
                            val extractedUrls = mutableListOf<String>()
                            for (j in 0 until downloadUrlArr.length()) {
                                val u = downloadUrlArr.optJSONObject(j)?.optString("url").orEmpty().replace("\\/", "/")
                                if (u.isNotBlank()) extractedUrls.add(u)
                            }
                            val orderedUrls = extractedUrls.filter { it.contains("_320.") } +
                                extractedUrls.filter { it.contains("_160.") } +
                                extractedUrls.reversed()
                            if (orderedUrls.isNotEmpty()) {
                                verifiedMirror.add(
                                    MirrorCandidate(
                                        title = itemTitle,
                                        artist = itemArtist,
                                        urls = orderedUrls.distinct(),
                                        score = score
                                    )
                                )
                            }
                        }

                        for (cand in verifiedMirror.sortedByDescending { it.score }.take(3)) {
                            for (candidateUrl in cand.urls) {
                                if (verifyStreamUrl(candidateUrl, userAgent)) {
                                    val quality = if (candidateUrl.contains("_320.")) "M4A 320kbps" else "M4A 160kbps"
                                    if (updateQualityState) _currentStreamQuality.value = quality
                                    Log.i(
                                        TAG,
                                        "SUCCESS: Resolved verified mirror stream ($quality, score=${cand.score}) " +
                                            "for '$cleanTitle' -> '${cand.title}' by '${cand.artist}'"
                                    )
                                    return StreamResult(
                                        url = candidateUrl,
                                        userAgent = userAgent,
                                        quality = quality,
                                        mode = PlaybackMediaMode.AUDIO,
                                        hasVideo = false
                                    )
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Tier 3 mirror fallback failed for query '$query': ${e.message}")
            }
        }

        throw Exception("Unable to resolve verified audio stream for '$cleanTitle' by '$cleanArtist' ($videoId)")
    }

    private fun selectPreferredMuxedVideoFormat(
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
                            { kotlin.math.abs((it.height ?: Int.MAX_VALUE) - maxHeight) },
                            { it.height ?: Int.MAX_VALUE },
                            { if (it.mimeType.contains("mp4")) 0 else 1 }
                        )
                    ) ?: pickHighestCandidate(safeFormats)
            }
        } ?: pickHighestCandidate(safeFormats)
    }

    /**
     * Fallback: Resolve YouTube stream using NewPipe with timeout
     */
    private fun resolveYouTubeWithNewPipe(
        videoId: String,
        mode: PlaybackMediaMode = PlaybackMediaMode.AUDIO,
        updateQualityState: Boolean = true
    ): StreamResult {
        return runBlocking {
            withTimeoutOrNull(NEWPIPE_TIMEOUT_MS) {
                Log.d(TAG, "Fetching YouTube stream info using NewPipe for: $videoId (mode=$mode)")
                val url = if (videoId.contains("youtube.com") || videoId.contains("youtu.be")) {
                    videoId
                } else {
                    "https://www.youtube.com/watch?v=$videoId"
                }
                val streamInfo = StreamInfo.getInfo(ServiceList.YouTube, url)
                val userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/136.0.0.0 Safari/537.36"

                if (mode == PlaybackMediaMode.VIDEO) {
                    val muxedVideoStreams = streamInfo.videoStreams
                        .filter { it.content.isNotBlank() && !it.isVideoOnly }
                        .sortedByDescending { it.height }
                    val videoStream = muxedVideoStreams.firstOrNull {
                        verifyStreamUrl(it.content, userAgent)
                    } ?: muxedVideoStreams.firstOrNull()

                    if (videoStream != null) {
                        val quality = "${videoStream.resolution.ifBlank { "360p" }} Video"
                        if (updateQualityState) _currentStreamQuality.value = quality
                        return@withTimeoutOrNull StreamResult(
                            url = videoStream.content,
                            userAgent = userAgent,
                            quality = quality,
                            mode = PlaybackMediaMode.VIDEO,
                            hasVideo = true
                        )
                    }
                }

                val sortedAudioStreams = streamInfo.audioStreams
                    .filter { it.content.isNotBlank() }
                    .sortedByDescending { it.averageBitrate }

                val audioStream = sortedAudioStreams.firstOrNull {
                    verifyStreamUrl(it.content, userAgent)
                } ?: sortedAudioStreams.firstOrNull()

                if (audioStream != null && mode == PlaybackMediaMode.AUDIO) {
                    val format = audioStream.format?.name ?: "M4A"
                    val bitrate = audioStream.averageBitrate / 1000
                    val quality = "$format ${bitrate}kbps (NewPipe)"

                    if (updateQualityState) _currentStreamQuality.value = quality
                    StreamResult(
                        url = audioStream.content,
                        userAgent = userAgent,
                        quality = quality,
                        hasVideo = false
                    )
                } else {
                    throw Exception("No matching $mode streams found via NewPipe")
                }
            } ?: throw Exception("NewPipe resolution timed out after ${NEWPIPE_TIMEOUT_MS}ms")
        }
    }

    /**
     * Resolves a playable H.264/AAC MP4 Music Video stream when a song is an audio-only Art Track
     * or when YouTube's /player endpoint blocks anonymous IPs.
     * Strictly verifies candidate video title + artist so an unrelated video is NEVER played.
     */
    private fun resolveFallbackVideoStream(
        videoId: String,
        titleInput: String?,
        artistInput: String?,
        allowYouTubeSearch: Boolean,
        updateQualityState: Boolean = true
    ): StreamResult? {
        val meta = resolveCanonicalMetadata(videoId, titleInput, artistInput)
        val cleanTitle = meta.primaryTitle
        val cleanArtist = meta.primaryArtist
        if (cleanTitle.isBlank()) return null

        val userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/136.0.0.0 Safari/537.36"

        fun evaluateScore(candTitle: String, candArtist: String): Int {
            val s1 = scoreTrackMatch(cleanTitle, cleanArtist, candTitle, candArtist)
            val s2 = if (!meta.secondaryTitle.isNullOrBlank()) {
                scoreTrackMatch(meta.secondaryTitle, meta.secondaryArtist ?: cleanArtist, candTitle, candArtist)
            } else 0
            return maxOf(s1, s2)
        }

        // Tier 1: If YouTube is not bot-blocked, check if there is a verified official YouTube Music Video ID
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
                            ?.filterIsInstance<com.zionhuang.innertube.models.SongItem>()
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
                                    if (updateQualityState) _currentStreamQuality.value = qLabel
                                    StreamResult(
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
                val request = okhttp3.Request.Builder()
                    .url(itunesVideoUrl)
                    .header("User-Agent", userAgent)
                    .get()
                    .build()

                val body = probeHttpClient.newCall(request).execute().use { resp ->
                    if (resp.isSuccessful) resp.body?.string().orEmpty() else ""
                }
                if (body.isNotBlank()) {
                    val root = org.json.JSONObject(body)
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
                                if (updateQualityState) _currentStreamQuality.value = quality
                                Log.i(TAG, "SUCCESS: Resolved verified Apple Music Video (score=$score) for '$cleanTitle'")
                                return StreamResult(
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
                Log.w(TAG, "Apple Music Video fallback failed for '$query': ${e.message}")
            }
        }

        return null
    }
}
