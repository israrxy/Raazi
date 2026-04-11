package com.israrxy.raazi.data.remote

import android.content.Context
import android.util.Log
import com.israrxy.raazi.data.lyrics.LyricsCache
import com.israrxy.raazi.data.lyrics.LyricsLookupCandidate
import com.israrxy.raazi.data.lyrics.LyricsMetadataSanitizer
import com.israrxy.raazi.data.lyrics.LyricsScriptDetector
import com.israrxy.raazi.data.lyrics.LyricsScriptFilter
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URLEncoder
import kotlin.math.abs

data class Lyrics(
    val id: Int,
    val trackName: String,
    val artistName: String,
    val plainLyrics: String?,
    val syncedLyrics: String?,
    val duration: Double,
    val language: String? = null,
    val source: String = "LRCLIB"
)

data class LyricsSearchResult(
    val lyrics: Lyrics,
    val matchScore: Int,
    val titleScore: Int,
    val artistScore: Int,
    val durationDelta: Double
)

class LrcLibApi(context: Context) {
    private val client = OkHttpClient()
    private val cache = LyricsCache(context)
    private val tag = "LrcLibApi"
    private val baseUrl = "https://lrclib.net/api"
    private val similarityThreshold = 80
    private val durationToleranceSeconds = 5.0
    private val relaxedDurationToleranceSeconds = 12.0

    suspend fun getLyrics(
        trackName: String,
        artistName: String,
        duration: Long,
        preferredScript: LyricsScriptFilter = LyricsScriptFilter.ALL
    ): Lyrics? = withContext(Dispatchers.IO) {
        val request = LyricsMetadataSanitizer.prepare(trackName, artistName)
        val cacheKey = "${request.cacheKey}|${duration / 1000L}"
        cache.get(cacheKey)?.let { cached ->
            Log.d(tag, "Lyrics cache hit for ${request.cacheKey}")
            if (LyricsScriptDetector.matches(preferredScript, cached)) {
                return@withContext cached
            }
        }

        val durationSeconds = duration / 1000.0
        Log.d(tag, "Lyrics lookup candidates: ${request.candidates.joinToString { "${it.artistName} - ${it.trackName}" }}")
        var fallbackExactMatch: Lyrics? = null

        request.candidates.forEach { candidate ->
            if (candidate.artistName.isBlank()) {
                return@forEach
            }
            fetchExact(candidate, durationSeconds)?.let { lyrics ->
                if (LyricsScriptDetector.matches(preferredScript, lyrics)) {
                    cache.put(cacheKey, lyrics)
                    return@withContext lyrics
                }
                if (fallbackExactMatch == null) {
                    fallbackExactMatch = lyrics
                }
            }
        }

        val bestMatch = searchLyricsOptions(trackName, artistName, duration, preferredScript)
            .firstOrNull()
            ?.lyrics

        if (bestMatch != null) {
            cache.put(cacheKey, bestMatch)
            return@withContext bestMatch
        }

        fallbackExactMatch?.let {
            cache.put(cacheKey, it)
            return@withContext it
        }

        return@withContext null
    }

    suspend fun searchLyricsOptions(
        trackName: String,
        artistName: String,
        duration: Long,
        preferredScript: LyricsScriptFilter = LyricsScriptFilter.ALL
    ): List<LyricsSearchResult> = withContext(Dispatchers.IO) {
        val request = LyricsMetadataSanitizer.prepare(trackName, artistName)
        val durationSeconds = duration / 1000.0
        val collected = linkedMapOf<String, Lyrics>()

        request.candidates.forEach { candidate ->
            if (candidate.artistName.isBlank()) {
                return@forEach
            }
            searchLyrics(trackName = candidate.trackName, artistName = candidate.artistName)
                .forEach { lyrics -> collected.putIfAbsent(resultKey(lyrics), lyrics) }
        }

        request.searchQueries.forEach { query ->
            searchLyrics(query = query)
                .forEach { lyrics -> collected.putIfAbsent(resultKey(lyrics), lyrics) }
        }

        rankResults(
            results = collected.values.toList(),
            titleComparisons = request.titleComparisons,
            artistComparisons = request.artistComparisons,
            durationSeconds = durationSeconds,
            preferredScript = preferredScript
        )
    }

