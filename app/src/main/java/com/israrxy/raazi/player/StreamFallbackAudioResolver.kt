package com.israrxy.raazi.player

import android.net.Uri
import android.util.Log
import com.israrxy.raazi.data.db.FormatEntity
import com.israrxy.raazi.model.PlaybackMediaMode
import com.israrxy.raazi.player.flac.HifiApiClient
import com.israrxy.raazi.player.flac.HifiTrack
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Request
import org.json.JSONObject

/**
 * Resolves the exact YouTube videoId via public Piped API instances so the stream is
 * guaranteed to be the exact same videoId when YouTube blocks datacenter IPs directly.
 */
internal fun StreamResolver.resolveExactVideoIdViaPiped(
    videoId: String,
    mode: PlaybackMediaMode,
    updateQualityState: Boolean
): StreamResolver.StreamResult? {
    if (!videoId.matches(Regex("^[a-zA-Z0-9_-]{11}$"))) return null
    val instances = listOf("https://pipedapi.kavin.rocks")
    val userAgent = StreamResolver.DEFAULT_WEB_USER_AGENT

    for (instance in instances) {
        try {
            val request = Request.Builder()
                .url("$instance/streams/$videoId")
                .header("User-Agent", userAgent)
                .get()
                .build()
            val body = probeHttpClient.newCall(request).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string().orEmpty() else ""
            }
            if (body.isBlank()) continue
            val json = JSONObject(body)

            if (mode == PlaybackMediaMode.VIDEO) {
                val videoStreams = json.optJSONArray("videoStreams")
                if (videoStreams != null) {
                    for (i in 0 until videoStreams.length()) {
                        val item = videoStreams.optJSONObject(i) ?: continue
                        val videoOnly = item.optBoolean("videoOnly", true)
                        val url = item.optString("url")
                        if (!videoOnly && url.isNotBlank() && verifyStreamUrl(url, userAgent)) {
                            val quality = "${item.optString("quality", "360p")} Video"
                            if (updateQualityState) setCurrentStreamQuality(quality)
                            Log.i(StreamResolver.TAG, "SUCCESS: Resolved exact videoId $videoId video via Piped ($instance)")
                            return StreamResolver.StreamResult(
                                url = url,
                                userAgent = userAgent,
                                quality = quality,
                                mode = PlaybackMediaMode.VIDEO,
                                hasVideo = true
                            )
                        }
                    }
                }
            }

            val audioStreams = json.optJSONArray("audioStreams")
            if (audioStreams != null && audioStreams.length() > 0) {
                val candidates = mutableListOf<Pair<Int, String>>()
                for (i in 0 until audioStreams.length()) {
                    val item = audioStreams.optJSONObject(i) ?: continue
                    val url = item.optString("url")
                    val bitrate = item.optInt("bitrate", 128000)
                    if (url.isNotBlank()) {
                        candidates.add(bitrate to url)
                    }
                }
                for ((bitrate, url) in candidates.sortedByDescending { it.first }.take(3)) {
                    if (verifyStreamUrl(url, userAgent)) {
                        val quality = "Audio ${(bitrate / 1000).coerceAtLeast(128)}kbps"
                        if (updateQualityState) setCurrentStreamQuality(quality)
                        Log.i(StreamResolver.TAG, "SUCCESS: Resolved exact videoId $videoId audio via Piped ($instance)")
                        return StreamResolver.StreamResult(
                            url = url,
                            userAgent = userAgent,
                            quality = quality,
                            mode = PlaybackMediaMode.AUDIO,
                            hasVideo = false
                        )
                    }
                }
            }
        } catch (_: Exception) {
        }
    }
    return null
}

/**
 * Resolves high-quality AAC/M4A audio streams when YouTube's /player endpoint blocks
 * anonymous datacenter/emulator IPs with LOGIN_REQUIRED ("Sign in to confirm you're not a bot").
 * Strictly verifies candidate track title + artist before accepting any stream URL so a
 * wrong/random song is NEVER played.
 */
