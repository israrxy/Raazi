package com.israrxy.raazi.model

enum class MusicContentType {
    SONG,
    VIDEO,
    ARTIST,
    ALBUM,
    PLAYLIST,
    UNKNOWN
}

data class MusicItem(
    val id: String,
    val title: String,
    val artist: String,
    val duration: Long, // in milliseconds
    val thumbnailUrl: String,
    val audioUrl: String,
    val videoUrl: String,
    val isLive: Boolean = false,
    val localPath: String? = null,
    val isFavorite: Boolean = false,
    val isPlaylist: Boolean = false,
    val artistId: String? = null,
    val contentType: MusicContentType = MusicContentType.SONG
)

data class Playlist(
    val id: String,
    val title: String,
    val description: String,
    val thumbnailUrl: String,
    val items: List<MusicItem>
)

data class SavedCollectionItem(
    val id: String,
    val sourceId: String,
    val title: String,
    val subtitle: String,
    val thumbnailUrl: String,
    val contentType: MusicContentType
)

data class SearchResult(
    val query: String,
    val items: List<MusicItem>
)

fun savedCollectionId(type: MusicContentType, sourceId: String): String = "${type.name}:$sourceId"

fun MusicItem.toSavedCollectionItemOrNull(): SavedCollectionItem? {
    val savedType = when {
        contentType == MusicContentType.ARTIST -> MusicContentType.ARTIST
        contentType == MusicContentType.ALBUM -> MusicContentType.ALBUM
        contentType == MusicContentType.PLAYLIST || isPlaylist -> MusicContentType.PLAYLIST
        else -> null
    } ?: return null

    val sourceId = when (savedType) {
        MusicContentType.ARTIST -> artistId ?: id
        else -> id
    }.ifBlank { return null }

    val subtitle = when (savedType) {
        MusicContentType.ARTIST -> "Artist"
        MusicContentType.ALBUM -> artist.ifBlank { "Album" }
        MusicContentType.PLAYLIST -> artist.ifBlank { "Playlist" }
        else -> ""
    }

    return SavedCollectionItem(
        id = savedCollectionId(savedType, sourceId),
        sourceId = sourceId,
        title = if (savedType == MusicContentType.ARTIST) title.ifBlank { artist } else title,
        subtitle = subtitle,
        thumbnailUrl = thumbnailUrl,
        contentType = savedType
    )
}

fun Playlist.toSavedCollectionItem(): SavedCollectionItem {
    val savedType = if (id.startsWith("MPREb")) MusicContentType.ALBUM else MusicContentType.PLAYLIST
    return SavedCollectionItem(
        id = savedCollectionId(savedType, id),
        sourceId = id,
        title = title,
        subtitle = description.ifBlank {
            if (savedType == MusicContentType.ALBUM) "Album" else "Playlist"
        },
        thumbnailUrl = thumbnailUrl,
        contentType = savedType
    )
}

enum class RepeatMode {
    OFF,      // No repeat
    ONE,      // Repeat current track
    ALL       // Repeat entire playlist
}

data class PlaybackState(
    val isPlaying: Boolean = false,
    val currentPosition: Long = 0,
    val duration: Long = 0,
    val currentTrack: MusicItem? = null,
    val playlist: List<MusicItem> = emptyList(),
    val currentIndex: Int = -1,
    val isShuffleEnabled: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val isLoading: Boolean = false,
    val isBuffering: Boolean = false
)
