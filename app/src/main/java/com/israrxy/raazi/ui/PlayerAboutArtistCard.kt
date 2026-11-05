package com.israrxy.raazi.ui

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.israrxy.raazi.model.MusicContentType
import com.israrxy.raazi.model.MusicItem
import com.israrxy.raazi.model.savedCollectionId
import com.israrxy.raazi.ui.theme.Zinc800
import com.israrxy.raazi.utils.ThumbnailUtils

@Composable
internal fun PlayerAboutArtistCard(
    currentTrack: MusicItem?,
    currentArtistSongs: List<MusicItem>,
    savedCollectionIds: Set<String>,
    onNavigateToArtist: (String) -> Unit,
    onToggleSavedArtist: (artistId: String, artistName: String, thumbnailUrl: String) -> Unit
) {
    val artistId = currentTrack?.artistId?.ifBlank { null } ?: currentTrack?.artist.orEmpty()
    val artistName = currentTrack?.artist ?: "Unknown Artist"
    val normalizedArtistId = remember(artistId, artistName) { artistId.ifBlank { artistName } }
    val savedArtistId = remember(normalizedArtistId) { savedCollectionId(MusicContentType.ARTIST, normalizedArtistId) }
    val isSavedArtist = savedArtistId in savedCollectionIds
    val artistThumbnail = currentArtistSongs.firstOrNull()?.thumbnailUrl ?: currentTrack?.thumbnailUrl ?: ""

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onNavigateToArtist("artist/${Uri.encode(normalizedArtistId)}?name=${Uri.encode(artistName)}") },
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                text = "ABOUT THE ARTIST",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White.copy(alpha = 0.6f),
                letterSpacing = 1.sp
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                AsyncImage(
                    model = ThumbnailUtils.getHighQualityThumbnail(artistThumbnail),
                    contentDescription = artistName,
                    modifier = Modifier.size(70.dp).clip(CircleShape).background(Zinc800),
                    contentScale = ContentScale.Crop
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = artistName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (isSavedArtist) "Saved to Library" else "Tap to view profile",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.5f)
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .then(
                            if (isSavedArtist) {
                                Modifier.border(1.dp, Color.White.copy(alpha = 0.4f), RoundedCornerShape(999.dp))
                            } else {
                                Modifier.background(Color.White)
                            }
                        )
                        .clickable { onToggleSavedArtist(normalizedArtistId, artistName, artistThumbnail) }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isSavedArtist) "Following" else "Follow",
                        color = if (isSavedArtist) Color.White else Color.Black,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}
