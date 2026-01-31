package com.israrxy.raazi.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import com.zionhuang.innertube.models.*

@Composable
fun SearchScreen(
    playerViewModel: MusicPlayerViewModel,
    onNavigateToPlayer: () -> Unit,
    onNavigateToArtist: (String, String) -> Unit
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
    
    // Track if showing results
    val showingResults = searchResults != null && searchResults!!.items.isNotEmpty()
    
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
                        // For bulk add to playlist, we'd need a multi-selection dialog
                        // For now just first one
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
        
        // Content: Results OR Suggestions
        if (showingResults) {
            // ONLY SHOW RESULTS
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                items(searchResults!!.items) { item ->
                    when (item) {
                        is SongItem -> {
                            val musicItem = MusicItem(
                                id = item.id ?: return@items,
                                title = item.title ?: "Unknown",
                                artist = item.artists?.joinToString(", ") { it.name ?: "" } ?: "Unknown",
                                duration = (item.duration?.toLong() ?: 0) * 1000,
                                thumbnailUrl = item.thumbnail ?: "",
                                audioUrl = "",
                                videoUrl = item.id ?: "",
                                artistId = item.artists?.firstOrNull()?.id
                            )
                            
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
                                    playerViewModel.playMusic(musicItem)
                                    onNavigateToPlayer()
                                },
                                onAddToPlaylist = { showAddToPlaylistItem = musicItem },
                                onGoToArtist = {
                                    val artist = item.artists?.firstOrNull()
                                    if (artist?.id != null) {
                                        onNavigateToArtist(artist.id!!, artist.name ?: "Unknown")
                                    }
                                },
                                onDownload = { playerViewModel.downloadTrack(musicItem) },
                                onLike = { playerViewModel.toggleFavorite(musicItem) }
                            )
                        }
                        else -> {} // Ignore other types for now
                    }
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
                            }
                        )
                    }
                }
                
                // Empty state
                if (searchQuery.isEmpty() && searchHistory.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillParentMaxHeight(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Default.Search,
                                    null,
                                    modifier = Modifier.size(64.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                                Spacer(Modifier.height(16.dp))
                                Text("Search for music", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }

    // Add To Playlist Dialog
    if (showAddToPlaylistItem != null) {
        com.israrxy.raazi.ui.components.AddToPlaylistDialog(
            viewModel = playerViewModel,
            onDismiss = { showAddToPlaylistItem = null },
            onPlaylistSelected = { playlist ->
                playerViewModel.addToPlaylist(playlist.id, showAddToPlaylistItem!!)
                showAddToPlaylistItem = null
            }
        )
    }
}

@Composable
private fun HistoryItem(query: String, onClick: () -> Unit, onDelete: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.History, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(query, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Close, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun SuggestionItem(text: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(text, color = MaterialTheme.colorScheme.onSurface)
    }
}
