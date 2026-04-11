package com.israrxy.raazi.ui

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.israrxy.raazi.RaaziApplication
import com.israrxy.raazi.data.account.YouTubeAccountSession
import com.israrxy.raazi.data.local.SettingsDataStore
import com.zionhuang.innertube.YouTube
import kotlinx.coroutines.delay

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YouTubeLoginScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val settingsDataStore = remember { SettingsDataStore(context) }
    val repository = remember {
        (context.applicationContext as RaaziApplication).container.musicRepository
    }

    var webView by remember { mutableStateOf<WebView?>(null) }
    var visitorData by remember { mutableStateOf<String?>(null) }
    var dataSyncId by remember { mutableStateOf<String?>(null) }
    var innerTubeCookie by remember { mutableStateOf<String?>(null) }
    var statusMessage by remember {
        mutableStateOf("Sign in to YouTube Music to sync likes and playlists into Raazi.")
    }
    var isFinalizing by remember { mutableStateOf(false) }
    var hasCapturedSession by remember { mutableStateOf(false) }

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
        statusMessage = "Finishing sign-in and syncing your account..."

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

            val syncResult = repository.syncYouTubeLibrary()
            statusMessage = buildString {
                append(accountInfo?.name?.let { "Connected as $it." } ?: "Connected to YouTube Music.")
                append(" Synced ${syncResult.likedSongs} liked songs and ${syncResult.playlists} playlists.")
            }

            delay(600)
            onNavigateBack()
        } catch (error: Exception) {
            hasCapturedSession = false
            statusMessage = "Sign-in failed: ${error.message ?: "Unknown error"}"
        } finally {
            isFinalizing = false
        }
    }

    BackHandler(enabled = webView?.canGoBack() == true) {
        webView?.goBack()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "YouTube Music Login",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = statusMessage,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            if (isFinalizing) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                )
            }

            AndroidView(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                factory = { viewContext ->
                    WebView(viewContext).apply {
                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView, url: String?) {
                                loadUrl("javascript:Android.onVisitorData(window.yt?.config_?.VISITOR_DATA)")
                                loadUrl("javascript:Android.onDataSyncId(window.yt?.config_?.DATASYNC_ID)")

                                if (url?.startsWith("https://music.youtube.com") == true) {
                                    innerTubeCookie = CookieManager.getInstance().getCookie(url)
                                    statusMessage = "Detected your YouTube Music session. Finalizing sign-in..."
                                }
                            }
                        }
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.builtInZoomControls = false
                        settings.displayZoomControls = false
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

                        webView = this
                        loadUrl("https://accounts.google.com/ServiceLogin?continue=https%3A%2F%2Fmusic.youtube.com")
                    }
                }
            )
        }
    }
}
