package com.israrxy.raazi.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsDataStore(private val context: Context) {

    companion object {
        val DATA_SAVER_KEY = booleanPreferencesKey("data_saver")
        val AUDIO_QUALITY_KEY = stringPreferencesKey("audio_quality")
        val CROSSFADE_KEY = booleanPreferencesKey("crossfade_enabled")
        val CROSSFADE_DURATION_KEY = stringPreferencesKey("crossfade_duration")
        val DYNAMIC_COLOR_KEY = booleanPreferencesKey("dynamic_color_enabled")
        val THEME_MODE_KEY = stringPreferencesKey("theme_mode")
        val GEMINI_API_KEY_KEY = stringPreferencesKey("gemini_api_key")
        val USE_GEMINI_IMPORT_KEY = booleanPreferencesKey("use_gemini_import")
        val BLUR_PLAYER_BACKGROUND_KEY = booleanPreferencesKey("blur_player_background")
        val PASTEL_ACCENT_KEY = stringPreferencesKey("pastel_accent")
        val IS_ONBOARDING_COMPLETED_KEY = booleanPreferencesKey("onboarding_completed")
        val EQUALIZER_PRESET_KEY = stringPreferencesKey("equalizer_preset")
        val EQUALIZER_LEVELS_KEY = stringPreferencesKey("equalizer_levels")
        val BASS_BOOST_KEY = stringPreferencesKey("bass_boost")
        val VIRTUALIZER_KEY = stringPreferencesKey("virtualizer")
        val REVERB_KEY = stringPreferencesKey("reverb")
        val CUSTOM_PRESETS_KEY = stringPreferencesKey("custom_presets")
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
        val PURE_BLACK_KEY = booleanPreferencesKey("pure_black_theme")
        val HAPTIC_FEEDBACK_KEY = booleanPreferencesKey("haptic_feedback")
        val BIOMETRIC_LOCK_KEY = booleanPreferencesKey("biometric_lock")
        val ALLOW_LANDSCAPE_KEY = booleanPreferencesKey("allow_landscape")
        val LYRICS_TRANSLATE_ENABLED_KEY = booleanPreferencesKey("lyrics_translate_enabled")
        val LYRICS_TRANSLATE_LANG_KEY = stringPreferencesKey("lyrics_translate_lang")
        val LASTFM_SCROBBLE_ENABLED_KEY = booleanPreferencesKey("lastfm_scrobble_enabled")
        val LASTFM_SESSION_KEY = stringPreferencesKey("lastfm_session_key")
        val LASTFM_USERNAME_KEY = stringPreferencesKey("lastfm_username")
        val VIDEO_QUALITY_KEY = stringPreferencesKey("video_quality")
        val DEVICE_EQ_PROFILES_KEY = stringPreferencesKey("device_eq_profiles")
        val FLAC_ENABLED_KEY = booleanPreferencesKey("flac_enabled")
        val HIFI_API_URL_KEY = stringPreferencesKey("hifi_api_url")
        val FLAC_QUALITY_KEY = stringPreferencesKey("flac_quality")
    }

    private fun <T> prefFlow(key: Preferences.Key<T>, default: T): Flow<T> =
        context.dataStore.data.map { prefs -> prefs[key] ?: default }

    private fun <T> nullablePrefFlow(key: Preferences.Key<T>): Flow<T?> =
        context.dataStore.data.map { prefs -> prefs[key] }

    private suspend fun <T> setPref(key: Preferences.Key<T>, value: T) {
        context.dataStore.edit { prefs -> prefs[key] = value }
    }

    val pureBlack: Flow<Boolean> = prefFlow(PURE_BLACK_KEY, false)
    suspend fun setPureBlack(enabled: Boolean) = setPref(PURE_BLACK_KEY, enabled)

    val hapticFeedback: Flow<Boolean> = prefFlow(HAPTIC_FEEDBACK_KEY, true)
    suspend fun setHapticFeedback(enabled: Boolean) = setPref(HAPTIC_FEEDBACK_KEY, enabled)

    val biometricLock: Flow<Boolean> = prefFlow(BIOMETRIC_LOCK_KEY, false)
    suspend fun setBiometricLock(enabled: Boolean) = setPref(BIOMETRIC_LOCK_KEY, enabled)

    val allowLandscape: Flow<Boolean> = prefFlow(ALLOW_LANDSCAPE_KEY, false)
    suspend fun setAllowLandscape(enabled: Boolean) = setPref(ALLOW_LANDSCAPE_KEY, enabled)

    val lyricsTranslateEnabled: Flow<Boolean> = prefFlow(LYRICS_TRANSLATE_ENABLED_KEY, false)
    suspend fun setLyricsTranslateEnabled(enabled: Boolean) = setPref(LYRICS_TRANSLATE_ENABLED_KEY, enabled)

    val lyricsTranslateLang: Flow<String> = prefFlow(LYRICS_TRANSLATE_LANG_KEY, "en")
    suspend fun setLyricsTranslateLang(lang: String) = setPref(LYRICS_TRANSLATE_LANG_KEY, lang)

    val lastfmScrobbleEnabled: Flow<Boolean> = prefFlow(LASTFM_SCROBBLE_ENABLED_KEY, false)
    suspend fun setLastfmScrobbleEnabled(enabled: Boolean) = setPref(LASTFM_SCROBBLE_ENABLED_KEY, enabled)

    val lastfmSessionKey: Flow<String?> = nullablePrefFlow(LASTFM_SESSION_KEY)
    val lastfmUsername: Flow<String?> = nullablePrefFlow(LASTFM_USERNAME_KEY)

    suspend fun setLastfmSession(sessionKey: String?, username: String?) {
        context.dataStore.edit { prefs ->
            if (sessionKey.isNullOrBlank()) prefs.remove(LASTFM_SESSION_KEY) else prefs[LASTFM_SESSION_KEY] = sessionKey
            if (username.isNullOrBlank()) prefs.remove(LASTFM_USERNAME_KEY) else prefs[LASTFM_USERNAME_KEY] = username
        }
    }

    val isOnboardingCompleted: Flow<Boolean> = prefFlow(IS_ONBOARDING_COMPLETED_KEY, false)
    suspend fun setOnboardingCompleted(completed: Boolean) = setPref(IS_ONBOARDING_COMPLETED_KEY, completed)

    val LAST_FOLLOW_PROMPT_KEY = stringPreferencesKey("last_follow_prompt_date")
    val lastFollowPromptDate: Flow<String?> = nullablePrefFlow(LAST_FOLLOW_PROMPT_KEY)
    suspend fun setLastFollowPromptDate(date: String) = setPref(LAST_FOLLOW_PROMPT_KEY, date)

    val themeMode: Flow<String> = prefFlow(THEME_MODE_KEY, "System")
    suspend fun setThemeMode(mode: String) = setPref(THEME_MODE_KEY, mode)

    val dataSaverEnabled: Flow<Boolean> = prefFlow(DATA_SAVER_KEY, false)
    suspend fun setDataSaver(enabled: Boolean) = setPref(DATA_SAVER_KEY, enabled)

    val audioQuality: Flow<String> = prefFlow(AUDIO_QUALITY_KEY, "Very High")
    suspend fun setAudioQuality(quality: String) = setPref(AUDIO_QUALITY_KEY, quality)

    val crossfadeEnabled: Flow<Boolean> = prefFlow(CROSSFADE_KEY, false)
    suspend fun setCrossfade(enabled: Boolean) = setPref(CROSSFADE_KEY, enabled)

    val crossfadeDuration: Flow<String> = prefFlow(CROSSFADE_DURATION_KEY, "Off")
    suspend fun setCrossfadeDuration(duration: String) = setPref(CROSSFADE_DURATION_KEY, duration)

    val useDynamicColor: Flow<Boolean> = prefFlow(DYNAMIC_COLOR_KEY, false)
    suspend fun setDynamicColor(enabled: Boolean) = setPref(DYNAMIC_COLOR_KEY, enabled)

    val geminiApiKey: Flow<String?> = nullablePrefFlow(GEMINI_API_KEY_KEY)
    suspend fun setGeminiApiKey(key: String) = setPref(GEMINI_API_KEY_KEY, key)

    val useGeminiImport: Flow<Boolean> = prefFlow(USE_GEMINI_IMPORT_KEY, false)
    suspend fun setUseGeminiImport(enabled: Boolean) = setPref(USE_GEMINI_IMPORT_KEY, enabled)

    val blurPlayerBackground: Flow<Boolean> = prefFlow(BLUR_PLAYER_BACKGROUND_KEY, true)
    suspend fun setBlurPlayerBackground(enabled: Boolean) = setPref(BLUR_PLAYER_BACKGROUND_KEY, enabled)

    val pastelAccent: Flow<String> = prefFlow(PASTEL_ACCENT_KEY, "Emerald")
    suspend fun setPastelAccent(accent: String) = setPref(PASTEL_ACCENT_KEY, accent)

    val equalizerPreset: Flow<String?> = nullablePrefFlow(EQUALIZER_PRESET_KEY)
    suspend fun setEqualizerPreset(preset: String) = setPref(EQUALIZER_PRESET_KEY, preset)

    val equalizerLevels: Flow<String?> = nullablePrefFlow(EQUALIZER_LEVELS_KEY)
    suspend fun setEqualizerLevels(levels: String) = setPref(EQUALIZER_LEVELS_KEY, levels)

    val bassBoostStrength: Flow<Short?> = context.dataStore.data.map { it[BASS_BOOST_KEY]?.toShortOrNull() }
    suspend fun setBassBoostStrength(strength: Short) = setPref(BASS_BOOST_KEY, strength.toString())

    val virtualizerStrength: Flow<Short?> = context.dataStore.data.map { it[VIRTUALIZER_KEY]?.toShortOrNull() }
    suspend fun setVirtualizerStrength(strength: Short) = setPref(VIRTUALIZER_KEY, strength.toString())

    val reverbPreset: Flow<Int?> = context.dataStore.data.map { it[REVERB_KEY]?.toIntOrNull() }
    suspend fun setReverbPreset(preset: Int) = setPref(REVERB_KEY, preset.toString())

    val customPresets: Flow<String?> = nullablePrefFlow(CUSTOM_PRESETS_KEY)
    suspend fun saveCustomPreset(presetJsonString: String) = setPref(CUSTOM_PRESETS_KEY, presetJsonString)
    suspend fun updateCustomPresets(presetsJsonString: String) = setPref(CUSTOM_PRESETS_KEY, presetsJsonString)

    suspend fun deleteCustomPreset(name: String) {
        val currentPresetsJson = customPresets.first() ?: return
        val updatedJson = filterOutCustomPreset(currentPresetsJson, name) ?: return
        setPref(CUSTOM_PRESETS_KEY, updatedJson)
    }

    val downloadWifiOnly: Flow<Boolean> = prefFlow(DOWNLOAD_WIFI_ONLY_KEY, false)
    suspend fun setDownloadWifiOnly(enabled: Boolean) = setPref(DOWNLOAD_WIFI_ONLY_KEY, enabled)

    val downloadQuality: Flow<String> = prefFlow(DOWNLOAD_QUALITY_KEY, "Very High")
    suspend fun setDownloadQuality(quality: String) = setPref(DOWNLOAD_QUALITY_KEY, quality)

    val maxConcurrentDownloads: Flow<String> = prefFlow(MAX_CONCURRENT_DOWNLOADS_KEY, "2")
    suspend fun setMaxConcurrentDownloads(count: String) = setPref(MAX_CONCURRENT_DOWNLOADS_KEY, count)

    val innerTubeCookie: Flow<String?> = nullablePrefFlow(INNER_TUBE_COOKIE_KEY)
    val visitorData: Flow<String?> = nullablePrefFlow(VISITOR_DATA_KEY)
    val dataSyncId: Flow<String?> = nullablePrefFlow(DATA_SYNC_ID_KEY)
    val accountName: Flow<String?> = nullablePrefFlow(ACCOUNT_NAME_KEY)
    val accountEmail: Flow<String?> = nullablePrefFlow(ACCOUNT_EMAIL_KEY)
    val accountChannelHandle: Flow<String?> = nullablePrefFlow(ACCOUNT_CHANNEL_HANDLE_KEY)
    val useLoginForBrowse: Flow<Boolean> = prefFlow(USE_LOGIN_FOR_BROWSE_KEY, true)
    val lastTopLevelRoute: Flow<String> = prefFlow(LAST_TOP_LEVEL_ROUTE_KEY, "home")

    suspend fun saveYouTubeSession(cookie: String, visitorData: String, dataSyncId: String?) {
        context.dataStore.edit { it.applyYouTubeSession(cookie, visitorData, dataSyncId) }
    }

    suspend fun saveYouTubeAccountInfo(name: String?, email: String?, channelHandle: String?) {
        context.dataStore.edit { it.applyYouTubeAccountInfo(name, email, channelHandle) }
    }

    suspend fun setVisitorData(value: String?) {
        context.dataStore.edit { prefs ->
            if (value.isNullOrBlank()) prefs.remove(VISITOR_DATA_KEY) else prefs[VISITOR_DATA_KEY] = value
        }
    }

    suspend fun setUseLoginForBrowse(enabled: Boolean) = setPref(USE_LOGIN_FOR_BROWSE_KEY, enabled)
    suspend fun setLastTopLevelRoute(route: String) = setPref(LAST_TOP_LEVEL_ROUTE_KEY, route)

    suspend fun clearYouTubeAccount() {
        context.dataStore.edit { it.clearYouTubeAccountPrefs() }
    }

    suspend fun clearAll() {
        context.dataStore.edit { it.clear() }
    }

    val videoQuality: Flow<String> = prefFlow(VIDEO_QUALITY_KEY, "AUTO")
    suspend fun setVideoQuality(wireName: String) = setPref(VIDEO_QUALITY_KEY, wireName)

    val flacEnabled: Flow<Boolean> = prefFlow(FLAC_ENABLED_KEY, false)
    suspend fun setFlacEnabled(enabled: Boolean) = setPref(FLAC_ENABLED_KEY, enabled)

    val hifiApiUrl: Flow<String> = prefFlow(HIFI_API_URL_KEY, "")
    suspend fun setHifiApiUrl(url: String) = setPref(HIFI_API_URL_KEY, url)

    val flacQuality: Flow<String> = prefFlow(FLAC_QUALITY_KEY, "HI_RES_LOSSLESS")
    suspend fun setFlacQuality(quality: String) = setPref(FLAC_QUALITY_KEY, quality)

    suspend fun getDeviceEqProfile(deviceAddress: String): String? {
        val raw = context.dataStore.data.first()[DEVICE_EQ_PROFILES_KEY]
        return decodeDeviceEqProfile(raw, deviceAddress)
    }

    suspend fun setDeviceEqProfile(deviceAddress: String, presetName: String) {
        context.dataStore.edit { prefs ->
            prefs[DEVICE_EQ_PROFILES_KEY] = encodeUpdatedDeviceEqProfiles(
                prefs[DEVICE_EQ_PROFILES_KEY],
                deviceAddress,
                presetName
            )
        }
    }
}
