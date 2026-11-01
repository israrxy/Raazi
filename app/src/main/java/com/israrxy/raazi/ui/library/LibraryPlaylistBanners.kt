package com.israrxy.raazi.ui.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.QueueMusic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.israrxy.raazi.data.db.PlaylistWithTracks
import com.israrxy.raazi.data.playlist.isYouTubeEditablePlaylist
import com.israrxy.raazi.data.playlist.isYouTubeSyncedPlaylist
import com.israrxy.raazi.model.MusicItem

internal data class LibraryPlaylistBannerUi(
    val key: String,
    val title: String,
    val subtitle: String,
    val artworkUrls: List<String>,
    val isLiked: Boolean,
    val menu: List<LibraryMenuItem>,
    val onClick: () -> Unit,
    val onLongClick: (() -> Unit)? = null
)

internal fun likedPlaylistBanner(
    likedSongs: List<MusicItem>,
    playCounts: Map<String, Int>,
    onOpen: () -> Unit
): LibraryPlaylistBannerUi = LibraryPlaylistBannerUi(
    key = "liked_playlist",
    title = "Liked Songs",
    subtitle = if (likedSongs.isEmpty()) {
        "No liked songs yet"
    } else {
        "${counted(likedSongs.size, "song")} \u2022 Default playlist"
    },
    artworkUrls = topPlayedArtwork(
        tracks = likedSongs.map { it.id to it.thumbnailUrl },
        playCounts = playCounts
    ),
    isLiked = true,
    menu = emptyList(),
    onClick = onOpen
)

internal fun PlaylistWithTracks.toPlaylistBannerUi(
    playCounts: Map<String, Int>,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
): LibraryPlaylistBannerUi {
    val subtitle = when {
        playlist.isYouTubeSyncedPlaylist() && playlist.isYouTubeEditablePlaylist() ->
            "Synced with YouTube Music"
        playlist.isYouTubeSyncedPlaylist() -> "Saved from YouTube Music"
        else -> "${counted(tracks.size, "song")} \u2022 Local"
    }
    return LibraryPlaylistBannerUi(
        key = "pl_${playlist.id}",
        title = playlist.displayTitle,
        subtitle = subtitle,
        artworkUrls = topPlayedArtwork(
            tracks = tracks.map { it.id to it.thumbnailUrl },
            playCounts = playCounts
        ),
        isLiked = false,
        menu = listOf(
            LibraryMenuItem("Rename", onRename),
            LibraryMenuItem("Delete", onDelete, destructive = true)
        ),
        onClick = onOpen,
        onLongClick = onRename
    )
}

/** The four most-played tracks, falling back to playlist order for unplayed tracks. */
internal fun topPlayedArtwork(
    tracks: List<Pair<String, String>>,
    playCounts: Map<String, Int>
): List<String> = tracks
    .sortedByDescending { (id, _) -> playCounts[id] ?: 0 }
    .mapNotNull { (_, thumbnailUrl) -> thumbnailUrl.takeIf { it.isNotBlank() } }
    .distinct()
    .take(4)

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun LibraryPlaylistBanner(
    item: LibraryPlaylistBannerUi,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
            .combinedClickable(onClick = item.onClick, onLongClick = item.onLongClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PlaylistArtworkMosaic(
            artworkUrls = item.artworkUrls,
            isLiked = item.isLiked,
            modifier = Modifier.size(88.dp)
        )

        Spacer(Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = item.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (item.menu.isNotEmpty()) {
            OverflowMenuButton(
                items = item.menu,
                contentDescription = "Options for ${item.title}"
            )
        } else {
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PlaylistArtworkMosaic(
    artworkUrls: List<String>,
    isLiked: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(15.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        when {
            artworkUrls.isEmpty() -> Icon(
                imageVector = Icons.Outlined.QueueMusic,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(32.dp)
            )
            artworkUrls.size == 1 -> ArtworkCell(
                url = artworkUrls.first(),
                modifier = Modifier.fillMaxSize()
            )
            else -> Column(modifier = Modifier.fillMaxSize()) {
                Row(modifier = Modifier.weight(1f)) {
                    ArtworkCell(url = artworkUrls.getOrNull(0), modifier = Modifier.weight(1f).fillMaxHeight())
                    ArtworkCell(url = artworkUrls.getOrNull(1), modifier = Modifier.weight(1f).fillMaxHeight())
                }
                Row(modifier = Modifier.weight(1f)) {
                    ArtworkCell(url = artworkUrls.getOrNull(2), modifier = Modifier.weight(1f).fillMaxHeight())
                    ArtworkCell(url = artworkUrls.getOrNull(3), modifier = Modifier.weight(1f).fillMaxHeight())
                }
            }
        }

        Surface(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(4.dp)
                .size(22.dp),
            shape = CircleShape,
            color = Color.Black.copy(alpha = 0.48f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = if (isLiked) Icons.Default.Favorite else Icons.Outlined.QueueMusic,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(12.dp)
                )
            }
        }
    }
}

@Composable
private fun ArtworkCell(
    url: String?,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
    }
}
