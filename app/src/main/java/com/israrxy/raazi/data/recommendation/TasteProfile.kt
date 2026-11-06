package com.israrxy.raazi.data.recommendation

import com.israrxy.raazi.model.MusicItem
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.exp
import kotlin.math.ln

/**
 * Local-only taste model for signed-out users.
 *
 * Everything is derived from Room (history, favourites, downloads, searches,
 * play/skip feedback) plus the artist picks made in the tastebuilder. Nothing
 * here touches a YouTube account or cookie.
 */
data class TasteArtist(
    val name: String,
    val key: String,
    val score: Double,
    val artistId: String? = null,
    val thumbnailUrl: String? = null,
    val lastPlayedMs: Long = 0L
)

data class TasteAlbum(
    val title: String,
    val artist: String,
    val key: String,
    val score: Double,
    val thumbnailUrl: String? = null,
    val lastPlayedMs: Long = 0L
)

data class TasteTrack(
    val item: MusicItem,
    val score: Double,
    val lastPlayedMs: Long,
    val strongPlays: Int = 0,
    val skips: Int = 0
) {
    val artistKey: String get() = TasteProfile.artistKey(item.artist)
}

data class TasteProfile(
    val tracks: List<TasteTrack> = emptyList(),
    val artists: List<TasteArtist> = emptyList(),
    val albums: List<TasteAlbum> = emptyList(),
    val seedArtists: List<MusicItem> = emptyList(),
    val rediscover: List<MusicItem> = emptyList(),
    val language: String = LANGUAGE_AUTO,
    val totalPlays: Int = 0,
    val dismissedArtistKeys: Set<String> = emptySet(),
    val updatedAt: Long = 0L
) {
    val isColdStart: Boolean get() = totalPlays < MIN_PLAYS

    /** Artist picks matter most until real plays arrive, then fade out. */
    val seedWeight: Double get() = seedArtifactWeight(totalPlays)

    fun isDismissed(artist: String?): Boolean = artistKey(artist) in dismissedArtistKeys

    /** Normalized artist affinity used to re-rank anonymous shelves. */
    fun affinity(limit: Int = 24): Map<String, Double> {
        val scores = LinkedHashMap<String, Double>()
        artists
            .filterNot { it.key in dismissedArtistKeys || it.key.isBlank() }
            .forEach { scores[it.key] = (scores[it.key] ?: 0.0) + it.score }
        seedArtists.forEach { seed ->
            val key = artistKey(seed.title.ifBlank { seed.artist })
            if (key.isNotBlank() && key !in dismissedArtistKeys) {
                scores[key] = (scores[key] ?: 0.0) + seedWeight
            }
        }
        val max = scores.values.maxOrNull() ?: return emptyMap()
        if (max <= 0.0) return emptyMap()
        return scores.entries
            .sortedByDescending { it.value }
            .take(limit)
            .associate { it.key to (it.value / max).coerceIn(0.0, 1.0) }
    }

    fun strongestTrackFor(artistKey: String): TasteTrack? =
        tracks.firstOrNull { it.artistKey == artistKey }

    fun recentTrackIds(sinceMs: Long): Set<String> =
        tracks.filter { it.lastPlayedMs >= sinceMs }.mapTo(mutableSetOf()) { it.item.id }

    companion object {
        const val MIN_PLAYS = 10
        const val HALF_LIFE_DAYS = 21.0
        const val SEED_WEIGHT = 3.2
        const val LANGUAGE_AUTO = "AUTO"
        private const val DAY_MS = 86_400_000.0

        fun recency(nowMs: Long, lastPlayedMs: Long): Double {
            val ageDays = (nowMs - lastPlayedMs).coerceAtLeast(0L) / DAY_MS
            return exp(-ln(2.0) * ageDays / HALF_LIFE_DAYS)
        }

        fun seedArtifactWeight(totalPlays: Int): Double =
            SEED_WEIGHT * (1.0 - totalPlays.toDouble() / (MIN_PLAYS * 2.0)).coerceIn(0.12, 1.0)

        fun artistKey(value: String?): String =
            value.orEmpty()
                .split(',', '&', ';', '/', '|')
                .firstOrNull()
                ?.trim()
                ?.lowercase()
                .orEmpty()
    }
}

/** JSON codec for the persisted artist picks and "not interested" keys. */
object TasteSeedCodec {
    fun encodeSeeds(items: List<MusicItem>): String {
        val array = JSONArray()
        items.distinctBy { it.title.lowercase() }.forEach { item ->
            array.put(
                JSONObject()
                    .put("id", item.id)
                    .put("title", item.title)
                    .put("artist", item.artist)
                    .put("thumbnailUrl", item.thumbnailUrl)
                    .put("artistId", item.artistId)
            )
        }
        return array.toString()
    }

    fun decodeSeeds(raw: String): List<MusicItem> {
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                val json = array.optJSONObject(index) ?: return@mapNotNull null
                val title = json.optString("title").ifBlank { json.optString("artist") }
                if (title.isBlank()) return@mapNotNull null
                val id = json.optString("id").ifBlank { title.lowercase() }
                MusicItem(
                    id = id,
                    title = title,
                    artist = json.optString("artist").ifBlank { title },
                    duration = 0L,
                    thumbnailUrl = json.optString("thumbnailUrl"),
                    audioUrl = "",
                    videoUrl = id,
                    artistId = json.optString("artistId").ifBlank { null },
                    contentType = com.israrxy.raazi.model.MusicContentType.ARTIST
                )
            }
        }.getOrDefault(emptyList())
    }

    fun encodeArtistKeys(keys: Collection<String>): String =
        keys.map { it.trim().lowercase() }.filter { it.isNotBlank() }.distinct().joinToString("\n")

    fun decodeArtistKeys(raw: String): Set<String> =
        raw.split('\n').map { it.trim().lowercase() }.filter { it.isNotBlank() }.toSet()
}
