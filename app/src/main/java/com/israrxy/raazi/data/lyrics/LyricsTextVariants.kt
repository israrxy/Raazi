package com.israrxy.raazi.data.lyrics

import android.icu.text.Transliterator

enum class LyricsViewVariant(val label: String) {
    ORIGINAL("Original"),
    ROMANIZED("Romanized")
}

object LyricsTextVariants {
    private val anyToLatin = runCatching {
        Transliterator.getInstance("Any-Latin; Latin-ASCII")
    }.getOrNull()

    fun availableVariants(text: String): List<LyricsViewVariant> {
        val romanized = romanize(text)
        return if (romanized != text && romanized.isNotBlank()) {
            listOf(LyricsViewVariant.ORIGINAL, LyricsViewVariant.ROMANIZED)
        } else {
            listOf(LyricsViewVariant.ORIGINAL)
        }
    }

    fun transform(text: String, variant: LyricsViewVariant): String {
        return when (variant) {
            LyricsViewVariant.ORIGINAL -> text
            LyricsViewVariant.ROMANIZED -> romanize(text)
        }
    }

    private fun romanize(text: String): String {
        val transliterator = anyToLatin ?: return text
        return runCatching {
            val latin = synchronized(transliterator) { transliterator.transliterate(text) }
            naturalizeHindi(latin)
        }.getOrDefault(text)
    }

    // ICU's literal Devanagari transliteration often writes colloquial Hindi contractions
    // as "tujhako". Prefer the spellings listeners expect to read while singing along.
    private fun naturalizeHindi(text: String): String = text.replace(
        Regex("\\b(tujh|mujh|tumh?|aap|is|us|kis)ako\\b", RegexOption.IGNORE_CASE)
    ) { match -> "${match.groupValues[1]}ko" }
}
