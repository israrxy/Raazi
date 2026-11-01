package com.israrxy.raazi.data.lyrics

/**
 * Public facade for the pure-Kotlin Modern Hinglish romanization engine.
 */
object HinglishRomanizer {

    fun containsDevanagari(text: String): Boolean =
        HinglishPhonology.containsDevanagari(text)

    fun romanize(text: String): String =
        HinglishPhonology.romanize(text)

    fun romanizeDevanagariWord(rawWord: String): String =
        HinglishPhonology.romanizeDevanagariWord(rawWord)

    fun naturalizeLatinHinglish(text: String): String =
        HinglishPhonology.naturalizeLatinHinglish(text)

    fun sanitizeHinglish(text: String): String =
        HinglishPhonology.sanitizeHinglish(text)
}
