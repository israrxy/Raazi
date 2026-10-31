package com.israrxy.raazi.data.remote

import android.util.Log
import com.israrxy.raazi.data.lyrics.LyricsLookupCandidate
import com.zionhuang.innertube.YouTube
import com.zionhuang.innertube.models.SongItem
import com.zionhuang.innertube.models.WatchEndpoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class LyricaAndYtLyricsExtractor {
    private val client = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(4, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val tag = "LyricaYtExtractor"

    suspend fun fetchLyricaCandidates(
        candidates: List<LyricsLookupCandidate>,
        durationSeconds: Double
    ): List<Lyrics> = withContext(Dispatchers.IO) {
        val found = LinkedHashMap<String, Lyrics>()
        for (candidate in candidates.take(2)) {
            if (candidate.trackName.isBlank()) continue
            fetchFromLyrist(candidate, durationSeconds)?.let {
                found.putIfAbsent("${it.artistName}|${it.trackName}", it)
            }
            if (found.isEmpty() && candidate.artistName.isNotBlank()) {
                fetchFromLyricsOvh(candidate, durationSeconds)?.let {
                    found.putIfAbsent("${it.artistName}|${it.trackName}", it)
                }
            }
            if (found.isNotEmpty()) break
        }
        found.values.toList()
    }

    suspend fun fetchYouTubeMusicCandidates(
        rawTrackId: String?,
        trackName: String,
        artistName: String,
        candidates: List<LyricsLookupCandidate>,
        durationSeconds: Double
    ): List<Lyrics> = withContext(Dispatchers.IO) {
        val cleanId = extractYouTubeVideoId(rawTrackId)
        val targetVideoIds = LinkedHashSet<Pair<String, LyricsLookupCandidate>>()

        val primaryCandidate = candidates.firstOrNull()
            ?: LyricsLookupCandidate(trackName = trackName, artistName = artistName)

        if (!cleanId.isNullOrBlank()) {
            targetVideoIds.add(cleanId to primaryCandidate)
        }

        if (targetVideoIds.isEmpty()) {
            val query = "${primaryCandidate.artistName} ${primaryCandidate.trackName}".trim()
            if (query.isNotBlank()) {
                runCatching {
                    YouTube.search(query, YouTube.SearchFilter.FILTER_SONG).getOrNull()
                        ?.items
                        ?.filterIsInstance<SongItem>()
                        ?.take(2)
                        ?.forEach { song ->
                            targetVideoIds.add(
                                song.id to LyricsLookupCandidate(
                                    trackName = song.title,
                                    artistName = song.artists.firstOrNull()?.name ?: primaryCandidate.artistName
                                )
                            )
                        }
                }
            }
        }

        val results = mutableListOf<Lyrics>()
        for ((videoId, meta) in targetVideoIds.take(2)) {
            supervisorScope {
                val browseDeferred = async {
                    runCatching {
                        val nextResult = YouTube.next(WatchEndpoint(videoId = videoId)).getOrNull()
                        val lyricsEndpoint = nextResult?.lyricsEndpoint ?: return@runCatching null
                        YouTube.lyrics(lyricsEndpoint).getOrNull()
                            ?.trim()
                            ?.takeIf { it.isNotBlank() && it.length > 20 }
                    }.getOrNull()
                }

                val transcriptDeferred = async {
                    runCatching {
                        YouTube.transcript(videoId).getOrNull()
                            ?.trim()
                            ?.takeIf { isValidLrcTranscript(it) }
                    }.getOrNull()
                }

                val plainText = browseDeferred.await()
                val syncedTranscript = transcriptDeferred.await()

                if (!syncedTranscript.isNullOrBlank() || !plainText.isNullOrBlank()) {
                    val plainFallback = plainText ?: syncedTranscript?.let(::stripLrcTags)
                    results += Lyrics(
                        id = videoId.hashCode(),
                        trackName = meta.trackName.ifBlank { trackName },
                        artistName = meta.artistName.ifBlank { artistName },
                        plainLyrics = plainFallback,
                        syncedLyrics = syncedTranscript,
                        duration = durationSeconds,
                        language = null,
                        source = "YT Music"
                    )
                }
            }
        }
        results
    }

    private fun fetchFromLyrist(
        candidate: LyricsLookupCandidate,
        durationSeconds: Double
    ): Lyrics? {
        val encodedTitle = encodePathSegment(candidate.trackName)
        val encodedArtist = encodePathSegment(candidate.artistName)
        val urls = buildList {
            if (encodedArtist.isNotBlank()) {
                add("https://lyrist.vercel.app/api/$encodedTitle/$encodedArtist")
            }
            add("https://lyrist.vercel.app/api/$encodedTitle")
        }

        for (url in urls) {
            val lyrics = runCatching {
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "RaaziApp/6.5.0")
                    .header("Accept", "application/json")
                    .build()

                client.newCall(request).execute().use { response ->
                    val body = response.body?.string() ?: return@use null
                    if (!response.isSuccessful || body.isBlank()) return@use null
                    val json = JSONObject(body)
                    val text = json.optString("lyrics").trim()
                    if (text.isBlank() || text.length < 25) return@use null
                    val title = json.optString("title").ifBlank { candidate.trackName }
                    val artist = json.optString("artist").ifBlank { candidate.artistName }
                    val hasLrcTags = text.contains(Regex("""\[\d+:\d+(?:\.\d+)?\]"""))
                    Lyrics(
                        id = "$artist|$title|Lyrica".hashCode(),
                        trackName = title,
                        artistName = artist,
                        plainLyrics = if (hasLrcTags) stripLrcTags(text) else text,
                        syncedLyrics = if (hasLrcTags) text else null,
                        duration = durationSeconds,
                        language = null,
                        source = "Lyrica"
                    )
                }
            }.getOrNull()

            if (lyrics != null) return lyrics
        }
        return null
    }

    private fun fetchFromLyricsOvh(
        candidate: LyricsLookupCandidate,
        durationSeconds: Double
    ): Lyrics? {
        if (candidate.artistName.isBlank() || candidate.trackName.isBlank()) return null
        return runCatching {
            val encodedArtist = encodePathSegment(candidate.artistName)
            val encodedTitle = encodePathSegment(candidate.trackName)
            val url = "https://api.lyrics.ovh/v1/$encodedArtist/$encodedTitle"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "RaaziApp/6.5.0")
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: return@use null
                if (!response.isSuccessful || body.isBlank()) return@use null
                val json = JSONObject(body)
                val rawLyrics = json.optString("lyrics")
                    .replace("\r\n", "\n")
                    .replace(Regex("""^Paroles de la chanson.*?\n""", RegexOption.IGNORE_CASE), "")
                    .trim()
                if (rawLyrics.isBlank() || rawLyrics.length < 25) return@use null

                Lyrics(
                    id = "${candidate.artistName}|${candidate.trackName}|LyricaOvh".hashCode(),
                    trackName = candidate.trackName,
                    artistName = candidate.artistName,
                    plainLyrics = rawLyrics,
                    syncedLyrics = null,
                    duration = durationSeconds,
                    language = null,
                    source = "Lyrica"
                )
            }
        }.onFailure {
            runCatching { Log.d(tag, "Lyrics.ovh lookup skipped: ${it.message}") }
        }.getOrNull()
    }

    private fun extractYouTubeVideoId(rawId: String?): String? {
        if (rawId.isNullOrBlank()) return null
        val stripped = rawId
            .removePrefix("youtube_")
            .substringAfter("v=", rawId.removePrefix("youtube_"))
            .substringBefore("&")
            .trim()
        return stripped.takeIf { it.length in 10..12 && !it.contains(" ") }
    }

    private fun isValidLrcTranscript(lrc: String): Boolean {
        val lines = lrc.lines().filter { it.isNotBlank() }
        if (lines.size < 4) return false
        val timestampedCount = lines.count { it.startsWith("[") && it.contains("]") }
        return timestampedCount >= 4
    }

    private fun stripLrcTags(lrc: String): String {
        val regex = Regex("""\[\d+:\d+(?:\.\d+)?\]|<\d+:\d+(?:\.\d+)?>""")
        return lrc.lineSequence()
            .map { it.replace(regex, "").trim() }
            .filter { it.isNotBlank() }
            .joinToString("\n")
    }

    private fun encodePathSegment(segment: String): String {
        return URLEncoder.encode(segment.trim(), "UTF-8").replace("+", "%20")
    }
}
