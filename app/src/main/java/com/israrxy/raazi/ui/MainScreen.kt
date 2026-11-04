package com.israrxy.raazi.ui

import android.net.Uri
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.israrxy.raazi.data.local.SettingsDataStore
import com.israrxy.raazi.ui.player.FloatingMiniPlayer
import com.israrxy.raazi.ui.player.MiniPlayer
import com.israrxy.raazi.viewmodel.MusicPlayerViewModel
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

    var isPlayerExpanded by rememberSaveable { mutableStateOf(false) }
    var restoredTopLevelRoute by rememberSaveable { mutableStateOf(false) }
    var isLyricsModeOpen by rememberSaveable { mutableStateOf(false) }
    val defaultPlayerScreen by settingsDataStore.defaultPlayerScreen.collectAsStateWithLifecycle(initialValue = "LAST_USED")

    val openPlayerSheet: () -> Unit = {
        if (defaultPlayerScreen == "LYRICS") {
            isLyricsModeOpen = true
        } else if (defaultPlayerScreen == "PLAYER") {
            isLyricsModeOpen = false
        }
        isPlayerExpanded = true
    }

    val isYouTubeLoggedIn by viewModel.isYouTubeLoggedIn.collectAsStateWithLifecycle()
    LaunchedEffect(isYouTubeLoggedIn) {
        if (isYouTubeLoggedIn) viewModel.syncYouTubeLibrary()
    }

    var isFloatingCollapsed by rememberSaveable { mutableStateOf(false) }
    val isSelectionModeActive by viewModel.isSelectionModeActive.collectAsStateWithLifecycle()
    val microPlayerEnabled by settingsDataStore.microPlayerEnabled.collectAsStateWithLifecycle(initialValue = true)
    val onRingtoneScreen = currentDestination?.route == "ringtone_trimmer"
    val onSettingsScreen = currentDestination?.route == "settings" || currentDestination?.route?.startsWith("settings") == true
    val showBottomBar = currentDestination.isTopLevelDestination() && !onRingtoneScreen
    val isMiniPlayerSuppressed = onSettingsScreen || isSelectionModeActive || onRingtoneScreen
    val showMiniPlayerBar = currentTrack != null && (!isFloatingCollapsed || !microPlayerEnabled) && !isMiniPlayerSuppressed
    val showFloatingBubble = currentTrack != null && isFloatingCollapsed && microPlayerEnabled && !isMiniPlayerSuppressed
    val navigationBarInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val collapsedMiniPlayerBottomPadding =
        if (showBottomBar) {
            navigationBarInset + bottomBarBaseHeight
        } else {
            navigationBarInset + 8.dp
        }
    val contentBottomPadding =
        if (showBottomBar) {
            navigationBarInset + bottomBarBaseHeight + (if (showMiniPlayerBar) 66.dp else 0.dp)
        } else {
            if (showMiniPlayerBar) 74.dp else 0.dp
        }

    val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    DisposableEffect(isPlayerExpanded, backDispatcher) {
        val callback = object : OnBackPressedCallback(isPlayerExpanded) {
            override fun handleOnBackPressed() {
                isPlayerExpanded = false
            }
        }
        backDispatcher?.addCallback(callback)
        onDispose { callback.remove() }
    }

    LaunchedEffect(currentTrack) {
        if (currentTrack == null) {
            isLyricsModeOpen = false
            isFloatingCollapsed = false
            isPlayerExpanded = false
        }
    }

    LaunchedEffect(lastTopLevelRoute, currentDestination) {
        if (restoredTopLevelRoute || currentDestination == null) return@LaunchedEffect
        restoredTopLevelRoute = true
        if (lastTopLevelRoute != "home" && currentDestination.matchesRoute("home")) {
            navController.navigate(lastTopLevelRoute) {
                popUpTo(navController.graph.findStartDestination().id)
                launchSingleTop = true
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
                    if (defaultPlayerScreen == "LYRICS") {
                        isLyricsModeOpen = true
                    } else if (defaultPlayerScreen == "PLAYER") {
                        isLyricsModeOpen = false
                    }
                    isPlayerExpanded = true
                    viewModel.consumeDeepLinkEvent()
                }
            }
            is MusicPlayerViewModel.DeepLinkEvent.OpenPlaylist -> {
                isPlayerExpanded = false
                navController.navigateToPlaylist(event.playlistId)
                viewModel.consumeDeepLinkEvent()
            }
            is MusicPlayerViewModel.DeepLinkEvent.OpenArtist -> {
                isPlayerExpanded = false
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
            isPlayerExpanded = false
        } else if (isIdle && currentRoute == "ringtone_trimmer") {
            navController.popBackStack()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0, 0, 0, 0)
        ) { innerPadding ->
            MainNavHost(
                navController = navController,
                viewModel = viewModel,
                openPlayerSheet = openPlayerSheet,
                contentBottomPadding = contentBottomPadding,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            )
        }

        if (showBottomBar) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
            ) {
                SimpleBottomNavBar(
                    navController = navController,
                    currentDestination = currentDestination,
                    hasMiniPlayerAbove = showMiniPlayerBar
                )
            }
        }

        if (showMiniPlayerBar) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = if (showBottomBar) 0.dp else 12.dp)
                    .padding(bottom = collapsedMiniPlayerBottomPadding)
            ) {
                MiniPlayer(
                    viewModel = viewModel,
                    onNavigateToPlayer = openPlayerSheet,
                    onCollapseToFloating = if (microPlayerEnabled) { { isFloatingCollapsed = true } } else null,
                    isDocked = showBottomBar,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        AnimatedVisibility(
            visible = isPlayerExpanded && currentTrack != null,
            enter = slideInVertically(
                initialOffsetY = { fullHeight -> fullHeight },
                animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing)
            ) + fadeIn(animationSpec = tween(durationMillis = 200)),
            exit = slideOutVertically(
                targetOffsetY = { fullHeight -> fullHeight },
                animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing)
            ) + fadeOut(animationSpec = tween(durationMillis = 180)),
            modifier = Modifier.fillMaxSize()
        ) {
            val nestedScrollConnection = remember {
                var accumulatedDragY = 0f
                object : NestedScrollConnection {
                    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                        if (available.y < 0f) {
                            accumulatedDragY = 0f
                        }
                        return Offset.Zero
                    }

                    override fun onPostScroll(
                        consumed: Offset,
                        available: Offset,
                        source: NestedScrollSource
                    ): Offset {
                        if (available.y > 0f) {
                            accumulatedDragY += available.y
                            if (accumulatedDragY > 50f) {
                                accumulatedDragY = 0f
                                isPlayerExpanded = false
                            }
                            return Offset(0f, available.y)
                        }
                        return Offset.Zero
                    }

                    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                        accumulatedDragY = 0f
                        if (available.y > 450f) {
                            isPlayerExpanded = false
                            return available
                        }
                        return Velocity.Zero
                    }
                }
            }

            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(nestedScrollConnection),
                color = MaterialTheme.colorScheme.background
            ) {
                PlayerScreen(
                    viewModel = viewModel,
                    navController = navController,
                    onCollapse = { isPlayerExpanded = false },
                    isLyricsVisible = isLyricsModeOpen,
                    onLyricsVisibilityChange = { isLyricsModeOpen = it }
                )
            }
        }

        if (showFloatingBubble) {
            FloatingMiniPlayer(
                viewModel = viewModel,
                onExpand = { isFloatingCollapsed = false }
            )
        }

        ConnectivityStatusBanner(
            isOnline = isOnline,
            justReconnected = justReconnected,
            currentDestination = currentDestination,
            navController = navController
        )
    }
}
