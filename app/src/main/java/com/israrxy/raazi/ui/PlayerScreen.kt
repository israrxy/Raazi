package com.israrxy.raazi.ui

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.israrxy.raazi.data.lyrics.LyricsMetadataSanitizer
import com.israrxy.raazi.model.MusicContentType
import com.israrxy.raazi.model.MusicItem
import com.israrxy.raazi.model.savedCollectionId
import com.israrxy.raazi.ui.theme.Zinc800
import com.israrxy.raazi.utils.ThumbnailUtils
import com.israrxy.raazi.viewmodel.MusicPlayerViewModel

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

    val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()
    val currentTrack = playbackState.currentTrack
    val rawProgress = playbackState.currentPosition
    val duration = playbackState.duration
    var isDraggingSlider by remember(currentTrack?.id) { mutableStateOf(false) }
    var dragFraction by remember(currentTrack?.id) { mutableFloatStateOf(0f) }
    var pendingSeekPositionMs by remember(currentTrack?.id) { mutableStateOf<Long?>(null) }
    var pendingSeekTimestampMs by remember(currentTrack?.id) { mutableLongStateOf(0L) }

    LaunchedEffect(rawProgress, pendingSeekPositionMs) {
        val target = pendingSeekPositionMs ?: return@LaunchedEffect
        val now = android.os.SystemClock.elapsedRealtime()
        if (kotlin.math.abs(rawProgress - target) <= 1_200L || (now - pendingSeekTimestampMs) > 2_500L) {
            pendingSeekPositionMs = null
        }
    }

    val progress = when {
        isDraggingSlider && duration > 0L -> (dragFraction * duration).toLong()
        pendingSeekPositionMs != null -> pendingSeekPositionMs!!
        else -> rawProgress
    }
    val currentTrackRelated by viewModel.currentTrackRelated.collectAsStateWithLifecycle()
    val currentArtistSongs by viewModel.currentArtistSongs.collectAsStateWithLifecycle()
    val favoriteTracks by viewModel.favoriteTracks.collectAsStateWithLifecycle()
    val savedCollectionIds by viewModel.savedCollectionIds.collectAsStateWithLifecycle()
    var playlistTargetTrack by remember { mutableStateOf<MusicItem?>(null) }

    val lyrics by viewModel.lyrics.collectAsStateWithLifecycle()
    val isLyricsLoading by viewModel.isLyricsLoading.collectAsStateWithLifecycle()
    val lyricsScriptFilter by viewModel.lyricsScriptFilter.collectAsStateWithLifecycle()
    val lyricsSearchResults by viewModel.lyricsSearchResults.collectAsStateWithLifecycle()
    val isLyricsSearchLoading by viewModel.isLyricsSearchLoading.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val videoQuality by viewModel.videoQuality.collectAsStateWithLifecycle()
    var showLyricsBrowser by remember { mutableStateOf(false) }
    var showOptionsSheet by remember { mutableStateOf(false) }
    var showCreditsSheet by remember { mutableStateOf(false) }
    val optionsSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showSleepTimer by remember { mutableStateOf(false) }
    val sleepTimerActive by com.israrxy.raazi.service.SleepTimer.getInstance().isActive.collectAsStateWithLifecycle()

    val parsedSyncedLines = remember(lyrics?.syncedLyrics) {
        parseLrcLines(lyrics?.syncedLyrics)
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
    val playNextItems = remember(currentTrack?.id, currentTrackRelated, currentArtistSongs) {
        (currentTrackRelated + currentArtistSongs)
            .filter { it.id != currentTrack?.id }
            .distinctBy { it.id }
            .take(30)
    }

    LaunchedEffect(isLyricsVisible) {
        if (!isLyricsVisible) {
            showLyricsBrowser = false
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        PlayerBlurredBackground(
            currentTrack = currentTrack,
            isLoading = isLoading
        )

        AnimatedVisibility(
            visible = true,
            enter = fadeIn(tween(300)),
            exit = fadeOut(tween(300))
        ) {
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
                        PlayerTopModeHeader(
                            playbackState = playbackState,
                            isLyricsVisible = isLyricsVisible,
                            sleepTimerActive = sleepTimerActive,
                            onCollapse = onCollapse,
                            onLyricsVisibilityChange = onLyricsVisibilityChange,
                            onSetPlaybackMediaMode = { viewModel.setPlaybackMediaMode(it) },
                            onShowOptionsSheet = { showOptionsSheet = true }
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        PlayerArtworkOrVideoSection(
                            viewModel = viewModel,
                            playbackState = playbackState
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        PlayerControlsAndActionsSection(
                            viewModel = viewModel,
                            playbackState = playbackState,
                            favoriteTracks = favoriteTracks,
                            progress = progress,
                            duration = duration,
                            isDraggingSlider = isDraggingSlider,
                            dragFraction = dragFraction,
                            onSliderChange = { fraction ->
                                isDraggingSlider = true
                                dragFraction = fraction.coerceIn(0f, 1f)
                            },
                            onSliderFinished = {
                                if (duration > 0L) {
                                    val seekPosition = (dragFraction * duration).toLong().coerceIn(0L, duration)
                                    pendingSeekPositionMs = seekPosition
                                    pendingSeekTimestampMs = android.os.SystemClock.elapsedRealtime()
                                    viewModel.seekTo(seekPosition)
                                }
                                isDraggingSlider = false
                            },
                            hasQueueOrRelated = playbackState.playlist.isNotEmpty() || playNextItems.isNotEmpty(),
                            onShowQueueSheet = { showPlayNextSheet = true }
                        )
                    }

                    // --- Extra content cards (scrollable below the player) ---
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp)
                            .padding(bottom = 32.dp)
                            .navigationBarsPadding()
                    ) {
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

                        Spacer(modifier = Modifier.height(24.dp))

                        PlayerAboutArtistCard(
                            currentTrack = currentTrack,
                            currentArtistSongs = currentArtistSongs,
                            savedCollectionIds = savedCollectionIds,
                            onNavigateToArtist = { route ->
                                onCollapse()
                                navController.navigate(route)
                            },
                            onToggleSavedArtist = { artistId, artistName, thumbnailUrl ->
                                viewModel.toggleSavedArtist(
                                    artistId = artistId,
                                    artistName = artistName,
                                    thumbnailUrl = thumbnailUrl
                                )
                            }
                        )
                    }
                }
            }
        }

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
            LyricsBrowserBottomSheet(
                viewModel = viewModel,
                lyrics = lyrics,
                lyricsSearchResults = lyricsSearchResults,
                isLyricsSearchLoading = isLyricsSearchLoading,
                lyricsScriptFilter = lyricsScriptFilter,
                lyricsSearchTitle = lyricsSearchTitle,
                onTitleChange = { lyricsSearchTitle = it },
                lyricsSearchArtist = lyricsSearchArtist,
                onArtistChange = { lyricsSearchArtist = it },
                selectedLyricsLanguage = selectedLyricsLanguage,
                onLanguageChange = { selectedLyricsLanguage = it },
                onDismiss = { showLyricsBrowser = false }
            )
        }

        PlayerSheetsAndDialogsHost(
            viewModel = viewModel,
            currentTrack = currentTrack,
            userPlaylists = userPlaylists,
            videoQuality = videoQuality,
            showSleepTimer = showSleepTimer,
            onDismissSleepTimer = { showSleepTimer = false },
            showPlaylistDialog = showPlaylistDialog,
            onDismissPlaylistDialog = { showPlaylistDialog = false },
            onOpenCreateDialogFromPlaylist = { showCreateDialog = true },
            showCreateDialog = showCreateDialog,
            onDismissCreateDialog = { showCreateDialog = false },
            newPlaylistName = newPlaylistName,
            onNewPlaylistNameChange = { newPlaylistName = it },
            playlistTargetTrack = playlistTargetTrack,
            onClearPlaylistTargetTrack = { playlistTargetTrack = null },
            showPlayNextSheet = showPlayNextSheet,
            onDismissPlayNextSheet = { showPlayNextSheet = false },
            showOptionsSheet = showOptionsSheet,
            optionsSheetState = optionsSheetState,
            onDismissOptionsSheet = { showOptionsSheet = false },
            onSaveToPlaylistFromOptions = {
                playlistTargetTrack = currentTrack
                showPlaylistDialog = true
                showOptionsSheet = false
            },
            onViewCreditsFromOptions = {
                showOptionsSheet = false
                currentTrack?.let { track -> viewModel.fetchSongCredits(track) }
                showCreditsSheet = true
            },
            showCreditsSheet = showCreditsSheet,
            onDismissCreditsSheet = { showCreditsSheet = false }
        )
    }
}

