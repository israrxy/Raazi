package com.israrxy.raazi.service

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Environment
import android.os.StatFs
import android.util.Log
import com.israrxy.raazi.data.db.DownloadEntity
import com.israrxy.raazi.model.MusicItem
import kotlinx.coroutines.ensureActive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import kotlin.coroutines.coroutineContext

private const val FILE_HELPER_TAG = "DownloadFileHelper"
internal const val RAAZI_DOWNLOAD_DIR = "Raazi"
private const val STREAM_BUFFER_SIZE = 8192

internal suspend fun streamTrackToFile(
    context: Context,
    httpClient: OkHttpClient,
    track: MusicItem,
    audioUrl: String,
    onProgress: (progress: Int, downloadedBytes: Long) -> Unit
): File {
    val downloadDir = File(
        context.getExternalFilesDir(Environment.DIRECTORY_MUSIC),
        RAAZI_DOWNLOAD_DIR
    )
    if (!downloadDir.exists()) downloadDir.mkdirs()

    val fileName = buildDownloadFileName(track)
    val outputFile = File(downloadDir, fileName)
    val tempFile = File(downloadDir, "$fileName.tmp")

    val request = Request.Builder()
        .url(audioUrl)
        .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
        .build()

    Log.d(FILE_HELPER_TAG, "Starting OkHttp download for ${track.title}")
    val startTime = System.currentTimeMillis()

    httpClient.newCall(request).execute().use { response ->
        if (!response.isSuccessful) {
            throw IllegalStateException("HTTP ${response.code}: ${response.message}")
        }

        val body = response.body ?: throw IllegalStateException("Empty response body")
        val contentLength = body.contentLength()
        var downloaded = 0L
        var lastProgressUpdate = 0L
        var lastEmittedProgress = -1

        body.byteStream().use { inputStream ->
            FileOutputStream(tempFile).use { outputStream ->
                val buffer = ByteArray(STREAM_BUFFER_SIZE)
                var bytesRead: Int

                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    coroutineContext.ensureActive()

                    outputStream.write(buffer, 0, bytesRead)
                    downloaded += bytesRead

                    val progress = if (contentLength > 0) {
                        (downloaded * 100 / contentLength).toInt()
                    } else 0
                    val now = System.currentTimeMillis()
                    if (progress > lastEmittedProgress || now - lastProgressUpdate > 500 || downloaded == contentLength) {
                        onProgress(progress, downloaded)
                        lastProgressUpdate = now
                        lastEmittedProgress = progress
                    }
                }
            }
        }

        if (tempFile.exists()) {
            if (outputFile.exists()) outputFile.delete()
            val renamed = tempFile.renameTo(outputFile)
            if (!renamed) {
                try {
                    tempFile.copyTo(outputFile, overwrite = true)
                    tempFile.delete()
                } catch (e: Exception) {
                    Log.e(FILE_HELPER_TAG, "File rename AND copy failed for ${track.title}", e)
                    throw e
                }
            }
        }

        val elapsed = System.currentTimeMillis() - startTime
        val speedKBps = if (elapsed > 0) (downloaded / 1024) / (elapsed / 1000.0) else 0.0
        Log.i(
            FILE_HELPER_TAG,
            "Download complete: ${track.title} | ${downloaded / 1024}KB in ${elapsed}ms (${String.format("%.1f", speedKBps)} KB/s)"
        )
    }

    return outputFile
}

internal fun buildDownloadFileName(track: MusicItem): String {
    val sanitizedTitle = track.title.replace(Regex("[^a-zA-Z0-9._\\- ]"), "_").take(60)
    val sanitizedId = track.id.replace(Regex("[^a-zA-Z0-9._-]"), "_")
    return "${sanitizedTitle}_${sanitizedId}.m4a"
}

internal fun cleanupTempDownloadFile(context: Context, download: DownloadEntity) {
    try {
        val downloadDir = File(
            context.getExternalFilesDir(Environment.DIRECTORY_MUSIC),
            RAAZI_DOWNLOAD_DIR
        )
        val sanitizedTrackId = download.trackId.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val tempFiles = downloadDir.listFiles()?.filter {
            it.name.contains(sanitizedTrackId) && it.name.endsWith(".tmp")
        }
        tempFiles?.forEach { it.delete() }
    } catch (e: Exception) {
        Log.e(FILE_HELPER_TAG, "Failed to cleanup temp files", e)
    }
}

internal fun isDeviceConnectedToWifi(context: Context): Boolean {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val network = cm.activeNetwork ?: return false
    val capabilities = cm.getNetworkCapabilities(network) ?: return false
    return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
}

internal fun calculateDownloadStorageUsed(context: Context): Long {
    val downloadDir = File(
        context.getExternalFilesDir(Environment.DIRECTORY_MUSIC),
        RAAZI_DOWNLOAD_DIR
    )
    if (!downloadDir.exists()) return 0L
    return downloadDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
}

internal fun calculateAvailableStorage(context: Context): Long {
    val appDir = context.getExternalFilesDir(null) ?: return 0L
    val stat = StatFs(appDir.path)
    return stat.availableBlocksLong * stat.blockSizeLong
}
