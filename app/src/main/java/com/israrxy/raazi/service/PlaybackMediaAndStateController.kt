package com.israrxy.raazi.service

import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import com.israrxy.raazi.model.MusicItem
import com.israrxy.raazi.model.PlaybackMediaMode
import com.israrxy.raazi.model.PlaybackState
import com.israrxy.raazi.model.PlaybackVideoQuality
import com.israrxy.raazi.player.StreamResolver
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

internal fun MusicPlaybackService.createPlayerListenerImpl() = object : Player.Listener {
    override fun onIsPlayingChanged(isPlaying: Boolean) {
        updateMediaSessionState()
        notifyPlaybackState(refreshNotification = true)
        if (isPlaying) startPositionUpdates() else stopPositionUpdates()
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        if (mediaItem != null) {
            currentIndex = exoPlayer.currentMediaItemIndex
            currentTrackRetryCount = 0
            val tag = mediaItem.localConfiguration?.tag
            if (tag is MusicItem) {
                val isSameTrack = tag.id == activeTrackId
                activeTrackId = tag.id
                if (!isSameTrack) {
                    currentStreamHasVideo = false
                    isVideoAvailable = false
                    transitionTargetPositionMs = null
                    transitionTrackId = null
                    currentAlbumArt = null
                    pendingNotificationTrackId = tag.id
                    loadAlbumArt(tag.thumbnailUrl)
                    notifyTrackChanged(tag)
                }
            }
            updateMediaSessionState()
            notifyPlaybackState(refreshNotification = true)
        }
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        if (playbackState == Player.STATE_READY) {
            currentTrackRetryCount = 0
            val currentTrackId = getCurrentTrack()?.id
            val dur = exoPlayer.duration
            if (dur > 0L) {
                lastKnownDurationMs = dur
                lastKnownDurationTrackId = currentTrackId
            }
            val targetPos = transitionTargetPositionMs?.takeIf { transitionTrackId == currentTrackId }
            if (targetPos != null) {
                transitionTargetPositionMs = null
                transitionTrackId = null
                val actualPos = exoPlayer.currentPosition.coerceAtLeast(0L)
                if (dur > 0L && targetPos >= dur - 1500L) {
                    exoPlayer.seekTo(0L)
                } else if (targetPos > 1500L && abs(actualPos - targetPos) > 2000L) {
                    exoPlayer.seekTo(targetPos)
                }
            }
            preloadAdjacentTracks(currentIndex)
        } else if (playbackState == Player.STATE_ENDED) {
            transitionTargetPositionMs = null
            transitionTrackId = null
            notifyPlaybackState()
        }
        updateMediaSessionState()
        notifyPlaybackState(refreshNotification = playbackState == Player.STATE_READY)
    }

    override fun onPositionDiscontinuity(
        oldPosition: Player.PositionInfo,
        newPosition: Player.PositionInfo,
        reason: Int
    ) {
        updateMediaSessionState()
        notifyPlaybackState()
    }

    override fun onPlayerError(error: PlaybackException) {
        Log.e(MusicPlaybackService.TAG, "ExoPlayer error: ${error.errorCodeName}", error)
        val currentTrack = getCurrentTrack()
        if (currentTrack != null) {
            StreamResolver.clearCacheForVideoId(currentTrack.id)
            if (currentTrack.videoUrl.isNotBlank()) {
                StreamResolver.clearCacheForVideoId(currentTrack.videoUrl)
            }
        }

        if (currentTrack != null && currentTrackRetryCount < 2) {
            currentTrackRetryCount++
            Log.w(MusicPlaybackService.TAG, "Retrying track '${currentTrack.title}' (attempt $currentTrackRetryCount/2)")
            serviceScope.launch {
                delay(300L)
                if (!isDestroyed && currentPlaylist.isNotEmpty()) {
                    val safeIndex = currentIndex.coerceIn(0, currentPlaylist.lastIndex)
                    val refreshedTrack = currentPlaylist[safeIndex].copy(audioUrl = "")
                    val updatedList = currentPlaylist.toMutableList()
                    updatedList[safeIndex] = refreshedTrack
                    currentPlaylist = updatedList

                    val mediaItem = createMediaItem(refreshedTrack)
                    if (safeIndex < exoPlayer.mediaItemCount) {
                        exoPlayer.replaceMediaItem(safeIndex, mediaItem)
                    } else {
                        val mediaItems = currentPlaylist.map { createMediaItem(it) }
                        exoPlayer.setMediaItems(mediaItems, safeIndex, 0L)
                    }
                    notifyPlaybackState(isLoading = true, refreshNotification = true)
                    exoPlayer.prepare()
                    exoPlayer.playWhenReady = true
                    exoPlayer.play()
                    requestAudioFocus()
                }
            }
            return
        }

        serviceScope.launch {
            delay(500L)
            if (currentPlaylist.size > 1 && currentIndex < currentPlaylist.lastIndex) {
                Log.w(MusicPlaybackService.TAG, "Auto-skipping to next track after error")
                next()
            } else {
                notifyPlaybackState()
            }
        }
    }
}

