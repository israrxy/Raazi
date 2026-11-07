package com.israrxy.raazi.data

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.israrxy.raazi.model.UpdateConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
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

    private companion object {
        const val UPDATE_DIR = "updates"
        const val APK_NAME = "raazi-update.apk"
        const val APK_PART_NAME = "raazi-update.apk.part"
        const val APK_PART_META_NAME = "raazi-update.apk.part.meta"
        const val META_NAME = "raazi-update.meta"
    }

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
     * Returns an already downloaded and verified APK for [config], if one is
     * still sitting in the app cache. This is what makes the updater resume
     * intelligently: a finished download is reused even if the install prompt
     * was dismissed or the app was killed, so the user never downloads the same
     * update twice.
     */
    suspend fun findDownloadedApk(
        context: Context,
        config: UpdateConfig
    ): File? = withContext(Dispatchers.IO) {
        val updateDir = File(context.cacheDir, UPDATE_DIR)
        val apkFile = File(updateDir, APK_NAME)
        val metaFile = File(updateDir, META_NAME)

        if (!apkFile.isFile || apkFile.length() <= 0L || !metaFile.isFile) {
            return@withContext null
        }

        val marker = runCatching { metaFile.readText() }.getOrNull()
        if (marker != cacheMarker(config)) {
            // Cached file belongs to a different update, or is a leftover.
            apkFile.delete()
            metaFile.delete()
            return@withContext null
        }

        if (!isValidUpdateApk(context, apkFile)) {
            apkFile.delete()
            metaFile.delete()
            return@withContext null
        }

        apkFile
    }

    /**
     * Downloads the update APK into the app cache so it can be handed to the
     * system package installer through a FileProvider.
     *
     * The download is resumable:
     *  - A fully downloaded, verified APK is reused without any network call.
     *  - Bytes already written by an interrupted attempt are kept in a partial
     *    file and requested again with an HTTP `Range` header.
     *  - The partial file is only promoted to the real APK once it is complete
     *    and parses as this app's package, so a half-written file can never be
     *    handed to the installer.
     */
    suspend fun downloadApk(
        context: Context,
        config: UpdateConfig,
        onProgress: (Int) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        findDownloadedApk(context, config)?.let { cached ->
            onProgress(100)
            return@withContext Result.success(cached)
        }

        val updateDir = File(context.cacheDir, UPDATE_DIR).apply { mkdirs() }
        val apkFile = File(updateDir, APK_NAME)
        val metaFile = File(updateDir, META_NAME)
        val partFile = File(updateDir, APK_PART_NAME)
        val partMetaFile = File(updateDir, APK_PART_META_NAME)
        val marker = cacheMarker(config)

        var resumeFrom = 0L
        var expectedTotal = 0L
        val partMeta = runCatching { partMetaFile.readText() }.getOrNull()
        if (partFile.isFile && partFile.length() > 0L && partMeta != null) {
            val lines = partMeta.split('\n')
            if (lines.size == 2 && lines[0] == marker) {
                resumeFrom = partFile.length()
                expectedTotal = lines[1].toLongOrNull() ?: 0L
            }
        }

        // A previous attempt may have written every byte but been interrupted
        // before promoting the file. Validate and promote instead of refetching.
        if (resumeFrom > 0L && expectedTotal > 0L && resumeFrom >= expectedTotal) {
            promotePartial(context, partFile, partMetaFile, apkFile, metaFile, marker, onProgress)
                ?.let { return@withContext it }
            resumeFrom = 0L
            expectedTotal = 0L
            partFile.delete()
            partMetaFile.delete()
        }

        if (resumeFrom == 0L) {
            // Drop stale APKs and unusable partials before a fresh download.
            apkFile.delete()
            metaFile.delete()
            partFile.delete()
            partMetaFile.delete()
        }

        val requestBuilder = Request.Builder()
            .url(config.downloadUrl)
            .header("Accept", "application/vnd.android.package-archive")
        if (resumeFrom > 0L) {
            requestBuilder.header("Range", "bytes=$resumeFrom-")
        }

        try {
            client.newCall(requestBuilder.build()).execute().use { response ->
                if (response.code == 416 && resumeFrom > 0L) {
                    // Nothing left to fetch; the partial file is already complete.
                    return@withContext promotePartial(
                        context, partFile, partMetaFile, apkFile, metaFile, marker, onProgress
                    ) ?: Result.failure(IOException("Downloaded file is not a valid Raazi APK"))
                }

                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        IOException("Download failed (HTTP ${response.code})")
                    )
                }

                val body = response.body
                    ?: return@withContext Result.failure(IOException("Download returned no data"))

                val appending = response.code == 206 && resumeFrom > 0L
                if (!appending) {
                    // Server ignored the range request: start the file over.
                    resumeFrom = 0L
                    expectedTotal = 0L
                    partFile.delete()
                }

                val contentLength = body.contentLength()
                val totalBytes = when {
                    appending && contentLength > 0L -> resumeFrom + contentLength
                    !appending && contentLength > 0L -> contentLength
                    else -> 0L
                }
                if (totalBytes > 0L) expectedTotal = totalBytes

                var writtenBytes = resumeFrom
                var lastPercent = -1

                body.byteStream().use { input ->
                    FileOutputStream(partFile, appending).use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            // Stop promptly if the dialog is dismissed mid-download.
                            currentCoroutineContext().ensureActive()
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            writtenBytes += read
                            if (totalBytes > 0L) {
                                val percent = ((writtenBytes * 100) / totalBytes)
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

                // Save the resume marker before validating, so even an abrupt
                // kill between these steps can resume from the bytes on disk.
                runCatching { partMetaFile.writeText("$marker\n$expectedTotal") }

                if (totalBytes > 0L && partFile.length() < totalBytes) {
                    return@withContext Result.failure(IOException("Download was interrupted"))
                }

                return@withContext promotePartial(
                    context, partFile, partMetaFile, apkFile, metaFile, marker, onProgress
                ) ?: Result.failure(IOException("Downloaded file is not a valid Raazi APK"))
            }
        } catch (e: CancellationException) {
            // Dialog dismissed: keep the bytes written so far for a later resume.
            if (partFile.length() > 0L) {
                runCatching { partMetaFile.writeText("$marker\n$expectedTotal") }
            }
            throw e
        } catch (e: Exception) {
            // Keep the partial file so the next attempt can resume it.
            if (partFile.length() > 0L) {
                runCatching { partMetaFile.writeText("$marker\n$expectedTotal") }
            }
            Log.e("UpdateManager", "Error downloading update", e)
            Result.failure(e)
        }
    }

    /**
     * Promotes a complete partial download to the real APK and writes the
     * completion marker. Returns null when the partial file is unusable, in
     * which case it is deleted so the next attempt starts clean.
     */
    private fun promotePartial(
        context: Context,
        partFile: File,
        partMetaFile: File,
        apkFile: File,
        metaFile: File,
        marker: String,
        onProgress: (Int) -> Unit
    ): Result<File>? {
        if (partFile.length() <= 0L) {
            partFile.delete()
            partMetaFile.delete()
            return null
        }

        if (!isValidUpdateApk(context, partFile)) {
            partFile.delete()
            partMetaFile.delete()
            return null
        }

        if (!partFile.renameTo(apkFile)) {
            partFile.delete()
            partMetaFile.delete()
            return null
        }

        // Marker is written last: its presence proves the APK is complete.
        runCatching { metaFile.writeText(marker) }
            .onFailure { Log.w("UpdateManager", "Could not write update marker", it) }
        partMetaFile.delete()

        onProgress(100)
        return Result.success(apkFile)
    }

    private fun cacheMarker(config: UpdateConfig): String {
        return "${config.latestVersionCode}|${config.downloadUrl}"
    }

    @Suppress("DEPRECATION")
    private fun isValidUpdateApk(context: Context, file: File): Boolean {
        val archiveInfo = context.packageManager.getPackageArchiveInfo(file.absolutePath, 0)
            ?: return false
        return archiveInfo.packageName == context.packageName
    }
}
