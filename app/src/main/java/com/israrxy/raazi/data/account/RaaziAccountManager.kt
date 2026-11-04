package com.israrxy.raazi.data.account

import android.content.Context
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
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
                .setAutoSelectEnabled(true)
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

            // Collect local favorites
            val localFavorites = database.musicDao().getAllTracks().first().map { it.id }

            // Collect local playlists
            val localPlaylistsWithTracks = database.musicDao().getAllPlaylistsWithTracks().first()
            val playlistsJson = JSONArray()
            for (p in localPlaylistsWithTracks) {
                val pObj = JSONObject()
                pObj.put("id", p.playlist.id)
                pObj.put("title", p.playlist.title)
                pObj.put("trackIds", JSONArray(p.tracks.map { it.id }))
                playlistsJson.put(pObj)
            }

            val requestBodyJson = JSONObject().apply {
                put("lastfmSessionKey", lastFmKey ?: "")
                put("lastfmUsername", lastFmUser ?: "")
                put("favorites", JSONArray(localFavorites))
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
                return@withContext Result.failure(Exception("Cloud sync failed (${response.code})"))
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

                // Restore cloud playlists
                val cloudPlaylists = respJson.optJSONArray("playlists")
                if (cloudPlaylists != null) {
                    val existingIds = database.musicDao().getAllPlaylists().first().map { it.id }.toSet()
                    for (i in 0 until cloudPlaylists.length()) {
                        val pObj = cloudPlaylists.getJSONObject(i)
                        val pId = pObj.optString("id")
                        val pTitle = pObj.optString("title")
                        val trackIds = pObj.optJSONArray("trackIds")

                        if (pId.isNotBlank() && pTitle.isNotBlank() && pId !in existingIds) {
                            database.musicDao().insertPlaylist(
                                PlaylistEntity(
                                    id = pId,
                                    title = pTitle,
                                    description = "Cloud playlist",
                                    thumbnailUrl = ""
                                )
                            )
                            if (trackIds != null) {
                                for (j in 0 until trackIds.length()) {
                                    val tId = trackIds.getString(j)
                                    database.musicDao().insertPlaylistTrackCrossRef(
                                        PlaylistTrackCrossRef(playlistId = pId, trackId = tId, position = j)
                                    )
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
