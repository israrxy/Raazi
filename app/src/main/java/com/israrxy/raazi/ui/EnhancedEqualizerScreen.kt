package com.israrxy.raazi.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.israrxy.raazi.data.local.BluetoothDeviceManager
import com.israrxy.raazi.ui.components.GlassBox
import com.israrxy.raazi.viewmodel.MusicPlayerViewModel

@Composable
fun EnhancedEqualizerScreen(viewModel: MusicPlayerViewModel) {
    val equalizerState by viewModel.equalizerState.collectAsState()
    val customPresets by viewModel.customPresets.collectAsState()
    val visualizerData by viewModel.visualizerData.collectAsState()
    val device by viewModel.connectedAudioDevice.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadEqualizerState()
        viewModel.loadCustomPresets()
        viewModel.applyDeviceEqProfileIfAny()
    }

    LaunchedEffect(equalizerState.bands) {
        if (viewModel.isVisualizerSupported()) viewModel.enableVisualizer(true)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (equalizerState.bands > 0) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Equalizer",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    if (device != null && device!!.type != BluetoothDeviceManager.AudioDevice.Type.BUILTIN_SPEAKER) {
                        Spacer(Modifier.width(10.dp))
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Outlined.Bluetooth, null, modifier = Modifier.size(12.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    when (device!!.type) {
                                        BluetoothDeviceManager.AudioDevice.Type.EARBUDS -> "Earbuds"
                                        BluetoothDeviceManager.AudioDevice.Type.HEADPHONES -> "Headphones"
                                        BluetoothDeviceManager.AudioDevice.Type.CAR -> "Car"
                                        BluetoothDeviceManager.AudioDevice.Type.WIRED -> "Wired"
                                        else -> "BT"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }

                // Spectrum Visualization
                GlassBox(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    SpectrumBars(visualizerData, viewModel.isVisualizerSupported())
                }

                Spacer(Modifier.height(16.dp))

                // Presets
                PresetChips(equalizerState, customPresets,
                    onSelect = { viewModel.usePreset(it) },
                    onCustomSelect = { viewModel.loadCustomPreset(it) },
                    onSave = { viewModel.saveCustomPreset(it) },
                    onDelete = { viewModel.deleteCustomPreset(it) }
                )

                Spacer(Modifier.height(16.dp))

                // Frequency Bands
                GlassBox(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    FrequencyBands(equalizerState) { band, level ->
                        viewModel.setBandLevel(band, level)
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Effects
                EffectsPanel(equalizerState,
                    onBass = { viewModel.setBassBoostStrength(it) },
                    onVirt = { viewModel.setVirtualizerStrength(it) },
                    onReverb = { viewModel.setReverbPreset(it) }
                )

                Spacer(Modifier.height(16.dp))

                // Actions
                ActionRow(
                    onSave = { viewModel.saveEqualizerSettings() },
                    onShare = { viewModel.exportEqualizerSettings() },
                    onReset = { viewModel.usePreset("Flat") }
                )

                Spacer(Modifier.height(100.dp))
            }
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.GraphicEq, null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(Modifier.height(14.dp))
                    Text(
                        "Equalizer not available",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }
    }
}

@Composable
private fun SpectrumBars(visualizerData: ByteArray?, isSupported: Boolean) {
    if (isSupported && visualizerData != null) {
        val processed = remember(visualizerData) { processFftData(visualizerData, 48) }
        val infiniteTransition = rememberInfiniteTransition(label = "spectrum")
        val pulse by infiniteTransition.animateFloat(
            initialValue = 0.96f, targetValue = 1.04f,
            animationSpec = infiniteRepeatable(tween(600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "pulse"
        )
        val primary = MaterialTheme.colorScheme.primary
        val secondary = MaterialTheme.colorScheme.secondary

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
                val fraction = i.toFloat() / processed.size
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
                if (isSupported) "Waiting for audio..." else "Spectrum not available",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun PresetChips(
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
private fun FrequencyBands(
    state: MusicPlayerViewModel.EqualizerState,
    onBandLevel: (Short, Short) -> Unit
) {
    val minLevel = state.minLevel.toFloat()
    val maxLevel = state.maxLevel.toFloat()
    val midPoint = (minLevel + maxLevel) / 2f

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

@Composable
private fun EffectsPanel(
    state: MusicPlayerViewModel.EqualizerState,
    onBass: (Short) -> Unit,
    onVirt: (Short) -> Unit,
    onReverb: (Int) -> Unit
) {
    GlassBox(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Effects",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            if (state.bassBoostSupported) {
                EffectSlider("Bass Boost", state.bassBoostStrength.toFloat(), 0f..1000f) {
                    onBass(it.toInt().toShort())
                }
                Spacer(Modifier.height(14.dp))
            }

            if (state.virtualizerSupported) {
                EffectSlider("3D Surround", state.virtualizerStrength.toFloat(), 0f..1000f) {
                    onVirt(it.toInt().toShort())
                }
                Spacer(Modifier.height(14.dp))
            }

            if (state.reverbSupported) {
                ReverbDropdown(state.reverbPreset, onReverb)
            }
        }
    }
}

@Composable
private fun EffectSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onValueChange: (Float) -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(
                "${((value / range.endInclusive) * 100).toInt()}%",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.height(6.dp))
        Slider(
            value = value, onValueChange = onValueChange, valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        )
    }
}

@Composable
private fun ReverbDropdown(current: Int, onSelect: (Int) -> Unit) {
    val presets = listOf("None", "Small Room", "Medium Room", "Large Room", "Medium Hall", "Large Hall", "Plate")
    val currentName = presets.getOrElse(current) { "None" }
    var expanded by remember { mutableStateOf(false) }

    Column {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Reverb", color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.clickable { expanded = true }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(currentName, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Default.ArrowDropDown, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            presets.forEachIndexed { i, p ->
                DropdownMenuItem(
                    text = { Text(p) },
                    onClick = { onSelect(i); expanded = false },
                    leadingIcon = if (i == current) {{ Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp)) }} else null
                )
            }
        }
    }
}

@Composable
private fun ActionRow(onSave: () -> Unit, onShare: () -> Unit, onReset: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Button(
            onClick = onSave,
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(14.dp)
        ) {
            Icon(Icons.Default.Save, null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Save")
        }
        OutlinedButton(
            onClick = onShare,
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(14.dp)
        ) {
            Icon(Icons.Default.Share, null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Share")
        }
        OutlinedButton(
            onClick = onReset,
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(14.dp)
        ) {
            Icon(Icons.Default.RestartAlt, null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Flat")
        }
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
