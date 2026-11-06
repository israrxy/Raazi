package com.israrxy.raazi.data.recommendation

import com.israrxy.raazi.model.MusicContentType
import com.israrxy.raazi.model.MusicItem
import com.zionhuang.innertube.YouTube
import com.zionhuang.innertube.models.AlbumItem
import kotlinx.coroutines.withTimeoutOrNull

private const val NETWORK_TIMEOUT_MS = 6_000L
private const val DAY_MS = 86_400_000L
private const val FRESH_WINDOW_MS = DAY_MS
private const val MAX_TRACKS_PER_SHELF = 12

/**
 * The YT-Music-shaped generators. Each one is independent: if it fails or
 * returns too little, the engine simply omits that shelf.
 */

/** Songs, albums and playlists the user was recently playing. */
class ListenAgainShelfGenerator(
    private val contextStore: PlaybackContextStore
) : ShelfGenerator {
    override suspend fun generate(profile: TasteProfile, seed: Int): GeneratedShelf? {
        val contexts = runCatching { contextStore.recent(18) }.getOrDefault(emptyList())
        val items = mutableListOf<ShelfItem>()
        val seen = mutableSetOf<String>()

        for (context in contexts) {
            if (context.kind == PlaybackContext.KIND_SONG && profile.isDismissed(context.subtitle)) continue
            if (!seen.add("${context.kind}:${context.id}")) continue
            when (context.kind) {
                PlaybackContext.KIND_SONG -> items += ShelfItem.Track(
                    MusicItem(
                        id = context.id,
                        title = context.title,
                        artist = context.subtitle,
                        duration = 0L,
                        thumbnailUrl = context.thumbnailUrl,
                        audioUrl = "",
                        videoUrl = context.id,
                        contentType = MusicContentType.SONG
                    )
                )
                PlaybackContext.KIND_PLAYLIST, PlaybackContext.KIND_ALBUM -> {
                    if (context.id.isBlank()) continue
                    items += ShelfItem.Collection(
                        id = context.id,
                        title = context.title,
                        subtitle = context.subtitle,
                        thumbnailUrl = context.thumbnailUrl,
                        kind = context.kind
                    )
                }
            }
            if (items.size >= MAX_TRACKS_PER_SHELF) break
        }

        if (items.size < 3) return null
        return GeneratedShelf(
            id = "listen_again",
            title = "Listen again",
            kind = ShelfKind.LISTEN_AGAIN,
            layout = ShelfLayout.LIST_COLUMNS,
            subtitle = "Songs, albums and playlists",
            items = items
        )
    }
}

/** Albums from the artist pages of the top (or picked) artists. */
class AlbumsForYouShelfGenerator : ShelfGenerator {
    override suspend fun generate(profile: TasteProfile, seed: Int): GeneratedShelf? {
        val artists = ShelfSeeds(seed * 31 + 5)
            .weightedPick(profile.artists.take(12), 3) { it.score }
        if (artists.isEmpty()) return null

        val items = mutableListOf<ShelfItem>()
        val seen = mutableSetOf<String>()
        for (artist in artists) {
            val albums = withTimeoutOrNull(NETWORK_TIMEOUT_MS) {
                runCatching {
                    YouTube.search("${artist.name} album", YouTube.SearchFilter.FILTER_ALBUM).getOrNull()
                }.getOrNull()
            }?.items.orEmpty().filterIsInstance<AlbumItem>()

            albums.forEach { album ->
                if (items.size >= MAX_TRACKS_PER_SHELF) return@forEach
                if (album.playlistId.isBlank() || album.title.isBlank()) return@forEach
                if (!seen.add(album.playlistId)) return@forEach
                items += ShelfItem.Collection(
                    id = album.playlistId,
                    title = album.title,
                    subtitle = album.artists
                        ?.joinToString(", ") { it.name }
                        .orEmpty()
                        .ifBlank { artist.name },
                    thumbnailUrl = album.thumbnail,
                    kind = PlaybackContext.KIND_ALBUM
                )
            }
        }

        if (items.size < 4) return null
        return GeneratedShelf(
            id = "albums_for_you",
            title = "Albums for you",
            kind = ShelfKind.ALBUMS_FOR_YOU,
            subtitle = "From artists you play",
            items = items
        )
    }
}

/** "Similar to <artist>" from the radio of one weighted-random top artist. */
class SimilarArtistShelfGenerator(
    private val radioPool: RadioPool,
    private val index: Int
) : ShelfGenerator {
    override suspend fun generate(profile: TasteProfile, seed: Int): GeneratedShelf? {
        val artists = profile.artists.filterNot { profile.isDismissed(it.name) }
        if (artists.isEmpty()) return null

        val artist = ShelfSeeds(seed * 53 + index * 977 + 17)
            .weightedPick(artists.take(8), 1) { it.score }
            .firstOrNull() ?: return null

        val track = ShelfSeeds(seed * 53 + index * 977 + 19)
            .weightedPick(
                profile.tracks.filter { it.artistKey == artist.key }.take(5),
                1
            ) { it.score }
            .firstOrNull()

        val radio = track?.let { radioPool.radioFor(it.item) }
            ?: radioPool.seedAndRadioForArtist(artist)?.radio.orEmpty()
        val items = freshTracks(profile, radio, MAX_TRACKS_PER_SHELF, skipRecent = true)
        if (items.size < 5) return null

        return GeneratedShelf(
            id = "similar_$index",
            title = artist.name,
            kind = ShelfKind.SIMILAR_TO,
            strapline = "Similar to",
            avatarUrl = artist.thumbnailUrl ?: items.firstOrNull()?.thumbnailUrl,
            moreEndpoint = artist.artistId,
            artistName = artist.name,
            items = items.map { ShelfItem.Track(it) }
        )
    }
}

