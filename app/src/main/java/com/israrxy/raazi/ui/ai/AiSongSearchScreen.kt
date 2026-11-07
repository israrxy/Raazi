package com.israrxy.raazi.ui.ai

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
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
import com.israrxy.raazi.viewmodel.AiInputMode
import com.israrxy.raazi.viewmodel.AiSongSearchUiState
import com.israrxy.raazi.viewmodel.AiSongSearchViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiSongSearchScreen(
    onClose: () -> Unit,
    onSearch: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenGuide: () -> Unit,
    viewModel: AiSongSearchViewModel = viewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> viewModel.refreshKeyState()
                Lifecycle.Event.ON_STOP -> viewModel.cancelRecording()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
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

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Find a song",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onOpenGuide) {
                        Icon(Icons.Filled.HelpOutline, contentDescription = "How to get a key")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
                .imePadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (!state.keyConfigured) {
                AiNoKeyState(
                    onOpenSettings = onOpenSettings,
                    onOpenGuide = onOpenGuide
                )
            } else {
                AiInputModeSwitch(
                    selected = state.mode,
                    onSelect = viewModel::setMode
                )
                when (state.mode) {
                    AiInputMode.PHOTO -> PhotoSearchBody(
                        state = state,
                        onPickPhoto = pickPhoto,
                        onSearch = onSearch,
                        onRetry = viewModel::retry,
                        onQueryChange = viewModel::updateSearchQuery,
                        onAlternative = viewModel::useAlternative,
                        onUseLyrics = viewModel::useDetectedLyrics
                    )

                    AiInputMode.LISTEN -> ListenSearchBody(
                        state = state,
                        onStart = requestListen,
                        onStop = viewModel::stopListening,
                        onSearch = onSearch,
                        onRetry = viewModel::retry,
                        onQueryChange = viewModel::updateSearchQuery,
                        onAlternative = viewModel::useAlternative,
                        onUseLyrics = viewModel::useDetectedLyrics
                    )
                }
            }
        }
    }
}

@Composable
private fun PhotoSearchBody(
    state: AiSongSearchUiState,
    onPickPhoto: () -> Unit,
    onSearch: (String) -> Unit,
    onRetry: () -> Unit,
    onQueryChange: (String) -> Unit,
    onAlternative: (SongCandidate) -> Unit,
    onUseLyrics: () -> Unit
) {
    val imageUri = state.imageUri
    if (imageUri == null) {
        AiPhotoPickerState(onPick = onPickPhoto)
        return
    }

    AiPhotoPreview(uri = imageUri)

    val identification = state.identification
    when {
        state.isIdentifying -> AiIdentifyingCard()

        state.error != null -> {
            AiErrorCard(
                message = state.error ?: "Could not identify that photo.",
                onRetry = onRetry
            )
        }

        identification != null -> {
            AiResultCard(
                identification = identification,
                query = state.searchQuery,
                onQueryChange = onQueryChange,
                onSearch = {
                    val query = state.searchQuery.trim()
                    if (query.isNotBlank()) onSearch(query)
                },
                onAlternative = onAlternative,
                onUseLyrics = onUseLyrics
            )
        }
    }

    AiChangePhotoButton(onPick = onPickPhoto)

    Text(
        text = "Photos are sent directly to Google Gemini using your key. " +
            "Raazi does not store or upload them anywhere else.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp)
    )
}

@Composable
private fun ListenSearchBody(
    state: AiSongSearchUiState,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onSearch: (String) -> Unit,
    onRetry: () -> Unit,
    onQueryChange: (String) -> Unit,
    onAlternative: (SongCandidate) -> Unit,
    onUseLyrics: () -> Unit
) {
    val identification = state.identification
    when {
        state.isRecording -> {
            AiListeningCard(
                elapsedMs = state.recordingElapsedMs,
                maxMs = state.recordingMaxMs,
                onStop = onStop
            )
        }

        state.isIdentifying -> AiIdentifyingCard(isListening = true)

        state.error != null -> {
            AiErrorCard(
                message = state.error ?: "Could not identify that song.",
                onRetry = onRetry
            )
        }

        identification != null -> {
            AiResultCard(
                identification = identification,
                query = state.searchQuery,
                onQueryChange = onQueryChange,
                onSearch = {
                    val query = state.searchQuery.trim()
                    if (query.isNotBlank()) onSearch(query)
                },
                onAlternative = onAlternative,
                onUseLyrics = onUseLyrics
            )
            AiListenAgainButton(onStart = onStart)
        }

        else -> AiListenStartCard(onStart = onStart)
    }

    Text(
        text = "Clips are sent directly to Google Gemini using your key. Nothing " +
            "is saved by Raazi. Only record audio you have permission to capture.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp)
    )
}
