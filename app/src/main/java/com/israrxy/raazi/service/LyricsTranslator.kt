package com.israrxy.raazi.service

import android.util.Log
import com.google.gson.JsonArray
import com.google.gson.JsonParser
import com.israrxy.raazi.data.lyrics.HinglishLexicon
import com.israrxy.raazi.data.lyrics.HinglishRomanizer
import com.israrxy.raazi.data.lyrics.LyricsTextVariants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.Collections
import java.util.concurrent.TimeUnit

/**
 * Keyless Google Neural Translation & Romanization service using the public
 * `translate.googleapis.com/translate_a/single` endpoint (`dt=t` and `dt=rm`).
 * Requires no API key or user account.
 */
object LyricsTranslator {
    private const val TAG = "LyricsTranslator"
    private const val ENDPOINT = "https://translate.googleapis.com/translate_a/single"
    private const val LINE_SEPARATOR = "\n"

    private val KEEP_OON_WORDS = setOf("hoon", "sukoon", "junoon", "khoon", "kanoon", "qaanoon", "soon", "moon", "noon")
    private val KEEP_EE_WORDS = setOf("free", "three", "see", "tree", "bee", "fee", "knee", "jee")

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()

    private val translationCache = Collections.synchronizedMap(
        object : LinkedHashMap<String, List<String>>(64, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<String>>?): Boolean {
                return size > 64
            }
        }
    )

    private val romanizationCache = Collections.synchronizedMap(
        object : LinkedHashMap<String, String>(512, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean {
                return size > 512
            }
        }
    )

    fun getCachedRomanization(text: String): String? = romanizationCache[text.trim()]

    /**
     * Translates each line into [targetLang] (e.g. "en", "hi", "es") using Google Neural Translation.
     * No API key is needed.
     */
    suspend fun translateLines(
        lines: List<String>,
        targetLang: String
    ): List<String> = withContext(Dispatchers.IO) {
        if (lines.isEmpty()) return@withContext emptyList()
        val cacheKey = "$targetLang::${lines.joinToString(LINE_SEPARATOR)}"
        translationCache[cacheKey]?.let { return@withContext it }

        val result = MutableList(lines.size) { lines[it] }
        val batchSize = 25

        for (start in lines.indices step batchSize) {
            val end = (start + batchSize).coerceAtMost(lines.size)
            val batch = lines.subList(start, end)
            val joined = batch.joinToString(LINE_SEPARATOR) { it.replace("\n", " ").trim() }
            if (joined.isBlank()) continue

            val responsePair = fetchGoogleTranslateAndRomanize(
                text = joined,
                targetLang = targetLang
            )
            val translatedText = responsePair?.first

            if (!translatedText.isNullOrBlank()) {
                val splitLines = translatedText.split(LINE_SEPARATOR)
                for (offset in batch.indices) {
                    val candidate = splitLines.getOrNull(offset)?.trim()
                    if (!candidate.isNullOrBlank()) {
                        result[start + offset] = candidate
                    }
                }
            }
        }

        translationCache[cacheKey] = result
        result
    }

    /**
     * Uses Google's Neural Romanization (`dt=rm`) combined with Modern Hinglish phonetic rules
     * to romanize a batch of lyric lines and populate the in-memory romanization cache.
     */
    suspend fun romanizeLines(lines: List<String>): List<String> = withContext(Dispatchers.IO) {
        if (lines.isEmpty()) return@withContext emptyList()
        val cleanLines = lines.map { it.replace("\n", " ").trim() }
        val uncachedIndices = cleanLines.indices.filter { idx ->
            val line = cleanLines[idx]
            line.isNotBlank() && romanizationCache[line] == null
        }

        if (uncachedIndices.isNotEmpty()) {
            val batchSize = 20
            for (chunk in uncachedIndices.chunked(batchSize)) {
                val joined = chunk.joinToString(LINE_SEPARATOR) { cleanLines[it] }
                val neuralRoman = fetchGoogleTranslateAndRomanize(
                    text = joined,
                    targetLang = "en"
                )?.second

                val neuralLines = neuralRoman?.split(LINE_SEPARATOR).orEmpty()
                chunk.forEachIndexed { cIdx, originalIdx ->
                    val sourceLine = cleanLines[originalIdx]
                    val rawNeuralLine = neuralLines.getOrNull(cIdx)?.trim().orEmpty()
                    val finalRoman = refineNeuralHinglishLine(sourceLine, rawNeuralLine)
                    romanizationCache[sourceLine] = finalRoman

                    // Also cache word-by-word mappings when token counts align for karaoke word sync
                    val srcTokens = sourceLine.split(Regex("\\s+")).filter { it.isNotBlank() }
                    val romTokens = finalRoman.split(Regex("\\s+")).filter { it.isNotBlank() }
                    if (srcTokens.size == romTokens.size) {
                        srcTokens.indices.forEach { wIdx ->
                            romanizationCache[srcTokens[wIdx]] = romTokens[wIdx]
                        }
                    }
                }
            }
        }

        cleanLines.map { line ->
            if (line.isBlank()) line
            else romanizationCache[line] ?: HinglishRomanizer.romanize(line)
        }
    }

    /**
     * Refines Google Neural's `dt=rm` output into modern Hinglish:
     * - Uses Google's neural romanization when available
     * - Converts Google's word-final `-ee` -> `-i` (`khushee` -> `khushi`),
     *   word-final `-oo` -> `-u` (`baajoo` -> `baaju`), and `-oon` -> `-un` (`bajoon` -> `bajun`)
     * - Applies exact lexicon overrides for words in the source line (e.g. `मिलकर` -> `milkar`, `धड़कन` -> `dhadkan`)
     * - Falls back to `HinglishRomanizer` if offline or if line counts did not align.
     */
    internal fun refineNeuralHinglishLine(sourceLine: String, neuralRomanLine: String): String {
        if (neuralRomanLine.isBlank()) {
            return HinglishRomanizer.romanize(sourceLine)
        }

        val indicCleaned = LyricsTextVariants.convertIndicDiacriticsToHinglish(neuralRomanLine)
        val modernizedNeural = modernizeGoogleNeuralTokens(indicCleaned)

        if (!HinglishRomanizer.containsDevanagari(sourceLine)) {
            return HinglishRomanizer.naturalizeLatinHinglish(modernizedNeural)
        }

        // Align Devanagari source words with Google Neural tokens so known Hinglish lexicon words
        // and schwa-deleted stems are preserved alongside Google's contextual romanization.
        val srcWords = sourceLine.split(Regex("\\s+")).filter { it.isNotBlank() }
        val neuralWords = modernizedNeural.split(Regex("\\s+")).filter { it.isNotBlank() }

        if (srcWords.size != neuralWords.size) {
            return HinglishRomanizer.naturalizeLatinHinglish(modernizedNeural)
        }

        return srcWords.indices.joinToString(" ") { idx ->
            val srcToken = srcWords[idx]
            val strippedSrc = srcToken.trim { !it.isLetterOrDigit() && it !in '\u0900'..'\u097F' }
            val lexiconMatch = HinglishLexicon.WORD_OVERRIDES[strippedSrc]
            if (lexiconMatch != null) {
                srcToken.replace(strippedSrc, lexiconMatch)
            } else if (HinglishRomanizer.containsDevanagari(strippedSrc)) {
                val localRoman = HinglishRomanizer.romanize(srcToken)
                val googleToken = neuralWords[idx]
                // If Google kept an un-dropped medial schwa (e.g. "milakara" vs "milkar"), prefer local schwa-deleted form
                if (googleToken.lowercase().endsWith("a") && !localRoman.lowercase().endsWith("a")) {
                    localRoman
                } else {
                    localRoman
                }
            } else {
                neuralWords[idx]
            }
        }
    }

    internal fun modernizeGoogleNeuralTokens(text: String): String {
        return Regex("""[A-Za-z']+""").replace(text) { match ->
            val word = match.value
            val lower = word.lowercase()
            val transformed = when {
                lower in KEEP_OON_WORDS || lower in KEEP_EE_WORDS -> lower
                lower.endsWith("oon") && lower.length > 3 -> lower.dropLast(3) + "un"
                lower.endsWith("oo") && lower.length > 2 -> lower.dropLast(2) + "u"
                lower.endsWith("ee") && lower.length > 2 -> lower.dropLast(2) + "i"
                lower.endsWith("aa") && lower.length > 3 -> lower.dropLast(1)
                else -> lower
            }
            if (word.firstOrNull()?.isUpperCase() == true) {
                transformed.replaceFirstChar { it.uppercase() }
            } else {
                transformed
            }
        }.let { HinglishRomanizer.naturalizeLatinHinglish(it) }
    }

    /**
     * Calls Google's `translate_a/single` endpoint with `dt=t` and `dt=rm`.
     * @return Pair(translatedText, sourceRomanization) or null on failure.
     */
    internal fun fetchGoogleTranslateAndRomanize(
        text: String,
        targetLang: String
    ): Pair<String, String?>? {
        if (text.isBlank()) return "" to null

        val url = ENDPOINT.toHttpUrl().newBuilder()
            .addQueryParameter("client", "gtx")
            .addQueryParameter("sl", "auto")
            .addQueryParameter("tl", targetLang)
            .addQueryParameter("dt", "t")
            .addQueryParameter("dt", "rm")
            .addQueryParameter("q", text)
            .build()

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Android 14; Mobile; rv:125.0) Gecko/125.0 Firefox/125.0")
            .get()
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "Google neural translate HTTP ${response.code}")
                    return null
                }
                val rawJson = response.body?.string().orEmpty()
                parseGoogleSingleResponse(rawJson)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Google neural translate error: ${e.message}")
            null
        }
    }

    internal fun parseGoogleSingleResponse(rawJson: String): Pair<String, String?>? {
        if (rawJson.isBlank()) return null
        return runCatching {
            val root = JsonParser.parseString(rawJson)
            if (!root.isJsonArray) return null
            val sentences = root.asJsonArray.getOrNull(0) ?: return null
            if (!sentences.isJsonArray) return null

            val translatedBuilder = StringBuilder()
            val romanBuilder = StringBuilder()

            val sentenceArray = sentences.asJsonArray
            for (i in 0 until sentenceArray.size()) {
                val elem = sentenceArray.get(i)
                if (!elem.isJsonArray) continue
                val seg = elem.asJsonArray

                val transPart = seg.optString(0)
                val origPart = seg.optString(1)
                if (transPart != null && origPart != null) {
                    translatedBuilder.append(transPart)
                }

                // Romanization entry has null at [0] and [1], and source transliteration at [3] (or [2])
                if (transPart == null && origPart == null) {
                    val srcRoman = seg.optString(3) ?: seg.optString(2)
                    if (!srcRoman.isNullOrBlank()) {
                        if (romanBuilder.isNotEmpty()) romanBuilder.append(LINE_SEPARATOR)
                        romanBuilder.append(srcRoman)
                    }
                }
            }

            val translated = translatedBuilder.toString()
            val romanized = romanBuilder.toString().takeIf { it.isNotBlank() }
            translated to romanized
        }.getOrNull()
    }

    private fun JsonArray.getOrNull(index: Int) =
        if (index in 0 until size()) get(index) else null

    private fun JsonArray.optString(index: Int): String? {
        if (index !in 0 until size()) return null
        val el = get(index)
        return if (el == null || el.isJsonNull) null else runCatching { el.asString }.getOrNull()
    }
}
