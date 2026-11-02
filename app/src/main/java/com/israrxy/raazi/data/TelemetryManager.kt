package com.israrxy.raazi.data

import android.content.Context
import android.os.Build
import android.util.Log
import com.israrxy.raazi.BuildConfig
import com.israrxy.raazi.data.local.SettingsDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class TelemetryManager {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val telemetryUrl = "https://raazi.pages.dev/api/ping"
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun sendDailyPingIfEligible(context: Context, settingsDataStore: SettingsDataStore) = withContext(Dispatchers.IO) {
        try {
            val enabled = settingsDataStore.telemetryEnabled.first()
            if (!enabled) return@withContext

            val accepted = settingsDataStore.privacyPolicyAccepted.first()
            if (!accepted) return@withContext

            val lastPing = settingsDataStore.lastTelemetryPing.first()
            val now = System.currentTimeMillis()
            val oneDayMillis = 24 * 60 * 60 * 1000L

            if (now - lastPing < oneDayMillis) {
                return@withContext
            }

            val installId = settingsDataStore.getOrCreateAnonymousInstallId()
            val payload = JSONObject().apply {
                put("installId", installId)
                put("version", BuildConfig.VERSION_NAME)
                put("build", BuildConfig.VERSION_CODE)
                put("os", Build.VERSION.SDK_INT)
            }

            val body = payload.toString().toRequestBody(jsonMediaType)
            val request = Request.Builder()
                .url(telemetryUrl)
                .post(body)
                .header("User-Agent", "Raazi/${BuildConfig.VERSION_NAME} (Android ${Build.VERSION.SDK_INT})")
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    settingsDataStore.setLastTelemetryPing(now)
                    Log.d("TelemetryManager", "Daily ping recorded successfully")
                } else {
                    Log.d("TelemetryManager", "Daily ping returned code: ${response.code}")
                }
            }
        } catch (e: Exception) {
            Log.d("TelemetryManager", "Failed to send telemetry ping (ignoring): ${e.message}")
        }
    }
}
