package com.israrxy.raazi.viewmodel

import android.util.Log
import androidx.lifecycle.viewModelScope
import com.israrxy.raazi.data.db.HomeInteractionEntity
import com.israrxy.raazi.data.recommendation.GeneratedShelf
import com.israrxy.raazi.data.recommendation.HomeShelfEngine
import com.israrxy.raazi.data.recommendation.PlaybackContext
import com.israrxy.raazi.data.recommendation.ShelfItem
import com.israrxy.raazi.data.recommendation.ShelfKind
import com.israrxy.raazi.data.recommendation.ShelfLayout
import com.israrxy.raazi.data.recommendation.TasteCandidateLoader
import com.israrxy.raazi.data.recommendation.TasteProfile
import com.israrxy.raazi.data.recommendation.TasteSeedCodec
import com.israrxy.raazi.data.recommendation.homeShelfSeed
import com.israrxy.raazi.model.MusicContentType
import com.israrxy.raazi.model.MusicItem
import com.israrxy.raazi.model.Playlist
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private const val TAG = "HomeShelves"
private const val PLAYBACK_REFRESH_INTERVAL_MS = 10 * 60 * 1000L

/**
 * Signed-out shelf refresh. Paints the cached feed for today's seed first,
 * then emits freshly generated shelves progressively.
 */
internal fun MusicPlayerViewModel.refreshHomeShelves(profile: TasteProfile, force: Boolean = false) {
    val state = homeTaste
    if (!force && state.shelves.value.isNotEmpty()) return
    state.job?.cancel()
    state.job = viewModelScope.launch {
        val seed = currentHomeShelfSeed()
        val engine = HomeShelfEngine(repository, state.playbackContextStore, state.shelfCache)
        val cached = engine.cached(seed)
        if (cached.isNotEmpty()) {
            state.shelves.value = cached.sortedBy { it.orderIndex() }.map { it.toHomeSection() }
            rebuildHomeFeedState()
        }

        val collected = mutableListOf<GeneratedShelf>()
        try {
            engine.stream(profile, seed).collect { shelf ->
                collected += shelf
                val merged = (collected + cached.filterNot { stale ->
                    collected.any { it.id == stale.id }
                }).sortedBy { it.orderIndex() }
                state.shelves.value = merged.map { it.toHomeSection() }
                rebuildHomeFeedState()
            }
            if (collected.isNotEmpty()) {
                engine.save(seed, collected.sortedBy { it.orderIndex() })
            }
            state.lastSeed = seed
            state.lastRefreshMs = System.currentTimeMillis()
        } catch (e: Exception) {
            Log.w(TAG, "Shelf generation stopped: ${e.message}")
        }
    }
}

internal suspend fun MusicPlayerViewModel.currentHomeShelfSeed(): Int {
    val refreshCounter = runCatching { settingsDataStore.homeRefreshSeed.first() }.getOrDefault(0)
    return homeShelfSeed(System.currentTimeMillis(), refreshCounter)
}

internal suspend fun MusicPlayerViewModel.advanceHomeShelfSeed() {
    val current = runCatching { settingsDataStore.homeRefreshSeed.first() }.getOrDefault(0)
    settingsDataStore.setHomeRefreshSeed(current + 1)
    homeTaste.lastRefreshMs = 0L
}

/** Paints today's cached shelves before the (possibly slow) profile rebuild. */
internal fun MusicPlayerViewModel.paintCachedHomeShelves() {
    if (homeTaste.shelves.value.isNotEmpty()) return
    viewModelScope.launch {
        val seed = currentHomeShelfSeed()
        val engine = HomeShelfEngine(repository, homeTaste.playbackContextStore, homeTaste.shelfCache)
        val cached = engine.cached(seed)
        if (cached.isNotEmpty() && homeTaste.shelves.value.isEmpty()) {
            homeTaste.shelves.value = cached.sortedBy { it.orderIndex() }.map { it.toHomeSection() }
            rebuildHomeFeedState()
        }
    }
}

internal fun MusicPlayerViewModel.maybeRefreshTasteAfterPlayback() {
    val now = System.currentTimeMillis()
    if (now - homeTaste.lastRefreshMs < PLAYBACK_REFRESH_INTERVAL_MS) return
    refreshPersonalizedSections(force = true)
}

