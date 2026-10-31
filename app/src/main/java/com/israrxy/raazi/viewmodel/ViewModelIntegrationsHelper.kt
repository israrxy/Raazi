package com.israrxy.raazi.viewmodel

import android.content.ComponentName
import android.content.ServiceConnection
import android.net.Uri
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import androidx.lifecycle.viewModelScope
import com.israrxy.raazi.model.MusicContentType
import com.israrxy.raazi.model.MusicItem
import com.israrxy.raazi.model.PlaybackState
import com.israrxy.raazi.service.MusicPlaybackService
import com.israrxy.raazi.service.RingtoneHelper
import com.israrxy.raazi.service.RingtoneType
import com.israrxy.raazi.service.SpotifyImportHelper
import com.israrxy.raazi.service.YouTubeMusicExtractor
import com.zionhuang.innertube.YouTube
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

internal fun MusicPlayerViewModel.createServiceConnection(): ServiceConnection = object : ServiceConnection {
    override fun onServiceConnected(className: ComponentName, service: IBinder) {
        try {
            val binder = service as MusicPlaybackService.MusicBinder
            playbackService = binder.getService()
            isServiceBound = true
            playbackService?.setPlaybackVideoQuality(videoQuality.value)

            val stateListener: (PlaybackState) -> Unit = { state ->
                val oldState = _playbackState.value
                _playbackState.value = state
                if (state.currentTrack?.id != oldState.currentTrack?.id) {
                    state.currentTrack?.let { track ->
                        addToHistoryInternal(track)
                        fetchLyricsImpl(track)
                        fetchAutoPlayQueue(track)
                    }
                }
            }
            playbackStateListener = stateListener
            playbackService?.addPlaybackStateListener(stateListener)

            val trackListener: (MusicItem) -> Unit = { track ->
                addToHistoryInternal(track)
                fetchRelatedAndArtistSongsForTrack(track)
            }
            trackChangedListener = trackListener
            playbackService?.addTrackChangedListener(trackListener)

            _playbackState.value = playbackService?.getPlaybackState() ?: PlaybackState()
            _playbackState.value.currentTrack?.let { initialTrack ->
                fetchLyricsImpl(initialTrack)
                fetchRelatedAndArtistSongsForTrack(initialTrack)
            }

            loadEqualizerState()
            loadCustomPresets()

            playbackService?.let { musicService ->
                val vizListener: (ByteArray) -> Unit = { data -> _visualizerData.value = data }
                visualizerListener = vizListener
                musicService.addVisualizerListener(vizListener)
            }

            processPendingPlayback()
        } catch (e: Exception) {
            Log.e("MusicVM", "Error in onServiceConnected", e)
        }
    }

    override fun onServiceDisconnected(arg0: ComponentName) {
        isServiceBound = false
        playbackService = null
    }
}

internal fun MusicPlayerViewModel.onClearedImpl() {
    try {
        if (isServiceBound) {
            playbackStateListener?.let { playbackService?.removePlaybackStateListener(it) }
            trackChangedListener?.let { playbackService?.removeTrackChangedListener(it) }
            visualizerListener?.let { playbackService?.removeVisualizerListener(it) }
            playbackStateListener = null
            trackChangedListener = null
            visualizerListener = null
            runCatching { app.unbindService(serviceConnection) }
            isServiceBound = false
        }
    } catch (e: Exception) {
        Log.e("MusicVM", "Error in onCleared", e)
    }
    runCatching { advancedDownloadManager.destroy() }
}

internal fun MusicPlayerViewModel.downloadForRingtoneImpl(track: MusicItem) {
    _ringtoneTrack.value = track
    _ringtoneState.value = MusicPlayerViewModel.RingtoneState.Downloading()
    try { playbackService?.pause() } catch (e: Exception) { Log.w("MusicVM", "pause before ringtone failed", e) }
    viewModelScope.launch(Dispatchers.IO) {
        try {
            val ringtoneHelper = RingtoneHelper(app)
            val extractor = YouTubeMusicExtractor.getInstance()
            val result = ringtoneHelper.downloadAudio(track, extractor) { progress ->
                _ringtoneState.value = MusicPlayerViewModel.RingtoneState.Downloading(progress)
            }
            _ringtoneState.value = MusicPlayerViewModel.RingtoneState.Ready(result.first, result.second)
        } catch (e: Exception) {
            Log.e("MusicVM", "Ringtone download failed", e)
            _ringtoneState.value = MusicPlayerViewModel.RingtoneState.Error("Download failed: ${e.message}")
        }
    }
}

