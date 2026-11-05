package com.israrxy.raazi.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Abc
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.israrxy.raazi.data.local.SettingsDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal fun LazyListScope.settingsLyricsSection(
    settingsDataStore: SettingsDataStore,
    scope: CoroutineScope,
    translateLyrics: Boolean,
    targetLanguage: String,
    onShowLanguageDialog: () -> Unit
) {
    item {
        val preferredLyricsType by settingsDataStore.preferredLyricsType.collectAsState(initial = "LAST_USED")
        val defaultPlayerScreen by settingsDataStore.defaultPlayerScreen.collectAsState(initial = "LAST_USED")
        val lyricsAutoScroll by settingsDataStore.lyricsAutoScroll.collectAsState(initial = true)
        val defaultLyricsView by settingsDataStore.defaultLyricsView.collectAsState(initial = "KARAOKE")
        val lyricsSyncPriority by settingsDataStore.lyricsSyncPriority.collectAsState(initial = "WORD_SYNC_FIRST")

        SettingsSection(title = "Lyrics Preferences & Script") {
            // Visual Preview Card
            LyricsVisualPreviewCard(
                preferredType = preferredLyricsType,
                translateEnabled = translateLyrics,
                targetLanguage = targetLanguage
            )

            SettingsDivider()

            // Script Selection: As Last Song vs Romanized vs Original
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                Text(
                    text = "Preferred Script",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Choose default text presentation for non-English songs",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val scriptOptions = listOf(
                        "LAST_USED" to "As Last Song",
                        "ROMANIZED" to "Romanized",
                        "ORIGINAL" to "Original"
                    )
                    scriptOptions.forEach { (type, label) ->
                        val selected = preferredLyricsType == type
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (selected) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                )
                                .border(
                                    1.dp,
                                    if (selected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                                    RoundedCornerShape(12.dp)
                                )
                                .clickable {
                                    scope.launch { settingsDataStore.setPreferredLyricsType(type) }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            SettingsDivider()

            // Default Player Screen on Open
            SettingsSelectItem(
                icon = Icons.Filled.OpenInFull,
                title = "Player Screen on Open",
                value = when (defaultPlayerScreen) {
                    "LYRICS" -> "Lyrics Screen"
                    "PLAYER" -> "Album Cover"
                    else -> "As Last Left"
                },
                onClick = {
                    val next = when (defaultPlayerScreen) {
                        "LAST_USED" -> "LYRICS"
                        "LYRICS" -> "PLAYER"
                        else -> "LAST_USED"
                    }
                    scope.launch { settingsDataStore.setDefaultPlayerScreen(next) }
                }
            )

            SettingsDivider()

            // Default Behaviour: Karaoke vs Plain
            SettingsSelectItem(
                icon = Icons.Filled.Mic,
                title = "Default Presentation",
                value = if (defaultLyricsView == "KARAOKE") "Synchronized Karaoke" else "Plain Text",
                onClick = {
                    val next = if (defaultLyricsView == "KARAOKE") "PLAIN" else "KARAOKE"
                    scope.launch { settingsDataStore.setDefaultLyricsView(next) }
                }
            )

            SettingsDivider()

            // Lyrics Sync Ranking Priority
            SettingsSelectItem(
                icon = Icons.Filled.AutoAwesome,
                title = "Lyrics Sync Priority",
                value = when (lyricsSyncPriority) {
                    "WORD_SYNC_FIRST" -> "Word-Synced (Flowing)"
                    "LINE_SYNC_FIRST" -> "Line-Synced"
                    "ANY_SYNC_FIRST" -> "Any Synced"
                    "PLAIN_FIRST" -> "Plain Text"
                    else -> "Word-Synced (Flowing)"
                },
                onClick = {
                    val next = when (lyricsSyncPriority) {
                        "WORD_SYNC_FIRST" -> "LINE_SYNC_FIRST"
                        "LINE_SYNC_FIRST" -> "ANY_SYNC_FIRST"
                        "ANY_SYNC_FIRST" -> "PLAIN_FIRST"
                        else -> "WORD_SYNC_FIRST"
                    }
                    scope.launch { settingsDataStore.setLyricsSyncPriority(next) }
                }
            )

            SettingsDivider()

            // Auto-scroll toggle
            SettingsSwitchItem(
                icon = Icons.Filled.Sync,
                title = "Auto-scroll Lyrics",
                subtitle = "Automatically keep current singing line centered in view",
                checked = lyricsAutoScroll,
                onCheckedChange = { enabled ->
                    scope.launch { settingsDataStore.setLyricsAutoScroll(enabled) }
                }
            )

            SettingsDivider()

            // Translation toggle
            SettingsSwitchItem(
                icon = Icons.Filled.Translate,
                title = "Translate Lyrics",
                subtitle = "Show Google Neural translated lines below lyrics",
                checked = translateLyrics,
                onCheckedChange = { enabled ->
                    scope.launch { settingsDataStore.setLyricsTranslateEnabled(enabled) }
                }
            )

            if (translateLyrics) {
                SettingsDivider()
                SettingsSelectItem(
                    icon = Icons.Filled.Language,
                    title = "Target Language",
                    value = targetLanguage.uppercase(),
                    onClick = onShowLanguageDialog
                )
            }
        }
    }
}

@Composable
internal fun LyricsVisualPreviewCard(
    preferredType: String,
    translateEnabled: Boolean,
    targetLanguage: String
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(14.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                }
                Text(
                    text = "Live Lyrics Preview",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(Modifier.height(10.dp))

            // Simulated lyrics block
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val mainLine = if (preferredType == "ORIGINAL") "तुम ही हो, अब तुम ही हो" else "Tum hi ho, ab tum hi ho"
                val subLine = if (preferredType == "ORIGINAL") "(Tum hi ho, ab tum hi ho)" else "(तुम ही हो, अब तुम ही हो)"

                Text(
                    text = mainLine,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Text(
                    text = subLine,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )

                if (translateEnabled) {
                    Text(
                        text = "Translation: \"You are the one, now you are the one\"",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}
