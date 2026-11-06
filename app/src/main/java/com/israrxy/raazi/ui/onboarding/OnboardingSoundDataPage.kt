package com.israrxy.raazi.ui.onboarding

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.DataSaverOn
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun OnboardingSoundDataPage(
    audioQuality: String,
    flacEnabled: Boolean,
    dataSaverEnabled: Boolean,
    downloadWifiOnly: Boolean,
    equalizerPreset: String?,
    onSelectSoundQualityMode: (mode: String) -> Unit,
    onSelectEqPreset: (preset: String, bassBoost: Short) -> Unit,
    onDownloadWifiOnlyToggle: (Boolean) -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "sound_spectrum_anim")
    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(550, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "wave_phase"
    )

    val activeQualityMode = when {
        dataSaverEnabled -> "SAVER"
        flacEnabled || audioQuality.equals("Very High", ignoreCase = true) -> "FLAC"
        else -> "HIGH"
    }

    val activePreset = equalizerPreset?.takeIf { it.isNotBlank() } ?: "Normal"

    // 7-bar spectrum weights shaped by active EQ preset
    val baseProfile = when (activePreset) {
        "Bass Boost" -> listOf(0.98f, 0.90f, 0.65f, 0.48f, 0.52f, 0.62f, 0.68f)
        "Vocal" -> listOf(0.42f, 0.54f, 0.85f, 0.96f, 0.88f, 0.58f, 0.45f)
        "Dance" -> listOf(0.94f, 0.82f, 0.55f, 0.68f, 0.78f, 0.90f, 0.95f)
        else -> listOf(0.65f, 0.72f, 0.78f, 0.74f, 0.76f, 0.70f, 0.64f)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 20.dp)
            .padding(top = 12.dp, bottom = 96.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Sound & Data",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = "Pick your audio power & data mode",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(14.dp))

        // LIVE SPECTRUM & BITRATE VISUALIZER CARD
        Surface(
            shape = RoundedCornerShape(26.dp),
            color = Color(0xFF10131A),
            border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)),
            tonalElevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .height(158.dp)
                .testTag("onboarding_sound_spectrum_preview")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
                                Color(0xFF10131A)
                            )
                        )
                    )
                    .padding(16.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.primary
                    ) {
                        Text(
                            text = when (activeQualityMode) {
                                "FLAC" -> "24-bit Hi-Fi FLAC"
                                "SAVER" -> "Data Saver • 64 kbps"
                                else -> "High • 256 kbps AAC"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(50),
                        color = Color.White.copy(alpha = 0.1f)
                    ) {
                        Text(
                            text = "EQ: $activePreset",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }

                // Animated 7-Bar Equalizer Stage
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(72.dp)
                        .padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.Bottom
                ) {
                    baseProfile.forEachIndexed { idx, baseHeight ->
                        val factor = if (idx % 2 == 0) wavePhase else (1.55f - wavePhase)
                        val animatedFraction by animateFloatAsState(
                            targetValue = (baseHeight * factor).coerceIn(0.22f, 1f),
                            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                            label = "eq_bar_$idx"
                        )
                        Box(
                            modifier = Modifier
                                .width(18.dp)
                                .fillMaxHeight(animatedFraction)
                                .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 4.dp, bottomEnd = 4.dp))
                                .background(
                                    Brush.verticalGradient(
                                        listOf(
                                            MaterialTheme.colorScheme.primary,
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                                        )
                                    )
                                )
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // SECTION 1: STREAMING QUALITY / DATA SAVER (3 Visual Cards)
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "Audio Quality",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SoundOptionTile(
                        icon = Icons.Default.HighQuality,
                        title = "Hi-Fi FLAC",
                        subtitle = "Best Sound",
                        selected = activeQualityMode == "FLAC",
                        onClick = { onSelectSoundQualityMode("FLAC") },
                        modifier = Modifier.weight(1f)
                    )
                    SoundOptionTile(
                        icon = Icons.Default.GraphicEq,
                        title = "High 256k",
                        subtitle = "Balanced",
                        selected = activeQualityMode == "HIGH",
                        onClick = { onSelectSoundQualityMode("HIGH") },
                        modifier = Modifier.weight(1f)
                    )
                    SoundOptionTile(
                        icon = Icons.Default.DataSaverOn,
                        title = "Save Data",
                        subtitle = "Low Mobile MB",
                        selected = activeQualityMode == "SAVER",
                        onClick = { onSelectSoundQualityMode("SAVER") },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // SECTION 2: SOUND VIBE / EQ PRESET (4 Visual Tiles)
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "Sound Vibe (Equalizer)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        Triple("Normal", "Natural", Icons.Default.Equalizer to 0.toShort()),
                        Triple("Bass Boost", "Deep Bass", Icons.Default.Speaker to 650.toShort()),
                        Triple("Vocal", "Clear Voice", Icons.Default.RecordVoiceOver to 150.toShort()),
                        Triple("Dance", "Party Loud", Icons.Default.GraphicEq to 500.toShort())
                    ).forEach { (presetKey, label, iconAndBass) ->
                        val (icon, bassValue) = iconAndBass
                        val selected = activePreset.equals(presetKey, ignoreCase = true)
                        SoundOptionTile(
                            icon = icon,
                            title = if (presetKey == "Normal") "Flat" else presetKey.substringBefore(" "),
                            subtitle = label,
                            selected = selected,
                            onClick = { onSelectEqPreset(presetKey, bassValue) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // SECTION 3: DOWNLOAD NETWORK (Wi-Fi Only vs Any Network)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            NetworkDownloadTile(
                icon = Icons.Default.Wifi,
                title = "Wi-Fi Only",
                subtitle = "Save Mobile Data",
                selected = downloadWifiOnly,
                onClick = { onDownloadWifiOnlyToggle(true) },
                modifier = Modifier.weight(1f)
            )
            NetworkDownloadTile(
                icon = Icons.Default.CellTower,
                title = "Any Network",
                subtitle = "Wi-Fi + Mobile",
                selected = !downloadWifiOnly,
                onClick = { onDownloadWifiOnlyToggle(false) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun SoundOptionTile(
    icon: ImageVector,
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
            )
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                shape = RoundedCornerShape(14.dp)
            )
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(
                    if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            textAlign = TextAlign.Center
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun NetworkDownloadTile(
    icon: ImageVector,
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.48f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
        ),
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}
