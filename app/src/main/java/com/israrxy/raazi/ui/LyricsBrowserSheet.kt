package com.israrxy.raazi.ui

import android.view.HapticFeedbackConstants
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.israrxy.raazi.data.lyrics.LyricsScriptDetector
import com.israrxy.raazi.data.lyrics.LyricsScriptFilter
import com.israrxy.raazi.data.remote.Lyrics
import com.israrxy.raazi.data.remote.LyricsProviderFilter
import com.israrxy.raazi.data.remote.LyricsSearchResult
import com.israrxy.raazi.data.remote.MultiSourceLyricsAggregator
import com.israrxy.raazi.ui.theme.Emerald400
import com.israrxy.raazi.ui.theme.Emerald500
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
    val view = LocalView.current
    var selectedProvider by remember { mutableStateOf(LyricsProviderFilter.ALL) }
    var showCustomQueryInputs by remember { mutableStateOf(false) }
    var expandedResultKey by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        if (lyricsSearchResults.isEmpty() && !isLyricsSearchLoading && lyricsSearchTitle.isNotBlank()) {
            viewModel.searchLyricsOptions(lyricsSearchTitle, lyricsSearchArtist)
        }
    }

    val languages = remember(lyricsSearchResults) {
        listOf("All") + lyricsSearchResults
            .mapNotNull { it.lyrics.language?.takeIf { lang -> lang.isNotBlank() } }
            .distinct()
    }

    val filteredResults = remember(lyricsSearchResults, selectedProvider, selectedLyricsLanguage, lyricsScriptFilter) {
        lyricsSearchResults.filter { result ->
            val providerMatches = selectedProvider.matches(result.lyrics.source)
            val languageMatches = selectedLyricsLanguage == "All" ||
                (result.lyrics.language?.ifBlank { "Unknown" } ?: "Unknown") == selectedLyricsLanguage
            val scriptMatches = LyricsScriptDetector.matches(lyricsScriptFilter, result.lyrics)
            providerMatches && languageMatches && scriptMatches
        }
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = {
            onDismiss()
            viewModel.clearLyricsSearchResults()
        },
        sheetState = sheetState,
        containerColor = Color(0xFF0B0F15),
        contentColor = Color.White,
        scrimColor = Color.Black.copy(alpha = 0.72f),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 6.dp)
                    .width(42.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color.White.copy(alpha = 0.22f))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 18.dp)
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(
                                            Emerald500.copy(alpha = 0.32f),
                                            Emerald400.copy(alpha = 0.12f)
                                        )
                                    )
                                )
                                .border(1.dp, Emerald400.copy(alpha = 0.35f), RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lyrics,
                                contentDescription = null,
                                tint = Emerald400,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Lyrics Studio & Providers",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White
                            )
                            Text(
                                text = "Musixmatch • LRCLIB • Genius • Lyrica • YT Music",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.55f)
                            )
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (lyrics?.source?.startsWith("Saved") == true) {
                        Surface(
                            onClick = {
                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                viewModel.clearSavedLyricsSelection()
                                Toast.makeText(context, "Restored automatic lyrics match", Toast.LENGTH_SHORT).show()
                            },
                            shape = RoundedCornerShape(999.dp),
                            color = Color(0xFFF87171).copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, Color(0xFFF87171).copy(alpha = 0.35f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.RestartAlt,
                                    contentDescription = "Unpin",
                                    tint = Color(0xFFF87171),
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "Unpin",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFF87171)
                                )
                            }
                        }
                    }

                    Surface(
                        onClick = { showCustomQueryInputs = !showCustomQueryInputs },
                        shape = RoundedCornerShape(999.dp),
                        color = if (showCustomQueryInputs) Emerald500.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.08f),
                        border = BorderStroke(
                            1.dp,
                            if (showCustomQueryInputs) Emerald400.copy(alpha = 0.45f) else Color.White.copy(alpha = 0.12f)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.EditNote,
                                contentDescription = "Edit search query",
                                tint = if (showCustomQueryInputs) Emerald400 else Color.White.copy(alpha = 0.85f),
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = "Custom Query",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = if (showCustomQueryInputs) Emerald400 else Color.White.copy(alpha = 0.85f)
                            )
                        }
                    }
                }
            }

            // Collapsible Custom Search Capsule
            AnimatedVisibility(
                visible = showCustomQueryInputs,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color.White.copy(alpha = 0.05f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            GlassSearchInputField(
                                value = lyricsSearchTitle,
                                onValueChange = onTitleChange,
                                label = "Track Title",
                                modifier = Modifier.weight(1f)
                            )
                            GlassSearchInputField(
                                value = lyricsSearchArtist,
                                onValueChange = onArtistChange,
                                label = "Artist",
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Button(
                            onClick = {
                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                onLanguageChange("All")
                                selectedProvider = LyricsProviderFilter.ALL
                                viewModel.searchLyricsOptions(lyricsSearchTitle, lyricsSearchArtist)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Emerald400,
                                contentColor = Color.Black
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                modifier = Modifier.size(17.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isLyricsSearchLoading) "Searching All 5 Engines..." else "Search All Engines",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            LyricsBrowserFilterStrip(
                lyricsSearchResults = lyricsSearchResults,
                selectedProvider = selectedProvider,
                onSelectProvider = {
                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    selectedProvider = it
                },
                lyricsScriptFilter = lyricsScriptFilter,
                onSelectScriptFilter = { viewModel.setLyricsScriptFilter(it) },
                languages = languages,
                selectedLyricsLanguage = selectedLyricsLanguage,
                onLanguageChange = onLanguageChange
            )

            // Results Viewport
            when {
                isLyricsSearchLoading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(260.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(
                                color = Emerald400,
                                strokeWidth = 2.5.dp,
                                modifier = Modifier.size(32.dp)
                            )
                            Text(
                                text = "Fetching from Musixmatch, LRCLIB, Genius, Lyrica & YT Music...",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.65f)
                            )
                        }
                    }
                }

                filteredResults.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(240.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SearchOff,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.35f),
                                modifier = Modifier.size(36.dp)
                            )
                            Text(
                                text = "No lyrics matched this filter",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White.copy(alpha = 0.85f)
                            )
                            TextButton(
                                onClick = {
                                    selectedProvider = LyricsProviderFilter.ALL
                                    onLanguageChange("All")
                                    viewModel.searchLyricsOptions(lyricsSearchTitle, lyricsSearchArtist)
                                }
                            ) {
                                Text("Reset filters & search again", color = Emerald400)
                            }
                        }
                    }
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 430.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(
                            items = filteredResults,
                            key = { "${it.lyrics.source}_${it.lyrics.id}_${it.lyrics.trackName}" }
                        ) { result ->
                            val itemKey = "${result.lyrics.source}_${result.lyrics.id}_${result.lyrics.trackName}"
                            val isActive = lyrics != null &&
                                lyrics.trackName.equals(result.lyrics.trackName, ignoreCase = true) &&
                                lyrics.source.removePrefix("Saved ").equals(result.lyrics.source, ignoreCase = true)
                            val isExpanded = expandedResultKey == itemKey

                            LyricsCandidateCard(
                                result = result,
                                isActive = isActive,
                                isExpanded = isExpanded,
                                onToggleExpand = {
                                    expandedResultKey = if (isExpanded) null else itemKey
                                },
                                onSelect = {
                                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                    viewModel.saveLyricsSelection(result)
                                    onDismiss()
                                    Toast.makeText(
                                        context,
                                        "Switched to ${result.lyrics.source} lyrics",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

