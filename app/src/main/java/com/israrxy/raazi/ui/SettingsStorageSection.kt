package com.israrxy.raazi.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import coil.Coil
import com.israrxy.raazi.data.local.SettingsDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.io.File

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
        var showSongsLimitDialog by remember { mutableStateOf(false) }
        val cachedSongsLimit by settingsDataStore.cachedSongsLimit.collectAsState(initial = 300)
        var cacheRefreshKey by remember { mutableStateOf(0) }

        val artworkBytes = remember(cacheRefreshKey) {
            runCatching {
                val imageLoader = Coil.imageLoader(context)
                imageLoader.diskCache?.size ?: File(context.cacheDir, "image_cache").walkTopDown().filter { it.isFile }.sumOf { it.length() }
            }.getOrDefault(0L)
        }

        val lyricsBytes = remember(cacheRefreshKey) {
            runCatching {
                val saved = File(context.filesDir, "saved-lyrics").walkTopDown().filter { it.isFile }.sumOf { it.length() }
                val api = File(context.cacheDir, "lyrics-cache").walkTopDown().filter { it.isFile }.sumOf { it.length() }
                saved + api
            }.getOrDefault(0L)
        }

        val streamBytes = remember(cacheRefreshKey) {
            runCatching {
                File(context.cacheDir, "flac_cache").walkTopDown().filter { it.isFile }.sumOf { it.length() } +
                    File(context.cacheDir, "ringtones").walkTopDown().filter { it.isFile }.sumOf { it.length() }
            }.getOrDefault(0L)
        }

        val totalCacheBytes = artworkBytes + lyricsBytes + streamBytes

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

        SettingsSection(title = "Storage & Cache Management (300 MB Safety Cap)") {
            SettingsSelectItem(
                icon = Icons.Filled.QueueMusic,
                title = "Auto-Cached Songs",
                value = "$cachedSongsLimit songs",
                onClick = { showSongsLimitDialog = true }
            )
            SettingsDivider()
            SettingsActionItem(
                icon = Icons.Filled.Image,
                title = "Clear Artwork Cache",
                subtitle = "Cached song & album art: ${formatStorageSize(artworkBytes)}",
                onClick = {
                    runCatching {
                        val imageLoader = Coil.imageLoader(context)
                        imageLoader.diskCache?.clear()
                        imageLoader.memoryCache?.clear()
                    }
                    cacheRefreshKey++
                    Toast.makeText(context, "Artwork cache cleared", Toast.LENGTH_SHORT).show()
                }
            )
            SettingsDivider()
            SettingsActionItem(
                icon = Icons.Filled.Lyrics,
                title = "Clear Lyrics Cache",
                subtitle = "Saved offline synced lyrics: ${formatStorageSize(lyricsBytes)}",
                onClick = {
                    runCatching {
                        File(context.filesDir, "saved-lyrics").deleteRecursively()
                        File(context.cacheDir, "lyrics-cache").deleteRecursively()
                    }
                    cacheRefreshKey++
                    Toast.makeText(context, "Lyrics cache cleared", Toast.LENGTH_SHORT).show()
                }
            )
            SettingsDivider()
            SettingsActionItem(
                icon = Icons.Filled.CleaningServices,
                title = "Clear Stream Cache",
                subtitle = "Temporary audio & streams: ${formatStorageSize(streamBytes)} (Total: ${formatStorageSize(totalCacheBytes)} / 300 MB)",
                onClick = {
                    onShowClearCacheDialog()
                    cacheRefreshKey++
                }
            )
        }

        if (showSongsLimitDialog) {
            val options = listOf("100 songs", "200 songs", "300 songs (Default)", "500 songs")
            val currentLabel = "$cachedSongsLimit songs" + if (cachedSongsLimit == 300) " (Default)" else ""
            SelectionDialog(
                title = "Auto-Cached Songs Limit",
                options = options,
                selectedOption = if (currentLabel in options) currentLabel else "300 songs (Default)",
                onOptionSelected = { selected ->
                    val num = selected.substringBefore(" songs").toIntOrNull() ?: 300
                    scope.launch { settingsDataStore.setCachedSongsLimit(num) }
                    showSongsLimitDialog = false
                },
                onDismiss = { showSongsLimitDialog = false }
            )
        }
    }
}

private fun formatStorageSize(bytes: Long): String {
    return when {
        bytes >= 1024 * 1024 * 1024 -> String.format(java.util.Locale.US, "%.1f GB", bytes.toDouble() / (1024 * 1024 * 1024))
        bytes >= 1024 * 1024 -> String.format(java.util.Locale.US, "%.1f MB", bytes.toDouble() / (1024 * 1024))
        bytes >= 1024 -> String.format(java.util.Locale.US, "%d KB", bytes / 1024)
        else -> "$bytes B"
    }
}
