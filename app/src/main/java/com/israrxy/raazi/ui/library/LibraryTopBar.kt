package com.israrxy.raazi.ui.library

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

// ---------------------------------------------------------------------------------------
// Header, search field and the filter / sort / layout control bar.
// ---------------------------------------------------------------------------------------

@Composable
internal fun LibraryHeader(
    summary: String,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Library",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            if (summary.isNotBlank()) {
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
        IconButton(onClick = onOpenSettings) {
            Icon(Icons.Default.Settings, contentDescription = "Settings")
        }
    }
}

@Composable
internal fun LibrarySearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = { Text("Search playlists, songs, artists") },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Default.Close, contentDescription = "Clear search")
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(16.dp)
    )
}

/**
 * Filter chips + sort + layout toggle in one bar. The chips scroll horizontally and the
 * two icon buttons stay pinned, so the bar never overflows on small phones.
 */
@Composable
internal fun LibraryFilterBar(
    filter: LibraryFilter,
    onFilterChange: (LibraryFilter) -> Unit,
    sort: LibrarySort,
    onSortChange: (LibrarySort) -> Unit,
    layout: LibraryLayout,
    onLayoutChange: (LibraryLayout) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            LibraryFilter.entries.forEach { option ->
                FilterChip(
                    selected = filter == option,
                    onClick = { onFilterChange(option) },
                    label = { Text(option.label) }
                )
            }
        }
        Spacer(Modifier.width(4.dp))
        LibrarySortMenu(current = sort, onSelect = onSortChange)
        LibraryLayoutToggle(layout = layout, onToggle = onLayoutChange)
    }
}

@Composable
private fun LibrarySortMenu(
    current: LibrarySort,
    onSelect: (LibrarySort) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Default.Sort, contentDescription = "Sort: ${current.label}")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            Text(
                text = "Sort by",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
            )
            LibrarySort.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label) },
                    trailingIcon = {
                        if (option == current) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = "Selected",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    },
                    onClick = {
                        expanded = false
                        onSelect(option)
                    }
                )
            }
        }
    }
}

@Composable
private fun LibraryLayoutToggle(
    layout: LibraryLayout,
    onToggle: (LibraryLayout) -> Unit
) {
    IconButton(
        onClick = {
            onToggle(if (layout == LibraryLayout.GRID) LibraryLayout.LIST else LibraryLayout.GRID)
        }
    ) {
        Icon(
            imageVector = if (layout == LibraryLayout.GRID) {
                Icons.Default.ViewList
            } else {
                Icons.Default.GridView
            },
            contentDescription = if (layout == LibraryLayout.GRID) {
                "Switch to list view"
            } else {
                "Switch to grid view"
            }
        )
    }
}
