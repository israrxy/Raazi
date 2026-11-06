package com.israrxy.raazi.data.recommendation

import android.content.Context
import com.israrxy.raazi.model.MusicContentType
import com.israrxy.raazi.model.MusicItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * File-backed cache of generated shelves, keyed by seed. A launch on the same
 * day paints the last generated feed instantly, even offline.
 */
class HomeShelfCache(context: Context) {
    private val file = File(context.filesDir, FILE_NAME)
    private val mutex = Mutex()

    data class Entry(val seed: Int, val savedAtMs: Long, val shelves: List<GeneratedShelf>) {
        fun isStale(nowMs: Long = System.currentTimeMillis()): Boolean =
            nowMs - savedAtMs > TTL_MS
    }

    suspend fun load(seed: Int): Entry? = withContext(Dispatchers.IO) {
        mutex.withLock { readAll()[seed.toString()] }
    }

    suspend fun save(seed: Int, shelves: List<GeneratedShelf>) = withContext(Dispatchers.IO) {
        if (shelves.isEmpty()) return@withContext
        mutex.withLock {
            val entries = readAll().toMutableMap()
            entries[seed.toString()] = Entry(seed, System.currentTimeMillis(), shelves)
            writeAll(entries)
        }
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        mutex.withLock {
            runCatching { file.delete() }
            Unit
        }
    }

    private fun readAll(): Map<String, Entry> {
        if (!file.exists()) return emptyMap()
        return runCatching {
            val root = JSONObject(file.readText())
            val result = mutableMapOf<String, Entry>()
            root.keys().forEach { key ->
                val json = root.optJSONObject(key) ?: return@forEach
                val shelves = json.optJSONArray("shelves").orEmpty().mapNotNull { item ->
                    if (item is JSONObject) item.toShelf() else null
                }
                result[key] = Entry(
                    seed = key.toIntOrNull() ?: 0,
                    savedAtMs = json.optLong("savedAt"),
                    shelves = shelves
                )
            }
            result
        }.getOrDefault(emptyMap())
    }

    private fun writeAll(entries: Map<String, Entry>) {
        val root = JSONObject()
        entries.values.sortedByDescending { it.savedAtMs }.take(MAX_ENTRIES).forEach { entry ->
            root.put(
                entry.seed.toString(),
                JSONObject()
                    .put("savedAt", entry.savedAtMs)
                    .put("shelves", JSONArray().apply {
                        entry.shelves.forEach { shelf -> put(shelf.toJson()) }
                    })
            )
        }
        runCatching {
            val temp = File(file.parentFile, "$FILE_NAME.tmp")
            temp.writeText(root.toString())
            if (!temp.renameTo(file)) {
                file.writeText(root.toString())
                temp.delete()
            }
        }
    }

    private fun GeneratedShelf.toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("title", title)
        .put("kind", kind.name)
        .put("layout", layout.name)
        .put("strapline", strapline)
        .put("subtitle", subtitle)
        .put("avatarUrl", avatarUrl)
        .put("moreEndpoint", moreEndpoint)
        .put("artistName", artistName)
        .put("items", JSONArray().apply {
            items.forEach { item ->
                put(
                    when (item) {
                        is ShelfItem.Track -> JSONObject()
                            .put("type", "track")
                            .put("item", item.item.toJson())
                        is ShelfItem.Collection -> JSONObject()
                            .put("type", "collection")
                            .put("id", item.id)
                            .put("title", item.title)
                            .put("subtitle", item.subtitle)
                            .put("thumbnailUrl", item.thumbnailUrl)
                            .put("kind", item.kind)
                    }
                )
            }
        })

    private fun JSONObject.toShelf(): GeneratedShelf? {
        val id = optString("id")
        val title = optString("title")
        if (id.isBlank() || title.isBlank()) return null
        val items = optJSONArray("items").orEmpty().mapNotNull { raw ->
            val item = raw as? JSONObject ?: return@mapNotNull null
            when (item.optString("type")) {
                "track" -> item.optJSONObject("item")?.toMusicItem()?.let { ShelfItem.Track(it) }
                "collection" -> extractCollection(item)
                else -> null
            }
        }
        return GeneratedShelf(
            id = id,
            title = title,
            kind = runCatching { ShelfKind.valueOf(optString("kind")) }.getOrDefault(ShelfKind.TRENDING),
            layout = runCatching { ShelfLayout.valueOf(optString("layout")) }.getOrDefault(ShelfLayout.CAROUSEL),
            strapline = optString("strapline").ifBlank { null },
            subtitle = optString("subtitle").ifBlank { null },
            avatarUrl = optString("avatarUrl").ifBlank { null },
            moreEndpoint = optString("moreEndpoint").ifBlank { null },
            artistName = optString("artistName").ifBlank { null },
            items = items
        )
    }

    private fun extractCollection(item: JSONObject): ShelfItem.Collection? {
        val id = item.optString("id")
        if (id.isBlank()) return null
        return ShelfItem.Collection(
            id = id,
            title = item.optString("title"),
            subtitle = item.optString("subtitle"),
            thumbnailUrl = item.optString("thumbnailUrl"),
            kind = item.optString("kind").ifBlank { PlaybackContext.KIND_PLAYLIST }
        )
    }

    private fun MusicItem.toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("title", title)
        .put("artist", artist)
        .put("duration", duration)
        .put("thumbnailUrl", thumbnailUrl)
        .put("audioUrl", audioUrl)
        .put("videoUrl", videoUrl)
        .put("contentType", contentType.name)
        .put("artistId", artistId)
        .put("isPlaylist", isPlaylist)
        .put("albumTitle", albumTitle)

    private fun JSONObject.toMusicItem(): MusicItem? {
        val id = optString("id")
        if (id.isBlank()) return null
        return MusicItem(
            id = id,
            title = optString("title"),
            artist = optString("artist"),
            duration = optLong("duration"),
            thumbnailUrl = optString("thumbnailUrl"),
            audioUrl = optString("audioUrl"),
            videoUrl = optString("videoUrl"),
            isPlaylist = optBoolean("isPlaylist"),
            artistId = optString("artistId").ifBlank { null },
            contentType = runCatching { MusicContentType.valueOf(optString("contentType")) }
                .getOrDefault(MusicContentType.SONG),
            albumTitle = optString("albumTitle").ifBlank { null }
        )
    }

    private fun JSONArray?.orEmpty(): List<Any?> =
        if (this == null) emptyList() else (0 until length()).map { opt(it) }

    companion object {
        const val TTL_MS = 12 * 60 * 60 * 1000L
        private const val FILE_NAME = "home_shelf_cache.json"
        private const val MAX_ENTRIES = 4
    }
}
