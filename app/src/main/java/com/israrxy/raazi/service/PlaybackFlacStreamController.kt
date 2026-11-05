package com.israrxy.raazi.service

import android.net.Uri
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.israrxy.raazi.model.PlaybackMediaMode
import com.israrxy.raazi.player.StreamResolver
import com.israrxy.raazi.player.flac.FlacResult
import java.io.File

/**
 * Switches the active track's playback stream to lossless FLAC seamlessly at the current position.
 */
fun MusicPlaybackService.switchCurrentTrackToFlac(flacResult: FlacResult): Boolean {
    if (!isPlayerInitialized() || isDestroyed) return false
    val currentTrack = getCurrentTrack() ?: return false
    val activeIndex = currentIndex
    if (activeIndex !in currentPlaylist.indices) return false

    val currentPos = getCurrentPosition()
    val wasPlaying = exoPlayer.playWhenReady

    // Update global stream quality state and active session mode
    StreamResolver.setCurrentStreamQuality(flacResult.qualityLabel)
    StreamResolver.sessionFlacEnabled = true

    // Audio-only playback for FLAC
    mediaMode = PlaybackMediaMode.AUDIO
    isVideoAvailable = false
    currentStreamHasVideo = false
    try {
        exoPlayer.clearVideoSurface()
    } catch (_: Exception) {}

    // Update track in current playlist with local FLAC file path
    val updatedTrack = currentTrack.copy(localPath = flacResult.localPath)
    val updatedList = currentPlaylist.toMutableList()
    updatedList[activeIndex] = updatedTrack
    currentPlaylist = updatedList

    val flacUri = if (flacResult.localPath.startsWith("http://") || flacResult.localPath.startsWith("https://")) {
        Uri.parse(flacResult.localPath)
    } else {
        Uri.fromFile(File(flacResult.localPath))
    }
    val newMediaItem = MediaItem.Builder()
        .setUri(flacUri)
        .setMediaId(updatedTrack.id)
        .setTag(updatedTrack)
        .build()

    transitionTargetPositionMs = currentPos
    transitionTrackId = updatedTrack.id

    exoPlayer.replaceMediaItem(activeIndex, newMediaItem)
    exoPlayer.seekTo(activeIndex, currentPos)
    if (exoPlayer.playbackState == Player.STATE_IDLE) {
        exoPlayer.prepare()
    }
    exoPlayer.playWhenReady = wasPlaying
    if (wasPlaying) {
        exoPlayer.play()
    }

    notifyPlaybackState(refreshNotification = true)
    updateMediaSessionState()
    Log.i(MusicPlaybackService.TAG, "Seamlessly switched track '${updatedTrack.title}' to FLAC stream (${flacResult.qualityLabel}) at ${currentPos}ms")
    return true
}

/**
 * Switches the active track's playback stream back to the standard YouTube stream seamlessly.
 */
fun MusicPlaybackService.switchCurrentTrackToStandard(): Boolean {
    if (!isPlayerInitialized() || isDestroyed) return false
    val currentTrack = getCurrentTrack() ?: return false
    val activeIndex = currentIndex
    if (activeIndex !in currentPlaylist.indices) return false

    val currentPos = getCurrentPosition()
    val wasPlaying = exoPlayer.playWhenReady

    StreamResolver.setCurrentStreamQuality(null)
    StreamResolver.sessionFlacEnabled = false

    // Clear localPath so it resolves standard stream through ResolvingDataSource
    val updatedTrack = currentTrack.copy(localPath = null)
    val updatedList = currentPlaylist.toMutableList()
    updatedList[activeIndex] = updatedTrack
    currentPlaylist = updatedList

    val standardMediaItem = createMediaItem(updatedTrack)

    transitionTargetPositionMs = currentPos
    transitionTrackId = updatedTrack.id

    exoPlayer.replaceMediaItem(activeIndex, standardMediaItem)
    exoPlayer.seekTo(activeIndex, currentPos)
    if (exoPlayer.playbackState == Player.STATE_IDLE) {
        exoPlayer.prepare()
    }
    exoPlayer.playWhenReady = wasPlaying
    if (wasPlaying) {
        exoPlayer.play()
    }

    notifyPlaybackState(refreshNotification = true)
    updateMediaSessionState()
    Log.i(MusicPlaybackService.TAG, "Seamlessly switched track '${updatedTrack.title}' back to standard stream at ${currentPos}ms")
    return true
}
