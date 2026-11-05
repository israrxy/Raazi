package com.israrxy.raazi.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.israrxy.raazi.data.local.SettingsDataStore
import com.israrxy.raazi.data.region.RegionManager
import com.israrxy.raazi.ui.onboarding.OnboardingAudioLyricsPage
import com.israrxy.raazi.ui.onboarding.OnboardingHeroPage
import com.israrxy.raazi.ui.onboarding.OnboardingPersonalizePage
import com.israrxy.raazi.ui.settings.SettingsRegionDialog
import com.israrxy.raazi.viewmodel.MusicPlayerViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(
    viewModel: MusicPlayerViewModel,
    onFinish: () -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { 5 })
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val settingsDataStore = remember { SettingsDataStore(context) }

    // Settings & State flows
    val privacyAccepted by settingsDataStore.privacyPolicyAccepted.collectAsState(initial = false)
    val themeMode by settingsDataStore.themeMode.collectAsState(initial = "System")
    val useDynamicColor by settingsDataStore.useDynamicColor.collectAsState(initial = true)
    val pastelAccent by settingsDataStore.pastelAccent.collectAsState(initial = "Emerald")
    val preferredLyricsType by settingsDataStore.preferredLyricsType.collectAsState(initial = "ROMANIZED")
    val audioQuality by settingsDataStore.audioQuality.collectAsState(initial = "High")
    val microPlayerEnabled by settingsDataStore.microPlayerEnabled.collectAsState(initial = true)
    val contentCountry by settingsDataStore.contentCountry.collectAsState(initial = "AUTO")
    val detectedCountryCode = remember { RegionManager.detectHardwareCountry(context) }

    var showRegionDialog by remember { mutableStateOf(false) }
    var isWebViewVisible by remember { mutableStateOf(false) }

    // Page 3 is Privacy. Disable swiping past page 3 until accepted.
    val isSwipeAllowed = pagerState.currentPage != 4 && (pagerState.currentPage != 3 || privacyAccepted)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Ambient background gradient
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                            MaterialTheme.colorScheme.background,
                            MaterialTheme.colorScheme.background
                        )
                    )
                )
        )

        // Horizontal Pager
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            userScrollEnabled = isSwipeAllowed
        ) { page ->
            when (page) {
                0 -> OnboardingHeroPage()
                1 -> OnboardingPersonalizePage(
                    themeMode = themeMode,
                    useDynamicColor = useDynamicColor,
                    pastelAccent = pastelAccent,
                    contentCountry = contentCountry,
                    detectedCountryCode = detectedCountryCode,
                    onThemeChange = { mode -> scope.launch { settingsDataStore.setThemeMode(mode) } },
                    onDynamicColorToggle = { enabled -> scope.launch { settingsDataStore.setDynamicColor(enabled) } },
                    onPastelAccentChange = { accent -> scope.launch { settingsDataStore.setPastelAccent(accent) } },
                    onRegionClick = { showRegionDialog = true }
                )
                2 -> OnboardingAudioLyricsPage(
                    preferredLyricsType = preferredLyricsType,
                    audioQuality = audioQuality,
                    microPlayerEnabled = microPlayerEnabled,
                    onLyricsTypeChange = { type -> scope.launch { settingsDataStore.setPreferredLyricsType(type) } },
                    onAudioQualityChange = { quality -> scope.launch { settingsDataStore.setAudioQuality(quality) } },
                    onMicroPlayerToggle = { enabled -> scope.launch { settingsDataStore.setMicroPlayerEnabled(enabled) } }
                )
                3 -> OnboardingPrivacyPage(
                    accepted = privacyAccepted,
                    onAcceptToggle = { accepted ->
                        scope.launch { settingsDataStore.setPrivacyPolicyAccepted(accepted) }
                    }
                )
                4 -> OnboardingAccountPage(
                    viewModel = viewModel,
                    settingsDataStore = settingsDataStore,
                    onWebViewVisibilityChange = { isWebViewVisible = it },
                    onFinished = {
                        scope.launch {
                            delay(300)
                            settingsDataStore.setPrivacyPolicyAccepted(true)
                            settingsDataStore.setOnboardingCompleted(true)
                            onFinish()
                        }
                    }
                )
            }
        }

        // Top Skip button
        if (pagerState.currentPage < 4 && !isWebViewVisible) {
            TextButton(
                onClick = {
                    scope.launch {
                        if (privacyAccepted) {
                            settingsDataStore.setOnboardingCompleted(true)
                            onFinish()
                        } else {
                            pagerState.animateScrollToPage(3)
                        }
                    }
                },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(16.dp)
            ) {
                Text(
                    text = "Skip",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // Bottom controller & Page Indicators
        if (!isWebViewVisible) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(24.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Back Button
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (pagerState.currentPage > 0) {
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    pagerState.animateScrollToPage(pagerState.currentPage - 1)
                                }
                            },
                            modifier = Modifier.height(48.dp),
                            shape = RoundedCornerShape(24.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Back", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                // Page Indicator Dots
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(5) { index ->
                        val isSelected = pagerState.currentPage == index
                        val width by animateDpAsState(
                            targetValue = if (isSelected) 22.dp else 7.dp,
                            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                            label = "dot_width"
                        )
                        val color by animateColorAsState(
                            targetValue = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f),
                            label = "dot_color"
                        )

                        Box(
                            modifier = Modifier
                                .height(7.dp)
                                .width(width)
                                .clip(CircleShape)
                                .background(color)
                        )
                    }
                }

                // Next / Finish Button
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    val canProceed = pagerState.currentPage != 3 || privacyAccepted
                    Button(
                        onClick = {
                            if (pagerState.currentPage < 4) {
                                scope.launch {
                                    pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                }
                            } else {
                                scope.launch {
                                    settingsDataStore.setPrivacyPolicyAccepted(true)
                                    settingsDataStore.setOnboardingCompleted(true)
                                    onFinish()
                                }
                            }
                        },
                        enabled = canProceed,
                        modifier = Modifier.height(48.dp),
                        shape = RoundedCornerShape(24.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp)
                    ) {
                        val isLastPage = pagerState.currentPage == 4
                        Text(
                            text = if (isLastPage) "Start" else "Next",
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            imageVector = if (isLastPage) Icons.Default.PlayArrow else Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        // Region Selection Dialog
        if (showRegionDialog) {
            SettingsRegionDialog(
                currentCountryCode = contentCountry,
                detectedCountryCode = detectedCountryCode,
                onDismiss = { showRegionDialog = false },
                onRegionSelected = { selectedCode ->
                    scope.launch {
                        settingsDataStore.setContentCountry(selectedCode)
                        val resolved = if (selectedCode.isBlank() || selectedCode.equals("AUTO", ignoreCase = true)) {
                            RegionManager.detectHardwareCountry(context)
                        } else {
                            selectedCode
                        }
                        RegionManager.applyLocale(
                            resolved,
                            RegionManager.getLanguageCode()
                        )
                    }
                    showRegionDialog = false
                }
            )
        }
    }
}
