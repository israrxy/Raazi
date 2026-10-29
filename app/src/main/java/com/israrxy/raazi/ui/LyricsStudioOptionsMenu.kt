package com.israrxy.raazi.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.israrxy.raazi.data.lyrics.LyricsScriptFilter
import com.israrxy.raazi.data.lyrics.LyricsViewVariant
import com.israrxy.raazi.data.remote.Lyrics
import com.israrxy.raazi.ui.theme.Emerald400
import com.israrxy.raazi.ui.theme.Emerald500

@Composable
internal fun BoxScope.LyricsSettingsDropdownMenu(
    visible: Boolean,
    lyrics: Lyrics?,
    hasWordSync: Boolean,
    isSynced: Boolean,
    availableVariants: List<LyricsViewVariant>,
    selectedVariant: LyricsViewVariant,
    onSelectVariant: (LyricsViewVariant) -> Unit,
    hasTranslationSupport: Boolean,
    showTranslation: Boolean,
    translateLang: String,
    onToggleTranslation: () -> Unit,
    lyricsScriptFilter: LyricsScriptFilter,
    onDismiss: () -> Unit,
    onOpenLyricsBrowser: () -> Unit,
    onRetryLyricsFetch: () -> Unit,
    onClearSavedLyricsSelection: () -> Unit,
    onSelectScriptFilter: (LyricsScriptFilter) -> Unit
) {
    val cleanSource = remember(lyrics?.source) {
        lyrics?.source?.removePrefix("Saved ")?.trim().orEmpty().ifBlank { "Auto Match" }
    }
    val isPinned = lyrics?.source?.startsWith("Saved") == true
    val providerColor = remember(cleanSource) { providerAccentColor(cleanSource) }
    val syncLabel = when {
        hasWordSync -> "WORD SYNC"
        isSynced -> "SYNCED LRC"
        lyrics != null -> "PLAIN TEXT"
        else -> "NO LYRICS"
    }

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(initialOffsetY = { -it / 4 }) + fadeIn() + scaleIn(initialScale = 0.96f),
        exit = slideOutVertically(targetOffsetY = { -it / 4 }) + fadeOut() + scaleOut(targetScale = 0.96f),
        modifier = Modifier
            .align(Alignment.TopCenter)
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .zIndex(10f)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(26.dp),
            color = Color(0xFF0E131A).copy(alpha = 0.98f),
            shadowElevation = 24.dp,
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "Lyrics Studio Options",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                        Text(
                            text = "Multi-source engine • Karaoke & script controls",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.56f)
                        )
                    }

                    Surface(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(999.dp),
                        color = Color.White.copy(alpha = 0.08f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close options",
                            tint = Color.White.copy(alpha = 0.8f),
                            modifier = Modifier
                                .padding(6.dp)
                                .size(16.dp)
                        )
                    }
                }

                Surface(
                    onClick = {
                        onDismiss()
                        onOpenLyricsBrowser()
                    },
                    shape = RoundedCornerShape(18.dp),
                    color = Color(0xFF141C25),
                    border = BorderStroke(1.dp, providerColor.copy(alpha = 0.35f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(providerColor.copy(alpha = 0.16f), RoundedCornerShape(12.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.GraphicEq,
                                    contentDescription = null,
                                    tint = providerColor,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Column {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = cleanSource,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color.White
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(999.dp),
                                        color = providerColor.copy(alpha = 0.18f)
                                    ) {
                                        Text(
                                            text = syncLabel,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = providerColor,
                                            fontSize = 9.sp,
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = if (isPinned) "Pinned manually • Tap to switch source" else "Auto-selected highest match • Tap to switch",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.58f),
                                    fontSize = 11.sp
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = Emerald400
                        ) {
                            Text(
                                text = "Switch",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.Black,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                if (availableVariants.size > 1 || hasTranslationSupport) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "DISPLAY & TRANSLATION",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White.copy(alpha = 0.52f),
                            fontSize = 10.sp
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            availableVariants.forEach { variant ->
                                val selected = selectedVariant == variant
                                Surface(
                                    onClick = { onSelectVariant(variant) },
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (selected) Color.White else Color.White.copy(alpha = 0.06f),
                                    border = BorderStroke(
                                        width = 1.dp,
                                        color = if (selected) Color.White else Color.White.copy(alpha = 0.08f)
                                    )
                                ) {
                                    Text(
                                        text = variant.label,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (selected) Color.Black else Color.White.copy(alpha = 0.85f),
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                                    )
                                }
                            }

                            if (hasTranslationSupport) {
                                Surface(
                                    onClick = onToggleTranslation,
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (showTranslation) Emerald500.copy(alpha = 0.24f) else Color.White.copy(alpha = 0.06f),
                                    border = BorderStroke(
                                        width = 1.dp,
                                        color = if (showTranslation) Emerald400.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.08f)
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Translate,
                                            contentDescription = null,
                                            tint = if (showTranslation) Emerald400 else Color.White.copy(alpha = 0.8f),
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Text(
                                            text = if (showTranslation) "AI Translate (${translateLang.uppercase()})" else "AI Translate",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = if (showTranslation) Emerald400 else Color.White.copy(alpha = 0.85f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "PREFERRED SCRIPT",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.52f),
                        fontSize = 10.sp
                    )
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        LyricsScriptFilter.visibleFilters.forEach { filter ->
                            val selected = lyricsScriptFilter == filter
                            Surface(
                                onClick = {
                                    onSelectScriptFilter(filter)
                                    onDismiss()
                                },
                                shape = RoundedCornerShape(999.dp),
                                color = if (selected) Emerald500.copy(alpha = 0.24f) else Color.White.copy(alpha = 0.06f),
                                border = BorderStroke(
                                    width = 1.dp,
                                    color = if (selected) Emerald400.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.08f)
                                )
                            ) {
                                Text(
                                    text = filter.label,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (selected) Emerald400 else Color.White.copy(alpha = 0.82f),
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        onClick = {
                            onDismiss()
                            onRetryLyricsFetch()
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                        color = Color.White.copy(alpha = 0.06f),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                tint = Emerald400,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Re-Sync Auto",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }

                    if (isPinned) {
                        Surface(
                            onClick = {
                                onDismiss()
                                onClearSavedLyricsSelection()
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp),
                            color = Color.White.copy(alpha = 0.06f),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoFixHigh,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.85f),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Unpin Saved",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
