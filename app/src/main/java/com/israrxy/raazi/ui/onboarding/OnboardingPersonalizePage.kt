package com.israrxy.raazi.ui.onboarding

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
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
    onRegionClick: () -> Unit,
    pureBlack: Boolean = false,
    blurPlayerBackground: Boolean = true,
    onPureBlackToggle: (Boolean) -> Unit = {},
    onBlurPlayerBackgroundToggle: (Boolean) -> Unit = {}
) {
    val activeAccentColor = if (useDynamicColor) {
        MaterialTheme.colorScheme.primary
    } else {
        when (pastelAccent) {
            "Emerald" -> Color(0xFF10B981)
            "Lavender" -> Color(0xFFB39DDB)
            "Sky" -> Color(0xFF90CAF9)
            "Peach" -> Color(0xFFFFCC80)
            else -> Color(0xFF10B981)
        }
    }

    val isLightPreview = themeMode.equals("Light", ignoreCase = true)
    val isAmoledPreview = pureBlack && !isLightPreview

    val previewBgColor by animateColorAsState(
        targetValue = when {
            isLightPreview -> Color(0xFFF8FAFC)
            isAmoledPreview -> Color(0xFF000000)
            else -> Color(0xFF121318)
        },
        animationSpec = tween(320),
        label = "preview_bg"
    )
    val previewCardColor by animateColorAsState(
        targetValue = when {
            isLightPreview -> Color(0xFFE2E8F0)
            isAmoledPreview -> Color(0xFF0D0E12)
            else -> Color(0xFF1E2028)
        },
        animationSpec = tween(320),
        label = "preview_card"
    )
    val previewTextColor by animateColorAsState(
        targetValue = if (isLightPreview) Color(0xFF0F172A) else Color(0xFFF8FAFC),
        animationSpec = tween(320),
        label = "preview_text"
    )
    val animatedAccent by animateColorAsState(
        targetValue = activeAccentColor,
        animationSpec = tween(320),
        label = "preview_accent"
    )

    val displayCountryCode = if (contentCountry.isBlank() || contentCountry.equals("AUTO", ignoreCase = true)) {
        detectedCountryCode
    } else {
        contentCountry
    }
    val regionInfo = RegionManager.SUPPORTED_REGIONS.firstOrNull { it.code.equals(displayCountryCode, ignoreCase = true) }
    val flagEmoji = regionInfo?.flagEmoji ?: "🌐"
    val regionName = regionInfo?.name ?: RegionManager.getRegionName(displayCountryCode)

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
            text = "Look & Style",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = "Tap to see live preview",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(14.dp))

        // LIVE INTERACTIVE MINI-PHONE MOCKUP
        PersonalizePhoneMockup(
            previewBgColor = previewBgColor,
            animatedAccent = animatedAccent,
            blurPlayerBackground = blurPlayerBackground,
            isLightPreview = isLightPreview,
            isAmoledPreview = isAmoledPreview,
            previewTextColor = previewTextColor,
            previewCardColor = previewCardColor,
            flagEmoji = flagEmoji,
            regionName = regionName,
            onRegionClick = onRegionClick
        )

        Spacer(Modifier.height(14.dp))

        // SECTION 1: 4 VISUAL SCREEN MODE TILES (Auto, Dark, AMOLED, Light)
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "Screen Theme",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(10.dp))

                val currentVisualMode = when {
                    themeMode.equals("Light", ignoreCase = true) -> "Light"
                    themeMode.equals("Dark", ignoreCase = true) && pureBlack -> "AMOLED"
                    themeMode.equals("Dark", ignoreCase = true) -> "Dark"
                    else -> "Auto"
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    VisualModeOptionTile(
                        label = "Auto",
                        icon = Icons.Default.BrightnessAuto,
                        swatchColor = Color(0xFF272A36),
                        selected = currentVisualMode == "Auto",
                        onClick = {
                            onPureBlackToggle(false)
                            onThemeChange("System")
                        },
                        modifier = Modifier.weight(1f)
                    )
                    VisualModeOptionTile(
                        label = "Dark",
                        icon = Icons.Default.DarkMode,
                        swatchColor = Color(0xFF14161F),
                        selected = currentVisualMode == "Dark",
                        onClick = {
                            onPureBlackToggle(false)
                            onThemeChange("Dark")
                        },
                        modifier = Modifier.weight(1f)
                    )
                    VisualModeOptionTile(
                        label = "AMOLED",
                        icon = Icons.Default.Contrast,
                        swatchColor = Color(0xFF000000),
                        selected = currentVisualMode == "AMOLED",
                        onClick = {
                            onThemeChange("Dark")
                            onPureBlackToggle(true)
                        },
                        modifier = Modifier.weight(1f)
                    )
                    VisualModeOptionTile(
                        label = "Light",
                        icon = Icons.Default.LightMode,
                        swatchColor = Color(0xFFF1F5F9),
                        selected = currentVisualMode == "Light",
                        onClick = {
                            onPureBlackToggle(false)
                            onThemeChange("Light")
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // SECTION 2: COLOR SWATCHES & WALLPAPER DYNAMIC COLOR
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Column {
                            Text(
                                text = "Wallpaper Color",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Match phone theme",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Switch(
                        checked = useDynamicColor,
                        onCheckedChange = onDynamicColorToggle
                    )
                }

                Spacer(Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    listOf(
                        "Emerald" to Color(0xFF10B981),
                        "Lavender" to Color(0xFFB39DDB),
                        "Sky" to Color(0xFF90CAF9),
                        "Peach" to Color(0xFFFFCC80)
                    ).forEach { (name, swatch) ->
                        val selected = !useDynamicColor && pastelAccent.equals(name, ignoreCase = true)
                        val scale by animateFloatAsState(
                            targetValue = if (selected) 1.05f else 1f,
                            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                            label = "swatch_scale"
                        )
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .scale(scale)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (selected) swatch.copy(alpha = 0.22f) else MaterialTheme.colorScheme.surface
                                )
                                .border(
                                    width = if (selected) 2.dp else 1.dp,
                                    color = if (selected) swatch else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .clickable {
                                    if (useDynamicColor) onDynamicColorToggle(false)
                                    onPastelAccentChange(name)
                                }
                                .padding(vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(swatch),
                                contentAlignment = Alignment.Center
                            ) {
                                if (selected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = Color.Black,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = name,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                color = if (selected) swatch else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // SECTION 3: PLAYER BACKDROP (Blurred Glow vs Clean Solid)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            BackdropOptionCard(
                title = "Blurred Art",
                subtitle = "Color Glow",
                selected = blurPlayerBackground,
                accentColor = animatedAccent,
                isBlurred = true,
                onClick = { onBlurPlayerBackgroundToggle(true) },
                modifier = Modifier.weight(1f)
            )
            BackdropOptionCard(
                title = "Clean Solid",
                subtitle = "Minimal Dark",
                selected = !blurPlayerBackground,
                accentColor = animatedAccent,
                isBlurred = false,
                onClick = { onBlurPlayerBackgroundToggle(false) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}
