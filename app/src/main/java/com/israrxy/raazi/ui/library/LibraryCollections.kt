package com.israrxy.raazi.ui.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Album
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.QueueMusic
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.Checkbox
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.israrxy.raazi.data.db.PlaylistWithTracks
import com.israrxy.raazi.data.playlist.isYouTubeEditablePlaylist
import com.israrxy.raazi.data.playlist.isYouTubeSyncedPlaylist
import com.israrxy.raazi.model.MusicContentType
import com.israrxy.raazi.model.MusicItem
import com.israrxy.raazi.model.SavedCollectionItem

// ---------------------------------------------------------------------------------------
// Playlists, albums and artists in the compact library list.
// ---------------------------------------------------------------------------------------

/** Everything a card/row needs, built once so grid and list stay visually consistent. */
internal data class LibraryCollectionUi(
    val key: String,
    val title: String,
    val subtitle: String,
    val thumbnailUrl: String?,
    val fallbackIcon: ImageVector,
    val menu: List<LibraryMenuItem>,
    val onClick: () -> Unit,
    val onLongClick: (() -> Unit)? = null
)

internal fun PlaylistWithTracks.toCollectionUi(
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
): LibraryCollectionUi {
    val playlist = playlist
    val subtitle = when {
        playlist.isYouTubeSyncedPlaylist() && playlist.isYouTubeEditablePlaylist() ->
            "Synced with YouTube Music"
        playlist.isYouTubeSyncedPlaylist() -> "Saved from YouTube Music"
        else -> "${counted(tracks.size, "song")} • Local"
    }
    return LibraryCollectionUi(
        key = "pl_${playlist.id}",
        title = playlist.displayTitle,
        subtitle = subtitle,
        thumbnailUrl = playlist.thumbnailUrl.takeIf { it.isNotBlank() },
        fallbackIcon = Icons.Outlined.QueueMusic,
        menu = listOf(
            LibraryMenuItem("Rename", onRename),
            LibraryMenuItem("Delete", onDelete, destructive = true)
        ),
        onClick = onOpen,
        onLongClick = onRename
    )
}

internal fun SavedCollectionItem.toCollectionUi(
    onOpen: () -> Unit,
    onRemove: () -> Unit
): LibraryCollectionUi = LibraryCollectionUi(
    key = id,
    title = title,
    subtitle = subtitle,
    thumbnailUrl = thumbnailUrl.takeIf { it.isNotBlank() },
    fallbackIcon = when (contentType) {
        MusicContentType.ARTIST -> Icons.Outlined.Person
        MusicContentType.ALBUM -> Icons.Outlined.Album
        else -> Icons.Outlined.Bookmark
    },
    menu = listOf(LibraryMenuItem("Remove from library", onRemove, destructive = true)),
    onClick = onOpen,
    onLongClick = onRemove
)

/** Compact one-line variant used by the list layout. */
@Composable
internal fun LibraryCollectionRow(
    item: LibraryCollectionUi,
    modifier: Modifier = Modifier
) {
    LibraryRow(
        icon = item.fallbackIcon,
        title = item.title,
        subtitle = item.subtitle,
        thumbnailUrl = item.thumbnailUrl,
        showChevron = false,
        onClick = item.onClick,
        onLongClick = item.onLongClick,
        modifier = modifier,
        trailing = {
            OverflowMenuButton(
                items = item.menu,
                contentDescription = "Options for ${item.title}"
            )
        }
    )
}

/** Turns a liked/history track into a row payload shared by grid and list layouts. */
internal data class LibrarySongUi(
    val key: String,
    val track: MusicItem,
    val isSelected: Boolean = false,
    val isSelectionMode: Boolean = false,
    val onClick: () -> Unit,
    val onLongClick: (() -> Unit)? = null,
    val onAddToPlaylist: (() -> Unit)? = null,
    val onRemove: (() -> Unit)? = null
)

/**
 * @param preview rows to show (possibly capped)
 * @param queue full list that starts playing, so a tap inside a capped preview still
 *   enqueues the whole section
 */
internal fun buildSongUi(
    preview: List<MusicItem>,
    queue: List<MusicItem>,
    keyPrefix: String,
    isSelected: (MusicItem) -> Boolean = { false },
    isSelectionMode: Boolean = false,
    onPlay: (List<MusicItem>, Int) -> Unit,
    onLongClick: ((MusicItem) -> Unit)? = null,
    onAddToPlaylist: ((MusicItem) -> Unit)? = null,
    onRemove: ((MusicItem) -> Unit)? = null
): List<LibrarySongUi> = preview.map { track ->
    val selected = isSelected(track)
    LibrarySongUi(
        key = "${keyPrefix}_${track.id}",
        track = track,
        isSelected = selected,
        isSelectionMode = isSelectionMode,
        onClick = {
            if (isSelectionMode && onLongClick != null) {
                onLongClick(track)
            } else {
                onPlay(queue, queue.indexOf(track).coerceAtLeast(0))
            }
        },
        onLongClick = onLongClick?.let { longClick -> { longClick(track) } },
        onAddToPlaylist = onAddToPlaylist?.let { add -> { add(track) } },
        onRemove = onRemove?.let { remove -> { remove(track) } }
    )
}

/** Artwork + title + artist + duration. No chevron: tapping here plays. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun LibrarySongRow(
    item: LibrarySongUi,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (item.isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                else Color.Transparent
            )
            .combinedClickable(
                onClick = item.onClick,
                onLongClick = item.onLongClick
            )
            .padding(vertical = 6.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (item.isSelectionMode) {
            Checkbox(
                checked = item.isSelected,
                onCheckedChange = { item.onClick() },
                modifier = Modifier.padding(end = 6.dp)
            )
        }

        Box(
            modifier = Modifier.size(46.dp).clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            if (item.track.thumbnailUrl.isNotBlank()) {
                AsyncImage(
                    model = item.track.thumbnailUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    imageVector = Icons.Outlined.MusicNote,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.width(13.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.track.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = item.track.artist,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        val duration = formatDuration(item.track.duration)
        if (duration.isNotEmpty()) {
            Spacer(Modifier.width(8.dp))
            Text(
                text = duration,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (!item.isSelectionMode) {
            val menuItems = buildList {
                item.onAddToPlaylist?.let { add ->
                    add(LibraryMenuItem("Add to playlist", add))
                }
                item.onRemove?.let { remove ->
                    add(LibraryMenuItem("Delete from history", remove, destructive = true))
                }
            }
            if (menuItems.isNotEmpty()) {
                OverflowMenuButton(
                    items = menuItems,
                    contentDescription = "Options for ${item.track.title}"
                )
            }
        }
    }
}
