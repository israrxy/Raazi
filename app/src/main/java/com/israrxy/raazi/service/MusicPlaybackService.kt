package com.israrxy.raazi.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.media.app.NotificationCompat.MediaStyle
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.israrxy.raazi.MainActivity
import com.israrxy.raazi.R
import com.israrxy.raazi.model.MusicItem
import com.israrxy.raazi.model.PlaybackState
import com.israrxy.raazi.model.RepeatMode
import kotlinx.coroutines.*
import java.net.URL
import coil.imageLoader
import coil.request.ImageRequest
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import okhttp3.OkHttpClient
import com.israrxy.raazi.player.StreamResolver
import android.net.Uri

class MusicPlaybackService : Service() {
    private lateinit var exoPlayer: ExoPlayer
    private lateinit var mediaSession: MediaSessionCompat
    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private val serviceBinder = MusicBinder()
    
    // Coroutine scope for background tasks
    private val extractor = YouTubeMusicExtractor()
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    private val notificationChannelId = "raazi_music_channel"
    private val notificationId = 1001
    
    // Playlist state
    private var currentPlaylist: List<MusicItem> = emptyList()
    private var originalPlaylist: List<MusicItem> = emptyList() // For shuffle
    private var currentIndex = -1
    private var playbackStateListeners = mutableSetOf<(PlaybackState) -> Unit>()
    private var currentAlbumArt: Bitmap? = null
    
    // Playback modes
    private var isShuffleEnabled = false
    private var repeatMode = RepeatMode.OFF
    
    // Position update job for continuous timeline updates
    private var positionUpdateJob: kotlinx.coroutines.Job? = null
    private var playOnFocusGain = false

    override fun onBind(intent: Intent): IBinder = serviceBinder

    inner class MusicBinder : Binder() {
        fun getService(): MusicPlaybackService = this@MusicPlaybackService
    }

    override fun onCreate() {
        super.onCreate()
        
        createNotificationChannel()
        
        val dataSourceFactory = createDataSourceFactory()
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)
        
        exoPlayer = ExoPlayer.Builder(this)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()
        
        // Create legacy MediaSession for notification controls
        mediaSession = MediaSessionCompat(this, "RaaziMusicSession").apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() {
                    resume()
                }
                
                override fun onPause() {
                    pause()
                }
                
                override fun onSkipToNext() {
                    next()
                }
                
                override fun onSkipToPrevious() {
                    previous()
                }
                
                override fun onStop() {
                    stop()
                }
                
