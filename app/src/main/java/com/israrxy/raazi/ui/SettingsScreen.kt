package com.israrxy.raazi.ui

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.israrxy.raazi.BuildConfig
import com.israrxy.raazi.data.account.YouTubeAccountSession
import com.israrxy.raazi.data.local.SettingsDataStore
import com.israrxy.raazi.ui.theme.Emerald500
import com.israrxy.raazi.ui.theme.ErrorRed
import com.israrxy.raazi.viewmodel.MusicPlayerViewModel
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun SettingsScreen(
    viewModel: MusicPlayerViewModel,
    onNavigateToYouTubeLogin: () -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    val settingsDataStore = remember { SettingsDataStore(context) }

    val dataSaverEnabled by settingsDataStore.dataSaverEnabled.collectAsState(initial = false)
    val audioQuality by settingsDataStore.audioQuality.collectAsState(initial = "Very High")
    val crossfadeDuration by settingsDataStore.crossfadeDuration.collectAsState(initial = "Off")
    val useDynamicColor by settingsDataStore.useDynamicColor.collectAsState(initial = true)
    val themeMode by settingsDataStore.themeMode.collectAsState(initial = "System")
    val downloadWifiOnly by settingsDataStore.downloadWifiOnly.collectAsState(initial = false)
    val downloadQuality by settingsDataStore.downloadQuality.collectAsState(initial = "Very High")
    val maxConcurrentDownloads by settingsDataStore.maxConcurrentDownloads.collectAsState(initial = "2")
    
    // Custom configurations
    val blurPlayerBackground by settingsDataStore.blurPlayerBackground.collectAsState(initial = true)
    val pastelAccent by settingsDataStore.pastelAccent.collectAsState(initial = "Emerald")
    val useGeminiImport by settingsDataStore.useGeminiImport.collectAsState(initial = false)
    val geminiApiKey by settingsDataStore.geminiApiKey.collectAsState(initial = "")

    var showApiKeyDialog by remember { mutableStateOf(false) }
    var tempApiKey by remember { mutableStateOf("") }

    val isYouTubeLoggedIn by viewModel.isYouTubeLoggedIn.collectAsState()
    val youTubeAccountName by viewModel.youTubeAccountName.collectAsState()
    val youTubeAccountEmail by viewModel.youTubeAccountEmail.collectAsState()
    val useLoginForBrowse by viewModel.useLoginForBrowse.collectAsState()
    val isSyncingYouTubeLibrary by viewModel.isSyncingYouTubeLibrary.collectAsState()
    val youTubeSyncStatus by viewModel.youTubeSyncStatus.collectAsState()

    var cacheSize by remember { mutableStateOf(calculateCacheSize(context.cacheDir)) }

    LaunchedEffect(youTubeSyncStatus) {
        val status = youTubeSyncStatus ?: return@LaunchedEffect
        Toast.makeText(context, status, Toast.LENGTH_SHORT).show()
        viewModel.clearYouTubeSyncStatus()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(scrollState)
            .padding(horizontal = 24.dp, vertical = 24.dp)
            .padding(bottom = 100.dp)
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Text(
            text = "Appearance, playback, downloads, and account controls in one place.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 20.dp)
        )

        SettingsHeroCard(
            isYouTubeLoggedIn = isYouTubeLoggedIn,
            accountName = youTubeAccountName,
            themeMode = themeMode,
            audioQuality = audioQuality
        )

        Spacer(modifier = Modifier.height(28.dp))

        SettingsSection(title = "YOUTUBE MUSIC") {
            SettingsItem(
                title = if (isYouTubeLoggedIn) {
                    youTubeAccountName ?: "Connected"
                } else {
                    "Connect Account"
                },
                subtitle = "Sign in for synced likes, playlists, and library import",
                value = if (isYouTubeLoggedIn) "Connected" else "Not connected",
                valueColor = if (isYouTubeLoggedIn) Emerald500 else MaterialTheme.colorScheme.onSurfaceVariant,
                showChevron = true,
                onClick = onNavigateToYouTubeLogin
            )

            if (!youTubeAccountEmail.isNullOrBlank()) {
                SettingsItem(
                    title = "Account",
                    subtitle = "Current YouTube Music identity",
                    value = youTubeAccountEmail,
                    valueColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            SettingsToggle(
                title = "Use Account For Browse",
                subtitle = "Use account-backed responses while browsing YouTube Music",
                checked = useLoginForBrowse,
                onCheckedChange = { enabled ->
                    viewModel.setUseLoginForBrowse(enabled)
                }
            )

            SettingsActionButton(
                title = if (isSyncingYouTubeLibrary) "Syncing..." else "Sync Likes & Playlists",
                onClick = { viewModel.syncYouTubeLibrary() },
                enabled = isYouTubeLoggedIn && !isSyncingYouTubeLibrary
            )

            if (isYouTubeLoggedIn) {
                SettingsActionButton(
                    title = "Log Out",
                    onClick = { viewModel.logoutFromYouTube() },
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                )
            } else {
                Text(
                    text = "Sign in to save likes to your YouTube Music account and create synced playlists.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        SettingsSection(title = "THEME") {
            SettingsItem(
                title = "App Theme",
                subtitle = "Switch between system, light, and dark mode",
                value = themeMode,
                valueColor = Emerald500,
                onClick = {
                    val modes = listOf("System", "Light", "Dark")
                    val currentIndex = modes.indexOf(themeMode)
                    val nextMode = modes[(currentIndex + 1) % modes.size]
                    scope.launch {
                        settingsDataStore.setThemeMode(nextMode)
                    }
                }
            )

            SettingsToggle(
                title = "Material You / Dynamic Colors",
                subtitle = "Match the app palette to your wallpaper on supported devices",
                checked = useDynamicColor,
                onCheckedChange = { enabled ->
                    scope.launch {
                        settingsDataStore.setDynamicColor(enabled)
                    }
                }
            )

            SettingsItem(
                title = "Pastel Accent Color",
                subtitle = "Choose your custom theme pastel accent color",
                value = pastelAccent,
                valueColor = when (pastelAccent) {
                    "Emerald" -> Emerald500
                    "Lavender" -> Color(0xFFB39DDB)
                    "Sky" -> Color(0xFF90CAF9)
                    "Peach" -> Color(0xFFFFCC80)
                    else -> Emerald500
                },
                onClick = {
                    val accents = listOf("Emerald", "Lavender", "Sky", "Peach")
                    val currentIndex = accents.indexOf(pastelAccent)
                    val nextAccent = accents[(currentIndex + 1) % accents.size]
                    scope.launch {
                        settingsDataStore.setPastelAccent(nextAccent)
                    }
                }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        SettingsSection(title = "PLAYBACK") {
            SettingsToggle(
                title = "Data Saver",
                subtitle = "Prefer lighter streams when bandwidth is limited",
                checked = dataSaverEnabled,
                onCheckedChange = { enabled ->
                    scope.launch {
                        settingsDataStore.setDataSaver(enabled)
                    }
                }
            )

            SettingsItem(
                title = "Audio Quality",
                subtitle = "Default stream quality for playback",
                value = audioQuality,
                valueColor = Emerald500,
                onClick = {
                    val qualities = listOf("Low", "Normal", "High", "Very High")
                    val currentIndex = qualities.indexOf(audioQuality)
                    val nextQuality = qualities[(currentIndex + 1) % qualities.size]
                    scope.launch {
                        settingsDataStore.setAudioQuality(nextQuality)
                    }
                    Toast.makeText(context, "Audio quality: $nextQuality", Toast.LENGTH_SHORT).show()
                }
            )

            SettingsItem(
                title = "Crossfade",
                subtitle = "Blend the end of one track into the next",
                value = crossfadeDuration,
                onClick = {
                    val options = listOf("Off", "3s", "5s", "8s", "12s")
                    val currentIndex = options.indexOf(crossfadeDuration)
                    val nextOption = options[(currentIndex + 1) % options.size]
                    scope.launch {
                        settingsDataStore.setCrossfadeDuration(nextOption)
                    }
                }
            )

            SettingsToggle(
                title = "Blur Player Background",
                subtitle = "Apply a blurred backdrop on the active player layout",
                checked = blurPlayerBackground,
                onCheckedChange = { enabled ->
                    scope.launch {
                        settingsDataStore.setBlurPlayerBackground(enabled)
                    }
                }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        SettingsSection(title = "DOWNLOADS") {
            SettingsToggle(
                title = "WiFi Only",
                subtitle = "Avoid downloading on mobile data",
                checked = downloadWifiOnly,
                onCheckedChange = { enabled ->
                    scope.launch {
                        settingsDataStore.setDownloadWifiOnly(enabled)
                    }
                }
            )

            SettingsItem(
                title = "Download Quality",
                subtitle = "Choose how much storage each song should use",
                value = downloadQuality,
                valueColor = Emerald500,
                onClick = {
                    val qualities = listOf("Normal", "High", "Very High", "Best")
                    val currentIndex = qualities.indexOf(downloadQuality)
                    val nextQuality = qualities[(currentIndex + 1) % qualities.size]
                    scope.launch {
                        settingsDataStore.setDownloadQuality(nextQuality)
                    }
                    Toast.makeText(context, "Download quality: $nextQuality", Toast.LENGTH_SHORT).show()
                }
            )

            SettingsItem(
                title = "Max Concurrent Downloads",
                subtitle = "Control how many downloads can run at once",
                value = maxConcurrentDownloads,
                onClick = {
                    val options = listOf("1", "2", "3", "4")
                    val currentIndex = options.indexOf(maxConcurrentDownloads)
                    val nextOption = options[(currentIndex + 1) % options.size]
                    scope.launch {
                        settingsDataStore.setMaxConcurrentDownloads(nextOption)
                    }
                }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        SettingsSection(title = "GEMINI AI INTEGRATION") {
            SettingsToggle(
                title = "Optimize Playlist Imports",
                subtitle = "Use Gemini 1.5 Flash to automatically improve matching",
                checked = useGeminiImport,
                onCheckedChange = { enabled ->
                    scope.launch {
                        settingsDataStore.setUseGeminiImport(enabled)
                    }
                }
            )

            if (useGeminiImport) {
                SettingsItem(
                    title = "Gemini API Key",
                    subtitle = if (geminiApiKey.isNullOrBlank()) "No key configured" else "Key set (Tap to update)",
                    value = if (geminiApiKey.isNullOrBlank()) "Set Key" else "••••••••",
                    valueColor = if (geminiApiKey.isNullOrBlank()) ErrorRed else Emerald500,
                    onClick = {
                        tempApiKey = geminiApiKey ?: ""
                        showApiKeyDialog = true
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        SettingsSection(title = "APP") {
            SettingsItem(
                title = "Clear Cache",
                subtitle = "Remove temporary artwork, lyrics, and network files",
                value = cacheSize,
                onClick = {
                    try {
                        context.cacheDir.deleteRecursively()
                        cacheSize = calculateCacheSize(context.cacheDir)
                        Toast.makeText(context, "Cache cleared!", Toast.LENGTH_SHORT).show()
                    } catch (_: Exception) {
                        Toast.makeText(context, "Failed to clear cache", Toast.LENGTH_SHORT).show()
                    }
                }
            )

            SettingsItem(
                title = "Reset Settings",
                subtitle = "Restore defaults and clear the saved account session",
                valueColor = ErrorRed,
                onClick = {
                    scope.launch {
                        settingsDataStore.clearAll()
                        YouTubeAccountSession.bootstrap(settingsDataStore)
                        cacheSize = calculateCacheSize(context.cacheDir)
                        Toast.makeText(context, "Settings reset!", Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "Raazi v${BuildConfig.VERSION_NAME}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
    }

    if (showApiKeyDialog) {
        AlertDialog(
            onDismissRequest = { showApiKeyDialog = false },
            title = { Text("Enter Gemini API Key") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Paste your free Gemini API Key from Google AI Studio (ai.google.dev). Keys are stored safely in local preferences.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = tempApiKey,
                        onValueChange = { tempApiKey = it },
                        placeholder = { Text("AIzaSy...") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        settingsDataStore.setGeminiApiKey(tempApiKey)
                        showApiKeyDialog = false
                    }
                }) {
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
}

@Composable
private fun SettingsHeroCard(
    isYouTubeLoggedIn: Boolean,
    accountName: String?,
    themeMode: String,
    audioQuality: String
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = if (isYouTubeLoggedIn) {
                        accountName ?: "YouTube account connected"
                    } else {
                        "Private mode active"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (isYouTubeLoggedIn) {
                        "Library sync and account-backed browse are available."
                    } else {
                        "Raazi stays local until you choose to connect a YouTube Music account."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SettingsBadge(
                    label = "Theme",
                    value = themeMode
                )
                SettingsBadge(
                    label = "Quality",
                    value = audioQuality
                )
                SettingsBadge(
                    label = "Account",
                    value = if (isYouTubeLoggedIn) "Connected" else "Offline",
                    highlight = isYouTubeLoggedIn
                )
            }
        }
    }
}

@Composable
private fun SettingsBadge(
    label: String,
    value: String,
    highlight: Boolean = false
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = if (highlight) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        }
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (highlight) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    var isExpanded by remember { mutableStateOf(true) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { isExpanded = !isExpanded }
                .padding(vertical = 12.dp, horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp
                ),
                color = MaterialTheme.colorScheme.primary
            )
            Icon(
                imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = if (isExpanded) "Collapse" else "Expand",
                tint = MaterialTheme.colorScheme.primary
            )
        }

        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                border = androidx.compose.foundation.BorderStroke(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
                ),
                tonalElevation = 1.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    content = content
                )
            }
        }
    }
}

@Composable
private fun SettingsItem(
    title: String,
    subtitle: String? = null,
    value: String? = null,
    valueColor: Color? = null,
    showChevron: Boolean = false,
    onClick: () -> Unit = {}
) {
    val resolvedValueColor = valueColor ?: MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 14.dp, horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = if (resolvedValueColor == ErrorRed) ErrorRed else MaterialTheme.colorScheme.onSurface
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (value != null) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
                color = resolvedValueColor,
                fontWeight = FontWeight.Medium
            )
        } else if (showChevron) {
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SettingsToggle(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp, horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        )
    }
}

@Composable
private fun SettingsActionButton(
    title: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    containerColor: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = MaterialTheme.colorScheme.onPrimary
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(title, fontWeight = FontWeight.Bold)
    }
}

private fun calculateCacheSize(dir: File): String {
    val size = calculateDirSize(dir)
    return when {
        size >= 1024 * 1024 * 1024 -> String.format("%.1f GB", size / (1024.0 * 1024 * 1024))
        size >= 1024 * 1024 -> String.format("%.1f MB", size / (1024.0 * 1024))
        size >= 1024 -> String.format("%.1f KB", size / 1024.0)
        else -> "$size B"
    }
}

private fun calculateDirSize(dir: File): Long {
    var size = 0L
    if (dir.isDirectory) {
        dir.listFiles()?.forEach { file ->
            size += if (file.isDirectory) calculateDirSize(file) else file.length()
        }
    }
    return size
}
