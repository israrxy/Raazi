package com.israrxy.raazi.data.lyrics

import java.text.Normalizer

/**
 * Dedicated phonological and transliteration engine for Bengali (Bangla) script.
 * Handles high-frequency lyric word overrides, natural Latin transliteration,
 * and refinement of Google Neural romanization specifically for Bengali.
 */
object BengaliRomanizer {

    private val BENGALI_CHAR_REGEX = Regex("""[\u0980-\u09FF]""")
    private val BENGALI_WORD_REGEX = Regex("""[\u0980-\u09FF]+""")
    private val COMBINING_MARKS = Regex("""\p{M}+""")
    private val MULTIPLE_SPACES = Regex("""[ \t]+""")

    fun containsBengali(text: String): Boolean = BENGALI_CHAR_REGEX.containsMatchIn(text)

    private fun mapVowel(c: Char): String? = when (c) {
        'অ' -> "o"
        'আ' -> "a"
        'ই', 'ঈ' -> "i"
        'উ', 'ঊ' -> "u"
        'ঋ' -> "ri"
        'এ' -> "e"
        'ঐ' -> "oi"
        'ও' -> "o"
        'ঔ' -> "ou"
        else -> null
    }

    private fun mapMatra(c: Char): String? = when (c) {
        '\u09BE' -> "a"   // া
        '\u09BF', '\u09C0' -> "i"  // ি, ী
        '\u09C1', '\u09C2' -> "u"  // ু, ূ
        '\u09C3' -> "ri"  // ৃ
        '\u09C7' -> "e"   // ে
        '\u09C8' -> "oi"  // ৈ
        '\u09CB' -> "o"   // ো
        '\u09CC' -> "ou"  // ৌ
        else -> null
    }

    private fun mapConsonant(c: Char): String? = when (c) {
        'ক' -> "k"
        'খ' -> "kh"
        'গ' -> "g"
        'ঘ' -> "gh"
        'ঙ' -> "ng"
        'চ' -> "ch"
        'ছ' -> "chh"
        'জ' -> "j"
        'ঝ' -> "jh"
        'ঞ' -> "n"
        'ট' -> "t"
        'ঠ' -> "th"
        'ড' -> "d"
        'ঢ' -> "dh"
        'ণ' -> "n"
        'ত' -> "t"
        'থ' -> "th"
        'দ' -> "d"
        'ধ' -> "dh"
        'ন' -> "n"
        'প' -> "p"
        'ফ' -> "f"
        'ব' -> "b"
        'ভ' -> "bh"
        'ম' -> "m"
        'য' -> "j"
        'র' -> "r"
        'ল' -> "l"
        'শ', 'ষ', 'স' -> "sh"
        'হ' -> "h"
        'ড়' -> "r"
        'ঢ়' -> "rh"
        'য়' -> "y"
        else -> null
    }

    private fun isSClusterFollower(c: Char?): Boolean = when (c) {
        'ত', 'থ', 'প', 'ফ', 'ন', 'র', 'ল', 'ট' -> true
        else -> false
    }

