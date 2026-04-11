package com.israrxy.raazi.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

// Extension property for DataStore
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsDataStore(private val context: Context) {

    companion object {
        // Preference Keys
        val DATA_SAVER_KEY = booleanPreferencesKey("data_saver")
        val AUDIO_QUALITY_KEY = stringPreferencesKey("audio_quality")
        val CROSSFADE_KEY = booleanPreferencesKey("crossfade_enabled")
        val CROSSFADE_DURATION_KEY = stringPreferencesKey("crossfade_duration")
        val DYNAMIC_COLOR_KEY = booleanPreferencesKey("dynamic_color_enabled")
        val THEME_MODE_KEY = stringPreferencesKey("theme_mode") // "System", "Light", "Dark"
        val IS_ONBOARDING_COMPLETED_KEY = booleanPreferencesKey("onboarding_completed")
        val EQUALIZER_PRESET_KEY = stringPreferencesKey("equalizer_preset")
        val EQUALIZER_LEVELS_KEY = stringPreferencesKey("equalizer_levels")
        val BASS_BOOST_KEY = stringPreferencesKey("bass_boost")
        val VIRTUALIZER_KEY = stringPreferencesKey("virtualizer")
        val REVERB_KEY = stringPreferencesKey("reverb")
        val CUSTOM_PRESETS_KEY = stringPreferencesKey("custom_presets")
        // Download Settings
        val DOWNLOAD_WIFI_ONLY_KEY = booleanPreferencesKey("download_wifi_only")
        val DOWNLOAD_QUALITY_KEY = stringPreferencesKey("download_quality")
        val MAX_CONCURRENT_DOWNLOADS_KEY = stringPreferencesKey("max_concurrent_downloads")
        val INNER_TUBE_COOKIE_KEY = stringPreferencesKey("inner_tube_cookie")
        val VISITOR_DATA_KEY = stringPreferencesKey("visitor_data")
        val DATA_SYNC_ID_KEY = stringPreferencesKey("data_sync_id")
        val ACCOUNT_NAME_KEY = stringPreferencesKey("account_name")
        val ACCOUNT_EMAIL_KEY = stringPreferencesKey("account_email")
        val ACCOUNT_CHANNEL_HANDLE_KEY = stringPreferencesKey("account_channel_handle")
        val USE_LOGIN_FOR_BROWSE_KEY = booleanPreferencesKey("use_login_for_browse")
        val LAST_TOP_LEVEL_ROUTE_KEY = stringPreferencesKey("last_top_level_route")
    }

    // Onboarding
    val isOnboardingCompleted: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[IS_ONBOARDING_COMPLETED_KEY] ?: false
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[IS_ONBOARDING_COMPLETED_KEY] = completed
        }
    }

    // Theme Mode
    val themeMode: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[THEME_MODE_KEY] ?: "System"
    }

    suspend fun setThemeMode(mode: String) {
        context.dataStore.edit { prefs ->
            prefs[THEME_MODE_KEY] = mode
        }
    }

    // Data Saver Mode
    val dataSaverEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[DATA_SAVER_KEY] ?: false
    }

    suspend fun setDataSaver(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[DATA_SAVER_KEY] = enabled
        }
    }

    // Audio Quality ("Low", "Normal", "High", "Very High")
    val audioQuality: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[AUDIO_QUALITY_KEY] ?: "Very High"
    }

    suspend fun setAudioQuality(quality: String) {
        context.dataStore.edit { prefs ->
            prefs[AUDIO_QUALITY_KEY] = quality
        }
    }

    // Crossfade
    val crossfadeEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[CROSSFADE_KEY] ?: false
    }

    suspend fun setCrossfade(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[CROSSFADE_KEY] = enabled
        }
    }

    val crossfadeDuration: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[CROSSFADE_DURATION_KEY] ?: "Off"
    }

    suspend fun setCrossfadeDuration(duration: String) {
        context.dataStore.edit { prefs ->
            prefs[CROSSFADE_DURATION_KEY] = duration
        }
    }
    
    // Dynamic Color (Material You)
    val useDynamicColor: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[DYNAMIC_COLOR_KEY] ?: false
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[DYNAMIC_COLOR_KEY] = enabled
        }
    }

    // Equalizer
    val equalizerPreset: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[EQUALIZER_PRESET_KEY]
    }

    suspend fun setEqualizerPreset(preset: String) {
        context.dataStore.edit { prefs ->
            prefs[EQUALIZER_PRESET_KEY] = preset
        }
    }

    val equalizerLevels: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[EQUALIZER_LEVELS_KEY]
    }

    suspend fun setEqualizerLevels(levels: String) {
        context.dataStore.edit { prefs ->
            prefs[EQUALIZER_LEVELS_KEY] = levels
        }
    }

    // Advanced Effects Settings
    val bassBoostStrength: Flow<Short?> = context.dataStore.data.map { prefs ->
        prefs[BASS_BOOST_KEY]?.toShortOrNull()
    }

    suspend fun setBassBoostStrength(strength: Short) {
        context.dataStore.edit { prefs ->
            prefs[BASS_BOOST_KEY] = strength.toString()
        }
    }

    val virtualizerStrength: Flow<Short?> = context.dataStore.data.map { prefs ->
            prefs[VIRTUALIZER_KEY]?.toShortOrNull()
    }

    suspend fun setVirtualizerStrength(strength: Short) {
        context.dataStore.edit { prefs ->
            prefs[VIRTUALIZER_KEY] = strength.toString()
        }
    }

    val reverbPreset: Flow<Int?> = context.dataStore.data.map { prefs ->
        prefs[REVERB_KEY]?.toIntOrNull()
    }

    suspend fun setReverbPreset(preset: Int) {
        context.dataStore.edit { prefs ->
            prefs[REVERB_KEY] = preset.toString()
        }
    }

    // Custom Presets Management
    val customPresets: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[CUSTOM_PRESETS_KEY]
    }

    suspend fun saveCustomPreset(presetJsonString: String) {
        context.dataStore.edit { prefs ->
            prefs[CUSTOM_PRESETS_KEY] = presetJsonString
        }
    }

    suspend fun updateCustomPresets(presetsJsonString: String) {
        context.dataStore.edit { prefs ->
            prefs[CUSTOM_PRESETS_KEY] = presetsJsonString
        }
    }

    suspend fun deleteCustomPreset(name: String) {
        val currentPresetsJson = customPresets.first()
        if (!currentPresetsJson.isNullOrEmpty()) {
            try {
                val presets = Json.decodeFromString<List<SerializableCustomPreset>>(currentPresetsJson)
                val updatedPresets = presets.filter { it.name != name }
                context.dataStore.edit { prefs ->
                    prefs[CUSTOM_PRESETS_KEY] = Json.encodeToString(updatedPresets)
                }
            } catch (e: Exception) {
                // Handle error
            }
        }
    }

    @Serializable
    data class SerializableCustomPreset(
        val name: String,
        val levels: String,
        val bassBoost: Short,
        val virtualizer: Short,
        val reverb: Int
    )

    // Download Settings
    val downloadWifiOnly: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[DOWNLOAD_WIFI_ONLY_KEY] ?: false
    }

    suspend fun setDownloadWifiOnly(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[DOWNLOAD_WIFI_ONLY_KEY] = enabled
        }
    }

    val downloadQuality: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[DOWNLOAD_QUALITY_KEY] ?: "Very High"
    }

    suspend fun setDownloadQuality(quality: String) {
        context.dataStore.edit { prefs ->
            prefs[DOWNLOAD_QUALITY_KEY] = quality
        }
    }

    val maxConcurrentDownloads: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[MAX_CONCURRENT_DOWNLOADS_KEY] ?: "2"
    }

    suspend fun setMaxConcurrentDownloads(count: String) {
        context.dataStore.edit { prefs ->
            prefs[MAX_CONCURRENT_DOWNLOADS_KEY] = count
        }
    }

    // YouTube Account
    val innerTubeCookie: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[INNER_TUBE_COOKIE_KEY]
    }

    val visitorData: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[VISITOR_DATA_KEY]
    }

    val dataSyncId: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[DATA_SYNC_ID_KEY]
    }

    val accountName: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[ACCOUNT_NAME_KEY]
    }

    val accountEmail: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[ACCOUNT_EMAIL_KEY]
    }

    val accountChannelHandle: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[ACCOUNT_CHANNEL_HANDLE_KEY]
    }

    val useLoginForBrowse: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[USE_LOGIN_FOR_BROWSE_KEY] ?: true
    }

    val lastTopLevelRoute: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[LAST_TOP_LEVEL_ROUTE_KEY] ?: "home"
    }

    suspend fun saveYouTubeSession(
        cookie: String,
        visitorData: String,
        dataSyncId: String?
    ) {
        context.dataStore.edit { prefs ->
            prefs[INNER_TUBE_COOKIE_KEY] = cookie
            prefs[VISITOR_DATA_KEY] = visitorData
            if (!dataSyncId.isNullOrBlank()) {
                prefs[DATA_SYNC_ID_KEY] = dataSyncId
            } else {
                prefs.remove(DATA_SYNC_ID_KEY)
            }
        }
    }

    suspend fun saveYouTubeAccountInfo(
        name: String?,
        email: String?,
        channelHandle: String?
    ) {
        context.dataStore.edit { prefs ->
            if (!name.isNullOrBlank()) {
                prefs[ACCOUNT_NAME_KEY] = name
            } else {
                prefs.remove(ACCOUNT_NAME_KEY)
            }
            if (!email.isNullOrBlank()) {
                prefs[ACCOUNT_EMAIL_KEY] = email
            } else {
                prefs.remove(ACCOUNT_EMAIL_KEY)
            }
            if (!channelHandle.isNullOrBlank()) {
                prefs[ACCOUNT_CHANNEL_HANDLE_KEY] = channelHandle
            } else {
                prefs.remove(ACCOUNT_CHANNEL_HANDLE_KEY)
            }
        }
    }

    suspend fun setVisitorData(value: String?) {
        context.dataStore.edit { prefs ->
            if (value.isNullOrBlank()) {
                prefs.remove(VISITOR_DATA_KEY)
            } else {
                prefs[VISITOR_DATA_KEY] = value
            }
        }
    }

    suspend fun setUseLoginForBrowse(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[USE_LOGIN_FOR_BROWSE_KEY] = enabled
        }
    }

    suspend fun setLastTopLevelRoute(route: String) {
        context.dataStore.edit { prefs ->
            prefs[LAST_TOP_LEVEL_ROUTE_KEY] = route
        }
    }

    suspend fun clearYouTubeAccount() {
        context.dataStore.edit { prefs ->
            prefs.remove(INNER_TUBE_COOKIE_KEY)
            prefs.remove(VISITOR_DATA_KEY)
            prefs.remove(DATA_SYNC_ID_KEY)
            prefs.remove(ACCOUNT_NAME_KEY)
            prefs.remove(ACCOUNT_EMAIL_KEY)
            prefs.remove(ACCOUNT_CHANNEL_HANDLE_KEY)
        }
    }

    // Clear all settings (cache clear simulation)
    suspend fun clearAll() {
        context.dataStore.edit { prefs ->
            prefs.clear()
        }
    }
}
