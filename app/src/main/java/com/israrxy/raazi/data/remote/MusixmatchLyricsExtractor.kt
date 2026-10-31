package com.israrxy.raazi.data.remote

import android.content.Context
import android.util.Log
import com.israrxy.raazi.data.lyrics.LyricsLookupCandidate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Base64
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class MusixmatchLyricsExtractor(context: Context? = null) {
    private val prefs = context?.getSharedPreferences("musixmatch_prefs", Context.MODE_PRIVATE)
    private val tokenMutex = Mutex()
    private val cookieStore = ConcurrentHashMap<String, MutableList<Cookie>>()

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .cookieJar(object : CookieJar {
                override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
                    val hostCookies = cookieStore.getOrPut(url.host) { mutableListOf() }
                    synchronized(hostCookies) {
                        hostCookies.removeAll { existing -> cookies.any { it.name == existing.name } }
                        hostCookies.addAll(cookies)
                    }
                }

                override fun loadForRequest(url: HttpUrl): List<Cookie> {
                    val hostCookies = cookieStore[url.host] ?: return emptyList()
                    synchronized(hostCookies) {
                        return hostCookies.toList()
                    }
                }
            })
            .connectTimeout(4, TimeUnit.SECONDS)
            .readTimeout(4, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    private val tag = "MusixmatchExtractor"
    private val baseUrl = "https://apic-desktop.musixmatch.com/ws/1.1"
    private val richTtmlBaseUrl = "https://lyrics-api.boidu.dev/getLyrics"

    suspend fun fetchBestForCandidates(
        candidates: List<LyricsLookupCandidate>,
        durationSeconds: Double
    ): List<Lyrics> = withContext(Dispatchers.IO) {
        val results = LinkedHashMap<String, Lyrics>()
        for (candidate in candidates.take(3)) {
            if (candidate.trackName.isBlank()) continue

            // 1. Primary RichSync/TTML extraction (word-by-word & line-by-line synced)
            fetchRichTtmlLyrics(candidate, durationSeconds)?.let { lyrics ->
                results.putIfAbsent("${lyrics.id}_${lyrics.source}", lyrics)
                if (!lyrics.syncedLyrics.isNullOrBlank()) {
                    return@withContext results.values.toList()
                }
            }

            // 2. Direct signed Musixmatch desktop API extraction
            fetchMacroLyrics(candidate, durationSeconds)?.let { lyrics ->
                results.putIfAbsent("${lyrics.id}_${lyrics.source}", lyrics)
                if (!lyrics.syncedLyrics.isNullOrBlank()) {
                    return@withContext results.values.toList()
                }
            }

            searchTrackCandidates(candidate, durationSeconds).forEach { lyrics ->
                results.putIfAbsent("${lyrics.id}_${lyrics.source}", lyrics)
            }

            if (results.values.any { !it.syncedLyrics.isNullOrBlank() }) {
                break
            }
        }
        results.values.toList()
    }

    suspend fun searchTracks(
        trackName: String,
        artistName: String,
        durationSeconds: Double
    ): List<Lyrics> = withContext(Dispatchers.IO) {
        val results = LinkedHashMap<String, Lyrics>()
        val primaryCandidate = LyricsLookupCandidate(trackName = trackName, artistName = artistName)
        fetchRichTtmlLyrics(primaryCandidate, durationSeconds)?.let {
            results["${it.id}_${it.source}"] = it
        }
        fetchMacroLyrics(primaryCandidate, durationSeconds)?.let {
            results.putIfAbsent("${it.id}_${it.source}", it)
        }
        searchTrackCandidates(primaryCandidate, durationSeconds).forEach {
            results.putIfAbsent("${it.id}_${it.source}", it)
        }
        results.values.toList()
    }

    internal fun fetchRichTtmlLyrics(
        candidate: LyricsLookupCandidate,
        durationSeconds: Double
    ): Lyrics? {
        return runCatching {
            val encodedSong = URLEncoder.encode(candidate.trackName.trim(), "UTF-8")
            val encodedArtist = URLEncoder.encode(candidate.artistName.trim(), "UTF-8")
            val urlBuilder = StringBuilder("$richTtmlBaseUrl?s=$encodedSong")
            if (encodedArtist.isNotBlank()) {
                urlBuilder.append("&a=$encodedArtist")
            }
            if (durationSeconds > 0) {
                urlBuilder.append("&d=${durationSeconds.toInt()}")
            }
            val rawBody = executeHttpGet(urlBuilder.toString()) ?: return@runCatching null
            MusixmatchTtmlParser.parseTtmlResponseToLyrics(
                rawJson = rawBody,
                trackName = candidate.trackName,
                artistName = candidate.artistName,
                fallbackDurationSeconds = durationSeconds
            )
        }.onFailure {
            runCatching { Log.d(tag, "RichSync TTML fetch failed for ${candidate.trackName}: ${it.message}") }
        }.getOrNull()
    }

    private fun executeHttpGet(urlStr: String): String? {
        return runCatching {
            val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                connectTimeout = 4000
                readTimeout = 4000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", DESKTOP_USER_AGENT)
                setRequestProperty("Accept", "application/json")
            }
            if (conn.responseCode in 200..299) {
                conn.inputStream.bufferedReader().use { it.readText() }.takeIf { it.isNotBlank() }
            } else null
        }.recoverCatching {
            val request = Request.Builder()
                .url(urlStr)
                .header("User-Agent", DESKTOP_USER_AGENT)
                .header("Accept", "application/json")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                response.body?.string()?.takeIf { it.isNotBlank() }
            }
        }.getOrNull()
    }

    private suspend fun getOrRefreshUserToken(forceRefresh: Boolean = false): String? {
        return tokenMutex.withLock {
            val savedToken = prefs?.getString(KEY_USER_TOKEN, null)?.takeIf(::isValidMxmToken)
            val savedTime = prefs?.getLong(KEY_TOKEN_TIMESTAMP, 0L) ?: 0L
            val now = System.currentTimeMillis()
            if (!forceRefresh && savedToken != null && (now - savedTime) < TOKEN_TTL_MS) {
                return@withLock savedToken
            }

            val baseTokenUrl = "$baseUrl/token.get?user_language=en&app_id=web-desktop-app-v1.0&format=json&t=$now"
            val signedUrl = signMxmUrl(baseTokenUrl)
            val token = runCatching {
                val request = Request.Builder()
                    .url(signedUrl)
                    .header("User-Agent", DESKTOP_USER_AGENT)
                    .header("Cookie", "x-mxm-token-guid=${UUID.randomUUID()}")
                    .build()
                client.newCall(request).execute().use { response ->
                    val body = response.body?.string() ?: return@use null
                    val json = JSONObject(body)
                    val message = json.optJSONObject("message") ?: return@use null
                    val statusCode = message.optJSONObject("header")?.optInt("status_code") ?: 0
                    if (statusCode != 200) return@use null
                    val newToken = message.optJSONObject("body")?.optString("user_token")
                    newToken?.takeIf(::isValidMxmToken)
                }
            }.getOrNull()

            if (!token.isNullOrBlank()) {
                prefs?.edit()
                    ?.putString(KEY_USER_TOKEN, token)
                    ?.putLong(KEY_TOKEN_TIMESTAMP, now)
                    ?.apply()
                return@withLock token
            }
            savedToken
        }
    }

    private fun isValidMxmToken(token: String?): Boolean {
        if (token.isNullOrBlank() || token.length < 16) return false
        if (token == "UpgradeOnlyUpgradeOnlyUpgradeOnlyUpgradeOnly") return false
        if (token.all { it == '0' }) return false
        return true
    }

    private fun signMxmUrl(unsignedUrl: String): String {
        return runCatching {
            val sdf = SimpleDateFormat("yyyyMMdd", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            val dateStr = sdf.format(Date())
            val mac = Mac.getInstance("HmacSHA1")
            mac.init(SecretKeySpec(MXM_DESKTOP_SECRET.toByteArray(Charsets.UTF_8), "HmacSHA1"))
            val rawSig = mac.doFinal((unsignedUrl + dateStr).toByteArray(Charsets.UTF_8))
            val encodedSig = URLEncoder.encode(Base64.getUrlEncoder().encodeToString(rawSig), "UTF-8")
            "$unsignedUrl&signature=$encodedSig&signature_protocol=sha1"
        }.getOrDefault(unsignedUrl)
    }

    private suspend fun executeMxmCall(method: String, params: Map<String, String>): JSONObject? {
        var token = getOrRefreshUserToken(forceRefresh = false)
        repeat(2) { attempt ->
            val queryParams = buildList {
                add("format=json")
                add("app_id=web-desktop-app-v1.0")
                if (!token.isNullOrBlank()) {
                    add("usertoken=${URLEncoder.encode(token, "UTF-8")}")
                }
                params.forEach { (k, v) ->
                    if (v.isNotBlank()) {
                        add("$k=${URLEncoder.encode(v, "UTF-8")}")
                    }
                }
            }.joinToString("&")

            val unsignedUrl = "$baseUrl/$method?$queryParams"
            val url = signMxmUrl(unsignedUrl)
            val result = runCatching {
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", DESKTOP_USER_AGENT)
                    .header("Cookie", "x-mxm-user-id=undefined; x-mxm-token-guid=${UUID.randomUUID()}")
                    .build()

                client.newCall(request).execute().use { response ->
                    val raw = response.body?.string() ?: return@use null
                    val json = JSONObject(raw)
                    val message = json.optJSONObject("message") ?: return@use null
                    val header = message.optJSONObject("header")
                    val statusCode = header?.optInt("status_code") ?: 0
                    val hint = header?.optString("hint").orEmpty()
                    if (statusCode == 401 || statusCode == 402 || hint.contains("renew", ignoreCase = true) || hint.contains("captcha", ignoreCase = true)) {
                        if (attempt == 0) {
                            token = getOrRefreshUserToken(forceRefresh = true) ?: return@use null
                            return@use JSONObject().put("_retry", true)
                        }
                        return@use null
                    }
                    if (statusCode != 200) return@use null
                    message.optJSONObject("body")
                }
            }.getOrNull()

            if (result?.optBoolean("_retry") == true) {
                return@repeat
            }
            return result
        }
        return null
    }

    private fun isDecoyTrack(trackId: Int, matchedTrackName: String, requestedTrackName: String): Boolean {
        if (trackId == DECOY_NOKIA_TRACK_ID) return true
        if (matchedTrackName.equals("NOKIA", ignoreCase = true) &&
            !requestedTrackName.contains("nokia", ignoreCase = true)
        ) {
            return true
        }
        return false
    }

    private suspend fun fetchMacroLyrics(
        candidate: LyricsLookupCandidate,
        durationSeconds: Double
    ): Lyrics? {
        val params = buildMap {
            put("q_track", candidate.trackName)
            if (candidate.artistName.isNotBlank()) {
                put("q_artist", candidate.artistName)
            }
            put("namespace", "lyrics_richsynched")
            put("optional_calls", "track.richsync")
            if (durationSeconds > 0) {
                put("q_duration", durationSeconds.toInt().toString())
                put("f_subtitle_length", durationSeconds.toInt().toString())
            }
        }

        val body = executeMxmCall("macro.subtitles.get", params) ?: return null
        val macroCalls = body.optJSONObject("macro_calls") ?: return null

        val trackObj = macroCalls.optJSONObject("matcher.track.get")
            ?.optJSONObject("message")
            ?.optJSONObject("body")
            ?.optJSONObject("track")

        val trackId = trackObj?.optInt("track_id") ?: 0
        val commontrackId = trackObj?.optInt("commontrack_id") ?: 0
        val matchedTrackName = trackObj?.optString("track_name")?.takeIf { it.isNotBlank() } ?: candidate.trackName
        val matchedArtistName = trackObj?.optString("artist_name")?.takeIf { it.isNotBlank() } ?: candidate.artistName
        if (isDecoyTrack(trackId, matchedTrackName, candidate.trackName)) {
            return null
        }

        val matchedDuration = trackObj?.optDouble("track_length")?.takeIf { it > 0 } ?: durationSeconds

        val richSyncBody = macroCalls.optJSONObject("track.richsync.get")
            ?.optJSONObject("message")
            ?.optJSONObject("body")
            ?.optJSONObject("richsync")
            ?.optString("richsync_body")
            ?.takeIf { it.isNotBlank() }
            ?: if (trackId > 0) fetchRichSyncBody(trackId) else null

        val wordSyncedLrc = richSyncBody?.let(MusixmatchTtmlParser::convertRichSyncToEnhancedLrc)

        val subtitleBody = macroCalls.optJSONObject("track.subtitles.get")
            ?.optJSONObject("message")
            ?.optJSONObject("body")
            ?.optJSONArray("subtitle_list")
            ?.optJSONObject(0)
            ?.optJSONObject("subtitle")
            ?.optString("subtitle_body")
            ?.takeIf { it.isNotBlank() }

        val lyricsObj = macroCalls.optJSONObject("track.lyrics.get")
            ?.optJSONObject("message")
            ?.optJSONObject("body")
            ?.optJSONObject("lyrics")

        val plainLyrics = lyricsObj
            ?.optString("lyrics_body")
            ?.let(MusixmatchTtmlParser::cleanMusixmatchLyricsBody)
            ?.takeIf { it.isNotBlank() }

        val language = lyricsObj?.optString("lyrics_language")?.takeIf { it.isNotBlank() }

        val finalSynced = wordSyncedLrc ?: subtitleBody
        val finalPlain = plainLyrics ?: subtitleBody?.let(MusixmatchTtmlParser::stripLrcTimestamps)

        if (finalSynced.isNullOrBlank() && finalPlain.isNullOrBlank()) {
            return null
        }

        return Lyrics(
            id = if (trackId != 0) trackId else if (commontrackId != 0) commontrackId else matchedTrackName.hashCode(),
            trackName = matchedTrackName,
            artistName = matchedArtistName,
            plainLyrics = finalPlain,
            syncedLyrics = finalSynced,
            duration = matchedDuration,
            language = language,
            source = "Musixmatch"
        )
    }

    private suspend fun searchTrackCandidates(
        candidate: LyricsLookupCandidate,
        durationSeconds: Double
    ): List<Lyrics> {
        val params = buildMap {
            put("q_track", candidate.trackName)
            if (candidate.artistName.isNotBlank()) {
                put("q_artist", candidate.artistName)
            }
            put("page_size", "4")
            put("page", "1")
            put("s_track_rating", "desc")
        }

        val body = executeMxmCall("track.search", params) ?: return emptyList()
        val trackList = body.optJSONArray("track_list") ?: return emptyList()
        val found = mutableListOf<Lyrics>()

        for (i in 0 until minOf(trackList.length(), 3)) {
            val track = trackList.optJSONObject(i)?.optJSONObject("track") ?: continue
            val trackId = track.optInt("track_id")
            val commontrackId = track.optInt("commontrack_id")
            val title = track.optString("track_name").ifBlank { candidate.trackName }
            if (isDecoyTrack(trackId, title, candidate.trackName)) continue

            val hasRichsync = track.optInt("has_richsync") == 1
            val hasSubtitles = track.optInt("has_subtitles") == 1
            val hasLyrics = track.optInt("has_lyrics") == 1
            if (!hasRichsync && !hasSubtitles && !hasLyrics) continue

            val artist = track.optString("artist_name").ifBlank { candidate.artistName }
            val trackDuration = track.optDouble("track_length").takeIf { it > 0 } ?: durationSeconds

            val richSyncLrc = if (hasRichsync && trackId > 0) {
                fetchRichSyncBody(trackId)?.let(MusixmatchTtmlParser::convertRichSyncToEnhancedLrc)
            } else null

            val subtitleLrc = if (richSyncLrc == null && hasSubtitles && commontrackId > 0) {
                fetchSubtitleBody(commontrackId)
            } else null

            val plainBody = if (hasLyrics && commontrackId > 0) {
                fetchPlainLyricsBody(commontrackId)
            } else null

            val synced = richSyncLrc ?: subtitleLrc
            val plain = plainBody ?: synced?.let(MusixmatchTtmlParser::stripLrcTimestamps)
            if (!synced.isNullOrBlank() || !plain.isNullOrBlank()) {
                found += Lyrics(
                    id = trackId.takeIf { it != 0 } ?: commontrackId,
                    trackName = title,
                    artistName = artist,
                    plainLyrics = plain,
                    syncedLyrics = synced,
                    duration = trackDuration,
                    language = null,
                    source = "Musixmatch"
                )
            }
        }
        return found
    }

    private suspend fun fetchRichSyncBody(trackId: Int): String? {
        val body = executeMxmCall("track.richsync.get", mapOf("track_id" to trackId.toString())) ?: return null
        return body.optJSONObject("richsync")?.optString("richsync_body")?.takeIf { it.isNotBlank() }
    }

    private suspend fun fetchSubtitleBody(commontrackId: Int): String? {
        val body = executeMxmCall(
            "track.subtitle.get",
            mapOf("commontrack_id" to commontrackId.toString(), "subtitle_format" to "lrc")
        ) ?: return null
        return body.optJSONObject("subtitle")?.optString("subtitle_body")?.takeIf { it.isNotBlank() }
    }

    private suspend fun fetchPlainLyricsBody(commontrackId: Int): String? {
        val body = executeMxmCall("track.lyrics.get", mapOf("commontrack_id" to commontrackId.toString())) ?: return null
        val raw = body.optJSONObject("lyrics")?.optString("lyrics_body") ?: return null
        return MusixmatchTtmlParser.cleanMusixmatchLyricsBody(raw).takeIf { it.isNotBlank() }
    }

    companion object {
        private const val KEY_USER_TOKEN = "mxm_user_token"
        private const val KEY_TOKEN_TIMESTAMP = "mxm_token_ts"
        private const val TOKEN_TTL_MS = 1000L * 60L * 45L
        private const val DECOY_NOKIA_TRACK_ID = 226291677
        private const val MXM_DESKTOP_SECRET = "IEJ5E8XFaHQvIQNfs7IC"
        private const val DESKTOP_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Musixmatch/3.19.4 Chrome/120.0.6099.227 Electron/28.2.0 Safari/537.36"
    }
}
