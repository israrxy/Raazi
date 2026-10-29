package com.israrxy.raazi.ui

import android.graphics.drawable.BitmapDrawable
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.palette.graphics.Palette
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.israrxy.raazi.model.MusicContentType
import com.israrxy.raazi.model.MusicItem
import com.israrxy.raazi.utils.ThumbnailUtils

internal fun formatSearchDuration(durationMs: Long): String? {
    val totalSeconds = (durationMs / 1000L).toInt()
    if (totalSeconds <= 0) return null
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%d:%02d", minutes, seconds)
    }
}

internal fun formatSearchItemSubtitle(
    item: MusicItem,
    includeDuration: Boolean = false
): String {
    val typeLabel = when (item.contentType) {
        MusicContentType.VIDEO -> "Video"
        MusicContentType.SONG -> "Song"
        MusicContentType.ARTIST -> "Artist"
        MusicContentType.ALBUM -> "Album"
        MusicContentType.PLAYLIST -> "Playlist"
        MusicContentType.UNKNOWN -> if (item.isPlaylist) "Playlist" else "Song"
    }
    val parts = mutableListOf<String>()
    parts.add(typeLabel)
    if (item.artist.isNotBlank() && !item.artist.equals(typeLabel, ignoreCase = true)) {
        parts.add(item.artist)
    }
    if (!item.viewsText.isNullOrBlank()) {
        parts.add(item.viewsText)
    }
    if (includeDuration) {
        formatSearchDuration(item.duration)?.let { parts.add(it) }
    }
    return parts.joinToString(" • ")
}

@Composable
internal fun TopResultHeroCard(
    topResult: MusicItem,
    onClick: (MusicItem) -> Unit,
    onAddToPlaylist: (MusicItem) -> Unit,
    onAddToQueue: (MusicItem) -> Unit,
    onDownload: (MusicItem) -> Unit,
    onShare: (MusicItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showMenu by remember { mutableStateOf(false) }
    val defaultAccent = MaterialTheme.colorScheme.surfaceVariant
    var extractedColor by remember(topResult.id, topResult.thumbnailUrl) {
        mutableStateOf<Color?>(null)
    }
    val animatedCoverColor by animateColorAsState(
        targetValue = extractedColor ?: defaultAccent,
        animationSpec = tween(durationMillis = 450),
        label = "top_result_cover_color"
    )

    val isVideo = topResult.contentType == MusicContentType.VIDEO
    val isArtist = topResult.isArtistResult()
    val isPlayable = topResult.isPlayableSearchItem()
    val thumbnailUrl = remember(topResult.thumbnailUrl) {
        ThumbnailUtils.getHighQualityThumbnail(topResult.thumbnailUrl)
    }
    val imageRequest = remember(context, thumbnailUrl) {
        ImageRequest.Builder(context)
            .data(thumbnailUrl)
            .allowHardware(false)
            .crossfade(true)
            .build()
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag("top_result_hero_card"),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        tonalElevation = 3.dp,
        border = BorderStroke(
            width = 1.dp,
            color = if (extractedColor != null) {
                animatedCoverColor.copy(alpha = 0.42f)
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            animatedCoverColor.copy(alpha = if (extractedColor != null) 0.48f else 0.25f),
                            animatedCoverColor.copy(alpha = if (extractedColor != null) 0.22f else 0.12f),
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.40f)
                        )
                    )
                )
                .clickable { onClick(topResult) }
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = if (isVideo) {
                        Modifier
                            .width(116.dp)
                            .height(66.dp)
                    } else {
                        Modifier.size(84.dp)
                    },
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = imageRequest,
                        contentDescription = topResult.title,
                        onSuccess = { state ->
                            val bitmap = (state.result.drawable as? BitmapDrawable)?.bitmap
                            if (bitmap != null && !bitmap.isRecycled) {
                                Palette.from(bitmap).generate { palette ->
                                    val rgb = palette?.vibrantSwatch?.rgb
                                        ?: palette?.mutedSwatch?.rgb
                                        ?: palette?.darkVibrantSwatch?.rgb
                                        ?: palette?.dominantSwatch?.rgb
                                        ?: palette?.darkMutedSwatch?.rgb
                                    if (rgb != null) {
                                        extractedColor = Color(rgb)
                                    }
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(if (isArtist) CircleShape else RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surface),
                        contentScale = ContentScale.Crop
                    )
                    if (isPlayable) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.55f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = topResult.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = formatSearchItemSubtitle(topResult, includeDuration = true),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Box {
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier.testTag("top_result_more_options")
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "More options",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        if (isPlayable) {
                            DropdownMenuItem(
                                text = { Text("Add to Queue") },
                                onClick = {
                                    showMenu = false
                                    onAddToQueue(topResult)
                                },
                                leadingIcon = {
                                    Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = null)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Save to playlist") },
                                onClick = {
                                    showMenu = false
                                    onAddToPlaylist(topResult)
                                },
                                leadingIcon = {
                                    Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = null)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Download") },
                                onClick = {
                                    showMenu = false
                                    onDownload(topResult)
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Download, contentDescription = null)
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Share") },
                            onClick = {
                                showMenu = false
                                onShare(topResult)
                            },
                            leadingIcon = {
                                Icon(Icons.Default.Share, contentDescription = null)
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = { onClick(topResult) },
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.onSurface,
                        contentColor = MaterialTheme.colorScheme.surface
                    ),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
                    modifier = Modifier.testTag("top_result_play_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isPlayable) "Play" else "Open",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (isPlayable) {
                    OutlinedButton(
                        onClick = { onAddToPlaylist(topResult) },
                        shape = RoundedCornerShape(50),
                        border = BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
                        ),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
                        modifier = Modifier.testTag("top_result_save_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.PlaylistAdd,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Save",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}
