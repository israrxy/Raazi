package com.israrxy.raazi.data.lyrics

import android.content.Context
import com.israrxy.raazi.data.remote.Lyrics
import org.json.JSONObject
import java.io.File

class SavedLyricsStore(context: Context) {
    private val directory = File(context.filesDir, "saved-lyrics").apply { mkdirs() }

    fun get(trackId: String): Lyrics? {
        val file = fileFor(trackId)
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
                source = json.optString("source").ifBlank { "Saved" }
            )
        }.getOrNull()
    }

    fun put(trackId: String, lyrics: Lyrics) {
        runCatching {
            val file = fileFor(trackId)
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

    fun remove(trackId: String) {
        runCatching {
            fileFor(trackId).delete()
        }
    }

    private fun fileFor(trackId: String): File {
        return File(directory, "${trackId.hashCode().toUInt().toString(16)}.json")
    }
}
