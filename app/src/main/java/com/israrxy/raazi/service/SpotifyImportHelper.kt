package com.israrxy.raazi.service

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.regex.Pattern

data class SpotifyTrack(
    val index: Int,
    val title: String,
    val artist: String,
    val durationMs: Long
)

data class SpotifyPlaylist(
    val title: String,
    val tracks: List<SpotifyTrack>
)

object SpotifyImportHelper {
    private const val TAG = "SpotifyImportHelper"
    private val client by lazy { OkHttpClient.Builder().build() }

    fun extractPlaylistId(url: String): String? {
        val trimmed = url.trim()
        val pattern = Pattern.compile("(?:playlist/|spotify:playlist:)([a-zA-Z0-9]{22})")
        val matcher = pattern.matcher(trimmed)
        return if (matcher.find()) {
            matcher.group(1)
        } else {
            null
        }
    }

    suspend fun fetchPlaylist(url: String): SpotifyPlaylist = withContext(Dispatchers.IO) {
        val playlistId = extractPlaylistId(url) ?: throw Exception("Invalid Spotify playlist URL")
        val embedUrl = "https://open.spotify.com/embed/playlist/$playlistId"

        logDebug("Fetching Spotify embed HTML for ID: $playlistId")
        val request = Request.Builder()
            .url(embedUrl)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            throw Exception("Failed to fetch Spotify playlist. HTTP Code: ${response.code}")
        }

        val html = response.body?.string() ?: throw Exception("Empty response from Spotify")
        parsePlaylistHtml(html)
    }

    internal fun parsePlaylistHtml(html: String): SpotifyPlaylist {
        // 1. Extract Playlist Title
        var title = "Spotify Playlist"
        val titlePattern = Pattern.compile("alt=\"([^\"]+?)\\s+cover\"", Pattern.CASE_INSENSITIVE)
        val titleMatcher = titlePattern.matcher(html)
        if (titleMatcher.find()) {
            title = titleMatcher.group(1) ?: "Spotify Playlist"
        } else {
            // Alternative title match from Marquee/metadata
            val altTitlePattern = Pattern.compile("TrackListWidget_metadataContainer.*?encore-text-body-medium\">(.*?)</span>", Pattern.DOTALL)
            val altMatcher = altTitlePattern.matcher(html)
            if (altMatcher.find()) {
                title = altMatcher.group(1)?.trim() ?: "Spotify Playlist"
            }
        }

        logDebug("Parsed Spotify playlist title: $title")

        // 2. Extract Tracks
        // Each track is enclosed in <li class="TracklistRow... data-testid="tracklist-row-X">...</li>
        val tracks = mutableListOf<SpotifyTrack>()
        
        // Find all <li> blocks corresponding to tracklist rows
        val liPattern = Pattern.compile("<li[^>]*data-testid=\"tracklist-row-(\\d+)\"[^>]*>(.*?)</li>", Pattern.DOTALL)
        val liMatcher = liPattern.matcher(html)

        while (liMatcher.find()) {
            val indexStr = liMatcher.group(1)
            val index = indexStr?.toIntOrNull() ?: continue
            val innerHtml = liMatcher.group(2) ?: continue

            // Parse Title inside h3 (TracklistRow_title)
            val titleTrackPattern = Pattern.compile("<h3[^>]*TracklistRow_title[^>]*>(.*?)</h3>", Pattern.DOTALL)
            val titleTrackMatcher = titleTrackPattern.matcher(innerHtml)
            val trackTitle = if (titleTrackMatcher.find()) {
                cleanHtml(titleTrackMatcher.group(1))
            } else {
                "Unknown Track"
            }

            // Parse Subtitle (Artists) inside h4 (TracklistRow_subtitle)
            val subtitlePattern = Pattern.compile("<h4[^>]*TracklistRow_subtitle[^>]*>(.*?)</h4>", Pattern.DOTALL)
            val subtitleMatcher = subtitlePattern.matcher(innerHtml)
            val artist = if (subtitleMatcher.find()) {
                // Remove explicit span or tags
                val rawArtist = subtitleMatcher.group(1)
                cleanHtml(rawArtist)
            } else {
                "Unknown Artist"
            }

            // Parse Duration inside TracklistRow_durationCell
            val durationPattern = Pattern.compile("<div[^>]*TracklistRow_durationCell[^>]*>(.*?)</div>", Pattern.DOTALL)
            val durationMatcher = durationPattern.matcher(innerHtml)
            var durationMs = 0L
            if (durationMatcher.find()) {
                val durationStr = cleanHtml(durationMatcher.group(1))
                durationMs = parseDurationToMs(durationStr)
            }

            tracks.add(
                SpotifyTrack(
                    index = index,
                    title = trackTitle,
                    artist = artist,
                    durationMs = durationMs
                )
            )
        }

        logDebug("Parsed ${tracks.size} tracks from Spotify HTML")
        if (tracks.isEmpty()) {
            throw Exception("No tracks could be found. Please ensure the playlist is public.")
        }

        return SpotifyPlaylist(title = title, tracks = tracks)
    }

    private fun cleanHtml(html: String?): String {
        if (html == null) return ""
        // Strip HTML tags and entities
        val tagsRemoved = html.replace("<[^>]*>".toRegex(), "")
        return tagsRemoved
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&#x27;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&#x2F;", "/")
            .replace("&#x3D;", "=")
            .replace("&#x60;", "`")
            .trim()
    }

    private fun parseDurationToMs(durationStr: String): Long {
        val parts = durationStr.split(":")
        return try {
            if (parts.size == 2) {
                val mins = parts[0].toLong()
                val secs = parts[1].toLong()
                (mins * 60 + secs) * 1000L
            } else if (parts.size == 3) {
                val hours = parts[0].toLong()
                val mins = parts[1].toLong()
                val secs = parts[2].toLong()
                (hours * 3600 + mins * 60 + secs) * 1000L
            } else {
                0L
            }
        } catch (e: Exception) {
            0L
        }
    }

    private fun logDebug(message: String) {
        try {
            Log.d(TAG, message)
        } catch (_: RuntimeException) {
            // android.util.Log is not available in local JVM unit tests.
        }
    }

    fun cleanTrackTitleForSearch(title: String): String {
        // Remove text inside parentheses (e.g. "(feat. Drake)")
        var cleaned = title.replace("\\([^)]*\\)".toRegex(), "")
        // Remove text inside brackets (e.g. "[Remastered]")
        cleaned = cleaned.replace("\\[[^]]*\\]".toRegex(), "")
        // Remove text after hyphens (e.g. "- Remastered 2020")
        val hyphenIndex = cleaned.indexOf("-")
        if (hyphenIndex != -1) {
            cleaned = cleaned.substring(0, hyphenIndex)
        }
        return cleaned.trim()
    }

    fun checkRelaxedMatch(spotifyCleanedTitle: String, ytTitle: String): Boolean {
        // Split title into words, stripping special chars
        val spotifyWords = spotifyCleanedTitle.lowercase()
            .replace("[^a-zA-Z0-9\\s]".toRegex(), "")
            .split("\\s+".toRegex())
            .filter { it.length > 2 }
        if (spotifyWords.isEmpty()) return true
        val ytLower = ytTitle.lowercase()
        return spotifyWords.any { ytLower.contains(it) }
    }
}
