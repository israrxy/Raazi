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
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .retryOnConnectionFailure(true)
        .build()

    private val defaultInstances = listOf(
        "https://hifi-api-bffw.onrender.com",
        "https://monochrome-api.samidy.com",
        "https://hifi-api-w9y8.onrender.com",
        "https://wolf.qqdl.site",
        "https://maus.qqdl.site",
        "https://vogel.qqdl.site",
        "https://katze.qqdl.site",
        "https://hund.qqdl.site",
        "https://tracks.monochrome.st"
    )

    var customInstanceUrl: String? = null
    @Volatile
    var lastResolutionIssue: String? = null

    private fun baseUrl(): String = customInstanceUrl?.takeIf { it.isNotBlank() } ?: defaultInstances.first()

    private fun formatExceptionMessage(e: Exception): String {
        return when (e) {
            is java.net.UnknownHostException -> "HiFi mirror unreachable (DNS lookup failed)"
            is java.net.SocketTimeoutException -> "HiFi mirror connection timed out"
            is java.net.ConnectException -> "Connection refused by HiFi mirror"
            else -> "HiFi connection error: ${e.localizedMessage ?: e.message ?: "unknown"}"
        }
    }

    private fun allUrls(path: String): List<String> {
        val urls = mutableListOf<String>()
        val custom = customInstanceUrl?.trim()?.trimEnd('/')
        if (!custom.isNullOrBlank()) {
            urls.add("$custom$path")
        }
        for (inst in defaultInstances) {
            val url = "$inst$path"
            if (url !in urls) urls.add(url)
        }
        return urls
    }

    suspend fun searchByISRC(isrc: String): List<HifiTrack> = withContext(Dispatchers.IO) {
        val urls = allUrls("/search/?i=$isrc")
        var lastErr: String? = null
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
                        lastResolutionIssue = null
                        return@withContext tracks
                    }
                } else {
                    lastErr = "HiFi server returned HTTP ${response.code}"
                }
            } catch (e: Exception) {
                lastErr = formatExceptionMessage(e)
                Log.w(TAG, "ISRC search failed on $url: ${e.message}")
            }
        }
        lastResolutionIssue = lastErr
        Log.w(TAG, "ISRC search failed on all instances for: $isrc")
        emptyList()
    }

    suspend fun searchByText(query: String, limit: Int = 10): List<HifiTrack> = withContext(Dispatchers.IO) {
        val encoded = java.net.URLEncoder.encode(query, "UTF-8")
        val urls = allUrls("/search/?s=$encoded&limit=$limit")
        var lastErr: String? = null
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
                        lastResolutionIssue = null
                        return@withContext tracks
                    }
                } else {
                    lastErr = "HiFi server returned HTTP ${response.code}"
                }
            } catch (e: Exception) {
                lastErr = formatExceptionMessage(e)
                Log.w(TAG, "Text search failed on $url: ${e.message}")
            }
        }
        lastResolutionIssue = lastErr
        Log.w(TAG, "Text search failed on all instances for: $query")
        emptyList()
    }

    suspend fun getStreamUrl(trackId: Long, quality: String = "HI_RES_LOSSLESS"): HifiStreamResponse? = withContext(Dispatchers.IO) {
        val urls = allUrls("/track?id=$trackId&quality=$quality")
        var lastErr: String? = null
        for (url in urls) {
            try {
                val request = Request.Builder().url(url).get().build()
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: continue
                    val result = json.decodeFromString<HifiStreamResponse>(body)
                    if (!result.urls.isNullOrEmpty() || !result.manifest.isNullOrEmpty()) {
                        Log.d(TAG, "Stream URL succeeded on $url")
                        lastResolutionIssue = null
                        return@withContext result
                    }
                } else {
                    lastErr = "HiFi server returned HTTP ${response.code}"
                }
            } catch (e: Exception) {
                lastErr = formatExceptionMessage(e)
                Log.w(TAG, "Stream URL failed on $url: ${e.message}")
            }
        }
        lastResolutionIssue = lastErr
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

    suspend fun testConnection(url: String): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val target = url.trim().trimEnd('/')
        val start = System.currentTimeMillis()
        try {
            val request = Request.Builder().url("$target/info?id=1").get().build()
            val response = client.newCall(request).execute()
            val elapsed = System.currentTimeMillis() - start
            if (response.isSuccessful) {
                Pair(true, "Connected (${elapsed}ms)")
            } else {
                Pair(false, "Server responded with HTTP ${response.code}")
            }
        } catch (e: Exception) {
            Pair(false, formatExceptionMessage(e))
        }
    }
}
