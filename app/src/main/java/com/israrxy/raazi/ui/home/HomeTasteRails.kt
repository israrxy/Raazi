package com.israrxy.raazi.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.israrxy.raazi.data.db.HomeInteractionEntity
import com.israrxy.raazi.utils.ThumbnailUtils
import com.israrxy.raazi.viewmodel.HomeFeedItem
import com.israrxy.raazi.viewmodel.HomeSection
import com.israrxy.raazi.viewmodel.MusicPlayerViewModel
import com.israrxy.raazi.viewmodel.playMix

@Composable
fun TasteBuilderCard(
    onPick: () -> Unit,
    onDismiss: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Pick artists you like",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Choose at least three and we'll shape your home feed around them.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))
                Button(onClick = onPick, shape = RoundedCornerShape(10.dp)) {
                    Text("Choose artists")
                }
            }
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Dismiss tastebuilder",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun MixRail(
    section: HomeSection,
    viewModel: MusicPlayerViewModel,
    onNavigateToPlayer: () -> Unit
) {
    val tracks = section.musicItems()
    if (tracks.isEmpty()) return

    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(listOf(section), key = { it.id }) { mix ->
            MixCard(
                title = mix.title,
                subtitle = mix.subtitle,
                onClick = {
                    viewModel.playMix(
                        title = mix.title,
                        artists = listOfNotNull(mix.artistName) + mix.subtitle.orEmpty()
                            .split(',')
                            .map { it.trim() }
                            .filter { it.isNotBlank() },
                        items = tracks
                    )
                    onNavigateToPlayer()
                }
            )
        }
    }
}

@Composable
private fun MixCard(
    title: String,
    subtitle: String?,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(160.dp)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(160.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(
                    Brush.linearGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.tertiary
                        )
                    )
                ),
            contentAlignment = Alignment.BottomStart
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "Made for you",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.82f)
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        subtitle?.takeIf { it.isNotBlank() }?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun ListenAgainRail(
    section: HomeSection,
    viewModel: MusicPlayerViewModel,
    onNavigateToPlayer: () -> Unit,
    onNavigateToPlaylist: (String) -> Unit
) {
    val rows = section.items.chunked(2)
    if (rows.isEmpty()) return

    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(rows, key = { row -> row.firstOrNull()?.stableId() ?: row.hashCode().toString() }) { row ->
            Column(
                modifier = Modifier.width(300.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                row.forEach { item ->
                    ListenAgainRow(
                        item = item,
                        section = section,
                        viewModel = viewModel,
                        onNavigateToPlayer = onNavigateToPlayer,
                        onNavigateToPlaylist = onNavigateToPlaylist
                    )
                }
            }
        }
    }
}

@Composable
private fun ListenAgainRow(
    item: HomeFeedItem,
    section: HomeSection,
    viewModel: MusicPlayerViewModel,
    onNavigateToPlayer: () -> Unit,
    onNavigateToPlaylist: (String) -> Unit
) {
    val title: String
    val subtitle: String
    val thumbnail: String
    val onClick: () -> Unit

    when (item) {
        is HomeFeedItem.Music -> {
            title = item.item.title
            subtitle = item.item.artist
            thumbnail = item.item.thumbnailUrl
            onClick = {
                viewModel.recordHomeInteraction(
                    itemId = item.item.id,
                    sectionId = section.id,
                    action = HomeInteractionEntity.ACTION_PLAY,
                    sourceType = section.sourceType
                )
                viewModel.playMusic(item.item)
                onNavigateToPlayer()
            }
        }
        is HomeFeedItem.PlaylistResult -> {
            title = item.playlist.title
            subtitle = item.playlist.description.ifBlank { "Playlist" }
            thumbnail = item.playlist.thumbnailUrl
            onClick = {
                viewModel.recordHomeInteraction(
                    itemId = item.playlist.id,
                    sectionId = section.id,
                    action = HomeInteractionEntity.ACTION_OPEN,
                    sourceType = section.sourceType
                )
                onNavigateToPlaylist(item.playlist.id)
            }
        }
        else -> return
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = ThumbnailUtils.getHighQualityThumbnail(thumbnail),
            contentDescription = title,
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentScale = ContentScale.Crop
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.fillMaxSize()) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
