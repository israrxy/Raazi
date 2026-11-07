package com.israrxy.raazi.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.israrxy.raazi.data.ai.AudioPayload
import com.israrxy.raazi.data.ai.GeminiClient
import com.israrxy.raazi.data.ai.GeminiException
import com.israrxy.raazi.data.ai.GeminiKeyStore
import com.israrxy.raazi.data.ai.ImagePayload
import com.israrxy.raazi.data.ai.SongCandidate
import com.israrxy.raazi.data.ai.SongIdentification
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AiInputMode { PHOTO, LISTEN }

data class AiSongSearchUiState(
    val keyConfigured: Boolean = false,
    val mode: AiInputMode = AiInputMode.PHOTO,
    val imageUri: Uri? = null,
    val isIdentifying: Boolean = false,
    val identification: SongIdentification? = null,
    val searchQuery: String = "",
    val error: String? = null,
    val isRecording: Boolean = false,
    val recordingElapsedMs: Long = 0L,
    val recordingMaxMs: Long = AudioPayload.MAX_DURATION_MS
)

/**
 * Runs the photo/audio -> Gemini -> YouTube-query flow. All recording, network,
 * and key access lives here so the screen stays a pure renderer.
 */
class AiSongSearchViewModel(app: Application) : AndroidViewModel(app) {

    private val keyStore = GeminiKeyStore(app)
    private val client = GeminiClient()

    private val _state = MutableStateFlow(
        AiSongSearchUiState(keyConfigured = keyStore.isConfigured())
    )
    val state: StateFlow<AiSongSearchUiState> = _state.asStateFlow()

    private var identifyJob: Job? = null
    private var audioJob: Job? = null
    private var lastAudio: ByteArray? = null

    @Volatile
    private var stopRecordingRequested = false

    fun refreshKeyState() {
        _state.update { it.copy(keyConfigured = keyStore.isConfigured()) }
    }

    fun setMode(mode: AiInputMode) {
        if (_state.value.mode == mode) return
        stopRecordingRequested = true
        audioJob?.cancel()
        audioJob = null
        identifyJob?.cancel()
        lastAudio = null
        _state.update {
            it.copy(
                mode = mode,
                imageUri = null,
                isIdentifying = false,
                identification = null,
                searchQuery = "",
                error = null,
                isRecording = false,
                recordingElapsedMs = 0L
            )
        }
    }

    fun onImagePicked(uri: Uri) {
        _state.update {
            it.copy(
                imageUri = uri,
                identification = null,
                searchQuery = "",
                error = null
            )
        }
        identify()
    }

