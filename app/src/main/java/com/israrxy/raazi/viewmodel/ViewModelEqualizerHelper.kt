package com.israrxy.raazi.viewmodel

import android.util.Base64
import android.util.Log
import androidx.lifecycle.viewModelScope
import com.israrxy.raazi.data.local.CustomEqualizerPreset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.json.JSONObject

internal val genrePresets = mapOf(
    "Rock" to listOf(300, 500, 800, 1200, 2000, 3000, 4000, 6000, 8000, 12000),
    "Pop" to listOf(200, 400, 600, 1000, 1500, 2500, 3500, 5000, 7000, 10000),
    "Jazz" to listOf(100, 300, 500, 800, 1200, 2000, 3000, 4500, 6000, 8000),
    "Classical" to listOf(0, 0, 100, 200, 300, 400, 500, 600, 700, 800),
    "Electronic" to listOf(500, 600, 700, 800, 1000, 1500, 2500, 4000, 6000, 8000),
    "Hip-Hop" to listOf(600, 700, 600, 300, 200, 300, 400, 500, 600, 700),
    "Acoustic" to listOf(200, 300, 400, 500, 600, 700, 800, 900, 1000, 1100),
    "Blues" to listOf(300, 400, 500, 600, 700, 800, 900, 1000, 1100, 1200),
    "Metal" to listOf(500, 600, 800, 1000, 1200, 2000, 3000, 4000, 6000, 8000),
    "Podcast" to listOf(-300, -200, -100, 0, 100, 200, 300, 400, 500, 600)
)

