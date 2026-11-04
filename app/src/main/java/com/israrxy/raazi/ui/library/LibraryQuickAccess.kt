package com.israrxy.raazi.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

// ---------------------------------------------------------------------------------------
// Account / sync card.
// ---------------------------------------------------------------------------------------

@Composable
internal fun LibrarySyncCard(
    sync: LibrarySyncState,
    actions: LibraryActions,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier
            .fillMaxWidth()
            .clickable(
                enabled = !sync.syncing,
                onClick = actions.syncLibrary
            )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 14.dp, top = 14.dp, bottom = 14.dp),
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
                val title = when (sync.syncType) {
                    LibrarySyncType.DUAL -> sync.accountName ?: "Google Drive & YouTube"
                    LibrarySyncType.GOOGLE_DRIVE -> sync.accountName ?: "Google Drive Backup"
                    LibrarySyncType.YOUTUBE_MUSIC -> sync.accountName ?: "YouTube Music connected"
                    LibrarySyncType.NONE -> "Back up your library"
                }
                val subtitle = when {
                    sync.syncing -> "Syncing likes and playlists\u2026"
                    sync.syncType == LibrarySyncType.DUAL -> "Google Drive & YouTube Music active"
                    sync.syncType == LibrarySyncType.GOOGLE_DRIVE -> "Private Google Drive backup active"
                    sync.syncType == LibrarySyncType.YOUTUBE_MUSIC -> "Likes and playlists stay in step"
                    else -> "Sign in to back up playlists & likes"
                }

                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!sync.loggedIn && actions.dismissSyncCardPermanently != null) {
                    Text(
                        text = "Don't show again",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .clickable { actions.dismissSyncCardPermanently.invoke() }
                    )
                }
            }

            if (sync.syncing) {
                LinearProgressIndicator(modifier = Modifier.width(64.dp))
            } else {
                TextButton(onClick = actions.syncLibrary) {
                    Text(if (sync.loggedIn) "Sync" else "Connect")
                }
            }

            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Dismiss",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
