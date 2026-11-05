package com.israrxy.raazi.ui.settings

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import androidx.compose.ui.res.painterResource
import com.israrxy.raazi.R
import com.israrxy.raazi.data.account.RaaziAccountManager
import com.israrxy.raazi.data.local.SettingsDataStore
import com.israrxy.raazi.service.LastFmScrobbler
import com.israrxy.raazi.ui.*
import com.israrxy.raazi.viewmodel.FeedRecoveryEngine
import com.israrxy.raazi.viewmodel.MusicPlayerViewModel
import kotlinx.coroutines.launch

/**
 * Dual-Login Settings Section:
 * 1. Raazi Ecosystem (1-Tap Google Sign-In, Google Drive appDataFolder Backup, Last.fm)
 * 2. YouTube Music (Legacy in-app WebView session sync)
 */
@Composable
fun SettingsDualLoginSection(
    viewModel: MusicPlayerViewModel,
    settingsDataStore: SettingsDataStore,
    onNavigateToYouTubeLogin: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var isRaaziSyncing by remember { mutableStateOf(false) }

    // Google Sign-In Activity Result Launcher
    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        scope.launch {
            isRaaziSyncing = true
            val res = RaaziAccountManager.handleSignInResult(result.data, context, settingsDataStore)
            isRaaziSyncing = false
            res.fold(
                onSuccess = { name ->
                    Toast.makeText(context, "Welcome to Raazi, $name! Google Drive connected.", Toast.LENGTH_SHORT).show()
                    scope.launch {
                        FeedRecoveryEngine.recoverPersonalizedFeed(viewModel, context, showFeedback = false)
                    }
                },
                onFailure = {
                    if (it.message != "Sign-in cancelled") {
                        Toast.makeText(context, "Sign in: ${it.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }
    }

    // Raazi Account State
    val raaziEmail by settingsDataStore.raaziUserEmail.collectAsStateWithLifecycle(initialValue = null)
    val raaziName by settingsDataStore.raaziUserName.collectAsStateWithLifecycle(initialValue = null)
    val raaziPicture by settingsDataStore.raaziUserPicture.collectAsStateWithLifecycle(initialValue = null)
    val raaziLastSync by settingsDataStore.raaziLastSync.collectAsStateWithLifecycle(initialValue = 0L)
    val raaziAutoSyncEnabled by settingsDataStore.raaziAutoSyncEnabled.collectAsStateWithLifecycle(initialValue = true)

    // Last.fm State
    val lastFmSessionKey by settingsDataStore.lastfmSessionKey.collectAsStateWithLifecycle(initialValue = null)
    val lastFmUsername by settingsDataStore.lastfmUsername.collectAsStateWithLifecycle(initialValue = null)
    val isScrobblingEnabled by settingsDataStore.lastfmScrobbleEnabled.collectAsStateWithLifecycle(initialValue = false)

    // YouTube Music State
    val isYouTubeLoggedIn by viewModel.isYouTubeLoggedIn.collectAsStateWithLifecycle()
    val youTubeAccountName by viewModel.youTubeAccountName.collectAsStateWithLifecycle()
    val youTubeAccountEmail by viewModel.youTubeAccountEmail.collectAsStateWithLifecycle()
    val useLoginForBrowse by viewModel.useLoginForBrowse.collectAsStateWithLifecycle()
    val isSyncingYouTubeLibrary by viewModel.isSyncingYouTubeLibrary.collectAsStateWithLifecycle()
    val youTubeSyncStatus by viewModel.youTubeSyncStatus.collectAsStateWithLifecycle()

    // Region State
    val contentCountry by settingsDataStore.contentCountry.collectAsStateWithLifecycle(initialValue = "AUTO")
    var showRegionDialog by remember { mutableStateOf(false) }
    val detectedCountryCode = remember { com.israrxy.raazi.data.region.RegionManager.detectHardwareCountry(context) }
    val regionDisplayValue = remember(contentCountry, detectedCountryCode) {
        if (contentCountry.isBlank() || contentCountry.equals("AUTO", ignoreCase = true)) {
            val detectedName = com.israrxy.raazi.data.region.RegionManager.getRegionName(detectedCountryCode)
            "Auto ($detectedName)"
        } else {
            com.israrxy.raazi.data.region.RegionManager.getRegionName(contentCountry)
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ==========================================
        // OPTION 1: RAAZI ECOSYSTEM + LAST.FM
        // ==========================================
        SettingsSection(title = "Raazi Account & Last.fm") {
            if (!raaziEmail.isNullOrBlank()) {
                // Connected Raazi Profile
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (!raaziPicture.isNullOrBlank()) {
                        AsyncImage(
                            model = raaziPicture,
                            contentDescription = "Avatar",
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(22.dp))
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.AccountCircle,
                            contentDescription = null,
                            modifier = Modifier.size(44.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = raaziName?.takeIf { it.isNotBlank() } ?: "Raazi User",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = raaziEmail ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "Google Drive",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                SettingsDivider()

                // Cloud Sync Button
                SettingsActionItem(
                    icon = Icons.Filled.CloudSync,
                    title = if (isRaaziSyncing) "Syncing with Google Drive..." else "Sync Google Drive Library",
                    subtitle = if (raaziLastSync > 0) {
                        "Last synced: ${formatTimestamp(raaziLastSync)}"
                    } else {
                        "Backup playlists, favorites & Last.fm to Google Drive"
                    },
                    onClick = {
                        if (!isRaaziSyncing) {
                            isRaaziSyncing = true
                            scope.launch {
                                val result = RaaziAccountManager.syncWithDrive(
                                    context = context,
                                    settingsDataStore = settingsDataStore,
                                    database = com.israrxy.raazi.RaaziApplication.instance.database
                                )
                                isRaaziSyncing = false
                                result.fold(
                                    onSuccess = { msg ->
                                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                        scope.launch {
                                            FeedRecoveryEngine.recoverPersonalizedFeed(viewModel, context, showFeedback = false)
                                        }
                                    },
                                    onFailure = { Toast.makeText(context, "Sync failed: ${it.message}", Toast.LENGTH_SHORT).show() }
                                )
                            }
                        }
                    }
                )

                SettingsDivider()

                // Auto-Sync Switch
                SettingsSwitchItem(
                    icon = Icons.Filled.Sync,
                    title = "Auto-Sync Library",
                    subtitle = "Automatically back up playlists & likes to Google Drive",
                    checked = raaziAutoSyncEnabled,
                    onCheckedChange = { enabled ->
                        scope.launch {
                            settingsDataStore.setRaaziAutoSyncEnabled(enabled)
                            if (enabled) {
                                RaaziAccountManager.scheduleAutoSync(context)
                            }
                        }
                    }
                )

                SettingsDivider()

                // Last.fm Integration inside Raazi Account
                if (!lastFmSessionKey.isNullOrBlank()) {
                    SettingsActionItem(
                        icon = Icons.Filled.Audiotrack,
                        title = "Last.fm: @${lastFmUsername ?: "connected"}",
                        subtitle = "Tap to disconnect Last.fm",
                        onClick = {
                            scope.launch {
                                settingsDataStore.setLastfmSession(null, null)
                                settingsDataStore.setLastfmScrobbleEnabled(false)
                                Toast.makeText(context, "Last.fm disconnected", Toast.LENGTH_SHORT).show()
                            }
                        },
                        isDestructive = true
                    )
                    SettingsDivider()
                    SettingsSwitchItem(
                        icon = Icons.Filled.Album,
                        title = "Scrobble Tracks",
                        subtitle = "Submit playback history to Last.fm",
                        checked = isScrobblingEnabled,
                        onCheckedChange = { enabled ->
                            scope.launch { settingsDataStore.setLastfmScrobbleEnabled(enabled) }
                        }
                    )
                } else {
                    SettingsActionItem(
                        icon = Icons.Filled.Audiotrack,
                        title = "Connect Last.fm",
                        subtitle = "1-Tap web login to scrobble and unlock stats",
                        onClick = {
                            LastFmScrobbler.launchWebAuth(context)
                        }
                    )
                }

                SettingsDivider()

                // Sign Out of Raazi
                SettingsActionItem(
                    icon = Icons.AutoMirrored.Filled.Logout,
                    title = "Sign Out of Raazi",
                    subtitle = "Disconnect Google Drive backup on this device",
                    onClick = {
                        scope.launch {
                            RaaziAccountManager.signOut(context, settingsDataStore)
                            Toast.makeText(context, "Signed out of Raazi", Toast.LENGTH_SHORT).show()
                        }
                    },
                    isDestructive = true
                )
            } else {
                // Not signed in to Raazi
                SettingsActionItem(
                    iconPainter = painterResource(R.drawable.ic_google_logo),
                    title = "Sign in with Google",
                    subtitle = "1-Tap setup for private Google Drive backup & permanent Last.fm",
                    onClick = {
                        val signInClient = RaaziAccountManager.getGoogleSignInClient(context)
                        googleSignInLauncher.launch(signInClient.signInIntent)
                    }
                )

                SettingsDivider()

                // Standalone Last.fm web login if user only wants scrobbling without Google
                if (!lastFmSessionKey.isNullOrBlank()) {
                    SettingsActionItem(
                        icon = Icons.Filled.Audiotrack,
                        title = "Last.fm: @${lastFmUsername ?: "connected"}",
                        subtitle = "Tap to disconnect",
                        onClick = {
                            scope.launch {
                                settingsDataStore.setLastfmSession(null, null)
                                settingsDataStore.setLastfmScrobbleEnabled(false)
                            }
                        },
                        isDestructive = true
                    )
                } else {
                    SettingsActionItem(
                        icon = Icons.Filled.Audiotrack,
                        title = "Connect Last.fm Only",
                        subtitle = "1-Tap web login for Last.fm scrobbling",
                        onClick = {
                            LastFmScrobbler.launchWebAuth(context)
                        }
                    )
                }
            }
        }

        // ==========================================
        // OPTION 2: YOUTUBE MUSIC (LEGACY)
        // ==========================================
        SettingsSection(title = "YouTube Music (Legacy)") {
            if (isYouTubeLoggedIn) {
                SettingsAccountSummaryItem(
                    title = youTubeAccountName?.takeIf { it.isNotBlank() } ?: "YouTube Music Account",
                    subtitle = youTubeAccountEmail?.takeIf { it.isNotBlank() } ?: "Connected"
                )
                SettingsDivider()
                SettingsSwitchItem(
                    icon = Icons.Filled.Public,
                    title = "Use Login for Browse",
                    subtitle = "Personalize Home feed using your YouTube account",
                    checked = useLoginForBrowse,
                    onCheckedChange = { viewModel.setUseLoginForBrowse(it) }
                )
                SettingsDivider()
                SettingsActionItem(
                    icon = Icons.Filled.Sync,
                    title = if (isSyncingYouTubeLibrary) "Syncing YouTube..." else "Sync YouTube Library",
                    subtitle = youTubeSyncStatus ?: "Pull liked songs and playlists from YouTube",
                    onClick = {
                        if (!isSyncingYouTubeLibrary) {
                            viewModel.syncYouTubeLibrary(isManual = true)
                        }
                    }
                )
                SettingsDivider()
                SettingsActionItem(
                    icon = Icons.Filled.ManageAccounts,
                    title = "Refresh YouTube Session",
                    subtitle = "Re-open YouTube Music login webview",
                    onClick = onNavigateToYouTubeLogin
                )
                SettingsDivider()
                SettingsActionItem(
                    icon = Icons.AutoMirrored.Filled.Logout,
                    title = "Sign Out of YouTube",
                    subtitle = "Disconnect YouTube Music cookies",
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
                    subtitle = "WebView login to sync YouTube cloud library & playlists",
                    onClick = onNavigateToYouTubeLogin
                )
            }
        }

        // ==========================================
        // OPTION 3: CONTENT & REGION
        // ==========================================
        SettingsSection(title = "Content & Region") {
            SettingsSelectItem(
                icon = Icons.Filled.Language,
                title = "Content Region",
                value = regionDisplayValue,
                onClick = { showRegionDialog = true }
            )
        }
    }

    if (showRegionDialog) {
        SettingsRegionDialog(
            currentCountryCode = contentCountry,
            detectedCountryCode = detectedCountryCode,
            onDismiss = { showRegionDialog = false },
            onRegionSelected = { selectedCode ->
                scope.launch {
                    settingsDataStore.setContentCountry(selectedCode)
                    val resolved = if (selectedCode.isBlank() || selectedCode.equals("AUTO", ignoreCase = true)) {
                        com.israrxy.raazi.data.region.RegionManager.detectHardwareCountry(context)
                    } else {
                        selectedCode
                    }
                    com.israrxy.raazi.data.region.RegionManager.applyLocale(
                        resolved,
                        com.israrxy.raazi.data.region.RegionManager.getLanguageCode()
                    )
                    viewModel.refreshHomeContent()
                    Toast.makeText(
                        context,
                        "Region set to ${com.israrxy.raazi.data.region.RegionManager.getRegionName(resolved)}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                showRegionDialog = false
            }
        )
    }
}

private fun formatTimestamp(timestamp: Long): String {
    if (timestamp <= 0) return "Never"
    val diff = System.currentTimeMillis() - timestamp
    val minutes = diff / (60 * 1000)
    val hours = minutes / 60
    return when {
        minutes < 1 -> "Just now"
        minutes < 60 -> "$minutes min ago"
        hours < 24 -> "$hours hours ago"
        else -> "${hours / 24} days ago"
    }
}
