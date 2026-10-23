package com.israrxy.raazi.ui.components

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.israrxy.raazi.model.MusicItem
import com.israrxy.raazi.ui.theme.*
import com.israrxy.raazi.utils.ThumbnailUtils
import com.israrxy.raazi.viewmodel.MusicPlayerViewModel

enum class QueueFilter(val label: String) {
    ALL("All"),
    FAMILIAR("Familiar"),
    POPULAR("Popular"),
    DISCOVER("Discover"),
    DEEP_CUTS("Deep cuts")
}

/**
 * YouTube Music style "Playing from your queue" sheet:
 *  - "Save" turns the temporary queue into a permanent playlist.
 *  - Smart filter chips re-order the upcoming auto-played songs (sourced live from InnerTube).
 *  - Drag handles let you manually re-order your own queued tracks.
 */
@Composable
fun QueueBottomSheet(
    viewModel: MusicPlayerViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()
    val favoriteTracks by viewModel.favoriteTracks.collectAsStateWithLifecycle()
    val currentArtistSongs by viewModel.currentArtistSongs.collectAsStateWithLifecycle()
    val autoPlayQueue by viewModel.autoPlayQueue.collectAsStateWithLifecycle()

    val density = LocalDensity.current
    val rowHeightPx = with(density) { 76.dp.toPx() }

    val currentTrack = playbackState.currentTrack

    // Pull the real YouTube Music "Up next" autoplay queue for the current song.
    // Only needed in shuffle mode: in stable mode the playlist is the queue.
    LaunchedEffect(currentTrack?.id, playbackState.isShuffleEnabled) {
        if (playbackState.isShuffleEnabled) {
            currentTrack?.let { viewModel.fetchAutoPlayQueue(it) }
        }
    }

    // --- Your own (manually queued) upcoming tracks: draggable, persisted to the service ---
    val userUpcoming = remember(playbackState.playlist, playbackState.currentIndex) {
        playbackState.playlist.drop(playbackState.currentIndex + 1)
    }
    val userSignature = userUpcoming.joinToString { it.id } + "|" + playbackState.currentIndex
    val userList = remember { mutableStateListOf<MusicItem>() }
    LaunchedEffect(userSignature) {
        userList.clear()
        userList.addAll(userUpcoming)
    }

    // --- InnerTube autoplay suggestions: filtered by the smart chips ---
    var selectedFilter by remember { mutableStateOf(QueueFilter.ALL) }
    val autoBase = remember(autoPlayQueue, currentTrack?.id) {
        autoPlayQueue.filter { it.id != currentTrack?.id }
    }
    val autoList = remember { mutableStateListOf<MusicItem>() }
    fun reseedAuto() {
        autoList.clear()
        autoList.addAll(applyQueueFilter(autoBase, selectedFilter, favoriteTracks, currentArtistSongs))
    }
    LaunchedEffect(autoBase, selectedFilter) { reseedAuto() }

    // Drag state for the user queue
    var draggingIndex by remember { mutableStateOf(-1) }
    var dragDelta by remember { mutableFloatStateOf(0f) }
    val listState = rememberLazyListState()

    fun commitUserOrder() {
        val current = viewModel.playbackState.value.currentTrack ?: return
        viewModel.setQueueOrder(listOf(current) + userList)
    }

    val totalUpcoming = userList.size + autoList.size

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 28.dp)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Playing from your queue",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (totalUpcoming == 1) "1 song upcoming" else "$totalUpcoming songs upcoming",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(
                onClick = {
                    val tracks = listOfNotNull(currentTrack) + userList + autoList
                    viewModel.saveCurrentQueueAsPlaylist(tracks = tracks)
                    Toast.makeText(context, "Queue saved to playlists", Toast.LENGTH_SHORT).show()
                    onDismiss()
                }
            ) {
                Text(
                    text = "Save",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Smart filter chips (operate on the InnerTube autoplay section)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            QueueFilter.values().forEach { filter ->
                FilterChip(
                    selected = selectedFilter == filter,
                    onClick = { selectedFilter = filter },
                    label = { Text(filter.label) },
                    leadingIcon = if (selectedFilter == filter) {
                        { Icon(Icons.Filled.Check, null, modifier = Modifier.size(16.dp)) }
                    } else null,
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        labelColor = MaterialTheme.colorScheme.onSurface
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Now playing (not draggable)
        if (currentTrack != null) {
            NowPlayingRow(track = currentTrack)
            Divider(
                color = MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }

        if (userList.isEmpty() && (!playbackState.isShuffleEnabled || autoList.isEmpty())) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Nothing lined up yet.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxHeight(0.7f),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                // In stable (non-shuffle) mode the queue is a playlist: it plays in
                // the order the user built it. Radio suggestions are only relevant
                // when shuffle is on, so showing them first buried the real queue
                // and made the list appear to change as tracks advanced.
                val showRadioSuggestions = playbackState.isShuffleEnabled

                if (showRadioSuggestions && autoList.isNotEmpty()) {
                    item {
                        SectionHeader(text = "Up next")
                    }
                    itemsIndexed(autoList, key = { _, item -> "a_" + item.id }) { _, item ->
                        AutoPlayRow(
                            track = item,
                            onPlayNext = {
                                viewModel.playNext(listOf(item))
                                Toast.makeText(context, "Playing next", Toast.LENGTH_SHORT).show()
                            },
                            onAddToQueue = { viewModel.addToQueue(item) },
                            onHide = {
                                autoList.remove(item)
                            }
                        )
                    }
                }

                if (userList.isNotEmpty()) {
                    item {
                        SectionHeader(
                            text = if (showRadioSuggestions) "Your queue" else "Next up"
                        )
                    }
                    itemsIndexed(userList, key = { _, item -> "u_" + item.id }) { index, item ->
                        val isDragging = draggingIndex == index
                        Box(
                            modifier = Modifier
                                .graphicsLayer {
                                    translationY = if (isDragging) dragDelta else 0f
                                    shadowElevation = if (isDragging) 8.dp.toPx() else 0f
                                }
                                .zIndex(if (isDragging) 1f else 0f)
                        ) {
                            QueueRow(
                                track = item,
                                isDragging = isDragging,
                                dragModifier = Modifier.pointerInput(Unit) {
                                    detectDragGestures(
                                        onDragStart = {
                                            draggingIndex = index
                                            dragDelta = 0f
                                        },
                                        onDrag = { change, dragAmount ->
                                            change.consume()
                                            dragDelta += dragAmount.y
                                            val from = draggingIndex
                                            if (from < 0 || userList.isEmpty()) return@detectDragGestures
                                            val shift = (dragDelta / rowHeightPx).toInt()
                                            val target = (from + shift).coerceIn(0, userList.lastIndex)
                                            if (target != from) {
                                                val list = userList.toMutableList()
                                                val moved = list.removeAt(from)
                                                list.add(target, moved)
                                                userList.clear()
                                                userList.addAll(list)
                                                draggingIndex = target
                                                dragDelta -= (target - from) * rowHeightPx
                                            }
                                        },
                                        onDragEnd = {
                                            draggingIndex = -1
                                            dragDelta = 0f
                                            commitUserOrder()
                                        },
                                        onDragCancel = {
                                            draggingIndex = -1
                                            dragDelta = 0f
                                        }
                                    )
                                },
                                onPlay = { viewModel.playFromQueue(index) },
                                onRemove = { viewModel.removeFromUpcoming(index) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp)
    )
}

private fun applyQueueFilter(
    upcoming: List<MusicItem>,
    filter: QueueFilter,
    favorites: List<MusicItem>,
    artistSongs: List<MusicItem>
): List<MusicItem> {
    if (filter == QueueFilter.ALL) return upcoming
    val favoriteIds = favorites.map { it.id }.toSet()
    val artistIds = artistSongs.map { it.id }.toSet()
    val score: (MusicItem) -> Int = when (filter) {
        QueueFilter.FAMILIAR -> { it -> if (it.id in favoriteIds) 0 else 1 }
        QueueFilter.DISCOVER -> { it -> if (it.id in favoriteIds) 1 else 0 }
        QueueFilter.DEEP_CUTS -> { it -> if (it.id in artistIds) 0 else 1 }
        // POPULAR keeps YouTube Music's own (popularity) ordering.
        else -> { _ -> 0 }
    }
    return upcoming.sortedBy(score)
}

@Composable
private fun NowPlayingRow(track: MusicItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = ThumbnailUtils.getHighQualityThumbnail(track.thumbnailUrl),
            contentDescription = null,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentScale = ContentScale.Crop
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = track.artist,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Icon(
            imageVector = Icons.Filled.GraphicEq,
            contentDescription = "Now playing",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun QueueRow(
    track: MusicItem,
    isDragging: Boolean,
    dragModifier: Modifier,
    onPlay: () -> Unit,
    onRemove: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (isDragging) MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)
                else Color.Transparent
            )
            .clickable(onClick = onPlay)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Drag handle
        Box(
            modifier = dragModifier.size(36.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.DragHandle,
                contentDescription = "Reorder",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
        }

        AsyncImage(
            model = ThumbnailUtils.getHighQualityThumbnail(track.thumbnailUrl),
            contentDescription = null,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentScale = ContentScale.Crop
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = track.artist,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = onRemove) {
            Icon(
                imageVector = Icons.Outlined.Close,
                contentDescription = "Remove",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun AutoPlayRow(
    track: MusicItem,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onHide: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onPlayNext)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = ThumbnailUtils.getHighQualityThumbnail(track.thumbnailUrl),
            contentDescription = null,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentScale = ContentScale.Crop
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = track.artist,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = onAddToQueue) {
            Icon(
                imageVector = Icons.Outlined.PlaylistAdd,
                contentDescription = "Add to queue",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
        IconButton(onClick = onHide) {
            Icon(
                imageVector = Icons.Outlined.Close,
                contentDescription = "Hide",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
