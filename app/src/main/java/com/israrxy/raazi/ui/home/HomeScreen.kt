package com.israrxy.raazi.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.israrxy.raazi.utils.ThumbnailUtils
import com.israrxy.raazi.ui.theme.*
import com.israrxy.raazi.viewmodel.MusicPlayerViewModel
import com.israrxy.raazi.data.db.PlaylistEntity
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: MusicPlayerViewModel,
    onNavigateToPlayer: () -> Unit,
    onNavigateToPlaylist: (String) -> Unit,
    onNavigateToArtist: (String, String) -> Unit
) {
    val homePage by viewModel.homePage.collectAsState()
    val quickPicks by viewModel.quickPicks.collectAsState()
    val keepListening by viewModel.keepListening.collectAsState()
    val similarRecommendations by viewModel.similarRecommendations.collectAsState()
    val explorePage by viewModel.explorePage.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isLoadingMore by viewModel.isLoadingMore.collectAsState()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        if (isLoading && homePage == null && quickPicks.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(bottom = 120.dp, top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // Greeting Section
                item {
                    GreetingSection()
                }
                
                // Keep Listening Section - NOW ON TOP!
                if (keepListening.isNotEmpty()) {
                    item {
                        SectionTitle(text = "Keep Listening")
                    }
                    
                    item {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(keepListening) { track ->
                                MusicCard(
                                    musicItem = track,
                                    viewModel = viewModel,
                                    onNavigateToPlayer = onNavigateToPlayer
                                )
                            }
                        }
                    }
                }
                
                // Quick Picks Section - Compact Numbered List
                if (quickPicks.isNotEmpty()) {
                    item {
                        SectionTitle(text = "Quick Picks")
                    }
                    
                    items(quickPicks.take(10).size) { index ->
                        val track = quickPicks[index]
                        QuickPickListItem(
                            number = index + 1,
                            musicItem = track,
                            viewModel = viewModel,
                            onNavigateToPlayer = onNavigateToPlayer
                        )
                    }
                }
                
                // Similar Recommendations (3-5 personalized sections!)
                similarRecommendations.forEach { recommendation ->
                    item {
                        SectionTitle(text = recommendation.title)
                    }
                    
                    item {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(recommendation.items) { ytItem ->
                                when (ytItem) {
                                    is com.zionhuang.innertube.models.SongItem -> {
                                        YouTubeSongCard(
                                            songItem = ytItem,
                                            viewModel = viewModel,
                                            onNavigateToPlayer = onNavigateToPlayer
                                        )
                                    }
                                    is com.zionhuang.innertube.models.AlbumItem -> {
                                        YouTubeAlbumCard(
                                            albumItem = ytItem,
                                            onNavigateToPlaylist = onNavigateToPlaylist
                                        )
                                    }
                                    is com.zionhuang.innertube.models.ArtistItem -> {
                                        YouTubeArtistCard(
                                            artistItem = ytItem,
                                            onNavigateToArtist = onNavigateToArtist
                                        )
                                    }
                                    is com.zionhuang.innertube.models.PlaylistItem -> {
                                        YouTubePlaylistCard(
                                            playlistItem = ytItem,
                                            onNavigateToPlaylist = onNavigateToPlaylist
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                
                // Explore Page Sections (New Releases, etc.)
                explorePage?.let { explore ->
                    explore.newReleaseAlbums.takeIf { it.isNotEmpty() }?.let { albums ->
                        item {
                            SectionTitle(text = "New Releases")
                        }
                        
                        item {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(albums) { album ->
                                    YouTubeAlbumCard(
                                        albumItem = album,
                                        onNavigateToPlaylist = onNavigateToPlaylist
                                    )
                                }
                            }
                        }
                    }
                    
                    explore.moodAndGenres.takeIf { it.isNotEmpty() }?.let { moods ->
                        item {
                            SectionTitle(text = "Moods & Genres")
                        }
                        
                        item {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(moods) { mood ->
                                    Box(
                                        modifier = Modifier
                                            .width(160.dp)
                                            .height(160.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Zinc800)
                                            .clickable { onNavigateToPlaylist(mood.endpoint.browseId ?: "") },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = mood.title,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onBackground
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                
                // YouTube HomePage Sections (EXACTLY like OuterTune)
                homePage?.sections?.forEach { section ->
                    item {
                        SectionTitle(text = section.title)
                    }
                    
                    item {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(section.items) { ytItem ->
                                when (ytItem) {
                                    is com.zionhuang.innertube.models.SongItem -> {
                                        YouTubeSongCard(
                                            songItem = ytItem,
                                            viewModel = viewModel,
                                            onNavigateToPlayer = onNavigateToPlayer
                                        )
                                    }
                                    is com.zionhuang.innertube.models.AlbumItem -> {
                                        YouTubeAlbumCard(
                                            albumItem = ytItem,
                                            onNavigateToPlaylist = onNavigateToPlaylist
                                        )
                                    }
                                    is com.zionhuang.innertube.models.ArtistItem -> {
                                        YouTubeArtistCard(
                                            artistItem = ytItem,
                                            onNavigateToArtist = onNavigateToArtist
                                        )
                                    }
                                    is com.zionhuang.innertube.models.PlaylistItem -> {
                                        YouTubePlaylistCard(
                                            playlistItem = ytItem,
                                            onNavigateToPlaylist = onNavigateToPlaylist
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                
                // Pagination: Load More trigger
                if (homePage?.continuation != null && !isLoadingMore) {
                    item {
                        LaunchedEffect(Unit) {
                            viewModel.loadMoreSections()
                        }
                    }
                }
                
                // Loading More Indicator
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
fun GreetingSection() {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val greeting = when (hour) {
        in 0..11 -> "Good Morning"
        in 12..16 -> "Good Afternoon"
        in 17..20 -> "Good Evening"
        else -> "Good Night"
    }

    val context = androidx.compose.ui.platform.LocalContext.current
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        // Branding Header (Centered Pill)
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(androidx.compose.ui.graphics.Color.White.copy(alpha = 0.1f)) // Lighter "glass" white
                    .clickable {
                        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://israrxy.qzz.io"))
                        context.startActivity(intent)
                    }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                androidx.compose.foundation.Image(
                    painter = androidx.compose.ui.res.painterResource(id = com.israrxy.raazi.R.drawable.raazi_logo),
                    contentDescription = null,
                    modifier = Modifier
                        .size(24.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = "Raazi",
                    style = MaterialTheme.typography.titleMedium, // Slightly smaller for pill
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "by Israr Ahamed",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 2.dp) // Align visually
                )
            }
        }
        
        Spacer(modifier = Modifier.height(12.dp))

        // Greeting
        Text(
            text = greeting,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f) // Slightly softer than branding
        )
        
        Text(
            text = "Welcome Back",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        )
        
        Spacer(modifier = Modifier.height(24.dp))
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
fun QuickPickListItem(
    number: Int,
    musicItem: MusicItem,
    viewModel: MusicPlayerViewModel,
    onNavigateToPlayer: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                viewModel.playMusic(musicItem)
                onNavigateToPlayer()
            }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Number
        Text(
            text = "$number",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.width(24.dp)
        )
        
        // Small Album Art
        AsyncImage(
            model = ThumbnailUtils.getListThumbnail(musicItem.thumbnailUrl),
            contentDescription = null,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Zinc800),
            contentScale = ContentScale.Crop
        )
        
        // Track Info
        Column(
            modifier = Modifier.weight(1f)
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
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun MusicCard(
    musicItem: MusicItem,
    viewModel: MusicPlayerViewModel,
    onNavigateToPlayer: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(160.dp)
            .clickable {
                viewModel.playMusic(musicItem)
                onNavigateToPlayer()
            }
    ) {
        AsyncImage(
            model = musicItem.thumbnailUrl?.replace("w120-h120", "w544-h544")?.replace("=w60-h60", "=w544-h544"),
            contentDescription = null,
            modifier = Modifier
                .size(160.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Zinc800),
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
    Column(
        modifier = Modifier
            .width(160.dp)
            .clickable {
                val musicItem = MusicItem(
                    id = songItem.id,
                    title = songItem.title,
                    artist = songItem.artists?.joinToString(", ") { it.name } ?: "Unknown Artist",
                    duration = (songItem.duration ?: 0) * 1000L,
                    thumbnailUrl = songItem.thumbnail,
                    audioUrl = "",
                    videoUrl = songItem.id,
                    isLive = false
                )
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
                .background(Zinc800),
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
    onNavigateToPlaylist: (String) -> Unit
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
                .background(Zinc800),
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
                .background(Zinc800),
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
    onNavigateToPlaylist: (String) -> Unit
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
                .background(Zinc800),
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


