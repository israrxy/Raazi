package com.israrxy.raazi.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.israrxy.raazi.model.MusicItem
import com.israrxy.raazi.ui.theme.*
import com.israrxy.raazi.utils.ThumbnailUtils
import com.israrxy.raazi.viewmodel.MusicPlayerViewModel
import com.israrxy.raazi.viewmodel.SearchViewModel
import com.israrxy.raazi.data.db.PlaylistEntity
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
    val searchSuggestions by searchViewModel.searchSuggestions.collectAsState()
    val searchHistory by searchViewModel.searchHistory.collectAsState()
    
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
    val showingResults = searchResults != null && 
                         searchResults!!.items.isNotEmpty() && 
                         searchViewModel.submittedQuery == searchQuery
    
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
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        // Bulk Actions Bar
        if (isSelectionMode) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                tonalElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { 
                        isSelectionMode = false
                        selectedTracks = emptySet()
                    }) {
                        Icon(Icons.Default.Close, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                    Text(
                        "${selectedTracks.size} selected",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { 
                        selectedTracks.forEach { playerViewModel.downloadTrack(it) }
                        isSelectionMode = false
                        selectedTracks = emptySet()
                    }) {
                        Icon(Icons.Default.Download, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                    IconButton(onClick = { 
                        if (selectedTracks.isNotEmpty()) {
                            showAddToPlaylistItem = selectedTracks.first()
                        }
                    }) {
                        Icon(Icons.Default.PlaylistAdd, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
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
                Text("Search songs, artists...", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            // ONLY SHOW RESULTS
            val searchItems = remember(searchResults) {
                searchResults?.items ?: emptyList()
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                itemsIndexed(searchItems) { index, musicItem ->
                    val isArtist = musicItem.artist == "Artist"
                    
                    com.israrxy.raazi.ui.components.SongListItem(
                        song = musicItem,
                        isSelected = selectedTracks.contains(musicItem),
                        isSelectionMode = isSelectionMode,
                        onSelectionChange = { selected ->
                            if (selected) {
                                selectedTracks = selectedTracks + musicItem
                            } else {
                                selectedTracks = selectedTracks - musicItem
                            }
                        },
                        onLongClick = {
                            isSelectionMode = true
                            selectedTracks = selectedTracks + musicItem
                        },
                        onClick = {
                            if (musicItem.isPlaylist) {
                                onNavigateToPlaylist(musicItem.id)
                            } else if (isArtist && musicItem.artistId != null) {
                                onNavigateToArtist(musicItem.artistId!!, musicItem.title)
                            } else {
                                val playableItems = searchItems.filter { !it.isPlaylist && it.artist != "Artist" }
                                val playableIndex = playableItems.indexOf(musicItem)
                                if (playableIndex != -1) {
                                     playerViewModel.playPlaylist(playableItems, playableIndex)
                                     onNavigateToPlayer()
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
                            if (!musicItem.isPlaylist && !isArtist) {
                                playerViewModel.downloadTrack(musicItem) 
                            }
                        },
                        onLike = { playerViewModel.toggleFavorite(musicItem) }
                    )
                }
            }
        } else {
            // SHOW SUGGESTIONS & HISTORY
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
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
            }
        }
    }
    
    // Add Playlist Dialog
    if (showAddToPlaylistItem != null) {
        com.israrxy.raazi.ui.components.AddToPlaylistDialog(
            viewModel = playerViewModel,
            onDismiss = { showAddToPlaylistItem = null },
            onPlaylistSelected = { playlist ->
                // Assuming addToPlaylist exists in viewModel and takes MusicItem, PlaylistEntity
                playerViewModel.addToPlaylist(showAddToPlaylistItem!!, playlist)
            }
        )
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
