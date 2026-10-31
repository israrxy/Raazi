package com.israrxy.raazi.viewmodel

import android.util.Log
import androidx.lifecycle.viewModelScope
import com.israrxy.raazi.model.MusicContentType
import com.israrxy.raazi.model.MusicItem
import com.israrxy.raazi.service.AdvancedDownloadManager
import com.zionhuang.innertube.YouTube
import com.zionhuang.innertube.models.ArtistItem
import com.zionhuang.innertube.models.SongItem
import com.zionhuang.innertube.models.WatchEndpoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal fun MusicPlayerViewModel.observeNetworkConnectivity() {
    viewModelScope.launch {
        var wasOffline = false
        connectivityObserver.isOnline.collect { online ->
            if (!online) {
                wasOffline = true
                if (_error.value != null) {
                    _error.value = null
                }
                rebuildHomeFeedState()
            } else {
                if (wasOffline) {
                    wasOffline = false
                    _justReconnected.value = true
                    launch {
                        delay(3000L)
                        _justReconnected.value = false
                    }
                    loadHomeContent()
                    _playbackState.value.currentTrack?.let { track ->
                        if (_lyrics.value == null) fetchLyricsImpl(track)
                        if (_autoPlayQueue.value.isEmpty()) fetchAutoPlayQueue(track)
                        if (_playbackState.value.playlist.size <= 1) {
                            playbackService?.let { service ->
                                launch(Dispatchers.IO) {
                                    try {
                                        val upNext = musicExtractor.getUpNext(track.id)
                                            .filter { it.id.isNotBlank() && it.id != track.id }
                                            .distinctBy { it.id }
                                        if (upNext.isNotEmpty()) {
                                            withContext(Dispatchers.Main) {
                                                service.addToQueue(upNext)
                                            }
                                        }
                                    } catch (_: Exception) {}
                                }
                            }
                        }
                    }
                } else if (_homePage.value == null && !_isLoading.value) {
                    loadHomeContent()
                }
            }
        }
    }
}

internal fun MusicPlayerViewModel.observeHistoryUpdates() {
    viewModelScope.launch {
        try {
            repository.playbackHistory.collect { history ->
                val activeTrack = _playbackState.value.currentTrack
                val merged = if (activeTrack != null) {
                    (listOf(activeTrack) + history.filter { it.id != activeTrack.id }).take(30)
                } else {
                    history.take(30)
                }
                _keepListening.value = merged
                rebuildHomeFeedState()
            }
        } catch (e: Exception) {
            Log.w("MusicVM", "History observation stopped", e)
        }
    }
    viewModelScope.launch {
        try {
            repository.getForgottenFavorites().collect { forgotten ->
                _forgottenFavorites.value = forgotten
                rebuildHomeFeedState()
            }
        } catch (e: Exception) {
            Log.w("MusicVM", "Forgotten favorites observation stopped", e)
        }
    }
}

internal fun MusicPlayerViewModel.observeDownloadEvents() {
    viewModelScope.launch {
        advancedDownloadManager.downloadEvents.collect { event ->
            when (event) {
                is AdvancedDownloadManager.DownloadEvent.Started ->
                    _downloadResult.value = "Downloading ${event.title}"
                is AdvancedDownloadManager.DownloadEvent.Completed ->
                    _downloadResult.value = "Downloaded ${event.title}"
                is AdvancedDownloadManager.DownloadEvent.Failed ->
                    _error.value = "Download failed: ${event.error}"
                is AdvancedDownloadManager.DownloadEvent.Cancelled ->
                    _downloadResult.value = "Cancelled ${event.title}"
                is AdvancedDownloadManager.DownloadEvent.Queued ->
                    _downloadResult.value = "Queued ${event.title}"
            }
        }
    }
}

internal fun MusicPlayerViewModel.syncDownloadSettings() {
    viewModelScope.launch {
        settingsDataStore.downloadWifiOnly.collect { wifiOnly ->
            advancedDownloadManager.wifiOnly = wifiOnly
        }
    }
    viewModelScope.launch {
        settingsDataStore.maxConcurrentDownloads.collect { max ->
            advancedDownloadManager.maxConcurrentDownloads = max.toIntOrNull() ?: 2
        }
    }
}

