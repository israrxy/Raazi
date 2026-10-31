package com.israrxy.raazi.service

import com.israrxy.raazi.model.MusicContentType
import com.israrxy.raazi.model.MusicItem
import com.zionhuang.innertube.models.YTItem
import com.zionhuang.innertube.pages.SearchSummaryPage
import kotlin.math.log10
import kotlin.math.min

private val QUERY_SYNONYMS: Map<String, List<String>> = mapOf(
    "jaane" to listOf("jaane", "jane", "janey"),
    "jane" to listOf("jaane", "jane", "janey"),
    "janey" to listOf("jaane", "jane", "janey"),
    "shey" to listOf("shey", "se", "she"),
    "se" to listOf("shey", "se", "she"),
    "valobashi" to listOf("valobashi", "bhalobashi", "bhalobasi", "valobasi", "bhalo bashi", "valo bashi"),
    "bhalobashi" to listOf("valobashi", "bhalobashi", "bhalobasi", "valobasi", "bhalo bashi", "valo bashi"),
    "bhalobasi" to listOf("valobashi", "bhalobashi", "bhalobasi", "valobasi", "bhalo bashi", "valo bashi"),
    "valobasi" to listOf("valobashi", "bhalobashi", "bhalobasi", "valobasi", "bhalo bashi", "valo bashi"),
    "sohokari" to listOf("sohokari", "shohokari"),
    "shohokari" to listOf("sohokari", "shohokari")
)

private val SPECIAL_INTENT_KEYWORDS = listOf(
    "cover", "live", "remix", "lofi", "lo-fi", "acoustic",
    "unplugged", "slowed", "reverb", "instrumental", "karaoke", "video", "lyrics"
)

private val VIDEO_INTENT_KEYWORDS = listOf(
    "video", "mv", "cover", "live", "lyrics", "lyric", "karaoke", "visualizer"
)

private val PLAYLIST_INTENT_KEYWORDS = listOf(
    "playlist", "playlists", "mix", "collection", "soundtrack", "ost",
    "compilation", "greatest hits", "best of", "top hits", "radio", "anthology"
)

private val ALBUM_INTENT_KEYWORDS = listOf("album", "ep", "lp", "discography")

private val ARTIST_INTENT_KEYWORDS = listOf("artist", "singer", "band", "musician")

internal fun parseViewsOrPlaysCount(viewsText: String?): Long {
    if (viewsText.isNullOrBlank()) return 0L
    val match = Regex("([\\d.]+)\\s*([KMBkmb])?").find(viewsText) ?: return 0L
    val number = match.groupValues[1].toDoubleOrNull() ?: return 0L
    val multiplier = when (match.groupValues.getOrNull(2)?.uppercase()) {
        "K" -> 1_000.0
        "M" -> 1_000_000.0
        "B" -> 1_000_000_000.0
        else -> 1.0
    }
    return (number * multiplier).toLong()
}

private fun tokenMatchesHaystack(token: String, haystack: String): Boolean {
    val variants = QUERY_SYNONYMS[token] ?: listOf(token)
    return variants.any { haystack.contains(it) }
}

