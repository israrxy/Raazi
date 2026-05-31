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
        FormatEntity::class,
        HomeInteractionEntity::class
    ],
    version = 14,
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

        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE tracks ADD COLUMN favoriteSyncState INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL(
                    "UPDATE tracks SET favoriteSyncState = 1 WHERE isFavorite = 1"
                )
            }
        }

        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE playlists ADD COLUMN customTitle TEXT DEFAULT NULL"
                )
            }
        }

        val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS home_interactions (
                        id TEXT NOT NULL PRIMARY KEY,
                        itemId TEXT NOT NULL,
                        sectionId TEXT NOT NULL,
                        action TEXT NOT NULL,
                        sourceType TEXT NOT NULL,
                        timestamp INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_home_interactions_itemId ON home_interactions(itemId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_home_interactions_sectionId ON home_interactions(sectionId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_home_interactions_timestamp ON home_interactions(timestamp)")
            }
        }
    }
}
