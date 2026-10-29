package com.israrxy.raazi.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.israrxy.raazi.data.lyrics.LyricsScriptFilter
import com.israrxy.raazi.data.lyrics.LyricsViewVariant
import com.israrxy.raazi.model.MusicItem
import com.israrxy.raazi.ui.theme.Emerald400
import com.israrxy.raazi.ui.theme.Emerald500
import com.israrxy.raazi.ui.theme.Zinc800
import com.israrxy.raazi.utils.ThumbnailUtils

@Composable
internal fun LyricsTopNavigationBar(
    showOptionsMenu: Boolean,
    selectedVariant: LyricsViewVariant,
    showTranslation: Boolean,
    onClose: () -> Unit,
    onOpenLyricsBrowser: () -> Unit,
    onToggleOptionsMenu: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            onClick = onClose,
            shape = CircleShape,
            color = Color.White.copy(alpha = 0.08f),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
            modifier = Modifier.size(40.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = "Back to player",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.Center
        ) {
            val pillSegmentWidth = 58.dp
            Box(
                modifier = Modifier
                    .height(38.dp)
                    .width(pillSegmentWidth * 2)
                    .clip(RoundedCornerShape(19.dp))
                    .background(Color.White.copy(alpha = 0.10f))
                    .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(19.dp))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(pillSegmentWidth)
                        .offset(x = pillSegmentWidth)
                        .clip(RoundedCornerShape(19.dp))
                        .background(Color.White)
                )
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { onClose() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Headphones,
                            contentDescription = "Song View",
                            tint = Color.White.copy(alpha = 0.72f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Subject,
                            contentDescription = "Lyrics View",
                            tint = Color.Black,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        Surface(
            onClick = onOpenLyricsBrowser,
            shape = CircleShape,
            color = Color.White.copy(alpha = 0.08f),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
            modifier = Modifier.size(40.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.ManageSearch,
                    contentDescription = "Search or switch lyrics provider",
                    tint = Color.White.copy(alpha = 0.92f),
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        val isOptionsActive = showOptionsMenu || selectedVariant != LyricsViewVariant.ORIGINAL || showTranslation
        Surface(
            onClick = onToggleOptionsMenu,
            shape = CircleShape,
            color = if (isOptionsActive) Emerald500.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.08f),
            border = BorderStroke(
                width = 1.dp,
                color = if (isOptionsActive) Emerald400.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.08f)
            ),
            modifier = Modifier.size(40.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = "Lyrics studio options",
                    tint = if (isOptionsActive) Emerald400 else Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
internal fun LyricsNowPlayingStrip(
    currentTrack: MusicItem?,
    lyricsSource: String?,
    isSynced: Boolean,
    lyricsStatusBadge: String,
    isTrackLiked: Boolean,
    showOptionsMenu: Boolean,
    availableVariants: List<LyricsViewVariant>,
    selectedVariant: LyricsViewVariant,
    onSelectVariant: (LyricsViewVariant) -> Unit,
    geminiApiKey: String?,
    showTranslation: Boolean,
    isTranslating: Boolean,
    translateLang: String,
    onToggleTranslation: () -> Unit,
    lyricsScriptFilter: LyricsScriptFilter,
    onToggleOptionsMenu: () -> Unit,
    onOpenLyricsBrowser: () -> Unit,
    onToggleFavorite: (MusicItem) -> Unit
) {
    val providerColor = remember(lyricsSource) {
        if (lyricsSource.isNullOrBlank()) Emerald400 else providerAccentColor(lyricsSource)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF10161E).copy(alpha = 0.72f))
                .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(20.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = ThumbnailUtils.getHighQualityThumbnail(currentTrack?.thumbnailUrl),
                contentDescription = currentTrack?.title,
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Zinc800),
                contentScale = ContentScale.Crop
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = currentTrack?.title ?: "No Track Playing",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = currentTrack?.artist ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.65f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Surface(
                onClick = onOpenLyricsBrowser,
                shape = RoundedCornerShape(999.dp),
                color = if (isSynced) providerColor.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.10f),
                border = BorderStroke(
                    width = 1.dp,
                    color = if (isSynced) providerColor.copy(alpha = 0.45f) else Color.White.copy(alpha = 0.16f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(if (isSynced) providerColor else Color.White.copy(alpha = 0.6f))
                    )
                    Text(
                        text = lyricsStatusBadge,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (isSynced) providerColor else Color.White.copy(alpha = 0.88f),
                        fontSize = 10.sp
                    )
                    Icon(
                        imageVector = Icons.Default.UnfoldMore,
                        contentDescription = "Switch provider",
                        tint = if (isSynced) providerColor else Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(12.dp)
                    )
                }
            }

            if (currentTrack != null) {
                IconButton(
                    onClick = { onToggleFavorite(currentTrack) },
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(
                        imageVector = if (isTrackLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = "Favorite",
                        tint = if (isTrackLiked) Emerald400 else Color.White.copy(alpha = 0.75f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Quick Studio Options Pill Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 1. Studio Options Pill
            Surface(
                onClick = onToggleOptionsMenu,
                shape = RoundedCornerShape(999.dp),
                color = if (showOptionsMenu) Emerald400 else Color.White.copy(alpha = 0.10f),
                border = BorderStroke(
                    width = 1.dp,
                    color = if (showOptionsMenu) Emerald400 else Color.White.copy(alpha = 0.12f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = null,
                        tint = if (showOptionsMenu) Color.Black else Emerald400,
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = "Options",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (showOptionsMenu) Color.Black else Color.White
                    )
                }
            }

            // 2. Switch Provider Pill
            Surface(
                onClick = onOpenLyricsBrowser,
                shape = RoundedCornerShape(999.dp),
                color = Color.White.copy(alpha = 0.08f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SwapHoriz,
                        contentDescription = null,
                        tint = providerColor,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "Providers",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White.copy(alpha = 0.9f)
                    )
                }
            }

            // 3. Original / Romanized Variants
            if (availableVariants.size > 1) {
                availableVariants.forEach { variant ->
                    val selected = selectedVariant == variant
                    Surface(
                        onClick = { onSelectVariant(variant) },
                        shape = RoundedCornerShape(999.dp),
                        color = if (selected) Color.White else Color.White.copy(alpha = 0.08f),
                        border = BorderStroke(
                            width = 1.dp,
                            color = if (selected) Color.White else Color.White.copy(alpha = 0.10f)
                        )
                    ) {
                        Text(
                            text = variant.label,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (selected) Color.Black else Color.White.copy(alpha = 0.85f),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            // 4. AI Translation Toggle
            if (!geminiApiKey.isNullOrBlank()) {
                Surface(
                    onClick = onToggleTranslation,
                    shape = RoundedCornerShape(999.dp),
                    color = if (showTranslation) Emerald500.copy(alpha = 0.24f) else Color.White.copy(alpha = 0.08f),
                    border = BorderStroke(
                        width = 1.dp,
                        color = if (showTranslation) Emerald400.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.10f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (isTranslating) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(12.dp),
                                strokeWidth = 1.5.dp,
                                color = Emerald400
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Translate,
                                contentDescription = null,
                                tint = if (showTranslation) Emerald400 else Color.White.copy(alpha = 0.8f),
                                modifier = Modifier.size(13.dp)
                            )
                        }
                        Text(
                            text = if (showTranslation) "Translated (${translateLang.uppercase()})" else "Translate",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = if (showTranslation) Emerald400 else Color.White.copy(alpha = 0.85f)
                        )
                    }
                }
            }

            // 5. Active Script Indicator Pill
            Surface(
                onClick = onToggleOptionsMenu,
                shape = RoundedCornerShape(999.dp),
                color = if (lyricsScriptFilter != LyricsScriptFilter.ALL) {
                    Emerald500.copy(alpha = 0.18f)
                } else {
                    Color.White.copy(alpha = 0.08f)
                },
                border = BorderStroke(
                    width = 1.dp,
                    color = if (lyricsScriptFilter != LyricsScriptFilter.ALL) {
                        Emerald400.copy(alpha = 0.45f)
                    } else {
                        Color.White.copy(alpha = 0.10f)
                    }
                )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Language,
                        contentDescription = null,
                        tint = if (lyricsScriptFilter != LyricsScriptFilter.ALL) Emerald400 else Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = "Script: ${lyricsScriptFilter.label}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (lyricsScriptFilter != LyricsScriptFilter.ALL) Emerald400 else Color.White.copy(alpha = 0.85f)
                    )
                }
            }
        }
    }
}
