package com.israrxy.raazi.data.account

import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.israrxy.raazi.data.db.AppDatabase
import com.israrxy.raazi.data.local.SettingsDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Manages Raazi Ecosystem user identity, Google Sign-In with Drive appDataFolder scope,
 * and zero-backend Google Drive library sync.
 */
object RaaziAccountManager {
    private const val TAG = "RaaziAccountManager"
    const val GOOGLE_WEB_CLIENT_ID = "974661534423-d8bo9cjl2a1tj3b1bnh7oe247qlcg1gt.apps.googleusercontent.com"
    val DRIVE_APPDATA_SCOPE = Scope("https://www.googleapis.com/auth/drive.appdata")

    /**
     * Build GoogleSignInClient requesting email, profile, and drive.appdata scope.
     */
    fun getGoogleSignInClient(context: Context): GoogleSignInClient {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestProfile()
            .requestScopes(DRIVE_APPDATA_SCOPE)
            .requestIdToken(GOOGLE_WEB_CLIENT_ID)
            .build()
        return GoogleSignIn.getClient(context, gso)
    }

    /**
     * Process Activity result from Google Sign-In intent.
     */
    suspend fun handleSignInResult(
        intent: Intent?,
        context: Context,
        settingsDataStore: SettingsDataStore
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val task = GoogleSignIn.getSignedInAccountFromIntent(intent)
            val account = task.getResult(ApiException::class.java)

            val email = account.email ?: return@withContext Result.failure(Exception("Google Account email not available"))
            val displayName = account.displayName ?: email
            val photoUrl = account.photoUrl?.toString()
            val idToken = account.idToken

            settingsDataStore.saveRaaziAccount(
                id = email,
                email = email,
                name = displayName,
                picture = photoUrl,
                idToken = idToken ?: ""
            )

            // Trigger initial sync to pull down existing cloud backup
            val app = context.applicationContext as? com.israrxy.raazi.RaaziApplication
            if (app != null) {
                GoogleDriveSyncManager.sync(context, settingsDataStore, app.database)
            }

            Result.success(displayName)
        } catch (e: ApiException) {
            Log.e(TAG, "Google Sign-In ApiException: code ${e.statusCode}", e)
            val msg = when (e.statusCode) {
                12501 -> "Sign-in cancelled"
                12500 -> "Sign-in configuration error. Check SHA-1 in Google Cloud Console."
                10 -> "Developer configuration error. Check Google Cloud Console setup."
                else -> "Google sign-in error (${e.statusCode})"
            }
            Result.failure(Exception(msg))
        } catch (e: Exception) {
            Log.e(TAG, "Sign in failed", e)
            Result.failure(e)
        }
    }

    /**
     * Sign out of Google and clear local Raazi account credentials.
     */
    suspend fun signOut(
        context: Context,
        settingsDataStore: SettingsDataStore
    ) = withContext(Dispatchers.IO) {
        try {
            getGoogleSignInClient(context).signOut()
        } catch (e: Exception) {
            Log.w(TAG, "Error signing out of Google client", e)
        }
        settingsDataStore.clearRaaziAccount()
    }

    /**
     * Perform sync with Google Drive appDataFolder.
     */
    suspend fun syncWithDrive(
        context: Context,
        settingsDataStore: SettingsDataStore,
        database: AppDatabase
    ): Result<String> {
        return GoogleDriveSyncManager.sync(context, settingsDataStore, database)
    }

    /**
     * Schedule a debounced background sync when favorites or playlists are modified.
     */
    fun scheduleAutoSync(context: Context) {
        RaaziDriveSyncWorker.scheduleDebouncedSync(context)
    }
}
