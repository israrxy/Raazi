package com.israrxy.raazi.service

import com.israrxy.raazi.RaaziApplication
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import com.israrxy.raazi.model.MusicItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.util.concurrent.TimeUnit

enum class RingtoneType(val ringtoneManagerType: Int, val mediaStoreField: String) {
    RINGTONE(RingtoneManager.TYPE_RINGTONE, MediaStore.Audio.Media.IS_RINGTONE),
    NOTIFICATION(RingtoneManager.TYPE_NOTIFICATION, MediaStore.Audio.Media.IS_NOTIFICATION),
    ALARM(RingtoneManager.TYPE_ALARM, MediaStore.Audio.Media.IS_ALARM)
}

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

    /**
     * Returns a local [File] when [url] points at on-device storage, otherwise null.
     * Handles bare absolute paths as well as file:// URIs.
     */
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

    /**
     * Downloads audio to a temp file and returns its path and total duration in ms.
     * [onProgress] is called with a percentage (0-100) during download.
     */
    suspend fun downloadAudio(
        track: MusicItem,
        extractor: YouTubeMusicExtractor,
        onProgress: ((Int) -> Unit)? = null
    ): Pair<String, Long> = withContext(Dispatchers.IO) {
        onProgress?.invoke(0)

        val cacheDir = File(context.cacheDir, "ringtones")
        if (!cacheDir.exists()) cacheDir.mkdirs()

        val safeId = track.id.replace(Regex("[^A-Za-z0-9_-]"), "_").take(64).ifEmpty { "track" }

        // 1. Check if track already has a valid local non-FLAC file we can use directly
        val existingLocalPath = track.localPath?.takeIf { it.isNotBlank() }
        if (existingLocalPath != null) {
            val localFile = resolveLocalSource(existingLocalPath)
            if (localFile != null && localFile.exists() && localFile.length() > 0L && !localFile.name.endsWith(".flac", ignoreCase = true)) {
                val ext = if (isWebmFormat(localFile.absolutePath)) "webm" else "m4a"
                val tempFile = File(cacheDir, "${safeId}_raw.$ext")
                localFile.inputStream().use { input ->
                    FileOutputStream(tempFile).use { output ->
                        input.copyTo(output, DEFAULT_BUFFER_SIZE)
                    }
                }
                onProgress?.invoke(100)
                val extractedDuration = getAudioDurationMs(tempFile.absolutePath)
                val localDuration = when {
                    extractedDuration > 0L -> extractedDuration
                    track.duration > 0L -> track.duration
                    else -> 30_000L
                }
                return@withContext Pair(tempFile.absolutePath, localDuration)
            }
        }

        // 2. Resolve remote stream preferring AAC/M4A so MediaMuxer and Android RingtoneManager work reliably
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
            val finalExt = if (isWebmFormat(rawTempFile.absolutePath)) "webm" else "m4a"
            val finalFile = File(cacheDir, "${safeId}_raw.$finalExt")
            rawTempFile.copyTo(finalFile, overwrite = true)
            rawTempFile.delete()
            onProgress?.invoke(100)
            val extractedDuration = getAudioDurationMs(finalFile.absolutePath)
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

        val finalExt = if (isWebmFormat(rawTempFile.absolutePath)) "webm" else "m4a"
        val tempFile = File(cacheDir, "${safeId}_raw.$finalExt")
        rawTempFile.copyTo(tempFile, overwrite = true)
        rawTempFile.delete()

        onProgress?.invoke(100)
        val extractedDuration = getAudioDurationMs(tempFile.absolutePath)
        val durationMs = when {
            extractedDuration > 0L -> extractedDuration
            track.duration > 0L -> track.duration
            else -> 30_000L
        }
        Pair(tempFile.absolutePath, durationMs)
    }

    /**
     * Trims the file and sets it as the default ringtone.
     */
    suspend fun trimAndSetRingtone(
        inputFilePath: String,
        startMs: Long,
        endMs: Long,
        title: String,
        ringtoneType: RingtoneType = RingtoneType.RINGTONE
    ) = withContext(Dispatchers.IO) {

        // 1. Check permission
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

        // Detect if input format is webm/opus
        val isWebmInput = isWebmFormat(inputFilePath)
        val ext = if (isWebmInput) "webm" else "m4a"

        // 2. Trim audio
        val outputDir = File(context.cacheDir, "ringtones")
        if (!outputDir.exists()) outputDir.mkdirs()
        val outputFile = File(outputDir, "trimmed_${System.currentTimeMillis()}.$ext")

        try {
            trimAudio(inputFilePath, outputFile.absolutePath, startMs, endMs)
        } catch (e: Exception) {
            Log.w("RingtoneHelper", "MediaMuxer trim failed, falling back to full audio file copy", e)
            inputFile.copyTo(outputFile, overwrite = true)
        }

        if (!outputFile.exists() || outputFile.length() == 0L) {
            // Fallback copy if muxer wrote 0 bytes
            try {
                inputFile.copyTo(outputFile, overwrite = true)
            } catch (_: Exception) {}
        }

        if (!outputFile.exists() || outputFile.length() == 0L) {
            outputFile.delete()
            throw Exception("Trimming produced an empty file. Please try a different selection.")
        }

        // 3. Save to MediaStore
        val uri = saveToMediaStore(outputFile.absolutePath, title, ringtoneType)
            ?: run {
                outputFile.delete()
                throw Exception("Failed to write ringtone to system storage.")
            }

        // 4. Set as default ringtone/notification/alarm
        try {
            RingtoneManager.setActualDefaultRingtoneUri(context, ringtoneType.ringtoneManagerType, uri)
        } catch (e: Exception) {
            Log.e("RingtoneHelper", "Failed to set actual default ringtone", e)
            outputFile.delete()
            throw Exception("Saved ringtone to storage, but couldn't set it as default. Check system Sound settings.")
        }

        // Clean up temp files
        outputFile.delete()
        inputFile.delete()
    }

    private fun isWebmFormat(filePath: String): Boolean {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(filePath)
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME)
                if (mime?.startsWith("audio/") == true) {
                    return mime.contains("webm") || mime.contains("opus") || mime.contains("vorbis")
                }
            }
        } catch (e: Exception) {
            Log.w("RingtoneHelper", "isWebmFormat check failed for $filePath", e)
        } finally {
            try { extractor.release() } catch (_: Exception) {}
        }
        return false
    }

    private fun getAudioDurationMs(filePath: String): Long {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(filePath)
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME)
                if (mime?.startsWith("audio/") == true && format.containsKey(MediaFormat.KEY_DURATION)) {
                    val durationUs = format.getLong(MediaFormat.KEY_DURATION)
                    if (durationUs > 0L) return durationUs / 1000L
                }
            }
        } catch (e: Exception) {
            Log.w("RingtoneHelper", "getAudioDurationMs failed for $filePath", e)
        } finally {
            try { extractor.release() } catch (_: Exception) {}
        }
        return 0L
    }

    private fun trimAudio(inputPath: String, outputPath: String, startMs: Long, endMs: Long) {
        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        var muxerStarted = false
        try {
            extractor.setDataSource(inputPath)
            var audioTrackIndex = -1
            var format: MediaFormat? = null

            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                val mime = f.getString(MediaFormat.KEY_MIME)
                if (mime?.startsWith("audio/") == true) {
                    audioTrackIndex = i
                    format = f
                    break
                }
            }

            if (audioTrackIndex == -1 || format == null) throw Exception("No audio track found")

            extractor.selectTrack(audioTrackIndex)
            val startUs = (startMs * 1000L).coerceAtLeast(0L)
            val endUs = (endMs * 1000L).coerceAtLeast(startUs + 500_000L)
            extractor.seekTo(startUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC)

            val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
            val isWebm = mime.contains("webm") || mime.contains("opus") || mime.contains("vorbis")
            val outputFormat = if (isWebm) {
                MediaMuxer.OutputFormat.MUXER_OUTPUT_WEBM
            } else {
                MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
            }

            muxer = MediaMuxer(outputPath, outputFormat)
            val muxerTrackIndex = muxer.addTrack(format)
            muxer.start()
            muxerStarted = true

            val maxChunkSize = if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE).takeIf { it > 0 } ?: (512 * 1024)
            } else {
                512 * 1024
            }
            val buffer = ByteBuffer.allocate(maxChunkSize)
            val bufferInfo = MediaCodec.BufferInfo()

            var firstSampleTimeUs = -1L
            var lastWrittenPtsUs = -1L

            while (true) {
                bufferInfo.offset = 0
                bufferInfo.size = extractor.readSampleData(buffer, 0)
                if (bufferInfo.size < 0) break

                val sampleTimeUs = extractor.sampleTime
                if (sampleTimeUs < 0L || sampleTimeUs > endUs) break

                if (firstSampleTimeUs < 0L) {
                    firstSampleTimeUs = sampleTimeUs
                }

                // Normalize presentation timestamps so trimmed ringtone starts immediately at 0s
                var normalizedPtsUs = (sampleTimeUs - firstSampleTimeUs).coerceAtLeast(0L)
                if (normalizedPtsUs <= lastWrittenPtsUs) {
                    normalizedPtsUs = lastWrittenPtsUs + 1L
                }
                lastWrittenPtsUs = normalizedPtsUs

                bufferInfo.presentationTimeUs = normalizedPtsUs
                bufferInfo.flags = extractor.sampleFlags

                muxer.writeSampleData(muxerTrackIndex, buffer, bufferInfo)
                extractor.advance()
            }
        } finally {
            try { extractor.release() } catch (_: Exception) {}
            if (muxerStarted) {
                try {
                    muxer?.stop()
                } catch (e: Exception) {
                    Log.w("RingtoneHelper", "MediaMuxer.stop failed", e)
                }
            }
            try {
                muxer?.release()
            } catch (e: Exception) {
                Log.w("RingtoneHelper", "MediaMuxer.release failed", e)
            }
        }
    }

    private fun saveToMediaStore(
        filePath: String,
        title: String,
        ringtoneType: RingtoneType = RingtoneType.RINGTONE
    ): Uri? {
        val file = File(filePath)
        if (!file.exists() || file.length() == 0L) {
            Log.e("RingtoneHelper", "saveToMediaStore called with missing/empty file: $filePath")
            return null
        }
        val ext = file.extension.lowercase().ifEmpty { "m4a" }
        val mimeType = if (ext == "webm") "audio/webm" else "audio/mp4"

        val cleanTitle = title.trim().ifEmpty { "Raazi Ringtone" }
        val safeFileName = cleanTitle
            .replace(Regex("[^A-Za-z0-9 _.-]"), "_")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(60)
            .ifEmpty { "Raazi_Ringtone" }

        val collectionUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

        // Best-effort cleanup of previously created ringtone with the same title owned by our app
        try {
            context.contentResolver.delete(
                collectionUri,
                "${MediaStore.MediaColumns.TITLE}=?",
                arrayOf(cleanTitle)
            )
        } catch (e: Exception) {
            Log.d("RingtoneHelper", "Skipping delete of existing MediaStore entry: ${e.message}")
        }

        val uniqueDisplayName = "${safeFileName}_${System.currentTimeMillis() % 100000}.$ext"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val relativePath = when (ringtoneType) {
                RingtoneType.RINGTONE -> android.os.Environment.DIRECTORY_RINGTONES
                RingtoneType.NOTIFICATION -> android.os.Environment.DIRECTORY_NOTIFICATIONS
                RingtoneType.ALARM -> android.os.Environment.DIRECTORY_ALARMS
            }

            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.TITLE, cleanTitle)
                put(MediaStore.MediaColumns.DISPLAY_NAME, uniqueDisplayName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.Audio.Media.IS_RINGTONE, if (ringtoneType == RingtoneType.RINGTONE) 1 else 0)
                put(MediaStore.Audio.Media.IS_NOTIFICATION, if (ringtoneType == RingtoneType.NOTIFICATION) 1 else 0)
                put(MediaStore.Audio.Media.IS_ALARM, if (ringtoneType == RingtoneType.ALARM) 1 else 0)
                put(MediaStore.Audio.Media.IS_MUSIC, 0)
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }

            val newUri = context.contentResolver.insert(collectionUri, values)
            if (newUri != null) {
                try {
                    var copySucceeded = false
                    context.contentResolver.openOutputStream(newUri)?.use { out ->
                        file.inputStream().use { input ->
                            input.copyTo(out)
                        }
                        copySucceeded = true
                    }
                    if (copySucceeded) {
                        val updateValues = ContentValues().apply {
                            put(MediaStore.MediaColumns.IS_PENDING, 0)
                        }
                        context.contentResolver.update(newUri, updateValues, null, null)
                        return newUri
                    } else {
                        try { context.contentResolver.delete(newUri, null, null) } catch (_: Exception) {}
                        return null
                    }
                } catch (e: Exception) {
                    Log.e("RingtoneHelper", "Failed to write media file to MediaStore", e)
                    try {
                        context.contentResolver.delete(newUri, null, null)
                    } catch (ex: Exception) {
                        Log.e("RingtoneHelper", "Failed to delete failed insert", ex)
                    }
                    return null
                }
            }
            return null
        } else {
            val dirName = when (ringtoneType) {
                RingtoneType.RINGTONE -> android.os.Environment.DIRECTORY_RINGTONES
                RingtoneType.NOTIFICATION -> android.os.Environment.DIRECTORY_NOTIFICATIONS
                RingtoneType.ALARM -> android.os.Environment.DIRECTORY_ALARMS
            }
            val externalDir = android.os.Environment.getExternalStoragePublicDirectory(dirName)
            if (!externalDir.exists()) {
                externalDir.mkdirs()
            }
            val destFile = File(externalDir, uniqueDisplayName)
            try {
                file.copyTo(destFile, overwrite = true)
            } catch (e: Exception) {
                Log.e("RingtoneHelper", "Failed to copy file to public directory on pre-Q", e)
                return null
            }

            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DATA, destFile.absolutePath)
                put(MediaStore.MediaColumns.TITLE, cleanTitle)
                put(MediaStore.MediaColumns.DISPLAY_NAME, uniqueDisplayName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.Audio.Media.IS_RINGTONE, if (ringtoneType == RingtoneType.RINGTONE) 1 else 0)
                put(MediaStore.Audio.Media.IS_NOTIFICATION, if (ringtoneType == RingtoneType.NOTIFICATION) 1 else 0)
                put(MediaStore.Audio.Media.IS_ALARM, if (ringtoneType == RingtoneType.ALARM) 1 else 0)
                put(MediaStore.Audio.Media.IS_MUSIC, 0)
            }

            return context.contentResolver.insert(collectionUri, values)
        }
    }
}
