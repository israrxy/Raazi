package com.israrxy.raazi.data.recommendation

import com.israrxy.raazi.model.MusicItem
import kotlin.random.Random

enum class ShelfLayout { CAROUSEL, LIST_COLUMNS, GRID }

enum class ShelfKind {
    LISTEN_AGAIN,
    ALBUMS_FOR_YOU,
    SIMILAR_TO,
    MY_MIX,
    BECAUSE_YOU_LISTENED,
    FORGOTTEN_FAVORITES,
    TRENDING
}

/**
 * A shelf can hold playable tracks or navigable collections (album/playlist).
 * Keeping the distinction here means the UI can open collections by their real
 * playlist id instead of pretending they are songs.
 */
sealed interface ShelfItem {
    fun key(): String

    data class Track(val item: MusicItem) : ShelfItem {
        override fun key(): String = "track:${item.id}"
    }

    data class Collection(
        val id: String,
        val title: String,
        val subtitle: String,
        val thumbnailUrl: String,
        val kind: String = PlaybackContext.KIND_PLAYLIST
    ) : ShelfItem {
        override fun key(): String = "$kind:$id"
    }
}

data class GeneratedShelf(
    val id: String,
    val title: String,
    val kind: ShelfKind,
    val layout: ShelfLayout = ShelfLayout.CAROUSEL,
    val strapline: String? = null,
    val subtitle: String? = null,
    val avatarUrl: String? = null,
    val moreEndpoint: String? = null,
    val artistName: String? = null,
    val items: List<ShelfItem> = emptyList()
)

interface ShelfGenerator {
    suspend fun generate(profile: TasteProfile, seed: Int): GeneratedShelf?
}

/** Same day + same refresh counter = same shelves; pull-to-refresh changes it. */
fun homeShelfSeed(dayMs: Long, refreshCounter: Int): Int {
    val day = dayMs / DAY_MS
    val mixed = day * 1_000_003L + refreshCounter.toLong() * 7_919L
    return (mixed and 0x7fff_ffffL).toInt()
}

/** Deterministic weighted sampling so shelves are stable within a seed. */
class ShelfSeeds(seed: Int) {
    private val random = Random(seed)

    fun <T> weightedPick(items: List<T>, count: Int, weight: (T) -> Double): List<T> {
        val pool = items.map { it to weight(it).coerceAtLeast(0.0) }.toMutableList()
        val picked = ArrayList<T>(count.coerceAtMost(pool.size))
        repeat(count.coerceAtMost(pool.size)) {
            if (pool.isEmpty()) return@repeat
            val total = pool.sumOf { it.second }
            val index = if (total <= 0.0) {
                random.nextInt(pool.size)
            } else {
                var roll = random.nextDouble() * total
                var selected = pool.lastIndex
                for (i in pool.indices) {
                    roll -= pool[i].second
                    if (roll <= 0.0) {
                        selected = i
                        break
                    }
                }
                selected
            }
            picked += pool.removeAt(index).first
        }
        return picked
    }
}

private const val DAY_MS = 86_400_000L
