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
import com.israrxy.raazi.data.ai.SongIdentification
import com.israrxy.raazi.data.ai.WebVerifyLevel
import com.israrxy.raazi.data.ai.WebVerifyUsage
import com.israrxy.raazi.data.ai.WebVerifyUsageStore
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
    val pendingSearch: String? = null,
    val error: String? = null,
    val isRecording: Boolean = false,
    val recordingElapsedMs: Long = 0L,
    val recordingMaxMs: Long = AudioPayload.MAX_DURATION_MS,
    val recordingLevels: List<Float> = emptyList(),
    val usage: WebVerifyUsage = WebVerifyUsage(
        used = 0,
        cap = WebVerifyUsageStore.DEFAULT_CAP,
        enabled = true,
        resetLabel = "",
        level = WebVerifyLevel.NORMAL,
        showCapExplanation = false
    ),
    val webNotice: String? = null
)

/**
 * Runs the photo/audio -> Gemini -> optional web verification -> YouTube-query
 * flow. All recording, network, key access, and quota tracking live here so the
 * sheet stays a pure renderer.
 */
class AiSongSearchViewModel(app: Application) : AndroidViewModel(app) {

    private val keyStore = GeminiKeyStore(app)
    private val usageStore = WebVerifyUsageStore(app)
    private val client = GeminiClient()

    private val _state = MutableStateFlow(
        AiSongSearchUiState(
            keyConfigured = keyStore.isConfigured(),
            usage = usageStore.read()
        )
    )
    val state: StateFlow<AiSongSearchUiState> = _state.asStateFlow()

    private var identifyJob: Job? = null
    private var audioJob: Job? = null
    private var lastAudio: ByteArray? = null

    @Volatile
    private var stopRecordingRequested = false

    fun refreshKeyState() {
        _state.update {
            it.copy(
                keyConfigured = keyStore.isConfigured(),
                usage = usageStore.read()
            )
        }
    }

    /** Refreshes key and usage state when the sheet opens. */
    fun onSheetOpened() {
        val usage = usageStore.read()
        val notice = buildWebNotice(usage)
        _state.update {
            it.copy(
                keyConfigured = keyStore.isConfigured(),
                usage = usage,
                webNotice = notice
            )
        }
    }

