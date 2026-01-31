package com.israrxy.raazi.service

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import com.israrxy.raazi.model.MusicItem
import java.io.File

class SimpleDownloadManager(private val context: Context) {

    fun downloadTrack(item: MusicItem, audioUrl: String): Long? {
        return try {
            val sanitizedId = item.id.replace(Regex("[^a-zA-Z0-9._-]"), "_")
            val fileName = "${item.title}_${item.artist}_$sanitizedId.m4a"
                .replace(Regex("[^a-zA-Z0-9._-]"), "_")
            
            val request = DownloadManager.Request(Uri.parse(audioUrl))
                .setTitle(item.title)
                .setDescription(item.artist)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_MUSIC, "Raazi/$fileName")
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(true)
            
            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            downloadManager.enqueue(request)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun getDownloadStatus(downloadId: Long): DownloadStatus {
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val query = DownloadManager.Query().setFilterById(downloadId)
        val cursor = downloadManager.query(query)
        
        if (cursor != null && cursor.moveToFirst()) {
            val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
            val bytesDownloadedIndex = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
            val bytesTotalIndex = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
            
            val status = cursor.getInt(statusIndex)
            val bytesDownloaded = cursor.getLong(bytesDownloadedIndex)
            val bytesTotal = cursor.getLong(bytesTotalIndex)
            cursor.close()
            
            val progress = if (bytesTotal > 0) (bytesDownloaded * 100 / bytesTotal).toInt() else 0
            
            return DownloadStatus(
                status = status,
                progress = progress,
                bytesDownloaded = bytesDownloaded,
                bytesTotal = bytesTotal
            )
        }
        cursor?.close()
        return DownloadStatus(DownloadManager.STATUS_FAILED, 0, 0, 0)
    }

    fun getDownloadPath(item: MusicItem): String {
        val sanitizedId = item.id.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val fileName = "${item.title}_${item.artist}_$sanitizedId.m4a"
            .replace(Regex("[^a-zA-Z0-9._-]"), "_")
        return File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "Raazi/$fileName").absolutePath
    }
}

data class DownloadStatus(
    val status: Int,
    val progress: Int,
    val bytesDownloaded: Long,
    val bytesTotal: Long
)