internal fun MusicPlaybackService.resumeImpl() {
    if (isDestroyed) return
    try {
        if (exoPlayer.mediaItemCount == 0 && currentPlaylist.isNotEmpty()) {
            val safeIndex = currentIndex.coerceIn(0, currentPlaylist.lastIndex)
            playExoPlayerPlaylist(currentPlaylist, safeIndex)
            return
        }
        when (exoPlayer.playbackState) {
            Player.STATE_IDLE -> {
                currentTrackRetryCount = 0
                notifyPlaybackState(isLoading = true, refreshNotification = true)
                exoPlayer.prepare()
            }
            Player.STATE_ENDED -> {
                val safeIndex = currentIndex.coerceIn(0, (exoPlayer.mediaItemCount - 1).coerceAtLeast(0))
                exoPlayer.seekTo(safeIndex, 0L)
            }
        }
        requestAudioFocus()
        exoPlayer.playWhenReady = true
        exoPlayer.play()
        notifyPlaybackState(refreshNotification = true)
    } catch (e: Exception) {
        Log.e(MusicPlaybackService.TAG, "Error resuming", e)
    }
}

internal fun MusicPlaybackService.getCurrentPositionImpl(): Long {
    val currentTrackId = getCurrentTrack()?.id
    val pendingPos = transitionTargetPositionMs?.takeIf { transitionTrackId != null && transitionTrackId == currentTrackId }
    val rawPos = try {
        if (isPlayerInitialized()) exoPlayer.currentPosition.coerceAtLeast(0L) else 0L
    } catch (_: Exception) { 0L }
    if (pendingPos != null) {
        val state = try { if (isPlayerInitialized()) exoPlayer.playbackState else Player.STATE_IDLE } catch (_: Exception) { Player.STATE_IDLE }
        if (state != Player.STATE_READY || (rawPos < 1000L && pendingPos > 1500L)) {
            return pendingPos
        }
    }
    return rawPos
}

internal fun MusicPlaybackService.getDurationImpl(): Long {
    val playerDur = try {
        if (isPlayerInitialized()) exoPlayer.duration else 0L
    } catch (_: Exception) { 0L }
    val activeTrack = getCurrentTrack()
    if (playerDur > 0L) {
        lastKnownDurationMs = playerDur
        lastKnownDurationTrackId = activeTrack?.id
        return playerDur
    }
    if (activeTrack != null && activeTrack.id == lastKnownDurationTrackId && lastKnownDurationMs > 0L) {
        return lastKnownDurationMs
    }
    return (activeTrack?.duration ?: 0L).coerceAtLeast(0L)
}

internal fun MusicPlaybackService.getCurrentTrackImpl(): MusicItem? {
    val exoTrack = try {
        if (isPlayerInitialized() && exoPlayer.mediaItemCount > 0) {
            exoPlayer.currentMediaItem?.localConfiguration?.tag as? MusicItem
        } else null
    } catch (_: Exception) { null }

    if (exoTrack != null) {
        if (currentPlaylist.getOrNull(currentIndex)?.id != exoTrack.id) {
            val foundIdx = currentPlaylist.indexOfFirst { it.id == exoTrack.id }
            if (foundIdx >= 0) currentIndex = foundIdx
        }
        return exoTrack
    }

    return if (currentPlaylist.isNotEmpty() && currentIndex in currentPlaylist.indices) {
        currentPlaylist[currentIndex]
    } else null
}

