package com.israrxy.raazi.data.account

import com.israrxy.raazi.data.db.AppDatabase
import com.israrxy.raazi.data.db.PlaybackHistoryEntity
import com.israrxy.raazi.data.db.PlaylistEntity
import com.israrxy.raazi.data.db.PlaylistTrackCrossRef
import com.israrxy.raazi.data.db.PlaylistWithTracks
import com.israrxy.raazi.data.db.SavedCollectionEntity
import com.israrxy.raazi.data.db.TrackEntity
import com.israrxy.raazi.data.local.SettingsDataStore
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject

/**
 * Encapsulates serialization and deserialization of the unified Raazi sync state (v2).
 * Covers favorites, playlists, playback history, saved collections, and algorithmic feed taste profile.
 */
object GoogleDriveSyncPayload {

    data class FeedProfileDto(
        val topArtists: List<String> = emptyList(),
        val seedTrackIds: List<String> = emptyList(),
        val totalPlayCount: Int = 0
    )

    data class RestoreSummary(
        val favoritesRestored: Int,
        val playlistsRestored: Int,
        val historyRestored: Int,
        val collectionsRestored: Int,
        val feedProfile: FeedProfileDto
    )

    /**
     * Serializes complete on-device library, history, collections, and taste profile into JSON (Version 2).
     */
    fun buildUnifiedStateJson(
        favorites: List<TrackEntity>,
        playlistsWithTracks: List<PlaylistWithTracks>,
        history: List<PlaybackHistoryEntity>,
        savedCollections: List<SavedCollectionEntity>,
        lastfmSessionKey: String?,
        lastfmUsername: String?
    ): JSONObject {
        val favoritesJson = JSONArray()
        for (f in favorites) {
            favoritesJson.put(JSONObject().apply {
                put("id", f.id)
                put("title", f.title)
                put("artist", f.artist)
                put("duration", f.duration)
                put("thumbnailUrl", f.thumbnailUrl)
                put("audioUrl", f.audioUrl)
                put("videoUrl", f.videoUrl)
                put("isLive", f.isLive)
            })
        }

        val playlistsJson = JSONArray()
        for (p in playlistsWithTracks) {
            val pObj = JSONObject().apply {
                put("id", p.playlist.id)
                put("title", p.playlist.title)
                put("description", p.playlist.description)
                put("thumbnailUrl", p.playlist.thumbnailUrl)
                put("customTitle", p.playlist.customTitle ?: "")
                val tracksJson = JSONArray()
                for (t in p.tracks) {
                    tracksJson.put(JSONObject().apply {
                        put("id", t.id)
                        put("title", t.title)
                        put("artist", t.artist)
                        put("duration", t.duration)
                        put("thumbnailUrl", t.thumbnailUrl)
                        put("audioUrl", t.audioUrl)
                        put("videoUrl", t.videoUrl)
                        put("isLive", t.isLive)
                    })
                }
                put("tracks", tracksJson)
            }
            playlistsJson.put(pObj)
        }

        val historyJson = JSONArray()
        for (h in history) {
            historyJson.put(JSONObject().apply {
                put("id", h.id)
                put("title", h.title)
                put("artist", h.artist)
                put("thumbnailUrl", h.thumbnailUrl)
                put("audioUrl", h.audioUrl)
                put("videoUrl", h.videoUrl)
                put("duration", h.duration)
                put("timestamp", h.timestamp)
                put("playCount", h.playCount)
            })
        }

        val collectionsJson = JSONArray()
        for (c in savedCollections) {
            collectionsJson.put(JSONObject().apply {
                put("id", c.id)
                put("sourceId", c.sourceId)
                put("title", c.title)
                put("subtitle", c.subtitle)
                put("thumbnailUrl", c.thumbnailUrl)
                put("contentType", c.contentType)
                put("timestamp", c.timestamp)
            })
        }

        // Calculate algorithmic taste profile from history and favorites
        val topArtists = history
            .groupBy { it.artist }
            .mapValues { entry -> entry.value.sumOf { it.playCount } }
            .entries
            .sortedByDescending { it.value }
            .take(10)
            .map { it.key }

        val seedTrackIds = history
            .sortedByDescending { it.playCount }
            .take(10)
            .map { it.id }

        val totalPlayCount = history.sumOf { it.playCount }

        val feedProfileJson = JSONObject().apply {
            val artistsArray = JSONArray()
            topArtists.forEach { artistsArray.put(it) }
            put("topArtists", artistsArray)

            val seedsArray = JSONArray()
            seedTrackIds.forEach { seedsArray.put(it) }
            put("seedTrackIds", seedsArray)

            put("totalPlayCount", totalPlayCount)
        }

        return JSONObject().apply {
            put("version", 2)
            put("updatedAt", System.currentTimeMillis())
            put("lastfmSessionKey", lastfmSessionKey ?: "")
            put("lastfmUsername", lastfmUsername ?: "")
            put("favorites", favoritesJson)
            put("playlists", playlistsJson)
            put("history", historyJson)
            put("savedCollections", collectionsJson)
            put("feedProfile", feedProfileJson)
        }
    }

