package com.israrxy.raazi.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.NorthWest
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.israrxy.raazi.data.db.SearchHistoryEntity
import com.israrxy.raazi.model.MusicContentType
import com.israrxy.raazi.model.MusicItem
import com.israrxy.raazi.utils.ThumbnailUtils

@Composable
internal fun SearchSuggestionsAndHistoryList(
    suggestionsListState: LazyListState,
    normalizedSearchQuery: String,
    searchHistory: List<SearchHistoryEntity>,
    searchSuggestions: List<String>,
    topResults: List<MusicItem>,
    onClearHistory: () -> Unit,
    onDeleteHistory: (SearchHistoryEntity) -> Unit,
    onSubmitSuggestionOrHistory: (String) -> Unit,
    onFillQuery: (String) -> Unit = onSubmitSuggestionOrHistory,
    onPreviewItemClick: (MusicItem) -> Unit
) {
    LazyColumn(
        state = suggestionsListState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 88.dp)
    ) {
        if (normalizedSearchQuery.isEmpty()) {
            if (searchHistory.isNotEmpty()) {
                item(key = "recent_searches_header") {
                    SearchSectionTitle(
                        title = "Recent searches",
                        actionText = "Clear",
                        onActionClick = onClearHistory
                    )
                }

                items(searchHistory.take(10), key = { it.id }) { history ->
                    RecentSearchRow(
                        history = history,
                        onClick = { onSubmitSuggestionOrHistory(history.query) },
                        onDelete = { onDeleteHistory(history) }
                    )
                }
            } else {
                item(key = "empty_recent_searches") {
                    EmptyRecentSearches()
                }
            }
        } else {
            val matchingHistory = searchHistory.take(2)
            val topSuggestions = searchSuggestions.take(5)
            val remainingSuggestions = searchSuggestions.drop(5)
            val previewResults = topResults.distinctBy { "${it.contentType}:${it.id}" }.take(6)

            // 1. Matching recent history at top (just like YT Music)
            if (matchingHistory.isNotEmpty()) {
                items(matchingHistory, key = { "recent_match_${it.id}" }) { history ->
                    RecentSearchRow(
                        history = history,
                        onClick = { onSubmitSuggestionOrHistory(history.query) },
                        onDelete = { onDeleteHistory(history) }
                    )
                }
            }

            // 2. Top YT Music query suggestions with prefix/completion highlighting
            if (topSuggestions.isNotEmpty()) {
                items(topSuggestions, key = { "suggestion_top_$it" }) { suggestion ->
                    SuggestionItem(
                        text = suggestion,
                        queryPrefix = normalizedSearchQuery,
                        onClick = { onSubmitSuggestionOrHistory(suggestion) },
                        onFillClick = { onFillQuery(suggestion) }
                    )
                }
            }

            // 3. YT Music Direct Entity Matches (Songs, Artists, Albums, Videos, Playlists)
            if (previewResults.isNotEmpty()) {
                items(previewResults, key = { item -> "top_result_${item.contentType}_${item.id}" }) { musicItem ->
                    SearchPreviewResultRow(
                        item = musicItem,
                        onClick = { onPreviewItemClick(musicItem) }
                    )
                }
            }

            // 4. Remaining query suggestions
            if (remainingSuggestions.isNotEmpty()) {
                items(remainingSuggestions.take(5), key = { "suggestion_rem_$it" }) { suggestion ->
                    SuggestionItem(
                        text = suggestion,
                        queryPrefix = normalizedSearchQuery,
                        onClick = { onSubmitSuggestionOrHistory(suggestion) },
                        onFillClick = { onFillQuery(suggestion) }
                    )
                }
            }

            // 5. Explicit Search action row
            item(key = "search_action") {
                SearchActionRow(
                    query = normalizedSearchQuery,
                    onClick = { onSubmitSuggestionOrHistory(normalizedSearchQuery) }
                )
            }
        }
    }
}

