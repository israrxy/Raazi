package com.israrxy.raazi.data.lyrics

import java.text.Normalizer

internal object HinglishPhonology {

    private val DEVANAGARI_WORD_REGEX = Regex("""[\u0900-\u097F]+""")
    private val DEVANAGARI_CHAR_REGEX = Regex("""[\u0900-\u097F]""")
    private val COMBINING_MARKS = Regex("""\p{M}+""")
    private val INVISIBLE_CHARS = Regex("""[\u200B-\u200D\uFEFF]""")
    private val SPACED_SINGLE_LETTERS =
        Regex("""(?<![A-Za-z])(?:[A-Za-z][ \t]+){2,}[A-Za-z](?![A-Za-z])""")
    private val MULTIPLE_SPACES = Regex("""[ \t]+""")

    fun containsDevanagari(text: String): Boolean = DEVANAGARI_CHAR_REGEX.containsMatchIn(text)

    fun romanize(text: String): String {
        if (text.isBlank()) return text
        val romanized = DEVANAGARI_WORD_REGEX.replace(text) { match ->
            romanizeDevanagariWord(match.value)
        }
        return naturalizeLatinHinglish(romanized)
    }

    fun romanizeDevanagariWord(rawWord: String): String {
        val cleanWord = rawWord.replace("।", ".").replace("॥", ".")
        if (cleanWord.isEmpty()) return cleanWord

        HinglishLexicon.WORD_OVERRIDES[cleanWord]?.let { return it }
        val normalized = normalizePrecomposedNuqta(cleanWord)
        HinglishLexicon.WORD_OVERRIDES[normalized]?.let { return it }

        val units = parseHinglishUnits(normalized)
        if (units.isEmpty()) return cleanWord

        val retainInherentSchwa = computeRetainedSchwas(units)
        val sb = StringBuilder()
        for (i in units.indices) {
            val u = units[i]
            val nextUnit = units.getOrNull(i + 1)
            val prevUnit = units.getOrNull(i - 1)
            val isWordFinal = i == units.lastIndex

            if (u.isIndependentVowel) {
                val prevHadVowel = prevUnit != null && !prevUnit.hasVirama
                sb.append(
                    renderIndependentVowel(
                        vowel = u.baseChar,
                        isWordInitial = i == 0,
                        isWordFinal = isWordFinal,
                        prevUnit = prevUnit,
                        prevHadVowel = prevHadVowel,
                        nasalMark = u.nasalMark
                    )
                )
            } else {
                sb.append(renderConsonant(u, nextUnit, prevUnit))

                when {
                    u.hasVirama -> Unit
                    u.explicitMatra != null -> {
                        sb.append(
                            renderMatra(
                                matra = u.explicitMatra,
                                isWordFinal = isWordFinal,
                                nextUnit = nextUnit,
                                nasalMark = u.nasalMark
                            )
                        )
                    }
                    retainInherentSchwa[i] -> {
                        val useEh = shouldFrontInherentAToEh(i, units, retainInherentSchwa)
                        sb.append(if (useEh) "e" else "a")
                    }
                }
            }

            if (u.nasalMark != null && !isNasalFoldedIntoVowel(u, isWordFinal)) {
                val nasalConsonant =
                    if (nextUnit != null && !nextUnit.isIndependentVowel && nextUnit.baseChar in LABIAL_CONSONANTS) {
                        "m"
                    } else {
                        "n"
                    }
                sb.append(nasalConsonant)
            }

            if (u.hasVisarga) sb.append("h")
        }

        return sb.toString()
            .replace(Regex("aaa+"), "aa")
            .replace(Regex("eee+"), "ee")
            .replace(Regex("ooo+"), "oo")
    }

