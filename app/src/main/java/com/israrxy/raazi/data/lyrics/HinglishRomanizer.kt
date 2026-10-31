package com.israrxy.raazi.data.lyrics

/**
 * Pure-Kotlin Modern Hinglish romanization engine for Devanagari lyrics.
 *
 * Converts Hindi/Urdu Devanagari into natural conversational Hinglish
 * (e.g. "बाजू" -> "baaju", "बजूँ" -> "bajun", "बाहों में" -> "baahon mein",
 * "मिलकर खुशी हुई" -> "milkar khushi hui", "धड़कन" -> "dhadkan") rather than
 * Sanskrit/ISO-15919 transliteration ("baju", "bajom", "bahom mem", "milakara khusi hui").
 */
object HinglishRomanizer {

    private val DEVANAGARI_WORD_REGEX = Regex("""[\u0900-\u097F]+""")
    private val DEVANAGARI_CHAR_REGEX = Regex("""[\u0900-\u097F]""")

    private const val VIRAMA = '\u094D'
    private const val NUQTA = '\u093C'
    private const val ANUSVARA = '\u0902'
    private const val CHANDRABINDU = '\u0901'
    private const val VISARGA = '\u0903'
    private const val AVAGRAHA = '\u093D'

    private val LABIAL_CONSONANTS = setOf('प', 'फ', 'ब', 'भ', 'म')

    private data class SyllableUnit(
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

        // 1. Direct lexicon lookup (exact or normalized nuqta)
        HinglishLexicon.WORD_OVERRIDES[cleanWord]?.let { return it }
        val normalized = normalizePrecomposedNuqta(cleanWord)
        HinglishLexicon.WORD_OVERRIDES[normalized]?.let { return it }

        // 2. Parse into Devanagari units
        val units = parseUnits(normalized)
        if (units.isEmpty()) return cleanWord

        // 3. Determine which units have a vowel (explicit or retained inherent schwa 'a')
        val retainInherentSchwa = computeRetainedSchwas(units)

        // 4. Render each unit into Modern Hinglish
        val sb = StringBuilder()
        for (i in units.indices) {
            val u = units[i]
            val nextUnit = units.getOrNull(i + 1)
            val prevUnit = units.getOrNull(i - 1)
            val isWordFinal = (i == units.lastIndex)

            if (u.isIndependentVowel) {
                val prevHadVowel = prevUnit != null && (!prevUnit.hasVirama)
                val vowelStr = renderIndependentVowel(
                    vowel = u.baseChar,
                    isWordInitial = (i == 0),
                    isWordFinal = isWordFinal,
                    prevUnit = prevUnit,
                    prevHadVowel = prevHadVowel,
                    nasalMark = u.nasalMark
                )
                sb.append(vowelStr)
            } else {
                // Render consonant (handling geminate 'च्' + 'च'/'छ' -> 'cch'/'chh')
                val consonantStr = renderConsonant(u, nextUnit, prevUnit)
                sb.append(consonantStr)

                when {
                    u.hasVirama -> Unit // No vowel after virama
                    u.explicitMatra != null -> {
                        val matraStr = renderMatra(
                            matra = u.explicitMatra,
                            isWordFinal = isWordFinal,
                            nextUnit = nextUnit,
                            nasalMark = u.nasalMark
                        )
                        sb.append(matraStr)
                    }
                    retainInherentSchwa[i] -> {
                        // Check Hindi 'अह' -> 'eh' fronting (e.g., कहना -> kehna, शहर -> shehar, पहला -> pehla)
                        val useEh = shouldFrontInherentAToEh(i, units, retainInherentSchwa)
                        sb.append(if (useEh) "e" else "a")
                    }
                }
            }

            // Render nasalization (ं / ँ) if not already folded into a word-final vowel suffix
            if (u.nasalMark != null && !isNasalFoldedIntoVowel(u, isWordFinal)) {
                val nasalConsonant = if (nextUnit != null && !nextUnit.isIndependentVowel && nextUnit.baseChar in LABIAL_CONSONANTS) {
                    "m"
                } else {
                    "n"
                }
                sb.append(nasalConsonant)
            }

            if (u.hasVisarga) {
                sb.append("h")
            }
        }

        return sb.toString()
            .replace(Regex("aaa+"), "aa")
            .replace(Regex("eee+"), "ee")
            .replace(Regex("ooo+"), "oo")
    }

