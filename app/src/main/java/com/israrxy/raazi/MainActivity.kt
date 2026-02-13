package com.israrxy.raazi

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.israrxy.raazi.ui.MainScreen
import com.israrxy.raazi.ui.theme.RaaziTheme
import com.israrxy.raazi.viewmodel.MusicPlayerViewModel
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.israrxy.raazi.data.local.SettingsDataStore
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    
    // Permission launcher for Android 13+ notifications
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        // Handle permission result - notification functionality will work if granted
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Request permissions
        requestPermissions()
        
        setContent {
            val context = LocalContext.current
            val settingsDataStore = remember { SettingsDataStore(context) }
            val useDynamicColor by settingsDataStore.useDynamicColor.collectAsState(initial = false)
            val themeMode by settingsDataStore.themeMode.collectAsState(initial = "System")
            
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

            val isOnboardingCompleted by settingsDataStore.isOnboardingCompleted.collectAsState(initial = true) // Default to true to prevent flash, wait for real value? No, default false is better for new users.
            // Actually, for existing users we might want to skip. But we can't distinguish "New Install" from "Update" easily without version tracking.
            // Let's assume everyone sees it once.
            
            // To handle the "loading" state better:
            val onboardingState = settingsDataStore.isOnboardingCompleted.collectAsState(initial = null)
            val scope = rememberCoroutineScope()

            RaaziTheme(
                darkTheme = darkTheme,
                dynamicColor = useDynamicColor
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val viewModel: MusicPlayerViewModel = viewModel(
                        factory = MusicPlayerViewModel.provideFactory(this.application)
                    )
                    
                    var showSplash by remember { mutableStateOf(true) }

                    if (showSplash || onboardingState.value == null) {
                        com.israrxy.raazi.ui.SplashScreen {
                            showSplash = false
                        }
                    } else {
                        if (onboardingState.value == false) {
                            com.israrxy.raazi.ui.OnboardingScreen {
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
            requestPermissionLauncher.launch(permissionsToRequest.first()) // Simple request for now. Ideally requestMultiplePermissions
        }
    }
}

@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
    RaaziTheme {
        // Preview content would go here
    }
}