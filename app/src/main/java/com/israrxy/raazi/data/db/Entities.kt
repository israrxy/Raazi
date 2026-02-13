package com.israrxy.raazi.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index

@Entity(tableName = "tracks")
data class TrackEntity(
    @PrimaryKey val id: String,
    val title: String,
    val artist: String,
    val duration: Long,
    val thumbnailUrl: String,
    val audioUrl: String,
    val videoUrl: String,
    val isLive: Boolean,
    val localPath: String? = null,
    val isFavorite: Boolean = false,
    val timestamp: Long = System.currentTimeMillis() // For recently played/added
)

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey val id: String,
    val title: String,
    val description: String,
    val thumbnailUrl: String
)

@Entity(
    tableName = "playlist_tracks",
    primaryKeys = ["playlistId", "trackId"],
    indices = [Index(value = ["trackId"])]
)
data class PlaylistTrackCrossRef(
    val playlistId: String,
    val trackId: String,
    val position: Int // For ordering
)

@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey val trackId: String,
    val title: String,
    val artist: String,
    val thumbnailUrl: String,
    val audioUrl: String,
    val videoUrl: String,
    val duration: Long,
    val downloadId: Long = -1L,        // Android DownloadManager ID
    val filePath: String? = null,       // Local file path once complete
    val status: Int = 0,               // 0=pending, 1=downloading, 2=completed, 3=failed, 4=paused
    val progress: Int = 0,             // 0-100
    val fileSize: Long = 0L,           // Total bytes
    val downloadedBytes: Long = 0L,    // Bytes downloaded so far
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null,
    val errorMessage: String? = null,
    val retryCount: Int = 0
) {
    companion object {
        const val STATUS_PENDING = 0
        const val STATUS_DOWNLOADING = 1
        const val STATUS_COMPLETED = 2
        const val STATUS_FAILED = 3
        const val STATUS_PAUSED = 4
    }
}

@Entity(tableName = "format")
data class FormatEntity(
    @PrimaryKey val id: String,          // track ID
    val mimeType: String,                // e.g. "audio/webm", "audio/mp4"
    val codecs: String,                  // e.g. "opus", "mp4a.40.2"
    val bitrate: Int,                    // in bps
    val sampleRate: Int? = null,         // in Hz
    val contentLength: Long = 0,         // file size in bytes
    val loudnessDb: Double? = null,
    val updatedAt: Long = System.currentTimeMillis()
)