    /**
     * Restores remote JSON into Room database with smart merge and conflict resolution.
     */
    suspend fun restoreUnifiedState(
        remoteJson: JSONObject,
        database: AppDatabase,
        settingsDataStore: SettingsDataStore,
        currentLastFmKey: String?
    ): RestoreSummary {
        val dao = database.musicDao()

        // 1. Last.fm restore
        val remoteLastFmKey = remoteJson.optString("lastfmSessionKey")
        val remoteLastFmUser = remoteJson.optString("lastfmUsername")
        if (remoteLastFmKey.isNotBlank() && currentLastFmKey.isNullOrBlank()) {
            settingsDataStore.setLastfmSession(remoteLastFmKey, remoteLastFmUser.ifBlank { null })
            settingsDataStore.setLastfmScrobbleEnabled(true)
        }

        // 2. Favorites restore
        var favoritesCount = 0
        val remoteFavorites = remoteJson.optJSONArray("favorites")
        if (remoteFavorites != null) {
            for (i in 0 until remoteFavorites.length()) {
                val fObj = remoteFavorites.optJSONObject(i) ?: continue
                val fId = fObj.optString("id")
                if (fId.isBlank()) continue

                val existing = dao.getTrack(fId)
                val entity = TrackEntity(
                    id = fId,
                    title = fObj.optString("title").ifBlank { existing?.title ?: "Track" },
                    artist = fObj.optString("artist").ifBlank { existing?.artist ?: "Unknown Artist" },
                    duration = fObj.optLong("duration", 0L).takeIf { it > 0 } ?: existing?.duration ?: 0L,
                    thumbnailUrl = fObj.optString("thumbnailUrl").ifBlank { existing?.thumbnailUrl ?: "" },
                    audioUrl = fObj.optString("audioUrl").ifBlank { existing?.audioUrl ?: "" },
                    videoUrl = fObj.optString("videoUrl").ifBlank { existing?.videoUrl ?: "" },
                    isLive = fObj.optBoolean("isLive", false),
                    localPath = existing?.localPath,
                    isFavorite = true,
                    favoriteSyncState = TrackEntity.FAVORITE_SYNCED
                )
                dao.insertTrack(entity)
                favoritesCount++
            }
        }

        // 3. Playlists restore
        var playlistsCount = 0
        val remotePlaylists = remoteJson.optJSONArray("playlists")
        if (remotePlaylists != null) {
            val existingIds = dao.getAllPlaylists().first().map { it.id }.toSet()
            for (i in 0 until remotePlaylists.length()) {
                val pObj = remotePlaylists.optJSONObject(i) ?: continue
                val pId = pObj.optString("id")
                val pTitle = pObj.optString("title")
                val pDesc = pObj.optString("description")
                val pThumb = pObj.optString("thumbnailUrl")
                val pCustom = pObj.optString("customTitle")

                if (pId.isNotBlank() && pTitle.isNotBlank() && pId !in existingIds) {
                    dao.insertPlaylist(
                        PlaylistEntity(
                            id = pId,
                            title = pTitle,
                            description = pDesc.ifBlank { "Cloud playlist" },
                            thumbnailUrl = pThumb,
                            customTitle = pCustom.ifBlank { null }
                        )
                    )
                    playlistsCount++

                    val tracksArray = pObj.optJSONArray("tracks")
                    if (tracksArray != null) {
                        for (j in 0 until tracksArray.length()) {
                            val tObj = tracksArray.optJSONObject(j) ?: continue
                            val tId = tObj.optString("id")
                            if (tId.isNotBlank()) {
                                val existing = dao.getTrack(tId)
                                val entity = TrackEntity(
                                    id = tId,
                                    title = tObj.optString("title").ifBlank { existing?.title ?: "Track" },
                                    artist = tObj.optString("artist").ifBlank { existing?.artist ?: "Unknown Artist" },
                                    duration = tObj.optLong("duration", 0L),
                                    thumbnailUrl = tObj.optString("thumbnailUrl").ifBlank { existing?.thumbnailUrl ?: "" },
                                    audioUrl = tObj.optString("audioUrl").ifBlank { existing?.audioUrl ?: "" },
                                    videoUrl = tObj.optString("videoUrl").ifBlank { existing?.videoUrl ?: "" },
                                    isLive = tObj.optBoolean("isLive", false),
                                    localPath = existing?.localPath,
                                    isFavorite = existing?.isFavorite ?: false,
                                    favoriteSyncState = existing?.favoriteSyncState ?: TrackEntity.FAVORITE_SYNCED
                                )
                                dao.insertTrack(entity)
                                dao.insertPlaylistTrackCrossRef(
                                    PlaylistTrackCrossRef(playlistId = pId, trackId = tId, position = j)
                                )
                            }
                        }
                    }
                }
            }
        }

        // 4. Playback History restore & merge
        var historyCount = 0
        val remoteHistory = remoteJson.optJSONArray("history")
        if (remoteHistory != null) {
            for (i in 0 until remoteHistory.length()) {
                val hObj = remoteHistory.optJSONObject(i) ?: continue
                val hId = hObj.optString("id")
                if (hId.isBlank()) continue

                val remotePlayCount = hObj.optInt("playCount", 1)
                val remoteTimestamp = hObj.optLong("timestamp", System.currentTimeMillis())

                val existing = dao.getPlaybackHistoryItem(hId)
                val mergedPlayCount = if (existing != null) maxOf(existing.playCount, remotePlayCount) else remotePlayCount
                val mergedTimestamp = if (existing != null) maxOf(existing.timestamp, remoteTimestamp) else remoteTimestamp

                val historyEntity = PlaybackHistoryEntity(
                    id = hId,
                    title = hObj.optString("title").ifBlank { existing?.title ?: "Track" },
                    artist = hObj.optString("artist").ifBlank { existing?.artist ?: "Unknown Artist" },
                    thumbnailUrl = hObj.optString("thumbnailUrl").ifBlank { existing?.thumbnailUrl ?: "" },
                    audioUrl = hObj.optString("audioUrl").ifBlank { existing?.audioUrl ?: "" },
                    videoUrl = hObj.optString("videoUrl").ifBlank { existing?.videoUrl ?: hId },
                    duration = hObj.optLong("duration", 0L),
                    timestamp = mergedTimestamp,
                    playCount = mergedPlayCount
                )
                dao.insertPlaybackHistory(historyEntity)
                historyCount++
            }
        }

        // 5. Saved Collections restore
        var collectionsCount = 0
        val remoteCollections = remoteJson.optJSONArray("savedCollections")
        if (remoteCollections != null) {
            for (i in 0 until remoteCollections.length()) {
                val cObj = remoteCollections.optJSONObject(i) ?: continue
                val cId = cObj.optString("id")
                val cSourceId = cObj.optString("sourceId")
                if (cId.isBlank()) continue

                val existing = dao.getSavedCollectionById(cId)
                if (existing == null) {
                    dao.insertSavedCollection(
                        SavedCollectionEntity(
                            id = cId,
                            sourceId = cSourceId.ifBlank { cId },
                            title = cObj.optString("title"),
                            subtitle = cObj.optString("subtitle"),
                            thumbnailUrl = cObj.optString("thumbnailUrl"),
                            contentType = cObj.optString("contentType"),
                            timestamp = cObj.optLong("timestamp", System.currentTimeMillis())
                        )
                    )
                    collectionsCount++
                }
            }
        }

        // 6. Extract or compute FeedProfileDto
        val profileObj = remoteJson.optJSONObject("feedProfile")
        val feedProfile = if (profileObj != null) {
            val topArtists = mutableListOf<String>()
            val artistsArr = profileObj.optJSONArray("topArtists")
            if (artistsArr != null) {
                for (k in 0 until artistsArr.length()) {
                    topArtists.add(artistsArr.getString(k))
                }
            }

            val seedTracks = mutableListOf<String>()
            val seedsArr = profileObj.optJSONArray("seedTrackIds")
            if (seedsArr != null) {
                for (k in 0 until seedsArr.length()) {
                    seedTracks.add(seedsArr.getString(k))
                }
            }

            FeedProfileDto(
                topArtists = topArtists,
                seedTrackIds = seedTracks,
                totalPlayCount = profileObj.optInt("totalPlayCount", 0)
            )
        } else {
            FeedProfileDto()
        }

        return RestoreSummary(
            favoritesRestored = favoritesCount,
            playlistsRestored = playlistsCount,
            historyRestored = historyCount,
            collectionsRestored = collectionsCount,
            feedProfile = feedProfile
        )
    }
}