@Composable
internal fun PlayerAboutArtistCard(
    currentTrack: MusicItem?,
    currentArtistSongs: List<MusicItem>,
    savedCollectionIds: Set<String>,
    onNavigateToArtist: (String) -> Unit,
    onToggleSavedArtist: (artistId: String, artistName: String, thumbnailUrl: String) -> Unit
) {
    val artistId = currentTrack?.artistId?.ifBlank { null } ?: currentTrack?.artist.orEmpty()
    val artistName = currentTrack?.artist ?: "Unknown Artist"
    val normalizedArtistId = remember(artistId, artistName) { artistId.ifBlank { artistName } }
    val savedArtistId = remember(normalizedArtistId) { savedCollectionId(MusicContentType.ARTIST, normalizedArtistId) }
    val isSavedArtist = savedArtistId in savedCollectionIds
    val artistThumbnail = currentArtistSongs.firstOrNull()?.thumbnailUrl ?: currentTrack?.thumbnailUrl ?: ""

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onNavigateToArtist("artist/${Uri.encode(normalizedArtistId)}?name=${Uri.encode(artistName)}") },
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
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
                    modifier = Modifier.size(70.dp).clip(CircleShape).background(Zinc800),
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
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .then(
                            if (isSavedArtist) {
                                Modifier.border(1.dp, Color.White.copy(alpha = 0.4f), RoundedCornerShape(999.dp))
                            } else {
                                Modifier.background(Color.White)
                            }
                        )
                        .clickable { onToggleSavedArtist(normalizedArtistId, artistName, artistThumbnail) }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
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
    }
}
