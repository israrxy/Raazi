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

    private val BENGALI_VOWELS = mapOf(
        'অ' to "o", 'আ' to "a", 'ই' to "i", 'ঈ' to "i",
        'উ' to "u", 'ঊ' to "u", 'ঋ' to "ri", 'এ' to "e",
        'ঐ' to "oi", 'ও' to "o", 'ঔ' to "ou"
    )

    private val BENGALI_MATRAS = mapOf(
        'া' to "a", 'ি' to "i", 'ী' to "i", 'ু' to "u",
        'ূ' to "u", 'ৃ' to "ri", 'ে' to "e", 'ৈ' to "oi",
        'ো' to "o", 'ৌ' to "ou"
    )

    private val BENGALI_CONSONANTS = mapOf(
        'ক' to "k", 'খ' to "kh", 'গ' to "g", 'ঘ' to "gh", 'ঙ' to "ng",
        'চ' to "ch", 'ছ' to "chh", 'জ' to "j", 'ঝ' to "jh", 'ঞ' to "n",
        'ট' to "t", 'ঠ' to "th", 'ড' to "d", 'ঢ' to "dh", 'ণ' to "n",
        'ত' to "t", 'থ' to "th", 'দ' to "d", 'ধ' to "dh", 'ন' to "n",
        'প' to "p", 'ফ' to "f", 'ব' to "b", 'ভ' to "bh", 'ম' to "m",
        'য' to "j", 'র' to "r", 'ল' to "l", 'শ' to "sh", 'ষ' to "sh",
        'স' to "sh", 'হ' to "h", 'ড়' to "r", 'ঢ়' to "rh", 'য়' to "y"
    )

    private val S_CLUSTER_FOLLOWERS = setOf('ত', 'থ', 'প', 'ফ', 'ন', 'র', 'ল', 'ট')

    private val BENGALI_WORD_OVERRIDES = mapOf(
        "আমি" to "ami", "তুমি" to "tumi", "আমরা" to "amra", "তোমরা" to "tomra",
        "আমার" to "amar", "তোমার" to "tomar", "আমায়" to "amay", "তোমায়" to "tomay",
        "আমাকে" to "amake", "তোমাকে" to "tomake", "তাকে" to "take", "তার" to "tar",
        "ভালোবাসি" to "bhalobashi", "ভালোবাসা" to "bhalobasha", "ভালোবাসো" to "bhalobaso",
        "ভালোবেসে" to "bhalobese", "সোনার" to "sonar", "সোনা" to "sona",
        "বাংলা" to "bangla", "বাংলাদেশ" to "bangladesh", "চিরদিন" to "chirodin",
        "আকাশ" to "akash", "বাতাস" to "batas", "হৃদয়" to "hridoy", "হৃদয়ে" to "hridoye",
        "নীরবে" to "nirobe", "রবে" to "robe", "মম" to "momo", "যেতে" to "jete",
        "একলা" to "ekla", "পথে" to "pothe", "যদি" to "jodi", "তোর" to "tor",
        "ডাক" to "dak", "শুনে" to "shune", "কেউ" to "keu", "না" to "na",
        "আসে" to "ase", "তবে" to "tobe", "চলো" to "cholo", "রে" to "re",
        "চোখ" to "chokh", "চোখে" to "chokhe", "চোখের" to "chokher",
        "মেলে" to "mele", "দেখো" to "dekho", "দেখি" to "dekhi", "দেখা" to "dekha",
        "যাকে" to "jake", "যায়" to "jay", "যাই" to "jai", "হয়ে" to "hoye",
        "হয়" to "hoy", "নয়" to "noy", "ছিল" to "chhilo", "আছে" to "achhe",
        "নেই" to "nei", "হবে" to "hobe", "করব" to "korbo", "করবো" to "korbo",
        "করে" to "kore", "করো" to "koro", "করতে" to "korte", "বলব" to "bolbo",
        "বলবো" to "bolbo", "বলে" to "bole", "বলো" to "bolo", "বলতে" to "bolte",
        "গান" to "gaan", "জীবন" to "jibon", "বন্ধু" to "bondhu", "বৃষ্টি" to "brishti",
        "মন" to "mon", "মনের" to "moner", "মনে" to "mone", "রাত" to "raat",
        "দিন" to "din", "আজ" to "aaj", "কাল" to "kaal", "স্বপ্ন" to "shopno",
        "স্বপ্নে" to "shopne", "স্বপ্নের" to "shopner", "স্নান" to "snan",
        "স্নানের" to "snaner", "বাষ্প" to "bashpo", "ভাসো" to "bhasho",
        "কেন" to "keno", "কেমন" to "kemon", "এখন" to "ekhon", "তখন" to "tokhon",
        "কখন" to "kokhon", "যেমন" to "jemon", "কিছু" to "kichhu", "একটু" to "ektu",
        "অনেক" to "onek", "কোন" to "kono", "কোনো" to "kono", "ভালো" to "bhalo",
        "সব" to "sob", "সবাই" to "sobai", "সবাইকে" to "sobaike", "সুন্দর" to "sundor"
    )

    fun containsBengali(text: String): Boolean = BENGALI_CHAR_REGEX.containsMatchIn(text)

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
        BENGALI_WORD_OVERRIDES[clean]?.let { return it }

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
                if (i < n && clean[i] in BENGALI_MATRAS) {
                    sb.append(BENGALI_MATRAS[clean[i]])
                    i++
                } else if (i < n && clean[i] != '\u09CD') {
                    sb.append("o")
                }
                continue
            }

            // j + virama + ny = gyan / gyo
            if (c == 'জ' && next == '\u09CD' && nextNext == 'ঞ') {
                sb.append(if (i == 0) "gyan" else "ggo")
                i += 3
                if (i < n && clean[i] in BENGALI_MATRAS) {
                    sb.append(BENGALI_MATRAS[clean[i]])
                    i++
                } else if (i < n && clean[i] != '\u09CD') {
                    sb.append("o")
                }
                continue
            }

            // Independent vowels
            val vowel = BENGALI_VOWELS[c]
            if (vowel != null) {
                sb.append(vowel)
                i++
                continue
            }

            // Explicit matras
            val matra = BENGALI_MATRAS[c]
            if (matra != null) {
                sb.append(matra)
                i++
                continue
            }

            // Consonants
            var consonantStr = BENGALI_CONSONANTS[c]
            if (consonantStr != null) {
                if (c == 'স' && next == '\u09CD' && nextNext in S_CLUSTER_FOLLOWERS) {
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
                        if (i < n && clean[i] in BENGALI_MATRAS) {
                            sb.append(BENGALI_MATRAS[clean[i]])
                            i++
                        }
                    } else if (nextNext == 'র') {
                        // Ra-phala
                        sb.append(consonantStr).append("r")
                        i += 3
                        if (i < n && clean[i] in BENGALI_MATRAS) {
                            sb.append(BENGALI_MATRAS[clean[i]])
                            i++
                        } else if (i < n && clean[i] != '\u09CD') {
                            sb.append("o")
                        }
                    } else {
                        sb.append(consonantStr)
                        i += 2
                    }
                } else if (next in BENGALI_MATRAS) {
                    sb.append(consonantStr).append(BENGALI_MATRAS[next])
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
        // Replace diacritics
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
