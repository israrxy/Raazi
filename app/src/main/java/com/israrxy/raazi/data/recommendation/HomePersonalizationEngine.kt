package com.israrxy.raazi.data.recommendation

import com.israrxy.raazi.model.MusicItem

/** A home rail produced purely from local signals (plus optional anonymous charts). */
data class PersonalizedSection(
    val id: String,
    val title: String,
    val kind: PersonalizedSectionKind,
    val subtitle: String? = null,
    val items: List<MusicItem> = emptyList()
)

enum class PersonalizedSectionKind {
    MIX,
    BECAUSE_YOU_LISTENED,
    TRENDING
}

/**
 * Builds YouTube-Music-style rails from a [ListeningProfile] without requiring
 * a YouTube login. The output is provider-neutral so the ViewModel can map it
 * onto the existing [com.israrxy.raazi.viewmodel.HomeSection] model.
 */
object HomePersonalizationEngine {
    private const val MAX_MIXES = 3
    private const val MAX_BECAUSE = 2
    private const val MIX_SIZE = 16
    private const val FAMILIAR_PER_MIX = 5
    private const val MAX_PER_ARTIST = 2
    private const val MAX_PER_SEED_ARTIST = 9
    private const val TRENDING_SIZE = 16

    fun build(
        profile: ListeningProfile,
        charts: Map<String, List<MusicItem>> = emptyMap(),
        nowMs: Long = System.currentTimeMillis()
    ): List<PersonalizedSection> {
        val sections = mutableListOf<PersonalizedSection>()
        val used = mutableSetOf<String>()
        val chartTracksByArtist = charts.values
            .flatten()
            .filter { it.id.isNotBlank() && it.id !in profile.dismissedIds }
            .distinctBy { it.id }
            .groupBy { artistKeyOf(it.artist) }

        val clusters = profile.topArtists(MAX_MIXES, nowMs)
        clusters.forEachIndexed { index, cluster ->
            val items = buildMix(cluster, profile, used, nowMs, chartTracksByArtist)
            if (items.size >= 5) {
                sections += PersonalizedSection(
                    id = "my_mix_${index}_${cluster.artist.hashCode()}",
                    title = "My Mix · ${cluster.artist}",
                    kind = PersonalizedSectionKind.MIX,
                    subtitle = "Familiar favourites + fresh discoveries",
                    items = items
                )
            }
        }

        profile.rankedTracks(nowMs)
            .filter { profile.relatedByTrack[it.item.id].orEmpty().isNotEmpty() }
            .take(MAX_BECAUSE)
            .forEach { seed ->
                val items = profile.relatedByTrack[seed.item.id].orEmpty()
                    .filter { it.id !in used && it.id !in profile.dismissedIds }
                    .distinctBy { it.id }
                    .take(MIX_SIZE)
                if (items.size >= 5) {
                    used += items.map { it.id }
                    sections += PersonalizedSection(
                        id = "because_${seed.item.id.hashCode()}",
                        title = "Because you listened to ${seed.item.title}",
                        kind = PersonalizedSectionKind.BECAUSE_YOU_LISTENED,
                        items = items
                    )
                }
            }

        val trending = pickTrendingChart(charts)
            .filter { it.id !in used && it.id.isNotBlank() }
            .distinctBy { it.id }
            .take(TRENDING_SIZE)
        if (trending.size >= 5) {
            sections += PersonalizedSection(
                id = "trending_now",
                title = trendingChartTitle(charts),
                kind = PersonalizedSectionKind.TRENDING,
                items = trending
            )
        }

        return sections
    }

    private fun buildMix(
        cluster: ArtistCluster,
        profile: ListeningProfile,
        used: MutableSet<String>,
        nowMs: Long,
        chartTracksByArtist: Map<String, List<MusicItem>>
    ): List<MusicItem> {
        val result = mutableListOf<MusicItem>()
        val localIds = mutableSetOf<String>()
        val artistCounts = mutableMapOf<String, Int>()
        val seedKey = cluster.artist.lowercase()
        var seedCount = 0

        fun add(item: MusicItem, artistKey: String) {
            if (result.size >= MIX_SIZE || item.id in localIds || item.id in used) return
            if (item.id in profile.dismissedIds) return
            if (artistKey == seedKey) {
                if (seedCount >= MAX_PER_SEED_ARTIST) return
            } else if (artistKey.isNotBlank() && (artistCounts[artistKey] ?: 0) >= MAX_PER_ARTIST) {
                return
            }
            result += item
            localIds += item.id
            if (artistKey == seedKey) {
                seedCount++
            } else if (artistKey.isNotBlank()) {
                artistCounts[artistKey] = (artistCounts[artistKey] ?: 0) + 1
            }
        }

        cluster.tracks.take(FAMILIAR_PER_MIX).forEach { add(it.item, seedKey) }

        cluster.tracks
            .sortedByDescending { it.score(nowMs) }
            .take(3)
            .flatMap { profile.relatedByTrack[it.item.id].orEmpty() }
            .forEach { candidate -> add(candidate, artistKeyOf(candidate.artist)) }

        // Anonymous charts are the discovery catalogue: they let a mix stay
        // fresh even when the cached related graph is sparse.
        chartTracksByArtist[seedKey].orEmpty().forEach { candidate ->
            add(candidate, seedKey)
        }

        // Fall back to the related graph of the most recent track when the
        // cluster is too small to fill a rail.
        if (result.size < 6) {
            profile.rankedTracks(nowMs)
                .asSequence()
                .flatMap { profile.relatedByTrack[it.item.id].orEmpty().asSequence() }
                .forEach { candidate -> add(candidate, artistKeyOf(candidate.artist)) }
        }

        used += localIds
        return result
    }

    private fun pickTrendingChart(charts: Map<String, List<MusicItem>>): List<MusicItem> {
        if (charts.isEmpty()) return emptyList()
        val preferred = charts.entries.firstOrNull {
            it.key.contains("top song", ignoreCase = true) ||
                it.key.contains("trending", ignoreCase = true)
        }
        return (preferred ?: charts.entries.first()).value
    }

    private fun trendingChartTitle(charts: Map<String, List<MusicItem>>): String {
        val name = charts.entries.firstOrNull {
            it.key.contains("top song", ignoreCase = true) ||
                it.key.contains("trending", ignoreCase = true)
        }?.key
        return name?.takeIf { it.isNotBlank() } ?: "Trending Now"
    }

    private fun artistKeyOf(value: String): String =
        value.split(',', '&', ';')
            .firstOrNull()
            ?.trim()
            ?.lowercase()
            .orEmpty()
}
