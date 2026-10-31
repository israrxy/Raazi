package com.israrxy.raazi.data.lyrics

internal const val VIRAMA = '\u094D'
internal const val NUQTA = '\u093C'
internal const val ANUSVARA = '\u0902'
internal const val CHANDRABINDU = '\u0901'
internal const val VISARGA = '\u0903'
internal const val AVAGRAHA = '\u093D'

internal val LABIAL_CONSONANTS = setOf('प', 'फ', 'ब', 'भ', 'म')

internal data class SyllableUnit(
    val isIndependentVowel: Boolean,
    val baseChar: Char,
    val hasNuqta: Boolean,
    val romanConsonant: String,
    val explicitMatra: Char?,
    val hasVirama: Boolean,
    val precededByVirama: Boolean,
    val nasalMark: Char?,
    val hasVisarga: Boolean
)

internal fun isIndependentVowel(ch: Char): Boolean =
    ch in '\u0904'..'\u0914' || ch == '\u0960' || ch == '\u0961'

internal fun isConsonant(ch: Char): Boolean =
    ch in '\u0915'..'\u0939' || ch in '\u0958'..'\u095F'

internal fun isDependentMatra(ch: Char): Boolean =
    ch in '\u093E'..'\u094C' || ch == '\u0962' || ch == '\u0963'

internal fun mapConsonant(ch: Char, hasNuqta: Boolean): String = when (ch) {
    'क' -> if (hasNuqta) "q" else "k"
    'ख' -> "kh"
    'ग' -> if (hasNuqta) "gh" else "g"
    'घ' -> "gh"
    'ङ' -> "n"
    'च' -> "ch"
    'छ' -> "chh"
    'ज' -> if (hasNuqta) "z" else "j"
    'झ' -> "jh"
    'ञ' -> "n"
    'ट' -> "t"
    'ठ' -> "th"
    'ड' -> "d"
    'ढ' -> "dh"
    'ण' -> "n"
    'त' -> "t"
    'थ' -> "th"
    'द' -> "d"
    'ध' -> "dh"
    'न', 'ऩ' -> "n"
    'प' -> "p"
    'फ' -> if (hasNuqta) "f" else "ph"
    'ब' -> "b"
    'भ' -> "bh"
    'म' -> "m"
    'य' -> "y"
    'र', 'ऱ' -> "r"
    'ल', 'ळ', 'ऴ' -> "l"
    'व' -> "v"
    'श', 'ष' -> "sh"
    'स' -> "s"
    'ह' -> "h"
    else -> ""
}

internal fun normalizePrecomposedNuqta(input: String): String {
    val sb = StringBuilder(input.length + 4)
    for (ch in input) {
        when (ch) {
            '\u0958' -> sb.append('क').append(NUQTA)
            '\u0959' -> sb.append('ख').append(NUQTA)
            '\u095A' -> sb.append('ग').append(NUQTA)
            '\u095B' -> sb.append('ज').append(NUQTA)
            '\u095C' -> sb.append('ड').append(NUQTA)
            '\u095D' -> sb.append('ढ').append(NUQTA)
            '\u095E' -> sb.append('फ').append(NUQTA)
            '\u095F' -> sb.append('य').append(NUQTA)
            else -> sb.append(ch)
        }
    }
    return sb.toString()
}

internal fun parseHinglishUnits(word: String): List<SyllableUnit> {
    val units = mutableListOf<SyllableUnit>()
    var i = 0
    var prevHadVirama = false

    while (i < word.length) {
        val ch = word[i]
        when {
            isIndependentVowel(ch) -> {
                var j = i + 1
                var nasal: Char? = null
                var visarga = false
                while (j < word.length) {
                    val m = word[j]
                    when (m) {
                        ANUSVARA, CHANDRABINDU -> { nasal = m; j++ }
                        VISARGA -> { visarga = true; j++ }
                        NUQTA, AVAGRAHA -> j++
                        else -> break
                    }
                }
                units.add(
                    SyllableUnit(
                        isIndependentVowel = true,
                        baseChar = ch,
                        hasNuqta = false,
                        romanConsonant = "",
                        explicitMatra = ch,
                        hasVirama = false,
                        precededByVirama = prevHadVirama,
                        nasalMark = nasal,
                        hasVisarga = visarga
                    )
                )
                prevHadVirama = false
                i = j
            }

            isConsonant(ch) -> {
                var j = i + 1
                var hasNuqta = false
                var matra: Char? = null
                var virama = false
                var nasal: Char? = null
                var visarga = false

                if (j < word.length && word[j] == NUQTA) {
                    hasNuqta = true
                    j++
                }

                if (!hasNuqta && j + 1 < word.length && word[j] == VIRAMA) {
                    val nextConsonant = word[j + 1]
                    if (ch == 'क' && nextConsonant == 'ष') {
                        j += 2
                        if (j < word.length && word[j] == NUQTA) j++
                        while (j < word.length) {
                            val m = word[j]
                            when {
                                isDependentMatra(m) -> { matra = m; j++ }
                                m == VIRAMA -> { virama = true; j++ }
                                m == ANUSVARA || m == CHANDRABINDU -> { nasal = m; j++ }
                                m == VISARGA -> { visarga = true; j++ }
                                m == AVAGRAHA -> j++
                                else -> break
                            }
                        }
                        units.add(
                            SyllableUnit(
                                isIndependentVowel = false,
                                baseChar = 'ष',
                                hasNuqta = false,
                                romanConsonant = "ksh",
                                explicitMatra = matra,
                                hasVirama = virama,
                                precededByVirama = prevHadVirama,
                                nasalMark = nasal,
                                hasVisarga = visarga
                            )
                        )
                        prevHadVirama = virama
                        i = j
                        continue
                    } else if (ch == 'ज' && nextConsonant == 'ञ') {
                        j += 2
                        while (j < word.length) {
                            val m = word[j]
                            when {
                                isDependentMatra(m) -> { matra = m; j++ }
                                m == VIRAMA -> { virama = true; j++ }
                                m == ANUSVARA || m == CHANDRABINDU -> { nasal = m; j++ }
                                m == VISARGA -> { visarga = true; j++ }
                                m == AVAGRAHA -> j++
                                else -> break
                            }
                        }
                        units.add(
                            SyllableUnit(
                                isIndependentVowel = false,
                                baseChar = 'ञ',
                                hasNuqta = false,
                                romanConsonant = "gy",
                                explicitMatra = matra,
                                hasVirama = virama,
                                precededByVirama = prevHadVirama,
                                nasalMark = nasal,
                                hasVisarga = visarga
                            )
                        )
                        prevHadVirama = virama
                        i = j
                        continue
                    }
                }

                while (j < word.length) {
                    val m = word[j]
                    when {
                        m == NUQTA -> { hasNuqta = true; j++ }
                        isDependentMatra(m) -> { matra = m; j++ }
                        m == VIRAMA -> { virama = true; j++ }
                        m == ANUSVARA || m == CHANDRABINDU -> { nasal = m; j++ }
                        m == VISARGA -> { visarga = true; j++ }
                        m == AVAGRAHA -> j++
                        else -> break
                    }
                }

                units.add(
                    SyllableUnit(
                        isIndependentVowel = false,
                        baseChar = ch,
                        hasNuqta = hasNuqta,
                        romanConsonant = mapConsonant(ch, hasNuqta),
                        explicitMatra = matra,
                        hasVirama = virama,
                        precededByVirama = prevHadVirama,
                        nasalMark = nasal,
                        hasVisarga = visarga
                    )
                )
                prevHadVirama = virama
                i = j
            }

            else -> i++
        }
    }
    return units
}
