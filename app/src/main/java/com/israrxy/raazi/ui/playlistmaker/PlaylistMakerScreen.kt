package com.israrxy.raazi.ui.playlistmaker

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.israrxy.raazi.ui.theme.Emerald500
import com.israrxy.raazi.viewmodel.PlaylistMakerViewModel
import kotlinx.coroutines.delay

private const val STEP_SEARCH = 0
private const val STEP_REVIEW = 1
private const val STEP_NAME = 2

@Composable
fun PlaylistMakerScreen(
    onClose: () -> Unit,
    onCreated: (String) -> Unit,
    viewModel: PlaylistMakerViewModel = viewModel()
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val results by viewModel.results.collectAsStateWithLifecycle()
    val selectedIds by viewModel.selectedIds.collectAsStateWithLifecycle()
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    val isSearching by viewModel.isSearching.collectAsStateWithLifecycle()
    val searchError by viewModel.searchError.collectAsStateWithLifecycle()
    val isCreating by viewModel.isCreating.collectAsStateWithLifecycle()
    val createError by viewModel.createError.collectAsStateWithLifecycle()
    val createdPlaylist by viewModel.createdPlaylist.collectAsStateWithLifecycle()
    val isYouTubeLoggedIn by viewModel.isYouTubeLoggedIn.collectAsStateWithLifecycle()

    var step by rememberSaveable { mutableIntStateOf(STEP_SEARCH) }
    var playlistName by rememberSaveable { mutableStateOf("My Playlist") }
    var syncedToYouTube by rememberSaveable { mutableStateOf(false) }
    var showDiscardDialog by remember { mutableStateOf(false) }

    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val searchFocusRequester = remember { FocusRequester() }

    LaunchedEffect(createdPlaylist?.id) {
        createdPlaylist?.let { onCreated(it.id) }
    }

    LaunchedEffect(step) {
        if (step == STEP_SEARCH) {
            delay(150L)
            searchFocusRequester.requestFocus()
            keyboardController?.show()
        } else {
            focusManager.clearFocus()
            keyboardController?.hide()
        }
    }

    val handleBack: () -> Unit = {
        when {
            step > STEP_SEARCH -> step -= 1
            draft.isEmpty() -> onClose()
            else -> showDiscardDialog = true
        }
    }

    BackHandler(onBack = handleBack)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            PlaylistMakerHeader(
                step = step,
                songCount = draft.size,
                onClose = handleBack
            )
        },
        bottomBar = {
            PlaylistMakerBottomBar(
                step = step,
                selectedCount = selectedIds.size,
                draftCount = draft.size,
                isCreating = isCreating,
                canCreate = playlistName.isNotBlank() && draft.isNotEmpty(),
                onClearSelection = viewModel::clearSelection,
                onAddSelected = {
                    val added = viewModel.addSelectedToDraft()
                    if (added > 0) {
                        step = STEP_SEARCH
                        searchFocusRequester.requestFocus()
                        keyboardController?.show()
                    }
                },
                onReview = { step = STEP_REVIEW },
                onAddMore = { step = STEP_SEARCH },
                onGoToName = { step = STEP_NAME },
                onCreate = {
                    focusManager.clearFocus()
                    keyboardController?.hide()
                    viewModel.createPlaylist(playlistName, syncedToYouTube)
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            PlaylistMakerStepIndicator(currentStep = step)

            when (step) {
                STEP_SEARCH -> PlaylistMakerSearchStep(
                    query = query,
                    results = results,
                    selectedIds = selectedIds,
                    draftIds = draft.mapTo(mutableSetOf()) { it.id },
                    isSearching = isSearching,
                    searchError = searchError,
                    focusRequester = searchFocusRequester,
                    onQueryChange = viewModel::onQueryChange,
                    onClearQuery = viewModel::clearQuery,
                    onToggleSelection = viewModel::toggleSelection,
                    onRetry = viewModel::retrySearch,
                    modifier = Modifier.weight(1f)
                )

                STEP_REVIEW -> PlaylistMakerReviewStep(
                    draft = draft,
                    onMoveUp = { id -> viewModel.moveInDraft(id, -1) },
                    onMoveDown = { id -> viewModel.moveInDraft(id, 1) },
                    onRemove = viewModel::removeFromDraft,
                    onAddMore = { step = STEP_SEARCH },
                    modifier = Modifier.weight(1f)
                )

                else -> PlaylistMakerNameStep(
                    draft = draft,
                    playlistName = playlistName,
                    onPlaylistNameChange = { playlistName = it },
                    isYouTubeLoggedIn = isYouTubeLoggedIn,
                    syncedToYouTube = syncedToYouTube,
                    onSyncedToYouTubeChange = { syncedToYouTube = it },
                    createError = createError,
                    onDismissError = viewModel::dismissCreateError,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text("Discard draft?") },
            text = { Text("Your ${draft.size} selected songs will be lost.") },
            confirmButton = {
                Button(
                    onClick = {
                        showDiscardDialog = false
                        onClose()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Discard")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) { Text("Keep editing") }
            }
        )
    }
}

@Composable
private fun PlaylistMakerHeader(
    step: Int,
    songCount: Int,
    onClose: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onClose) {
            Icon(
                imageVector = if (step > STEP_SEARCH) Icons.AutoMirrored.Filled.ArrowBack
                else Icons.Default.Close,
                contentDescription = if (step > STEP_SEARCH) "Back" else "Close"
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "New playlist",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = buildString {
                    append("Step ${step + 1} of 3")
                    if (songCount > 0) append(" · $songCount songs")
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PlaylistMakerStepIndicator(currentStep: Int) {
    val labels = listOf("Search", "Review", "Name")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        labels.forEachIndexed { index, label ->
            val active = index <= currentStep
            Surface(
                shape = RoundedCornerShape(50),
                color = if (active) Emerald500 else MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.size(22.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "${index + 1}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (active) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (index == currentStep) FontWeight.Bold else FontWeight.Normal,
                color = if (active) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 6.dp)
            )
            if (index != labels.lastIndex) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp)
                        .height(1.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant)
                )
            }
        }
    }
}

@Composable
private fun PlaylistMakerBottomBar(
    step: Int,
    selectedCount: Int,
    draftCount: Int,
    isCreating: Boolean,
    canCreate: Boolean,
    onClearSelection: () -> Unit,
    onAddSelected: () -> Unit,
    onReview: () -> Unit,
    onAddMore: () -> Unit,
    onGoToName: () -> Unit,
    onCreate: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.background,
        tonalElevation = 3.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            when (step) {
                STEP_SEARCH -> {
                    if (selectedCount > 0) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            TextButton(onClick = onClearSelection) { Text("Clear") }
                            Button(
                                onClick = onAddSelected,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(addLabel(selectedCount))
                            }
                        }
                    } else if (draftCount > 0) {
                        Button(onClick = onReview, modifier = Modifier.fillMaxWidth()) {
                            Text("Review $draftCount ${if (draftCount == 1) "song" else "songs"}")
                        }
                    } else {
                        Text(
                            text = "Select songs above to start your playlist",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                        )
                    }
                }

                STEP_REVIEW -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = onAddMore,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Add more")
                        }
                        Button(
                            onClick = onGoToName,
                            enabled = draftCount > 0,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Next")
                        }
                    }
                }

                else -> {
                    Button(
                        onClick = onCreate,
                        enabled = canCreate && !isCreating,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isCreating) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(if (isCreating) "Creating…" else "Create playlist")
                    }
                }
            }
        }
    }
}

private fun addLabel(count: Int): String =
    if (count == 1) "Add 1 song" else "Add $count songs"
