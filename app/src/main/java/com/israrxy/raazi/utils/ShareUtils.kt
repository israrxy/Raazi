package com.israrxy.raazi.utils

import android.content.Context
import android.content.Intent
import com.israrxy.raazi.model.MusicItem

/**
 * Helpers for sharing tracks via the Android Sharesheet.
 */
object ShareUtils {

    /** Build a shareable YouTube link for a track id, when the id looks like a video id. */
    private fun buildLink(item: MusicItem): String? {
        val id = item.id
        if (id.isBlank()) return null
        // YouTube video ids are 11 chars; treat those as watchable links.
        return if (id.length == 11) "https://music.youtube.com/watch?v=$id" else null
    }

    fun shareTrack(context: Context, item: MusicItem) {
        val link = buildLink(item)
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
