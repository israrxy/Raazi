package com.israrxy.raazi.ui.components

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.israrxy.raazi.data.ApkInstaller
import com.israrxy.raazi.data.UpdateManager
import com.israrxy.raazi.model.UpdateConfig
import kotlinx.coroutines.launch
import java.io.File

private const val WEBSITE_URL = "https://raazi.pages.dev"

private fun isDirectApkUrl(url: String): Boolean {
    val path = runCatching { Uri.parse(url).path }.getOrNull().orEmpty()
    return path.endsWith(".apk", ignoreCase = true)
}

private enum class UpdatePhase {
    READY,
    DOWNLOADING,
    READY_TO_INSTALL,
    INSTALLING,
    INSTALLER_OPENED,
    FAILED
}

@Composable
fun UpdateDialog(
    config: UpdateConfig,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val updateManager = remember { UpdateManager() }

    var phase by remember(config.latestVersionCode) { mutableStateOf(UpdatePhase.READY) }
    var progress by remember(config.latestVersionCode) { mutableIntStateOf(0) }
    var downloadedApk by remember { mutableStateOf<File?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        val apk = downloadedApk
        if (apk != null && ApkInstaller.canInstall(context)) {
            ApkInstaller.install(context, apk)
                .onSuccess { phase = UpdatePhase.INSTALLER_OPENED }
                .onFailure {
                    phase = UpdatePhase.FAILED
                    errorMessage = it.message ?: "Could not open the installer"
                }
        } else {
            phase = UpdatePhase.FAILED
            errorMessage = "Allow installs from this app, then try again."
        }
    }

    fun openInstaller(apk: File) {
        if (ApkInstaller.canInstall(context)) {
            ApkInstaller.install(context, apk)
                .onSuccess { phase = UpdatePhase.INSTALLER_OPENED }
                .onFailure {
                    phase = UpdatePhase.FAILED
                    errorMessage = it.message ?: "Could not open the installer"
                }
        } else {
            phase = UpdatePhase.INSTALLING
            permissionLauncher.launch(ApkInstaller.unknownSourcesSettingsIntent(context))
        }
    }

    // A finished download survives dialog dismissal and app restarts. If it is
    // still valid, skip downloading entirely and offer the install prompt.
    LaunchedEffect(config.latestVersionCode, config.downloadUrl) {
        if (phase == UpdatePhase.READY) {
            updateManager.findDownloadedApk(context, config)?.let { cached ->
                downloadedApk = cached
                phase = UpdatePhase.READY_TO_INSTALL
            }
        }
    }

    fun openInBrowser(url: String) {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }
    }

    fun downloadOrInstall() {
        if (!isDirectApkUrl(config.downloadUrl)) {
            // A website link (the normal release config) keeps the classic
            // behavior: open it in the browser instead of downloading it.
            openInBrowser(config.downloadUrl)
            return
        }

        val cached = downloadedApk
        if (cached != null && cached.isFile) {
            // A completed download is reused: never fetch the same APK twice.
            openInstaller(cached)
            return
        }

        downloadedApk = null
        phase = UpdatePhase.DOWNLOADING
        progress = 0
        errorMessage = null
        scope.launch {
            val result = updateManager.downloadApk(context, config) { percent ->
                progress = percent
            }
            result.onSuccess { apk ->
                downloadedApk = apk
                phase = UpdatePhase.READY_TO_INSTALL
                openInstaller(apk)
            }.onFailure {
                phase = UpdatePhase.FAILED
                errorMessage = it.message ?: "Download failed"
            }
        }
    }

    fun retry() {
        // Reuse the downloaded APK when it is still on disk; only fall back to
        // downloading when the cache was cleared or the download never finished.
        downloadOrInstall()
    }

    fun visitWebsite() {
        openInBrowser(WEBSITE_URL)
        if (!config.forceUpdate) onDismiss()
    }

    val properties = if (config.forceUpdate) {
        DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        )
    } else {
        DialogProperties()
    }

    if (config.forceUpdate) {
        BackHandler { /* Force update: block back dismissal */ }
    }

    AlertDialog(
        onDismissRequest = {
            if (!config.forceUpdate && phase != UpdatePhase.DOWNLOADING) {
                onDismiss()
            }
        },
        title = {
            Text(
                text = when (phase) {
                    UpdatePhase.READY -> "Update available"
                    UpdatePhase.DOWNLOADING -> "Downloading update"
                    UpdatePhase.READY_TO_INSTALL -> "Ready to install"
                    UpdatePhase.INSTALLING -> "Opening installer"
                    UpdatePhase.INSTALLER_OPENED -> "Finish installing"
                    UpdatePhase.FAILED -> "Update failed"
                }
            )
        },
        text = {
            when (phase) {
                UpdatePhase.READY -> Column(
                    modifier = Modifier
                        .heightIn(max = 320.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = if (isDirectApkUrl(config.downloadUrl)) {
                            "Version ${config.latestVersionName} is available. " +
                                "Download it inside the app and install it directly."
                        } else {
                            "Version ${config.latestVersionName} is available. " +
                                "Open the download page to get the update."
                        },
                        fontWeight = FontWeight.Medium
                    )
                    if (config.releaseNotes.isNotBlank()) {
                        Text(text = config.releaseNotes)
                    }
                    if (config.forceUpdate) {
                        Text(
                            text = "This update is required.",
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                UpdatePhase.DOWNLOADING -> Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    LinearProgressIndicator(
                        progress = { progress / 100f },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(text = "Downloading… $progress%")
                    Text(
                        text = "Keep the app open until the download finishes.",
                        fontWeight = FontWeight.Normal
                    )
                }

                UpdatePhase.READY_TO_INSTALL -> Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "The update is already downloaded.",
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Open the system installer to finish. " +
                            "There is no need to download it again."
                    )
                }

                UpdatePhase.INSTALLING -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp))
                    Text(text = "Preparing the installer…")
                }

                UpdatePhase.INSTALLER_OPENED -> Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Follow the system installer to finish updating.",
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "If the installer didn't open or the installation failed, " +
                            "open it again or use the website to get the update manually."
                    )
                }

                UpdatePhase.FAILED -> Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = errorMessage ?: "Something went wrong while updating.",
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = if (downloadedApk?.isFile == true) {
                            "The update is still downloaded, so you can retry the " +
                                "installer without downloading it again."
                        } else {
                            "Try again, or open the website to get the update manually."
                        }
                    )
                }
            }
        },
        confirmButton = {
            when (phase) {
                UpdatePhase.READY -> Button(onClick = { downloadOrInstall() }) {
                    Text("Update now")
                }
                UpdatePhase.DOWNLOADING -> {
                    if (!config.forceUpdate) {
                        TextButton(onClick = onDismiss) { Text("Cancel") }
                    }
                }
                UpdatePhase.READY_TO_INSTALL -> Button(
                    onClick = {
                        downloadedApk?.let { openInstaller(it) } ?: downloadOrInstall()
                    }
                ) {
                    Text("Install now")
                }
                UpdatePhase.INSTALLING -> Unit
                UpdatePhase.INSTALLER_OPENED -> Button(
                    onClick = {
                        downloadedApk?.let { openInstaller(it) } ?: downloadOrInstall()
                    }
                ) {
                    Text("Open installer again")
                }
                UpdatePhase.FAILED -> Button(onClick = { retry() }) {
                    Text("Try again")
                }
            }
        },
        dismissButton = {
            when (phase) {
                UpdatePhase.READY -> {
                    if (!config.forceUpdate) {
                        TextButton(onClick = onDismiss) { Text("Later") }
                    }
                }
                UpdatePhase.DOWNLOADING -> Unit
                UpdatePhase.READY_TO_INSTALL,
                UpdatePhase.INSTALLING,
                UpdatePhase.INSTALLER_OPENED,
                UpdatePhase.FAILED -> Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    TextButton(onClick = { visitWebsite() }) { Text("Visit website") }
                    if (!config.forceUpdate) {
                        TextButton(onClick = onDismiss) { Text("Later") }
                    }
                }
            }
        },
        properties = properties
    )
}
