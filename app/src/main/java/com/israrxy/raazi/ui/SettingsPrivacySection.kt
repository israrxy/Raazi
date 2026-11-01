package com.israrxy.raazi.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.israrxy.raazi.data.local.SettingsDataStore
import com.israrxy.raazi.viewmodel.MusicPlayerViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal fun LazyListScope.settingsPrivacySection(
    biometricLock: Boolean,
    onShowResetSettingsDialog: () -> Unit,
    settingsDataStore: SettingsDataStore,
    viewModel: MusicPlayerViewModel,
    scope: CoroutineScope,
    context: Context
) {
    item {
        val saveSearchHistory by settingsDataStore.saveSearchHistory.collectAsState(initial = true)
        var showClearSearchDialog by remember { mutableStateOf(false) }
        var showClearHistoryDialog by remember { mutableStateOf(false) }

        SettingsSection(title = "Search & History") {
            SettingsSwitchItem(
                icon = Icons.Filled.History,
                title = "Save Search History",
                subtitle = "Remember query text for fast suggestions",
                checked = saveSearchHistory,
                onCheckedChange = { enabled ->
                    scope.launch { settingsDataStore.setSaveSearchHistory(enabled) }
                }
            )
            SettingsDivider()
            SettingsActionItem(
                icon = Icons.Filled.DeleteSweep,
                title = "Clear Search History",
                subtitle = "Delete all saved search queries",
                onClick = { showClearSearchDialog = true }
            )
            SettingsDivider()
            SettingsActionItem(
                icon = Icons.Filled.ClearAll,
                title = "Clear Playback History",
                subtitle = "Reset recently played songs in library",
                onClick = { showClearHistoryDialog = true }
            )
        }

        SettingsSection(title = "Security & Reset") {
            SettingsSwitchItem(
                icon = Icons.Filled.Fingerprint,
                title = "Biometric Lock",
                subtitle = "Require authentication on launch",
                checked = biometricLock,
                onCheckedChange = { enabled ->
                    scope.launch { settingsDataStore.setBiometricLock(enabled) }
                }
            )
            SettingsDivider()
            SettingsActionItem(
                icon = Icons.Filled.Restore,
                title = "Reset Settings",
                subtitle = "Restore default preferences",
                onClick = onShowResetSettingsDialog,
                isDestructive = true
            )
        }

        if (showClearSearchDialog) {
            AlertDialog(
                onDismissRequest = { showClearSearchDialog = false },
                title = { Text("Clear Search History") },
                text = { Text("Are you sure you want to delete all saved search queries?") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.clearSearchHistory()
                            Toast.makeText(context, "Search history cleared", Toast.LENGTH_SHORT).show()
                            showClearSearchDialog = false
                        }
                    ) {
                        Text("Clear")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showClearSearchDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        if (showClearHistoryDialog) {
            val playbackHistory by viewModel.playbackHistory.collectAsState(initial = emptyList())
            AlertDialog(
                onDismissRequest = { showClearHistoryDialog = false },
                title = { Text("Clear Playback History") },
                text = { Text("Are you sure you want to remove all songs from recently played history?") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            if (playbackHistory.isNotEmpty()) {
                                viewModel.removeTracksFromHistory(playbackHistory)
                            }
                            Toast.makeText(context, "Playback history cleared", Toast.LENGTH_SHORT).show()
                            showClearHistoryDialog = false
                        }
                    ) {
                        Text("Clear")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showClearHistoryDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}
