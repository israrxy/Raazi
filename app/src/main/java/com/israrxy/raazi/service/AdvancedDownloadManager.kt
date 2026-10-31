package com.israrxy.raazi.service

import android.content.Context
import android.os.Environment
import android.util.Log
import com.israrxy.raazi.data.db.DownloadEntity
import com.israrxy.raazi.data.db.MusicDao
import com.israrxy.raazi.model.MusicItem
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Semaphore
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * High-performance download manager using OkHttp direct streaming.
 */
class AdvancedDownloadManager(
    private val context: Context,
    private val musicDao: MusicDao,
    private val musicExtractor: YouTubeMusicExtractor
) {
    companion object {
        private const val TAG = "AdvancedDownloadMgr"
        private const val MAX_RETRIES = 3
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private var downloadSemaphore = Semaphore(2)
    var maxConcurrentDownloads = 2
        set(value) {
            field = value
            downloadSemaphore = Semaphore(value)
        }
    var wifiOnly = false

    private val activeJobs = ConcurrentHashMap<String, Job>()
    @Volatile private var isDestroyed = false

    val activeDownloads: Flow<List<DownloadEntity>> = musicDao.getActiveDownloads()
    val completedDownloads: Flow<List<DownloadEntity>> = musicDao.getCompletedDownloads()
    val failedDownloads: Flow<List<DownloadEntity>> = musicDao.getFailedDownloads()
    val allDownloads: Flow<List<DownloadEntity>> = musicDao.getAllDownloads()
    val activeDownloadCount: Flow<Int> = musicDao.getActiveDownloadCount()

    private val _downloadEvents = MutableSharedFlow<DownloadEvent>(extraBufferCapacity = 20)
    val downloadEvents: SharedFlow<DownloadEvent> = _downloadEvents.asSharedFlow()

    sealed class DownloadEvent {
        data class Started(val trackId: String, val title: String) : DownloadEvent()
        data class Completed(val trackId: String, val title: String) : DownloadEvent()
        data class Failed(val trackId: String, val title: String, val error: String) : DownloadEvent()
        data class Cancelled(val trackId: String, val title: String) : DownloadEvent()
        data class Queued(val trackId: String, val title: String) : DownloadEvent()
    }

    fun downloadTrack(track: MusicItem) {
        if (isDestroyed) {
            Log.w(TAG, "Download manager destroyed, ignoring download for ${track.title}")
            return
        }
        val job = scope.launch {
            try {
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

                if (wifiOnly && !isDeviceConnectedToWifi(context)) {
                    insertFailedDownload(track, "WiFi-only mode enabled. Connect to WiFi to download.")
                    return@launch
                }

                if (getAvailableStorage() <= 50L * 1024 * 1024) {
                    insertFailedDownload(track, "Not enough storage space")
                    return@launch
                }

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

                downloadSemaphore.acquire()

                try {
                    musicDao.updateDownloadStatus(track.id, DownloadEntity.STATUS_DOWNLOADING)
                    _downloadEvents.emit(DownloadEvent.Started(track.id, track.title))

                    val audioUrl = try {
                        val result = com.israrxy.raazi.player.StreamResolver.resolveStreamUrl(
                            videoIdInput = track.videoUrl,
                            title = track.title,
                            artist = track.artist,
                            mode = com.israrxy.raazi.model.PlaybackMediaMode.AUDIO,
                            preferAac = true
                        )
                        result.url
                    } catch (e: Exception) {
                        Log.w(TAG, "StreamResolver failed for ${track.title}, falling back to NewPipe", e)
                        try {
                            musicExtractor.getAudioStreamUrl(track.videoUrl)
                        } catch (e2: Exception) {
                            Log.e(TAG, "Both stream resolution methods failed for ${track.title}", e2)
                            musicDao.updateDownloadStatus(
                                track.id, DownloadEntity.STATUS_FAILED,
                                errorMessage = "Could not resolve audio stream: ${e.message}"
                            )
                            _downloadEvents.emit(DownloadEvent.Failed(track.id, track.title, "Stream resolution failed"))
                            return@launch
                        }
                    }

                    if (audioUrl.isEmpty()) {
                        musicDao.updateDownloadStatus(
                            track.id, DownloadEntity.STATUS_FAILED,
                            errorMessage = "No streamable audio found"
                        )
                        _downloadEvents.emit(DownloadEvent.Failed(track.id, track.title, "No audio stream"))
                        return@launch
                    }

                    var downloadUrl = audioUrl
                    if ((downloadUrl.contains("youtube.com") || downloadUrl.contains("googlevideo.com")) && !downloadUrl.contains("&range=")) {
                        val format = musicDao.getFormat(track.id)
                        val length = if (format != null && format.contentLength > 0L) {
                            format.contentLength
                        } else {
                            50_000_000L
                        }
                        downloadUrl += "&range=0-$length"
                        Log.d(TAG, "Bypassing YouTube throttling for ${track.title} with range=0-$length")
                    }

                    val outputFile = withContext(Dispatchers.IO) {
                        streamTrackToFile(
                            context = context,
                            httpClient = httpClient,
                            track = track,
                            audioUrl = downloadUrl,
                            onProgress = { progress, downloadedBytes ->
                                musicDao.updateDownloadProgress(track.id, progress, downloadedBytes)
                            }
                        )
                    }

                    val filePath = outputFile.absolutePath
                    musicDao.updateDownloadStatus(
                        trackId = track.id,
                        status = DownloadEntity.STATUS_COMPLETED,
                        filePath = filePath,
                        completedAt = System.currentTimeMillis()
                    )
                    musicDao.updateTrackLocalPath(track.id, filePath)
                    _downloadEvents.emit(DownloadEvent.Completed(track.id, track.title))

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

    fun downloadPlaylist(tracks: List<MusicItem>) {
        tracks.forEach { downloadTrack(it) }
    }

    fun cancelDownload(trackId: String) {
        scope.launch {
            activeJobs[trackId]?.cancel()
            activeJobs.remove(trackId)

            val download = musicDao.getDownloadByTrackId(trackId)
            if (download != null) {
                cleanupTempDownloadFile(context, download)
                musicDao.deleteDownloadByTrackId(trackId)
                _downloadEvents.emit(DownloadEvent.Cancelled(trackId, download.title))
            }
        }
    }

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

    fun isTrackDownloaded(trackId: String): Boolean {
        val download = musicDao.getDownloadByTrackId(trackId) ?: return false
        return download.status == DownloadEntity.STATUS_COMPLETED &&
                download.filePath != null &&
                File(download.filePath).exists()
    }

    fun getDownloadState(trackId: String): DownloadEntity? = musicDao.getDownloadByTrackId(trackId)

    fun getDownloadStorageUsed(): Long = calculateDownloadStorageUsed(context)

    fun getAvailableStorage(): Long = calculateAvailableStorage(context)

    fun deleteAllDownloads() {
        scope.launch {
            activeJobs.values.forEach { it.cancel() }
            activeJobs.clear()

            val downloadDir = File(
                context.getExternalFilesDir(Environment.DIRECTORY_MUSIC),
                RAAZI_DOWNLOAD_DIR
            )
            downloadDir.deleteRecursively()
            val all = musicDao.getAllDownloads().first()
            all.forEach { download ->
                musicDao.deleteDownloadByTrackId(download.trackId)
                musicDao.clearTrackLocalPath(download.trackId)
            }
        }
    }

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

    fun destroy() {
        if (isDestroyed) return
        isDestroyed = true
        try {
            activeJobs.values.forEach { it.cancel() }
            activeJobs.clear()
            scope.cancel()
        } catch (e: Exception) {
            Log.e(TAG, "Error during destroy", e)
        }
    }
}