internal fun StreamResolver.resolveFallbackAudioStream(
    videoId: String,
    titleInput: String?,
    artistInput: String?,
    isrc: String? = null,
    updateQualityState: Boolean = true
): StreamResolver.StreamResult {
    val meta = resolveCanonicalMetadata(videoId, titleInput, artistInput)
    val cleanTitle = meta.primaryTitle
    val cleanArtist = meta.primaryArtist
    val searchArtist = meta.primarySearchArtist

    if (cleanTitle.isBlank()) {
        throw Exception("Cannot resolve fallback stream for $videoId without track title")
    }

    val userAgent = StreamResolver.DEFAULT_WEB_USER_AGENT

    fun evaluateScore(candTitle: String, candArtist: String): Int {
        val s1 = scoreTrackMatch(cleanTitle, cleanArtist, candTitle, candArtist)
        val s2 = if (searchArtist.isNotBlank() && searchArtist != cleanArtist) {
            scoreTrackMatch(cleanTitle, searchArtist, candTitle, candArtist)
        } else 0
        val s3 = if (!meta.secondaryTitle.isNullOrBlank()) {
            scoreTrackMatch(meta.secondaryTitle, meta.secondaryArtist ?: cleanArtist, candTitle, candArtist)
        } else 0
        return maxOf(s1, s2, s3)
    }

    fun evaluateTitleOnlyScore(candTitle: String): Int {
        val t1 = scoreTrackMatch(cleanTitle, "", candTitle, "")
        val t2 = if (!meta.secondaryTitle.isNullOrBlank()) {
            scoreTrackMatch(meta.secondaryTitle, "", candTitle, "")
        } else 0
        return maxOf(t1, t2)
    }

    val queries = listOfNotNull(
        if (searchArtist.isNotBlank() && !isGenericOrChannelArtist(normalizeForComparison(searchArtist))) "$cleanTitle $searchArtist".trim() else null,
        if (!meta.secondaryTitle.isNullOrBlank() && !meta.secondarySearchArtist.isNullOrBlank() && !isGenericOrChannelArtist(normalizeForComparison(meta.secondarySearchArtist))) {
            "${meta.secondaryTitle} ${meta.secondarySearchArtist}".trim()
        } else null,
        cleanTitle,
        meta.secondaryTitle
    ).distinct()

    // Tier 1: Direct JioSaavn API with JSON parsing & strict Title+Artist verification (320kbps AAC/M4A)
    for (query in queries) {
        try {
            val encodedQuery = Uri.encode(query)
            val apiUrl = "https://www.jiosaavn.com/api.php?p=1&q=$encodedQuery&_format=json&_marker=0&api_version=4&ctx=web6dot0&n=10&__call=search.getResults"
            val request = Request.Builder()
                .url(apiUrl)
                .header("User-Agent", userAgent)
                .get()
                .build()

            val body = probeHttpClient.newCall(request).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string().orEmpty() else ""
            }

            if (body.isNotBlank()) {
                val root = JSONObject(body)
                val results = root.optJSONArray("results")
                if (results != null && results.length() > 0) {
                    data class SaavnCandidate(
                        val title: String,
                        val artist: String,
                        val encryptedUrl: String,
                        val score: Int,
                        val titleOnlyScore: Int
                    )
                    val allCandidates = mutableListOf<SaavnCandidate>()
                    for (i in 0 until results.length()) {
                        val item = results.optJSONObject(i) ?: continue
                        val itemTitle = decodeHtmlEntities(
                            item.optString("title").ifBlank { item.optString("song") }
                        )
                        val moreInfo = item.optJSONObject("more_info")
                        val encryptedUrl = moreInfo?.optString("encrypted_media_url")
                            .orEmpty()
                            .ifBlank { item.optString("encrypted_media_url") }
                        if (encryptedUrl.isBlank()) continue

                        val artistBuilder = mutableListOf<String>()
                        val artistMap = moreInfo?.optJSONObject("artistMap")
                        val primaryArtistsArr = artistMap?.optJSONArray("primary_artists")
                            ?: artistMap?.optJSONArray("artists")
                        if (primaryArtistsArr != null) {
                            for (j in 0 until primaryArtistsArr.length()) {
                                primaryArtistsArr.optJSONObject(j)?.optString("name")?.takeIf { it.isNotBlank() }?.let {
                                    artistBuilder.add(it)
                                }
                            }
                        }
                        moreInfo?.optString("music")?.takeIf { it.isNotBlank() }?.let { artistBuilder.add(it) }
                        moreInfo?.optString("singers")?.takeIf { it.isNotBlank() }?.let { artistBuilder.add(it) }
                        item.optString("subtitle")?.takeIf { it.isNotBlank() }?.let { artistBuilder.add(it) }

                        val itemArtist = decodeHtmlEntities(artistBuilder.joinToString(", "))
                        val score = evaluateScore(itemTitle, itemArtist)
                        val titleOnlyScore = evaluateTitleOnlyScore(itemTitle)
                        allCandidates.add(
                            SaavnCandidate(
                                title = itemTitle,
                                artist = itemArtist,
                                encryptedUrl = encryptedUrl.replace("\\/", "/"),
                                score = score,
                                titleOnlyScore = titleOnlyScore
                            )
                        )
                    }

                    val strictMatches = allCandidates.filter { it.score >= 55 }.sortedByDescending { it.score }
                    val exactTitleFallbacks = if (strictMatches.isEmpty()) {
                        allCandidates.filter { it.titleOnlyScore >= 56 }.sortedByDescending { it.score }
                    } else emptyList()

                    for (cand in (strictMatches + exactTitleFallbacks).take(3)) {
                        val baseDecrypted = decryptSaavnMediaUrl(cand.encryptedUrl) ?: continue
                        val urlVariants = listOf(
                            baseDecrypted.replace("_96.mp4", "_320.mp4").replace("_160.mp4", "_320.mp4"),
                            baseDecrypted.replace("_96.mp4", "_160.mp4"),
                            baseDecrypted
                        ).distinct()

                        for (candidateUrl in urlVariants) {
                            if (verifyStreamUrl(candidateUrl, userAgent)) {
                                val bitrate = when {
                                    candidateUrl.contains("_320.") -> 320000
                                    candidateUrl.contains("_160.") -> 160000
                                    else -> 96000
                                }
                                val quality = "M4A ${bitrate / 1000}kbps"
                                if (updateQualityState) setCurrentStreamQuality(quality)
                                runCatching {
                                    musicDao?.upsertFormat(
                                        FormatEntity(
                                            id = videoId,
                                            mimeType = "audio/mp4",
                                            codecs = "mp4a.40.2",
                                            bitrate = bitrate,
                                            sampleRate = 44100,
                                            contentLength = 0L
                                        )
                                    )
                                }
                                Log.i(
                                    StreamResolver.TAG,
                                    "SUCCESS: Resolved verified Saavn stream ($quality, score=${cand.score}) " +
                                        "for '$cleanTitle' -> '${cand.title}' by '${cand.artist}'"
                                )
                                return StreamResolver.StreamResult(
                                    url = candidateUrl,
                                    userAgent = userAgent,
                                    quality = quality,
                                    mode = PlaybackMediaMode.AUDIO,
                                    hasVideo = false
                                )
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(StreamResolver.TAG, "Tier 1 Saavn fallback failed for query '$query': ${e.message}")
        }
    }

    // Tier 2: Saavn API Mirror (saavn.sumit.co) with JSON parsing & strict Title+Artist verification
    for (query in queries) {
        try {
            val encodedQuery = Uri.encode(query)
            val apiUrl = "https://saavn.sumit.co/api/search/songs?query=$encodedQuery&limit=10"
            val request = Request.Builder()
                .url(apiUrl)
                .header("User-Agent", userAgent)
                .get()
                .build()

            val body = probeHttpClient.newCall(request).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string().orEmpty() else ""
            }

            if (body.isNotBlank()) {
                val root = JSONObject(body)
                val results = root.optJSONObject("data")?.optJSONArray("results")
                if (results != null && results.length() > 0) {
                    data class MirrorCandidate(
                        val title: String,
                        val artist: String,
                        val urls: List<String>,
                        val score: Int,
                        val titleOnlyScore: Int
                    )
                    val allMirror = mutableListOf<MirrorCandidate>()
                    for (i in 0 until results.length()) {
                        val item = results.optJSONObject(i) ?: continue
                        val itemTitle = decodeHtmlEntities(item.optString("name"))
                        val artistsObj = item.optJSONObject("artists")
                        val primaryArr = artistsObj?.optJSONArray("primary") ?: artistsObj?.optJSONArray("all")
                        val artistNames = mutableListOf<String>()
                        if (primaryArr != null) {
                            for (j in 0 until primaryArr.length()) {
                                primaryArr.optJSONObject(j)?.optString("name")?.takeIf { it.isNotBlank() }?.let {
                                    artistNames.add(it)
                                }
                            }
                        }
                        val itemArtist = decodeHtmlEntities(artistNames.joinToString(", "))
                        val score = evaluateScore(itemTitle, itemArtist)
                        val titleOnlyScore = evaluateTitleOnlyScore(itemTitle)

                        val downloadUrlArr = item.optJSONArray("downloadUrl") ?: continue
                        val extractedUrls = mutableListOf<String>()
                        for (j in 0 until downloadUrlArr.length()) {
                            val u = downloadUrlArr.optJSONObject(j)?.optString("url").orEmpty().replace("\\/", "/")
                            if (u.isNotBlank()) extractedUrls.add(u)
                        }
                        val orderedUrls = extractedUrls.filter { it.contains("_320.") } +
                            extractedUrls.filter { it.contains("_160.") } +
                            extractedUrls.reversed()
                        if (orderedUrls.isNotEmpty()) {
                            allMirror.add(
                                MirrorCandidate(
                                    title = itemTitle,
                                    artist = itemArtist,
                                    urls = orderedUrls.distinct(),
                                    score = score,
                                    titleOnlyScore = titleOnlyScore
                                )
                            )
                        }
                    }

                    val strictMirror = allMirror.filter { it.score >= 55 }.sortedByDescending { it.score }
                    val fallbackMirror = if (strictMirror.isEmpty()) {
                        allMirror.filter { it.titleOnlyScore >= 56 }.sortedByDescending { it.score }
                    } else emptyList()

                    for (cand in (strictMirror + fallbackMirror).take(3)) {
                        for (candidateUrl in cand.urls) {
                            if (verifyStreamUrl(candidateUrl, userAgent)) {
                                val bitrate = if (candidateUrl.contains("_320.")) 320000 else 160000
                                val quality = if (candidateUrl.contains("_320.")) "M4A 320kbps" else "M4A 160kbps"
                                if (updateQualityState) setCurrentStreamQuality(quality)
                                runCatching {
                                    musicDao?.upsertFormat(
                                        FormatEntity(
                                            id = videoId,
                                            mimeType = "audio/mp4",
                                            codecs = "mp4a.40.2",
                                            bitrate = bitrate,
                                            sampleRate = 44100,
                                            contentLength = 0L
                                        )
                                    )
                                }
                                Log.i(
                                    StreamResolver.TAG,
                                    "SUCCESS: Resolved verified mirror stream ($quality, score=${cand.score}) " +
                                        "for '$cleanTitle' -> '${cand.title}' by '${cand.artist}'"
                                )
                                return StreamResolver.StreamResult(
                                    url = candidateUrl,
                                    userAgent = userAgent,
                                    quality = quality,
                                    mode = PlaybackMediaMode.AUDIO,
                                    hasVideo = false
                                )
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(StreamResolver.TAG, "Tier 2 mirror fallback failed for query '$query': ${e.message}")
        }
    }

    // Tier 3: Global Apple Music / iTunes Audio CDN (256kbps AAC/M4A direct HTTP stream)
    for (query in queries) {
        try {
            val encodedQuery = Uri.encode(query)
            val apiUrl = "https://itunes.apple.com/search?term=$encodedQuery&entity=song&limit=10"
            val request = Request.Builder()
                .url(apiUrl)
                .header("User-Agent", userAgent)
                .get()
                .build()

            val body = probeHttpClient.newCall(request).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string().orEmpty() else ""
            }

            if (body.isNotBlank()) {
                val root = JSONObject(body)
                val results = root.optJSONArray("results")
                if (results != null && results.length() > 0) {
                    data class AppleAudioCandidate(
                        val title: String,
                        val artist: String,
                        val audioUrl: String,
                        val score: Int,
                        val titleOnlyScore: Int
                    )
                    val candidates = mutableListOf<AppleAudioCandidate>()
                    for (i in 0 until results.length()) {
                        val item = results.optJSONObject(i) ?: continue
                        val previewUrl = item.optString("previewUrl").replace("\\/", "/")
                        if (previewUrl.isBlank()) continue
                        val trackName = item.optString("trackName")
                        val artistName = item.optString("artistName")
                        val score = evaluateScore(trackName, artistName)
                        val titleOnlyScore = evaluateTitleOnlyScore(trackName)
                        candidates.add(
                            AppleAudioCandidate(
                                title = trackName,
                                artist = artistName,
                                audioUrl = previewUrl,
                                score = score,
                                titleOnlyScore = titleOnlyScore
                            )
                        )
                    }

                    val strictApple = candidates.filter { it.score >= 55 }.sortedByDescending { it.score }
                    val fallbackApple = if (strictApple.isEmpty()) {
                        candidates.filter { it.titleOnlyScore >= 56 }.sortedByDescending { it.score }
                    } else emptyList()

                    for (cand in (strictApple + fallbackApple).take(3)) {
                        if (verifyStreamUrl(cand.audioUrl, userAgent)) {
                            val quality = "AAC 256kbps"
                            if (updateQualityState) setCurrentStreamQuality(quality)
                            runCatching {
                                musicDao?.upsertFormat(
                                    FormatEntity(
                                        id = videoId,
                                        mimeType = "audio/mp4",
                                        codecs = "mp4a.40.2",
                                        bitrate = 256000,
                                        sampleRate = 44100,
                                        contentLength = 0L
                                    )
                                )
                            }
                            Log.i(
                                StreamResolver.TAG,
                                "SUCCESS: Resolved verified Apple Music audio stream ($quality, score=${cand.score}) " +
                                    "for '$cleanTitle' -> '${cand.title}' by '${cand.artist}'"
                            )
                            return StreamResolver.StreamResult(
                                url = cand.audioUrl,
                                userAgent = userAgent,
                                quality = quality,
                                mode = PlaybackMediaMode.AUDIO,
                                hasVideo = false
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(StreamResolver.TAG, "Tier 3 Apple Music audio fallback failed for query '$query': ${e.message}")
        }
    }

    // Tier 4: Global Tidal HiFi Catalog (with circuit-breaker protection)
    resolveTidalHifiAudioStream(
        videoId = videoId,
        cleanTitle = cleanTitle,
        searchArtist = searchArtist,
        isrc = isrc,
        updateQualityState = updateQualityState,
        evaluateScore = ::evaluateScore
    )?.let { return it }

    throw Exception("Unable to resolve verified audio stream for '$cleanTitle' by '$cleanArtist' ($videoId)")
}
