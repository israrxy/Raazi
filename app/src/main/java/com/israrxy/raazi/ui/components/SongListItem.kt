package com.israrxy.raazi.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import com.israrxy.raazi.ui.theme.*
import com.israrxy.raazi.utils.ThumbnailUtils

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun SongListItem(
    song: MusicItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isLiked: Boolean = false,
    isSelected: Boolean = false,
    isSelectionMode: Boolean = false,
    selectionEnabled: Boolean = true,
    onSelectionChange: (Boolean) -> Unit = {},
    onLongClick: () -> Unit = {},
    onLike: () -> Unit = {},
    onSave: () -> Unit = {},
    onAddToPlaylist: () -> Unit = {},
    onGoToArtist: () -> Unit = {},
    onDownload: () -> Unit = {},
    showAddToPlaylist: Boolean = true,
    showGoToArtist: Boolean = true,
    showDownload: Boolean = true,
    showLike: Boolean = true,
    showSave: Boolean = false,
    isSaved: Boolean = false
) {
    var showMenu by remember { mutableStateOf(false) }
    val hasMenuActions = showAddToPlaylist || showGoToArtist || showDownload || showLike || showSave

    Row(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { 
                    if (isSelectionMode && selectionEnabled) onSelectionChange(!isSelected) else onClick() 
                },
                onLongClick = {
                    if (selectionEnabled) {
                        onLongClick()
                    }
                }
            )
            .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else Color.Transparent)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isSelectionMode && selectionEnabled) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onSelectionChange(it) },
                colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary),
                modifier = Modifier.padding(end = 12.dp)
            )
        }
        AsyncImage(
            model = ThumbnailUtils.getListThumbnail(song.thumbnailUrl),
            contentDescription = null,
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentScale = ContentScale.Crop
        )
        
        Spacer(modifier = Modifier.width(12.dp))
        
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = song.artist,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        
        if (hasMenuActions) {
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Options",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                    modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                ) {
                    if (showAddToPlaylist) {
                        DropdownMenuItem(
                            text = { Text("Add to Playlist", color = MaterialTheme.colorScheme.onSurface) },
                            onClick = {
                                showMenu = false
                                onAddToPlaylist()
                            },
                            leadingIcon = { Icon(Icons.Default.PlaylistAdd, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                        )
                    }

                    if (showGoToArtist) {
                        DropdownMenuItem(
                            text = { Text("Go to Artist", color = MaterialTheme.colorScheme.onSurface) },
                            onClick = {
                                showMenu = false
                                onGoToArtist()
                            },
                            leadingIcon = { Icon(Icons.Default.Person, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                        )
                    }

                    if (showDownload) {
                        DropdownMenuItem(
                            text = { Text("Download", color = MaterialTheme.colorScheme.onSurface) },
                            onClick = {
                                showMenu = false
                                onDownload()
                            },
                            leadingIcon = { Icon(Icons.Default.Download, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                        )
                    }

                    if (showLike) {
                        DropdownMenuItem(
                            text = { Text(if (isLiked) "Unlike" else "Like", color = MaterialTheme.colorScheme.onSurface) },
                            onClick = {
                                showMenu = false
                                onLike()
                            },
                            leadingIcon = {
                                Icon(
                                    if (isLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                    null,
                                    tint = if (isLiked) Color.Red else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        )
                    }

                    if (showSave) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    if (isSaved) "Remove from Library" else "Save to Library",
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            },
                            onClick = {
                                showMenu = false
                                onSave()
                            },
                            leadingIcon = {
                                Icon(
                                    if (isSaved) Icons.Default.Bookmark else Icons.Outlined.BookmarkBorder,
                                    null,
                                    tint = if (isSaved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}
