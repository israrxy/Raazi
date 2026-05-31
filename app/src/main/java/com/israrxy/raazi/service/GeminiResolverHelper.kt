package com.israrxy.raazi.service

import android.util.Log
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray

data class GeminiTrackQuery(
    val index: Int,
    val query: String,
    val preference: String // "SONG" or "VIDEO"
)

object GeminiResolverHelper {
    private const val TAG = "GeminiResolverHelper"
    private val client = OkHttpClient()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val jsonParser = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class GeminiResponse(
        val candidates: List<Candidate>? = null
    )

    @Serializable
    private data class Candidate(
        val content: Content? = null
    )

    @Serializable
    private data class Content(
        val parts: List<Part>? = null
    )

    @Serializable
    private data class Part(
        val text: String? = null
    )

    suspend fun generateOptimizedQueries(
        apiKey: String,
        tracks: List<SpotifyTrack>
    ): Map<Int, GeminiTrackQuery> = withContext(Dispatchers.IO) {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=$apiKey"
        
        val trackListString = tracks.joinToString("\n") { 
            "[${it.index}] Title: \"${it.title}\", Artist: \"${it.artist}\"" 
        }

        val prompt = """
            You are a music metadata matcher assistant. You help match Spotify tracks to their best YouTube Music query counterparts.
            For each track in the list below, generate a clean, optimized search query that removes fluff (like "- 2020 Remaster", "feat.", special characters, etc.) and is likely to return the exact song on YouTube Music.
            If the song is an obscure remix, live version, or cover, or likely only available as a video, set "preference" to "VIDEO". Otherwise, set "preference" to "SONG".
            
            Return the output strictly as a JSON array of objects. Each object MUST have:
            - "index": integer (matching the track index from input)
            - "query": string (optimized YouTube search query)
            - "preference": string ("SONG" or "VIDEO")

            Input Tracks:
            $trackListString
        """.trimIndent()

        val requestBodyJson = """
            {
              "contents": [{
                "parts":[{
                  "text": ${Json.encodeToString(prompt)}
                }]
              }],
              "generationConfig": {
                "responseMimeType": "application/json"
              }
            }
        """.trimIndent()

        Log.d(TAG, "Requesting Gemini REST API optimization for ${tracks.size} tracks...")
        
        val request = Request.Builder()
            .url(url)
            .post(requestBodyJson.toRequestBody(jsonMediaType))
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            val errorBody = response.body?.string()
            Log.e(TAG, "Gemini REST API failed with code: ${response.code}. Error: $errorBody")
            throw Exception("Gemini API call failed with code: ${response.code}")
        }

        val bodyString = response.body?.string() ?: throw Exception("Empty response from Gemini REST API")
        Log.d(TAG, "Received raw response from Gemini REST API: $bodyString")

        val geminiResponse = jsonParser.decodeFromString<GeminiResponse>(bodyString)
        val textResult = geminiResponse.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            ?: throw Exception("Invalid response structure from Gemini")

        Log.d(TAG, "Extracted JSON text from Gemini: $textResult")

        val parsedMap = mutableMapOf<Int, GeminiTrackQuery>()
        try {
            val jsonArray = JSONArray(textResult.trim())
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val index = obj.getInt("index")
                val query = obj.getString("query")
                val preference = obj.optString("preference", "SONG")
                parsedMap[index] = GeminiTrackQuery(index, query, preference)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed parsing JSON output from Gemini", e)
            throw e
        }

        parsedMap
    }
}