internal fun MusicPlayerViewModel.fetchRelatedAndArtistSongsForTrack(track: MusicItem) {
    viewModelScope.launch(Dispatchers.IO) {
        try {
            repository.fetchAndSaveRelated(track)
        } catch (e: Exception) {
            Log.w("MusicVM", "Failed to fetch related for ${track.title}", e)
        }

        try {
            val artistQuery = track.artist.split(",").first().trim()
            if (artistQuery.isNotBlank() && artistQuery != "Unknown Artist") {
                val result = YouTube.search(
                    artistQuery,
                    filter = YouTube.SearchFilter.FILTER_SONG
                ).getOrNull()
                val songs = result?.items?.mapNotNull { item ->
                    when (item) {
                        is SongItem -> MusicItem(
                            id = item.id,
                            title = item.title,
                            artist = item.artists.joinToString(", ") { it.name },
                            duration = (item.duration?.toLong() ?: 0) * 1000L,
                            thumbnailUrl = item.thumbnail,
                            audioUrl = "",
                            videoUrl = item.id,
                            isLive = false,
                            contentType = MusicContentType.SONG,
                            setVideoId = item.setVideoId
                        )
                        else -> null
                    }
                }?.take(5) ?: emptyList()
                _currentArtistSongs.value = songs
            } else {
                _currentArtistSongs.value = emptyList()
            }
        } catch (e: Exception) {
            Log.w("MusicVM", "Failed to fetch artist songs for ${track.artist}", e)
            _currentArtistSongs.value = emptyList()
        }
    }
}

