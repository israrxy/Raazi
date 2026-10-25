package com.israrxy.raazi.ui.components

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.israrxy.raazi.model.MusicItem
import com.israrxy.raazi.model.RepeatMode
import com.israrxy.raazi.ui.theme.Emerald500
import com.israrxy.raazi.utils.ThumbnailUtils
import com.israrxy.raazi.viewmodel.MusicPlayerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueBottomSheet(
    viewModel: MusicPlayerViewModel,
    onDismiss: () -> Unit,
    onSaveQueueAsPlaylist: (List<MusicItem>) -> Unit
) {
    val context = LocalContext.current
    val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()
    val currentTrack = playbackState.currentTrack
    val playlist = playbackState.playlist
    val currentIndex = playbackState.currentIndex

    val autoPlayTracks by viewModel.autoPlayQueue.collectAsStateWithLifecycle()
    val isLoadingAutoPlay by viewModel.isLoadingAutoPlay.collectAsStateWithLifecycle()

    var reorderMode by remember { mutableStateOf(false) }

    val upcoming: List<Pair<Int, MusicItem>> = remember(playlist, currentIndex) {
        if (currentIndex in playlist.indices && currentIndex + 1 <= playlist.lastIndex) {
            playlist.subList(currentIndex + 1, playlist.size)
                .mapIndexed { offset, item -> (currentIndex + 1 + offset) to item }
        } else {
            emptyList()
        }
    }

    val extraAutoPlayTracks = remember(autoPlayTracks, playlist, currentTrack) {
        val queuedIds = playlist.map { it.id }.toSet()
        autoPlayTracks.filter { it.id !in queuedIds && it.id != currentTrack?.id }
    }

    // Proactively fetch Up Next radio recommendations when the queue sheet opens
    LaunchedEffect(currentTrack?.id) {
        if (currentTrack != null) {
            viewModel.fetchAutoPlayQueue()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.85f)
            .padding(bottom = 16.dp)
    ) {
        // --- Header ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Playing Queue",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                val subtitle = when {
                    upcoming.isEmpty() && extraAutoPlayTracks.isEmpty() -> "1 track playing"
                    upcoming.isNotEmpty() -> "${upcoming.size} upcoming in queue"
                    else -> "${extraAutoPlayTracks.size} suggested up next"
                }
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Shuffle Pill Toggle with distinct active color
            val shuffleActive = playbackState.isShuffleEnabled
            Surface(
                onClick = { viewModel.toggleShuffle() },
                shape = CircleShape,
                color = if (shuffleActive) {
                    Emerald500.copy(alpha = 0.2f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
                modifier = Modifier
                    .height(38.dp)
                    .then(
                        if (shuffleActive) {
                            Modifier.border(1.dp, Emerald500.copy(alpha = 0.6f), CircleShape)
                        } else {
                            Modifier
                        }
                    )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Shuffle,
                        contentDescription = "Shuffle",
                        tint = if (shuffleActive) Emerald500 else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                    if (shuffleActive) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(Emerald500)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(6.dp))

            // Repeat toggle
            val repeatActive = playbackState.repeatMode != RepeatMode.OFF
            IconButton(onClick = { viewModel.toggleRepeat() }) {
                Icon(
                    imageVector = when (playbackState.repeatMode) {
                        RepeatMode.ONE -> Icons.Default.RepeatOne
                        else -> Icons.Default.Repeat
                    },
                    contentDescription = "Repeat",
                    tint = if (repeatActive) Emerald500 else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (upcoming.size > 1) {
                IconButton(onClick = { reorderMode = !reorderMode }) {
                    Icon(
                        imageVector = if (reorderMode) Icons.Default.Check else Icons.Outlined.SwapVert,
                        contentDescription = if (reorderMode) "Done reordering" else "Reorder queue",
                        tint = if (reorderMode) Emerald500 else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // --- Action pills ---
        if (playlist.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                AssistChip(
                    onClick = { onSaveQueueAsPlaylist(playlist) },
                    label = { Text("Save as playlist") },
                    leadingIcon = {
                        Icon(
                            Icons.Outlined.PlaylistAdd,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                )
                if (upcoming.isNotEmpty()) {
                    AssistChip(
                        onClick = {
                            viewModel.clearUpcomingQueue()
                            Toast.makeText(context, "Cleared upcoming tracks", Toast.LENGTH_SHORT).show()
                        },
                        label = { Text("Clear upcoming") },
                        leadingIcon = {
                            Icon(
                                Icons.Outlined.ClearAll,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    )
                }
            }
        }

        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
            modifier = Modifier.padding(vertical = 8.dp)
        )

        // --- Scrollable body ---
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            if (currentTrack != null) {
                item(key = "header_now_playing") {
                    SectionLabel("Now playing")
                }
                item(key = "now_${currentTrack.id}") {
                    NowPlayingRow(
                        track = currentTrack,
                        isPlaying = playbackState.isPlaying,
                        onPlayPause = { viewModel.playPause() }
                    )
                }
            }

            if (upcoming.isNotEmpty()) {
                item(key = "header_up_next") {
                    SectionLabel(
                        text = if (reorderMode) "Tap arrows to reorder" else "Next in Queue (${upcoming.size})"
                    )
                }
                itemsIndexed(
                    items = upcoming,
                    key = { idx, pair -> "up_${pair.first}_${pair.second.id}_$idx" }
                ) { relativeIdx, (absoluteIndex, track) ->
                    UpcomingQueueRow(
                        track = track,
                        reorderMode = reorderMode,
                        canMoveUp = relativeIdx > 0,
                        canMoveDown = relativeIdx < upcoming.lastIndex,
                        onClick = { viewModel.jumpToQueueIndex(absoluteIndex) },
                        onRemove = { viewModel.removeFromQueue(absoluteIndex) },
                        onMoveUp = {
                            if (relativeIdx > 0) {
                                viewModel.moveQueueItem(absoluteIndex, absoluteIndex - 1)
                            }
                        },
                        onMoveDown = {
                            if (relativeIdx < upcoming.lastIndex) {
                                viewModel.moveQueueItem(absoluteIndex, absoluteIndex + 1)
                            }
                        }
                    )
                }
            }

            // Always show AutoPlay / Radio "Up Next" recommendations if not already in upcoming
            if (extraAutoPlayTracks.isNotEmpty() || isLoadingAutoPlay) {
                item(key = "header_autoplay") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "Up Next • Radio",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Auto-selected songs based on what's playing",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (isLoadingAutoPlay) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                itemsIndexed(
                    items = extraAutoPlayTracks,
                    key = { idx, item -> "auto_${item.id}_$idx" }
                ) { index, track ->
                    AutoPlayRow(
                        track = track,
                        onClick = {
                            viewModel.playFromAutoPlay(index)
                        },
                        onAddToQueue = {
                            viewModel.addToQueue(track)
                            Toast.makeText(context, "Added to queue", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            } else if (upcoming.isEmpty() && !isLoadingAutoPlay) {
                item(key = "empty_upcoming") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 36.dp, horizontal = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Outlined.QueueMusic,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "Fetching Up Next songs...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

