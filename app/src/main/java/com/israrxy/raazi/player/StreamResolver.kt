package com.israrxy.raazi.player

import com.zionhuang.innertube.YouTube
import com.zionhuang.innertube.models.YouTubeClient
import kotlinx.coroutines.runBlocking
import android.util.Log
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.StreamInfo
import kotlinx.coroutines.flow.asStateFlow
import com.israrxy.raazi.data.db.MusicDao
import com.israrxy.raazi.data.db.FormatEntity

object StreamResolver {
    private const val TAG = "StreamResolver"
    
    // Injected DAO for saving format info
    var musicDao: MusicDao? = null

    /**
     * Resolves the actual stream URL for a given video ID.
     * This is called synchronously by the player on a background thread.
     */
    /**
     * Resolves the actual stream URL for a given video ID.
     * This is called synchronously by the player on a background thread.
     */
    data class StreamResult(val url: String, val userAgent: String, val quality: String? = null)

    private val _currentStreamQuality = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    val currentStreamQuality = _currentStreamQuality.asStateFlow()

    private val poTokenGenerator = com.israrxy.raazi.player.potoken.PoTokenGenerator()

    /**
     * Resolves the actual stream URL for a given video ID or URL.
     * Supports both YouTube and SoundCloud.
     * This is called synchronously by the player on a background thread.
     */
    /**
     * Resolves the actual stream URL for a given video ID or URL.
     * Supports both YouTube and SoundCloud.
     * This is called synchronously by the player on a background thread.
     * 
     * @param videoIdInput The video ID or URL to resolve.
     * @param title Optional title for fallback search.
     * @param artist Optional artist for fallback search.
     */
    fun resolveStreamUrl(videoIdInput: String, title: String? = null, artist: String? = null): StreamResult {
        var videoId = videoIdInput
        Log.d(TAG, "Resolving stream for input: $videoId (Metadata: $title - $artist)")
        
        // Robustly extract ID if input is a URL
        if (videoId.contains("youtube.com") || videoId.contains("youtu.be")) {
             if (videoId.contains("v=")) {
                 videoId = videoId.substringAfter("v=").substringBefore("&")
                 Log.d(TAG, "Extracted ID from URL: $videoId")
             } else if (videoId.contains("youtu.be/")) {
                 videoId = videoId.substringAfter("youtu.be/").substringBefore("?")
                 Log.d(TAG, "Extracted ID from short URL: $videoId")
             } else if (videoId.contains("/shorts/")) {
                 videoId = videoId.substringAfter("/shorts/").substringBefore("?")
                 Log.d(TAG, "Extracted ID from Shorts URL: $videoId")
             }
             
             // FAIL FAST & RETRY: If it's a YouTube URL but we still have a full URL (no ID extracted), logic failed.
             // This specifically catches "https://www.youtube.com/watch" without parameters.
             if (videoId.contains("youtube.com") || videoId.contains("youtu.be")) {
                 if (videoId.startsWith("http")) {
                     Log.e(TAG, "Failed to extract ID from YouTube URL: $videoId")
                     
                     // FALLBACK: If we have metadata, try to search for the song
                     if (!title.isNullOrEmpty()) {
                         Log.i(TAG, "Attempting fallback search for '$title - $artist'")
                         val fallbackId = runBlocking {
                             try {
                                 val extractor = com.israrxy.raazi.service.YouTubeMusicExtractor()
                                 // Search for the song
                                 val query = if (!artist.isNullOrEmpty()) "$title $artist" else title
                                 val searchResult = extractor.searchMusic(query) // Use searchMusic which returns SearchResult
                                 // Get first song result
                                 searchResult.items.firstOrNull()?.id
                             } catch (e: Exception) {
                                 Log.e(TAG, "Fallback search failed", e)
                                 null
                             }
                         }
                         
                         if (!fallbackId.isNullOrEmpty()) {
                             Log.i(TAG, "Fallback search successful! New ID: $fallbackId")
                             videoId = fallbackId
                             // Continue to resolution with new ID
                         } else {
                             Log.e(TAG, "Fallback search yielded no results")
                             throw IllegalArgumentException("Invalid YouTube URL (missing ID): $videoIdInput and fallback failed")
                         }
                     } else {
                         throw IllegalArgumentException("Invalid YouTube URL (missing ID): $videoIdInput and no metadata for fallback")
                     }
                 }
             }
        }

        // Detect if this is a SoundCloud URL
        if (videoId.contains("soundcloud.com")) {
            Log.i(TAG, "Detected SoundCloud URL, using NewPipe extractor")
            return resolveSoundCloudStream(videoId)
        }

        // Detect if this is a Bandcamp URL
        if (videoId.contains("bandcamp.com")) {
            Log.i(TAG, "Detected Bandcamp URL, using NewPipe extractor")
            return resolveBandcampStream(videoId)
        }
        
        // Otherwise, use YouTube extraction
        return resolveYouTubeStream(videoId)
    }
    
