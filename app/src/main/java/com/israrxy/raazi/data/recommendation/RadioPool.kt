package com.israrxy.raazi.data.recommendation

import com.israrxy.raazi.data.repository.MusicRepository
import com.israrxy.raazi.model.MusicItem
import com.israrxy.raazi.service.convertYTItemToMusicItem
import com.zionhuang.innertube.YouTube
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Memoized anonymous radio. Every shelf is seeded from a track (RDAMVM radio
 * via the existing extractor), so one seed artist shared by several shelves
 * only costs one round trip.
 */
class RadioPool(
    private val repository: MusicRepository,
    private val timeoutMs: Long = 6_000L
) {
    private val mutex = Mutex()
    private val radioCache = mutableMapOf<String, List<MusicItem>>()
    private val artistCache = mutableMapOf<String, ArtistSeed?>()

    data class ArtistSeed(val seed: MusicItem, val radio: List<MusicItem>)

    suspend fun radioFor(track: MusicItem): List<MusicItem> {
        val key = track.id
        val (hit, cached) = mutex.withLock { radioCache.containsKey(key) to radioCache[key] }
        if (hit) return cached.orEmpty()

        val videoId = track.videoUrl.trim().ifEmpty { track.id }
        val result = withTimeoutOrNull(timeoutMs) {
            runCatching { repository.getRecommendations(videoId) }.getOrDefault(emptyList())
        }.orEmpty()
            .filter { it.id.isNotBlank() && it.id != track.id }
            .distinctBy { it.id }

        mutex.withLock { radioCache[key] = result }
        return result
    }

    /** Cold-start path: find a seed song for the artist, then its radio. */
    suspend fun seedAndRadioForArtist(artist: TasteArtist): ArtistSeed? {
        val key = artist.key
        val (hit, cached) = mutex.withLock { artistCache.containsKey(key) to artistCache[key] }
        if (hit) return cached

        val search = withTimeoutOrNull(timeoutMs) {
            runCatching {
                YouTube.search(artist.name, YouTube.SearchFilter.FILTER_SONG).getOrNull()
            }.getOrNull()
        }
        val mapped = search?.items.orEmpty().mapNotNull { convertYTItemToMusicItem(it) }
        val seed = mapped.firstOrNull { TasteProfile.artistKey(it.artist) == key }
            ?: mapped.firstOrNull()

        val result = seed?.let { ArtistSeed(it, radioFor(it)) }
        mutex.withLock { artistCache[key] = result }
        return result
    }
}
