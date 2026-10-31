package com.israrxy.raazi.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.israrxy.raazi.BuildConfig
import com.israrxy.raazi.data.local.SettingsDataStore
import com.israrxy.raazi.viewmodel.MusicPlayerViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
internal fun SettingsCategoryDetailList(
    currentCategory: SettingsCategory,
    viewModel: MusicPlayerViewModel,
    settingsDataStore: SettingsDataStore,
    scope: CoroutineScope,
    context: Context,
    isYouTubeLoggedIn: Boolean,
    youTubeAccountName: String?,
    youTubeAccountEmail: String?,
    useLoginForBrowse: Boolean,
    isSyncingYouTubeLibrary: Boolean,
    youTubeSyncStatus: String?,
    onNavigateToYouTubeLogin: () -> Unit,
    lastFmSessionKey: String?,
    lastFmUsername: String?,
    isScrobblingEnabled: Boolean,
    onShowLastFmLoginDialog: () -> Unit,
    appTheme: String,
    onShowThemeDialog: () -> Unit,
    dynamicColors: Boolean,
    amoledBlack: Boolean,
    blurPlayerBackground: Boolean,
    allowRotation: Boolean,
    pastelAccents: List<Pair<String, Color>>,
    accentColorName: String,
    audioQuality: String,
    onShowAudioQualityDialog: () -> Unit,
    crossfadeDuration: String,
    onShowCrossfadeDialog: () -> Unit,
    dataSaver: Boolean,
    hapticFeedback: Boolean,
    translateLyrics: Boolean,
    targetLanguage: String,
    onShowLanguageDialog: () -> Unit,
    downloadQuality: String,
    onShowDownloadQualityDialog: () -> Unit,
    wifiOnlyDownload: Boolean,
    maxConcurrentDownloads: String,
    onShowConcurrentDownloadsDialog: () -> Unit,
    onShowClearCacheDialog: () -> Unit,
    biometricLock: Boolean,
    onShowResetSettingsDialog: () -> Unit,
    isCheckingForUpdate: Boolean,
    onCheckForUpdate: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        when (currentCategory) {
            SettingsCategory.ACCOUNT -> {
                item {
                    SettingsSection(title = "YouTube Music") {
                        if (isYouTubeLoggedIn) {
                            SettingsAccountSummaryItem(
                                title = youTubeAccountName?.takeIf { it.isNotBlank() } ?: "YouTube Music Account",
                                subtitle = youTubeAccountEmail?.takeIf { it.isNotBlank() } ?: "Connected"
                            )
                            SettingsDivider()
                            SettingsSwitchItem(
                                icon = Icons.Filled.Public,
                                title = "Use Login for Browse",
                                subtitle = "Personalize Home feed using your account",
                                checked = useLoginForBrowse,
                                onCheckedChange = { viewModel.setUseLoginForBrowse(it) }
                            )
                            SettingsDivider()
                            SettingsActionItem(
                                icon = Icons.Filled.Sync,
                                title = if (isSyncingYouTubeLibrary) "Syncing Library..." else "Sync Library Now",
                                subtitle = youTubeSyncStatus ?: "Pull liked songs and saved playlists",
                                onClick = {
                                    if (!isSyncingYouTubeLibrary) {
                                        viewModel.syncYouTubeLibrary()
                                    }
                                }
                            )
                            SettingsDivider()
                            SettingsActionItem(
                                icon = Icons.Filled.ManageAccounts,
                                title = "Refresh Session",
                                subtitle = "Re-open YouTube Music login",
                                onClick = onNavigateToYouTubeLogin
                            )
                            SettingsDivider()
                            SettingsActionItem(
                                icon = Icons.AutoMirrored.Filled.Logout,
                                title = "Sign Out",
                                subtitle = "Disconnect your YouTube Music account",
                                onClick = {
                                    viewModel.logoutFromYouTube()
                                    Toast.makeText(context, "Signed out of YouTube Music", Toast.LENGTH_SHORT).show()
                                },
                                isDestructive = true
                            )
                        } else {
                            SettingsActionItem(
                                icon = Icons.AutoMirrored.Filled.Login,
                                title = "Sign in to YouTube Music",
                                subtitle = "Sync library, playlists & personalized home feed",
                                onClick = onNavigateToYouTubeLogin
                            )
                        }
                    }
                }

                item {
                    SettingsSection(title = "Last.fm") {
                        if (!lastFmSessionKey.isNullOrBlank()) {
                            SettingsActionItem(
                                icon = Icons.Filled.AccountCircle,
                                title = lastFmUsername ?: "Connected",
                                subtitle = "Tap to sign out of Last.fm",
                                onClick = {
                                    scope.launch {
                                        settingsDataStore.setLastfmSession(null, null)
                                        settingsDataStore.setLastfmScrobbleEnabled(false)
                                        Toast.makeText(context, "Signed out of Last.fm", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                isDestructive = true
                            )
                            SettingsDivider()
                            SettingsSwitchItem(
                                icon = Icons.Filled.Album,
                                title = "Scrobble Tracks",
                                subtitle = "Send listening history to Last.fm",
                                checked = isScrobblingEnabled,
                                onCheckedChange = { enabled ->
                                    scope.launch { settingsDataStore.setLastfmScrobbleEnabled(enabled) }
                                }
                            )
                        } else {
                            SettingsActionItem(
                                icon = Icons.AutoMirrored.Filled.Login,
                                title = "Sign in to Last.fm",
                                subtitle = "Scrobble tracks you play",
                                onClick = onShowLastFmLoginDialog
                            )
                        }
                    }
                }
            }

            SettingsCategory.APPEARANCE -> {
                item {
                    SettingsSection(title = "Theme") {
                        SettingsSelectItem(
                            icon = Icons.Filled.Palette,
                            title = "Theme Mode",
                            value = appTheme,
                            onClick = onShowThemeDialog
                        )
                        SettingsDivider()
                        SettingsSwitchItem(
                            icon = Icons.Filled.ColorLens,
                            title = "Dynamic Colors",
                            subtitle = "Match system wallpaper on Android 12+",
                            checked = dynamicColors,
                            onCheckedChange = { enabled ->
                                scope.launch { settingsDataStore.setDynamicColor(enabled) }
                            }
                        )
                        SettingsDivider()
                        SettingsSwitchItem(
                            icon = Icons.Filled.DarkMode,
                            title = "Pure Black (AMOLED)",
                            subtitle = "True black background in dark mode",
                            checked = amoledBlack,
                            onCheckedChange = { enabled ->
                                scope.launch { settingsDataStore.setPureBlack(enabled) }
                            }
                        )
                        SettingsDivider()
                        SettingsSwitchItem(
                            icon = Icons.Filled.BlurOn,
                            title = "Blur Player Backdrop",
                            subtitle = "Soft artwork blur on the player screen",
                            checked = blurPlayerBackground,
                            onCheckedChange = { enabled ->
                                scope.launch { settingsDataStore.setBlurPlayerBackground(enabled) }
                            }
                        )
                        SettingsDivider()
                        SettingsSwitchItem(
                            icon = Icons.Filled.ScreenRotation,
                            title = "Allow Landscape",
                            subtitle = "Rotate screen across the app",
                            checked = allowRotation,
                            onCheckedChange = { enabled ->
                                scope.launch { settingsDataStore.setAllowLandscape(enabled) }
                            }
                        )
                    }
                }

                item {
                    SettingsSection(title = "Accent Color") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(pastelAccents) { (name, color) ->
                                    val isSelected = accentColorName == name
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(10.dp))
                                            .clickable {
                                                scope.launch { settingsDataStore.setPastelAccent(name) }
                                            }
                                            .padding(4.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clip(CircleShape)
                                                .background(color)
                                                .border(
                                                    width = if (isSelected) 2.dp else 1.dp,
                                                    color = if (isSelected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                                    shape = CircleShape
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (isSelected) {
                                                Icon(
                                                    imageVector = Icons.Filled.Check,
                                                    contentDescription = "Selected",
                                                    tint = Color.Black,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = name,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            SettingsCategory.PLAYBACK -> {
                item {
                    SettingsSection(title = "Audio") {
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
                }
            }

            SettingsCategory.LYRICS_AI -> {
                item {
                    SettingsSection(title = "Lyrics & Translation") {
                        SettingsSwitchItem(
                            icon = Icons.Filled.Translate,
                            title = "Translate Lyrics",
                            subtitle = "Show Google Neural translated lines below lyrics",
                            checked = translateLyrics,
                            onCheckedChange = { enabled ->
                                scope.launch { settingsDataStore.setLyricsTranslateEnabled(enabled) }
                            }
                        )
                        if (translateLyrics) {
                            SettingsDivider()
                            SettingsSelectItem(
                                icon = Icons.Filled.Language,
                                title = "Target Language",
                                value = targetLanguage.uppercase(),
                                onClick = onShowLanguageDialog
                            )
                        }
                    }
                }
            }

            SettingsCategory.DOWNLOADS -> {
                item {
                    SettingsSection(title = "Downloads & Cache") {
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
                        SettingsDivider()
                        SettingsActionItem(
                            icon = Icons.Filled.CleaningServices,
                            title = "Clear Cache",
                            subtitle = "Remove temporary cached images and streams",
                            onClick = onShowClearCacheDialog
                        )
                    }
                }
            }

            SettingsCategory.SECURITY -> {
                item {
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
                }
            }

            SettingsCategory.ABOUT -> {
                item {
                    SettingsSection(title = "Application") {
                        SettingsActionItem(
                            icon = Icons.Filled.SystemUpdate,
                            title = "Check for Updates",
                            subtitle = if (isCheckingForUpdate) "Checking..." else "Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                            onClick = onCheckForUpdate
                        )
                        SettingsDivider()
                        SettingsActionItem(
                            icon = Icons.Filled.Code,
                            title = "Source Code",
                            subtitle = "github.com/israrxy/Raazi",
                            onClick = {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/israrxy/Raazi"))
                                context.startActivity(intent)
                            }
                        )
                        SettingsDivider()
                        SettingsActionItem(
                            icon = Icons.Filled.Person,
                            title = "Instagram",
                            subtitle = "@israrxy",
                            onClick = {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.instagram.com/israrxy/"))
                                context.startActivity(intent)
                            }
                        )
                        SettingsDivider()
                        SettingsActionItem(
                            icon = Icons.Filled.Language,
                            title = "Website",
                            subtitle = "israrxy.qzz.io",
                            onClick = {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://israrxy.qzz.io"))
                                context.startActivity(intent)
                            }
                        )
                    }
                }
                item {
                    DeveloperCreditsFooter(
                        onOpenUrl = { url ->
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                            }
                        }
                    )
                }
            }
        }
    }
}
