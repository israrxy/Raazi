package com.israrxy.raazi.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.israrxy.raazi.data.region.RegionManager

@Composable
fun OnboardingPersonalizePage(
    themeMode: String,
    useDynamicColor: Boolean,
    pastelAccent: String,
    contentCountry: String,
    detectedCountryCode: String,
    onThemeChange: (String) -> Unit,
    onDynamicColorToggle: (Boolean) -> Unit,
    onPastelAccentChange: (String) -> Unit,
    onRegionClick: () -> Unit
) {
    val displayCountryCode = if (contentCountry.isBlank() || contentCountry.equals("AUTO", ignoreCase = true)) {
        detectedCountryCode
    } else {
        contentCountry
    }
    val regionInfo = RegionManager.SUPPORTED_REGIONS.firstOrNull { it.code.equals(displayCountryCode, ignoreCase = true) }
    val flagEmoji = regionInfo?.flagEmoji ?: "🌐"
    val regionName = regionInfo?.name ?: RegionManager.getRegionName(displayCountryCode)
    val isAuto = contentCountry.isBlank() || contentCountry.equals("AUTO", ignoreCase = true)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 24.dp)
            .padding(top = 16.dp, bottom = 96.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(68.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Palette,
                contentDescription = null,
                modifier = Modifier.size(34.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(Modifier.height(16.dp))

        Text(
            text = "Make It Yours",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(Modifier.height(4.dp))

        Text(
            text = "Choose your aesthetic and confirm your music region.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(20.dp))

        // Card 1: Content Region Card
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onRegionClick() }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = flagEmoji,
                    fontSize = 32.sp,
                    modifier = Modifier.padding(end = 14.dp)
                )

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = regionName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.width(6.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = if (isAuto) "Auto-Detected" else "Selected",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Text(
                        text = "Charts & Quick Picks tuned to $regionName",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                FilledTonalButton(
                    onClick = onRegionClick,
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text("Change", fontWeight = FontWeight.SemiBold)
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // Card 2: Theme Selector
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "App Theme",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )

                Spacer(Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("System", "Dark", "Light").forEach { mode ->
                        val selected = themeMode.equals(mode, ignoreCase = true)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
                                )
                                .border(
                                    1.dp,
                                    if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                    RoundedCornerShape(10.dp)
                                )
                                .clickable { onThemeChange(mode) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = mode,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Dynamic Color",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Harmonize with system wallpaper on Android 12+",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = useDynamicColor,
                        onCheckedChange = onDynamicColorToggle
                    )
                }

                if (!useDynamicColor) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "Accent Color",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("Emerald", "Lavender", "Sky", "Peach").forEach { accent ->
                            val selected = pastelAccent.equals(accent, ignoreCase = true)
                            val accentColor = when (accent) {
                                "Emerald" -> Color(0xFF10B981)
                                "Lavender" -> Color(0xFFB39DDB)
                                "Sky" -> Color(0xFF90CAF9)
                                "Peach" -> Color(0xFFFFCC80)
                                else -> Color(0xFF10B981)
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(36.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (selected) accentColor.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surface)
                                    .border(
                                        width = if (selected) 2.dp else 1.dp,
                                        color = if (selected) accentColor else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    .clickable { onPastelAccentChange(accent) },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = accent,
                                    fontWeight = FontWeight.SemiBold,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (selected) accentColor else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