    /**
     * Resolve Bandcamp stream using NewPipe
     */
    private fun resolveBandcampStream(url: String): StreamResult {
        return runBlocking {
            try {
                Log.d(TAG, "Fetching Bandcamp stream info for: $url")
                val streamInfo = StreamInfo.getInfo(ServiceList.Bandcamp, url)
                
                val audioStream = streamInfo.audioStreams.maxByOrNull { it.averageBitrate }
                
                if (audioStream != null) {
                    Log.i(TAG, "SUCCESS: Resolved Bandcamp audio stream")
                    val quality = "MP3 ${(audioStream.averageBitrate / 1000)}kbps"
                    _currentStreamQuality.value = quality
                    return@runBlocking StreamResult(
                        url = audioStream.content,
                        userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
                        quality = quality
                    )
                }
                
                throw Exception("No audio streams found for Bandcamp track")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to resolve Bandcamp stream", e)
                throw e
            }
        }
    }
    
    /**
     * Resolve SoundCloud stream using NewPipe
     */
    private fun resolveSoundCloudStream(url: String): StreamResult {
        return runBlocking {
            try {
                Log.d(TAG, "Fetching SoundCloud stream info for: $url")
                val streamInfo = StreamInfo.getInfo(ServiceList.SoundCloud, url)
                
                // Prefer progressive HTTP streams over HLS to avoid format issues
                // SoundCloud typically provides both formats
                val audioStream = streamInfo.audioStreams
                    .filter { !it.content.contains(".m3u8") } // Exclude HLS streams
                    .maxByOrNull { it.averageBitrate }
                    ?: streamInfo.audioStreams.maxByOrNull { it.averageBitrate } // Fallback to any stream
                
                if (audioStream != null) {
                    val format = audioStream.format?.name ?: "MP3"
                    val bitrate = audioStream.averageBitrate / 1000
                    val quality = "$format ${bitrate}kbps"
                    Log.i(TAG, "SUCCESS: Resolved SoundCloud audio stream (format: $format)")
                    _currentStreamQuality.value = quality
                    return@runBlocking StreamResult(
                        url = audioStream.content,
                        userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
                        quality = quality
                    )
                }
                
                // Fallback to video streams if no audio-only
                val videoStream = streamInfo.videoOnlyStreams.firstOrNull() 
                    ?: streamInfo.videoStreams.firstOrNull()
                    
                if (videoStream != null) {
                    Log.i(TAG, "SUCCESS: Resolved SoundCloud video stream (fallback)")
                    return@runBlocking StreamResult(
                        url = videoStream.content,
                        userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
                        quality = "Video Stream"
                    )
                }
                
                throw Exception("No streams found for SoundCloud track")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to resolve SoundCloud stream", e)
                throw e
            }
        }
    }
    
