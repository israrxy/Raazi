package com.israrxy.raazi.service

import com.israrxy.raazi.model.MusicItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.security.MessageDigest

/**
 * Last.fm scrobbling helper (singleton object, mirroring the SleepTimer pattern).
 *
 * Implements the Last.fm API v2.0 over plain HTTP POST to the audioscrobbler root.
 * Signing: build api_sig = md5 of the sorted-by-key concatenation of ALL params
 * (including api_key and method) with NO urlencoding, then append the shared
 * secret. The secret itself is NOT sent as a parameter — it is only used for the
 * signature.
 */
object LastFmScrobbler {

    private const val API_KEY = "b25b959554ed76058ac220b7b2ed4e6"
    private const val SECRET = "425b55975eed76058ac220b7b4e8f71"
    private const val ROOT = "https://ws.audioscrobbler.com/2.0/"

    // Session key is injected by the ViewModel (observed from SettingsDataStore).
    // Held here so the call sites can stay simple (item-only signatures).
    var sessionKey: String? = null

    private val client by lazy { OkHttpClient.Builder().build() }

    /**
     * Authenticate with username + password and return the (sessionKey, name) pair.
     */
    suspend fun getMobileSession(
        username: String,
        password: String
    ): Result<Pair<String, String>> = withContext(Dispatchers.IO) {
        try {
            val params = linkedMapOf(
                "method" to "auth.getMobileSession",
                "username" to username,
                "password" to password,
                "api_key" to API_KEY
            )
            params["api_sig"] = buildSignature(params)
            val response = post(params)

            val key = tagValue(response, "key")
            val name = tagValue(response, "name")
            if (key != null && name != null) {
                Result.success(key to name)
            } else {
                val err = tagValue(response, "error") ?: "Failed to authenticate with Last.fm"
                Result.failure(Exception(err))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Send a "now playing" notification. Requires the session key to be set.
     */
    suspend fun updateNowPlaying(item: MusicItem): Boolean = withContext(Dispatchers.IO) {
        val key = sessionKey
        if (key.isNullOrBlank()) return@withContext false
        if (item.title.isBlank() || item.artist.isBlank()) return@withContext false
        try {
            val params = linkedMapOf(
                "method" to "track.updateNowPlaying",
                "artist" to item.artist,
                "track" to item.title,
                "api_key" to API_KEY,
                "sk" to key
            )
            if (item.duration > 0) {
                params["duration"] = (item.duration / 1000).toString()
            }
            params["api_sig"] = buildSignature(params)
            val response = post(params)
            response.contains("status=\"ok\"")
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Scrobble a track (count it toward the user's history). Requires the session
     * key to be set. Uses the current time as the playback start timestamp.
     */
    suspend fun scrobble(item: MusicItem): Boolean = withContext(Dispatchers.IO) {
        val key = sessionKey
        if (key.isNullOrBlank()) return@withContext false
        if (item.title.isBlank() || item.artist.isBlank()) return@withContext false
        try {
            val params = linkedMapOf(
                "method" to "track.scrobble",
                "artist" to item.artist,
                "track" to item.title,
                "timestamp" to (System.currentTimeMillis() / 1000).toString(),
                "api_key" to API_KEY,
                "sk" to key
            )
            if (item.duration > 0) {
                params["duration"] = (item.duration / 1000).toString()
            }
            params["api_sig"] = buildSignature(params)
            val response = post(params)
            response.contains("status=\"ok\"")
        } catch (_: Exception) {
            false
        }
    }

    // --- internal helpers ---

    /**
     * Build the api_sig: concatenate (key + value) for every param sorted
     * alphabetically by key (no separators, no encoding), then append the secret,
     * then md5 the whole string.
     */
    private fun buildSignature(params: Map<String, String>): String {
        val sb = StringBuilder()
        params.toSortedMap().forEach { (k, v) -> sb.append(k).append(v) }
        sb.append(SECRET)
        return md5(sb.toString())
    }

    private fun md5(input: String): String {
        val bytes = MessageDigest.getInstance("MD5").digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private suspend fun post(params: Map<String, String>): String = withContext(Dispatchers.IO) {
        val formBody = FormBody.Builder()
        params.forEach { (k, v) -> formBody.add(k, v) }
        val request = Request.Builder()
            .url(ROOT)
            .post(formBody.build())
            .build()
        client.newCall(request).execute().use { response ->
            response.body?.string() ?: ""
        }
    }

    private fun tagValue(xml: String, tag: String): String? {
        val regex = "<$tag>(.*?)</$tag>".toRegex(RegexOption.DOT_MATCHES_ALL)
        return regex.find(xml)?.groupValues?.get(1)?.trim()?.ifBlank { null }
    }
}
