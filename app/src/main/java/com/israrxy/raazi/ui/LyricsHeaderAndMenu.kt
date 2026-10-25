package com.israrxy.raazi.ui

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
        IconButton(
            onClick = onClose,
            modifier = Modifier.size(44.dp)
        ) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = "Back to player",
                tint = Color.White,
                modifier = Modifier.size(28.dp)
            )
        }

        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.Center
        ) {
            val pillSegmentWidth = 56.dp
            Box(
                modifier = Modifier
                    .height(38.dp)
                    .width(pillSegmentWidth * 2)
                    .clip(RoundedCornerShape(19.dp))
                    .background(Color.White.copy(alpha = 0.12f))
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

        IconButton(
            onClick = onOpenLyricsBrowser,
            modifier = Modifier.size(44.dp)
        ) {
            Icon(
                imageVector = Icons.Default.ManageSearch,
                contentDescription = "Search or change lyrics",
                tint = Color.White.copy(alpha = 0.9f),
                modifier = Modifier.size(24.dp)
            )
        }

        IconButton(
            onClick = onToggleOptionsMenu,
            modifier = Modifier.size(44.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Tune,
                contentDescription = "Lyrics options",
                tint = if (showOptionsMenu || selectedVariant != LyricsViewVariant.ORIGINAL || showTranslation) Emerald400 else Color.White,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
internal fun LyricsNowPlayingStrip(
    currentTrack: MusicItem?,
    isSynced: Boolean,
    lyricsStatusBadge: String,
    isTrackLiked: Boolean,
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
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(Color.White.copy(alpha = 0.08f))
                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(18.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = ThumbnailUtils.getHighQualityThumbnail(currentTrack?.thumbnailUrl),
                contentDescription = currentTrack?.title,
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(10.dp))
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
                shape = RoundedCornerShape(999.dp),
                color = if (isSynced) Emerald500.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.1f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (isSynced) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(Emerald400)
                        )
                    }
                    Text(
                        text = lyricsStatusBadge,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isSynced) Emerald400 else Color.White.copy(alpha = 0.7f),
                        fontSize = 10.sp
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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (availableVariants.size > 1) {
                availableVariants.forEach { variant ->
                    val selected = selectedVariant == variant
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = if (selected) Color.White else Color.White.copy(alpha = 0.1f),
                        modifier = Modifier.clickable { onSelectVariant(variant) }
                    ) {
                        Text(
                            text = variant.label,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = if (selected) Color.Black else Color.White.copy(alpha = 0.85f),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            if (!geminiApiKey.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = if (showTranslation) Emerald500.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.1f),
                    modifier = Modifier
                        .border(
                            width = 1.dp,
                            color = if (showTranslation) Emerald400.copy(alpha = 0.6f) else Color.Transparent,
                            shape = RoundedCornerShape(999.dp)
                        )
                        .clickable { onToggleTranslation() }
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

            Surface(
                shape = RoundedCornerShape(999.dp),
                color = Color.White.copy(alpha = 0.08f),
                modifier = Modifier.clickable { onToggleOptionsMenu() }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Language,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = "Script: ${lyricsScriptFilter.label}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(999.dp),
                color = Color.White.copy(alpha = 0.08f),
                modifier = Modifier.clickable { onOpenLyricsBrowser() }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SwapHoriz,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "Switch Match",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }
}
