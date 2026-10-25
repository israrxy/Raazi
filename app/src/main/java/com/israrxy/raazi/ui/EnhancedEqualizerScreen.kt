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
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.israrxy.raazi.data.local.BluetoothDeviceManager
import com.israrxy.raazi.ui.components.GlassBox
import com.israrxy.raazi.viewmodel.MusicPlayerViewModel

@Composable
fun EnhancedEqualizerScreen(viewModel: MusicPlayerViewModel) {
    val context = LocalContext.current
    val equalizerState by viewModel.equalizerState.collectAsState()
    val customPresets by viewModel.customPresets.collectAsState()
    val visualizerData by viewModel.visualizerData.collectAsState()
    val device by viewModel.connectedAudioDevice.collectAsState()

    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasAudioPermission = granted
        if (granted) {
            viewModel.enableVisualizer(true)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.loadEqualizerState()
        viewModel.loadCustomPresets()
        viewModel.applyDeviceEqProfileIfAny()
    }

    DisposableEffect(hasAudioPermission, equalizerState.bands) {
        if (hasAudioPermission && equalizerState.bands > 0) {
            viewModel.enableVisualizer(true)
        }
        onDispose {
            viewModel.enableVisualizer(false)
        }
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
                        .height(64.dp)
                        .clickable(enabled = !hasAudioPermission) {
                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        },
                    shape = RoundedCornerShape(16.dp)
                ) {
                    SpectrumBars(
                        visualizerData = visualizerData,
                        isSupported = hasAudioPermission && viewModel.isVisualizerSupported(),
                        hasPermission = hasAudioPermission
                    )
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