    fun onImagePicked(uri: Uri) {
        stopRecordingRequested = true
        audioJob?.cancel()
        audioJob = null
        lastAudio = null
        _state.update {
            it.copy(
                mode = AiInputMode.PHOTO,
                imageUri = uri,
                identification = null,
                searchQuery = "",
                pendingSearch = null,
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
                    imageUri = null,
                    isRecording = true,
                    recordingElapsedMs = 0L,
                    recordingLevels = emptyList(),
                    isIdentifying = false,
                    identification = null,
                    searchQuery = "",
                    pendingSearch = null,
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
                    },
                    onLevel = { level ->
                        _state.update { current ->
                            if (current.isRecording) {
                                current.copy(
                                    recordingLevels = (current.recordingLevels + level)
                                        .takeLast(MAX_LEVEL_SAMPLES)
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
                    it.copy(
                        isRecording = false,
                        recordingElapsedMs = 0L,
                        recordingLevels = emptyList()
                    )
                }
                throw e
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isRecording = false,
                        recordingElapsedMs = 0L,
                        recordingLevels = emptyList(),
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

    /** Stops recording without sending audio, for example when leaving the sheet. */
    fun cancelRecording() {
        if (!_state.value.isRecording) return
        stopRecordingRequested = true
        audioJob?.cancel()
        audioJob = null
        _state.update {
            it.copy(
                isRecording = false,
                recordingElapsedMs = 0L,
                recordingLevels = emptyList()
            )
        }
    }

    fun onMicPermissionDenied() {
        _state.update {
            it.copy(
                mode = AiInputMode.LISTEN,
                isRecording = false,
                recordingLevels = emptyList(),
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
                    _state.update {
                        it.copy(error = null, identification = null, pendingSearch = null)
                    }
                } else {
                    audioJob?.cancel()
                    audioJob = viewModelScope.launch {
                        identifyAudio(apiKey, audio)
                    }
                }
            }
        }
    }

    /** Runs the normal Search with an explicit choice from the sheet. */
    fun searchNow(query: String) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return
        _state.update { it.copy(searchQuery = trimmed, pendingSearch = trimmed) }
    }

    fun consumePendingSearch() {
        _state.update { it.copy(pendingSearch = null) }
    }

    /** Clears the sheet and cancels any work still in flight. */
    fun reset() {
        stopRecordingRequested = true
        audioJob?.cancel()
        audioJob = null
        identifyJob?.cancel()
        identifyJob = null
        lastAudio = null
        _state.value = AiSongSearchUiState(
            keyConfigured = keyStore.isConfigured(),
            usage = usageStore.read()
        )
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
                    pendingSearch = null,
                    error = null
                )
            }

            try {
                val jpeg = ImagePayload.prepare(getApplication(), uri)
                val first = identifyWithFallback(apiKey) { model ->
                    client.identify(apiKey, model, jpeg)
                }
                applyResult(verifyIfUseful(apiKey, first, AiInputMode.PHOTO))
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
                pendingSearch = null,
                error = null
            )
        }

        try {
            val first = identifyWithFallback(apiKey) { model ->
                client.identifyAudio(apiKey, model, wav)
            }
            applyResult(verifyIfUseful(apiKey, first, AiInputMode.LISTEN))
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

    private fun applyResult(result: SongIdentification) {
        val query = result.primaryQuery()
        val autoSearch = result.isConfident && query.isNotBlank()
        _state.update {
            it.copy(
                isIdentifying = false,
                identification = result,
                searchQuery = query,
                pendingSearch = if (autoSearch) query else null,
                error = null
            )
        }
    }

    /**
     * Audio is always verified. A photo is only verified when it contained
     * lyrics or the first pass was unsure. Failures fall back silently to the
     * first result and never consume the local quota.
     */
    private suspend fun verifyIfUseful(
        apiKey: String,
        result: SongIdentification,
        mode: AiInputMode
    ): SongIdentification {
        if (!shouldVerify(result, mode)) return result
        if (!usageStore.canVerify()) return result

        return try {
            val verification = client.verifyWithWebSearch(
                apiKey = apiKey,
                model = keyStore.readModel(),
                identification = result,
                source = if (mode == AiInputMode.LISTEN) {
                    "a short audio clip"
                } else {
                    "a photo"
                }
            )
            val usage = usageStore.record(verification.searchQueries)
            val notice = buildWebNotice(usage)
            _state.update {
                it.copy(usage = usage, webNotice = notice)
            }
            val verified = verification.identification
            if (verified.hasCandidate || verified.hasLyrics) verified else result
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            result
        }
    }

    private fun shouldVerify(result: SongIdentification, mode: AiInputMode): Boolean {
        if (!result.hasCandidate && !result.hasLyrics) return false
        return mode == AiInputMode.LISTEN || result.hasLyrics || !result.isConfident
    }

    private fun buildWebNotice(usage: WebVerifyUsage): String? {
        if (!usage.enabled) return null
        return when {
            usage.level == WebVerifyLevel.AT_CAP ->
                "Web check limit reached — using Gemini's first match until ${usage.resetLabel}."

            usage.level == WebVerifyLevel.NEAR_CAP ->
                "Web checks almost used — ${usage.remaining} left this month."

            usage.showCapExplanation -> {
                usageStore.markCapExplanationShown()
                "Web checks are capped at ${usage.cap}/month to avoid charges."
            }

            else -> null
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

    private companion object {
        const val MAX_LEVEL_SAMPLES = 40
    }
}
