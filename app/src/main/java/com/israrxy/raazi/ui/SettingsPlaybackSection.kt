package com.israrxy.raazi.ui

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.israrxy.raazi.data.local.SettingsDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal fun LazyListScope.settingsPlaybackSection(
    audioQuality: String,
    onShowAudioQualityDialog: () -> Unit,
    crossfadeDuration: String,
    onShowCrossfadeDialog: () -> Unit,
    dataSaver: Boolean,
    hapticFeedback: Boolean,
    settingsDataStore: SettingsDataStore,
    scope: CoroutineScope
) {
    item {
        val doubleTapSeekDuration by settingsDataStore.doubleTapSeekDuration.collectAsState(initial = "10")
        val keepScreenAwake by settingsDataStore.keepScreenAwake.collectAsState(initial = false)
        val pauseOnDisconnect by settingsDataStore.pauseOnDisconnect.collectAsState(initial = true)
        val flacEnabled by settingsDataStore.flacEnabled.collectAsState(initial = false)
        val hifiApiUrl by settingsDataStore.hifiApiUrl.collectAsState(initial = "")
        var showSeekDialog by remember { mutableStateOf(false) }
        var showLosslessDialog by remember { mutableStateOf(false) }

        SettingsSection(title = "Audio Quality & Streaming") {
            SettingsSelectItem(
                icon = Icons.Filled.HighQuality,
                title = "Streaming Quality",
                value = audioQuality,
                onClick = onShowAudioQualityDialog
            )
            SettingsDivider()
            SettingsSwitchItem(
                icon = Icons.Filled.Audiotrack,
                title = "Lossless Audio (FLAC)",
                subtitle = if (flacEnabled) "Auto-switches to Lossless FLAC when available with studio master fallback" else "Disabled · Standard AAC/Opus audio",
                checked = flacEnabled,
                onCheckedChange = { enabled ->
                    scope.launch { settingsDataStore.setFlacEnabled(enabled) }
                }
            )
            if (flacEnabled) {
                SettingsDivider()
                SettingsSelectItem(
                    icon = Icons.Filled.CloudQueue,
                    title = "Lossless Server",
                    value = if (hifiApiUrl.isNotBlank()) "Custom Server" else "Community Network (Auto)",
                    onClick = { showLosslessDialog = true }
                )
            }
            SettingsDivider()
            SettingsSelectItem(
                icon = Icons.Filled.GraphicEq,
                title = "Crossfade",
                value = crossfadeDuration,
                onClick = onShowCrossfadeDialog
            )
            SettingsDivider()
            SettingsSwitchItem(
                icon = Icons.Filled.DataSaverOn,
                title = "Data Saver",
                subtitle = "Lower stream bitrate on mobile data",
                checked = dataSaver,
                onCheckedChange = { enabled ->
                    scope.launch { settingsDataStore.setDataSaver(enabled) }
                }
            )
            SettingsDivider()
            SettingsSwitchItem(
                icon = Icons.Filled.Vibration,
                title = "Haptic Feedback",
                subtitle = "Subtle vibrations on controls",
                checked = hapticFeedback,
                onCheckedChange = { enabled ->
                    scope.launch { settingsDataStore.setHapticFeedback(enabled) }
                }
            )
        }

        SettingsSection(title = "Player Controls & Behaviors") {
            SettingsSelectItem(
                icon = Icons.Filled.FastForward,
                title = "Double-Tap Seek",
                value = "${doubleTapSeekDuration}s",
                onClick = { showSeekDialog = true }
            )
            SettingsDivider()
            SettingsSwitchItem(
                icon = Icons.Filled.ScreenLockPortrait,
                title = "Keep Screen Awake",
                subtitle = "Prevent screen from sleeping while player or lyrics are open",
                checked = keepScreenAwake,
                onCheckedChange = { enabled ->
                    scope.launch { settingsDataStore.setKeepScreenAwake(enabled) }
                }
            )
            SettingsDivider()
            SettingsSwitchItem(
                icon = Icons.Filled.Headphones,
                title = "Pause on Disconnect",
                subtitle = "Automatically pause when headphones or Bluetooth disconnect",
                checked = pauseOnDisconnect,
                onCheckedChange = { enabled ->
                    scope.launch { settingsDataStore.setPauseOnDisconnect(enabled) }
                }
            )
        }

        if (showSeekDialog) {
            SelectionDialog(
                title = "Double-Tap Seek Duration",
                options = listOf("5", "10", "15", "30"),
                selectedOption = doubleTapSeekDuration,
                onOptionSelected = { selected ->
                    scope.launch { settingsDataStore.setDoubleTapSeekDuration(selected) }
                    showSeekDialog = false
                },
                onDismiss = { showSeekDialog = false }
            )
        }

        if (showLosslessDialog) {
            com.israrxy.raazi.ui.settings.SettingsLosslessDialog(
                initialUrl = hifiApiUrl,
                settingsDataStore = settingsDataStore,
                scope = scope,
                onDismiss = { showLosslessDialog = false }
            )
        }
    }
}
