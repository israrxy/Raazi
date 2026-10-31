package com.israrxy.raazi.ui

import android.net.Uri
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.LocalIndication
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import com.israrxy.raazi.ui.theme.Emerald500
import com.israrxy.raazi.utils.ConnectivityObserver
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
import com.israrxy.raazi.ui.ringtone.RingtoneTrimmerScreen
import com.israrxy.raazi.viewmodel.MusicPlayerViewModel
import com.israrxy.raazi.utils.rememberHapticController
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

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
    val isYouTubeLoggedIn by viewModel.isYouTubeLoggedIn.collectAsStateWithLifecycle()
    LaunchedEffect(isYouTubeLoggedIn) {
        if (isYouTubeLoggedIn) viewModel.syncYouTubeLibrary()
    }

    val onRingtoneScreen = currentDestination?.route == "ringtone_trimmer"
    val showBottomBar = currentDestination.isTopLevelDestination() &&
        sheetState.currentValue != SheetValue.Expanded && !onRingtoneScreen
    val showMiniPlayer = currentTrack != null &&
        sheetState.currentValue != SheetValue.Expanded && !onRingtoneScreen
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

    val ringtoneState by viewModel.ringtoneState.collectAsStateWithLifecycle()
    val isOnline by viewModel.isOnline.collectAsStateWithLifecycle()
    val justReconnected by viewModel.justReconnected.collectAsStateWithLifecycle()
    val pendingDeepLinkEvent by viewModel.pendingDeepLinkEvent.collectAsStateWithLifecycle()

    LaunchedEffect(pendingDeepLinkEvent, currentTrack) {
        when (val event = pendingDeepLinkEvent) {
            is MusicPlayerViewModel.DeepLinkEvent.ExpandPlayer -> {
                if (currentTrack != null) {
                    isLyricsModeOpen = false
                    sheetState.expand()
                    viewModel.consumeDeepLinkEvent()
                }
            }
            is MusicPlayerViewModel.DeepLinkEvent.OpenPlaylist -> {
                isLyricsModeOpen = false
                if (sheetState.currentValue == SheetValue.Expanded) {
                    sheetState.partialExpand()
                }
                navController.navigateToPlaylist(event.playlistId)
                viewModel.consumeDeepLinkEvent()
            }
            is MusicPlayerViewModel.DeepLinkEvent.OpenArtist -> {
                isLyricsModeOpen = false
                if (sheetState.currentValue == SheetValue.Expanded) {
                    sheetState.partialExpand()
                }
                val encodedId = Uri.encode(event.artistId)
                val encodedName = Uri.encode(event.artistName ?: event.artistId)
                navController.navigate("artist/$encodedId?name=$encodedName")
                viewModel.consumeDeepLinkEvent()
            }
            null -> Unit
        }
    }

    LaunchedEffect(ringtoneState) {
        val isIdle = ringtoneState is MusicPlayerViewModel.RingtoneState.Idle
        val currentRoute = navController.currentDestination?.route

        if (!isIdle && currentRoute != "ringtone_trimmer") {
            navController.navigate("ringtone_trimmer") {
                launchSingleTop = true
            }
            // Collapse the player sheet so the ringtone screen is fully visible
            scope.launch { sheetState.partialExpand() }
        } else if (isIdle && currentRoute == "ringtone_trimmer") {
            navController.popBackStack()
        }
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
                        .padding(bottom = contentBottomPadding),
                    enterTransition = {
                        val isPeer = initialState.destination.isTopLevelDestination() && targetState.destination.isTopLevelDestination()
                        if (isPeer) {
                            fadeIn(animationSpec = tween(150))
                        } else {
                            slideInHorizontally(
                                initialOffsetX = { (it * 0.08f).toInt() },
                                animationSpec = tween(durationMillis = 250, easing = FastOutSlowInEasing)
                            ) + fadeIn(animationSpec = tween(250))
                        }
                    },
                    exitTransition = {
                        val isPeer = initialState.destination.isTopLevelDestination() && targetState.destination.isTopLevelDestination()
                        if (isPeer) {
                            fadeOut(animationSpec = tween(150))
                        } else {
                            slideOutHorizontally(
                                targetOffsetX = { -(it * 0.08f).toInt() },
                                animationSpec = tween(durationMillis = 250, easing = FastOutSlowInEasing)
                            ) + fadeOut(animationSpec = tween(250))
                        }
                    },
                    popEnterTransition = {
                        val isPeer = initialState.destination.isTopLevelDestination() && targetState.destination.isTopLevelDestination()
                        if (isPeer) {
                            fadeIn(animationSpec = tween(150))
                        } else {
                            slideInHorizontally(
                                initialOffsetX = { -(it * 0.08f).toInt() },
                                animationSpec = tween(durationMillis = 250, easing = FastOutSlowInEasing)
                            ) + fadeIn(animationSpec = tween(250))
                        }
                    },
                    popExitTransition = {
                        val isPeer = initialState.destination.isTopLevelDestination() && targetState.destination.isTopLevelDestination()
                        if (isPeer) {
                            fadeOut(animationSpec = tween(150))
                        } else {
                            slideOutHorizontally(
                                targetOffsetX = { (it * 0.08f).toInt() },
                                animationSpec = tween(durationMillis = 250, easing = FastOutSlowInEasing)
                            ) + fadeOut(animationSpec = tween(250))
                        }
                    }
                ) {
                    composable("home") {
                        HomeScreen(
                            viewModel = viewModel,
                            onNavigateToPlayer = { scope.launch { sheetState.expand() } },
                            onNavigateToPlaylist = { id ->
                                navController.navigateToPlaylist(id)
                            },
                            onNavigateToArtist = { artistId, artistName ->
                                navController.navigate("artist/$artistId?name=${Uri.encode(artistName)}")
                            },
                            onNavigateToSearch = {
                                navController.navigate("search") {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
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
                                navController.navigateToPlaylist(id)
                            }
                        )
                    }
                    composable("library") {
                        LibraryScreen(
                            viewModel = viewModel,
                            onNavigateToPlayer = { scope.launch { sheetState.expand() } },
                            onNavigateToPlaylist = { id ->
                                navController.navigateToPlaylist(id)
                            },
                            onNavigateToArtist = { artistId, artistName ->
                                navController.navigate("artist/$artistId?name=${Uri.encode(artistName)}")
                            },
                            onNavigateToDownloads = { navController.navigate("downloads") },
                            onNavigateToSettings = { navController.navigate("settings") },
                            onNavigateToSpotifyImport = { navController.navigate("spotify_import") }
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
                    composable("ringtone_trimmer") {
                        RingtoneTrimmerScreen(
                            viewModel = viewModel,
                            onClose = { viewModel.resetRingtoneState() }
                        )
                    }
                    composable("spotify_import") {
                        com.israrxy.raazi.ui.playlist.SpotifyImportScreen(
                            viewModel = viewModel,
                            onNavigateBack = { navController.popBackStack() },
                            onNavigateToPlaylist = { id ->
                                val cleanId = cleanPlaylistId(id)
                                navController.navigate("playlist/${Uri.encode(cleanId)}") {
                                    popUpTo("library")
                                }
                            }
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

        ConnectivityStatusBanner(
            isOnline = isOnline,
            justReconnected = justReconnected,
            currentDestination = currentDestination,
            navController = navController,
            modifier = Modifier.align(Alignment.TopCenter)
        )
    }
}

