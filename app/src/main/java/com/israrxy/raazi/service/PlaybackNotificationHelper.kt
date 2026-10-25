package com.israrxy.raazi.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.media.app.NotificationCompat.MediaStyle
import coil.imageLoader
import coil.request.ImageRequest
import com.israrxy.raazi.MainActivity
import com.israrxy.raazi.R
import com.israrxy.raazi.model.PlaybackState
import com.israrxy.raazi.model.RepeatMode
import com.israrxy.raazi.widget.NowPlayingWidget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal fun MusicPlaybackService.toggleFavoriteFromNotificationImpl() {
    val track = getCurrentTrack() ?: return
    val trackId = track.id
    val nowFavorite = !favoriteTrackIds.contains(trackId)
    if (nowFavorite) favoriteTrackIds.add(trackId) else favoriteTrackIds.remove(trackId)
    sendLikeEventBroadcast(trackId, nowFavorite)
    notifyPlaybackState(refreshNotification = true)
}

internal fun MusicPlaybackService.sendLikeEventBroadcast(trackId: String, nowFavorite: Boolean) {
    val intent = Intent(MusicPlaybackService.NOTIFICATION_LIKE_CHANNEL).apply {
        putExtra(MusicPlaybackService.EXTRA_TRACK_ID, trackId)
        putExtra("is_favorite", nowFavorite)
        setPackage(packageName)
    }
    sendBroadcast(intent)
}

internal fun MusicPlaybackService.createNotificationChannel() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val channel = NotificationChannel(
            notificationChannelId,
            "Music Playback",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Controls for music playback"
            setShowBadge(false)
        }
        val notificationManager = getSystemService(Service.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }
}

internal fun MusicPlaybackService.startForegroundService() {
    try {
        val notification = createNotification(getPlaybackState())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                notificationId,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(notificationId, notification)
        }
    } catch (e: Exception) {
        Log.w(MusicPlaybackService.TAG, "Could not start foreground service: ${e.message}")
    }
}

