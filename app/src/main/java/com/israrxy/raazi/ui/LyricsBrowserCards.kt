package com.israrxy.raazi.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.israrxy.raazi.data.lyrics.LyricsScriptDetector
import com.israrxy.raazi.data.lyrics.LyricsScriptFilter
import com.israrxy.raazi.data.remote.LyricsProviderFilter
import com.israrxy.raazi.data.remote.LyricsSearchResult
import com.israrxy.raazi.data.remote.MultiSourceLyricsAggregator

private val SHEET_TAG_STRIP_REGEX = Regex("""\[\d+:\d+(?:\.\d+)?\]|<\d+:\d+(?:\.\d+)?>""")

internal fun providerAccentColor(source: String): Color = Color.White

@Composable
internal fun LyricsBrowserFilterStrip(
    lyricsSearchResults: List<LyricsSearchResult>,
    selectedProvider: LyricsProviderFilter,
    onSelectProvider: (LyricsProviderFilter) -> Unit,
    lyricsScriptFilter: LyricsScriptFilter,
    onSelectScriptFilter: (LyricsScriptFilter) -> Unit,
    languages: List<String>,
    selectedLyricsLanguage: String,
    onLanguageChange: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(vertical = 2.dp)
        ) {
            items(LyricsProviderFilter.entries) { provider ->
                val count = remember(lyricsSearchResults, provider) {
                    if (provider == LyricsProviderFilter.ALL) {
                        lyricsSearchResults.size
                    } else {
                        lyricsSearchResults.count { provider.matches(it.lyrics.source) }
                    }
                }
                val isSelected = selectedProvider == provider

                Surface(
                    onClick = { onSelectProvider(provider) },
                    shape = RoundedCornerShape(999.dp),
                    color = if (isSelected) Color.White else Color(0xFF1A1A1E),
                    border = BorderStroke(
                        1.dp,
                        if (isSelected) Color.White else Color.White.copy(alpha = 0.08f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = provider.label,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (isSelected) Color(0xFF09090B) else Color.White.copy(alpha = 0.72f),
                            fontSize = 12.sp
                        )
                        if (count > 0) {
                            Text(
                                text = count.toString(),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium,
                                fontSize = 11.sp,
                                color = if (isSelected) {
                                    Color(0xFF09090B).copy(alpha = 0.65f)
                                } else {
                                    Color.White.copy(alpha = 0.42f)
                                }
                            )
                        }
                    }
                }
            }
        }

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(vertical = 1.dp)
        ) {
            items(LyricsScriptFilter.entries) { filter ->
                val selected = lyricsScriptFilter == filter
                Surface(
                    onClick = { onSelectScriptFilter(filter) },
                    shape = RoundedCornerShape(8.dp),
                    color = if (selected) Color.White.copy(alpha = 0.14f) else Color.Transparent,
                    border = BorderStroke(
                        1.dp,
                        if (selected) Color.White.copy(alpha = 0.28f) else Color.White.copy(alpha = 0.07f)
                    )
                ) {
                    Text(
                        text = filter.label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (selected) Color.White else Color.White.copy(alpha = 0.56f),
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }

            items(languages.filter { it != "All" }) { lang ->
                val selected = selectedLyricsLanguage == lang
                Surface(
                    onClick = { onLanguageChange(if (selected) "All" else lang) },
                    shape = RoundedCornerShape(8.dp),
                    color = if (selected) Color.White.copy(alpha = 0.14f) else Color.Transparent,
                    border = BorderStroke(
                        1.dp,
                        if (selected) Color.White.copy(alpha = 0.28f) else Color.White.copy(alpha = 0.07f)
                    )
                ) {
                    Text(
                        text = lang.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (selected) Color.White else Color.White.copy(alpha = 0.56f),
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }
        }
    }
}

@Composable
internal fun GlassSearchInputField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF141417))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.45f),
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(2.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            ),
            cursorBrush = SolidColor(Color.White),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
internal fun LyricsCandidateCard(
    result: LyricsSearchResult,
    isActive: Boolean,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onSelect: () -> Unit
) {
    val isWordSynced = MultiSourceLyricsAggregator.hasWordSync(result.lyrics.syncedLyrics)
    val isLineSynced = !result.lyrics.syncedLyrics.isNullOrBlank()

    val syncBadgeText = when {
        isWordSynced -> "Word Sync"
        isLineSynced -> "Synced"
        else -> "Plain"
    }

    val cleanLines = remember(result.lyrics.syncedLyrics, result.lyrics.plainLyrics) {
        (result.lyrics.syncedLyrics ?: result.lyrics.plainLyrics)
            ?.lineSequence()
            ?.map { it.replace(SHEET_TAG_STRIP_REGEX, "").trim() }
            ?.filter { it.isNotBlank() }
            ?.toList()
            .orEmpty()
    }

    val previewText = remember(cleanLines, isExpanded) {
        when {
            cleanLines.isEmpty() -> "No preview available"
            isExpanded -> cleanLines.take(10).joinToString("\n")
            else -> cleanLines.take(2).joinToString("\n")
        }
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (isActive) Color(0xFF1F1F23) else Color(0xFF161619),
        border = BorderStroke(
            width = 1.dp,
            color = if (isActive) Color.White.copy(alpha = 0.32f) else Color.White.copy(alpha = 0.06f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = result.lyrics.trackName,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (isActive) {
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .clip(CircleShape)
                                    .background(Color.White),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Selected",
                                    tint = Color(0xFF09090B),
                                    modifier = Modifier.size(11.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = buildString {
                            append(result.lyrics.artistName.ifBlank { "Unknown Artist" })
                            append(" • ")
                            append(LyricsScriptDetector.detect(result.lyrics).label)
                            if (result.lyrics.duration > 0) {
                                append(" • %.0fs".format(result.lyrics.duration))
                            }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.48f),
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color.White.copy(alpha = if (isWordSynced || isLineSynced) 0.10f else 0.05f)
                    ) {
                        Text(
                            text = "${result.lyrics.source} • $syncBadgeText",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = Color.White.copy(alpha = if (isWordSynced || isLineSynced) 0.88f else 0.55f),
                            fontSize = 10.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

                    if (cleanLines.size > 2) {
                        IconButton(
                            onClick = onToggleExpand,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = if (isExpanded) "Collapse preview" else "Expand preview",
                                tint = Color.White.copy(alpha = 0.55f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            Text(
                text = previewText,
                style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp),
                color = Color.White.copy(alpha = 0.68f),
                maxLines = if (isExpanded) 10 else 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
