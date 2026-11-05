package com.israrxy.raazi.ui

import android.content.Context
import androidx.compose.runtime.Composable
import com.israrxy.raazi.data.local.SettingsDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Selection and confirmation dialogs for the main settings screen. Kept in a
 * separate file so [SettingsScreen] stays focused on layout.
 */
@Composable
internal fun SettingsSelectionDialogsHost(
    context: Context,
    scope: CoroutineScope,
    settingsDataStore: SettingsDataStore,
    showAudioQualityDialog: Boolean,
    audioQuality: String,
    onDismissAudioQuality: () -> Unit,
    showDownloadQualityDialog: Boolean,
    downloadQuality: String,
    onDismissDownloadQuality: () -> Unit,
    showThemeDialog: Boolean,
    appTheme: String,
    onDismissTheme: () -> Unit,
    showCrossfadeDialog: Boolean,
    crossfadeDuration: String,
    onDismissCrossfade: () -> Unit,
    showConcurrentDownloadsDialog: Boolean,
    maxConcurrentDownloads: String,
    onDismissConcurrentDownloads: () -> Unit,
    showLanguageDialog: Boolean,
    targetLanguage: String,
    onDismissLanguage: () -> Unit,
    showClearCacheDialog: Boolean,
    onDismissClearCache: () -> Unit,
    showResetSettingsDialog: Boolean,
    onDismissResetSettings: () -> Unit
) {
    if (showAudioQualityDialog) {
        SelectionDialog(
            title = "Streaming Audio Quality",
            options = listOf("Low", "Normal", "High", "Very High"),
            selectedOption = audioQuality,
            descriptions = mapOf(
                "Low" to "Saves the most data, lower fidelity",
                "Normal" to "Balanced quality and data use",
                "High" to "Better detail for most connections",
                "Very High" to "Best available stream, uses more data"
            ),
            onOptionSelected = { selected ->
                scope.launch { settingsDataStore.setAudioQuality(selected) }
                onDismissAudioQuality()
            },
            onDismiss = onDismissAudioQuality
        )
    }

    if (showDownloadQualityDialog) {
        SelectionDialog(
            title = "Download Quality",
            options = listOf("Low", "Normal", "High", "Very High"),
            selectedOption = downloadQuality,
            descriptions = mapOf(
                "Low" to "Smallest files, quickest downloads",
                "Normal" to "Everyday offline listening",
                "High" to "Better audio for premium gear",
                "Very High" to "Largest files with maximum detail"
            ),
            onOptionSelected = { selected ->
                scope.launch { settingsDataStore.setDownloadQuality(selected) }
                onDismissDownloadQuality()
            },
            onDismiss = onDismissDownloadQuality
        )
    }

    if (showThemeDialog) {
        SelectionDialog(
            title = "Theme Mode",
            options = listOf("System", "Light", "Dark"),
            selectedOption = appTheme,
            descriptions = mapOf(
                "System" to "Follow your device appearance",
                "Light" to "Always use the light theme",
                "Dark" to "Always use the dark theme"
            ),
            onOptionSelected = { selected ->
                scope.launch { settingsDataStore.setThemeMode(selected) }
                onDismissTheme()
            },
            onDismiss = onDismissTheme
        )
    }

    if (showCrossfadeDialog) {
        SelectionDialog(
            title = "Crossfade",
            options = listOf("Off", "2s", "4s", "6s", "8s", "10s", "12s"),
            selectedOption = crossfadeDuration,
            descriptions = mapOf(
                "Off" to "Tracks change without blending",
                "2s" to "Subtle, quick transition",
                "4s" to "Smooth short blend",
                "6s" to "Recommended for most playlists",
                "8s" to "Longer, cinematic blend",
                "10s" to "Very slow crossfade",
                "12s" to "Maximum overlap between tracks"
            ),
            onOptionSelected = { selected ->
                scope.launch {
                    settingsDataStore.setCrossfade(selected != "Off")
                    settingsDataStore.setCrossfadeDuration(selected)
                }
                onDismissCrossfade()
            },
            onDismiss = onDismissCrossfade
        )
    }

    if (showConcurrentDownloadsDialog) {
        SelectionDialog(
            title = "Concurrent Downloads",
            options = listOf("1", "2", "3", "4", "5"),
            selectedOption = maxConcurrentDownloads,
            descriptions = mapOf(
                "1" to "Slowest, gentlest on your connection",
                "2" to "Balanced default",
                "3" to "Faster batch downloads",
                "4" to "Aggressive parallel downloads",
                "5" to "Maximum speed, highest data use"
            ),
            onOptionSelected = { selected ->
                scope.launch { settingsDataStore.setMaxConcurrentDownloads(selected) }
                onDismissConcurrentDownloads()
            },
            onDismiss = onDismissConcurrentDownloads
        )
    }

    if (showLanguageDialog) {
        SelectionDialog(
            title = "Target Language",
            options = listOf("en", "es", "fr", "de", "hi", "ur", "ar", "ja", "ko", "pt", "ru", "tr"),
            selectedOption = targetLanguage,
            onOptionSelected = { selected ->
                scope.launch { settingsDataStore.setLyricsTranslateLang(selected) }
                onDismissLanguage()
            },
            onDismiss = onDismissLanguage
        )
    }

    if (showClearCacheDialog) {
        SettingsClearCacheDialog(context = context, onDismiss = onDismissClearCache)
    }

    if (showResetSettingsDialog) {
        SettingsResetDialog(
            context = context,
            scope = scope,
            settingsDataStore = settingsDataStore,
            onDismiss = onDismissResetSettings
        )
    }
}