internal suspend fun MusicPlayerViewModel.loadSimilarRecommendations() {
    val recommendations = mutableListOf<MusicPlayerViewModel.SimilarRecommendation>()

    try {
        val playbackList = repository.playbackHistory.first()

        if (playbackList.isEmpty()) {
            loadFallbackRecommendations(recommendations)
            _similarRecommendations.value = recommendations
            return
        }

        val topArtists = playbackList
            .groupBy { it.artist }
            .entries.sortedByDescending { it.value.size }
            .take(5)

        for ((artistName, songs) in topArtists) {
            try {
                val searchResult = YouTube.search(
                    artistName,
                    filter = YouTube.SearchFilter.FILTER_ARTIST
                ).getOrNull()
                val artistItem = searchResult?.items?.firstOrNull() as? ArtistItem

                if (artistItem != null) {
                    val artistPage = YouTube.artist(artistItem.id).getOrNull()
                    artistPage?.let { page ->
                        val items = page.sections.flatMap { it.items }.shuffled().take(15)
                            .map { MusicPlayerViewModel.RecommendationItem.FromYTItem(it) }
                        if (items.isNotEmpty() && recommendations.size < 3) {
                            recommendations.add(
                                MusicPlayerViewModel.SimilarRecommendation(
                                    title = "More from $artistName",
                                    thumbnailUrl = songs.firstOrNull()?.thumbnailUrl,
                                    items = items
                                )
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w("MusicVM", "Artist recommendation failed for $artistName: ${e.message}")
            }
        }

        val topSongs = playbackList.take(10).distinctBy { it.videoUrl }

        for (song in topSongs.take(5)) {
            try {
                if (song.videoUrl.isNotEmpty()) {
                    val nextPage = YouTube.next(
                        WatchEndpoint(videoId = song.videoUrl)
                    ).getOrNull()

                    val relatedEndpoint = nextPage?.relatedEndpoint
                    if (relatedEndpoint != null) {
                        val relatedPage = YouTube.related(relatedEndpoint).getOrNull()
                        relatedPage?.let { page ->
                            val items = (
                                page.songs.shuffled().take(8).map { MusicPlayerViewModel.RecommendationItem.FromYTItem(it) } +
                                    page.albums.shuffled().take(4).map { MusicPlayerViewModel.RecommendationItem.FromYTItem(it) } +
                                    page.playlists.shuffled().take(3).map { MusicPlayerViewModel.RecommendationItem.FromYTItem(it) }
                                ).shuffled()

                            if (items.isNotEmpty() && recommendations.size < 5) {
                                recommendations.add(
                                    MusicPlayerViewModel.SimilarRecommendation(
                                        title = "Because you listened to ${song.title}",
                                        thumbnailUrl = song.thumbnailUrl,
                                        items = items
                                    )
                                )
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w("MusicVM", "Song recommendation failed for ${song.title}: ${e.message}")
            }
        }

        try {
            val allFavoriteTracks = repository.favoriteTracks.first()
            if (allFavoriteTracks.isNotEmpty()) {
                val randomFavorite = allFavoriteTracks.random()
                val searchResult = YouTube.search(
                    "${randomFavorite.title} ${randomFavorite.artist}",
                    filter = YouTube.SearchFilter.FILTER_SONG
                ).getOrNull()

                searchResult?.items?.firstOrNull()?.let { item ->
                    if (item is SongItem) {
                        val nextPage = YouTube.next(
                            WatchEndpoint(videoId = item.id)
                        ).getOrNull()

                        val relatedEndpoint = nextPage?.relatedEndpoint
                        if (relatedEndpoint != null) {
                            val relatedPage = YouTube.related(relatedEndpoint).getOrNull()
                            relatedPage?.let { page ->
                                val recItems = page.songs.shuffled().take(12)
                                    .map { MusicPlayerViewModel.RecommendationItem.FromYTItem(it) }
                                if (recItems.isNotEmpty() && recommendations.size < 6) {
                                    recommendations.add(
                                        MusicPlayerViewModel.SimilarRecommendation(
                                            title = "More like ${item.title}",
                                            thumbnailUrl = null,
                                            items = recItems
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w("MusicVM", "Genre recommendation failed: ${e.message}")
        }

        if (recommendations.size < 2) {
            loadTrendingRecommendations(recommendations)
        }

        _similarRecommendations.value = recommendations.shuffled()
    } catch (e: Exception) {
        Log.e("MusicVM", "Error in loadSimilarRecommendations", e)
        val fallbackRecs = mutableListOf<MusicPlayerViewModel.SimilarRecommendation>()
        loadFallbackRecommendations(fallbackRecs)
        _similarRecommendations.value = fallbackRecs
    }
}

private suspend fun MusicPlayerViewModel.loadFallbackRecommendations(
    recommendations: MutableList<MusicPlayerViewModel.SimilarRecommendation>
) {
    try {
        val trending = repository.getTrending()
        if (trending.items.isNotEmpty()) {
            recommendations.add(
                MusicPlayerViewModel.SimilarRecommendation(
                    title = "Trending Now",
                    thumbnailUrl = trending.items.firstOrNull()?.thumbnailUrl,
                    items = trending.items.take(15).map { MusicPlayerViewModel.RecommendationItem.FromMusicItem(it) }
                )
            )
        }

        val charts = repository.getAllCharts()
        charts.entries.firstOrNull()?.let { (chartName, items) ->
            if (items.isNotEmpty()) {
                recommendations.add(
                    MusicPlayerViewModel.SimilarRecommendation(
                        title = chartName,
                        thumbnailUrl = items.firstOrNull()?.thumbnailUrl,
                        items = items.take(15).map { MusicPlayerViewModel.RecommendationItem.FromMusicItem(it) }
                    )
                )
            }
        }
    } catch (e: Exception) {
        Log.e("MusicVM", "Fallback recommendations failed", e)
    }
}

private suspend fun MusicPlayerViewModel.loadTrendingRecommendations(
    recommendations: MutableList<MusicPlayerViewModel.SimilarRecommendation>
) {
    try {
        val trending = repository.getTrending()
        if (trending.items.isNotEmpty()) {
            recommendations.add(
                MusicPlayerViewModel.SimilarRecommendation(
                    title = "Trending",
                    thumbnailUrl = trending.items.firstOrNull()?.thumbnailUrl,
                    items = trending.items.take(15).map { MusicPlayerViewModel.RecommendationItem.FromMusicItem(it) }
                )
            )
        }
    } catch (e: Exception) {
        Log.w("MusicVM", "Trending recommendations failed", e)
    }
}
