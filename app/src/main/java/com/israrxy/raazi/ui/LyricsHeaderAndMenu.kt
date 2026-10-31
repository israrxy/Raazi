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
import com.israrxy.raazi.ui.theme.Zinc800
import com.israrxy.raazi.utils.ThumbnailUtils

@Composable
internal fun LyricsTopNavigationBar(
    onClose: () -> Unit,
    onOpenLyricsBrowser: () -> Unit,
    onOpenTrackDetails: () -> Unit
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
            modifier = Modifier.size(38.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = "Back to player",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.Center
        ) {
            val pillSegmentWidth = 54.dp
            Box(
                modifier = Modifier
                    .height(34.dp)
                    .width(pillSegmentWidth * 2)
                    .clip(RoundedCornerShape(17.dp))
                    .background(Color.White.copy(alpha = 0.08f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(pillSegmentWidth)
                        .offset(x = pillSegmentWidth)
                        .clip(RoundedCornerShape(17.dp))
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
                            tint = Color.White.copy(alpha = 0.65f),
                            modifier = Modifier.size(16.dp)
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
                            tint = Color(0xFF09090B),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        Surface(
            onClick = onOpenLyricsBrowser,
            shape = CircleShape,
            color = Color.White.copy(alpha = 0.08f),
            modifier = Modifier.size(38.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.ManageSearch,
                    contentDescription = "Lyrics source",
                    tint = Color.White.copy(alpha = 0.88f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        Surface(
            onClick = onOpenTrackDetails,
            shape = CircleShape,
            color = Color.White.copy(alpha = 0.08f),
            modifier = Modifier.size(38.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "Song details",
                    tint = Color.White,
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
    showTranslation: Boolean,
    isTranslating: Boolean,
    translateLang: String,
    onToggleTranslation: () -> Unit,
    lyricsScriptFilter: LyricsScriptFilter,
    onToggleOptionsMenu: () -> Unit,
    onOpenLyricsBrowser: () -> Unit,
    onToggleFavorite: (MusicItem) -> Unit
) {
    val cleanSourceLabel = remember(lyricsSource, isSynced) {
        val src = lyricsSource?.removePrefix("Saved ")?.trim().orEmpty()
        when {
            src.isNotBlank() && isSynced -> "$src • Synced"
            src.isNotBlank() -> src
            else -> lyricsStatusBadge.lowercase().replaceFirstChar { it.uppercase() }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Color.White.copy(alpha = 0.06f))
                .border(1.dp, Color.White.copy(alpha = 0.07f), RoundedCornerShape(14.dp))
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = ThumbnailUtils.getHighQualityThumbnail(currentTrack?.thumbnailUrl),
                contentDescription = currentTrack?.title,
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Zinc800),
                contentScale = ContentScale.Crop
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = currentTrack?.title ?: "No Track Playing",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(1.dp))
                Text(
                    text = currentTrack?.artist ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.56f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Surface(
                onClick = onOpenLyricsBrowser,
                shape = RoundedCornerShape(8.dp),
                color = Color.White.copy(alpha = 0.08f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = cleanSourceLabel,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        color = Color.White.copy(alpha = 0.86f),
                        fontSize = 11.sp
                    )
                    Icon(
                        imageVector = Icons.Default.UnfoldMore,
                        contentDescription = "Switch source",
                        tint = Color.White.copy(alpha = 0.56f),
                        modifier = Modifier.size(12.dp)
                    )
                }
            }

            if (currentTrack != null) {
                IconButton(
                    onClick = { onToggleFavorite(currentTrack) },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = if (isTrackLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = "Favorite",
                        tint = if (isTrackLiked) Color.White else Color.White.copy(alpha = 0.65f),
                        modifier = Modifier.size(19.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (availableVariants.size > 1) {
                availableVariants.forEach { variant ->
                    val selected = selectedVariant == variant
                    Surface(
                        onClick = { onSelectVariant(variant) },
                        shape = RoundedCornerShape(8.dp),
                        color = if (selected) Color.White else Color.White.copy(alpha = 0.07f)
                    ) {
                        Text(
                            text = variant.label,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (selected) Color(0xFF09090B) else Color.White.copy(alpha = 0.78f),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }
            }

            Surface(
                onClick = onToggleTranslation,
                shape = RoundedCornerShape(8.dp),
                color = if (showTranslation) Color.White else Color.White.copy(alpha = 0.07f)
            ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            if (isTranslating) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(11.dp),
                                    strokeWidth = 1.5.dp,
                                    color = if (showTranslation) Color(0xFF09090B) else Color.White
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Translate,
                                    contentDescription = null,
                                    tint = if (showTranslation) Color(0xFF09090B) else Color.White.copy(alpha = 0.75f),
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                            Text(
                                text = if (showTranslation) "Translated (${translateLang.uppercase()})" else "Translate",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (showTranslation) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (showTranslation) Color(0xFF09090B) else Color.White.copy(alpha = 0.78f)
                            )
                        }
                    }

                    if (lyricsScriptFilter != LyricsScriptFilter.ALL) {
                        Surface(
                            onClick = onToggleOptionsMenu,
                            shape = RoundedCornerShape(8.dp),
                            color = Color.White.copy(alpha = 0.07f)
                        ) {
                            Text(
                                text = "Script: ${lyricsScriptFilter.label}",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.78f),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }
                }
            }
        }
