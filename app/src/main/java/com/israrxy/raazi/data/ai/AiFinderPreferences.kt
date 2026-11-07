package com.israrxy.raazi.data.ai

import android.content.Context

/**
 * Small, non-secret preferences for the in-Search song finder. Auto search is
 * on by default so a confident match opens without an extra tap.
 */
class AiFinderPreferences(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)

    fun isAutoSearchEnabled(): Boolean = prefs.getBoolean(KEY_AUTO_SEARCH, true)

    fun setAutoSearchEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_SEARCH, enabled).apply()
    }

    companion object {
        private const val PREFS_FILE = "raazi_ai_finder"
        private const val KEY_AUTO_SEARCH = "auto_search"
    }
}