internal fun MusicPlayerViewModel.loadEqualizerStateImpl() {
    playbackService?.let { service ->
        try {
            val bands = service.getEqualizerBands()
            if (bands > 0) {
                val range = service.getBandLevelRange()
                val systemPresets = service.getPresetNames()
                val allPresets = systemPresets + genrePresets.keys

                viewModelScope.launch {
                    val savedLevelsStr = settingsDataStore.equalizerLevels.first()
                    val savedPreset = settingsDataStore.equalizerPreset.first() ?: "Custom"
                    val savedBassBoost = settingsDataStore.bassBoostStrength.first()
                    val savedVirtualizer = settingsDataStore.virtualizerStrength.first()
                    val savedReverb = settingsDataStore.reverbPreset.first()

                    val levels = mutableListOf<Short>()
                    val freqs = mutableListOf<Int>()

                    if (savedLevelsStr != null) {
                        try {
                            val savedLevels = savedLevelsStr.split(",").map { it.toShort() }
                            for (i in 0 until bands) {
                                val level = savedLevels.getOrElse(i) { service.getBandLevel(i.toShort()) }
                                service.setBandLevel(i.toShort(), level)
                                levels.add(level)
                                freqs.add(service.getCenterFreq(i.toShort()))
                            }
                        } catch (_: Exception) {
                            for (i in 0 until bands) {
                                levels.add(service.getBandLevel(i.toShort()))
                                freqs.add(service.getCenterFreq(i.toShort()))
                            }
                        }
                    } else {
                        for (i in 0 until bands) {
                            levels.add(service.getBandLevel(i.toShort()))
                            freqs.add(service.getCenterFreq(i.toShort()))
                        }
                    }

                    savedBassBoost?.let { service.setBassBoostStrength(it) }
                    savedVirtualizer?.let { service.setVirtualizerStrength(it) }
                    savedReverb?.let { service.setReverbPreset(it) }

                    _equalizerState.value = MusicPlayerViewModel.EqualizerState(
                        bands = bands,
                        minLevel = range[0],
                        maxLevel = range[1],
                        currentLevels = levels,
                        centerFreqs = freqs,
                        presets = allPresets,
                        currentPreset = savedPreset,
                        bassBoostStrength = savedBassBoost ?: 0,
                        virtualizerStrength = savedVirtualizer ?: 0,
                        reverbPreset = savedReverb ?: 0,
                        bassBoostSupported = service.isBassBoostSupported(),
                        virtualizerSupported = service.isVirtualizerSupported(),
                        reverbSupported = service.isReverbSupported()
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("MusicVM", "Error loading EQ", e)
        }
    }
}

internal fun MusicPlayerViewModel.setBandLevelImpl(band: Short, level: Short) {
    playbackService?.setBandLevel(band, level)
    val current = _equalizerState.value
    val newLevels = current.currentLevels.toMutableList()
    if (band < newLevels.size) {
        newLevels[band.toInt()] = level
        _equalizerState.value = current.copy(currentLevels = newLevels, currentPreset = "Custom")
    }
}

internal fun MusicPlayerViewModel.usePresetImpl(presetName: String) {
    playbackService?.let { service ->
        val current = _equalizerState.value

        if (genrePresets.containsKey(presetName)) {
            val genreLevels = genrePresets[presetName]!!
            val adjustedLevels = mutableListOf<Short>()

            val range = service.getBandLevelRange()
            val minLevel = range[0]
            val maxLevel = range[1]

            for (i in 0 until current.bands) {
                val freqValue = if (i < genreLevels.size) genreLevels[i] else 0
                val level = ((freqValue.toFloat() / 12000f) * (maxLevel - minLevel) + minLevel).toInt().toShort()
                    .coerceIn(minLevel, maxLevel)
                service.setBandLevel(i.toShort(), level)
                adjustedLevels.add(level)
            }

            _equalizerState.value = _equalizerState.value.copy(
                currentLevels = adjustedLevels,
                currentPreset = presetName
            )
        } else {
            val systemPresets = service.getPresetNames()
            val index = systemPresets.indexOf(presetName)
            if (index != -1) {
                service.usePreset(index.toShort())
                val bands = service.getEqualizerBands()
                val levels = mutableListOf<Short>()
                for (i in 0 until bands) {
                    levels.add(service.getBandLevel(i.toShort()))
                }
                _equalizerState.value = _equalizerState.value.copy(
                    currentLevels = levels,
                    currentPreset = presetName
                )
            }
        }
    }
}

internal fun MusicPlayerViewModel.saveEqualizerSettingsImpl() {
    val current = _equalizerState.value
    val levelsStr = current.currentLevels.joinToString(",")
    viewModelScope.launch {
        settingsDataStore.setEqualizerPreset(current.currentPreset)
        settingsDataStore.setEqualizerLevels(levelsStr)
        settingsDataStore.setBassBoostStrength(current.bassBoostStrength)
        settingsDataStore.setVirtualizerStrength(current.virtualizerStrength)
        settingsDataStore.setReverbPreset(current.reverbPreset)
        connectedAudioDevice.value?.address?.takeIf { it.isNotBlank() }?.let { addr ->
            settingsDataStore.setDeviceEqProfile(addr, current.currentPreset)
        }
    }
}

internal fun MusicPlayerViewModel.applyDeviceEqProfileIfAnyImpl() {
    val device = connectedAudioDevice.value ?: return
    if (device.address.isBlank()) return
    viewModelScope.launch {
        val preset = settingsDataStore.getDeviceEqProfile(device.address) ?: return@launch
        if (preset != _equalizerState.value.currentPreset) {
            withContext(Dispatchers.Main) { usePreset(preset) }
        }
    }
}

internal fun MusicPlayerViewModel.setBassBoostStrengthImpl(strength: Short) {
    playbackService?.setBassBoostStrength(strength)
    _equalizerState.value = _equalizerState.value.copy(
        bassBoostStrength = strength,
        currentPreset = "Custom"
    )
}

internal fun MusicPlayerViewModel.setVirtualizerStrengthImpl(strength: Short) {
    playbackService?.setVirtualizerStrength(strength)
    _equalizerState.value = _equalizerState.value.copy(
        virtualizerStrength = strength,
        currentPreset = "Custom"
    )
}

internal fun MusicPlayerViewModel.setReverbPresetImpl(preset: Int) {
    playbackService?.setReverbPreset(preset)
    _equalizerState.value = _equalizerState.value.copy(
        reverbPreset = preset,
        currentPreset = "Custom"
    )
}

internal fun MusicPlayerViewModel.saveCustomPresetImpl(name: String) {
    val current = _equalizerState.value
    val newPreset = MusicPlayerViewModel.CustomPreset(
        name = name,
        levels = current.currentLevels,
        bassBoost = current.bassBoostStrength,
        virtualizer = current.virtualizerStrength,
        reverb = current.reverbPreset
    )

    val updatedPresets = _customPresets.value.toMutableList()
    updatedPresets.add(newPreset)
    _customPresets.value = updatedPresets

    viewModelScope.launch {
        val serializablePresets = updatedPresets.map { preset ->
            Json { ignoreUnknownKeys = true }.encodeToString(
                CustomEqualizerPreset(
                    name = preset.name,
                    levels = preset.levels.joinToString(","),
                    bassBoost = preset.bassBoost,
                    virtualizer = preset.virtualizer,
                    reverb = preset.reverb
                )
            )
        }
        settingsDataStore.updateCustomPresets("[${serializablePresets.joinToString(",")}]")
    }
}

internal fun MusicPlayerViewModel.loadCustomPresetImpl(preset: MusicPlayerViewModel.CustomPreset) {
    playbackService?.let { service ->
        preset.levels.forEachIndexed { index, level ->
            if (index < _equalizerState.value.bands) {
                service.setBandLevel(index.toShort(), level)
            }
        }
        service.setBassBoostStrength(preset.bassBoost)
        service.setVirtualizerStrength(preset.virtualizer)
        service.setReverbPreset(preset.reverb)

        _equalizerState.value = _equalizerState.value.copy(
            currentLevels = preset.levels,
            bassBoostStrength = preset.bassBoost,
            virtualizerStrength = preset.virtualizer,
            reverbPreset = preset.reverb,
            currentPreset = preset.name
        )
    }
}

internal fun MusicPlayerViewModel.deleteCustomPresetImpl(preset: MusicPlayerViewModel.CustomPreset) {
    val updatedPresets = _customPresets.value.filter { it.name != preset.name }
    _customPresets.value = updatedPresets
    viewModelScope.launch {
        settingsDataStore.deleteCustomPreset(preset.name)
    }
}

internal fun MusicPlayerViewModel.loadCustomPresetsImpl() {
    viewModelScope.launch {
        val presetsStr = settingsDataStore.customPresets.first()
        if (!presetsStr.isNullOrEmpty()) {
            try {
                val json = Json { ignoreUnknownKeys = true }
                val serializablePresets = json.decodeFromString<List<CustomEqualizerPreset>>(presetsStr)
                val customPresets = serializablePresets.map { sp ->
                    MusicPlayerViewModel.CustomPreset(
                        name = sp.name,
                        levels = sp.levels.split(",").map { it.trim().toShort() },
                        bassBoost = sp.bassBoost,
                        virtualizer = sp.virtualizer,
                        reverb = sp.reverb
                    )
                }
                _customPresets.value = customPresets
            } catch (e: Exception) {
                Log.e("MusicVM", "Error loading custom presets", e)
            }
        }
    }
}

internal fun MusicPlayerViewModel.exportEqualizerSettingsImpl(): String {
    val current = _equalizerState.value
    val exportData = mapOf(
        "preset" to current.currentPreset,
        "levels" to current.currentLevels.joinToString(","),
        "bassBoost" to current.bassBoostStrength,
        "virtualizer" to current.virtualizerStrength,
        "reverb" to current.reverbPreset,
        "customPresets" to _customPresets.value.map {
            mapOf(
                "name" to it.name,
                "levels" to it.levels.joinToString(","),
                "bassBoost" to it.bassBoost,
                "virtualizer" to it.virtualizer,
                "reverb" to it.reverb
            )
        }
    )
    return Base64.encodeToString(
        JSONObject(exportData).toString().toByteArray(),
        Base64.NO_WRAP
    )
}
