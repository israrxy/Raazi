package com.israrxy.raazi.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.israrxy.raazi.ui.home.HomeScreen
import com.israrxy.raazi.ui.library.LibraryScreen
import com.israrxy.raazi.ui.downloads.DownloadsScreen
import com.israrxy.raazi.ui.player.MiniPlayer
import com.israrxy.raazi.ui.theme.*
import com.israrxy.raazi.viewmodel.MusicPlayerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MusicPlayerViewModel) {
    val navController = rememberNavController()
    val playbackState by viewModel.playbackState.collectAsState()

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    
    val sheetState = rememberStandardBottomSheetState(
        initialValue = SheetValue.PartiallyExpanded
    )
    val scaffoldState = rememberBottomSheetScaffoldState(
        bottomSheetState = sheetState
    )
    val scope = rememberCoroutineScope()

    // Handle back press to collapse sheet
    val backDispatcher = androidx.activity.compose.LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    DisposableEffect(sheetState.currentValue) {
        val callback = object : androidx.activity.OnBackPressedCallback(sheetState.currentValue == SheetValue.Expanded) {
            override fun handleOnBackPressed() {
                scope.launch { sheetState.partialExpand() }
            }
        }
        backDispatcher?.addCallback(callback)
        onDispose { callback.remove() }
    }

    val bottomBarHeight = 80.dp
    val miniPlayerHeight = 72.dp

    val navBarHeight = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val totalPeekHeight = miniPlayerHeight + bottomBarHeight + navBarHeight

    Box(modifier = Modifier.fillMaxSize()) {
        BottomSheetScaffold(
            scaffoldState = scaffoldState,
        sheetContent = {
            if (playbackState.currentTrack != null) {
                PlayerScreen(
                    viewModel = viewModel,
                    navController = navController,
                    sheetState = sheetState,
                    onCollapse = { scope.launch { sheetState.partialExpand() } },
                    onExpand = { scope.launch { sheetState.expand() } }
                )
            } else {
                Box(Modifier.height(0.dp)) // No height placeholder
            }
        },
        // Total peek height includes MiniPlayer + Custom Bottom Bar Height + System Nav Bar Insets
        // Since Nav Bar is overlay, we need to peek enough to show MiniPlayer ABOVE it.
        sheetPeekHeight = if (playbackState.currentTrack != null) (miniPlayerHeight + 86.dp) else 0.dp,
        sheetContainerColor = Color.Transparent,
        sheetContentColor = Color.White,
        sheetTonalElevation = 0.dp,
        sheetShadowElevation = 0.dp,
        sheetDragHandle = null,
        sheetSwipeEnabled = playbackState.currentTrack != null
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                // Fade out bottom bar as sheet expands
                // Let's use currentValue to hide it for now
                if (sheetState.currentValue != SheetValue.Expanded) {
                    // SimpleBottomNavBar(navController) // Moved to content for transparency
                }
            }
        ) { innerPadding ->
            val paddingBottom = if (playbackState.currentTrack != null) miniPlayerHeight else 0.dp
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                NavHost(
                    navController = navController,
                    startDestination = "home",
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = paddingBottom) // Apply padding ONLY to content
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
                                navController.navigate("artist/$artistId?name=${android.net.Uri.encode(artistName)}")
                            }
                        )
                    }
                    composable("search") {
                        SearchScreen(
                            playerViewModel = viewModel,
                            onNavigateToPlayer = { scope.launch { sheetState.expand() } },
                            onNavigateToArtist = { artistId, artistName ->
                                 navController.navigate("artist/$artistId?name=${android.net.Uri.encode(artistName)}")
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
                            onNavigateToDownloads = {
                                navController.navigate("downloads")
                            }
                        )
                    }
                    composable("settings") {
                        SettingsScreen()
                    }
                    composable("equalizer") {
                        com.israrxy.raazi.ui.EnhancedEqualizerScreen(viewModel = viewModel)
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
    }

    // Floating Nav Bar Overlay - Moved OUTSIDE BottomSheetScaffold
    if (sheetState.currentValue != SheetValue.Expanded) {
        Box(modifier = Modifier.align(Alignment.BottomCenter)) {
             SimpleBottomNavBar(navController)
        }
    }
}
}

@Composable
private fun SimpleBottomNavBar(navController: NavHostController) {


    val items = listOf(
        BottomNavItem("Home", Icons.Default.Home, Icons.Outlined.Home, "home"),
        BottomNavItem("Search", Icons.Default.Search, Icons.Outlined.Search, "search"),
        BottomNavItem("Library", Icons.Default.LibraryMusic, Icons.Outlined.LibraryMusic, "library"),
        BottomNavItem("Audio", Icons.Default.GraphicEq, Icons.Outlined.GraphicEq, "equalizer"),
        BottomNavItem("Settings", Icons.Default.Settings, Icons.Outlined.Settings, "settings")
    )

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // Sonic Glass Navigation Bar
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 0.dp), // Edge to edge
        contentAlignment = Alignment.BottomCenter
    ) {
        // Glass Panel
        com.israrxy.raazi.ui.components.GlassBox(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 0.dp)
                //.navigationBarsPadding() // Removed internal padding to let it sit flush if needed, or handle externally
                .windowInsetsPadding(WindowInsets.navigationBars) 
                .height(86.dp)
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)), // Solid background
             shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            // Remove transparency from GlassBox call but structure remains
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                items.forEach { item ->
                    val isSelected = currentRoute == item.route
                    val color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clickable(
                                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                indication = null // No ripple for cleaner look
                            ) {
                                navController.navigate(item.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                            .padding(8.dp)
                    ) {
                        Icon(
                            imageVector = if (isSelected) item.selectedIcon else item.unselectedIcon,
                            contentDescription = item.title,
                            tint = color,
                            modifier = Modifier.size(26.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.labelSmall,
                            color = color,
                            fontWeight = if (isSelected) androidx.compose.ui.text.font.FontWeight.Medium else androidx.compose.ui.text.font.FontWeight.Normal,
                            fontSize = 10.sp
                        )
                    }
                }
            }
        }
    }
}

data class BottomNavItem(
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val route: String
)