package com.israrxy.raazi.data.recommendation

import com.israrxy.raazi.model.MusicItem
import kotlin.math.exp
import kotlin.math.min

private const val DAY_MS = 86_400_000.0
private const val RECENCY_HALF_LIFE_DAYS = 14.0

/**
 * A single playable track together with the local signals attached to it.
 */
data class ProfileTrack(
    val item: MusicItem,
    val playCount: Int,
    val lastPlayedMs: Long,
    val completions: Int = 0,
    val skips: Int = 0
) {
    fun score(nowMs: Long): Double {
        val ageDays = (nowMs - lastPlayedMs).coerceAtLeast(0L) / DAY_MS
        val recency = exp(-ageDays / RECENCY_HALF_LIFE_DAYS)
        val completionBoost = 1.0 + min(completions, 6) * 0.06
        val skipPenalty = 1.0 / (1.0 + skips * 0.30)
        return playCount.coerceIn(1, 25) * recency * completionBoost * skipPenalty
    }

    val primaryArtist: String
        get() = item.artist.split(',', '&', ';').firstOrNull()?.trim().orEmpty()

    val artistKey: String
        get() = primaryArtist.lowercase()
}

data class ArtistCluster(
    val artist: String,
    val tracks: List<ProfileTrack>
)

/**
 * Local listening profile used to build the home feed without a YouTube account.
 *
 * Everything here comes from Room: playback history, favourites, cached related
 * tracks and play/skip feedback. No network or account is required.
 */
data class ListeningProfile(
    val tracks: List<ProfileTrack>,
    val favorites: List<MusicItem>,
    val rediscover: List<MusicItem>,
    val relatedByTrack: Map<String, List<MusicItem>>,
    val dismissedIds: Set<String>
) {
    val hasHistory: Boolean
        get() = tracks.isNotEmpty() || favorites.isNotEmpty()

    val knownIds: Set<String>
        get() = buildSet {
            tracks.forEach { add(it.item.id) }
            favorites.forEach { add(it.id) }
        }

    fun rankedTracks(nowMs: Long = System.currentTimeMillis()): List<ProfileTrack> =
        tracks
            .filter { it.item.id !in dismissedIds }
            .sortedByDescending { it.score(nowMs) }

    fun topArtists(limit: Int, nowMs: Long = System.currentTimeMillis()): List<ArtistCluster> =
        tracks
            .filter { it.item.id !in dismissedIds && it.artistKey.isNotBlank() }
            .groupBy { it.artistKey }
            .map { (_, artistTracks) ->
                val ranked = artistTracks.sortedByDescending { it.score(nowMs) }
                ArtistCluster(
                    artist = ranked.first().primaryArtist,
                    tracks = ranked
                )
            }
            .sortedByDescending { cluster ->
                cluster.tracks.sumOf { it.score(nowMs) }
            }
            .take(limit)

    /**
     * Normalized artist affinity map used to re-rank anonymous shelves
     * (community playlists, charts, YouTube browse shelves).
     */
    fun artistScores(limit: Int = 20, nowMs: Long = System.currentTimeMillis()): Map<String, Double> {
        val scores = topArtists(limit, nowMs)
            .associate { cluster ->
                cluster.artist.lowercase() to cluster.tracks.sumOf { it.score(nowMs) }
            }
            .toMutableMap()
        favorites.forEach { item ->
            val key = item.artist.split(',', '&', ';').firstOrNull()?.trim()?.lowercase().orEmpty()
            if (key.isNotBlank() && key !in scores) scores[key] = 0.5
        }
        return scores
    }
}
