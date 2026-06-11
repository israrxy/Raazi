package com.israrxy.raazi.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import com.israrxy.raazi.data.db.TrackEntity
import com.israrxy.raazi.data.db.PlaylistEntity
import com.israrxy.raazi.data.db.PlaylistTrackCrossRef
import com.israrxy.raazi.data.db.PlaybackHistoryEntity
import com.israrxy.raazi.data.db.RelatedSongEntity
import com.israrxy.raazi.data.db.SearchHistoryEntity
import com.israrxy.raazi.data.db.HomeInteractionEntity

@Dao
interface MusicDao {
    // Favorites / Library tracks
    // Favorites
    @Query("SELECT * FROM tracks WHERE isFavorite = 1 ORDER BY timestamp DESC")
    fun getAllTracks(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE favoriteSyncState != 0 ORDER BY timestamp ASC")
    fun getPendingFavoriteSyncTracks(): List<TrackEntity>
    
    @Query("SELECT * FROM tracks WHERE id = :id")
    fun getTrack(id: String): TrackEntity?
    
    @Query("SELECT * FROM tracks WHERE localPath IS NOT NULL ORDER BY timestamp DESC")
    fun getDownloadedTracks(): Flow<List<TrackEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertTrack(track: TrackEntity): Long

    @Query("UPDATE tracks SET favoriteSyncState = :syncState WHERE id = :trackId")
    fun updateFavoriteSyncState(trackId: String, syncState: Int): Int

    @Delete
    fun deleteTrack(track: TrackEntity): Int

    // Playlists
    @Query("SELECT * FROM playlists")
    fun getAllPlaylists(): Flow<List<PlaylistEntity>>

    @Transaction
    @Query("SELECT * FROM playlists")
    fun getAllPlaylistsWithTracks(): Flow<List<PlaylistWithTracks>>

    @Query("SELECT * FROM saved_collections ORDER BY timestamp DESC")
    fun getSavedCollections(): Flow<List<SavedCollectionEntity>>

    @Query("SELECT * FROM saved_collections WHERE id = :savedCollectionId LIMIT 1")
    fun getSavedCollectionById(savedCollectionId: String): SavedCollectionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertPlaylist(playlist: PlaylistEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertSavedCollection(savedCollection: SavedCollectionEntity): Long

    @Query("SELECT * FROM playlists WHERE id = :playlistId LIMIT 1")
    fun getPlaylistById(playlistId: String): PlaylistEntity?

    @Transaction
    @Query("SELECT * FROM playlists WHERE id = :playlistId")
    fun getPlaylistWithTracks(playlistId: String): PlaylistWithTracks?

    // Playlist Tracks Management
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertPlaylistTrackCrossRef(crossRef: PlaylistTrackCrossRef): Long

    // Remove track from playlist
    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId AND trackId = :trackId")
    fun removeTrackFromPlaylist(playlistId: String, trackId: String): Int

    @Query("DELETE FROM playlists WHERE id = :playlistId")
    fun deletePlaylistById(playlistId: String): Int

    @Query("DELETE FROM saved_collections WHERE id = :savedCollectionId")
    fun deleteSavedCollectionById(savedCollectionId: String): Int

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId")
    fun deletePlaylistTracksByPlaylistId(playlistId: String): Int

    @Query("SELECT MAX(position) FROM playlist_tracks WHERE playlistId = :playlistId")
    fun getMaxPlaylistPosition(playlistId: String): Int?

    // --- Playlist Management: Rename, Reorder, Track Count ---

    @Query("UPDATE playlists SET customTitle = :newTitle WHERE id = :playlistId")
    fun renamePlaylist(playlistId: String, newTitle: String): Int

    @Query("SELECT COUNT(*) FROM playlist_tracks WHERE playlistId = :playlistId")
    fun getPlaylistTrackCount(playlistId: String): Int

    @Query("SELECT trackId FROM playlist_tracks WHERE playlistId = :playlistId ORDER BY position ASC")
    fun getPlaylistTrackIds(playlistId: String): List<String>

    @Query("UPDATE playlist_tracks SET position = :position WHERE playlistId = :playlistId AND trackId = :trackId")
    fun updatePlaylistTrackPosition(playlistId: String, trackId: String, position: Int): Int

    // --- End Playlist Management ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertPlaybackHistory(history: PlaybackHistoryEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertRelatedSongs(relatedSongs: List<RelatedSongEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertTracks(tracks: List<TrackEntity>): List<Long>

    // Smart Quick Picks: Recommendations based on last 20 played songs
    @Query("""
        SELECT t.* FROM tracks t
        JOIN (
            SELECT relatedTrackId, COUNT(sourceTrackId) as weight 
            FROM related_songs 
            WHERE sourceTrackId IN (SELECT id FROM playback_history ORDER BY timestamp DESC LIMIT 20)
            GROUP BY relatedTrackId
        ) r ON t.id = r.relatedTrackId
        ORDER BY r.weight DESC, t.timestamp DESC 
        LIMIT 20
    """)
    fun getSmartRecommendations(): Flow<List<TrackEntity>>
    
    // Playback History - increased limit for better recommendations
    @Query("SELECT * FROM playback_history ORDER BY timestamp DESC LIMIT 100")
    fun getPlaybackHistory(): Flow<List<PlaybackHistoryEntity>>

    @Query("SELECT * FROM playback_history WHERE timestamp < :cutoff ORDER BY playCount DESC, timestamp DESC LIMIT 50")
    fun getForgottenFavorites(cutoff: Long): Flow<List<PlaybackHistoryEntity>>

    @Query("SELECT * FROM playback_history WHERE timestamp > :cutoff ORDER BY playCount DESC, timestamp DESC LIMIT 50")
    fun getMostPlayed(cutoff: Long): Flow<List<PlaybackHistoryEntity>>

    @Query("""
        INSERT INTO playback_history (id, title, artist, thumbnailUrl, audioUrl, videoUrl, duration, timestamp, playCount)
        VALUES (:id, :title, :artist, :thumbnailUrl, :audioUrl, :videoUrl, :duration, :timestamp, 1)
        ON CONFLICT(id) DO UPDATE SET 
            playCount = playCount + 1, 
            timestamp = :timestamp,
            audioUrl = CASE WHEN :audioUrl != '' THEN :audioUrl ELSE audioUrl END
    """)
    fun upsertPlaybackHistory(id: String, title: String, artist: String, thumbnailUrl: String, audioUrl: String, videoUrl: String, duration: Long, timestamp: Long)

    @Delete
    fun deletePlaybackHistory(item: PlaybackHistoryEntity): Int

    // Search History
    @Query("SELECT * FROM search_history WHERE query LIKE :query || '%' ORDER BY timestamp DESC")
    fun searchHistory(query: String = ""): Flow<List<SearchHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertSearchHistory(searchHistory: SearchHistoryEntity): Long

    @Delete
    fun deleteSearchHistory(searchHistory: SearchHistoryEntity): Int

    @Query("DELETE FROM search_history")
    fun clearSearchHistory(): Int

    // Downloads Management
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertDownload(download: DownloadEntity): Long

    @Query("UPDATE downloads SET progress = :progress, downloadedBytes = :downloadedBytes, status = 1 WHERE trackId = :trackId")
    fun updateDownloadProgress(trackId: String, progress: Int, downloadedBytes: Long)

    @Query("UPDATE downloads SET status = :status, filePath = :filePath, errorMessage = :errorMessage, completedAt = CASE WHEN :status = 2 THEN :completedAt ELSE NULL END WHERE trackId = :trackId")
    fun updateDownloadStatus(trackId: String, status: Int, filePath: String? = null, errorMessage: String? = null, completedAt: Long? = System.currentTimeMillis())

    @Query("UPDATE downloads SET downloadId = :downloadId, status = 1 WHERE trackId = :trackId")
    fun updateDownloadId(trackId: String, downloadId: Long)

    @Query("UPDATE downloads SET retryCount = retryCount + 1 WHERE trackId = :trackId")
    fun incrementRetryCount(trackId: String)

    @Query("SELECT * FROM downloads WHERE status IN (0, 1) ORDER BY createdAt ASC")
    fun getActiveDownloads(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE status = 2 ORDER BY completedAt DESC")
    fun getCompletedDownloads(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE status = 3 ORDER BY createdAt DESC")
    fun getFailedDownloads(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads ORDER BY createdAt DESC")
    fun getAllDownloads(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE trackId = :trackId")
    fun getDownloadByTrackId(trackId: String): DownloadEntity?

    @Query("DELETE FROM downloads WHERE trackId = :trackId")
    fun deleteDownloadByTrackId(trackId: String)

    @Query("SELECT COUNT(*) FROM downloads WHERE status IN (0, 1)")
    fun getActiveDownloadCount(): Flow<Int>

    @Query("UPDATE tracks SET localPath = :localPath WHERE id = :trackId")
    fun updateTrackLocalPath(trackId: String, localPath: String)

    @Query("UPDATE tracks SET localPath = NULL WHERE id = :trackId")
    fun clearTrackLocalPath(trackId: String)

    // Format / Codec Info
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertFormat(format: FormatEntity): Long

    @Query("SELECT * FROM format WHERE id = :trackId")
    fun getFormat(trackId: String): FormatEntity?

    @Query("SELECT * FROM format WHERE id = :trackId")
    fun getFormatFlow(trackId: String): Flow<FormatEntity?>

    // Home personalization metrics
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertHomeInteraction(interaction: HomeInteractionEntity): Long

    @Query("SELECT * FROM home_interactions WHERE timestamp > :since ORDER BY timestamp DESC")
    fun getHomeInteractionsSince(since: Long): List<HomeInteractionEntity>

    @Query("SELECT itemId FROM home_interactions WHERE action = :action AND timestamp > :since GROUP BY itemId ORDER BY COUNT(*) DESC")
    fun getHomeInteractionItemIds(action: String, since: Long): List<String>

    @Query("DELETE FROM home_interactions WHERE timestamp < :before")
    fun deleteOldHomeInteractions(before: Long): Int

    @Query("""
        SELECT t.* FROM tracks t
        INNER JOIN related_songs r ON t.id = r.relatedTrackId
        WHERE r.sourceTrackId = :trackId
    """)
    fun getRelatedTracks(trackId: String): Flow<List<TrackEntity>>
}

data class PlaylistWithTracks(
    @Embedded val playlist: PlaylistEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = PlaylistTrackCrossRef::class,
            parentColumn = "playlistId",
            entityColumn = "trackId"
        )
    )
    val tracks: List<TrackEntity>
)
