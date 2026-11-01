package com.israrxy.raazi.ui.library

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
internal fun LibrarySelectionBottomBar(
    selectedCount: Int,
    onCancel: () -> Unit,
    onAddToPlaylist: (() -> Unit)? = null,
    onDelete: () -> Unit,
    deleteLabel: String = "Delete",
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 8.dp,
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onCancel, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Cancel selection")
            }

            Spacer(Modifier.width(8.dp))

            Text(
                text = "$selectedCount selected",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )

            if (onAddToPlaylist != null) {
                IconButton(onClick = onAddToPlaylist, modifier = Modifier.size(40.dp)) {
                    Icon(
                        imageVector = Icons.Default.PlaylistAdd,
                        contentDescription = "Add to playlist",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(Modifier.width(8.dp))
            }

            IconButton(onClick = onDelete, modifier = Modifier.size(40.dp)) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = deleteLabel,
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
