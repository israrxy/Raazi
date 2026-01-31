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

    // Clear all settings (cache clear simulation)
    suspend fun clearAll() {
        context.dataStore.edit { prefs ->
            prefs.clear()
        }
    }
}