fun MusicPlayerViewModel.playMix(title: String, artists: List<String>, items: List<MusicItem>) {
    if (items.isEmpty()) return
    _isSearchQueue.value = false
    viewModelScope.launch(Dispatchers.IO) {
        runCatching {
            homeTaste.playbackContextStore.record(
                PlaybackContext(
                    id = "mix:${artists.joinToString("|").lowercase()}",
                    title = title,
                    subtitle = artists.joinToString(", "),
                    thumbnailUrl = items.first().thumbnailUrl,
                    kind = PlaybackContext.KIND_ARTIST_RADIO
                )
            )
        }
    }
    playPlaylist(items.shuffled(), 0)
}

/** Records the album/playlist context behind a playback so Listen again can show it. */
internal fun MusicPlayerViewModel.rememberPlaylistContext(playlist: Playlist) {
    viewModelScope.launch(Dispatchers.IO) {
        val isAlbum = homeTaste.profile?.albums?.any {
            it.title.equals(playlist.title, ignoreCase = true)
        } == true
        runCatching {
            homeTaste.playbackContextStore.record(
                PlaybackContext(
                    id = playlist.id,
                    title = playlist.title,
                    subtitle = playlist.description.ifBlank { "Playlist" },
                    thumbnailUrl = playlist.thumbnailUrl,
                    kind = if (isAlbum) PlaybackContext.KIND_ALBUM else PlaybackContext.KIND_PLAYLIST
                )
            )
        }
    }
}

internal fun MusicPlayerViewModel.openTasteBuilder() {
    homeTaste.showBuilder.value = true
    loadTasteBuilderCandidates()
}

internal fun MusicPlayerViewModel.closeTasteBuilder() {
    homeTaste.showBuilder.value = false
}

internal fun MusicPlayerViewModel.dismissTasteBuilder() {
    homeTaste.builderDismissed.value = true
    viewModelScope.launch { runCatching { settingsDataStore.setTasteBuilderDismissed(true) } }
}

internal fun MusicPlayerViewModel.loadTasteBuilderCandidates() {
    if (homeTaste.candidatesJob?.isActive == true) return
    homeTaste.candidatesJob = viewModelScope.launch {
        val language = runCatching { settingsDataStore.tasteLanguage.first() }
            .getOrDefault(TasteProfile.LANGUAGE_AUTO)
        val profileArtists = homeTaste.profile?.artists.orEmpty().map { artist ->
            MusicItem(
                id = artist.artistId ?: artist.key,
                title = artist.name,
                artist = artist.name,
                duration = 0L,
                thumbnailUrl = artist.thumbnailUrl.orEmpty(),
                audioUrl = "",
                videoUrl = "",
                artistId = artist.artistId,
                contentType = MusicContentType.ARTIST
            )
        }
        if (profileArtists.isNotEmpty() && homeTaste.candidates.value.isEmpty()) {
            homeTaste.candidates.value = profileArtists
        }

        val loaded = runCatching {
            TasteCandidateLoader(repository).load(
                seeds = homeTaste.profile?.seedArtists.orEmpty(),
                language = language
            )
        }.getOrDefault(emptyList())

        homeTaste.candidates.value = (profileArtists + loaded)
            .distinctBy { it.title.lowercase() }
            .take(24)
    }
}

internal fun MusicPlayerViewModel.submitTastePicks(picks: List<MusicItem>, language: String) {
    if (picks.size < 3) return
    homeTaste.showBuilder.value = false
    homeTaste.language.value = language
    homeTaste.builderDismissed.value = true
    viewModelScope.launch {
        settingsDataStore.setTasteSeedArtists(TasteSeedCodec.encodeSeeds(picks))
        settingsDataStore.setTasteLanguage(language)
        settingsDataStore.setTasteBuilderDismissed(true)
        advanceHomeShelfSeed()
        refreshPersonalizedSections(force = true)
    }
}

