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
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.israrxy.raazi.BuildConfig
import com.israrxy.raazi.R
import com.israrxy.raazi.data.UpdateManager
import com.israrxy.raazi.data.local.SettingsDataStore
import com.israrxy.raazi.model.UpdateConfig
import com.israrxy.raazi.ui.components.UpdateDialog
import com.israrxy.raazi.ui.theme.*
import com.israrxy.raazi.viewmodel.MusicPlayerViewModel
import kotlinx.coroutines.launch

private enum class SettingsCategory(
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
                                                onClick = { showLastFmLoginDialog = true }
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
                                            onClick = { showThemeDialog = true }
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
                                            onClick = { showAudioQualityDialog = true }
                                        )
                                        SettingsDivider()
                                        SettingsSelectItem(
                                            icon = Icons.Filled.GraphicEq,
                                            title = "Crossfade",
                                            value = crossfadeDuration,
                                            onClick = { showCrossfadeDialog = true }
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
                                    SettingsSection(title = "Lyrics & AI") {
                                        SettingsSwitchItem(
                                            icon = Icons.Filled.Translate,
                                            title = "Translate Lyrics",
                                            subtitle = "Show translated lines below lyrics",
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
                                                onClick = { showLanguageDialog = true }
                                            )
                                        }
                                        SettingsDivider()
                                        SettingsSwitchItem(
                                            icon = Icons.Filled.AutoAwesome,
                                            title = "Smart Playlist Import",
                                            subtitle = "Clean track metadata with Gemini",
                                            checked = optimizeImportWithGemini,
                                            onCheckedChange = { enabled ->
                                                scope.launch { settingsDataStore.setUseGeminiImport(enabled) }
                                            }
                                        )
                                        SettingsDivider()
                                        SettingsActionItem(
                                            icon = Icons.Filled.Key,
                                            title = "Gemini API Key",
                                            subtitle = if (geminiApiKey.isNullOrBlank()) "Not configured" else "Configured",
                                            onClick = { showApiKeyDialog = true }
                                        )
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
                                            onClick = { showDownloadQualityDialog = true }
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
                                            onClick = { showConcurrentDownloadsDialog = true }
                                        )
                                        SettingsDivider()
                                        SettingsActionItem(
                                            icon = Icons.Filled.CleaningServices,
                                            title = "Clear Cache",
                                            subtitle = "Remove temporary cached images and streams",
                                            onClick = { showClearCacheDialog = true }
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
                                            onClick = { showResetSettingsDialog = true },
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
                                            onClick = {
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
        AlertDialog(
            onDismissRequest = { showClearCacheDialog = false },
            title = { Text("Clear Cache?", fontWeight = FontWeight.SemiBold) },
            text = { Text("Cached thumbnails and temporary streams will be removed. Downloaded tracks are kept.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        runCatching { context.cacheDir.deleteRecursively() }
                        Toast.makeText(context, "Cache cleared", Toast.LENGTH_SHORT).show()
                        showClearCacheDialog = false
                    }
                ) {
                    Text("Clear")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearCacheDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showResetSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showResetSettingsDialog = false },
            title = { Text("Reset Settings?", fontWeight = FontWeight.SemiBold) },
            text = { Text("Restore all preferences to their default values?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            settingsDataStore.clearAll()
                            Toast.makeText(context, "Settings reset", Toast.LENGTH_SHORT).show()
                        }
                        showResetSettingsDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Reset")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetSettingsDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showApiKeyDialog) {
        var keyInput by remember(geminiApiKey) { mutableStateOf(geminiApiKey ?: "") }
        AlertDialog(
            onDismissRequest = { showApiKeyDialog = false },
            title = { Text("Gemini API Key", fontWeight = FontWeight.SemiBold) },
            text = {
                OutlinedTextField(
                    value = keyInput,
                    onValueChange = { keyInput = it },
                    label = { Text("API Key") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch { settingsDataStore.setGeminiApiKey(keyInput.trim()) }
                        showApiKeyDialog = false
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showApiKeyDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showLastFmLoginDialog) {
        var username by remember { mutableStateOf("") }
        var password by remember { mutableStateOf("") }
        var isLoggingIn by remember { mutableStateOf(false) }
        var errorMessage by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { if (!isLoggingIn) showLastFmLoginDialog = false },
            title = { Text("Sign in to Last.fm", fontWeight = FontWeight.SemiBold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text("Username") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Password") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (errorMessage != null) {
                        Text(
                            text = errorMessage!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !isLoggingIn && username.isNotBlank() && password.isNotBlank(),
                    onClick = {
                        isLoggingIn = true
                        errorMessage = null
                        scope.launch {
                            val success = viewModel.connectLastFm(username.trim(), password)
                            isLoggingIn = false
                            if (success) {
                                settingsDataStore.setLastfmScrobbleEnabled(true)
                                showLastFmLoginDialog = false
                            } else {
                                errorMessage = "Invalid credentials or network error"
                            }
                        }
                    }
                ) {
                    if (isLoggingIn) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Sign In")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !isLoggingIn,
                    onClick = { showLastFmLoginDialog = false }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun MinimalSettingsCategoryRow(
    category: SettingsCategory,
    valueText: String?,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = category.icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp)
        )

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = category.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = category.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (!valueText.isNullOrBlank()) {
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = valueText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 110.dp)
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
            modifier = Modifier.size(13.dp)
        )
    }
}

@Composable
fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            Column(content = content)
        }
    }
}

@Composable
fun SettingsSwitchItem(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
fun SettingsSelectItem(
    icon: ImageVector,
    title: String,
    value: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(8.dp))
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
            modifier = Modifier.size(13.dp)
        )
    }
}

@Composable
fun SettingsActionItem(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit,
    isDestructive: Boolean = false
) {
    val contentColor = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    val iconColor = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = contentColor
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SettingsAccountSummaryItem(
    title: String,
    subtitle: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Filled.AccountCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(28.dp)
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun SettingsDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp),
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f)
    )
}

@Composable
fun SelectionDialog(
    title: String,
    options: List<String>,
    selectedOption: String,
    onOptionSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.SemiBold) },
        text = {
            Column {
                options.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onOptionSelected(option) }
                            .padding(vertical = 10.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = option == selectedOption,
                            onClick = { onOptionSelected(option) }
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = option,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun DeveloperCreditsFooter(
    onOpenUrl: (String) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "DEVELOPED BY",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.2.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
                Text(
                    text = "Israr Ahamed (@israrxy)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                DeveloperLinkChip(
                    icon = Icons.Filled.Code,
                    label = "GitHub",
                    onClick = { onOpenUrl("https://github.com/israrxy") },
                    modifier = Modifier.weight(1f)
                )
                DeveloperLinkChip(
                    icon = Icons.Filled.AlternateEmail,
                    label = "Instagram",
                    onClick = { onOpenUrl("https://www.instagram.com/israrxy/") },
                    modifier = Modifier.weight(1f)
                )
                DeveloperLinkChip(
                    icon = Icons.Filled.Language,
                    label = "Website",
                    onClick = { onOpenUrl("https://israrxy.qzz.io") },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun DeveloperLinkChip(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.65f)
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 10.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

