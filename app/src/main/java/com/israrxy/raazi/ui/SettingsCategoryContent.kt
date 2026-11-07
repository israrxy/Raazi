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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel as composeViewModel
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
    onNavigateToGeminiGuide: () -> Unit,
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
    var showFeatureGuideDialog by remember { mutableStateOf(false) }

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
                    com.israrxy.raazi.ui.settings.SettingsDualLoginSection(
                        viewModel = viewModel,
                        settingsDataStore = settingsDataStore,
                        onNavigateToYouTubeLogin = onNavigateToYouTubeLogin
                    )
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

                item {
                    val microPlayerEnabled by settingsDataStore.microPlayerEnabled.collectAsState(initial = true)
                    SettingsSection(title = "Player Experience") {
                        SettingsSwitchItem(
                            icon = Icons.Filled.OpenInFull,
                            title = "Floating Micro Player",
                            subtitle = "Collapse MiniPlayer into a draggable corner tile with cover art and controls",
                            checked = microPlayerEnabled,
                            onCheckedChange = { enabled ->
                                scope.launch { settingsDataStore.setMicroPlayerEnabled(enabled) }
                            }
                        )
                    }
                }
            }

            SettingsCategory.PLAYBACK -> {
                settingsPlaybackSection(
                    audioQuality = audioQuality,
                    onShowAudioQualityDialog = onShowAudioQualityDialog,
                    crossfadeDuration = crossfadeDuration,
                    onShowCrossfadeDialog = onShowCrossfadeDialog,
                    dataSaver = dataSaver,
                    hapticFeedback = hapticFeedback,
                    settingsDataStore = settingsDataStore,
                    scope = scope
                )
            }

            SettingsCategory.AI_GEMINI -> {
                item {
                    val aiViewModel: com.israrxy.raazi.viewmodel.AiSettingsViewModel =
                        composeViewModel()
                    com.israrxy.raazi.ui.settings.SettingsAiSection(
                        viewModel = aiViewModel,
                        onOpenGuide = onNavigateToGeminiGuide
                    )
                }
            }

            SettingsCategory.LYRICS_AI -> {
                settingsLyricsSection(
                    settingsDataStore = settingsDataStore,
                    scope = scope,
                    translateLyrics = translateLyrics,
                    targetLanguage = targetLanguage,
                    onShowLanguageDialog = onShowLanguageDialog
                )
            }

            SettingsCategory.DOWNLOADS -> {
                settingsStorageSection(
                    downloadQuality = downloadQuality,
                    onShowDownloadQualityDialog = onShowDownloadQualityDialog,
                    wifiOnlyDownload = wifiOnlyDownload,
                    maxConcurrentDownloads = maxConcurrentDownloads,
                    onShowConcurrentDownloadsDialog = onShowConcurrentDownloadsDialog,
                    onShowClearCacheDialog = onShowClearCacheDialog,
                    settingsDataStore = settingsDataStore,
                    scope = scope,
                    context = context
                )
            }

            SettingsCategory.SECURITY -> {
                settingsPrivacySection(
                    biometricLock = biometricLock,
                    onShowResetSettingsDialog = onShowResetSettingsDialog,
                    settingsDataStore = settingsDataStore,
                    viewModel = viewModel,
                    scope = scope,
                    context = context
                )
            }

            SettingsCategory.PERMISSIONS -> {
                item {
                    com.israrxy.raazi.ui.settings.SettingsPermissionsSection()
                }
            }

            SettingsCategory.FEEDBACK -> {
                item {
                    SettingsSection(title = "Send Feedback") {
                        SettingsActionItem(
                            icon = Icons.Filled.Feedback,
                            title = "Email the Developer",
                            subtitle = "feedback@israrxy.qzz.io",
                            onClick = {
                                runCatching {
                                    context.startActivity(
                                        Intent(
                                            Intent.ACTION_SENDTO,
                                            Uri.parse("mailto:feedback@israrxy.qzz.io")
                                        ).apply {
                                            putExtra(
                                                Intent.EXTRA_SUBJECT,
                                                "Raazi feedback (v${BuildConfig.VERSION_NAME})"
                                            )
                                        }
                                    )
                                }.onFailure {
                                    Toast.makeText(context, "No email app found", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    }
                }
            }

            SettingsCategory.ABOUT -> {
                item {
                    SettingsSection(title = "Application") {
                        SettingsActionItem(
                            icon = Icons.Filled.AutoAwesome,
                            title = "Feature Guide & Tips",
                            subtitle = "Learn gestures, micro player, lyrics & batch selection",
                            onClick = { showFeatureGuideDialog = true }
                        )
                        SettingsDivider()
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
                            onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/israrxy/Raazi"))) }
                        )
                        SettingsDivider()
                        SettingsActionItem(
                            icon = Icons.Filled.Person,
                            title = "Follow on Instagram",
                            subtitle = "@israrxy • updates, feature drops & behind-the-scenes",
                            onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.instagram.com/israrxy/"))) }
                        )
                        SettingsDivider()
                        SettingsActionItem(
                            icon = Icons.Filled.Language,
                            title = "Website",
                            subtitle = "israrxy.qzz.io",
                            onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://israrxy.qzz.io"))) }
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

    if (showFeatureGuideDialog) {
        FeatureGuideDialog(onDismiss = { showFeatureGuideDialog = false })
    }
}