internal fun MusicPlaybackService.setPlaybackMediaModeImpl(mode: PlaybackMediaMode): Boolean {
    if (mediaMode == mode) {
        if (isPlayerInitialized() && !exoPlayer.isPlaying && currentPlaylist.isNotEmpty()) {
            resume()
        } else {
            notifyPlaybackState()
        }
        return true
    }
    mediaMode = mode
    currentTrackRetryCount = 0

    if (!isPlayerInitialized() || currentIndex !in currentPlaylist.indices) {
        notifyPlaybackState()
        return true
    }

    val playerReadyOrBuffering = exoPlayer.playbackState == Player.STATE_READY ||
        exoPlayer.playbackState == Player.STATE_BUFFERING
    if (currentStreamHasVideo && playerReadyOrBuffering) {
        if (mediaMode == PlaybackMediaMode.VIDEO) {
            isVideoAvailable = true
            if (videoSurface != null) exoPlayer.setVideoSurface(videoSurface)
        } else {
            isVideoAvailable = false
            exoPlayer.clearVideoSurface()
        }
        replaceUpcomingMediaItemsForCurrentMode()
        notifyPlaybackState(isLoading = false)
        return true
    }

    if (mediaMode == PlaybackMediaMode.VIDEO && videoSurface != null) {
        exoPlayer.setVideoSurface(videoSurface)
    } else if (mediaMode == PlaybackMediaMode.AUDIO) {
        isVideoAvailable = false
        exoPlayer.clearVideoSurface()
    }

    val activeTrack = getCurrentTrack()
    val rawPos = getCurrentPosition()
    val dur = getDuration()
    val safePos = if (exoPlayer.playbackState == Player.STATE_ENDED || (dur > 0L && rawPos >= dur - 1500L)) 0L else rawPos

    if (dur > 0L && activeTrack != null) {
        lastKnownDurationMs = dur
        lastKnownDurationTrackId = activeTrack.id
    }
    transitionTargetPositionMs = safePos
    transitionTrackId = activeTrack?.id
    val shouldPlay = exoPlayer.playWhenReady || exoPlayer.isPlaying || true

    notifyPlaybackState(isLoading = true)
    syncPlaylistQueue(currentIndex, safePos, shouldPlay = shouldPlay, forceRePrepare = true)
    return true
}

internal fun MusicPlaybackService.setPlaybackVideoQualityImpl(quality: PlaybackVideoQuality): Boolean {
    if (videoQuality == quality) {
        notifyPlaybackState()
        return true
    }
    videoQuality = quality
    currentTrackRetryCount = 0
    if (mediaMode == PlaybackMediaMode.VIDEO && currentIndex in currentPlaylist.indices && isPlayerInitialized()) {
        val activeTrack = getCurrentTrack()
        val currentPos = getCurrentPosition()
        val dur = getDuration()
        if (dur > 0L && activeTrack != null) {
            lastKnownDurationMs = dur
            lastKnownDurationTrackId = activeTrack.id
        }
        transitionTargetPositionMs = currentPos
        transitionTrackId = activeTrack?.id
        currentStreamHasVideo = false
        val isPlaying = exoPlayer.playWhenReady || exoPlayer.isPlaying
        notifyPlaybackState(isLoading = true)
        syncPlaylistQueue(currentIndex, currentPos, isPlaying, forceRePrepare = true)
    } else {
        notifyPlaybackState()
    }
    return true
}

internal fun MusicPlaybackService.getPlaybackStateImpl(): PlaybackState {
    val playing = try { if (isPlayerInitialized()) exoPlayer.isPlaying else false } catch (_: Exception) { false }
    val position = getCurrentPosition()
    val duration = getDuration()
    val buffering = try {
        if (isPlayerInitialized()) exoPlayer.playbackState == Player.STATE_BUFFERING else false
    } catch (_: Exception) { false }
    val speed = try {
        if (isPlayerInitialized()) exoPlayer.playbackParameters.speed else 1.0f
    } catch (_: Exception) { 1.0f }

    return PlaybackState(
        isPlaying = playing,
        currentPosition = position,
        duration = duration,
        currentTrack = getCurrentTrack(),
        playlist = currentPlaylist,
        currentIndex = currentIndex,
        isShuffleEnabled = isShuffleEnabled,
        repeatMode = repeatMode,
        isLoading = false,
        isBuffering = buffering,
        mediaMode = mediaMode,
        isVideoAvailable = isVideoAvailable,
        videoQuality = videoQuality,
        playbackSpeed = if (speed.isNaN() || speed.isInfinite()) 1.0f else speed
    )
}
