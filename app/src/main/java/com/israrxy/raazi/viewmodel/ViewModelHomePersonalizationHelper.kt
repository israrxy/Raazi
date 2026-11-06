package com.israrxy.raazi.viewmodel

import android.util.Log
import androidx.lifecycle.viewModelScope
import com.israrxy.raazi.data.account.YouTubeAccountSession
import com.israrxy.raazi.data.db.HomeInteractionEntity
import com.israrxy.raazi.data.recommendation.HomePersonalizationEngine
import com.israrxy.raazi.data.recommendation.ListeningProfileBuilder
import com.israrxy.raazi.data.recommendation.PersonalizedSection
import com.zionhuang.innertube.YouTube
import com.zionhuang.innertube.models.PlaylistItem
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Rebuilds the local-first personalized rails (My Mix, Because you listened
 * to…, Trending) and the affinity map used to re-rank anonymous shelves.
 * These are generated from Room signals only, so they work for Google Drive
 * users who never sign in to YouTube Music.
 */
internal fun MusicPlayerViewModel.refreshPersonalizedSections(force: Boolean = false) {
    if (!force && personalizedSections.isNotEmpty()) return
    personalizedSectionsJob?.cancel()
    personalizedSectionsJob = viewModelScope.launch {
        try {
            val profile = ListeningProfileBuilder(repository, musicDao).build()
            artistAffinity = profile.artistScores()
            val cachedCharts = _allCharts.value
            val charts = if (cachedCharts.isNotEmpty()) cachedCharts
            else runCatching { repository.getAllCharts() }.getOrDefault(emptyMap())
            if (charts.isNotEmpty()) _allCharts.value = charts
            val generated = HomePersonalizationEngine.build(profile, charts)
            personalizedSections = generated
                .map { it.toHomeSection() }
                .filter { it.items.isNotEmpty() }
            rebuildHomeFeedState()
        } catch (e: Exception) {
            Log.e("MusicVM", "Error generating personalized sections", e)
        }
    }
}

/**
 * Loads the anonymous discovery surfaces that must never disappear for
 * Drive-only users: charts and community playlists.
 */
internal fun MusicPlayerViewModel.loadDiscoverySources(force: Boolean = false) {
    if (!force && (_allCharts.value.isNotEmpty() || _communityPlaylists.value.isNotEmpty())) return

    viewModelScope.launch {
        try {
            val charts = repository.getAllCharts()
            if (charts.isNotEmpty()) {
                _allCharts.value = charts
                rebuildHomeFeedState()
            }
        } catch (e: Exception) {
            Log.w("MusicVM", "Charts source failed", e)
        }
    }

    viewModelScope.launch {
        try {
            runCatching { YouTubeAccountSession.ensureBootstrapped(settingsDataStore) }
            val playlists = fetchCommunityPlaylists()
            if (playlists.isNotEmpty()) {
                _communityPlaylists.value = playlists
                rebuildHomeFeedState()
            }
        } catch (e: Exception) {
            Log.w("MusicVM", "Community playlist source failed", e)
        }
    }
}

private suspend fun MusicPlayerViewModel.fetchCommunityPlaylists(): List<PlaylistItem> {
    val artistSeeds = runCatching {
        repository.playbackHistory.first()
            .mapNotNull { it.artist.split(',').firstOrNull()?.trim() }
            .filter { it.isNotBlank() && it != "Unknown Artist" }
            .distinct()
            .take(3)
    }.getOrDefault(emptyList())

    val seeds = artistSeeds.ifEmpty {
        listOf("Trending hits", "Chill mix", "Party hits")
    }

    val playlists = mutableListOf<PlaylistItem>()
    for (seed in seeds) {
        val result = runCatching {
            withTimeoutOrNull(REQUEST_TIMEOUT_MS) {
                YouTube.search(seed, YouTube.SearchFilter.FILTER_COMMUNITY_PLAYLIST)
            }
        }.getOrNull()?.getOrNull()

        result?.items?.forEach { item ->
            if (item is PlaylistItem) playlists += item
        }
    }
    return playlists.distinctBy { it.id }.take(20)
}

private const val REQUEST_TIMEOUT_MS = 5000L

private fun PersonalizedSection.toHomeSection(): HomeSection = HomeSection(
    id = "personalized_$id",
    title = title,
    type = HomeSectionType.RECOMMENDATION,
    sourceType = HomeInteractionEntity.SOURCE_LOCAL,
    items = items.map(HomeFeedItem::Music)
)