/** "My Mix N": familiar tracks from 2-3 artists blended with radio discovery. */
class MixShelfGenerator(
    private val radioPool: RadioPool,
    private val index: Int
) : ShelfGenerator {
    override suspend fun generate(profile: TasteProfile, seed: Int): GeneratedShelf? {
        val artists = ShelfSeeds(seed * 97 + index * 131 + 23)
            .weightedPick(profile.artists.filterNot { profile.isDismissed(it.name) }.take(10), 3) { it.score }
        if (artists.isEmpty()) return null

        val keys = artists.mapTo(mutableSetOf()) { it.key }
        val familiar = profile.tracks
            .filter { it.artistKey in keys }
            .sortedByDescending { it.score }
            .take(6)
            .map { it.item }

        val radio = mutableListOf<MusicItem>()
        artists.take(2).forEach { artist ->
            val track = profile.strongestTrackFor(artist.key)
            val related = track?.let { radioPool.radioFor(it.item) }
                ?: radioPool.seedAndRadioForArtist(artist)?.radio
            radio += related.orEmpty()
        }
        val discovery = freshTracks(profile, radio, MAX_TRACKS_PER_SHELF, skipRecent = false)
        val items = interleave(familiar, discovery, familiarRun = 2, discoveryRun = 1, limit = 16)
        if (items.size < 8) return null

        return GeneratedShelf(
            id = "mix_${index + 1}",
            title = "My Mix ${index + 1}",
            kind = ShelfKind.MY_MIX,
            layout = ShelfLayout.GRID,
            subtitle = artists.joinToString(", ") { it.name },
            artistName = artists.first().name,
            items = items.map { ShelfItem.Track(it) }
        )
    }
}

/** Radio from a recent strong play. */
class BecauseYouListenedShelfGenerator(
    private val radioPool: RadioPool
) : ShelfGenerator {
    override suspend fun generate(profile: TasteProfile, seed: Int): GeneratedShelf? {
        val now = System.currentTimeMillis()
        val track = profile.tracks
            .filter { it.strongPlays > 0 && now - it.lastPlayedMs <= 14 * DAY_MS }
            .maxByOrNull { it.lastPlayedMs }
            ?: profile.tracks.maxByOrNull { it.lastPlayedMs }
            ?: return null

        val items = freshTracks(
            profile,
            radioPool.radioFor(track.item),
            MAX_TRACKS_PER_SHELF,
            skipRecent = true
        )
        if (items.size < 5) return null

        return GeneratedShelf(
            id = "because_${track.item.id}",
            title = track.item.title,
            kind = ShelfKind.BECAUSE_YOU_LISTENED,
            strapline = "Because you listened to",
            avatarUrl = track.item.thumbnailUrl,
            artistName = track.item.artist,
            items = items.map { ShelfItem.Track(it) }
        )
    }
}

/** Existing forgotten-favourites source, reshaped as a shelf. */
class ForgottenFavoritesShelfGenerator : ShelfGenerator {
    override suspend fun generate(profile: TasteProfile, seed: Int): GeneratedShelf? {
        val items = profile.rediscover
            .filterNot { profile.isDismissed(it.artist) }
            .distinctBy { it.id }
            .take(MAX_TRACKS_PER_SHELF)
        if (items.size < 5) return null

        return GeneratedShelf(
            id = "forgotten_favorites",
            title = "Rediscover",
            kind = ShelfKind.FORGOTTEN_FAVORITES,
            subtitle = "Favourites you have not played in a while",
            items = items.map { ShelfItem.Track(it) }
        )
    }
}

private fun freshTracks(
    profile: TasteProfile,
    candidates: List<MusicItem>,
    limit: Int,
    skipRecent: Boolean
): List<MusicItem> {
    val recentIds = if (skipRecent) {
        profile.recentTrackIds(System.currentTimeMillis() - FRESH_WINDOW_MS)
    } else {
        emptySet()
    }
    return candidates.asSequence()
        .filter { it.id.isNotBlank() }
        .filterNot { profile.isDismissed(it.artist) }
        .filterNot { it.id in recentIds }
        .distinctBy { it.id }
        .take(limit)
        .toList()
}

private fun interleave(
    familiar: List<MusicItem>,
    discovery: List<MusicItem>,
    familiarRun: Int,
    discoveryRun: Int,
    limit: Int
): List<MusicItem> {
    val result = mutableListOf<MusicItem>()
    var familiarIndex = 0
    var discoveryIndex = 0
    while (result.size < limit && (familiarIndex < familiar.size || discoveryIndex < discovery.size)) {
        repeat(familiarRun) {
            if (result.size < limit && familiarIndex < familiar.size) {
                result += familiar[familiarIndex++]
            }
        }
        repeat(discoveryRun) {
            if (result.size < limit && discoveryIndex < discovery.size) {
                result += discovery[discoveryIndex++]
            }
        }
    }
    return result.distinctBy { it.id }
}
