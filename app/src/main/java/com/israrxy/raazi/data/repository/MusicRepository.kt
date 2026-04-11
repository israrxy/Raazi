package com.israrxy.raazi.data.repository

import com.israrxy.raazi.data.db.AppDatabase
import com.israrxy.raazi.data.db.PlaylistEntity
import com.israrxy.raazi.data.db.SavedCollectionEntity
import com.israrxy.raazi.data.db.TrackEntity
import com.israrxy.raazi.data.db.PlaylistTrackCrossRef
import com.israrxy.raazi.data.lyrics.LyricsScriptFilter
import com.israrxy.raazi.data.lyrics.SavedLyricsStore
import com.israrxy.raazi.data.local.SettingsDataStore
import com.israrxy.raazi.data.playlist.buildYouTubePlaylistDescription
import com.israrxy.raazi.data.playlist.isYouTubeEditablePlaylist
import com.israrxy.raazi.data.playlist.isYouTubeSyncedPlaylist
import com.israrxy.raazi.data.remote.LyricsSearchResult
import com.israrxy.raazi.model.MusicContentType
import com.israrxy.raazi.model.MusicItem
import com.israrxy.raazi.model.Playlist
import com.israrxy.raazi.model.SavedCollectionItem
import com.israrxy.raazi.model.SearchResult
import com.israrxy.raazi.model.toSavedCollectionItem
import com.israrxy.raazi.model.toSavedCollectionItemOrNull
import com.israrxy.raazi.service.YouTubeMusicExtractor
import com.zionhuang.innertube.YouTube
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

data class AccountSyncResult(
    val likedSongs: Int,
    val playlists: Int
)