internal fun MusicPlayerViewModel.setAsRingtoneImpl(filePath: String, startMs: Long, endMs: Long) {
    if (!Settings.System.canWrite(app)) {
        _ringtoneState.value = MusicPlayerViewModel.RingtoneState.NeedsPermission
        return
    }
    _ringtoneState.value = MusicPlayerViewModel.RingtoneState.Trimming
    viewModelScope.launch(Dispatchers.IO) {
        try {
            val ringtoneHelper = RingtoneHelper(app)
            _ringtoneState.value = MusicPlayerViewModel.RingtoneState.Setting
            val trackTitle = _ringtoneTrack.value?.title ?: "Raazi Ringtone"
            val type = _ringtoneType.value
            ringtoneHelper.trimAndSetRingtone(filePath, startMs, endMs, trackTitle, type)
            val typeLabel = when (type) {
                RingtoneType.RINGTONE -> "Ringtone"
                RingtoneType.NOTIFICATION -> "Notification"
                RingtoneType.ALARM -> "Alarm"
            }
            _ringtoneState.value = MusicPlayerViewModel.RingtoneState.Done("$typeLabel set successfully!")
        } catch (e: Exception) {
            Log.e("MusicVM", "Ringtone set failed", e)
            _ringtoneState.value = MusicPlayerViewModel.RingtoneState.Error("Failed to set ringtone: ${e.message}")
        }
    }
}

internal fun MusicPlayerViewModel.startSpotifyImportImpl(playlistUrl: String) {
    _spotifyImportState.value = MusicPlayerViewModel.SpotifyImportState.FetchingSpotify
    viewModelScope.launch(Dispatchers.IO) {
        try {
            val spotifyPlaylist = SpotifyImportHelper.fetchPlaylist(playlistUrl)
            _spotifyPlaylistName.value = spotifyPlaylist.title

            val tracksToResolve = spotifyPlaylist.tracks.map { track ->
                MusicPlayerViewModel.ResolvedSpotifyTrack(track, null, isResolving = true)
            }
            _resolvedSpotifyTracks.value = tracksToResolve
            _spotifyImportState.value = MusicPlayerViewModel.SpotifyImportState.ResolvingTracks(0, tracksToResolve.size)

            val semaphore = Semaphore(5)
            val total = tracksToResolve.size
            var completedCount = 0

            coroutineScope {
                tracksToResolve.mapIndexed { index, resolvedTrack ->
                    launch {
                        semaphore.withPermit {
                            try {
                                val cleanedTitle = SpotifyImportHelper.cleanTrackTitleForSearch(resolvedTrack.spotifyTrack.title)
                                val query = "$cleanedTitle ${resolvedTrack.spotifyTrack.artist}"

                                val searchResult = musicExtractor.searchMusic(query, 0)

                                val songs = searchResult.items.filter { it.contentType == MusicContentType.SONG }
                                val firstSong = songs.firstOrNull()
                                val bestMatch = if (firstSong != null && SpotifyImportHelper.checkRelaxedMatch(cleanedTitle, firstSong.title)) {
                                    firstSong
                                } else {
                                    val videos = searchResult.items.filter { it.contentType == MusicContentType.VIDEO }
                                    videos.firstOrNull() ?: firstSong ?: searchResult.items.firstOrNull()
                                }

                                updateResolvedTrack(index) {
                                    it.copy(resolvedItem = bestMatch, isResolving = false)
                                }
                            } catch (e: Exception) {
                                Log.e("MusicVM", "Error resolving track at index $index", e)
                                updateResolvedTrack(index) {
                                    it.copy(isResolving = false, isError = true)
                                }
                            } finally {
                                synchronized(this@startSpotifyImportImpl) {
                                    completedCount++
                                    _spotifyImportState.value = MusicPlayerViewModel.SpotifyImportState.ResolvingTracks(completedCount, total)
                                }
                            }
                        }
                    }
                }
            }

            _spotifyImportState.value = MusicPlayerViewModel.SpotifyImportState.Reviewing
        } catch (e: Exception) {
            Log.e("MusicVM", "Spotify import failed", e)
            _spotifyImportState.value = MusicPlayerViewModel.SpotifyImportState.Error(e.message ?: "Unknown error")
        }
    }
}

internal fun MusicPlayerViewModel.updateResolvedTrack(
    index: Int,
    transform: (MusicPlayerViewModel.ResolvedSpotifyTrack) -> MusicPlayerViewModel.ResolvedSpotifyTrack
) {
    val currentList = _resolvedSpotifyTracks.value.toMutableList()
    if (index in currentList.indices) {
        currentList[index] = transform(currentList[index])
        _resolvedSpotifyTracks.value = currentList
    }
}

