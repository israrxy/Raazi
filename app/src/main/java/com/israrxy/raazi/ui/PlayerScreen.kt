package com.israrxy.raazi.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
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

import androidx.compose.material.icons.filled.Download // Import
import androidx.compose.material.icons.filled.PlaylistAdd // Import
import androidx.compose.material.icons.filled.Person // Import
import androidx.compose.ui.draw.blur // Import for blur effect
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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

    // State Hoisting for Lyrics
    val lyrics by viewModel.lyrics.collectAsStateWithLifecycle()
    val isLyricsLoading by viewModel.isLyricsLoading.collectAsStateWithLifecycle()
    val lyricsScriptFilter by viewModel.lyricsScriptFilter.collectAsStateWithLifecycle()
    val lyricsSearchResults by viewModel.lyricsSearchResults.collectAsStateWithLifecycle()
    val isLyricsSearchLoading by viewModel.isLyricsSearchLoading.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    var showLyricsBrowser by remember { mutableStateOf(false) }
    var showLyricsMenu by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
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

    LaunchedEffect(isLyricsVisible) {
        if (!isLyricsVisible) {
            showLyricsMenu = false
            showLyricsBrowser = false
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding() // Moved padding here
                .padding(horizontal = 24.dp)
        ) {
            // HEADER
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onCollapse) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Collapse",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
                
                Text(
                    text = "NOW PLAYING",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = Color.White.copy(alpha = 0.5f)
                )
                
                // Menu Button
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Options",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                    ) {
                        DropdownMenuItem(
                            text = { Text("Add to Playlist", color = MaterialTheme.colorScheme.onSurface) },
                            onClick = { 
                                showMenu = false
                                showPlaylistDialog = true
                            },
                            leadingIcon = { Icon(Icons.Default.PlaylistAdd, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                        )
                        
                        DropdownMenuItem(
                            text = { Text("Download", color = MaterialTheme.colorScheme.onSurface) },
                            onClick = { 
                                showMenu = false
                                currentTrack?.let { viewModel.downloadTrack(it) }
                            },
                            leadingIcon = { Icon(Icons.Default.Download, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            



            // ARTWORK - Shadow & Rounded
            var dragOffset by remember { mutableFloatStateOf(0f) }
            Box(
                modifier = Modifier
                    .weight(1f) // Takes available space
                    .fillMaxWidth()
                    .padding(vertical = 24.dp)
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
                 AnimatedContent(
                     targetState = currentTrack,
                     transitionSpec = {
                         fadeIn(animationSpec = tween(400)) togetherWith fadeOut(animationSpec = tween(400))
                     },
                     label = "AlbumArtTransition"
                 ) { track ->
                     if (!playbackState.isLoading && track != null) {
                        AsyncImage(
                            model = ThumbnailUtils.getHighQualityThumbnail(track.thumbnailUrl),
                            contentDescription = "Album Art",
                            modifier = Modifier
                                .fillMaxWidth(0.9f)
                                .aspectRatio(1f)
                                .shadow(24.dp, RoundedCornerShape(24.dp))
                                .clip(RoundedCornerShape(24.dp))
                                .background(Zinc800),
                            contentScale = ContentScale.Crop
                        )
                     } else {
                         // Loading skeleton using shimmer effect
                         Box(
                             modifier = Modifier
                                 .fillMaxWidth(0.9f)
                                 .aspectRatio(1f)
                                 .clip(RoundedCornerShape(24.dp))
                                 .shimmerEffect()
                         )
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
                        val favoriteTracks by viewModel.favoriteTracks.collectAsStateWithLifecycle()
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
            val codecLabel = remember(currentFormat, streamQuality) {
                when {
                    currentFormat != null -> {
                        val f = currentFormat!!
                        val codec = f.codecs.uppercase().replace("MP4A.40.2", "AAC").replace("MP4A.40.5", "AAC-HE")
                        val bitrate = "${f.bitrate / 1000}kbps"
                        val sampleRate = if (f.sampleRate != null) " · ${f.sampleRate / 1000}kHz" else ""
                        "$codec · $bitrate$sampleRate"
                    }
                    streamQuality != null -> streamQuality!!
                    else -> null
                }
            }
            if (codecLabel != null) {
                Text(
                    text = codecLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.35f),
                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    letterSpacing = 0.8.sp
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // CONTROLS
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Shuffle button - shows active state
                IconButton(onClick = { viewModel.toggleShuffle() }) {
                    Icon(
                        Icons.Default.Shuffle, 
                        contentDescription = "Shuffle",
                        tint = if (playbackState.isShuffleEnabled) Emerald500 else Zinc400
                    )
                }
                
                IconButton(
                    onClick = { viewModel.previous() },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Default.SkipPrevious, null, tint = Color.White, modifier = Modifier.size(40.dp))
                }
                
                // Play/Pause FAB
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .shadow(16.dp, CircleShape)
                        .clip(CircleShape)
                        .background(Color.White)
                        .clickable { 
                            if (isPlaying) viewModel.pause() else viewModel.resume()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Play/Pause",
                        tint = Color.Black,
                        modifier = Modifier.size(36.dp)
                    )
                }
                
                IconButton(
                    onClick = { viewModel.next() },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Default.SkipNext, null, tint = Color.White, modifier = Modifier.size(40.dp))
                }
                
                // Repeat button - shows mode state
                IconButton(onClick = { viewModel.toggleRepeat() }) {
                    Icon(
                        imageVector = when (playbackState.repeatMode) {
                            com.israrxy.raazi.model.RepeatMode.ONE -> Icons.Default.RepeatOne
                            else -> Icons.Default.Repeat
                        },
                        contentDescription = "Repeat",
                        tint = when (playbackState.repeatMode) {
                            com.israrxy.raazi.model.RepeatMode.OFF -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                            else -> MaterialTheme.colorScheme.primary
                        }
                    )
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

                // Lyrics Button
                val lyricsAccent = if (isLyricsVisible) Emerald500 else Color.White.copy(alpha = 0.9f)

                Box(
                    modifier = Modifier
                        .size(50.dp)
                        .clip(CircleShape)
                        .clickable { onLyricsVisibilityChange(!isLyricsVisible) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Subject, null, tint = lyricsAccent, modifier = Modifier.size(24.dp))
                }
            }
        }
        
        // LYRICS OVERLAY
        AnimatedVisibility(
            visible = isLyricsVisible,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            val syncedLyricsRaw = lyrics?.syncedLyrics
            val plainLyrics = lyrics?.plainLyrics
            val variantSourceText = syncedLyricsRaw ?: plainLyrics.orEmpty()
            val availableLyricsVariants = remember(variantSourceText) {
                LyricsTextVariants.availableVariants(variantSourceText)
            }
            LaunchedEffect(availableLyricsVariants) {
                if (selectedLyricsVariant !in availableLyricsVariants) {
                    selectedLyricsVariant = LyricsViewVariant.ORIGINAL
                }
            }
            val lyricsMode = when {
                isLyricsLoading -> "Searching"
                lyrics?.source?.startsWith("Saved") == true -> "Saved"
                !syncedLyricsRaw.isNullOrEmpty() -> "Synced"
                !plainLyrics.isNullOrEmpty() -> "Plain"
                else -> "No Lyrics"
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(lyricsDismissConnection)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF05080D),
                                Color(0xFF0A1119),
                                Color(0xFF030507)
                            )
                        )
                    )
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 14.dp)
                    .graphicsLayer { translationY = lyricsDismissTranslation }
            ) {
                Box(
                    modifier = Modifier.fillMaxSize()
                ) {
                    if (showLyricsMenu) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .zIndex(1f)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) { showLyricsMenu = false }
                        )
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopCenter)
                            .padding(top = 4.dp)
                            .zIndex(2f)
                            .pointerInput(isLyricsVisible) {
                                var headerDrag = 0f
                                detectVerticalDragGestures(
                                    onDragEnd = { lyricsDismissDrag = 0f; headerDrag = 0f },
                                    onDragCancel = { lyricsDismissDrag = 0f; headerDrag = 0f }
                                ) { change, dragAmount ->
                                    if (!isLyricsVisible || showLyricsMenu || showLyricsBrowser) return@detectVerticalDragGestures
                                    if (dragAmount > 0f) {
                                        change.consume()
                                        headerDrag += dragAmount
                                        lyricsDismissDrag = headerDrag
                                        if (headerDrag >= lyricsDismissThresholdPx) {
                                            lyricsDismissDrag = 0f
                                            headerDrag = 0f
                                            onLyricsVisibilityChange(false)
                                        }
                                    } else {
                                        lyricsDismissDrag = 0f
                                        headerDrag = 0f
                                    }
                                }
                            }
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = Color.White.copy(alpha = 0.12f)
                            ) {
                                IconButton(onClick = { showLyricsMenu = !showLyricsMenu }) {
                                    Icon(Icons.Default.Menu, null, tint = Color.White)
                                }
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Lyrics",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    text = listOfNotNull(
                                        currentTrack?.title?.takeIf { it.isNotBlank() },
                                        currentTrack?.artist?.takeIf { it.isNotBlank() }
                                    ).joinToString("  /  "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.68f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = Color.White.copy(alpha = 0.08f)
                            ) {
                                Text(
                                    text = lyricsMode.uppercase(),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Emerald500.copy(alpha = 0.92f),
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            Surface(
                                shape = CircleShape,
                                color = Color.White.copy(alpha = 0.12f)
                            ) {
                                IconButton(onClick = { onLyricsVisibilityChange(false) }) {
                                    Icon(Icons.Default.Close, null, tint = Color.White)
                                }
                            }
                        }

                        AnimatedVisibility(
                            visible = showLyricsMenu,
                            enter = slideInVertically(
                                initialOffsetY = { -it / 2 },
                                animationSpec = tween(240)
                            ) + fadeIn(animationSpec = tween(220)),
                            exit = slideOutVertically(
                                targetOffsetY = { -it / 2 },
                                animationSpec = tween(180)
                            ) + fadeOut(animationSpec = tween(180))
                        ) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 14.dp),
                                shape = RoundedCornerShape(28.dp),
                                color = Color(0xFF101722).copy(alpha = 0.98f),
                                shadowElevation = 18.dp
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                                    verticalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    Text(
                                        text = "Lyrics Options",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Text(
                                        text = "Change source, script, and display style without leaving lyrics mode.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.White.copy(alpha = 0.7f)
                                    )

                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                showLyricsMenu = false
                                                showLyricsBrowser = true
                                                viewModel.searchLyricsOptions(lyricsSearchTitle, lyricsSearchArtist)
                                            },
                                        shape = RoundedCornerShape(22.dp),
                                        color = Color.White.copy(alpha = 0.06f)
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 16.dp, vertical = 14.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.Default.Search, null, tint = Color.White)
                                            Spacer(modifier = Modifier.width(14.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = "Browse lyric matches",
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = Color.White
                                                )
                                                Text(
                                                    text = "Pick a different source or synced version.",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = Color.White.copy(alpha = 0.68f)
                                                )
                                            }
                                        }
                                    }

                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                showLyricsMenu = false
                                                viewModel.retryLyricsFetch()
                                            },
                                        shape = RoundedCornerShape(22.dp),
                                        color = Color.White.copy(alpha = 0.06f)
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 16.dp, vertical = 14.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.Default.Refresh, null, tint = Color.White)
                                            Spacer(modifier = Modifier.width(14.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = "Retry automatic search",
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = Color.White
                                                )
                                                Text(
                                                    text = "Fetch a fresh automatic match now.",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = Color.White.copy(alpha = 0.68f)
                                                )
                                            }
                                        }
                                    }

                                    if (lyrics?.source?.startsWith("Saved") == true) {
                                        Surface(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    showLyricsMenu = false
                                                    viewModel.clearSavedLyricsSelection()
                                                },
                                            shape = RoundedCornerShape(22.dp),
                                            color = Color.White.copy(alpha = 0.06f)
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(Icons.Default.AutoFixHigh, null, tint = Color.White)
                                                Spacer(modifier = Modifier.width(14.dp))
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = "Use automatic lyrics",
                                                        style = MaterialTheme.typography.titleSmall,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = Color.White
                                                    )
                                                    Text(
                                                        text = "Stop forcing the saved lyrics for this track.",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = Color.White.copy(alpha = 0.68f)
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    Text(
                                        text = "Script",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.White.copy(alpha = 0.62f)
                                    )

                                    Row(
                                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        LyricsScriptFilter.visibleFilters.forEach { filter ->
                                            FilterChip(
                                                selected = lyricsScriptFilter == filter,
                                                onClick = {
                                                    viewModel.setLyricsScriptFilter(filter)
                                                    showLyricsMenu = false
                                                },
                                                label = { Text(filter.label) },
                                                border = null,
                                                colors = FilterChipDefaults.filterChipColors(
                                                    selectedContainerColor = Emerald500.copy(alpha = 0.22f),
                                                    selectedLabelColor = Color.White,
                                                    containerColor = Color.White.copy(alpha = 0.06f),
                                                    labelColor = Color.White.copy(alpha = 0.88f)
                                                )
                                            )
                                        }
                                    }

                                    if (availableLyricsVariants.size > 1) {
                                        Text(
                                            text = "View",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color.White.copy(alpha = 0.62f)
                                        )

                                        Row(
                                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            availableLyricsVariants.forEach { variant ->
                                                FilterChip(
                                                    selected = selectedLyricsVariant == variant,
                                                    onClick = {
                                                        selectedLyricsVariant = variant
                                                        showLyricsMenu = false
                                                    },
                                                    label = { Text(variant.label) },
                                                    border = null,
                                                    colors = FilterChipDefaults.filterChipColors(
                                                        selectedContainerColor = Emerald500.copy(alpha = 0.22f),
                                                        selectedLabelColor = Color.White,
                                                        containerColor = Color.White.copy(alpha = 0.06f),
                                                        labelColor = Color.White.copy(alpha = 0.88f)
                                                    )
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (false) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Lyrics",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    text = listOfNotNull(
                                        currentTrack?.title?.takeIf { it.isNotBlank() },
                                        currentTrack?.artist?.takeIf { it.isNotBlank() }
                                    ).joinToString("  •  "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Zinc400,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = Color.White.copy(alpha = 0.08f)
                            ) {
                                Text(
                                    text = lyricsMode,
                                    color = Color.White.copy(alpha = 0.92f),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            IconButton(onClick = { onLyricsVisibilityChange(false) }) {
                                Icon(Icons.Default.Close, null, tint = Color.White)
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

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
                                    label = { Text(filter.label) },
                                    border = null,
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Emerald500.copy(alpha = 0.24f),
                                        selectedLabelColor = Color.White,
                                        selectedLeadingIconColor = Color.White,
                                        containerColor = Color.White.copy(alpha = 0.06f),
                                        labelColor = Color.White.copy(alpha = 0.88f)
                                    )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        if (availableLyricsVariants.size > 1) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                availableLyricsVariants.forEach { variant ->
                                    FilterChip(
                                        selected = selectedLyricsVariant == variant,
                                        onClick = { selectedLyricsVariant = variant },
                                        label = { Text(variant.label) },
                                        border = null,
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = Emerald500.copy(alpha = 0.24f),
                                            selectedLabelColor = Color.White,
                                            selectedLeadingIconColor = Color.White,
                                            containerColor = Color.White.copy(alpha = 0.06f),
                                            labelColor = Color.White.copy(alpha = 0.88f)
                                        )
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = {
                                    showLyricsBrowser = true
                                    viewModel.searchLyricsOptions(lyricsSearchTitle, lyricsSearchArtist)
                                }
                            ) {
                                Text("Search & Filter")
                            }
                            FilledTonalButton(
                                onClick = { viewModel.retryLyricsFetch() },
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = Color.White.copy(alpha = 0.08f),
                                    contentColor = Color.White
                                )
                            ) {
                                Text("Retry Auto")
                            }
                            if (lyrics?.source?.startsWith("Saved") == true) {
                                FilledTonalButton(
                                    onClick = { viewModel.clearSavedLyricsSelection() },
                                    colors = ButtonDefaults.filledTonalButtonColors(
                                        containerColor = Color.White.copy(alpha = 0.08f),
                                        contentColor = Color.White
                                    )
                                ) {
                                    Text("Use Auto")
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                    ) {
                            when {
                                isLyricsLoading -> {
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            CircularProgressIndicator(color = Emerald500)
                                            Text(
                                                text = "Searching lyrics",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                color = Color.White
                                            )
                                            Text(
                                                text = "Auto search is prioritizing ${lyricsScriptFilter.label.lowercase()} lyrics when possible.",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = Zinc400,
                                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                            )
                                        }
                                    }
                                }

                                syncedLyricsRaw.isNullOrEmpty() && plainLyrics.isNullOrEmpty() -> {
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            Text(
                                                text = "No lyrics yet",
                                                style = MaterialTheme.typography.titleLarge,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                            )
                                            Text(
                                                text = "Open the top menu to browse versions, switch lyric view, or retry automatic search.",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = Zinc400,
                                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                            )
                                        }
                                    }
                                }

                                !syncedLyricsRaw.isNullOrEmpty() -> {
                                    val lines = remember(syncedLyricsRaw) {
                                        syncedLyricsRaw.lines().mapNotNull { line ->
                                            val regex = Regex("\\[(\\d+):(\\d+\\.\\d+)\\](.*)")
                                            val match = regex.find(line)
                                            if (match != null) {
                                                val min = match.groupValues[1].toLong()
                                                val sec = match.groupValues[2].toDouble()
                                                val timeMs = (min * 60 * 1000 + sec * 1000).toLong()
                                                val text = match.groupValues[3].trim()
                                                timeMs to text
                                            } else null
                                        }
                                    }

                                    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
                                    val activeIndex = remember(progress, lines) {
                                        lines.indexOfLast { it.first <= progress }.coerceAtLeast(0)
                                    }

                                    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                                        val centeredPadding = (maxHeight / 2 - 44.dp).coerceAtLeast(96.dp)

                                        LaunchedEffect(activeIndex, lines.size, centeredPadding) {
                                            if (lines.isNotEmpty()) {
                                                listState.animateScrollToItem(
                                                    index = activeIndex,
                                                    scrollOffset = 0
                                                )
                                            }
                                        }

                                        androidx.compose.foundation.lazy.LazyColumn(
                                            state = listState,
                                            modifier = Modifier.fillMaxSize(),
                                            contentPadding = PaddingValues(
                                                horizontal = 18.dp,
                                                vertical = centeredPadding
                                            )
                                        ) {
                                            items(lines.size) { index ->
                                                val (time, text) = lines[index]
                                                val distance = kotlin.math.abs(activeIndex - index)
                                                val isCurrent = index == activeIndex
                                                val targetAlpha = when (distance) {
                                                    0 -> 1f
                                                    1 -> 0.72f
                                                    2 -> 0.46f
                                                    else -> 0.22f
                                                }
                                                val targetScale = when (distance) {
                                                    0 -> 1f
                                                    1 -> 0.95f
                                                    2 -> 0.91f
                                                    else -> 0.88f
                                                }
                                                val animatedAlpha by animateFloatAsState(
                                                    targetValue = targetAlpha,
                                                    animationSpec = tween(durationMillis = 280),
                                                    label = "lyrics_alpha"
                                                )
                                                val animatedScale by animateFloatAsState(
                                                    targetValue = targetScale,
                                                    animationSpec = tween(durationMillis = 280),
                                                    label = "lyrics_scale"
                                                )
                                                val animatedColor by animateColorAsState(
                                                    targetValue = if (isCurrent) Color.White else Color.White.copy(alpha = 0.92f),
                                                    animationSpec = tween(durationMillis = 280),
                                                    label = "lyrics_color"
                                                )

                                                Text(
                                                    text = LyricsTextVariants
                                                        .transform(text, selectedLyricsVariant)
                                                        .ifBlank { " " },
                                                    style = if (isCurrent) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleLarge,
                                                    color = animatedColor.copy(alpha = animatedAlpha),
                                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .graphicsLayer(
                                                            scaleX = animatedScale,
                                                            scaleY = animatedScale
                                                        )
                                                        .padding(vertical = 10.dp)
                                                        .clickable(enabled = text.isNotBlank()) { viewModel.seekTo(time) },
                                                    lineHeight = if (isCurrent) 40.sp else 36.sp
                                                )
                                            }
                                        }
                                    }
                                }

                                else -> {
                                    val scrollState = rememberScrollState()
                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .verticalScroll(scrollState)
                                            .padding(horizontal = 22.dp, vertical = 36.dp)
                                    ) {
                                        val displayedPlainLyrics = remember(plainLyrics, selectedLyricsVariant) {
                                            LyricsTextVariants.transform(plainLyrics.orEmpty(), selectedLyricsVariant)
                                        }
                                        Text(
                                            text = displayedPlainLyrics,
                                            style = MaterialTheme.typography.headlineSmall,
                                            color = Color.White.copy(alpha = 0.96f),
                                            fontWeight = FontWeight.Medium,
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                            modifier = Modifier.fillMaxWidth(),
                                            lineHeight = 40.sp
                                        )
                                        Spacer(modifier = Modifier.height(100.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
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
        if (showPlaylistDialog) {
            AlertDialog(
                onDismissRequest = { showPlaylistDialog = false },
                title = { Text("Add to Playlist", color = MaterialTheme.colorScheme.onSurface) },
                text = {
                    if (userPlaylists.isEmpty()) {
                        Text("No playlists created yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth().heightIn(max = 350.dp)
                        ) {
                            // CREATE NEW PLAYLIST OPTION
                            item {
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
                            }

                            items(items = userPlaylists) { playlist: com.israrxy.raazi.data.db.PlaylistEntity ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            if (currentTrack != null) {
                                                viewModel.addToPlaylist(playlist.id, currentTrack)
                                                showPlaylistDialog = false
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
                },
                confirmButton = {
                    TextButton(onClick = { 
                        showPlaylistDialog = false
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
                            if (newPlaylistName.isNotBlank() && currentTrack != null) {
                                viewModel.createPlaylist(newPlaylistName)
                                // We need to wait a bit or observe the new playlist to add the track
                                // For simplicity/speed, we'll assume the user might want a separate step,
                                // but ideally we create AND add.
                                // The ViewModel.createPlaylist likely doesn't return the ID easily here.
                                // Let's just create it and notify.
                                showCreateDialog = false
                                newPlaylistName = ""
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
        }
    }
private fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%d:%02d", minutes, seconds)
}
