package com.israrxy.raazi.data.local

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SearchHistoryManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("search_history", Context.MODE_PRIVATE)
    private val _history = MutableStateFlow<List<String>>(loadHistory())
    val history: StateFlow<List<String>> = _history.asStateFlow()

    fun addQuery(query: String) {
        if (query.isBlank()) return
        
        val current = _history.value.toMutableList()
        current.remove(query) // Remove duplicate if exists
        current.add(0, query) // Add to top
        
        if (current.size > 10) { // Limit to 10 items
            current.removeAt(current.lastIndex)
        }
        
        _history.value = current
        saveHistory(current)
    }

    fun clearHistory() {
        _history.value = emptyList()
        prefs.edit().remove(KEY_HISTORY).apply()
    }

    private fun loadHistory(): List<String> {
        val set = prefs.getStringSet(KEY_HISTORY, emptySet()) ?: emptySet()
        // SharedPreferences Set is unordered, but we want order. 
        // For simplicity in this JSON-less implementation, we might lose order on cold start 
        // unless we join to string. Let's use a joined string with delimiter.
        val storedString = prefs.getString(KEY_HISTORY_ORDERED, "") ?: ""
        return if (storedString.isNotEmpty()) {
            storedString.split("|").filter { it.isNotEmpty() }
        } else {
            emptyList()
        }
    }

    private fun saveHistory(list: List<String>) {
        val joined = list.joinToString("|")
        prefs.edit().putString(KEY_HISTORY_ORDERED, joined).apply()
    }

    companion object {
        private const val KEY_HISTORY = "history_set" // Legacy
        private const val KEY_HISTORY_ORDERED = "history_ordered"
    }
}
