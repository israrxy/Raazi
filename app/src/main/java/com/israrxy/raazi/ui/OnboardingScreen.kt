package com.israrxy.raazi.ui

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.israrxy.raazi.data.account.YouTubeAccountSession
import com.israrxy.raazi.data.local.SettingsDataStore
import com.israrxy.raazi.ui.theme.ElectricViolet
import com.israrxy.raazi.ui.theme.Emerald500
import com.israrxy.raazi.ui.theme.Zinc900
import com.israrxy.raazi.viewmodel.MusicPlayerViewModel
import com.zionhuang.innertube.YouTube
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
    
    // Privacy policy acceptance
    val privacyAccepted by settingsDataStore.privacyPolicyAccepted.collectAsState(initial = false)

    // Theme options from DataStore
    val themeMode by settingsDataStore.themeMode.collectAsState(initial = "System")
    val useDynamicColor by settingsDataStore.useDynamicColor.collectAsState(initial = true)
    val pastelAccent by settingsDataStore.pastelAccent.collectAsState(initial = "Emerald")
    val preferredLyricsType by settingsDataStore.preferredLyricsType.collectAsState(initial = "ROMANIZED")
    val microPlayerEnabled by settingsDataStore.microPlayerEnabled.collectAsState(initial = true)
    val lyricsAutoScroll by settingsDataStore.lyricsAutoScroll.collectAsState(initial = true)
    val contentCountry by settingsDataStore.contentCountry.collectAsState(initial = "AUTO")
    var showRegionDialog by remember { mutableStateOf(false) }
    val detectedCountryCode = remember { com.israrxy.raazi.data.region.RegionManager.detectHardwareCountry(context) }
    
    // WebView active state
    var isWebViewVisible by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Ambient background brush
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
        
        // Pager containing pages
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            userScrollEnabled = pagerState.currentPage != 4 && (pagerState.currentPage != 1 || privacyAccepted) // Disable swiping past privacy until accepted
        ) { page ->
            when (page) {
                0 -> WelcomeIntroPage()
                1 -> OnboardingPrivacyPage(
                    accepted = privacyAccepted,
                    onAcceptToggle = { accepted ->
                        scope.launch { settingsDataStore.setPrivacyPolicyAccepted(accepted) }
                    }
                )
                2 -> CustomizationPage(
                    themeMode = themeMode,
                    useDynamicColor = useDynamicColor,
                    pastelAccent = pastelAccent,
                    onThemeChange = { mode -> scope.launch { settingsDataStore.setThemeMode(mode) } },
                    onDynamicColorToggle = { enabled -> scope.launch { settingsDataStore.setDynamicColor(enabled) } },
                    onPastelAccentChange = { accent -> scope.launch { settingsDataStore.setPastelAccent(accent) } }
                )
                3 -> OnboardingPreferencesPage(
                    preferredLyricsType = preferredLyricsType,
                    microPlayerEnabled = microPlayerEnabled,
                    lyricsAutoScroll = lyricsAutoScroll,
                    contentCountry = contentCountry,
                    detectedCountryCode = detectedCountryCode,
                    onLyricsTypeChange = { type -> scope.launch { settingsDataStore.setPreferredLyricsType(type) } },
                    onMicroPlayerToggle = { enabled -> scope.launch { settingsDataStore.setMicroPlayerEnabled(enabled) } },
                    onLyricsAutoScrollToggle = { enabled -> scope.launch { settingsDataStore.setLyricsAutoScroll(enabled) } },
                    onRegionClick = { showRegionDialog = true }
                )
                4 -> OnboardingAccountPage(
                    viewModel = viewModel,
                    settingsDataStore = settingsDataStore,
                    onWebViewVisibilityChange = { isWebViewVisible = it },
                    onFinished = {
                        scope.launch {
                            delay(400)
                            settingsDataStore.setPrivacyPolicyAccepted(true)
                            onFinish()
                        }
                    }
                )
            }
        }

        // Top Skip button
        if (pagerState.currentPage < 4 && !isWebViewVisible && (pagerState.currentPage != 1 || privacyAccepted)) {
            TextButton(
                onClick = {
                    scope.launch {
                        if (privacyAccepted) {
                            onFinish()
                        } else {
                            pagerState.animateScrollToPage(1)
                        }
                    }
                },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(16.dp)
            ) {
                Text(
                    text = "Skip All",
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
                            modifier = Modifier.height(50.dp),
                            shape = RoundedCornerShape(25.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                            Spacer(Modifier.width(4.dp))
                            Text("Back", fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    repeat(5) { index ->
                        val isSelected = pagerState.currentPage == index
                        val width by animateDpAsState(if (isSelected) 24.dp else 8.dp, label = "width")
                        val color by animateColorAsState(
                            if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                            label = "color"
                        )

                        Box(
                            modifier = Modifier
                                .height(8.dp)
                                .width(width)
                                .clip(CircleShape)
                                .background(color)
                        )
                    }
                }

                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    val canProceed = pagerState.currentPage != 1 || privacyAccepted
                    Button(
                        onClick = {
                            if (pagerState.currentPage < 4) {
                                scope.launch {
                                    pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                }
                            } else {
                                scope.launch {
                                    settingsDataStore.setPrivacyPolicyAccepted(true)
                                    onFinish()
                                }
                            }
                        },
                        enabled = canProceed,
                        modifier = Modifier.height(50.dp),
                        shape = RoundedCornerShape(25.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp)
                    ) {
                        Text(
                            text = if (pagerState.currentPage == 4) "Finish" else "Next",
                            fontWeight = FontWeight.Bold
                        )
                        if (pagerState.currentPage < 4) {
                            Spacer(Modifier.width(6.dp))
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, null)
                        }
                    }
                }
            }
        }

        if (showRegionDialog) {
            com.israrxy.raazi.ui.settings.SettingsRegionDialog(
                currentCountryCode = contentCountry,
                detectedCountryCode = detectedCountryCode,
                onDismiss = { showRegionDialog = false },
                onRegionSelected = { selectedCode ->
                    scope.launch {
                        settingsDataStore.setContentCountry(selectedCode)
                        val resolved = if (selectedCode.isBlank() || selectedCode.equals("AUTO", ignoreCase = true)) {
                            com.israrxy.raazi.data.region.RegionManager.detectHardwareCountry(context)
                        } else {
                            selectedCode
                        }
                        com.israrxy.raazi.data.region.RegionManager.applyLocale(
                            resolved,
                            com.israrxy.raazi.data.region.RegionManager.getLanguageCode()
                        )
                    }
                    showRegionDialog = false
                }
            )
        }
    }
}

