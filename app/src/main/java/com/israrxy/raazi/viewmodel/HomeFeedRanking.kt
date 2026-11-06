package com.israrxy.raazi.viewmodel

import com.israrxy.raazi.model.MusicItem
import com.zionhuang.innertube.models.AlbumItem
import com.zionhuang.innertube.models.ArtistItem
import com.zionhuang.innertube.models.PlaylistItem
import com.zionhuang.innertube.models.SongItem
import com.zionhuang.innertube.models.YTItem

/**
 * Two-tier dedupe + anonymous-shelf personalization.
 *
 * Familiar content (history, favourites, mixes) dedupes against itself.
 * Discovery content (charts, community playlists, anonymous shelves) dedupes
 * only against dismissals and itself, so a chart song can still appear in a
 * chart rail even when Quick Picks also used it. Every source keeps a floor so
 * personalization can never make an anonymous shelf disappear.
 */
internal class HomeFeedRanking(
    private val dismissedIds: Set<String>,
    private val artistAffinity: Map<String, Double>,
    private val maxAppearances: Int = 2
) {
    private val familiarSeen = mutableSetOf<String>()
    private val discoverySeen = mutableSetOf<String>()
    private val appearances = mutableMapOf<String, Int>()

    fun familiarMusic(items: List<MusicItem>, limit: Int): List<MusicItem> =
        selectMusic(items, limit, familiarSeen, checkFamiliar = true)

    fun discoveryMusic(items: List<MusicItem>, limit: Int): List<MusicItem> =
        selectMusic(items, limit, discoverySeen, checkFamiliar = false)

    fun discoveryYouTube(
        items: List<YTItem>,
        limit: Int,
        idOf: (YTItem) -> String
    ): List<YTItem> {
        val result = mutableListOf<YTItem>()
        for (item in items) {
            if (result.size >= limit) break
            val id = idOf(item)
            if (!canShow(id, discoverySeen, checkFamiliar = false)) continue
            mark(id, discoverySeen)
            result += item
        }
        return result
    }

    /**
     * Personalized anonymous shelf: re-rank by affinity, dedupe, then backfill
     * from the original shelf order until [floor] items are available.
     */
    fun personalizedShelf(
        items: List<YTItem>,
        limit: Int,
        floor: Int,
        idOf: (YTItem) -> String
    ): List<YTItem> {
        if (items.isEmpty()) return emptyList()
        val ranked = rankYouTube(items)
        val picked = discoveryYouTube(ranked, limit, idOf)
        if (picked.size >= floor) return picked

        val result = picked.toMutableList()
        val pickedIds = result.mapTo(mutableSetOf()) { idOf(it) }
        val fallbackFloor = minOf(floor, limit, items.size)
        for (item in items) {
            if (result.size >= fallbackFloor) break
            val id = idOf(item)
            if (id.isBlank() || id in dismissedIds || id in pickedIds) continue
            if ((appearances[id] ?: 0) >= maxAppearances) continue
            pickedIds += id
            discoverySeen += id
            appearances[id] = (appearances[id] ?: 0) + 1
            result += item
        }
        return result
    }

    fun rankYouTube(items: List<YTItem>): List<YTItem> =
        items.sortedByDescending { affinityOf(it) }

    fun rankMusic(items: List<MusicItem>): List<MusicItem> =
        items.sortedByDescending { affinityOf(it) }

    fun affinityOf(item: YTItem): Double {
        val artists = when (item) {
            is SongItem -> item.artists.map { it.name }
            is AlbumItem -> item.artists?.mapNotNull { it.name }.orEmpty()
            is ArtistItem -> listOf(item.title)
            is PlaylistItem -> listOfNotNull(item.author?.name, item.title)
            else -> emptyList()
        }
        return artists
            .map { artistAffinity[normalizeArtist(it)] ?: 0.0 }
            .maxOrNull()
            ?: 0.0
    }

    fun affinityOf(item: MusicItem): Double =
        artistAffinity[normalizeArtist(item.artist)] ?: 0.0

    private fun selectMusic(
        items: List<MusicItem>,
        limit: Int,
        seen: MutableSet<String>,
        checkFamiliar: Boolean
    ): List<MusicItem> {
        val result = mutableListOf<MusicItem>()
        for (item in items) {
            if (result.size >= limit) break
            if (!canShow(item.id, seen, checkFamiliar)) continue
            mark(item.id, seen)
            result += item
        }
        return result
    }

    private fun canShow(id: String, seen: Set<String>, checkFamiliar: Boolean): Boolean {
        if (id.isBlank() || id in dismissedIds) return false
        if (id in seen) return false
        if (checkFamiliar && id in familiarSeen) return false
        if ((appearances[id] ?: 0) >= maxAppearances) return false
        return true
    }

    private fun mark(id: String, seen: MutableSet<String>) {
        seen += id
        appearances[id] = (appearances[id] ?: 0) + 1
    }

    private fun normalizeArtist(value: String?): String =
        value.orEmpty()
            .split(',', '&', ';')
            .firstOrNull()
            ?.trim()
            ?.lowercase()
            .orEmpty()
}
