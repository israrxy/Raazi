package com.israrxy.raazi.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.israrxy.raazi.viewmodel.MusicPlayerViewModel

@Composable
internal fun SpectrumBars(
    visualizerData: ByteArray?,
    isSupported: Boolean,
    hasPermission: Boolean = true
) {
    if (isSupported && visualizerData != null) {
        val processed = remember(visualizerData) { processFftData(visualizerData, 48) }
        val infiniteTransition = rememberInfiniteTransition(label = "spectrum")
        val pulse by infiniteTransition.animateFloat(
            initialValue = 0.96f, targetValue = 1.04f,
            animationSpec = infiniteRepeatable(tween(600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "pulse"
        )
        val primary = MaterialTheme.colorScheme.primary

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 8.dp)
        ) {
            val w = size.width
            val h = size.height
            val gap = 2.dp.toPx()
            val barW = (w - gap * (processed.size - 1)) / processed.size

            processed.forEachIndexed { i, amp ->
                val barHeight = (h * amp * pulse).coerceAtLeast(2.dp.toPx())
                val color = primary.copy(alpha = 0.4f + amp * 0.6f)
                drawRoundRect(
                    color = color,
                    topLeft = Offset(i * (barW + gap), h - barHeight),
                    size = Size(barW, barHeight),
                    cornerRadius = CornerRadius(barW * 0.35f)
                )
            }
        }
    } else {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                when {
                    !hasPermission -> "Tap to enable live audio spectrum"
                    isSupported -> "Waiting for audio..."
                    else -> "Spectrum not available on this device"
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
internal fun PresetChips(
    state: MusicPlayerViewModel.EqualizerState,
    customPresets: List<MusicPlayerViewModel.CustomPreset>,
    onSelect: (String) -> Unit,
    onCustomSelect: (MusicPlayerViewModel.CustomPreset) -> Unit,
    onSave: (String) -> Unit,
    onDelete: (MusicPlayerViewModel.CustomPreset) -> Unit
) {
    var showSaveDialog by remember { mutableStateOf(false) }
    val genres = listOf("Flat", "Rock", "Pop", "Jazz", "Classical", "Electronic", "Hip-Hop", "Acoustic", "Metal", "Podcast")
    val primary = MaterialTheme.colorScheme.primary
    val primaryContainer = MaterialTheme.colorScheme.primaryContainer
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant

    Column {
        Text(
            "Presets",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(genres) { preset ->
                val active = state.currentPreset == preset
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = if (active) primaryContainer else surfaceVariant,
                    contentColor = if (active) primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    border = BorderStroke(
                        1.dp,
                        if (active) primary else MaterialTheme.colorScheme.outlineVariant
                    ),
                    modifier = Modifier.clickable { onSelect(preset) }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (active) {
                            Icon(Icons.Default.Equalizer, null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(6.dp))
                        }
                        Text(
                            preset,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }
        }

        val systemPresets = state.presets.filter { it !in genres }
        if (systemPresets.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(systemPresets) { preset ->
                    val active = state.currentPreset == preset
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = if (active) MaterialTheme.colorScheme.secondaryContainer else surfaceVariant,
                        contentColor = if (active) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                        border = BorderStroke(1.dp, if (active) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.clickable { onSelect(preset) }
                    ) {
                        Text(
                            preset,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }
        }

        if (customPresets.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(customPresets) { preset ->
                    val active = state.currentPreset == preset.name
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = if (active) MaterialTheme.colorScheme.tertiaryContainer else surfaceVariant,
                        contentColor = if (active) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                        border = BorderStroke(1.dp, if (active) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.clickable { onCustomSelect(preset) }
                    ) {
                        Row(
                            modifier = Modifier.padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(preset.name, style = MaterialTheme.typography.labelLarge, fontWeight = if (active) FontWeight.Bold else FontWeight.Medium)
                            IconButton(onClick = { onDelete(preset) }, modifier = Modifier.size(22.dp)) {
                                Icon(Icons.Default.Close, null, modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Surface(
            shape = RoundedCornerShape(999.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.clickable { showSaveDialog = true }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(6.dp))
                Text("Save", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
            }
        }
    }

    if (showSaveDialog) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text("Save Preset") },
            text = {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    placeholder = { Text("Preset name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = { if (name.isNotBlank()) { onSave(name); showSaveDialog = false } }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
internal fun FrequencyBands(
    state: MusicPlayerViewModel.EqualizerState,
    onBandLevel: (Short, Short) -> Unit
) {
    val minLevel = state.minLevel.toFloat()
    val maxLevel = state.maxLevel.toFloat()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            listOf("${(maxLevel / 100).toInt()}dB", "0dB", "${(minLevel / 100).toInt()}dB").forEach {
                Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
            }
        }

        Spacer(Modifier.height(4.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            state.centerFreqs.forEachIndexed { index, freq ->
                val level = state.currentLevels.getOrElse(index) { 0 }
                BandSlider(
                    freqLabel = formatFreq(freq),
                    level = level.toFloat(),
                    minLevel = minLevel,
                    maxLevel = maxLevel,
                    onValueChange = { onBandLevel(index.toShort(), it.toInt().toShort()) }
                )
            }
        }
    }
}

@Composable
private fun BandSlider(
    freqLabel: String,
    level: Float,
    minLevel: Float,
    maxLevel: Float,
    onValueChange: (Float) -> Unit
) {
    val mid = (minLevel + maxLevel) / 2f
    val db = ((level - mid) / 100f).toInt()
    val gainColor = when {
        db > 0 -> MaterialTheme.colorScheme.primary
        db < 0 -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(44.dp)
    ) {
        Text(
            "${if (db > 0) "+" else ""}$db",
            color = gainColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
        Spacer(Modifier.height(6.dp))

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.height(200.dp).width(44.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )
            Slider(
                value = level,
                onValueChange = onValueChange,
                valueRange = minLevel..maxLevel,
                modifier = Modifier
                    .graphicsLayer {
                        rotationZ = 270f
                        transformOrigin = TransformOrigin(0.5f, 0.5f)
                    }
                    .layout { measurable, constraints ->
                        val p = measurable.measure(
                            Constraints(
                                minWidth = constraints.minHeight, maxWidth = constraints.maxHeight,
                                minHeight = constraints.minWidth, maxHeight = constraints.maxWidth
                            )
                        )
                        layout(p.height, p.width) {
                            p.place(-p.width / 2 + p.height / 2, -p.height / 2 + p.width / 2)
                        }
                    }
                    .width(200.dp),
                colors = SliderDefaults.colors(
                    thumbColor = gainColor,
                    activeTrackColor = gainColor,
                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                    activeTickColor = MaterialTheme.colorScheme.surfaceVariant,
                    inactiveTickColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        }

        Spacer(Modifier.height(6.dp))
        Text(
            freqLabel,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}

private fun formatFreq(freq: Int): String = when {
    freq < 1000 -> "${freq}Hz"
    freq < 1000000 -> "${freq / 1000}k"
    else -> "${freq / 1000000}k"
}

private fun processFftData(fftData: ByteArray, bandCount: Int): List<Float> {
    val out = mutableListOf<Float>()
    val fftSize = fftData.size / 2
    val samplesPerBand = (fftSize / bandCount).coerceAtLeast(1)
    for (i in 0 until bandCount) {
        var magnitude = 0f
        val start = i * samplesPerBand + 1
        val end = minOf((i + 1) * samplesPerBand, fftSize)
        for (j in start until end) {
            if (j * 2 + 1 < fftData.size) {
                val real = fftData[j * 2].toFloat()
                val imag = fftData[j * 2 + 1].toFloat()
                magnitude += kotlin.math.sqrt(real * real + imag * imag)
            }
        }
        val norm = (magnitude / samplesPerBand) / 128f
        val smoothed = (norm * 0.7f + (out.lastOrNull() ?: 0f) * 0.3f).coerceIn(0f, 1f)
        out.add(smoothed)
    }
    return out
}
