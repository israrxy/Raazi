package com.israrxy.raazi.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "playback_history")
data class PlaybackHistoryEntity(
    @PrimaryKey val id: String, // Track ID
    val title: String,
    val artist: String,
    val thumbnailUrl: String,
    val audioUrl: String, // Can be empty if streamed
    val videoUrl: String,
    val duration: Long,
    val timestamp: Long, // When it was played
    val playCount: Int = 1
)
