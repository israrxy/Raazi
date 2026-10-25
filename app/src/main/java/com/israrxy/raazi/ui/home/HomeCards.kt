package com.israrxy.raazi.ui.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.israrxy.raazi.model.MusicItem
import com.israrxy.raazi.model.Playlist
import com.israrxy.raazi.utils.ThumbnailUtils
import com.israrxy.raazi.viewmodel.MusicPlayerViewModel

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MusicCard(
    musicItem: MusicItem,
    viewModel: MusicPlayerViewModel,
    onNavigateToPlayer: () -> Unit,
    onClick: (() -> Unit)? = null,
    isSelected: Boolean = false,
    isSelectionMode: Boolean = false,
    onToggleSelection: (MusicItem) -> Unit = {},
    onEnterSelection: (MusicItem) -> Unit = {}
) {
    Column(
        modifier = Modifier
            .width(160.dp)
            .combinedClickable(
                onClick = {
                    if (isSelectionMode) {
                        onToggleSelection(musicItem)
                    } else {
                        if (onClick != null) {
                            onClick()
                        } else {
                            viewModel.playMusic(musicItem)
                        }
                        onNavigateToPlayer()
                    }
                },
                onLongClick = { onEnterSelection(musicItem) }
            )
    ) {
        Box {
            AsyncImage(
                model = musicItem.thumbnailUrl?.replace("w120-h120", "w544-h544")?.replace("=w60-h60", "=w544-h544"),
                contentDescription = null,
                modifier = Modifier
                    .size(160.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentScale = ContentScale.Crop
            )
            if (isSelectionMode) {
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
            text = musicItem.artist,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun YouTubeSongCard(
    songItem: com.zionhuang.innertube.models.SongItem,
    viewModel: MusicPlayerViewModel,
    onNavigateToPlayer: () -> Unit
) {
    val musicItem = remember(songItem) {
        MusicItem(
            id = songItem.id,
            title = songItem.title,
            artist = songItem.artists?.joinToString(", ") { it.name } ?: "Unknown Artist",
            duration = (songItem.duration ?: 0) * 1000L,
            thumbnailUrl = songItem.thumbnail,
            audioUrl = "",
            videoUrl = songItem.id,
            isLive = false
        )
    }
    Column(
        modifier = Modifier
            .width(160.dp)
            .clickable {
                viewModel.playMusic(musicItem)
                onNavigateToPlayer()
            }
    ) {
        AsyncImage(
            model = songItem.thumbnail?.replace("w120-h120", "w544-h544")?.replace("=w60-h60", "=w544-h544"),
            contentDescription = null,
            modifier = Modifier
                .size(160.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentScale = ContentScale.Crop
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = songItem.title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = songItem.artists?.joinToString(", ") { it.name } ?: "Unknown Artist",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun YouTubeAlbumCard(
    albumItem: com.zionhuang.innertube.models.AlbumItem,
    viewModel: MusicPlayerViewModel,
    onNavigateToPlaylist: (String) -> Unit,
    onNavigateToPlayer: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(160.dp)
            .clickable {
                onNavigateToPlaylist(albumItem.id)
            }
    ) {
        AsyncImage(
            model = albumItem.thumbnail?.replace("w120-h120", "w544-h544")?.replace("=w60-h60", "=w544-h544"),
            contentDescription = null,
            modifier = Modifier
                .size(160.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentScale = ContentScale.Crop
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = albumItem.title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = albumItem.artists?.joinToString(", ") { it.name } ?: "Unknown Artist",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun YouTubeArtistCard(
    artistItem: com.zionhuang.innertube.models.ArtistItem,
    onNavigateToArtist: (String, String) -> Unit
) {
    Column(
        modifier = Modifier
            .width(160.dp)
            .clickable {
                onNavigateToArtist(artistItem.id, artistItem.title)
            }
    ) {
        AsyncImage(
            model = artistItem.thumbnail?.replace("w120-h120", "w544-h544")?.replace("=w60-h60", "=w544-h544"),
            contentDescription = null,
            modifier = Modifier
                .size(160.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentScale = ContentScale.Crop
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = artistItem.title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = "Artist",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun YouTubePlaylistCard(
    playlistItem: com.zionhuang.innertube.models.PlaylistItem,
    viewModel: MusicPlayerViewModel,
    onNavigateToPlaylist: (String) -> Unit,
    onNavigateToPlayer: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(160.dp)
            .clickable {
                onNavigateToPlaylist(playlistItem.id)
            }
    ) {
        AsyncImage(
            model = playlistItem.thumbnail?.replace("w120-h120", "w544-h544")?.replace("=w60-h60", "=w544-h544"),
            contentDescription = null,
            modifier = Modifier
                .size(160.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentScale = ContentScale.Crop
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = playlistItem.title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = "Playlist",
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
            model = playlist.thumbnailUrl,
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
    androidx.compose.foundation.lazy.LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(section.items.filterIsInstance<com.israrxy.raazi.viewmodel.HomeFeedItem.Mood>().size) { index ->
            val mood = section.items.filterIsInstance<com.israrxy.raazi.viewmodel.HomeFeedItem.Mood>()[index]
            Box(
                modifier = Modifier
                    .width(150.dp)
                    .height(96.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
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
                    color = MaterialTheme.colorScheme.onSurface,
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun QuickPickTile(
    number: Int,
    musicItem: MusicItem,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    isSelected: Boolean = false,
    isSelectionMode: Boolean = false,
    onToggleSelection: (MusicItem) -> Unit = {},
    onEnterSelection: (MusicItem) -> Unit = {}
) {
    Surface(
        modifier = modifier
            .height(72.dp)
            .clip(RoundedCornerShape(8.dp))
            .combinedClickable(
                onClick = { if (isSelectionMode) onToggleSelection(musicItem) else onClick() },
                onLongClick = { onEnterSelection(musicItem) }
            ),
        shape = RoundedCornerShape(8.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
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
                    modifier = Modifier.size(52.dp).clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
                    contentScale = ContentScale.Crop
                )
            }

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
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
                Text(
                    text = number.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.58f),
                    modifier = Modifier.width(18.dp)
                )
            }
        }
    }
}

