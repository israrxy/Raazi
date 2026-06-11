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
    val pagerState = rememberPagerState(pageCount = { 4 })
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val settingsDataStore = remember { SettingsDataStore(context) }
    
    // Theme options from DataStore
    val themeMode by settingsDataStore.themeMode.collectAsState(initial = "System")
    val useDynamicColor by settingsDataStore.useDynamicColor.collectAsState(initial = true)
    val pastelAccent by settingsDataStore.pastelAccent.collectAsState(initial = "Emerald")
    
    // WebView active state
    var isWebViewVisible by remember { mutableStateOf(false) }
    
    // Gemini configurations
    val useGeminiImport by settingsDataStore.useGeminiImport.collectAsState(initial = false)
    val geminiApiKey by settingsDataStore.geminiApiKey.collectAsState(initial = "")
    var apiKeyText by remember { mutableStateOf("") }
    
    LaunchedEffect(geminiApiKey) {
        if (geminiApiKey != null && apiKeyText.isEmpty()) {
            apiKeyText = geminiApiKey!!
        }
    }

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
                            delay(1000)
                            pagerState.animateScrollToPage(3)
                        }
                    }
                )
                3 -> GeminiSetupPage(
                    useGemini = useGeminiImport,
                    apiKey = apiKeyText,
                    onUseGeminiToggle = { enabled -> scope.launch { settingsDataStore.setUseGeminiImport(enabled) } },
                    onApiKeyChange = {
                        apiKeyText = it
                        scope.launch { settingsDataStore.setGeminiApiKey(it) }
                    }
                )
            }
        }

        // Top Skip button
        if (pagerState.currentPage < 3 && !isWebViewVisible) {
            TextButton(
                onClick = {
                    if (pagerState.currentPage == 2) {
                        // Skip connection
                        scope.launch { pagerState.animateScrollToPage(3) }
                    } else {
                        onFinish()
                    }
                },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(16.dp)
            ) {
                Text(
                    text = if (pagerState.currentPage == 2) "Skip" else "Skip All",
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
                    repeat(4) { index ->
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
                        if (pagerState.currentPage < 3) {
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
                        text = if (pagerState.currentPage == 3) "Finish Setup" else "Next",
                        fontWeight = FontWeight.Bold
                    )
                    if (pagerState.currentPage < 3) {
                        Spacer(Modifier.width(8.dp))
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, null)
                    }
                }
            }
        }
    }
}

@Composable
private fun WelcomeIntroPage() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(160.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primaryContainer,
                            MaterialTheme.colorScheme.surface
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.MusicNote,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
        
        Spacer(Modifier.height(40.dp))
        
        Text(
            text = "Welcome to Raazi",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )
        
        Spacer(Modifier.height(16.dp))
        
        Text(
            text = "Experience seamless local music playback, custom Equalizer support, ringtone creation, and YouTube Music integration in one modern client.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            lineHeight = 24.sp
        )
        
        Spacer(Modifier.height(60.dp))
    }
}

