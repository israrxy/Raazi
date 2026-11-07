package com.israrxy.raazi.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.israrxy.raazi.ui.ai.SongFinderSheet
import com.israrxy.raazi.viewmodel.VoiceSearchViewModel

/**
 * Hosts the two Search overlays so the screen itself only flips booleans and
 * forwards results back into the normal search flow.
 */
@Composable
internal fun SearchOverlays(
    showSongFinder: Boolean,
    showVoiceSearch: Boolean,
    onDismissSongFinder: () -> Unit,
    onSongSearch: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenGuide: () -> Unit,
    onDismissVoiceSearch: () -> Unit,
    onVoiceResult: (String) -> Unit
) {
    if (showSongFinder) {
        SongFinderSheet(
            onDismiss = onDismissSongFinder,
            onSearch = onSongSearch,
            onOpenSettings = onOpenSettings,
            onOpenGuide = onOpenGuide
        )
    }

    if (showVoiceSearch) {
        VoiceSearchOverlay(
            onDismiss = onDismissVoiceSearch,
            onResult = onVoiceResult
        )
    }
}

@Composable
private fun VoiceSearchOverlay(
    onDismiss: () -> Unit,
    onResult: (String) -> Unit,
    viewModel: VoiceSearchViewModel = viewModel()
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.startListening()
        } else {
            viewModel.showPermissionError()
        }
    }

    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            viewModel.startListening()
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    LaunchedEffect(state.finalText) {
        val text = state.finalText?.trim().orEmpty()
        if (text.isNotBlank()) {
            viewModel.clearResult()
            onResult(text)
            onDismiss()
        }
    }

    VoiceSearchDialog(
        state = state,
        onCancel = {
            viewModel.cancel()
            onDismiss()
        },
        onRetry = { viewModel.startListening() },
        onRequestPermission = {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        },
        onOpenSettings = {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        }
    )
}