internal fun MusicPlayerViewModel.finalizeSpotifyImportImpl(playlistTitle: String, syncedToYouTube: Boolean) {
    _spotifyImportState.value = MusicPlayerViewModel.SpotifyImportState.Importing
    viewModelScope.launch {
        try {
            val playlistEntity = repository.createPlaylist(playlistTitle, syncedToYouTube = syncedToYouTube)
            val resolvedItems = _resolvedSpotifyTracks.value.mapNotNull { it.resolvedItem }

            if (resolvedItems.isNotEmpty()) {
                repository.addToPlaylist(resolvedItems, playlistEntity)
            }

            _spotifyImportState.value = MusicPlayerViewModel.SpotifyImportState.Success(playlistEntity.id)
        } catch (e: Exception) {
            Log.e("MusicVM", "Error finalizing import", e)
            _spotifyImportState.value = MusicPlayerViewModel.SpotifyImportState.Error(e.message ?: "Failed to save playlist.")
        }
    }
}

internal fun MusicPlayerViewModel.handleDeepLinkImpl(uri: Uri) {
    val scheme = uri.scheme?.lowercase().orEmpty()
    val host = uri.host?.lowercase().orEmpty()
    val pathSegments = uri.pathSegments.orEmpty().map { it.trim() }.filter { it.isNotEmpty() }

    val routeType = if (scheme == "raazi") {
        host
    } else {
        pathSegments.firstOrNull()?.lowercase().orEmpty()
    }

    val secondSegment = if (scheme == "raazi") {
        pathSegments.firstOrNull()
    } else {
        pathSegments.getOrNull(1)
    }

    when (routeType) {
        "playlist", "album" -> {
            val rawPlaylistId = uri.getQueryParameter("list")
                ?: uri.getQueryParameter("id")
                ?: secondSegment
            val cleanId = rawPlaylistId?.trim()?.removePrefix("VL")
            if (!cleanId.isNullOrBlank()) {
                _pendingDeepLinkEvent.value = MusicPlayerViewModel.DeepLinkEvent.OpenPlaylist(cleanId)
                return
            }
        }
        "artist" -> {
            val rawArtistId = uri.getQueryParameter("id")
                ?: uri.getQueryParameter("channelId")
                ?: secondSegment
            val artistName = uri.getQueryParameter("name")
            if (!rawArtistId.isNullOrBlank()) {
                _pendingDeepLinkEvent.value = MusicPlayerViewModel.DeepLinkEvent.OpenArtist(rawArtistId.trim(), artistName)
                return
            }
        }
    }

    val videoId = uri.getQueryParameter("id")
        ?: uri.getQueryParameter("video")
        ?: uri.getQueryParameter("v")
        ?: (if (routeType == "song" || routeType == "play" || routeType == "watch") secondSegment else null)

    if (videoId.isNullOrBlank()) {
        val fallbackListId = uri.getQueryParameter("list")?.trim()?.removePrefix("VL")
        if (!fallbackListId.isNullOrBlank()) {
            _pendingDeepLinkEvent.value = MusicPlayerViewModel.DeepLinkEvent.OpenPlaylist(fallbackListId)
        }
        return
    }

    val cleanVideoId = videoId.trim()
    viewModelScope.launch {
        try {
            val songs = YouTube.queue(
                videoIds = listOf(cleanVideoId)
            ).getOrNull()
            val item = songs?.firstOrNull()
            val musicItem = if (item != null) {
                MusicItem(
                    id = item.id,
                    title = item.title,
                    artist = item.artists.joinToString(", ") { it.name },
                    artistId = item.artists.firstOrNull()?.id,
                    duration = (item.duration?.toLong() ?: 0) * 1000L,
                    thumbnailUrl = item.thumbnail.ifBlank { "https://i.ytimg.com/vi/${item.id}/hqdefault.jpg" },
                    audioUrl = "",
                    videoUrl = item.id,
                    isLive = false,
                    contentType = MusicContentType.SONG,
                    setVideoId = item.setVideoId
                )
            } else {
                MusicItem(
                    id = cleanVideoId,
                    title = uri.getQueryParameter("title")?.takeIf { it.isNotBlank() } ?: "Loading track...",
                    artist = uri.getQueryParameter("artist")?.takeIf { it.isNotBlank() } ?: "YouTube Music",
                    duration = 0L,
                    thumbnailUrl = "https://i.ytimg.com/vi/$cleanVideoId/hqdefault.jpg",
                    audioUrl = "",
                    videoUrl = cleanVideoId,
                    isLive = false,
                    contentType = MusicContentType.SONG
                )
            }
            playMusic(musicItem)
            _pendingDeepLinkEvent.value = MusicPlayerViewModel.DeepLinkEvent.ExpandPlayer
        } catch (e: Exception) {
            Log.e("MusicVM", "Error handling deep link: $cleanVideoId", e)
        }
    }
}
