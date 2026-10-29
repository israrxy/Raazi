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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
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
import com.israrxy.raazi.ui.theme.Emerald400
import com.israrxy.raazi.ui.theme.Emerald500

private val SHEET_TAG_STRIP_REGEX = Regex("""\[\d+:\d+(?:\.\d+)?\]|<\d+:\d+(?:\.\d+)?>""")

internal fun providerAccentColor(source: String): Color {
    return when {
        source.contains("Musixmatch", ignoreCase = true) -> Color(0xFFFF6B8B)
        source.contains("LRCLIB", ignoreCase = true) -> Emerald400
        source.contains("Genius", ignoreCase = true) -> Color(0xFFFACC15)
        source.contains("Lyrica", ignoreCase = true) -> Color(0xFFA78BFA)
        source.contains("YT Music", ignoreCase = true) -> Color(0xFFF87171)
        else -> Emerald400
    }
}

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
            horizontalArrangement = Arrangement.spacedBy(8.dp),
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
                val accent = if (provider == LyricsProviderFilter.ALL) {
                    Emerald400
                } else {
                    providerAccentColor(provider.label)
                }

                Surface(
                    onClick = { onSelectProvider(provider) },
                    shape = RoundedCornerShape(999.dp),
                    color = if (isSelected) accent.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.06f),
                    border = BorderStroke(
                        1.dp,
                        if (isSelected) accent else Color.White.copy(alpha = 0.10f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(if (isSelected) accent else accent.copy(alpha = 0.55f))
                        )
                        Text(
                            text = provider.label,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) Color.White else Color.White.copy(alpha = 0.72f)
                        )
                        if (count > 0) {
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = if (isSelected) accent.copy(alpha = 0.30f) else Color.White.copy(alpha = 0.10f)
                            ) {
                                Text(
                                    text = count.toString(),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp,
                                    color = if (isSelected) Color.White else Color.White.copy(alpha = 0.75f),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 2.dp)
        ) {
            items(LyricsScriptFilter.entries) { filter ->
                val selected = lyricsScriptFilter == filter
                FilterChip(
                    selected = selected,
                    onClick = { onSelectScriptFilter(filter) },
                    label = { Text(filter.label, fontSize = 11.sp) },
                    leadingIcon = if (selected) {
                        {
                            Icon(
                                Icons.Default.Done,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    } else null,
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Emerald500.copy(alpha = 0.22f),
                        selectedLabelColor = Emerald400,
                        selectedLeadingIconColor = Emerald400,
                        containerColor = Color.White.copy(alpha = 0.05f),
                        labelColor = Color.White.copy(alpha = 0.68f)
                    )
                )
            }

            items(languages) { lang ->
                val selected = selectedLyricsLanguage == lang
                FilterChip(
                    selected = selected,
                    onClick = { onLanguageChange(if (selected) "None" else lang) },
                    label = { Text("Translate: $lang", fontSize = 11.sp) },
                    leadingIcon = {
                        Icon(
                            if (selected) Icons.Default.Done else Icons.Default.Language,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFF38BDF8).copy(alpha = 0.22f),
                        selectedLabelColor = Color(0xFF7DD3FC),
                        selectedLeadingIconColor = Color(0xFF7DD3FC),
                        containerColor = Color.White.copy(alpha = 0.05f),
                        labelColor = Color.White.copy(alpha = 0.68f)
                    )
                )
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
            .clip(RoundedCornerShape(14.dp))
            .background(Color.Black.copy(alpha = 0.42f))
            .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = Emerald400.copy(alpha = 0.85f),
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(2.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            ),
            cursorBrush = SolidColor(Emerald400),
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
    val providerColor = providerAccentColor(result.lyrics.source)

    val syncBadgeText = when {
        isWordSynced -> "WORD SYNC"
        isLineSynced -> "SYNCED LRC"
        else -> "PLAIN TEXT"
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
        if (cleanLines.isEmpty()) {
            "No preview available"
        } else if (isExpanded) {
            cleanLines.take(12).joinToString("\n")
        } else {
            cleanLines.take(3).joinToString("\n")
        }
    }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (isActive) Color(0xFF13221E) else Color(0xFF141B24),
        border = BorderStroke(
            width = if (isActive) 1.5.dp else 1.dp,
            color = if (isActive) Emerald400.copy(alpha = 0.65f) else Color.White.copy(alpha = 0.08f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = providerColor.copy(alpha = 0.16f),
                        border = BorderStroke(1.dp, providerColor.copy(alpha = 0.45f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(providerColor)
                            )
                            Text(
                                text = result.lyrics.source.uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = providerColor,
                                fontSize = 10.sp
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = if (isWordSynced || isLineSynced) {
                            Emerald500.copy(alpha = 0.16f)
                        } else {
                            Color.White.copy(alpha = 0.08f)
                        }
                    ) {
                        Text(
                            text = syncBadgeText,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isWordSynced || isLineSynced) Emerald400 else Color.White.copy(alpha = 0.7f),
                            fontSize = 10.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

                    if (isActive) {
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = Emerald400
                        ) {
                            Text(
                                text = "ACTIVE",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.Black,
                                fontSize = 9.sp,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                            )
                        }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = Color.White.copy(alpha = 0.08f)
                ) {
                    Text(
                        text = "${result.matchScore}% Match",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.88f),
                        fontSize = 10.sp,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = result.lyrics.trackName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
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
                        color = Color.White.copy(alpha = 0.58f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.Black.copy(alpha = 0.36f))
                    .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(14.dp))
                    .clickable { onToggleExpand() }
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Text(
                    text = previewText,
                    style = MaterialTheme.typography.bodySmall.copy(lineHeight = 19.sp),
                    color = Color.White.copy(alpha = 0.82f),
                    maxLines = if (isExpanded) 12 else 3,
                    overflow = TextOverflow.Ellipsis
                )
                if (cleanLines.size > 3) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (isExpanded) "Show less" else "Tap to preview ${cleanLines.size} lines",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = Emerald400.copy(alpha = 0.85f),
                        fontSize = 10.sp
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    onClick = onSelect,
                    shape = RoundedCornerShape(12.dp),
                    color = if (isActive) Emerald500.copy(alpha = 0.24f) else Emerald400
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = if (isActive) Icons.Default.CheckCircle else Icons.Default.Done,
                            contentDescription = null,
                            tint = if (isActive) Emerald400 else Color.Black,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = if (isActive) "Pinned (${result.lyrics.source})" else "Use ${result.lyrics.source}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isActive) Emerald400 else Color.Black
                        )
                    }
                }
            }
        }
    }
}
