package com.israrxy.raazi.data.recommendation

import com.israrxy.raazi.data.repository.MusicRepository
import com.israrxy.raazi.model.MusicContentType
import com.israrxy.raazi.model.MusicItem
import com.zionhuang.innertube.YouTube
import com.zionhuang.innertube.models.ArtistItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Artist candidates for the cold-start tastebuilder. Uses only anonymous
 * surfaces: the local charts cache plus an anonymous artist search.
 */
class TasteCandidateLoader(private val repository: MusicRepository) {

    suspend fun load(
        seeds: List<MusicItem>,
        language: String,
        limit: Int = 24
    ): List<MusicItem> = withContext(Dispatchers.IO) {
        val result = LinkedHashMap<String, MusicItem>()
        val seenNames = mutableSetOf<String>()

        seeds.forEach { seed ->
            val name = seed.title.ifBlank { seed.artist }
            if (name.isNotBlank() && seenNames.add(name.lowercase())) {
                result[seed.id] = seed
            }
        }

        val charts = runCatching { repository.getAllCharts() }.getOrDefault(emptyMap())
        charts.values.flatten().forEach { track ->
            if (result.size >= limit) return@forEach
            val name = track.artist.split(',', '&', ';').firstOrNull()?.trim().orEmpty()
            if (name.isBlank() || !seenNames.add(name.lowercase())) return@forEach
            result["artist:${name.lowercase()}"] = artistItem(name, track.artistId, track.thumbnailUrl)
        }

        val search = withTimeoutOrNull(SEARCH_TIMEOUT_MS) {
            runCatching {
                YouTube.search(languageQuery(language), YouTube.SearchFilter.FILTER_ARTIST).getOrNull()
            }.getOrNull()
        }
        search?.items.orEmpty()
            .filterIsInstance<ArtistItem>()
            .forEach { artist ->
                if (result.size >= limit) return@forEach
                if (!seenNames.add(artist.title.lowercase())) return@forEach
                result[artist.id] = MusicItem(
                    id = artist.id,
                    title = artist.title,
                    artist = artist.title,
                    duration = 0L,
                    thumbnailUrl = artist.thumbnail.orEmpty(),
                    audioUrl = "",
                    videoUrl = artist.shareLink,
                    artistId = artist.id,
                    contentType = MusicContentType.ARTIST
                )
            }

        result.values.toList()
    }

    private fun artistItem(name: String, artistId: String?, thumbnailUrl: String) = MusicItem(
        id = artistId ?: "artist:${name.lowercase()}",
        title = name,
        artist = name,
        duration = 0L,
        thumbnailUrl = thumbnailUrl,
        audioUrl = "",
        videoUrl = "",
        artistId = artistId,
        contentType = MusicContentType.ARTIST
    )

    companion object {
        private const val SEARCH_TIMEOUT_MS = 6_000L

        fun languageQuery(language: String): String = when (language.uppercase()) {
            "HINDI" -> "hindi music artists"
            "PUNJABI" -> "punjabi music artists"
            "TAMIL" -> "tamil music artists"
            "TELUGU" -> "telugu music artists"
            "ENGLISH" -> "english pop artists"
            else -> "popular music artists"
        }
    }
}
