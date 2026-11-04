package com.israrxy.raazi.data.account

import android.content.Context
import android.util.Log
import com.google.android.gms.auth.GoogleAuthUtil
import com.israrxy.raazi.data.db.AppDatabase
import com.israrxy.raazi.data.db.PlaylistEntity
import com.israrxy.raazi.data.db.PlaylistTrackCrossRef
import com.israrxy.raazi.data.db.TrackEntity
import com.israrxy.raazi.data.local.SettingsDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * Manages zero-backend library backup & sync using the user's private Google Drive appDataFolder.
 * 
 * Target file: `raazi_sync_v1.json.gz`
 * Scope: `https://www.googleapis.com/auth/drive.appdata`
 */
object GoogleDriveSyncManager {
    private const val TAG = "GoogleDriveSync"
    const val DRIVE_APPDATA_SCOPE = "oauth2:https://www.googleapis.com/auth/drive.appdata"
    private const val SYNC_FILE_NAME = "raazi_sync_v1.json.gz"

    private const val DRIVE_FILES_ENDPOINT = "https://www.googleapis.com/drive/v3/files"
    private const val DRIVE_UPLOAD_ENDPOINT = "https://www.googleapis.com/upload/drive/v3/files"

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Retrieve an OAuth2 Bearer token for Google Drive appDataFolder using GoogleAuthUtil.
     */
    suspend fun getAccessToken(context: Context, email: String): String = withContext(Dispatchers.IO) {
        GoogleAuthUtil.getToken(context, email, DRIVE_APPDATA_SCOPE)
    }

    /**
     * Invalidate cached token if a request returns 401.
     */
    suspend fun invalidateToken(context: Context, token: String) = withContext(Dispatchers.IO) {
        try {
            GoogleAuthUtil.clearToken(context, token)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to clear token", e)
        }
    }

