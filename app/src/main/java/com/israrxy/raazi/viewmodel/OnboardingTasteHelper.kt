package com.israrxy.raazi.viewmodel

import androidx.lifecycle.viewModelScope
import com.israrxy.raazi.model.MusicContentType
import com.israrxy.raazi.model.MusicItem
import com.israrxy.raazi.model.canPlayAsTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class OnboardingArtistSeed(
    val id: String,
    val name: String,
    val thumbnailUrl: String,
    val tag: String = "Popular"
)

internal object OnboardingTasteCatalog {
    fun defaultArtistsForRegion(countryCode: String): List<OnboardingArtistSeed> {
        val code = countryCode.uppercase()
        val southAsian = listOf(
            OnboardingArtistSeed("UCtFOW7jJXChfFNoucRFqRmw", "Arijit Singh", "https://i.ytimg.com/vi/IJq0yyWug1k/hqdefault.jpg", "Romantic"),
            OnboardingArtistSeed("UC_A7K2dXFsTMAciGmnNxy-Q", "Atif Aslam", "https://i.ytimg.com/vi/a9Hxkc9YxGE/hqdefault.jpg", "Melody"),
            OnboardingArtistSeed("UC3MLnJtqc_phABBriLRhtgQ", "A.R. Rahman", "https://i.ytimg.com/vi/jHNNMj5bNQw/hqdefault.jpg", "Legend"),
            OnboardingArtistSeed("UCq-Fj5jknLsUf-MWSy4_brA", "Shreya Ghoshal", "https://i.ytimg.com/vi/6FURuLYrR_Q/hqdefault.jpg", "Vocals"),
            OnboardingArtistSeed("UCbTLwN10NoCU4WDzLf1JMOA", "Diljit Dosanjh", "https://i.ytimg.com/vi/cl0a3i2wFcc/hqdefault.jpg", "Punjabi"),
            OnboardingArtistSeed("UC2pmfLm7iq6Ov1UwYrWYkZA", "Pritam", "https://i.ytimg.com/vi/BddP6PYo2gs/hqdefault.jpg", "Bollywood"),
            OnboardingArtistSeed("UC0C-w0YjGpqDXGB8IHb662A", "The Weeknd", "https://i.ytimg.com/vi/4NRXx6U8ABQ/hqdefault.jpg", "Pop"),
            OnboardingArtistSeed("UC-J-KZfRV8c13fOCkhXdLiQ", "Dua Lipa", "https://i.ytimg.com/vi/TUVcZfQe-Kw/hqdefault.jpg", "Dance")
        )
        val global = listOf(
            OnboardingArtistSeed("UC0C-w0YjGpqDXGB8IHb662A", "The Weeknd", "https://i.ytimg.com/vi/4NRXx6U8ABQ/hqdefault.jpg", "Pop"),
            OnboardingArtistSeed("UCqECaJ8Gagnn7YCbPEzWH6g", "Taylor Swift", "https://i.ytimg.com/vi/b1kbLwvqugk/hqdefault.jpg", "Pop"),
            OnboardingArtistSeed("UCByOQJjav0CUDwxCk-jVNRQ", "Drake", "https://i.ytimg.com/vi/xpVfcZ0ZcFM/hqdefault.jpg", "Hip-Hop"),
            OnboardingArtistSeed("UC-J-KZfRV8c13fOCkhXdLiQ", "Dua Lipa", "https://i.ytimg.com/vi/TUVcZfQe-Kw/hqdefault.jpg", "Dance"),
            OnboardingArtistSeed("UCiGm_E4ZwYSHV3bcW1pnSeQ", "Billie Eilish", "https://i.ytimg.com/vi/DyDfgMOUjCI/hqdefault.jpg", "Indie"),
            OnboardingArtistSeed("UCtFOW7jJXChfFNoucRFqRmw", "Arijit Singh", "https://i.ytimg.com/vi/IJq0yyWug1k/hqdefault.jpg", "Soul"),
            OnboardingArtistSeed("UCoUM-UJ7rirJYP8CQ0EIaHA", "Bruno Mars", "https://i.ytimg.com/vi/OPf0YbXqDm0/hqdefault.jpg", "Funk"),
            OnboardingArtistSeed("UCcdwLMPsaU2ezNSJU1nFoBQ", "Coldplay", "https://i.ytimg.com/vi/dvgZkm1xWPE/hqdefault.jpg", "Rock")
        )
        return if (code in setOf("IN", "PK", "BD", "NP", "LK", "AE", "GB", "CA")) southAsian else global
    }

