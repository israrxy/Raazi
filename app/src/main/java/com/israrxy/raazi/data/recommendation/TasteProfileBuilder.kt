package com.israrxy.raazi.data.recommendation

import android.util.Log
import com.israrxy.raazi.data.db.HomeInteractionEntity
import com.israrxy.raazi.data.db.MusicDao
import com.israrxy.raazi.data.local.SettingsDataStore
import com.israrxy.raazi.data.repository.MusicRepository
import com.israrxy.raazi.data.repository.mapHistoryToDomain
import com.israrxy.raazi.data.repository.mapTrackToDomain
import com.israrxy.raazi.model.MusicItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

private const val TAG = "TasteProfile"
private const val DAY_MS = 86_400_000L
private const val SIGNAL_WINDOW_DAYS = 60L

/**
 * Builds the signed-out taste profile from Room only.
 *
 * Scores time-decay with a 21-day half-life. Plays are the base signal;
 * completion/strong plays lift a track, skips demote it, and favourites,
 * downloads and searches add a smaller boost. Artist picks from the
 * tastebuilder get a strong initial weight that fades as real plays arrive.
 */
class TasteProfileBuilder(
    private val repository: MusicRepository,
    private val musicDao: MusicDao,
    private val settingsDataStore: SettingsDataStore
) {
    suspend fun build(nowMs: Long = System.currentTimeMillis()): TasteProfile =
        withContext(Dispatchers.IO) {
            val resetAt = runCatching { settingsDataStore.tasteResetAt.first() }.getOrDefault(0L)
            val seeds = TasteSeedCodec.decodeSeeds(
                runCatching { settingsDataStore.tasteSeedArtists.first() }.getOrDefault("")
            )
            val dismissed = TasteSeedCodec.decodeArtistKeys(
                runCatching { settingsDataStore.tasteDismissedArtists.first() }.getOrDefault("")
            )
            val language = runCatching { settingsDataStore.tasteLanguage.first() }
                .getOrDefault(TasteProfile.LANGUAGE_AUTO)
            val dismissedItems = runCatching { repository.getDismissedHomeItemIds() }
                .getOrDefault(emptySet())

            val history = runCatching { musicDao.getAllPlaybackHistoryDirect() }
                .getOrDefault(emptyList())
                .filter { it.id.isNotBlank() && it.timestamp >= resetAt }
            val favorites = runCatching { musicDao.getAllTracks().first() }
                .getOrDefault(emptyList())
                .filter { it.timestamp >= resetAt }
            val downloads = runCatching { musicDao.getDownloadedTracks().first() }
                .getOrDefault(emptyList())
            val searches = runCatching { musicDao.searchHistory("").first() }
                .getOrDefault(emptyList())
            val signals = runCatching {
                musicDao.getHomeSignalCounts(
                    actions = listOf(
                        HomeInteractionEntity.ACTION_COMPLETE,
                        HomeInteractionEntity.ACTION_SKIP
                    ),
                    since = maxOf(resetAt, nowMs - SIGNAL_WINDOW_DAYS * DAY_MS)
                )
            }.getOrDefault(emptyList())

            val completions = signals
                .filter { it.action == HomeInteractionEntity.ACTION_COMPLETE }
                .associate { it.itemId to it.count }
            val skips = signals
                .filter { it.action == HomeInteractionEntity.ACTION_SKIP }
                .associate { it.itemId to it.count }
            val favoriteIds = favorites.mapTo(mutableSetOf()) { it.id }
            val downloadIds = downloads.mapTo(mutableSetOf()) { it.id }

            val trackInputs = LinkedHashMap<String, Triple<MusicItem, Int, Long>>()
            history.forEach { entity ->
                if (entity.id in dismissedItems) return@forEach
                val item = runCatching { mapHistoryToDomain(entity) }.getOrNull() ?: return@forEach
                trackInputs[entity.id] = Triple(item, entity.playCount, entity.timestamp)
            }
            favorites.forEach { entity ->
                if (entity.id in dismissedItems) return@forEach
                if (trackInputs.containsKey(entity.id)) return@forEach
                val item = runCatching { mapTrackToDomain(entity) }.getOrNull() ?: return@forEach
                trackInputs[entity.id] = Triple(item, 1, entity.timestamp)
            }

            val tracks = trackInputs.values.mapNotNull { (item, playCount, lastPlayed) ->
                if (TasteProfile.artistKey(item.artist) in dismissed) return@mapNotNull null
                val recency = TasteProfile.recency(nowMs, lastPlayed)
                val strong = completions[item.id] ?: 0
                val skipCount = skips[item.id] ?: 0
                val base = playCount.coerceIn(1, 30) * recency
                val score = base *
                    (1.0 + minOf(strong, 6) * 0.12) /
                    (1.0 + skipCount * 0.35) *
                    (if (item.id in favoriteIds) 1.6 else 1.0) *
                    (if (item.id in downloadIds) 1.25 else 1.0)
                TasteTrack(
                    item = item,
                    score = score,
                    lastPlayedMs = lastPlayed,
                    strongPlays = strong,
                    skips = skipCount
                )
            }.sortedByDescending { it.score }

            val albums = tracks
                .mapNotNull { track ->
                    val title = track.item.albumTitle?.takeIf { it.isNotBlank() }
                        ?: return@mapNotNull null
                    val artist = primaryArtist(track.item.artist)
                    TasteAlbum(
                        title = title,
                        artist = artist,
                        key = TasteProfile.artistKey(artist) + ":" + title.lowercase(),
                        score = track.score,
                        thumbnailUrl = track.item.thumbnailUrl,
                        lastPlayedMs = track.lastPlayedMs
                    )
                }
                .groupBy { it.key }
                .map { (_, group) ->
                    val first = group.first()
                    first.copy(
                        score = group.sumOf { it.score },
                        lastPlayedMs = group.maxOf { it.lastPlayedMs }
                    )
                }
                .sortedByDescending { it.score }
                .take(20)

            val artistMap = LinkedHashMap<String, TasteArtist>()
            tracks.forEach { track ->
                val key = track.artistKey
                if (key.isBlank()) return@forEach
                val existing = artistMap[key]
                artistMap[key] = if (existing == null) {
                    TasteArtist(
                        name = primaryArtist(track.item.artist),
                        key = key,
                        score = track.score,
                        artistId = track.item.artistId,
                        thumbnailUrl = track.item.thumbnailUrl,
                        lastPlayedMs = track.lastPlayedMs
                    )
                } else {
                    existing.copy(
                        score = existing.score + track.score,
                        artistId = existing.artistId ?: track.item.artistId,
                        thumbnailUrl = existing.thumbnailUrl ?: track.item.thumbnailUrl,
                        lastPlayedMs = maxOf(existing.lastPlayedMs, track.lastPlayedMs)
                    )
                }
            }

            val searchTokens = searches
                .flatMap { listOf(it.query, it.resultArtist.orEmpty(), it.resultTitle.orEmpty()) }
                .map { it.lowercase() }
                .filter { it.isNotBlank() }
            if (searchTokens.isNotEmpty()) {
                artistMap.keys.toList().forEach { key ->
                    val artist = artistMap[key] ?: return@forEach
                    if (searchTokens.any { it.contains(key) }) {
                        artistMap[key] = artist.copy(score = artist.score * 1.25)
                    }
                }
            }

            val seedBoost = TasteProfile.seedArtifactWeight(history.sumOf { it.playCount })
            seeds.forEach { seed ->
                val name = seed.title.ifBlank { seed.artist }
                val key = TasteProfile.artistKey(name)
                if (key.isBlank() || key in dismissed) return@forEach
                val existing = artistMap[key]
                artistMap[key] = if (existing == null) {
                    TasteArtist(
                        name = name,
                        key = key,
                        score = seedBoost,
                        artistId = seed.artistId ?: seed.id,
                        thumbnailUrl = seed.thumbnailUrl.ifBlank { null },
                        lastPlayedMs = 0L
                    )
                } else {
                    existing.copy(
                        score = existing.score + seedBoost,
                        thumbnailUrl = existing.thumbnailUrl ?: seed.thumbnailUrl.ifBlank { null }
                    )
                }
            }

            val rediscover = runCatching { repository.getForgottenFavorites().first() }
                .getOrDefault(emptyList())
                .filterNot { TasteProfile.artistKey(it.artist) in dismissed }

            Log.d(
                TAG,
                "profile: ${tracks.size} tracks, ${artistMap.size} artists, " +
                    "${seeds.size} picks, ${history.sumOf { it.playCount }} plays"
            )

            TasteProfile(
                tracks = tracks,
                artists = artistMap.values.sortedByDescending { it.score }.take(30),
                albums = albums,
                seedArtists = seeds,
                rediscover = rediscover,
                language = language,
                totalPlays = history.sumOf { it.playCount },
                dismissedArtistKeys = dismissed,
                updatedAt = nowMs
            )
        }

    private fun primaryArtist(value: String): String =
        value.split(',', '&', ';').firstOrNull()?.trim().orEmpty()
}
