package com.israrxy.raazi.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.israrxy.raazi.utils.ThumbnailUtils
import com.israrxy.raazi.viewmodel.HomeFeedItem
import com.israrxy.raazi.viewmodel.HomeSection
import com.israrxy.raazi.viewmodel.MusicPlayerViewModel

@Composable
fun HomeStatusHeader(
    activeCategoryTitle: String? = null,
    isRefreshing: Boolean,
    onRefresh: () -> Unit
) {
    val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
    val greeting = when (hour) {
        in 0..5 -> "Good Night"
        in 6..11 -> "Good Morning"
        in 12..16 -> "Good Afternoon"
        in 17..20 -> "Good Evening"
        else -> "Good Night"
    }
    val context = androidx.compose.ui.platform.LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Branding pill — minimal flat surface
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .clickable {
                        val intent = android.content.Intent(
                            android.content.Intent.ACTION_VIEW,
                            android.net.Uri.parse("https://israrxy.qzz.io")
                        )
                        context.startActivity(intent)
                    }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.foundation.Image(
                        painter = androidx.compose.ui.res.painterResource(id = com.israrxy.raazi.R.drawable.raazi_logo),
                        contentDescription = null,
                        modifier = Modifier
                            .size(18.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Raazi",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "@israrxy",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (isRefreshing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = activeCategoryTitle ?: greeting,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

@Composable
fun HomeCategoryFilterBar(
    chips: List<com.zionhuang.innertube.pages.HomePage.Chip>,
    selectedChip: com.zionhuang.innertube.pages.HomePage.Chip?,
    onSelectChip: (com.zionhuang.innertube.pages.HomePage.Chip?) -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(top = 2.dp, bottom = 4.dp)
    ) {
        item(key = "chip_all") {
            val isAllSelected = selectedChip == null
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (isAllSelected) {
                    MaterialTheme.colorScheme.onBackground
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                },
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onSelectChip(null) }
            ) {
                Text(
                    text = "All",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (isAllSelected) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (isAllSelected) {
                        MaterialTheme.colorScheme.background
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }
        }
        items(chips, key = { "chip_${it.title}" }) { chip ->
            val isSelected = selectedChip?.title.equals(chip.title, ignoreCase = true)
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (isSelected) {
                    MaterialTheme.colorScheme.onBackground
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                },
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onSelectChip(chip) }
            ) {
                Text(
                    text = chip.title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.background
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }
        }
    }
}

@Composable
fun HomeSectionHeader(
    section: HomeSection,
    onAction: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = section.title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (section.actionLabel != null && onAction != null) {
            TextButton(onClick = onAction) {
                Text(section.actionLabel)
            }
        }
    }
}

internal fun HomeSection.musicItems(): List<MusicItem> =
    items.mapNotNull { (it as? HomeFeedItem.Music)?.item }

internal fun HomeFeedItem.stableId(): String = when (this) {
    is HomeFeedItem.Music -> item.id
    is HomeFeedItem.PlaylistResult -> playlist.id
    is HomeFeedItem.Mood -> title
    is HomeFeedItem.YouTube -> when (item) {
        is com.zionhuang.innertube.models.SongItem -> item.id
        is com.zionhuang.innertube.models.AlbumItem -> item.id
        is com.zionhuang.innertube.models.ArtistItem -> item.id
        is com.zionhuang.innertube.models.PlaylistItem -> item.id
    }
}

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
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(section.musicItems()) { musicItem ->
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
                    viewModel.playMusic(musicItem)
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
        items(section.items) { item ->
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
        is HomeFeedItem.YouTube -> YouTubeFeedCard(
            ytItem = item.item,
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
                        viewModel.playMusic(ytItem.toHomeMusicItem())
                        onNavigateToPlayer()
                    }
                    is com.zionhuang.innertube.models.AlbumItem -> onNavigateToPlaylist(ytItem.id)
                    is com.zionhuang.innertube.models.PlaylistItem -> onNavigateToPlaylist(ytItem.id)
                    is com.zionhuang.innertube.models.ArtistItem -> onNavigateToArtist(ytItem.id, ytItem.title)
                }
            }
        )
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

@Composable
fun YouTubeFeedCard(
    ytItem: com.zionhuang.innertube.models.YTItem,
    onClick: () -> Unit
) {
    val isArtist = ytItem is com.zionhuang.innertube.models.ArtistItem
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
            .clickable(onClick = onClick)
            .padding(4.dp),
        horizontalAlignment = if (isArtist) Alignment.CenterHorizontally else Alignment.Start
    ) {
        AsyncImage(
            model = ThumbnailUtils.getHighQualityThumbnail(thumbnail ?: ""),
            contentDescription = title,
            modifier = Modifier
                .size(152.dp)
                .clip(if (isArtist) CircleShape else RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentScale = ContentScale.Crop
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
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

