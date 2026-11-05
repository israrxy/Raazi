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
        val resetToArtworkOnTrackChange by settingsDataStore.resetToArtworkOnTrackChange.collectAsState(initial = true)
        val openPlayerOnTrackSelect by settingsDataStore.openPlayerOnTrackSelect.collectAsState(initial = true)
        var showSeekDialog by remember { mutableStateOf(false) }
        var showTrackSelectActionDialog by remember { mutableStateOf(false) }

        SettingsSection(title = "Audio Quality & Streaming") {
            SettingsSelectItem(
                icon = Icons.Filled.HighQuality,
                title = "Streaming Quality",
                value = audioQuality,
                onClick = onShowAudioQualityDialog
            )
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
            SettingsDivider()
            SettingsSelectItem(
                icon = Icons.Filled.OpenInFull,
                title = "Track Selection Action",
                value = if (openPlayerOnTrackSelect) "Open Full Player" else "Play in Mini Player",
                onClick = { showTrackSelectActionDialog = true }
            )
            SettingsDivider()
            SettingsSwitchItem(
                icon = Icons.Filled.Image,
                title = "Reset to Artwork on Song Change",
                subtitle = "Automatically return to cover art view when a new track plays",
                checked = resetToArtworkOnTrackChange,
                onCheckedChange = { enabled ->
                    scope.launch { settingsDataStore.setResetToArtworkOnTrackChange(enabled) }
                }
            )
        }

        if (showTrackSelectActionDialog) {
            SelectionDialog(
                title = "When Track Selected",
                options = listOf("Open Full Player", "Play in Mini Player"),
                selectedOption = if (openPlayerOnTrackSelect) "Open Full Player" else "Play in Mini Player",
                descriptions = mapOf(
                    "Open Full Player" to "Expand the full player when you pick a song from a list",
                    "Play in Mini Player" to "Start playback without leaving the current screen"
                ),
                onOptionSelected = { selected ->
                    scope.launch { settingsDataStore.setOpenPlayerOnTrackSelect(selected == "Open Full Player") }
                    showTrackSelectActionDialog = false
                },
                onDismiss = { showTrackSelectActionDialog = false }
            )
        }

        if (showSeekDialog) {
            SelectionDialog(
                title = "Double-Tap Seek Duration",
                options = listOf("5", "10", "15", "30"),
                selectedOption = doubleTapSeekDuration,
                descriptions = mapOf(
                    "5" to "Jump 5 seconds forward or back",
                    "10" to "Balanced default for most songs",
                    "15" to "Skip further with each double tap",
                    "30" to "Large jumps for long tracks"
                ),
                onOptionSelected = { selected ->
                    scope.launch { settingsDataStore.setDoubleTapSeekDuration(selected) }
                    showSeekDialog = false
                },
                onDismiss = { showSeekDialog = false }
            )
        }
    }
}
