package com.israrxy.raazi.data.account

import android.content.Context
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.israrxy.raazi.data.db.AppDatabase
import com.israrxy.raazi.data.db.PlaylistEntity
import com.israrxy.raazi.data.db.PlaylistTrackCrossRef
import com.israrxy.raazi.data.db.TrackEntity
import com.israrxy.raazi.data.local.SettingsDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/**
 * Manages Raazi Ecosystem user identity, Google 1-Tap sign-in,
 * and Cloudflare/Cosmos DB sync for playlists, favorites, and Last.fm tokens.
 */
object RaaziAccountManager {
    private const val TAG = "RaaziAccountManager"
    const val GOOGLE_WEB_CLIENT_ID = "974661534423-d8bo9cjl2a1tj3b1bnh7oe247qlcg1gt.apps.googleusercontent.com"
    private const val SYNC_ENDPOINT = "https://raazi.pages.dev/api/sync"

    private val httpClient by lazy { OkHttpClient.Builder().build() }

    /**
     * Launch Android Credential Manager bottom-sheet for 1-tap Google Sign-In.
     */
    suspend fun signInWithGoogle(context: Context, settingsDataStore: SettingsDataStore): Result<String> = withContext(Dispatchers.Main) {
        try {
            val credentialManager = CredentialManager.create(context)
            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(GOOGLE_WEB_CLIENT_ID)
                .setAutoSelectEnabled(false)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            val response = credentialManager.getCredential(context, request)
            val credential = response.credential

            if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                val idToken = googleIdTokenCredential.idToken
                val email = googleIdTokenCredential.id
                val displayName = googleIdTokenCredential.displayName
                val photoUrl = googleIdTokenCredential.profilePictureUri?.toString()

                settingsDataStore.saveRaaziAccount(
                    id = email,
                    email = email,
                    name = displayName,
                    picture = photoUrl,
                    idToken = idToken
                )
                Result.success(displayName ?: email)
            } else {
                Result.failure(Exception("Unsupported credential type"))
            }
        } catch (_: GetCredentialCancellationException) {
            Result.failure(Exception("Sign-in cancelled"))
        } catch (e: NoCredentialException) {
            Log.e(TAG, "No credentials available", e)
            Result.failure(Exception("No Google account selected or Android Client ID not registered in Google Cloud Console"))
        } catch (e: Exception) {
            Log.e(TAG, "Google sign in failed", e)
            Result.failure(e)
        }
    }

    /**
     * Sign out of the Raazi Account.
     */
    suspend fun signOut(settingsDataStore: SettingsDataStore) = withContext(Dispatchers.IO) {
        settingsDataStore.clearRaaziAccount()
    }

    /**
     * Sync local favorites, playlists, and Last.fm token with Cloudflare Pages & Azure Cosmos DB.
     */
    suspend fun syncWithCloud(
        settingsDataStore: SettingsDataStore,
        database: AppDatabase
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val idToken = settingsDataStore.raaziGoogleIdToken.first()
            if (idToken.isNullOrBlank()) {
                return@withContext Result.failure(Exception("Please sign in to Raazi first"))
            }

            val lastFmKey = settingsDataStore.lastfmSessionKey.first()
            val lastFmUser = settingsDataStore.lastfmUsername.first()

            // Collect local favorites with full metadata
            val localFavoriteTracks = database.musicDao().getAllTracks().first()
            val favoritesJson = JSONArray()
            for (f in localFavoriteTracks) {
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

            // Collect local playlists with tracks
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

            val requestBodyJson = JSONObject().apply {
                put("lastfmSessionKey", lastFmKey ?: "")
                put("lastfmUsername", lastFmUser ?: "")
                put("favorites", favoritesJson)
                put("playlists", playlistsJson)
            }

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val request = Request.Builder()
                .url(SYNC_ENDPOINT)
                .addHeader("Authorization", "Bearer $idToken")
                .post(requestBodyJson.toString().toRequestBody(mediaType))
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                val errBody = response.body?.string().orEmpty()
                val serverMsg = try {
                    JSONObject(errBody).optString("error", errBody)
                } catch (_: Exception) {
                    errBody
                }
                val detail = if (serverMsg.isNotBlank()) serverMsg else "HTTP ${response.code}"
                return@withContext Result.failure(Exception("Cloud sync failed: $detail"))
            }

            val responseBody = response.body?.string().orEmpty()
            if (responseBody.isNotBlank()) {
                val respJson = JSONObject(responseBody)

                // Restore Last.fm if cloud had it
                val cloudLastFmKey = respJson.optString("lastfmSessionKey")
                val cloudLastFmUser = respJson.optString("lastfmUsername")
                if (cloudLastFmKey.isNotBlank() && lastFmKey.isNullOrBlank()) {
                    settingsDataStore.setLastfmSession(cloudLastFmKey, cloudLastFmUser.ifBlank { null })
                    settingsDataStore.setLastfmScrobbleEnabled(true)
                }

                // Restore favorites
                val cloudFavorites = respJson.optJSONArray("favorites")
                if (cloudFavorites != null) {
                    for (i in 0 until cloudFavorites.length()) {
                        val fItem = cloudFavorites.opt(i)
                        val fObj = fItem as? JSONObject
                        val fId = fObj?.optString("id") ?: fItem as? String ?: continue
                        if (fId.isBlank()) continue

                        val existing = database.musicDao().getTrack(fId)
                        val entity = TrackEntity(
                            id = fId,
                            title = fObj?.optString("title")?.ifBlank { null } ?: existing?.title ?: "Track",
                            artist = fObj?.optString("artist")?.ifBlank { null } ?: existing?.artist ?: "Unknown Artist",
                            duration = fObj?.optLong("duration", 0L) ?: existing?.duration ?: 0L,
                            thumbnailUrl = fObj?.optString("thumbnailUrl")?.ifBlank { null } ?: existing?.thumbnailUrl ?: "",
                            audioUrl = fObj?.optString("audioUrl")?.ifBlank { null } ?: existing?.audioUrl ?: "",
                            videoUrl = fObj?.optString("videoUrl")?.ifBlank { null } ?: existing?.videoUrl ?: "",
                            isLive = fObj?.optBoolean("isLive", false) ?: existing?.isLive ?: false,
                            localPath = existing?.localPath,
                            isFavorite = true,
                            favoriteSyncState = TrackEntity.FAVORITE_SYNCED
                        )
                        database.musicDao().insertTrack(entity)
                    }
                }

                // Restore cloud playlists
                val cloudPlaylists = respJson.optJSONArray("playlists")
                if (cloudPlaylists != null) {
                    val existingIds = database.musicDao().getAllPlaylists().first().map { it.id }.toSet()
                    for (i in 0 until cloudPlaylists.length()) {
                        val pObj = cloudPlaylists.optJSONObject(i) ?: continue
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
                            val trackIdsArray = pObj.optJSONArray("trackIds")

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
                            } else if (trackIdsArray != null) {
                                for (j in 0 until trackIdsArray.length()) {
                                    val tId = trackIdsArray.optString(j)
                                    if (tId.isNotBlank()) {
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

            settingsDataStore.setRaaziLastSync(System.currentTimeMillis())
            Result.success("Sync completed successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Sync error", e)
            Result.failure(e)
        }
    }
}
