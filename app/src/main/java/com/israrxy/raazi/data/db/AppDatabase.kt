package com.israrxy.raazi.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        TrackEntity::class,
        PlaylistEntity::class,
        SavedCollectionEntity::class,
        PlaylistTrackCrossRef::class,
        PlaybackHistoryEntity::class,
        RelatedSongEntity::class,
        SearchHistoryEntity::class,
        DownloadEntity::class,
        FormatEntity::class
    ],
    version = 11,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun musicDao(): MusicDao

    companion object {
        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS saved_collections (
                        id TEXT NOT NULL PRIMARY KEY,
                        sourceId TEXT NOT NULL,
                        title TEXT NOT NULL,
                        subtitle TEXT NOT NULL,
                        thumbnailUrl TEXT NOT NULL,
                        contentType TEXT NOT NULL,
                        timestamp INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }
    }
}