    /**
     * Resolve YouTube stream using InnerTube (original logic)
     */
    private fun resolveYouTubeStream(videoId: String): StreamResult {
        // Use InnerTube to fetch player response
        // We use runBlocking because this is called from a synchronous DataSource method,
        // but likely on a background thread (ExoPlayer loader thread).
        // List of clients to try in order of preference
        val clients = listOf(
            YouTubeClient.ANDROID_VR_NO_AUTH,
            YouTubeClient.WEB_REMIX,
            YouTubeClient.IOS,
            YouTubeClient.ANDROID
        )

        var lastError: Throwable? = null
        
        Log.i(TAG, "Resolving YouTube stream for videoId: $videoId")
        
        // Prepare session info for PoToken
        val isLoggedIn = YouTube.cookie != null
        val sessionId = if (isLoggedIn) YouTube.dataSyncId else YouTube.visitorData
        Log.d(TAG, "Session info - isLoggedIn: $isLoggedIn, sessionId present: ${sessionId != null}")

        // Fetch signature timestamp (STS) for clients that need it (like IOS)
        val signatureTimestamp = com.zionhuang.innertube.NewPipeUtils.getSignatureTimestamp(videoId).getOrNull()
        Log.d(TAG, "Signature Timestamp fetched: $signatureTimestamp")

        for (client in clients) {
            try {
                return runBlocking {
                    Log.d(TAG, "Attempting resolve with client: $client")
                    // PoToken logic
                    var webPlayerPot: String? = null
                    var webStreamingPot: String? = null
                    
                    if (client.useWebPoTokens) {
                         if (sessionId != null) {
                             try {
                                 Log.d(TAG, "Requesting PoToken for client $client")
                                 val potResult = poTokenGenerator.getWebClientPoToken(videoId, sessionId)
                                 if (potResult != null) {
                                     webPlayerPot = potResult.playerRequestPoToken
                                     webStreamingPot = potResult.streamingDataPoToken
                                     Log.d(TAG, "PoToken successfully obtained")
                                 } else {
                                     Log.w(TAG, "PoToken result was null")
                                 }
                             } catch (e: Exception) {
                                 Log.e(TAG, "Failed to get PoToken", e)
                             }
                         } else {
                             Log.w(TAG, "Skipping PoToken: sessionId is null")
                         }
                    }

                    val result = YouTube.player(
                        videoId, 
                        client = client, 
                        signatureTimestamp = signatureTimestamp,
                        webPlayerPot = webPlayerPot
                    )
                    
                    val response = result.getOrThrow()
                    
                    if (response.playabilityStatus.status != "OK") {
                         Log.w(TAG, "Playability status not OK for $client: ${response.playabilityStatus.status}")
                    }

                    val adaptiveFormats = response.streamingData?.adaptiveFormats ?: emptyList()
                    val audioFormats = adaptiveFormats.filter { it.isAudio }
                    Log.d(TAG, "Found ${audioFormats.size} audio formats for client $client")
                    
                    val bestFormat = audioFormats.maxByOrNull { format ->
                        var score = format.bitrate.toLong()
                        if (format.mimeType.contains("webm") || format.mimeType.contains("opus")) {
                            score += 10000 // Boost preference for opus
                        }
                        score
                    }
                    
                    if (bestFormat != null && bestFormat.url != null) {
                        Log.i(TAG, "SUCCESS: Resolved YouTube stream using client: $client")
                        var url = bestFormat.url!!
                        if (client.useWebPoTokens && webStreamingPot != null) {
                            url += "&pot=$webStreamingPot"
                            Log.d(TAG, "Appended streaming PoToken to URL")
                        }
                        
                        // Extract quality info
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

                        // Save format info to DB
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

                        // Ensure we use the client's user agent for the stream request
                        return@runBlocking StreamResult(url, client.userAgent, quality)
                    } else {
                        // Only throw if this was the last client or if we really expected this to work
                        Log.w(TAG, "No audio formats found with client $client")
                        // Continue to next client implies we shouldn't throw immediately inside the loop unless we want to abort
                        // But original logic threw exception to catch block
                        throw Exception("No audio formats found with client $client")
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to resolve with client $client: ${e.message}")
                lastError = e
                // Continue to next client
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
        
        // If all failed
        Log.e(TAG, "CRITICAL: All resolution methods failed for $videoId", lastError)
        throw lastError ?: Exception("Failed to resolve YouTube stream after trying all clients")
    }

    /**
     * Fallback: Resolve YouTube stream using NewPipe
     */
    private fun resolveYouTubeWithNewPipe(videoId: String): StreamResult {
        return runBlocking {
            try {
                Log.d(TAG, "Fetching YouTube stream info using NewPipe for: $videoId")
                // Construct standard YouTube URL, handling if videoId is already a URL
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
                    
                    Log.i(TAG, "SUCCESS: Resolved YouTube stream using NewPipe (fallback)")
                    _currentStreamQuality.value = quality
                    return@runBlocking StreamResult(
                        url = audioStream.content,
                        userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
                        quality = quality
                    )
                }
                throw Exception("No audio streams found via NewPipe")
            } catch (e: Exception) {
                Log.e(TAG, "NewPipe YouTube resolution failed", e)
                throw e
            }
        }
    }
}
