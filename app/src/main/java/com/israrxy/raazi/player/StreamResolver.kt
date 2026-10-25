package com.israrxy.raazi.player

import android.net.Uri
import android.util.Log
import com.israrxy.raazi.RaaziApplication
import com.israrxy.raazi.data.db.MusicDao
import com.israrxy.raazi.data.local.SettingsDataStore
import com.israrxy.raazi.model.PlaybackMediaMode
import com.israrxy.raazi.model.PlaybackVideoQuality
import com.israrxy.raazi.player.flac.FlacStreamResolver
import com.israrxy.raazi.service.YouTubeMusicExtractor
import com.zionhuang.innertube.YouTube
import com.zionhuang.innertube.models.YouTubeClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.schabi.newpipe.extractor.stream.StreamInfo
import java.util.concurrent.TimeUnit

object StreamResolver {
    internal const val TAG = "StreamResolver"
    private const val CACHE_TTL_MS = 10 * 60 * 1000L
    private const val MAX_CACHE_SIZE = 36
    internal const val RESOLVE_TIMEOUT_MS = 30_000L
    internal const val NEWPIPE_TIMEOUT_MS = 20_000L
    internal const val DEFAULT_WEB_USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/136.0.0.0 Safari/537.36"

    var musicDao: MusicDao? = null
    var sessionFlacEnabled: Boolean? = false

    data class StreamResult(
        val url: String,
        val userAgent: String,
        val quality: String? = null,
        val mode: PlaybackMediaMode = PlaybackMediaMode.AUDIO,
        val hasVideo: Boolean = false,
        val isFlac: Boolean = false
    )

    private val _currentStreamQuality = MutableStateFlow<String?>(null)
    val currentStreamQuality = _currentStreamQuality.asStateFlow()

    internal fun setCurrentStreamQuality(quality: String?) {
        _currentStreamQuality.value = quality
    }

