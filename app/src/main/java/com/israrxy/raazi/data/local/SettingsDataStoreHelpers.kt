package com.israrxy.raazi.data.local

import androidx.datastore.preferences.core.MutablePreferences
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class CustomEqualizerPreset(
    val name: String,
    val levels: String,
    val bassBoost: Short,
    val virtualizer: Short,
    val reverb: Int
)

internal fun filterOutCustomPreset(currentPresetsJson: String, nameToRemove: String): String? {
    return try {
        val presets = Json.decodeFromString<List<CustomEqualizerPreset>>(currentPresetsJson)
        val updatedPresets = presets.filter { it.name != nameToRemove }
        Json.encodeToString(updatedPresets)
    } catch (_: Exception) {
        null
    }
}

internal fun decodeDeviceEqProfile(rawJson: String?, deviceAddress: String): String? {
    if (rawJson.isNullOrBlank()) return null
    return runCatching { Json.decodeFromString<Map<String, String>>(rawJson) }
        .getOrNull()
        ?.get(deviceAddress)
}

internal fun encodeUpdatedDeviceEqProfiles(
    rawJson: String?,
    deviceAddress: String,
    presetName: String
): String {
    val current = runCatching {
        rawJson?.let { Json.decodeFromString<Map<String, String>>(it) } ?: emptyMap()
    }.getOrDefault(emptyMap())
    val updated = current.toMutableMap().apply { this[deviceAddress] = presetName }
    return Json.encodeToString(updated)
}

internal fun MutablePreferences.applyYouTubeSession(
    cookie: String,
    visitorData: String,
    dataSyncId: String?
) {
    this[SettingsDataStore.INNER_TUBE_COOKIE_KEY] = cookie
    this[SettingsDataStore.VISITOR_DATA_KEY] = visitorData
    if (!dataSyncId.isNullOrBlank()) {
        this[SettingsDataStore.DATA_SYNC_ID_KEY] = dataSyncId
    } else {
        remove(SettingsDataStore.DATA_SYNC_ID_KEY)
    }
}

internal fun MutablePreferences.applyYouTubeAccountInfo(
    name: String?,
    email: String?,
    channelHandle: String?
) {
    if (!name.isNullOrBlank()) {
        this[SettingsDataStore.ACCOUNT_NAME_KEY] = name
    } else {
        remove(SettingsDataStore.ACCOUNT_NAME_KEY)
    }
    if (!email.isNullOrBlank()) {
        this[SettingsDataStore.ACCOUNT_EMAIL_KEY] = email
    } else {
        remove(SettingsDataStore.ACCOUNT_EMAIL_KEY)
    }
    if (!channelHandle.isNullOrBlank()) {
        this[SettingsDataStore.ACCOUNT_CHANNEL_HANDLE_KEY] = channelHandle
    } else {
        remove(SettingsDataStore.ACCOUNT_CHANNEL_HANDLE_KEY)
    }
}

internal fun MutablePreferences.clearYouTubeAccountPrefs() {
    remove(SettingsDataStore.INNER_TUBE_COOKIE_KEY)
    remove(SettingsDataStore.VISITOR_DATA_KEY)
    remove(SettingsDataStore.DATA_SYNC_ID_KEY)
    remove(SettingsDataStore.ACCOUNT_NAME_KEY)
    remove(SettingsDataStore.ACCOUNT_EMAIL_KEY)
    remove(SettingsDataStore.ACCOUNT_CHANNEL_HANDLE_KEY)
}
