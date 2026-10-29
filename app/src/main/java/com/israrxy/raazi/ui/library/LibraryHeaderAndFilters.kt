package com.israrxy.raazi.ui.library

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.israrxy.raazi.ui.theme.Emerald500

enum class LibrarySortOrder(val label: String) {
    RECENTLY_ADDED("Recently added"),
    TITLE_ASC("Title (A-Z)"),
    TITLE_DESC("Title (Z-A)")
}

/**
 * Modern Library Top Bar with User Avatar, Title, Search, Plus (+), and Settings.
 */
@Composable
internal fun LibraryTopBar(
    isLoggedIn: Boolean,
    accountName: String?,
    onOpenSearch: () -> Unit,
    onAddClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // User profile avatar / YT indicator
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(
                    if (isLoggedIn) Emerald500.copy(alpha = 0.2f)
                    else MaterialTheme.colorScheme.surfaceContainerHighest
                ),
            contentAlignment = Alignment.Center
        ) {
            val initial = accountName?.firstOrNull()?.uppercaseChar()?.toString() ?: "Y"
            if (isLoggedIn) {
                Text(
                    text = initial,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Emerald500
                )
            } else {
                Icon(
                    imageVector = Icons.Outlined.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        Text(
            text = "Your Library",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.5).sp
            ),
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f)
        )

        IconButton(onClick = onOpenSearch) {
            Icon(Icons.Filled.Search, contentDescription = "Search library")
        }

        IconButton(onClick = onAddClick) {
            Icon(Icons.Filled.Add, contentDescription = "New playlist or import")
        }

        IconButton(onClick = onSettingsClick) {
            Icon(Icons.Filled.Settings, contentDescription = "Settings")
        }
    }
}

/**
 * In-Library Search Field with smooth expand/collapse.
 */
@Composable
internal fun LibrarySearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    visible: Boolean,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut()
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = { Text("Find in your library...") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = Emerald500) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = onClear) {
                        Icon(Icons.Filled.Close, contentDescription = "Clear search")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Emerald500,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.7f)
            ),
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 6.dp)
        )
    }
}

/**
 * Filter Pills: "Playlists", "Songs", "Albums", "Artists", "Downloaded".
 */
@Composable
internal fun LibraryFilterChips(
    selectedFilter: String,
    onFilterSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        listOf("All", "Playlists", "Songs", "Albums", "Artists", "Downloaded").forEach { filter ->
            val isSelected = selectedFilter == filter
            FilterChip(
                selected = isSelected,
                onClick = { onFilterSelect(if (isSelected && filter != "All") "All" else filter) },
                label = {
                    Text(
                        text = filter,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                    )
                },
                leadingIcon = if (isSelected && filter != "All") {
                    { Icon(Icons.Filled.Close, contentDescription = "Clear filter", modifier = Modifier.size(16.dp)) }
                } else null,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Emerald500.copy(alpha = 0.22f),
                    selectedLabelColor = Emerald500
                ),
                shape = RoundedCornerShape(50)
            )
        }
    }
}

/**
 * Sort Order Selector & Grid/List Toggle Bar.
 */
@Composable
internal fun LibrarySortAndViewBar(
    currentSort: LibrarySortOrder,
    onSortChange: (LibrarySortOrder) -> Unit,
    isGridView: Boolean,
    onToggleGridView: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showSortMenu by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { showSortMenu = true }
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Sort,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = currentSort.label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            DropdownMenu(
                expanded = showSortMenu,
                onDismissRequest = { showSortMenu = false }
            ) {
                LibrarySortOrder.entries.forEach { sort ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = sort.label,
                                fontWeight = if (sort == currentSort) FontWeight.Bold else FontWeight.Normal,
                                color = if (sort == currentSort) Emerald500 else MaterialTheme.colorScheme.onSurface
                            )
                        },
                        onClick = {
                            onSortChange(sort)
                            showSortMenu = false
                        }
                    )
                }
            }
        }

        IconButton(
            onClick = onToggleGridView,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                imageVector = if (isGridView) Icons.AutoMirrored.Filled.ViewList else Icons.Default.GridView,
                contentDescription = if (isGridView) "Switch to list view" else "Switch to grid view",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * Modern Action Bottom Sheet when user taps "+" in Library.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LibraryActionBottomSheet(
    onNewPlaylist: () -> Unit,
    onImportSpotify: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            Text(
                text = "Add to Library",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            ListItem(
                headlineContent = { Text("New Playlist", fontWeight = FontWeight.SemiBold) },
                supportingContent = { Text("Create a local or YouTube Music playlist") },
                leadingContent = {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(Emerald500.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.PlaylistAdd, contentDescription = null, tint = Emerald500)
                    }
                },
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable {
                        onDismiss()
                        onNewPlaylist()
                    }
            )

            ListItem(
                headlineContent = { Text("Import from Spotify", fontWeight = FontWeight.SemiBold) },
                supportingContent = { Text("Import any public Spotify playlist by URL") },
                leadingContent = {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF1DB954).copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.CloudDownload, contentDescription = null, tint = Color(0xFF1DB954))
                    }
                },
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable {
                        onDismiss()
                        onImportSpotify()
                    }
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
