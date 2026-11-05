package com.israrxy.raazi.ui

import android.net.Uri
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.israrxy.raazi.data.local.SettingsDataStore
import com.israrxy.raazi.ui.player.FloatingMiniPlayer
import com.israrxy.raazi.ui.player.MiniPlayer
import com.israrxy.raazi.ui.player.PlayerTransition
import com.israrxy.raazi.ui.player.RevealCardShape
import com.israrxy.raazi.ui.player.lerpFloat
import com.israrxy.raazi.viewmodel.MusicPlayerViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

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

    val playerTransition = remember { PlayerTransition() }
    val playerExpansion = playerTransition.expansion
    val sheetSpringSpec = remember {
        spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 550f)
    }

    val scope = rememberCoroutineScope()
    var restoredTopLevelRoute by rememberSaveable { mutableStateOf(false) }
    var isLyricsModeOpen by rememberSaveable { mutableStateOf(false) }
    val defaultPlayerScreen by settingsDataStore.defaultPlayerScreen.collectAsStateWithLifecycle(initialValue = "LAST_USED")

    val expandPlayer: () -> Unit = {
        playerTransition.captureOrigin()
        if (defaultPlayerScreen == "LYRICS") {
            isLyricsModeOpen = true
        } else if (defaultPlayerScreen == "PLAYER") {
            isLyricsModeOpen = false
        }
        scope.launch { playerExpansion.animateTo(1f, sheetSpringSpec) }
    }

    val collapsePlayer: () -> Unit = {
        scope.launch {
            playerExpansion.animateTo(0f, sheetSpringSpec)
        }
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
    val onPlaylistScreen = currentDestination?.route?.startsWith("playlist") == true
    val showBottomBar = (currentDestination.isTopLevelDestination() || onPlaylistScreen) && !onRingtoneScreen
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

    BackHandler(enabled = playerExpansion.value > 0.001f) {
        if (isLyricsModeOpen) {
            isLyricsModeOpen = false
        } else {
            collapsePlayer()
        }
    }

    LaunchedEffect(currentTrack) {
        if (currentTrack == null) {
            isFloatingCollapsed = false
            collapsePlayer()
        }
    }

    LaunchedEffect(playerExpansion.value) {
        if (playerExpansion.value <= 0.001f) {
            playerTransition.clearOrigin()
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
                    expandPlayer()
                    viewModel.consumeDeepLinkEvent()
                }
            }
            is MusicPlayerViewModel.DeepLinkEvent.OpenPlaylist -> {
                collapsePlayer()
                navController.navigateToPlaylist(event.playlistId)
                viewModel.consumeDeepLinkEvent()
            }
            is MusicPlayerViewModel.DeepLinkEvent.OpenArtist -> {
                collapsePlayer()
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
            collapsePlayer()
        } else if (isIdle && currentRoute == "ringtone_trimmer") {
            navController.popBackStack()
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        val parentHeightPx = constraints.maxHeight.toFloat()
        val effectiveHeight = parentHeightPx.coerceAtLeast(1f)
        var miniPlayerDownwardDelta by remember { mutableFloatStateOf(0f) }
        var isDraggingPlayer by remember { mutableStateOf(false) }
        var dragProgress by remember { mutableFloatStateOf(0f) }
        var dragJob by remember { mutableStateOf<Job?>(null) }

        val dismissModifier = Modifier.pointerInput(parentHeightPx) {
            var gestureStartMs = 0L
            var totalDragPx = 0f
            var dismissDragJob: Job? = null
            detectVerticalDragGestures(
                onDragStart = {
                    gestureStartMs = SystemClock.uptimeMillis()
                    totalDragPx = 0f
                    dragProgress = playerExpansion.value
                },
                onDragEnd = {
                    dismissDragJob?.cancel()
                    dismissDragJob = null
                    val elapsedMs = (SystemClock.uptimeMillis() - gestureStartMs).coerceAtLeast(1L)
                    val velocity = totalDragPx / (elapsedMs / 1000f)
                    if (dragProgress < 0.70f || velocity > 500f) {
                        collapsePlayer()
                    } else {
                        expandPlayer()
                    }
                },
                onDragCancel = {
                    dismissDragJob?.cancel()
                    dismissDragJob = null
                    expandPlayer()
                },
                onVerticalDrag = { change, dragAmount ->
                    change.consume()
                    totalDragPx += dragAmount
                    dragProgress = (dragProgress - dragAmount / effectiveHeight).coerceIn(0f, 1f)
                    dismissDragJob?.cancel()
                    dismissDragJob = scope.launch {
                        playerExpansion.snapTo(dragProgress)
                    }
                }
            )
        }

        // Background App Surface (scaled back & dimmed as player expands)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val progress = playerExpansion.value
                    val scale = 1f - (0.045f * progress)
                    scaleX = scale
                    scaleY = scale
                    clip = progress > 0.01f
                    shape = RoundedCornerShape((20 * progress).dp)
                }
        ) {
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                containerColor = Color.Transparent,
                contentWindowInsets = WindowInsets(0, 0, 0, 0)
            ) { innerPadding ->
                MainNavHost(
                    navController = navController,
                    viewModel = viewModel,
                    openPlayerSheet = expandPlayer,
                    contentBottomPadding = contentBottomPadding,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                )
            }

            if (playerExpansion.value > 0.005f) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.45f * playerExpansion.value))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = collapsePlayer
                        )
                )
            }
        }

        // Bottom Navigation Bar (sinks down and fades out)
        if (showBottomBar) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .graphicsLayer {
                        val progress = playerExpansion.value
                        translationY = 80.dp.toPx() * progress
                        alpha = (1f - progress * 1.5f).coerceIn(0f, 1f)
                    }
            ) {
                SimpleBottomNavBar(
                    navController = navController,
                    currentDestination = currentDestination,
                    hasMiniPlayerAbove = showMiniPlayerBar
                )
            }
        }

        // MiniPlayer (with interactive Drag-Up gesture)
        if (showMiniPlayerBar && playerExpansion.value < 0.99f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = if (showBottomBar) 0.dp else 12.dp)
                    .padding(bottom = collapsedMiniPlayerBottomPadding)
                    .graphicsLayer {
                        val progress = playerExpansion.value
                        val originTop = playerTransition.origin?.player?.top ?: defaultCardTop
                        translationY = -originTop * progress
                        alpha = (1f - playerTransition.stage(0.01f, 0.08f)).coerceIn(0f, 1f)
                    }
                    .draggable(
                        state = rememberDraggableState { delta ->
                            if (!isDraggingPlayer) {
                                isDraggingPlayer = true
                                dragProgress = playerExpansion.value
                                if (playerExpansion.value <= 0.001f) {
                                    playerTransition.captureOrigin()
                                }
                            }
                            miniPlayerDownwardDelta += delta
                            dragProgress = (dragProgress - delta / effectiveHeight).coerceIn(0f, 1f)
                            dragJob?.cancel()
                            dragJob = scope.launch {
                                playerExpansion.snapTo(dragProgress)
                            }
                        },
                        orientation = Orientation.Vertical,
                        onDragStarted = {
                            isDraggingPlayer = true
                            dragProgress = playerExpansion.value
                            if (playerExpansion.value <= 0.001f) {
                                playerTransition.captureOrigin()
                            }
                        },
                        onDragStopped = { velocity ->
                            isDraggingPlayer = false
                            dragJob?.cancel()
                            dragJob = null
                            if (miniPlayerDownwardDelta > 36f && microPlayerEnabled && playerExpansion.value < 0.05f) {
                                isFloatingCollapsed = true
                            } else if (velocity < -500f || dragProgress > 0.25f) {
                                expandPlayer()
                            } else {
                                collapsePlayer()
                            }
                            miniPlayerDownwardDelta = 0f
                        }
                    )
            ) {
                MiniPlayer(
                    viewModel = viewModel,
                    onNavigateToPlayer = expandPlayer,
                    onCollapseToFloating = if (microPlayerEnabled) { { isFloatingCollapsed = true } } else null,
                    isDocked = showBottomBar,
                    modifier = Modifier.fillMaxWidth(),
                    transition = playerTransition
                )
            }
        }

        // Full-Screen Player Screen Overlay (with interactive morph and flight)
        val density = LocalDensity.current
        val miniHeightPx = with(density) { 68.dp.toPx() }
        val miniSidePx = with(density) { if (showBottomBar) 0f else 12.dp.toPx() }
        val miniCornerPx = with(density) { if (showBottomBar) 0f else 16.dp.toPx() }
        val cornerTarget = with(density) { 24.dp.toPx() }
        val cardShadowPx = with(density) { 18.dp.toPx() }
        val defaultCardTop = parentHeightPx - with(density) { collapsedMiniPlayerBottomPadding.toPx() } - miniHeightPx

        val cardTop: () -> Float = {
            lerpFloat(
                playerTransition.origin?.player?.top ?: playerTransition.dockPlayer?.top ?: defaultCardTop,
                0f,
                playerExpansion.value
            )
        }
        val revealShapeA = remember { RevealCardShape() }
        val revealShapeB = remember { RevealCardShape() }
        val shapeToggle = remember { booleanArrayOf(false) }

        if (currentTrack != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .offset {
                        if (playerExpansion.value <= 0.001f) {
                            IntOffset(0, parentHeightPx.roundToInt() + 100)
                        } else {
                            IntOffset.Zero
                        }
                    }
                    .onGloballyPositioned { coordinates ->
                        playerTransition.cardCoordinates = coordinates
                    }
                    .graphicsLayer {
                        val progress = playerExpansion.value
                        val origin = playerTransition.origin?.player ?: playerTransition.dockPlayer
                        val originHeight = origin?.height ?: miniHeightPx
                        val originCorner = if (origin != null) originHeight / 2f else miniCornerPx
                        val side = lerpFloat(origin?.left ?: miniSidePx, 0f, progress)
                        val right = lerpFloat(origin?.let { size.width - it.right } ?: miniSidePx, 0f, progress)
                        val cardHeight = lerpFloat(originHeight, size.height, progress)
                        translationY = cardTop()
                        val currentShape = if (shapeToggle[0]) revealShapeA else revealShapeB
                        shapeToggle[0] = !shapeToggle[0]
                        shape = currentShape.update(side, cardHeight, lerpFloat(originCorner, cornerTarget, progress), right)
                        clip = true
                        shadowElevation = if (progress > 0.001f && progress < 0.999f) cardShadowPx else 0f
                    }
            ) {
                PlayerScreen(
                    viewModel = viewModel,
                    navController = navController,
                    onCollapse = collapsePlayer,
                    isLyricsVisible = isLyricsModeOpen,
                    onLyricsVisibilityChange = { isLyricsModeOpen = it },
                    dismissModifier = dismissModifier,
                    transition = playerTransition,
                    cardTop = cardTop
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
