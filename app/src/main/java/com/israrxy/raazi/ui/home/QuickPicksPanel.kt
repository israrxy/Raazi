package com.israrxy.raazi.ui.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.israrxy.raazi.model.MusicItem
import com.israrxy.raazi.utils.ThumbnailUtils
import com.israrxy.raazi.viewmodel.MusicPlayerViewModel

@OptIn(ExperimentalFoundationApi::class)
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

    val configuration = LocalConfiguration.current
    val columnWidth = (configuration.screenWidthDp.dp - 48.dp).coerceIn(280.dp, 350.dp)
    val columns = items.chunked(4)

    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        items(columns) { columnTracks ->
            Column(
                modifier = Modifier.width(columnWidth),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                columnTracks.forEach { track ->
                    val isSelected = selectedTracks.contains(track)
                    QuickPickRowItem(
                        musicItem = track,
                        isSelected = isSelected,
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
                            viewModel.playFromQuickPicks(track, items)
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
private fun QuickPickRowItem(
    musicItem: MusicItem,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onToggleSelection: (MusicItem) -> Unit,
    onEnterSelection: (MusicItem) -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(8.dp))
            .combinedClickable(
                onClick = { if (isSelectionMode) onToggleSelection(musicItem) else onClick() },
                onLongClick = { onEnterSelection(musicItem) }
            ),
        shape = RoundedCornerShape(8.dp),
        color = if (isSelected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (isSelectionMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onToggleSelection(musicItem) },
                    modifier = Modifier.size(26.dp)
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    AsyncImage(
                        model = ThumbnailUtils.getListThumbnail(musicItem.thumbnailUrl),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
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
                    text = musicItem.artist.ifBlank { "Unknown Artist" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(
                onClick = onClick,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
