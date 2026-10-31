package com.israrxy.raazi.data.playlist

import com.israrxy.raazi.data.db.PlaylistEntity

private const val REMOTE_PLAYLIST_PREFIX = "__raazi_youtube__:"

data class YouTubePlaylistMetadata(
    val isEditable: Boolean
)

fun buildYouTubePlaylistDescription(isEditable: Boolean): String {
    return REMOTE_PLAYLIST_PREFIX + if (isEditable) "editable" else "readonly"
}

fun parseYouTubePlaylistMetadata(description: String): YouTubePlaylistMetadata? {
    if (!description.startsWith(REMOTE_PLAYLIST_PREFIX)) {
        return null
    }
    return YouTubePlaylistMetadata(
        isEditable = description.removePrefix(REMOTE_PLAYLIST_PREFIX) == "editable"
    )
}

fun PlaylistEntity.youtubePlaylistMetadata(): YouTubePlaylistMetadata? {
    return parseYouTubePlaylistMetadata(description)
}

fun PlaylistEntity.isYouTubeSyncedPlaylist(): Boolean {
    return youtubePlaylistMetadata() != null
}

fun PlaylistEntity.isYouTubeEditablePlaylist(): Boolean {
    return youtubePlaylistMetadata()?.isEditable == true
}
