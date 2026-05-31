package com.israrxy.raazi.player

import com.zionhuang.innertube.YouTube
import com.zionhuang.innertube.models.YouTubeClient
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import android.util.Log
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.StreamInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.asStateFlow
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

    data class StreamResult(
        val url: String,
        val userAgent: String,
        val quality: String? = null,
        val mode: PlaybackMediaMode = PlaybackMediaMode.AUDIO,
        val hasVideo: Boolean = false
    )

    private val _currentStreamQuality = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    val currentStreamQuality = _currentStreamQuality.asStateFlow()

    private val poTokenGenerator = com.israrxy.raazi.player.potoken.PoTokenGenerator()
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
        preferAac: Boolean = false
    ): StreamResult {
        try {
            return resolveStreamUrlInternal(videoIdInput, title, artist, mode, preferredVideoQuality, preferAac)
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
        preferAac: Boolean = false
    ): StreamResult {
        var videoId = videoIdInput
        Log.d(TAG, "Resolving stream for input: $videoId")
        
        // Robustly extract ID if input is a URL
        if (videoId.contains("youtube.com") || videoId.contains("youtu.be")) {
            if (videoId.contains("v=")) {
                videoId = videoId.substringAfter("v=").substringBefore("&")
            } else if (videoId.contains("youtu.be/")) {
                videoId = videoId.substringAfter("youtu.be/").substringBefore("?")
            } else if (videoId.contains("/shorts/")) {
                videoId = videoId.substringAfter("/shorts/").substringBefore("?")
            }
             
            if (videoId.contains("youtube.com") || videoId.contains("youtu.be")) {
                if (videoId.startsWith("http")) {
                    Log.e(TAG, "Failed to extract ID from YouTube URL: $videoId")
                     
                    if (!title.isNullOrEmpty()) {
                        val fallbackId = runBlocking {
                            withTimeoutOrNull(RESOLVE_TIMEOUT_MS) {
                                try {
                                    val extractor = com.israrxy.raazi.service.YouTubeMusicExtractor.getInstance()
                                    val query = if (!artist.isNullOrEmpty()) "$title $artist" else title
                                    val searchResult = extractor.searchMusic(query)
                                    searchResult.items.firstOrNull()?.id
                                } catch (e: Exception) {
                                    Log.e(TAG, "Fallback search failed", e)
                                    null
                                }
                            }
                        }
                         
                        if (!fallbackId.isNullOrEmpty()) {
                            Log.i(TAG, "Fallback search successful! New ID: $fallbackId")
                            videoId = fallbackId
                        } else {
                            throw IllegalArgumentException("Invalid YouTube URL and fallback failed: $videoIdInput")
                        }
                    } else {
                        throw IllegalArgumentException("Invalid YouTube URL and no metadata for fallback: $videoIdInput")
                    }
                }
            }
        }

        // Detect if this is a Bandcamp URL
        if (videoId.contains("bandcamp.com")) {
            Log.i(TAG, "Detected Bandcamp URL, using NewPipe extractor")
            val cacheKey = normalizeCacheKey(videoId, mode, preferredVideoQuality, preferAac)
            getCachedStream(cacheKey)?.let { cached ->
                _currentStreamQuality.value = cached.quality
                return cached
            }
            return resolveBandcampStream(videoId).also { resolved ->
                cacheResolvedStream(cacheKey, resolved)
            }
        }

        val cacheKey = normalizeCacheKey(videoId, mode, preferredVideoQuality, preferAac)
        getCachedStream(cacheKey)?.let { cached ->
            _currentStreamQuality.value = cached.quality
            return cached
        }

        val resolved = when {
            videoId.contains("soundcloud.com") -> {
                Log.i(TAG, "Detected SoundCloud URL, using NewPipe extractor")
                resolveSoundCloudStream(videoId)
            }
            else -> resolveYouTubeStream(videoId, mode, preferredVideoQuality, preferAac)
        }
        cacheResolvedStream(cacheKey, resolved)
        return resolved
    }

    fun preloadStream(videoIdInput: String, title: String? = null, artist: String? = null) {
        val cacheKey = normalizeCacheKey(
            videoIdInput,
            PlaybackMediaMode.AUDIO,
            PlaybackVideoQuality.AUTO
        )
        if (getCachedStream(cacheKey) != null) return

        synchronized(preloadLock) {
            if (!inFlightPreloads.add(cacheKey)) return
        }

        preloadScope.launch {
            try {
                resolveStreamUrl(videoIdInput, title, artist)
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

    private fun normalizeCacheKey(
        videoIdInput: String,
        mode: PlaybackMediaMode,
        preferredVideoQuality: PlaybackVideoQuality,
        preferAac: Boolean = false
    ): String {
        val trimmed = videoIdInput.trim()
        val normalized = when {
            trimmed.contains("soundcloud.com") || trimmed.contains("bandcamp.com") -> trimmed
            trimmed.contains("v=") -> trimmed.substringAfter("v=").substringBefore("&")
            trimmed.contains("youtu.be/") -> trimmed.substringAfter("youtu.be/").substringBefore("?")
            trimmed.contains("/shorts/") -> trimmed.substringAfter("/shorts/").substringBefore("?")
            else -> trimmed
        }
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
    private fun resolveBandcampStream(url: String): StreamResult {
        return runBlocking {
            withTimeoutOrNull(NEWPIPE_TIMEOUT_MS) {
                Log.d(TAG, "Fetching Bandcamp stream info for: $url")
                val streamInfo = StreamInfo.getInfo(ServiceList.Bandcamp, url)
                
                val audioStream = streamInfo.audioStreams.maxByOrNull { it.averageBitrate }
                
                if (audioStream != null) {
                    Log.i(TAG, "SUCCESS: Resolved Bandcamp audio stream")
                    val quality = "MP3 ${(audioStream.averageBitrate / 1000)}kbps"
                    _currentStreamQuality.value = quality
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
    private fun resolveSoundCloudStream(url: String): StreamResult {
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
                    _currentStreamQuality.value = quality
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
    
    /**
     * Resolve YouTube stream using InnerTube with timeout protection
     */
    private fun resolveYouTubeStream(
        videoId: String,
        mode: PlaybackMediaMode,
        preferredVideoQuality: PlaybackVideoQuality,
        preferAac: Boolean = false
    ): StreamResult {
        val clients = listOf(
            YouTubeClient.ANDROID_VR_NO_AUTH,
            YouTubeClient.WEB_REMIX,
            YouTubeClient.IOS,
            YouTubeClient.ANDROID
        )

        var lastError: Throwable? = null
        
        Log.i(TAG, "Resolving YouTube stream for videoId: $videoId")
        
        val isLoggedIn = YouTube.cookie != null
        val sessionId = if (isLoggedIn) YouTube.dataSyncId else YouTube.visitorData

        val signatureTimestamp = com.zionhuang.innertube.NewPipeUtils.getSignatureTimestamp(videoId).getOrNull()

        for (client in clients) {
            try {
                val result = runBlocking {
                    withTimeoutOrNull(RESOLVE_TIMEOUT_MS) {
                        Log.d(TAG, "Attempting resolve with client: $client")
                        var webPlayerPot: String? = null
                        var webStreamingPot: String? = null
                        
                        if (client.useWebPoTokens) {
                            if (sessionId != null) {
                                try {
                                    val potResult = poTokenGenerator.getWebClientPoToken(videoId, sessionId)
                                    if (potResult != null) {
                                        webPlayerPot = potResult.playerRequestPoToken
                                        webStreamingPot = potResult.streamingDataPoToken
                                    }
                                } catch (e: Exception) {
                                    Log.e(TAG, "Failed to get PoToken", e)
                                }
                            }
                        }

                        val playerResult = YouTube.player(
                            videoId,
                            client = client,
                            signatureTimestamp = signatureTimestamp,
                            webPlayerPot = webPlayerPot
                        )
                        
                        val response = playerResult.getOrThrow()
                        val streamingData = response.streamingData
                            ?: throw Exception("Missing streaming data for client $client")
                        val muxedVideoFormats = streamingData.formats
                            .orEmpty()
                            .filter { format ->
                                !format.isAudio &&
                                    format.url != null &&
                                    (format.audioChannels ?: 0) > 0
                            }
                        val hasVideo = muxedVideoFormats.isNotEmpty()
                        
                        if (response.playabilityStatus.status != "OK") {
                            Log.w(TAG, "Playability status not OK for $client: ${response.playabilityStatus.status}")
                        }

                        val adaptiveFormats = streamingData.adaptiveFormats
                        val audioFormats = adaptiveFormats.filter { it.isAudio }
                        val selectedFormat = when (mode) {
                            PlaybackMediaMode.AUDIO -> {
                                audioFormats.maxByOrNull { format ->
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
                            }
                            PlaybackMediaMode.VIDEO -> {
                                selectPreferredMuxedVideoFormat(
                                    muxedVideoFormats,
                                    preferredVideoQuality
                                )
                            }
                        }

                        if (selectedFormat != null && selectedFormat.url != null) {
                            Log.i(TAG, "SUCCESS: Resolved YouTube stream using client: $client")
                            var url = selectedFormat.url!!
                            if (client.useWebPoTokens && webStreamingPot != null) {
                                url += "&pot=$webStreamingPot"
                            }
                            
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
                            _currentStreamQuality.value = quality

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
                                    "No video formats found with client $client"
                                } else {
                                    "No audio formats found with client $client"
                                }
                            )
                        }
                    } // end withTimeoutOrNull
                } // end runBlocking
                if (result != null) return result
                else throw Exception("Resolution timed out for client $client")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to resolve with client $client: ${e.message}")
                lastError = e
            }
        }
        
        if (mode == PlaybackMediaMode.AUDIO) {
            // If all InnerTube clients failed, try NewPipe as a final fallback
            Log.w(TAG, "All InnerTube clients failed, attempting fallback to NewPipe for YouTube")
            try {
                return resolveYouTubeWithNewPipe(videoId)
            } catch (e: Exception) {
                Log.e(TAG, "NewPipe fallback also failed", e)
                lastError = e
            }
        }
        
        Log.e(TAG, "CRITICAL: All resolution methods failed for $videoId", lastError)
        throw lastError ?: Exception("Failed to resolve YouTube stream after trying all clients")
    }

    private fun selectPreferredMuxedVideoFormat(
        formats: List<PlayerResponse.StreamingData.Format>,
        preferredVideoQuality: PlaybackVideoQuality
    ): PlayerResponse.StreamingData.Format? {
        val safeFormats = formats.filter { it.url != null }
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
    private fun resolveYouTubeWithNewPipe(videoId: String): StreamResult {
        return runBlocking {
            withTimeoutOrNull(NEWPIPE_TIMEOUT_MS) {
                Log.d(TAG, "Fetching YouTube stream info using NewPipe for: $videoId")
                val url = if (videoId.contains("youtube.com") || videoId.contains("youtu.be")) {
                    videoId
                } else {
                    "https://www.youtube.com/watch?v=$videoId"
                }
                val streamInfo = StreamInfo.getInfo(ServiceList.YouTube, url)
                
                val audioStream = streamInfo.audioStreams.maxByOrNull { it.averageBitrate }
                
                if (audioStream != null) {
                    val format = audioStream.format?.name ?: "M4A"
                    val bitrate = audioStream.averageBitrate / 1000
                    val quality = "$format ${bitrate}kbps (NewPipe)"
                    
                    _currentStreamQuality.value = quality
                    StreamResult(
                        url = audioStream.content,
                        userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
                        quality = quality,
                        hasVideo = false
                    )
                } else {
                    throw Exception("No audio streams found via NewPipe")
                }
            } ?: throw Exception("NewPipe resolution timed out after ${NEWPIPE_TIMEOUT_MS}ms")
        }
    }
}
