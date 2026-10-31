package com.israrxy.raazi.service

import android.app.Service
import android.content.Intent
import android.graphics.Bitmap
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Binder
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.support.v4.media.session.MediaSessionCompat
import android.util.Log
import android.view.Surface
import androidx.media3.common.C
import androidx.media3.common.PlaybackParameters
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.israrxy.raazi.model.MusicItem
import com.israrxy.raazi.model.PlaybackMediaMode
import com.israrxy.raazi.model.PlaybackState
import com.israrxy.raazi.model.PlaybackVideoQuality
import com.israrxy.raazi.model.RepeatMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet

class MusicPlaybackService : Service() {
    internal lateinit var exoPlayer: ExoPlayer
    internal lateinit var mediaSession: MediaSessionCompat
    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private val serviceBinder = MusicBinder()

    internal val favoriteTrackIds: MutableSet<String> = ConcurrentHashMap.newKeySet()
    internal val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    internal val notificationChannelId = "raazi_music_channel"
    internal val notificationId = 1001

    internal var currentPlaylist: List<MusicItem> = emptyList()
    internal var originalPlaylist: List<MusicItem> = emptyList()
    internal var currentIndex = -1
    private val playbackStateListeners = CopyOnWriteArraySet<(PlaybackState) -> Unit>()
    private val trackChangedListeners = CopyOnWriteArraySet<(MusicItem) -> Unit>()
    internal var currentAlbumArt: Bitmap? = null
    internal var pendingNotificationTrackId: String? = null
    @Volatile internal var isDestroyed = false
    internal var currentTrackRetryCount = 0

    internal var isShuffleEnabled = false
    internal var repeatMode = RepeatMode.OFF
    internal var mediaMode = PlaybackMediaMode.AUDIO
    internal var videoQuality = PlaybackVideoQuality.AUTO
    internal var videoSurface: Surface? = null
    internal var isVideoAvailable = false
    internal var currentStreamHasVideo = false
    internal var activeTrackId: String? = null
    @Volatile internal var transitionTargetPositionMs: Long? = null
    @Volatile internal var transitionTrackId: String? = null
    @Volatile internal var lastKnownDurationMs: Long = 0L
    @Volatile internal var lastKnownDurationTrackId: String? = null

    private var positionUpdateJob: Job? = null
    private var playOnFocusGain = false

    private val audioEffectsController by lazy {
        PlaybackAudioEffectsController(
            context = this,
            exoPlayerProvider = { if (isPlayerInitialized()) exoPlayer else null },
            isDestroyedProvider = { isDestroyed }
        )
    }

    internal fun isPlayerInitialized(): Boolean = ::exoPlayer.isInitialized

    override fun onBind(intent: Intent): IBinder = serviceBinder

    inner class MusicBinder : Binder() {
        fun getService(): MusicPlaybackService = this@MusicPlaybackService
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        val dataSourceFactory = createDataSourceFactory()
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)
        val playerAudioAttributes = androidx.media3.common.AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        exoPlayer = ExoPlayer.Builder(this)
            .setMediaSourceFactory(mediaSourceFactory)
            .setAudioAttributes(playerAudioAttributes, false)
            .setHandleAudioBecomingNoisy(true)
            .build()

        audioEffectsController.initializeAudioEffects()

