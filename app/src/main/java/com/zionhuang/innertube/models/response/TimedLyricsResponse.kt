package com.zionhuang.innertube.models.response

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

@Serializable
data class TimedLyricsResponse(
    val contents: Contents? = null,
) {
    @Serializable
    data class Contents(
        val elementRenderer: ElementRenderer? = null,
    )

    @Serializable
    data class ElementRenderer(
        val newElement: NewElement? = null,
    )

    @Serializable
    data class NewElement(
        val type: Type? = null,
    )

    @Serializable
    data class Type(
        val componentType: ComponentType? = null,
    )

    @Serializable
    data class ComponentType(
        val model: Model? = null,
    )

    @Serializable
    data class Model(
        val timedLyricsModel: TimedLyricsModel? = null,
    )

    @Serializable
    data class TimedLyricsModel(
        val lyricsData: LyricsData? = null,
    )

    @Serializable
    data class LyricsData(
        val timedLyricsData: List<TimedLyricLine>? = null,
    )

    @Serializable
    data class TimedLyricLine(
        val lyricLine: String? = null,
        val cueRange: CueRange? = null,
    )

    @Serializable
    data class CueRange(
        val startTimeMilliseconds: JsonElement? = null,
        val endTimeMilliseconds: JsonElement? = null,
    )
}

internal fun TimedLyricsResponse.toLrc(): String? {
    val cues = contents?.elementRenderer?.newElement?.type?.componentType?.model
        ?.timedLyricsModel?.lyricsData?.timedLyricsData
        ?.mapNotNull { line ->
            val text = line.lyricLine?.trim().orEmpty()
            val startMs = line.cueRange?.startTimeMilliseconds?.toMillisOrNull()
                ?: return@mapNotNull null
            if (text.isBlank() || startMs < 0L) null else startMs to text
        }
        .orEmpty()
    if (cues.size < 4) return null

    return cues.joinToString("\n") { (startMs, text) ->
        "[%02d:%02d.%03d]%s".format(
            startMs / 60_000,
            (startMs / 1_000) % 60,
            startMs % 1_000,
            text
        )
    }
}

private fun JsonElement.toMillisOrNull(): Long? =
    (this as? JsonPrimitive)?.content?.toLongOrNull()