    fun startListening() {
        val state = _state.value
        if (state.isRecording || state.isIdentifying) return

        val apiKey = keyStore.readKey()
        if (apiKey.isNullOrBlank()) {
            _state.update {
                it.copy(
                    keyConfigured = false,
                    isRecording = false,
                    error = "Add your Gemini API key in Settings → AI & Gemini first."
                )
            }
            return
        }

        cancelRecording()
        identifyJob?.cancel()
        audioJob?.cancel()
        audioJob = null
        lastAudio = null
        stopRecordingRequested = false

        audioJob = viewModelScope.launch {
            _state.update {
                it.copy(
                    keyConfigured = true,
                    mode = AiInputMode.LISTEN,
                    isRecording = true,
                    recordingElapsedMs = 0L,
                    isIdentifying = false,
                    identification = null,
                    searchQuery = "",
                    error = null
                )
            }

            try {
                val wav = AudioPayload.record(
                    shouldStop = { stopRecordingRequested },
                    onProgress = { elapsed, maxMs ->
                        _state.update { current ->
                            if (current.isRecording) {
                                current.copy(
                                    recordingElapsedMs = elapsed,
                                    recordingMaxMs = maxMs
                                )
                            } else {
                                current
                            }
                        }
                    }
                )
                lastAudio = wav
                _state.update { it.copy(isRecording = false) }
                identifyAudio(apiKey, wav)
            } catch (e: CancellationException) {
                _state.update {
                    it.copy(isRecording = false, recordingElapsedMs = 0L)
                }
                throw e
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isRecording = false,
                        recordingElapsedMs = 0L,
                        isIdentifying = false,
                        error = e.message ?: "Could not listen to that clip. Try again."
                    )
                }
            }
        }
    }

    /** Finishes early; the clip is identified as soon as recording stops. */
    fun stopListening() {
        if (_state.value.isRecording) stopRecordingRequested = true
    }

    /** Stops recording without sending audio, for example when leaving the screen. */
    fun cancelRecording() {
        if (!_state.value.isRecording) return
        stopRecordingRequested = true
        audioJob?.cancel()
        audioJob = null
        _state.update {
            it.copy(isRecording = false, recordingElapsedMs = 0L)
        }
    }

    fun onMicPermissionDenied() {
        _state.update {
            it.copy(
                isRecording = false,
                error = "Microphone permission is needed. Allow it for Raazi in " +
                    "system settings, then try again."
            )
        }
    }

    fun retry() {
        when (_state.value.mode) {
            AiInputMode.PHOTO -> identify()
            AiInputMode.LISTEN -> {
                val audio = lastAudio
                val apiKey = keyStore.readKey()
                if (audio == null || apiKey.isNullOrBlank()) {
                    _state.update { it.copy(error = null) }
                } else {
                    audioJob?.cancel()
                    audioJob = viewModelScope.launch {
                        identifyAudio(apiKey, audio)
                    }
                }
            }
        }
    }

    fun updateSearchQuery(value: String) {
        _state.update { it.copy(searchQuery = value.take(160)) }
    }

    fun useAlternative(candidate: SongCandidate) {
        _state.update { it.copy(searchQuery = candidate.query) }
    }

    fun useDetectedLyrics() {
        val lyrics = _state.value.identification?.lyricsQuery().orEmpty()
        if (lyrics.isNotBlank()) {
            _state.update { it.copy(searchQuery = lyrics) }
        }
    }

    fun dismissError() {
        _state.update { it.copy(error = null) }
    }

    private fun identify() {
        val uri = _state.value.imageUri ?: return
        val apiKey = keyStore.readKey()
        if (apiKey.isNullOrBlank()) {
            _state.update {
                it.copy(
                    keyConfigured = false,
                    isIdentifying = false,
                    error = "Add your Gemini API key in Settings → AI & Gemini first."
                )
            }
            return
        }

        identifyJob?.cancel()
        identifyJob = viewModelScope.launch {
            _state.update {
                it.copy(
                    keyConfigured = true,
                    isIdentifying = true,
                    identification = null,
                    error = null
                )
            }

            try {
                val jpeg = ImagePayload.prepare(getApplication(), uri)
                val result = identifyWithFallback(apiKey) { model ->
                    client.identify(apiKey, model, jpeg)
                }
                _state.update {
                    it.copy(
                        isIdentifying = false,
                        identification = result,
                        searchQuery = result.primaryQuery(),
                        error = null
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isIdentifying = false,
                        identification = null,
                        error = e.message ?: "Could not identify that photo. Try again."
                    )
                }
            }
        }
    }

    private suspend fun identifyAudio(apiKey: String, wav: ByteArray) {
        _state.update {
            it.copy(
                keyConfigured = true,
                isIdentifying = true,
                identification = null,
                error = null
            )
        }

        try {
            val result = identifyWithFallback(apiKey) { model ->
                client.identifyAudio(apiKey, model, wav)
            }
            _state.update {
                it.copy(
                    isIdentifying = false,
                    identification = result,
                    searchQuery = result.primaryQuery(),
                    error = null
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _state.update {
                it.copy(
                    isIdentifying = false,
                    identification = null,
                    error = e.message ?: "Could not identify that song. Try again."
                )
            }
        }
    }

    private suspend fun identifyWithFallback(
        apiKey: String,
        request: suspend (model: String) -> SongIdentification
    ): SongIdentification {
        val requested = keyStore.readModel()
        return try {
            request(requested)
        } catch (e: GeminiException) {
            if (e.statusCode != 404) throw e
            val available = client.listModels(apiKey)
            val fallback = GeminiClient.chooseModel(available, "gemini-2.5-flash")
            if (fallback == requested) throw e
            keyStore.saveModel(fallback)
            request(fallback)
        }
    }

    override fun onCleared() {
        stopRecordingRequested = true
        audioJob?.cancel()
        identifyJob?.cancel()
        super.onCleared()
    }
}
