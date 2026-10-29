package com.israrxy.raazi.data.repository

import com.israrxy.raazi.data.db.AppDatabase
import com.israrxy.raazi.data.db.HomeInteractionEntity
import com.israrxy.raazi.data.db.PlaylistEntity
import com.israrxy.raazi.data.db.TrackEntity
import com.israrxy.raazi.data.local.SettingsDataStore
import com.israrxy.raazi.data.lyrics.LyricsScriptFilter
import com.israrxy.raazi.data.lyrics.SavedLyricsStore
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class MusicRepository(
    internal val db: AppDatabase,
    context: android.content.Context,
    private val extractor: YouTubeMusicExtractor = YouTubeMusicExtractor.getInstance()
) {
    private val downloadManager = com.israrxy.raazi.service.SimpleDownloadManager(context)
    private val lrcLibApi = com.israrxy.raazi.data.remote.LrcLibApi(context)
    private val lyricsAggregator = com.israrxy.raazi.data.remote.MultiSourceLyricsAggregator(context, lrcLibApi)
    private val savedLyricsStore = SavedLyricsStore(context)
    private val chartsExtractor = com.israrxy.raazi.data.remote.YouTubeChartsExtractor()
    internal val settingsDataStore = SettingsDataStore(context)

    suspend fun getLyrics(
        item: MusicItem,
        preferredScript: LyricsScriptFilter = LyricsScriptFilter.ALL
    ): com.israrxy.raazi.data.remote.Lyrics? {
        return savedLyricsStore.get(item.id)
            ?: lyricsAggregator.getLyrics(
                trackId = item.id,
                trackName = item.title,
                artistName = item.artist,
                durationMs = item.duration,
                preferredScript = preferredScript
            )
    }

    suspend fun searchLyricsOptions(
        item: MusicItem,
        titleOverride: String = item.title,
        artistOverride: String = item.artist,
        preferredScript: LyricsScriptFilter = LyricsScriptFilter.ALL
    ): List<LyricsSearchResult> {
        return lyricsAggregator.searchAllProviders(
            trackId = item.id,
            trackName = titleOverride,
            artistName = artistOverride,
            durationMs = item.duration,
            preferredScript = preferredScript
        )
    }

    suspend fun saveLyricsForTrack(item: MusicItem, lyrics: com.israrxy.raazi.data.remote.Lyrics) {
        savedLyricsStore.put(item.id, lyrics.copy(source = "Saved ${lyrics.source}"))
    }

    suspend fun clearSavedLyricsForTrack(item: MusicItem) {
        savedLyricsStore.remove(item.id)
    }

    suspend fun getSearchSuggestions(query: String): List<String> = extractor.getSearchSuggestions(query)

    suspend fun searchMusic(query: String, serviceId: Int = 0): SearchResult = extractor.searchMusic(query, serviceId)

    suspend fun downloadTrack(item: MusicItem) = withContext(Dispatchers.IO) {
        val url = if (item.audioUrl.isNotEmpty()) item.audioUrl else getStreamUrl(item.videoUrl)
        if (url.isNotEmpty()) {
            val downloadId = downloadManager.downloadTrack(item, url)
            if (downloadId != null) {
                val path = downloadManager.getDownloadPath(item)
                val existing = db.musicDao().getTrack(item.id)
                val isFav = existing?.isFavorite ?: false

                val entity = item.copy(localPath = path, audioUrl = url, isFavorite = isFav)
                    .toEntity(favoriteSyncState = existing?.favoriteSyncState ?: TrackEntity.FAVORITE_SYNCED)
                db.musicDao().insertTrack(entity)
            }
        }
    }

    suspend fun getTrending(): SearchResult {
        try {
            val charts = chartsExtractor.getCharts()
            val topSongs = charts.entries.find { it.key.contains("Top songs", ignoreCase = true) }?.value
            if (!topSongs.isNullOrEmpty()) {
                return SearchResult("Top Songs Global", topSongs)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return extractor.getTopSongs()
    }

    suspend fun getTopSongs(): SearchResult = getTrending()

    suspend fun getTrendingVideos(): SearchResult {
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

    suspend fun getHomeFeed(): List<SearchResult> = extractor.getHomeSections()

    suspend fun getAllCharts(): Map<String, List<MusicItem>> {
        return try {
            chartsExtractor.getCharts()
        } catch (_: Exception) {
            emptyMap()
        }
    }

    suspend fun getMoodPlaylists(mood: String): List<Playlist> = extractor.getMoodPlaylists(mood)

    suspend fun getGenrePlaylists(genre: String): List<Playlist> = extractor.getGenrePlaylists(genre)

    suspend fun getNewReleaseAlbums(): List<Playlist> = extractor.getNewReleaseAlbums()

    suspend fun getRecommendations(videoId: String): List<MusicItem> = extractor.getUpNext(videoId)

    suspend fun getStreamUrl(videoUrl: String): String = extractor.getAudioStreamUrl(videoUrl)

    suspend fun getPlaylist(playlistId: String): Playlist = withContext(Dispatchers.IO) {
        val localPlaylist = db.musicDao().getPlaylistWithTracks(playlistId)

        if (localPlaylist != null && !localPlaylist.playlist.isYouTubeSyncedPlaylist()) {
            val orderedIds = db.musicDao().getPlaylistTrackIds(playlistId)
            val byId = localPlaylist.tracks.associateBy { it.id }
            val orderedTracks = orderedIds.mapNotNull { byId[it] } + localPlaylist.tracks.filter { it.id !in orderedIds }
            val ordered = localPlaylist.copy(tracks = orderedTracks)
            return@withContext mapPlaylistWithTracksToDomain(ordered)
        }

        try {
            extractor.getPlaylist(playlistId)
        } catch (error: Exception) {
            if (localPlaylist != null) {
                mapPlaylistWithTracksToDomain(localPlaylist)
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

    val favoriteTracks: Flow<List<MusicItem>> = db.musicDao().getAllTracks().map { entities ->
        entities.map { mapTrackToDomain(it) }
    }

    val downloadedTracks: Flow<List<MusicItem>> = db.musicDao().getDownloadedTracks().map { entities ->
        entities.map { mapTrackToDomain(it) }
    }

    val savedCollections: Flow<List<SavedCollectionItem>> = db.musicDao().getSavedCollections().map { entities ->
        entities.map { mapSavedCollectionToDomain(it) }
    }

    suspend fun addToFavorites(item: MusicItem) = withContext(Dispatchers.IO) {
        val existing = db.musicDao().getTrack(item.id)
        val localPath = existing?.localPath
        val videoId = item.youtubeVideoIdOrNull()
        val syncState = if (videoId != null) TrackEntity.FAVORITE_PENDING_LIKE else TrackEntity.FAVORITE_SYNCED

        val entity = item.copy(
            isFavorite = true,
            localPath = localPath ?: item.localPath
        ).toEntity(favoriteSyncState = syncState)

        db.musicDao().insertTrack(entity)

        if (videoId != null && isYouTubeLoggedIn()) {
            if (pushFavoriteToYouTube(videoId, like = true)) {
                db.musicDao().updateFavoriteSyncState(item.id, TrackEntity.FAVORITE_SYNCED)
            }
        }
    }

    suspend fun removeFromFavorites(item: MusicItem) = withContext(Dispatchers.IO) {
        val existing = db.musicDao().getTrack(item.id)
        if (existing != null) {
            val videoId = item.youtubeVideoIdOrNull() ?: mapTrackToDomain(existing).youtubeVideoIdOrNull()
            val syncState = if (videoId != null) TrackEntity.FAVORITE_PENDING_UNLIKE else TrackEntity.FAVORITE_SYNCED
            val updated = existing.copy(
                isFavorite = false,
                favoriteSyncState = syncState
            )
            db.musicDao().insertTrack(updated)

            if (videoId != null && isYouTubeLoggedIn()) {
                if (pushFavoriteToYouTube(videoId, like = false)) {
                    db.musicDao().updateFavoriteSyncState(item.id, TrackEntity.FAVORITE_SYNCED)
                }
            }
        }
    }

    val userPlaylists: Flow<List<PlaylistEntity>> = db.musicDao().getAllPlaylists()
    val userPlaylistsWithTracks: Flow<List<com.israrxy.raazi.data.db.PlaylistWithTracks>> = db.musicDao().getAllPlaylistsWithTracks()

    suspend fun toggleSavedCollection(item: MusicItem) {
        val savedCollection = item.toSavedCollectionItemOrNull() ?: return
        toggleSavedCollectionImpl(savedCollection)
    }

    suspend fun toggleSavedCollection(playlist: Playlist) {
        toggleSavedCollectionImpl(playlist.toSavedCollectionItem())
    }

    suspend fun toggleSavedArtist(
        artistId: String,
        artistName: String,
        thumbnailUrl: String = ""
    ) {
        val normalizedId = artistId.ifBlank { artistName.trim() }
        if (normalizedId.isBlank()) return
        toggleSavedCollectionImpl(
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

    suspend fun syncLocalPlaylistToYouTube(playlistId: String): String = withContext(Dispatchers.IO) {
        syncLocalPlaylistToYouTubeInternal(db, playlistId, isYouTubeLoggedIn())
    }

    suspend fun syncYouTubeLibrary(): AccountSyncResult = withContext(Dispatchers.IO) {
        if (!isYouTubeLoggedIn()) {
            return@withContext AccountSyncResult(likedSongs = 0, playlists = 0)
        }

        val favoritePushResult = runCatching { syncLocalFavoritesToYouTube(db) }
            .getOrDefault(FavoritePushResult(synced = 0, pending = 0))
        val likedCount = runCatching { syncLikedSongsFromYouTube(db) }
            .onFailure { android.util.Log.e("MusicRepository", "Failed to sync liked songs", it) }
            .getOrDefault(0)
        val playlistsCount = runCatching { syncPlaylistsFromYouTube(db) }
            .onFailure { android.util.Log.e("MusicRepository", "Failed to sync playlists", it) }
            .getOrDefault(0)

        AccountSyncResult(
            likedSongs = likedCount,
            playlists = playlistsCount,
            accountLikeUpdates = favoritePushResult.synced,
            pendingLikeUpdates = favoritePushResult.pending
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

    suspend fun addToPlaylist(track: MusicItem, playlist: PlaylistEntity) = withContext(Dispatchers.IO) {
        insertTracksIntoPlaylist(db, listOf(track), playlist)
    }

    suspend fun addToPlaylist(tracks: List<MusicItem>, playlist: PlaylistEntity) = withContext(Dispatchers.IO) {
        insertTracksIntoPlaylist(db, tracks, playlist)
    }

    suspend fun renamePlaylist(playlistId: String, newName: String) = withContext(Dispatchers.IO) {
        val playlist = db.musicDao().getPlaylistById(playlistId)
            ?: throw IllegalArgumentException("Playlist not found")

        val validName = newName.trim().ifBlank { throw IllegalArgumentException("Name cannot be empty") }

        if (playlist.isYouTubeSyncedPlaylist()) {
            if (!playlist.isYouTubeEditablePlaylist()) {
                throw IllegalStateException("This synced playlist can't be renamed from Raazi.")
            }
            try {
                YouTube.renamePlaylist(playlist.id, validName)
            } catch (e: Exception) {
                android.util.Log.w("MusicRepo", "YouTube rename failed, updating locally only", e)
            }
        }

        db.musicDao().renamePlaylist(playlistId, validName)
    }

    suspend fun deletePlaylist(playlistId: String) = withContext(Dispatchers.IO) {
        val playlist = db.musicDao().getPlaylistById(playlistId)
            ?: throw IllegalArgumentException("Playlist not found")

        if (playlist.isYouTubeSyncedPlaylist() && playlist.isYouTubeEditablePlaylist()) {
            try {
                YouTube.deletePlaylist(playlist.id)
            } catch (e: Exception) {
                android.util.Log.w("MusicRepo", "YouTube playlist delete failed, removing locally only", e)
            }
        }

        db.musicDao().deletePlaylistTracksByPlaylistId(playlistId)
        db.musicDao().deletePlaylistById(playlistId)
    }

    suspend fun removeTrackFromPlaylist(playlistId: String, trackId: String, setVideoId: String? = null) = withContext(Dispatchers.IO) {
        val playlist = db.musicDao().getPlaylistById(playlistId)
            ?: throw IllegalArgumentException("Playlist not found")

        if (playlist.isYouTubeSyncedPlaylist()) {
            if (!playlist.isYouTubeEditablePlaylist()) {
                throw IllegalStateException("This synced playlist can't be edited from Raazi.")
            }
            if (setVideoId != null) {
                YouTube.removeFromPlaylist(playlistId, trackId, setVideoId).getOrThrow()
            }
            return@withContext
        }

        db.musicDao().removeTrackFromPlaylist(playlistId, trackId)
        val remainingTrackIds = db.musicDao().getPlaylistTrackIds(playlistId)
        remainingTrackIds.forEachIndexed { index, tid ->
            db.musicDao().updatePlaylistTrackPosition(playlistId, tid, index)
        }
    }

    suspend fun removeTracksFromPlaylist(playlistId: String, tracks: List<MusicItem>) = withContext(Dispatchers.IO) {
        val playlist = db.musicDao().getPlaylistById(playlistId)
            ?: throw IllegalArgumentException("Playlist not found")

        if (playlist.isYouTubeSyncedPlaylist()) {
            if (!playlist.isYouTubeEditablePlaylist()) {
                throw IllegalStateException("This synced playlist can't be edited from Raazi.")
            }
            for (track in tracks) {
                val setVideoId = track.setVideoId ?: continue
                YouTube.removeFromPlaylist(playlistId, track.id, setVideoId).getOrThrow()
            }
            return@withContext
        }

        for (track in tracks) {
            db.musicDao().removeTrackFromPlaylist(playlistId, track.id)
        }
        val remainingTrackIds = db.musicDao().getPlaylistTrackIds(playlistId)
        remainingTrackIds.forEachIndexed { index, tid ->
            db.musicDao().updatePlaylistTrackPosition(playlistId, tid, index)
        }
    }

    suspend fun reorderPlaylistTracks(playlistId: String, trackIds: List<String>) = withContext(Dispatchers.IO) {
        trackIds.forEachIndexed { index, trackId ->
            db.musicDao().updatePlaylistTrackPosition(playlistId, trackId, index)
        }
    }

    suspend fun addToHistory(item: MusicItem) = withContext(Dispatchers.IO) {
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

    suspend fun fetchAndSaveRelated(item: MusicItem) = withContext(Dispatchers.IO) {
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

    val playbackHistory: Flow<List<MusicItem>> = db.musicDao().getPlaybackHistory().map { entities ->
        entities.map { mapHistoryToDomain(it) }
    }

    val quickPicks: Flow<List<MusicItem>> = db.musicDao().getSmartRecommendations().map { entities ->
        entities.map { mapTrackToDomain(it) }
    }

    fun getForgottenFavorites(): Flow<List<MusicItem>> {
        val cutoff = System.currentTimeMillis() - 86400000L * 14
        return db.musicDao().getForgottenFavorites(cutoff).map { entities ->
            entities.map { mapHistoryToDomain(it) }
        }
    }

    fun getKeepListening(): Flow<List<MusicItem>> {
        val cutoff = System.currentTimeMillis() - 86400000L * 14
        return db.musicDao().getMostPlayed(cutoff).map { entities ->
            entities.map { mapHistoryToDomain(it) }
        }
    }

    fun getRelatedTracks(trackId: String): Flow<List<MusicItem>> {
        return db.musicDao().getRelatedTracks(trackId).map { entities ->
            entities.map { mapTrackToDomain(it) }
        }
    }

    suspend fun recordHomeInteraction(itemId: String, sectionId: String, action: String, sourceType: String) =
        recordHomeInteractionImpl(itemId, sectionId, action, sourceType)

    suspend fun getDismissedHomeItemIds(): Set<String> = getDismissedHomeItemIdsImpl()

    suspend fun getEngagedHomeItemIds(): List<String> = getEngagedHomeItemIdsImpl()
}
