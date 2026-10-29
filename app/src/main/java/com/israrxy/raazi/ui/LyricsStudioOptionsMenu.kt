package com.israrxy.raazi.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.HorizontalDivider
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
        lyrics?.source?.removePrefix("Saved ")?.trim().orEmpty().ifBlank { "Auto" }
    }
    val isPinned = lyrics?.source?.startsWith("Saved") == true
    val syncLabel = when {
        hasWordSync -> "Word Sync"
        isSynced -> "Synced"
        lyrics != null -> "Plain"
        else -> "None"
    }

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(initialOffsetY = { -it / 5 }) + fadeIn() + scaleIn(initialScale = 0.97f),
        exit = slideOutVertically(targetOffsetY = { -it / 5 }) + fadeOut() + scaleOut(targetScale = 0.97f),
        modifier = Modifier
            .align(Alignment.TopCenter)
            .padding(horizontal = 20.dp, vertical = 6.dp)
            .zIndex(10f)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = Color(0xFF141417),
            shadowElevation = 20.dp,
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.09f))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Lyrics Options",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )

                    Surface(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(999.dp),
                        color = Color.White.copy(alpha = 0.07f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.White.copy(alpha = 0.75f),
                            modifier = Modifier
                                .padding(5.dp)
                                .size(15.dp)
                        )
                    }
                }

                Surface(
                    onClick = {
                        onDismiss()
                        onOpenLyricsBrowser()
                    },
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF1C1C20),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.07f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "$cleanSource • $syncLabel",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                            Text(
                                text = if (isPinned) "Pinned manually" else "Automatic best match",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.48f),
                                fontSize = 11.sp
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SwapHoriz,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.8f),
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = "Change",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Medium,
                                color = Color.White.copy(alpha = 0.88f)
                            )
                        }
                    }
                }

                if (availableVariants.size > 1 || hasTranslationSupport) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Text Display",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.45f),
                            fontSize = 11.sp
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            availableVariants.forEach { variant ->
                                val selected = selectedVariant == variant
                                Surface(
                                    onClick = { onSelectVariant(variant) },
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (selected) Color.White else Color(0xFF1C1C20),
                                    border = BorderStroke(
                                        1.dp,
                                        if (selected) Color.White else Color.White.copy(alpha = 0.07f)
                                    )
                                ) {
                                    Text(
                                        text = variant.label,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                        color = if (selected) Color(0xFF09090B) else Color.White.copy(alpha = 0.8f),
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                                    )
                                }
                            }

                            if (hasTranslationSupport) {
                                Surface(
                                    onClick = onToggleTranslation,
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (showTranslation) Color.White else Color(0xFF1C1C20),
                                    border = BorderStroke(
                                        1.dp,
                                        if (showTranslation) Color.White else Color.White.copy(alpha = 0.07f)
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Translate,
                                            contentDescription = null,
                                            tint = if (showTranslation) Color(0xFF09090B) else Color.White.copy(alpha = 0.75f),
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Text(
                                            text = if (showTranslation) "Translated (${translateLang.uppercase()})" else "Translate",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = if (showTranslation) FontWeight.SemiBold else FontWeight.Normal,
                                            color = if (showTranslation) Color(0xFF09090B) else Color.White.copy(alpha = 0.8f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Preferred Script",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.45f),
                        fontSize = 11.sp
                    )
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        LyricsScriptFilter.visibleFilters.forEach { filter ->
                            val selected = lyricsScriptFilter == filter
                            Surface(
                                onClick = {
                                    onSelectScriptFilter(filter)
                                    onDismiss()
                                },
                                shape = RoundedCornerShape(8.dp),
                                color = if (selected) Color.White else Color(0xFF1C1C20),
                                border = BorderStroke(
                                    1.dp,
                                    if (selected) Color.White else Color.White.copy(alpha = 0.07f)
                                )
                            ) {
                                Text(
                                    text = filter.label,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (selected) Color(0xFF09090B) else Color.White.copy(alpha = 0.78f),
                                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = Color.White.copy(alpha = 0.07f))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        onClick = {
                            onDismiss()
                            onRetryLyricsFetch()
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF1C1C20),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.07f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.8f),
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Refresh Match",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Medium,
                                color = Color.White.copy(alpha = 0.88f)
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
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFF1C1C20),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.07f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.RestartAlt,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.8f),
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Reset Auto",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = Color.White.copy(alpha = 0.88f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