@Composable
private fun CustomizationPage(
    themeMode: String,
    useDynamicColor: Boolean,
    pastelAccent: String,
    onThemeChange: (String) -> Unit,
    onDynamicColorToggle: (Boolean) -> Unit,
    onPastelAccentChange: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Palette,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        
        Spacer(Modifier.height(28.dp))
        
        Text(
            text = "Make it Yours",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        
        Spacer(Modifier.height(24.dp))
        
        // Theme selection card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "App Theme",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val modes = listOf("Light", "Dark", "System")
                    modes.forEach { mode ->
                        val selected = themeMode == mode
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                                )
                                .clickable { onThemeChange(mode) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = mode,
                                fontWeight = FontWeight.Bold,
                                color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
        
        Spacer(Modifier.height(16.dp))
        
        // Material You / Dynamic colors card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Material You Theme",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Extract dynamic colors matching system wallpaper",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = useDynamicColor,
                    onCheckedChange = onDynamicColorToggle
                )
            }
        }
        
        // Accent Color card
        if (!useDynamicColor) {
            Spacer(Modifier.height(16.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Accent Color",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val accents = listOf("Emerald", "Lavender", "Sky", "Peach")
                        accents.forEach { accent ->
                            val selected = pastelAccent == accent
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
                                    .height(44.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                                    )
                                    .border(
                                        width = 1.5.dp,
                                        color = accentColor,
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .clickable { onPastelAccentChange(accent) },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = accent,
                                    fontWeight = FontWeight.Bold,
                                    color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }
        }
        
        Spacer(Modifier.height(60.dp))
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun YouTubeConnectPage(
    viewModel: MusicPlayerViewModel,
    settingsDataStore: SettingsDataStore,
    onWebViewVisibilityChange: (Boolean) -> Unit,
    onConnected: () -> Unit
) {
    val isYouTubeLoggedIn by viewModel.isYouTubeLoggedIn.collectAsState()
    val youTubeAccountName by viewModel.youTubeAccountName.collectAsState()
    
    var showWebView by remember { mutableStateOf(false) }
    var isWebViewLoading by remember { mutableStateOf(true) }
    var visitorData by remember { mutableStateOf<String?>(null) }
    var dataSyncId by remember { mutableStateOf<String?>(null) }
    var innerTubeCookie by remember { mutableStateOf<String?>(null) }
    var statusMessage by remember { mutableStateOf("Capturing sign-in session details...") }
    var isFinalizing by remember { mutableStateOf(false) }
    var hasCapturedSession by remember { mutableStateOf(false) }
    
    LaunchedEffect(showWebView) {
        onWebViewVisibilityChange(showWebView)
    }

    // Cookie capture hook
    LaunchedEffect(innerTubeCookie, visitorData, dataSyncId, hasCapturedSession) {
        val cookie = innerTubeCookie
        val sessionVisitorData = visitorData
        if (hasCapturedSession || cookie.isNullOrBlank() || sessionVisitorData.isNullOrBlank()) {
            return@LaunchedEffect
        }
        if (!cookie.contains("SAPISID")) {
            return@LaunchedEffect
        }

        hasCapturedSession = true
        isFinalizing = true
        statusMessage = "Authenticating with YouTube Music..."

        try {
            YouTube.cookie = cookie
            YouTube.visitorData = sessionVisitorData
            YouTube.dataSyncId = YouTubeAccountSession.normalizeDataSyncId(dataSyncId)

            val accountInfo = YouTube.accountInfo().getOrNull()
            YouTubeAccountSession.persistSession(
                settingsDataStore = settingsDataStore,
                cookie = cookie,
                visitorData = sessionVisitorData,
                dataSyncId = dataSyncId,
                accountInfo = accountInfo
            )

            viewModel.syncYouTubeLibrary()
            onConnected()
        } catch (error: Exception) {
            hasCapturedSession = false
            statusMessage = "Authentication failed: ${error.message}"
        } finally {
            isFinalizing = false
        }
    }

    if (showWebView) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { showWebView = false }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                }
                Spacer(Modifier.width(8.dp))
                Text("Sign in with Google", fontWeight = FontWeight.Bold)
            }
            
            if (isWebViewLoading || isFinalizing) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                if (isFinalizing) {
                    Text(
                        text = statusMessage,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp)
                    )
                }
            }
            
            AndroidView(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                factory = { viewContext ->
                    WebView(viewContext).apply {
                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                                super.onPageStarted(view, url, favicon)
                                isWebViewLoading = true
                            }

                            override fun onPageFinished(view: WebView, url: String?) {
                                isWebViewLoading = false
                                loadUrl("javascript:Android.onVisitorData(window.yt?.config_?.VISITOR_DATA)")
                                loadUrl("javascript:Android.onDataSyncId(window.yt?.config_?.DATASYNC_ID)")

                                if (url?.startsWith("https://music.youtube.com") == true) {
                                    innerTubeCookie = CookieManager.getInstance().getCookie(url)
                                }
                            }
                        }
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        addJavascriptInterface(object {
                            @JavascriptInterface
                            fun onVisitorData(value: String?) {
                                visitorData = value?.takeIf { it.isNotBlank() && it != "null" }
                            }

                            @JavascriptInterface
                            fun onDataSyncId(value: String?) {
                                dataSyncId = value?.takeIf { it.isNotBlank() && it != "null" }
                            }
                        }, "Android")
                        loadUrl("https://accounts.google.com/ServiceLogin?continue=https%3A%2F%2Fmusic.youtube.com")
                    }
                }
            )
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.CloudSync,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            
            Spacer(Modifier.height(28.dp))
            
            Text(
                text = "Connect YouTube Music",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            
            Spacer(Modifier.height(16.dp))
            
            Text(
                text = "Sync your liked songs, custom playlists, and listening recommendations seamlessly between Raazi and your YouTube account.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp
            )
            
            Spacer(Modifier.height(32.dp))
            
            if (isYouTubeLoggedIn) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.CheckCircle, null, tint = Emerald500)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Connected as ${youTubeAccountName ?: "Account"}",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
                
                Spacer(Modifier.height(12.dp))
                
                Button(
                    onClick = onConnected,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Continue", fontWeight = FontWeight.Bold)
                }
            } else {
                Button(
                    onClick = { showWebView = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.Login, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Sign In with Google Account", fontWeight = FontWeight.Bold)
                }
                
                Spacer(Modifier.height(12.dp))
                
                OutlinedButton(
                    onClick = onConnected, // Proceed locally
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Use Locally / Skip", fontWeight = FontWeight.Bold)
                }
            }
            
            Spacer(Modifier.height(60.dp))
        }
    }
}

@Composable
private fun GeminiSetupPage(
    useGemini: Boolean,
    apiKey: String,
    onUseGeminiToggle: (Boolean) -> Unit,
    onApiKeyChange: (String) -> Unit
) {
    var keyText by remember { mutableStateOf(apiKey) }
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp)
            .verticalScroll(scrollState),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.AutoAwesome,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        
        Spacer(Modifier.height(24.dp))
        
        Text(
            text = "AI-Optimized Imports",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        
        Spacer(Modifier.height(12.dp))
        
        Text(
            text = "Enable Gemini 1.5 Flash to automatically clean track titles and suggest smart matches during Spotify playlist imports.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            lineHeight = 22.sp
        )
        
        Spacer(Modifier.height(24.dp))
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Optimize Imports",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Analyze and resolve track lists with Gemini",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = useGemini,
                        onCheckedChange = onUseGeminiToggle
                    )
                }
                
                if (useGemini) {
                    HorizontalDivider()
                    
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Gemini API Key",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        OutlinedTextField(
                            value = keyText,
                            onValueChange = {
                                keyText = it
                                onApiKeyChange(it)
                            },
                            placeholder = { Text("AIzaSy...") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )
                        Text(
                            text = "Get a free API Key from Google AI Studio (ai.google.dev). Keys are stored locally.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        
        Spacer(Modifier.height(80.dp))
    }
}
