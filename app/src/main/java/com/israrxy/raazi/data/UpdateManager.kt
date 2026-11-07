package com.israrxy.raazi.data

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.israrxy.raazi.model.UpdateConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

class UpdateManager {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
    private val gson = Gson()

    // GitHub raw URL for the update configuration file
    private val updateUrl = "https://raw.githubusercontent.com/israrxy/Raazi/main/update.json"

    suspend fun checkUpdate(): UpdateConfig? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$updateUrl?t=${System.currentTimeMillis()}")
            .header("Cache-Control", "no-cache, no-store, must-revalidate")
            .header("Pragma", "no-cache")
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

    /**
     * Downloads the update APK into the app cache so it can be handed to the
     * system package installer through a FileProvider. Progress is reported as
     * 0..100 and the result is only successful when the file is a valid APK for
     * this package.
     */
    suspend fun downloadApk(
        context: Context,
        config: UpdateConfig,
        onProgress: (Int) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(config.downloadUrl)
            .header("Accept", "application/vnd.android.package-archive")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        IOException("Download failed (HTTP ${response.code})")
                    )
                }

                val body = response.body
                    ?: return@withContext Result.failure(IOException("Download returned no data"))

                val updateDir = File(context.cacheDir, "updates").apply { mkdirs() }
                updateDir.listFiles()?.forEach { it.delete() }
                val apkFile = File(updateDir, "raazi-update.apk")

                val totalBytes = body.contentLength()
                var downloadedBytes = 0L
                var lastPercent = -1

                body.byteStream().use { input ->
                    apkFile.outputStream().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            downloadedBytes += read
                            if (totalBytes > 0L) {
                                val percent = ((downloadedBytes * 100) / totalBytes)
                                    .toInt()
                                    .coerceIn(0, 100)
                                if (percent != lastPercent) {
                                    lastPercent = percent
                                    onProgress(percent)
                                }
                            }
                        }
                    }
                }

                if (apkFile.length() <= 0L) {
                    apkFile.delete()
                    return@withContext Result.failure(IOException("Downloaded file is empty"))
                }

                if (!isValidUpdateApk(context, apkFile)) {
                    apkFile.delete()
                    return@withContext Result.failure(
                        IOException("Downloaded file is not a valid Raazi APK")
                    )
                }

                onProgress(100)
                Result.success(apkFile)
            }
        } catch (e: Exception) {
            Log.e("UpdateManager", "Error downloading update", e)
            Result.failure(e)
        }
    }

    @Suppress("DEPRECATION")
    private fun isValidUpdateApk(context: Context, file: File): Boolean {
        val archiveInfo = context.packageManager.getPackageArchiveInfo(file.absolutePath, 0)
            ?: return false
        return archiveInfo.packageName == context.packageName
    }
}
