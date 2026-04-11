package com.israrxy.raazi.ui

import android.net.Uri
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.israrxy.raazi.data.local.SettingsDataStore
import com.israrxy.raazi.ui.downloads.DownloadsScreen
import com.israrxy.raazi.ui.home.HomeScreen
import com.israrxy.raazi.ui.library.LibraryScreen
import com.israrxy.raazi.ui.player.MiniPlayer
import com.israrxy.raazi.viewmodel.MusicPlayerViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private val topLevelDestinations = listOf(
    BottomNavItem("Home", Icons.Default.Home, Icons.Outlined.Home, "home"),
    BottomNavItem("Search", Icons.Default.Search, Icons.Outlined.Search, "search"),
    BottomNavItem("Library", Icons.Default.LibraryMusic, Icons.Outlined.LibraryMusic, "library"),
    BottomNavItem("Audio", Icons.Default.GraphicEq, Icons.Outlined.GraphicEq, "equalizer"),
    BottomNavItem("Settings", Icons.Default.Settings, Icons.Outlined.Settings, "settings")
)

private val bottomBarBaseHeight = 76.dp
private val miniPlayerGap = 10.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MusicPlayerViewModel) {
    val context = LocalContext.current
    val settingsDataStore = remember { SettingsDataStore(context) }
    val navController = rememberNavController()
    val currentTrackFlow = remember(viewModel) {
        viewModel.playbackState
            .map { it.currentTrack }
            .distinctUntilChanged()
    }
    val currentTrack by currentTrackFlow.collectAsStateWithLifecycle(initialValue = null)
    val lastTopLevelRoute by settingsDataStore.lastTopLevelRoute.collectAsStateWithLifecycle(initialValue = "home")
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val sheetState = rememberStandardBottomSheetState(initialValue = SheetValue.PartiallyExpanded)
    val scaffoldState = rememberBottomSheetScaffoldState(bottomSheetState = sheetState)
    val scope = rememberCoroutineScope()
    var restoredTopLevelRoute by rememberSaveable { mutableStateOf(false) }
    var isLyricsModeOpen by rememberSaveable { mutableStateOf(false) }

    val showBottomBar = currentDestination.isTopLevelDestination() &&
        sheetState.currentValue != SheetValue.Expanded
    val showMiniPlayer = currentTrack != null &&
        sheetState.currentValue != SheetValue.Expanded
    val navigationBarInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val collapsedMiniPlayerBottomPadding =
        if (showBottomBar) {
            navigationBarInset + bottomBarBaseHeight + miniPlayerGap
        } else {
            navigationBarInset + 12.dp
        }
    val contentBottomPadding =
        if (showBottomBar) {
            navigationBarInset + bottomBarBaseHeight
        } else {
            0.dp
        }
    val sheetPeekHeight = 0.dp

    val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    DisposableEffect(sheetState.currentValue, backDispatcher, isLyricsModeOpen) {
        val callback = object : OnBackPressedCallback(sheetState.currentValue == SheetValue.Expanded) {
            override fun handleOnBackPressed() {
                if (isLyricsModeOpen) {
                    isLyricsModeOpen = false
                } else {
                    scope.launch { sheetState.partialExpand() }
                }
            }
        }
        backDispatcher?.addCallback(callback)
        onDispose { callback.remove() }
    }

    LaunchedEffect(currentTrack) {
        if (currentTrack == null) {
            isLyricsModeOpen = false
        }
    }

    LaunchedEffect(sheetState.currentValue) {
        if (sheetState.currentValue != SheetValue.Expanded && isLyricsModeOpen) {
            isLyricsModeOpen = false
        }
    }

    LaunchedEffect(lastTopLevelRoute, currentDestination) {
        if (restoredTopLevelRoute || currentDestination == null) return@LaunchedEffect
        restoredTopLevelRoute = true
        if (lastTopLevelRoute != "home" && currentDestination.matchesRoute("home")) {
            navController.navigate(lastTopLevelRoute) {
                popUpTo(navController.graph.findStartDestination().id) {
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    LaunchedEffect(currentDestination) {
        val selectedTopLevelRoute = topLevelDestinations
            .firstOrNull { currentDestination.matchesRoute(it.route) }
            ?.route
            ?: return@LaunchedEffect
        settingsDataStore.setLastTopLevelRoute(selectedTopLevelRoute)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        BottomSheetScaffold(
            scaffoldState = scaffoldState,
            sheetContent = {
                if (currentTrack != null) {
                    PlayerScreen(
                        viewModel = viewModel,
                        navController = navController,
                        sheetState = sheetState,
                        onCollapse = { scope.launch { sheetState.partialExpand() } },
                        isLyricsVisible = isLyricsModeOpen,
                        onLyricsVisibilityChange = { isLyricsModeOpen = it }
                    )
                } else {
                    Box(modifier = Modifier.height(0.dp))
                }
            },
            sheetPeekHeight = sheetPeekHeight,
            sheetContainerColor = Color.Transparent,
            sheetContentColor = MaterialTheme.colorScheme.onBackground,
            sheetTonalElevation = 0.dp,
            sheetShadowElevation = 0.dp,
            sheetDragHandle = null,
            sheetSwipeEnabled = currentTrack != null && !isLyricsModeOpen
        ) {
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                containerColor = Color.Transparent,
                contentWindowInsets = WindowInsets(0, 0, 0, 0)
            ) { innerPadding ->
                NavHost(
                    navController = navController,
                    startDestination = "home",
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(bottom = contentBottomPadding)
                ) {
                    composable("home") {
                        HomeScreen(
                            viewModel = viewModel,
                            onNavigateToPlayer = { scope.launch { sheetState.expand() } },
                            onNavigateToPlaylist = { id ->
                                val cleanId = id.replace("https://www.youtube.com/playlist?list=", "")
                                navController.navigate("playlist/$cleanId")
                            },
                            onNavigateToArtist = { artistId, artistName ->
                                navController.navigate("artist/$artistId?name=${Uri.encode(artistName)}")
                            }
                        )
                    }
                    composable("search") {
                        SearchScreen(
                            playerViewModel = viewModel,
                            onNavigateToPlayer = { scope.launch { sheetState.expand() } },
                            onNavigateToArtist = { artistId, artistName ->
                                navController.navigate("artist/$artistId?name=${Uri.encode(artistName)}")
                            },
                            onNavigateToPlaylist = { id ->
                                val cleanId = id.replace("https://www.youtube.com/playlist?list=", "")
                                navController.navigate("playlist/$cleanId")
                            }
                        )
                    }
                    composable("library") {
                        LibraryScreen(
                            viewModel = viewModel,
                            onNavigateToPlayer = { scope.launch { sheetState.expand() } },
                            onNavigateToPlaylist = { id ->
                                navController.navigate("playlist/$id")
                            },
                            onNavigateToArtist = { artistId, artistName ->
                                navController.navigate("artist/$artistId?name=${Uri.encode(artistName)}")
                            },
                            onNavigateToDownloads = { navController.navigate("downloads") },
                            onNavigateToSettings = { navController.navigate("settings") }
                        )
                    }
                    composable("settings") {
                        SettingsScreen(
                            viewModel = viewModel,
                            onNavigateToYouTubeLogin = { navController.navigate("youtube_login") }
                        )
                    }
                    composable("youtube_login") {
                        YouTubeLoginScreen(
                            onNavigateBack = { navController.popBackStack() }
                        )
                    }
                    composable("equalizer") {
                        EnhancedEqualizerScreen(viewModel = viewModel)
                    }
                    composable("playlist/{playlistId}") { backStackEntry ->
                        val playlistId = backStackEntry.arguments?.getString("playlistId") ?: ""
                        com.israrxy.raazi.ui.playlist.PlaylistDetailScreen(
                            playlistId = playlistId,
                            viewModel = viewModel,
                            onNavigateBack = { navController.popBackStack() },
                            onNavigateToPlayer = { scope.launch { sheetState.expand() } }
                        )
                    }
                    composable("artist/{artistId}?name={artistName}") { backStackEntry ->
                        val artistId = backStackEntry.arguments?.getString("artistId") ?: ""
                        val artistName = backStackEntry.arguments?.getString("artistName")
                        ArtistScreen(
                            artistId = artistId,
                            artistName = artistName,
                            viewModel = viewModel,
                            onNavigateBack = { navController.popBackStack() }
                        )
                    }
                    composable("downloads") {
                        DownloadsScreen(
                            viewModel = viewModel,
                            onBack = { navController.popBackStack() },
                            onNavigateToPlayer = { scope.launch { sheetState.expand() } }
                        )
                    }
                }
            }
        }

        if (showMiniPlayer) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 16.dp)
                    .padding(bottom = collapsedMiniPlayerBottomPadding)
            ) {
                MiniPlayer(
                    viewModel = viewModel,
                    onNavigateToPlayer = { scope.launch { sheetState.expand() } },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        if (showBottomBar) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
            ) {
                SimpleBottomNavBar(
                    navController = navController,
                    currentDestination = currentDestination
                )
            }
        }
    }
}

@Composable
private fun SimpleBottomNavBar(
    navController: NavHostController,
    currentDestination: NavDestination?
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
            tonalElevation = 10.dp,
            shadowElevation = 18.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                topLevelDestinations.forEach { item ->
                    val isSelected = currentDestination.matchesRoute(item.route)
                    val itemWeight by animateFloatAsState(
                        targetValue = if (isSelected) 1.45f else 0.9f,
                        label = "bottom_nav_weight"
                    )
                    val containerColor by animateColorAsState(
                        targetValue = if (isSelected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            Color.Transparent
                        },
                        label = "bottom_nav_container"
                    )
                    val contentColor by animateColorAsState(
                        targetValue = if (isSelected) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        label = "bottom_nav_content"
                    )

                    Row(
                        modifier = Modifier
                            .weight(itemWeight, fill = true)
                            .clip(RoundedCornerShape(20.dp))
                            .background(containerColor)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                navController.navigate(item.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                            .animateContentSize()
                            .padding(horizontal = 10.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isSelected) item.selectedIcon else item.unselectedIcon,
                            contentDescription = item.title,
                            tint = contentColor,
                            modifier = Modifier.size(22.dp)
                        )
                        AnimatedVisibility(
                            visible = isSelected,
                            enter = fadeIn() + slideInHorizontally(initialOffsetX = { it / 3 }),
                            exit = fadeOut() + slideOutHorizontally(targetOffsetX = { it / 3 })
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.padding(start = 8.dp)) {
                                    Text(
                                        text = item.title,
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        color = contentColor,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun NavDestination?.matchesRoute(route: String): Boolean {
    return this?.hierarchy?.any { it.route == route } == true
}

private fun NavDestination?.isTopLevelDestination(): Boolean {
    return topLevelDestinations.any { matchesRoute(it.route) }
}

data class BottomNavItem(
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val route: String
)
