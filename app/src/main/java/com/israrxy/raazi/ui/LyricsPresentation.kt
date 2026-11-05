package com.israrxy.raazi.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.israrxy.raazi.data.local.SettingsDataStore
import com.israrxy.raazi.data.lyrics.LyricsTextVariants
import com.israrxy.raazi.data.lyrics.LyricsUiState
import com.israrxy.raazi.data.lyrics.LyricsViewVariant
import com.israrxy.raazi.data.remote.Lyrics
import com.israrxy.raazi.service.LyricsTranslator
import com.israrxy.raazi.viewmodel.MusicPlayerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Single source of truth for everything the lyrics UI renders. Both the full
 * [LyricsScreen] and the mini [SpotifyLyricsPreviewCard] consume the exact same
 * instance so variant, translation and bracket state can never drift apart.
 */
internal class LyricsPresentation(
    val lyrics: Lyrics?,
    val isLoading: Boolean,
    val isUnavailable: Boolean,
    val selectedVariant: LyricsViewVariant,
    val availableVariants: List<LyricsViewVariant>,
    val showTranslation: Boolean,
    val isTranslating: Boolean,
    val translateLang: String,
    val hideBrackets: Boolean,
    val syncedLines: List<LyricLine>,
    val plainLyrics: String?,
    val translatedPlain: String?,
    val isSynced: Boolean,
    val hasWordSync: Boolean,
    val onSelectVariant: (LyricsViewVariant) -> Unit,
    val onToggleTranslation: () -> Unit,
    val onToggleHideBrackets: () -> Unit
)

