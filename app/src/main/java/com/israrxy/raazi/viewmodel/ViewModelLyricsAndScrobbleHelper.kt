package com.israrxy.raazi.viewmodel

import android.util.Log
import androidx.lifecycle.viewModelScope
import com.israrxy.raazi.data.lyrics.LyricsScriptFilter
import com.israrxy.raazi.data.remote.LyricsSearchResult
import com.israrxy.raazi.model.MusicItem
import com.israrxy.raazi.model.PlaybackState
import com.israrxy.raazi.service.LastFmScrobbler
import com.zionhuang.innertube.YouTube
import com.zionhuang.innertube.models.WatchEndpoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

internal fun MusicPlayerViewModel.fetchLyricsImpl(track: MusicItem, forceRefresh: Boolean = false) {
    val state = playbackState.value
    val effectiveDuration = if (track.duration > 0L) track.duration else state.duration
    val resolvedTrack = if (effectiveDuration > 0L && track.duration != effectiveDuration) {
        track.copy(duration = effectiveDuration)
    } else {
        track
    }
    lyricsController.load(resolvedTrack, forceRefresh = forceRefresh)
}

internal fun MusicPlayerViewModel.fetchSongCreditsImpl(track: MusicItem) {
    songCreditsFetchJob?.cancel()
    songCreditsFetchJob = viewModelScope.launch(Dispatchers.IO) {
        try {
            _isSongCreditsLoading.value = true
            _songCredits.value = null
            val endpoint = WatchEndpoint(videoId = track.id)
            val nextResult = YouTube.next(endpoint).getOrNull()
            val desc = nextResult?.description
            if (!desc.isNullOrBlank()) {
                _songCredits.value = desc
            }
        } catch (e: Exception) {
            Log.w("MusicVM", "fetchSongCredits failed", e)
        } finally {
            _isSongCreditsLoading.value = false
        }
    }
}

internal fun MusicPlayerViewModel.retryLyricsFetchImpl() {
    lyricsController.retry(forceRefresh = true)
}

internal fun MusicPlayerViewModel.searchLyricsOptionsImpl(
    titleOverride: String? = null,
    artistOverride: String? = null,
    force: Boolean = false
) {
    lyricsController.searchOptions(titleOverride, artistOverride, force = force)
}

internal fun MusicPlayerViewModel.saveLyricsSelectionImpl(result: LyricsSearchResult) {
    lyricsController.selectResult(result)
}

internal fun MusicPlayerViewModel.clearSavedLyricsSelectionImpl() {
    lyricsController.clearSavedSelection()
}

internal fun MusicPlayerViewModel.setLyricsScriptFilterImpl(filter: LyricsScriptFilter) {
    lyricsController.setScriptFilter(filter)
}

internal fun MusicPlayerViewModel.initLastFmScrobbling() {
    viewModelScope.launch {
        settingsDataStore.lastfmScrobbleEnabled.collect { enabled ->
            lastFmEnabled = enabled
            if (!enabled) {
                LastFmScrobbler.sessionKey = null
                lastFmTrackId = null
                lastFmNowPlayingSent = false
                scrobbledTrackIds.clear()
                _lastfmStatus.value = null
            }
        }
    }

    viewModelScope.launch {
        settingsDataStore.lastfmSessionKey.collect { key ->
            LastFmScrobbler.sessionKey = key
            if (key.isNullOrBlank()) {
                scrobbledTrackIds.clear()
                lastFmTrackId = null
                lastFmNowPlayingSent = false
                _lastfmStatus.value = null
            }
        }
    }

    viewModelScope.launch {
        playbackState.collect { state ->
            handleLastFmPlayback(state)
        }
    }
}

