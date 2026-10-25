package com.israrxy.raazi.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.israrxy.raazi.data.lyrics.LyricsMetadataSanitizer
import com.israrxy.raazi.data.lyrics.LyricsScriptDetector
import com.israrxy.raazi.data.lyrics.LyricsScriptFilter
import com.israrxy.raazi.data.lyrics.LyricsTextVariants
import com.israrxy.raazi.data.lyrics.LyricsViewVariant
import com.israrxy.raazi.ui.theme.*
import com.israrxy.raazi.utils.ThumbnailUtils
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.togetherWith
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.tween
import com.israrxy.raazi.viewmodel.MusicPlayerViewModel
import com.israrxy.raazi.ui.components.shimmerEffect
import com.israrxy.raazi.ui.components.SongListItem
import com.israrxy.raazi.ui.components.QueueBottomSheet
import com.israrxy.raazi.ui.components.PlayerOptionsSheet
import com.israrxy.raazi.ui.components.SongCreditsBottomSheet
import com.israrxy.raazi.data.local.SettingsDataStore
import com.israrxy.raazi.service.LyricsTranslator
import android.net.Uri
import com.israrxy.raazi.model.MusicContentType
import com.israrxy.raazi.model.MusicItem
import com.israrxy.raazi.model.PlaybackMediaMode
import com.israrxy.raazi.model.savedCollectionId

