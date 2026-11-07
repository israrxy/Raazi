package com.israrxy.raazi.service

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.audiofx.AudioEffect
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.PresetReverb
import android.media.audiofx.Virtualizer
import android.media.audiofx.Visualizer
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.media3.exoplayer.ExoPlayer
import java.util.UUID
import java.util.concurrent.CopyOnWriteArraySet

internal class PlaybackAudioEffectsController(
    private val context: Context,
    private val exoPlayerProvider: () -> ExoPlayer?,
    private val isDestroyedProvider: () -> Boolean
) {
    private var audioEffectsInitRetryCount = 0
    private var initializedSessionId = 0
    private var audioEqualizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null
    private var presetReverb: PresetReverb? = null
    private var visualizer: Visualizer? = null
    private var visualizerData: ByteArray? = null
    private var bassBoostUnsupported = false
    private var virtualizerUnsupported = false
    private var presetReverbUnsupported = false
    private var visualizerUnsupported = false
    private var cachedBassBoostStrength: Short = 0
    private var cachedVirtualizerStrength: Short = 0
    private var cachedReverbPreset: Int = 0
    private val visualizerListeners = CopyOnWriteArraySet<(ByteArray) -> Unit>()
    private val mainHandler = Handler(Looper.getMainLooper())

    private var visualizerRequested = false
    private var visualizerSessionId = 0
    private var visualizerRetryCount = 0
    private var visualizerFailureCount = 0

    private val visualizerRetryRunnable = Runnable {
        if (isDestroyedProvider() || !visualizerRequested || visualizerUnsupported) return@Runnable
        if (ensureVisualizerInitialized()) {
            visualizerRetryCount = 0
            runCatching { visualizer?.enabled = true }
        } else {
            scheduleVisualizerRetry()
        }
    }

    private fun isEmulatorAudioHal(): Boolean {
        val hardware = Build.HARDWARE.lowercase()
        val fingerprint = Build.FINGERPRINT.lowercase()
        val product = Build.PRODUCT.lowercase()
        val model = Build.MODEL.lowercase()
        val brand = Build.BRAND.lowercase()
        val device = Build.DEVICE.lowercase()
        return hardware.contains("ranchu") ||
            hardware.contains("goldfish") ||
            hardware.contains("cutf") ||
            hardware.contains("vsoc") ||
            fingerprint.startsWith("generic") ||
            fingerprint.contains("emulator") ||
            product.contains("sdk") ||
            product.contains("emulator") ||
            model.contains("sdk") ||
            model.contains("emulator") ||
            (brand.startsWith("generic") && device.startsWith("generic"))
    }

    private fun isAudioEffectAvailable(
        effectTypeUuid: UUID,
        requireInsertMode: Boolean = true
    ): Boolean {
        if (effectTypeUuid != AudioEffect.EFFECT_TYPE_EQUALIZER && isEmulatorAudioHal()) {
            return false
        }
        return runCatching {
            AudioEffect.queryEffects()?.any { descriptor ->
                descriptor.type == effectTypeUuid &&
                    (!requireInsertMode || descriptor.connectMode == AudioEffect.EFFECT_INSERT)
            } ?: false
        }.getOrDefault(false)
    }

    private fun hasRecordAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun initializeAudioEffects() {
        if (isDestroyedProvider()) return
        try {
            val player = exoPlayerProvider() ?: return
            val audioSessionId = player.audioSessionId
            if (audioSessionId == 0) {
                if (audioEffectsInitRetryCount < MAX_AUDIO_EFFECTS_RETRIES) {
                    audioEffectsInitRetryCount++
                    Log.w(
                        "MusicService",
                        "Audio session not ready, retry $audioEffectsInitRetryCount/$MAX_AUDIO_EFFECTS_RETRIES"
                    )
                    mainHandler.postDelayed({
                        if (!isDestroyedProvider()) initializeAudioEffects()
                    }, 1000L)
                }
                return
            }
            audioEffectsInitRetryCount = 0

            if (initializedSessionId == audioSessionId && audioEqualizer != null) {
                // Equalizer already lives on this session; only make sure the
                // visualizer is attached to it as well.
                if (visualizerRequested && hasRecordAudioPermission()) {
                    if (ensureVisualizerInitialized()) {
                        runCatching { visualizer?.enabled = true }
                    }
                }
                return
            }

            audioEqualizer?.release()
            bassBoost?.release()
            virtualizer?.release()
            presetReverb?.release()
            releaseVisualizer()
            audioEqualizer = null
            bassBoost = null
            virtualizer = null
            presetReverb = null
            initializedSessionId = audioSessionId

            if (isAudioEffectAvailable(AudioEffect.EFFECT_TYPE_EQUALIZER)) {
                runCatching {
                    audioEqualizer = Equalizer(0, audioSessionId).apply {
                        enabled = true
                    }
                }.onFailure { e ->
                    Log.w("MusicService", "Equalizer unavailable: ${e.message}")
                }
            }

            if (cachedBassBoostStrength > 0) {
                setBassBoostStrength(cachedBassBoostStrength)
            }
            if (cachedVirtualizerStrength > 0) {
                setVirtualizerStrength(cachedVirtualizerStrength)
            }
            if (cachedReverbPreset > 0) {
                setReverbPreset(cachedReverbPreset)
            }

            if (visualizerRequested && hasRecordAudioPermission()) {
                if (ensureVisualizerInitialized()) {
                    runCatching { visualizer?.enabled = true }
                }
            }

            Log.d("MusicService", "Advanced audio effects initialized successfully")
        } catch (e: Exception) {
            Log.w("MusicService", "Error initializing audio effects: ${e.message}")
        }
    }

    private fun ensureBassBoostInitialized(): BassBoost? {
        bassBoost?.let { return it }
        if (bassBoostUnsupported || isDestroyedProvider() || !isAudioEffectAvailable(AudioEffect.EFFECT_TYPE_BASS_BOOST)) {
            return null
        }
        val audioSessionId = initializedSessionId.takeIf { it != 0 }
            ?: runCatching { exoPlayerProvider()?.audioSessionId ?: 0 }.getOrDefault(0)
        if (audioSessionId == 0) return null
        return runCatching {
            BassBoost(0, audioSessionId).apply {
                enabled = true
                bassBoost = this
            }
        }.getOrElse { e ->
            bassBoostUnsupported = true
            Log.w("MusicService", "BassBoost unavailable: ${e.message}")
            null
        }
    }

    private fun ensureVirtualizerInitialized(): Virtualizer? {
        virtualizer?.let { return it }
        if (virtualizerUnsupported || isDestroyedProvider() || !isAudioEffectAvailable(AudioEffect.EFFECT_TYPE_VIRTUALIZER)) {
            return null
        }
        val audioSessionId = initializedSessionId.takeIf { it != 0 }
            ?: runCatching { exoPlayerProvider()?.audioSessionId ?: 0 }.getOrDefault(0)
        if (audioSessionId == 0) return null
        return runCatching {
            Virtualizer(0, audioSessionId).apply {
                enabled = true
                virtualizer = this
            }
        }.getOrElse { e ->
            virtualizerUnsupported = true
            Log.w("MusicService", "Virtualizer unavailable: ${e.message}")
            null
        }
    }

    private fun ensurePresetReverbInitialized(): PresetReverb? {
        presetReverb?.let { return it }
        if (presetReverbUnsupported || isDestroyedProvider() || !isAudioEffectAvailable(AudioEffect.EFFECT_TYPE_PRESET_REVERB, requireInsertMode = true)) {
            return null
        }
        val audioSessionId = initializedSessionId.takeIf { it != 0 }
            ?: runCatching { exoPlayerProvider()?.audioSessionId ?: 0 }.getOrDefault(0)
        if (audioSessionId == 0) return null
        return runCatching {
            PresetReverb(0, audioSessionId).apply {
                enabled = true
                presetReverb = this
            }
        }.getOrElse { e ->
            presetReverbUnsupported = true
            Log.w("MusicService", "PresetReverb unavailable: ${e.message}")
            null
        }
    }

    private fun ensureVisualizerInitialized(): Boolean {
        if (isDestroyedProvider() || visualizerUnsupported) return false
        if (!hasRecordAudioPermission()) return false

        val audioSessionId = runCatching { exoPlayerProvider()?.audioSessionId ?: 0 }.getOrDefault(0)
        if (audioSessionId == 0) return false

        if (visualizer != null && visualizerSessionId == audioSessionId) return true
        if (visualizer != null) releaseVisualizer()

        return runCatching {
            val createdVisualizer = Visualizer(audioSessionId)
            createdVisualizer.enabled = false
            val captureRange = Visualizer.getCaptureSizeRange()
            if (captureRange.size >= 2) {
                createdVisualizer.captureSize = captureRange[1]
            }
            val captureListener = object : Visualizer.OnDataCaptureListener {
                override fun onWaveFormDataCapture(visualizer: Visualizer?, waveform: ByteArray?, samplingRate: Int) {}
                override fun onFftDataCapture(visualizer: Visualizer?, fft: ByteArray?, samplingRate: Int) {
                    fft?.let { data ->
                        // The capture buffer is only valid during this callback.
                        // Copy it before caching/emitting: the engine may reuse
                        // the same array, which would also make StateFlow skip
                        // updates (ByteArray compares by reference).
                        val snapshot = data.copyOf()
                        visualizerData = snapshot
                        visualizerListeners.forEach { listener ->
                            try {
                                listener(snapshot)
                            } catch (e: Exception) {
                                Log.w("MusicService", "Error notifying visualizer listener: ${e.message}")
                            }
                        }
                    }
                }
            }
            val captureResult = createdVisualizer.setDataCaptureListener(
                captureListener,
                Visualizer.getMaxCaptureRate() / 2,
                false,
                true
            )
            if (captureResult != Visualizer.SUCCESS) {
                error("setDataCaptureListener failed: $captureResult")
            }
            visualizer = createdVisualizer
            visualizerSessionId = audioSessionId
            visualizerFailureCount = 0
            Log.d("MusicService", "Visualizer initialized on session $audioSessionId")
            true
        }.getOrElse { e ->
            visualizerFailureCount++
            if (e is UnsupportedOperationException || visualizerFailureCount >= MAX_VISUALIZER_FAILURES) {
                visualizerUnsupported = true
            }
            Log.w(
                "MusicService",
                "Visualizer unavailable on session $audioSessionId (${e.message}), attempt $visualizerFailureCount"
            )
            false
        }
    }

    private fun releaseVisualizer() {
        mainHandler.removeCallbacks(visualizerRetryRunnable)
        runCatching { visualizer?.release() }
        visualizer = null
        visualizerSessionId = 0
    }

    private fun scheduleVisualizerRetry() {
        if (isDestroyedProvider() || !visualizerRequested || visualizerUnsupported) return
        if (visualizerRetryCount >= MAX_VISUALIZER_RETRIES) {
            Log.w("MusicService", "Visualizer retries exhausted")
            return
        }
        visualizerRetryCount++
        mainHandler.removeCallbacks(visualizerRetryRunnable)
        mainHandler.postDelayed(visualizerRetryRunnable, VISUALIZER_RETRY_DELAY_MS)
    }

    fun getEqualizerBands(): Short = audioEqualizer?.numberOfBands ?: 0

    fun getBandLevelRange(): ShortArray = audioEqualizer?.bandLevelRange ?: shortArrayOf(0, 0)

    fun getBandLevel(band: Short): Short = audioEqualizer?.getBandLevel(band) ?: 0

    fun setBandLevel(band: Short, level: Short) {
        audioEqualizer?.setBandLevel(band, level)
    }

    fun getCenterFreq(band: Short): Int = audioEqualizer?.getCenterFreq(band) ?: 0

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

    fun getBassBoostStrength(): Short = bassBoost?.roundedStrength ?: cachedBassBoostStrength

    fun setBassBoostStrength(strength: Short) {
        cachedBassBoostStrength = strength
        if (strength <= 0 && bassBoost == null) return
        runCatching {
            ensureBassBoostInitialized()?.setStrength(strength)
        }
    }

    fun isBassBoostSupported(): Boolean =
        bassBoost != null || (!bassBoostUnsupported && isAudioEffectAvailable(AudioEffect.EFFECT_TYPE_BASS_BOOST))

    fun getVirtualizerStrength(): Short = virtualizer?.roundedStrength ?: cachedVirtualizerStrength

    fun setVirtualizerStrength(strength: Short) {
        cachedVirtualizerStrength = strength
        if (strength <= 0 && virtualizer == null) return
        runCatching {
            ensureVirtualizerInitialized()?.setStrength(strength)
        }
    }

    fun isVirtualizerSupported(): Boolean =
        virtualizer != null || (!virtualizerUnsupported && isAudioEffectAvailable(AudioEffect.EFFECT_TYPE_VIRTUALIZER))

    fun getReverbPresets(): List<String> = listOf(
        "None", "Small Room", "Medium Room", "Large Room",
        "Medium Hall", "Large Hall", "Plate"
    )

    fun setReverbPreset(preset: Int) {
        cachedReverbPreset = preset
        if (preset <= 0 && presetReverb == null) return
        runCatching {
            ensurePresetReverbInitialized()?.preset = preset.toShort()
        }
    }

    fun getReverbPreset(): Int = presetReverb?.preset?.toInt() ?: cachedReverbPreset

    fun isReverbSupported(): Boolean =
        presetReverb != null || (!presetReverbUnsupported && isAudioEffectAvailable(AudioEffect.EFFECT_TYPE_PRESET_REVERB, requireInsertMode = true))

    fun getFrequencyData(): ByteArray? = visualizerData

    fun isVisualizerSupported(): Boolean =
        visualizer != null || (hasRecordAudioPermission() && !visualizerUnsupported)

    fun enableVisualizer(enable: Boolean) {
        visualizerRequested = enable
        if (!enable) {
            mainHandler.removeCallbacks(visualizerRetryRunnable)
            runCatching { visualizer?.enabled = false }
                .onFailure { Log.w("MusicService", "Could not disable visualizer: ${it.message}") }
            return
        }
        visualizerRetryCount = 0
        if (ensureVisualizerInitialized()) {
            runCatching { visualizer?.enabled = true }
                .onFailure { Log.w("MusicService", "Could not enable visualizer: ${it.message}") }
            Log.d("MusicService", "Visualizer enabled")
        } else if (!visualizerUnsupported && hasRecordAudioPermission()) {
            // No playback session yet (nothing playing). Keep polling so the
            // spectrum starts as soon as audio actually begins.
            scheduleVisualizerRetry()
        }
    }

    fun onPlaybackReady() {
        val currentSessionId = runCatching { exoPlayerProvider()?.audioSessionId ?: 0 }.getOrDefault(0)
        if (currentSessionId == 0) return
        if (currentSessionId != initializedSessionId) {
            audioEffectsInitRetryCount = 0
            visualizerUnsupported = false
            visualizerFailureCount = 0
            initializeAudioEffects()
        }
        if (visualizerRequested) enableVisualizer(true)
    }

    fun addVisualizerListener(listener: (ByteArray) -> Unit) {
        visualizerListeners.add(listener)
    }

    fun removeVisualizerListener(listener: (ByteArray) -> Unit) {
        visualizerListeners.remove(listener)
        if (visualizerListeners.isEmpty()) {
            enableVisualizer(false)
        }
    }

    fun release() {
        visualizerRequested = false
        visualizerRetryCount = 0
        releaseVisualizer()
        try {
            audioEqualizer?.release()
            bassBoost?.release()
            virtualizer?.release()
            presetReverb?.release()
        } catch (e: Exception) {
            Log.w("MusicService", "Error releasing audio effects: ${e.message}")
        }
        visualizerListeners.clear()
    }

    companion object {
        private const val MAX_AUDIO_EFFECTS_RETRIES = 3
        private const val MAX_VISUALIZER_RETRIES = 20
        private const val VISUALIZER_RETRY_DELAY_MS = 750L
        private const val MAX_VISUALIZER_FAILURES = 4
    }
}
