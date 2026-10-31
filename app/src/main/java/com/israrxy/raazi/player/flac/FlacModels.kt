package com.israrxy.raazi.player.flac

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class HifiSearchResult(
    val items: List<HifiTrack>? = null,
)

@Serializable
data class HifiTrack(
    val id: Long,
    val title: String? = null,
    val duration: Long? = null,
    @SerialName("artistName")
    val artistName: String? = null,
    @SerialName("artists")
    val artists: List<HifiArtist>? = null,
    @SerialName("albumTitle")
    val albumTitle: String? = null,
    @SerialName("albumId")
    val albumId: Long? = null,
    @SerialName("cover")
    val cover: String? = null,
    @SerialName("popularity")
    val popularity: Int? = null,
    @SerialName("isrc")
    val isrc: String? = null,
) {
    val displayArtist: String
        get() = artistName ?: artists?.firstOrNull()?.name ?: "Unknown"
}

@Serializable
data class HifiArtist(
    val id: Long? = null,
    val name: String? = null,
)

@Serializable
data class HifiStreamResponse(
    @SerialName("mimeType")
    val mimeType: String? = null,
    @SerialName("codecs")
    val codecs: String? = null,
    @SerialName("encryptionType")
    val encryptionType: String? = null,
    @SerialName("urls")
    val urls: List<String>? = null,
    @SerialName("manifestMimeType")
    val manifestMimeType: String? = null,
    @SerialName("manifest")
    val manifest: String? = null,
)

@Serializable
data class HifiTrackInfo(
    val id: Long,
    val title: String? = null,
    val duration: Long? = null,
    @SerialName("artistName")
    val artistName: String? = null,
    @SerialName("albumTitle")
    val albumTitle: String? = null,
    @SerialName("cover")
    val cover: String? = null,
    @SerialName("isrc")
    val isrc: String? = null,
    @SerialName("audioQuality")
    val audioQuality: String? = null,
)

data class FlacResult(
    val localPath: String,
    val qualityLabel: String,
    val title: String,
    val artist: String,
    val album: String? = null,
    val coverUrl: String? = null,
    val durationMs: Long? = null,
    val tidalTrackId: Long? = null,
)