                override fun onSeekTo(pos: Long) {
                    seekTo(pos)
                }
            })
            isActive = true
        }
        

        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAcceptsDelayedFocusGain(true)
                .setOnAudioFocusChangeListener { focusChange ->
                    when (focusChange) {
                        AudioManager.AUDIOFOCUS_LOSS -> {
                            playOnFocusGain = exoPlayer.isPlaying // Try to resume when they stop
                            pause()
                        }
                        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                            playOnFocusGain = exoPlayer.isPlaying
                            pause()
                        }
                        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                            exoPlayer.volume = 0.3f
                        }
                        AudioManager.AUDIOFOCUS_GAIN -> {
                            exoPlayer.volume = 1f
                            if (playOnFocusGain) {
                                resume()
                                playOnFocusGain = false
                            }
                        }
                    }
                }
                .build()
        }
        
        exoPlayer.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                updateMediaSessionState()
                notifyPlaybackState()
                
                // Start or stop position updates based on playing state
                if (isPlaying) {
                    startPositionUpdates()
                } else {
                    stopPositionUpdates()
                }
            }
            
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    // Auto play next track
                    next()
                }
                updateMediaSessionState()
                notifyPlaybackState()
            }
            
            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                notifyPlaybackState()
            }
        })
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        mediaSession.release()
        exoPlayer.release()
        abandonAudioFocus()
    }

    fun toggleShuffle() {
        isShuffleEnabled = !isShuffleEnabled
        if (currentPlaylist.isNotEmpty()) {
            val currentTrack = currentPlaylist.getOrNull(currentIndex)
            if (isShuffleEnabled) {
                // Shuffle but keep current track first or find it
                val remaining = originalPlaylist.filter { it.id != currentTrack?.id }.shuffled()
                currentPlaylist = if (currentTrack != null) listOf(currentTrack) + remaining else remaining
                currentIndex = 0
            } else {
                // Restore original order
                currentPlaylist = originalPlaylist
                // Find current track index in original
                currentIndex = currentPlaylist.indexOfFirst { it.id == currentTrack?.id }.coerceAtLeast(0)
            }
        }
        notifyPlaybackState()
    }

    fun toggleRepeat() {
        repeatMode = when (repeatMode) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
        notifyPlaybackState()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                notificationChannelId,
                "Music Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Controls for music playback"
                setShowBadge(false)
            }
            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun startForegroundService() {
        val notification = createNotification(getPlaybackState())
        startForeground(notificationId, notification)
    }

    private fun createNotification(playbackState: PlaybackState): Notification {
        val contentIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this, 0, contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val currentTrack = playbackState.currentTrack
        val title = currentTrack?.title ?: "No Track"
        val artist = currentTrack?.artist ?: "Unknown Artist"
        val isPlaying = playbackState.isPlaying

        // Action intents
        val prevIntent = Intent(this, MusicPlaybackService::class.java).apply { action = ACTION_PREV }
        val prevPending = PendingIntent.getService(this, 1, prevIntent, PendingIntent.FLAG_IMMUTABLE)

        val playPauseIntent = Intent(this, MusicPlaybackService::class.java).apply { 
            action = if (isPlaying) ACTION_PAUSE else ACTION_PLAY 
        }
        val playPausePending = PendingIntent.getService(this, 2, playPauseIntent, PendingIntent.FLAG_IMMUTABLE)

        val nextIntent = Intent(this, MusicPlaybackService::class.java).apply { action = ACTION_NEXT }
        val nextPending = PendingIntent.getService(this, 3, nextIntent, PendingIntent.FLAG_IMMUTABLE)
        
        val stopIntent = Intent(this, MusicPlaybackService::class.java).apply { action = ACTION_STOP }
        val stopPending = PendingIntent.getService(this, 4, stopIntent, PendingIntent.FLAG_IMMUTABLE)

        val builder = NotificationCompat.Builder(this, notificationChannelId)
            .setContentTitle(title)
            .setContentText(artist)
            .setSubText("Raazi Music")
            .setSmallIcon(R.drawable.ic_music_note)
            .setLargeIcon(currentAlbumArt)
            .setContentIntent(contentPendingIntent)
            .setDeleteIntent(stopPending)
            .setOngoing(isPlaying)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            
            // Actions
            .addAction(NotificationCompat.Action(
                R.drawable.ic_skip_previous, "Previous", prevPending
            ))
            .addAction(NotificationCompat.Action(
                if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play,
                if (isPlaying) "Pause" else "Play",
                playPausePending
            ))
            .addAction(NotificationCompat.Action(
                R.drawable.ic_skip_next, "Next", nextPending
            ))
            
            // Media style with session token
            .setStyle(MediaStyle()
                .setShowActionsInCompactView(0, 1, 2)
                .setMediaSession(mediaSession.sessionToken)
            )
            
        return builder.build()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> resume()
            ACTION_PAUSE -> pause()
            ACTION_PREV -> previous()
            ACTION_NEXT -> next()
            ACTION_STOP -> {
                stop()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun updateNotification() {
        val state = getPlaybackState()
        if (state.currentTrack != null) {
            val notification = createNotification(state)
            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(notificationId, notification)
        }
    }
    
    private fun updateMediaSessionState() {
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
            
        mediaSession.setPlaybackState(playbackStateBuilder.build())
        
        // Update Metadata for Duration in Notification
        val currentTrack = getCurrentTrack()
        if (currentTrack != null) {
            val metadataBuilder = android.support.v4.media.MediaMetadataCompat.Builder()
                .putString(android.support.v4.media.MediaMetadataCompat.METADATA_KEY_TITLE, currentTrack.title)
                .putString(android.support.v4.media.MediaMetadataCompat.METADATA_KEY_ARTIST, currentTrack.artist)
                .putLong(android.support.v4.media.MediaMetadataCompat.METADATA_KEY_DURATION, getDuration())
            
            if (currentAlbumArt != null) {
                metadataBuilder.putBitmap(android.support.v4.media.MediaMetadataCompat.METADATA_KEY_ALBUM_ART, currentAlbumArt)
            }
            
            mediaSession.setMetadata(metadataBuilder.build())
        }
    }
    
    private fun loadAlbumArt(url: String?) {
        if (url.isNullOrEmpty()) {
            currentAlbumArt = null
            updateNotification()
            return
        }
        
        serviceScope.launch(Dispatchers.IO) {
            try {
                val loader = this@MusicPlaybackService.imageLoader
                val request = ImageRequest.Builder(this@MusicPlaybackService)
                    .data(url)
                    .allowHardware(false) // Required for LargeIcon
                    .build()
                
                val result = loader.execute(request).drawable
                currentAlbumArt = (result as? android.graphics.drawable.BitmapDrawable)?.bitmap
                
                // Update notification with new art
                withContext(Dispatchers.Main) {
                    updateNotification()
                }
            } catch (e: Exception) {
                currentAlbumArt = null
                withContext(Dispatchers.Main) {
                    updateNotification()
                }
            }
        }
    }

    fun playMusic(musicItem: MusicItem, resetPlaylist: Boolean = true) {
        // Set loading state
        notifyPlaybackState(isLoading = true)
        
        // Add to playlist if not already playing from one or if reset requested
        if (resetPlaylist || currentPlaylist.isEmpty()) {
            currentPlaylist = listOf(musicItem)
            originalPlaylist = listOf(musicItem)
            currentIndex = 0
        }
        
        // Use local path if available (downloaded), otherwise use custom raazi URI for resolution
        val audioSource = if (musicItem.localPath != null && java.io.File(musicItem.localPath).exists()) {
             android.net.Uri.fromFile(java.io.File(musicItem.localPath)).toString()
        } else {
             "raazi://youtube/${musicItem.videoUrl}"
        }
        
        playMediaItem(musicItem, audioSource)
    }

    private fun playMediaItem(musicItem: MusicItem, source: String) {
        try {
            android.util.Log.d(TAG, "Playing: ${musicItem.title} from $source")
            
            // CRITICAL: Stop and clear previous media before playing new song
            exoPlayer.stop()
            exoPlayer.clearMediaItems()
            
            val mediaItem = androidx.media3.common.MediaItem.Builder()
                .setUri(source)
                .setMediaId(musicItem.videoUrl)
                .build()
            exoPlayer.setMediaItem(mediaItem)
            exoPlayer.prepare()
            exoPlayer.playWhenReady = true
            requestAudioFocus()
            
            // Clear loading state when playback starts
            notifyPlaybackState(isLoading = false)
            
            // Load album art for notification
            loadAlbumArt(musicItem.thumbnailUrl)
            
            // Notify state change immediately
            notifyPlaybackState()
            
            startForegroundService()
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error playing media item", e)
            if (currentPlaylist.size > 1) {
                 if (currentIndex < currentPlaylist.size - 1) {
                     next()
                 }
            }
        }
    }

    fun playPlaylist(playlist: List<MusicItem>, startIndex: Int = 0) {
        originalPlaylist = playlist
        currentPlaylist = if (isShuffleEnabled) {
            playlist.shuffled()
        } else {
            playlist
        }
        currentIndex = startIndex
        if (currentIndex >= 0 && currentIndex < currentPlaylist.size) {
            playMusic(currentPlaylist[currentIndex], resetPlaylist = false)
        }
    }

    fun pause() {
        exoPlayer.pause()
        updateNotification()
    }

    fun resume() {
        exoPlayer.play()
        requestAudioFocus()
        updateNotification()
    }

    fun stop() {
        exoPlayer.stop()
        abandonAudioFocus()
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    fun next() {
        if (currentPlaylist.isEmpty()) return
        
        when (repeatMode) {
            RepeatMode.ONE -> {
                // Restart current track
                exoPlayer.seekTo(0)
                exoPlayer.play()
            }
            RepeatMode.ALL -> {
                currentIndex = (currentIndex + 1) % currentPlaylist.size
                playMusic(currentPlaylist[currentIndex], resetPlaylist = false)
            }
            RepeatMode.OFF -> {
                if (currentIndex < currentPlaylist.size - 1) {
                    currentIndex++
                    playMusic(currentPlaylist[currentIndex], resetPlaylist = false)
                }
            }
        }
    }

    fun previous() {
        // If more than 3 seconds in, restart current track
        if (exoPlayer.currentPosition > 3000) {
            exoPlayer.seekTo(0)
            return
        }
        
        if (currentPlaylist.isNotEmpty() && currentIndex > 0) {
            currentIndex--
            playMusic(currentPlaylist[currentIndex], resetPlaylist = false)
        }
    }

    fun seekTo(position: Long) {
        exoPlayer.seekTo(position)
    }

    fun getCurrentPosition(): Long = exoPlayer.currentPosition

    fun getDuration(): Long = exoPlayer.duration

    fun isPlaying(): Boolean = exoPlayer.isPlaying

    fun getCurrentTrack(): MusicItem? {
        return if (currentPlaylist.isNotEmpty() && currentIndex >= 0) {
            currentPlaylist.getOrNull(currentIndex)
        } else {
            null
        }
    }

    fun getPlaybackState(): PlaybackState {
        return PlaybackState(
            isPlaying = exoPlayer.isPlaying,
            currentPosition = exoPlayer.currentPosition,
            duration = exoPlayer.duration.coerceAtLeast(0),
            currentTrack = if (currentPlaylist.isNotEmpty() && currentIndex >= 0 && currentIndex < currentPlaylist.size) {
                currentPlaylist[currentIndex]
            } else null,
            playlist = currentPlaylist,
            currentIndex = currentIndex,
            isShuffleEnabled = isShuffleEnabled,
            repeatMode = repeatMode,
            isLoading = false, // Will be overridden by notify calls
            isBuffering = exoPlayer.playbackState == androidx.media3.common.Player.STATE_BUFFERING
        )
    }

    fun addPlaybackStateListener(listener: (PlaybackState) -> Unit) {
        playbackStateListeners.add(listener)
        // Immediately send current state to new listener
        listener(getPlaybackState())
    }

    fun removePlaybackStateListener(listener: (PlaybackState) -> Unit) {
        playbackStateListeners.remove(listener)
    }
    
    private fun notifyPlaybackState(isLoading: Boolean = false) {
        val state = getPlaybackState().copy(isLoading = isLoading)
        playbackStateListeners.forEach { it(state) }
        
        // Update notification only if it's already showing
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (state.currentTrack != null) {
             updateNotification()
        }
    }
    
    // Continuously update position while playing (every 500ms)
    private fun startPositionUpdates() {
        stopPositionUpdates() // Cancel any existing job
        positionUpdateJob = serviceScope.launch {
            while (isPlaying()) {
                notifyPlaybackState()
                kotlinx.coroutines.delay(500L) // Update every 500ms
            }
        }
    }
    
    private fun stopPositionUpdates() {
        positionUpdateJob?.cancel()
        positionUpdateJob = null
    }

    private fun requestAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { request ->
                audioManager?.requestAudioFocus(request)
            }
        } else {
            @Suppress("DEPRECATION")
            audioManager?.requestAudioFocus(
                null,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            )
        }
    }

    private fun abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { request ->
                audioManager?.abandonAudioFocusRequest(request)
            }
        } else {
            @Suppress("DEPRECATION")
            audioManager?.abandonAudioFocus(null)
        }
    }
    


    
    private fun createDataSourceFactory(): androidx.media3.datasource.DataSource.Factory {
        val okHttpClient = OkHttpClient.Builder().build()
        val okHttpDataSourceFactory = OkHttpDataSource.Factory(okHttpClient)
        
        // DefaultDataSource will use OkHttp for valid URLs and handle file/asset/content URIs too
        val upstreamFactory = DefaultDataSource.Factory(this, okHttpDataSourceFactory)
        
        return ResolvingDataSource.Factory(upstreamFactory) { dataSpec ->
            val isRaaziScheme = dataSpec.uri.scheme == "raazi" && dataSpec.uri.host == "youtube"
            // Use key if available, otherwise extract from URI for 'raazi://' scheme
            var videoId = dataSpec.key ?: if (isRaaziScheme) dataSpec.uri.lastPathSegment else null
            
            // Fix: If videoId is a full URL, extract the actual ID
            if (!videoId.isNullOrEmpty() && (videoId.contains("youtube.com") || videoId.contains("youtu.be"))) {
                try {
                    if (videoId.contains("v=")) {
                         videoId = videoId.substringAfter("v=").substringBefore("&")
                    } else if (videoId.contains("youtu.be/")) {
                         videoId = videoId.substringAfter("youtu.be/").substringBefore("?")
                    }
                } catch (e: Exception) {
                    android.util.Log.w(TAG, "Failed to extract ID from URL: $videoId")
                }
            }

            if (!videoId.isNullOrEmpty() && videoId != "watch" && (isRaaziScheme || !dataSpec.uri.toString().startsWith("http"))) {
                try {
                    val result = StreamResolver.resolveStreamUrl(videoId)
                    android.util.Log.d(TAG, "Resolved stream for $videoId: ${result.url}")
                    
                    val headers = mutableMapOf<String, String>()
                    headers["User-Agent"] = result.userAgent
                    
                    return@Factory dataSpec.buildUpon()
                        .setUri(Uri.parse(result.url))
                        .setHttpRequestHeaders(headers)
                        .build()
                } catch (e: Exception) {
                    android.util.Log.e(TAG, "Failed to resolve stream for $videoId", e)
                    // If resolution fails, we let it fall through or throw. 
                    // Throwing here avoids passing a bad URI to OkHttp.
                    throw androidx.media3.common.PlaybackException(
                        "Failed to resolve stream for $videoId",
                        e,
                        androidx.media3.common.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED
                    )
                }
            } else if (videoId == "watch") {
                 // Handle case where parsing failed or lastPathSegment was 'watch'
                 // Try to find v parameter from the URI query itself if possible, though unlikely in this scheme structure
                 // Fallback: Check if the original URI string contains v=
                 val originalUri = dataSpec.uri.toString()
                 if (originalUri.contains("v=")) {
                     val extractedId = originalUri.substringAfter("v=").substringBefore("&")
                     if (extractedId.isNotEmpty()) {
                         try {
                            val result = StreamResolver.resolveStreamUrl(extractedId)
                            val headers = mutableMapOf<String, String>()
                            headers["User-Agent"] = result.userAgent
                            
                            return@Factory dataSpec.buildUpon()
                                .setUri(Uri.parse(result.url))
                                .setHttpRequestHeaders(headers)
                                .build()
                         } catch(e: Exception) {
                             // Log and fall through
                         }
                     }
                 }
            }
            return@Factory dataSpec
        }
    }

    companion object {
        private const val TAG = "MusicPlaybackService"
        const val ACTION_PLAY = "com.israrxy.raazi.ACTION_PLAY"
        const val ACTION_PAUSE = "com.israrxy.raazi.ACTION_PAUSE"
        const val ACTION_NEXT = "com.israrxy.raazi.ACTION_NEXT"
        const val ACTION_PREV = "com.israrxy.raazi.ACTION_PREV"
        const val ACTION_STOP = "com.israrxy.raazi.ACTION_STOP"
    }
}