internal fun scoreSearchCandidate(
    query: String,
    item: MusicItem,
    isCardMain: Boolean,
    index: Int
): Double {
    val qNorm = query.trim().lowercase()
    val tokens = qNorm.split(Regex("\\s+")).filter { it.length >= 2 }
    val titleLower = item.title.lowercase()
    val artistLower = item.artist.lowercase()
    val combined = "$titleLower $artistLower"

    val matchedTokens = tokens.count { tokenMatchesHaystack(it, combined) }
    val matchRatio = if (tokens.isNotEmpty()) matchedTokens.toDouble() / tokens.size.toDouble() else 0.0

    var score = matchRatio * 200.0
    if (tokens.isNotEmpty() && matchedTokens == tokens.size) {
        score += 80.0
    }

    if (titleLower == qNorm) {
        score += 110.0
    } else if (titleLower.startsWith(qNorm) && qNorm.length >= 3) {
        score += 65.0
    }

    // Dynamic Intent Detection
    val wantsPlaylist = PLAYLIST_INTENT_KEYWORDS.any { qNorm.contains(it) }
    val wantsAlbum = ALBUM_INTENT_KEYWORDS.any { qNorm.contains(it) }
    val wantsArtist = ARTIST_INTENT_KEYWORDS.any { qNorm.contains(it) }
    val wantsVideo = VIDEO_INTENT_KEYWORDS.any { qNorm.contains(it) }

    for (word in SPECIAL_INTENT_KEYWORDS) {
        if (qNorm.contains(word)) {
            if (combined.contains(word)) {
                score += 120.0
            } else {
                score -= 80.0
            }
        } else if (word != "video" && titleLower.contains(word)) {
            score -= 40.0
        }
    }

    val count = parseViewsOrPlaysCount(item.viewsText)
    if (count > 0L) {
        score += min(90.0, log10(count.toDouble() + 1.0) * 10.0)
    }

    // Dynamic Type-Specific Weighting based on User Intent
    when {
        item.contentType == MusicContentType.PLAYLIST || item.isPlaylist -> {
            if (wantsPlaylist) {
                score += 180.0
                if (titleLower.contains("playlist") || titleLower.contains("mix")) {
                    score += 40.0
                }
            } else if (matchRatio >= 0.75) {
                score += 60.0
            }
        }
        item.contentType == MusicContentType.ALBUM -> {
            if (wantsAlbum) {
                score += 180.0
            } else if (matchRatio >= 0.75) {
                score += 50.0
            }
        }
        item.contentType == MusicContentType.ARTIST -> {
            if (wantsArtist) {
                score += 180.0
            } else if (matchRatio >= 0.85) {
                score += 55.0
            }
        }
        item.contentType == MusicContentType.VIDEO -> {
            if (wantsVideo) {
                score += 70.0
            } else if (wantsPlaylist || wantsAlbum) {
                score -= 80.0
            }
        }
        item.contentType == MusicContentType.SONG -> {
            if (wantsPlaylist || wantsAlbum) {
                score -= 80.0
            } else if (!wantsVideo && matchRatio >= 0.5) {
                score += 65.0
            }
        }
        else -> Unit
    }

    if (isCardMain && matchRatio >= 0.45) {
        score += 35.0
    }

    score -= index * 3.0
    return score
}

/**
 * Dynamically selects the best top result across all content types (Playlists, Albums, Artists, Songs, Videos)
 * without hardcoding, accurately respecting query intent and token match.
 */
internal fun selectDynamicTopResult(
    query: String,
    cardMainItem: MusicItem?,
    unfilteredTracks: List<MusicItem> = emptyList(),
    moreFromYouTube: List<MusicItem> = emptyList(),
    typedSongs: List<MusicItem> = emptyList(),
    typedPlaylists: List<MusicItem> = emptyList(),
    typedAlbums: List<MusicItem> = emptyList(),
    typedArtists: List<MusicItem> = emptyList(),
    summaryAllItems: List<MusicItem> = emptyList()
): MusicItem? {
    val qNorm = query.trim().lowercase()
    val wantsPlaylist = PLAYLIST_INTENT_KEYWORDS.any { qNorm.contains(it) }
    val wantsAlbum = ALBUM_INTENT_KEYWORDS.any { qNorm.contains(it) }
    val wantsArtist = ARTIST_INTENT_KEYWORDS.any { qNorm.contains(it) }

    // If cardMainItem matches explicit high-level intent with high confidence, prefer it
    if (cardMainItem != null) {
        val matchesExplicitIntent = (wantsPlaylist && (cardMainItem.contentType == MusicContentType.PLAYLIST || cardMainItem.isPlaylist)) ||
            (wantsAlbum && cardMainItem.contentType == MusicContentType.ALBUM) ||
            (wantsArtist && cardMainItem.contentType == MusicContentType.ARTIST)

        if (matchesExplicitIntent) {
            return cardMainItem
        }
    }

    // Pool candidates across all available sources and types
    val summaryPlaylists = summaryAllItems.filter { it.contentType == MusicContentType.PLAYLIST || it.isPlaylist }
    val summaryAlbums = summaryAllItems.filter { it.contentType == MusicContentType.ALBUM }
    val summaryArtists = summaryAllItems.filter { it.contentType == MusicContentType.ARTIST }

    val candidates = (
        listOfNotNull(cardMainItem) +
            typedPlaylists.take(6) +
            summaryPlaylists.take(6) +
            typedAlbums.take(4) +
            summaryAlbums.take(4) +
            typedArtists.take(4) +
            summaryArtists.take(4) +
            unfilteredTracks.take(8) +
            moreFromYouTube.take(4) +
            typedSongs.take(4)
        ).distinctBy { "${it.contentType}:${it.id}" }

    if (candidates.isEmpty()) return cardMainItem

    return candidates.withIndex().maxByOrNull { (index, candidate) ->
        scoreSearchCandidate(
            query = query,
            item = candidate,
            isCardMain = candidate.id == cardMainItem?.id,
            index = index
        )
    }?.value ?: cardMainItem
}

