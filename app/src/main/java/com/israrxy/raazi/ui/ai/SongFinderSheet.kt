package com.israrxy.raazi.ui.ai

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.israrxy.raazi.data.ai.AudioPayload
import com.israrxy.raazi.viewmodel.AiInputMode
import com.israrxy.raazi.viewmodel.AiSongSearchViewModel
import kotlinx.coroutines.launch

/**
 * Compact song finder that lives inside Search. A confident match closes the
 * sheet and runs the normal Search; unsure results stay here as short rows.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongFinderSheet(
    onDismiss: () -> Unit,
    onSearch: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenGuide: () -> Unit,
    viewModel: AiSongSearchViewModel = viewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val dismissSheet: () -> Unit = {
        scope.launch {
            runCatching { sheetState.hide() }
            viewModel.reset()
            onDismiss()
        }
    }

    LaunchedEffect(Unit) { viewModel.onSheetOpened() }

    LaunchedEffect(state.pendingSearch) {
        val query = state.pendingSearch ?: return@LaunchedEffect
        viewModel.consumePendingSearch()
        runCatching { sheetState.hide() }
        onSearch(query)
        onDismiss()
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> viewModel.refreshKeyState()
                Lifecycle.Event.ON_STOP -> viewModel.cancelRecording()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.reset()
        }
    }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) viewModel.onImagePicked(uri)
    }

    val micPermission = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.startListening()
        } else {
            viewModel.onMicPermissionDenied()
        }
    }

    val pickPhoto: () -> Unit = {
        photoPicker.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }

    val requestListen: () -> Unit = {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            viewModel.startListening()
        } else {
            micPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    val openAppSettings: () -> Unit = {
        runCatching {
            context.startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:${context.packageName}")
                )
            )
        }
    }

    val permissionBlocked = state.mode == AiInputMode.LISTEN &&
        state.error?.contains("Microphone permission", ignoreCase = true) == true
    val changeInput = when {
        permissionBlocked -> openAppSettings
        state.mode == AiInputMode.PHOTO -> pickPhoto
        else -> requestListen
    }
    val changeInputLabel = when {
        permissionBlocked -> "Open settings"
        state.mode == AiInputMode.PHOTO -> "Choose another photo"
        else -> "Listen again"
    }

    ModalBottomSheet(
        onDismissRequest = dismissSheet,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 440.dp)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 16.dp)
        ) {
            SheetHeader(onClose = dismissSheet)
            Spacer(modifier = Modifier.height(6.dp))

            when {
                !state.keyConfigured -> FinderNoKeyBody(
                    onOpenSettings = onOpenSettings,
                    onOpenGuide = onOpenGuide
                )

                state.isRecording -> FinderRecordingPanel(
                    elapsedMs = state.recordingElapsedMs,
                    maxMs = state.recordingMaxMs,
                    levels = state.recordingLevels,
                    canExtend = state.recordingMaxMs < AudioPayload.MAX_EXTENDED_DURATION_MS,
                    onCancel = viewModel::cancelRecording,
                    onExtend = viewModel::extendRecording,
                    onStop = viewModel::stopListening
                )

                state.isIdentifying || state.pendingSearch != null -> FinderStatusRow(
                    loading = true,
                    title = "Identifying…",
                    subtitle = if (state.mode == AiInputMode.LISTEN) {
                        "Gemini is listening for a match."
                    } else {
                        "Gemini is reading your photo."
                    }
                )

                state.error != null -> FinderErrorBody(
                    state = state,
                    onRetry = viewModel::retry,
                    onChangeInput = changeInput,
                    onChangeInputLabel = changeInputLabel
                )

                state.identification != null -> FinderMatchBody(
                    state = state,
                    onSearch = viewModel::searchNow,
                    onRetry = viewModel::retry,
                    onChangeInput = changeInput,
                    onChangeInputLabel = changeInputLabel
                )

                else -> FinderActionBody(
                    onPickPhoto = pickPhoto,
                    onListen = requestListen
                )
            }

            SheetFooter(
                state = state,
                onAutoSearchChange = viewModel::setAutoSearch,
                onOpenSettings = onOpenSettings,
                onDismiss = dismissSheet
            )
        }
    }
}
