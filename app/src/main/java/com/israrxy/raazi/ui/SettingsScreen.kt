package com.israrxy.raazi.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.israrxy.raazi.BuildConfig
import com.israrxy.raazi.data.UpdateManager
import com.israrxy.raazi.data.local.SettingsDataStore
import com.israrxy.raazi.model.UpdateConfig
import com.israrxy.raazi.ui.components.UpdateDialog
import com.israrxy.raazi.ui.theme.Emerald500
import com.israrxy.raazi.viewmodel.MusicPlayerViewModel
import kotlinx.coroutines.launch

internal enum class SettingsCategory(
    val title: String,
    val subtitle: String,
    val icon: ImageVector
) {
    ACCOUNT(
        title = "Account & Sync",
        subtitle = "YouTube Music & Last.fm",
        icon = Icons.Filled.AccountCircle
    ),
    APPEARANCE(
        title = "Appearance",
        subtitle = "Theme, colors & layout",
        icon = Icons.Filled.Palette
    ),
    PLAYBACK(
        title = "Playback & Audio",
        subtitle = "Streaming quality & crossfade",
        icon = Icons.AutoMirrored.Filled.VolumeUp
    ),
    LYRICS_AI(
        title = "Lyrics & AI",
        subtitle = "Translations & smart import",
        icon = Icons.Filled.AutoAwesome
    ),
    DOWNLOADS(
        title = "Downloads & Storage",
        subtitle = "Offline quality & cache",
        icon = Icons.Filled.Download
    ),
    SECURITY(
        title = "Privacy & Security",
        subtitle = "Biometric lock & reset",
        icon = Icons.Filled.Security
    ),
    ABOUT(
        title = "About",
        subtitle = "Updates & version info",
        icon = Icons.Filled.Info
    )
}

