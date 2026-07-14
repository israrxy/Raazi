package com.israrxy.raazi.ui

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Surface
import coil.compose.AsyncImage
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.israrxy.raazi.model.MusicContentType
import com.israrxy.raazi.model.MusicItem
import com.israrxy.raazi.model.toSavedCollectionItemOrNull
import com.israrxy.raazi.utils.ThumbnailUtils
import com.israrxy.raazi.viewmodel.MusicPlayerViewModel
import com.israrxy.raazi.viewmodel.SearchViewModel
import com.israrxy.raazi.data.db.SearchHistoryEntity

@Composable
fun SearchScreen(
    playerViewModel: MusicPlayerViewModel,
    onNavigateToPlayer: () -> Unit,
    onNavigateToArtist: (String, String) -> Unit,
    onNavigateToPlaylist: (String) -> Unit
) {
    val searchViewModel: SearchViewModel = viewModel()
    var searchQuery by remember { mutableStateOf("") }
    
    // Add To Playlist Dialog State
    var showAddToPlaylistItem by remember { mutableStateOf<MusicItem?>(null) }
    
    val searchResults by searchViewModel.searchResults.collectAsState()
    val isSearching by searchViewModel.isSearching.collectAsState()
    val searchSuggestions by searchViewModel.searchSuggestions.collectAsState()
    val searchHistory by searchViewModel.searchHistory.collectAsState()
    val topResults by searchViewModel.topResults.collectAsStateWithLifecycle()
    val favoriteTracks by playerViewModel.favoriteTracks.collectAsStateWithLifecycle()
    val savedCollectionIds by playerViewModel.savedCollectionIds.collectAsStateWithLifecycle()
    val searchError by searchViewModel.searchError.collectAsState()
    
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    
    // Selection state
    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedTracks by remember { mutableStateOf(setOf<MusicItem>()) }
    
    // Clear search on exit
    DisposableEffect(Unit) {
        onDispose {
            searchViewModel.clearSearchResults()
        }
    }

    val normalizedSearchQuery = searchQuery.trim()

    // Track if showing results
    val showingResults = normalizedSearchQuery.isNotBlank() &&
        searchViewModel.submittedQuery == normalizedSearchQuery &&
        (isSearching || searchResults != null)
    val searchItems = remember(searchResults) {
        searchResults?.items ?: emptyList()
    }
    val searchSections = remember(searchItems) {
        buildSearchSections(searchItems)
    }
    val visibleSections = remember(searchSections) {
        searchSections.filter { it.items.isNotEmpty() }
    }
    var selectedFilter by remember { mutableStateOf("Personalized") }
    val filteredSections = remember(visibleSections, selectedFilter) {
        if (selectedFilter == "All" || selectedFilter == "Personalized") {
            visibleSections
        } else {
            visibleSections.filter { it.type.name == selectedFilter }
        }
    }
    val playableItems = remember(searchItems) {
        searchItems.filter { it.isPlayableSearchItem() }
    }
    val currentPlayableItems = remember(filteredSections, selectedFilter, playableItems) {
        if (selectedFilter == "Personalized") {
            playableItems
        } else {
            filteredSections.flatMap { it.items }.filter { it.isPlayableSearchItem() }
        }
    }
    var tracksToAddToPlaylist by remember { mutableStateOf<List<MusicItem>?>(null) }
    
    // Update query for suggestions
    LaunchedEffect(searchQuery) {
        searchViewModel.query.value = searchQuery
    }
    
    // Auto-exit selection mode if empty
    LaunchedEffect(selectedTracks) {
        if (selectedTracks.isEmpty() && isSelectionMode) {
            isSelectionMode = false
        }
    }

    LaunchedEffect(searchQuery, visibleSections) {
        if (selectedFilter != "Personalized" && selectedFilter != "All" && visibleSections.none { it.type.name == selectedFilter }) {
            selectedFilter = "Personalized"
        }
        if (searchViewModel.submittedQuery != searchQuery) {
            selectedFilter = "Personalized"
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        // Bulk Actions Bar
        AnimatedVisibility(
            visible = isSelectionMode,
            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut()
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.95f),
                tonalElevation = 6.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val allPlayableSelected = currentPlayableItems.isNotEmpty() && currentPlayableItems.all { selectedTracks.contains(it) }
                    Checkbox(
                        checked = allPlayableSelected,
                        onCheckedChange = { checkAll ->
                            if (checkAll) {
                                selectedTracks = selectedTracks + currentPlayableItems
                            } else {
                                selectedTracks = selectedTracks - currentPlayableItems.toSet()
                            }
                        },
                        colors = CheckboxDefaults.colors(
                            checkedColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            uncheckedColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
                        )
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        "${selectedTracks.size} selected",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = {
                        if (selectedTracks.isNotEmpty()) {
                            val list = selectedTracks.toList()
                            playerViewModel.playPlaylist(list, 0)
                            isSelectionMode = false
                            selectedTracks = emptySet()
                            onNavigateToPlayer()
                        }
                    }) {
                        Icon(Icons.Default.PlayArrow, "Play Selected", tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                    IconButton(onClick = {
                        if (selectedTracks.isNotEmpty()) {
                            playerViewModel.addToQueue(selectedTracks.toList())
                            isSelectionMode = false
                            selectedTracks = emptySet()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.QueueMusic, "Add to Queue", tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                    IconButton(onClick = {
                        if (selectedTracks.isNotEmpty()) {
                            tracksToAddToPlaylist = selectedTracks.toList()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.PlaylistAdd, "Add to Playlist", tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                    IconButton(onClick = {
                        selectedTracks.forEach { playerViewModel.downloadTrack(it) }
                        isSelectionMode = false
                        selectedTracks = emptySet()
                    }) {
                        Icon(Icons.Default.Download, "Download", tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                    IconButton(onClick = {
                        isSelectionMode = false
                        selectedTracks = emptySet()
                    }) {
                        Icon(Icons.Default.Close, "Cancel", tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
            }
        }
        SearchHeader(
            query = searchQuery,
            onQueryChange = { searchQuery = it },
            onClear = {
                searchQuery = ""
                searchViewModel.clearSearchResults()
            },
            onSearch = {
                if (normalizedSearchQuery.isNotBlank()) {
                    searchViewModel.performSearch(normalizedSearchQuery)
                    keyboardController?.hide()
                    focusManager.clearFocus()
                }
            }
        )
        // Content: Results OR Suggestions
        if (showingResults) {
            when {
                isSearching -> {
                    SearchLoadingState(query = searchQuery)
                }

                searchError != null -> {
                    SearchErrorState(
                        message = searchError ?: "Something went wrong",
                        onRetry = {
                            if (searchViewModel.submittedQuery.isNotBlank()) {
                                searchViewModel.performSearch(searchViewModel.submittedQuery)
                            }
                        }
                    )
                }

                visibleSections.isEmpty() -> {
                    SearchEmptyState(query = searchQuery)
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 80.dp)
                    ) {
                        item {
                            SearchResultsHeader(
                                query = searchQuery,
                                selectedFilter = selectedFilter,
                                onSelectFilter = { selectedFilter = it },
                                sections = visibleSections
                            )
                        }

                        if (selectedFilter == "Personalized") {
                            items(playableItems, key = { item -> "personalized_${item.id}" }) { musicItem ->
                                val isPlayable = musicItem.isPlayableSearchItem()
                                val isLiked = favoriteTracks.any { it.id == musicItem.id }
                                val savedCollectionId = musicItem.toSavedCollectionItemOrNull()?.id
                                val isSaved = savedCollectionId != null && savedCollectionId in savedCollectionIds

                                com.israrxy.raazi.ui.components.SongListItem(
                                    song = musicItem,
                                    isLiked = isLiked,
                                    isSaved = isSaved,
                                    isSelected = selectedTracks.contains(musicItem),
                                    isSelectionMode = isSelectionMode,
                                    selectionEnabled = isPlayable,
                                    onSelectionChange = { selected ->
                                        if (selected) {
                                            selectedTracks = selectedTracks + musicItem
                                        } else {
                                            selectedTracks = selectedTracks - musicItem
                                        }
                                    },
                                    onLongClick = {
                                        if (isPlayable) {
                                            isSelectionMode = true
                                            selectedTracks = selectedTracks + musicItem
                                        }
                                    },
                                    onClick = {
                                        val playableIndex = playableItems.indexOf(musicItem)
                                        if (playableIndex != -1) {
                                            playerViewModel.playPlaylist(playableItems, playableIndex)
                                            onNavigateToPlayer()
                                        }
                                    },
                                    onAddToPlaylist = { showAddToPlaylistItem = musicItem },
                                    onGoToArtist = {
                                        if (musicItem.artistId != null) {
                                            onNavigateToArtist(musicItem.artistId!!, musicItem.artist)
                                        }
                                    },
                                    onDownload = {
                                        if (isPlayable) {
                                            playerViewModel.downloadTrack(musicItem)
                                        }
                                    },
                                    onLike = { playerViewModel.toggleFavorite(musicItem) },
                                    onSave = { playerViewModel.toggleSavedCollection(musicItem) },
                                    showAddToPlaylist = isPlayable,
                                    showGoToArtist = !musicItem.isArtistResult() && musicItem.artistId != null,
                                    showDownload = isPlayable,
                                    showLike = isPlayable,
                                    showSave = musicItem.toSavedCollectionItemOrNull() != null
                                )
                            }
                        } else {
                            filteredSections.forEach { section ->
                                item(key = "header_${section.type.name}") {
                                    SearchSectionHeader(
                                        title = section.title,
                                        count = section.items.size,
                                        subtitle = section.subtitle
                                    )
                                }

                                items(section.items, key = { item -> "${section.type.name}_${item.id}" }) { musicItem ->
                                    val isArtist = musicItem.isArtistResult()
                                    val isPlayable = musicItem.isPlayableSearchItem()
                                    val canOpenArtist = !isArtist && musicItem.artistId != null
                                    val isLiked = favoriteTracks.any { it.id == musicItem.id }
                                    val savedCollectionId = musicItem.toSavedCollectionItemOrNull()?.id
                                    val isSaved = savedCollectionId != null && savedCollectionId in savedCollectionIds

                                    com.israrxy.raazi.ui.components.SongListItem(
                                        song = musicItem,
                                        isLiked = isLiked,
                                        isSaved = isSaved,
                                        isSelected = selectedTracks.contains(musicItem),
                                        isSelectionMode = isSelectionMode,
                                        selectionEnabled = isPlayable,
                                        onSelectionChange = { selected ->
                                            if (selected) {
                                                selectedTracks = selectedTracks + musicItem
                                            } else {
                                                selectedTracks = selectedTracks - musicItem
                                            }
                                        },
                                        onLongClick = {
                                            if (isPlayable) {
                                                isSelectionMode = true
                                                selectedTracks = selectedTracks + musicItem
                                            }
                                        },
                                        onClick = {
                                            when {
                                                musicItem.isPlaylistResult() -> onNavigateToPlaylist(musicItem.id)
                                                isArtist && musicItem.artistId != null -> onNavigateToArtist(musicItem.artistId!!, musicItem.title)
                                                else -> {
                                                    val playableIndex = playableItems.indexOf(musicItem)
                                                    if (playableIndex != -1) {
                                                        playerViewModel.playPlaylist(playableItems, playableIndex)
                                                        onNavigateToPlayer()
                                                    }
                                                }
                                            }
                                        },
                                        onAddToPlaylist = { showAddToPlaylistItem = musicItem },
                                        onGoToArtist = {
                                            if (musicItem.artistId != null) {
                                                onNavigateToArtist(musicItem.artistId!!, musicItem.artist)
                                            }
                                        },
                                        onDownload = {
                                            if (isPlayable) {
                                                playerViewModel.downloadTrack(musicItem)
                                            }
                                        },
                                        onLike = { playerViewModel.toggleFavorite(musicItem) },
                                        onSave = { playerViewModel.toggleSavedCollection(musicItem) },
                                        showAddToPlaylist = isPlayable,
                                        showGoToArtist = canOpenArtist,
                                        showDownload = isPlayable,
                                        showLike = isPlayable,
                                        showSave = musicItem.toSavedCollectionItemOrNull() != null
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // SHOW SUGGESTIONS & HISTORY
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                if (normalizedSearchQuery.isEmpty()) {
                    if (searchHistory.isNotEmpty()) {
                        item(key = "recent_searches_header") {
                            SearchSectionTitle(
                                title = "Recent searches",
                                actionText = "Clear",
                                onActionClick = { searchViewModel.clearSearchHistory() }
                            )
                        }

                        items(searchHistory.take(10), key = { it.id }) { history ->
                            RecentSearchRow(
                                history = history,
                                onClick = {
                                    searchQuery = history.query
                                    searchViewModel.performSearch(history.query)
                                    keyboardController?.hide()
                                    focusManager.clearFocus()
                                },
                                onDelete = { searchViewModel.deleteSearchHistory(history) }
                            )
                        }
                    } else {
                        item(key = "empty_recent_searches") {
                            EmptyRecentSearches()
                        }
                    }
                } else {
                    if (topResults.isNotEmpty()) {
                        val previewResults = topResults.distinctBy { it.id }.take(8)
                        val playableTopItems = previewResults.filter { it.isPlayableSearchItem() }

                        item(key = "top_results_header") {
                            SearchSectionTitle(title = "Top results")
                        }

                        items(previewResults, key = { item -> "top_result_${item.contentType}_${item.id}" }) { musicItem ->
                            SearchPreviewResultRow(
                                item = musicItem,
                                onClick = {
                                    when {
                                        musicItem.isPlaylistResult() -> onNavigateToPlaylist(musicItem.id)
                                        musicItem.isArtistResult() && musicItem.artistId != null -> onNavigateToArtist(musicItem.artistId!!, musicItem.title)
                                        else -> {
                                            val playableIndex = playableTopItems.indexOf(musicItem)
                                            if (playableIndex != -1) {
                                                playerViewModel.playPlaylist(playableTopItems, playableIndex)
                                            } else {
                                                playerViewModel.playPlaylist(listOf(musicItem), 0)
                                            }
                                            onNavigateToPlayer()
                                        }
                                    }
                                }
                            )
                        }
                    }

                    item(key = "search_action") {
                        SearchActionRow(
                            query = normalizedSearchQuery,
                            onClick = {
                                searchViewModel.performSearch(normalizedSearchQuery)
                                keyboardController?.hide()
                                focusManager.clearFocus()
                            }
                        )
                    }

                    if (searchHistory.isNotEmpty()) {
                        item(key = "recent_matches_header") {
                            SearchSectionTitle(title = "Recent searches")
                        }

                        items(searchHistory.take(3), key = { "recent_match_${it.id}" }) { history ->
                            RecentSearchRow(
                                history = history,
                                onClick = {
                                    searchQuery = history.query
                                    searchViewModel.performSearch(history.query)
                                    keyboardController?.hide()
                                    focusManager.clearFocus()
                                },
                                onDelete = { searchViewModel.deleteSearchHistory(history) }
                            )
                        }
                    }

                    if (searchSuggestions.isNotEmpty()) {
                        item(key = "suggestions_header") {
                            SearchSectionTitle(title = "Suggested searches")
                        }

                        items(searchSuggestions.take(8), key = { "suggestion_$it" }) { suggestion ->
                            SuggestionItem(
                                text = suggestion,
                                onClick = {
                                    searchQuery = suggestion
                                    searchViewModel.performSearch(suggestion)
                                    keyboardController?.hide()
                                    focusManager.clearFocus()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
    
    // Add Playlist Dialog
    if (showAddToPlaylistItem != null) {
        com.israrxy.raazi.ui.components.AddToPlaylistDialog(
            viewModel = playerViewModel,
            onDismiss = { showAddToPlaylistItem = null },
            onPlaylistSelected = { playlist ->
                playerViewModel.addToPlaylist(showAddToPlaylistItem!!, playlist)
                showAddToPlaylistItem = null
            }
        )
    }

    if (tracksToAddToPlaylist != null) {
        com.israrxy.raazi.ui.components.AddToPlaylistDialog(
            viewModel = playerViewModel,
            onDismiss = { tracksToAddToPlaylist = null },
            onPlaylistSelected = { playlist ->
                playerViewModel.addToPlaylist(tracksToAddToPlaylist!!, playlist)
                tracksToAddToPlaylist = null
                isSelectionMode = false
                selectedTracks = emptySet()
            }
        )
    }
}

private data class SearchSectionUiModel(
    val type: MusicContentType,
    val title: String,
    val subtitle: String,
    val items: List<MusicItem>
)

private fun buildSearchSections(items: List<MusicItem>): List<SearchSectionUiModel> {
    return listOf(
        SearchSectionUiModel(
            type = MusicContentType.SONG,
            title = "Music",
            subtitle = "Songs and audio-first matches",
            items = items.filter { it.contentType == MusicContentType.SONG || it.contentType == MusicContentType.UNKNOWN }
        ),
        SearchSectionUiModel(
            type = MusicContentType.VIDEO,
            title = "Videos",
            subtitle = "Watch-based matches",
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
            subtitle = "Curated lists and mixes",
            items = items.filter { it.contentType == MusicContentType.PLAYLIST }
        )
    )
}

private fun MusicItem.isArtistResult(): Boolean {
    return contentType == MusicContentType.ARTIST || artistId != null || artist == "Artist"
}

private fun MusicItem.isPlaylistResult(): Boolean {
    return isPlaylist || contentType == MusicContentType.ALBUM || contentType == MusicContentType.PLAYLIST
}

private fun MusicItem.isPlayableSearchItem(): Boolean {
    return contentType == MusicContentType.SONG ||
        contentType == MusicContentType.VIDEO ||
        (contentType == MusicContentType.UNKNOWN && !isPlaylistResult() && !isArtistResult())
}

@Composable
private fun SearchHeader(
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
                    text = "What do you want to listen to?",
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
                        Icon(Icons.Default.Close, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
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
            shape = RoundedCornerShape(10.dp),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch() })
        )
    }
}

@Composable
private fun SearchResultsHeader(
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
        Text(
            text = "Results for \"$query\"",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = selectedFilter == "Personalized",
                onClick = { onSelectFilter("Personalized") },
                label = { Text("Personalized") }
            )
            FilterChip(
                selected = selectedFilter == "All",
                onClick = { onSelectFilter("All") },
                label = { Text("All (${sections.sumOf { it.items.size }})") }
            )
            sections.forEach { section ->
                FilterChip(
                    selected = selectedFilter == section.type.name,
                    onClick = {
                        onSelectFilter(
                            if (selectedFilter == section.type.name) "Personalized" else section.type.name
                        )
                    },
                    label = { Text("${section.title} (${section.items.size})") }
                )
            }
        }
    }
}

@Composable
private fun SearchSectionHeader(
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
private fun SearchLoadingState(query: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
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
private fun SearchEmptyState(query: String) {
    Box(
        modifier = Modifier
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
private fun SearchErrorState(
    message: String,
    onRetry: () -> Unit
) {
    Box(
        modifier = Modifier
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

@Composable
private fun SearchIdleHint(query: String) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "Ready to search",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Press search to look for \"$query\" across music, videos, artists, albums, and playlists.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
        items(items, key = { it.id }) { history ->
            RecentSearchRow(
                history = history,
                onClick = { onClick(history) },
                onDelete = { onDelete(history) }
            )
        }
    }
}

@Composable
private fun SearchSectionTitle(
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
private fun EmptyRecentSearches() {
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
    }
}

@Composable
private fun SearchActionRow(
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
private fun RecentSearchRow(
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
            Icon(Icons.Default.Close, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun SuggestionItem(text: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(16.dp))
        Text(text, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface)
        Icon(Icons.Default.NorthWest, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SearchPreviewResultRow(
    item: MusicItem,
    onClick: () -> Unit
) {
    val subtitle = item.searchPreviewSubtitle()

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
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentScale = ContentScale.Crop
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
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

private fun MusicItem.searchPreviewSubtitle(): String {
    return when {
        isArtistResult() -> "Artist"
        contentType == MusicContentType.ALBUM -> listOf("Album", artist).filter { it.isNotBlank() }.joinToString(" • ")
        contentType == MusicContentType.PLAYLIST || isPlaylist -> listOf("Playlist", artist).filter { it.isNotBlank() }.joinToString(" • ")
        contentType == MusicContentType.VIDEO -> listOf("Video", artist).filter { it.isNotBlank() }.joinToString(" • ")
        artist.isNotBlank() -> artist
        else -> "Song"
    }
}
