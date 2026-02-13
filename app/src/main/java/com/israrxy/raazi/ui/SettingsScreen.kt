package com.israrxy.raazi.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.israrxy.raazi.data.local.SettingsDataStore
import com.israrxy.raazi.ui.theme.*
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    
    // Settings DataStore
    val settingsDataStore = remember { SettingsDataStore(context) }
    
    // Observe settings
    val dataSaverEnabled by settingsDataStore.dataSaverEnabled.collectAsState(initial = false)
    val audioQuality by settingsDataStore.audioQuality.collectAsState(initial = "Very High")
    val crossfadeDuration by settingsDataStore.crossfadeDuration.collectAsState(initial = "Off")
    val useDynamicColor by settingsDataStore.useDynamicColor.collectAsState(initial = false)
    val themeMode by settingsDataStore.themeMode.collectAsState(initial = "System")
    val downloadWifiOnly by settingsDataStore.downloadWifiOnly.collectAsState(initial = false)
    val downloadQuality by settingsDataStore.downloadQuality.collectAsState(initial = "Very High")
    val maxConcurrentDownloads by settingsDataStore.maxConcurrentDownloads.collectAsState(initial = "2")
    
    // Calculate actual cache size
    val cacheSize = remember {
        try {
            val cacheDir = context.cacheDir
            val size = calculateDirSize(cacheDir)
            formatFileSize(size)
        } catch (e: Exception) {
            "0 MB"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(scrollState)
            .padding(horizontal = 24.dp, vertical = 24.dp)
            .padding(bottom = 100.dp) // Bottom padding for nav bar
    ) {
        // HEADER
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(bottom = 32.dp)
        )



        // THEME SECTION
        SettingsSection(title = "THEME") {
            SettingsItem(
                title = "App Theme",
                value = themeMode,
                valueColor = Emerald500,
                onClick = {
                    val modes = listOf("System", "Light", "Dark")
                    val currentIndex = modes.indexOf(themeMode)
                    val nextMode = modes[(currentIndex + 1) % modes.size]
                    scope.launch {
                        settingsDataStore.setThemeMode(nextMode)
                    }
                }
            )

            SettingsToggle(
                title = "Material You / Dynamic Colors",
                checked = useDynamicColor,
                onCheckedChange = { enabled ->
                    scope.launch {
                        settingsDataStore.setDynamicColor(enabled)
                    }
                }
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        // PLAYBACK SECTION
        SettingsSection(title = "PLAYBACK") {
            // Data Saver Toggle
            SettingsToggle(
                title = "Data Saver",
                checked = dataSaverEnabled,
                onCheckedChange = { enabled ->
                    scope.launch {
                        settingsDataStore.setDataSaver(enabled)
                    }
                }
            )
            
            // Audio Quality Selector
            SettingsItem(
                title = "Audio Quality",
                value = audioQuality,
                valueColor = Emerald500,
                onClick = {
                    // Cycle through qualities
                    val qualities = listOf("Low", "Normal", "High", "Very High")
                    val currentIndex = qualities.indexOf(audioQuality)
                    val nextQuality = qualities[(currentIndex + 1) % qualities.size]
                    scope.launch {
                        settingsDataStore.setAudioQuality(nextQuality)
                    }
                    Toast.makeText(context, "Audio quality: $nextQuality", Toast.LENGTH_SHORT).show()
                }
            )
            
            // Crossfade Selector
            SettingsItem(
                title = "Crossfade",
                value = crossfadeDuration,
                onClick = {
                    // Cycle through options
                    val options = listOf("Off", "3s", "5s", "8s", "12s")
                    val currentIndex = options.indexOf(crossfadeDuration)
                    val nextOption = options[(currentIndex + 1) % options.size]
                    scope.launch {
                        settingsDataStore.setCrossfadeDuration(nextOption)
                    }
                }
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        // DOWNLOADS SECTION
        SettingsSection(title = "DOWNLOADS") {
            SettingsToggle(
                title = "WiFi Only",
                checked = downloadWifiOnly,
                onCheckedChange = { enabled ->
                    scope.launch {
                        settingsDataStore.setDownloadWifiOnly(enabled)
                    }
                }
            )

            SettingsItem(
                title = "Download Quality",
                value = downloadQuality,
                valueColor = Emerald500,
                onClick = {
                    val qualities = listOf("Normal", "High", "Very High", "Best")
                    val currentIndex = qualities.indexOf(downloadQuality)
                    val nextQuality = qualities[(currentIndex + 1) % qualities.size]
                    scope.launch {
                        settingsDataStore.setDownloadQuality(nextQuality)
                    }
                    Toast.makeText(context, "Download quality: $nextQuality", Toast.LENGTH_SHORT).show()
                }
            )

            SettingsItem(
                title = "Max Concurrent Downloads",
                value = maxConcurrentDownloads,
                onClick = {
                    val options = listOf("1", "2", "3", "4")
                    val currentIndex = options.indexOf(maxConcurrentDownloads)
                    val nextOption = options[(currentIndex + 1) % options.size]
                    scope.launch {
                        settingsDataStore.setMaxConcurrentDownloads(nextOption)
                    }
                }
            )
        }
        
        // APP SECTION
        SettingsSection(title = "APP") {
            SettingsItem(
                title = "Clear Cache",
                value = cacheSize,
                onClick = {
                    try {
                        context.cacheDir.deleteRecursively()
                        Toast.makeText(context, "Cache cleared!", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Toast.makeText(context, "Failed to clear cache", Toast.LENGTH_SHORT).show()
                    }
                }
            )
            
            SettingsItem(
                title = "Reset Settings",
                valueColor = ErrorRed,
                onClick = {
                    scope.launch {
                        settingsDataStore.clearAll()
                        Toast.makeText(context, "Settings reset!", Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Text(
            text = "Raazi v3.0.0",
            style = MaterialTheme.typography.labelSmall,
            color = Zinc700,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
    }
}

// Helper functions
private fun calculateDirSize(dir: File): Long {
    var size: Long = 0
    if (dir.isDirectory) {
        dir.listFiles()?.forEach { file ->
            size += if (file.isDirectory) calculateDirSize(file) else file.length()
        }
    }
    return size
}

private fun formatFileSize(size: Long): String {
    return when {
        size >= 1024 * 1024 * 1024 -> String.format("%.1f GB", size / (1024.0 * 1024 * 1024))
        size >= 1024 * 1024 -> String.format("%.1f MB", size / (1024.0 * 1024))
        size >= 1024 -> String.format("%.1f KB", size / 1024.0)
        else -> "$size B"
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = Zinc600,
            letterSpacing = 2.sp,
            modifier = Modifier.padding(start = 8.dp, bottom = 12.dp)
        )
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            content()
        }
    }
}

@Composable
private fun SettingsItem(
    title: String,
    value: String? = null,
    valueColor: Color = Zinc500,
    showChevron: Boolean = false,
    onClick: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 12.dp, horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = if (valueColor == ErrorRed) ErrorRed else MaterialTheme.colorScheme.onBackground
        )
        
        if (value != null) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
                color = valueColor,
                fontWeight = FontWeight.Medium
            )
        } else if (showChevron) {
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = Zinc600,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun SettingsToggle(
    title: String, 
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp, horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onBackground
        )
        
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Zinc50,
                checkedTrackColor = Emerald500,
                uncheckedThumbColor = Zinc400,
                uncheckedTrackColor = Zinc800,
                uncheckedBorderColor = Color.Transparent
            )
        )
    }
}