internal fun MusicPlaybackService.createContentPendingIntent(): PendingIntent {
    val contentIntent = Intent(this, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }
    return PendingIntent.getActivity(
        this,
        0,
        contentIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}

internal fun MusicPlaybackService.createNotification(playbackState: PlaybackState): Notification {
    val contentPendingIntent = createContentPendingIntent()

    val currentTrack = playbackState.currentTrack
    val title = currentTrack?.title ?: "No Track"
    val artist = currentTrack?.artist ?: "Unknown Artist"
    val isPlaying = playbackState.isPlaying
    val largeIcon = currentAlbumArt ?: BitmapFactory.decodeResource(resources, R.mipmap.ic_launcher)

    val isFav = isTrackFavorite(currentTrack?.id)

    fun servicePending(action: String, requestCode: Int): PendingIntent {
        val intent = Intent(this, MusicPlaybackService::class.java).apply { this.action = action }
        return PendingIntent.getService(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    val shufflePending = servicePending(MusicPlaybackService.ACTION_TOGGLE_SHUFFLE, 10)
    val prevPending = servicePending(MusicPlaybackService.ACTION_PREV, 11)
    val playPausePending = servicePending(
        if (isPlaying) MusicPlaybackService.ACTION_PAUSE else MusicPlaybackService.ACTION_PLAY,
        12
    )
    val nextPending = servicePending(MusicPlaybackService.ACTION_NEXT, 13)
    val repeatPending = servicePending(MusicPlaybackService.ACTION_TOGGLE_REPEAT, 14)
    val favoritePending = servicePending(MusicPlaybackService.ACTION_TOGGLE_FAVORITE, 15)
    val speedPending = servicePending(MusicPlaybackService.ACTION_CYCLE_SPEED, 16)
    val stopPending = servicePending(MusicPlaybackService.ACTION_STOP, 17)

    val shuffleIcon = if (playbackState.isShuffleEnabled) R.drawable.ic_shuffle_active else R.drawable.ic_shuffle
    val shuffleLabel = if (playbackState.isShuffleEnabled) "Shuffle" else "Shuffle off"

    val repeatIcon = when (playbackState.repeatMode) {
        RepeatMode.ONE -> R.drawable.ic_repeat_one_active
        RepeatMode.ALL -> R.drawable.ic_repeat_active
        RepeatMode.OFF -> R.drawable.ic_repeat
    }
    val repeatLabel = when (playbackState.repeatMode) {
        RepeatMode.ONE -> "Repeat one"
        RepeatMode.ALL -> "Repeat all"
        RepeatMode.OFF -> "Repeat off"
    }

    val favoriteIcon = if (isFav) R.drawable.ic_heart_filled else R.drawable.ic_heart_outline
    val favoriteLabel = if (isFav) "Unlike" else "Like"

    val speedIcon = if (playbackState.playbackSpeed != 1.0f) R.drawable.ic_speed_active else R.drawable.ic_speed
    val speedText = formatSpeedText(playbackState.playbackSpeed)
    val speedLabel = if (playbackState.playbackSpeed != 1.0f) "Speed · $speedText" else "Speed"

    val queuePosition = if (currentPlaylist.isNotEmpty() && currentIndex >= 0) {
        "Track ${currentIndex + 1} of ${currentPlaylist.size}"
    } else null
    val speedBadge = if (playbackState.playbackSpeed != 1.0f) speedText else null
    val subText = listOfNotNull(
        queuePosition,
        speedBadge,
        if (playbackState.isShuffleEnabled) "Shuffle" else null,
        when (playbackState.repeatMode) {
            RepeatMode.ONE -> "Repeat One"
            RepeatMode.ALL -> "Repeat All"
            RepeatMode.OFF -> null
        }
    ).joinToString("  ·  ")

    val currentPos = try {
        if (isPlayerInitialized()) exoPlayer.currentPosition else 0L
    } catch (_: Exception) {
        0L
    }

    val builder = NotificationCompat.Builder(this, notificationChannelId)
        .setContentTitle(title)
        .setContentText(artist)
        .setSubText(subText.ifEmpty { null })
        .setSmallIcon(R.drawable.ic_music_note)
        .setLargeIcon(largeIcon)
        .setContentIntent(contentPendingIntent)
        .setDeleteIntent(stopPending)
        .setOngoing(isPlaying)
        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
        .setOnlyAlertOnce(true)
        .setSilent(true)
        .setShowWhen(false)
        .setColor(Color.parseColor("#10B981"))
        .setColorized(false)
        .setUsesChronometer(true)
        .setWhen(System.currentTimeMillis() - currentPos)

    builder.addAction(NotificationCompat.Action(shuffleIcon, shuffleLabel, shufflePending))
    builder.addAction(NotificationCompat.Action(favoriteIcon, favoriteLabel, favoritePending))
    builder.addAction(NotificationCompat.Action(R.drawable.ic_skip_previous, "Previous", prevPending))
    builder.addAction(
        NotificationCompat.Action(
            if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play,
            if (isPlaying) "Pause" else "Play",
            playPausePending
        )
    )
    builder.addAction(NotificationCompat.Action(R.drawable.ic_skip_next, "Next", nextPending))
    builder.addAction(NotificationCompat.Action(repeatIcon, repeatLabel, repeatPending))
    builder.addAction(NotificationCompat.Action(speedIcon, speedLabel, speedPending))

    val mediaStyle = MediaStyle()
        .setMediaSession(mediaSession.sessionToken)
        .setShowActionsInCompactView(2, 3, 4)
        .setShowCancelButton(true)
        .setCancelButtonIntent(stopPending)

    builder.setStyle(mediaStyle)
    return builder.build()
}

internal fun MusicPlaybackService.formatSpeedText(speed: Float): String {
    if (speed.isNaN() || speed.isInfinite()) return "1x"
    val rounded = (speed * 100f).toInt()
    val value = rounded / 100f
    val intValue = value.toInt()
    return if (value == intValue.toFloat()) {
        "${intValue}x"
    } else {
        String.format("%.2f", value).trimEnd('0').trimEnd('.') + "x"
    }
}

internal fun MusicPlaybackService.updateNotification() {
    val state = getPlaybackState()
    if (state.currentTrack != null) {
        val notification = createNotification(state)
        val notificationManager = getSystemService(Service.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(notificationId, notification)

        NowPlayingWidget.updateWidget(
            this,
            state.currentTrack?.title,
            state.currentTrack?.artist,
            state.currentTrack?.thumbnailUrl,
            state.isPlaying
        )
    }
}

internal fun MusicPlaybackService.updateMediaSessionState() {
    val state = if (exoPlayer.isPlaying) {
        PlaybackStateCompat.STATE_PLAYING
    } else {
        PlaybackStateCompat.STATE_PAUSED
    }

    val playbackStateBuilder = PlaybackStateCompat.Builder()
        .setActions(
            PlaybackStateCompat.ACTION_PLAY or
                PlaybackStateCompat.ACTION_PAUSE or
                PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                PlaybackStateCompat.ACTION_SEEK_TO or
                PlaybackStateCompat.ACTION_STOP
        )
        .setState(state, exoPlayer.currentPosition, 1f)
        .addCustomAction(
            PlaybackStateCompat.CustomAction.Builder(
                MusicPlaybackService.ACTION_TOGGLE_SHUFFLE,
                if (isShuffleEnabled) "Shuffle On" else "Shuffle Off",
                if (isShuffleEnabled) R.drawable.ic_shuffle_active else R.drawable.ic_shuffle
            ).build()
        )
        .addCustomAction(
            PlaybackStateCompat.CustomAction.Builder(
                MusicPlaybackService.ACTION_TOGGLE_REPEAT,
                when (repeatMode) {
                    RepeatMode.OFF -> "Repeat Off"
                    RepeatMode.ALL -> "Repeat All"
                    RepeatMode.ONE -> "Repeat One"
                },
                when (repeatMode) {
                    RepeatMode.ONE -> R.drawable.ic_repeat_one_active
                    RepeatMode.ALL -> R.drawable.ic_repeat_active
                    RepeatMode.OFF -> R.drawable.ic_repeat
                }
            ).build()
        )
        .addCustomAction(
            PlaybackStateCompat.CustomAction.Builder(
                MusicPlaybackService.ACTION_TOGGLE_FAVORITE,
                if (isTrackFavorite(getCurrentTrack()?.id)) "Unlike" else "Like",
                if (isTrackFavorite(getCurrentTrack()?.id)) R.drawable.ic_heart_filled else R.drawable.ic_heart_outline
            ).build()
        )
        .addCustomAction(
            PlaybackStateCompat.CustomAction.Builder(
                MusicPlaybackService.ACTION_CYCLE_SPEED,
                "Speed · ${formatSpeedText(getPlaybackSpeed())}",
                if (getPlaybackSpeed() != 1.0f) R.drawable.ic_speed_active else R.drawable.ic_speed
            ).build()
        )

    mediaSession.setPlaybackState(playbackStateBuilder.build())

    val currentTrack = getCurrentTrack()
    if (currentTrack != null) {
        val metadataBuilder = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, currentTrack.title)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, currentTrack.artist)
            .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE, currentTrack.title)
            .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE, currentTrack.artist)
            .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_DESCRIPTION, "Playing in Raazi")
            .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, "Raazi")
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, getDuration())
            .putString(
                MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON_URI,
                currentTrack.thumbnailUrl
            )

        if (currentAlbumArt != null) {
            metadataBuilder
                .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, currentAlbumArt)
                .putBitmap(MediaMetadataCompat.METADATA_KEY_ART, currentAlbumArt)
                .putBitmap(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON, currentAlbumArt)
        }

        mediaSession.setMetadata(metadataBuilder.build())
    }
}

internal fun MusicPlaybackService.loadAlbumArt(url: String?) {
    if (url.isNullOrEmpty()) {
        currentAlbumArt = null
        updateNotification()
        return
    }

    val expectedTrackId = pendingNotificationTrackId

    serviceScope.launch(Dispatchers.IO) {
        try {
            val loader = this@loadAlbumArt.imageLoader
            val request = ImageRequest.Builder(this@loadAlbumArt)
                .data(url)
                .allowHardware(false)
                .build()

            val result = loader.execute(request).drawable
            val bitmap = (result as? BitmapDrawable)?.bitmap

            withContext(Dispatchers.Main) {
                if (expectedTrackId != null && getCurrentTrack()?.id != expectedTrackId) {
                    return@withContext
                }
                currentAlbumArt = bitmap
                updateNotification()
            }
        } catch (_: Exception) {
            withContext(Dispatchers.Main) {
                if (expectedTrackId != null && getCurrentTrack()?.id != expectedTrackId) {
                    return@withContext
                }
                currentAlbumArt = null
                updateNotification()
            }
        }
    }
}
