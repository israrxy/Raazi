package com.israrxy.raazi.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.israrxy.raazi.model.MusicItem
import com.israrxy.raazi.ui.theme.*
import com.israrxy.raazi.utils.ThumbnailUtils
import com.israrxy.raazi.viewmodel.MusicPlayerViewModel
import com.israrxy.raazi.data.db.PlaylistEntity
import kotlinx.coroutines.launch

@Composable
fun ArtistScreen(
    artistId: String,
    artistName: String?,
    viewModel: MusicPlayerViewModel,
    onNavigateBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var songs by remember { mutableStateOf<List<MusicItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    // Add To Playlist Dialog State
    var showAddToPlaylistItem by remember { mutableStateOf<MusicItem?>(null) }
    
    LaunchedEffect(artistId) {
        scope.launch {
            try {
                // Search for artist's songs
                val query = artistName ?: artistId
                val result = com.zionhuang.innertube.YouTube.search(
                    query, 
                    com.zionhuang.innertube.YouTube.SearchFilter.FILTER_SONG
                ).getOrNull()
                
                songs = result?.items?.mapNotNull { item ->
                    when (item) {
                        is com.zionhuang.innertube.models.SongItem -> MusicItem(
                            id = item.id ?: return@mapNotNull null,
                            title = item.title ?: "Unknown",
                            artist = item.artists?.joinToString(", ") { it.name ?: "" } ?: "Unknown",
                            duration = (item.duration?.toLong() ?: 0) * 1000,
                            thumbnailUrl = item.thumbnail ?: "",
                            audioUrl = "",
                            videoUrl = item.id ?: return@mapNotNull null,
                            artistId = item.artists?.firstOrNull()?.id
                        )
                        else -> null
                    }
                } ?: emptyList()
            } finally {
                isLoading = false
            }
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onNavigateBack) {
                Icon(Icons.Default.ArrowBack, null, tint = MaterialTheme.colorScheme.onBackground)
            }
            Text(
                text = artistName ?: "Artist",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
        
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 100.dp)
        ) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Note: artistDetails is not available in this scope. Assuming a placeholder or a different source for thumbnail.
                    // For now, using a placeholder or a default image.
                    // If artistDetails is meant to be fetched, it needs to be added to the ViewModel and observed.
                    AsyncImage(
                        model = ThumbnailUtils.getHighQualityThumbnail(""), // Placeholder for artist thumbnail
                        contentDescription = "Artist",
                        modifier = Modifier
                            .size(200.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = artistName ?: "Unknown Artist",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            }

            if (isLoading) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                }
            } else {
                items(songs) { song ->
                    val musicItem = MusicItem(
                        id = song.id,
                        title = song.title,
                        artist = song.artist,
                        duration = song.duration,
                        thumbnailUrl = song.thumbnailUrl,
                        audioUrl = song.audioUrl,
                        videoUrl = song.videoUrl,
                        artistId = song.artistId
                    )

                    com.israrxy.raazi.ui.components.SongListItem(
                        song = musicItem,
                        onClick = {
                            viewModel.playMusic(musicItem)
                        },
                        onAddToPlaylist = { showAddToPlaylistItem = musicItem },
                        onGoToArtist = { /* Already on artist screen, maybe do nothing or navigate */ },
                        onDownload = { /* TODO */ },
                        onLike = { /* TODO */ }
                    )
                }
            }
        }
    }

    // Add To Playlist Dialog
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
