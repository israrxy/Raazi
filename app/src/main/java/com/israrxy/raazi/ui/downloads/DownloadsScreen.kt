package com.israrxy.raazi.ui.downloads

import android.text.format.Formatter
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.israrxy.raazi.data.db.DownloadEntity
import com.israrxy.raazi.model.MusicItem
import com.israrxy.raazi.viewmodel.MusicPlayerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    viewModel: MusicPlayerViewModel,
    onBack: () -> Unit,
    onNavigateToPlayer: () -> Unit
) {
    val context = LocalContext.current

    val activeDownloads by viewModel.dbActiveDownloads.collectAsState()
    val completedDownloads by viewModel.dbCompletedDownloads.collectAsState()
    val failedDownloads by viewModel.dbFailedDownloads.collectAsState()

    val storageUsed = remember { viewModel.getDownloadStorageUsed() }
    val storageAvailable = remember { viewModel.getAvailableStorage() }

    var showDeleteAllDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Downloads", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    if (completedDownloads.isNotEmpty()) {
                        IconButton(onClick = { showDeleteAllDialog = true }) {
                            Icon(Icons.Default.DeleteSweep, "Delete all")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 100.dp)
        ) {
            // Storage Banner
            item {
                StorageBanner(
                    usedBytes = storageUsed,
                    availableBytes = storageAvailable
                )
            }

            // Active Downloads
            if (activeDownloads.isNotEmpty()) {
                item {
                    SectionTitle(
                        title = "Downloading",
                        count = activeDownloads.size,
                        icon = Icons.Default.Download
                    )
                }
                items(activeDownloads, key = { it.trackId }) { download ->
                    ActiveDownloadItem(
                        download = download,
                        onCancel = { viewModel.cancelDownload(download.trackId) }
                    )
                }
            }

            // Failed Downloads
            if (failedDownloads.isNotEmpty()) {
                item {
                    SectionTitle(
                        title = "Failed",
                        count = failedDownloads.size,
                        icon = Icons.Default.ErrorOutline
                    )
                }
                items(failedDownloads, key = { it.trackId }) { download ->
                    FailedDownloadItem(
                        download = download,
                        onRetry = { viewModel.retryDownload(download.trackId) },
                        onDelete = { viewModel.deleteDownloadedFile(download.trackId) }
                    )
                }
            }

            // Completed Downloads
            if (completedDownloads.isNotEmpty()) {
                item {
                    SectionTitle(
                        title = "Downloaded",
                        count = completedDownloads.size,
                        icon = Icons.Default.CheckCircle
                    )
                }
                items(completedDownloads, key = { it.trackId }) { download ->
                    CompletedDownloadItem(
                        download = download,
                        onPlay = {
                            val track = download.toMusicItem()
                            viewModel.playMusic(track)
                            onNavigateToPlayer()
                        },
                        onDelete = { viewModel.deleteDownloadedFile(download.trackId) },
                        onSetAsRingtone = { viewModel.downloadForRingtone(download.toMusicItem()) },
                        onShowMoreOptions = {
                            android.widget.Toast.makeText(
                                context,
                                "More options for ${download.title}",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        }
                    )
                }
            }

            // Empty State
            if (activeDownloads.isEmpty() && completedDownloads.isEmpty() && failedDownloads.isEmpty()) {
                item {
                    EmptyDownloadsState()
                }
            }
        }
    }

    // Delete All Confirmation Dialog
    if (showDeleteAllDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAllDialog = false },
            title = { Text("Delete All Downloads") },
            text = { Text("This will delete all downloaded files. This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteAllDownloads()
                        showDeleteAllDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) { Text("Delete All") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAllDialog = false }) { Text("Cancel") }
            }
        )
    }
}

