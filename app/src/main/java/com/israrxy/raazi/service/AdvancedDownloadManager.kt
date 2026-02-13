package com.israrxy.raazi.service

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Environment
import android.os.StatFs
import android.util.Log
import com.israrxy.raazi.data.db.DownloadEntity
import com.israrxy.raazi.data.db.MusicDao
import com.israrxy.raazi.model.MusicItem
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Semaphore
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import java.util.concurrent.ConcurrentHashMap

/**
 * High-performance download manager using OkHttp direct streaming.
 * Downloads complete in seconds instead of minutes by bypassing
 * Android's throttled system DownloadManager.
 */
class AdvancedDownloadManager(
    private val context: Context,
    private val musicDao: MusicDao,
    private val musicExtractor: YouTubeMusicExtractor
) {
    companion object {
        private const val TAG = "AdvancedDownloadMgr"
        private const val MAX_RETRIES = 3
        private const val DOWNLOAD_DIR = "Raazi"
        private const val BUFFER_SIZE = 8192
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // OkHttp client optimized for large downloads
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    // Concurrency control
    private var downloadSemaphore = Semaphore(2)
    var maxConcurrentDownloads = 2
        set(value) {
            field = value
            downloadSemaphore = Semaphore(value)
        }
    var wifiOnly = false

    // Active download jobs for cancellation
    private val activeJobs = ConcurrentHashMap<String, Job>()

    // Observable state from DB
    val activeDownloads: Flow<List<DownloadEntity>> = musicDao.getActiveDownloads()
    val completedDownloads: Flow<List<DownloadEntity>> = musicDao.getCompletedDownloads()
    val failedDownloads: Flow<List<DownloadEntity>> = musicDao.getFailedDownloads()
    val allDownloads: Flow<List<DownloadEntity>> = musicDao.getAllDownloads()
    val activeDownloadCount: Flow<Int> = musicDao.getActiveDownloadCount()

    // Events
    private val _downloadEvents = MutableSharedFlow<DownloadEvent>(extraBufferCapacity = 20)
    val downloadEvents: SharedFlow<DownloadEvent> = _downloadEvents.asSharedFlow()

    sealed class DownloadEvent {
        data class Started(val trackId: String, val title: String) : DownloadEvent()
        data class Completed(val trackId: String, val title: String) : DownloadEvent()
        data class Failed(val trackId: String, val title: String, val error: String) : DownloadEvent()
        data class Cancelled(val trackId: String, val title: String) : DownloadEvent()
        data class Queued(val trackId: String, val title: String) : DownloadEvent()
    }

    /**
     * Download a single track using OkHttp direct streaming.
     * Resolves stream URL, then streams bytes directly to disk with real-time progress.
     */
    fun downloadTrack(track: MusicItem) {
        val job = scope.launch {
            try {
                // Check duplicate
                val existing = musicDao.getDownloadByTrackId(track.id)
                if (existing != null) {
                    when (existing.status) {
                        DownloadEntity.STATUS_COMPLETED -> {
                            if (existing.filePath != null && File(existing.filePath).exists()) {
                                _downloadEvents.emit(DownloadEvent.Completed(track.id, track.title))
                                return@launch
                            }
                            musicDao.deleteDownloadByTrackId(track.id)
                        }
                        DownloadEntity.STATUS_DOWNLOADING, DownloadEntity.STATUS_PENDING -> {
                            _downloadEvents.emit(DownloadEvent.Queued(track.id, track.title))
                            return@launch
                        }
                        DownloadEntity.STATUS_FAILED, DownloadEntity.STATUS_PAUSED -> {
                            musicDao.deleteDownloadByTrackId(track.id)
                        }
                    }
                }

                // Check WiFi-only
                if (wifiOnly && !isOnWifi()) {
                    insertFailedDownload(track, "WiFi-only mode enabled. Connect to WiFi to download.")
                    return@launch
                }

                // Check storage
                if (!hasEnoughStorage()) {
                    insertFailedDownload(track, "Not enough storage space")
                    return@launch
                }

                // Insert as pending
                val downloadEntity = DownloadEntity(
                    trackId = track.id,
                    title = track.title,
                    artist = track.artist,
                    thumbnailUrl = track.thumbnailUrl,
                    audioUrl = track.audioUrl,
                    videoUrl = track.videoUrl,
                    duration = track.duration,
                    status = DownloadEntity.STATUS_PENDING
                )
                musicDao.insertDownload(downloadEntity)
                _downloadEvents.emit(DownloadEvent.Queued(track.id, track.title))

                // Wait for semaphore slot (concurrency control)
                downloadSemaphore.acquire()

                try {
                    // Resolve audio stream URL
                    musicDao.updateDownloadStatus(track.id, DownloadEntity.STATUS_DOWNLOADING)
                    _downloadEvents.emit(DownloadEvent.Started(track.id, track.title))

                    val audioUrl = try {
                        musicExtractor.getAudioStreamUrl(track.videoUrl)
                    } catch (e: Exception) {
                        Log.e(TAG, "Stream resolution failed for ${track.title}", e)
                        musicDao.updateDownloadStatus(
                            track.id, DownloadEntity.STATUS_FAILED,
                            errorMessage = "Could not resolve audio stream: ${e.message}"
                        )
                        _downloadEvents.emit(DownloadEvent.Failed(track.id, track.title, "Stream resolution failed"))
                        return@launch
                    }

                    if (audioUrl.isEmpty()) {
                        musicDao.updateDownloadStatus(
                            track.id, DownloadEntity.STATUS_FAILED,
                            errorMessage = "No streamable audio found"
                        )
                        _downloadEvents.emit(DownloadEvent.Failed(track.id, track.title, "No audio stream"))
                        return@launch
                    }

                    // Direct OkHttp download with streaming
                    streamDownload(track, audioUrl)

                } finally {
                    downloadSemaphore.release()
                    activeJobs.remove(track.id)
                }

            } catch (e: CancellationException) {
                Log.d(TAG, "Download cancelled for ${track.title}")
                musicDao.deleteDownloadByTrackId(track.id)
                _downloadEvents.emit(DownloadEvent.Cancelled(track.id, track.title))
            } catch (e: Exception) {
                Log.e(TAG, "Download failed for ${track.title}", e)
                musicDao.updateDownloadStatus(
                    track.id, DownloadEntity.STATUS_FAILED,
                    errorMessage = e.message
                )
                _downloadEvents.emit(DownloadEvent.Failed(track.id, track.title, e.message ?: "Unknown error"))
            }
        }
        activeJobs[track.id] = job
    }

    /**
     * Stream bytes directly from URL to disk using OkHttp.
     * Progress is emitted per chunk for real-time UI updates.
     */
    private suspend fun streamDownload(track: MusicItem, audioUrl: String) {
        withContext(Dispatchers.IO) {
            // Ensure download directory exists
            val downloadDir = File(
                context.getExternalFilesDir(Environment.DIRECTORY_MUSIC),
                DOWNLOAD_DIR
            )
            if (!downloadDir.exists()) downloadDir.mkdirs()

            val fileName = buildFileName(track)
            val outputFile = File(downloadDir, fileName)
            val tempFile = File(downloadDir, "$fileName.tmp")

            val request = Request.Builder()
                .url(audioUrl)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
                .build()

            Log.d(TAG, "Starting OkHttp download for ${track.title}")
            val startTime = System.currentTimeMillis()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("HTTP ${response.code}: ${response.message}")
                }

                val body = response.body ?: throw IllegalStateException("Empty response body")
                val contentLength = body.contentLength()
                var downloaded = 0L
                var lastProgressUpdate = 0L

                body.byteStream().use { inputStream ->
                    FileOutputStream(tempFile).use { outputStream ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        var bytesRead: Int

                        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                            // Check for cancellation
                            ensureActive()

                            outputStream.write(buffer, 0, bytesRead)
                            downloaded += bytesRead

                            // Update progress every ~50KB to avoid DB spam
                            val now = System.currentTimeMillis()
                            if (now - lastProgressUpdate > 100 || downloaded == contentLength) {
                                val progress = if (contentLength > 0) {
                                    (downloaded * 100 / contentLength).toInt()
                                } else 0
                                musicDao.updateDownloadProgress(track.id, progress, downloaded)
                                lastProgressUpdate = now
                            }
                        }
                    }
                }

                // Rename temp file to final
                if (tempFile.exists()) {
                    if (outputFile.exists()) outputFile.delete()
                    tempFile.renameTo(outputFile)
                }

                val elapsed = System.currentTimeMillis() - startTime
                val speedKBps = if (elapsed > 0) (downloaded / 1024) / (elapsed / 1000.0) else 0.0
                Log.i(TAG, "Download complete: ${track.title} | ${downloaded/1024}KB in ${elapsed}ms (${String.format("%.1f", speedKBps)} KB/s)")

                val filePath = outputFile.absolutePath
                musicDao.updateDownloadStatus(
                    trackId = track.id,
                    status = DownloadEntity.STATUS_COMPLETED,
                    filePath = filePath,
                    completedAt = System.currentTimeMillis()
                )
                musicDao.updateTrackLocalPath(track.id, filePath)
                _downloadEvents.emit(DownloadEvent.Completed(track.id, track.title))
            }
        }
    }

    /**
     * Download multiple tracks (batch/playlist download).
     */
    fun downloadPlaylist(tracks: List<MusicItem>) {
        tracks.forEach { downloadTrack(it) }
    }

    /**
     * Cancel an active download.
     */
    fun cancelDownload(trackId: String) {
        scope.launch {
            // Cancel the job first
            activeJobs[trackId]?.cancel()
            activeJobs.remove(trackId)

            // Clean up temp file
            val download = musicDao.getDownloadByTrackId(trackId)
            if (download != null) {
                cleanupTempFile(download)
                musicDao.deleteDownloadByTrackId(trackId)
                _downloadEvents.emit(DownloadEvent.Cancelled(trackId, download.title))
            }
        }
    }

    /**
     * Retry a failed download.
     */
    fun retryDownload(trackId: String) {
        scope.launch {
            val download = musicDao.getDownloadByTrackId(trackId) ?: return@launch
            if (download.retryCount >= MAX_RETRIES) {
                _downloadEvents.emit(DownloadEvent.Failed(trackId, download.title, "Max retries exceeded"))
                return@launch
            }
            musicDao.incrementRetryCount(trackId)
            musicDao.deleteDownloadByTrackId(trackId)

            val track = MusicItem(
                id = download.trackId,
                title = download.title,
                artist = download.artist,
                duration = download.duration,
                thumbnailUrl = download.thumbnailUrl,
                audioUrl = download.audioUrl,
                videoUrl = download.videoUrl
            )
            downloadTrack(track)
        }
    }

    /**
     * Delete a completed download (removes file + DB entry).
     */
    fun deleteDownloadedFile(trackId: String) {
        scope.launch {
            val download = musicDao.getDownloadByTrackId(trackId) ?: return@launch
            download.filePath?.let { path ->
                try {
                    File(path).delete()
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to delete file: $path", e)
                }
            }
            musicDao.deleteDownloadByTrackId(trackId)
            musicDao.clearTrackLocalPath(trackId)
        }
    }

    /**
     * Check if a track is already downloaded and available.
     */
    fun isTrackDownloaded(trackId: String): Boolean {
        val download = musicDao.getDownloadByTrackId(trackId) ?: return false
        return download.status == DownloadEntity.STATUS_COMPLETED &&
                download.filePath != null &&
                File(download.filePath).exists()
    }

    /**
     * Get download state for a specific track.
     */
    fun getDownloadState(trackId: String): DownloadEntity? {
        return musicDao.getDownloadByTrackId(trackId)
    }

    /**
     * Get total storage used by downloads.
     */
    fun getDownloadStorageUsed(): Long {
        val downloadDir = File(
            context.getExternalFilesDir(Environment.DIRECTORY_MUSIC),
            DOWNLOAD_DIR
        )
        if (!downloadDir.exists()) return 0L
        return downloadDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    /**
     * Get available storage space.
     */
    fun getAvailableStorage(): Long {
        val stat = StatFs(Environment.getExternalStorageDirectory().path)
        return stat.availableBlocksLong * stat.blockSizeLong
    }

    /**
     * Delete all downloaded files and clear DB entries.
     */
    fun deleteAllDownloads() {
        scope.launch {
            // Cancel all active downloads
            activeJobs.values.forEach { it.cancel() }
            activeJobs.clear()

            val downloadDir = File(
                context.getExternalFilesDir(Environment.DIRECTORY_MUSIC),
                DOWNLOAD_DIR
            )
            downloadDir.deleteRecursively()
            val all = musicDao.getAllDownloads().first()
            all.forEach { download ->
                musicDao.deleteDownloadByTrackId(download.trackId)
                musicDao.clearTrackLocalPath(download.trackId)
            }
        }
    }

    // ─── Private Helpers ──────────────────────────────────────

    private fun insertFailedDownload(track: MusicItem, error: String) {
        val entity = DownloadEntity(
            trackId = track.id,
            title = track.title,
            artist = track.artist,
            thumbnailUrl = track.thumbnailUrl,
            audioUrl = track.audioUrl,
            videoUrl = track.videoUrl,
            duration = track.duration,
            status = DownloadEntity.STATUS_FAILED,
            errorMessage = error
        )
        musicDao.insertDownload(entity)
        scope.launch {
            _downloadEvents.emit(DownloadEvent.Failed(track.id, track.title, error))
        }
    }

    private fun buildFileName(track: MusicItem): String {
        val sanitizedTitle = track.title.replace(Regex("[^a-zA-Z0-9._\\- ]"), "_").take(60)
        val sanitizedId = track.id.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        return "${sanitizedTitle}_${sanitizedId}.m4a"
    }

    private fun cleanupTempFile(download: DownloadEntity) {
        try {
            val downloadDir = File(
                context.getExternalFilesDir(Environment.DIRECTORY_MUSIC),
                DOWNLOAD_DIR
            )
            val tempFiles = downloadDir.listFiles()?.filter {
                it.name.contains(download.trackId.replace(Regex("[^a-zA-Z0-9._-]"), "_")) && it.name.endsWith(".tmp")
            }
            tempFiles?.forEach { it.delete() }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cleanup temp files", e)
        }
    }

    private fun isOnWifi(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(network) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    private fun hasEnoughStorage(minBytes: Long = 50 * 1024 * 1024): Boolean {
        return getAvailableStorage() > minBytes
    }

    fun destroy() {
        activeJobs.values.forEach { it.cancel() }
        activeJobs.clear()
        scope.cancel()
    }
}
