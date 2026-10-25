package com.israrxy.raazi.ui

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.israrxy.raazi.data.account.YouTubeAccountSession
import com.israrxy.raazi.data.local.SettingsDataStore
import com.israrxy.raazi.ui.theme.Emerald500
import com.israrxy.raazi.viewmodel.MusicPlayerViewModel
import com.zionhuang.innertube.YouTube

@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun YouTubeConnectPage(
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
                    onClick = onConnected,
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
internal fun GeminiSetupPage(
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
