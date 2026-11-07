package com.israrxy.raazi.data.ai

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class GeminiException(
    val statusCode: Int,
    override val message: String
) : Exception(message)

/** Result of a grounded second pass, plus how many billable searches it used. */
data class WebVerification(
    val identification: SongIdentification,
    val searchQueries: Int
)

/**
 * Thin client for the Gemini REST API. Runs entirely on the user's own key and
 * talks to Google directly; Raazi never proxies or stores requests.
 */
class GeminiClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(90, TimeUnit.SECONDS)
        .build()

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun identify(
        apiKey: String,
        model: String,
        jpegBytes: ByteArray
    ): SongIdentification = withContext(Dispatchers.IO) {
        val encodedImage = Base64.encodeToString(jpegBytes, Base64.NO_WRAP)
        val body = buildIdentifyBody(
            prompt = PHOTO_IDENTIFY_PROMPT,
            mimeType = "image/jpeg",
            base64Data = encodedImage
        )
        parseIdentification(executeGenerate(apiKey, model, body))
    }

    /**
     * Sends a short recorded clip. Gemini can use vocals/lyrics and melody, but
     * it is not a fingerprinting service, so the prompt asks it to stay honest
     * about low confidence.
     */
    suspend fun identifyAudio(
        apiKey: String,
        model: String,
        wavBytes: ByteArray
    ): SongIdentification = withContext(Dispatchers.IO) {
        val encodedAudio = Base64.encodeToString(wavBytes, Base64.NO_WRAP)
        val body = buildIdentifyBody(
            prompt = AUDIO_IDENTIFY_PROMPT,
            mimeType = "audio/wav",
            base64Data = encodedAudio
        )
        parseIdentification(executeGenerate(apiKey, model, body))
    }

    /**
     * Second, text-only pass that asks Gemini to confirm the first result with
     * Google Search. The audio/photo is never re-uploaded here.
     *
     * Grounding and structured output have not always coexisted reliably, so
     * this request asks for JSON in the prompt and parses the answer
     * tolerantly instead of setting responseMimeType.
     */
    suspend fun verifyWithWebSearch(
        apiKey: String,
        model: String,
        identification: SongIdentification,
        source: String
    ): WebVerification = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            putJsonArray("contents") {
                addJsonObject {
                    put("role", "user")
                    putJsonArray("parts") {
                        addJsonObject { put("text", webVerifyPrompt(identification, source)) }
                    }
                }
            }
            putJsonArray("tools") {
                addJsonObject { putJsonObject("google_search") {} }
            }
            putJsonObject("generationConfig") {
                put("maxOutputTokens", 1200)
            }
        }.toString()

        val raw = executeGenerate(apiKey, model, body)
        val parsed = SongIdentificationParser.parse(extractText(raw))
            ?: throw GeminiException(0, "Gemini could not verify that match.")
        WebVerification(
            identification = parsed.copy(verifiedByWeb = hasGroundingMetadata(raw)),
            searchQueries = countWebSearchQueries(raw)
        )
    }

    private fun buildIdentifyBody(
        prompt: String,
        mimeType: String,
        base64Data: String
    ): String = buildJsonObject {
        putJsonArray("contents") {
            addJsonObject {
                put("role", "user")
                putJsonArray("parts") {
                    addJsonObject { put("text", prompt) }
                    addJsonObject {
                        putJsonObject("inline_data") {
                            put("mime_type", mimeType)
                            put("data", base64Data)
                        }
                    }
                }
            }
        }
        putJsonObject("generationConfig") {
            put("maxOutputTokens", 1200)
            put("responseMimeType", "application/json")
        }
    }.toString()

    private fun parseIdentification(responseText: String): SongIdentification {
        val answer = extractText(responseText)
        return SongIdentificationParser.parse(answer)
            ?: throw GeminiException(
                0,
                "Gemini returned an unexpected answer. Try again."
            )
    }

    /** Sends a tiny text-only prompt so testing a key never uploads a photo. */
    suspend fun testKey(apiKey: String, model: String): String = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            putJsonArray("contents") {
                addJsonObject {
                    put("role", "user")
                    putJsonArray("parts") {
                        addJsonObject { put("text", "Reply with the single word OK.") }
                    }
                }
            }
            putJsonObject("generationConfig") {
                put("maxOutputTokens", 16)
            }
        }
        extractText(executeGenerate(apiKey, model, body.toString())).ifBlank { "OK" }
    }

    /** Lists models that this key can use for generateContent. Never throws. */
    suspend fun listModels(apiKey: String): List<String> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url("$BASE_URL/models?pageSize=1000")
                .addHeader("x-goog-api-key", apiKey)
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@runCatching emptyList<String>()
                parseModels(response.body?.string().orEmpty())
            }
        }.getOrDefault(emptyList())
    }

    private fun executeGenerate(apiKey: String, model: String, body: String): String {
        val request = Request.Builder()
            .url("$BASE_URL/models/$model:generateContent")
            .addHeader("x-goog-api-key", apiKey)
            .post(body.toRequestBody(jsonMediaType))
            .build()

        client.newCall(request).execute().use { response ->
            val responseText = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw GeminiException(
                    statusCode = response.code,
                    message = messageFor(response.code, responseText)
                )
            }
            return responseText
        }
    }

    private fun extractText(raw: String): String {
        val root = runCatching { json.parseToJsonElement(raw).jsonObject }.getOrNull()
            ?: return ""
        val candidate = (root["candidates"] as? JsonArray)
            ?.firstOrNull()
            ?.let { it as? JsonObject }
        val parts = (candidate?.get("content") as? JsonObject)
            ?.get("parts") as? JsonArray
        val text = parts
            ?.mapNotNull { (it as? JsonObject)?.get("text")?.jsonPrimitive?.contentOrNull }
            ?.joinToString("\n")
            .orEmpty()
        if (text.isNotBlank()) return text

        val blockReason = (root["promptFeedback"] as? JsonObject)
            ?.get("blockReason")
            ?.jsonPrimitive
            ?.contentOrNull
        return if (blockReason != null) {
            throw GeminiException(
                0,
                "Google blocked that image ($blockReason). Try another photo."
            )
        } else {
            ""
        }
    }

    private fun parseModels(raw: String): List<String> {
        val root = runCatching { json.parseToJsonElement(raw).jsonObject }.getOrNull()
            ?: return emptyList()
        val models = root["models"] as? JsonArray ?: return emptyList()
        return models.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            val name = (obj["name"] as? JsonPrimitive)
                ?.contentOrNull
                ?.removePrefix("models/")
                ?.takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            val methods = (obj["supportedGenerationMethods"] as? JsonArray)
                ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
                .orEmpty()
            if (methods.isNotEmpty() && "generateContent" !in methods) null else name
        }.distinct()
    }

    private fun groundingMetadata(raw: String): JsonObject? = runCatching {
        val root = json.parseToJsonElement(raw).jsonObject
        val candidate = (root["candidates"] as? JsonArray)
            ?.firstOrNull()
            ?.let { it as? JsonObject }
        candidate?.get("groundingMetadata") as? JsonObject
    }.getOrNull()

    private fun hasGroundingMetadata(raw: String): Boolean = groundingMetadata(raw) != null

    private fun countWebSearchQueries(raw: String): Int {
        val queries = groundingMetadata(raw)
            ?.get("webSearchQueries") as? JsonArray
        return queries?.size?.takeIf { it > 0 } ?: 1
    }

    private fun webVerifyPrompt(
        identification: SongIdentification,
        source: String
    ): String {
        val title = identification.title ?: "unknown"
        val artist = identification.artist ?: "unknown"
        val album = identification.album ?: "unknown"
        val year = identification.year ?: "unknown"
        val confidence = identification.confidencePercent?.let { "$it%" } ?: "unknown"
        val lyrics = identification.detectedText?.take(500) ?: "none"
        val query = identification.searchQuery ?: "none"
        val alternatives = identification.alternatives
            .take(3)
            .joinToString("; ") { "${it.title} — ${it.artist}" }
            .ifBlank { "none" }
        return """
            You verify a music match using Google Search.

            A first pass identified this song from $source:
            - Title: $title
            - Artist: $artist
            - Album: $album
            - Year: $year
            - Confidence: $confidence
            - YouTube query: $query
            - Detected lyrics: $lyrics
            - Other guesses: $alternatives

            Search the web to confirm the canonical song. Prefer the original
            studio recording by the credited artist. If the first pass is wrong,
            return the correct song. If no song can be confirmed, set identified
            to false.

            Return ONLY a JSON object with exactly this shape:
            {
              "identified": true or false,
              "title": "song title or null",
              "artist": "artist name or null",
              "album": "album name or null",
              "year": "release year or null",
              "confidence": 0.0 to 1.0,
              "search_query": "best YouTube search query or null",
              "detected_text": "confirmed lyrics excerpt, max 300 chars, or null",
              "alternatives": [{"title": "other match", "artist": "artist"}],
              "message": "short explanation when not identified, else null"
            }

            Rules:
            - Use the search results. Never invent a song.
            - search_query must be tuned for YouTube (for example "Ed Sheeran Perfect").
            - confidence must reflect your real certainty after searching.
            - Keep alternatives to at most 3 likely matches.
        """.trimIndent()
    }

    private fun messageFor(code: Int, body: String): String {
        val apiMessage = runCatching {
            json.parseToJsonElement(body)
                .jsonObject["error"]
                ?.jsonObject
                ?.get("message")
                ?.jsonPrimitive
                ?.contentOrNull
        }.getOrNull()

        return when {
            code == 400 && apiMessage?.contains("API key", ignoreCase = true) == true ->
                INVALID_KEY_MESSAGE
            code == 400 ->
                "Gemini could not read that photo or clip. Try a different one."
            code == 401 || code == 403 ->
                INVALID_KEY_MESSAGE
            code == 404 ->
                "That Gemini model is not available for this key. Open Settings → AI & Gemini and test the key again."
            code == 429 ->
                "The free Gemini limit was reached. Wait a minute and try again."
            code in 500..599 ->
                "Google Gemini is busy right now. Try again in a moment."
            else ->
                apiMessage?.takeIf { it.isNotBlank() }
                    ?: "Gemini request failed ($code). Try again."
        }
    }

    companion object {
        private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta"
        private const val INVALID_KEY_MESSAGE =
            "Gemini rejected that API key. Check it in Settings → AI & Gemini."

        private val PREFERRED_MODELS = listOf(
            "gemini-3.5-flash-lite",
            "gemini-3.8-flash",
            "gemini-3.5-flash",
            "gemini-3-flash",
            "gemini-2.5-flash-lite",
            "gemini-2.5-flash",
            "gemini-2.0-flash"
        )

        /**
         * Keeps the requested model when the key supports it, otherwise falls
         * back to the newest available Flash model.
         */
        fun chooseModel(available: List<String>, preferred: String): String {
            if (available.isEmpty()) return preferred
            if (preferred in available) return preferred
            PREFERRED_MODELS.firstOrNull { it in available }?.let { return it }
            return available
                .filter {
                    it.contains("flash", ignoreCase = true) &&
                        !it.contains("lite", ignoreCase = true) &&
                        !it.contains("image", ignoreCase = true) &&
                        !it.contains("tts", ignoreCase = true)
                }
                .maxOrNull()
                ?: available.first()
        }

        private val PHOTO_IDENTIFY_PROMPT = """
            You identify music from an image. The image may be an album cover, a
            single cover, a screenshot of a song, or a photo of printed lyrics.

            Look carefully at all visible text, artwork, and layout.
            - If it is an album cover, identify the album and its most popular track.
            - If it is a single or song screenshot, identify that song.
            - If it is lyrics, identify the song those lyrics belong to.

            Return ONLY a JSON object with exactly this shape:
            {
              "identified": true or false,
              "title": "song title or null",
              "artist": "artist name or null",
              "album": "album name or null",
              "year": "release year or null",
              "confidence": 0.0 to 1.0,
              "search_query": "best YouTube search query or null",
              "detected_text": "visible lyrics or text excerpt, max 300 chars, or null",
              "alternatives": [{"title": "other match", "artist": "artist"}],
              "message": "short explanation when not identified, else null"
            }

            Rules:
            - Never invent a song. If you cannot identify it, set identified to false.
            - search_query must be tuned for YouTube (for example "Ed Sheeran Perfect").
            - confidence must reflect your real certainty.
            - detected_text should contain lyrics only when they are visible.
            - Keep alternatives to at most 3 likely matches.
        """.trimIndent()

        private val AUDIO_IDENTIFY_PROMPT = """
            You identify music from a short audio clip. The clip may contain a
            song playing in the background, someone humming or singing, or an
            instrumental passage.

            Listen for lyrics, vocal words, melody, harmony, instruments, and
            production style.
            - If vocals or lyrics are audible, use them to identify the song.
            - If it is hummed or instrumental, match only a melody you recognise
              with real confidence.
            - Prefer the original artist and studio track. Mention a cover, live
              version, or remix only when it is clearly audible.

            Return ONLY a JSON object with exactly this shape:
            {
              "identified": true or false,
              "title": "song title or null",
              "artist": "artist name or null",
              "album": "album name or null",
              "year": "release year or null",
              "confidence": 0.0 to 1.0,
              "search_query": "best YouTube search query or null",
              "detected_text": "lyrics or vocal words you can hear, max 300 chars, or null",
              "alternatives": [{"title": "other match", "artist": "artist"}],
              "message": "short explanation when not identified, else null"
            }

            Rules:
            - Never invent a song. If you cannot identify it, set identified to false.
            - Audio matching is much harder than reading text. Keep confidence honest.
            - search_query must be tuned for YouTube.
            - detected_text should contain lyrics only when they are audible.
            - Keep alternatives to at most 3 likely matches.
        """.trimIndent()
    }
}