    /**
     * Perform full sync: download remote state (if exists), merge with local Room database,
     * and upload the unified state back to Google Drive appDataFolder.
     */
    suspend fun sync(
        context: Context,
        settingsDataStore: SettingsDataStore,
        database: AppDatabase
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val email = settingsDataStore.raaziUserEmail.first()
            if (email.isNullOrBlank()) {
                return@withContext Result.failure(Exception("Not signed into Raazi Google Account"))
            }

            var token = try {
                getAccessToken(context, email)
            } catch (e: Exception) {
                return@withContext Result.failure(Exception("Failed to get Drive authorization: ${e.message}"))
            }

            // 1. Find existing file in appDataFolder
            var fileInfo = findSyncFile(token)
            if (fileInfo == null) {
                // Retry once if token might be stale
                invalidateToken(context, token)
                token = getAccessToken(context, email)
                fileInfo = findSyncFile(token)
            }

            val remoteJson = if (fileInfo != null) {
                downloadSyncFile(token, fileInfo.first)
            } else null

            // 2. Restore remote data into local Room if available
            val lastFmKey = settingsDataStore.lastfmSessionKey.first()
            val lastFmUser = settingsDataStore.lastfmUsername.first()

            if (remoteJson != null) {
                restoreRemoteState(remoteJson, database, settingsDataStore, lastFmKey)
            }

            // 3. Serialize unified local state to compressed GZIP
            val localFavorites = database.musicDao().getAllTracks().first()
            val favoritesJson = JSONArray()
            for (f in localFavorites) {
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

            val localPlaylistsWithTracks = database.musicDao().getAllPlaylistsWithTracks().first()
            val playlistsJson = JSONArray()
            for (p in localPlaylistsWithTracks) {
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

            val unifiedState = JSONObject().apply {
                put("version", 1)
                put("updatedAt", System.currentTimeMillis())
                put("lastfmSessionKey", settingsDataStore.lastfmSessionKey.first() ?: "")
                put("lastfmUsername", settingsDataStore.lastfmUsername.first() ?: "")
                put("favorites", favoritesJson)
                put("playlists", playlistsJson)
            }

            val rawBytes = unifiedState.toString().toByteArray(Charsets.UTF_8)
            val gzippedBytes = compressGzip(rawBytes)

            // 4. Upload to appDataFolder (create or update)
            if (fileInfo != null) {
                updateSyncFile(token, fileInfo.first, gzippedBytes)
            } else {
                createSyncFile(token, gzippedBytes)
            }

            settingsDataStore.setRaaziLastSync(System.currentTimeMillis())
            Result.success("Sync completed successfully via Google Drive")
        } catch (e: Exception) {
            Log.e(TAG, "Google Drive sync error", e)
            Result.failure(e)
        }
    }

    private fun findSyncFile(token: String): Pair<String, String>? {
        val url = DRIVE_FILES_ENDPOINT.toHttpUrl().newBuilder()
            .addQueryParameter("spaces", "appDataFolder")
            .addQueryParameter("q", "name='$SYNC_FILE_NAME' and trashed=false")
            .addQueryParameter("fields", "files(id,name,modifiedTime)")
            .build()
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $token")
            .get()
            .build()

        val response = httpClient.newCall(request).execute()
        if (!response.isSuccessful) return null

        val bodyStr = response.body?.string().orEmpty()
        val filesArray = JSONObject(bodyStr).optJSONArray("files") ?: return null
        if (filesArray.length() == 0) return null

        val firstFile = filesArray.getJSONObject(0)
        return Pair(firstFile.getString("id"), firstFile.optString("modifiedTime"))
    }

    private fun downloadSyncFile(token: String, fileId: String): JSONObject? {
        val url = "$DRIVE_FILES_ENDPOINT/$fileId?alt=media"
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $token")
            .get()
            .build()

        val response = httpClient.newCall(request).execute()
        if (!response.isSuccessful) return null

        val rawBytes = response.body?.bytes() ?: return null
        val decompressedBytes = decompressGzip(rawBytes)
        val jsonStr = String(decompressedBytes, Charsets.UTF_8)
        return JSONObject(jsonStr)
    }

    private fun createSyncFile(token: String, gzippedBytes: ByteArray) {
        val mediaTypeRelated = "multipart/related".toMediaType()
        val jsonMeta = JSONObject().apply {
            put("name", SYNC_FILE_NAME)
            put("parents", JSONArray().put("appDataFolder"))
        }.toString()

        val requestBody = MultipartBody.Builder()
            .setType(mediaTypeRelated)
            .addPart(jsonMeta.toRequestBody("application/json; charset=UTF-8".toMediaType()))
            .addPart(gzippedBytes.toRequestBody("application/gzip".toMediaType()))
            .build()

        val request = Request.Builder()
            .url("$DRIVE_UPLOAD_ENDPOINT?uploadType=multipart")
            .addHeader("Authorization", "Bearer $token")
            .post(requestBody)
            .build()

        val response = httpClient.newCall(request).execute()
        if (!response.isSuccessful) {
            val err = response.body?.string().orEmpty()
            throw Exception("Failed to create file in appDataFolder: HTTP ${response.code} $err")
        }
    }

    private fun updateSyncFile(token: String, fileId: String, gzippedBytes: ByteArray) {
        val request = Request.Builder()
            .url("$DRIVE_UPLOAD_ENDPOINT/$fileId?uploadType=media")
            .addHeader("Authorization", "Bearer $token")
            .patch(gzippedBytes.toRequestBody("application/gzip".toMediaType()))
            .build()

        val response = httpClient.newCall(request).execute()
        if (!response.isSuccessful) {
            val err = response.body?.string().orEmpty()
            throw Exception("Failed to update file in appDataFolder: HTTP ${response.code} $err")
        }
    }

    private suspend fun restoreRemoteState(
        remoteJson: JSONObject,
        database: AppDatabase,
        settingsDataStore: SettingsDataStore,
        currentLastFmKey: String?
    ) {
        // 1. Last.fm restore
        val remoteLastFmKey = remoteJson.optString("lastfmSessionKey")
        val remoteLastFmUser = remoteJson.optString("lastfmUsername")
        if (remoteLastFmKey.isNotBlank() && currentLastFmKey.isNullOrBlank()) {
            settingsDataStore.setLastfmSession(remoteLastFmKey, remoteLastFmUser.ifBlank { null })
            settingsDataStore.setLastfmScrobbleEnabled(true)
        }

        // 2. Favorites restore
        val remoteFavorites = remoteJson.optJSONArray("favorites")
        if (remoteFavorites != null) {
            for (i in 0 until remoteFavorites.length()) {
                val fObj = remoteFavorites.optJSONObject(i) ?: continue
                val fId = fObj.optString("id")
                if (fId.isBlank()) continue

                val existing = database.musicDao().getTrack(fId)
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
                database.musicDao().insertTrack(entity)
            }
        }

        // 3. Playlists restore
        val remotePlaylists = remoteJson.optJSONArray("playlists")
        if (remotePlaylists != null) {
            val existingIds = database.musicDao().getAllPlaylists().first().map { it.id }.toSet()
            for (i in 0 until remotePlaylists.length()) {
                val pObj = remotePlaylists.optJSONObject(i) ?: continue
                val pId = pObj.optString("id")
                val pTitle = pObj.optString("title")
                val pDesc = pObj.optString("description")
                val pThumb = pObj.optString("thumbnailUrl")
                val pCustom = pObj.optString("customTitle")

                if (pId.isNotBlank() && pTitle.isNotBlank() && pId !in existingIds) {
                    database.musicDao().insertPlaylist(
                        PlaylistEntity(
                            id = pId,
                            title = pTitle,
                            description = pDesc.ifBlank { "Cloud playlist" },
                            thumbnailUrl = pThumb,
                            customTitle = pCustom.ifBlank { null }
                        )
                    )

                    val tracksArray = pObj.optJSONArray("tracks")
                    if (tracksArray != null) {
                        for (j in 0 until tracksArray.length()) {
                            val tObj = tracksArray.optJSONObject(j) ?: continue
                            val tId = tObj.optString("id")
                            if (tId.isNotBlank()) {
                                val existing = database.musicDao().getTrack(tId)
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
                                database.musicDao().insertTrack(entity)
                                database.musicDao().insertPlaylistTrackCrossRef(
                                    PlaylistTrackCrossRef(playlistId = pId, trackId = tId, position = j)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private fun compressGzip(data: ByteArray): ByteArray {
        val bos = ByteArrayOutputStream()
        GZIPOutputStream(bos).use { it.write(data) }
        return bos.toByteArray()
    }

    private fun decompressGzip(data: ByteArray): ByteArray {
        return GZIPInputStream(ByteArrayInputStream(data)).use { it.readBytes() }
    }
}