        mediaSession = MediaSessionCompat(this, "RaaziMusicSession").apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() = resume()
                override fun onPause() = pause()
                override fun onSkipToNext() = next()
                override fun onSkipToPrevious() = previous()
                override fun onStop() = stop()
                override fun onSeekTo(pos: Long) = seekTo(pos)
                override fun onCustomAction(action: String?, extras: Bundle?) {
                    when (action) {
                        ACTION_TOGGLE_SHUFFLE -> toggleShuffle()
                        ACTION_TOGGLE_REPEAT -> toggleRepeat()
                        ACTION_TOGGLE_FAVORITE -> toggleFavoriteFromNotificationImpl()
                        ACTION_CYCLE_SPEED -> cyclePlaybackSpeed()
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
                        AudioManager.AUDIOFOCUS_LOSS,
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

        exoPlayer.addListener(createPlayerListenerImpl())
    }

    fun toggleShuffle() = toggleShuffleImpl()

    fun toggleRepeat() {
        repeatMode = when (repeatMode) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
        applyRepeatModeToPlayer()
        notifyPlaybackState(refreshNotification = true)
    }

    fun setPlaybackSpeed(speed: Float) {
        if (isDestroyed || speed.isNaN() || speed.isInfinite()) return
        val safeSpeed = speed.coerceIn(MIN_PLAYBACK_SPEED, MAX_PLAYBACK_SPEED)
        try {
            if (isPlayerInitialized()) {
                exoPlayer.playbackParameters = PlaybackParameters(safeSpeed)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error setting playback speed", e)
        }
        try {
            notifyPlaybackState(refreshNotification = false)
        } catch (_: Exception) {}
    }

    fun getPlaybackSpeed(): Float {
        return try {
            if (isPlayerInitialized()) exoPlayer.playbackParameters.speed else 1.0f
        } catch (_: Exception) { 1.0f }
    }

    fun cyclePlaybackSpeed(): Float {
        if (isDestroyed) return 1.0f
        val current = getPlaybackSpeed()
        val next = SPEED_CYCLE_PRESETS.firstOrNull { it > current + 0.01f } ?: SPEED_CYCLE_PRESETS.first()
        setPlaybackSpeed(next)
        return next
    }

    fun updateFavoriteTrackIds(ids: Set<String>) {
        favoriteTrackIds.clear()
        favoriteTrackIds.addAll(ids)
        notifyPlaybackState(refreshNotification = true)
    }

    fun isTrackFavorite(trackId: String?): Boolean = trackId != null && favoriteTrackIds.contains(trackId)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> resume()
            ACTION_PAUSE -> pause()
            ACTION_PREV -> previous()
            ACTION_NEXT -> next()
            ACTION_TOGGLE_SHUFFLE -> toggleShuffle()
            ACTION_TOGGLE_REPEAT -> toggleRepeat()
            ACTION_TOGGLE_FAVORITE -> toggleFavoriteFromNotificationImpl()
            ACTION_CYCLE_SPEED -> cyclePlaybackSpeed()
            ACTION_STOP -> {
                stop()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    fun playMusic(musicItem: MusicItem, resetPlaylist: Boolean = true) {
        notifyPlaybackState(isLoading = true, refreshNotification = true)
        if (resetPlaylist || currentPlaylist.isEmpty()) {
            currentPlaylist = listOf(musicItem)
            originalPlaylist = listOf(musicItem)
            currentIndex = 0
        }
        playExoPlayerPlaylist(currentPlaylist, currentIndex)

        if (currentPlaylist.size <= 1) {
            val targetId = musicItem.id
            serviceScope.launch(Dispatchers.IO) {
                try {
                    val upNext = YouTubeMusicExtractor.getInstance()
                        .getUpNext(targetId)
                        .filter { it.id.isNotBlank() && it.id != targetId }
                        .distinctBy { it.id }
                    if (upNext.isNotEmpty()) {
                        withContext(Dispatchers.Main) {
                            if (!isDestroyed && getCurrentTrack()?.id == targetId && currentPlaylist.size <= 1) {
                                val tracksToQueue = if (isShuffleEnabled) upNext.shuffled() else upNext
                                addToQueue(tracksToQueue)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Auto-populate Up Next queue failed for $targetId: ${e.message}")
                }
            }
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
        if (isDestroyed) return
        try { exoPlayer.pause() } catch (e: Exception) { Log.e(TAG, "Error pausing", e) }
    }

    fun resume() = resumeImpl()

    fun stop() {
        if (isDestroyed) return
        try {
            exoPlayer.stop()
            abandonAudioFocus()
            stopForeground(STOP_FOREGROUND_REMOVE)
        } catch (e: Exception) { Log.e(TAG, "Error stopping", e) }
    }

    fun next() = nextImpl()
    fun previous() = previousImpl()

    fun seekTo(position: Long) {
        if (isDestroyed) return
        val safePos = position.coerceAtLeast(0L)
        if (transitionTargetPositionMs != null) transitionTargetPositionMs = safePos
        try {
            exoPlayer.seekTo(safePos)
            notifyPlaybackState()
        } catch (e: Exception) {
            Log.e(TAG, "Error seeking", e)
        }
    }

    fun getCurrentPosition(): Long = getCurrentPositionImpl()
    fun getDuration(): Long = getDurationImpl()
    fun isPlaying(): Boolean = try { if (isPlayerInitialized()) exoPlayer.isPlaying else false } catch (_: Exception) { false }
    fun getCurrentTrack(): MusicItem? = getCurrentTrackImpl()
    fun getPlayer(): ExoPlayer? = if (isPlayerInitialized()) exoPlayer else null

    fun togglePlaybackMode(): Boolean {
        val nextMode = if (mediaMode == PlaybackMediaMode.AUDIO) PlaybackMediaMode.VIDEO else PlaybackMediaMode.AUDIO
        return setPlaybackMediaMode(nextMode)
    }

    fun setPlaybackMediaMode(mode: PlaybackMediaMode): Boolean = setPlaybackMediaModeImpl(mode)

    fun setVideoSurface(surface: Surface?) {
        videoSurface = surface
        if (isPlayerInitialized()) {
            if (surface != null && mediaMode == PlaybackMediaMode.VIDEO) {
                exoPlayer.setVideoSurface(surface)
            } else {
                exoPlayer.clearVideoSurface()
            }
        }
    }

    fun clearVideoSurface() {
        videoSurface = null
        if (isPlayerInitialized()) exoPlayer.clearVideoSurface()
    }

    fun setPlaybackVideoQuality(quality: PlaybackVideoQuality): Boolean = setPlaybackVideoQualityImpl(quality)
    fun getPlaybackState(): PlaybackState = getPlaybackStateImpl()

    fun addPlaybackStateListener(listener: (PlaybackState) -> Unit) {
        playbackStateListeners.add(listener)
        listener(getPlaybackState())
    }

    fun removePlaybackStateListener(listener: (PlaybackState) -> Unit) {
        playbackStateListeners.remove(listener)
    }

    fun addTrackChangedListener(listener: (MusicItem) -> Unit) {
        trackChangedListeners.add(listener)
    }

    fun removeTrackChangedListener(listener: (MusicItem) -> Unit) {
        trackChangedListeners.remove(listener)
    }

    internal fun notifyTrackChanged(track: MusicItem) {
        trackChangedListeners.forEach { it(track) }
    }

    internal fun notifyPlaybackState(
        isLoading: Boolean = false,
        refreshNotification: Boolean = false
    ) {
        val state = getPlaybackState().copy(isLoading = isLoading)
        playbackStateListeners.forEach { it(state) }

        if (refreshNotification && state.currentTrack != null) {
            if (isPlayerInitialized()) updateMediaSessionState()
            updateNotification()
        }
    }

    internal fun startPositionUpdates() {
        stopPositionUpdates()
        positionUpdateJob = serviceScope.launch {
            while (isPlaying()) {
                notifyPlaybackState()
                delay(100L)
            }
        }
    }

    internal fun stopPositionUpdates() {
        positionUpdateJob?.cancel()
        positionUpdateJob = null
    }

    internal fun requestAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { audioManager?.requestAudioFocus(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager?.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
        }
    }

    private fun abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { audioManager?.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager?.abandonAudioFocus(null)
        }
    }

    // Equalizer & Audio Effects delegation
    fun getEqualizerBands(): Short = audioEffectsController.getEqualizerBands()
    fun getBandLevelRange(): ShortArray = audioEffectsController.getBandLevelRange()
    fun getBandLevel(band: Short): Short = audioEffectsController.getBandLevel(band)
    fun setBandLevel(band: Short, level: Short) = audioEffectsController.setBandLevel(band, level)
    fun getCenterFreq(band: Short): Int = audioEffectsController.getCenterFreq(band)
    fun getPresetNames(): List<String> = audioEffectsController.getPresetNames()
    fun usePreset(preset: Short) = audioEffectsController.usePreset(preset)
    fun getBassBoostStrength(): Short = audioEffectsController.getBassBoostStrength()
    fun setBassBoostStrength(strength: Short) = audioEffectsController.setBassBoostStrength(strength)
    fun isBassBoostSupported(): Boolean = audioEffectsController.isBassBoostSupported()
    fun getVirtualizerStrength(): Short = audioEffectsController.getVirtualizerStrength()
    fun setVirtualizerStrength(strength: Short) = audioEffectsController.setVirtualizerStrength(strength)
    fun isVirtualizerSupported(): Boolean = audioEffectsController.isVirtualizerSupported()
    fun getReverbPresets(): List<String> = audioEffectsController.getReverbPresets()
    fun setReverbPreset(preset: Int) = audioEffectsController.setReverbPreset(preset)
    fun getReverbPreset(): Int = audioEffectsController.getReverbPreset()
    fun isReverbSupported(): Boolean = audioEffectsController.isReverbSupported()
    fun getFrequencyData(): ByteArray? = audioEffectsController.getFrequencyData()
    fun isVisualizerSupported(): Boolean = audioEffectsController.isVisualizerSupported()
    fun enableVisualizer(enable: Boolean) = audioEffectsController.enableVisualizer(enable)
    fun addVisualizerListener(listener: (ByteArray) -> Unit) = audioEffectsController.addVisualizerListener(listener)
    fun removeVisualizerListener(listener: (ByteArray) -> Unit) = audioEffectsController.removeVisualizerListener(listener)

    // Queue delegation
    fun addToQueue(items: List<MusicItem>) = addToQueueImpl(items)
    fun playNext(items: List<MusicItem>) = playNextImpl(items)
    fun jumpToIndex(absoluteIndex: Int) { if (!isDestroyed) skipToPlaylistIndex(absoluteIndex) }
    fun removeFromQueue(absoluteIndex: Int) = removeFromQueueImpl(absoluteIndex)
    fun moveQueueItem(fromAbsolute: Int, toAbsolute: Int) = moveQueueItemImpl(fromAbsolute, toAbsolute)
    fun clearUpcoming() = clearUpcomingImpl()
    fun setQueueOrder(newPlaylist: List<MusicItem>) = setQueueOrderImpl(newPlaylist)

    override fun onDestroy() {
        isDestroyed = true
        super.onDestroy()
        try { serviceScope.cancel() } catch (_: Exception) {}
        try { mediaSession.release() } catch (_: Exception) {}
        try { exoPlayer.release() } catch (_: Exception) {}
        try { abandonAudioFocus() } catch (_: Exception) {}
        audioEffectsController.release()
        playbackStateListeners.clear()
        trackChangedListeners.clear()
    }

    companion object {
        internal const val TAG = "MusicPlaybackService"
        const val ACTION_PLAY = "com.israrxy.raazi.ACTION_PLAY"
        const val ACTION_PAUSE = "com.israrxy.raazi.ACTION_PAUSE"
        const val ACTION_NEXT = "com.israrxy.raazi.ACTION_NEXT"
        const val ACTION_PREV = "com.israrxy.raazi.ACTION_PREV"
        const val ACTION_TOGGLE_SHUFFLE = "com.israrxy.raazi.ACTION_TOGGLE_SHUFFLE"
        const val ACTION_TOGGLE_REPEAT = "com.israrxy.raazi.ACTION_TOGGLE_REPEAT"
        const val ACTION_STOP = "com.israrxy.raazi.ACTION_STOP"
        const val ACTION_TOGGLE_FAVORITE = "com.israrxy.raazi.ACTION_TOGGLE_FAVORITE"
        const val ACTION_CYCLE_SPEED = "com.israrxy.raazi.ACTION_CYCLE_SPEED"

        const val NOTIFICATION_LIKE_CHANNEL = "com.israrxy.raazi.LIKE_EVENT"
        const val EXTRA_TRACK_ID = "track_id"

        val SPEED_CYCLE_PRESETS = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)
        const val MIN_PLAYBACK_SPEED = 0.25f
        const val MAX_PLAYBACK_SPEED = 3.0f
    }
}
