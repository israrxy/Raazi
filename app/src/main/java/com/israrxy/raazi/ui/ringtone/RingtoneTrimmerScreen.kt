package com.israrxy.raazi.ui.ringtone

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.israrxy.raazi.viewmodel.MusicPlayerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RingtoneTrimmerScreen(
    viewModel: MusicPlayerViewModel,
    onClose: () -> Unit
) {
    val state by viewModel.ringtoneState.collectAsStateWithLifecycle()
    val track by viewModel.ringtoneTrack.collectAsStateWithLifecycle()
    val ringtoneType by viewModel.ringtoneType.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showSuccessDialog by remember { mutableStateOf(false) }
    var successMessage by remember { mutableStateOf("") }
    var showErrorDialog by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }

    // Remember the last Ready state so permission prompts or recoverable errors don't lose the trimmer UI
    var lastReadyState by remember { mutableStateOf<MusicPlayerViewModel.RingtoneState.Ready?>(null) }

    LaunchedEffect(state) {
        when (val s = state) {
            is MusicPlayerViewModel.RingtoneState.Ready -> {
                lastReadyState = s
            }
            is MusicPlayerViewModel.RingtoneState.Done -> {
                showSuccessDialog = true
                successMessage = s.message
            }
            is MusicPlayerViewModel.RingtoneState.Error -> {
                showErrorDialog = true
                errorMessage = s.message
            }
            is MusicPlayerViewModel.RingtoneState.NeedsPermission -> {
                val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                runCatching { context.startActivity(intent) }
                // Restore Ready state if available so the user stays on the trimmer screen when returning from Settings
                lastReadyState?.let { ready ->
                    viewModel.restoreRingtoneReadyState(ready.filePath, ready.durationMs)
                }
            }
            else -> {}
        }
    }

    if (showSuccessDialog) {
        AlertDialog(
            onDismissRequest = {
                showSuccessDialog = false
                viewModel.resetRingtoneState()
                onClose()
            },
            icon = {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp)
                )
            },
            title = { Text("Ringtone Ready!", textAlign = TextAlign.Center) },
            text = {
                Text(
                    successMessage,
                    textAlign = TextAlign.Center
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showSuccessDialog = false
                        viewModel.resetRingtoneState()
                        onClose()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Done")
                }
            }
        )
    }

    if (showErrorDialog) {
        AlertDialog(
            onDismissRequest = {
                showErrorDialog = false
                val ready = lastReadyState
                if (ready != null) {
                    viewModel.restoreRingtoneReadyState(ready.filePath, ready.durationMs)
                } else {
                    viewModel.resetRingtoneState()
                    onClose()
                }
            },
            icon = {
                Icon(
                    Icons.Default.Error,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(48.dp)
                )
            },
            title = { Text("Couldn't Set Ringtone", textAlign = TextAlign.Center) },
            text = {
                Text(
                    errorMessage,
                    textAlign = TextAlign.Center
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showErrorDialog = false
                        val ready = lastReadyState
                        if (ready != null) {
                            viewModel.restoreRingtoneReadyState(ready.filePath, ready.durationMs)
                        } else {
                            viewModel.resetRingtoneState()
                            onClose()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(if (lastReadyState != null) "Try Again" else "Close")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "Set Ringtone",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        if (track != null) {
                            Text(
                                "${track!!.title} • ${track!!.artist}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.resetRingtoneState()
                        onClose()
                    }) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            val activeReady = (state as? MusicPlayerViewModel.RingtoneState.Ready) ?: lastReadyState
            when (val currentState = state) {
                is MusicPlayerViewModel.RingtoneState.Downloading -> {
                    DownloadingContent(currentState.progress)
                }
                is MusicPlayerViewModel.RingtoneState.Trimming,
                is MusicPlayerViewModel.RingtoneState.Setting -> {
                    ProcessingContent(state is MusicPlayerViewModel.RingtoneState.Setting)
                }
                is MusicPlayerViewModel.RingtoneState.Ready,
                is MusicPlayerViewModel.RingtoneState.NeedsPermission -> {
                    if (activeReady != null) {
                        TrimmerContent(
                            trackTitle = track?.title ?: "Unknown",
                            filePath = activeReady.filePath,
                            durationMs = activeReady.durationMs,
                            ringtoneType = ringtoneType,
                            onRingtoneTypeChange = { viewModel.setRingtoneType(it) },
                            onSetRingtone = { startMs, endMs ->
                                viewModel.setAsRingtone(activeReady.filePath, startMs, endMs)
                            },
                            onRequestPermission = {
                                val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                runCatching { context.startActivity(intent) }
                            }
                        )
                    }
                }
                else -> {}
            }
        }
    }
}

@Composable
private fun DownloadingContent(progress: Int) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.padding(32.dp)
    ) {
        CircularProgressIndicator(modifier = Modifier.size(64.dp))
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            "Preparing audio for trimming...",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            if (progress > 0) "$progress% downloaded" else "Resolving high-compatibility stream...",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (progress > 0) {
            Spacer(modifier = Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = { progress / 100f },
                modifier = Modifier
                    .fillMaxWidth(0.65f)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
            )
        }
    }
}

@Composable
private fun ProcessingContent(isSetting: Boolean) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.padding(32.dp)
    ) {
        CircularProgressIndicator(modifier = Modifier.size(64.dp))
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            if (isSetting) "Applying system ringtone..." else "Trimming audio selection...",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Please wait a moment",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
    }
}
