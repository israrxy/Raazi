package com.israrxy.raazi.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.israrxy.raazi.model.MusicItem
import com.israrxy.raazi.utils.ThumbnailUtils
import com.israrxy.raazi.viewmodel.HomeFeedItem
import com.israrxy.raazi.viewmodel.HomeSection
import com.israrxy.raazi.viewmodel.MusicPlayerViewModel

@Composable
fun HomeStatusHeader(
    activeCategoryTitle: String? = null,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onShuffle: (() -> Unit)? = null
) {
    val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
    val greeting = when (hour) {
        in 0..5 -> "Good Night"
        in 6..11 -> "Good Morning"
        in 12..16 -> "Good Afternoon"
        in 17..20 -> "Good Evening"
        else -> "Good Night"
    }
    val context = androidx.compose.ui.platform.LocalContext.current
    var shuffleTurns by remember { mutableIntStateOf(0) }
    val diceRotation by animateFloatAsState(
        targetValue = shuffleTurns * 360f,
        animationSpec = tween(durationMillis = 560),
        label = "home-shuffle-dice"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Branding pill — minimal flat surface
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .clickable {
                        try {
                            val intent = android.content.Intent(
                                android.content.Intent.ACTION_VIEW,
                                android.net.Uri.parse("https://israrxy.qzz.io")
                            )
                            context.startActivity(intent)
                        } catch (_: Exception) {}
                    }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.foundation.Image(
                        painter = androidx.compose.ui.res.painterResource(id = com.israrxy.raazi.R.drawable.raazi_logo),
                        contentDescription = null,
                        modifier = Modifier
                            .size(18.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Raazi",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "@israrxy",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (isRefreshing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                IconButton(
                    onClick = onRefresh,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh home feed",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = activeCategoryTitle ?: greeting,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            if (onShuffle != null) {
                IconButton(
                    onClick = {
                        shuffleTurns++
                        onShuffle()
                    },
                    enabled = !isRefreshing,
                    modifier = Modifier.size(42.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Casino,
                        contentDescription = "Shuffle home feed",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .size(23.dp)
                            .graphicsLayer { rotationZ = diceRotation }
                    )
                }
            }
        }
    }
}

@Composable
fun HomeCategoryFilterBar(
    chips: List<com.zionhuang.innertube.pages.HomePage.Chip>,
    selectedChip: com.zionhuang.innertube.pages.HomePage.Chip?,
    onSelectChip: (com.zionhuang.innertube.pages.HomePage.Chip?) -> Unit
) {
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    LazyRow(
        state = listState,
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(top = 2.dp, bottom = 4.dp)
    ) {
        item(key = "chip_all") {
            val isAllSelected = selectedChip == null
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (isAllSelected) {
                    MaterialTheme.colorScheme.onBackground
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                },
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onSelectChip(null) }
            ) {
                Text(
                    text = "All",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (isAllSelected) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (isAllSelected) {
                        MaterialTheme.colorScheme.background
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }
        }
        items(chips, key = { "chip_${it.title}" }) { chip ->
            val isSelected = selectedChip?.title.equals(chip.title, ignoreCase = true)
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (isSelected) {
                    MaterialTheme.colorScheme.onBackground
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                },
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onSelectChip(chip) }
            ) {
                Text(
                    text = chip.title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.background
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }
        }
    }
}

@Composable
fun HomeSectionHeader(
    section: HomeSection,
    onAction: (() -> Unit)? = null,
    onMore: (() -> Unit)? = null,
    onNotInterested: (() -> Unit)? = null
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        section.strapline?.takeIf { it.isNotBlank() }?.let { strapline ->
            Text(
                text = strapline,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!section.avatarUrl.isNullOrBlank()) {
                AsyncImage(
                    model = ThumbnailUtils.getHighQualityThumbnail(section.avatarUrl!!),
                    contentDescription = null,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.width(10.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = section.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                section.subtitle?.takeIf { it.isNotBlank() }?.let { subtitle ->
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (section.moreEndpoint != null && onMore != null) {
                TextButton(onClick = onMore) {
                    Text("More", style = MaterialTheme.typography.labelLarge)
                }
            }
            if (section.actionLabel != null && onAction != null) {
                val refreshLabel = section.actionLabel.contains("refresh", ignoreCase = true) ||
                    section.actionLabel.contains("reload", ignoreCase = true)
                if (refreshLabel) {
                    IconButton(onClick = onAction, modifier = Modifier.size(36.dp)) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = section.actionLabel,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                } else {
                    TextButton(onClick = onAction) {
                        Text(section.actionLabel)
                    }
                }
            }
            if (onNotInterested != null) {
                Box {
                    IconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Shelf options",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Not interested in ${section.artistName ?: section.title}") },
                            onClick = {
                                menuExpanded = false
                                onNotInterested()
                            }
                        )
                    }
                }
            }
        }
    }
}

internal fun HomeSection.musicItems(): List<MusicItem> =
    items.mapNotNull { (it as? HomeFeedItem.Music)?.item }

internal fun HomeFeedItem.stableId(): String = when (this) {
    is HomeFeedItem.Music -> item.id
    is HomeFeedItem.PlaylistResult -> playlist.id
    is HomeFeedItem.Mood -> title
    is HomeFeedItem.YouTube -> item.id
}

@Composable
fun HomeFeedErrorView(
    activeCategoryTitle: String?,
    chips: List<com.zionhuang.innertube.pages.HomePage.Chip>,
    selectedChip: com.zionhuang.innertube.pages.HomePage.Chip?,
    errorMessage: String?,
    onRefreshStatus: () -> Unit,
    onSelectChip: (com.zionhuang.innertube.pages.HomePage.Chip?) -> Unit,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        HomeStatusHeader(
            activeCategoryTitle = activeCategoryTitle,
            isRefreshing = false,
            onRefresh = onRefreshStatus
        )
        HomeCategoryFilterBar(
            chips = chips,
            selectedChip = selectedChip,
            onSelectChip = onSelectChip
        )
        Spacer(modifier = Modifier.height(56.dp))
        Icon(
            imageVector = Icons.Default.CloudOff,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Something went wrong",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = errorMessage ?: "Failed to load your feed. Check your connection and try again.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onRetry,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 48.dp)
                .height(48.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.Refresh, null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Retry", fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun HomeFeedEmptyView(
    activeCategoryTitle: String?,
    chips: List<com.zionhuang.innertube.pages.HomePage.Chip>,
    selectedChip: com.zionhuang.innertube.pages.HomePage.Chip?,
    isRefreshing: Boolean,
    onRefreshStatus: () -> Unit,
    onSelectChip: (com.zionhuang.innertube.pages.HomePage.Chip?) -> Unit,
    onNavigateToSearch: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        HomeStatusHeader(
            activeCategoryTitle = activeCategoryTitle,
            isRefreshing = isRefreshing,
            onRefresh = onRefreshStatus
        )
        HomeCategoryFilterBar(
            chips = chips,
            selectedChip = selectedChip,
            onSelectChip = onSelectChip
        )
        Spacer(modifier = Modifier.height(64.dp))
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Search for a song",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Find your favorite music to get started.\nYour history will appear here.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onNavigateToSearch,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp)
                .height(48.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.Search, null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Search a Song", fontWeight = FontWeight.SemiBold)
        }
    }
}
