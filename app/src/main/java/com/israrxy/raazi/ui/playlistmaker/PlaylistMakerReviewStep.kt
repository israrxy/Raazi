package com.israrxy.raazi.ui.playlistmaker

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import com.israrxy.raazi.utils.ThumbnailUtils

@Composable
internal fun PlaylistMakerReviewStep(
    draft: List<MusicItem>,
    onMoveUp: (String) -> Unit,
    onMoveDown: (String) -> Unit,
    onRemove: (String) -> Unit,
    onAddMore: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (draft.isEmpty()) {
        PlaylistMakerMessage(
            title = "No songs yet",
            body = "Go back and add some songs to your playlist.",
            actionLabel = "Add songs",
            onAction = onAddMore,
            modifier = modifier.fillMaxSize()
        )
        return
    }

    val totalDuration = draft.sumOf { it.duration }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 20.dp,
            end = 20.dp,
            top = 8.dp,
            bottom = 16.dp
        )
    ) {
        item {
            Column(modifier = Modifier.padding(bottom = 10.dp)) {
                Text(
                    text = "${draft.size} ${if (draft.size == 1) "song" else "songs"}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${formatTotalDuration(totalDuration)} · use the arrows to reorder",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        itemsIndexed(draft, key = { _, item -> item.id }) { index, item ->
            PlaylistMakerDraftRow(
                index = index,
                item = item,
                canMoveUp = index > 0,
                canMoveDown = index < draft.lastIndex,
                onMoveUp = { onMoveUp(item.id) },
                onMoveDown = { onMoveDown(item.id) },
                onRemove = { onRemove(item.id) }
            )
        }
    }
}

@Composable
private fun PlaylistMakerDraftRow(
    index: Int,
    item: MusicItem,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "${index + 1}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(20.dp)
        )
        AsyncImage(
            model = ThumbnailUtils.getListThumbnail(item.thumbnailUrl),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = item.artist,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = onMoveUp, enabled = canMoveUp, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Move up")
        }
        IconButton(onClick = onMoveDown, enabled = canMoveDown, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Move down")
        }
        IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Remove from draft",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
