package com.israrxy.raazi.data

import android.util.Log
import com.google.gson.Gson
import com.israrxy.raazi.model.UpdateConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

class UpdateManager {
    private val client = OkHttpClient()
    private val gson = Gson()
    // GitHub raw URL for the update configuration file
    private val updateUrl = "https://raw.githubusercontent.com/israrxy/Raazi/main/update.json"

    suspend fun checkUpdate(): UpdateConfig? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(updateUrl)
            .build()
        
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e("UpdateManager", "Failed to fetch update config: ${response.code}")
                    return@withContext null
                }
                
                val body = response.body?.string()
                if (body.isNullOrEmpty()) {
                     Log.e("UpdateManager", "Empty response body")
                     return@withContext null
                }
                
                return@withContext gson.fromJson(body, UpdateConfig::class.java)
            }
        } catch (e: Exception) {
            Log.e("UpdateManager", "Error checking update", e)
            return@withContext null
        }
    }
}