    fun naturalizeLatinHinglish(text: String): String {
        var out = text
        out = out.replace(
            Regex("\\b(tujh|mujh|tumh?|aap|is|us|kis|jis|hum)ako\\b", RegexOption.IGNORE_CASE)
        ) { match -> "${match.groupValues[1]}ko" }
        out = out.replace(
            Regex("\\b(baaj|baj)om\\b", RegexOption.IGNORE_CASE)
        ) { match ->
            val prefix = match.groupValues[1]
            if (prefix.startsWith("B")) "Bajun" else "bajun"
        }
        out = out.replace(
            Regex("\\b(baah|bah|raah|rah|aankh|ankh|saans|sans|yaad|yad|baat|bat|raat|rat|palk|palak|kadam|sapn|apn)om\\b", RegexOption.IGNORE_CASE)
        ) { match -> "${match.groupValues[1]}on" }
        return sanitizeHinglish(out)
    }

    fun sanitizeHinglish(text: String): String {
        if (text.isBlank()) return text
        return text.lines().joinToString("\n") { rawLine ->
            if (rawLine.isBlank() || DEVANAGARI_CHAR_REGEX.containsMatchIn(rawLine)) {
                return@joinToString rawLine
            }
            var out = rawLine.replace('\u00A0', ' ').replace(INVISIBLE_CHARS, "")
            out = out
                .replace("ś", "sh").replace("ṣ", "sh")
                .replace("Ś", "Sh").replace("Ṣ", "Sh")
                .replace("ṁ", "n").replace("ṃ", "n")
                .replace("Ṁ", "N").replace("Ṃ", "N")
            if (out.any { it.code > 0x7F }) {
                out = Normalizer.normalize(out, Normalizer.Form.NFD).replace(COMBINING_MARKS, "")
            }
            out = SPACED_SINGLE_LETTERS.replace(out) { it.value.replace(MULTIPLE_SPACES, "") }
            out
                .replace('’', '\'').replace('‘', '\'')
                .replace('“', '"').replace('”', '"')
                .replace('–', '-').replace('—', '-')
                .replace("…", "...")
                .replace(MULTIPLE_SPACES, " ")
                .trim()
        }
    }

    private fun computeRetainedSchwas(units: List<SyllableUnit>): BooleanArray {
        val n = units.size
        val hasVowel = BooleanArray(n)
        val isSchwaCandidate = BooleanArray(n)

        for (i in 0 until n) {
            val u = units[i]
            when {
                u.isIndependentVowel -> hasVowel[i] = true
                u.hasVirama -> hasVowel[i] = false
                u.explicitMatra != null -> hasVowel[i] = true
                else -> {
                    isSchwaCandidate[i] = true
                    hasVowel[i] = true
                }
            }
        }

        val last = n - 1
        if (n >= 2 && isSchwaCandidate[last]) {
            val uLast = units[last]
            val keepFinalForConjunct =
                uLast.precededByVirama && (uLast.baseChar == 'य' || uLast.baseChar == 'व')
            if (!keepFinalForConjunct && uLast.nasalMark == null && !uLast.hasVisarga) {
                hasVowel[last] = false
            }
        }

        val hasOpenPrefix = n >= 4 && !units[0].isIndependentVowel &&
            (units[0].baseChar == 'ब' && units[0].explicitMatra == '\u0947' ||
                units[0].baseChar == 'न' && units[0].explicitMatra == '\u093E')

        for (i in (n - 2) downTo 1) {
            if (!isSchwaCandidate[i]) continue
            if (hasOpenPrefix && i == 1) continue

            val u = units[i]
            if (u.precededByVirama || u.nasalMark != null || u.hasVisarga) continue

            val prev = units[i - 1]
            val next = units[i + 1]

            val prevHasOpenVowel =
                hasVowel[i - 1] && prev.nasalMark == null && !prev.hasVisarga && !prev.hasVirama
            val nextHasVowel = hasVowel[i + 1] && !next.hasVirama

            if (prevHasOpenVowel && nextHasVowel) hasVowel[i] = false
        }

        return BooleanArray(n) { idx -> isSchwaCandidate[idx] && hasVowel[idx] }
    }

