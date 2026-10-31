package com.israrxy.raazi.service

import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.Player
import com.israrxy.raazi.model.MusicItem
import com.israrxy.raazi.model.RepeatMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal fun MusicPlaybackService.toggleShuffleImpl() {
    if (isDestroyed) return
    try {
        val currentTrack = currentPlaylist.getOrNull(currentIndex) ?: getCurrentTrack()
        val currentPosition = try {
            if (isPlayerInitialized()) exoPlayer.currentPosition.coerceAtLeast(0L) else 0L
        } catch (_: Exception) { 0L }
        val shouldKeepPlaying = try {
            if (isPlayerInitialized()) exoPlayer.playWhenReady else false
        } catch (_: Exception) { false }
        isShuffleEnabled = !isShuffleEnabled
        if (currentPlaylist.size > 1) {
            if (isShuffleEnabled) {
                val remaining = originalPlaylist.filter { it.id != currentTrack?.id }.shuffled()
                currentPlaylist = if (currentTrack != null) listOf(currentTrack) + remaining else remaining
                currentIndex = 0
            } else {
                currentPlaylist = originalPlaylist
                currentIndex = currentPlaylist.indexOfFirst { it.id == currentTrack?.id }.coerceAtLeast(0)
            }
            reorderQueueInPlace(
                targetIndex = currentIndex,
                targetPositionMs = currentPosition,
                shouldPlay = shouldKeepPlaying
            )
        } else if (isShuffleEnabled && currentTrack != null) {
            val targetId = currentTrack.id
            serviceScope.launch(Dispatchers.IO) {
                try {
                    val upNext = YouTubeMusicExtractor.getInstance()
                        .getUpNext(targetId)
                        .filter { it.id.isNotBlank() && it.id != targetId }
                        .distinctBy { it.id }
                        .shuffled()
                    if (upNext.isNotEmpty()) {
                        withContext(Dispatchers.Main) {
                            if (!isDestroyed && getCurrentTrack()?.id == targetId && currentPlaylist.size <= 1) {
                                addToQueue(upNext)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w(MusicPlaybackService.TAG, "Shuffle radio populate failed: ${e.message}")
                }
            }
        }
        notifyPlaybackState(refreshNotification = true)
    } catch (e: Exception) {
        Log.e(MusicPlaybackService.TAG, "Error toggling shuffle", e)
    }
}

internal fun MusicPlaybackService.reorderQueueInPlace(
    targetIndex: Int,
    targetPositionMs: Long,
    shouldPlay: Boolean
) {
    if (!isPlayerInitialized()
        || exoPlayer.playbackState == Player.STATE_IDLE
        || exoPlayer.mediaItemCount != currentPlaylist.size
    ) {
        syncPlaylistQueue(targetIndex, targetPositionMs, shouldPlay)
        return
    }
    val working = (0 until exoPlayer.mediaItemCount).mapNotNull { i ->
        (exoPlayer.getMediaItemAt(i).localConfiguration?.tag as? MusicItem)?.id
    }.toMutableList()
    if (working.size != currentPlaylist.size) {
        syncPlaylistQueue(targetIndex, targetPositionMs, shouldPlay)
        return
    }
    val desired = currentPlaylist.map { it.id }
    for (dest in desired.indices) {
        val at = working.indexOf(desired[dest])
        if (at != dest && at in 0 until exoPlayer.mediaItemCount) {
            exoPlayer.moveMediaItem(at, dest)
            working.removeAt(at)
            working.add(dest, desired[dest])
        }
    }
    exoPlayer.playWhenReady = shouldPlay
    if (shouldPlay) requestAudioFocus()
    preloadAdjacentTracks(targetIndex)
}

internal fun MusicPlaybackService.applyRepeatModeToPlayer() {
    if (!isPlayerInitialized()) return
    exoPlayer.repeatMode = when (repeatMode) {
        RepeatMode.ONE -> Player.REPEAT_MODE_ONE
        RepeatMode.ALL -> Player.REPEAT_MODE_ALL
        else -> Player.REPEAT_MODE_OFF
    }
}

internal fun MusicPlaybackService.playExoPlayerPlaylist(playlist: List<MusicItem>, startIndex: Int) {
    try {
        if (playlist.isEmpty()) return
        val safeStartIndex = startIndex.coerceIn(0, playlist.lastIndex)
        Log.d(MusicPlaybackService.TAG, "Setting playlist of ${playlist.size} items, starting at $safeStartIndex")
        currentTrackRetryCount = 0
        currentIndex = safeStartIndex
        currentStreamHasVideo = false
        isVideoAvailable = false
        transitionTargetPositionMs = null
        transitionTrackId = null

        exoPlayer.stop()
        exoPlayer.clearMediaItems()

        val mediaItems = playlist.map { createMediaItem(it) }
        exoPlayer.setMediaItems(mediaItems, safeStartIndex, C.TIME_UNSET)

        applyRepeatModeToPlayer()
        requestAudioFocus()
        exoPlayer.playWhenReady = true
        exoPlayer.prepare()
        exoPlayer.play()

        notifyPlaybackState(isLoading = true, refreshNotification = true)
        startForegroundService()
    } catch (e: Exception) {
        Log.e(MusicPlaybackService.TAG, "Error playing playlist", e)
    }
}

internal fun MusicPlaybackService.nextImpl() {
    if (currentPlaylist.isEmpty()) return

    val targetIndex = when {
        currentIndex < currentPlaylist.lastIndex -> currentIndex + 1
        repeatMode == RepeatMode.ALL && currentPlaylist.size > 1 -> 0
        else -> null
    }

    if (targetIndex != null) {
        skipToPlaylistIndex(targetIndex)
        return
    }

    val currentTrack = getCurrentTrack() ?: return
    notifyPlaybackState(isLoading = true, refreshNotification = false)
    serviceScope.launch(Dispatchers.IO) {
        try {
            val upNext = YouTubeMusicExtractor.getInstance()
                .getUpNext(currentTrack.id)
                .filter { it.id.isNotBlank() && it.id != currentTrack.id }
                .distinctBy { it.id }
            if (upNext.isNotEmpty()) {
                withContext(Dispatchers.Main) {
                    if (!isDestroyed && getCurrentTrack()?.id == currentTrack.id) {
                        val tracksToAdd = if (isShuffleEnabled) upNext.shuffled() else upNext
                        val nextPos = currentIndex + 1
                        addToQueue(tracksToAdd)
                        if (nextPos in currentPlaylist.indices) {
                            skipToPlaylistIndex(nextPos)
                        }
                    }
                }
            } else {
                withContext(Dispatchers.Main) {
                    notifyPlaybackState(isLoading = false, refreshNotification = false)
                }
            }
        } catch (e: Exception) {
            Log.w(MusicPlaybackService.TAG, "Next track radio fetch failed: ${e.message}")
            withContext(Dispatchers.Main) {
                notifyPlaybackState(isLoading = false, refreshNotification = false)
            }
        }
    }
}

internal fun MusicPlaybackService.previousImpl() {
    val currentPos = try {
        if (isPlayerInitialized()) exoPlayer.currentPosition else 0L
    } catch (_: Exception) { 0L }
    if (currentPos > 3000) {
        try { exoPlayer.seekTo(0) } catch (_: Exception) {}
        notifyPlaybackState()
        return
    }

    val targetIndex = when {
        currentPlaylist.isEmpty() -> null
        currentIndex > 0 -> currentIndex - 1
        repeatMode == RepeatMode.ALL && currentPlaylist.isNotEmpty() -> currentPlaylist.lastIndex
        else -> null
    } ?: return

    skipToPlaylistIndex(targetIndex)
}

internal fun MusicPlaybackService.replaceUpcomingMediaItemsForCurrentMode() {
    if (!isPlayerInitialized()) return
    val count = exoPlayer.mediaItemCount
    if (count != currentPlaylist.size) return
    for (i in currentPlaylist.indices) {
        if (i != currentIndex) {
            try {
                exoPlayer.replaceMediaItem(i, createMediaItem(currentPlaylist[i]))
            } catch (_: Exception) {}
        }
    }
}

internal fun MusicPlaybackService.syncPlaylistQueue(
    targetIndex: Int,
    targetPositionMs: Long = 0L,
    shouldPlay: Boolean = exoPlayer.playWhenReady,
    forceRePrepare: Boolean = false
) {
    if (targetIndex !in currentPlaylist.indices) return

    val mediaItems = currentPlaylist.map { createMediaItem(it) }
    val wasIdle = exoPlayer.playbackState == Player.STATE_IDLE
    exoPlayer.setMediaItems(mediaItems, targetIndex, targetPositionMs)
    if (wasIdle || forceRePrepare) {
        if (exoPlayer.playbackState == Player.STATE_IDLE) {
            exoPlayer.prepare()
        }
    }
    applyRepeatModeToPlayer()
    exoPlayer.playWhenReady = shouldPlay
    if (shouldPlay) {
        requestAudioFocus()
        exoPlayer.play()
    }
    preloadAdjacentTracks(targetIndex)
}

internal fun MusicPlaybackService.skipToPlaylistIndex(targetIndex: Int) {
    if (targetIndex !in currentPlaylist.indices) return

    currentStreamHasVideo = false
    isVideoAvailable = false
    transitionTargetPositionMs = null
    transitionTrackId = null

    if (exoPlayer.mediaItemCount != currentPlaylist.size) {
        currentIndex = targetIndex
        playExoPlayerPlaylist(currentPlaylist, targetIndex)
        return
    }

    currentIndex = targetIndex
    currentTrackRetryCount = 0
    currentAlbumArt = null
    pendingNotificationTrackId = currentPlaylist[targetIndex].id
    updateMediaSessionState()
    notifyPlaybackState(isLoading = true, refreshNotification = true)
    exoPlayer.seekToDefaultPosition(targetIndex)
    if (exoPlayer.playbackState == Player.STATE_IDLE) {
        exoPlayer.prepare()
    }
    requestAudioFocus()
    exoPlayer.playWhenReady = true
    exoPlayer.play()
}

internal fun MusicPlaybackService.addToQueueImpl(items: List<MusicItem>) {
    if (isDestroyed || items.isEmpty()) return
    val newItems = items.filter { item -> currentPlaylist.none { it.id == item.id } }
    if (newItems.isEmpty()) return

    currentPlaylist = currentPlaylist + newItems
    originalPlaylist = originalPlaylist + newItems

    val exoItems = newItems.map { createMediaItem(it) }
    exoPlayer.addMediaItems(exoItems)

    notifyPlaybackState(refreshNotification = false)
}

internal fun MusicPlaybackService.playNextImpl(items: List<MusicItem>) {
    if (isDestroyed || items.isEmpty()) return
    val newItems = items.filter { item -> currentPlaylist.none { it.id == item.id } }
    if (newItems.isEmpty()) return

    val insertIndex = (currentIndex + 1).coerceIn(0, currentPlaylist.size)

    val head = currentPlaylist.take(insertIndex)
    val tail = currentPlaylist.drop(insertIndex)
    currentPlaylist = head + newItems + tail
    originalPlaylist = originalPlaylist + newItems

    val exoItems = newItems.map { createMediaItem(it) }
    exoPlayer.addMediaItems(insertIndex, exoItems)

    notifyPlaybackState(refreshNotification = false)
}

internal fun MusicPlaybackService.removeFromQueueImpl(absoluteIndex: Int) {
    if (isDestroyed) return
    if (absoluteIndex <= currentIndex || absoluteIndex !in currentPlaylist.indices) return
    val removed = currentPlaylist.getOrNull(absoluteIndex) ?: return
    currentPlaylist = currentPlaylist - removed
    originalPlaylist = originalPlaylist - removed
    try { exoPlayer.removeMediaItem(absoluteIndex) } catch (_: Exception) { }
    notifyPlaybackState(refreshNotification = true)
}

internal fun MusicPlaybackService.moveQueueItemImpl(fromAbsolute: Int, toAbsolute: Int) {
    if (isDestroyed) return
    val f = fromAbsolute.coerceIn(0, currentPlaylist.lastIndex)
    val t = toAbsolute.coerceIn(0, currentPlaylist.lastIndex)
    if (f == t || f == currentIndex || t == currentIndex) return
    val moved = currentPlaylist[f]
    currentPlaylist = currentPlaylist.toMutableList().apply {
        add(t, removeAt(f))
    }
    originalPlaylist = currentPlaylist
    try { exoPlayer.moveMediaItem(f, t) } catch (_: Exception) { }
    notifyPlaybackState(refreshNotification = true)
}

internal fun MusicPlaybackService.clearUpcomingImpl() {
    if (isDestroyed) return
    val keep = (currentIndex + 1).coerceAtLeast(0)
    if (keep >= currentPlaylist.size) return
    repeat(currentPlaylist.size - keep) {
        try { exoPlayer.removeMediaItem(keep) } catch (_: Exception) { }
    }
    currentPlaylist = currentPlaylist.take(keep)
    originalPlaylist = currentPlaylist
    notifyPlaybackState(refreshNotification = true)
}

internal fun MusicPlaybackService.setQueueOrderImpl(newPlaylist: List<MusicItem>) {
    if (isDestroyed || newPlaylist.isEmpty()) return
    val currentTrackId = getCurrentTrack()?.id
    currentPlaylist = newPlaylist
    originalPlaylist = newPlaylist
    if (currentTrackId != null) {
        val updatedIndex = newPlaylist.indexOfFirst { it.id == currentTrackId }
        currentIndex = if (updatedIndex >= 0) {
            updatedIndex
        } else {
            currentIndex.coerceIn(0, newPlaylist.lastIndex)
        }
    } else {
        currentIndex = currentIndex.coerceIn(0, newPlaylist.lastIndex)
    }
    val isPlaying = try { exoPlayer.isPlaying } catch (_: Exception) { false }
    val pos = try { exoPlayer.currentPosition } catch (_: Exception) { 0L }
    syncPlaylistQueue(currentIndex, pos, isPlaying)
    notifyPlaybackState(refreshNotification = false)
}
