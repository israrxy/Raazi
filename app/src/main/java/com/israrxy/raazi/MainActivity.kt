package com.israrxy.raazi

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.israrxy.raazi.service.MusicPlaybackService
import com.israrxy.raazi.ui.MainScreen
import com.israrxy.raazi.ui.theme.RaaziTheme
import com.israrxy.raazi.viewmodel.MusicPlayerViewModel
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.israrxy.raazi.data.local.SettingsDataStore
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private var feedbackReceiver: BroadcastReceiver? = null

    // Permission launcher for Android 13+ notifications
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // Handle permission results
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        enableEdgeToEdge()

        // Request permissions
        requestPermissions()

        bootstrapPoToken()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            feedbackReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    val message = intent.getStringExtra(MusicPlaybackService.EXTRA_FEEDBACK_MESSAGE)
                    if (!message.isNullOrBlank()) {
                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                    }
                }
            }
            val filter = IntentFilter(MusicPlaybackService.NOTIFICATION_FEEDBACK_CHANNEL)
            registerReceiver(feedbackReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            feedbackReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    val message = intent.getStringExtra(MusicPlaybackService.EXTRA_FEEDBACK_MESSAGE)
                    if (!message.isNullOrBlank()) {
                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                    }
                }
            }
            val filter = IntentFilter(MusicPlaybackService.NOTIFICATION_FEEDBACK_CHANNEL)
            registerReceiver(feedbackReceiver, filter)
        }

        setContent {
            val context = LocalContext.current
            val settingsDataStore = remember { SettingsDataStore(context) }
            val useDynamicColor by settingsDataStore.useDynamicColor.collectAsStateWithLifecycle(initialValue = true)
            val themeMode by settingsDataStore.themeMode.collectAsStateWithLifecycle(initialValue = "System")
            val pastelAccent by settingsDataStore.pastelAccent.collectAsStateWithLifecycle(initialValue = "Emerald")
            
            // Update Check Logic
            val updateManager = remember { com.israrxy.raazi.data.UpdateManager() }
            var updateConfig by remember { mutableStateOf<com.israrxy.raazi.model.UpdateConfig?>(null) }
            var showUpdateDialog by remember { mutableStateOf(false) }

            LaunchedEffect(Unit) {
                val config = updateManager.checkUpdate()
                if (config != null && config.latestVersionCode > BuildConfig.VERSION_CODE) {
                    updateConfig = config
                    showUpdateDialog = true
                }
            }
            
            val darkTheme = when (themeMode) {
                "Light" -> false
                "Dark" -> true
                else -> androidx.compose.foundation.isSystemInDarkTheme()
            }
            
            if (showUpdateDialog && updateConfig != null) {
                com.israrxy.raazi.ui.components.UpdateDialog(
                    config = updateConfig!!,
                    onDismiss = { showUpdateDialog = false }
                )
            }

            val onboardingCompleted by settingsDataStore.isOnboardingCompleted.collectAsStateWithLifecycle(initialValue = null)
            val scope = rememberCoroutineScope()

            RaaziTheme(
                darkTheme = darkTheme,
                dynamicColor = useDynamicColor,
                pastelAccent = pastelAccent
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val viewModel: MusicPlayerViewModel = viewModel(
                        factory = MusicPlayerViewModel.provideFactory(this.application)
                    )
                    
                    var showSplash by remember { mutableStateOf(true) }

                    if (showSplash || onboardingCompleted == null) {
                        com.israrxy.raazi.ui.SplashScreen {
                            showSplash = false
                        }
                    } else {
                        if (onboardingCompleted == false) {
                            com.israrxy.raazi.ui.OnboardingScreen(viewModel = viewModel) {
                                scope.launch {
                                    settingsDataStore.setOnboardingCompleted(true)
                                }
                            }
                        } else {
                            MainScreen(viewModel = viewModel)
                        }
                    }
                }
            }
        }
    }
    
    private fun bootstrapPoToken() {
        lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val sessionId = com.zionhuang.innertube.YouTube.dataSyncId
                    ?: com.zionhuang.innertube.YouTube.visitorData
                    ?: "anonymous"
                val pot = com.israrxy.raazi.player.potoken.PoTokenGenerator.getInstance()
                    .getWebClientPoToken("bootstrap", sessionId)
                if (pot != null) {
                    com.zionhuang.innertube.YouTube.webPoToken = pot.streamingDataPoToken
                    android.util.Log.d("MainActivity", "PoToken bootstrap ok (${pot.streamingDataPoToken.length} chars)")
                }
            } catch (e: Exception) {
                android.util.Log.w("MainActivity", "PoToken bootstrap failed", e)
            }
        }
    }

    private fun requestPermissions() {
        val permissionsToRequest = mutableListOf<String>()

        // Notifications (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        // Storage
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.READ_MEDIA_AUDIO)
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED && Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                 permissionsToRequest.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }

        if (permissionsToRequest.isNotEmpty()) {
            requestPermissionLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }

    override fun onDestroy() {
        try {
            feedbackReceiver?.let { unregisterReceiver(it) }
        } catch (_: Exception) {}
        feedbackReceiver = null
        super.onDestroy()
    }
}
