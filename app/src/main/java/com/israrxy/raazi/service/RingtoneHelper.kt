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
     * Downloads audio to a temp file and returns its path and total duration in ms.
     * [onProgress] is called with a percentage (0-100) during download.
     */
    suspend fun downloadAudio(
        track: MusicItem,
        extractor: YouTubeMusicExtractor,
        onProgress: ((Int) -> Unit)? = null
    ): Pair<String, Long> = withContext(Dispatchers.IO) {
        onProgress?.invoke(0)
        val audioUrl = if (track.audioUrl.isNotEmpty() && !track.audioUrl.contains("youtube.com") && !track.audioUrl.contains("googlevideo.com")) {
            track.audioUrl
        } else {
            extractor.getAudioStreamUrl(track.videoUrl)
        }
        if (audioUrl.isEmpty()) {
            throw Exception("Could not resolve audio stream")
        }

        onProgress?.invoke(5)
        val cacheDir = File(context.cacheDir, "ringtones")
        if (!cacheDir.exists()) cacheDir.mkdirs()

        val tempFile = File(cacheDir, "${track.id}_raw.m4a")
        
        var downloadUrl = audioUrl
        if ((downloadUrl.contains("youtube.com") || downloadUrl.contains("googlevideo.com")) && !downloadUrl.contains("&range=")) {
            try {
                val musicDao = RaaziApplication.instance.database.musicDao()
                val format = musicDao.getFormat(track.id)
                val length = if (format != null && format.contentLength > 0L) {
                    format.contentLength
                } else {
                    10_000_000L
                }
                downloadUrl += "&range=0-$length"
                Log.d("RingtoneHelper", "Bypassing YouTube throttling for ringtone ${track.title} with range=0-$length")
            } catch (e: Exception) {
                Log.w("RingtoneHelper", "Failed to query format info for range parameter", e)
            }
        }

        val request = Request.Builder()
            .url(downloadUrl)
            .header("User-Agent", "Mozilla/5.0")
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("Failed to download: ${response.code}")
            
            val body = response.body ?: throw Exception("Empty response body")
            val contentLength = body.contentLength()
            var downloaded = 0L
            var lastProgressUpdate = 0L
            val buffer = ByteArray(8192)

            body.byteStream().use { input ->
                FileOutputStream(tempFile).use { output ->
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        downloaded += bytesRead
                        val now = System.currentTimeMillis()
                        if (now - lastProgressUpdate > 100 || downloaded == contentLength) {
                            val progress = if (contentLength > 0) {
                                5 + ((downloaded * 95 / contentLength).toInt()).coerceIn(0, 95)
                            } else 0
                            onProgress?.invoke(progress)
                            lastProgressUpdate = now
                        }
                    }
                }
            }
        }
        
        onProgress?.invoke(100)
        val durationMs = if (track.duration > 0) track.duration else getAudioDurationMs(tempFile.absolutePath)
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

        trimAudio(inputFilePath, outputFile.absolutePath, startMs, endMs)

        if (!outputFile.exists() || outputFile.length() == 0L) {
            outputFile.delete()
            throw Exception("Trimming produced an empty file. Please try a different selection.")
        }

        // 3. Save to MediaStore
        val uri = saveToMediaStore(outputFile.absolutePath, title, ringtoneType)
            ?: run {
                outputFile.delete()
                throw Exception("Failed to write ringtone to system storage. Check storage permissions.")
            }

        // 4. Set as default ringtone/notification/alarm
        try {
            RingtoneManager.setActualDefaultRingtoneUri(context, ringtoneType.ringtoneManagerType, uri)
        } catch (e: Exception) {
            android.util.Log.e("RingtoneHelper", "Failed to set actual default ringtone", e)
            outputFile.delete()
            inputFile.delete()
            throw Exception("Saved ringtone to storage but couldn't set it as default. Try again from system Settings > Sound.")
        }

        // Clean up temp file
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
            e.printStackTrace()
        } finally {
            extractor.release()
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
                if (mime?.startsWith("audio/") == true) {
                    return format.getLong(MediaFormat.KEY_DURATION) / 1000L
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            extractor.release()
        }
        return 0L
    }

    private fun trimAudio(inputPath: String, outputPath: String, startMs: Long, endMs: Long) {
        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
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
            extractor.seekTo(startMs * 1000, MediaExtractor.SEEK_TO_CLOSEST_SYNC)

            val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
            val isWebm = mime.contains("webm") || mime.contains("opus") || mime.contains("vorbis")
            val outputFormat = if (isWebm) {
                MediaMuxer.OutputFormat.MUXER_OUTPUT_WEBM
            } else {
                MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
            }

            try {
                muxer = MediaMuxer(outputPath, outputFormat)
            } catch (e: Exception) {
                Log.e("RingtoneHelper", "Failed to create MediaMuxer with $outputFormat", e)
                throw Exception("This device cannot write the audio format. Try a different track.")
            }

            val muxerTrackIndex = muxer.addTrack(format)
            muxer.start()

            val maxChunkSize = format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE).takeIf { it > 0 } ?: 1024 * 1024
            val buffer = ByteBuffer.allocate(maxChunkSize)
            val bufferInfo = MediaCodec.BufferInfo()

            while (true) {
                bufferInfo.size = extractor.readSampleData(buffer, 0)
                if (bufferInfo.size < 0) break

                val presentationTimeUs = extractor.sampleTime
                if (presentationTimeUs > endMs * 1000) break

                bufferInfo.presentationTimeUs = presentationTimeUs
                bufferInfo.flags = extractor.sampleFlags

                muxer.writeSampleData(muxerTrackIndex, buffer, bufferInfo)
                extractor.advance()
            }
        } finally {
            extractor.release()
            try {
                muxer?.stop()
            } catch (e: Exception) {
                Log.w("RingtoneHelper", "MediaMuxer.stop failed", e)
            }
            try {
                muxer?.release()
            } catch (e: Exception) {
                Log.w("RingtoneHelper", "MediaMuxer.release failed", e)
            }
        }
    }

    private fun saveToMediaStore(filePath: String, title: String, ringtoneType: RingtoneType = RingtoneType.RINGTONE): Uri? {
        val file = File(filePath)
        if (!file.exists() || file.length() == 0L) {
            Log.e("RingtoneHelper", "saveToMediaStore called with missing/empty file: $filePath")
            return null
        }
        val ext = file.extension.lowercase()
        val mimeType = if (ext == "webm") "audio/webm" else "audio/mp4"

        val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI

        // Delete existing with same title to avoid duplicates
        try {
            context.contentResolver.delete(uri, "${MediaStore.MediaColumns.TITLE}=?", arrayOf(title))
        } catch (e: Exception) {
            Log.w("RingtoneHelper", "Failed to delete existing ringtone entry", e)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.TITLE, title)
                put(MediaStore.MediaColumns.DISPLAY_NAME, "$title.$ext")
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.Audio.Media.IS_RINGTONE, ringtoneType == RingtoneType.RINGTONE)
                put(MediaStore.Audio.Media.IS_NOTIFICATION, ringtoneType == RingtoneType.NOTIFICATION)
                put(MediaStore.Audio.Media.IS_ALARM, ringtoneType == RingtoneType.ALARM)
                put(MediaStore.Audio.Media.IS_MUSIC, false)

                val relativePath = when (ringtoneType) {
                    RingtoneType.RINGTONE -> android.os.Environment.DIRECTORY_RINGTONES
                    RingtoneType.NOTIFICATION -> android.os.Environment.DIRECTORY_NOTIFICATIONS
                    RingtoneType.ALARM -> android.os.Environment.DIRECTORY_ALARMS
                }
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }

            val newUri = context.contentResolver.insert(uri, values)
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
            return newUri
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
            val destFile = File(externalDir, "$title.$ext")
            try {
                file.copyTo(destFile, overwrite = true)
            } catch (e: Exception) {
                Log.e("RingtoneHelper", "Failed to copy file to public directory on pre-Q", e)
                return null
            }

            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DATA, destFile.absolutePath)
                put(MediaStore.MediaColumns.TITLE, title)
                put(MediaStore.MediaColumns.DISPLAY_NAME, "$title.$ext")
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.Audio.Media.IS_RINGTONE, ringtoneType == RingtoneType.RINGTONE)
                put(MediaStore.Audio.Media.IS_NOTIFICATION, ringtoneType == RingtoneType.NOTIFICATION)
                put(MediaStore.Audio.Media.IS_ALARM, ringtoneType == RingtoneType.ALARM)
                put(MediaStore.Audio.Media.IS_MUSIC, false)
            }

            return context.contentResolver.insert(uri, values)
        }
    }
}
