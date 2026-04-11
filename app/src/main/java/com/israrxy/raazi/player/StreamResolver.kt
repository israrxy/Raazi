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

object StreamResolver {
    private const val TAG = "StreamResolver"
    private const val CACHE_TTL_MS = 10 * 60 * 1000L
    private const val MAX_CACHE_SIZE = 12
    private const val RESOLVE_TIMEOUT_MS = 30_000L
    private const val NEWPIPE_TIMEOUT_MS = 20_000L
    
    // Injected DAO for saving format info
    var musicDao: MusicDao? = null

    data class StreamResult(val url: String, val userAgent: String, val quality: String? = null)

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
    fun resolveStreamUrl(videoIdInput: String, title: String? = null, artist: String? = null): StreamResult {
        try {
            return resolveStreamUrlInternal(videoIdInput, title, artist)
        } catch (e: Exception) {
            Log.e(TAG, "CRITICAL: Stream resolution failed completely for: $videoIdInput", e)
            throw e
        }
    }

    private fun resolveStreamUrlInternal(videoIdInput: String, title: String? = null, artist: String? = null): StreamResult {
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
            val cacheKey = normalizeCacheKey(videoId)
            getCachedStream(cacheKey)?.let { cached ->
                _currentStreamQuality.value = cached.quality
                return cached
            }
            return resolveBandcampStream(videoId).also { resolved ->
                cacheResolvedStream(cacheKey, resolved)
            }
        }

        val cacheKey = normalizeCacheKey(videoId)
        getCachedStream(cacheKey)?.let { cached ->
            _currentStreamQuality.value = cached.quality
            return cached
        }

        val resolved = when {
            videoId.contains("soundcloud.com") -> {
                Log.i(TAG, "Detected SoundCloud URL, using NewPipe extractor")
                resolveSoundCloudStream(videoId)
            }
            else -> resolveYouTubeStream(videoId)
        }
        cacheResolvedStream(cacheKey, resolved)
        return resolved
    }

    fun preloadStream(videoIdInput: String, title: String? = null, artist: String? = null) {
        val cacheKey = normalizeCacheKey(videoIdInput)
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

    private fun normalizeCacheKey(videoIdInput: String): String {
        val trimmed = videoIdInput.trim()
        return when {
            trimmed.contains("soundcloud.com") || trimmed.contains("bandcamp.com") -> trimmed
            trimmed.contains("v=") -> trimmed.substringAfter("v=").substringBefore("&")
            trimmed.contains("youtu.be/") -> trimmed.substringAfter("youtu.be/").substringBefore("?")
            trimmed.contains("/shorts/") -> trimmed.substringAfter("/shorts/").substringBefore("?")
            else -> trimmed
        }
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
                        quality = quality
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
                        quality = quality
                    )
                } else {
                    // Fallback to video streams
                    val videoStream = streamInfo.videoOnlyStreams.firstOrNull() 
                        ?: streamInfo.videoStreams.firstOrNull()
                    if (videoStream != null) {
                        StreamResult(
                            url = videoStream.content,
                            userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
                            quality = "Video Stream"
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
    private fun resolveYouTubeStream(videoId: String): StreamResult {
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
                        
                        if (response.playabilityStatus.status != "OK") {
                            Log.w(TAG, "Playability status not OK for $client: ${response.playabilityStatus.status}")
                        }

                        val adaptiveFormats = response.streamingData?.adaptiveFormats ?: emptyList()
                        val audioFormats = adaptiveFormats.filter { it.isAudio }
                        
                        val bestFormat = audioFormats.maxByOrNull { format ->
                            var score = format.bitrate.toLong()
                            if (format.mimeType.contains("webm") || format.mimeType.contains("opus")) {
                                score += 10000
                            }
                            score
                        }
                        
                        if (bestFormat != null && bestFormat.url != null) {
                            Log.i(TAG, "SUCCESS: Resolved YouTube stream using client: $client")
                            var url = bestFormat.url!!
                            if (client.useWebPoTokens && webStreamingPot != null) {
                                url += "&pot=$webStreamingPot"
                            }
                            
                            val codecs = try {
                                bestFormat.mimeType.substringAfter("codecs=").removeSurrounding("\"")
                            } catch (e: Exception) { "unknown" }
                            val quality = if (bestFormat.mimeType.contains("opus")) {
                                "Opus ${bestFormat.bitrate / 1000}kbps"
                            } else if (bestFormat.mimeType.contains("mp4")) {
                                "M4A ${bestFormat.bitrate / 1000}kbps"
                            } else {
                                "YouTube ${bestFormat.bitrate / 1000}kbps"
                            }
                            _currentStreamQuality.value = quality

                            try {
                                musicDao?.upsertFormat(FormatEntity(
                                    id = videoId,
                                    mimeType = bestFormat.mimeType.split(";")[0],
                                    codecs = codecs,
                                    bitrate = bestFormat.bitrate,
                                    sampleRate = bestFormat.audioSampleRate,
                                    contentLength = bestFormat.contentLength ?: 0L
                                ))
                            } catch (e: Exception) {
                                Log.w(TAG, "Failed to save format info", e)
                            }

                            StreamResult(url, client.userAgent, quality)
                        } else {
                            throw Exception("No audio formats found with client $client")
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
        
        // If all InnerTube clients failed, try NewPipe as a final fallback
        Log.w(TAG, "All InnerTube clients failed, attempting fallback to NewPipe for YouTube")
        try {
            return resolveYouTubeWithNewPipe(videoId)
        } catch (e: Exception) {
            Log.e(TAG, "NewPipe fallback also failed", e)
            lastError = e
        }
        
        Log.e(TAG, "CRITICAL: All resolution methods failed for $videoId", lastError)
        throw lastError ?: Exception("Failed to resolve YouTube stream after trying all clients")
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
                        quality = quality
                    )
                } else {
                    throw Exception("No audio streams found via NewPipe")
                }
            } ?: throw Exception("NewPipe resolution timed out after ${NEWPIPE_TIMEOUT_MS}ms")
        }
    }
}
