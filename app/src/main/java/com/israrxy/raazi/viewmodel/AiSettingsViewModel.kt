package com.israrxy.raazi.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.israrxy.raazi.data.ai.GeminiClient
import com.israrxy.raazi.data.ai.GeminiException
import com.israrxy.raazi.data.ai.GeminiKeyStore
import com.israrxy.raazi.data.ai.WebVerifyLevel
import com.israrxy.raazi.data.ai.WebVerifyUsage
import com.israrxy.raazi.data.ai.WebVerifyUsageStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AiSettingsUiState(
    val hasKey: Boolean = false,
    val model: String = GeminiKeyStore.DEFAULT_MODEL,
    val isBusy: Boolean = false,
    val message: String? = null,
    val error: String? = null,
    val usage: WebVerifyUsage = WebVerifyUsage(
        used = 0,
        cap = WebVerifyUsageStore.DEFAULT_CAP,
        enabled = true,
        resetLabel = "",
        level = WebVerifyLevel.NORMAL,
        showCapExplanation = false
    )
)

/**
 * Owns saving, testing, and removing the user's Gemini key. The Compose
 * settings section only renders this state.
 */
class AiSettingsViewModel(app: Application) : AndroidViewModel(app) {

    private val keyStore = GeminiKeyStore(app)
    private val usageStore = WebVerifyUsageStore(app)
    private val client = GeminiClient()

    private val _state = MutableStateFlow(
        AiSettingsUiState(
            hasKey = keyStore.isConfigured(),
            model = keyStore.readModel(),
            usage = usageStore.read()
        )
    )
    val state: StateFlow<AiSettingsUiState> = _state.asStateFlow()

    fun refresh() {
        _state.update {
            it.copy(
                hasKey = keyStore.isConfigured(),
                model = keyStore.readModel(),
                usage = usageStore.read()
            )
        }
    }

    fun setWebVerifyEnabled(enabled: Boolean) {
        usageStore.setEnabled(enabled)
        _state.update { it.copy(usage = usageStore.read()) }
    }

    fun setWebCheckCap(cap: Int) {
        usageStore.setCap(cap)
        _state.update { it.copy(usage = usageStore.read()) }
    }

    fun clearStatus() {
        _state.update { it.copy(message = null, error = null) }
    }

    fun saveAndTest(rawKey: String) {
        if (_state.value.isBusy) return
        val key = rawKey.trim()
        if (key.isBlank()) {
            _state.update { it.copy(error = "Paste your Gemini API key first.") }
            return
        }
        verifyKey(key, saveKey = true)
    }

    fun testSavedKey() {
        if (_state.value.isBusy) return
        val key = keyStore.readKey()
        if (key.isNullOrBlank()) {
            _state.update { it.copy(error = "Add a Gemini API key first.", hasKey = false) }
            return
        }
        verifyKey(key, saveKey = false)
    }

    private fun verifyKey(key: String, saveKey: Boolean) {
        viewModelScope.launch {
            _state.update { it.copy(isBusy = true, message = null, error = null) }
            try {
                if (saveKey) keyStore.saveKey(key)
                val requested = keyStore.readModel()
                val available = client.listModels(key)
                var model = GeminiClient.chooseModel(available, requested)

                try {
                    client.testKey(key, model)
                } catch (e: GeminiException) {
                    if (e.statusCode == 404) {
                        val fallback = GeminiClient.chooseModel(available, "gemini-2.5-flash")
                        if (fallback == model) throw e
                        model = fallback
                        client.testKey(key, model)
                    } else {
                        throw e
                    }
                }

                keyStore.saveModel(model)
                val note = if (model != requested) {
                    "Connected. $requested isn't available on this key, so Raazi is using $model."
                } else {
                    "Connected · using $model."
                }
                _state.update {
                    it.copy(
                        isBusy = false,
                        hasKey = true,
                        model = model,
                        message = note,
                        error = null
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isBusy = false,
                        hasKey = keyStore.isConfigured(),
                        model = keyStore.readModel(),
                        error = e.message ?: "Could not verify that key. Try again."
                    )
                }
            }
        }
    }

    fun setModel(model: String) {
        val normalized = model.trim()
        if (normalized.isBlank()) return
        keyStore.saveModel(normalized)
        _state.update { it.copy(model = normalized, message = null, error = null) }
    }

    fun removeKey() {
        keyStore.clearKey()
        _state.update {
            it.copy(
                hasKey = false,
                isBusy = false,
                message = "Gemini key removed from this device.",
                error = null
            )
        }
    }
}
