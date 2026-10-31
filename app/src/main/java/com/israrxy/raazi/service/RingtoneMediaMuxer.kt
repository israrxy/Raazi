package com.israrxy.raazi.service

import android.content.ContentValues
import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import java.io.File
import java.nio.ByteBuffer

enum class RingtoneType(val ringtoneManagerType: Int, val mediaStoreField: String) {
    RINGTONE(RingtoneManager.TYPE_RINGTONE, MediaStore.Audio.Media.IS_RINGTONE),
    NOTIFICATION(RingtoneManager.TYPE_NOTIFICATION, MediaStore.Audio.Media.IS_NOTIFICATION),
    ALARM(RingtoneManager.TYPE_ALARM, MediaStore.Audio.Media.IS_ALARM)
}

internal fun isWebmAudioFormat(filePath: String): Boolean {
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

internal fun extractAudioDurationMs(filePath: String): Long {
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

internal fun trimAudioWithMuxer(inputPath: String, outputPath: String, startMs: Long, endMs: Long) {
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

internal fun saveRingtoneToMediaStore(
    context: Context,
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
            RingtoneType.RINGTONE -> Environment.DIRECTORY_RINGTONES
            RingtoneType.NOTIFICATION -> Environment.DIRECTORY_NOTIFICATIONS
            RingtoneType.ALARM -> Environment.DIRECTORY_ALARMS
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
            RingtoneType.RINGTONE -> Environment.DIRECTORY_RINGTONES
            RingtoneType.NOTIFICATION -> Environment.DIRECTORY_NOTIFICATIONS
            RingtoneType.ALARM -> Environment.DIRECTORY_ALARMS
        }
        val externalDir = Environment.getExternalStoragePublicDirectory(dirName)
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