    fun defaultSongsForRegion(countryCode: String): List<MusicItem> {
        val code = countryCode.uppercase()
        val southAsianSongs = listOf(
            MusicItem(
                id = "BddP6PYo2gs",
                title = "Kesariya",
                artist = "Pritam, Arijit Singh",
                duration = 268000L,
                thumbnailUrl = "https://i.ytimg.com/vi/BddP6PYo2gs/hqdefault.jpg",
                audioUrl = "",
                videoUrl = "BddP6PYo2gs",
                contentType = MusicContentType.SONG
            ),
            MusicItem(
                id = "IJq0yyWug1k",
                title = "Tum Hi Ho",
                artist = "Arijit Singh",
                duration = 262000L,
                thumbnailUrl = "https://i.ytimg.com/vi/IJq0yyWug1k/hqdefault.jpg",
                audioUrl = "",
                videoUrl = "IJq0yyWug1k",
                contentType = MusicContentType.SONG
            ),
            MusicItem(
                id = "6FURuLYrR_Q",
                title = "Samjhawan",
                artist = "Jawad Ahmad, Shaarib Toshi, Arijit Singh, Shreya Ghoshal",
                duration = 269000L,
                thumbnailUrl = "https://i.ytimg.com/vi/6FURuLYrR_Q/hqdefault.jpg",
                audioUrl = "",
                videoUrl = "6FURuLYrR_Q",
                contentType = MusicContentType.SONG
            ),
            MusicItem(
                id = "cl0a3i2wFcc",
                title = "Lover",
                artist = "Diljit Dosanjh",
                duration = 190000L,
                thumbnailUrl = "https://i.ytimg.com/vi/cl0a3i2wFcc/hqdefault.jpg",
                audioUrl = "",
                videoUrl = "cl0a3i2wFcc",
                contentType = MusicContentType.SONG
            ),
            MusicItem(
                id = "a9Hxkc9YxGE",
                title = "Tajdar-e-Haram",
                artist = "Atif Aslam",
                duration = 618000L,
                thumbnailUrl = "https://i.ytimg.com/vi/a9Hxkc9YxGE/hqdefault.jpg",
                audioUrl = "",
                videoUrl = "a9Hxkc9YxGE",
                contentType = MusicContentType.SONG
            ),
            MusicItem(
                id = "4NRXx6U8ABQ",
                title = "Blinding Lights",
                artist = "The Weeknd",
                duration = 200000L,
                thumbnailUrl = "https://i.ytimg.com/vi/4NRXx6U8ABQ/hqdefault.jpg",
                audioUrl = "",
                videoUrl = "4NRXx6U8ABQ",
                contentType = MusicContentType.SONG
            )
        )
        val globalSongs = listOf(
            MusicItem(
                id = "4NRXx6U8ABQ",
                title = "Blinding Lights",
                artist = "The Weeknd",
                duration = 200000L,
                thumbnailUrl = "https://i.ytimg.com/vi/4NRXx6U8ABQ/hqdefault.jpg",
                audioUrl = "",
                videoUrl = "4NRXx6U8ABQ",
                contentType = MusicContentType.SONG
            ),
            MusicItem(
                id = "TUVcZfQe-Kw",
                title = "Levitating",
                artist = "Dua Lipa",
                duration = 203000L,
                thumbnailUrl = "https://i.ytimg.com/vi/TUVcZfQe-Kw/hqdefault.jpg",
                audioUrl = "",
                videoUrl = "TUVcZfQe-Kw",
                contentType = MusicContentType.SONG
            ),
            MusicItem(
                id = "OPf0YbXqDm0",
                title = "Uptown Funk",
                artist = "Mark Ronson, Bruno Mars",
                duration = 270000L,
                thumbnailUrl = "https://i.ytimg.com/vi/OPf0YbXqDm0/hqdefault.jpg",
                audioUrl = "",
                videoUrl = "OPf0YbXqDm0",
                contentType = MusicContentType.SONG
            ),
            MusicItem(
                id = "DyDfgMOUjCI",
                title = "bad guy",
                artist = "Billie Eilish",
                duration = 194000L,
                thumbnailUrl = "https://i.ytimg.com/vi/DyDfgMOUjCI/hqdefault.jpg",
                audioUrl = "",
                videoUrl = "DyDfgMOUjCI",
                contentType = MusicContentType.SONG
            ),
            MusicItem(
                id = "dvgZkm1xWPE",
                title = "Viva La Vida",
                artist = "Coldplay",
                duration = 242000L,
                thumbnailUrl = "https://i.ytimg.com/vi/dvgZkm1xWPE/hqdefault.jpg",
                audioUrl = "",
                videoUrl = "dvgZkm1xWPE",
                contentType = MusicContentType.SONG
            ),
            MusicItem(
                id = "BddP6PYo2gs",
                title = "Kesariya",
                artist = "Pritam, Arijit Singh",
                duration = 268000L,
                thumbnailUrl = "https://i.ytimg.com/vi/BddP6PYo2gs/hqdefault.jpg",
                audioUrl = "",
                videoUrl = "BddP6PYo2gs",
                contentType = MusicContentType.SONG
            )
        )
        return if (code in setOf("IN", "PK", "BD", "NP", "LK", "AE", "GB", "CA")) southAsianSongs else globalSongs
    }
}

