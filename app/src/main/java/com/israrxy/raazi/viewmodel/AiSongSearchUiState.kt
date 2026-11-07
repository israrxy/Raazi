package com.israrxy.raazi.viewmodel

import android.net.Uri
import com.israrxy.raazi.data.ai.AudioPayload
import com.israrxy.raazi.data.ai.SongIdentification
import com.israrxy.raazi.data.ai.WebVerifyLevel
import com.israrxy.raazi.data.ai.WebVerifyUsage
import com.israrxy.raazi.data.ai.WebVerifyUsageStore

enum class AiInputMode { PHOTO, LISTEN }

data class AiSongSearchUiState(
    val keyConfigured: Boolean = false,
    val mode: AiInputMode = AiInputMode.PHOTO,
    val imageUri: Uri? = null,
    val isIdentifying: Boolean = false,
    val identification: SongIdentification? = null,
    val pendingSearch: String? = null,
    val error: String? = null,
    val isRecording: Boolean = false,
    val recordingElapsedMs: Long = 0L,
    val recordingMaxMs: Long = AudioPayload.MAX_DURATION_MS,
    val recordingLevels: List<Float> = emptyList(),
    val autoSearch: Boolean = true,
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

/** Builds the one-line notice shown in the sheet footer. */
internal fun buildWebNotice(
    usage: WebVerifyUsage,
    onExplanationShown: () -> Unit
): String? {
    if (!usage.enabled) return null
    return when {
        usage.level == WebVerifyLevel.AT_CAP ->
            "Web check limit reached — using Gemini's first match until ${usage.resetLabel}."

        usage.level == WebVerifyLevel.NEAR_CAP ->
            "Web checks almost used — ${usage.remaining} left this month."

        usage.showCapExplanation -> {
            onExplanationShown()
            "Web checks are capped at ${usage.cap}/month to avoid charges."
        }

        else -> null
    }
}
