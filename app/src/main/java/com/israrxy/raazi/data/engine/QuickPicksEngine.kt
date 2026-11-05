package com.israrxy.raazi.data.engine

import android.util.Log
import com.israrxy.raazi.data.db.MusicDao
import com.israrxy.raazi.data.remote.YouTubeChartsExtractor
import com.israrxy.raazi.data.repository.MusicRepository
import com.israrxy.raazi.model.MusicItem
import com.israrxy.raazi.service.YouTubeMusicExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Algorithmic Quick Picks Engine for Raazi.
 * Generates dynamic, personalized radio blends (35% favorites, 65% radio discoveries)
 * for active users, and region-based trending charts for fresh installs.
 */
class QuickPicksEngine(
    private val repository: MusicRepository,
    private val musicDao: MusicDao,
    private val musicExtractor: YouTubeMusicExtractor = YouTubeMusicExtractor.getInstance(),
    private val chartsExtractor: YouTubeChartsExtractor = YouTubeChartsExtractor()
) {
    companion object {
        private const val TAG = "QuickPicksEngine"
        private const val TARGET_QUICK_PICKS_COUNT = 24
        private const val RADIO_TIMEOUT_MS = 6000L
    }

    suspend fun generateQuickPicks(
        dismissedItemIds: Set<String> = emptySet(),
        forceRefresh: Boolean = false
    ): List<MusicItem> = withContext(Dispatchers.IO) {
        try {
            val playbackHistory: List<MusicItem> = try { repository.playbackHistory.first() } catch (_: Exception) { emptyList() }
            val topPlayed: List<MusicItem> = try { repository.quickPicks.first() } catch (_: Exception) { emptyList() }
            val favorites: List<MusicItem> = try { repository.favoriteTracks.first() } catch (_: Exception) { emptyList() }

            val candidatePool: List<MusicItem> = (topPlayed + favorites + playbackHistory)
                .filter { it.id.isNotBlank() && it.id !in dismissedItemIds }
                .distinctBy { it.id }

            if (candidatePool.isEmpty()) {
                Log.d(TAG, "No local history/favorites available. Kickstarting with regional trending.")
                return@withContext fetchRegionalQuickPicks(dismissedItemIds)
            }

            // User has history: Build personalized radio blend
            val seeds = selectDiverseSeeds(candidatePool, forceRefresh)
            if (seeds.isEmpty()) {
                return@withContext candidatePool.shuffled().take(TARGET_QUICK_PICKS_COUNT)
            }

            val discoveryTracks = mutableListOf<MusicItem>()
            for (seed in seeds) {
                try {
                    val radioItems = withTimeoutOrNull(RADIO_TIMEOUT_MS) {
                        musicExtractor.getUpNext(seed.id)
                    }.orEmpty()

                    val validRadio = radioItems
                        .filter { it.id.isNotBlank() && it.id != seed.id && it.id !in dismissedItemIds }
                    discoveryTracks.addAll(validRadio)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to get radio for seed ${seed.title}: ${e.message}")
                }
            }

            // If radio discovery completely failed (e.g. offline), fall back to top songs
            if (discoveryTracks.isEmpty()) {
                Log.w(TAG, "All seed radios failed. Falling back to local history shuffle.")
                return@withContext candidatePool.shuffled().take(TARGET_QUICK_PICKS_COUNT)
            }

            // Blend: ~35% familiar favorites + ~65% fresh radio discovery
            val seedIds = seeds.map { it.id }.toSet()
            val familiarPool: MutableList<MusicItem> = candidatePool
                .filter { it.id !in seedIds }
                .let { list -> if (forceRefresh) list.shuffled() else list }
                .toMutableList()

            val candidateIds = candidatePool.map { it.id }.toSet()
            val freshPool: MutableList<MusicItem> = discoveryTracks
                .filter { it.id !in candidateIds }
                .distinctBy { it.id }
                .let { list -> if (forceRefresh) list.shuffled() else list }
                .toMutableList()

            val blended = mutableListOf<MusicItem>()
            val maxIterations = TARGET_QUICK_PICKS_COUNT

            // Interleave pattern: [Discovery, Discovery, Favorite, Discovery, Discovery, Favorite, ...]
            while (blended.size < maxIterations && (freshPool.isNotEmpty() || familiarPool.isNotEmpty())) {
                if (freshPool.isNotEmpty()) {
                    blended.add(freshPool.removeAt(0))
                }
                if (blended.size < maxIterations && freshPool.isNotEmpty()) {
                    blended.add(freshPool.removeAt(0))
                }
                if (blended.size < maxIterations && familiarPool.isNotEmpty()) {
                    blended.add(familiarPool.removeAt(0))
                }
            }

            val result = blended
                .filter { it.id !in dismissedItemIds }
                .distinctBy { it.id }
                .take(TARGET_QUICK_PICKS_COUNT)

            if (result.size >= 8) {
                result
            } else {
                (result + candidatePool)
                    .filter { it.id !in dismissedItemIds }
                    .distinctBy { it.id }
                    .take(TARGET_QUICK_PICKS_COUNT)
            }
        } catch (e: Exception) {
            Log.e(TAG, "QuickPicks generation failed: ${e.message}", e)
            fetchRegionalQuickPicks(dismissedItemIds)
        }
    }

    private fun selectDiverseSeeds(pool: List<MusicItem>, randomize: Boolean): List<MusicItem> {
        val candidates = if (randomize) pool.shuffled() else pool
        val seenArtists = mutableSetOf<String>()
        val seeds = mutableListOf<MusicItem>()

        for (track in candidates) {
            val primaryArtist = track.artist.split(",").firstOrNull()?.trim()?.lowercase() ?: track.artist.lowercase()
            if (primaryArtist.isNotBlank() && primaryArtist !in seenArtists) {
                seenArtists.add(primaryArtist)
                seeds.add(track)
                if (seeds.size >= 4) break
            }
        }

        return if (seeds.isNotEmpty()) seeds else candidates.take(3)
    }

    private suspend fun fetchRegionalQuickPicks(dismissedItemIds: Set<String>): List<MusicItem> {
        return try {
            val charts = withTimeoutOrNull(RADIO_TIMEOUT_MS) {
                chartsExtractor.getCharts()
            }.orEmpty()

            val topSongs = charts.entries.find { it.key.contains("Top songs", ignoreCase = true) }?.value
                ?: charts.values.firstOrNull()

            if (!topSongs.isNullOrEmpty()) {
                topSongs.filter { it.id.isNotBlank() && it.id !in dismissedItemIds }
                    .distinctBy { it.id }
                    .take(TARGET_QUICK_PICKS_COUNT)
            } else {
                val trending = withTimeoutOrNull(RADIO_TIMEOUT_MS) {
                    repository.getTrending()
                }
                trending?.items.orEmpty()
                    .filter { it.id.isNotBlank() && it.id !in dismissedItemIds }
                    .distinctBy { it.id }
                    .take(TARGET_QUICK_PICKS_COUNT)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Regional fallback failed: ${e.message}", e)
            emptyList()
        }
    }
}