    private fun getWordOverride(word: String): String? = when (word) {
        "আমি" -> "ami"
        "তুমি" -> "tumi"
        "আমরা" -> "amra"
        "তোমরা" -> "tomra"
        "আমার" -> "amar"
        "তোমার" -> "tomar"
        "আমায়" -> "amay"
        "তোমায়" -> "tomay"
        "আমাকে" -> "amake"
        "তোমাকে" -> "tomake"
        "তাকে" -> "take"
        "তার" -> "tar"
        "ভালোবাসি" -> "bhalobashi"
        "ভালোবাসা" -> "bhalobasha"
        "ভালোবাসো" -> "bhalobaso"
        "ভালোবেসে" -> "bhalobese"
        "সোনার" -> "sonar"
        "সোনা" -> "sona"
        "বাংলা" -> "bangla"
        "বাংলাদেশ" -> "bangladesh"
        "চিরদিন" -> "chirodin"
        "আকাশ" -> "akash"
        "বাতাস" -> "batas"
        "হৃদয়" -> "hridoy"
        "হৃদয়ে" -> "hridoye"
        "নীরবে" -> "nirobe"
        "রবে" -> "robe"
        "মম" -> "momo"
        "যেতে" -> "jete"
        "একলা" -> "ekla"
        "পথে" -> "pothe"
        "যদি" -> "jodi"
        "তোর" -> "tor"
        "ডাক" -> "dak"
        "শুনে" -> "shune"
        "কেউ" -> "keu"
        "না" -> "na"
        "আসে" -> "ase"
        "তবে" -> "tobe"
        "চলো" -> "cholo"
        "রে" -> "re"
        "চোখ" -> "chokh"
        "চোখে" -> "chokhe"
        "চোখের" -> "chokher"
        "মেলে" -> "mele"
        "দেখো" -> "dekho"
        "দেখি" -> "dekhi"
        "দেখা" -> "dekha"
        "যাকে" -> "jake"
        "যায়" -> "jay"
        "যাই" -> "jai"
        "হয়ে" -> "hoye"
        "হয়" -> "hoy"
        "নয়" -> "noy"
        "ছিল" -> "chhilo"
        "আছে" -> "achhe"
        "নেই" -> "nei"
        "হবে" -> "hobe"
        "করব", "করবো" -> "korbo"
        "করে" -> "kore"
        "করো" -> "koro"
        "করতে" -> "korte"
        "বলব", "বলবো" -> "bolbo"
        "বলে" -> "bole"
        "বলো" -> "bolo"
        "বলতে" -> "bolte"
        "গান" -> "gaan"
        "জীবন" -> "jibon"
        "বন্ধু" -> "bondhu"
        "বৃষ্টি" -> "brishti"
        "মন" -> "mon"
        "মনের" -> "moner"
        "মনে" -> "mone"
        "রাত" -> "raat"
        "দিন" -> "din"
        "আজ" -> "aaj"
        "কাল" -> "kaal"
        "স্বপ্ন" -> "shopno"
        "স্বপ্নে" -> "shopne"
        "স্বপ্নের" -> "shopner"
        "স্নান" -> "snan"
        "স্নানের" -> "snaner"
        "বাষ্প" -> "bashpo"
        "ভাসো" -> "bhasho"
        "কেন" -> "keno"
        "কেমন" -> "kemon"
        "এখন" -> "ekhon"
        "তখন" -> "tokhon"
        "কখন" -> "kokhon"
        "যেমন" -> "jemon"
        "কিছু" -> "kichhu"
        "একটু" -> "ektu"
        "অনেক" -> "onek"
        "কোন", "কোনো" -> "kono"
        "ভালো" -> "bhalo"
        "সব" -> "sob"
        "সবাই" -> "sobai"
        "সবাইকে" -> "sobaike"
        "সুন্দর" -> "sundor"
        else -> null
    }

    fun romanize(text: String): String {
        if (text.isBlank()) return text
        val romanized = BENGALI_WORD_REGEX.replace(text) { match ->
            romanizeBengaliWord(match.value)
        }
        return sanitizeBengali(romanized)
    }

