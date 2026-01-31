package com.israrxy.raazi.data.remote

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URLEncoder

data class Lyrics(
    val id: Int,
    val trackName: String,
    val artistName: String,
    val plainLyrics: String?,
    val syncedLyrics: String?,
    val duration: Double
)

class LrcLibApi {
    private val client = OkHttpClient()
    private val TAG = "LrcLibApi"
    private val BASE_URL = "https://lrclib.net/api"

    suspend fun getLyrics(trackName: String, artistName: String, duration: Long): Lyrics? = withContext(Dispatchers.IO) {
        try {
            val encodedTrack = URLEncoder.encode(trackName, "UTF-8")
            val encodedArtist = URLEncoder.encode(artistName, "UTF-8")
            val durationSeconds = duration / 1000.0
            
            val url = "$BASE_URL/get?artist_name=$encodedArtist&track_name=$encodedTrack&duration=$durationSeconds"
            Log.d(TAG, "Fetching lyrics from: $url")
            
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "RaaziApp/1.0")
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string()

            if (response.isSuccessful && !body.isNullOrEmpty()) {
                val json = JSONObject(body)
                return@withContext parseLyrics(json)
            } else {
                // If specific match fails, try search
                Log.d(TAG, "Exact match failed, trying search...")
                return@withContext searchLyrics(trackName, artistName)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching lyrics", e)
            return@withContext null
        }
    }

    private fun searchLyrics(trackName: String, artistName: String): Lyrics? {
        try {
            val encodedTrack = URLEncoder.encode(trackName, "UTF-8")
            val encodedArtist = URLEncoder.encode(artistName, "UTF-8")
            val url = "$BASE_URL/search?q=$encodedTrack+$encodedArtist"
            
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "RaaziApp/1.0")
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return null
            
            val jsonArray = JSONArray(body)
            if (jsonArray.length() > 0) {
                // Pick the first one for now
                return parseLyrics(jsonArray.getJSONObject(0))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Search lyrics failed", e)
        }
        return null
    }

    private fun parseLyrics(json: JSONObject): Lyrics {
        return Lyrics(
            id = json.optInt("id"),
            trackName = json.optString("trackName"),
            artistName = json.optString("artistName"),
            plainLyrics = json.optString("plainLyrics"),
            syncedLyrics = json.optString("syncedLyrics"),
            duration = json.optDouble("duration")
        )
    }
}
