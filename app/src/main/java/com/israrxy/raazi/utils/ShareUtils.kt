package com.israrxy.raazi.utils

import android.content.Context
import android.content.Intent
import com.israrxy.raazi.model.MusicItem

/**
 * Helpers for sharing tracks via the Android Sharesheet.
 */
object ShareUtils {
    const val BASE_DOMAIN = "https://raazi.pages.dev"

    /** Build a shareable Raazi deep link for a track id. */
    fun buildTrackLink(trackId: String): String? {
        val cleanId = trackId.trim()
        if (cleanId.isBlank()) return null
        return "$BASE_DOMAIN/?video=${android.net.Uri.encode(cleanId)}"
    }

    fun buildPlaylistLink(playlistId: String): String? {
        val cleanId = playlistId.trim().removePrefix("VL")
        if (cleanId.isBlank()) return null
        return "$BASE_DOMAIN/playlist?list=${android.net.Uri.encode(cleanId)}"
    }

    fun buildArtistLink(artistId: String, artistName: String? = null): String? {
        val cleanId = artistId.trim()
        if (cleanId.isBlank()) return null
        val nameParam = if (!artistName.isNullOrBlank()) "&name=${android.net.Uri.encode(artistName)}" else ""
        return "$BASE_DOMAIN/artist?id=${android.net.Uri.encode(cleanId)}$nameParam"
    }

    fun shareTrack(context: Context, item: MusicItem) {
        val link = buildTrackLink(item.id)
        val title = item.title.ifBlank { "this song" }
        val artist = item.artist.trim()
        val text = buildString {
            append(title)
            if (artist.isNotBlank()) append(" — ").append(artist)
            if (link != null) {
                append("\n")
                append(link)
            }
            append("\n\nShared from Raazi")
        }

        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, text)
        }
        val chooser = Intent.createChooser(sendIntent, "Share song").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }
}
