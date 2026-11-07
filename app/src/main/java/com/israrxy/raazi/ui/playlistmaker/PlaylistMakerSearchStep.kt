package com.israrxy.raazi.ui.playlistmaker

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.israrxy.raazi.model.MusicItem
import com.israrxy.raazi.ui.theme.Emerald500
import com.israrxy.raazi.utils.ThumbnailUtils

@Composable
internal fun PlaylistMakerSearchStep(
    query: String,
    results: List<MusicItem>,
    selectedIds: Set<String>,
    draftIds: Set<String>,
    isSearching: Boolean,
    searchError: String?,
    focusRequester: FocusRequester,
    onQueryChange: (String) -> Unit,
    onClearQuery: () -> Unit,
    onToggleSelection: (MusicItem) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    val keyboardController = LocalSoftwareKeyboardController.current

    Column(modifier = modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .focusRequester(focusRequester),
            placeholder = { Text("Search songs") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = onClearQuery) {
                        Icon(Icons.Default.Close, contentDescription = "Clear search")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { keyboardController?.hide() })
        )

        Spacer(modifier = Modifier.height(8.dp))

        Box(modifier = Modifier.weight(1f)) {
            when {
                isSearching && results.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Emerald500)
                    }
                }

                searchError != null -> {
                    PlaylistMakerMessage(
                        title = "Search failed",
                        body = searchError,
                        actionLabel = "Retry",
                        onAction = onRetry,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                query.isBlank() -> {
                    PlaylistMakerMessage(
                        title = "Search for songs",
                        body = "Pick a few songs, add them to the draft, then search again for the next batch.",
                        modifier = Modifier.fillMaxSize()
                    )
                }

                results.isEmpty() -> {
                    PlaylistMakerMessage(
                        title = "No songs found",
                        body = "Try a different title or artist.",
                        modifier = Modifier.fillMaxSize()
                    )
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = 20.dp,
                            end = 20.dp,
                            top = 4.dp,
                            bottom = 16.dp
                        )
                    ) {
                        item {
                            Text(
                                text = "${results.size} results",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                        items(results, key = { it.id }) { item ->
                            PlaylistMakerSongRow(
                                item = item,
                                isSelected = item.id in selectedIds,
                                isAdded = item.id in draftIds,
                                onToggle = { onToggleSelection(item) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaylistMakerSongRow(
    item: MusicItem,
    isSelected: Boolean,
    isAdded: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(enabled = !isAdded, onClick = onToggle)
            .background(
                if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                else Color.Transparent
            )
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isAdded) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = "Already added",
                tint = Emerald500,
                modifier = Modifier
                    .size(22.dp)
                    .padding(horizontal = 2.dp)
            )
        } else {
            Checkbox(
                checked = isSelected,
                onCheckedChange = null
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        AsyncImage(
            model = ThumbnailUtils.getListThumbnail(item.thumbnailUrl),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(50.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = item.artist,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (isAdded) {
            Text(
                text = "Added",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = Emerald500
            )
        } else {
            Text(
                text = formatTrackDuration(item.duration),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
internal fun PlaylistMakerMessage(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(44.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (actionLabel != null && onAction != null) {
            Spacer(modifier = Modifier.height(12.dp))
            TextButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}
