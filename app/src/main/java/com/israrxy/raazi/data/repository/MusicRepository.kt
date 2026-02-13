package com.israrxy.raazi.data.repository

import com.israrxy.raazi.data.db.AppDatabase
import com.israrxy.raazi.data.db.PlaylistEntity
import com.israrxy.raazi.data.db.TrackEntity
import com.israrxy.raazi.model.MusicItem
import com.israrxy.raazi.model.Playlist
import com.israrxy.raazi.model.SearchResult
import com.israrxy.raazi.service.YouTubeMusicExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import com.israrxy.raazi.data.db.PlaylistTrackCrossRef

class MusicRepository(
    private val db: AppDatabase,
    context: android.content.Context,
    private val extractor: YouTubeMusicExtractor = YouTubeMusicExtractor()
) {
    private val downloadManager = com.israrxy.raazi.service.SimpleDownloadManager(context)
    private val lrcLibApi = com.israrxy.raazi.data.remote.LrcLibApi()
    private val chartsExtractor = com.israrxy.raazi.data.remote.YouTubeChartsExtractor()

    // Remote
    suspend fun getLyrics(trackName: String, artistName: String, duration: Long): com.israrxy.raazi.data.remote.Lyrics? {
        return lrcLibApi.getLyrics(trackName, artistName, duration)
    }

    suspend fun getSearchSuggestions(query: String): List<String> {
        return extractor.getSearchSuggestions(query)
    }

    suspend fun searchMusic(query: String, serviceId: Int = 0): SearchResult {
        return extractor.searchMusic(query, serviceId)
    }
    
    suspend fun downloadTrack(item: MusicItem) {
        withContext(Dispatchers.IO) {
            val url = if (item.audioUrl.isNotEmpty()) item.audioUrl else getStreamUrl(item.videoUrl)
            if (url.isNotEmpty()) {
                val downloadId = downloadManager.downloadTrack(item, url)
                if (downloadId != null) {
                    // Save to DB with local path for offline playback
                    val path = downloadManager.getDownloadPath(item)
                    
                    // Check existing to preserve isFavorite
                    val existing = db.musicDao().getTrack(item.id)
                    val isFav = existing?.isFavorite ?: false
                    
                    val entity = item.copy(localPath = path, audioUrl = url, isFavorite = isFav).toEntity()
                    db.musicDao().insertTrack(entity)
                }
            }
        }
    }
    
    suspend fun getTrending(): SearchResult {
        // Use Charts API for Global Top Songs
        try {
            val charts = chartsExtractor.getCharts()
            // OuterTune usually has "Top songs" or "Top songs Global"
            val topSongs = charts.entries.find { it.key.contains("Top songs", ignoreCase = true) }?.value
            if (!topSongs.isNullOrEmpty()) {
                return SearchResult("Top Songs Global", topSongs)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return extractor.getTopSongs()
    }
    
    suspend fun getTopSongs(): SearchResult {
         return getTrending()
    }

    suspend fun getTrendingVideos(): SearchResult {
        // Use Charts API for Trending Videos
        try {
             val charts = chartsExtractor.getCharts()
             val topVideos = charts.entries.find { it.key.contains("Top music videos", ignoreCase = true) }?.value
             if (!topVideos.isNullOrEmpty()) {
                 return SearchResult("Top Music Videos", topVideos)
             }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return extractor.getTrendingVideos()
    }
    
    suspend fun getHomeFeed(): List<SearchResult> {
        return extractor.getHomeSections()
    }

    suspend fun getAllCharts(): Map<String, List<MusicItem>> {
        return try {
            chartsExtractor.getCharts()
        } catch (e: Exception) {
            emptyMap()
        }
    }
    
    suspend fun getMoodPlaylists(mood: String): List<Playlist> {
        return extractor.getMoodPlaylists(mood)
    }

    suspend fun getGenrePlaylists(genre: String): List<Playlist> {
        return extractor.getMoodPlaylists(genre)
    }
    
    suspend fun getNewReleaseAlbums(): List<Playlist> {
        return extractor.getNewReleaseAlbums()
    }
    
    suspend fun getRecommendations(videoId: String): List<MusicItem> {
        return extractor.getUpNext(videoId)
    }


    suspend fun getStreamUrl(videoUrl: String): String {
        return extractor.getAudioStreamUrl(videoUrl)
    }
    
    suspend fun getPlaylist(playlistId: String): Playlist = withContext(Dispatchers.IO) {
        // Try local DB first
        val localPlaylist = db.musicDao().getPlaylistWithTracks(playlistId)
        if (localPlaylist != null) {
            mapToDomain(localPlaylist)
        } else {
            // Fallback to network
            extractor.getPlaylist(playlistId)
        }
    }

    // Local
    val favoriteTracks: Flow<List<MusicItem>> = db.musicDao().getAllTracks().map { entities ->
        entities.map { mapToDomain(it) }
    }
    
    val downloadedTracks: Flow<List<MusicItem>> = db.musicDao().getDownloadedTracks().map { entities ->
        entities.map { mapToDomain(it) }
    }

    suspend fun addToFavorites(item: MusicItem) {
        withContext(Dispatchers.IO) {
            // Preserve existing data (like download path)
            val existing = db.musicDao().getTrack(item.id)
            val localPath = existing?.localPath
            
            // If we have an existing entity, we might want to keep its timestamp or other fields?
            // For favorites, usually we update timestamp to now to show at top? Yes.
            
            val entity = item.copy(
                isFavorite = true, 
                localPath = localPath ?: item.localPath // Keep existing download if any
            ).toEntity()
            
            db.musicDao().insertTrack(entity)
        }
    }

    suspend fun removeFromFavorites(item: MusicItem) {
        withContext(Dispatchers.IO) {
            val existing = db.musicDao().getTrack(item.id)
            if (existing != null) {
                // Determine if we should delete or just unset flag
                if (existing.localPath == null) {
                    // Not downloaded, assume can be deleted from library if not favorite
                    // But wait, what if it's in history? History is separate table now.
                    // Tracks table is essentially "Library + Downloads". 
                    // If not favorite and not downloaded, we can remove from 'tracks' table?
                    // Actually, let's just unset the flag for safety. 
                    // A proper implementation would check if it's referenced elsewhere or just keep it cached.
                    // For "Liked Songs Count" fix, unsetting flag is sufficient.
                     val updated = existing.copy(isFavorite = false)
                     // If it's not downloaded and not favorite, we could delete it to clean up?
                     // Let's stick to unsetting flag to be safe against deleting unintended data.
                     db.musicDao().insertTrack(updated)
                } else {
                    // It is downloaded, MUST keep it, just unlike
                    val updated = existing.copy(isFavorite = false)
                    db.musicDao().insertTrack(updated)
                }
            }
        }
    }
    
    // Playlists Management
    val userPlaylists: Flow<List<PlaylistEntity>> = db.musicDao().getAllPlaylists()

    suspend fun createPlaylist(name: String) {
        withContext(Dispatchers.IO) {
            val playlist = PlaylistEntity(
                id = java.util.UUID.randomUUID().toString(),
                title = name,
                description = "",
                thumbnailUrl = ""
            )
            db.musicDao().insertPlaylist(playlist)
        }
    }

    suspend fun addToPlaylist(track: MusicItem, playlist: PlaylistEntity) {
        withContext(Dispatchers.IO) {
            // Ensure track exists in DB
            val trackEntity = track.toEntity()
            db.musicDao().insertTrack(trackEntity)
            
            // Add relation
            // Determine position - simplest is to put at end, but simpler is just 0 if we don't query order yet
            // Ideally: val nextPos = db.musicDao().getMaxPosition(playlist.id) + 1
            // For now, usage 0.
            db.musicDao().insertPlaylistTrackCrossRef(
                PlaylistTrackCrossRef(
                    playlistId = playlist.id,
                    trackId = track.id,
                    position = 0 
                )
            )
        }
    }

    // History
    suspend fun addToHistory(item: MusicItem) {
        withContext(Dispatchers.IO) {
            db.musicDao().insertPlaybackHistory(
                com.israrxy.raazi.data.db.PlaybackHistoryEntity(
                    id = item.id,
                    title = item.title,
                    artist = item.artist,
                    thumbnailUrl = item.thumbnailUrl,
                    audioUrl = item.audioUrl,
                    videoUrl = item.videoUrl,
                    duration = item.duration,
                    timestamp = System.currentTimeMillis()
                )
            )
        }
    }
    
    suspend fun fetchAndSaveRelated(item: MusicItem) {
        withContext(Dispatchers.IO) {
            try {
                val related = extractor.getUpNext(item.videoUrl.ifEmpty { item.id })
                if (related.isNotEmpty()) {
                    val mappings = related.map { 
                        com.israrxy.raazi.data.db.RelatedSongEntity(
                             sourceTrackId = item.id,
                             relatedTrackId = it.id
                        ) 
                    }
                    db.musicDao().insertRelatedSongs(mappings)
                    
                    val tracks = related.map { it.toEntity() }
                    db.musicDao().insertTracks(tracks)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    val playbackHistory: Flow<List<MusicItem>> = db.musicDao().getPlaybackHistory().map { entities ->
        entities.map { mapHistoryToDomain(it) }
    }
    
    val quickPicks: Flow<List<MusicItem>> = db.musicDao().getSmartRecommendations().map { entities ->
        entities.map { mapToDomain(it) }
    }

    fun getForgottenFavorites(): Flow<List<MusicItem>> {
        val cutoff = System.currentTimeMillis() - 86400000L * 14 // 2 weeks
        return db.musicDao().getForgottenFavorites(cutoff).map { entities ->
           entities.map { mapHistoryToDomain(it) }
        }
    }
    
    fun getKeepListening(): Flow<List<MusicItem>> {
        val cutoff = System.currentTimeMillis() - 86400000L * 14 // 2 weeks
        return db.musicDao().getMostPlayed(cutoff).map { entities ->
            entities.map { mapHistoryToDomain(it) }
        }
    }

    // Mappers
    private fun MusicItem.toEntity(): TrackEntity {
        return TrackEntity(
            id = id,
            title = title,
            artist = artist,
            duration = duration,
            thumbnailUrl = thumbnailUrl,
            audioUrl = audioUrl,
            videoUrl = videoUrl,
            isLive = isLive,
            localPath = localPath,
            isFavorite = isFavorite
        )
    }

    // Removed toHistoryEntity as we pass params directly to upsert
    
    private fun mapToDomain(entity: TrackEntity): MusicItem {
        return MusicItem(
            id = entity.id,
            title = entity.title,
            artist = entity.artist,
            duration = entity.duration,
            thumbnailUrl = entity.thumbnailUrl,
            audioUrl = entity.audioUrl,
            videoUrl = entity.videoUrl,
            isLive = entity.isLive,
            localPath = entity.localPath,
            isFavorite = entity.isFavorite
        )
    }

    private fun mapHistoryToDomain(entity: com.israrxy.raazi.data.db.PlaybackHistoryEntity): MusicItem {
        return MusicItem(
            id = entity.id,
            title = entity.title,
            artist = entity.artist,
            duration = entity.duration,
            thumbnailUrl = entity.thumbnailUrl,
            audioUrl = entity.audioUrl,
            videoUrl = entity.videoUrl,
            isLive = false, 
            localPath = null 
        )
    }
    
    private fun mapToDomain(playlist: com.israrxy.raazi.data.db.PlaylistWithTracks): Playlist {
        return Playlist(
            id = playlist.playlist.id,
            title = playlist.playlist.title,
            description = playlist.playlist.description,
            thumbnailUrl = playlist.playlist.thumbnailUrl,
            items = playlist.tracks.map { mapToDomain(it) }
        )
    }
}
