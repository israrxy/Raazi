package com.israrxy.raazi.service

import android.os.Bundle
import android.support.v4.media.session.MediaSessionCompat

/**
 * Builds the MediaSession used for lock-screen controls, media buttons and
 * Android 13+ system media UI. Extracted from [MusicPlaybackService] to keep the
 * service file focused on lifecycle + playback orchestration.
 */
internal fun MusicPlaybackService.createPlaybackMediaSession(): MediaSessionCompat =
    MediaSessionCompat(this, "RaaziMusicSession").apply {
        setCallback(object : MediaSessionCompat.Callback() {
            override fun onPlay() = resume()
            override fun onPause() = pause()
            override fun onSkipToNext() = next()
            override fun onSkipToPrevious() = previous()
            override fun onStop() = stop()
            override fun onSeekTo(pos: Long) = seekTo(pos)
            override fun onCustomAction(action: String?, extras: Bundle?) {
                when (action) {
                    MusicPlaybackService.ACTION_TOGGLE_SHUFFLE -> toggleShuffle()
                    MusicPlaybackService.ACTION_TOGGLE_REPEAT -> toggleRepeat()
                    MusicPlaybackService.ACTION_TOGGLE_FAVORITE -> toggleFavoriteFromNotificationImpl()
                    MusicPlaybackService.ACTION_CYCLE_SPEED -> cyclePlaybackSpeed()
                    MusicPlaybackService.ACTION_OPEN_BROWSER -> openLockScreenBrowser()
                }
            }
        })
        setFlags(
            MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
        )
        setSessionActivity(createContentPendingIntent())
        isActive = true
    }
