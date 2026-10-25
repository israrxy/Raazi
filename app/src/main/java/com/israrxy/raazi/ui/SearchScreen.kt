package com.israrxy.raazi.ui

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.israrxy.raazi.model.MusicItem
import com.israrxy.raazi.model.toSavedCollectionItemOrNull
import com.israrxy.raazi.utils.ShareUtils
import com.israrxy.raazi.viewmodel.MusicPlayerViewModel
import com.israrxy.raazi.viewmodel.SearchViewModel

@Composable
fun SearchScreen(
    playerViewModel: MusicPlayerViewModel,
    onNavigateToPlayer: () -> Unit,
    onNavigateToArtist: (String, String) -> Unit,
    onNavigateToPlaylist: (String) -> Unit
) {
    val context = LocalContext.current
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
    var selectedFilter by remember { mutableStateOf("All") }
    val filteredSections = remember(visibleSections, selectedFilter) {
        if (selectedFilter == "All" || selectedFilter == "Personalized") {
            visibleSections
        } else {
            visibleSections.filter { it.type.name == selectedFilter }
        }
    }
    val playableItems = remember(searchItems, searchResults) {
        val ytTopTracks = searchResults?.topTracks.orEmpty()
        if (ytTopTracks.isNotEmpty()) {
            ytTopTracks.filter { it.isPlayableSearchItem() }.distinctBy { it.id }
        } else {
            searchItems.filter { it.isPlayableSearchItem() }.distinctBy { it.id }.take(15)
        }
    }
    val currentPlayableItems = remember(filteredSections, selectedFilter, playableItems) {
        when (selectedFilter) {
            "Personalized" -> playableItems
            "All" -> (playableItems + filteredSections.flatMap { it.items }.filter { it.isPlayableSearchItem() }).distinctBy { it.id }
            else -> filteredSections.flatMap { it.items }.filter { it.isPlayableSearchItem() }.distinctBy { it.id }
        }
    }
    var tracksToAddToPlaylist by remember { mutableStateOf<List<MusicItem>?>(null) }

    val suggestionsListState = rememberLazyListState()

    // Update query for suggestions
    LaunchedEffect(searchQuery) {
        searchViewModel.query.value = searchQuery
    }

    // Keep suggestions scrolled to top so "Top results" stays below the search box
    LaunchedEffect(searchQuery, topResults, searchSuggestions, searchHistory) {
        suggestionsListState.scrollToItem(0)
    }

    // Auto-exit selection mode if empty
    LaunchedEffect(selectedTracks) {
        if (selectedTracks.isEmpty() && isSelectionMode) {
            isSelectionMode = false
        }
    }

    LaunchedEffect(searchQuery, visibleSections) {
        if (selectedFilter != "Personalized" && selectedFilter != "All" && visibleSections.none { it.type.name == selectedFilter }) {
            selectedFilter = "All"
        }
        if (searchViewModel.submittedQuery != searchQuery) {
            selectedFilter = "All"
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
                            val count = selectedTracks.size
                            playerViewModel.addToQueue(selectedTracks.toList())
                            Toast.makeText(context, "Added $count songs to queue", Toast.LENGTH_SHORT).show()
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
                        Toast.makeText(context, "Downloading ${selectedTracks.size} tracks", Toast.LENGTH_SHORT).show()
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
        Box(modifier = Modifier.weight(1f)) {
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

                    visibleSections.isEmpty() && playableItems.isEmpty() -> {
                        SearchEmptyState(query = searchQuery)
                    }

                    else -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 88.dp)
                        ) {
                            item {
                                SearchResultsHeader(
                                    query = searchQuery,
                                    selectedFilter = selectedFilter,
                                    onSelectFilter = { selectedFilter = it },
                                    sections = visibleSections,
                                    topTracksCount = playableItems.size
                                )
                            }

                            if ((selectedFilter == "All" || selectedFilter == "Personalized") && playableItems.isNotEmpty()) {
                                if (selectedFilter == "All") {
                                    item(key = "header_TOP_TRACKS") {
                                        SearchSectionHeader(
                                            title = "Top Tracks",
                                            count = playableItems.size,
                                            subtitle = "Top matches for your search"
                                        )
                                    }
                                }

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
                                            searchViewModel.saveSearchHistory(
                                                query = musicItem.title,
                                                thumbnailUrl = musicItem.thumbnailUrl,
                                                resultTitle = musicItem.title,
                                                resultArtist = musicItem.artist
                                            )
                                            playerViewModel.playMusic(musicItem)
                                            onNavigateToPlayer()
                                        },
                                        onAddToQueue = {
                                            playerViewModel.addToQueue(musicItem)
                                            Toast.makeText(context, "Added to queue", Toast.LENGTH_SHORT).show()
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
                                                Toast.makeText(context, "Downloading ${musicItem.title}", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        onDownloadForRingtone = {
                                            if (isPlayable) {
                                                playerViewModel.downloadForRingtone(musicItem)
                                            }
                                        },
                                        onShare = {
                                            ShareUtils.shareTrack(context, musicItem)
                                        },
                                        onLike = { playerViewModel.toggleFavorite(musicItem) },
                                        onSave = { playerViewModel.toggleSavedCollection(musicItem) },
                                        showAddToPlaylist = isPlayable,
                                        showGoToArtist = !musicItem.isArtistResult() && musicItem.artistId != null,
                                        showDownload = isPlayable,
                                        showRingtone = isPlayable,
                                        showLike = isPlayable,
                                        showSave = musicItem.toSavedCollectionItemOrNull() != null,
                                        showMoreOptions = false
                                    )
                                }
                            }

                            if (selectedFilter != "Personalized") {
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
                                                selectedTracks = if (selected) selectedTracks + musicItem else selectedTracks - musicItem
                                            },
                                            onLongClick = {
                                                if (isPlayable) { isSelectionMode = true; selectedTracks = selectedTracks + musicItem }
                                            },
                                            onClick = {
                                                when {
                                                    musicItem.isPlaylistResult() -> onNavigateToPlaylist(musicItem.id)
                                                    isArtist -> onNavigateToArtist(musicItem.artistId ?: musicItem.id, musicItem.title)
                                                    else -> {
                                                        searchViewModel.saveSearchHistory(musicItem.title, musicItem.thumbnailUrl, musicItem.title, musicItem.artist)
                                                        playerViewModel.playMusic(musicItem)
                                                        onNavigateToPlayer()
                                                    }
                                                }
                                            },
                                            onAddToQueue = {
                                                if (isPlayable) {
                                                    playerViewModel.addToQueue(musicItem)
                                                    Toast.makeText(context, "Added to queue", Toast.LENGTH_SHORT).show()
                                                }
                                            },
                                            onAddToPlaylist = { showAddToPlaylistItem = musicItem },
                                            onGoToArtist = { musicItem.artistId?.let { onNavigateToArtist(it, musicItem.artist) } },
                                            onDownload = {
                                                if (isPlayable) {
                                                    playerViewModel.downloadTrack(musicItem)
                                                    Toast.makeText(context, "Downloading ${musicItem.title}", Toast.LENGTH_SHORT).show()
                                                }
                                            },
                                            onDownloadForRingtone = { if (isPlayable) playerViewModel.downloadForRingtone(musicItem) },
                                            onShare = { ShareUtils.shareTrack(context, musicItem) },
                                            onLike = { playerViewModel.toggleFavorite(musicItem) },
                                            onSave = { playerViewModel.toggleSavedCollection(musicItem) },
                                            showAddToPlaylist = isPlayable,
                                            showGoToArtist = canOpenArtist,
                                            showDownload = isPlayable,
                                            showRingtone = isPlayable,
                                            showLike = isPlayable,
                                            showSave = musicItem.toSavedCollectionItemOrNull() != null,
                                            showMoreOptions = false
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                SearchSuggestionsAndHistoryList(
                    suggestionsListState = suggestionsListState,
                    normalizedSearchQuery = normalizedSearchQuery,
                    searchHistory = searchHistory,
                    searchSuggestions = searchSuggestions,
                    topResults = topResults,
                    onClearHistory = { searchViewModel.clearSearchHistory() },
                    onDeleteHistory = { searchViewModel.deleteSearchHistory(it) },
                    onSubmitSuggestionOrHistory = { selectedQuery ->
                        searchQuery = selectedQuery
                        searchViewModel.performSearch(selectedQuery)
                        keyboardController?.hide()
                        focusManager.clearFocus()
                    },
                    onPreviewItemClick = { musicItem ->
                        when {
                            musicItem.isPlaylistResult() -> onNavigateToPlaylist(musicItem.id)
                            musicItem.isArtistResult() -> onNavigateToArtist(musicItem.artistId ?: musicItem.id, musicItem.title)
                            else -> {
                                searchViewModel.saveSearchHistory(musicItem.title, musicItem.thumbnailUrl, musicItem.title, musicItem.artist)
                                playerViewModel.playMusic(musicItem)
                                onNavigateToPlayer()
                            }
                        }
                    }
                )
            }
        }
    }

    if (showAddToPlaylistItem != null) {
        com.israrxy.raazi.ui.components.AddToPlaylistDialog(
            viewModel = playerViewModel,
            onDismiss = { showAddToPlaylistItem = null },
            onPlaylistSelected = { playlist ->
                playerViewModel.addToPlaylist(showAddToPlaylistItem!!, playlist)
                Toast.makeText(context, "Added to ${playlist.title}", Toast.LENGTH_SHORT).show()
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
                Toast.makeText(context, "Added to ${playlist.title}", Toast.LENGTH_SHORT).show()
                tracksToAddToPlaylist = null; isSelectionMode = false; selectedTracks = emptySet()
            }
        )
    }
}
