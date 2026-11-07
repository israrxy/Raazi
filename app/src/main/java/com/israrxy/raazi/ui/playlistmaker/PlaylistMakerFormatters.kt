package com.israrxy.raazi.ui.playlistmaker

internal fun formatTrackDuration(millis: Long): String {
    if (millis <= 0L) return ""
    val totalSeconds = millis / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}

internal fun formatTotalDuration(millis: Long): String {
    if (millis <= 0L) return "Unknown length"
    val totalMinutes = millis / 60_000
    return if (totalMinutes >= 60) {
        "${totalMinutes / 60} hr ${totalMinutes % 60} min"
    } else {
        "$totalMinutes min"
    }
}