    fun romanizeBengaliWord(rawWord: String): String {
        val clean = rawWord.replace("।", ".").replace("॥", ".")
        if (clean.isEmpty()) return clean
        getWordOverride(clean)?.let { return it }

        val sb = StringBuilder()
        var i = 0
        val n = clean.length
        while (i < n) {
            val c = clean[i]
            val next = clean.getOrNull(i + 1)
            val nextNext = clean.getOrNull(i + 2)

            // Special conjuncts: k + virama + ss = ksh (initial kh, medial/final kkh)
            if (c == 'ক' && next == '\u09CD' && nextNext == 'ষ') {
                sb.append(if (i == 0) "kh" else "kkh")
                i += 3
                val matra = clean.getOrNull(i)?.let { mapMatra(it) }
                if (matra != null) {
                    sb.append(matra)
                    i++
                } else if (clean.getOrNull(i) != '\u09CD' && i < n) {
                    sb.append("o")
                }
                continue
            }

            // j + virama + ny = gyan / gyo
            if (c == 'জ' && next == '\u09CD' && nextNext == 'ঞ') {
                sb.append(if (i == 0) "gyan" else "ggo")
                i += 3
                val matra = clean.getOrNull(i)?.let { mapMatra(it) }
                if (matra != null) {
                    sb.append(matra)
                    i++
                } else if (clean.getOrNull(i) != '\u09CD' && i < n) {
                    sb.append("o")
                }
                continue
            }

            // Independent vowels
            val vowel = mapVowel(c)
            if (vowel != null) {
                sb.append(vowel)
                i++
                continue
            }

            // Explicit matras
            val matra = mapMatra(c)
            if (matra != null) {
                sb.append(matra)
                i++
                continue
            }

            // Consonants
            var consonantStr = mapConsonant(c)
            if (consonantStr != null) {
                if (c == 'স' && next == '\u09CD' && isSClusterFollower(nextNext)) {
                    consonantStr = "s"
                }

                if (next == '\u09CD') {
                    if (nextNext == 'য') {
                        // Ja-phala
                        if (i == 0) {
                            sb.append(consonantStr).append("ya")
                        } else {
                            sb.append(consonantStr).append(consonantStr.last())
                        }
                        i += 3
                        val followMatra = clean.getOrNull(i)?.let { mapMatra(it) }
                        if (followMatra != null) {
                            sb.append(followMatra)
                            i++
                        }
                    } else if (nextNext == 'র') {
                        // Ra-phala
                        sb.append(consonantStr).append("r")
                        i += 3
                        val followMatra = clean.getOrNull(i)?.let { mapMatra(it) }
                        if (followMatra != null) {
                            sb.append(followMatra)
                            i++
                        } else if (clean.getOrNull(i) != '\u09CD' && i < n) {
                            sb.append("o")
                        }
                    } else {
                        sb.append(consonantStr)
                        i += 2
                    }
                } else {
                    val explicitNextMatra = next?.let { mapMatra(it) }
                    if (explicitNextMatra != null) {
                        sb.append(consonantStr).append(explicitNextMatra)
                        i += 2
                    } else {
                        val isWordFinal = (i == n - 1)
                        if (isWordFinal && n > 2) {
                            sb.append(consonantStr)
                        } else {
                            sb.append(consonantStr).append("o")
                        }
                        i++
                    }
                }
                continue
            }

            // Signs
            when (c) {
                'ং' -> sb.append("ng")
                'ৎ' -> sb.append("t")
                'ঃ' -> sb.append("h")
                'ঁ' -> sb.append("n")
                else -> sb.append(c)
            }
            i++
        }
        return sb.toString()
    }

    /**
     * Refines Google Neural Romanization (`dt=rm`) specifically for Bengali lyrics.
     * Ensures clean ASCII output, replaces ISO-15919 diacritics appropriately
     * without applying Hindi/Hinglish phonological transformations.
     */
    fun refineNeuralBengaliLine(sourceLine: String, neuralRomanLine: String): String {
        if (neuralRomanLine.isBlank()) {
            return romanize(sourceLine)
        }
        var s = neuralRomanLine
        s = s.replace("ā", "a").replace("ī", "i").replace("ū", "u")
            .replace("ē", "e").replace("ō", "o")
            .replace("ṛ", "ri").replace("ṝ", "ri")
            .replace("ṁ", "ng").replace("ṃ", "ng")
            .replace("Ṁ", "Ng").replace("Ṃ", "Ng")
            .replace("m\u0310", "n").replace("M\u0310", "N")
            .replace("ś", "sh").replace("ṣ", "sh")
            .replace("Ś", "Sh").replace("Ṣ", "Sh")
            .replace("ṭ", "t").replace("ḍ", "d").replace("ṇ", "n")
        s = Regex("""\b[Aa]amar\b""").replace(s) { match ->
            if (match.value.startsWith("A")) "Amar" else "amar"
        }
        return sanitizeBengali(s)
    }

    fun sanitizeBengali(text: String): String {
        if (text.isBlank()) return text
        return text.lines().joinToString("\n") { line ->
            var out = line.replace('\u00A0', ' ')
            if (out.any { it.code > 0x7F && !containsBengali(it.toString()) }) {
                out = Normalizer.normalize(out, Normalizer.Form.NFD).replace(COMBINING_MARKS, "")
            }
            out.replace(MULTIPLE_SPACES, " ").trim()
        }
    }
}
