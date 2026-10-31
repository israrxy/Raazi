package com.israrxy.raazi.player

import android.net.Uri
import android.util.Base64
import android.util.Log
import com.israrxy.raazi.data.db.FormatEntity
import com.israrxy.raazi.model.PlaybackMediaMode
import com.israrxy.raazi.model.PlaybackVideoQuality
import com.israrxy.raazi.player.flac.HifiApiClient
import com.israrxy.raazi.player.flac.HifiTrack
import com.zionhuang.innertube.YouTube
import com.zionhuang.innertube.models.WatchEndpoint
import com.zionhuang.innertube.models.YouTubeClient
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Request
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

internal data class CanonicalTrackMetadata(
    val primaryTitle: String,
    val primaryArtist: String,
    val primarySearchArtist: String,
    val secondaryTitle: String? = null,
    val secondaryArtist: String? = null,
    val secondarySearchArtist: String? = null
)

private val genericChannelOrLabelNames = setOf(
    "unknown",
    "unknown artist",
    "artist",
    "various artists",
    "t series",
    "tseries",
    "zee music company",
    "sony music india",
    "sony music",
    "yrf",
    "yash raj films",
    "saregama",
    "saregama music",
    "tips official",
    "speed records",
    "desi melodies",
    "white hill music",
    "eros now",
    "aditya music",
    "think music india",
    "lahari music",
    "ishtar music"
)

internal fun decodeHtmlEntities(input: String): String {
    return input
        .replace("&quot;", "\"")
        .replace("&#039;", "'")
        .replace("&#39;", "'")
        .replace("&apos;", "'")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
}