    /**
     * Cleans up any Latin/ICU-transliterated Hindi strings so Sanskritisms like
     * "-om"/"-um" nasal endings or un-contracted pronouns become natural Hinglish.
     */
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
        return out
    }

    private fun normalizePrecomposedNuqta(input: String): String {
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

    private fun parseUnits(word: String): List<SyllableUnit> {
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

                    // Special conjunct lookahead: क्ष (ksh) and ज्ञ (gy)
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

    /**
     * Right-to-left Hindi Schwa Deletion (Two-Sided Open Syllable Rule):
     * - Word-final inherent 'a' is dropped on words with >= 2 units (unless final unit is a conjunct ending in y/r/l/v).
     * - Medial inherent 'a' is dropped when preceded by a vowel syllable and followed by a syllable that has a vowel.
     */
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

        // Step 1: Word-final schwa deletion
        val last = n - 1
        if (n >= 2 && isSchwaCandidate[last]) {
            val uLast = units[last]
            val keepFinalForConjunct = uLast.precededByVirama && (uLast.baseChar == 'य' || uLast.baseChar == 'व')
            if (!keepFinalForConjunct && uLast.nasalMark == null && !uLast.hasVisarga) {
                hasVowel[last] = false
            }
        }

        // Step 2: Prefix boundary check (e.g., बेख़बर -> bekhabar, नासमझ -> nasamajh)
        val hasOpenPrefix = n >= 4 && !units[0].isIndependentVowel &&
            (units[0].baseChar == 'ब' && units[0].explicitMatra == '\u0947' ||
                units[0].baseChar == 'न' && units[0].explicitMatra == '\u093E')

        // Step 3: Right-to-left medial schwa deletion
        for (i in (n - 2) downTo 1) {
            if (!isSchwaCandidate[i]) continue
            if (hasOpenPrefix && i == 1) continue

            val u = units[i]
            if (u.precededByVirama || u.nasalMark != null || u.hasVisarga) continue

            val prev = units[i - 1]
            val next = units[i + 1]

            val prevHasOpenVowel = hasVowel[i - 1] && prev.nasalMark == null && !prev.hasVisarga && !prev.hasVirama
            val nextHasVowel = hasVowel[i + 1] && !next.hasVirama

            if (prevHasOpenVowel && nextHasVowel) {
                hasVowel[i] = false
            }
        }

        return BooleanArray(n) { idx -> isSchwaCandidate[idx] && hasVowel[idx] }
    }

    private fun shouldFrontInherentAToEh(
        index: Int,
        units: List<SyllableUnit>,
        retainInherentSchwa: BooleanArray
    ): Boolean {
        if (!retainInherentSchwa[index]) return false
        val u = units[index]
        val next = units.getOrNull(index + 1) ?: return false

        // Consonant with inherent 'a' followed by 'ह' (without long vowel on 'ह') -> 'eh' (e.g., कहना -> kehna, शहर -> shehar, पहला -> pehla)
        if (!next.isIndependentVowel && next.baseChar == 'ह') {
            val hMatra = next.explicitMatra
            if (hMatra == null || hMatra == '\u093F' || hMatra == '\u0941') {
                return true
            }
        }

        // 'ह' itself between consonant and final consonant (e.g. शहर -> shehar, लहर -> lehar, ठहर -> thehar)
        if (u.baseChar == 'ह' && index > 0) {
            return false // 'श' got 'e', 'ह' keeps 'a' -> shehar
        }
        return false
    }

    private fun isNasalFoldedIntoVowel(u: SyllableUnit, isWordFinal: Boolean): Boolean {
        if (u.nasalMark == null || !isWordFinal) return false
        val m = u.explicitMatra
        return m == '\u0947' || m == '\u094B' || m == '\u0942' || m == '\u0941' || m == '\u093E' ||
            (u.isIndependentVowel && (u.baseChar == 'ऊ' || u.baseChar == 'उ' || u.baseChar == 'ए' || u.baseChar == 'ओ' || u.baseChar == 'आ'))
    }

    private fun renderConsonant(
        u: SyllableUnit,
        nextUnit: SyllableUnit?,
        prevUnit: SyllableUnit?
    ): String {
        // Geminate च् + च -> cch (saccha), च् + छ -> chh (achha)
        if (u.hasVirama && u.baseChar == 'च' && nextUnit != null && !nextUnit.isIndependentVowel) {
            if (nextUnit.baseChar == 'च') return "c"
            if (nextUnit.baseChar == 'छ') return ""
        }
        // 'व' after a consonant with virama (e.g. ख़्वाब -> khwaab)
        if (u.baseChar == 'व' && prevUnit?.hasVirama == true) {
            return "w"
        }
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
                isWordFinal && prevHadVowel -> "i" // हुई -> hui, कोई -> koi, तन्हाई -> tanhaai
                isWordInitial -> "ee"
                else -> "i"
            }
            'उ' -> if (isWordFinal && hasNasal) "un" else "u"
            'ऊ' -> when {
                isWordFinal && hasNasal -> "un" // जाऊँ -> jaaun, पाऊँ -> paaun
                !isWordInitial && prevHadVowel -> "u" // जाऊँगा -> jaaunga
                else -> "oo"
            }
            'ऋ' -> "ri"
            'ए', 'ॲ' -> when {
                isWordFinal && hasNasal && prevHadVowel -> "yein" // दुआएँ -> duaayein, अदाएँ -> adaayein
                isWordFinal && hasNasal -> "ein"
                !isWordInitial && prevHadVowel && prevUnit?.explicitMatra == '\u093E' -> "ye" // जाए -> jaaye, आए -> aaye
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
            '\u093E' -> { // ा
                when {
                    isWordFinal && hasNasal -> "aan" // दास्ताँ -> daastaan, लड़कियाँ -> ladkiyaan
                    isWordFinal -> "a" // मेरा -> mera, जाना -> jaana, थोड़ा -> thoda
                    else -> "aa" // बाजू -> baaju, बात -> baat, प्यार -> pyaar, बाहों -> baahon
                }
            }
            '\u093F' -> "i" // ि
            '\u0940' -> { // ी
                val followedByYa = nextUnit != null && !nextUnit.isIndependentVowel && nextUnit.baseChar == 'य'
                if (isWordFinal || followedByYa) "i" else "ee" // खुशी -> khushi, गीत -> geet, दीवाना -> deewana
            }
            '\u0941' -> { // ु
                if (isWordFinal && hasNasal) "un" else "u" // बजुँ -> bajun
            }
            '\u0942' -> { // ू
                val followedByVowel = nextUnit != null && nextUnit.isIndependentVowel
                when {
                    isWordFinal && hasNasal -> "un" // बजूँ -> bajun, बाजूँ -> baajun, करूँ -> karun
                    isWordFinal || followedByVowel -> "u" // बाजू -> baaju, आँसू -> aansu, बाजुओं -> baajuon
                    else -> "oo" // फूल -> phool, दूर -> door, सुकून -> sukoon
                }
            }
            '\u0943', '\u0944' -> "ri" // ृ
            '\u0947', '\u0945' -> { // े
                if (isWordFinal && hasNasal) "ein" else "e" // आँखें -> aankhein, बातें -> baatein, चलें -> chalein
            }
            '\u0948' -> "ai" // ै
            '\u094B', '\u0949' -> { // ो
                if (isWordFinal && hasNasal) "on" else "o" // बाहों -> baahon, राहों -> raahon, बजों -> bajon
            }
            '\u094C' -> "au" // ौ
            else -> ""
        }
    }

    private fun mapConsonant(ch: Char, hasNuqta: Boolean): String = when (ch) {
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
        'ड' -> "d" // ड़ -> d in modern Hinglish (dhadkan, thoda, bada, chhod, ladki)
        'ढ' -> "dh" // ढ़ -> dh in modern Hinglish (padh, badh, chadh)
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

    private fun isIndependentVowel(ch: Char): Boolean = ch in '\u0904'..'\u0914' || ch == '\u0960' || ch == '\u0961'

    private fun isConsonant(ch: Char): Boolean = ch in '\u0915'..'\u0939' || ch in '\u0958'..'\u095F'

    private fun isDependentMatra(ch: Char): Boolean = ch in '\u093E'..'\u094C' || ch == '\u0962' || ch == '\u0963'
}
