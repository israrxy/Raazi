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
    val pagerState = rememberPagerState(pageCount = { 3 })
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val settingsDataStore = remember { SettingsDataStore(context) }
    
    // Theme options from DataStore
    val themeMode by settingsDataStore.themeMode.collectAsState(initial = "System")
    val useDynamicColor by settingsDataStore.useDynamicColor.collectAsState(initial = true)
    val pastelAccent by settingsDataStore.pastelAccent.collectAsState(initial = "Emerald")
    
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
            userScrollEnabled = pagerState.currentPage != 2 // Disable swiping when WebView is active
        ) { page ->
            when (page) {
                0 -> WelcomeIntroPage()
                1 -> CustomizationPage(
                    themeMode = themeMode,
                    useDynamicColor = useDynamicColor,
                    pastelAccent = pastelAccent,
                    onThemeChange = { mode -> scope.launch { settingsDataStore.setThemeMode(mode) } },
                    onDynamicColorToggle = { enabled -> scope.launch { settingsDataStore.setDynamicColor(enabled) } },
                    onPastelAccentChange = { accent -> scope.launch { settingsDataStore.setPastelAccent(accent) } }
                )
                2 -> YouTubeConnectPage(
                    viewModel = viewModel,
                    settingsDataStore = settingsDataStore,
                    onWebViewVisibilityChange = { isWebViewVisible = it },
                    onConnected = {
                        scope.launch {
                            delay(600)
                            onFinish()
                        }
                    }
                )
            }
        }

        // Top Skip button
        if (pagerState.currentPage < 2 && !isWebViewVisible) {
            TextButton(
                onClick = { onFinish() },
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
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(24.dp)
                    .fillMaxWidth()
            ) {
                // Indicators
                Row(
                    modifier = Modifier.align(Alignment.CenterStart),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    repeat(3) { index ->
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
                
                // Navigation Button
                Button(
                    onClick = {
                        if (pagerState.currentPage < 2) {
                            scope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                            }
                        } else {
                            onFinish()
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .height(50.dp),
                    shape = RoundedCornerShape(25.dp)
                ) {
                    Text(
                        text = if (pagerState.currentPage == 2) "Finish Setup" else "Next",
                        fontWeight = FontWeight.Bold
                    )
                    if (pagerState.currentPage < 2) {
                        Spacer(Modifier.width(8.dp))
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, null)
                    }
                }
            }
        }
    }
}

