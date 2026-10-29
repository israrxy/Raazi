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

    private fun isVisualizerEffectAvailable(): Boolean {
        if (visualizerUnsupported || isEmulatorAudioHal()) return false
        val visualizerUuid = UUID.fromString("e46b26a0-dddd-11db-8afd-0002a5d5c51b")
        return isAudioEffectAvailable(visualizerUuid, requireInsertMode = false)
    }

    fun initializeAudioEffects() {
        if (isDestroyedProvider() || audioEffectsInitRetryCount >= MAX_AUDIO_EFFECTS_RETRIES) {
            if (audioEffectsInitRetryCount >= MAX_AUDIO_EFFECTS_RETRIES) {
                Log.w("MusicService", "Max audio effects init retries reached")
            }
            return
        }
        try {
            val player = exoPlayerProvider() ?: return
            val audioSessionId = player.audioSessionId
            if (audioSessionId == 0) {
                audioEffectsInitRetryCount++
                Log.w("MusicService", "Audio session not ready, retry $audioEffectsInitRetryCount/$MAX_AUDIO_EFFECTS_RETRIES")
                Handler(Looper.getMainLooper()).postDelayed({
                    if (!isDestroyedProvider()) initializeAudioEffects()
                }, 1000)
                return
            }

            if (initializedSessionId == audioSessionId && audioEqualizer != null) {
                return
            }

            audioEqualizer?.release()
            bassBoost?.release()
            virtualizer?.release()
            presetReverb?.release()
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

            if (visualizerListeners.isNotEmpty() && hasRecordAudioPermission() && isVisualizerEffectAvailable()) {
                ensureVisualizerInitialized()
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
        if (visualizer != null) return true
        if (!hasRecordAudioPermission() || !isVisualizerEffectAvailable()) {
            return false
        }
        val audioSessionId = runCatching { exoPlayerProvider()?.audioSessionId ?: 0 }.getOrDefault(0)
        if (audioSessionId == 0) return false

        return runCatching {
            val createdVisualizer = Visualizer(audioSessionId)
            createdVisualizer.enabled = false
            val captureRange = Visualizer.getCaptureSizeRange()
            if (captureRange.size >= 2) {
                createdVisualizer.captureSize = captureRange[1]
            }
            createdVisualizer.setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                override fun onWaveFormDataCapture(visualizer: Visualizer?, waveform: ByteArray?, samplingRate: Int) {}
                override fun onFftDataCapture(visualizer: Visualizer?, fft: ByteArray?, samplingRate: Int) {
                    fft?.let { data ->
                        visualizerData = data
                        visualizerListeners.forEach { listener ->
                            try {
                                listener(data)
                            } catch (e: Exception) {
                                Log.w("MusicService", "Error notifying visualizer listener: ${e.message}")
                            }
                        }
                    }
                }
            }, Visualizer.getMaxCaptureRate() / 2, false, true)
            visualizer = createdVisualizer
            Log.d("MusicService", "Visualizer initialized successfully")
            true
        }.getOrElse { e ->
            visualizerUnsupported = true
            Log.w("MusicService", "Visualizer unavailable on this session: ${e.message}")
            false
        }
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

    fun isVisualizerSupported(): Boolean {
        return visualizer != null || (hasRecordAudioPermission() && isVisualizerEffectAvailable())
    }

    fun enableVisualizer(enable: Boolean) {
        try {
            if (enable && !ensureVisualizerInitialized()) {
                return
            }
            visualizer?.enabled = enable
            Log.d("MusicService", "Visualizer enabled: $enable")
        } catch (e: Exception) {
            Log.w("MusicService", "Could not toggle visualizer: ${e.message}")
        }
    }

    fun addVisualizerListener(listener: (ByteArray) -> Unit) {
        visualizerListeners.add(listener)
    }

    fun removeVisualizerListener(listener: (ByteArray) -> Unit) {
        visualizerListeners.remove(listener)
        if (visualizerListeners.isEmpty() && visualizer != null) {
            enableVisualizer(false)
        }
    }

    fun release() {
        try {
            visualizer?.release()
            visualizer = null
        } catch (e: Exception) {
            Log.w("MusicService", "Error releasing visualizer: ${e.message}")
        }
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
    }
}
