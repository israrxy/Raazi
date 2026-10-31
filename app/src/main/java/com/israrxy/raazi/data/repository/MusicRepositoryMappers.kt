package com.israrxy.raazi.data.repository

import com.israrxy.raazi.data.db.HomeInteractionEntity
import com.israrxy.raazi.data.db.PlaybackHistoryEntity
import com.israrxy.raazi.data.db.PlaylistWithTracks
import com.israrxy.raazi.data.db.SavedCollectionEntity
import com.israrxy.raazi.data.db.TrackEntity
import com.israrxy.raazi.model.MusicContentType
import com.israrxy.raazi.model.MusicItem
import com.israrxy.raazi.model.Playlist
import com.israrxy.raazi.model.SavedCollectionItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

data class AccountSyncResult(
    val likedSongs: Int,
    val playlists: Int,
    val accountLikeUpdates: Int = 0,
    val pendingLikeUpdates: Int = 0
)

internal data class FavoritePushResult(
    val synced: Int,
    val pending: Int
)

internal fun MusicItem.toEntity(favoriteSyncState: Int = TrackEntity.FAVORITE_SYNCED): TrackEntity {
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
        isFavorite = isFavorite,
        favoriteSyncState = favoriteSyncState
    )
}

internal fun mapTrackToDomain(entity: TrackEntity): MusicItem {
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

internal fun mapHistoryToDomain(entity: PlaybackHistoryEntity): MusicItem {
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

internal fun mapPlaylistWithTracksToDomain(playlist: PlaylistWithTracks): Playlist {
    return Playlist(
        id = playlist.playlist.id,
        title = playlist.playlist.displayTitle,
        description = playlist.playlist.description,
        thumbnailUrl = playlist.playlist.thumbnailUrl,
        items = playlist.tracks.map { mapTrackToDomain(it) }
    )
}

internal fun SavedCollectionItem.toEntity(): SavedCollectionEntity {
    return SavedCollectionEntity(
        id = id,
        sourceId = sourceId,
        title = title,
        subtitle = subtitle,
        thumbnailUrl = thumbnailUrl,
        contentType = contentType.name
    )
}

internal fun mapSavedCollectionToDomain(entity: SavedCollectionEntity): SavedCollectionItem {
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

internal fun MusicItem.youtubeVideoIdOrNull(): String? {
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

internal fun com.zionhuang.innertube.models.SongItem.toMusicItem(): MusicItem? {
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
        isFavorite = true,
        setVideoId = setVideoId
    )
}

internal suspend fun MusicRepository.recordHomeInteractionImpl(
    itemId: String,
    sectionId: String,
    action: String,
    sourceType: String
) = withContext(Dispatchers.IO) {
    if (itemId.isBlank() || sectionId.isBlank()) return@withContext

    val timestamp = System.currentTimeMillis()
    db.musicDao().insertHomeInteraction(
        HomeInteractionEntity(
            id = "$timestamp-${itemId.hashCode()}-${sectionId.hashCode()}-${action.hashCode()}",
            itemId = itemId,
            sectionId = sectionId,
            action = action,
            sourceType = sourceType,
            timestamp = timestamp
        )
    )

    val retentionCutoff = timestamp - 86400000L * 60
    db.musicDao().deleteOldHomeInteractions(retentionCutoff)
}

internal suspend fun MusicRepository.getDismissedHomeItemIdsImpl(): Set<String> = withContext(Dispatchers.IO) {
    val since = System.currentTimeMillis() - 86400000L * 30
    db.musicDao()
        .getHomeInteractionItemIds(HomeInteractionEntity.ACTION_DISMISS, since)
        .toSet()
}

internal suspend fun MusicRepository.getEngagedHomeItemIdsImpl(): List<String> = withContext(Dispatchers.IO) {
    val since = System.currentTimeMillis() - 86400000L * 30
    db.musicDao()
        .getHomeInteractionItemIds(HomeInteractionEntity.ACTION_PLAY, since) +
        db.musicDao().getHomeInteractionItemIds(HomeInteractionEntity.ACTION_OPEN, since)
}

internal suspend fun MusicRepository.toggleSavedCollectionImpl(savedCollection: SavedCollectionItem) {
    withContext(Dispatchers.IO) {
        if (db.musicDao().getSavedCollectionById(savedCollection.id) != null) {
            db.musicDao().deleteSavedCollectionById(savedCollection.id)
        } else {
            db.musicDao().insertSavedCollection(savedCollection.toEntity())
        }
    }
}

internal suspend fun MusicRepository.isYouTubeLoggedIn(): Boolean {
    return settingsDataStore.innerTubeCookie.first()?.contains("SAPISID") == true
}
