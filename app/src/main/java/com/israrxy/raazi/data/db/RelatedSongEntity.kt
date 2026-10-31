package com.israrxy.raazi.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "related_songs",
    primaryKeys = ["sourceTrackId", "relatedTrackId"],
    indices = [Index(value = ["relatedTrackId"])]
)
data class RelatedSongEntity(
    val sourceTrackId: String,
    val relatedTrackId: String,
    val timestamp: Long = System.currentTimeMillis() // To age out old relations if needed
)
