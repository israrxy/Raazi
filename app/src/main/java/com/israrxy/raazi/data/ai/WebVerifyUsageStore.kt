package com.israrxy.raazi.data.ai

import android.content.Context
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

enum class WebVerifyLevel { NORMAL, NEAR_CAP, AT_CAP }

/**
 * A device-local view of this month's Google Search verification usage.
 *
 * Google meters grounding per API key/project, so this counter can only be a
 * guardrail for Raazi's own requests. It resets on the first of each month.
 */
data class WebVerifyUsage(
    val used: Int,
    val cap: Int,
    val enabled: Boolean,
    val resetLabel: String,
    val level: WebVerifyLevel,
    val showCapExplanation: Boolean
) {
    val remaining: Int get() = (cap - used).coerceAtLeast(0)

    val fraction: Float
        get() = if (cap <= 0) 0f else (used.toFloat() / cap).coerceIn(0f, 1f)

    val percent: Int get() = (fraction * 100f).roundToInt()
}

/**
 * Persists monthly web-search verification usage and the user's cap. This is
 * deliberately separate from the encrypted key store: it contains no secrets.
 */
class WebVerifyUsageStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)

    fun read(): WebVerifyUsage {
        rolloverIfNeeded()
        val cap = prefs.getInt(KEY_CAP, DEFAULT_CAP).coerceAtLeast(MIN_CAP)
        val used = prefs.getInt(KEY_USED, 0).coerceAtLeast(0)
        val enabled = prefs.getBoolean(KEY_ENABLED, true)
        val level = when {
            used >= cap -> WebVerifyLevel.AT_CAP
            used >= (cap * NEAR_CAP_FRACTION).toInt() -> WebVerifyLevel.NEAR_CAP
            else -> WebVerifyLevel.NORMAL
        }
        return WebVerifyUsage(
            used = used,
            cap = cap,
            enabled = enabled,
            resetLabel = resetLabel(),
            level = level,
            showCapExplanation = prefs.getString(KEY_CAP_NOTICE_MONTH, null) != currentMonth()
        )
    }

    /** True when a verification request is allowed under the local cap. */
    fun canVerify(): Boolean {
        val usage = read()
        return usage.enabled && usage.used < usage.cap
    }

    /** Records a successful verification. Failed requests never call this. */
    fun record(queries: Int): WebVerifyUsage = synchronized(this) {
        rolloverIfNeeded()
        val used = prefs.getInt(KEY_USED, 0).coerceAtLeast(0)
        prefs.edit().putInt(KEY_USED, used + queries.coerceAtLeast(1)).apply()
        read()
    }

    fun setEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    fun setCap(cap: Int) {
        prefs.edit().putInt(KEY_CAP, cap.coerceAtLeast(MIN_CAP)).apply()
    }

    fun markCapExplanationShown() {
        prefs.edit().putString(KEY_CAP_NOTICE_MONTH, currentMonth()).apply()
    }

    private fun rolloverIfNeeded() {
        val month = currentMonth()
        if (prefs.getString(KEY_MONTH, null) != month) {
            prefs.edit()
                .putString(KEY_MONTH, month)
                .putInt(KEY_USED, 0)
                .apply()
        }
    }

    private fun currentMonth(): String = LocalDate.now().format(MONTH_FORMAT)

    private fun resetLabel(): String = LocalDate.now()
        .plusMonths(1)
        .withDayOfMonth(1)
        .format(RESET_FORMAT)

    companion object {
        const val DEFAULT_CAP = 900
        const val MIN_CAP = 10

        val CAP_OPTIONS = listOf(100, 300, 900, 1_500, 3_000, 5_000)

        private const val PREFS_FILE = "raazi_web_verify"
        private const val KEY_MONTH = "month"
        private const val KEY_USED = "used"
        private const val KEY_CAP = "cap"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_CAP_NOTICE_MONTH = "cap_notice_month"
        private const val NEAR_CAP_FRACTION = 0.8f

        private val MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM", Locale.US)
        private val RESET_FORMAT = DateTimeFormatter.ofPattern("MMM d", Locale.US)
    }
}
