package com.israrxy.raazi.data.lyrics

import android.icu.text.Transliterator
import com.israrxy.raazi.service.LyricsTranslator

enum class LyricsViewVariant(val label: String) {
    ORIGINAL("Original"),
    ROMANIZED("Romanized")
}

object LyricsTextVariants {
    private val anyToLatin = runCatching {
        Transliterator.getInstance("Any-Latin")
    }.getOrNull()

    private val latinToAscii = runCatching {
        Transliterator.getInstance("Latin-ASCII")
    }.getOrNull()

    private val nonAsciiLetterRegex = Regex("""[^\p{ASCII}]""")

    fun availableVariants(text: String): List<LyricsViewVariant> = runCatching {
        val romanized = romanize(text)
        if ((romanized != text || nonAsciiLetterRegex.containsMatchIn(text)) && romanized.isNotBlank()) {
            listOf(LyricsViewVariant.ORIGINAL, LyricsViewVariant.ROMANIZED)
        } else {
            listOf(LyricsViewVariant.ORIGINAL)
        }
    }.getOrDefault(listOf(LyricsViewVariant.ORIGINAL))

    fun transform(text: String, variant: LyricsViewVariant): String = runCatching {
        when (variant) {
            LyricsViewVariant.ORIGINAL -> text
            LyricsViewVariant.ROMANIZED -> romanize(text)
        }
    }.getOrDefault(text)

    fun romanize(text: String): String = runCatching {
        if (text.isBlank()) return@runCatching text

        // 1. Check Google Neural Romanization cache first (single-line or multi-line)
        LyricsTranslator.getCachedRomanization(text)?.let { cached ->
            return@runCatching if (BengaliRomanizer.containsBengali(text)) {
                BengaliRomanizer.sanitizeBengali(cached)
            } else {
                HinglishRomanizer.sanitizeHinglish(cached)
            }
        }
        if (text.contains('\n')) {
            val lines = text.lines()
            val allCached = lines.all { it.isBlank() || LyricsTranslator.getCachedRomanization(it) != null }
            if (allCached) {
                return@runCatching lines.joinToString("\n") { line ->
                    if (line.isBlank()) line
                    else {
                        val cached = LyricsTranslator.getCachedRomanization(line) ?: line
                        if (BengaliRomanizer.containsBengali(line)) BengaliRomanizer.sanitizeBengali(cached)
                        else HinglishRomanizer.sanitizeHinglish(cached)
                    }
                }
            }
        }

        // 2. Handle Bengali separately without interfering with global logic
        if (BengaliRomanizer.containsBengali(text)) {
            return@runCatching BengaliRomanizer.romanize(text)
        }

        // 3. Convert Devanagari words using dedicated Modern Hinglish phonological engine
        var step = runCatching { HinglishRomanizer.romanize(text) }.getOrDefault(text)

        // 4. If non-ASCII characters remain (e.g. Gurmukhi, Arabic, CJK), transliterate
        if (nonAsciiLetterRegex.containsMatchIn(step)) {
            val toLatin = anyToLatin
            if (toLatin != null) {
                step = runCatching {
                    val rawLatin = synchronized(toLatin) { toLatin.transliterate(step) }
                    val indicFixed = convertIndicDiacriticsToHinglish(rawLatin)
                    val ascii = latinToAscii?.let { asciiTrans ->
                        synchronized(asciiTrans) { asciiTrans.transliterate(indicFixed) }
                    } ?: indicFixed
                    HinglishRomanizer.naturalizeLatinHinglish(ascii)
                }.getOrDefault(step)
            }
        }

        HinglishRomanizer.sanitizeHinglish(step)
    }.getOrDefault(text)

    /**
     * Converts ICU ISO-15919 diacritics (such as ṁ, ṃ, m̐, ś, ṣ) into modern Hinglish
     * equivalents before ASCII folding so nasalized vowels become 'n' instead of 'm'.
     */
    internal fun convertIndicDiacriticsToHinglish(latinWithDiacritics: String): String {
        var s = latinWithDiacritics
        // Chandrabindu m̐ (m + \u0310) -> n
        s = s.replace("m\u0310", "n").replace("M\u0310", "N")
        // Anusvara ṁ (\u1E41) / ṃ (\u1E43) -> m before labials (p, b, m), else n
        s = s.replace(Regex("[ṁṃ](?=[pbmPBM])"), "m")
        s = s.replace('ṁ', 'n').replace('ṃ', 'n')
        s = s.replace('Ṁ', 'N').replace('Ṃ', 'N')
        // Sibilants ś / ṣ -> sh
        s = s.replace("ś", "sh").replace("ṣ", "sh")
        s = s.replace("Ś", "Sh").replace("Ṣ", "Sh")
        // Common ISO-15919 vowel/consonant diacritics -> plain Hinglish letters
        s = s.replace("ā", "a").replace("ī", "i").replace("ū", "u")
        s = s.replace("ē", "e").replace("ō", "o")
        s = s.replace("ṛ", "r").replace("ṝ", "r")
        s = s.replace("ṅ", "n").replace("ñ", "n").replace("ṇ", "n")
        s = s.replace("ṭ", "t").replace("ḍ", "d")
        return HinglishRomanizer.sanitizeHinglish(s)
    }
}
