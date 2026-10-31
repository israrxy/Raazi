package com.israrxy.raazi.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
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
fun EqualizerScreen(viewModel: MusicPlayerViewModel) {
    val equalizerState by viewModel.equalizerState.collectAsState()
    
    // Load state if empty
    LaunchedEffect(Unit) {
        viewModel.loadEqualizerState()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(16.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Text(
                text = "Equalizer",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(bottom = 24.dp, top = 24.dp)
            )

            if (equalizerState.bands > 0) {
                GlassBox(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(bottom = 80.dp), // Space for nav bar
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        // Preset Selector
                        var expanded by remember { mutableStateOf(false) }
                        
                        Box(modifier = Modifier.padding(bottom = 32.dp)) {
                            TextButton(onClick = { expanded = true }) {
                                Text(
                                    text = equalizerState.currentPreset,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            
                            DropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false },
                                modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                            ) {
                                equalizerState.presets.forEach { preset ->
                                    DropdownMenuItem(
                                        text = { Text(preset) },
                                        onClick = {
                                            viewModel.usePreset(preset)
                                            expanded = false
                                        }
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text("Custom") },
                                    onClick = {
                                        // "Custom" is set automatically when sliders move
                                        expanded = false
                                    }
                                )
                            }
                        }

                        // Bands
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

                                EqualizerBandSlider(
                                    freqLabel = freqLabel,
                                    level = currentLevel,
                                    range = minLevel..maxLevel,
                                    onValueChange = { newLevel ->
                                        viewModel.setBandLevel(index.toShort(), newLevel)
                                    }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(32.dp))

                        Button(
                            onClick = { viewModel.saveEqualizerSettings() },
                            modifier = Modifier.padding(bottom = 16.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Save Configuration")
                        }
                    }
                }
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Equalizer not available", color = Color.Gray)
                }
            }
        }
    }
}

@Composable
fun EqualizerBandSlider(
    freqLabel: String,
    level: Short,
    range: IntRange,
    onValueChange: (Short) -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(40.dp)
    ) {
        // Gain Label (dB)
        Text(
            text = "${level / 100}dB",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 10.sp
        )
        
        Spacer(modifier = Modifier.height(8.dp))

        // Vertical Slider using rotated Box
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .height(200.dp)
                .width(40.dp)
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
                                maxHeight = constraints.maxHeight,
                            )
                        )
                        layout(placeable.height, placeable.width) {
                            placeable.place(-placeable.width / 2 + placeable.height / 2, -placeable.height / 2 + placeable.width / 2)
                        }
                    }
                    .width(200.dp), // Visual height becomes width before rotation logic adjustment
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Frequency Label
        Text(
            text = freqLabel,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            fontSize = 10.sp,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}
