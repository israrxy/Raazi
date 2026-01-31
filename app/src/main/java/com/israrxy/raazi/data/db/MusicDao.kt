package com.israrxy.raazi.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import com.israrxy.raazi.data.db.TrackEntity
import com.israrxy.raazi.data.db.PlaylistEntity
import com.israrxy.raazi.data.db.PlaylistTrackCrossRef
import com.israrxy.raazi.data.db.PlaybackHistoryEntity
import com.israrxy.raazi.data.db.RelatedSongEntity
import com.israrxy.raazi.data.db.SearchHistoryEntity

@Dao
interface MusicDao {
    // Favorites / Library tracks
    @Query("SELECT * FROM tracks ORDER BY timestamp DESC")
    fun getAllTracks(): Flow<List<TrackEntity>>
    
    @Query("SELECT * FROM tracks WHERE localPath IS NOT NULL ORDER BY timestamp DESC")
    fun getDownloadedTracks(): Flow<List<TrackEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertTrack(track: TrackEntity): Long

    @Delete
    fun deleteTrack(track: TrackEntity): Int

    // Playlists
    @Query("SELECT * FROM playlists")
    fun getAllPlaylists(): Flow<List<PlaylistEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertPlaylist(playlist: PlaylistEntity): Long

    @Transaction
    @Query("SELECT * FROM playlists WHERE id = :playlistId")
    fun getPlaylistWithTracks(playlistId: String): PlaylistWithTracks?

    // Playlist Tracks Management
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertPlaylistTrackCrossRef(crossRef: PlaylistTrackCrossRef): Long

    // Remove track from playlist
    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId AND trackId = :trackId")
    fun removeTrackFromPlaylist(playlistId: String, trackId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertPlaybackHistory(history: PlaybackHistoryEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertRelatedSongs(relatedSongs: List<RelatedSongEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertTracks(tracks: List<TrackEntity>)

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
    
    // Playback History
    @Query("SELECT * FROM playback_history ORDER BY timestamp DESC LIMIT 20")
    fun getPlaybackHistory(): Flow<List<PlaybackHistoryEntity>>

    @Query("SELECT * FROM playback_history WHERE timestamp < :cutoff ORDER BY playCount DESC LIMIT 20")
    fun getForgottenFavorites(cutoff: Long): Flow<List<PlaybackHistoryEntity>>

    @Query("SELECT * FROM playback_history WHERE timestamp > :cutoff ORDER BY playCount DESC LIMIT 20")
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
    fun deletePlaybackHistory(item: PlaybackHistoryEntity)

    // Search History
    @Query("SELECT * FROM search_history WHERE query LIKE :query || '%' ORDER BY timestamp DESC")
    fun searchHistory(query: String = ""): Flow<List<SearchHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertSearchHistory(searchHistory: SearchHistoryEntity)

    @Delete
    fun deleteSearchHistory(searchHistory: SearchHistoryEntity)

    @Query("DELETE FROM search_history")
    fun clearSearchHistory()
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