internal fun cleanMetadataForSearch(raw: String): String {
    return decodeHtmlEntities(raw)
        .replace(Regex("\\([^)]*(official|video|audio|lyric|lyrics|visualizer|hd|hq|4k|remaster|full song|mv|from\\s+)[^)]*\\)", RegexOption.IGNORE_CASE), " ")
        .replace(Regex("\\[[^\\]]*(official|video|audio|lyric|lyrics|visualizer|hd|hq|4k|remaster|full song|mv|from\\s+)[^\\]]*\\]", RegexOption.IGNORE_CASE), " ")
        .replace(Regex("(?i)\\b(official\\s+music\\s+video|official\\s+video|official\\s+audio|lyric\\s+video|full\\s+video|full\\s+song|video\\s+song|audio\\s+song|lyrical\\s+video)\\b"), " ")
        .replace(Regex("(?i)\\s+-\\s+topic$"), "")
        .replace(Regex("[-|•]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
}

internal fun normalizeForComparison(raw: String): String {
    return cleanMetadataForSearch(raw)
        .lowercase()
        .replace(Regex("\\([^)]*\\)"), " ")
        .replace(Regex("\\[[^\\]]*\\]"), " ")
        .replace(Regex("(?i)\\b(feat|ft|featuring)\\b.*$"), " ")
        .replace(Regex("[^a-z0-9\\s]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
}

internal fun isGenericOrChannelArtist(normArtist: String): Boolean {
    if (normArtist.isBlank()) return true
    if (normArtist in genericChannelOrLabelNames) return true
    if (normArtist.endsWith("vevo") || normArtist.endsWith("topic") || normArtist.endsWith("records") || normArtist.endsWith("music") || normArtist.endsWith("official")) {
        return true
    }
    return false
}

/**
 * Strictly scores whether a candidate track from a fallback CDN (Tidal HiFi, JioSaavn, iTunes)
 * actually matches the requested track's title and artist.
 * Returns a score in 0..100. Candidates with score < 55 MUST be rejected so a wrong song is never played.
 */
internal fun scoreTrackMatch(
    targetTitle: String,
    targetArtist: String,
    candidateTitle: String,
    candidateArtist: String
): Int {
    val normTargetTitle = normalizeForComparison(targetTitle)
    val normCandTitle = normalizeForComparison(candidateTitle)
    if (normTargetTitle.isBlank() || normCandTitle.isBlank()) return 0

    val normTargetArtist = normalizeForComparison(targetArtist)
    val normCandArtist = normalizeForComparison(candidateArtist)

    val unwantedVariantTokens = listOf("karaoke", "instrumental", "ringtone", "8d", "nightcore", "slowed", "reverb", "cover")
    for (variant in unwantedVariantTokens) {
        val candHas = candidateTitle.lowercase().contains(variant)
        val targetHas = targetTitle.lowercase().contains(variant)
        if (candHas && !targetHas) {
            return 0
        }
    }

    val targetTitleWords = normTargetTitle.split(" ").filter { it.length >= 2 }
    val candTitleWords = normCandTitle.split(" ").filter { it.length >= 2 }
    if (targetTitleWords.isEmpty() || candTitleWords.isEmpty()) return 0

    val titleIntersection = targetTitleWords.intersect(candTitleWords.toSet())
    val targetRecall = titleIntersection.size.toFloat() / targetTitleWords.size.toFloat()
    val candPrecision = titleIntersection.size.toFloat() / candTitleWords.size.toFloat()

    val titleScore = when {
        normCandTitle == normTargetTitle -> 65
        normTargetTitle.contains(normCandTitle) && normCandTitle.length >= 4 && candTitleWords.size >= 1 && candPrecision >= 0.8f -> {
            if (normTargetTitle.contains(normCandArtist) || targetRecall >= 0.5f) 58 else 42
        }
        normCandTitle.contains(normTargetTitle) && normTargetTitle.length >= 4 && targetRecall >= 0.8f -> 56
        targetRecall >= 0.75f && candPrecision >= 0.6f -> 52
        targetRecall >= 0.6f && candPrecision >= 0.5f && titleIntersection.any { it.length >= 4 } -> 40
        else -> 0
    }

    if (titleScore == 0) return 0

    val targetArtistWords = normTargetArtist
        .split(" ")
        .filter { it.length >= 2 && it !in setOf("the", "and", "feat", "ft", "with", "official", "music", "vevo", "topic") }
    val candArtistWords = normCandArtist
        .split(" ")
        .filter { it.length >= 2 && it !in setOf("the", "and", "feat", "ft", "with") }

    val artistMatchesTitle = candArtistWords.isNotEmpty() && candArtistWords.any { it.length >= 3 && normTargetTitle.contains(it) }
    val artistIntersection = targetArtistWords.intersect(candArtistWords.toSet())

    val artistScore = when {
        normTargetArtist.isNotBlank() && normCandArtist.isNotBlank() &&
            (normCandArtist == normTargetArtist || normCandArtist.contains(normTargetArtist) || normTargetArtist.contains(normCandArtist)) -> 35
        artistIntersection.isNotEmpty() -> 30
        artistMatchesTitle -> 25
        isGenericOrChannelArtist(normTargetArtist) -> 15
        else -> {
            if (normCandTitle == normTargetTitle && targetTitleWords.size >= 3) 5 else -30
        }
    }

    return (titleScore + artistScore).coerceIn(0, 100)
}

/**
 * Retrieves authoritative song title & artist from YouTube.next(videoId) (which is not blocked
 * by /player bot checks) and combines it with caller-supplied title/artist metadata.
 */
internal fun resolveCanonicalMetadata(
    videoId: String,
    titleInput: String?,
    artistInput: String?
): CanonicalTrackMetadata {
    val callerTitle = titleInput?.takeIf { it.isNotBlank() && !it.equals("Unknown", ignoreCase = true) }
    val callerArtist = artistInput
        ?.replace(Regex("^(?:Song|Video|Album|Single|EP)\\s*[•·-]\\s*", RegexOption.IGNORE_CASE), "")
        ?.trim()
        ?.takeIf {
            it.isNotBlank() && !it.equals("Unknown", ignoreCase = true) && !it.equals("Unknown Artist", ignoreCase = true)
        }

    var ytNextTitle: String? = null
    var ytNextArtist: String? = null

    if (videoId.matches(Regex("^[a-zA-Z0-9_-]{11}$"))) {
        val needAuthoritativeLookup = callerTitle.isNullOrBlank() ||
            callerArtist.isNullOrBlank() ||
            isGenericOrChannelArtist(normalizeForComparison(callerArtist)) ||
            callerArtist.contains(Regex("\\b(?:plays|views)\\b", RegexOption.IGNORE_CASE)) ||
            callerTitle.contains("|") ||
            callerTitle.contains(" - ")

        if (needAuthoritativeLookup) {
            runCatching {
                runBlocking {
                    withTimeoutOrNull(3_000L) {
                        val nextResult = YouTube.next(
                            WatchEndpoint(
                                videoId = videoId,
                                playlistId = "RDAMVM$videoId"
                            )
                        ).getOrNull() ?: YouTube.next(
                            WatchEndpoint(videoId = videoId)
                        ).getOrNull()

                        val matchedItem = nextResult?.items?.firstOrNull { it.id == videoId }
                            ?: nextResult?.items?.firstOrNull()
                        if (matchedItem != null && (matchedItem.id == videoId || callerTitle.isNullOrBlank())) {
                            ytNextTitle = matchedItem.title.takeIf { it.isNotBlank() }
                            ytNextArtist = matchedItem.artists
                                .mapNotNull { it.name.takeIf { n -> n.isNotBlank() } }
                                .joinToString(", ")
                                .takeIf { it.isNotBlank() }
                        }
                    }
                }
            }
        }
    }

    var splitArtist: String? = null
    var splitTitle: String? = null
    if (!callerTitle.isNullOrBlank() && callerTitle.contains(" - ")) {
        val parts = callerTitle.split(" - ", limit = 2)
        if (parts.size == 2) {
            val left = cleanMetadataForSearch(parts[0].substringBefore("|"))
            val right = cleanMetadataForSearch(parts[1].substringBefore("|"))
            if (left.isNotBlank() && right.isNotBlank()) {
                splitArtist = left
                splitTitle = right
            }
        }
    } else if (!callerTitle.isNullOrBlank() && callerTitle.contains("|")) {
        val firstSegment = cleanMetadataForSearch(callerTitle.substringBefore("|"))
        if (firstSegment.isNotBlank()) {
            splitTitle = firstSegment
        }
    }

    val bestTitle = cleanMetadataForSearch(ytNextTitle ?: splitTitle ?: callerTitle ?: "")
    val rawBestArtist = ytNextArtist
        ?: callerArtist?.takeIf { !isGenericOrChannelArtist(normalizeForComparison(it)) }
        ?: splitArtist
        ?: callerArtist
        ?: ""

    val bestArtist = cleanMetadataForSearch(rawBestArtist)
    val bestSearchArtist = cleanMetadataForSearch(
        rawBestArtist
            .substringBefore(",")
            .substringBefore("&")
            .substringBefore("feat")
    )

    val altTitle = callerTitle?.let { cleanMetadataForSearch(it) }?.takeIf { it.isNotBlank() && !it.equals(bestTitle, ignoreCase = true) }
    val altArtist = callerArtist?.let { cleanMetadataForSearch(it) }?.takeIf { it.isNotBlank() }
    val altSearchArtist = callerArtist?.let {
        cleanMetadataForSearch(it.substringBefore(",").substringBefore("&").substringBefore("feat"))
    }?.takeIf { it.isNotBlank() }

    return CanonicalTrackMetadata(
        primaryTitle = bestTitle.ifBlank { altTitle.orEmpty() },
        primaryArtist = bestArtist,
        primarySearchArtist = bestSearchArtist,
        secondaryTitle = altTitle,
        secondaryArtist = altArtist,
        secondarySearchArtist = altSearchArtist
    )
}

internal fun decryptSaavnMediaUrl(encrypted: String): String? {
    return try {
        val cipher = Cipher.getInstance("DES/ECB/PKCS5Padding")
        val keySpec = SecretKeySpec("38346591".toByteArray(Charsets.UTF_8), "DES")
        cipher.init(Cipher.DECRYPT_MODE, keySpec)
        val decoded = Base64.decode(encrypted.trim(), Base64.DEFAULT)
        String(cipher.doFinal(decoded), Charsets.UTF_8).trim()
    } catch (e: Exception) {
        Log.w(StreamResolver.TAG, "Failed to decrypt Saavn URL: ${e.message}")
        null
    }
}

internal fun StreamResolver.extractYouTubeVideoId(input: String): String? {
    val trimmed = input.trim()
    if (trimmed.matches(Regex("^[a-zA-Z0-9_-]{11}$"))) return trimmed
    if (!trimmed.contains("youtube.com") && !trimmed.contains("youtu.be")) return null

    return try {
        val uri = Uri.parse(trimmed)
        val host = uri.host.orEmpty().lowercase()
        when {
            host == "youtu.be" -> uri.pathSegments.firstOrNull()
            uri.getQueryParameter("v")?.isNotBlank() == true -> uri.getQueryParameter("v")
            uri.pathSegments.firstOrNull() in setOf("shorts", "embed", "live") -> uri.pathSegments.getOrNull(1)
            uri.pathSegments.firstOrNull() == "watch" -> uri.getQueryParameter("v")
            else -> null
        }?.takeIf { it.matches(Regex("^[a-zA-Z0-9_-]{11}$")) }
    } catch (e: Exception) {
        Log.w(StreamResolver.TAG, "Failed to parse YouTube URL: $input", e)
        null
    }
}

internal fun StreamResolver.userAgentForStreamUrl(
    url: String,
    fallbackUserAgent: String = StreamResolver.DEFAULT_WEB_USER_AGENT
): String {
    return try {
        val cParam = Uri.parse(url).getQueryParameter("c")?.uppercase().orEmpty()
        when {
            cParam == "IOS" -> YouTubeClient.IOS.userAgent
            cParam == "ANDROID_VR" -> YouTubeClient.ANDROID_VR_NO_AUTH.userAgent
            cParam == "ANDROID_TESTSUITE" -> "com.google.android.youtube/1.9 (Linux; U; Android 12; US) gzip"
            cParam.startsWith("ANDROID") -> "com.google.android.youtube/19.29.37 (Linux; U; Android 14) gzip"
            else -> fallbackUserAgent
        }
    } catch (_: Exception) {
        fallbackUserAgent
    }
}

internal fun StreamResolver.normalizeCacheKey(
    videoIdInput: String,
    mode: PlaybackMediaMode,
    preferredVideoQuality: PlaybackVideoQuality,
    preferAac: Boolean = false
): String? {
    val trimmed = videoIdInput.trim()
    if (trimmed.isBlank() || trimmed == "watch") return null
    val normalized = when {
        trimmed.contains("soundcloud.com") || trimmed.contains("bandcamp.com") -> trimmed
        else -> extractYouTubeVideoId(trimmed) ?: trimmed
    }
    if (normalized.isBlank() || normalized == "watch") return null
    val qualityKey = if (mode == PlaybackMediaMode.VIDEO) {
        preferredVideoQuality.wireName
    } else {
        PlaybackVideoQuality.AUTO.wireName
    }
    return "${mode.name}:$qualityKey:$normalized${if (preferAac) ":aac" else ""}"
}

internal fun StreamResolver.verifyStreamUrl(url: String, userAgent: String): Boolean {
    if (url.startsWith("file://")) return true
    return try {
        val request = Request.Builder()
            .url(url)
            .get()
            .header("User-Agent", userAgent)
            .header("Range", "bytes=0-1")
            .build()
        probeHttpClient.newCall(request).execute().use { response ->
            val ok = response.isSuccessful || response.code == 206 || response.code == 200
            if (!ok) {
                Log.w(StreamResolver.TAG, "Stream probe failed with HTTP ${response.code} for url")
            }
            ok
        }
    } catch (e: Exception) {
        Log.w(StreamResolver.TAG, "Stream probe exception: ${e.message}")
        false
    }
}

internal fun StreamResolver.resolveTidalHifiAudioStream(
    videoId: String,
    cleanTitle: String,
    searchArtist: String,
    isrc: String?,
    updateQualityState: Boolean,
    evaluateScore: (String, String) -> Int
): StreamResolver.StreamResult? {
    if (System.currentTimeMillis() < hifiDeadUntilMs) return null
    return runCatching {
        val hifiResult = runBlocking {
            withTimeoutOrNull(2_500L) {
                var candidates = emptyList<HifiTrack>()
                if (!isrc.isNullOrBlank()) {
                    candidates = HifiApiClient.searchByISRC(isrc)
                }
                if (candidates.isEmpty()) {
                    val q = if (searchArtist.isNotBlank() && !isGenericOrChannelArtist(normalizeForComparison(searchArtist))) {
                        "$cleanTitle $searchArtist"
                    } else {
                        cleanTitle
                    }
                    candidates = HifiApiClient.searchByText(q, limit = 10)
                }

                if (candidates.isEmpty()) {
                    hifiDeadUntilMs = System.currentTimeMillis() + 10 * 60_000L
                    return@withTimeoutOrNull null
                }

                val scoredCandidates = candidates
                    .map { track ->
                        val score = evaluateScore(track.title.orEmpty(), track.displayArtist)
                        track to score
                    }
                    .filter { it.second >= 55 }
                    .sortedByDescending { it.second }

                for ((matchedTrack, _) in scoredCandidates.take(2)) {
                    for (tierQuality in listOf("HIGH", "LOW")) {
                        val streamResp = HifiApiClient.getStreamUrl(matchedTrack.id, tierQuality)
                        val directUrl = streamResp?.urls?.firstOrNull { it.isNotBlank() }
                        if (!directUrl.isNullOrBlank() && verifyStreamUrl(directUrl, "TIDAL_ANDROID/1039 okhttp/3.14.9")) {
                            val bitrate = if (tierQuality == "HIGH") 320000 else 96000
                            val qualityLabel = "M4A ${bitrate / 1000}kbps"
                            if (updateQualityState) setCurrentStreamQuality(qualityLabel)
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
                            return@withTimeoutOrNull StreamResolver.StreamResult(
                                url = directUrl,
                                userAgent = "TIDAL_ANDROID/1039 okhttp/3.14.9",
                                quality = qualityLabel,
                                mode = PlaybackMediaMode.AUDIO,
                                hasVideo = false
                            )
                        }
                    }
                }
                null
            }
        }
        if (hifiResult == null) {
            hifiDeadUntilMs = System.currentTimeMillis() + 10 * 60_000L
        }
        hifiResult
    }.getOrNull()
}

internal fun StreamResolver.preloadAlternateModeImpl(
    videoIdInput: String,
    title: String? = null,
    artist: String? = null,
    mode: PlaybackMediaMode,
    preferredVideoQuality: PlaybackVideoQuality = PlaybackVideoQuality.AUTO,
    isrc: String? = null
) {
    val trimmed = videoIdInput.trim()
    if (trimmed.isBlank() || trimmed == "watch") return
    val cacheKey = normalizeCacheKey(trimmed, mode, preferredVideoQuality) ?: return
    if (getCachedStream(cacheKey) != null) return
    synchronized(preloadLock) { if (!inFlightPreloads.add(cacheKey)) return }
    preloadScope.launch {
        try {
            resolveStreamUrl(trimmed, title, artist, mode, preferredVideoQuality, isrc = isrc, updateQualityState = false)
        } catch (e: Exception) {
            Log.w(StreamResolver.TAG, "Alternate mode ($mode) preload failed for $cacheKey: ${e.message}")
        } finally {
            synchronized(preloadLock) { inFlightPreloads.remove(cacheKey) }
        }
    }
}

internal fun StreamResolver.preloadStreamImpl(videoIdInput: String, title: String? = null, artist: String? = null) {
    val trimmed = videoIdInput.trim()
    if (trimmed.isBlank() || trimmed == "watch") return
    val cacheKey = normalizeCacheKey(trimmed, PlaybackMediaMode.AUDIO, PlaybackVideoQuality.AUTO) ?: return
    if (getCachedStream(cacheKey) != null) return
    synchronized(preloadLock) { if (!inFlightPreloads.add(cacheKey)) return }
    preloadScope.launch {
        try {
            resolveStreamUrl(videoIdInput = trimmed, title = title, artist = artist, updateQualityState = false)
        } catch (e: Exception) {
            Log.w(StreamResolver.TAG, "Preload failed for $cacheKey: ${e.message}")
        } finally {
            synchronized(preloadLock) { inFlightPreloads.remove(cacheKey) }
        }
    }
}
