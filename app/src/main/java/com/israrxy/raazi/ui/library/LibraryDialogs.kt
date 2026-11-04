package com.israrxy.raazi.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.israrxy.raazi.data.db.PlaylistEntity
import com.israrxy.raazi.data.db.PlaylistWithTracks
import com.israrxy.raazi.model.MusicItem
import com.israrxy.raazi.ui.components.AddToPlaylistDialog
import com.israrxy.raazi.viewmodel.MusicPlayerViewModel

// ---------------------------------------------------------------------------------------
// Create / rename / delete dialogs.
// ---------------------------------------------------------------------------------------

@Composable
internal fun CreatePlaylistDialog(
    name: String,
    onNameChange: (String) -> Unit,
    createSynced: Boolean,
    onCreateSyncedChange: (Boolean) -> Unit,
    canCreateSynced: Boolean,
    onDismiss: () -> Unit,
    onCreate: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New playlist") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                TextField(
                    value = name,
                    onValueChange = onNameChange,
                    placeholder = { Text("Playlist name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (canCreateSynced) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Create in YouTube Music", fontWeight = FontWeight.SemiBold)
                            Text(
                                text = "Available in your account and in Raazi.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(checked = createSynced, onCheckedChange = onCreateSyncedChange)
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onCreate, enabled = name.isNotBlank()) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
internal fun RenamePlaylistDialog(
    name: String,
    onNameChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onRename: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename playlist") },
        text = {
            TextField(
                value = name,
                onValueChange = onNameChange,
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(onClick = onRename, enabled = name.isNotBlank()) {
                Text("Rename")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
internal fun DeletePlaylistDialog(
    playlistTitle: String,
    onDismiss: () -> Unit,
    onDelete: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete playlist") },
        text = {
            Text("Delete \u201C$playlistTitle\u201D? This can't be undone.")
        },
        confirmButton = {
            Button(onClick = onDelete) { Text("Delete") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
internal fun LibraryScreenDialogs(
    viewModel: MusicPlayerViewModel,
    showAddToPlaylistDialog: Boolean,
    tracksForPlaylistAdd: List<MusicItem>,
    onDismissAddToPlaylist: () -> Unit,
    onPlaylistSelectedForAdd: (PlaylistWithTracks) -> Unit,
    showCreateDialog: Boolean,
    playlistName: String,
    onPlaylistNameChange: (String) -> Unit,
    createSynced: Boolean,
    onCreateSyncedChange: (Boolean) -> Unit,
    canCreateSynced: Boolean,
    onDismissCreate: () -> Unit,
    onCreatePlaylist: () -> Unit,
    showRenameDialog: Boolean,
    playlistToRename: PlaylistEntity?,
    renamePlaylistName: String,
    onRenamePlaylistNameChange: (String) -> Unit,
    onDismissRename: () -> Unit,
    onRenamePlaylist: () -> Unit,
    showDeleteDialog: Boolean,
    playlistToDelete: PlaylistEntity?,
    onDismissDelete: () -> Unit,
    onDeletePlaylist: () -> Unit
) {
    if (showAddToPlaylistDialog && tracksForPlaylistAdd.isNotEmpty()) {
        AddToPlaylistDialog(
            viewModel = viewModel,
            tracksToAdd = tracksForPlaylistAdd,
            onDismiss = onDismissAddToPlaylist,
            onPlaylistSelected = onPlaylistSelectedForAdd
        )
    }

    if (showCreateDialog) {
        CreatePlaylistDialog(
            name = playlistName,
            onNameChange = onPlaylistNameChange,
            createSynced = createSynced,
            onCreateSyncedChange = onCreateSyncedChange,
            canCreateSynced = canCreateSynced,
            onDismiss = onDismissCreate,
            onCreate = onCreatePlaylist
        )
    }

    if (showRenameDialog && playlistToRename != null) {
        RenamePlaylistDialog(
            name = renamePlaylistName,
            onNameChange = onRenamePlaylistNameChange,
            onDismiss = onDismissRename,
            onRename = onRenamePlaylist
        )
    }

    if (showDeleteDialog && playlistToDelete != null) {
        DeletePlaylistDialog(
            playlistTitle = playlistToDelete.displayTitle,
            onDismiss = onDismissDelete,
            onDelete = onDeletePlaylist
        )
    }
}
