package com.israrxy.raazi.data.repository

import com.israrxy.raazi.data.lyrics.LyricsScriptFilter
import com.israrxy.raazi.data.remote.Lyrics
import com.israrxy.raazi.data.remote.LyricsSearchResult
import com.israrxy.raazi.data.remote.MultiSourceLyricsAggregator
import com.israrxy.raazi.model.MusicItem
import kotlinx.coroutines.flow.first

internal suspend fun MusicRepository.getLyricsImpl(
    item: MusicItem,
    preferredScript: LyricsScriptFilter = LyricsScriptFilter.ALL,
    forceRefresh: Boolean = false
): Lyrics? {
    val syncPriority = settingsDataStore.lyricsSyncPriority.first()
    if (!forceRefresh) {
        savedLyricsStore.get(item.id)?.let { cached ->
            val isCachedWordSynced = MultiSourceLyricsAggregator.hasWordSync(cached.syncedLyrics)
            if (syncPriority != "WORD_SYNC_FIRST" || isCachedWordSynced) {
                return cached
            }
        }
    }
    val fetched = lyricsAggregator.getLyrics(
        item.id,
        item.title,
        item.artist,
        item.duration,
        preferredScript,
        forceRefresh,
        syncPriority
    )
    if (fetched != null && (!fetched.syncedLyrics.isNullOrBlank() || !fetched.plainLyrics.isNullOrBlank())) {
        savedLyricsStore.put(item.id, fetched)
    }
    return fetched
}

internal suspend fun MusicRepository.searchLyricsOptionsImpl(
    item: MusicItem,
    titleOverride: String = item.title,
    artistOverride: String = item.artist,
    preferredScript: LyricsScriptFilter = LyricsScriptFilter.ALL
): List<LyricsSearchResult> {
    val syncPriority = settingsDataStore.lyricsSyncPriority.first()
    return lyricsAggregator.searchAllProviders(
        trackId = item.id,
        trackName = titleOverride,
        artistName = artistOverride,
        durationMs = item.duration,
        preferredScript = preferredScript,
        syncPriority = syncPriority
    )
}
