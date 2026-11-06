package com.israrxy.raazi.data.recommendation

import android.util.Log
import com.israrxy.raazi.data.repository.MusicRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.withTimeoutOrNull

private const val TAG = "HomeShelves"
private const val GENERATOR_TIMEOUT_MS = 6_000L

/**
 * Runs every shelf generator in parallel with a 6s timeout and emits shelves
 * progressively in their fixed YT-Music order. Failing generators are dropped.
 */
class HomeShelfEngine(
    private val repository: MusicRepository,
    private val contextStore: PlaybackContextStore,
    private val cache: HomeShelfCache
) {
    private val radioPool = RadioPool(repository)

    fun generators(): List<ShelfGenerator> = buildList {
        add(ListenAgainShelfGenerator(contextStore))
        add(AlbumsForYouShelfGenerator())
        repeat(3) { add(SimilarArtistShelfGenerator(radioPool, it)) }
        repeat(5) { add(MixShelfGenerator(radioPool, it)) }
        add(BecauseYouListenedShelfGenerator(radioPool))
        add(ForgottenFavoritesShelfGenerator())
    }

    suspend fun cached(seed: Int): List<GeneratedShelf> =
        runCatching { cache.load(seed)?.shelves.orEmpty() }.getOrDefault(emptyList())

    fun stream(profile: TasteProfile, seed: Int): Flow<GeneratedShelf> = channelFlow {
        val deferred = generators().map { generator ->
            async {
                val started = System.currentTimeMillis()
                val shelf = withTimeoutOrNull(GENERATOR_TIMEOUT_MS) {
                    runCatching { generator.generate(profile, seed) }.getOrNull()
                }
                Log.d(
                    TAG,
                    "${generator.javaClass.simpleName}: ${shelf?.items?.size ?: 0} items " +
                        "in ${System.currentTimeMillis() - started}ms"
                )
                shelf
            }
        }

        val seen = mutableSetOf<String>()
        for (deferredShelf in deferred) {
            val shelf = deferredShelf.await() ?: continue
            val items = shelf.items.filterNot { it.key() in seen }
            if (items.size < shelf.minimumItems()) continue
            seen += items.map { it.key() }
            send(shelf.copy(items = items))
        }
    }

    suspend fun save(seed: Int, shelves: List<GeneratedShelf>) {
        runCatching { cache.save(seed, shelves) }
    }
}

private fun GeneratedShelf.minimumItems(): Int = when (kind) {
    ShelfKind.LISTEN_AGAIN -> 3
    ShelfKind.ALBUMS_FOR_YOU -> 4
    ShelfKind.MY_MIX -> 6
    ShelfKind.FORGOTTEN_FAVORITES -> 4
    else -> 5
}
