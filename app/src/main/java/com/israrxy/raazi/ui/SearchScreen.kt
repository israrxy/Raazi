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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
    val context = LocalContext.current
    
    // Add To Playlist Dialog State
    var showAddToPlaylistItem by remember { mutableStateOf<MusicItem?>(null) }
    
    val searchResults by searchViewModel.searchResults.collectAsState()
    val isSearching by searchViewModel.isSearching.collectAsState()
    val searchSuggestions by searchViewModel.searchSuggestions.collectAsState()
    val searchHistory by searchViewModel.searchHistory.collectAsState()
    val topResults by searchViewModel.topResults.collectAsStateWithLifecycle()
    val favoriteTracks by playerViewModel.favoriteTracks.collectAsStateWithLifecycle()
    val savedCollectionIds by playerViewModel.savedCollectionIds.collectAsStateWithLifecycle()
    
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

    // Track if showing results
    val showingResults = searchQuery.isNotBlank() &&
        searchViewModel.submittedQuery == searchQuery &&
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
                        Icon(Icons.Default.QueueMusic, "Add to Queue", tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                    IconButton(onClick = {
                        if (selectedTracks.isNotEmpty()) {
                            tracksToAddToPlaylist = selectedTracks.toList()
                        }
                    }) {
                        Icon(Icons.Default.PlaylistAdd, "Add to Playlist", tint = MaterialTheme.colorScheme.onPrimaryContainer)
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
        // Search Bar
        TextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .height(56.dp),
            placeholder = {
                Text("Search music, videos, artists...", color = MaterialTheme.colorScheme.onSurfaceVariant)
            },
            leadingIcon = {
                Icon(Icons.Default.Search, null, tint = MaterialTheme.colorScheme.primary)
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
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
            shape = RoundedCornerShape(16.dp),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(
                onSearch = {
                    if (searchQuery.isNotBlank()) {
                        searchViewModel.performSearch(searchQuery)
                        keyboardController?.hide()
                        focusManager.clearFocus()
                    }
                }
            )
        )
        
        // Service Toggle
        val selectedService by searchViewModel.selectedService.collectAsState()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            com.israrxy.raazi.ui.components.SonicChip(
                text = "YouTube",
                isSelected = selectedService == 0,
                onClick = { searchViewModel.selectService(0) }
            )
            Spacer(modifier = Modifier.width(8.dp))
            com.israrxy.raazi.ui.components.SonicChip(
                text = "SoundCloud",
                isSelected = selectedService == 1,
                onClick = { searchViewModel.selectService(1) }
            )
            Spacer(modifier = Modifier.width(8.dp))
            com.israrxy.raazi.ui.components.SonicChip(
                text = "Bandcamp",
                isSelected = selectedService == 2,
                onClick = { searchViewModel.selectService(2) }
            )
        }
        
        // Content: Results OR Suggestions
        if (showingResults) {
            when {
                isSearching -> {
                    SearchLoadingState(query = searchQuery)
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
                if (searchQuery.isNotEmpty() && searchSuggestions.isEmpty() && searchHistory.isEmpty()) {
                    item {
                        SearchIdleHint(query = searchQuery)
                    }
                }

                // History
                if (searchHistory.isNotEmpty() && searchQuery.isEmpty()) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Recent", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                            TextButton(onClick = { searchViewModel.clearSearchHistory() }) {
                                Text("Clear", color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                    
                    items(searchHistory) { history ->
                        HistoryItem(
                            query = history.query,
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
                
                // Suggestions
                if (searchSuggestions.isNotEmpty() && searchQuery.isNotEmpty()) {
                    items(searchSuggestions) { suggestion ->
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

                // Top Results (matching typing)
                if (topResults.isNotEmpty() && searchQuery.isNotEmpty()) {
                    item {
                        Text(
                            text = "Top Results",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp)
                        )
                    }
                    
                    val playableTopItems = topResults.filter { it.isPlayableSearchItem() }
                    
                    items(topResults, key = { item -> "top_result_${item.contentType}_${item.id}" }) { musicItem ->
                        val isArtist = musicItem.isArtistResult()
                        val isPlayable = musicItem.isPlayableSearchItem()
                        val isLiked = favoriteTracks.any { it.id == musicItem.id }
                        val savedCollectionId = musicItem.toSavedCollectionItemOrNull()?.id
                        val isSaved = savedCollectionId != null && savedCollectionId in savedCollectionIds

                        com.israrxy.raazi.ui.components.SongListItem(
                            song = musicItem,
                            isLiked = isLiked,
                            isSaved = isSaved,
                            showAddToPlaylist = isPlayable,
                            showGoToArtist = !isArtist && musicItem.artistId != null,
                            showDownload = isPlayable,
                            showRingtone = isPlayable,
                            showAddToQueue = isPlayable,
                            showMoreOptions = isPlayable,
                            showLike = isPlayable,
                            showSave = musicItem.toSavedCollectionItemOrNull() != null,
                            onClick = {
                                when {
                                    musicItem.isPlaylistResult() -> onNavigateToPlaylist(musicItem.id)
                                    isArtist && musicItem.artistId != null -> onNavigateToArtist(musicItem.artistId!!, musicItem.title)
                                    else -> {
                                        val playableIndex = playableTopItems.indexOf(musicItem)
                                        if (playableIndex != -1) {
                                            playerViewModel.playPlaylist(playableTopItems, playableIndex)
                                            onNavigateToPlayer()
                                        } else {
                                            playerViewModel.playPlaylist(listOf(musicItem), 0)
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
                            onDownloadForRingtone = {
                                if (isPlayable) {
                                    playerViewModel.downloadForRingtone(musicItem)
                                }
                            },
                            onAddToQueue = {
                                if (isPlayable) {
                                    playerViewModel.addToQueue(listOf(musicItem))
                                    android.widget.Toast.makeText(context, "Added to queue", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            },
                            onShowMoreOptions = {
                                if (isPlayable) {
                                    android.widget.Toast.makeText(context, "More options for ${musicItem.title}", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            },
                            onLike = { playerViewModel.toggleFavorite(musicItem) },
                            onSave = { playerViewModel.toggleSavedCollection(musicItem) }
                        )
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
fun HistoryItem(query: String, onClick: () -> Unit, onDelete: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.History, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(16.dp))
        Text(query, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface)
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
