package com.israrxy.raazi.utils

import android.view.View
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import com.israrxy.raazi.data.local.SettingsDataStore
import kotlinx.coroutines.flow.Flow

@Composable
fun <T> Flow<T>.collectAsStateSafe(initial: T): T =
    collectAsStateWithLifecycle(initialValue = initial).value

/**
 * Compose-friendly wrapper around [HapticFeedback] that respects the user's
 * "Haptic Feedback" preference from [SettingsDataStore].
 */
class HapticController(private val view: View, private val enabled: Boolean) {

    fun lightTap() {
        if (enabled) HapticFeedback.lightTap(view)
    }

    fun mediumImpact() {
        if (enabled) HapticFeedback.mediumImpact(view)
    }

    fun tick() {
        if (enabled) HapticFeedback.tick(view)
    }

    fun success() {
        if (enabled) HapticFeedback.success(view)
    }

    fun error() {
        if (enabled) HapticFeedback.error(view)
    }

    fun longPress() {
        if (enabled) HapticFeedback.longPress(view)
    }
}

@Composable
fun rememberHapticController(): HapticController {
    val view = LocalView.current
    val context = LocalContext.current
    val enabled = SettingsDataStore(context).hapticFeedback.collectAsStateSafe(initial = true)
    return remember(view, enabled) { HapticController(view, enabled) }
}
