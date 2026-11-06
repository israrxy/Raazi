package com.israrxy.raazi.ui.settings

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.israrxy.raazi.ui.SettingsActionItem
import com.israrxy.raazi.ui.SettingsDivider
import com.israrxy.raazi.ui.SettingsSection
import com.israrxy.raazi.ui.lockscreen.LockScreenBrowserActivity
import com.israrxy.raazi.utils.LockScreenAccess

/**
 * Settings page that explains and configures lock-screen access.
 *
 * No runtime permission is required to draw above the keyguard on stock
 * Android, so this page is mostly about (a) making sure notifications exist and
 * (b) pointing OEM users at the hidden "show on lock screen" switch.
 */
@Composable
internal fun SettingsLockScreenSection(context: Context) {
    val skin = remember { LockScreenAccess.detectSkin() }
    var notificationsEnabled by remember {
        mutableStateOf(LockScreenAccess.notificationsEnabled(context))
    }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                notificationsEnabled = LockScreenAccess.notificationsEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LockScreenExplainer()

    SettingsSection(title = "Status") {
        SettingsActionItem(
            icon = Icons.Filled.NotificationsActive,
            title = "Media notification",
            subtitle = if (notificationsEnabled) {
                "Enabled - required to reach the browser from the lock screen"
            } else {
                "Blocked - turn it on or the lock-screen browser cannot be opened"
            },
            iconTint = if (notificationsEnabled) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.error,
            onClick = {
                if (!LockScreenAccess.openNotificationSettings(context)) {
                    LockScreenAccess.openAppDetails(context)
                }
            }
        )
        SettingsDivider()
        SettingsActionItem(
            icon = Icons.Filled.PhoneAndroid,
            title = "Lock-screen display",
            subtitle = if (LockScreenAccess.needsManualGrant(skin)) {
                "${skin.label} needs a manual switch"
            } else {
                "Ready - no extra permission needed"
            },
            onClick = { LockScreenAccess.openOemLockScreenSettings(context) }
        )
    }

    SettingsSection(title = "${skin.label} setup") {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            LockScreenAccess.stepsFor(skin).forEachIndexed { index, step ->
                StepRow(number = index + 1, text = step)
            }
        }
    }

    SettingsSection(title = "Try it") {
        SettingsActionItem(
            icon = Icons.Filled.PlayArrow,
            title = "Open the browser now",
            subtitle = "Then lock the phone and tap the notification artwork",
            onClick = {
                val intent = Intent(context, LockScreenBrowserActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { context.startActivity(intent) }
                    .onFailure {
                        Toast.makeText(
                            context,
                            "Could not open the browser",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
            }
        )
    }
}

@Composable
private fun LockScreenExplainer() {
    SettingsSection(title = "How it works") {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .background(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Raazi draws the browser above the lock screen with " +
                        "Android's showWhenLocked flag.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "It does not use the \"Display over other apps\" permission, " +
                        "and no other permission is needed. Only the media " +
                        "notification must be allowed.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "If the lock screen is set to hide notification content, " +
                        "Android asks for your PIN before opening anything. Set " +
                        "lock-screen notifications to show content to avoid that.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun StepRow(number: Int, text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .background(
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = number.toString(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