import androidx.compose.material.icons.filled.Download // Import
import androidx.compose.material.icons.filled.PlaylistAdd // Import
import androidx.compose.material.icons.filled.Person // Import
import androidx.compose.ui.draw.blur // Import for blur effect
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import android.widget.Toast
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import androidx.lifecycle.compose.collectAsStateWithLifecycle


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    viewModel: MusicPlayerViewModel,
    navController: androidx.navigation.NavController,
    sheetState: SheetState,
    onCollapse: () -> Unit,
    isLyricsVisible: Boolean,
    onLyricsVisibilityChange: (Boolean) -> Unit
) {
    val isExpanded = sheetState.targetValue == SheetValue.Expanded || sheetState.currentValue == SheetValue.Expanded
    if (!isExpanded) {
        Spacer(modifier = Modifier.height(0.dp))
        return
    }

    val context = LocalContext.current
    val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()
    val currentTrack = playbackState.currentTrack
    val isPlaying = playbackState.isPlaying
    val progress = playbackState.currentPosition
    val duration = playbackState.duration
    val currentTrackRelated by viewModel.currentTrackRelated.collectAsStateWithLifecycle()
    val currentArtistSongs by viewModel.currentArtistSongs.collectAsStateWithLifecycle()
    val favoriteTracks by viewModel.favoriteTracks.collectAsStateWithLifecycle()
    val savedCollectionIds by viewModel.savedCollectionIds.collectAsStateWithLifecycle()
    var playlistTargetTrack by remember { mutableStateOf<com.israrxy.raazi.model.MusicItem?>(null) }

    // State Hoisting for Lyrics
    val lyrics by viewModel.lyrics.collectAsStateWithLifecycle()
    val isLyricsLoading by viewModel.isLyricsLoading.collectAsStateWithLifecycle()
    val lyricsScriptFilter by viewModel.lyricsScriptFilter.collectAsStateWithLifecycle()
    val lyricsSearchResults by viewModel.lyricsSearchResults.collectAsStateWithLifecycle()
    val isLyricsSearchLoading by viewModel.isLyricsSearchLoading.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val videoQuality by viewModel.videoQuality.collectAsStateWithLifecycle()
    var showLyricsBrowser by remember { mutableStateOf(false) }
    var showLyricsMenu by remember { mutableStateOf(false) }
    var showFullLyrics by remember { mutableStateOf(false) }
    var showOptionsSheet by remember { mutableStateOf(false) }
    var showCreditsSheet by remember { mutableStateOf(false) }
    val optionsSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showSleepTimer by remember { mutableStateOf(false) }
    val sleepTimerActive by com.israrxy.raazi.service.SleepTimer.getInstance().isActive.collectAsStateWithLifecycle()

    // Lyrics translation state
    val geminiApiKey by SettingsDataStore(context).geminiApiKey.collectAsStateWithLifecycle(initialValue = "")
    val lyricsTranslateEnabled by SettingsDataStore(context).lyricsTranslateEnabled.collectAsStateWithLifecycle(initialValue = false)
    val lyricsTranslateLang by SettingsDataStore(context).lyricsTranslateLang.collectAsStateWithLifecycle(initialValue = "en")
    var showTranslate by remember { mutableStateOf(false) }
    var isTranslating by remember { mutableStateOf(false) }
    var translateError by remember { mutableStateOf<String?>(null) }
    val translatedSyncedLines = remember { mutableStateListOf<Pair<Long, String>>() }
    var translatedPlain by remember { mutableStateOf<String?>(null) }

    val parsedSyncedLines = remember(lyrics?.syncedLyrics) {
        parseLrcLines(lyrics?.syncedLyrics)
    }
    val isSyncedLyrics = remember(lyrics) { !lyrics?.syncedLyrics.isNullOrEmpty() }

    LaunchedEffect(lyrics?.syncedLyrics, lyrics?.plainLyrics, lyricsTranslateEnabled, showTranslate, geminiApiKey) {
        translatedSyncedLines.clear()
        translatedPlain = null
        translateError = null
        if (!showTranslate || !lyricsTranslateEnabled || geminiApiKey.isNullOrBlank()) return@LaunchedEffect
        val source = lyrics?.syncedLyrics ?: lyrics?.plainLyrics
        if (source.isNullOrBlank()) return@LaunchedEffect
        isTranslating = true
        try {
            if (parsedSyncedLines.isNotEmpty()) {
                val trans = LyricsTranslator.translateLines(geminiApiKey!!, parsedSyncedLines.map { it.second }, lyricsTranslateLang)
                parsedSyncedLines.forEachIndexed { i, (t, _) ->
                    translatedSyncedLines.add(t to trans.getOrElse(i) { "" })
                }
            } else if (!lyrics?.plainLyrics.isNullOrBlank()) {
                val trans = LyricsTranslator.translateLines(geminiApiKey!!, listOf(lyrics!!.plainLyrics!!), lyricsTranslateLang)
                translatedPlain = trans.firstOrNull()
            }
        } catch (e: Exception) {
            translateError = "Translation failed"
        } finally {
            isTranslating = false
        }
    }

    var showPlayNextSheet by remember { mutableStateOf(false) }
    var showPlaylistDialog by remember { mutableStateOf(false) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }
    val userPlaylists by viewModel.userPlaylists.collectAsStateWithLifecycle()
    var lyricsSearchTitle by remember(currentTrack?.id) {
        mutableStateOf(LyricsMetadataSanitizer.sanitizeTrackTitle(currentTrack?.title.orEmpty()))
    }
    var lyricsSearchArtist by remember(currentTrack?.id) {
        mutableStateOf(LyricsMetadataSanitizer.sanitizeArtist(currentTrack?.artist.orEmpty()))
    }
    var selectedLyricsLanguage by remember(currentTrack?.id) { mutableStateOf("All") }
    var selectedLyricsVariant by remember(currentTrack?.id) { mutableStateOf(LyricsViewVariant.ORIGINAL) }
    val density = LocalDensity.current
    var lyricsDismissDrag by remember { mutableFloatStateOf(0f) }
    val playNextItems = remember(currentTrack?.id, currentTrackRelated, currentArtistSongs) {
        (currentTrackRelated + currentArtistSongs)
            .filter { it.id != currentTrack?.id }
            .distinctBy { it.id }
            .take(30)
    }

    LaunchedEffect(isLyricsVisible) {
        if (!isLyricsVisible) {
            showLyricsMenu = false
            showLyricsBrowser = false
            showFullLyrics = false
            lyricsDismissDrag = 0f
        }
    }

    val lyricsDismissThresholdPx = with(density) { 120.dp.toPx() }
    val lyricsDismissTranslation by animateFloatAsState(
        targetValue = if (lyricsDismissDrag > 0f) lyricsDismissDrag * 0.32f else 0f,
        animationSpec = tween(durationMillis = 180),
        label = "lyrics_dismiss_translation"
    )
    val lyricsDismissConnection = remember(
        isLyricsVisible,
        showLyricsMenu,
        showLyricsBrowser,
        lyricsDismissThresholdPx
    ) {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if (!isLyricsVisible || showLyricsMenu || showLyricsBrowser || source != NestedScrollSource.UserInput) {
                    lyricsDismissDrag = 0f
                    return Offset.Zero
                }

                if (available.y > 0f) {
                    lyricsDismissDrag = (lyricsDismissDrag + available.y).coerceAtMost(lyricsDismissThresholdPx * 1.4f)
                    if (lyricsDismissDrag >= lyricsDismissThresholdPx) {
                        lyricsDismissDrag = 0f
                        onLyricsVisibilityChange(false)
                    }
                    return Offset(0f, available.y)
                }

                if (consumed.y < 0f || available.y < 0f) {
                    lyricsDismissDrag = 0f
                }
                return Offset.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                lyricsDismissDrag = 0f
                return Velocity.Zero
            }
        }
    }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        AnimatedVisibility(
            visible = true,
            enter = fadeIn(tween(500)),
            exit = fadeOut(tween(250))
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                AnimatedContent(
                    targetState = currentTrack,
                    transitionSpec = {
                        fadeIn(animationSpec = tween(700)) togetherWith fadeOut(animationSpec = tween(700))
                    },
                    label = "BackgroundTransition"
                ) { track ->
                    if (track != null) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            val isAndroid12 = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S

                            AsyncImage(
                                model = if (isAndroid12) {
                                    track.thumbnailUrl
                                } else {
                                    coil.request.ImageRequest.Builder(LocalContext.current)
                                        .data(track.thumbnailUrl)
                                        .transformations(
                                            com.israrxy.raazi.utils.BlurTransformation(LocalContext.current, radius = 25, sampling = 6f)
                                        )
                                        .build()
                                },
                                contentDescription = null,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .then(if (isAndroid12) Modifier.blur(radius = 60.dp) else Modifier),
                                contentScale = ContentScale.Crop
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.6f))
                            )
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    brush = Brush.verticalGradient(
                                        colors = listOf(Zinc900, PureBlack)
                                    )
                                )
                        )
                    }
                }

                if (isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.4f))
                    ) {
                        com.israrxy.raazi.ui.components.SkeletonPlayer()
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = true,
            enter = fadeIn(tween(300)),
            exit = fadeOut(tween(300))
        ) {
            // Main Scrollable Content
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val screenHeight = maxHeight
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 0.dp, max = screenHeight)
                            .statusBarsPadding()
                            .padding(horizontal = 24.dp)
                    ) {
            // --- NORMAL PLAYER MODE ---
            // HEADER
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onCollapse,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Collapse",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }

                // AUDIO / VIDEO / LYRICS — Icon-only minimal mode toggle
                val isVideoMode = playbackState.mediaMode == PlaybackMediaMode.VIDEO && !isLyricsVisible
                val playerModeIndex = when {
                    isLyricsVisible -> 2
                    isVideoMode -> 1
                    else -> 0
                }
                val pillSegmentWidth = 52.dp
                val pillOffset by animateDpAsState(
                    targetValue = pillSegmentWidth * playerModeIndex,
                    animationSpec = tween(durationMillis = 220),
                    label = "pill_offset"
                )

                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .height(38.dp)
                            .width(pillSegmentWidth * 3)
                            .clip(RoundedCornerShape(19.dp))
                            .background(Color.White.copy(alpha = 0.12f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(pillSegmentWidth)
                                .offset(x = pillOffset)
                                .clip(RoundedCornerShape(19.dp))
                                .background(Color.White)
                        )
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Audio (Song) Icon
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) {
                                        if (isLyricsVisible) onLyricsVisibilityChange(false)
                                        if (playbackState.mediaMode != PlaybackMediaMode.AUDIO) {
                                            viewModel.setPlaybackMediaMode(PlaybackMediaMode.AUDIO)
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MusicNote,
                                    contentDescription = "Audio Mode",
                                    tint = if (playerModeIndex == 0) Color.Black else Color.White.copy(alpha = 0.78f),
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                            // Video Icon
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) {
                                        if (isLyricsVisible) onLyricsVisibilityChange(false)
                                        if (playbackState.mediaMode != PlaybackMediaMode.VIDEO) {
                                            viewModel.setPlaybackMediaMode(PlaybackMediaMode.VIDEO)
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.OndemandVideo,
                                    contentDescription = "Video Mode",
                                    tint = if (playerModeIndex == 1) Color.Black else Color.White.copy(alpha = 0.78f),
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                            // Lyrics Icon
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) {
                                        onLyricsVisibilityChange(!isLyricsVisible)
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lyrics,
                                    contentDescription = "Lyrics Mode",
                                    tint = if (playerModeIndex == 2) Color.Black else Color.White.copy(alpha = 0.78f),
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                        }
                    }
                }

                // Menu Button -> opens the YouTube Music style action sheet
                IconButton(
                    onClick = { showOptionsSheet = true },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Options",
                        tint = if (sleepTimerActive) Emerald500 else Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))

            // ===================== NORMAL PLAYER MODE =====================
            var dragOffset by remember { mutableFloatStateOf(0f) }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(vertical = 20.dp)
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                if (dragOffset < -100f) {
                                    viewModel.next()
                                } else if (dragOffset > 100f) {
                                    viewModel.previous()
                                }
                                dragOffset = 0f
                            }
                        ) { change: PointerInputChange, dragAmount: Float ->
                            change.consume()
                            dragOffset += dragAmount
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                val track = currentTrack
                if (track != null && playbackState.mediaMode == PlaybackMediaMode.VIDEO) {
                    var activeSurface by remember { mutableStateOf<android.view.Surface?>(null) }
                    DisposableEffect(Unit) {
                        onDispose {
                            viewModel.clearVideoSurface()
                            activeSurface?.release()
                            activeSurface = null
                        }
                    }
                    Box(
                        modifier = Modifier
                            .layout { measurable, constraints ->
                                val horizontalPaddingPx = 24.dp.roundToPx()
                                val placeable = measurable.measure(
                                    constraints.copy(
                                        minWidth = constraints.maxWidth + horizontalPaddingPx * 2,
                                        maxWidth = constraints.maxWidth + horizontalPaddingPx * 2
                                    )
                                )
                                layout(placeable.width, placeable.height) {
                                    placeable.placeRelative(-horizontalPaddingPx, 0)
                                }
                            }
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                            .background(Color.Black),
                        contentAlignment = Alignment.Center
                    ) {
                        // High-resolution 16:9 widescreen visual backdrop so it never flashes a black box
                        AsyncImage(
                            model = ThumbnailUtils.getHighQualityThumbnail(track.thumbnailUrl),
                            contentDescription = track.title,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )

                        // TextureView renders decoded video frames directly inside Compose with proper clipping
                        androidx.compose.ui.viewinterop.AndroidView(
                            factory = { ctx ->
                                android.view.TextureView(ctx).apply {
                                    isOpaque = false
                                    surfaceTextureListener = object : android.view.TextureView.SurfaceTextureListener {
                                        override fun onSurfaceTextureAvailable(
                                            surfaceTexture: android.graphics.SurfaceTexture,
                                            width: Int,
                                            height: Int
                                        ) {
                                            activeSurface?.release()
                                            val newSurface = android.view.Surface(surfaceTexture)
                                            activeSurface = newSurface
                                            viewModel.setVideoSurface(newSurface)
                                        }

                                        override fun onSurfaceTextureSizeChanged(
                                            surfaceTexture: android.graphics.SurfaceTexture,
                                            width: Int,
                                            height: Int
                                        ) {}

                                        override fun onSurfaceTextureDestroyed(
                                            surfaceTexture: android.graphics.SurfaceTexture
                                        ): Boolean {
                                            viewModel.clearVideoSurface()
                                            activeSurface?.release()
                                            activeSurface = null
                                            return true
                                        }

                                        override fun onSurfaceTextureUpdated(
                                            surfaceTexture: android.graphics.SurfaceTexture
                                        ) {}
                                    }
                                    // If surfaceTexture is already available on attach
                                    surfaceTexture?.let { st ->
                                        activeSurface?.release()
                                        val newSurface = android.view.Surface(st)
                                        activeSurface = newSurface
                                        viewModel.setVideoSurface(newSurface)
                                    }
                                }
                            },
                            update = {
                                if (playbackState.isVideoAvailable && activeSurface != null) {
                                    viewModel.setVideoSurface(activeSurface)
                                }
                            },
                            modifier = Modifier
                                .fillMaxSize()
                                .alpha(if (playbackState.isVideoAvailable && !playbackState.isLoading) 1f else 0.01f)
                        )

                        if (playbackState.isLoading || playbackState.isBuffering) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.35f)),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    color = Color.White,
                                    modifier = Modifier.size(34.dp),
                                    strokeWidth = 2.5.dp
                                )
                            }
                        }
                    }
                } else {
                    AnimatedContent(
                        targetState = track,
                        transitionSpec = {
                            fadeIn(animationSpec = tween(350)) togetherWith fadeOut(animationSpec = tween(350))
                        },
                        label = "AlbumArtTransition"
                    ) { targetTrack ->
                        if (!playbackState.isLoading && targetTrack != null) {
                            AsyncImage(
                                model = ThumbnailUtils.getHighQualityThumbnail(targetTrack.thumbnailUrl),
                                contentDescription = "Album Art",
                                modifier = Modifier
                                    .fillMaxWidth(0.88f)
                                    .aspectRatio(1f)
                                    .shadow(20.dp, RoundedCornerShape(20.dp))
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(Zinc800),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.88f)
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(20.dp))
                                    .shimmerEffect()
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // SONG INFO
            Column(modifier = Modifier.fillMaxWidth()) {
                AnimatedContent(
                    targetState = currentTrack,
                    transitionSpec = {
                        (fadeIn(animationSpec = tween(300)) + slideInVertically { height -> height / 4 })
                            .togetherWith(fadeOut(animationSpec = tween(300)) + slideOutVertically { height -> -height / 4 })
                    },
                    label = "SongInfoTransition"
                ) { track ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.Start
                        ) {
                            Text(
                                text = track?.title ?: "Not Playing",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = track?.artist ?: "Unknown Artist",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White.copy(alpha = 0.7f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        
                        // Like Button
                        val isLiked = track?.let { t -> favoriteTracks.any { it.id == t.id } } == true
                        
                        IconButton(onClick = { track?.let { viewModel.toggleFavorite(it) } }) {
                            Icon(
                                imageVector = if (isLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                contentDescription = if (isLiked) "Unlike" else "Like",
                                tint = if (isLiked) Color.Red else Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // PROGRESS BAR
            Column(modifier = Modifier.fillMaxWidth()) {
                Slider(
                    value = if (duration > 0) progress.toFloat() / duration.toFloat() else 0f,
                    onValueChange = { fraction ->
                        // Calculate seek position and tell ViewModel to seek
                        val seekPosition = (fraction * duration).toLong()
                        viewModel.seekTo(seekPosition)
                    },
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = Color.White,
                        inactiveTrackColor = Color.White.copy(alpha = 0.2f)
                    ),
                    modifier = Modifier.fillMaxWidth().height(20.dp) // Make touch target reasonable
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatTime(progress),
                        style = MaterialTheme.typography.labelSmall,
                        color = Zinc500
                    )
                    Text(
                        text = formatTime(duration),
                        style = MaterialTheme.typography.labelSmall,
                        color = Zinc500
                    )
                }
            }

            // CODEC INFO — subtle inline label
            val currentFormat by viewModel.currentFormat.collectAsStateWithLifecycle()
            val streamQuality by viewModel.streamQuality.collectAsStateWithLifecycle()
            val codecLabel = remember(currentFormat, streamQuality, playbackState.videoQuality, playbackState.mediaMode) {
                when {
                    streamQuality != null && streamQuality!!.contains("FLAC") -> streamQuality!!
                    currentFormat != null -> {
                        val f = currentFormat!!
                        val codec = f.codecs.uppercase().replace("MP4A.40.2", "AAC").replace("MP4A.40.5", "AAC-HE")
                        val bitrate = "${f.bitrate / 1000}kbps"
                        val sampleRate = if (f.sampleRate != null) " · ${f.sampleRate / 1000}kHz" else ""
                        val videoInfo = if (playbackState.mediaMode == PlaybackMediaMode.VIDEO) " · ${playbackState.videoQuality.label}" else ""
                        "$codec · $bitrate$sampleRate$videoInfo"
                    }
                    streamQuality != null -> streamQuality!!
                    playbackState.mediaMode == PlaybackMediaMode.VIDEO -> playbackState.videoQuality.label
                    else -> null
                }
            }
            val isFlacLabel = codecLabel?.contains("FLAC") == true
            if (codecLabel != null) {
                Text(
                    text = codecLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isFlacLabel) Color(0xFFE0B0FF).copy(alpha = 0.8f) else Color.White.copy(alpha = 0.35f),
                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    letterSpacing = 0.8.sp
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // CONTROLS — fixed-size cells so the row never overflows the screen width
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Shuffle button - clearly shows active & inactive state
                val shuffleEnabled = playbackState.isShuffleEnabled
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(72.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(
                                    if (shuffleEnabled) Emerald500.copy(alpha = 0.22f) else Color.Transparent
                                )
                                .clickable { viewModel.toggleShuffle() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Shuffle,
                                contentDescription = "Shuffle",
                                tint = if (shuffleEnabled) Emerald500 else Color.White.copy(alpha = 0.78f),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        if (shuffleEnabled) {
                            Spacer(modifier = Modifier.height(3.dp))
                            Box(
                                modifier = Modifier
                                    .size(5.dp)
                                    .clip(CircleShape)
                                    .background(Emerald500)
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(72.dp),
                    contentAlignment = Alignment.Center
                ) {
                    IconButton(
                        onClick = { viewModel.previous() },
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(Icons.Default.SkipPrevious, null, tint = Color.White, modifier = Modifier.size(34.dp))
                    }
                }

                // Play/Pause FAB
                val isTrackBusy = (playbackState.isLoading || playbackState.isBuffering) && !isPlaying
                Box(
                    modifier = Modifier.size(72.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .shadow(16.dp, CircleShape)
                            .clip(CircleShape)
                            .background(Color.White)
                            .clickable {
                                if (isPlaying) viewModel.pause() else viewModel.resume()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isTrackBusy) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(26.dp),
                                color = Color.Black,
                                strokeWidth = 2.5.dp
                            )
                        } else {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = "Play/Pause",
                                tint = Color.Black,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(72.dp),
                    contentAlignment = Alignment.Center
                ) {
                    IconButton(
                        onClick = { viewModel.next() },
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(Icons.Default.SkipNext, null, tint = Color.White, modifier = Modifier.size(34.dp))
                    }
                }

                // Repeat button - shows mode state
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(72.dp),
                    contentAlignment = Alignment.Center
                ) {
                    IconButton(
                        onClick = { viewModel.toggleRepeat() },
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            imageVector = when (playbackState.repeatMode) {
                                com.israrxy.raazi.model.RepeatMode.ONE -> Icons.Default.RepeatOne
                                else -> Icons.Default.Repeat
                            },
                            contentDescription = "Repeat",
                            tint = when (playbackState.repeatMode) {
                                com.israrxy.raazi.model.RepeatMode.OFF -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                else -> MaterialTheme.colorScheme.primary
                            },
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // BOTTOM ACTIONS — Icon Only (No Bubble)
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Download Button
                val allDownloads by viewModel.dbAllDownloads.collectAsStateWithLifecycle()
                val downloadState = currentTrack?.let { track ->
                    allDownloads.find { it.trackId == track.id }
                }
                val downloadIcon = when (downloadState?.status) {
                    com.israrxy.raazi.data.db.DownloadEntity.STATUS_COMPLETED -> Icons.Default.CheckCircle
                    com.israrxy.raazi.data.db.DownloadEntity.STATUS_DOWNLOADING,
                    com.israrxy.raazi.data.db.DownloadEntity.STATUS_PENDING -> Icons.Default.Downloading
                    com.israrxy.raazi.data.db.DownloadEntity.STATUS_FAILED -> Icons.Default.ErrorOutline
                    else -> Icons.Default.Download
                }
                val isDownloaded = downloadState?.status == com.israrxy.raazi.data.db.DownloadEntity.STATUS_COMPLETED
                val isFailed = downloadState?.status == com.israrxy.raazi.data.db.DownloadEntity.STATUS_FAILED
                
                val dlAccent = when {
                    isDownloaded -> Emerald500
                    isFailed -> Color(0xFFFF5252)
                    else -> Color.White.copy(alpha = 0.9f)
                }

                Box(
                    modifier = Modifier
                        .size(50.dp)
                        .clip(CircleShape)
                        .clickable {
                            currentTrack?.let { track ->
                                when (downloadState?.status) {
                                    com.israrxy.raazi.data.db.DownloadEntity.STATUS_COMPLETED -> {
                                        Toast.makeText(context, "Already downloaded", Toast.LENGTH_SHORT).show()
                                    }
                                    com.israrxy.raazi.data.db.DownloadEntity.STATUS_DOWNLOADING,
                                    com.israrxy.raazi.data.db.DownloadEntity.STATUS_PENDING -> {
                                        Toast.makeText(context, "Download in progress", Toast.LENGTH_SHORT).show()
                                    }
                                    com.israrxy.raazi.data.db.DownloadEntity.STATUS_FAILED -> {
                                        viewModel.retryDownload(track.id)
                                    }
                                    else -> {
                                        viewModel.downloadTrack(track)
                                    }
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                     if (downloadState?.status == com.israrxy.raazi.data.db.DownloadEntity.STATUS_DOWNLOADING) {
                        CircularProgressIndicator(
                            progress = downloadState.progress / 100f,
                            modifier = Modifier.size(50.dp),
                            color = Emerald500,
                            strokeWidth = 2.dp,
                            trackColor = Color.Transparent
                        )
                    }
                    Icon(downloadIcon, null, tint = dlAccent, modifier = Modifier.size(24.dp))
                }

                Spacer(modifier = Modifier.width(24.dp))

                val hasQueueOrRelated = playbackState.playlist.isNotEmpty() || playNextItems.isNotEmpty()
                val playNextAccent = if (hasQueueOrRelated) Color.White.copy(alpha = 0.9f) else Color.White.copy(alpha = 0.5f)
                Box(
                    modifier = Modifier
                        .size(50.dp)
                        .clip(CircleShape)
                        .clickable { showPlayNextSheet = true },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.QueueMusic, contentDescription = "Queue", tint = playNextAccent, modifier = Modifier.size(24.dp))
                }

                Spacer(modifier = Modifier.width(24.dp))

                // Share Button
                Box(
                    modifier = Modifier
                        .size(50.dp)
                        .clip(CircleShape)
                        .clickable {
                            val link = viewModel.getShareLink()
                            if (link != null) {
                                val sendIntent = android.content.Intent().apply {
                                    action = android.content.Intent.ACTION_SEND
                                    putExtra(android.content.Intent.EXTRA_TEXT, link)
                                    type = "text/plain"
                                }
                                context.startActivity(android.content.Intent.createChooser(sendIntent, "Share song"))
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Share, null, tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.size(24.dp))
                }

            }
                    } // end of height(screenHeight) Column

                    // --- Extra content cards (scrollable below the player) ---
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp)
                            .padding(bottom = 32.dp)
                            .navigationBarsPadding()
            ) {
                // 0. Spotify-style Inline Synced Lyrics Preview Card
                Spacer(modifier = Modifier.height(20.dp))
                SpotifyLyricsPreviewCard(
                    syncedLines = parsedSyncedLines,
                    plainLyrics = lyrics?.plainLyrics,
                    currentPosition = progress,
                    isLyricsLoading = isLyricsLoading,
                    onExpandLyrics = { onLyricsVisibilityChange(true) },
                    onSearchLyrics = {
                        showLyricsBrowser = true
                        viewModel.searchLyricsOptions(lyricsSearchTitle, lyricsSearchArtist)
                    },
                    onSeekToLine = { viewModel.seekTo(it) }
                )

                // 1. About the Artist Card
                val artistId = currentTrack?.artistId?.ifBlank { null } ?: currentTrack?.artist.orEmpty()
                val artistName = currentTrack?.artist ?: "Unknown Artist"
                val normalizedArtistId = remember(artistId, artistName) {
                    artistId.ifBlank { artistName }
                }
                val savedArtistId = remember(normalizedArtistId) {
                    savedCollectionId(MusicContentType.ARTIST, normalizedArtistId)
                }
                val isSavedArtist = savedArtistId in savedCollectionIds

                val artistThumbnail = currentArtistSongs.firstOrNull()?.thumbnailUrl
                    ?: currentTrack?.thumbnailUrl
                    ?: ""

                Spacer(modifier = Modifier.height(24.dp))

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onCollapse()
                            navController.navigate("artist/${Uri.encode(normalizedArtistId)}?name=${Uri.encode(artistName)}")
                        },
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "ABOUT THE ARTIST",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White.copy(alpha = 0.6f),
                            letterSpacing = 1.sp
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            AsyncImage(
                                model = ThumbnailUtils.getHighQualityThumbnail(artistThumbnail),
                                contentDescription = artistName,
                                modifier = Modifier
                                    .size(70.dp)
                                    .clip(CircleShape)
                                    .background(Zinc800),
                                contentScale = ContentScale.Crop
                            )

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = artistName,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = if (isSavedArtist) "Saved to Library" else "Tap to view profile",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.5f)
                                )
                            }

                            // Follow / Bookmark Button
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(999.dp))
                                    .then(
                                        if (isSavedArtist) {
                                            Modifier
                                                .border(1.dp, Color.White.copy(alpha = 0.4f), RoundedCornerShape(999.dp))
                                                .clickable {
                                                    viewModel.toggleSavedArtist(
                                                        artistId = normalizedArtistId,
                                                        artistName = artistName,
                                                        thumbnailUrl = artistThumbnail
                                                    )
                                                }
                                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                        } else {
                                            Modifier
                                                .background(Color.White)
                                                .clickable {
                                                    viewModel.toggleSavedArtist(
                                                        artistId = normalizedArtistId,
                                                        artistName = artistName,
                                                        thumbnailUrl = artistThumbnail
                                                    )
                                                }
                                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                        }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (isSavedArtist) "Following" else "Follow",
                                    color = if (isSavedArtist) Color.White else Color.Black,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                    } // end of extra content cards Column
                } // end of outer Column
            } // end of BoxWithConstraints
        } // end of AnimatedVisibility (main content)

        // UNIFIED SPOTIFY + YOUTUBE MUSIC LYRICS SCREEN OVERLAY
        AnimatedVisibility(
            visible = isLyricsVisible,
            enter = slideInVertically(initialOffsetY = { it / 3 }, animationSpec = tween(320)) + fadeIn(tween(260)),
            exit = slideOutVertically(targetOffsetY = { it / 3 }, animationSpec = tween(260)) + fadeOut(tween(200)),
            modifier = Modifier.fillMaxSize()
        ) {
            LyricsScreen(
                viewModel = viewModel,
                onClose = { onLyricsVisibilityChange(false) },
                onOpenLyricsBrowser = {
                    showLyricsBrowser = true
                    viewModel.searchLyricsOptions(lyricsSearchTitle, lyricsSearchArtist)
                }
            )
        }

        if (showLyricsBrowser) {
            val languages = remember(lyricsSearchResults) {
                listOf("All") + lyricsSearchResults
                    .map { it.lyrics.language?.ifBlank { "Unknown" } ?: "Unknown" }
                    .distinct()
            }
            val filteredResults = remember(lyricsSearchResults, selectedLyricsLanguage, lyricsScriptFilter) {
                lyricsSearchResults.filter { result ->
                    val languageMatches = selectedLyricsLanguage == "All" ||
                        (result.lyrics.language?.ifBlank { "Unknown" } ?: "Unknown") == selectedLyricsLanguage
                    val scriptMatches = LyricsScriptDetector.matches(lyricsScriptFilter, result.lyrics)
                    languageMatches && scriptMatches
                }
            }

            ModalBottomSheet(
                onDismissRequest = {
                    showLyricsBrowser = false
                    viewModel.clearLyricsSearchResults()
                },
                containerColor = MaterialTheme.colorScheme.surface,
                dragHandle = { BottomSheetDefaults.DragHandle() }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Browse Lyrics",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Search manually, compare versions, then save the right one for this song.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = lyricsSearchTitle,
                        onValueChange = { lyricsSearchTitle = it },
                        label = { Text("Song Title") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = lyricsSearchArtist,
                        onValueChange = { lyricsSearchArtist = it },
                        label = { Text("Artist") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = {
                                selectedLyricsLanguage = "All"
                                viewModel.searchLyricsOptions(lyricsSearchTitle, lyricsSearchArtist)
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(if (isLyricsSearchLoading) "Searching..." else "Search")
                        }

                        if (lyrics?.source?.startsWith("Saved") == true) {
                            OutlinedButton(
                                onClick = {
                                    viewModel.clearSavedLyricsSelection()
                                    Toast.makeText(context, "Using automatic lyrics again", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Remove Saved")
                            }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        LyricsScriptFilter.visibleFilters.forEach { filter ->
                            FilterChip(
                                selected = lyricsScriptFilter == filter,
                                onClick = { viewModel.setLyricsScriptFilter(filter) },
                                label = { Text(filter.label) }
                            )
                        }
                    }

                    if (languages.size > 1) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            languages.forEach { language ->
                                FilterChip(
                                    selected = selectedLyricsLanguage == language,
                                    onClick = { selectedLyricsLanguage = language },
                                    label = { Text(language) }
                                )
                            }
                        }
                    }

                    when {
                        isLyricsSearchLoading -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(220.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator()
                            }
                        }

                        filteredResults.isEmpty() -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(220.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No lyric versions found yet.",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        else -> {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 420.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(filteredResults) { result ->
                                    Surface(
                                        shape = RoundedCornerShape(18.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(16.dp),
                                            verticalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = result.lyrics.trackName,
                                                        style = MaterialTheme.typography.titleMedium,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                    Text(
                                                        text = result.lyrics.artistName,
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                                Surface(
                                                    shape = RoundedCornerShape(999.dp),
                                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                                                ) {
                                                    Text(
                                                        text = "${result.matchScore}%",
                                                        color = MaterialTheme.colorScheme.primary,
                                                        style = MaterialTheme.typography.labelMedium,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                                    )
                                                }
                                            }

                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                AssistChip(
                                                    onClick = {},
                                                    label = { Text(LyricsScriptDetector.detect(result.lyrics).label) }
                                                )
                                                AssistChip(
                                                    onClick = {},
                                                    label = { Text(result.lyrics.language ?: "Unknown") }
                                                )
                                                AssistChip(
                                                    onClick = {},
                                                    label = { Text(result.lyrics.source) }
                                                )
                                                AssistChip(
                                                    onClick = {},
                                                    label = { Text("%.0fs".format(result.lyrics.duration)) }
                                                )
                                            }

                                            Text(
                                                text = result.lyrics.syncedLyrics?.lineSequence()?.firstOrNull { it.isNotBlank() }
                                                    ?: result.lyrics.plainLyrics?.lineSequence()?.firstOrNull { it.isNotBlank() }
                                                    ?: "No preview available",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )

                                            Button(
                                                onClick = {
                                                    viewModel.saveLyricsSelection(result)
                                                    showLyricsBrowser = false
                                                    Toast.makeText(context, "Lyrics saved for this song", Toast.LENGTH_SHORT).show()
                                                },
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Text("Use And Save")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        
        // ADD TO PLAYLIST DIALOG
        if (showSleepTimer) {
            com.israrxy.raazi.ui.components.SleepTimerDialog(
                onDismiss = { showSleepTimer = false },
                onTimerSet = { minutes ->
                    com.israrxy.raazi.service.SleepTimer.getInstance().start(minutes) {
                        viewModel.pause()
                    }
                    showSleepTimer = false
                }
            )
        }

        if (showPlaylistDialog) {
            AlertDialog(
                onDismissRequest = {
                    showPlaylistDialog = false
                    playlistTargetTrack = null
                },
                title = { Text("Add to Playlist", color = MaterialTheme.colorScheme.onSurface) },
                text = {
                    Column {
                        // CREATE NEW PLAYLIST OPTION — always shown, otherwise a user
                        // with zero playlists has no way to create their first one.
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showPlaylistDialog = false
                                    showCreateDialog = true
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Add, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(16.dp))
                            Text("Create New Playlist", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(vertical = 8.dp))

                        if (userPlaylists.isEmpty()) {
                            Text(
                                "No playlists yet",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 12.dp)
                            )
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp)
                            ) {
                                items(items = userPlaylists) { playlist: com.israrxy.raazi.data.db.PlaylistEntity ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                val targetTrack = playlistTargetTrack ?: currentTrack
                                                if (targetTrack != null) {
                                                    viewModel.addToPlaylist(playlist.id, targetTrack)
                                                    showPlaylistDialog = false
                                                    playlistTargetTrack = null
                                                    Toast.makeText(context, "Added to ${playlist.title}", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                            .padding(vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.QueueMusic, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                                        Spacer(modifier = Modifier.width(16.dp))
                                        Text(playlist.title, color = MaterialTheme.colorScheme.onSurface)
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { 
                        showPlaylistDialog = false
                        playlistTargetTrack = null
                    }) {
                        Text("Dismiss", color = MaterialTheme.colorScheme.primary)
                    }
                },
                containerColor = MaterialTheme.colorScheme.surface
            )
        }

        // CREATE NEW PLAYLIST DIALOG
        if (showCreateDialog) {
            AlertDialog(
                onDismissRequest = { showCreateDialog = false },
                title = { Text("Create Playlist", color = MaterialTheme.colorScheme.onSurface) },
                text = {
                    TextField(
                        value = newPlaylistName,
                        onValueChange = { newPlaylistName = it },
                        placeholder = { Text("Playlist name", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                            cursorColor = MaterialTheme.colorScheme.primary,
                            focusedIndicatorColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val targetTrack = playlistTargetTrack ?: currentTrack
                            if (newPlaylistName.isNotBlank() && targetTrack != null) {
                                viewModel.createPlaylist(newPlaylistName)
                                showCreateDialog = false
                                newPlaylistName = ""
                                playlistTargetTrack = null
                                Toast.makeText(context, "Playlist created! Add the song from the menu.", Toast.LENGTH_LONG).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("Create", color = MaterialTheme.colorScheme.onPrimary)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showCreateDialog = false }) {
                        Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                containerColor = MaterialTheme.colorScheme.surface
            )
        }

        // UP NEXT / MUSIC MIX SHEET
        // QUEUE SHEET (YouTube Music style: smart filters + drag reorder + Save)
        if (showPlayNextSheet) {
            ModalBottomSheet(
                onDismissRequest = { showPlayNextSheet = false },
                containerColor = MaterialTheme.colorScheme.surface,
                dragHandle = { BottomSheetDefaults.DragHandle() }
            ) {
                QueueBottomSheet(
                    viewModel = viewModel,
                    onDismiss = { showPlayNextSheet = false },
                    onSaveQueueAsPlaylist = { tracks ->
                        viewModel.saveCurrentQueueAsPlaylist(tracks = tracks)
                        Toast.makeText(context, "Saved queue as playlist!", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }

        // THREE-DOT ACTION SHEET
        if (showOptionsSheet) {
            ModalBottomSheet(
                onDismissRequest = { showOptionsSheet = false },
                sheetState = optionsSheetState,
                containerColor = MaterialTheme.colorScheme.surface,
                dragHandle = { BottomSheetDefaults.DragHandle() }
            ) {
                PlayerOptionsSheet(
                    track = currentTrack,
                    viewModel = viewModel,
                    onDismiss = { showOptionsSheet = false },
                    onSaveToPlaylist = {
                        playlistTargetTrack = currentTrack
                        showPlaylistDialog = true
                        showOptionsSheet = false
                    },
                    onViewCredits = {
                        showOptionsSheet = false
                        currentTrack?.let { track ->
                            viewModel.fetchSongCredits(track)
                        }
                        showCreditsSheet = true
                    },
                    onSetRingtone = {
                        currentTrack?.let { track -> viewModel.downloadForRingtone(track) }
                    },
                    currentVideoQuality = videoQuality,
                    onSelectVideoQuality = { viewModel.setPlaybackVideoQuality(it) }
                )
            }
        }

        // SONG CREDITS SHEET
        if (showCreditsSheet) {
            ModalBottomSheet(
                onDismissRequest = { showCreditsSheet = false },
                containerColor = MaterialTheme.colorScheme.surface,
                dragHandle = { BottomSheetDefaults.DragHandle() }
            ) {
                SongCreditsBottomSheet(
                    viewModel = viewModel,
                    onDismiss = { showCreditsSheet = false }
                )
            }
        }
        }
    }
}
private fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%d:%02d", minutes, seconds)
}
