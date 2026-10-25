package com.israrxy.raazi.service

import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.provider.Settings
import android.util.Log
import com.israrxy.raazi.RaaziApplication
import com.israrxy.raazi.model.MusicItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

class RingtoneHelper(private val context: Context) {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    fun hasWriteSettingsPermission(): Boolean {
        return try {
            Settings.System.canWrite(context)
        } catch (_: Exception) {
            false
        }
    }

    fun buildWriteSettingsIntent(): Intent? {
        return try {
            Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun resolveLocalSource(url: String): File? {
        return try {
            when {
                url.startsWith("file://") -> {
                    val path = Uri.parse(url).path ?: return null
                    File(path)
                }
                url.startsWith("/") -> File(url)
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }

    suspend fun downloadAudio(
        track: MusicItem,
        extractor: YouTubeMusicExtractor,
        onProgress: ((Int) -> Unit)? = null
    ): Pair<String, Long> = withContext(Dispatchers.IO) {
        onProgress?.invoke(0)

        val cacheDir = File(context.cacheDir, "ringtones")
        if (!cacheDir.exists()) cacheDir.mkdirs()

        val safeId = track.id.replace(Regex("[^A-Za-z0-9_-]"), "_").take(64).ifEmpty { "track" }

        val existingLocalPath = track.localPath?.takeIf { it.isNotBlank() }
        if (existingLocalPath != null) {
            val localFile = resolveLocalSource(existingLocalPath)
            if (localFile != null && localFile.exists() && localFile.length() > 0L && !localFile.name.endsWith(".flac", ignoreCase = true)) {
                val ext = if (isWebmAudioFormat(localFile.absolutePath)) "webm" else "m4a"
                val tempFile = File(cacheDir, "${safeId}_raw.$ext")
                localFile.inputStream().use { input ->
                    FileOutputStream(tempFile).use { output ->
                        input.copyTo(output, DEFAULT_BUFFER_SIZE)
                    }
                }
                onProgress?.invoke(100)
                val extractedDuration = extractAudioDurationMs(tempFile.absolutePath)
                val localDuration = when {
                    extractedDuration > 0L -> extractedDuration
                    track.duration > 0L -> track.duration
                    else -> 30_000L
                }
                return@withContext Pair(tempFile.absolutePath, localDuration)
            }
        }

        var resolvedUserAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36"
        val sourceVideoId = track.videoUrl.ifBlank { track.id }
        val audioUrl = if (track.audioUrl.isNotEmpty() &&
            !track.audioUrl.contains("youtube.com") &&
            !track.audioUrl.contains("googlevideo.com") &&
            !track.audioUrl.endsWith(".flac", ignoreCase = true)
        ) {
            track.audioUrl
        } else {
            try {
                val streamResult = com.israrxy.raazi.player.StreamResolver.resolveStreamUrl(
                    videoIdInput = sourceVideoId,
                    title = track.title,
                    artist = track.artist,
                    mode = com.israrxy.raazi.model.PlaybackMediaMode.AUDIO,
                    preferAac = true
                )
                resolvedUserAgent = streamResult.userAgent
                streamResult.url
            } catch (e: Exception) {
                Log.w("RingtoneHelper", "StreamResolver failed for ringtone, falling back to extractor", e)
                extractor.getAudioStreamUrl(sourceVideoId)
            }
        }
        if (audioUrl.isEmpty()) {
            throw Exception("Could not resolve audio stream")
        }

        onProgress?.invoke(5)
        val rawTempFile = File(cacheDir, "${safeId}_download.tmp")

        val localSource = resolveLocalSource(audioUrl)
        if (localSource != null) {
            if (!localSource.exists()) {
                throw Exception("Local audio file is missing: ${localSource.absolutePath}")
            }
            localSource.inputStream().use { input ->
                FileOutputStream(rawTempFile).use { output ->
                    input.copyTo(output, DEFAULT_BUFFER_SIZE)
                }
            }
            val finalExt = if (isWebmAudioFormat(rawTempFile.absolutePath)) "webm" else "m4a"
            val finalFile = File(cacheDir, "${safeId}_raw.$finalExt")
            rawTempFile.copyTo(finalFile, overwrite = true)
            rawTempFile.delete()
            onProgress?.invoke(100)
            val extractedDuration = extractAudioDurationMs(finalFile.absolutePath)
            val localDuration = when {
                extractedDuration > 0L -> extractedDuration
                track.duration > 0L -> track.duration
                else -> 30_000L
            }
            return@withContext Pair(finalFile.absolutePath, localDuration)
        }

        var downloadUrl = audioUrl
        if ((downloadUrl.contains("youtube.com") || downloadUrl.contains("googlevideo.com")) && !downloadUrl.contains("&range=")) {
            try {
                val musicDao = RaaziApplication.instance.database.musicDao()
                val format = musicDao.getFormat(track.id)
                val length = if (format != null && format.contentLength > 0L) {
                    format.contentLength
                } else {
                    15_000_000L
                }
                downloadUrl += "&range=0-$length"
                Log.d("RingtoneHelper", "Bypassing YouTube throttling for ringtone ${track.title} with range=0-$length")
            } catch (e: Exception) {
                Log.w("RingtoneHelper", "Failed to query format info for range parameter", e)
            }
        }

        val request = Request.Builder()
            .url(downloadUrl)
            .header("User-Agent", resolvedUserAgent)
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("Failed to download audio (HTTP ${response.code})")

            val body = response.body ?: throw Exception("Empty response body")
            val contentLength = body.contentLength()
            var downloaded = 0L
            var lastProgressUpdate = 0L
            val buffer = ByteArray(8192)

            body.byteStream().use { input ->
                FileOutputStream(rawTempFile).use { output ->
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        downloaded += bytesRead
                        val now = System.currentTimeMillis()
                        if (now - lastProgressUpdate > 100 || downloaded == contentLength) {
                            val progress = if (contentLength > 0) {
                                5 + ((downloaded * 95 / contentLength).toInt()).coerceIn(0, 95)
                            } else 50
                            onProgress?.invoke(progress)
                            lastProgressUpdate = now
                        }
                    }
                }
            }
        }

        val finalExt = if (isWebmAudioFormat(rawTempFile.absolutePath)) "webm" else "m4a"
        val tempFile = File(cacheDir, "${safeId}_raw.$finalExt")
        rawTempFile.copyTo(tempFile, overwrite = true)
        rawTempFile.delete()

        onProgress?.invoke(100)
        val extractedDuration = extractAudioDurationMs(tempFile.absolutePath)
        val durationMs = when {
            extractedDuration > 0L -> extractedDuration
            track.duration > 0L -> track.duration
            else -> 30_000L
        }
        Pair(tempFile.absolutePath, durationMs)
    }

    suspend fun trimAndSetRingtone(
        inputFilePath: String,
        startMs: Long,
        endMs: Long,
        title: String,
        ringtoneType: RingtoneType = RingtoneType.RINGTONE
    ) = withContext(Dispatchers.IO) {
        if (!Settings.System.canWrite(context)) {
            throw Exception("Missing WRITE_SETTINGS permission. Please grant it in system settings.")
        }

        val inputFile = File(inputFilePath)
        if (!inputFile.exists() || inputFile.length() == 0L) {
            throw Exception("Downloaded audio is missing or empty. Please try again.")
        }

        if (endMs - startMs < 200L) {
            throw Exception("Selected ringtone is too short. Pick at least 1 second.")
        }

        val isWebmInput = isWebmAudioFormat(inputFilePath)
        val ext = if (isWebmInput) "webm" else "m4a"

        val outputDir = File(context.cacheDir, "ringtones")
        if (!outputDir.exists()) outputDir.mkdirs()
        val outputFile = File(outputDir, "trimmed_${System.currentTimeMillis()}.$ext")

        try {
            trimAudioWithMuxer(inputFilePath, outputFile.absolutePath, startMs, endMs)
        } catch (e: Exception) {
            Log.w("RingtoneHelper", "MediaMuxer trim failed, falling back to full audio file copy", e)
            inputFile.copyTo(outputFile, overwrite = true)
        }

        if (!outputFile.exists() || outputFile.length() == 0L) {
            try {
                inputFile.copyTo(outputFile, overwrite = true)
            } catch (_: Exception) {}
        }

        if (!outputFile.exists() || outputFile.length() == 0L) {
            outputFile.delete()
            throw Exception("Trimming produced an empty file. Please try a different selection.")
        }

        val uri = saveRingtoneToMediaStore(context, outputFile.absolutePath, title, ringtoneType)
            ?: run {
                outputFile.delete()
                throw Exception("Failed to write ringtone to system storage.")
            }

        try {
            RingtoneManager.setActualDefaultRingtoneUri(context, ringtoneType.ringtoneManagerType, uri)
        } catch (e: Exception) {
            Log.e("RingtoneHelper", "Failed to set actual default ringtone", e)
            outputFile.delete()
            throw Exception("Saved ringtone to storage, but couldn't set it as default. Check system Sound settings.")
        }

        outputFile.delete()
        inputFile.delete()
    }
}
