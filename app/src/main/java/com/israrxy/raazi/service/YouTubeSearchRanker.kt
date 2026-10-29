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
    "video", "mv", "cover", "live", "lyrics", "lyric", "karaoke"
)

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
        score += 95.0
    } else if (titleLower.startsWith(qNorm) && qNorm.length >= 3) {
        score += 55.0
    }

    val wantsVideoOrSpecial = VIDEO_INTENT_KEYWORDS.any { qNorm.contains(it) }
    for (word in SPECIAL_INTENT_KEYWORDS) {
        if (qNorm.contains(word)) {
            if (combined.contains(word)) {
                score += 120.0
            } else {
                score -= 90.0
            }
        } else if (word != "video" && titleLower.contains(word)) {
            score -= 45.0
        }
    }

    val count = parseViewsOrPlaysCount(item.viewsText)
    if (count > 0L) {
        score += min(90.0, log10(count.toDouble() + 1.0) * 10.0)
    }

    when (item.contentType) {
        MusicContentType.SONG -> {
            if (!wantsVideoOrSpecial && matchRatio >= 0.5) {
                score += 65.0
            }
        }
        MusicContentType.VIDEO -> {
            if (wantsVideoOrSpecial && matchRatio >= 0.5) {
                score += 55.0
            }
        }
        else -> Unit
    }

    if (isCardMain && matchRatio >= 0.5) {
        score += 35.0
    }

    score -= index * 3.0
    return score
}

internal fun selectDynamicTopResult(
    query: String,
    cardMainItem: MusicItem?,
    unfilteredTracks: List<MusicItem>,
    moreFromYouTube: List<MusicItem>,
    typedSongs: List<MusicItem>
): MusicItem? {
    if (cardMainItem != null &&
        (cardMainItem.contentType == MusicContentType.ARTIST ||
            cardMainItem.contentType == MusicContentType.ALBUM ||
            cardMainItem.contentType == MusicContentType.PLAYLIST)
    ) {
        return cardMainItem
    }

    val candidates = (
        listOfNotNull(cardMainItem) +
            unfilteredTracks.take(10) +
            moreFromYouTube.take(3) +
            typedSongs.take(3)
        ).distinctBy { it.id }

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
 * Prioritizes YT Music's native `recommendedItems` from `music/get_search_suggestions`
 * while promoting official audio `SONG` over `VIDEO` when the user didn't request a video,
 * and supplements with top matches from `searchSummary` scored against YT Music's top predicted query.
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

    // If YT Music's native autocomplete returned direct entity items, preserve their priority
    // while ensuring that if the #1 item is a Video and an official Song version of the same track
    // exists in summaryItems/directSuggestItems, we surface the Song first (just like YT Music).
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
            typedSongs = summaryItems.filter { it.contentType == MusicContentType.SONG }
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
