package com.israrxy.raazi.ui.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.israrxy.raazi.data.ai.SongCandidate
import com.israrxy.raazi.data.ai.WebVerifyLevel
import com.israrxy.raazi.viewmodel.AiSongSearchUiState

@Composable
internal fun SheetHeader(onClose: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Find a song",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Photo or a 15-second clip · your Gemini key",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onClose, modifier = Modifier.size(40.dp)) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "Close",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
internal fun FinderActionBody(
    onPickPhoto: () -> Unit,
    onListen: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        FinderActionCard(
            icon = Icons.Filled.PhotoLibrary,
            title = "Choose a photo",
            subtitle = "Album art, screenshot, or lyrics",
            onClick = onPickPhoto
        )
        FinderActionCard(
            icon = Icons.Filled.Mic,
            title = "Listen for 15 seconds",
            subtitle = "Play the song near your phone",
            onClick = onListen
        )
        Spacer(modifier = Modifier.height(2.dp))
        FinderInstructions()
    }
}

@Composable
private fun FinderActionCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Row(
            modifier = Modifier.padding(15.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                        RoundedCornerShape(14.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(26.dp)
                )
            }
            Spacer(modifier = Modifier.size(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
internal fun FinderNoKeyBody(
    onOpenSettings: () -> Unit,
    onOpenGuide: () -> Unit
) {
    FinderStatusRow(
        icon = Icons.Filled.Key,
        title = "Gemini key needed",
        subtitle = "Photo and audio search use your own free key.",
        actionLabel = "Add key",
        onAction = onOpenSettings
    )
    Spacer(modifier = Modifier.height(4.dp))
    FinderNoticeRow(
        text = "Free key, no credit card.",
        actionLabel = "How",
        onAction = onOpenGuide
    )
    Spacer(modifier = Modifier.height(4.dp))
    FinderInstructions()
}

@Composable
internal fun FinderErrorBody(
    state: AiSongSearchUiState,
    onRetry: () -> Unit,
    onChangeInput: () -> Unit,
    onChangeInputLabel: String
) {
    FinderStatusRow(
        icon = Icons.Filled.ErrorOutline,
        title = "Couldn't identify it",
        subtitle = state.error ?: "Try a different photo or a longer clip."
    )
    FinderSecondaryActions(
        retryLabel = "Try again",
        onChangeInput = onChangeInput,
        onChangeInputLabel = onChangeInputLabel,
        onRetry = onRetry
    )
}

@Composable
internal fun FinderMatchBody(
    state: AiSongSearchUiState,
    onSearch: (String) -> Unit,
    onRetry: () -> Unit,
    onChangeInput: () -> Unit,
    onChangeInputLabel: String
) {
    val identification = state.identification ?: return
    val candidates = buildList {
        if (identification.hasCandidate) {
            add(
                SongCandidate(
                    title = identification.title.orEmpty(),
                    artist = identification.artist.orEmpty()
                )
            )
        }
        addAll(identification.alternatives)
    }
        .filter { it.query.isNotBlank() }
        .distinctBy { it.query.lowercase() }
        .take(3)

    if (candidates.isNotEmpty()) {
        Text(
            text = "Did you mean?",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 2.dp)
        )
        candidates.forEach { candidate ->
            FinderCandidateRow(
                title = candidate.title,
                artist = candidate.artist,
                onClick = { onSearch(candidate.query) }
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
    }

    val lyrics = identification.lyricsQuery()
    if (lyrics.isNotBlank()) {
        FinderActionRow(
            icon = Icons.Filled.Search,
            title = "Search detected lyrics",
            subtitle = lyrics,
            onClick = { onSearch(lyrics) }
        )
    }

    if (candidates.isEmpty() && lyrics.isBlank()) {
        FinderStatusRow(
            icon = Icons.Filled.HelpOutline,
            title = "No match yet",
            subtitle = identification.message ?: "Try a clearer photo or a longer clip."
        )
    }

    FinderSecondaryActions(
        retryLabel = "Try again",
        onChangeInput = onChangeInput,
        onChangeInputLabel = onChangeInputLabel,
        onRetry = onRetry
    )
}

@Composable
internal fun SheetFooter(
    state: AiSongSearchUiState,
    onAutoSearchChange: (Boolean) -> Unit,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    Spacer(modifier = Modifier.height(8.dp))
    FinderToggleRow(
        title = "Auto search",
        subtitle = if (state.autoSearch) {
            "Open the best match without tapping"
        } else {
            "Show matches and let me choose"
        },
        checked = state.autoSearch,
        onCheckedChange = onAutoSearchChange
    )
    state.webNotice?.let { notice ->
        FinderNoticeRow(
            text = notice,
            actionLabel = if (
                state.usage.enabled &&
                (state.usage.showCapExplanation || state.usage.level != WebVerifyLevel.NORMAL)
            ) {
                "Change"
            } else {
                null
            },
            onAction = onOpenSettings
        )
    }
    FinderNoticeRow(text = "Sent only to Gemini with your key.")
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End
    ) {
        TextButton(onClick = onDismiss) {
            Text("Dismiss", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
