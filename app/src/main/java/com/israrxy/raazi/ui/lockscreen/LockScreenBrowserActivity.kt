package com.israrxy.raazi.ui.lockscreen

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.os.Build
import android.os.Bundle
import android.util.Log
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
 * Song browser that renders above the keyguard while the device is locked and
 * as a regular full-screen browser otherwise.
 *
 * It is the target of the media notification / MediaSession content intent, the
 * Quick Settings tile and the now-playing widget. Tapping the notification is
 * the supported way in: System UI sends the PendingIntent, which is exempt from
 * background activity launch limits, whereas a service calling `startActivity`
 * is blocked on Android 10+.
 *
 * The activity opts into `showWhenLocked` + `turnScreenOn` so it is usable
 * without an unlock, and closes itself once the user actually unlocks.
 */
class LockScreenBrowserActivity : FragmentActivity() {

    private val userPresentReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val keyguardLocked = isKeyguardLocked()
        Log.i(
            TAG,
            "Lock-screen browser opened (keyguardLocked=$keyguardLocked, sdk=${Build.VERSION.SDK_INT})"
        )
        showOverKeyguard()
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

        setContent {
            val settingsDataStore = remember { SettingsDataStore(applicationContext) }
            val isLockScreenMode = remember { keyguardLocked }
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
                    isLockScreenMode = isLockScreenMode,
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

    private fun isKeyguardLocked(): Boolean =
        (getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager)?.isKeyguardLocked == true

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

    private companion object {
        const val TAG = "LockScreenBrowser"
    }
}
