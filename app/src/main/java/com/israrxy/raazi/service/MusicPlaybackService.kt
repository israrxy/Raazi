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
import android.media.audiofx.BassBoost
import android.media.audiofx.Virtualizer
import android.media.audiofx.PresetReverb
import android.media.audiofx.AudioEffect
import android.media.audiofx.Visualizer
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
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannel()
        } else {
             createNotificationChannel()
        }
        
        val dataSourceFactory = createDataSourceFactory()
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)
        
        exoPlayer = ExoPlayer.Builder(this)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()
            
        // Initialize Advanced Audio Effects
        try {
            val audioSessionId = exoPlayer.audioSessionId
            audioEqualizer = android.media.audiofx.Equalizer(0, audioSessionId)
            audioEqualizer?.enabled = true

            // Initialize Bass Boost
            bassBoost = BassBoost(0, audioSessionId)
            bassBoost?.enabled = true

            // Initialize Virtualizer
            virtualizer = Virtualizer(0, audioSessionId)
            virtualizer?.enabled = true

            // Initialize Preset Reverb
            presetReverb = PresetReverb(0, audioSessionId)
            presetReverb?.enabled = true

            // Initialize Visualizer for spectrum analysis
            try {
                visualizer = Visualizer(audioSessionId)
                visualizer?.enabled = false // Start disabled
                visualizer?.captureSize = Visualizer.getCaptureSizeRange()[1] // Use max capture size
                visualizer?.setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(visualizer: Visualizer?, waveform: ByteArray?, samplingRate: Int) {
                        // Not used for spectrum visualization
                    }

                    override fun onFftDataCapture(visualizer: Visualizer?, fft: ByteArray?, samplingRate: Int) {
                        fft?.let { data ->
                            visualizerData = data
                            // Notify listeners of new data
                            visualizerListeners.forEach { listener ->
                                try {
                                    listener(data)
                                } catch (e: Exception) {
                                    android.util.Log.e("MusicService", "Error notifying visualizer listener", e)
                                }
                            }
                        }
                    }
                }, Visualizer.getMaxCaptureRate() / 2, false, true) // Only capture FFT data
                android.util.Log.d("MusicService", "Visualizer initialized successfully")
            } catch (e: Exception) {
                android.util.Log.e("MusicService", "Error initializing visualizer", e)
            }

            android.util.Log.d("MusicService", "Advanced audio effects initialized successfully")
        } catch (e: Exception) {
            android.util.Log.e("MusicService", "Error initializing audio effects", e)
        }
        
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
            
            override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
                // Sync current index with ExoPlayer
                if (mediaItem != null) {
                    currentIndex = exoPlayer.currentMediaItemIndex
                    val tag = mediaItem.localConfiguration?.tag
                    if (tag is MusicItem) {
                         // Load album art for notification
                         loadAlbumArt(tag.thumbnailUrl)
                    }
                    notifyPlaybackState()
                    updateNotification()
                }
            }
            
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    // Playlist ended
                    notifyPlaybackState()
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
            
            // Update home screen widget
            com.israrxy.raazi.widget.NowPlayingWidget.updateWidget(
                this,
                state.currentTrack?.title,
                state.currentTrack?.artist,
                state.currentTrack?.thumbnailUrl,
                state.isPlaying
            )
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
        
        playExoPlayerPlaylist(currentPlaylist, currentIndex)
    }

    private fun createMediaItem(musicItem: MusicItem): androidx.media3.common.MediaItem {
        // Use local path if available (downloaded), otherwise use custom raazi URI or direct URL based on stream type
        val sourceUri = if (musicItem.localPath != null && java.io.File(musicItem.localPath).exists()) {
             android.net.Uri.fromFile(java.io.File(musicItem.localPath)).toString()
        } else if (musicItem.videoUrl.contains("soundcloud.com") || musicItem.videoUrl.contains("bandcamp.com")) {
             // For Soundcloud/Bandcamp, we still use raazi scheme to let resolver handle it, OR we could resolve directly here.
             // Using resolver is safer as it handles extraction.
             // Ensure we pass the full URL as the ID in the raazi scheme if needed, or just rely on the resolver's logic
             "raazi://youtube/${java.net.URLEncoder.encode(musicItem.videoUrl, "UTF-8")}?title=${java.net.URLEncoder.encode(musicItem.title, "UTF-8")}&artist=${java.net.URLEncoder.encode(musicItem.artist, "UTF-8")}" 
        } else if (musicItem.videoUrl.startsWith("http")) {
              // Direct URL (already resolved or specific type), usually we just wrap it
              // But for safety and consistency with our resolver architecture:
              "raazi://youtube/${java.net.URLEncoder.encode(musicItem.videoUrl, "UTF-8")}?title=${java.net.URLEncoder.encode(musicItem.title, "UTF-8")}&artist=${java.net.URLEncoder.encode(musicItem.artist, "UTF-8")}"
        } else {
             "raazi://youtube/${java.net.URLEncoder.encode(musicItem.videoUrl, "UTF-8")}?title=${java.net.URLEncoder.encode(musicItem.title, "UTF-8")}&artist=${java.net.URLEncoder.encode(musicItem.artist, "UTF-8")}"
        }

        return androidx.media3.common.MediaItem.Builder()
            .setUri(sourceUri)
            .setMediaId(musicItem.videoUrl)
            .setTag(musicItem) // Store the MusicItem in the tag for easy retrieval
            .build()
    }

    private fun playExoPlayerPlaylist(playlist: List<MusicItem>, startIndex: Int) {
        try {
            android.util.Log.d(TAG, "Setting playlist of ${playlist.size} items, starting at $startIndex")
            
            exoPlayer.stop()
            exoPlayer.clearMediaItems()
            
            val mediaItems = playlist.map { createMediaItem(it) }
            exoPlayer.setMediaItems(mediaItems, startIndex, androidx.media3.common.C.TIME_UNSET)
            
            exoPlayer.prepare()
            exoPlayer.playWhenReady = true
            requestAudioFocus()
            
            // Notify state change
            notifyPlaybackState(isLoading = true) 
            startForegroundService()
            
            // Note: onMediaItemTransition will handle updating the current track info once the player actually switches
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error playing playlist", e)
        }
    }

    fun playPlaylist(playlist: List<MusicItem>, startIndex: Int = 0) {
        originalPlaylist = playlist
        if (isShuffleEnabled) {
             val targetTrack = playlist.getOrNull(startIndex)
            if (targetTrack != null) {
                val remaining = playlist.filter { it.id != targetTrack.id }.shuffled()
                currentPlaylist = listOf(targetTrack) + remaining
                currentIndex = 0
            } else {
                currentPlaylist = playlist.shuffled()
                currentIndex = 0
            }
        } else {
            currentPlaylist = playlist
            currentIndex = startIndex
        }
        
        playExoPlayerPlaylist(currentPlaylist, currentIndex)
    }

    fun pause() {
        exoPlayer.pause()
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
    


    
    // --- Advanced Audio Effects Support ---
    private var audioEqualizer: android.media.audiofx.Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null
    private var presetReverb: PresetReverb? = null
    private var visualizer: Visualizer? = null
    private var visualizerData: ByteArray? = null
    private var visualizerListeners = mutableSetOf<(ByteArray) -> Unit>()

    // Equalizer methods
    fun getEqualizerBands(): Short {
        return audioEqualizer?.numberOfBands ?: 0
    }

    fun getBandLevelRange(): ShortArray {
        return audioEqualizer?.bandLevelRange ?: shortArrayOf(0, 0)
    }

    fun getBandLevel(band: Short): Short {
        return audioEqualizer?.getBandLevel(band) ?: 0
    }

    fun setBandLevel(band: Short, level: Short) {
        audioEqualizer?.setBandLevel(band, level)
    }

    fun getCenterFreq(band: Short): Int {
        return audioEqualizer?.getCenterFreq(band) ?: 0
    }

    fun getPresetNames(): List<String> {
        val count = audioEqualizer?.numberOfPresets ?: 0
        val names = mutableListOf<String>()
        for (i in 0 until count) {
            names.add(audioEqualizer?.getPresetName(i.toShort()) ?: "Preset $i")
        }
        return names
    }

    fun usePreset(preset: Short) {
        audioEqualizer?.usePreset(preset)
    }

    // Bass Boost methods
    fun getBassBoostStrength(): Short {
        return bassBoost?.roundedStrength ?: 0
    }

    fun setBassBoostStrength(strength: Short) {
        bassBoost?.setStrength(strength)
    }

    fun isBassBoostSupported(): Boolean {
        return bassBoost != null
    }

    // Virtualizer methods
    fun getVirtualizerStrength(): Short {
        return virtualizer?.roundedStrength ?: 0
    }

    fun setVirtualizerStrength(strength: Short) {
        virtualizer?.setStrength(strength)
    }

    fun isVirtualizerSupported(): Boolean {
        return virtualizer != null
    }

    // Preset Reverb methods
    fun getReverbPresets(): List<String> {
        return listOf(
            "None", "Small Room", "Medium Room", "Large Room",
            "Medium Hall", "Large Hall", "Plate"
        )
    }

    fun setReverbPreset(preset: Int) {
        presetReverb?.preset = preset.toShort()
    }

    fun getReverbPreset(): Int {
        return presetReverb?.preset?.toInt() ?: 0
    }

    fun isReverbSupported(): Boolean {
        return presetReverb != null
    }

    // Spectrum Analysis
    fun getFrequencyData(): ByteArray? {
        return visualizerData
    }

    // Visualizer Management
    fun isVisualizerSupported(): Boolean {
        return visualizer != null
    }

    fun enableVisualizer(enable: Boolean) {
        try {
            visualizer?.enabled = enable
            android.util.Log.d("MusicService", "Visualizer enabled: $enable")
        } catch (e: Exception) {
            android.util.Log.e("MusicService", "Error enabling visualizer", e)
        }
    }

    fun addVisualizerListener(listener: (ByteArray) -> Unit) {
        visualizerListeners.add(listener)
        // Enable visualizer if this is the first listener
        if (visualizerListeners.size == 1) {
            enableVisualizer(true)
        }
    }

    fun removeVisualizerListener(listener: (ByteArray) -> Unit) {
        visualizerListeners.remove(listener)
        // Disable visualizer if no more listeners
        if (visualizerListeners.isEmpty()) {
            enableVisualizer(false)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        mediaSession.release()
        exoPlayer.release()
        abandonAudioFocus()
        
        // Cleanup visualizer
        try {
            visualizer?.release()
        } catch (e: Exception) {
            android.util.Log.e("MusicService", "Error releasing visualizer", e)
        }
        // Cleanup other audio effects
        try {
            audioEqualizer?.release()
            bassBoost?.release()
            virtualizer?.release()
            presetReverb?.release()
        } catch (e: Exception) {
            android.util.Log.e("MusicService", "Error releasing audio effects", e)
        }
    }

    private fun createDataSourceFactory(): androidx.media3.datasource.DataSource.Factory {
        val okHttpClient = OkHttpClient.Builder().build()
        val okHttpDataSourceFactory = OkHttpDataSource.Factory(okHttpClient)
        
        // DefaultDataSource will use OkHttp for valid URLs and handle file/asset/content URIs too
        val upstreamFactory = DefaultDataSource.Factory(this, okHttpDataSourceFactory)
        
        return ResolvingDataSource.Factory(upstreamFactory) { dataSpec ->
            val isRaaziScheme = dataSpec.uri.scheme == "raazi" && dataSpec.uri.host == "youtube"
            // Use key if available, otherwise extract full path from URI for 'raazi://' scheme
            // IMPORTANT: Use full path, not lastPathSegment, to preserve SoundCloud URLs
            var videoId = dataSpec.key ?: if (isRaaziScheme) {
                // Remove leading "/" from path to get the actual URL/ID
                val encodedId = dataSpec.uri.path?.removePrefix("/") ?: dataSpec.uri.lastPathSegment
                if (encodedId != null) java.net.URLDecoder.decode(encodedId, "UTF-8") else null
            } else null
            
            android.util.Log.d(TAG, "Extracted videoId from dataSpec: $videoId")
            
            // Fix: If videoId is a full URL, extract the actual ID (but preserve SoundCloud/Bandcamp URLs)
            if (!videoId.isNullOrEmpty()) {
                // Preserve SoundCloud and Bandcamp URLs as-is
                if (videoId.contains("soundcloud.com") || videoId.contains("bandcamp.com")) {
                    android.util.Log.d(TAG, "Detected SoundCloud/Bandcamp URL, keeping full URL: $videoId")
                } else if (!videoId.contains("youtube.com") && !videoId.contains("youtu.be")) {
                    // Not a URL with youtube in it?
                    // Maybe check if it's a URL at all for safety
                    if (videoId.startsWith("http")) {
                         // It's a non-youtube HTTP URL? Usually shouldn't happen for us unless it's direct.
                         // Let it fall through to isResolvableUrl check
                    }
                }
            } else if (videoId.isNullOrEmpty()) {
                 // Fallback: Use the URI itself if key is missing
                 val uriString = dataSpec.uri.toString()
                 if (uriString.startsWith("http")) {
                     videoId = uriString
                     android.util.Log.d(TAG, "No key found, using URI as videoId: $videoId")
                 }
            }

            // Determine if we should attempt to resolve this URI
            val host = dataSpec.uri.host?.lowercase() ?: ""
            // Simplified check: if it looks like something we support, try resolving it.
            // StreamResolver now handles extraction, so we just pass the URL string.
            val isResolvableUrl = videoId?.contains("youtube.com") == true || videoId?.contains("youtu.be") == true ||
                                  host.contains("youtube.com") || host.contains("youtu.be") || 
                                  host.contains("soundcloud.com") || host.contains("bandcamp.com")

            if (!videoId.isNullOrEmpty() && videoId != "watch" && (isRaaziScheme || isResolvableUrl)) {
                try {
                    // Extract metadata for fallback
                    val title = dataSpec.uri.getQueryParameter("title")
                    val artist = dataSpec.uri.getQueryParameter("artist")
                    
                    val result = StreamResolver.resolveStreamUrl(videoId!!, title, artist)
                    android.util.Log.d(TAG, "Resolved stream for $videoId: ${result.url}")
                    
                    val headers = mutableMapOf<String, String>()
                    headers["User-Agent"] = result.userAgent
                    
                    return@Factory dataSpec.buildUpon()
                        .setUri(Uri.parse(result.url))
                        .setHttpRequestHeaders(headers)
                        .build()
                } catch (e: Exception) {
                    android.util.Log.e(TAG, "Failed to resolve stream for $videoId", e)
                    // If it was a YouTube URL that failed, throw exception.
                    // If it was just a random http url, maybe fall through?
                    // Let's stick to throwing for network failures on supported streams to avoid bad state.
                    throw androidx.media3.common.PlaybackException(
                        "Failed to resolve stream for $videoId",
                        e,
                        androidx.media3.common.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED
                    )
                }
            } else if (videoId == "watch") {
                 // Handle case where parsing failed or lastPathSegment was 'watch'
                 // Try to find v parameter from the URI query itself if possible
                 val originalUri = dataSpec.uri.toString()
                 if (originalUri.contains("v=")) {
                     val extractedId = originalUri.substringAfter("v=").substringBefore("&")
                     if (extractedId.isNotEmpty()) {
                         try {
                            val title = dataSpec.uri.getQueryParameter("title")
                            val artist = dataSpec.uri.getQueryParameter("artist")
                            val result = StreamResolver.resolveStreamUrl(extractedId, title, artist)
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
                 } else {
                     // NEW: If "watch" segment but NO v= param, this is invalid.
                     android.util.Log.e(TAG, "Invalid 'watch' URL without ID: ${dataSpec.uri}")
                     throw androidx.media3.common.PlaybackException(
                        "Invalid watch URL: ${dataSpec.uri}",
                        null,
                        androidx.media3.common.PlaybackException.ERROR_CODE_IO_UNSPECIFIED
                     )
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