    internal val preloadScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val cacheLock = Any()
    private val streamInfoLock = Any()
    internal val preloadLock = Any()
    internal val inFlightPreloads = mutableSetOf<String>()
    private val resolutionCache = object : LinkedHashMap<String, CachedStreamResult>(MAX_CACHE_SIZE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CachedStreamResult>?): Boolean {
            return size > MAX_CACHE_SIZE
        }
    }
    private val streamInfoCache = object : LinkedHashMap<String, CachedStreamInfo>(18, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CachedStreamInfo>?): Boolean {
            return size > 18
        }
    }

    private data class CachedStreamResult(
        val result: StreamResult,
        val cachedAtMs: Long
    )

    private data class CachedStreamInfo(
        val info: StreamInfo,
        val cachedAtMs: Long
    )

    internal val probeHttpClient = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(4, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    @Volatile internal var youtubeBotBlockedUntilMs: Long = 0L
    @Volatile internal var pipedDeadUntilMs: Long = 0L
    @Volatile internal var hifiDeadUntilMs: Long = 0L

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
                            val extractor = YouTubeMusicExtractor.getInstance()
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

        if (mode == PlaybackMediaMode.AUDIO && !preferAac && title != null && artist != null) {
            val flacPrefs = try {
                runBlocking {
                    val settings = SettingsDataStore(RaaziApplication.instance)
                    Pair(settings.flacEnabled.first(), settings.flacQuality.first())
                }
            } catch (_: Exception) { Pair(false, "HI_RES_LOSSLESS") }

            val flacEnabled = sessionFlacEnabled ?: flacPrefs.first
            val flacQuality = flacPrefs.second

            if (flacEnabled) {
                try {
                    val flacResult = runBlocking {
                        FlacStreamResolver.resolveFlacStream(
                            context = RaaziApplication.instance,
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
        if (updateQualityState && !preferAac && !videoId.contains("soundcloud.com") && !videoId.contains("bandcamp.com")) {
            val alternateMode = if (mode == PlaybackMediaMode.AUDIO) {
                PlaybackMediaMode.VIDEO
            } else {
                PlaybackMediaMode.AUDIO
            }
            preloadAlternateMode(
                videoIdInput = videoId,
                title = title,
                artist = artist,
                mode = alternateMode,
                preferredVideoQuality = preferredVideoQuality,
                isrc = isrc
            )
        }
        return resolved
    }

    fun preloadAlternateMode(
        videoIdInput: String,
        title: String? = null,
        artist: String? = null,
        mode: PlaybackMediaMode,
        preferredVideoQuality: PlaybackVideoQuality = PlaybackVideoQuality.AUTO,
        isrc: String? = null
    ) = preloadAlternateModeImpl(videoIdInput, title, artist, mode, preferredVideoQuality, isrc)

    fun preloadStream(videoIdInput: String, title: String? = null, artist: String? = null) =
        preloadStreamImpl(videoIdInput, title, artist)

    internal fun getCachedStream(cacheKey: String): StreamResult? {
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

    internal fun cacheResolvedStream(cacheKey: String, result: StreamResult) {
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
        synchronized(streamInfoLock) {
            streamInfoCache.keys.removeAll { it.contains(videoId) }
        }
    }

    internal fun getCachedStreamInfo(videoId: String): StreamInfo? {
        synchronized(streamInfoLock) {
            val cached = streamInfoCache[videoId] ?: return null
            if (System.currentTimeMillis() - cached.cachedAtMs > CACHE_TTL_MS) {
                streamInfoCache.remove(videoId)
                return null
            }
            return cached.info
        }
    }

    internal fun cacheStreamInfo(videoId: String, info: StreamInfo) {
        synchronized(streamInfoLock) {
            streamInfoCache[videoId] = CachedStreamInfo(
                info = info,
                cachedAtMs = System.currentTimeMillis()
            )
        }
    }

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
        var lastError: Throwable? = null
        val isLoggedIn = YouTube.cookie != null
        val isRecentlyBotBlocked = !isLoggedIn && System.currentTimeMillis() < youtubeBotBlockedUntilMs
        var botBlockedCount = if (isRecentlyBotBlocked) 2 else 0

        Log.i(TAG, "Resolving YouTube stream directly via NewPipeExtractor for videoId: $videoId (mode=$mode)")

        if (!isRecentlyBotBlocked) {
            try {
                return resolveYouTubeWithNewPipe(
                    videoId = videoId,
                    mode = mode,
                    preferredVideoQuality = preferredVideoQuality,
                    preferAac = preferAac,
                    updateQualityState = updateQualityState
                )
            } catch (e: Exception) {
                Log.w(TAG, "NewPipe direct extraction failed for $videoId ($mode): ${e.message}")
                if (e.message?.contains("not a bot", ignoreCase = true) == true ||
                    e.message?.contains("Sign in", ignoreCase = true) == true
                ) {
                    botBlockedCount++
                }
                lastError = e
            }
        }

        val innerTubeAttempt = resolveYouTubeWithInnerTubeClients(
            videoId = videoId,
            mode = mode,
            preferredVideoQuality = preferredVideoQuality,
            preferAac = preferAac,
            updateQualityState = updateQualityState,
            isLoggedIn = isLoggedIn,
            isRecentlyBotBlocked = isRecentlyBotBlocked,
            initialBotBlockedCount = botBlockedCount
        )
        if (innerTubeAttempt.result != null) return innerTubeAttempt.result
        botBlockedCount = innerTubeAttempt.botBlockedCount
        if (innerTubeAttempt.lastError != null) lastError = innerTubeAttempt.lastError

        if (System.currentTimeMillis() >= pipedDeadUntilMs) {
            resolveExactVideoIdViaPiped(videoId, mode, updateQualityState)?.let { pipedResult ->
                return pipedResult
            }
            pipedDeadUntilMs = System.currentTimeMillis() + 10 * 60_000L
        }

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
                    return fallbackVideoAsAudio.copy(
                        mode = PlaybackMediaMode.AUDIO,
                        hasVideo = false
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "Fallback video-as-audio resolution failed for $videoId: ${e.message}")
            }
        }

        Log.e(TAG, "CRITICAL: All resolution methods failed for $videoId", lastError)
        throw lastError ?: Exception("Failed to resolve stream after trying all sources")
    }
}