/**
 * Builds YouTube Music-accurate entity suggestions for the search dropdown.
 * Prioritizes direct entity recommendations while promoting correct types (such as Playlists
 * when searching for playlists) dynamically without hardcoding.
 */
internal fun buildDynamicSuggestionItems(
    query: String,
    queries: List<String> = emptyList(),
    summaryPage: SearchSummaryPage?,
    recommendedItems: List<YTItem>
): List<MusicItem> {
    val qNorm = query.trim().lowercase()
    val effectiveQuery = queries.firstOrNull()?.takeIf { it.isNotBlank() } ?: query
    val wantsVideo = VIDEO_INTENT_KEYWORDS.any { qNorm.contains(it) }

    val directSuggestItems = recommendedItems.mapNotNull { convertYTItemToMusicItem(it) }
    val cardSection = summaryPage?.summaries?.firstOrNull { it.mainItem != null }
    val cardMain = cardSection?.mainItem?.let { convertYTItemToMusicItem(it) }
    val moreItems = cardSection?.moreItems.orEmpty().mapNotNull { convertYTItemToMusicItem(it) }
    val summaryItems = summaryPage?.summaries.orEmpty()
        .filter { it.mainItem == null }
        .flatMap { section -> section.items.mapNotNull { convertYTItemToMusicItem(it) } }

    val summaryPlaylists = summaryItems.filter { it.contentType == MusicContentType.PLAYLIST || it.isPlaylist }
    val summarySongs = summaryItems.filter { it.contentType == MusicContentType.SONG }

    val prioritizedDirect = if (directSuggestItems.isNotEmpty()) {
        val firstDirect = directSuggestItems.first()
        if (!wantsVideo && firstDirect.contentType == MusicContentType.VIDEO) {
            val matchingSong = (directSuggestItems + summaryItems + listOfNotNull(cardMain))
                .firstOrNull { candidate ->
                    candidate.contentType == MusicContentType.SONG &&
                        candidate.title.equals(firstDirect.title, ignoreCase = true)
                }
            if (matchingSong != null) {
                listOf(matchingSong) + directSuggestItems.filterNot { it.id == matchingSong.id }
            } else {
                directSuggestItems
            }
        } else {
            directSuggestItems
        }
    } else {
        emptyList()
    }

    val topDynamic = if (prioritizedDirect.isNotEmpty()) {
        prioritizedDirect.first()
    } else {
        selectDynamicTopResult(
            query = effectiveQuery,
            cardMainItem = cardMain,
            unfilteredTracks = summaryItems,
            moreFromYouTube = moreItems,
            typedSongs = summarySongs,
            typedPlaylists = summaryPlaylists,
            summaryAllItems = summaryItems
        )
    }

    val supplementaryPool = (
        listOfNotNull(cardMain) +
            summaryItems +
            moreItems
        ).distinctBy { "${it.contentType}:${it.id}" }
        .withIndex()
        .sortedByDescending { (idx, item) ->
            maxOf(
                scoreSearchCandidate(effectiveQuery, item, item.id == cardMain?.id, idx),
                scoreSearchCandidate(query, item, item.id == cardMain?.id, idx)
            )
        }
        .map { it.value }

    return (
        listOfNotNull(topDynamic) +
            prioritizedDirect +
            supplementaryPool
        )
        .distinctBy { "${it.contentType}:${it.id}" }
        .take(8)
}
