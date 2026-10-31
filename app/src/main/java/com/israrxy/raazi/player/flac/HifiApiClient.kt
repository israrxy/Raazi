package com.israrxy.raazi.player.flac

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

object HifiApiClient {
    private const val TAG = "HifiApiClient"

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val defaultInstances = listOf(
        "https://hifi-api.binimum.org",
        "https://hifi-api.monochrome.tf",
    )

    var customInstanceUrl: String? = null

    private fun baseUrl(): String = customInstanceUrl ?: defaultInstances.first()

    private fun allUrls(path: String): List<String> {
        val urls = mutableListOf<String>()
        customInstanceUrl?.let { urls.add("$it$path") }
        for (inst in defaultInstances) {
            val url = "$inst$path"
            if (url !in urls) urls.add(url)
        }
        return urls
    }

    suspend fun searchByISRC(isrc: String): List<HifiTrack> = withContext(Dispatchers.IO) {
        val urls = allUrls("/search/?i=$isrc")
        for (url in urls) {
            try {
                val request = Request.Builder().url(url).get().build()
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: continue
                    val result = json.decodeFromString<HifiSearchResult>(body)
                    val tracks = result.items.orEmpty()
                    if (tracks.isNotEmpty()) {
                        Log.d(TAG, "ISRC search succeeded on $url: ${tracks.size} results")
                        return@withContext tracks
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "ISRC search failed on $url: ${e.message}")
            }
        }
        Log.w(TAG, "ISRC search failed on all instances for: $isrc")
        emptyList()
    }

    suspend fun searchByText(query: String, limit: Int = 10): List<HifiTrack> = withContext(Dispatchers.IO) {
        val encoded = java.net.URLEncoder.encode(query, "UTF-8")
        val urls = allUrls("/search/?s=$encoded&limit=$limit")
        for (url in urls) {
            try {
                val request = Request.Builder().url(url).get().build()
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: continue
                    val result = json.decodeFromString<HifiSearchResult>(body)
                    val tracks = result.items.orEmpty()
                    if (tracks.isNotEmpty()) {
                        Log.d(TAG, "Text search succeeded on $url: ${tracks.size} results")
                        return@withContext tracks
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Text search failed on $url: ${e.message}")
            }
        }
        Log.w(TAG, "Text search failed on all instances for: $query")
        emptyList()
    }

    suspend fun getStreamUrl(trackId: Long, quality: String = "HI_RES_LOSSLESS"): HifiStreamResponse? = withContext(Dispatchers.IO) {
        val urls = allUrls("/track?id=$trackId&quality=$quality")
        for (url in urls) {
            try {
                val request = Request.Builder().url(url).get().build()
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: continue
                    val result = json.decodeFromString<HifiStreamResponse>(body)
                    if (!result.urls.isNullOrEmpty() || !result.manifest.isNullOrEmpty()) {
                        Log.d(TAG, "Stream URL succeeded on $url")
                        return@withContext result
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Stream URL failed on $url: ${e.message}")
            }
        }
        Log.w(TAG, "Stream URL failed on all instances for trackId: $trackId")
        null
    }

    suspend fun getTrackInfo(trackId: Long): HifiTrackInfo? = withContext(Dispatchers.IO) {
        val urls = allUrls("/info?id=$trackId")
        for (url in urls) {
            try {
                val request = Request.Builder().url(url).get().build()
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: continue
                    val result = json.decodeFromString<HifiTrackInfo>(body)
                    Log.d(TAG, "Track info succeeded on $url")
                    return@withContext result
                }
            } catch (e: Exception) {
                Log.w(TAG, "Track info failed on $url: ${e.message}")
            }
        }
        null
    }

    suspend fun checkHealth(url: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url("$url/info?id=1").get().build()
            val response = client.newCall(request).execute()
            response.isSuccessful
        } catch (e: Exception) {
            false
        }
    }
}
