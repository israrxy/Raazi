package com.israrxy.raazi.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import coil.Coil
import com.israrxy.raazi.data.local.SettingsDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal fun LazyListScope.settingsStorageSection(
    downloadQuality: String,
    onShowDownloadQualityDialog: () -> Unit,
    wifiOnlyDownload: Boolean,
    maxConcurrentDownloads: String,
    onShowConcurrentDownloadsDialog: () -> Unit,
    onShowClearCacheDialog: () -> Unit,
    settingsDataStore: SettingsDataStore,
    scope: CoroutineScope,
    context: Context
) {
    item {
        SettingsSection(title = "Downloads") {
            SettingsSelectItem(
                icon = Icons.Filled.Download,
                title = "Download Quality",
                value = downloadQuality,
                onClick = onShowDownloadQualityDialog
            )
            SettingsDivider()
            SettingsSwitchItem(
                icon = Icons.Filled.Wifi,
                title = "Wi-Fi Only",
                subtitle = "Pause downloads on cellular data",
                checked = wifiOnlyDownload,
                onCheckedChange = { enabled ->
                    scope.launch { settingsDataStore.setDownloadWifiOnly(enabled) }
                }
            )
            SettingsDivider()
            SettingsSelectItem(
                icon = Icons.Filled.Speed,
                title = "Concurrent Downloads",
                value = maxConcurrentDownloads,
                onClick = onShowConcurrentDownloadsDialog
            )
        }

        SettingsSection(title = "Storage & Cache Management") {
            SettingsActionItem(
                icon = Icons.Filled.Image,
                title = "Clear Artwork Cache",
                subtitle = "Free up device storage by clearing cached song & album art",
                onClick = {
                    runCatching {
                        val imageLoader = Coil.imageLoader(context)
                        imageLoader.diskCache?.clear()
                        imageLoader.memoryCache?.clear()
                    }
                    Toast.makeText(context, "Artwork cache cleared", Toast.LENGTH_SHORT).show()
                }
            )
            SettingsDivider()
            SettingsActionItem(
                icon = Icons.Filled.CleaningServices,
                title = "Clear Stream Cache",
                subtitle = "Remove temporary streaming segments without deleting downloads",
                onClick = onShowClearCacheDialog
            )
        }
    }
}
