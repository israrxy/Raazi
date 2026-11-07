package com.israrxy.raazi.ui.ai

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.israrxy.raazi.data.ai.SongCandidate
import com.israrxy.raazi.data.ai.WebVerifyLevel
import com.israrxy.raazi.viewmodel.AiInputMode
import com.israrxy.raazi.viewmodel.AiSongSearchUiState
import com.israrxy.raazi.viewmodel.AiSongSearchViewModel

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
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val dismissSheet: () -> Unit = {
        viewModel.reset()
        onDismiss()
    }

    LaunchedEffect(Unit) { viewModel.onSheetOpened() }

    LaunchedEffect(state.pendingSearch) {
        val query = state.pendingSearch ?: return@LaunchedEffect
        viewModel.consumePendingSearch()
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
                    onCancel = viewModel::cancelRecording,
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
                onOpenSettings = onOpenSettings
            )
        }
    }
}

@Composable
private fun SheetHeader(onClose: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Find a song",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Photo or a 15-second clip · your Gemini key",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onClose, modifier = Modifier.size(40.dp)) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "Close",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun FinderActionBody(
    onPickPhoto: () -> Unit,
    onListen: () -> Unit
) {
    FinderActionRow(
        icon = Icons.Filled.PhotoCamera,
        title = "Choose a photo",
        subtitle = "Album art, screenshot, or lyrics",
        onClick = onPickPhoto
    )
    FinderActionRow(
        icon = Icons.Filled.Mic,
        title = "Listen for 15 seconds",
        subtitle = "Play the song near your phone",
        onClick = onListen
    )
}

@Composable
private fun FinderNoKeyBody(
    onOpenSettings: () -> Unit,
    onOpenGuide: () -> Unit
) {
    FinderStatusRow(
        icon = Icons.Filled.Key,
        title = "Gemini key needed",
        subtitle = "Photo and audio search use your own free key.",
        actionLabel = "Add key",
        onAction = onOpenSettings
    )
    Spacer(modifier = Modifier.height(4.dp))
    FinderNoticeRow(
        text = "Free key, no credit card.",
        actionLabel = "How",
        onAction = onOpenGuide
    )
}

@Composable
private fun FinderErrorBody(
    state: AiSongSearchUiState,
    onRetry: () -> Unit,
    onChangeInput: () -> Unit,
    onChangeInputLabel: String
) {
    FinderStatusRow(
        icon = Icons.Filled.ErrorOutline,
        title = "Couldn't identify it",
        subtitle = state.error ?: "Try a different photo or a longer clip."
    )
    FinderSecondaryActions(
        retryLabel = "Try again",
        onChangeInput = onChangeInput,
        onChangeInputLabel = onChangeInputLabel,
        onRetry = onRetry
    )
}

@Composable
private fun FinderMatchBody(
    state: AiSongSearchUiState,
    onSearch: (String) -> Unit,
    onRetry: () -> Unit,
    onChangeInput: () -> Unit,
    onChangeInputLabel: String
) {
    val identification = state.identification ?: return
    val candidates = buildList {
        if (identification.hasCandidate) {
            add(
                SongCandidate(
                    title = identification.title.orEmpty(),
                    artist = identification.artist.orEmpty()
                )
            )
        }
        addAll(identification.alternatives)
    }
        .filter { it.query.isNotBlank() }
        .distinctBy { it.query.lowercase() }
        .take(3)

    if (candidates.isNotEmpty()) {
        Text(
            text = "Did you mean?",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 2.dp)
        )
        candidates.forEach { candidate ->
            FinderCandidateRow(
                title = candidate.title,
                artist = candidate.artist,
                onClick = { onSearch(candidate.query) }
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
    }

    val lyrics = identification.lyricsQuery()
    if (lyrics.isNotBlank()) {
        FinderActionRow(
            icon = Icons.Filled.Search,
            title = "Search detected lyrics",
            subtitle = lyrics,
            onClick = { onSearch(lyrics) }
        )
    }

    if (candidates.isEmpty() && lyrics.isBlank()) {
        FinderStatusRow(
            icon = Icons.Filled.HelpOutline,
            title = "No match yet",
            subtitle = identification.message ?: "Try a clearer photo or a longer clip."
        )
    }

    FinderSecondaryActions(
        retryLabel = "Try again",
        onChangeInput = onChangeInput,
        onChangeInputLabel = onChangeInputLabel,
        onRetry = onRetry
    )
}

@Composable
private fun SheetFooter(
    state: AiSongSearchUiState,
    onOpenSettings: () -> Unit
) {
    Spacer(modifier = Modifier.height(8.dp))
    state.webNotice?.let { notice ->
        FinderNoticeRow(
            text = notice,
            actionLabel = if (
                state.usage.enabled &&
                (state.usage.showCapExplanation || state.usage.level != WebVerifyLevel.NORMAL)
            ) {
                "Change"
            } else {
                null
            },
            onAction = onOpenSettings
        )
    }
    FinderNoticeRow(text = "Sent only to Gemini with your key.")
}
