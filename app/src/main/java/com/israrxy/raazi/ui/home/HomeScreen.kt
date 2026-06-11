package com.israrxy.raazi.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.israrxy.raazi.model.MusicItem
import com.israrxy.raazi.model.Playlist
import com.israrxy.raazi.utils.ThumbnailUtils
import com.israrxy.raazi.ui.theme.*
import com.israrxy.raazi.viewmodel.HomeFeedItem
import com.israrxy.raazi.viewmodel.HomeSection
import com.israrxy.raazi.viewmodel.HomeSectionType
import com.israrxy.raazi.viewmodel.MusicPlayerViewModel
import com.israrxy.raazi.data.db.PlaylistEntity
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: MusicPlayerViewModel,
    onNavigateToPlayer: () -> Unit,
    onNavigateToPlaylist: (String) -> Unit,
    onNavigateToArtist: (String, String) -> Unit,
    onNavigateToSearch: () -> Unit = {}
) {
    val homeFeedState by viewModel.homeFeedState.collectAsState()
    val homePage by viewModel.homePage.collectAsState()
    val isLoadingMore by viewModel.isLoadingMore.collectAsState()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        if (homeFeedState.isLoading) {
            androidx.compose.foundation.lazy.LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(bottom = 120.dp, top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item { com.israrxy.raazi.ui.components.ShimmerSectionHeader() }
                items(4) { com.israrxy.raazi.ui.components.ShimmerMusicItemCard() }
                item { com.israrxy.raazi.ui.components.ShimmerSectionHeader() }
                items(3) { com.israrxy.raazi.ui.components.ShimmerMusicItemCard() }
            }
        } else if (homeFeedState.sections.none { it.type != HomeSectionType.STATUS }) {
            // Empty state — new user with no history or content
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                HomeStatusHeader(
                    isRefreshing = homeFeedState.isRefreshing,
                    onRefresh = { viewModel.refreshHomeSection("home_status") }
                )
                Spacer(modifier = Modifier.height(64.dp))
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    modifier = Modifier.size(72.dp),
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                )
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = "Search for a song",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Find your favorite music to get started.\nYour history will appear here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = onNavigateToSearch,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp)
                        .height(52.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Default.Search, null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Search a Song", fontWeight = FontWeight.Bold)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(bottom = 120.dp, top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                homeFeedState.sections.forEach { section ->
                    when (section.type) {
                        HomeSectionType.STATUS -> item {
                            HomeStatusHeader(
                                isRefreshing = homeFeedState.isRefreshing,
                                onRefresh = { viewModel.refreshHomeSection(section.id) }
                            )
                        }

                        HomeSectionType.KEEP_LISTENING -> item {
                            HomeSectionHeader(section = section, onAction = { viewModel.refreshHomeSection(section.id) })
                            KeepListeningPanel(
                                items = section.musicItems(),
                                viewModel = viewModel,
                                onNavigateToPlayer = onNavigateToPlayer,
                                sectionId = section.id,
                                sourceType = section.sourceType
                            )
                        }

                        HomeSectionType.QUICK_PICKS -> item {
                            HomeSectionHeader(section = section, onAction = { viewModel.refreshHomeSection(section.id) })
                            QuickPicksPanel(
                                items = section.musicItems(),
                                viewModel = viewModel,
                                onNavigateToPlayer = onNavigateToPlayer,
                                sectionId = section.id,
                                sourceType = section.sourceType
                            )
                        }

                        HomeSectionType.FORGOTTEN_FAVORITES -> item {
                            HomeSectionHeader(section = section)
                            HomeMusicRail(
                                section = section,
                                viewModel = viewModel,
                                onNavigateToPlayer = onNavigateToPlayer
                            )
                        }

                        HomeSectionType.MOODS -> item {
                            HomeSectionHeader(section = section)
                            MoodRail(section = section, viewModel = viewModel)
                        }

                        HomeSectionType.MOOD_PLAYLISTS -> item {
                            HomeSectionHeader(section = section)
                            MoodPlaylistRail(
                                section = section,
                                onNavigateToPlaylist = onNavigateToPlaylist
                            )
                        }

                        HomeSectionType.RECOMMENDATION,
                        HomeSectionType.NEW_RELEASES,
                        HomeSectionType.YOUTUBE_RAIL -> item {
                            HomeSectionHeader(section = section)
                            HomeFeedRail(
                                section = section,
                                viewModel = viewModel,
                                onNavigateToPlayer = onNavigateToPlayer,
                                onNavigateToPlaylist = onNavigateToPlaylist,
                                onNavigateToArtist = onNavigateToArtist
                            )
                        }
                    }
                }
                
                if (homePage?.continuation != null && !isLoadingMore) {
                    item {
                        LaunchedEffect(Unit) {
                            viewModel.loadMoreSections()
                        }
                    }
                }

                if (isLoadingMore) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                        }
                    }
                }
            }
        }
    }
    
    // Add To Playlist Dialog
    var showAddToPlaylistItem by remember { mutableStateOf<MusicItem?>(null) }

    if (showAddToPlaylistItem != null) {
        com.israrxy.raazi.ui.components.AddToPlaylistDialog(
            viewModel = viewModel,
            onDismiss = { showAddToPlaylistItem = null },
            onPlaylistSelected = { playlist ->
                viewModel.addToPlaylist(playlist.id, showAddToPlaylistItem!!)
                showAddToPlaylistItem = null
            }
        )
    }
}

