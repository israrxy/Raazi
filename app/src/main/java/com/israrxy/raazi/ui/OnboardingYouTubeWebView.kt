package com.israrxy.raazi.ui

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun OnboardingYouTubeWebView(
    onDismiss: () -> Unit,
    onSessionCaptured: (cookie: String, visitorData: String, dataSyncId: String?) -> Unit
) {
    var isWebViewLoading by remember { mutableStateOf(true) }
    var visitorData by remember { mutableStateOf<String?>(null) }
    var dataSyncId by remember { mutableStateOf<String?>(null) }
    var innerTubeCookie by remember { mutableStateOf<String?>(null) }
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
        onSessionCaptured(cookie, sessionVisitorData, dataSyncId)
    }

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
            IconButton(onClick = onDismiss) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Sign in to YouTube Music",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium
            )
        }

        if (isWebViewLoading || hasCapturedSession) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            if (hasCapturedSession) {
                Text(
                    text = "Authenticating YouTube Music session...",
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
}
