package com.israrxy.raazi.ui

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.israrxy.raazi.data.db.PlaylistEntity
import com.israrxy.raazi.data.lyrics.LyricsScriptDetector
import com.israrxy.raazi.data.lyrics.LyricsScriptFilter
import com.israrxy.raazi.data.remote.Lyrics
import com.israrxy.raazi.data.remote.LyricsSearchResult
import com.israrxy.raazi.model.MusicItem
import com.israrxy.raazi.model.PlaybackVideoQuality
import com.israrxy.raazi.ui.components.PlayerOptionsSheet
import com.israrxy.raazi.ui.components.QueueBottomSheet
import com.israrxy.raazi.ui.components.SleepTimerDialog
import com.israrxy.raazi.ui.components.SongCreditsBottomSheet
import com.israrxy.raazi.viewmodel.MusicPlayerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LyricsBrowserBottomSheet(
    viewModel: MusicPlayerViewModel,
    lyrics: Lyrics?,
    lyricsSearchResults: List<LyricsSearchResult>,
    isLyricsSearchLoading: Boolean,
    lyricsScriptFilter: LyricsScriptFilter,
    lyricsSearchTitle: String,
    onTitleChange: (String) -> Unit,
    lyricsSearchArtist: String,
    onArtistChange: (String) -> Unit,
    selectedLyricsLanguage: String,
    onLanguageChange: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
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
            onDismiss()
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
            Text("Browse Lyrics", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                text = "Search manually, compare versions, then save the right one for this song.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedTextField(
                value = lyricsSearchTitle,
                onValueChange = onTitleChange,
                label = { Text("Song Title") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = lyricsSearchArtist,
                onValueChange = onArtistChange,
                label = { Text("Artist") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = {
                        onLanguageChange("All")
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
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
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
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    languages.forEach { language ->
                        FilterChip(
                            selected = selectedLyricsLanguage == language,
                            onClick = { onLanguageChange(language) },
                            label = { Text(language) }
                        )
                    }
                }
            }

            when {
                isLyricsSearchLoading -> {
                    Box(modifier = Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }

                filteredResults.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) {
                        Text("No lyric versions found yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(filteredResults) { result ->
                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(result.lyrics.trackName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                            Text(
                                                result.lyrics.artistName,
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

                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        AssistChip(onClick = {}, label = { Text(LyricsScriptDetector.detect(result.lyrics).label) })
                                        AssistChip(onClick = {}, label = { Text(result.lyrics.language ?: "Unknown") })
                                        AssistChip(onClick = {}, label = { Text(result.lyrics.source) })
                                        AssistChip(onClick = {}, label = { Text("%.0fs".format(result.lyrics.duration)) })
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
                                            onDismiss()
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlayerSheetsAndDialogsHost(
    viewModel: MusicPlayerViewModel,
    currentTrack: MusicItem?,
    userPlaylists: List<PlaylistEntity>,
    videoQuality: PlaybackVideoQuality,
    showSleepTimer: Boolean,
    onDismissSleepTimer: () -> Unit,
    showPlaylistDialog: Boolean,
    onDismissPlaylistDialog: () -> Unit,
    onOpenCreateDialogFromPlaylist: () -> Unit,
    showCreateDialog: Boolean,
    onDismissCreateDialog: () -> Unit,
    newPlaylistName: String,
    onNewPlaylistNameChange: (String) -> Unit,
    playlistTargetTrack: MusicItem?,
    onClearPlaylistTargetTrack: () -> Unit,
    showPlayNextSheet: Boolean,
    onDismissPlayNextSheet: () -> Unit,
    showOptionsSheet: Boolean,
    optionsSheetState: SheetState,
    onDismissOptionsSheet: () -> Unit,
    onSaveToPlaylistFromOptions: () -> Unit,
    onViewCreditsFromOptions: () -> Unit,
    showCreditsSheet: Boolean,
    onDismissCreditsSheet: () -> Unit
) {
    val context = LocalContext.current

    if (showSleepTimer) {
        SleepTimerDialog(
            onDismiss = onDismissSleepTimer,
            onTimerSet = { minutes ->
                com.israrxy.raazi.service.SleepTimer.getInstance().start(minutes) { viewModel.pause() }
                onDismissSleepTimer()
            }
        )
    }

    if (showPlaylistDialog) {
        AlertDialog(
            onDismissRequest = {
                onDismissPlaylistDialog()
                onClearPlaylistTargetTrack()
            },
            title = { Text("Add to Playlist", color = MaterialTheme.colorScheme.onSurface) },
            text = {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onDismissPlaylistDialog()
                                onOpenCreateDialogFromPlaylist()
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
                        Text("No playlists yet", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 12.dp))
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp)) {
                            items(items = userPlaylists) { playlist: PlaylistEntity ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            val targetTrack = playlistTargetTrack ?: currentTrack
                                            if (targetTrack != null) {
                                                viewModel.addToPlaylist(playlist.id, targetTrack)
                                                onDismissPlaylistDialog()
                                                onClearPlaylistTargetTrack()
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
                    onDismissPlaylistDialog()
                    onClearPlaylistTargetTrack()
                }) {
                    Text("Dismiss", color = MaterialTheme.colorScheme.primary)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = onDismissCreateDialog,
            title = { Text("Create Playlist", color = MaterialTheme.colorScheme.onSurface) },
            text = {
                TextField(
                    value = newPlaylistName,
                    onValueChange = onNewPlaylistNameChange,
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
                            onDismissCreateDialog()
                            onNewPlaylistNameChange("")
                            onClearPlaylistTargetTrack()
                            Toast.makeText(context, "Playlist created! Add the song from the menu.", Toast.LENGTH_LONG).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Create", color = MaterialTheme.colorScheme.onPrimary)
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissCreateDialog) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    if (showPlayNextSheet) {
        ModalBottomSheet(
            onDismissRequest = onDismissPlayNextSheet,
            containerColor = MaterialTheme.colorScheme.surface,
            dragHandle = { BottomSheetDefaults.DragHandle() }
        ) {
            QueueBottomSheet(
                viewModel = viewModel,
                onDismiss = onDismissPlayNextSheet,
                onSaveQueueAsPlaylist = { tracks ->
                    viewModel.saveCurrentQueueAsPlaylist(tracks = tracks)
                    Toast.makeText(context, "Saved queue as playlist!", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }

    if (showOptionsSheet) {
        ModalBottomSheet(
            onDismissRequest = onDismissOptionsSheet,
            sheetState = optionsSheetState,
            containerColor = MaterialTheme.colorScheme.surface,
            dragHandle = { BottomSheetDefaults.DragHandle() }
        ) {
            PlayerOptionsSheet(
                track = currentTrack,
                viewModel = viewModel,
                onDismiss = onDismissOptionsSheet,
                onSaveToPlaylist = onSaveToPlaylistFromOptions,
                onViewCredits = onViewCreditsFromOptions,
                onSetRingtone = { currentTrack?.let { track -> viewModel.downloadForRingtone(track) } },
                currentVideoQuality = videoQuality,
                onSelectVideoQuality = { viewModel.setPlaybackVideoQuality(it) }
            )
        }
    }

    if (showCreditsSheet) {
        ModalBottomSheet(
            onDismissRequest = onDismissCreditsSheet,
            containerColor = MaterialTheme.colorScheme.surface,
            dragHandle = { BottomSheetDefaults.DragHandle() }
        ) {
            SongCreditsBottomSheet(
                viewModel = viewModel,
                onDismiss = onDismissCreditsSheet
            )
        }
    }
}
