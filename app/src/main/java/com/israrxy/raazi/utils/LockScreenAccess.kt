package com.israrxy.raazi.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat

/**
 * Which OEM skin the device runs.
 *
 * Stock Android honours `showWhenLocked` without any extra grant. Several
 * Chinese skins require a per-app switch that has no public permission API, so
 * the only thing an app can do is detect the skin and send the user to the
 * right settings screen.
 */
enum class DeviceSkin(val label: String) {
    STOCK("Stock Android"),
    XIAOMI("Xiaomi / Redmi / POCO"),
    OPPO("OPPO / Realme / OnePlus"),
    VIVO("vivo / iQOO"),
    HUAWEI("Huawei / Honor"),
    SAMSUNG("Samsung"),
    OTHER("Android")
}

/**
 * Lock-screen access helpers.
 *
 * Showing an activity above the keyguard needs `android:showWhenLocked="true"`
 * and nothing else - not the overlay permission, not a full-screen intent.
 * OEM skins additionally hide a "show on lock screen" switch in their own
 * settings app; [openOemLockScreenSettings] deep-links to it when possible.
 */
object LockScreenAccess {

    fun detectSkin(): DeviceSkin {
        val name = "${Build.MANUFACTURER} ${Build.BRAND}".lowercase()
        return when {
            "xiaomi" in name || "redmi" in name || "poco" in name -> DeviceSkin.XIAOMI
            "oppo" in name || "realme" in name || "oneplus" in name -> DeviceSkin.OPPO
            "vivo" in name || "iqoo" in name -> DeviceSkin.VIVO
            "huawei" in name || "honor" in name -> DeviceSkin.HUAWEI
            "samsung" in name -> DeviceSkin.SAMSUNG
            "google" in name || "pixel" in name -> DeviceSkin.STOCK
            else -> DeviceSkin.OTHER
        }
    }

    /** True when the app may post the media notification that opens the browser. */
    fun notificationsEnabled(context: Context): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled()

    /** Whether this skin needs a manual per-app switch for lock-screen display. */
    fun needsManualGrant(skin: DeviceSkin): Boolean = when (skin) {
        DeviceSkin.XIAOMI, DeviceSkin.OPPO, DeviceSkin.VIVO, DeviceSkin.HUAWEI -> true
        else -> false
    }

    /** Step-by-step instructions for the detected skin. */
    fun stepsFor(skin: DeviceSkin): List<String> = when (skin) {
        DeviceSkin.XIAOMI -> listOf(
            "Settings → Apps → Manage apps → Raazi",
            "Other permissions → turn on \"Show on lock screen\"",
            "Other permissions → \"Display pop-up windows while running in the background\" → Always allow"
        )
        DeviceSkin.OPPO -> listOf(
            "Settings → Apps → App management → Raazi",
            "Turn on \"Allow lock screen display\"",
            "Allow \"Display pop-up windows while running in the background\""
        )
        DeviceSkin.VIVO -> listOf(
            "Settings → Apps → Raazi → Permissions",
            "Turn on \"Show on lock screen\"",
            "Turn on background pop-up / auto-start for Raazi"
        )
        DeviceSkin.HUAWEI -> listOf(
            "Settings → Apps → Raazi → Battery",
            "App launch → Manage manually",
            "Enable Auto-launch and Secondary launch"
        )
        DeviceSkin.SAMSUNG -> listOf(
            "No extra switch needed - Samsung honours the lock-screen flag",
            "Make sure Settings → Lock screen → Notifications is not set to hide content"
        )
        DeviceSkin.STOCK, DeviceSkin.OTHER -> listOf(
            "No special permission is needed on this device",
            "Tap the artwork on the media notification while locked"
        )
    }

    fun openAppDetails(context: Context): Boolean = open(
        context,
        Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null)
        )
    )

    fun openNotificationSettings(context: Context): Boolean = open(
        context,
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    )

    /**
     * Tries the skin's own permission editor first, then falls back to the
     * standard app-info screen. Returns true if something was opened.
     */
    fun openOemLockScreenSettings(context: Context): Boolean {
        val pkg = context.packageName
        val candidates = when (detectSkin()) {
            DeviceSkin.XIAOMI -> listOf(
                component(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.permissions.PermissionsEditorActivity",
                    "miui.intent.action.APP_PERM_EDITOR",
                    pkg
                ),
                component(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.permissions.AppPermissionsEditorActivity",
                    "miui.intent.action.APP_PERM_EDITOR",
                    pkg
                )
            )
            DeviceSkin.OPPO -> listOf(
                component(
                    "com.coloros.safecenter",
                    "com.coloros.safecenter.permission.startup.StartupAppListActivity",
                    null,
                    pkg
                ),
                component(
                    "com.oplus.safecenter",
                    "com.oplus.safecenter.permission.startup.StartupAppListActivity",
                    null,
                    pkg
                )
            )
            DeviceSkin.VIVO -> listOf(
                component(
                    "com.vivo.permissionmanager",
                    "com.vivo.permissionmanager.activity.BgStartUpManagerActivity",
                    null,
                    pkg
                ),
                component(
                    "com.iqoo.secure",
                    "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity",
                    null,
                    pkg
                )
            )
            DeviceSkin.HUAWEI -> listOf(
                component(
                    "com.huawei.systemmanager",
                    "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
                    null,
                    pkg
                ),
                component(
                    "com.huawei.systemmanager",
                    "com.huawei.systemmanager.optimize.process.ProtectActivity",
                    null,
                    pkg
                )
            )
            else -> emptyList()
        }
        for (intent in candidates) {
            if (open(context, intent)) return true
        }
        return openAppDetails(context)
    }

    private fun component(
        packageName: String,
        className: String,
        action: String?,
        appPackage: String
    ): Intent = Intent().apply {
        setClassName(packageName, className)
        if (action != null) this.action = action
        putExtra("extra_pkgname", appPackage)
        putExtra("pkg_name", appPackage)
        putExtra("package_name", appPackage)
    }

    private fun open(context: Context, intent: Intent): Boolean {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (intent.resolveActivity(context.packageManager) == null) return false
        return runCatching { context.startActivity(intent) }.isSuccess
    }
}
