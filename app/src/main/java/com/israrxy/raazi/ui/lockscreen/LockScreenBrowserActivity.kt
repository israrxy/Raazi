package com.israrxy.raazi.ui.lockscreen

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.israrxy.raazi.MainActivity
import com.israrxy.raazi.data.local.SettingsDataStore
import com.israrxy.raazi.ui.theme.RaaziTheme
import com.israrxy.raazi.viewmodel.LockScreenBrowserViewModel

/**
 * Sleek song browser that renders above the keyguard.
 *
 * The activity opts into `showWhenLocked` + `turnScreenOn`, which lets the
 * notification "Browse" action, the Quick Settings tile and the now-playing
 * widget open it without the user unlocking the device. If the user does unlock
 * while it is open, the overlay closes so the real app takes over.
 */
class LockScreenBrowserActivity : FragmentActivity() {

    private val userPresentReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showOverKeyguard()
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

        setContent {
            val settingsDataStore = remember { SettingsDataStore(applicationContext) }
            val dynamicColor by settingsDataStore.useDynamicColor
                .collectAsStateWithLifecycle(initialValue = false)
            val accent by settingsDataStore.pastelAccent
                .collectAsStateWithLifecycle(initialValue = "Emerald")
            val pureBlack by settingsDataStore.pureBlack
                .collectAsStateWithLifecycle(initialValue = false)

            val browserViewModel: LockScreenBrowserViewModel = viewModel(
                factory = LockScreenBrowserViewModel.provideFactory(application)
            )

            RaaziTheme(
                darkTheme = true,
                dynamicColor = dynamicColor,
                pastelAccent = accent,
                pureBlack = pureBlack
            ) {
                LockScreenBrowserScreen(
                    viewModel = browserViewModel,
                    onClose = { finish() },
                    onOpenApp = {
                        startActivity(
                            Intent(this@LockScreenBrowserActivity, MainActivity::class.java)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                        finish()
                    }
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        runCatching {
            registerReceiver(userPresentReceiver, IntentFilter(Intent.ACTION_USER_PRESENT))
        }
    }

    override fun onStop() {
        runCatching { unregisterReceiver(userPresentReceiver) }
        super.onStop()
    }

    private fun showOverKeyguard() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
    }
}