class MusicRepository(
    private val db: AppDatabase,
    context: android.content.Context,
    private val extractor: YouTubeMusicExtractor = YouTubeMusicExtractor.getInstance()
) {
    private val downloadManager = com.israrxy.raazi.service.SimpleDownloadManager(context)
    private val lrcLibApi = com.israrxy.raazi.data.remote.LrcLibApi(context)
    private val savedLyricsStore = SavedLyricsStore(context)
    private val chartsExtractor = com.israrxy.raazi.data.remote.YouTubeChartsExtractor()
    private val settingsDataStore = SettingsDataStore(context)

    // Remote
    suspend fun getLyrics(
        item: MusicItem,
        preferredScript: LyricsScriptFilter = LyricsScriptFilter.ALL
    ): com.israrxy.raazi.data.remote.Lyrics? {
        return savedLyricsStore.get(item.id)
            ?: lrcLibApi.getLyrics(item.title, item.artist, item.duration, preferredScript)
    }

    suspend fun searchLyricsOptions(
        item: MusicItem,
        titleOverride: String = item.title,
        artistOverride: String = item.artist,
        preferredScript: LyricsScriptFilter = LyricsScriptFilter.ALL
    ): List<LyricsSearchResult> {
        return lrcLibApi.searchLyricsOptions(
            trackName = titleOverride,
            artistName = artistOverride,
            duration = item.duration,
            preferredScript = preferredScript
        )
    }

    suspend fun saveLyricsForTrack(item: MusicItem, lyrics: com.israrxy.raazi.data.remote.Lyrics) {
        savedLyricsStore.put(item.id, lyrics.copy(source = "Saved ${lyrics.source}"))
    }

    suspend fun clearSavedLyricsForTrack(item: MusicItem) {
        savedLyricsStore.remove(item.id)
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
        val localPlaylist = db.musicDao().getPlaylistWithTracks(playlistId)

        if (localPlaylist != null && !localPlaylist.playlist.isYouTubeSyncedPlaylist()) {
            return@withContext mapToDomain(localPlaylist)
        }

        try {
            extractor.getPlaylist(playlistId)
        } catch (error: Exception) {
            if (localPlaylist != null) {
                mapToDomain(localPlaylist)
            } else {
                throw error
            }
        }
    }

    suspend fun getLikedSongsPlaylist(): Playlist = withContext(Dispatchers.IO) {
        val remotePlaylist = YouTube.playlist("LM").getOrThrow()
        Playlist(
            id = "favorites",
            title = remotePlaylist.playlist.title.ifBlank { "Liked Songs" },
            description = "From your YouTube Music account",
            thumbnailUrl = remotePlaylist.playlist.thumbnail ?: "",
            items = remotePlaylist.songs.mapNotNull { song -> song.toMusicItem() }
        )
    }

    // Local
    val favoriteTracks: Flow<List<MusicItem>> = db.musicDao().getAllTracks().map { entities ->
        entities.map { mapToDomain(it) }
    }
    
    val downloadedTracks: Flow<List<MusicItem>> = db.musicDao().getDownloadedTracks().map { entities ->
        entities.map { mapToDomain(it) }
    }

    val savedCollections: Flow<List<SavedCollectionItem>> = db.musicDao().getSavedCollections().map { entities ->
        entities.map { mapSavedCollectionToDomain(it) }
    }

    suspend fun addToFavorites(item: MusicItem) {
        withContext(Dispatchers.IO) {
            val existing = db.musicDao().getTrack(item.id)
            val localPath = existing?.localPath

            val entity = item.copy(
                isFavorite = true, 
                localPath = localPath ?: item.localPath
            ).toEntity()

            db.musicDao().insertTrack(entity)

            item.youtubeVideoIdOrNull()?.let { videoId ->
                if (isYouTubeLoggedIn()) {
                    YouTube.likeVideo(videoId, like = true).getOrThrow()
                }
            }
        }
    }

    suspend fun removeFromFavorites(item: MusicItem) {
        withContext(Dispatchers.IO) {
            val existing = db.musicDao().getTrack(item.id)
            if (existing != null) {
                val updated = existing.copy(isFavorite = false)
                db.musicDao().insertTrack(updated)

                item.youtubeVideoIdOrNull()?.let { videoId ->
                    if (isYouTubeLoggedIn()) {
                        YouTube.likeVideo(videoId, like = false).getOrThrow()
                    }
                }
            }
        }
    }
    
    // Playlists Management
    val userPlaylists: Flow<List<PlaylistEntity>> = db.musicDao().getAllPlaylists()

    suspend fun toggleSavedCollection(item: MusicItem) {
        val savedCollection = item.toSavedCollectionItemOrNull() ?: return
        toggleSavedCollection(savedCollection)
    }

    suspend fun toggleSavedCollection(playlist: Playlist) {
        toggleSavedCollection(playlist.toSavedCollectionItem())
    }

    suspend fun toggleSavedArtist(
        artistId: String,
        artistName: String,
        thumbnailUrl: String = ""
    ) {
        val normalizedId = artistId.ifBlank { artistName.trim() }
        if (normalizedId.isBlank()) return
        toggleSavedCollection(
            SavedCollectionItem(
                id = com.israrxy.raazi.model.savedCollectionId(MusicContentType.ARTIST, normalizedId),
                sourceId = normalizedId,
                title = artistName.ifBlank { "Unknown Artist" },
                subtitle = "Artist",
                thumbnailUrl = thumbnailUrl,
                contentType = MusicContentType.ARTIST
            )
        )
    }

    suspend fun createPlaylist(name: String, syncedToYouTube: Boolean = false): PlaylistEntity =
        withContext(Dispatchers.IO) {
            val validName = name.ifBlank { "My Playlist" }
            val playlist = if (syncedToYouTube) {
                if (!isYouTubeLoggedIn()) {
                    throw IllegalStateException("Sign in to YouTube Music before creating synced playlists.")
                }

                val remotePlaylistId = YouTube.createPlaylist(validName)
                PlaylistEntity(
                    id = remotePlaylistId,
                    title = validName,
                    description = buildYouTubePlaylistDescription(isEditable = true),
                    thumbnailUrl = ""
                )
            } else {
                PlaylistEntity(
                    id = java.util.UUID.randomUUID().toString(),
                    title = validName,
                    description = "",
                    thumbnailUrl = ""
                )
            }
            db.musicDao().insertPlaylist(playlist)
            playlist
        }

    suspend fun syncYouTubeLibrary(): AccountSyncResult = withContext(Dispatchers.IO) {
        if (!isYouTubeLoggedIn()) {
            return@withContext AccountSyncResult(likedSongs = 0, playlists = 0)
        }

        AccountSyncResult(
            likedSongs = syncLikedSongsFromYouTube(),
            playlists = syncPlaylistsFromYouTube()
        )
    }

    suspend fun clearYouTubePlaylistsCache() = withContext(Dispatchers.IO) {
        db.musicDao().getAllPlaylists().first()
            .filter { it.isYouTubeSyncedPlaylist() }
            .forEach { playlist ->
                db.musicDao().deletePlaylistTracksByPlaylistId(playlist.id)
                db.musicDao().deletePlaylistById(playlist.id)
            }
    }

    suspend fun addToPlaylist(playlistId: String, track: MusicItem) {
        val playlist = withContext(Dispatchers.IO) {
            db.musicDao().getPlaylistById(playlistId)
        } ?: throw IllegalArgumentException("Playlist not found")

        addToPlaylist(track, playlist)
    }

    suspend fun addToPlaylist(track: MusicItem, playlist: PlaylistEntity) {
        withContext(Dispatchers.IO) {
            if (playlist.isYouTubeSyncedPlaylist()) {
                if (!playlist.isYouTubeEditablePlaylist()) {
                    throw IllegalStateException("This synced playlist can't be edited from Raazi.")
                }

                val videoId = track.youtubeVideoIdOrNull()
                    ?: throw IllegalArgumentException("Only YouTube Music tracks can be added to synced playlists.")

                YouTube.addToPlaylist(playlist.id, videoId).getOrThrow()
                return@withContext
            }

            val trackEntity = track.toEntity()
            db.musicDao().insertTrack(trackEntity)

            val nextPosition = (db.musicDao().getMaxPlaylistPosition(playlist.id) ?: -1) + 1
            db.musicDao().insertPlaylistTrackCrossRef(
                PlaylistTrackCrossRef(
                    playlistId = playlist.id,
                    trackId = track.id,
                    position = nextPosition
                )
            )
        }
    }

    // History
    suspend fun addToHistory(item: MusicItem) {
        withContext(Dispatchers.IO) {
            // Use upsert to properly increment play count
            db.musicDao().upsertPlaybackHistory(
                id = item.id,
                title = item.title,
                artist = item.artist,
                thumbnailUrl = item.thumbnailUrl ?: "",
                audioUrl = item.audioUrl ?: "",
                videoUrl = item.videoUrl ?: "",
                duration = item.duration,
                timestamp = System.currentTimeMillis()
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

    private suspend fun toggleSavedCollection(savedCollection: SavedCollectionItem) {
        withContext(Dispatchers.IO) {
            if (db.musicDao().getSavedCollectionById(savedCollection.id) != null) {
                db.musicDao().deleteSavedCollectionById(savedCollection.id)
            } else {
                db.musicDao().insertSavedCollection(savedCollection.toEntity())
            }
        }
    }

    private fun SavedCollectionItem.toEntity(): SavedCollectionEntity {
        return SavedCollectionEntity(
            id = id,
            sourceId = sourceId,
            title = title,
            subtitle = subtitle,
            thumbnailUrl = thumbnailUrl,
            contentType = contentType.name
        )
    }

    private fun mapSavedCollectionToDomain(entity: SavedCollectionEntity): SavedCollectionItem {
        val parsedType = runCatching { enumValueOf<MusicContentType>(entity.contentType) }
            .getOrDefault(MusicContentType.UNKNOWN)
        return SavedCollectionItem(
            id = entity.id,
            sourceId = entity.sourceId,
            title = entity.title,
            subtitle = entity.subtitle,
            thumbnailUrl = entity.thumbnailUrl,
            contentType = parsedType
        )
    }

    private suspend fun syncLikedSongsFromYouTube(): Int {
        val remotePlaylist = YouTube.playlist("LM").getOrThrow()
        var syncedCount = 0

        remotePlaylist.songs.mapNotNull { it.toMusicItem() }.forEach { item ->
            val existing = db.musicDao().getTrack(item.id)
            val entity = item.copy(
                isFavorite = true,
                localPath = existing?.localPath ?: item.localPath
            ).toEntity()
            db.musicDao().insertTrack(entity)
            syncedCount++
        }

        return syncedCount
    }

    private suspend fun syncPlaylistsFromYouTube(): Int {
        val libraryPage = YouTube.library("FEmusic_liked_playlists").getOrThrow()
        val remotePlaylists = libraryPage.items
            .filterIsInstance<com.zionhuang.innertube.models.PlaylistItem>()
            .filterNot { it.id == "LM" || it.id == "SE" }

        val existingRemoteIds = db.musicDao().getAllPlaylists().first()
            .filter { it.isYouTubeSyncedPlaylist() }
            .map { it.id }
            .toSet()

        val freshRemoteIds = mutableSetOf<String>()
        remotePlaylists.forEach { playlist ->
            freshRemoteIds += playlist.id
            db.musicDao().insertPlaylist(
                PlaylistEntity(
                    id = playlist.id,
                    title = playlist.title,
                    description = buildYouTubePlaylistDescription(playlist.isEditable),
                    thumbnailUrl = playlist.thumbnail ?: ""
                )
            )
        }

        (existingRemoteIds - freshRemoteIds).forEach { playlistId ->
            db.musicDao().deletePlaylistTracksByPlaylistId(playlistId)
            db.musicDao().deletePlaylistById(playlistId)
        }

        return freshRemoteIds.size
    }

    private suspend fun isYouTubeLoggedIn(): Boolean {
        return settingsDataStore.innerTubeCookie.first()?.contains("SAPISID") == true
    }

    private fun MusicItem.youtubeVideoIdOrNull(): String? {
        val candidates = listOf(id, videoUrl)
        for (candidate in candidates) {
            if (candidate.isBlank()) continue
            if (candidate.contains("soundcloud.com", ignoreCase = true) ||
                candidate.contains("bandcamp.com", ignoreCase = true)
            ) {
                return null
            }
            if (candidate.matches(Regex("[A-Za-z0-9_-]{11}"))) {
                return candidate
            }
            if (candidate.contains("watch?v=")) {
                val extracted = candidate.substringAfter("watch?v=").substringBefore("&")
                return extracted.takeIf { it.isNotBlank() }
            }
            if (candidate.contains("youtu.be/")) {
                val extracted = candidate.substringAfter("youtu.be/").substringBefore("?")
                return extracted.takeIf { it.isNotBlank() }
            }
        }
        return null
    }

    private fun com.zionhuang.innertube.models.SongItem.toMusicItem(): MusicItem? {
        val songId = id ?: return null
        return MusicItem(
            id = songId,
            title = title ?: "Unknown",
            artist = artists?.joinToString(", ") { it.name ?: "" }.orEmpty().ifBlank { "Unknown Artist" },
            duration = (duration?.toLong() ?: 0L) * 1000L,
            thumbnailUrl = thumbnail ?: "",
            audioUrl = "",
            videoUrl = songId,
            isLive = false,
            isFavorite = true
        )
    }
}
