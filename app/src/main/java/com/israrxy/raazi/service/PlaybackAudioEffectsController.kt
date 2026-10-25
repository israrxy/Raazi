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
    private var audioEqualizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null
    private var presetReverb: PresetReverb? = null
    private var visualizer: Visualizer? = null
    private var visualizerData: ByteArray? = null
    private val visualizerListeners = CopyOnWriteArraySet<(ByteArray) -> Unit>()

    private fun isAudioEffectAvailable(effectTypeUuid: UUID): Boolean {
        return runCatching {
            AudioEffect.queryEffects()?.any { descriptor ->
                descriptor.type == effectTypeUuid
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
        val visualizerUuid = UUID.fromString("e46b26a0-dddd-11db-8afd-0002a5d5c51b")
        return isAudioEffectAvailable(visualizerUuid)
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

            if (isAudioEffectAvailable(AudioEffect.EFFECT_TYPE_EQUALIZER)) {
                runCatching {
                    audioEqualizer = Equalizer(0, audioSessionId).apply {
                        enabled = true
                    }
                }.onFailure { e ->
                    Log.w("MusicService", "Equalizer unavailable: ${e.message}")
                }
            }

            if (isAudioEffectAvailable(AudioEffect.EFFECT_TYPE_BASS_BOOST)) {
                runCatching {
                    bassBoost = BassBoost(0, audioSessionId).apply {
                        enabled = true
                    }
                }.onFailure { e ->
                    Log.w("MusicService", "BassBoost unavailable: ${e.message}")
                }
            }

            if (isAudioEffectAvailable(AudioEffect.EFFECT_TYPE_VIRTUALIZER)) {
                runCatching {
                    virtualizer = Virtualizer(0, audioSessionId).apply {
                        enabled = true
                    }
                }.onFailure { e ->
                    Log.w("MusicService", "Virtualizer unavailable: ${e.message}")
                }
            }

            if (isAudioEffectAvailable(AudioEffect.EFFECT_TYPE_PRESET_REVERB)) {
                runCatching {
                    presetReverb = PresetReverb(0, audioSessionId).apply {
                        enabled = true
                    }
                }.onFailure { e ->
                    Log.w("MusicService", "PresetReverb unavailable: ${e.message}")
                }
            }

            if (visualizerListeners.isNotEmpty() && hasRecordAudioPermission()) {
                ensureVisualizerInitialized()
            }

            Log.d("MusicService", "Advanced audio effects initialized successfully")
        } catch (e: Exception) {
            Log.w("MusicService", "Error initializing audio effects: ${e.message}")
        }
    }

    private fun ensureVisualizerInitialized(): Boolean {
        if (isDestroyedProvider()) return false
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

    fun getBassBoostStrength(): Short = bassBoost?.roundedStrength ?: 0

    fun setBassBoostStrength(strength: Short) {
        bassBoost?.setStrength(strength)
    }

    fun isBassBoostSupported(): Boolean = bassBoost != null

    fun getVirtualizerStrength(): Short = virtualizer?.roundedStrength ?: 0

    fun setVirtualizerStrength(strength: Short) {
        virtualizer?.setStrength(strength)
    }

    fun isVirtualizerSupported(): Boolean = virtualizer != null

    fun getReverbPresets(): List<String> = listOf(
        "None", "Small Room", "Medium Room", "Large Room",
        "Medium Hall", "Large Hall", "Plate"
    )

    fun setReverbPreset(preset: Int) {
        presetReverb?.preset = preset.toShort()
    }

    fun getReverbPreset(): Int = presetReverb?.preset?.toInt() ?: 0

    fun isReverbSupported(): Boolean = presetReverb != null

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