@Composable
fun SettingsScreen(
    viewModel: MusicPlayerViewModel,
    onNavigateToYouTubeLogin: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settingsDataStore = remember { SettingsDataStore(context) }
    val updateManager = remember { UpdateManager() }

    // Collect Settings State from SettingsDataStore
    val audioQuality by settingsDataStore.audioQuality.collectAsStateWithLifecycle(initialValue = "Very High")
    val downloadQuality by settingsDataStore.downloadQuality.collectAsStateWithLifecycle(initialValue = "Very High")
    val wifiOnlyDownload by settingsDataStore.downloadWifiOnly.collectAsStateWithLifecycle(initialValue = false)
    val maxConcurrentDownloads by settingsDataStore.maxConcurrentDownloads.collectAsStateWithLifecycle(initialValue = "2")

    val appTheme by settingsDataStore.themeMode.collectAsStateWithLifecycle(initialValue = "System")
    val dynamicColors by settingsDataStore.useDynamicColor.collectAsStateWithLifecycle(initialValue = false)
    val amoledBlack by settingsDataStore.pureBlack.collectAsStateWithLifecycle(initialValue = false)
    val blurPlayerBackground by settingsDataStore.blurPlayerBackground.collectAsStateWithLifecycle(initialValue = true)
    val allowRotation by settingsDataStore.allowLandscape.collectAsStateWithLifecycle(initialValue = false)
    val hapticFeedback by settingsDataStore.hapticFeedback.collectAsStateWithLifecycle(initialValue = true)

    val crossfadeDuration by settingsDataStore.crossfadeDuration.collectAsStateWithLifecycle(initialValue = "Off")
    val dataSaver by settingsDataStore.dataSaverEnabled.collectAsStateWithLifecycle(initialValue = false)
    val biometricLock by settingsDataStore.biometricLock.collectAsStateWithLifecycle(initialValue = false)

    // Last.fm State
    val lastFmSessionKey by settingsDataStore.lastfmSessionKey.collectAsStateWithLifecycle(initialValue = null)
    val lastFmUsername by settingsDataStore.lastfmUsername.collectAsStateWithLifecycle(initialValue = null)
    val isScrobblingEnabled by settingsDataStore.lastfmScrobbleEnabled.collectAsStateWithLifecycle(initialValue = false)
    val lastFmMessage by viewModel.lastfmMessage.collectAsStateWithLifecycle()

    // AI & Lyrics State
    val translateLyrics by settingsDataStore.lyricsTranslateEnabled.collectAsStateWithLifecycle(initialValue = false)
    val targetLanguage by settingsDataStore.lyricsTranslateLang.collectAsStateWithLifecycle(initialValue = "en")
    val optimizeImportWithGemini by settingsDataStore.useGeminiImport.collectAsStateWithLifecycle(initialValue = false)
    val geminiApiKey by settingsDataStore.geminiApiKey.collectAsStateWithLifecycle(initialValue = null)
    val accentColorName by settingsDataStore.pastelAccent.collectAsStateWithLifecycle(initialValue = "Emerald")

    // Update State
    var updateConfig by remember { mutableStateOf<UpdateConfig?>(null) }
    var isCheckingForUpdate by remember { mutableStateOf(false) }

    // YouTube Account State from ViewModel
    val isYouTubeLoggedIn by viewModel.isYouTubeLoggedIn.collectAsStateWithLifecycle()
    val youTubeAccountName by viewModel.youTubeAccountName.collectAsStateWithLifecycle()
    val youTubeAccountEmail by viewModel.youTubeAccountEmail.collectAsStateWithLifecycle()
    val useLoginForBrowse by viewModel.useLoginForBrowse.collectAsStateWithLifecycle()
    val isSyncingYouTubeLibrary by viewModel.isSyncingYouTubeLibrary.collectAsStateWithLifecycle()
    val youTubeSyncStatus by viewModel.youTubeSyncStatus.collectAsStateWithLifecycle()

    var selectedCategory by rememberSaveable { mutableStateOf<SettingsCategory?>(null) }

    // Dialog States
    var showAudioQualityDialog by remember { mutableStateOf(false) }
    var showDownloadQualityDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showCrossfadeDialog by remember { mutableStateOf(false) }
    var showConcurrentDownloadsDialog by remember { mutableStateOf(false) }
    var showClearCacheDialog by remember { mutableStateOf(false) }
    var showResetSettingsDialog by remember { mutableStateOf(false) }
    var showLastFmLoginDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showApiKeyDialog by remember { mutableStateOf(false) }

    BackHandler(enabled = selectedCategory != null) {
        selectedCategory = null
    }

    LaunchedEffect(lastFmMessage) {
        lastFmMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearLastFmMessage()
        }
    }

    if (updateConfig != null) {
        UpdateDialog(
            config = updateConfig!!,
            onDismiss = { updateConfig = null }
        )
    }

    val pastelAccents = remember {
        listOf(
            "Emerald" to Emerald500,
            "Rose" to Color(0xFFFFB7B2),
            "Peach" to Color(0xFFFFDAC1),
            "Mint" to Color(0xFFE2F0CB),
            "Sage" to Color(0xFFB5EAD7),
            "Lavender" to Color(0xFFC7CEEA),
            "Sky" to Color(0xFFAEC6CF),
            "Lilac" to Color(0xFFD9B8FF),
            "Lemon" to Color(0xFFFDFD96),
            "Coral" to Color(0xFFFFAB91),
            "Aqua" to Color(0xFF80DEEA),
            "Periwinkle" to Color(0xFF9FA8DA),
            "Blush" to Color(0xFFF48FB1),
            "Pistachio" to Color(0xFFC5E1A5),
            "Butter" to Color(0xFFFFF59D),
            "Mauve" to Color(0xFFCE93D8)
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        AnimatedContent(
            targetState = selectedCategory,
            transitionSpec = {
                if (targetState != null) {
                    (slideInHorizontally(tween(220)) { it / 5 } + fadeIn(tween(180))).togetherWith(
                        slideOutHorizontally(tween(220)) { -it / 6 } + fadeOut(tween(150))
                    )
                } else {
                    (slideInHorizontally(tween(220)) { -it / 6 } + fadeIn(tween(180))).togetherWith(
                        slideOutHorizontally(tween(220)) { it / 5 } + fadeOut(tween(150))
                    )
                }
            },
            label = "settings_category_navigation"
        ) { currentCategory ->
            if (currentCategory == null) {
                // Clean, minimal Settings Hub
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .padding(horizontal = 20.dp),
                    contentPadding = PaddingValues(bottom = 120.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    item {
                        Text(
                            text = "Settings",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
                        )
                    }

                    item {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow
                        ) {
                            Column {
                                SettingsCategory.entries.forEachIndexed { index, category ->
                                    val valueText = when (category) {
                                        SettingsCategory.ACCOUNT -> if (isYouTubeLoggedIn) {
                                            youTubeAccountName?.takeIf { it.isNotBlank() } ?: "Connected"
                                        } else {
                                            "Not signed in"
                                        }
                                        SettingsCategory.APPEARANCE -> appTheme
                                        SettingsCategory.PLAYBACK -> audioQuality
                                        SettingsCategory.LYRICS_AI -> if (translateLyrics) targetLanguage.uppercase() else "Off"
                                        SettingsCategory.DOWNLOADS -> downloadQuality
                                        SettingsCategory.SECURITY -> if (biometricLock) "On" else "Off"
                                        SettingsCategory.ABOUT -> "v${BuildConfig.VERSION_NAME}"
                                    }
                                    MinimalSettingsCategoryRow(
                                        category = category,
                                        valueText = valueText,
                                        onClick = { selectedCategory = category }
                                    )
                                    if (index < SettingsCategory.entries.lastIndex) {
                                        SettingsDivider()
                                    }
                                }
                            }
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
            } else {
                // Clean Category Detail Screen
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { selectedCategory = null },
                            modifier = Modifier.size(44.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = currentCategory.title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }

                    SettingsCategoryDetailList(
                        currentCategory = currentCategory,
                        viewModel = viewModel,
                        settingsDataStore = settingsDataStore,
                        scope = scope,
                        context = context,
                        isYouTubeLoggedIn = isYouTubeLoggedIn,
                        youTubeAccountName = youTubeAccountName,
                        youTubeAccountEmail = youTubeAccountEmail,
                        useLoginForBrowse = useLoginForBrowse,
                        isSyncingYouTubeLibrary = isSyncingYouTubeLibrary,
                        youTubeSyncStatus = youTubeSyncStatus,
                        onNavigateToYouTubeLogin = onNavigateToYouTubeLogin,
                        lastFmSessionKey = lastFmSessionKey,
                        lastFmUsername = lastFmUsername,
                        isScrobblingEnabled = isScrobblingEnabled,
                        onShowLastFmLoginDialog = { showLastFmLoginDialog = true },
                        appTheme = appTheme,
                        onShowThemeDialog = { showThemeDialog = true },
                        dynamicColors = dynamicColors,
                        amoledBlack = amoledBlack,
                        blurPlayerBackground = blurPlayerBackground,
                        allowRotation = allowRotation,
                        pastelAccents = pastelAccents,
                        accentColorName = accentColorName,
                        audioQuality = audioQuality,
                        onShowAudioQualityDialog = { showAudioQualityDialog = true },
                        crossfadeDuration = crossfadeDuration,
                        onShowCrossfadeDialog = { showCrossfadeDialog = true },
                        dataSaver = dataSaver,
                        hapticFeedback = hapticFeedback,
                        translateLyrics = translateLyrics,
                        targetLanguage = targetLanguage,
                        onShowLanguageDialog = { showLanguageDialog = true },
                        optimizeImportWithGemini = optimizeImportWithGemini,
                        geminiApiKey = geminiApiKey,
                        onShowApiKeyDialog = { showApiKeyDialog = true },
                        downloadQuality = downloadQuality,
                        onShowDownloadQualityDialog = { showDownloadQualityDialog = true },
                        wifiOnlyDownload = wifiOnlyDownload,
                        maxConcurrentDownloads = maxConcurrentDownloads,
                        onShowConcurrentDownloadsDialog = { showConcurrentDownloadsDialog = true },
                        onShowClearCacheDialog = { showClearCacheDialog = true },
                        biometricLock = biometricLock,
                        onShowResetSettingsDialog = { showResetSettingsDialog = true },
                        isCheckingForUpdate = isCheckingForUpdate,
                        onCheckForUpdate = {
                            if (!isCheckingForUpdate) {
                                isCheckingForUpdate = true
                                scope.launch {
                                    val config = updateManager.checkUpdate()
                                    isCheckingForUpdate = false
                                    if (config != null && config.latestVersionCode > BuildConfig.VERSION_CODE) {
                                        updateConfig = config
                                    } else {
                                        Toast.makeText(
                                            context,
                                            "Up to date (v${BuildConfig.VERSION_NAME})",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            }
                        }
                    )
                }
            }
        }
    }

    // Dialogs
    if (showAudioQualityDialog) {
        SelectionDialog(
            title = "Streaming Audio Quality",
            options = listOf("Low", "Normal", "High", "Very High"),
            selectedOption = audioQuality,
            onOptionSelected = { selected ->
                scope.launch { settingsDataStore.setAudioQuality(selected) }
                showAudioQualityDialog = false
            },
            onDismiss = { showAudioQualityDialog = false }
        )
    }

    if (showDownloadQualityDialog) {
        SelectionDialog(
            title = "Download Quality",
            options = listOf("Low", "Normal", "High", "Very High"),
            selectedOption = downloadQuality,
            onOptionSelected = { selected ->
                scope.launch { settingsDataStore.setDownloadQuality(selected) }
                showDownloadQualityDialog = false
            },
            onDismiss = { showDownloadQualityDialog = false }
        )
    }

    if (showThemeDialog) {
        SelectionDialog(
            title = "Theme Mode",
            options = listOf("System", "Light", "Dark"),
            selectedOption = appTheme,
            onOptionSelected = { selected ->
                scope.launch { settingsDataStore.setThemeMode(selected) }
                showThemeDialog = false
            },
            onDismiss = { showThemeDialog = false }
        )
    }

    if (showCrossfadeDialog) {
        val options = listOf("Off", "2s", "4s", "6s", "8s", "10s", "12s")
        SelectionDialog(
            title = "Crossfade",
            options = options,
            selectedOption = crossfadeDuration,
            onOptionSelected = { selected ->
                scope.launch {
                    settingsDataStore.setCrossfade(selected != "Off")
                    settingsDataStore.setCrossfadeDuration(selected)
                }
                showCrossfadeDialog = false
            },
            onDismiss = { showCrossfadeDialog = false }
        )
    }

    if (showConcurrentDownloadsDialog) {
        val options = listOf("1", "2", "3", "4", "5")
        SelectionDialog(
            title = "Concurrent Downloads",
            options = options,
            selectedOption = maxConcurrentDownloads,
            onOptionSelected = { selected ->
                scope.launch { settingsDataStore.setMaxConcurrentDownloads(selected) }
                showConcurrentDownloadsDialog = false
            },
            onDismiss = { showConcurrentDownloadsDialog = false }
        )
    }

    if (showLanguageDialog) {
        SelectionDialog(
            title = "Target Language",
            options = listOf("en", "es", "fr", "de", "hi", "ur", "ar", "ja", "ko", "pt", "ru", "tr"),
            selectedOption = targetLanguage,
            onOptionSelected = { selected ->
                scope.launch { settingsDataStore.setLyricsTranslateLang(selected) }
                showLanguageDialog = false
            },
            onDismiss = { showLanguageDialog = false }
        )
    }

    if (showClearCacheDialog) {
        SettingsClearCacheDialog(
            context = context,
            onDismiss = { showClearCacheDialog = false }
        )
    }

    if (showResetSettingsDialog) {
        SettingsResetDialog(
            context = context,
            scope = scope,
            settingsDataStore = settingsDataStore,
            onDismiss = { showResetSettingsDialog = false }
        )
    }

    if (showApiKeyDialog) {
        SettingsGeminiApiKeyDialog(
            geminiApiKey = geminiApiKey,
            scope = scope,
            settingsDataStore = settingsDataStore,
            onDismiss = { showApiKeyDialog = false }
        )
    }

    if (showLastFmLoginDialog) {
        SettingsLastFmLoginDialog(
            viewModel = viewModel,
            settingsDataStore = settingsDataStore,
            scope = scope,
            onDismiss = { showLastFmLoginDialog = false }
        )
    }
}
