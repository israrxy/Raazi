package com.israrxy.raazi.viewmodel

import android.app.Application
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.Locale

data class VoiceSearchUiState(
    val isListening: Boolean = false,
    val partialText: String = "",
    val finalText: String? = null,
    val error: String? = null,
    val rms: Float = 0f
)

/**
 * Owns Android's on-device speech recognizer so the Search screen only renders
 * the voice popup. Results are delivered through [VoiceSearchUiState.finalText].
 */
class VoiceSearchViewModel(app: Application) : AndroidViewModel(app) {

    private var recognizer: SpeechRecognizer? = null

    private val _state = MutableStateFlow(VoiceSearchUiState())
    val state: StateFlow<VoiceSearchUiState> = _state.asStateFlow()

    fun startListening() {
        val context = getApplication<Application>()
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            _state.value = VoiceSearchUiState(
                error = "Speech recognition isn't available on this device."
            )
            return
        }

        destroyRecognizer()
        val created = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer = created
        created.setRecognitionListener(listener)
        _state.value = VoiceSearchUiState(isListening = true)

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
        }
        runCatching { created.startListening(intent) }
            .onFailure {
                _state.value = VoiceSearchUiState(
                    error = it.message ?: "Could not start voice search."
                )
            }
    }

    /** Stops the current recognizer without keeping a result. */
    fun cancel() {
        runCatching { recognizer?.cancel() }
        destroyRecognizer()
        _state.value = VoiceSearchUiState()
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    /** Shows the permission message when the user denies the mic request. */
    fun showPermissionError() {
        _state.value = VoiceSearchUiState(
            error = "Microphone permission is needed for voice search."
        )
    }

    fun clearResult() {
        _state.update { it.copy(finalText = null, partialText = "") }
    }

    private fun destroyRecognizer() {
        runCatching { recognizer?.destroy() }
        recognizer = null
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            _state.update { it.copy(isListening = true, error = null) }
        }

        override fun onBeginningOfSpeech() = Unit

        override fun onRmsChanged(rmsdB: Float) {
            val level = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
            _state.update { it.copy(rms = level) }
        }

        override fun onBufferReceived(buffer: ByteArray?) = Unit

        override fun onEndOfSpeech() {
            _state.update { it.copy(isListening = false) }
        }

        override fun onError(error: Int) {
            _state.value = _state.value.copy(
                isListening = false,
                error = errorMessage(error)
            )
        }

        override fun onResults(results: Bundle?) {
            val text = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                .orEmpty()
                .trim()
            _state.value = _state.value.copy(
                isListening = false,
                finalText = text.takeIf { it.isNotBlank() },
                error = if (text.isBlank()) "I didn't catch that. Try again." else null
            )
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val text = partialResults
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                .orEmpty()
            if (text.isNotBlank()) {
                _state.update { it.copy(partialText = text) }
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }

    private fun errorMessage(code: Int): String = when (code) {
        SpeechRecognizer.ERROR_AUDIO -> "Audio recording error. Try again."
        SpeechRecognizer.ERROR_CLIENT -> "Voice search stopped. Try again."
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
            "Microphone permission is needed for voice search."
        SpeechRecognizer.ERROR_NETWORK,
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Voice search needs a network connection."
        SpeechRecognizer.ERROR_NO_MATCH -> "I didn't catch that. Try again."
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Voice search is busy. Try again."
        SpeechRecognizer.ERROR_SERVER -> "Voice search server error. Try again."
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "I didn't hear anything. Try again."
        else -> "Voice search failed. Try again."
    }

    override fun onCleared() {
        cancel()
        super.onCleared()
    }
}
