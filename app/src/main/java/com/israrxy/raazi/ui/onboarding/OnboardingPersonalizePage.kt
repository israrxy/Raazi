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
        Surface(
            shape = RoundedCornerShape(26.dp),
            color = previewBgColor,
            border = BorderStroke(2.5.dp, animatedAccent.copy(alpha = 0.55f)),
            tonalElevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .height(168.dp)
                .testTag("onboarding_theme_phone_preview")
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Simulated Blurred Artwork Glow
                if (blurPlayerBackground) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        animatedAccent.copy(alpha = if (isLightPreview) 0.28f else 0.38f),
                                        animatedAccent.copy(alpha = 0.08f),
                                        Color.Transparent
                                    )
                                )
                            )
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // Mock Status + Region Pill
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(animatedAccent)
                            )
                            Text(
                                text = when {
                                    isAmoledPreview -> "AMOLED Black"
                                    isLightPreview -> "Light Look"
                                    else -> "Dark Look"
                                },
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = previewTextColor
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(50),
                            color = previewCardColor,
                            modifier = Modifier.clickable { onRegionClick() }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(text = flagEmoji, style = MaterialTheme.typography.labelSmall)
                                Text(
                                    text = regionName,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = previewTextColor
                                )
                            }
                        }
                    }

                    // Mock Quick Pick Cards Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        repeat(3) { idx ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = previewCardColor.copy(alpha = if (blurPlayerBackground) 0.82f else 1f),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(26.dp)
                                            .clip(RoundedCornerShape(7.dp))
                                            .background(animatedAccent.copy(alpha = 0.25f + idx * 0.15f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.MusicNote,
                                            contentDescription = null,
                                            tint = animatedAccent,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(8.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(previewTextColor.copy(alpha = 0.45f))
                                    )
                                }
                            }
                        }
                    }

                    // Mock Mini-Player Bar inside Phone
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = previewCardColor,
                        border = BorderStroke(1.dp, animatedAccent.copy(alpha = 0.35f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(animatedAccent),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.MusicNote,
                                        contentDescription = null,
                                        tint = Color.Black,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Column {
                                    Box(
                                        modifier = Modifier
                                            .width(72.dp)
                                            .height(8.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(previewTextColor.copy(alpha = 0.85f))
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Box(
                                        modifier = Modifier
                                            .width(44.dp)
                                            .height(6.dp)
                                            .clip(RoundedCornerShape(3.dp))
                                            .background(animatedAccent.copy(alpha = 0.75f))
                                    )
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(animatedAccent),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint = Color.Black,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

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

@Composable
private fun VisualModeOptionTile(
    label: String,
    icon: ImageVector,
    swatchColor: Color,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.04f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "mode_scale"
    )
    Column(
        modifier = modifier
            .scale(scale)
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
                .size(28.dp)
                .clip(CircleShape)
                .background(swatchColor)
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (label == "Light") Color(0xFF0F172A) else Color.White,
                modifier = Modifier.size(15.dp)
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun BackdropOptionCard(
    title: String,
    subtitle: String,
    selected: Boolean,
    accentColor: Color,
    isBlurred: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
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
                        if (isBlurred) {
                            Brush.linearGradient(listOf(accentColor, Color(0xFF1E293B)))
                        } else {
                            Brush.linearGradient(listOf(Color(0xFF0F172A), Color(0xFF0F172A)))
                        }
                    )
                    .border(1.dp, accentColor.copy(alpha = 0.4f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
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
