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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.israrxy.raazi.data.lyrics.LyricsScriptDetector
import com.israrxy.raazi.data.lyrics.LyricsScriptFilter
import com.israrxy.raazi.data.remote.Lyrics
import com.israrxy.raazi.data.remote.LyricsProviderFilter
import com.israrxy.raazi.data.remote.LyricsSearchResult
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
        containerColor = Color(0xFF101012),
        contentColor = Color.White,
        scrimColor = Color.Black.copy(alpha = 0.68f),
        shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 4.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color.White.copy(alpha = 0.18f))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
                .navigationBarsPadding()
                .padding(horizontal = 18.dp)
                .padding(bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Lyrics Source",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                    Text(
                        text = "${filteredResults.size} matches across 5 providers",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.48f),
                        fontSize = 12.sp
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (lyrics?.source?.startsWith("Saved") == true) {
                        Surface(
                            onClick = {
                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                viewModel.clearSavedLyricsSelection()
                                Toast.makeText(context, "Restored automatic match", Toast.LENGTH_SHORT).show()
                            },
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF1A1A1E),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.RestartAlt,
                                    contentDescription = "Reset to automatic",
                                    tint = Color.White.copy(alpha = 0.78f),
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "Auto",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Medium,
                                    color = Color.White.copy(alpha = 0.78f)
                                )
                            }
                        }
                    }

                    Surface(
                        onClick = { showCustomQueryInputs = !showCustomQueryInputs },
                        shape = RoundedCornerShape(8.dp),
                        color = if (showCustomQueryInputs) Color.White else Color(0xFF1A1A1E),
                        border = BorderStroke(
                            1.dp,
                            if (showCustomQueryInputs) Color.White else Color.White.copy(alpha = 0.08f)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = "Custom search",
                                tint = if (showCustomQueryInputs) Color(0xFF09090B) else Color.White.copy(alpha = 0.78f),
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "Custom Search",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium,
                                color = if (showCustomQueryInputs) Color(0xFF09090B) else Color.White.copy(alpha = 0.78f)
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = showCustomQueryInputs,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFF161619),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.07f))
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
                                label = "Title",
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
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White,
                                contentColor = Color(0xFF09090B)
                            ),
                            contentPadding = PaddingValues(vertical = 10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isLyricsSearchLoading) "Searching..." else "Search",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold
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

            when {
                isLyricsSearchLoading -> {
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
                            CircularProgressIndicator(
                                color = Color.White.copy(alpha = 0.85f),
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(26.dp)
                            )
                            Text(
                                text = "Searching providers...",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.52f)
                            )
                        }
                    }
                }

                filteredResults.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SearchOff,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.32f),
                                modifier = Modifier.size(30.dp)
                            )
                            Text(
                                text = "No matching lyrics found",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = Color.White.copy(alpha = 0.78f)
                            )
                            TextButton(
                                onClick = {
                                    selectedProvider = LyricsProviderFilter.ALL
                                    onLanguageChange("All")
                                    viewModel.searchLyricsOptions(lyricsSearchTitle, lyricsSearchArtist)
                                }
                            ) {
                                Text(
                                    text = "Reset filters",
                                    color = Color.White.copy(alpha = 0.85f),
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
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
                                        "Using ${result.lyrics.source}",
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
