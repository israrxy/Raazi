package com.israrxy.raazi.data.lyrics

import android.content.Context
import com.israrxy.raazi.data.remote.Lyrics
import org.json.JSONObject
import java.io.File
import java.util.LinkedHashMap

class LyricsCache(context: Context) {
    private val cacheDirectory = File(context.cacheDir, "lyrics-cache").apply { mkdirs() }
    private val memoryCache = object : LinkedHashMap<String, Lyrics>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Lyrics>?): Boolean {
            return size > 64
        }
    }

    fun get(key: String): Lyrics? = synchronized(memoryCache) {
        memoryCache[key]
    } ?: readFromDisk(key)?.also { lyrics ->
        synchronized(memoryCache) {
            memoryCache[key] = lyrics
        }
    }

    fun put(key: String, lyrics: Lyrics) {
        synchronized(memoryCache) {
            memoryCache[key] = lyrics
        }

        runCatching {
            val file = cacheFile(key)
            file.parentFile?.mkdirs()
            file.writeText(
                JSONObject()
                    .put("id", lyrics.id)
                    .put("trackName", lyrics.trackName)
                    .put("artistName", lyrics.artistName)
                    .put("plainLyrics", lyrics.plainLyrics)
                    .put("syncedLyrics", lyrics.syncedLyrics)
                    .put("duration", lyrics.duration)
                    .put("language", lyrics.language)
                    .put("source", lyrics.source)
                    .toString()
            )
        }
    }

    private fun readFromDisk(key: String): Lyrics? {
        val file = cacheFile(key)
        if (!file.exists()) {
            return null
        }

        return runCatching {
            val json = JSONObject(file.readText())
            Lyrics(
                id = json.optInt("id"),
                trackName = json.optString("trackName"),
                artistName = json.optString("artistName"),
                plainLyrics = json.optString("plainLyrics").takeIf { it.isNotBlank() },
                syncedLyrics = json.optString("syncedLyrics").takeIf { it.isNotBlank() },
                duration = json.optDouble("duration"),
                language = json.optString("language").takeIf { it.isNotBlank() },
                source = json.optString("source").ifBlank { "LRCLIB" }
            )
        }.getOrNull()
    }

    private fun cacheFile(key: String): File {
        return File(cacheDirectory, "${key.hashCode().toUInt().toString(16)}.json")
    }
}
