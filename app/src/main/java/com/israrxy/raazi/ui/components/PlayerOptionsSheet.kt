package com.israrxy.raazi.ui.components

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.israrxy.raazi.data.db.DownloadEntity
import com.israrxy.raazi.model.MusicItem
import com.israrxy.raazi.model.PlaybackVideoQuality
import com.israrxy.raazi.ui.theme.*
import com.israrxy.raazi.utils.ShareUtils
import com.israrxy.raazi.viewmodel.MusicPlayerViewModel
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable

/**
 * YouTube Music style three-dot action sheet.
 * Top: quick-action grid (Play next / Save to playlist / Share).
 * Bottom: scrollable list of contextual actions.
 */
@Composable
fun PlayerOptionsSheet(
    track: MusicItem?,
    viewModel: MusicPlayerViewModel,
    onDismiss: () -> Unit,
    onSaveToPlaylist: () -> Unit,
    onViewCredits: () -> Unit,
    onSetRingtone: () -> Unit = {},
    currentVideoQuality: PlaybackVideoQuality = PlaybackVideoQuality.AUTO,
    onSelectVideoQuality: (PlaybackVideoQuality) -> Unit = {}
) {
    val context = LocalContext.current
    if (track == null) {
        onDismiss()
        return
    }

    val favoriteTracks by viewModel.favoriteTracks.collectAsStateWithLifecycle()
    val allDownloads by viewModel.dbAllDownloads.collectAsStateWithLifecycle()
    val isFavorite = favoriteTracks.any { it.id == track.id }
    val downloadState = allDownloads.find { it.trackId == track.id }
    val isDownloaded = downloadState?.status == DownloadEntity.STATUS_COMPLETED

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 28.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // --- Quick-action grid ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            QuickAction(
                modifier = Modifier.weight(1f),
                icon = Icons.Outlined.PlaylistPlay,
                label = "Play next",
                onClick = {
                    viewModel.playNext(listOf(track))
                    Toast.makeText(context, "Playing next", Toast.LENGTH_SHORT).show()
                    onDismiss()
                }
            )
            QuickAction(
                modifier = Modifier.weight(1f),
                icon = Icons.Outlined.PlaylistAdd,
                label = "Save to playlist",
                onClick = {
                    onSaveToPlaylist()
                }
            )
            QuickAction(
                modifier = Modifier.weight(1f),
                icon = Icons.Outlined.Share,
                label = "Share",
                onClick = {
                    ShareUtils.shareTrack(context, track)
                    onDismiss()
                }
            )
        }

        Divider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(vertical = 8.dp))

        // --- Video quality ---
        var showQualityOptions by remember { mutableStateOf(false) }
        SheetOption(
            icon = Icons.Default.HighQuality,
            title = "Video quality",
            subtitle = "Current: ${currentVideoQuality.label}",
            onClick = { showQualityOptions = !showQualityOptions }
        )
        AnimatedVisibility(visible = showQualityOptions) {
            Column {
                PlaybackVideoQuality.values().forEach { quality ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSelectVideoQuality(quality)
                                showQualityOptions = false
                            }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Spacer(Modifier.width(40.dp))
                        Text(
                            text = quality.label,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (quality == currentVideoQuality) FontWeight.Bold else FontWeight.Normal,
                            color = if (quality == currentVideoQuality) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                        if (quality == currentVideoQuality) {
                            Spacer(Modifier.width(8.dp))
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }

        Divider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(vertical = 8.dp))

        // --- Scrollable list options ---
        SheetOption(
            icon = Icons.Outlined.Radio,
            title = "Start mix",
            subtitle = "Launch a radio based on this song",
            onClick = {
                viewModel.startRadio(track)
                Toast.makeText(context, "Starting mix", Toast.LENGTH_SHORT).show()
                onDismiss()
            }
        )
        SheetOption(
            icon = Icons.Outlined.QueueMusic,
            title = "Add to queue",
            subtitle = "Append to your listening list",
            onClick = {
                viewModel.addToQueue(track)
                Toast.makeText(context, "Added to queue", Toast.LENGTH_SHORT).show()
                onDismiss()
            }
        )
        SheetOption(
            icon = if (isDownloaded) Icons.Filled.CheckCircle else Icons.Outlined.Download,
            title = if (isDownloaded) "Saved offline" else "Download",
            subtitle = if (isDownloaded) "Tap to download again" else "Save for offline listening",
            onClick = {
                viewModel.downloadTrack(track)
                onDismiss()
            }
        )
        SheetOption(
            icon = if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
            title = if (isFavorite) "Saved to library" else "Save to library",
            subtitle = "Add to your liked songs",
            onClick = {
                viewModel.toggleFavorite(track)
                onDismiss()
            }
        )
        SheetOption(
            icon = Icons.Outlined.Info,
            title = "View song credits",
            subtitle = "Artist, writers and more",
            onClick = {
                onViewCredits()
                onDismiss()
            }
        )
        SheetOption(
            icon = Icons.Default.PhoneAndroid,
            title = "Set as Ringtone",
            subtitle = "Trim and set as ringtone, alarm or notification",
            onClick = {
                onSetRingtone()
                onDismiss()
            }
        )
        SheetOption(
            icon = Icons.Outlined.ClearAll,
            title = "Dismiss queue",
            subtitle = "Clear everything lined up to play",
            onClick = {
                viewModel.dismissQueue()
                Toast.makeText(context, "Queue cleared", Toast.LENGTH_SHORT).show()
                onDismiss()
            }
        )
    }
}

@Composable
private fun QuickAction(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .size(48.dp)
                    .padding(12.dp)
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun SheetOption(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
