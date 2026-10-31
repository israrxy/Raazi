package com.israrxy.raazi.ui.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.israrxy.raazi.model.MusicItem
import com.israrxy.raazi.ui.theme.Zinc700
import com.israrxy.raazi.ui.theme.Zinc800
import com.israrxy.raazi.utils.ThumbnailUtils
import com.israrxy.raazi.viewmodel.MusicPlayerViewModel

@Composable
fun KeepListeningPanel(
    items: List<MusicItem>,
    viewModel: MusicPlayerViewModel,
    onNavigateToPlayer: () -> Unit,
    sectionId: String = "keep_listening",
    sourceType: String = com.israrxy.raazi.data.db.HomeInteractionEntity.SOURCE_LOCAL,
    isSelectionMode: Boolean = false,
    selectedTracks: Set<MusicItem> = emptySet(),
    onToggleSelection: (MusicItem) -> Unit = {},
    onEnterSelection: (MusicItem) -> Unit = {}
) {
    if (items.isEmpty()) return

    Column(
        modifier = Modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        KeepListeningResumeItem(
            musicItem = items.first(),
            isSelected = selectedTracks.contains(items.first()),
            isSelectionMode = isSelectionMode,
            onToggleSelection = onToggleSelection,
            onEnterSelection = onEnterSelection,
            onClick = {
                viewModel.recordHomeInteraction(
                    itemId = items.first().id,
                    sectionId = sectionId,
                    action = com.israrxy.raazi.data.db.HomeInteractionEntity.ACTION_PLAY,
                    sourceType = sourceType
                )
                if (items.size > 1) {
                    viewModel.playPlaylist(items, 0)
                } else {
                    viewModel.playMusic(items.first())
                }
                onNavigateToPlayer()
            }
        )

        if (items.size > 1) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                itemsIndexed(items.drop(1)) { index, track ->
                    KeepListeningRecentItem(
                        musicItem = track,
                        isSelected = selectedTracks.contains(track),
                        isSelectionMode = isSelectionMode,
                        onToggleSelection = onToggleSelection,
                        onEnterSelection = onEnterSelection,
                        onClick = {
                            viewModel.recordHomeInteraction(
                                itemId = track.id,
                                sectionId = sectionId,
                                action = com.israrxy.raazi.data.db.HomeInteractionEntity.ACTION_PLAY,
                                sourceType = sourceType
                            )
                            viewModel.playPlaylist(items, index + 1)
                            onNavigateToPlayer()
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun KeepListeningResumeItem(
    musicItem: MusicItem,
    onClick: () -> Unit,
    isSelected: Boolean = false,
    isSelectionMode: Boolean = false,
    onToggleSelection: (MusicItem) -> Unit = {},
    onEnterSelection: (MusicItem) -> Unit = {}
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(104.dp)
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(
                onClick = { if (isSelectionMode) onToggleSelection(musicItem) else onClick() },
                onLongClick = { onEnterSelection(musicItem) }
            ),
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (isSelectionMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onToggleSelection(musicItem) },
                    modifier = Modifier.size(28.dp)
                )
            } else {
                AsyncImage(
                    model = ThumbnailUtils.getListThumbnail(musicItem.thumbnailUrl),
                    contentDescription = null,
                    modifier = Modifier
                        .size(84.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentScale = ContentScale.Crop
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Latest Played • Resume",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1
                )
                Text(
                    text = musicItem.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = listOf(musicItem.artist, formatTrackDuration(musicItem.duration))
                        .filter { it.isNotBlank() }
                        .joinToString(" • "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (!isSelectionMode) {
                Surface(
                    modifier = Modifier.size(42.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    shadowElevation = 2.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = "Play",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(27.dp)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun KeepListeningRecentItem(
    musicItem: MusicItem,
    onClick: () -> Unit,
    isSelected: Boolean = false,
    isSelectionMode: Boolean = false,
    onToggleSelection: (MusicItem) -> Unit = {},
    onEnterSelection: (MusicItem) -> Unit = {}
) {
    Surface(
        modifier = Modifier
            .width(244.dp)
            .height(70.dp)
            .clip(RoundedCornerShape(8.dp))
            .combinedClickable(
                onClick = { if (isSelectionMode) onToggleSelection(musicItem) else onClick() },
                onLongClick = { onEnterSelection(musicItem) }
            ),
        shape = RoundedCornerShape(8.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            if (isSelectionMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onToggleSelection(musicItem) },
                    modifier = Modifier.size(26.dp)
                )
            } else {
                AsyncImage(
                    model = ThumbnailUtils.getListThumbnail(musicItem.thumbnailUrl),
                    contentDescription = null,
                    modifier = Modifier
                        .size(54.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentScale = ContentScale.Crop
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = musicItem.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = musicItem.artist,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (!isSelectionMode) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = "Play",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

private fun formatTrackDuration(durationMs: Long): String {
    if (durationMs <= 0L) return ""
    val totalSeconds = durationMs / 1000L
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}

@Composable
fun FilterChipButton(
    text: String,
    onClick: () -> Unit
) {
    FilterChip(
        selected = false,
        onClick = onClick,
        label = {
            Text(text, color = MaterialTheme.colorScheme.onSurface)
        },
        colors = FilterChipDefaults.filterChipColors(
            containerColor = Zinc800,
            selectedContainerColor = Zinc700
        )
    )
}

@Composable
fun QuickPicksPanel(
    items: List<MusicItem>,
    viewModel: MusicPlayerViewModel,
    onNavigateToPlayer: () -> Unit,
    sectionId: String = "quick_picks",
    sourceType: String = com.israrxy.raazi.data.db.HomeInteractionEntity.SOURCE_LOCAL,
    isSelectionMode: Boolean = false,
    selectedTracks: Set<MusicItem> = emptySet(),
    onToggleSelection: (MusicItem) -> Unit = {},
    onEnterSelection: (MusicItem) -> Unit = {}
) {
    if (items.isEmpty()) return

    val featuredTrack = items.first()
    val compactTracks = items.drop(1)

    Column(
        modifier = Modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        QuickPickFeaturedItem(
            musicItem = featuredTrack,
            isSelected = selectedTracks.contains(featuredTrack),
            isSelectionMode = isSelectionMode,
            onToggleSelection = onToggleSelection,
            onEnterSelection = onEnterSelection,
            onClick = {
                viewModel.recordHomeInteraction(
                    itemId = featuredTrack.id,
                    sectionId = sectionId,
                    action = com.israrxy.raazi.data.db.HomeInteractionEntity.ACTION_PLAY,
                    sourceType = sourceType
                )
                if (items.size > 1) {
                    viewModel.playPlaylist(items, 0)
                } else {
                    viewModel.playMusic(featuredTrack)
                }
                onNavigateToPlayer()
            }
        )

        compactTracks.chunked(2).forEachIndexed { rowIndex, rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                rowItems.forEachIndexed { columnIndex, track ->
                    val quickPickIndex = 1 + rowIndex * 2 + columnIndex
                    QuickPickTile(
                        number = quickPickIndex + 1,
                        musicItem = track,
                        modifier = Modifier.weight(1f),
                        isSelected = selectedTracks.contains(track),
                        isSelectionMode = isSelectionMode,
                        onToggleSelection = onToggleSelection,
                        onEnterSelection = onEnterSelection,
                        onClick = {
                            viewModel.recordHomeInteraction(
                                itemId = track.id,
                                sectionId = sectionId,
                                action = com.israrxy.raazi.data.db.HomeInteractionEntity.ACTION_PLAY,
                                sourceType = sourceType
                            )
                            viewModel.playPlaylist(items, quickPickIndex)
                            onNavigateToPlayer()
                        }
                    )
                }

                if (rowItems.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun QuickPickFeaturedItem(
    musicItem: MusicItem,
    onClick: () -> Unit,
    isSelected: Boolean = false,
    isSelectionMode: Boolean = false,
    onToggleSelection: (MusicItem) -> Unit = {},
    onEnterSelection: (MusicItem) -> Unit = {}
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(112.dp)
            .clip(RoundedCornerShape(8.dp))
            .combinedClickable(
                onClick = { if (isSelectionMode) onToggleSelection(musicItem) else onClick() },
                onLongClick = { onEnterSelection(musicItem) }
            ),
        shape = RoundedCornerShape(8.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.62f),
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (isSelectionMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onToggleSelection(musicItem) },
                    modifier = Modifier.size(30.dp)
                )
            } else {
                AsyncImage(
                    model = ThumbnailUtils.getListThumbnail(musicItem.thumbnailUrl),
                    contentDescription = null,
                    modifier = Modifier
                        .size(92.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentScale = ContentScale.Crop
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = musicItem.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = musicItem.artist,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.76f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (!isSelectionMode) {
                Surface(
                    modifier = Modifier.size(44.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    shadowElevation = 2.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = "Play",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }
        }
    }
}

