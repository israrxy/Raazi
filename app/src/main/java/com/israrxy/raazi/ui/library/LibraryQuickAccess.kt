package com.israrxy.raazi.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.outlined.QueueMusic
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

// ---------------------------------------------------------------------------------------
// Account / sync card, the 2x2 quick-access tiles and the action pills.
// ---------------------------------------------------------------------------------------

@Composable
internal fun LibrarySyncCard(
    sync: LibrarySyncState,
    actions: LibraryActions,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier
            .fillMaxWidth()
            .clickable(
                enabled = !sync.syncing,
                onClick = if (sync.loggedIn) actions.syncLibrary else actions.openSettings
            )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (sync.loggedIn) Icons.Default.CloudDone else Icons.Default.CloudOff,
                    contentDescription = null,
                    tint = if (sync.loggedIn) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = sync.accountName?.takeIf { sync.loggedIn } ?: if (sync.loggedIn) {
                        "YouTube Music connected"
                    } else {
                        "Connect YouTube Music"
                    },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = when {
                        sync.syncing -> "Syncing likes and playlists\u2026"
                        sync.loggedIn -> "Likes and playlists stay in step"
                        else -> "Sign in to back up your library"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(Modifier.width(10.dp))

            if (sync.syncing) {
                LinearProgressIndicator(modifier = Modifier.width(64.dp))
            } else {
                TextButton(onClick = if (sync.loggedIn) actions.syncLibrary else actions.openSettings) {
                    Text(if (sync.loggedIn) "Sync" else "Connect")
                }
            }
        }
    }
}

@Composable
internal fun LibraryQuickAccessGrid(
    state: LibraryUiState,
    actions: LibraryActions,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            QuickAccessTile(
                modifier = Modifier.weight(1f),
                title = "Liked songs",
                count = state.likedSongs.size,
                icon = Icons.Default.Favorite,
                colors = listOf(Color(0xFFE91E63), Color(0xFF8E24AA)),
                onClick = { actions.openPlaylist("favorites") }
            )
            QuickAccessTile(
                modifier = Modifier.weight(1f),
                title = "Playlists",
                count = state.userPlaylists.size,
                icon = Icons.Outlined.QueueMusic,
                colors = listOf(Color(0xFF7C4DFF), Color(0xFF4527A0)),
                onClick = { actions.selectFilter(LibraryFilter.PLAYLISTS) }
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            QuickAccessTile(
                modifier = Modifier.weight(1f),
                title = "Downloads",
                count = state.downloadCount,
                icon = Icons.Default.Download,
                colors = listOf(Color(0xFF00897B), Color(0xFF004D40)),
                onClick = actions.openDownloads
            )
            QuickAccessTile(
                modifier = Modifier.weight(1f),
                title = "History",
                count = state.history.size,
                icon = Icons.Default.History,
                colors = listOf(Color(0xFF1E88E5), Color(0xFF1565C0)),
                onClick = { actions.openPlaylist("history") }
            )
        }
    }
}

@Composable
private fun QuickAccessTile(
    title: String,
    count: Int,
    icon: ImageVector,
    colors: List<Color>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .height(104.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.linearGradient(colors))
            .clickable(onClick = onClick)
            .padding(13.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Box(
            modifier = Modifier.size(34.dp)
                .background(Color.White.copy(alpha = 0.20f), RoundedCornerShape(11.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(19.dp))
        }
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = compactCount(count),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.85f)
            )
        }
    }
}

/** Two full-width pill buttons: the two things people actually create here. */
@Composable
internal fun LibraryActionPills(actions: LibraryActions, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedButton(
            onClick = actions.createPlaylist,
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(16.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("New playlist", maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        OutlinedButton(
            onClick = actions.openSpotifyImport,
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(16.dp)
        ) {
            Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Import", maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
