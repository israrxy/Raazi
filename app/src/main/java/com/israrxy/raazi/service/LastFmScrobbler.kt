package com.israrxy.raazi.service

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import com.israrxy.raazi.model.MusicItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.security.MessageDigest

/**
 * Last.fm scrobbling and web auth helper.
 *
 * Implements the Last.fm API v2.0 over plain HTTP POST to the audioscrobbler root.
 * Supports:
 * - 1-Tap Web Authorization via Chrome Custom Tabs (callback: raazi://lastfm-callback)
 * - Session exchange (auth.getSession)
 * - Now playing notifications (track.updateNowPlaying)
 * - Scrobbling (track.scrobble)
 * - Loved tracks sync (track.love / track.unlove)
 */
object LastFmScrobbler {

    const val API_KEY = "f0f3602c10d8324dcc019a53a08735c5"
    private const val SECRET = "4862ead2d36d356cca5d5103c12a7336"
    private const val ROOT = "https://ws.audioscrobbler.com/2.0/"
    const val CALLBACK_URL = "raazi://lastfm-callback"

    // Session key is injected by the ViewModel (observed from SettingsDataStore).
    // Held here so the call sites can stay simple (item-only signatures).
    var sessionKey: String? = null

    private val client by lazy { OkHttpClient.Builder().build() }

    /**
     * Builds the authorization URL for user approval in browser/Custom Tab.
     */
    fun getAuthUrl(): String {
        return "https://www.last.fm/api/auth/?api_key=$API_KEY&cb=$CALLBACK_URL"
    }

    /**
     * Launch Chrome Custom Tab (or fallback browser) to allow user to connect Last.fm.
     */
    fun launchWebAuth(context: Context) {
        val uri = Uri.parse(getAuthUrl())
        try {
            val customTabsIntent = CustomTabsIntent.Builder()
                .setShowTitle(true)
                .build()
            customTabsIntent.launchUrl(context, uri)
        } catch (_: Exception) {
            val fallback = Intent(Intent.ACTION_VIEW, uri).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(fallback)
        }
    }

    /**
     * Exchange a temporary request token (from raazi://lastfm-callback?token=...)
     * for a permanent session key via auth.getSession.
     */
    suspend fun getSessionFromToken(token: String): Result<Pair<String, String>> = withContext(Dispatchers.IO) {
        try {
            val params = linkedMapOf(
                "method" to "auth.getSession",
                "api_key" to API_KEY,
                "token" to token
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
     * Legacy authenticate with username + password and return the (sessionKey, name) pair.
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

    /**
     * Love a track on Last.fm.
     */
    suspend fun loveTrack(item: MusicItem): Boolean = withContext(Dispatchers.IO) {
        val key = sessionKey
        if (key.isNullOrBlank() || item.title.isBlank() || item.artist.isBlank()) return@withContext false
        try {
            val params = linkedMapOf(
                "method" to "track.love",
                "artist" to item.artist,
                "track" to item.title,
                "api_key" to API_KEY,
                "sk" to key
            )
            params["api_sig"] = buildSignature(params)
            val response = post(params)
            response.contains("status=\"ok\"")
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Unlove a track on Last.fm.
     */
    suspend fun unloveTrack(item: MusicItem): Boolean = withContext(Dispatchers.IO) {
        val key = sessionKey
        if (key.isNullOrBlank() || item.title.isBlank() || item.artist.isBlank()) return@withContext false
        try {
            val params = linkedMapOf(
                "method" to "track.unlove",
                "artist" to item.artist,
                "track" to item.title,
                "api_key" to API_KEY,
                "sk" to key
            )
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
