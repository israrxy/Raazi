package com.israrxy.raazi.ui.playlist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.zionhuang.innertube.YouTube
import com.zionhuang.innertube.models.PlaylistItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal data class RelatedPlaylistItemUi(
    val id: String,
    val title: String,
    val subtitle: String,
    val thumbnailUrl: String
)

@Composable
internal fun PlaylistRelatedSection(
    playlist: Playlist?,
    tracks: List<MusicItem>,
    onNavigateToPlaylist: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (playlist == null) return

    var relatedPlaylists by remember(playlist.id) { mutableStateOf<List<RelatedPlaylistItemUi>>(emptyList()) }
    var isLoading by remember(playlist.id) { mutableStateOf(true) }

    LaunchedEffect(playlist.id, playlist.title) {
        isLoading = true
        val targetQuery = buildRelatedPlaylistQuery(playlist.title, tracks)
        val fetched = withContext(Dispatchers.IO) {
            runCatching {
                val featured = YouTube.search(
                    targetQuery,
                    filter = YouTube.SearchFilter.FILTER_FEATURED_PLAYLIST
                ).getOrNull()?.items
                val community = YouTube.search(
                    targetQuery,
                    filter = YouTube.SearchFilter.FILTER_COMMUNITY_PLAYLIST
                ).getOrNull()?.items
                val combined = (featured.orEmpty() + community.orEmpty())
                    .filterIsInstance<PlaylistItem>()
                    .filter {
                        val cleanId = it.id.trim()
                        cleanId.isNotBlank() &&
                            cleanId != playlist.id &&
                            cleanId != playlist.id.removePrefix("VL")
                    }
                    .distinctBy { it.id }
                    .take(10)
                combined.map { item ->
                    RelatedPlaylistItemUi(
                        id = item.id,
                        title = item.title,
                        subtitle = item.author?.name ?: (item.songCountText ?: "Playlist"),
                        thumbnailUrl = item.thumbnail.orEmpty()
                    )
                }
            }.getOrDefault(emptyList())
        }
        relatedPlaylists = fetched
        isLoading = false
    }

    if (!isLoading && relatedPlaylists.isEmpty()) return

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 24.dp, bottom = 16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
        ) {
            Text(
                text = "Related Playlists",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "Based on ${playlist.title}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (isLoading) {
                items(4) {
                    RelatedPlaylistPlaceholder()
                }
            } else {
                items(relatedPlaylists, key = { it.id }) { item ->
                    RelatedPlaylistCard(
                        item = item,
                        onClick = { onNavigateToPlaylist(item.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun RelatedPlaylistCard(
    item: RelatedPlaylistItemUi,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(136.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(bottom = 6.dp)
    ) {
        Surface(
            modifier = Modifier
                .size(136.dp)
                .clip(RoundedCornerShape(14.dp)),
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            if (item.thumbnailUrl.isNotBlank()) {
                AsyncImage(
                    model = item.thumbnailUrl,
                    contentDescription = item.title,
                    modifier = Modifier.size(136.dp),
                    contentScale = ContentScale.Crop
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = item.title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        Text(
            text = item.subtitle,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun RelatedPlaylistPlaceholder() {
    Column(modifier = Modifier.width(136.dp)) {
        Surface(
            modifier = Modifier
                .size(136.dp)
                .clip(RoundedCornerShape(14.dp)),
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ) {}
        Spacer(modifier = Modifier.height(8.dp))
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .height(14.dp)
                .clip(RoundedCornerShape(4.dp)),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        ) {}
    }
}

private fun buildRelatedPlaylistQuery(title: String, tracks: List<MusicItem>): String {
    val clean = title.trim()
    val isGeneric = clean.isBlank() ||
        clean.equals("Liked Songs", ignoreCase = true) ||
        clean.equals("Favorites", ignoreCase = true) ||
        clean.equals("Downloads", ignoreCase = true) ||
        clean.equals("History", ignoreCase = true)

    if (!isGeneric) {
        return "$clean playlist"
    }

    val topArtist = tracks
        .mapNotNull { it.artist.split(",").firstOrNull()?.trim() }
        .firstOrNull { it.isNotBlank() && !it.equals("Unknown Artist", ignoreCase = true) }

    return if (!topArtist.isNullOrBlank()) {
        "$topArtist mix"
    } else {
        "Top Hits"
    }
}
