package com.israrxy.raazi.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.israrxy.raazi.model.MusicItem
import com.israrxy.raazi.utils.ThumbnailUtils
import com.israrxy.raazi.viewmodel.HomeFeedItem
import com.israrxy.raazi.viewmodel.HomeSection
import com.israrxy.raazi.viewmodel.MusicPlayerViewModel

@Composable
fun HomeMusicRail(
    section: HomeSection,
    viewModel: MusicPlayerViewModel,
    onNavigateToPlayer: () -> Unit,
    isSelectionMode: Boolean = false,
    selectedTracks: Set<MusicItem> = emptySet(),
    onToggleSelection: (MusicItem) -> Unit = {},
    onEnterSelection: (MusicItem) -> Unit = {}
) {
    val tracks = section.musicItems()
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(tracks, key = { it.id }) { musicItem ->
            MusicCard(
                musicItem = musicItem,
                viewModel = viewModel,
                onNavigateToPlayer = onNavigateToPlayer,
                isSelected = selectedTracks.contains(musicItem),
                isSelectionMode = isSelectionMode,
                onToggleSelection = onToggleSelection,
                onEnterSelection = onEnterSelection,
                onClick = {
                    viewModel.recordHomeInteraction(
                        itemId = musicItem.id,
                        sectionId = section.id,
                        action = com.israrxy.raazi.data.db.HomeInteractionEntity.ACTION_PLAY,
                        sourceType = section.sourceType
                    )
                    val idx = tracks.indexOfFirst { it.id == musicItem.id }.coerceAtLeast(0)
                    if (tracks.size > 1) {
                        viewModel.playPlaylist(tracks, idx)
                    } else {
                        viewModel.playMusic(musicItem)
                    }
                }
            )
        }
    }
}

@Composable
fun HomeFeedRail(
    section: HomeSection,
    viewModel: MusicPlayerViewModel,
    onNavigateToPlayer: () -> Unit,
    onNavigateToPlaylist: (String) -> Unit,
    onNavigateToArtist: (String, String) -> Unit,
    isSelectionMode: Boolean = false,
    selectedTracks: Set<MusicItem> = emptySet(),
    onToggleSelection: (MusicItem) -> Unit = {},
    onEnterSelection: (MusicItem) -> Unit = {}
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(section.items, key = { it.stableId() }) { item ->
            HomeFeedItemCard(
                item = item,
                section = section,
                viewModel = viewModel,
                onNavigateToPlayer = onNavigateToPlayer,
                onNavigateToPlaylist = onNavigateToPlaylist,
                onNavigateToArtist = onNavigateToArtist,
                isSelectionMode = isSelectionMode,
                selectedTracks = selectedTracks,
                onToggleSelection = onToggleSelection,
                onEnterSelection = onEnterSelection
            )
        }
    }
}

