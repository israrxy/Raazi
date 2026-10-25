package com.zionhuang.innertube.models

import kotlinx.serialization.Serializable

@Serializable
data class Runs(
    val runs: List<Run>?,
)

@Serializable
data class Run(
    val text: String,
    val navigationEndpoint: NavigationEndpoint?,
)

fun List<Run>.splitBySeparator(): List<List<Run>> {
    val res = mutableListOf<List<Run>>()
    var tmp = mutableListOf<Run>()
    forEach { run ->
        if (run.text == " • ") {
            res.add(tmp)
            tmp = mutableListOf()
        } else {
            tmp.add(run)
        }
    }
    res.add(tmp)
    return res
}

private val KNOWN_TYPE_LABELS = setOf(
    "song", "video", "album", "artist", "playlist", "single", "ep",
    "episode", "podcast", "profile", "station", "audiobook"
)

fun List<List<Run>>.clean(): List<List<Run>> {
    val firstRun = getOrNull(0)?.getOrNull(0) ?: return this
    return if (firstRun.navigationEndpoint == null && firstRun.text.trim().lowercase() in KNOWN_TYPE_LABELS) {
        this.drop(1)
    } else {
        this
    }
}

fun List<Run>.oddElements() = filterIndexed { index, _ ->
    index % 2 == 0
}
