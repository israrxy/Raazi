package com.israrxy.raazi.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.israrxy.raazi.model.MusicItem
import com.israrxy.raazi.ui.theme.Zinc800
import com.israrxy.raazi.utils.ThumbnailUtils

@Composable
internal fun LyricsNowPlayingStrip(
    currentTrack: MusicItem?,
    isTrackLiked: Boolean,
    isPlaying: Boolean,
    currentPosition: Long = 0L,
    duration: Long,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onToggleFavorite: (MusicItem) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Color.White.copy(alpha = 0.06f))
                .border(1.dp, Color.White.copy(alpha = 0.07f), RoundedCornerShape(14.dp))
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = ThumbnailUtils.getHighQualityThumbnail(currentTrack?.thumbnailUrl),
                contentDescription = currentTrack?.title,
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Zinc800),
                contentScale = ContentScale.Crop
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = currentTrack?.title ?: "No Track Playing",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(1.dp))
                Text(
                    text = currentTrack?.artist ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.56f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            if (currentTrack != null) {
                IconButton(
                    onClick = { onToggleFavorite(currentTrack) },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = if (isTrackLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = "Favorite",
                        tint = if (isTrackLiked) Color.White else Color.White.copy(alpha = 0.65f),
                        modifier = Modifier.size(19.dp)
                    )
                }
            }

            Surface(
                onClick = onPlayPause,
                shape = CircleShape,
                color = Color.White.copy(alpha = 0.12f),
                modifier = Modifier.size(38.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // Compact sleek timeline under track card
        LyricsSleekTimeline(
            currentPosition = currentPosition,
            duration = duration,
            onSeek = onSeek,
            modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
        )
    }
}

@Composable
internal fun SongDetailsDialog(
    visible: Boolean,
    currentTrack: MusicItem?,
    lyricsSource: String?,
    onDismiss: () -> Unit
) {
    if (!visible || currentTrack == null) return

    val contentLabel = when (currentTrack.contentType) {
        com.israrxy.raazi.model.MusicContentType.VIDEO -> "Music video"
        com.israrxy.raazi.model.MusicContentType.ALBUM -> "Album"
        com.israrxy.raazi.model.MusicContentType.PLAYLIST -> "Playlist"
        com.israrxy.raazi.model.MusicContentType.ARTIST -> "Artist"
        else -> "Song"
    }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Song details") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(currentTrack.title)
                Text(currentTrack.artist, color = Color.White.copy(alpha = 0.72f))
                currentTrack.albumTitle?.takeIf { it.isNotBlank() }?.let { album ->
                    Text("Album · $album", color = Color.White.copy(alpha = 0.72f))
                }
                Text("Type · $contentLabel", color = Color.White.copy(alpha = 0.72f))
                Text("Lyrics · ${lyricsSource ?: "Not found"}", color = Color.White.copy(alpha = 0.72f))
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Done") }
        },
        containerColor = Color(0xFF18181B),
        titleContentColor = Color.White,
        textContentColor = Color.White
    )
}
