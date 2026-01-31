package com.israrxy.raazi.model

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
    val isPlaylist: Boolean = false,
    val artistId: String? = null
)

data class Playlist(
    val id: String,
    val title: String,
    val description: String,
    val thumbnailUrl: String,
    val items: List<MusicItem>
)

data class SearchResult(
    val query: String,
    val items: List<MusicItem>
)

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