package com.israrxy.raazi.ui.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.israrxy.raazi.model.MusicContentType
import com.israrxy.raazi.model.MusicItem
import com.israrxy.raazi.model.Playlist
import com.israrxy.raazi.model.canPlayAsTrack
import com.israrxy.raazi.utils.ThumbnailUtils
import com.israrxy.raazi.viewmodel.MusicPlayerViewModel

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MusicCard(
    musicItem: MusicItem,
    viewModel: MusicPlayerViewModel,
    onNavigateToPlayer: () -> Unit,
    onClick: (() -> Unit)? = null,
    openPlayerOnClick: Boolean = true,
    isSelected: Boolean = false,
    isSelectionMode: Boolean = false,
    onToggleSelection: (MusicItem) -> Unit = {},
    onEnterSelection: (MusicItem) -> Unit = {}
) {
    val isArtist = musicItem.contentType == MusicContentType.ARTIST
    val artworkShape = if (isArtist) CircleShape else RoundedCornerShape(8.dp)
    Column(
        modifier = Modifier
            .width(160.dp)
            .combinedClickable(
                onClick = {
                    if (isSelectionMode && !isArtist) {
                        onToggleSelection(musicItem)
                    } else {
                        if (onClick != null) {
                            onClick()
                        } else if (musicItem.canPlayAsTrack()) {
                            viewModel.playMusic(musicItem)
                        }
                        if (openPlayerOnClick) onNavigateToPlayer()
                    }
                },
                onLongClick = { if (!isArtist) onEnterSelection(musicItem) }
            )
    ) {
        Box(
            modifier = Modifier
                .size(160.dp)
                .clip(artworkShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            if (isArtist && musicItem.thumbnailUrl.isBlank()) {
                Icon(
                    imageVector = Icons.Outlined.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(72.dp)
                )
            } else {
                AsyncImage(
                    model = ThumbnailUtils.getHighQualityThumbnail(musicItem.thumbnailUrl),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
            if (isSelectionMode && !isArtist) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(28.dp),
                    shape = CircleShape,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = "Selected",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = musicItem.title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = if (isArtist) "Artist" else musicItem.artist,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun MoodPlaylistCard(
    playlist: Playlist,
    onNavigateToPlaylist: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .width(160.dp)
            .clickable { onNavigateToPlaylist(playlist.id) }
    ) {
        AsyncImage(
            model = ThumbnailUtils.getHighQualityThumbnail(playlist.thumbnailUrl ?: ""),
            contentDescription = null,
            modifier = Modifier
                .size(160.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentScale = ContentScale.Crop
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = playlist.title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = playlist.description.ifBlank { "Playlist" },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun MoodRail(
    section: com.israrxy.raazi.viewmodel.HomeSection,
    viewModel: MusicPlayerViewModel
) {
    val moods = remember(section.items) {
        section.items.filterIsInstance<com.israrxy.raazi.viewmodel.HomeFeedItem.Mood>()
    }
    val selectedChip by viewModel.selectedChip.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    androidx.compose.foundation.lazy.LazyRow(
        state = listState,
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(moods.size, key = { moods[it].title }) { index ->
            val mood = moods[index]
            val isSelected = selectedChip == mood.title
            Box(
                modifier = Modifier
                    .width(150.dp)
                    .height(96.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                    .clickable {
                        viewModel.recordHomeInteraction(
                            itemId = mood.title,
                            sectionId = section.id,
                            action = com.israrxy.raazi.data.db.HomeInteractionEntity.ACTION_OPEN,
                            sourceType = section.sourceType
                        )
                        viewModel.onChipSelected(mood.title)
                    }
                    .padding(12.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    text = mood.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun MoodPlaylistRail(
    section: com.israrxy.raazi.viewmodel.HomeSection,
    onNavigateToPlaylist: (String) -> Unit
) {
    when {
        section.isLoading -> Box(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
        }
        section.items.isEmpty() -> Text(
            text = "No playlists found",
            modifier = Modifier.padding(horizontal = 16.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        else -> {
            val playlistItems = section.items.filterIsInstance<com.israrxy.raazi.viewmodel.HomeFeedItem.PlaylistResult>()
            androidx.compose.foundation.lazy.LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(playlistItems.size) { idx ->
                    MoodPlaylistCard(playlist = playlistItems[idx].playlist, onNavigateToPlaylist = onNavigateToPlaylist)
                }
            }
        }
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(horizontal = 16.dp)
    )
}



