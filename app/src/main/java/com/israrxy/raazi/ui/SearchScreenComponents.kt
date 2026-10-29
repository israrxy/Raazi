package com.israrxy.raazi.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.israrxy.raazi.model.MusicContentType
import com.israrxy.raazi.model.MusicItem

internal data class SearchSectionUiModel(
    val type: MusicContentType,
    val title: String,
    val subtitle: String,
    val items: List<MusicItem>
)

internal fun buildSearchSections(items: List<MusicItem>): List<SearchSectionUiModel> {
    return listOf(
        SearchSectionUiModel(
            type = MusicContentType.SONG,
            title = "Songs",
            subtitle = "Official audio and songs",
            items = items.filter {
                it.contentType == MusicContentType.SONG ||
                    (it.contentType == MusicContentType.UNKNOWN && !it.isArtistResult() && !it.isPlaylistResult())
            }
        ),
        SearchSectionUiModel(
            type = MusicContentType.VIDEO,
            title = "Videos",
            subtitle = "Music videos, covers, and live performances",
            items = items.filter { it.contentType == MusicContentType.VIDEO }
        ),
        SearchSectionUiModel(
            type = MusicContentType.ARTIST,
            title = "Artists",
            subtitle = "Profiles and channels",
            items = items.filter { it.isArtistResult() }
        ),
        SearchSectionUiModel(
            type = MusicContentType.ALBUM,
            title = "Albums",
            subtitle = "Album and release pages",
            items = items.filter { it.contentType == MusicContentType.ALBUM }
        ),
        SearchSectionUiModel(
            type = MusicContentType.PLAYLIST,
            title = "Playlists",
            subtitle = "Community and featured playlists",
            items = items.filter { it.contentType == MusicContentType.PLAYLIST }
        )
    )
}

internal fun MusicItem.isArtistResult(): Boolean {
    return contentType == MusicContentType.ARTIST ||
        (contentType == MusicContentType.UNKNOWN && artist.equals("Artist", ignoreCase = true) && videoUrl.isBlank())
}

internal fun MusicItem.isPlaylistResult(): Boolean {
    return isPlaylist || contentType == MusicContentType.ALBUM || contentType == MusicContentType.PLAYLIST
}

internal fun MusicItem.isPlayableSearchItem(): Boolean {
    return contentType == MusicContentType.SONG ||
        contentType == MusicContentType.VIDEO ||
        (contentType == MusicContentType.UNKNOWN && !isPlaylistResult() && !isArtistResult())
}

@Composable
internal fun SearchHeader(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    onSearch: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(top = 14.dp, bottom = 8.dp)
    ) {
        Text(
            text = "Search",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(12.dp))
        TextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            placeholder = {
                Text(
                    text = "Songs, artists, albums, or videos...",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            leadingIcon = {
                Icon(Icons.Default.Search, null, tint = MaterialTheme.colorScheme.onSurface)
            },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = onClear) {
                        Icon(Icons.Default.Close, "Clear search", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                cursorColor = MaterialTheme.colorScheme.primary,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent
            ),
            shape = RoundedCornerShape(14.dp),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch() })
        )
    }
}

@Composable
internal fun SearchResultsHeader(
    query: String,
    selectedFilter: String,
    onSelectFilter: (String) -> Unit,
    sections: List<SearchSectionUiModel>
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = selectedFilter == "All",
                onClick = { onSelectFilter("All") },
                label = { Text("All") }
            )
            sections.forEach { section ->
                FilterChip(
                    selected = selectedFilter == section.type.name,
                    onClick = {
                        onSelectFilter(
                            if (selectedFilter == section.type.name) "All" else section.type.name
                        )
                    },
                    label = { Text("${section.title} (${section.items.size})") }
                )
            }
        }
    }
}

@Composable
internal fun SearchSectionHeader(
    title: String,
    count: Int,
    subtitle: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
internal fun SearchLoadingState(query: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CircularProgressIndicator()
            Text(
                text = "Searching for \"$query\"",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Grouping music, videos, artists, albums, and playlists.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
internal fun SearchEmptyState(query: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.SearchOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(40.dp)
            )
            Text(
                text = "No results for \"$query\"",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "Try a shorter title, artist name, or switch search service.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
internal fun SearchErrorState(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Icon(
                imageVector = Icons.Default.CloudOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                modifier = Modifier.size(44.dp)
            )
            Text(
                text = "Couldn't load results",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Button(
                onClick = onRetry,
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Default.Refresh, null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Retry", fontWeight = FontWeight.Bold)
            }
        }
    }
}
