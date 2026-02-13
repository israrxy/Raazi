package com.israrxy.raazi.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.israrxy.raazi.ui.components.GlassBox
import com.israrxy.raazi.viewmodel.MusicPlayerViewModel
import kotlin.math.roundToInt

@Composable
fun EnhancedEqualizerScreen(viewModel: MusicPlayerViewModel) {
    val equalizerState by viewModel.equalizerState.collectAsState()
    val customPresets by viewModel.customPresets.collectAsState()
    val visualizerData by viewModel.visualizerData.collectAsState()
    
    // Load state if empty
    LaunchedEffect(Unit) {
        viewModel.loadEqualizerState()
        viewModel.loadCustomPresets()
    }

    // Enable visualizer when screen is displayed and supported
    LaunchedEffect(equalizerState.bands) {
        if (viewModel.isVisualizerSupported()) {
            viewModel.enableVisualizer(true)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = "Advanced Equalizer",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(bottom = 24.dp, top = 24.dp)
            )

            if (equalizerState.bands > 0) {
                // Real-time Spectrum Visualization
                RealTimeSpectrumVisualization(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                        .padding(bottom = 16.dp),
                    visualizerData = visualizerData,
                    isSupported = viewModel.isVisualizerSupported()
                )

                // Presets Section
                PresetSection(
                    equalizerState = equalizerState,
                    customPresets = customPresets,
                    onPresetSelected = { viewModel.usePreset(it) },
                    onCustomPresetSelected = { viewModel.loadCustomPreset(it) },
                    onSaveCustom = { name -> viewModel.saveCustomPreset(name) },
                    onDeleteCustom = { preset -> viewModel.deleteCustomPreset(preset) }
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Equalizer Bands
                GlassBox(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    EqualizerBandsSection(
                        equalizerState = equalizerState,
                        onBandLevelChanged = { band, level -> 
                            viewModel.setBandLevel(band, level) 
                        }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Advanced Effects
                AdvancedEffectsSection(
                    equalizerState = equalizerState,
                    onBassBoostChanged = { viewModel.setBassBoostStrength(it) },
                    onVirtualizerChanged = { viewModel.setVirtualizerStrength(it) },
                    onReverbChanged = { viewModel.setReverbPreset(it) }
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Action Buttons
                ActionButtonsSection(
                    onSave = { viewModel.saveEqualizerSettings() },
                    onExport = { viewModel.exportEqualizerSettings() },
                    onReset = { viewModel.usePreset("Flat") }
                )

                Spacer(modifier = Modifier.height(80.dp)) // Space for nav bar
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(), 
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.GraphicEq,
                            contentDescription = null,
                            tint = Color.Gray,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "Equalizer not available", 
                            color = Color.Gray,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RealTimeSpectrumVisualization(
    modifier: Modifier = Modifier,
    visualizerData: ByteArray?,
    isSupported: Boolean
) {
    GlassBox(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp)
    ) {
        if (isSupported && visualizerData != null) {
            // Process FFT data for visualization
            val processedData = remember(visualizerData) {
                processFftData(visualizerData, 32) // Create 32 frequency bands
            }
            
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.Bottom
            ) {
                processedData.forEach { amplitude ->
                    val height = (60.dp * amplitude) // Max height 60dp
                    Box(
                        modifier = Modifier
                            .width(4.dp)
                            .height(height)
                            .background(
                                color = interpolateSpectrumColor(amplitude),
                                shape = RoundedCornerShape(2.dp)
                            )
                    )
                }
            }
        } else {
            // Fallback when visualizer is not supported
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isSupported) "Waiting for audio..." else "Spectrum analysis not available",
                    color = Color.Gray,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

private fun processFftData(fftData: ByteArray, bandCount: Int): List<Float> {
    val processedData = mutableListOf<Float>()
    val fftSize = fftData.size / 2
    val samplesPerBand = fftSize / bandCount
    
    for (i in 0 until bandCount) {
        var magnitude = 0f
        val startIdx = i * samplesPerBand + 1 // Skip DC component
        val endIdx = minOf((i + 1) * samplesPerBand, fftSize)
        
        for (j in startIdx until endIdx) {
            if (j < fftData.size) {
                val real = fftData[j * 2].toFloat()
                val imag = fftData[j * 2 + 1].toFloat()
                magnitude += kotlin.math.sqrt(real * real + imag * imag)
            }
        }
        
        // Normalize and smooth the data
        val normalizedMagnitude = (magnitude / samplesPerBand) / 128f // Normalize to 0-1 range
        val smoothed = (normalizedMagnitude * 0.7f + (processedData.lastOrNull() ?: 0f) * 0.3f)
        processedData.add(smoothed.coerceIn(0f, 1f))
    }
    
    return processedData
}

private fun interpolateSpectrumColor(amplitude: Float): Color {
    return when {
        amplitude < 0.3f -> Color.Green.copy(alpha = 0.7f + amplitude * 0.3f)
        amplitude < 0.7f -> Color.Yellow.copy(alpha = 0.7f + amplitude * 0.3f)
        else -> Color.Red.copy(alpha = 0.7f + amplitude * 0.3f)
    }
}

@Composable
private fun PresetSection(
    equalizerState: MusicPlayerViewModel.EqualizerState,
    customPresets: List<MusicPlayerViewModel.CustomPreset>,
    onPresetSelected: (String) -> Unit,
    onCustomPresetSelected: (MusicPlayerViewModel.CustomPreset) -> Unit,
    onSaveCustom: (String) -> Unit,
    onDeleteCustom: (MusicPlayerViewModel.CustomPreset) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var showSaveDialog by remember { mutableStateOf(false) }
    
    GlassBox(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Presets",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            
            // Current Preset Display
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Simplified preset selector
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    TextButton(
                        onClick = { expanded = !expanded },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = equalizerState.currentPreset,
                            color = Color.White
                        )
                    }
                }
                
                Spacer(modifier = Modifier.width(8.dp))
                
                IconButton(
                    onClick = { showSaveDialog = true },
                    enabled = equalizerState.currentPreset == "Custom"
                ) {
                    Icon(
                        Icons.Default.Save,
                        contentDescription = "Save preset",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            
            // Preset dropdown
            if (expanded) {
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // System and Genre Presets
                    equalizerState.presets.forEach { preset ->
                        TextButton(
                            onClick = {
                                onPresetSelected(preset)
                                expanded = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                preset,
                                color = if (preset in listOf("Rock", "Pop", "Jazz", "Classical", "Electronic", "Hip-Hop", "Acoustic", "Blues", "Metal", "Podcast")) {
                                    MaterialTheme.colorScheme.primary
                                } else Color.White
                            )
                        }
                    }
                    
                    // Custom Presets
                    if (customPresets.isNotEmpty()) {
                        Divider(color = MaterialTheme.colorScheme.onSurfaceVariant)
                        customPresets.forEach { preset ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(
                                    onClick = {
                                        onCustomPresetSelected(preset)
                                        expanded = false
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        preset.name,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        onDeleteCustom(preset)
                                        expanded = false
                                    }
                                ) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "Delete preset",
                                        tint = Color.Red
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    
    // Save Custom Preset Dialog
    if (showSaveDialog) {
        var presetName by remember { mutableStateOf("") }
        
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text("Save Custom Preset") },
            text = {
                OutlinedTextField(
                    value = presetName,
                    onValueChange = { presetName = it },
                    label = { Text("Preset Name") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (presetName.isNotEmpty()) {
                            onSaveCustom(presetName)
                            showSaveDialog = false
                        }
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun EqualizerBandsSection(
    equalizerState: MusicPlayerViewModel.EqualizerState,
    onBandLevelChanged: (Short, Short) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Frequency Bands",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(bottom = 24.dp)
        )
        
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            equalizerState.centerFreqs.forEachIndexed { index, freq ->
                val currentLevel = equalizerState.currentLevels.getOrElse(index) { 0 }
                val minLevel = equalizerState.minLevel
                val maxLevel = equalizerState.maxLevel
                
                val freqLabel = if (freq < 1000000) {
                    "${freq / 1000}Hz"
                } else {
                    "${freq / 1000000}kHz"
                }

                EnhancedEqualizerBandSlider(
                    freqLabel = freqLabel,
                    level = currentLevel,
                    range = minLevel..maxLevel,
                    onValueChange = { newLevel ->
                        onBandLevelChanged(index.toShort(), newLevel)
                    }
                )
            }
        }
    }
}

@Composable
private fun AdvancedEffectsSection(
    equalizerState: MusicPlayerViewModel.EqualizerState,
    onBassBoostChanged: (Short) -> Unit,
    onVirtualizerChanged: (Short) -> Unit,
    onReverbChanged: (Int) -> Unit
) {
    GlassBox(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Advanced Effects",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            
            // Bass Boost
            if (equalizerState.bassBoostSupported) {
                EffectSlider(
                    title = "Bass Boost",
                    value = equalizerState.bassBoostStrength.toFloat(),
                    valueRange = 0f..1000f,
                    onValueChange = { onBassBoostChanged(it.toInt().toShort()) },
                    unit = ""
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
            
            // Virtualizer
            if (equalizerState.virtualizerSupported) {
                EffectSlider(
                    title = "Virtualizer (3D)",
                    value = equalizerState.virtualizerStrength.toFloat(),
                    valueRange = 0f..1000f,
                    onValueChange = { onVirtualizerChanged(it.toInt().toShort()) },
                    unit = ""
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
            
            // Reverb
            if (equalizerState.reverbSupported) {
                var expanded by remember { mutableStateOf(false) }
                val reverbPresets = listOf("None", "Small Room", "Medium Room", "Large Room", "Medium Hall", "Large Hall", "Plate")
                val currentReverbName = if (equalizerState.reverbPreset < reverbPresets.size) {
                    reverbPresets[equalizerState.reverbPreset]
                } else "None"
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Reverb",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        TextButton(
                            onClick = { expanded = !expanded },
                            modifier = Modifier.width(140.dp)
                        ) {
                            Text(
                                currentReverbName,
                                color = Color.White
                            )
                        }
                    }
                }
                
                if (expanded) {
                    Column(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        reverbPresets.forEachIndexed { index, preset ->
                            TextButton(
                                onClick = {
                                    onReverbChanged(index)
                                    expanded = false
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(preset, color = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EffectSlider(
    title: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    unit: String
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = title,
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "${value.toInt()}$unit",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodySmall
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        )
    }
}

@Composable
private fun ActionButtonsSection(
    onSave: () -> Unit,
    onExport: () -> Unit,
    onReset: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        Button(
            onClick = onSave,
            modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Save")
        }
        
        OutlinedButton(
            onClick = onExport,
            modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Export")
        }
        
        OutlinedButton(
            onClick = onReset,
            modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Reset")
        }
    }
}

@Composable
private fun EnhancedEqualizerBandSlider(
    freqLabel: String,
    level: Short,
    range: IntRange,
    onValueChange: (Short) -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(45.dp)
    ) {
        // Gain Label (dB) with better styling
        Text(
            text = "${level / 100}dB",
            style = MaterialTheme.typography.labelSmall,
            color = if (level > 0) Color.Green else if (level < 0) Color.Red else Color.Gray,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(8.dp))

        // Enhanced Vertical Slider
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .height(220.dp)
                .width(45.dp)
        ) {
            Slider(
                value = level.toFloat(),
                onValueChange = { onValueChange(it.toInt().toShort()) },
                valueRange = range.first.toFloat()..range.last.toFloat(),
                modifier = Modifier
                    .graphicsLayer {
                        rotationZ = 270f
                        transformOrigin = TransformOrigin(0.5f, 0.5f)
                    }
                    .layout { measurable, constraints ->
                        val placeable = measurable.measure(
                            Constraints(
                                minWidth = constraints.minHeight,
                                maxWidth = constraints.maxHeight,
                                minHeight = constraints.minWidth,
                                maxHeight = constraints.maxWidth,
                            )
                        )
                        layout(placeable.height, placeable.width) {
                            placeable.place(-placeable.width / 2 + placeable.height / 2, -placeable.height / 2 + placeable.width / 2)
                        }
                    }
                    .width(220.dp),
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = Color.Transparent,
                    activeTickColor = Color.Transparent,
                    inactiveTickColor = Color.Transparent
                )
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Enhanced Frequency Label
        Text(
            text = freqLabel,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}