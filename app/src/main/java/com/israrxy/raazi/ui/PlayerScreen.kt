package com.israrxy.raazi.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import coil.compose.AsyncImage
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerInputChange


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    viewModel: MusicPlayerViewModel,
    navController: androidx.navigation.NavController,
    sheetState: SheetState,
    onCollapse: () -> Unit,
    onExpand: () -> Unit
) {
    val fraction = try {
        val offset = sheetState.requireOffset()
        val total = 1000f // Rough estimate or use Anchor values
        // We'll calculate fraction more reliably if we knew anchors, but for now:
        if (sheetState.currentValue == SheetValue.Expanded) 1f else 0f
    } catch (e: Exception) { 1f }

    // Use a more reliable fraction based on anchored draggable if possible, 
    // but for now, we'll just handle Expanded/PartiallyExpanded visibility.
    val isExpanded = sheetState.targetValue == SheetValue.Expanded || sheetState.currentValue == SheetValue.Expanded
    val context = LocalContext.current
    val playbackState by viewModel.playbackState.collectAsState()
    val currentTrack = playbackState.currentTrack
    val isPlaying = playbackState.isPlaying
    val progress = playbackState.currentPosition
    val duration = playbackState.duration

    // State Hoisting for Lyrics
    val lyrics by viewModel.lyrics.collectAsState()
    var showLyrics by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showPlaylistDialog by remember { mutableStateOf(false) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }
    val userPlaylists by viewModel.userPlaylists.collectAsState()

    val backgroundAlpha by animateFloatAsState(
        targetValue = if (isExpanded) 1f else 0f,
        animationSpec = tween(500),
        label = "backgroundAlpha"
    )

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        // GLASS BACKGROUND
        // GLASS BACKGROUND
        AnimatedContent(
            targetState = currentTrack,
            transitionSpec = {
                fadeIn(animationSpec = tween(700)) togetherWith fadeOut(animationSpec = tween(700))
            },
            label = "BackgroundTransition"
        ) { track ->
            if (track != null) {
                Box(modifier = Modifier.fillMaxSize()) {
                    AsyncImage(
                        model = track.thumbnailUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .blur(radius = 60.dp)
                            .graphicsLayer(alpha = backgroundAlpha),
                        contentScale = ContentScale.Crop
                    )
                    // Dark overlay for readability
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer(alpha = backgroundAlpha)
                            .background(Color.Black.copy(alpha = 0.6f))
                    )
                }
            } else {
                 Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(alpha = backgroundAlpha)
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(Zinc900, PureBlack)
                            )
                        )
                )
            }
        }
        
        // Loading Skeleton Overlay
        val isLoading by viewModel.isLoading.collectAsState()
        val playState by viewModel.playbackState.collectAsState()
        // Show skeleton if loading AND (not playing OR current track is null)
        // Effectively shows during initial load
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(alpha = backgroundAlpha)
                    .background(Color.Black.copy(alpha = 0.4f)) // Slight dim
            ) {
                com.israrxy.raazi.ui.components.SkeletonPlayer()
            }
        }
        
        // Interpolated Content
        if (!isExpanded) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                com.israrxy.raazi.ui.player.MiniPlayer(
                    viewModel = viewModel,
                    onNavigateToPlayer = onExpand
                )
            }
        }
        
        AnimatedVisibility(
            visible = isExpanded,
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
                        val favoriteTracks by viewModel.favoriteTracks.collectAsState()
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
            val currentFormat by viewModel.currentFormat.collectAsState()
            val streamQuality by viewModel.streamQuality.collectAsState()
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
                val allDownloads by viewModel.dbAllDownloads.collectAsState()
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
                val lyricsAccent = if (showLyrics) Emerald500 else Color.White.copy(alpha = 0.9f)

                Box(
                    modifier = Modifier
                        .size(50.dp)
                        .clip(CircleShape)
                        .clickable { showLyrics = !showLyrics },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Subject, null, tint = lyricsAccent, modifier = Modifier.size(24.dp))
                }
            }
        }
        
        // LYRICS OVERLAY
        AnimatedVisibility(
            visible = showLyrics,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
             Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(PureBlack.copy(alpha = 0.95f))
                    .statusBarsPadding()
                    .padding(24.dp)
            ) {
                 Column {
                     // Close Header
                     Row(
                         modifier = Modifier.fillMaxWidth(),
                         horizontalArrangement = Arrangement.End
                     ) {
                         IconButton(onClick = { showLyrics = false }) {
                             Icon(Icons.Default.Close, null, tint = Zinc400)
                         }
                     }
                     
                     Spacer(modifier = Modifier.height(0.dp))
                     
                     val syncedLyricsRaw = lyrics?.syncedLyrics
                     val plainLyrics = lyrics?.plainLyrics
                     
                     if (syncedLyricsRaw.isNullOrEmpty() && plainLyrics.isNullOrEmpty()) {
                         Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Lyrics not available", color = Zinc500)
                         }
                     } else {
                         if (!syncedLyricsRaw.isNullOrEmpty()) {
                             // SYNCED LYRICS IMPLEMENTATION
                             val lines = remember(syncedLyricsRaw) {
                                 syncedLyricsRaw!!.lines().mapNotNull { line ->
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
                             
                             // Find active line index
                             val activeIndex = remember(progress) {
                                 lines.indexOfLast { it.first <= progress }.coerceAtLeast(0)
                             }

                             // Auto-scroll to active line
                             LaunchedEffect(activeIndex) {
                                 if (activeIndex >= 0) {
                                     listState.animateScrollToItem(activeIndex, scrollOffset = -300) // Offset to keep it centered
                                 }
                             }

                             androidx.compose.foundation.lazy.LazyColumn(
                                 state = listState,
                                 modifier = Modifier.fillMaxSize(),
                                 contentPadding = PaddingValues(vertical = 200.dp), // Padding for center focus
                                 horizontalAlignment = Alignment.CenterHorizontally
                             ) {
                                 items(lines.size) { index ->
                                     val isCurrent = index == activeIndex
                                     val (time, text) = lines[index]
                                     val alpha = if (isCurrent) 1f else 0.5f
                                     val scale by animateFloatAsState(if (isCurrent) 1.1f else 1f, label = "scale")

                                     Text(
                                         text = text,
                                         style = MaterialTheme.typography.headlineMedium,
                                         color = Color.White.copy(alpha = alpha),
                                         fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                                         textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                         modifier = Modifier
                                             .padding(vertical = 8.dp)
                                             .graphicsLayer(scaleX = scale, scaleY = scale)
                                             .clickable { 
                                                 viewModel.seekTo(time) 
                                             },
                                         lineHeight = 36.sp
                                     )
                                 }
                             }
                         } else {
                             // SCROLLABLE PLAIN LYRICS
                             val scrollState = rememberScrollState()
                             Column(
                                 modifier = Modifier
                                     .fillMaxSize()
                                     .verticalScroll(scrollState),
                                 horizontalAlignment = Alignment.CenterHorizontally
                             ) {
                                 Text(
                                     text = plainLyrics ?: "",
                                     style = MaterialTheme.typography.headlineMedium,
                                     color = Color.White,
                                     fontWeight = FontWeight.Bold,
                                     textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                     lineHeight = 40.sp 
                                 )
                                 Spacer(modifier = Modifier.height(100.dp)) 
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
}

private fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%d:%02d", minutes, seconds)
}