@Composable
fun HomeStatusHeader(
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
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // Branding pill — top left
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                .clickable {
                    val intent = android.content.Intent(
                        android.content.Intent.ACTION_VIEW,
                        android.net.Uri.parse("https://israrxy.qzz.io")
                    )
                    context.startActivity(intent)
                }
                .padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                androidx.compose.foundation.Image(
                    painter = androidx.compose.ui.res.painterResource(id = com.israrxy.raazi.R.drawable.raazi_logo),
                    contentDescription = null,
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Raazi",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "@israrxy",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Greeting
        Text(
            text = greeting,
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
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

private fun HomeSection.musicItems(): List<MusicItem> =
    items.mapNotNull { (it as? HomeFeedItem.Music)?.item }

private fun HomeFeedItem.stableId(): String = when (this) {
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
    onNavigateToPlayer: () -> Unit
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
    onNavigateToArtist: (String, String) -> Unit
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
                onNavigateToArtist = onNavigateToArtist
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
    onNavigateToArtist: (String, String) -> Unit
) {
    when (item) {
        is HomeFeedItem.Music -> MusicCard(
            musicItem = item.item,
            viewModel = viewModel,
            onNavigateToPlayer = onNavigateToPlayer,
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
    val title = when (ytItem) {
        is com.zionhuang.innertube.models.SongItem -> ytItem.title
        is com.zionhuang.innertube.models.AlbumItem -> ytItem.title
        is com.zionhuang.innertube.models.ArtistItem -> ytItem.title
        is com.zionhuang.innertube.models.PlaylistItem -> ytItem.title
    }
    val subtitle = when (ytItem) {
        is com.zionhuang.innertube.models.SongItem -> ytItem.artists?.joinToString(", ") { it.name } ?: "Song"
        is com.zionhuang.innertube.models.AlbumItem -> ytItem.artists?.joinToString(", ") { it.name } ?: "Album"
        is com.zionhuang.innertube.models.ArtistItem -> "Artist"
        is com.zionhuang.innertube.models.PlaylistItem -> "Playlist"
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
            .clickable(onClick = onClick)
    ) {
        AsyncImage(
            model = thumbnail?.replace("w120-h120", "w544-h544")?.replace("=w60-h60", "=w544-h544"),
            contentDescription = null,
            modifier = Modifier
                .size(160.dp)
                .clip(RoundedCornerShape(8.dp))
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
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun com.zionhuang.innertube.models.SongItem.toHomeMusicItem(): MusicItem {
    return MusicItem(
        id = id,
        title = title,
        artist = artists?.joinToString(", ") { it.name } ?: "Unknown Artist",
        duration = (duration ?: 0) * 1000L,
        thumbnailUrl = thumbnail,
        audioUrl = "",
        videoUrl = id,
        isLive = false
    )
}

@Composable
fun MoodRail(
    section: HomeSection,
    viewModel: MusicPlayerViewModel
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(section.items.filterIsInstance<HomeFeedItem.Mood>()) { mood ->
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
    section: HomeSection,
    onNavigateToPlaylist: (String) -> Unit
) {
    when {
        section.isLoading -> Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
        }
        section.items.isEmpty() -> Text(
            text = "No playlists found",
            modifier = Modifier.padding(horizontal = 16.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        else -> LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(section.items.filterIsInstance<HomeFeedItem.PlaylistResult>()) { item ->
                MoodPlaylistCard(
                    playlist = item.playlist,
                    onNavigateToPlaylist = onNavigateToPlaylist
                )
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

@Composable
fun KeepListeningPanel(
    items: List<MusicItem>,
    viewModel: MusicPlayerViewModel,
    onNavigateToPlayer: () -> Unit,
    sectionId: String = "keep_listening",
    sourceType: String = com.israrxy.raazi.data.db.HomeInteractionEntity.SOURCE_LOCAL
) {
    if (items.isEmpty()) return

    Column(
        modifier = Modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        KeepListeningResumeItem(
            musicItem = items.first(),
            onClick = {
                viewModel.recordHomeInteraction(
                    itemId = items.first().id,
                    sectionId = sectionId,
                    action = com.israrxy.raazi.data.db.HomeInteractionEntity.ACTION_PLAY,
                    sourceType = sourceType
                )
                viewModel.playMusic(items.first())
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
                        onClick = {
                            viewModel.recordHomeInteraction(
                                itemId = track.id,
                                sectionId = sectionId,
                                action = com.israrxy.raazi.data.db.HomeInteractionEntity.ACTION_PLAY,
                                sourceType = sourceType
                            )
                            viewModel.playMusic(track)
                            onNavigateToPlayer()
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun KeepListeningResumeItem(
    musicItem: MusicItem,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(104.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AsyncImage(
                model = ThumbnailUtils.getListThumbnail(musicItem.thumbnailUrl),
                contentDescription = null,
                modifier = Modifier
                    .size(84.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentScale = ContentScale.Crop
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Resume",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1
                )
                Text(
                    text = musicItem.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
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

@Composable
fun KeepListeningRecentItem(
    musicItem: MusicItem,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .width(244.dp)
            .height(70.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            AsyncImage(
                model = ThumbnailUtils.getListThumbnail(musicItem.thumbnailUrl),
                contentDescription = null,
                modifier = Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentScale = ContentScale.Crop
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = musicItem.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
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

            Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = "Play",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
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
    sourceType: String = com.israrxy.raazi.data.db.HomeInteractionEntity.SOURCE_LOCAL
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
            onClick = {
                viewModel.recordHomeInteraction(
                    itemId = featuredTrack.id,
                    sectionId = sectionId,
                    action = com.israrxy.raazi.data.db.HomeInteractionEntity.ACTION_PLAY,
                    sourceType = sourceType
                )
                viewModel.playMusic(featuredTrack)
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
                        onClick = {
                            viewModel.recordHomeInteraction(
                                itemId = track.id,
                                sectionId = sectionId,
                                action = com.israrxy.raazi.data.db.HomeInteractionEntity.ACTION_PLAY,
                                sourceType = sourceType
                            )
                            viewModel.playMusic(track)
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

@Composable
fun QuickPickFeaturedItem(
    musicItem: MusicItem,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(112.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.62f),
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AsyncImage(
                model = ThumbnailUtils.getListThumbnail(musicItem.thumbnailUrl),
                contentDescription = null,
                modifier = Modifier
                    .size(92.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentScale = ContentScale.Crop
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = musicItem.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
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

@Composable
fun QuickPickTile(
    number: Int,
    musicItem: MusicItem,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .height(72.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AsyncImage(
                model = ThumbnailUtils.getListThumbnail(musicItem.thumbnailUrl),
                contentDescription = null,
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentScale = ContentScale.Crop
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = musicItem.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
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

@Composable
fun MusicCard(
    musicItem: MusicItem,
    viewModel: MusicPlayerViewModel,
    onNavigateToPlayer: () -> Unit,
    onClick: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .width(160.dp)
            .clickable {
                if (onClick != null) {
                    onClick()
                } else {
                    viewModel.playMusic(musicItem)
                }
                onNavigateToPlayer()
            }
    ) {
        AsyncImage(
            model = musicItem.thumbnailUrl?.replace("w120-h120", "w544-h544")?.replace("=w60-h60", "=w544-h544"),
            contentDescription = null,
            modifier = Modifier
                .size(160.dp)
                .clip(RoundedCornerShape(8.dp))
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentScale = ContentScale.Crop
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = musicItem.title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
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