private fun MusicPlayerViewModel.handleLastFmPlayback(state: PlaybackState) {
    if (!lastFmEnabled) return
    val key = LastFmScrobbler.sessionKey
    if (key.isNullOrBlank()) return
    val track = state.currentTrack ?: return

    if (track.id != lastFmTrackId) {
        lastFmTrackId = track.id
        lastFmNowPlayingSent = false
        scrobbledTrackIds.remove(track.id)
    }

    if (!state.isPlaying) return

    if (!lastFmNowPlayingSent) {
        lastFmNowPlayingSent = true
        sendLastFmNowPlaying(track)
    }

    if (!scrobbledTrackIds.contains(track.id)) {
        val durationMs = if (state.duration > 0) state.duration else track.duration
        if (durationMs > 0 && state.currentPosition >= scrobbleThresholdMs(durationMs)) {
            scrobbleLastFm(track)
        }
    }
}

private fun scrobbleThresholdMs(durationMs: Long): Long {
    val half = durationMs / 2
    val fourMinutes = 4 * 60 * 1000L
    return minOf(half, fourMinutes)
}

private fun MusicPlayerViewModel.sendLastFmNowPlaying(track: MusicItem) {
    viewModelScope.launch(Dispatchers.IO) {
        try {
            val ok = LastFmScrobbler.updateNowPlaying(track)
            _lastfmStatus.value = if (ok) {
                "Now playing: ${track.title}"
            } else {
                "Last.fm error: now playing failed"
            }
        } catch (e: Exception) {
            Log.w("MusicVM", "Last.fm now playing failed", e)
            _lastfmStatus.value = "Last.fm error: ${e.message ?: "unknown"}"
        }
    }
}

private fun MusicPlayerViewModel.scrobbleLastFm(track: MusicItem) {
    scrobbledTrackIds.add(track.id)
    viewModelScope.launch(Dispatchers.IO) {
        try {
            val ok = LastFmScrobbler.scrobble(track)
            if (ok) {
                _lastfmStatus.value = "Scrobbled: ${track.title}"
            } else {
                scrobbledTrackIds.remove(track.id)
                _lastfmStatus.value = "Last.fm error: scrobble failed"
            }
        } catch (e: Exception) {
            Log.w("MusicVM", "Last.fm scrobble failed", e)
            scrobbledTrackIds.remove(track.id)
            _lastfmStatus.value = "Last.fm error: ${e.message ?: "unknown"}"
        }
    }
}

internal suspend fun MusicPlayerViewModel.connectLastFmImpl(username: String, password: String): Boolean {
    return try {
        val result = LastFmScrobbler.getMobileSession(username, password)
        result.fold(
            onSuccess = { (key, name) ->
                settingsDataStore.setLastfmSession(key, name)
                LastFmScrobbler.sessionKey = key
                _lastfmMessage.value = "Connected to Last.fm as $name"
                true
            },
            onFailure = { e ->
                _lastfmMessage.value = "Last.fm: ${e.message ?: "connection failed"}"
                false
            }
        )
    } catch (e: Exception) {
        Log.e("MusicVM", "Last.fm connect failed", e)
        _lastfmMessage.value = "Last.fm: ${e.message ?: "connection failed"}"
        false
    }
}

internal suspend fun MusicPlayerViewModel.authenticateLastFmTokenImpl(token: String): Boolean {
    return try {
        val result = LastFmScrobbler.getSessionFromToken(token)
        result.fold(
            onSuccess = { (key, name) ->
                settingsDataStore.setLastfmSession(key, name)
                settingsDataStore.setLastfmScrobbleEnabled(true)
                LastFmScrobbler.sessionKey = key
                _lastfmMessage.value = "Connected to Last.fm as $name"
                true
            },
            onFailure = { e ->
                _lastfmMessage.value = "Last.fm: ${e.message ?: "authentication failed"}"
                false
            }
        )
    } catch (e: Exception) {
        Log.e("MusicVM", "Last.fm token exchange failed", e)
        _lastfmMessage.value = "Last.fm: ${e.message ?: "authentication failed"}"
        false
    }
}
