package com.israrxy.raazi.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
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

data class AiSongSearchUiState(
    val keyConfigured: Boolean = false,
    val imageUri: Uri? = null,
    val isIdentifying: Boolean = false,
    val identification: SongIdentification? = null,
    val searchQuery: String = "",
    val error: String? = null
)

/**
 * Runs the photo -> Gemini -> YouTube-query flow. All network and key access
 * lives here so the screen stays a pure renderer.
 */
class AiSongSearchViewModel(app: Application) : AndroidViewModel(app) {

    private val keyStore = GeminiKeyStore(app)
    private val client = GeminiClient()

    private val _state = MutableStateFlow(
        AiSongSearchUiState(keyConfigured = keyStore.isConfigured())
    )
    val state: StateFlow<AiSongSearchUiState> = _state.asStateFlow()

    private var identifyJob: Job? = null

    fun refreshKeyState() {
        _state.update { it.copy(keyConfigured = keyStore.isConfigured()) }
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

    fun retry() {
        identify()
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
                val requestedModel = keyStore.readModel()
                val result = try {
                    client.identify(apiKey, requestedModel, jpeg)
                } catch (e: GeminiException) {
                    if (e.statusCode != 404) throw e
                    val available = client.listModels(apiKey)
                    val fallback = GeminiClient.chooseModel(available, "gemini-2.5-flash")
                    if (fallback == requestedModel) throw e
                    keyStore.saveModel(fallback)
                    client.identify(apiKey, fallback, jpeg)
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

    override fun onCleared() {
        identifyJob?.cancel()
        super.onCleared()
    }
}
