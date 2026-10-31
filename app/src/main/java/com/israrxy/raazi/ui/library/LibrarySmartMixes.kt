package com.israrxy.raazi.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.israrxy.raazi.model.MusicItem

@Composable
internal fun LibrarySmartMixesRow(
    mixes: List<LibrarySmartMix>,
    onPlay: (LibrarySmartMix) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        LibrarySectionHeader(title = "Made for you", count = mixes.size)
        Spacer(Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(mixes, key = { it.key }) { mix ->
                LibrarySmartMixCard(mix = mix, onPlay = { onPlay(mix) })
            }
        }
    }
}

@Composable
private fun LibrarySmartMixCard(
    mix: LibrarySmartMix,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (icon, colors) = when (mix.kind) {
        LibrarySmartMixKind.ON_REPEAT -> Icons.Default.Repeat to listOf(
            MaterialTheme.colorScheme.primary.copy(alpha = 0.92f),
            MaterialTheme.colorScheme.primary.copy(alpha = 0.48f)
        )
        LibrarySmartMixKind.REDISCOVER -> Icons.Default.AutoAwesome to listOf(
            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.90f),
            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.46f)
        )
        LibrarySmartMixKind.DISCOVER -> Icons.Default.Explore to listOf(
            MaterialTheme.colorScheme.secondary.copy(alpha = 0.90f),
            MaterialTheme.colorScheme.secondary.copy(alpha = 0.46f)
        )
    }

    Box(
        modifier = modifier
            .width(236.dp)
            .height(142.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(Brush.linearGradient(colors))
            .clickable(onClick = onPlay)
            .padding(16.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = mix.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Surface(shape = CircleShape, color = Color.White.copy(alpha = 0.18f)) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play ${mix.title}",
                        tint = Color.White,
                        modifier = Modifier.padding(6.dp).size(18.dp)
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            Text(
                text = mix.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.82f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(Modifier.height(10.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                MixArtworkStack(tracks = mix.tracks)
                Spacer(Modifier.weight(1f))
                Text(
                    text = "${mix.tracks.size} tracks",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White.copy(alpha = 0.86f)
                )
            }
        }
    }
}

@Composable
private fun MixArtworkStack(tracks: List<MusicItem>) {
    Box(modifier = Modifier.height(34.dp).width(74.dp)) {
        tracks.take(3).forEachIndexed { index, track ->
            Box(
                modifier = Modifier
                    .offset(x = (index * 20).dp)
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.22f))
                    .border(1.5.dp, Color.White.copy(alpha = 0.38f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (track.thumbnailUrl.isNotBlank()) {
                    AsyncImage(
                        model = track.thumbnailUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = Icons.Outlined.MusicNote,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}