    private fun shouldFrontInherentAToEh(
        index: Int,
        units: List<SyllableUnit>,
        retainInherentSchwa: BooleanArray
    ): Boolean {
        if (!retainInherentSchwa[index]) return false
        val next = units.getOrNull(index + 1) ?: return false

        if (!next.isIndependentVowel && next.baseChar == 'ह') {
            val hMatra = next.explicitMatra
            if (hMatra == null || hMatra == '\u093F' || hMatra == '\u0941') return true
        }
        return false
    }

    private fun isNasalFoldedIntoVowel(u: SyllableUnit, isWordFinal: Boolean): Boolean {
        if (u.nasalMark == null || !isWordFinal) return false
        val m = u.explicitMatra
        return m == '\u0947' || m == '\u094B' || m == '\u0942' || m == '\u0941' || m == '\u093E' ||
            (u.isIndependentVowel &&
                (u.baseChar == 'ऊ' || u.baseChar == 'उ' || u.baseChar == 'ए' || u.baseChar == 'ओ' || u.baseChar == 'आ'))
    }

    private fun renderConsonant(
        u: SyllableUnit,
        nextUnit: SyllableUnit?,
        prevUnit: SyllableUnit?
    ): String {
        if (u.hasVirama && u.baseChar == 'च' && nextUnit != null && !nextUnit.isIndependentVowel) {
            if (nextUnit.baseChar == 'च') return "c"
            if (nextUnit.baseChar == 'छ') return ""
        }
        if (u.baseChar == 'व' && prevUnit?.hasVirama == true) return "w"
        return u.romanConsonant
    }

    private fun renderIndependentVowel(
        vowel: Char,
        isWordInitial: Boolean,
        isWordFinal: Boolean,
        prevUnit: SyllableUnit?,
        prevHadVowel: Boolean,
        nasalMark: Char?
    ): String {
        val hasNasal = nasalMark != null
        return when (vowel) {
            'अ' -> "a"
            'आ' -> if (isWordFinal && hasNasal) "aan" else "aa"
            'इ' -> "i"
            'ई' -> when {
                isWordFinal && prevHadVowel -> "i"
                isWordInitial -> "ee"
                else -> "i"
            }
            'उ' -> if (isWordFinal && hasNasal) "un" else "u"
            'ऊ' -> when {
                isWordFinal && hasNasal -> "un"
                !isWordInitial && prevHadVowel -> "u"
                else -> "oo"
            }
            'ऋ' -> "ri"
            'ए', 'ॲ' -> when {
                isWordFinal && hasNasal && prevHadVowel -> "yein"
                isWordFinal && hasNasal -> "ein"
                !isWordInitial && prevHadVowel && prevUnit?.explicitMatra == '\u093E' -> "ye"
                else -> "e"
            }
            'ऐ' -> "ai"
            'ओ', 'ऑ' -> if (isWordFinal && hasNasal) "on" else "o"
            'औ' -> "au"
            else -> "a"
        }
    }

    private fun renderMatra(
        matra: Char,
        isWordFinal: Boolean,
        nextUnit: SyllableUnit?,
        nasalMark: Char?
    ): String {
        val hasNasal = nasalMark != null
        return when (matra) {
            '\u093E' -> when {
                isWordFinal && hasNasal -> "aan"
                isWordFinal -> "a"
                else -> "aa"
            }
            '\u093F' -> "i"
            '\u0940' -> {
                val followedByYa =
                    nextUnit != null && !nextUnit.isIndependentVowel && nextUnit.baseChar == 'य'
                if (isWordFinal || followedByYa) "i" else "ee"
            }
            '\u0941' -> if (isWordFinal && hasNasal) "un" else "u"
            '\u0942' -> {
                val followedByVowel = nextUnit != null && nextUnit.isIndependentVowel
                when {
                    isWordFinal && hasNasal -> "un"
                    isWordFinal || followedByVowel -> "u"
                    else -> "oo"
                }
            }
            '\u0943', '\u0944' -> "ri"
            '\u0947', '\u0945' -> if (isWordFinal && hasNasal) "ein" else "e"
            '\u0948' -> "ai"
            '\u094B', '\u0949' -> if (isWordFinal && hasNasal) "on" else "o"
            '\u094C' -> "au"
            else -> ""
        }
    }
}
