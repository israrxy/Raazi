package com.israrxy.raazi.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.israrxy.raazi.data.lyrics.LyricsScriptFilter
import com.israrxy.raazi.data.lyrics.LyricsViewVariant
import com.israrxy.raazi.data.remote.Lyrics

private val SheetCardColor = Color(0xFF1B1B1F)
private val SheetBorder = Color.White.copy(alpha = 0.06f)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LyricsOptionsSheet(
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
    hideBrackets: Boolean = true,
    onToggleHideBrackets: () -> Unit = {},
    lyricsScriptFilter: LyricsScriptFilter,
    onDismiss: () -> Unit,
    onOpenLyricsBrowser: () -> Unit,
    onOpenTrackDetails: () -> Unit = {},
    onRetryLyricsFetch: () -> Unit,
    onClearSavedLyricsSelection: () -> Unit,
    onSelectScriptFilter: (LyricsScriptFilter) -> Unit
) {
    if (!visible) return

    val cleanSource = remember(lyrics?.source) {
        lyrics?.source?.removePrefix("Saved ")?.trim().orEmpty().ifBlank { "Auto" }
    }
    val isPinned = lyrics?.source?.startsWith("Saved") == true
    val syncLabel = when {
        hasWordSync -> "Word sync"
        isSynced -> "Synced"
        lyrics != null -> "Plain"
        else -> "None"
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF141417),
        contentColor = Color.White,
        dragHandle = {
            BottomSheetDefaults.DragHandle(color = Color.White.copy(alpha = 0.25f))
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = "Lyrics options",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
                Text(
                    text = "Choose a source and tune how lyrics look",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.48f)
                )
            }

            SheetSectionLabel("Source")

            Surface(
                onClick = onOpenLyricsBrowser,
                shape = RoundedCornerShape(14.dp),
                color = SheetCardColor,
                border = BorderStroke(1.dp, SheetBorder)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.White.copy(alpha = 0.06f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.LibraryMusic,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.78f),
                            modifier = Modifier.size(19.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = cleanSource,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                            maxLines = 1
                        )
                        Text(
                            text = if (isPinned) "Pinned manually" else "Automatic best match",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.46f),
                            fontSize = 11.sp
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = syncLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.52f)
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Change",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Medium,
                                color = Color.White.copy(alpha = 0.88f)
                            )
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.6f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            if (availableVariants.size > 1 || hasTranslationSupport) {
                SheetSectionLabel("Text display")

                if (availableVariants.size > 1) {
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
                                shape = RoundedCornerShape(10.dp),
                                color = if (selected) Color.White else SheetCardColor,
                                border = BorderStroke(
                                    1.dp,
                                    if (selected) Color.White else SheetBorder
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
                    }
                }

                if (hasTranslationSupport) {
                    OptionSwitchRow(
                        icon = Icons.Default.Translate,
                        title = "Translate",
                        subtitle = if (showTranslation) {
                            "Showing ${translateLang.uppercase()} translation"
                        } else {
                            "Add a ${translateLang.uppercase()} translation"
                        },
                        checked = showTranslation,
                        onCheckedChange = { onToggleTranslation() }
                    )
                    OptionSwitchRow(
                        icon = Icons.Default.VisibilityOff,
                        title = "Hide bracketed text",
                        subtitle = "Remove [..] annotations from lyrics",
                        checked = hideBrackets,
                        onCheckedChange = { onToggleHideBrackets() }
                    )
                }
            }

            SheetSectionLabel("Preferred script")

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                LyricsScriptFilter.visibleFilters.forEach { filter ->
                    val selected = lyricsScriptFilter == filter
                    Surface(
                        onClick = { onSelectScriptFilter(filter) },
                        shape = RoundedCornerShape(10.dp),
                        color = if (selected) Color.White else SheetCardColor,
                        border = BorderStroke(
                            1.dp,
                            if (selected) Color.White else SheetBorder
                        )
                    ) {
                        Text(
                            text = filter.label,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (selected) Color(0xFF09090B) else Color.White.copy(alpha = 0.8f),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                        )
                    }
                }
            }

            HorizontalDivider(color = Color.White.copy(alpha = 0.07f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SheetActionButton(
                    icon = Icons.Default.Refresh,
                    label = "Refresh",
                    onClick = {
                        onDismiss()
                        onRetryLyricsFetch()
                    }
                )
                if (isPinned) {
                    SheetActionButton(
                        icon = Icons.Default.RestartAlt,
                        label = "Reset",
                        onClick = {
                            onDismiss()
                            onClearSavedLyricsSelection()
                        }
                    )
                }
                SheetActionButton(
                    icon = Icons.Default.Info,
                    label = "Details",
                    onClick = {
                        onDismiss()
                        onOpenTrackDetails()
                    }
                )
            }
        }
    }
}

@Composable
private fun SheetSectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Medium,
        color = Color.White.copy(alpha = 0.45f),
        fontSize = 11.sp
    )
}

@Composable
private fun OptionSwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(SheetCardColor)
            .border(1.dp, SheetBorder, RoundedCornerShape(14.dp))
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.72f),
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.46f),
                fontSize = 11.sp
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color(0xFF09090B),
                checkedTrackColor = Color.White,
                uncheckedThumbColor = Color.White.copy(alpha = 0.7f),
                uncheckedTrackColor = Color.White.copy(alpha = 0.12f)
            )
        )
    }
}

@Composable
private fun RowScope.SheetActionButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.weight(1f),
        shape = RoundedCornerShape(12.dp),
        color = SheetCardColor,
        border = BorderStroke(1.dp, SheetBorder)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.78f),
                modifier = Modifier.size(15.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = Color.White.copy(alpha = 0.88f)
            )
        }
    }
}
