package com.israrxy.raazi.data

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import android.util.Log
import com.google.gson.Gson
import com.israrxy.raazi.BuildConfig
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
import java.security.MessageDigest
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

        const val PREFS_NAME = "raazi_update"
        const val KEY_LAST_CHECK = "last_check_ms"
        const val KEY_LAST_CONFIG = "last_config_json"
        const val CHECK_INTERVAL_MS = 6 * 60 * 60 * 1000L
    }

    /**
     * Fetches the update config. Launch-time callers use the default throttling
     * (at most once every six hours) and fall back to the last fetched config,
     * while the manual Settings check passes [force] = true.
     */
    suspend fun checkUpdate(
        context: Context,
        force: Boolean = false
    ): UpdateConfig? = withContext(Dispatchers.IO) {
        if (!force && !shouldCheckNow(context)) {
            return@withContext readCachedConfig(context)
        }

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

                val config = gson.fromJson(body, UpdateConfig::class.java)
                if (config != null) {
                    markChecked(context)
                    cacheConfig(context, body)
                }
                return@withContext config
            }
        } catch (e: Exception) {
            Log.e("UpdateManager", "Error checking update", e)
            return@withContext readCachedConfig(context)
        }
    }

    /**
     * Removes a cached update once the installed app is already at least as new
     * as the downloaded APK, so a finished update does not sit in the cache
     * forever after a successful install.
     */
    suspend fun cleanupInstalledUpdate(context: Context) = withContext(Dispatchers.IO) {
        val updateDir = File(context.cacheDir, UPDATE_DIR)
        val marker = runCatching { File(updateDir, META_NAME).readText() }.getOrNull()
        val cachedVersionCode = marker?.substringBefore('|')?.toIntOrNull()
        if (cachedVersionCode != null && BuildConfig.VERSION_CODE >= cachedVersionCode) {
            updateDir.deleteRecursively()
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

        if (validationError(context, apkFile, config) != null) {
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
     *  - The partial file is only promoted to the real APK once it is complete,
     *    parses as this app's package, is at least the advertised version, and
     *    is signed with the same key as the installed app.
     *
     * [onProgress] receives the percentage and the total size in bytes (0 when
     * the server does not report a content length).
     */
    suspend fun downloadApk(
        context: Context,
        config: UpdateConfig,
        onProgress: (Int, Long) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        findDownloadedApk(context, config)?.let { cached ->
            onProgress(100, cached.length())
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
            return@withContext promotePartial(
                context, config, partFile, partMetaFile, apkFile, metaFile, marker, onProgress
            )
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
                        context, config, partFile, partMetaFile, apkFile, metaFile, marker, onProgress
                    )
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
                                    onProgress(percent, totalBytes)
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
                    context, config, partFile, partMetaFile, apkFile, metaFile, marker, onProgress
                )
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
     * completion marker. Any failure deletes the unusable file so the next
     * attempt starts clean.
     */
    private fun promotePartial(
        context: Context,
        config: UpdateConfig,
        partFile: File,
        partMetaFile: File,
        apkFile: File,
        metaFile: File,
        marker: String,
        onProgress: (Int, Long) -> Unit
    ): Result<File> {
        if (partFile.length() <= 0L) {
            partFile.delete()
            partMetaFile.delete()
            return Result.failure(IOException("The downloaded update is empty."))
        }

        val error = validationError(context, partFile, config)
        if (error != null) {
            partFile.delete()
            partMetaFile.delete()
            return Result.failure(IOException(error))
        }

        val size = partFile.length()
        if (!partFile.renameTo(apkFile)) {
            partFile.delete()
            partMetaFile.delete()
            return Result.failure(IOException("Could not store the downloaded update."))
        }

        // Marker is written last: its presence proves the APK is complete.
        runCatching { metaFile.writeText(marker) }
            .onFailure { Log.w("UpdateManager", "Could not write update marker", it) }
        partMetaFile.delete()

        onProgress(100, size)
        return Result.success(apkFile)
    }

    /**
     * Validates that [file] is this app, at least the advertised version, newer
     * than the installed build, and signed with the same key. Returns a
     * user-facing message on failure, or null when the APK is safe to install.
     */
    private fun validationError(context: Context, file: File, config: UpdateConfig): String? {
        val flags = signatureFlags()
        @Suppress("DEPRECATION")
        val archiveInfo = context.packageManager.getPackageArchiveInfo(file.absolutePath, flags)
            ?: return "The downloaded file is not a valid APK."

        if (archiveInfo.packageName != context.packageName) {
            return "The downloaded file belongs to a different app."
        }

        val archiveVersionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            archiveInfo.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            archiveInfo.versionCode.toLong()
        }

        if (archiveVersionCode < config.latestVersionCode) {
            return "The downloaded update is older than version ${config.latestVersionName}."
        }

        if (archiveVersionCode <= BuildConfig.VERSION_CODE.toLong()) {
            return "The downloaded update is not newer than the installed version."
        }

        val installedSigners = installedSignatureHashes(context)
        val archiveSigners = signatureHashes(archiveInfo)
        if (installedSigners != null && archiveSigners != null && installedSigners != archiveSigners) {
            return "This update isn't signed with the same key as your installed app. " +
                "Get it from the website instead."
        }

        return null
    }

    private fun signatureFlags(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            @Suppress("DEPRECATION")
            PackageManager.GET_SIGNATURES
        }
    }

    private fun installedSignatureHashes(context: Context): Set<String>? {
        return try {
            val info = context.packageManager.getPackageInfo(context.packageName, signatureFlags())
            signatureHashes(info)
        } catch (e: Exception) {
            Log.w("UpdateManager", "Could not read installed signatures", e)
            null
        }
    }

    private fun signatureHashes(info: PackageInfo): Set<String>? {
        val signatures: Array<Signature>? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.signingInfo?.apkContentsSigners
        } else {
            @Suppress("DEPRECATION")
            info.signatures
        }
        if (signatures.isNullOrEmpty()) return null

        return signatures.map { signature ->
            val digest = MessageDigest.getInstance("SHA-256")
                .digest(signature.toByteArray())
            digest.joinToString("") { byte -> "%02x".format(byte) }
        }.toSet()
    }

    private fun cacheMarker(config: UpdateConfig): String {
        return "${config.latestVersionCode}|${config.downloadUrl}"
    }

    private fun updatePrefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun cacheConfig(context: Context, json: String) {
        updatePrefs(context).edit()
            .putString(KEY_LAST_CONFIG, json)
            .apply()
    }

    private fun readCachedConfig(context: Context): UpdateConfig? {
        val json = updatePrefs(context).getString(KEY_LAST_CONFIG, null) ?: return null
        return runCatching { gson.fromJson(json, UpdateConfig::class.java) }.getOrNull()
    }

    private fun shouldCheckNow(context: Context): Boolean {
        val lastCheck = updatePrefs(context).getLong(KEY_LAST_CHECK, 0L)
        return System.currentTimeMillis() - lastCheck >= CHECK_INTERVAL_MS
    }

    private fun markChecked(context: Context) {
        updatePrefs(context).edit()
            .putLong(KEY_LAST_CHECK, System.currentTimeMillis())
            .apply()
    }
}
