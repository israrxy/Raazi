package com.israrxy.raazi.data.recommendation

import android.content.Context
import com.israrxy.raazi.model.MusicItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * What the user was listening in when playback started. This is what lets
 * "Listen again" surface albums and playlists, not just songs.
 */
data class PlaybackContext(
    val id: String,
    val title: String,
    val subtitle: String,
    val thumbnailUrl: String,
    val kind: String,
    val timestampMs: Long = System.currentTimeMillis()
) {
    companion object {
        const val KIND_SONG = "song"
        const val KIND_ALBUM = "album"
        const val KIND_PLAYLIST = "playlist"
        const val KIND_ARTIST_RADIO = "artist_radio"
        const val KIND_SEARCH = "search"

        fun song(item: MusicItem) = PlaybackContext(
            id = item.id,
            title = item.title,
            subtitle = item.artist,
            thumbnailUrl = item.thumbnailUrl,
            kind = KIND_SONG
        )
    }
}

/**
 * Small file-backed ring of recent playback contexts. Kept out of Room so the
 * change needs no schema migration.
 */
class PlaybackContextStore(context: Context) {
    private val file = File(context.filesDir, FILE_NAME)
    private val mutex = Mutex()

    suspend fun record(context: PlaybackContext) = withContext(Dispatchers.IO) {
        if (context.id.isBlank() || context.title.isBlank()) return@withContext
        mutex.withLock {
            val existing = read()
            val updated = (listOf(context) + existing.filterNot {
                it.kind == context.kind && it.id == context.id
            }).take(MAX_CONTEXTS)
            write(updated)
        }
    }

    suspend fun recent(limit: Int = 16): List<PlaybackContext> = withContext(Dispatchers.IO) {
        mutex.withLock { read().take(limit) }
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        mutex.withLock {
            runCatching { file.delete() }
            Unit
        }
    }

    private fun read(): List<PlaybackContext> {
        if (!file.exists()) return emptyList()
        return runCatching {
            val array = JSONArray(file.readText())
            (0 until array.length()).mapNotNull { index ->
                val json = array.optJSONObject(index) ?: return@mapNotNull null
                PlaybackContext(
                    id = json.optString("id"),
                    title = json.optString("title"),
                    subtitle = json.optString("subtitle"),
                    thumbnailUrl = json.optString("thumbnailUrl"),
                    kind = json.optString("kind"),
                    timestampMs = json.optLong("timestampMs")
                )
            }.filter { it.id.isNotBlank() && it.title.isNotBlank() }
        }.getOrDefault(emptyList())
    }

    private fun write(contexts: List<PlaybackContext>) {
        val array = JSONArray()
        contexts.forEach { context ->
            array.put(
                JSONObject()
                    .put("id", context.id)
                    .put("title", context.title)
                    .put("subtitle", context.subtitle)
                    .put("thumbnailUrl", context.thumbnailUrl)
                    .put("kind", context.kind)
                    .put("timestampMs", context.timestampMs)
            )
        }
        runCatching {
            val temp = File(file.parentFile, "$FILE_NAME.tmp")
            temp.writeText(array.toString())
            if (!temp.renameTo(file)) {
                file.writeText(array.toString())
                temp.delete()
            }
        }
    }

    companion object {
        private const val FILE_NAME = "playback_contexts.json"
        private const val MAX_CONTEXTS = 32
    }
}