    private fun fetchExact(candidate: LyricsLookupCandidate, durationSeconds: Double): Lyrics? {
        return runCatching {
            val encodedTrack = URLEncoder.encode(candidate.trackName, "UTF-8")
            val encodedArtist = URLEncoder.encode(candidate.artistName, "UTF-8")
            val url = "$baseUrl/get?artist_name=$encodedArtist&track_name=$encodedTrack&duration=$durationSeconds"

            Log.d(tag, "Fetching exact lyrics for ${candidate.artistName} - ${candidate.trackName}")

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "RaaziApp/1.0")
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string()
                if (!response.isSuccessful || body.isNullOrBlank()) {
                    return@runCatching null
                }

                parseLyrics(JSONObject(body))
            }
        }.onFailure {
            Log.e(tag, "Exact lyrics lookup failed for ${candidate.artistName} - ${candidate.trackName}", it)
        }.getOrNull()
    }

    private fun searchLyrics(
        query: String? = null,
        trackName: String? = null,
        artistName: String? = null
    ): List<Lyrics> {
        return runCatching {
            val url = buildString {
                append("$baseUrl/search?")
                val params = buildList {
                    query?.takeIf { it.isNotBlank() }?.let {
                        add("q=${URLEncoder.encode(it, "UTF-8")}")
                    }
                    trackName?.takeIf { it.isNotBlank() }?.let {
                        add("track_name=${URLEncoder.encode(it, "UTF-8")}")
                    }
                    artistName?.takeIf { it.isNotBlank() }?.let {
                        add("artist_name=${URLEncoder.encode(it, "UTF-8")}")
                    }
                }
                append(params.joinToString("&"))
            }

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "RaaziApp/1.0")
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string()
                if (!response.isSuccessful || body.isNullOrBlank()) {
                    return@use emptyList()
                }

                val jsonArray = JSONArray(body)
                List(jsonArray.length()) { index ->
                    parseLyrics(jsonArray.getJSONObject(index))
                }
            }
        }.onFailure {
            Log.e(tag, "Search lyrics failed for query=$query track=$trackName artist=$artistName", it)
        }.getOrDefault(emptyList())
    }

    private fun rankResults(
        results: List<Lyrics>,
        titleComparisons: List<String>,
        artistComparisons: List<String>,
        durationSeconds: Double,
        preferredScript: LyricsScriptFilter
    ): List<LyricsSearchResult> {
        val ranked = results.mapNotNull { lyrics ->
            val durationDelta = abs(lyrics.duration - durationSeconds)
            val titleScore = titleComparisons.maxOfOrNull {
                LyricsMetadataSanitizer.similarityScore(it, lyrics.trackName)
            } ?: 0
            val artistScore = artistComparisons.maxOfOrNull {
                LyricsMetadataSanitizer.similarityScore(it, lyrics.artistName)
            } ?: 0
            val combinedScore = (titleScore * 0.7 + artistScore * 0.3).toInt()
            val durationMatches = lyrics.duration <= 0.0 ||
                durationDelta <= durationToleranceSeconds ||
                (durationDelta <= relaxedDurationToleranceSeconds && combinedScore >= 90)
            if (!durationMatches) {
                return@mapNotNull null
            }

            LyricsSearchResult(
                lyrics = lyrics,
                matchScore = combinedScore,
                titleScore = titleScore,
                artistScore = artistScore,
                durationDelta = durationDelta
            )
        }
            .sortedWith(
                compareByDescending<LyricsSearchResult> {
                    LyricsScriptDetector.matches(preferredScript, it.lyrics)
                }
                    .thenByDescending { it.matchScore }
                    .thenByDescending { it.titleScore }
                    .thenBy { it.durationDelta }
            )
            .filter { it.matchScore >= similarityThreshold }

        ranked.firstOrNull()?.let { best ->
            Log.d(
                tag,
                "Selected lyrics candidate ${best.lyrics.artistName} - ${best.lyrics.trackName} score=${best.matchScore} durationDelta=${best.durationDelta}"
            )
        }
        return ranked
    }

    private fun parseLyrics(json: JSONObject): Lyrics {
        return Lyrics(
            id = json.optInt("id"),
            trackName = json.optString("trackName"),
            artistName = json.optString("artistName"),
            plainLyrics = json.optString("plainLyrics").takeIf { it.isNotBlank() },
            syncedLyrics = json.optString("syncedLyrics").takeIf { it.isNotBlank() },
            duration = json.optDouble("duration"),
            language = json.optString("lang").takeIf { it.isNotBlank() }
                ?: json.optString("language").takeIf { it.isNotBlank() },
            source = "LRCLIB"
        )
    }

    private fun resultKey(lyrics: Lyrics): String {
        return listOf(
            lyrics.id.toString(),
            lyrics.trackName,
            lyrics.artistName,
            lyrics.language.orEmpty()
        ).joinToString("|")
    }
}
