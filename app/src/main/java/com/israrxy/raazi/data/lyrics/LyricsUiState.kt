package com.israrxy.raazi.data.lyrics

import com.israrxy.raazi.data.remote.Lyrics

/**
 * Strict, mutually exclusive lyrics loading states.
 *
 * The UI must only show the "lyrics not available" placeholder for [Unavailable].
 * [Loading] stays active for the entire multi-provider fallback pipeline so a slow
 * provider can never make the screen flash an empty state.
 */
sealed interface LyricsUiState {
    val trackId: String?

    data object Idle : LyricsUiState {
        override val trackId: String? = null
    }

    data class Loading(override val trackId: String?) : LyricsUiState

    data class Success(
        override val trackId: String?,
        val lyrics: Lyrics,
        val providerCache: Map<String, Lyrics> = emptyMap()
    ) : LyricsUiState

    data class Unavailable(override val trackId: String?) : LyricsUiState
}

val LyricsUiState.lyricsOrNull: Lyrics?
    get() = (this as? LyricsUiState.Success)?.lyrics

val LyricsUiState.isLoading: Boolean
    get() = this is LyricsUiState.Loading

fun Lyrics.hasUsableText(): Boolean =
    !syncedLyrics.isNullOrBlank() || !plainLyrics.isNullOrBlank()
