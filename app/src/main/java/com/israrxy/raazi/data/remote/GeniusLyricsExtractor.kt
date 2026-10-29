package com.israrxy.raazi.data.remote

import android.util.Log
import com.israrxy.raazi.data.lyrics.LyricsLookupCandidate
import com.israrxy.raazi.data.lyrics.LyricsMetadataSanitizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class GeniusLyricsExtractor {
    private val client = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(4, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val tag = "GeniusLyricsExtractor"

    private data class GeniusSongHit(
        val id: Int,
        val title: String,
        val artist: String,
        val url: String
    )

    suspend fun fetchBestForCandidates(
        candidates: List<LyricsLookupCandidate>,
        queries: List<String>,
        durationSeconds: Double
    ): List<Lyrics> = withContext(Dispatchers.IO) {
        val searchQueries = buildList {
            candidates.take(2).forEach { c ->
                val q = "${c.artistName} ${c.trackName}".trim()
                if (q.isNotBlank()) add(q)
            }
            addAll(queries.take(2))
        }.distinct()

        val hits = LinkedHashMap<String, GeniusSongHit>()
        for (query in searchQueries.take(2)) {
            searchGeniusHits(query).forEach { hit ->
                hits.putIfAbsent(hit.url, hit)
            }
            if (hits.size >= 3) break
        }

        val rankedHits = hits.values
            .sortedByDescending { hit ->
                val titleScore = candidates.maxOfOrNull {
                    LyricsMetadataSanitizer.similarityScore(it.trackName, hit.title)
                } ?: 0
                val artistScore = candidates.maxOfOrNull {
                    if (it.artistName.isBlank()) 70 else LyricsMetadataSanitizer.similarityScore(it.artistName, hit.artist)
                } ?: 70
                (titleScore * 0.72 + artistScore * 0.28).toInt()
            }
            .take(2)

        val extracted = mutableListOf<Lyrics>()
        for (hit in rankedHits) {
            scrapeLyricsFromPage(hit, durationSeconds)?.let { lyrics ->
                extracted += lyrics
            }
        }
        extracted
    }

    private fun searchGeniusHits(query: String): List<GeniusSongHit> {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val urls = listOf(
            "https://genius.com/api/search/multi?per_page=5&q=$encoded",
            "https://genius.com/api/search?q=$encoded"
        )

        for (url in urls) {
            val hits = runCatching {
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", BROWSER_USER_AGENT)
                    .header("Accept", "application/json")
                    .build()

                client.newCall(request).execute().use { response ->
                    val body = response.body?.string() ?: return@use emptyList()
                    if (!response.isSuccessful || body.isBlank()) return@use emptyList()
                    parseGeniusSearchJson(JSONObject(body))
                }
            }.getOrDefault(emptyList())

            if (hits.isNotEmpty()) {
                return hits
            }
        }
        return emptyList()
    }

    private fun parseGeniusSearchJson(root: JSONObject): List<GeniusSongHit> {
        val response = root.optJSONObject("response") ?: return emptyList()
        val results = mutableListOf<GeniusSongHit>()

        val sections = response.optJSONArray("sections")
        if (sections != null) {
            for (s in 0 until sections.length()) {
                val section = sections.optJSONObject(s) ?: continue
                val type = section.optString("type")
                if (type != "song" && type != "top_hit") continue
                val hits = section.optJSONArray("hits") ?: continue
                for (h in 0 until hits.length()) {
                    val hitObj = hits.optJSONObject(h) ?: continue
                    val hitType = hitObj.optString("type", "song")
                    if (hitType != "song") continue
                    val result = hitObj.optJSONObject("result") ?: continue
                    parseSongResult(result)?.let(results::add)
                }
            }
        }

        val directHits = response.optJSONArray("hits")
        if (directHits != null) {
            for (h in 0 until directHits.length()) {
                val hitObj = directHits.optJSONObject(h) ?: continue
                val result = hitObj.optJSONObject("result") ?: continue
                parseSongResult(result)?.let(results::add)
            }
        }

        return results.distinctBy { it.url }
    }

    private fun parseSongResult(result: JSONObject): GeniusSongHit? {
        val url = result.optString("url")
        if (url.isBlank() || !url.contains("genius.com")) return null
        val lyricsState = result.optString("lyrics_state", "complete")
        if (lyricsState == "unreleased" || result.optBoolean("instrumental", false)) return null
        val id = result.optInt("id", url.hashCode())
        val title = result.optString("title").ifBlank { result.optString("full_title") }
        val artist = result.optJSONObject("primary_artist")?.optString("name")
            ?.ifBlank { result.optString("artist_names") }
            .orEmpty()
        if (title.isBlank()) return null
        return GeniusSongHit(
            id = id,
            title = title,
            artist = artist,
            url = url
        )
    }

    private fun scrapeLyricsFromPage(hit: GeniusSongHit, durationSeconds: Double): Lyrics? {
        return runCatching {
            val request = Request.Builder()
                .url(hit.url)
                .header("User-Agent", BROWSER_USER_AGENT)
                .header("Accept-Language", "en-US,en;q=0.9")
                .build()

            client.newCall(request).execute().use { response ->
                val html = response.body?.string() ?: return@use null
                if (!response.isSuccessful || html.isBlank()) return@use null
                val combinedText = extractLyricsFromGeniusHtml(html)
                if (combinedText.isNullOrBlank() || combinedText.length < 25) return@use null

                Lyrics(
                    id = hit.id,
                    trackName = hit.title,
                    artistName = hit.artist,
                    plainLyrics = combinedText,
                    syncedLyrics = null,
                    duration = durationSeconds,
                    language = null,
                    source = "Genius"
                )
            }
        }.onFailure {
            runCatching { Log.w(tag, "Failed to scrape Genius lyrics from ${hit.url}: ${it.message}") }
        }.getOrNull()
    }

    companion object {
        private const val BROWSER_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"

        internal fun extractLyricsFromGeniusHtml(html: String): String? {
            val rawContainers = extractDataLyricsContainers(html)
            if (rawContainers.isEmpty()) return null
            return rawContainers
                .map(::htmlContainerToPlainText)
                .filter { it.isNotBlank() }
                .joinToString("\n\n")
                .let(::cleanGeniusPlainText)
                .takeIf { it.isNotBlank() }
        }

        internal fun extractDataLyricsContainers(html: String): List<String> {
            val containers = mutableListOf<String>()
            val marker = "data-lyrics-container=\"true\""
            var searchIndex = 0

            while (true) {
                val markerIdx = html.indexOf(marker, searchIndex)
                if (markerIdx == -1) break

                val openDivStart = html.lastIndexOf("<div", markerIdx)
                val openDivEnd = html.indexOf('>', markerIdx)
                if (openDivStart == -1 || openDivEnd == -1) break

                var depth = 1
                var cursor = openDivEnd + 1
                while (cursor < html.length && depth > 0) {
                    val nextOpen = html.indexOf("<div", cursor, ignoreCase = true)
                    val nextClose = html.indexOf("</div", cursor, ignoreCase = true)
                    if (nextClose == -1) break

                    if (nextOpen != -1 && nextOpen < nextClose) {
                        depth++
                        cursor = nextOpen + 4
                    } else {
                        depth--
                        if (depth == 0) {
                            containers.add(html.substring(openDivEnd + 1, nextClose))
                        }
                        cursor = nextClose + 5
                    }
                }
                searchIndex = cursor.coerceAtLeast(openDivEnd + 1)
            }
            return containers
        }

        private fun htmlContainerToPlainText(containerHtml: String): String {
            val withoutExcluded = containerHtml.replace(
                Regex("""<div[^>]*data-exclude-from-selection="true"[^>]*>[\s\S]*?</div>""", RegexOption.IGNORE_CASE),
                ""
            )
            val withLineBreaks = withoutExcluded
                .replace(Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE), "\n")
                .replace(Regex("""</p>""", RegexOption.IGNORE_CASE), "\n")
            val strippedTags = withLineBreaks.replace(Regex("""<[^>]+>"""), "")
            return decodeHtmlEntities(strippedTags)
        }

        private fun decodeHtmlEntities(input: String): String {
            return input
                .replace("&amp;", "&")
                .replace("&quot;", "\"")
                .replace("&#x27;", "'")
                .replace("&#39;", "'")
                .replace("&apos;", "'")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&nbsp;", " ")
                .replace(Regex("""&#(\d+);""")) { match ->
                    val code = match.groupValues[1].toIntOrNull()
                    if (code != null) code.toChar().toString() else match.value
                }
                .replace(Regex("""&#x([0-9a-fA-F]+);""")) { match ->
                    val code = match.groupValues[1].toIntOrNull(16)
                    if (code != null) code.toChar().toString() else match.value
                }
        }

        private fun cleanGeniusPlainText(raw: String): String {
            val lines = raw.lines()
                .map { it.trim() }
                .toMutableList()

            while (lines.isNotEmpty()) {
                val first = lines.first()
                if (first.isBlank() ||
                    first.contains("Contributors", ignoreCase = true) ||
                    first.contains("Translations", ignoreCase = true) ||
                    first.endsWith("Lyrics", ignoreCase = true) ||
                    first.startsWith("Read More", ignoreCase = true)
                ) {
                    lines.removeAt(0)
                } else {
                    break
                }
            }

            return lines.joinToString("\n")
                .replace(Regex("""You might also like""", RegexOption.IGNORE_CASE), "")
                .replace(Regex("""\d*\s*Embed$""", RegexOption.IGNORE_CASE), "")
                .replace(Regex("""\n{3,}"""), "\n\n")
                .trim()
        }
    }
}
