package com.israrxy.raazi.ui.ai

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
                    Column {
                        Text(
                            text = "Find a song",
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Photo or audio · your Gemini key",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
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
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
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

                AnimatedContent(
                    targetState = state.mode,
                    transitionSpec = {
                        fadeIn(animationSpec = tween(200)) togetherWith
                            fadeOut(animationSpec = tween(120))
                    },
                    label = "aiInputMode"
                ) { mode ->
                    when (mode) {
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

                AiPrivacyNote(
                    text = if (state.mode == AiInputMode.LISTEN) {
                        "The clip goes straight to Google Gemini with your key. " +
                            "Raazi never saves it. Only record audio you may capture."
                    } else {
                        "Your photo goes straight to Google Gemini with your key. " +
                            "Raazi never stores or uploads it anywhere else."
                    }
                )
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
            AiListeningHero(
                elapsedMs = state.recordingElapsedMs,
                maxMs = state.recordingMaxMs,
                levels = state.recordingLevels,
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

        else -> AiListenHero(onStart = onStart)
    }
}
