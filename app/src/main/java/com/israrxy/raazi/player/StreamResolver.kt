package com.israrxy.raazi.player

import com.zionhuang.innertube.YouTube
import com.zionhuang.innertube.models.YouTubeClient
import kotlinx.coroutines.runBlocking
import android.util.Log

object StreamResolver {
    private const val TAG = "StreamResolver"

    /**
     * Resolves the actual stream URL for a given video ID.
     * This is called synchronously by the player on a background thread.
     */
    data class StreamResult(val url: String, val userAgent: String)

    private val poTokenGenerator = com.israrxy.raazi.player.potoken.PoTokenGenerator()

    /**
     * Resolves the actual stream URL for a given video ID.
     * This is called synchronously by the player on a background thread.
     */
    fun resolveStreamUrl(videoId: String): StreamResult {
        Log.d(TAG, "Resolving stream for videoId: $videoId")
        
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
        
        Log.i(TAG, "Resolving stream for videoId: $videoId")
        
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
                            score += 10000 // Boost preferrence for opus
                        }
                        score
                    }
                    
                    if (bestFormat != null && bestFormat.url != null) {
                        Log.i(TAG, "SUCCESS: Resolved stream using client: $client")
                        var url = bestFormat.url!!
                        if (client.useWebPoTokens && webStreamingPot != null) {
                            url += "&pot=$webStreamingPot"
                            Log.d(TAG, "Appended streaming PoToken to URL")
                        }
                        // Ensure we use the client's user agent for the stream request
                        StreamResult(url, client.userAgent)
                    } else {
                        throw Exception("No audio formats found with client $client (or URL was null)")
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to resolve with client $client: ${e.message}")
                lastError = e
                // Continue to next client
            }
        }
        
        // If all failed
        Log.e(TAG, "CRITICAL: All clients failed to resolve stream for $videoId", lastError)
        throw lastError ?: Exception("Failed to resolve stream after trying all clients")
    }
}