@Composable
fun RecentSearchCards(
    history: List<SearchHistoryEntity>,
    onClick: (SearchHistoryEntity) -> Unit,
    onDelete: (SearchHistoryEntity) -> Unit
) {
    val items = history.take(6)

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        userScrollEnabled = false
    ) {
        items(items, key = { it.id }) { historyItem ->
            RecentSearchRow(
                history = historyItem,
                onClick = { onClick(historyItem) },
                onDelete = { onDelete(historyItem) }
            )
        }
    }
}

@Composable
internal fun SearchSectionTitle(
    title: String,
    actionText: String? = null,
    onActionClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        if (actionText != null && onActionClick != null) {
            TextButton(onClick = onActionClick) {
                Text(actionText, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
internal fun EmptyRecentSearches() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 28.dp, vertical = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.size(32.dp)
        )
        Text(
            text = "No recent searches",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = "Search for your favorite tracks, artists, and playlists.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
internal fun SearchActionRow(
    query: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Search, null, tint = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.width(16.dp))
        Text(
            text = "Search for \"$query\"",
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun RecentSearchRow(
    history: SearchHistoryEntity,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val primaryText = history.resultTitle?.takeIf { it.isNotBlank() } ?: history.query
    val secondaryText = history.resultArtist?.takeIf { it.isNotBlank() }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (!history.thumbnailUrl.isNullOrBlank()) {
            AsyncImage(
                model = ThumbnailUtils.getHighQualityThumbnail(history.thumbnailUrl ?: ""),
                contentDescription = history.resultTitle ?: history.query,
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentScale = ContentScale.Crop
            )
            Spacer(Modifier.width(14.dp))
        } else {
            Icon(Icons.Default.History, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(16.dp))
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = primaryText,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (secondaryText != null) {
                Text(
                    text = secondaryText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Close, "Remove from history", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun SuggestionItem(
    text: String,
    queryPrefix: String = "",
    onClick: () -> Unit,
    onFillClick: () -> Unit = onClick
) {
    val normalColor = MaterialTheme.colorScheme.onSurfaceVariant
    val boldColor = MaterialTheme.colorScheme.onSurface
    val annotated = buildAnnotatedString {
        val prefix = queryPrefix.trim()
        if (prefix.isNotEmpty() && text.startsWith(prefix, ignoreCase = true)) {
            withStyle(SpanStyle(color = normalColor, fontWeight = FontWeight.Normal)) {
                append(text.substring(0, prefix.length))
            }
            withStyle(SpanStyle(color = boldColor, fontWeight = FontWeight.Bold)) {
                append(text.substring(prefix.length))
            }
        } else {
            withStyle(SpanStyle(color = boldColor, fontWeight = FontWeight.Medium)) {
                append(text)
            }
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 16.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(16.dp))
        Text(
            text = annotated,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        IconButton(onClick = onFillClick) {
            Icon(
                imageVector = Icons.Default.NorthWest,
                contentDescription = "Fill query",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
internal fun SearchPreviewResultRow(
    item: MusicItem,
    onClick: () -> Unit
) {
    val subtitle = item.searchPreviewSubtitle()
    val isArtist = item.isArtistResult()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = ThumbnailUtils.getHighQualityThumbnail(item.thumbnailUrl),
            contentDescription = item.title,
            modifier = Modifier
                .size(48.dp)
                .clip(if (isArtist) CircleShape else RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentScale = ContentScale.Crop
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

internal fun MusicItem.searchPreviewSubtitle(): String {
    return when {
        isArtistResult() -> "Artist"
        contentType == MusicContentType.ALBUM -> listOf("Album", artist).filter { it.isNotBlank() }.joinToString(" • ")
        contentType == MusicContentType.PLAYLIST || isPlaylist -> listOf("Playlist", artist).filter { it.isNotBlank() }.joinToString(" • ")
        else -> formatSearchItemSubtitle(this)
    }
}
