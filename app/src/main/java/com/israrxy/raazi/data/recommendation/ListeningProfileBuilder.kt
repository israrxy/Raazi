package com.israrxy.raazi.data.recommendation

import android.util.Log
import com.israrxy.raazi.data.db.HomeInteractionEntity
import com.israrxy.raazi.data.db.MusicDao
import com.israrxy.raazi.data.repository.MusicRepository
import com.israrxy.raazi.data.repository.mapHistoryToDomain
import com.israrxy.raazi.data.repository.mapTrackToDomain
import com.israrxy.raazi.model.MusicItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

private const val TAG = "ListeningProfile"
private const val DAY_MS = 86_400_000L
private const val SIGNAL_WINDOW_DAYS = 60L
private const val RELATED_SEED_LIMIT = 8
private const val MIN_RELATED_SEEDS = 3
private const val RELATED_WARMUP_LIMIT = 3
private const val RELATED_WARMUP_TIMEOUT_MS = 6000L

/**
 * Builds the local listening profile used by the home feed.
 *
 * The whole point of this class is that no YouTube account is required: every
 * signal comes from Room (playback history, favourites, cached related tracks,
 * play/skip feedback and home dismissals).
 */
class ListeningProfileBuilder(
    private val repository: MusicRepository,
    private val musicDao: MusicDao
) {
    suspend fun build(nowMs: Long = System.currentTimeMillis()): ListeningProfile =
        withContext(Dispatchers.IO) {
            val dismissed = runCatching { repository.getDismissedHomeItemIds() }
                .getOrElse { emptySet() }

            val history = runCatching { musicDao.getAllPlaybackHistoryDirect() }
                .getOrElse { emptyList() }

            val signals = runCatching {
                musicDao.getHomeSignalCounts(
                    actions = listOf(
                        HomeInteractionEntity.ACTION_COMPLETE,
                        HomeInteractionEntity.ACTION_SKIP
                    ),
                    since = nowMs - SIGNAL_WINDOW_DAYS * DAY_MS
                )
            }.getOrElse { emptyList() }

            val completions = signals
                .filter { it.action == HomeInteractionEntity.ACTION_COMPLETE }
                .associate { it.itemId to it.count }
            val skips = signals
                .filter { it.action == HomeInteractionEntity.ACTION_SKIP }
                .associate { it.itemId to it.count }

            val profileTracks = history
                .asSequence()
                .filter { it.id.isNotBlank() && it.id !in dismissed }
                .map { entity ->
                    ProfileTrack(
                        item = runCatching { mapHistoryToDomain(entity) }.getOrNull()
                            ?: MusicItem(
                                id = entity.id,
                                title = entity.title,
                                artist = entity.artist,
                                duration = entity.duration,
                                thumbnailUrl = entity.thumbnailUrl,
                                audioUrl = entity.audioUrl,
                                videoUrl = entity.videoUrl
                            ),
                        playCount = entity.playCount,
                        lastPlayedMs = entity.timestamp,
                        completions = completions[entity.id] ?: 0,
                        skips = skips[entity.id] ?: 0
                    )
                }
                .toList()

            val favorites = runCatching { repository.favoriteTracks.first() }
                .getOrElse { emptyList() }
                .filter { it.id.isNotBlank() && it.id !in dismissed }

            val rediscover = runCatching { repository.getForgottenFavorites().first() }
                .getOrElse { emptyList() }
                .filter { it.id !in dismissed }

            ListeningProfile(
                tracks = profileTracks,
                favorites = favorites,
                rediscover = rediscover,
                relatedByTrack = loadRelatedTracks(profileTracks, nowMs),
                dismissedIds = dismissed
            )
        }

    private suspend fun loadRelatedTracks(
        tracks: List<ProfileTrack>,
        nowMs: Long
    ): Map<String, List<MusicItem>> {
        val seeds = tracks
            .sortedByDescending { it.score(nowMs) }
            .take(RELATED_SEED_LIMIT)

        val result = mutableMapOf<String, List<MusicItem>>()
        for (seed in seeds) {
            val related = relatedFor(seed.item)
            if (related.isNotEmpty()) {
                result[seed.item.id] = related
            }
        }

        // Cold-ish libraries (favourites but no cached related graph yet) benefit
        // from warming a few edges, which keeps the feed personal on day one.
        if (result.size < MIN_RELATED_SEEDS) {
            for (seed in seeds.take(RELATED_WARMUP_LIMIT)) {
                if (result.containsKey(seed.item.id)) continue
                runCatching {
                    withTimeoutOrNull(RELATED_WARMUP_TIMEOUT_MS) {
                        repository.fetchAndSaveRelated(seed.item)
                    }
                }
                val related = relatedFor(seed.item)
                if (related.isNotEmpty()) {
                    result[seed.item.id] = related
                }
            }
        }

        Log.d(TAG, "Loaded related tracks for ${result.size}/${seeds.size} seeds")
        return result
    }

    private suspend fun relatedFor(item: MusicItem): List<MusicItem> = runCatching {
        musicDao.getRelatedTracksDirect(item.id)
            .mapNotNull { entity -> runCatching { mapTrackToDomain(entity) }.getOrNull() }
            .filter { it.id.isNotBlank() && it.id != item.id }
            .distinctBy { it.id }
    }.getOrElse { emptyList() }
}
