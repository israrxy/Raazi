package com.israrxy.raazi.ui.home

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.israrxy.raazi.model.MusicItem
import com.israrxy.raazi.viewmodel.HomeSectionType
import com.israrxy.raazi.viewmodel.MusicPlayerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: MusicPlayerViewModel,
    onNavigateToPlayer: () -> Unit,
    onNavigateToPlaylist: (String) -> Unit,
    onNavigateToArtist: (String, String) -> Unit,
    onNavigateToSearch: () -> Unit = {}
) {
    val homeFeedState by viewModel.homeFeedState.collectAsState()
    val homePage by viewModel.homePage.collectAsState()
    val isLoadingMore by viewModel.isLoadingMore.collectAsState()
    val selectedInnerTubeChip by viewModel.selectedInnerTubeChip.collectAsState()
    val playbackState by viewModel.playbackState.collectAsState()

    // Home batch-selection state
    var isHomeSelectionMode by remember { mutableStateOf(false) }
    var selectedHomeTracks by remember { mutableStateOf(setOf<MusicItem>()) }
    var bulkAddToPlaylist by remember { mutableStateOf<List<MusicItem>?>(null) }

    val onHomeToggleSelection: (MusicItem) -> Unit = { item ->
        selectedHomeTracks = if (selectedHomeTracks.contains(item)) {
            selectedHomeTracks - item
        } else {
            selectedHomeTracks + item
        }
    }
    val onHomeEnterSelection: (MusicItem) -> Unit = { item ->
        isHomeSelectionMode = true
        selectedHomeTracks = selectedHomeTracks + item
    }
    val exitHomeSelection: () -> Unit = {
        isHomeSelectionMode = false
        selectedHomeTracks = emptySet()
    }

    LaunchedEffect(selectedHomeTracks) {
        if (selectedHomeTracks.isEmpty() && isHomeSelectionMode) {
            isHomeSelectionMode = false
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            AnimatedVisibility(
                visible = isHomeSelectionMode,
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
                        Text(
                            "${selectedHomeTracks.size} selected",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = {
                            if (selectedHomeTracks.isNotEmpty()) {
                                viewModel.playPlaylist(selectedHomeTracks.toList(), 0)
                                exitHomeSelection()
                                onNavigateToPlayer()
                            }
                        }) {
                            Icon(Icons.Filled.PlayArrow, "Play Selected", tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                        IconButton(onClick = {
                            if (selectedHomeTracks.isNotEmpty()) {
                                viewModel.addToQueue(selectedHomeTracks.toList())
                                exitHomeSelection()
                            }
                        }) {
                            Icon(Icons.AutoMirrored.Filled.QueueMusic, "Add to Queue", tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                        IconButton(onClick = {
                            if (selectedHomeTracks.isNotEmpty()) {
                                bulkAddToPlaylist = selectedHomeTracks.toList()
                            }
                        }) {
                            Icon(Icons.AutoMirrored.Filled.PlaylistAdd, "Add to Playlist", tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                        IconButton(onClick = {
                            selectedHomeTracks.forEach { viewModel.downloadTrack(it) }
                            exitHomeSelection()
                        }) {
                            Icon(Icons.Default.Download, "Download", tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                        IconButton(onClick = exitHomeSelection) {
                            Icon(Icons.Default.Close, "Cancel", tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                }
            }

            val allCategoryChips = remember(homePage?.chips) {
                val dynamicChips = homePage?.chips.orEmpty()
                val defaultTitles = listOf("Podcasts", "Party", "Relax", "Workout", "Focus", "Romance", "Sleep")
                val merged = mutableListOf<com.zionhuang.innertube.pages.HomePage.Chip>()
                dynamicChips.forEach { chip ->
                    if (!chip.title.equals("All", ignoreCase = true)) {
                        merged.add(chip)
                    }
                }
                defaultTitles.forEach { title ->
                    if (merged.none { it.title.equals(title, ignoreCase = true) }) {
                        merged.add(
                            com.zionhuang.innertube.pages.HomePage.Chip(
                                title = title,
                                endpoint = null,
                                deselectEndPoint = null
                            )
                        )
                    }
                }
                merged
            }

            if (homeFeedState.isLoading) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 120.dp, top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        HomeStatusHeader(
                            activeCategoryTitle = selectedInnerTubeChip?.title,
                            isRefreshing = true,
                            onRefresh = { viewModel.refreshHomeSection("home_status") }
                        )
                        HomeCategoryFilterBar(
                            chips = allCategoryChips,
                            selectedChip = selectedInnerTubeChip,
                            onSelectChip = { viewModel.selectFilterChip(it) }
                        )
                    }
                    item { com.israrxy.raazi.ui.components.ShimmerSectionHeader() }
                    items(4) { com.israrxy.raazi.ui.components.ShimmerMusicItemCard() }
                    item { com.israrxy.raazi.ui.components.ShimmerSectionHeader() }
                    items(3) { com.israrxy.raazi.ui.components.ShimmerMusicItemCard() }
                }
            } else if (homeFeedState.errorMessage != null && homeFeedState.sections.none { it.type != HomeSectionType.STATUS }) {
                // Error state — failed to load home content
                Column(
                    modifier = Modifier
                        .fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    HomeStatusHeader(
                        activeCategoryTitle = selectedInnerTubeChip?.title,
                        isRefreshing = homeFeedState.isRefreshing,
                        onRefresh = { viewModel.refreshHomeSection("home_status") }
                    )
                    HomeCategoryFilterBar(
                        chips = allCategoryChips,
                        selectedChip = selectedInnerTubeChip,
                        onSelectChip = { viewModel.selectFilterChip(it) }
                    )
                    Spacer(modifier = Modifier.height(56.dp))
                    Icon(
                        imageVector = Icons.Default.CloudOff,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Something went wrong",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = homeFeedState.errorMessage ?: "Failed to load your feed. Check your connection and try again.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = {
                            if (selectedInnerTubeChip != null) {
                                viewModel.selectFilterChip(selectedInnerTubeChip)
                            } else {
                                viewModel.loadHomeContent()
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 48.dp)
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Refresh, null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Retry", fontWeight = FontWeight.SemiBold)
                    }
                }
            } else if (homeFeedState.sections.none { it.type != HomeSectionType.STATUS }) {
                // Empty state — new user with no history or content
                Column(
                    modifier = Modifier
                        .fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    HomeStatusHeader(
                        activeCategoryTitle = selectedInnerTubeChip?.title,
                        isRefreshing = homeFeedState.isRefreshing,
                        onRefresh = { viewModel.refreshHomeSection("home_status") }
                    )
                    HomeCategoryFilterBar(
                        chips = allCategoryChips,
                        selectedChip = selectedInnerTubeChip,
                        onSelectChip = { viewModel.selectFilterChip(it) }
                    )
                    Spacer(modifier = Modifier.height(64.dp))
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Search for a song",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Find your favorite music to get started.\nYour history will appear here.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = onNavigateToSearch,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 32.dp)
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Search, null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Search a Song", fontWeight = FontWeight.SemiBold)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 120.dp, top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    homeFeedState.sections.forEach { section ->
                        when (section.type) {
                            HomeSectionType.STATUS -> item {
                                HomeStatusHeader(
                                    activeCategoryTitle = selectedInnerTubeChip?.title,
                                    isRefreshing = homeFeedState.isRefreshing,
                                    onRefresh = { viewModel.refreshHomeSection(section.id) }
                                )
                                HomeCategoryFilterBar(
                                    chips = allCategoryChips,
                                    selectedChip = selectedInnerTubeChip,
                                    onSelectChip = { viewModel.selectFilterChip(it) }
                                )
                            }

                            HomeSectionType.KEEP_LISTENING -> item {
                                HomeSectionHeader(section = section, onAction = { viewModel.refreshHomeSection(section.id) })
                                KeepListeningPanel(
                                    items = section.musicItems(),
                                    viewModel = viewModel,
                                    onNavigateToPlayer = onNavigateToPlayer,
                                    sectionId = section.id,
                                    sourceType = section.sourceType,
                                    isSelectionMode = isHomeSelectionMode,
                                    selectedTracks = selectedHomeTracks,
                                    onToggleSelection = onHomeToggleSelection,
                                    onEnterSelection = onHomeEnterSelection
                                )
                            }

                            HomeSectionType.QUICK_PICKS -> item {
                                HomeSectionHeader(section = section, onAction = { viewModel.refreshHomeSection(section.id) })
                                QuickPicksPanel(
                                    items = section.musicItems(),
                                    viewModel = viewModel,
                                    onNavigateToPlayer = onNavigateToPlayer,
                                    sectionId = section.id,
                                    sourceType = section.sourceType,
                                    isSelectionMode = isHomeSelectionMode,
                                    selectedTracks = selectedHomeTracks,
                                    onToggleSelection = onHomeToggleSelection,
                                    onEnterSelection = onHomeEnterSelection
                                )
                            }

                            HomeSectionType.FORGOTTEN_FAVORITES -> item {
                                HomeSectionHeader(section = section)
                                HomeMusicRail(
                                    section = section,
                                    viewModel = viewModel,
                                    onNavigateToPlayer = onNavigateToPlayer,
                                    isSelectionMode = isHomeSelectionMode,
                                    selectedTracks = selectedHomeTracks,
                                    onToggleSelection = onHomeToggleSelection,
                                    onEnterSelection = onHomeEnterSelection
                                )
                            }

                            HomeSectionType.MOODS -> item {
                                HomeSectionHeader(section = section)
                                MoodRail(section = section, viewModel = viewModel)
                            }

                            HomeSectionType.MOOD_PLAYLISTS -> item {
                                HomeSectionHeader(section = section)
                                MoodPlaylistRail(
                                    section = section,
                                    onNavigateToPlaylist = onNavigateToPlaylist
                                )
                            }

                            HomeSectionType.RECOMMENDATION,
                            HomeSectionType.NEW_RELEASES,
                            HomeSectionType.YOUTUBE_RAIL -> item {
                                HomeSectionHeader(section = section)
                                HomeFeedRail(
                                    section = section,
                                    viewModel = viewModel,
                                    onNavigateToPlayer = onNavigateToPlayer,
                                    onNavigateToPlaylist = onNavigateToPlaylist,
                                    onNavigateToArtist = onNavigateToArtist,
                                    isSelectionMode = isHomeSelectionMode,
                                    selectedTracks = selectedHomeTracks,
                                    onToggleSelection = onHomeToggleSelection,
                                    onEnterSelection = onHomeEnterSelection
                                )
                            }
                        }
                    }

                    if (homePage?.continuation != null && !isLoadingMore) {
                        item(key = "pagination_${homePage?.continuation}") {
                            LaunchedEffect(homePage?.continuation) {
                                viewModel.loadMoreSections()
                            }
                        }
                    }

                    if (isLoadingMore) {
                        item {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    // Add To Playlist Dialog
    var showAddToPlaylistItem by remember { mutableStateOf<MusicItem?>(null) }

    if (showAddToPlaylistItem != null) {
        com.israrxy.raazi.ui.components.AddToPlaylistDialog(
            viewModel = viewModel,
            onDismiss = { showAddToPlaylistItem = null },
            onPlaylistSelected = { playlist ->
                viewModel.addToPlaylist(playlist.id, showAddToPlaylistItem!!)
                showAddToPlaylistItem = null
            }
        )
    }

    if (bulkAddToPlaylist != null) {
        com.israrxy.raazi.ui.components.AddToPlaylistDialog(
            viewModel = viewModel,
            onDismiss = { bulkAddToPlaylist = null },
            onPlaylistSelected = { playlist ->
                viewModel.addToPlaylist(bulkAddToPlaylist!!, playlist)
                bulkAddToPlaylist = null
                exitHomeSelection()
            }
        )
    }
}