internal fun MusicPlayerViewModel.dismissTasteArtist(artistName: String) {
    val key = TasteProfile.artistKey(artistName)
    if (key.isBlank()) return

    homeTaste.shelves.value = homeTaste.shelves.value.filterNot { section ->
        TasteProfile.artistKey(section.artistName.orEmpty()) == key
    }
    homeTaste.profile = homeTaste.profile?.let { profile ->
        profile.copy(
            artists = profile.artists.filterNot { it.key == key },
            tracks = profile.tracks.filterNot { it.artistKey == key },
            dismissedArtistKeys = profile.dismissedArtistKeys + key
        )
    }
    rebuildHomeFeedState()

    viewModelScope.launch {
        val raw = runCatching { settingsDataStore.tasteDismissedArtists.first() }.getOrDefault("")
        val keys = TasteSeedCodec.decodeArtistKeys(raw) + key
        settingsDataStore.setTasteDismissedArtists(TasteSeedCodec.encodeArtistKeys(keys))
        advanceHomeShelfSeed()
        refreshPersonalizedSections(force = true)
    }
}

internal fun MusicPlayerViewModel.resetTasteProfile() {
    homeTaste.showBuilder.value = false
    homeTaste.candidates.value = emptyList()
    homeTaste.shelves.value = emptyList()
    homeTaste.profile = null
    homeTaste.isColdStart.value = true
    homeTaste.builderDismissed.value = false
    personalizedSections = emptyList()
    rebuildHomeFeedState()

    viewModelScope.launch {
        runCatching {
            settingsDataStore.setTasteSeedArtists("")
            settingsDataStore.setTasteDismissedArtists("")
            settingsDataStore.setTasteBuilderDismissed(false)
            settingsDataStore.setTasteResetAt(System.currentTimeMillis())
            advanceHomeShelfSeed()
            homeTaste.shelfCache.clear()
            homeTaste.playbackContextStore.clear()
        }
        refreshPersonalizedSections(force = true)
    }
}

internal fun GeneratedShelf.toHomeSection(): HomeSection = HomeSection(
    id = "shelf_$id",
    title = title,
    type = when (kind) {
        ShelfKind.LISTEN_AGAIN -> HomeSectionType.LISTEN_AGAIN
        ShelfKind.ALBUMS_FOR_YOU -> HomeSectionType.ALBUMS_FOR_YOU
        ShelfKind.SIMILAR_TO -> HomeSectionType.SIMILAR_TO
        ShelfKind.MY_MIX -> HomeSectionType.MY_MIX
        ShelfKind.BECAUSE_YOU_LISTENED -> HomeSectionType.BECAUSE_YOU_LISTENED
        ShelfKind.FORGOTTEN_FAVORITES -> HomeSectionType.FORGOTTEN_FAVORITES
        ShelfKind.TRENDING -> HomeSectionType.RECOMMENDATION
    },
    sourceType = HomeInteractionEntity.SOURCE_LOCAL,
    items = items.map { item ->
        when (item) {
            is ShelfItem.Track -> HomeFeedItem.Music(item.item)
            is ShelfItem.Collection -> HomeFeedItem.PlaylistResult(
                Playlist(
                    id = item.id,
                    title = item.title,
                    description = item.subtitle,
                    thumbnailUrl = item.thumbnailUrl,
                    items = emptyList()
                )
            )
        }
    },
    strapline = strapline,
    avatarUrl = avatarUrl,
    moreEndpoint = moreEndpoint,
    subtitle = subtitle,
    artistName = artistName,
    layout = when (layout) {
        ShelfLayout.LIST_COLUMNS -> HomeSectionLayout.LIST_COLUMNS
        ShelfLayout.GRID -> HomeSectionLayout.GRID
        ShelfLayout.CAROUSEL -> HomeSectionLayout.CAROUSEL
    }
)

private fun GeneratedShelf.orderIndex(): Int = when {
    id == "listen_again" -> 0
    id == "albums_for_you" -> 10
    id.startsWith("similar_") -> 20 + (id.substringAfterLast('_').toIntOrNull() ?: 0)
    id.startsWith("mix_") -> 40 + (id.substringAfterLast('_').toIntOrNull() ?: 0)
    id.startsWith("because_") -> 60
    id == "forgotten_favorites" -> 70
    else -> 90
}