internal suspend fun MusicPlayerViewModel.loadOnboardingTasteSuggestions(
    countryCode: String
): Pair<List<OnboardingArtistSeed>, List<MusicItem>> = withContext(Dispatchers.IO) {
    val defaultArtists = OnboardingTasteCatalog.defaultArtistsForRegion(countryCode)
    val defaultSongs = OnboardingTasteCatalog.defaultSongsForRegion(countryCode)

    val chartTracks = runCatching {
        val charts = _allCharts.value.ifEmpty { repository.getAllCharts() }
        charts.values.flatten()
            .filter { it.canPlayAsTrack() && it.title.isNotBlank() }
            .distinctBy { it.id }
            .take(12)
    }.getOrDefault(emptyList())

    val trendingTracks = if (chartTracks.isNotEmpty()) {
        chartTracks
    } else {
        runCatching {
            repository.getTrending().items
                .filter { it.id.isNotBlank() && it.title.isNotBlank() && !it.isPlaylist }
                .distinctBy { it.id }
                .take(12)
        }.getOrDefault(emptyList())
    }

    val mergedSongs = (trendingTracks + defaultSongs).distinctBy { it.id }.take(14)

    val chartArtists = mergedSongs
        .mapNotNull { track ->
            val primaryName = track.artist.split(',', '&', ';').firstOrNull()?.trim()
            if (primaryName.isNullOrBlank() || primaryName.equals("Unknown Artist", ignoreCase = true)) {
                null
            } else {
                OnboardingArtistSeed(
                    id = track.artistId?.takeIf { it.isNotBlank() } ?: primaryName,
                    name = primaryName,
                    thumbnailUrl = track.thumbnailUrl,
                    tag = "Trending"
                )
            }
        }
        .distinctBy { it.name.lowercase() }

    val mergedArtists = (defaultArtists + chartArtists)
        .distinctBy { it.name.lowercase() }
        .take(12)

    mergedArtists to mergedSongs
}

internal suspend fun MusicPlayerViewModel.searchOnboardingTaste(
    query: String
): Pair<List<OnboardingArtistSeed>, List<MusicItem>> = withContext(Dispatchers.IO) {
    val trimmed = query.trim()
    if (trimmed.isBlank()) return@withContext emptyList<OnboardingArtistSeed>() to emptyList()

    val result = runCatching { repository.searchMusic(trimmed) }.getOrNull()
        ?: return@withContext emptyList<OnboardingArtistSeed>() to emptyList()

    val allItems = listOfNotNull(result.topResult) + result.topTracks + result.items
    val artistMatches = allItems
        .filter {
            it.contentType == MusicContentType.ARTIST ||
                (it.contentType == MusicContentType.UNKNOWN && it.artist.equals("Artist", ignoreCase = true))
        }
        .map { item ->
            OnboardingArtistSeed(
                id = item.artistId?.takeIf { it.isNotBlank() } ?: item.id,
                name = item.title,
                thumbnailUrl = item.thumbnailUrl,
                tag = "Artist"
            )
        }

    val songMatches = allItems
        .filter {
            it.id.isNotBlank() &&
                !it.isPlaylist &&
                (it.contentType == MusicContentType.SONG ||
                    it.contentType == MusicContentType.VIDEO ||
                    (it.contentType == MusicContentType.UNKNOWN && !it.artist.equals("Artist", ignoreCase = true)))
        }
        .distinctBy { it.id }
        .take(12)

    val derivedArtists = songMatches.mapNotNull { song ->
        val primaryArtist = song.artist.split(',', '&', ';').firstOrNull()?.trim()
        if (primaryArtist.isNullOrBlank() || primaryArtist.equals("Unknown Artist", ignoreCase = true)) {
            null
        } else {
            OnboardingArtistSeed(
                id = song.artistId?.takeIf { it.isNotBlank() } ?: primaryArtist,
                name = primaryArtist,
                thumbnailUrl = song.thumbnailUrl,
                tag = "Artist"
            )
        }
    }

    val combinedArtists = (artistMatches + derivedArtists)
        .distinctBy { it.name.lowercase() }
        .take(8)

    combinedArtists to songMatches
}

internal fun MusicPlayerViewModel.commitOnboardingTasteSelections(
    selectedArtists: List<OnboardingArtistSeed>,
    selectedSongs: List<MusicItem>
) {
    if (selectedArtists.isEmpty() && selectedSongs.isEmpty()) return
    viewModelScope.launch(Dispatchers.IO) {
        val currentSavedIds = savedCollectionIds.value
        selectedArtists.forEach { artist ->
            val savedId = com.israrxy.raazi.model.savedCollectionId(MusicContentType.ARTIST, artist.id)
            if (savedId !in currentSavedIds) {
                runCatching {
                    repository.toggleSavedArtist(
                        artistId = artist.id,
                        artistName = artist.name,
                        thumbnailUrl = artist.thumbnailUrl
                    )
                }
            }
        }
        selectedSongs.forEach { song ->
            runCatching { repository.addToFavorites(song) }
            runCatching { repository.addToHistory(song) }
        }
        withContext(Dispatchers.Main) {
            if (selectedSongs.isNotEmpty()) {
                _keepListening.value = (selectedSongs + _keepListening.value)
                    .distinctBy { it.id }
                    .take(30)
            }
            refreshPersonalizedSections(force = true)
            rebuildHomeFeedState()
        }
    }
}
