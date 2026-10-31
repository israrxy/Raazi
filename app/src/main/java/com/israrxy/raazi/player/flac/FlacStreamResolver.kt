package com.israrxy.raazi.player.flac

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

object FlacStreamResolver {
    private const val TAG = "FlacStreamResolver"
    private const val FLAC_CACHE_DIR = "flac"
    private const val FLAC_TIMEOUT_MS = 60_000L

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    data class MatchResult(
        val track: HifiTrack,
        val confidence: Float,
    )

    fun matchTrack(
        candidates: List<HifiTrack>,
        title: String,
        artist: String,
    ): HifiTrack? {
        if (candidates.isEmpty()) return null

        val normalizedTitle = title.lowercase().trim()
        val normalizedArtist = artist.lowercase().trim()

        val scored = candidates.map { track ->
            var score = 0f

            val trackTitle = track.title?.lowercase()?.trim() ?: ""
            val trackArtist = track.displayArtist.lowercase().trim()

            if (trackTitle == normalizedTitle) score += 50f
            else if (normalizedTitle.contains(trackTitle) || trackTitle.contains(normalizedTitle)) score += 30f
            else if (fuzzyMatch(normalizedTitle, trackTitle) > 0.7f) score += 20f

            if (trackArtist == normalizedArtist) score += 50f
            else if (normalizedArtist.contains(trackArtist) || trackArtist.contains(normalizedArtist)) score += 30f
            else if (fuzzyMatch(normalizedArtist, trackArtist) > 0.7f) score += 20f

            if (track.isrc != null) score += 5f

            MatchResult(track, score)
        }

        val best = scored.maxByOrNull { it.confidence }
        return if (best != null && best.confidence >= 30f) {
            Log.d(TAG, "Best match: '${best.track.title}' by '${best.track.displayArtist}' (score: ${best.confidence})")
            best.track
        } else {
            Log.w(TAG, "No confident match found (best score: ${best?.confidence ?: 0f})")
            null
        }
    }

    private fun fuzzyMatch(a: String, b: String): Float {
        if (a.isEmpty() || b.isEmpty()) return 0f
        val wordsA = a.split("\\s+".toRegex()).toSet()
        val wordsB = b.split("\\s+".toRegex()).toSet()
        val intersection = wordsA.intersect(wordsB).size
        val union = wordsA.union(wordsB).size
        return if (union > 0) intersection.toFloat() / union.toFloat() else 0f
    }

    suspend fun resolveFlacStream(
        context: Context,
        isrc: String?,
        title: String,
        artist: String,
        quality: String = "HI_RES_LOSSLESS",
    ): FlacResult? = withContext(Dispatchers.IO) {
        Log.i(TAG, "Resolving FLAC for: '$title' by '$artist' (ISRC: $isrc)")

        var candidates = emptyList<HifiTrack>()

        if (!isrc.isNullOrBlank()) {
            Log.d(TAG, "Trying ISRC lookup: $isrc")
            candidates = HifiApiClient.searchByISRC(isrc)
        }

        if (candidates.isEmpty()) {
            Log.d(TAG, "ISRC lookup returned no results, falling back to text search")
            candidates = HifiApiClient.searchByText("$title $artist", limit = 10)
        }

        val matchedTrack = matchTrack(candidates, title, artist)
        if (matchedTrack == null) {
            Log.w(TAG, "No matching track found on Tidal")
            return@withContext null
        }

        Log.i(TAG, "Matched Tidal track: id=${matchedTrack.id}, '${matchedTrack.title}' by '${matchedTrack.displayArtist}'")

        val streamResponse = HifiApiClient.getStreamUrl(matchedTrack.id, quality)
        if (streamResponse == null) {
            Log.e(TAG, "Failed to get stream URL for Tidal track ${matchedTrack.id}")
            return@withContext null
        }

        val streamUrl = when {
            !streamResponse.urls.isNullOrEmpty() -> streamResponse.urls.first()
            !streamResponse.manifest.isNullOrEmpty() -> {
                Log.d(TAG, "Got DASH manifest, using first URL from manifest")
                return@withContext null
            }
            else -> {
                Log.e(TAG, "No stream URL or manifest in response")
                return@withContext null
            }
        }

        val cacheDir = File(context.cacheDir, FLAC_CACHE_DIR)
        if (!cacheDir.exists()) cacheDir.mkdirs()

        val safeFileName = "${matchedTrack.id}.flac"
        val outputFile = File(cacheDir, safeFileName)

        if (outputFile.exists() && outputFile.length() > 0) {
            Log.d(TAG, "FLAC already cached: ${outputFile.absolutePath}")
            return@withContext FlacResult(
                localPath = outputFile.absolutePath,
                qualityLabel = formatQualityLabel(streamResponse, quality),
                title = matchedTrack.title ?: title,
                artist = matchedTrack.displayArtist,
                album = matchedTrack.albumTitle,
                coverUrl = matchedTrack.cover,
                durationMs = matchedTrack.duration,
                tidalTrackId = matchedTrack.id,
            )
        }

        Log.d(TAG, "Downloading FLAC from: $streamUrl")
        try {
            val request = Request.Builder()
                .url(streamUrl)
                .header("User-Agent", "TIDAL_ANDROID/1039 okhttp/3.14.9")
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.e(TAG, "Download failed with status: ${response.code}")
                return@withContext null
            }

            response.body?.byteStream()?.use { input ->
                outputFile.outputStream().use { output ->
                    input.copyTo(output, bufferSize = 8192)
                }
            }

            if (outputFile.length() == 0L) {
                Log.e(TAG, "Downloaded file is empty")
                outputFile.delete()
                return@withContext null
            }

            Log.i(TAG, "FLAC downloaded successfully: ${outputFile.length()} bytes -> ${outputFile.absolutePath}")

            FlacResult(
                localPath = outputFile.absolutePath,
                qualityLabel = formatQualityLabel(streamResponse, quality),
                title = matchedTrack.title ?: title,
                artist = matchedTrack.displayArtist,
                album = matchedTrack.albumTitle,
                coverUrl = matchedTrack.cover,
                durationMs = matchedTrack.duration,
                tidalTrackId = matchedTrack.id,
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download FLAC", e)
            outputFile.delete()
            null
        }
    }

    private fun formatQualityLabel(response: HifiStreamResponse, quality: String): String {
        val mimeType = response.mimeType ?: ""
        return when {
            quality.contains("HI_RES") -> "FLAC Hi-Res"
            quality.contains("LOSSLESS") -> "FLAC CD"
            mimeType.contains("flac") -> "FLAC"
            else -> "HiFi"
        }
    }

    fun clearCache(context: Context) {
        val cacheDir = File(context.cacheDir, FLAC_CACHE_DIR)
        if (cacheDir.exists()) {
            cacheDir.listFiles()?.forEach { it.delete() }
            Log.d(TAG, "FLAC cache cleared")
        }
    }

    fun getCacheSize(context: Context): Long {
        val cacheDir = File(context.cacheDir, FLAC_CACHE_DIR)
        if (!cacheDir.exists()) return 0
        return cacheDir.listFiles()?.sumOf { it.length() } ?: 0
    }
}