@Composable
fun HomeFeedItemCard(
    item: HomeFeedItem,
    section: HomeSection,
    viewModel: MusicPlayerViewModel,
    onNavigateToPlayer: () -> Unit,
    onNavigateToPlaylist: (String) -> Unit,
    onNavigateToArtist: (String, String) -> Unit,
    isSelectionMode: Boolean = false,
    selectedTracks: Set<MusicItem> = emptySet(),
    onToggleSelection: (MusicItem) -> Unit = {},
    onEnterSelection: (MusicItem) -> Unit = {}
) {
    when (item) {
        is HomeFeedItem.Music -> MusicCard(
            musicItem = item.item,
            viewModel = viewModel,
            onNavigateToPlayer = onNavigateToPlayer,
            isSelected = selectedTracks.contains(item.item),
            isSelectionMode = isSelectionMode,
            onToggleSelection = onToggleSelection,
            onEnterSelection = onEnterSelection,
            onClick = {
                viewModel.recordHomeInteraction(
                    itemId = item.item.id,
                    sectionId = section.id,
                    action = com.israrxy.raazi.data.db.HomeInteractionEntity.ACTION_PLAY,
                    sourceType = section.sourceType
                )
                viewModel.playMusic(item.item)
            }
        )
        is HomeFeedItem.YouTube -> {
            val songTrack = (item.item as? com.zionhuang.innertube.models.SongItem)?.toHomeMusicItem()
            YouTubeFeedCard(
                ytItem = item.item,
                isSelected = songTrack != null && selectedTracks.any { it.id == songTrack.id },
                isSelectionMode = isSelectionMode,
                onToggleSelection = { songTrack?.let(onToggleSelection) },
                onEnterSelection = { songTrack?.let(onEnterSelection) },
                onClick = {
                    viewModel.recordHomeInteraction(
                        itemId = item.stableId(),
                        sectionId = section.id,
                        action = if (item.item is com.zionhuang.innertube.models.SongItem) {
                            com.israrxy.raazi.data.db.HomeInteractionEntity.ACTION_PLAY
                        } else {
                            com.israrxy.raazi.data.db.HomeInteractionEntity.ACTION_OPEN
                        },
                        sourceType = section.sourceType
                    )
                    when (val ytItem = item.item) {
                        is com.zionhuang.innertube.models.SongItem -> {
                            val railSongs = section.items
                                .mapNotNull { ((it as? HomeFeedItem.YouTube)?.item as? com.zionhuang.innertube.models.SongItem)?.toHomeMusicItem() }
                            val idx = railSongs.indexOfFirst { it.id == ytItem.id }.coerceAtLeast(0)
                            if (railSongs.size > 1) {
                                viewModel.playPlaylist(railSongs, idx)
                            } else {
                                viewModel.playMusic(ytItem.toHomeMusicItem())
                            }
                            viewModel.fetchAutoPlayQueue()
                            onNavigateToPlayer()
                        }
                        is com.zionhuang.innertube.models.AlbumItem -> onNavigateToPlaylist(ytItem.playlistId)
                        is com.zionhuang.innertube.models.PlaylistItem -> onNavigateToPlaylist(ytItem.id)
                        is com.zionhuang.innertube.models.ArtistItem -> onNavigateToArtist(ytItem.id, ytItem.title)
                    }
                }
            )
        }
        is HomeFeedItem.PlaylistResult -> MoodPlaylistCard(
            playlist = item.playlist,
            onNavigateToPlaylist = {
                viewModel.recordHomeInteraction(
                    itemId = item.playlist.id,
                    sectionId = section.id,
                    action = com.israrxy.raazi.data.db.HomeInteractionEntity.ACTION_OPEN,
                    sourceType = section.sourceType
                )
                onNavigateToPlaylist(it)
            }
        )
        is HomeFeedItem.Mood -> Unit
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun YouTubeFeedCard(
    ytItem: com.zionhuang.innertube.models.YTItem,
    onClick: () -> Unit,
    isSelected: Boolean = false,
    isSelectionMode: Boolean = false,
    onToggleSelection: () -> Unit = {},
    onEnterSelection: () -> Unit = {}
) {
    val isArtist = ytItem is com.zionhuang.innertube.models.ArtistItem
    val isSong = ytItem is com.zionhuang.innertube.models.SongItem
    val title = when (ytItem) {
        is com.zionhuang.innertube.models.SongItem -> ytItem.title
        is com.zionhuang.innertube.models.AlbumItem -> ytItem.title
        is com.zionhuang.innertube.models.ArtistItem -> ytItem.title
        is com.zionhuang.innertube.models.PlaylistItem -> ytItem.title
    }
    val subtitle = when (ytItem) {
        is com.zionhuang.innertube.models.SongItem -> ytItem.artists?.joinToString(", ") { it.name } ?: "Song"
        is com.zionhuang.innertube.models.AlbumItem -> listOfNotNull(
            ytItem.artists?.joinToString(", ") { it.name },
            ytItem.year?.toString()
        ).joinToString(" • ").ifBlank { "Album" }
        is com.zionhuang.innertube.models.ArtistItem -> "Artist"
        is com.zionhuang.innertube.models.PlaylistItem -> ytItem.author?.name ?: "Playlist"
    }
    val thumbnail = when (ytItem) {
        is com.zionhuang.innertube.models.SongItem -> ytItem.thumbnail
        is com.zionhuang.innertube.models.AlbumItem -> ytItem.thumbnail
        is com.zionhuang.innertube.models.ArtistItem -> ytItem.thumbnail
        is com.zionhuang.innertube.models.PlaylistItem -> ytItem.thumbnail
    }

    Column(
        modifier = Modifier
            .width(160.dp)
            .clip(RoundedCornerShape(12.dp))
            .then(
                if (isSong) {
                    Modifier.combinedClickable(
                        onClick = { if (isSelectionMode) onToggleSelection() else onClick() },
                        onLongClick = onEnterSelection
                    )
                } else {
                    Modifier.clickable(onClick = onClick)
                }
            )
            .padding(4.dp),
        horizontalAlignment = if (isArtist) Alignment.CenterHorizontally else Alignment.Start
    ) {
        Box {
            AsyncImage(
                model = ThumbnailUtils.getHighQualityThumbnail(thumbnail ?: ""),
                contentDescription = title,
                modifier = Modifier
                    .size(152.dp)
                    .clip(if (isArtist) CircleShape else RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentScale = ContentScale.Crop
            )
            if (isSelectionMode && isSong) {
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
                                imageVector = Icons.Default.Check,
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
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = if (isArtist) androidx.compose.ui.text.style.TextAlign.Center else androidx.compose.ui.text.style.TextAlign.Start
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = if (isArtist) androidx.compose.ui.text.style.TextAlign.Center else androidx.compose.ui.text.style.TextAlign.Start
        )
    }
}

internal fun com.zionhuang.innertube.models.SongItem.toHomeMusicItem(): MusicItem {
    return MusicItem(
        id = id,
        title = title,
        artist = artists?.joinToString(", ") { it.name } ?: "Unknown Artist",
        duration = (duration ?: 0) * 1000L,
        thumbnailUrl = ThumbnailUtils.getHighQualityThumbnail(thumbnail),
        audioUrl = "",
        videoUrl = id,
        isLive = false
    )
}

