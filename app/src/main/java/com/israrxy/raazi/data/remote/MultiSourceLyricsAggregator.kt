package com.israrxy.raazi.data.remote

import android.content.Context
import android.util.Log
import com.israrxy.raazi.data.lyrics.LyricsCache
import com.israrxy.raazi.data.lyrics.LyricsMetadataSanitizer
import com.israrxy.raazi.data.lyrics.LyricsScriptDetector
import com.israrxy.raazi.data.lyrics.LyricsScriptFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs

enum class LyricsProviderFilter(val label: String, val sourceKey: String?) {
    ALL("All Sources", null),
    MUSIXMATCH("Musixmatch", "Musixmatch"),
    LRCLIB("LRCLIB", "LRCLIB"),
    GENIUS("Genius", "Genius"),
    LYRICA("Lyrica", "Lyrica"),
    YT_MUSIC("YT Music", "YT Music");

    fun matches(source: String): Boolean {
        val target = sourceKey ?: return true
        return source.contains(target, ignoreCase = true)
    }
}

class MultiSourceLyricsAggregator(
    context: Context,
    private val lrcLibApi: LrcLibApi = LrcLibApi(context)
) {
    private val cache = LyricsCache(context)
    private val musixmatchExtractor = MusixmatchLyricsExtractor(context)
    private val geniusExtractor = GeniusLyricsExtractor()
    private val lyricaAndYtExtractor = LyricaAndYtLyricsExtractor()
    private val tag = "LyricsAggregator"

    suspend fun getLyrics(
        trackId: String?,
        trackName: String,
        artistName: String,
        durationMs: Long,
        preferredScript: LyricsScriptFilter = LyricsScriptFilter.ALL
    ): Lyrics? = withContext(Dispatchers.IO) {
        val prepared = LyricsMetadataSanitizer.prepare(trackName, artistName)
        val cacheKey = "${prepared.cacheKey}|${durationMs / 1000L}|v2"

        cache.get(cacheKey)?.let { cached ->
            if (LyricsScriptDetector.matches(preferredScript, cached)) {
                Log.d(tag, "Multi-source cache hit (${cached.source}) for ${prepared.cacheKey}")
                return@withContext cached
            }
        }

        val ranked = searchAllProviders(
            trackId = trackId,
            trackName = trackName,
            artistName = artistName,
            durationMs = durationMs,
            preferredScript = preferredScript
        )

        val best = ranked.firstOrNull()?.lyrics
        if (best != null) {
            Log.d(tag, "Selected best lyrics from ${best.source} for $artistName - $trackName")
            cache.put(cacheKey, best)
        }
        best
    }

    suspend fun searchAllProviders(
        trackId: String?,
        trackName: String,
        artistName: String,
        durationMs: Long,
        preferredScript: LyricsScriptFilter = LyricsScriptFilter.ALL
    ): List<LyricsSearchResult> = withContext(Dispatchers.IO) {
        val prepared = LyricsMetadataSanitizer.prepare(trackName, artistName)
        val durationSeconds = (durationMs / 1000.0).coerceAtLeast(0.0)

        val rawCandidates = supervisorScope {
            val mxmDeferred = async {
                withTimeoutOrNull(PROVIDER_TIMEOUT_MS) {
                    runCatching {
                        musixmatchExtractor.fetchBestForCandidates(prepared.candidates, durationSeconds)
                    }.getOrDefault(emptyList())
                } ?: emptyList()
            }

            val lrcLibDeferred = async {
                withTimeoutOrNull(PROVIDER_TIMEOUT_MS) {
                    runCatching {
                        val list = mutableListOf<Lyrics>()
                        lrcLibApi.getLyrics(trackName, artistName, durationMs, preferredScript)?.let(list::add)
                        lrcLibApi.searchLyricsOptions(trackName, artistName, durationMs, preferredScript)
                            .map { it.lyrics }
                            .forEach(list::add)
                        list
                    }.getOrDefault(emptyList())
                } ?: emptyList()
            }

            val geniusDeferred = async {
                withTimeoutOrNull(PROVIDER_TIMEOUT_MS) {
                    runCatching {
                        geniusExtractor.fetchBestForCandidates(
                            candidates = prepared.candidates,
                            queries = prepared.searchQueries,
                            durationSeconds = durationSeconds
                        )
                    }.getOrDefault(emptyList())
                } ?: emptyList()
            }

            val lyricaDeferred = async {
                withTimeoutOrNull(PROVIDER_TIMEOUT_MS) {
                    runCatching {
                        lyricaAndYtExtractor.fetchLyricaCandidates(prepared.candidates, durationSeconds)
                    }.getOrDefault(emptyList())
                } ?: emptyList()
            }

            val ytMusicDeferred = async {
                withTimeoutOrNull(PROVIDER_TIMEOUT_MS) {
                    runCatching {
                        lyricaAndYtExtractor.fetchYouTubeMusicCandidates(
                            rawTrackId = trackId,
                            trackName = trackName,
                            artistName = artistName,
                            candidates = prepared.candidates,
                            durationSeconds = durationSeconds
                        )
                    }.getOrDefault(emptyList())
                } ?: emptyList()
            }

            buildList {
                addAll(mxmDeferred.await())
                addAll(lrcLibDeferred.await())
                addAll(geniusDeferred.await())
                addAll(lyricaDeferred.await())
                addAll(ytMusicDeferred.await())
            }
        }

        val deduped = LinkedHashMap<String, Lyrics>()
        rawCandidates.forEach { candidate ->
            if (candidate.syncedLyrics.isNullOrBlank() && candidate.plainLyrics.isNullOrBlank()) {
                return@forEach
            }
            val syncKind = when {
                hasWordSync(candidate.syncedLyrics) -> "WORD"
                !candidate.syncedLyrics.isNullOrBlank() -> "LINE"
                else -> "PLAIN"
            }
            val key = "${candidate.source.lowercase()}|$syncKind|${candidate.artistName.lowercase().trim()}|${candidate.trackName.lowercase().trim()}"
            deduped.putIfAbsent(key, candidate)
        }

        rankCandidates(
            candidates = deduped.values.toList(),
            titleComparisons = prepared.titleComparisons,
            artistComparisons = prepared.artistComparisons,
            durationSeconds = durationSeconds,
            preferredScript = preferredScript
        )
    }

    private fun rankCandidates(
        candidates: List<Lyrics>,
        titleComparisons: List<String>,
        artistComparisons: List<String>,
        durationSeconds: Double,
        preferredScript: LyricsScriptFilter
    ): List<LyricsSearchResult> {
        return candidates.mapNotNull { lyrics ->
            val titleScore = titleComparisons.maxOfOrNull {
                LyricsMetadataSanitizer.similarityScore(it, lyrics.trackName)
            } ?: 75

            val artistScore = if (artistComparisons.isEmpty() || lyrics.artistName.isBlank()) {
                72
            } else {
                artistComparisons.maxOfOrNull {
                    LyricsMetadataSanitizer.similarityScore(it, lyrics.artistName)
                } ?: 65
            }

            val baseScore = (titleScore * 0.72 + artistScore * 0.28).toInt()
            if (baseScore < MIN_METADATA_SCORE && lyrics.source != "YT Music") {
                return@mapNotNull null
            }

            val isWordSynced = hasWordSync(lyrics.syncedLyrics)
            val isLineSynced = !lyrics.syncedLyrics.isNullOrBlank()
            val syncBonus = when {
                isWordSynced -> 16
                isLineSynced -> 11
                else -> 0
            }

            val durationDelta = if (durationSeconds > 0 && lyrics.duration > 0) {
                abs(lyrics.duration - durationSeconds)
            } else {
                0.0
            }

            val durationAdjustment = when {
                durationSeconds <= 0 || lyrics.duration <= 0 -> 0
                durationDelta <= 4.0 -> 5
                durationDelta <= 12.0 -> 2
                durationDelta > 28.0 && isLineSynced -> -12
                else -> 0
            }

            val providerReliabilityBonus = when (lyrics.source) {
                "Musixmatch" -> 4
                "LRCLIB" -> 3
                "YT Music" -> 3
                "Genius" -> 2
                "Lyrica" -> 1
                else -> 0
            }

            val finalScore = (baseScore + syncBonus + durationAdjustment + providerReliabilityBonus)
                .coerceIn(0, 100)

            LyricsSearchResult(
                lyrics = lyrics,
                matchScore = finalScore,
                titleScore = titleScore,
                artistScore = artistScore,
                durationDelta = durationDelta
            )
        }.sortedWith(
            compareByDescending<LyricsSearchResult> {
                LyricsScriptDetector.matches(preferredScript, it.lyrics)
            }
                .thenByDescending { it.matchScore }
                .thenByDescending { hasWordSync(it.lyrics.syncedLyrics) }
                .thenByDescending { !it.lyrics.syncedLyrics.isNullOrBlank() }
                .thenByDescending { it.titleScore }
                .thenBy { it.durationDelta }
        )
    }

    companion object {
        private const val PROVIDER_TIMEOUT_MS = 3200L
        private const val MIN_METADATA_SCORE = 52
        private val WORD_TAG_REGEX = Regex("""<\d+:\d+(?:\.\d+)?>""")

        fun hasWordSync(syncedLyrics: String?): Boolean {
            if (syncedLyrics.isNullOrBlank()) return false
            return WORD_TAG_REGEX.containsMatchIn(syncedLyrics)
        }
    }
}
