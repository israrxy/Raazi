package com.israrxy.raazi.data.repository

import android.util.Log
import com.israrxy.raazi.data.db.AppDatabase
import com.israrxy.raazi.data.db.PlaylistEntity
import com.israrxy.raazi.data.db.PlaylistTrackCrossRef
import com.israrxy.raazi.data.db.TrackEntity
import com.israrxy.raazi.data.playlist.buildYouTubePlaylistDescription
import com.israrxy.raazi.data.playlist.isYouTubeEditablePlaylist
import com.israrxy.raazi.data.playlist.isYouTubeSyncedPlaylist
import com.israrxy.raazi.model.MusicContentType
import com.israrxy.raazi.model.MusicItem
import com.zionhuang.innertube.YouTube
import kotlinx.coroutines.flow.first

internal suspend fun syncLocalPlaylistToYouTubeInternal(
    db: AppDatabase,
    playlistId: String,
    isLoggedIn: Boolean
): String {
    val localPlaylist = db.musicDao().getPlaylistWithTracks(playlistId)
        ?: throw IllegalArgumentException("Playlist not found")

    if (localPlaylist.playlist.isYouTubeSyncedPlaylist()) {
        return playlistId
    }

    if (!isLoggedIn) {
        throw IllegalStateException("Sign in to YouTube Music before syncing playlists.")
    }

    val title = localPlaylist.playlist.displayTitle
    val remotePlaylistId = YouTube.createPlaylist(title)

    val tracks = localPlaylist.tracks.map { mapTrackToDomain(it) }
    for (track in tracks) {
        val videoId = track.youtubeVideoIdOrNull() ?: continue
        try {
            YouTube.addToPlaylist(remotePlaylistId, videoId).getOrThrow()
        } catch (e: Exception) {
            Log.e("MusicRepository", "Failed to add track ${track.id} to remote playlist $remotePlaylistId", e)
        }
    }

    db.musicDao().deletePlaylistTracksByPlaylistId(playlistId)
    db.musicDao().deletePlaylistById(playlistId)

    val syncedPlaylist = PlaylistEntity(
        id = remotePlaylistId,
        title = title,
        description = buildYouTubePlaylistDescription(isEditable = true),
        thumbnailUrl = localPlaylist.playlist.thumbnailUrl,
        customTitle = localPlaylist.playlist.customTitle
    )
    db.musicDao().insertPlaylist(syncedPlaylist)

    localPlaylist.tracks.forEachIndexed { index, trackEntity ->
        db.musicDao().insertPlaylistTrackCrossRef(
            PlaylistTrackCrossRef(
                playlistId = remotePlaylistId,
                trackId = trackEntity.id,
                position = index
            )
        )
    }

    val oldSavedId = com.israrxy.raazi.model.savedCollectionId(MusicContentType.PLAYLIST, playlistId)
    val savedCollection = db.musicDao().getSavedCollectionById(oldSavedId)
    if (savedCollection != null) {
        db.musicDao().deleteSavedCollectionById(oldSavedId)
        db.musicDao().insertSavedCollection(
            savedCollection.copy(
                id = com.israrxy.raazi.model.savedCollectionId(MusicContentType.PLAYLIST, remotePlaylistId),
                sourceId = remotePlaylistId
            )
        )
    }

    return remotePlaylistId
}

internal suspend fun syncLocalFavoritesToYouTube(db: AppDatabase): FavoritePushResult {
    var syncedCount = 0

    db.musicDao().getPendingFavoriteSyncTracks().forEach { track ->
        val videoId = mapTrackToDomain(track).youtubeVideoIdOrNull()
        if (videoId == null) {
            db.musicDao().updateFavoriteSyncState(track.id, TrackEntity.FAVORITE_SYNCED)
            return@forEach
        }

        val shouldLike = track.favoriteSyncState == TrackEntity.FAVORITE_PENDING_LIKE
        if (pushFavoriteToYouTube(videoId, like = shouldLike)) {
            db.musicDao().updateFavoriteSyncState(track.id, TrackEntity.FAVORITE_SYNCED)
            syncedCount++
        }
    }

    val pendingCount = db.musicDao().getPendingFavoriteSyncTracks().size
    return FavoritePushResult(synced = syncedCount, pending = pendingCount)
}

internal suspend fun syncLikedSongsFromYouTube(db: AppDatabase): Int {
    val remotePlaylist = YouTube.playlist("LM").getOrThrow()
    val remoteItems = remotePlaylist.songs.mapNotNull { it.toMusicItem() }.asReversed()
    val remoteIds = remoteItems.map { it.id }.toSet()
    val pendingUnlikes = db.musicDao().getPendingFavoriteSyncTracks()
        .filter { it.favoriteSyncState == TrackEntity.FAVORITE_PENDING_UNLIKE }
        .map { it.id }
        .toSet()

    db.musicDao().getAllTracks().first()
        .filter { it.favoriteSyncState == TrackEntity.FAVORITE_SYNCED }
        .filter { it.id !in remoteIds }
        .filter { mapTrackToDomain(it).youtubeVideoIdOrNull() != null }
        .forEach { track ->
            db.musicDao().insertTrack(
                track.copy(
                    isFavorite = false,
                    favoriteSyncState = TrackEntity.FAVORITE_SYNCED
                )
            )
        }

    remoteItems.forEach { item ->
        if (item.id in pendingUnlikes) {
            return@forEach
        }

        val existing = db.musicDao().getTrack(item.id)
        val entity = item.copy(
            isFavorite = true,
            localPath = existing?.localPath ?: item.localPath
        ).toEntity(favoriteSyncState = TrackEntity.FAVORITE_SYNCED)
        db.musicDao().insertTrack(entity)
    }

    return remoteItems.size
}

internal suspend fun syncPlaylistsFromYouTube(db: AppDatabase): Int {
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

internal suspend fun pushFavoriteToYouTube(videoId: String, like: Boolean): Boolean {
    return try {
        YouTube.likeVideo(videoId, like = like).getOrThrow()
        true
    } catch (error: Exception) {
        Log.e("MusicRepository", "Failed to sync favorite state for $videoId", error)
        false
    }
}

internal suspend fun insertTracksIntoPlaylist(
    db: AppDatabase,
    tracks: List<MusicItem>,
    playlist: PlaylistEntity
) {
    if (playlist.isYouTubeSyncedPlaylist()) {
        if (!playlist.isYouTubeEditablePlaylist()) {
            throw IllegalStateException("This synced playlist can't be edited from Raazi.")
        }
        for (track in tracks) {
            val videoId = track.youtubeVideoIdOrNull()
                ?: if (tracks.size == 1) {
                    throw IllegalArgumentException("Only YouTube Music tracks can be added to synced playlists.")
                } else {
                    continue
                }
            YouTube.addToPlaylist(playlist.id, videoId).getOrThrow()
        }
    }

    var nextPosition = (db.musicDao().getMaxPlaylistPosition(playlist.id) ?: -1) + 1
    for (track in tracks) {
        val existingTrack = db.musicDao().getTrack(track.id)
        val trackEntity = track.copy(
            isFavorite = existingTrack?.isFavorite ?: track.isFavorite,
            localPath = existingTrack?.localPath ?: track.localPath
        ).toEntity(favoriteSyncState = existingTrack?.favoriteSyncState ?: TrackEntity.FAVORITE_SYNCED)
        db.musicDao().insertTrack(trackEntity)

        db.musicDao().insertPlaylistTrackCrossRef(
            PlaylistTrackCrossRef(
                playlistId = playlist.id,
                trackId = track.id,
                position = nextPosition++
            )
        )
    }
}
