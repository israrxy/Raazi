package com.israrxy.raazi.service

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Translates lyric lines using the Gemini REST API (same key used for playlist imports).
 * Reuses the [GeminiResolverHelper] networking style.
 */
object LyricsTranslator {
    private const val TAG = "LyricsTranslator"
    private val client = OkHttpClient()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val jsonParser = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class GeminiResponse(val candidates: List<Candidate>? = null)

    @Serializable
    private data class Candidate(val content: Content? = null)

    @Serializable
    private data class Content(val parts: List<Part>? = null)

    @Serializable
    private data class Part(val text: String? = null)

    /**
     * Translates each line to [targetLang] (e.g. "en"). Returns a list with one entry per input
     * line (best-effort; falls back to the original line if parsing fails for that index).
     */
    suspend fun translateLines(
        apiKey: String,
        lines: List<String>,
        targetLang: String
    ): List<String> = withContext(Dispatchers.IO) {
        if (lines.isEmpty()) return@withContext emptyList()

        val numbered = lines.mapIndexed { i, l -> "${i + 1}. ${l.replace("\n", " ")}" }.joinToString("\n")
        val prompt = """
            You are a music lyric translator. Translate each of the following numbered lyric lines into $targetLang.
            Keep the exact same numbering (1., 2., 3., ...) and one translated line per number.
            Do not add commentary, headers, or extra lines. Preserve line breaks per number.
            If a line is already in $targetLang or has no translatable words, return it unchanged.

            $numbered
        """.trimIndent()

        val body = """
            {
              "contents": [{
                "parts":[{
                  "text": ${Json.encodeToString(prompt)}
                }]
              }]
            }
        """.trimIndent()

        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=$apiKey"
        val request = Request.Builder()
            .url(url)
            .post(body.toRequestBody(jsonMediaType))
            .build()

        return@withContext try {
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.w(TAG, "translate failed code ${response.code}")
                lines
            } else {
                val raw = response.body?.string().orEmpty()
                val text = jsonParser.decodeFromString<GeminiResponse>(raw)
                    .candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                    .orEmpty()
                parseNumbered(text, lines)
            }
        } catch (e: Exception) {
            Log.w(TAG, "translate exception", e)
            lines
        }
    }

    private fun parseNumbered(response: String, fallback: List<String>): List<String> {
        val result = MutableList(fallback.size) { fallback[it] }
        response.lines().forEach { line ->
            val m = Regex("^\\s*(\\d+)[.)\\s-]*+(.*)$").find(line) ?: return@forEach
            val idx = m.groupValues[1].toIntOrNull()?.minus(1) ?: return@forEach
            if (idx in result.indices) result[idx] = m.groupValues[2].trim()
        }
        return result
    }
}
