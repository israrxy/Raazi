package com.israrxy.raazi.data.ai

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.roundToInt

data class SongCandidate(
    val title: String,
    val artist: String
) {
    val query: String
        get() = listOf(title, artist)
            .filter { it.isNotBlank() }
            .joinToString(" ")
}

/**
 * Normalized result of a Gemini vision request. All fields are nullable because
 * the model is allowed to return partial data (for example lyrics it could read
 * but not identify).
 */
data class SongIdentification(
    val identified: Boolean,
    val title: String?,
    val artist: String?,
    val album: String?,
    val year: String?,
    val confidencePercent: Int?,
    val searchQuery: String?,
    val detectedText: String?,
    val alternatives: List<SongCandidate>,
    val message: String?
) {
    val hasCandidate: Boolean
        get() = identified && (!title.isNullOrBlank() || !artist.isNullOrBlank())

    fun primaryQuery(): String = listOfNotNull(
        searchQuery?.takeIf { it.isNotBlank() },
        listOfNotNull(title, artist)
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .takeIf { it.isNotBlank() },
        detectedText?.trim()?.take(120)?.takeIf { it.isNotBlank() }
    ).firstOrNull().orEmpty()

    fun lyricsQuery(): String =
        detectedText?.trim()?.take(120)?.takeIf { it.isNotBlank() }.orEmpty()
}

/**
 * Gemini is asked for JSON, but models occasionally wrap it in markdown fences
 * or add a sentence before it. This parser is deliberately forgiving.
 */
internal object SongIdentificationParser {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun parse(raw: String): SongIdentification? {
        val payload = extractJsonObject(raw) ?: return null
        val obj = runCatching { json.parseToJsonElement(payload).jsonObject }.getOrNull() ?: return null

        val title = obj.stringValue("title")
        val artist = obj.stringValue("artist")
        val identified = obj.booleanValue("identified")
            ?: (!title.isNullOrBlank() || !artist.isNullOrBlank())

        val alternatives = runCatching {
            (obj["alternatives"] as? JsonArray)?.toList().orEmpty().mapNotNull { element ->
                when (element) {
                    is JsonObject -> {
                        val altTitle = element.stringValue("title").orEmpty()
                        val altArtist = element.stringValue("artist").orEmpty()
                        if (altTitle.isBlank() && altArtist.isBlank()) {
                            null
                        } else {
                            SongCandidate(title = altTitle, artist = altArtist)
                        }
                    }
                    is JsonPrimitive -> element.contentOrNull
                        ?.takeIf { it.isNotBlank() }
                        ?.let { SongCandidate(title = it, artist = "") }
                    else -> null
                }
            }
        }.getOrDefault(emptyList())

        return SongIdentification(
            identified = identified,
            title = title,
            artist = artist,
            album = obj.stringValue("album"),
            year = obj.stringValue("year"),
            confidencePercent = obj.confidencePercent(),
            searchQuery = obj.stringValue("search_query") ?: obj.stringValue("searchQuery"),
            detectedText = obj.stringValue("detected_text") ?: obj.stringValue("detectedText"),
            alternatives = alternatives.distinctBy { it.query.lowercase() },
            message = obj.stringValue("message")
        )
    }

    private fun extractJsonObject(raw: String): String? {
        val cleaned = raw
            .replace("```json", "")
            .replace("```", "")
            .trim()
        val start = cleaned.indexOf('{')
        val end = cleaned.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return cleaned.substring(start, end + 1)
    }

    private fun JsonObject.stringValue(key: String): String? =
        (this[key] as? JsonPrimitive)
            ?.contentOrNull
            ?.trim()
            ?.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }

    private fun JsonObject.booleanValue(key: String): Boolean? =
        (this[key] as? JsonPrimitive)?.booleanOrNull

    private fun JsonObject.confidencePercent(): Int? {
        val primitive = this["confidence"] as? JsonPrimitive ?: return null
        val raw = primitive.doubleOrNull ?: primitive.contentOrNull?.toDoubleOrNull() ?: return null
        val percent = if (raw <= 1.0) raw * 100.0 else raw
        return percent.roundToInt().coerceIn(0, 100)
    }
}