@Composable
internal fun rememberLyricsPresentation(
    viewModel: MusicPlayerViewModel,
    durationMs: Long
): LyricsPresentation {
    val context = LocalContext.current
    val settingsDataStore = remember { SettingsDataStore(context) }
    val coroutineScope = rememberCoroutineScope()

    val lyricsState by viewModel.lyricsState.collectAsStateWithLifecycle()

    val lyrics = (lyricsState as? LyricsUiState.Success)?.lyrics
    val isLoading = lyricsState is LyricsUiState.Loading || lyricsState is LyricsUiState.Idle
    val isUnavailable = lyricsState is LyricsUiState.Unavailable

    val translateEnabledPref by settingsDataStore.lyricsTranslateEnabled
        .collectAsStateWithLifecycle(initialValue = false)
    val translateLang by settingsDataStore.lyricsTranslateLang
        .collectAsStateWithLifecycle(initialValue = "en")
    val hideBracketsPref by settingsDataStore.lyricsHideBracketsEnabled
        .collectAsStateWithLifecycle(initialValue = true)
    val preferredLyricsTypePref by settingsDataStore.preferredLyricsType
        .collectAsStateWithLifecycle(initialValue = "LAST_USED")
    val lastSwitchedLyricsTypePref by settingsDataStore.lastSwitchedLyricsType
        .collectAsStateWithLifecycle(initialValue = "ROMANIZED")

    var showTranslation by remember(translateEnabledPref) { mutableStateOf(translateEnabledPref) }
    var hideBrackets by remember(hideBracketsPref) { mutableStateOf(hideBracketsPref) }
    var isTranslating by remember { mutableStateOf(false) }
    val translatedLines = remember { mutableStateListOf<String>() }
    var translatedPlain by remember { mutableStateOf<String?>(null) }
    var selectedVariant by remember { mutableStateOf(LyricsViewVariant.ORIGINAL) }

    val rawSourceText = remember(lyrics) {
        (lyrics?.syncedLyrics ?: lyrics?.plainLyrics.orEmpty()).replace(STRIP_WORD_TAGS_REGEX, "")
    }
    val availableVariants = remember(rawSourceText) {
        LyricsTextVariants.availableVariants(rawSourceText)
    }

    LaunchedEffect(preferredLyricsTypePref, lastSwitchedLyricsTypePref, availableVariants) {
        val effectiveType = if (preferredLyricsTypePref == "LAST_USED") {
            lastSwitchedLyricsTypePref
        } else {
            preferredLyricsTypePref
        }
        val wantsRomanized = effectiveType == "ROMANIZED"
        selectedVariant = if (wantsRomanized && LyricsViewVariant.ROMANIZED in availableVariants) {
            LyricsViewVariant.ROMANIZED
        } else {
            LyricsViewVariant.ORIGINAL
        }
    }

    val parsedPairs = remember(lyrics?.syncedLyrics, hideBrackets) {
        parseLrcLines(lyrics?.syncedLyrics, hideBrackets = hideBrackets)
    }

    val syncedLines = remember(
        parsedPairs,
        durationMs,
        translatedLines.toList(),
        showTranslation,
        selectedVariant
    ) {
        buildRichLyricLines(
            rawPairs = parsedPairs,
            durationMs = durationMs,
            translations = translatedLines.toList(),
            showTranslation = showTranslation,
            variant = selectedVariant
        )
    }

    LaunchedEffect(selectedVariant) {
        withContext(Dispatchers.Default) {
            LyricsTextVariants.transform("warmup", selectedVariant)
        }
    }

    LaunchedEffect(lyrics?.syncedLyrics, lyrics?.plainLyrics, showTranslation, translateLang, hideBrackets) {
        if (!showTranslation) {
            translatedLines.clear()
            translatedPlain = null
            return@LaunchedEffect
        }
        val source = lyrics?.syncedLyrics ?: lyrics?.plainLyrics
        if (source.isNullOrBlank()) return@LaunchedEffect

        isTranslating = true
        try {
            withContext(Dispatchers.Default) {
                if (parsedPairs.isNotEmpty()) {
                    val originalTexts = parsedPairs.map { it.second.replace(STRIP_WORD_TAGS_REGEX, "").trim() }
                    val translations = LyricsTranslator.translateLines(originalTexts, translateLang)
                    withContext(Dispatchers.Main) {
                        translatedLines.clear()
                        translatedLines.addAll(translations)
                    }
                } else if (!lyrics?.plainLyrics.isNullOrBlank()) {
                    val plainLines = lyrics?.plainLyrics?.lines().orEmpty()
                    val translations = LyricsTranslator.translateLines(plainLines, translateLang)
                    withContext(Dispatchers.Main) {
                        translatedPlain = translations.joinToString("\n")
                    }
                }
            }
        } catch (_: Exception) {
        } finally {
            isTranslating = false
        }
    }

    val plainLyrics = remember(lyrics?.plainLyrics, hideBrackets, selectedVariant) {
        val raw = lyrics?.plainLyrics
        if (raw.isNullOrBlank()) {
            null
        } else {
            val base = if (hideBrackets) {
                raw.lines().map { cleanBracketedText(it) }.filter { it.isNotBlank() }.joinToString("\n")
            } else {
                raw
            }
            LyricsTextVariants.transform(base, selectedVariant)
        }
    }

    val onSelectVariant: (LyricsViewVariant) -> Unit = { variant ->
        selectedVariant = variant
        coroutineScope.launch {
            settingsDataStore.setLastSwitchedLyricsType(variant.name)
            if (preferredLyricsTypePref != "LAST_USED") {
                settingsDataStore.setPreferredLyricsType(variant.name)
            }
        }
    }

    // The script filter is part of the presentation surface so callers can react
    // when the provider changes; filtering itself is cached in LyricsController.
    return LyricsPresentation(
        lyrics = lyrics,
        isLoading = isLoading,
        isUnavailable = isUnavailable,
        selectedVariant = selectedVariant,
        availableVariants = availableVariants,
        showTranslation = showTranslation,
        isTranslating = isTranslating,
        translateLang = translateLang,
        hideBrackets = hideBrackets,
        syncedLines = syncedLines,
        plainLyrics = plainLyrics,
        translatedPlain = translatedPlain,
        isSynced = syncedLines.isNotEmpty(),
        hasWordSync = syncedLines.any { it.hasExplicitWordSync },
        onSelectVariant = onSelectVariant,
        onToggleTranslation = { showTranslation = !showTranslation },
        onToggleHideBrackets = {
            hideBrackets = !hideBrackets
            coroutineScope.launch { settingsDataStore.setLyricsHideBracketsEnabled(hideBrackets) }
        }
    )
}
