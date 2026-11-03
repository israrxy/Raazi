package com.israrxy.raazi.data.remote

import com.google.gson.JsonParser
import java.util.Locale

internal object CaptionTranscriptParser {

    private val NOISE_CUES = setOf(
        "[music]", "(music)", "music",
        "[applause]", "(applause)",
        "[laughter]", "(laughter)",
        "[cheering]", "(cheering)",
        "[♪♪♪]", "♪♪♪", "[♪]", "♪"
    )

    fun parseCaptionsToLrc(raw: String): String? {
        val trimmed = raw.trim()
        return when {
            trimmed.startsWith("WEBVTT") || trimmed.contains("-->") -> parseVttToLrc(trimmed)
            trimmed.contains("<timedtext") || trimmed.contains("<transcript") ||
                    trimmed.contains("<p ") || trimmed.contains("<text ") -> parseXmlToLrc(trimmed)
            trimmed.startsWith("{") && trimmed.contains("\"events\"") -> parseJson3ToLrc(trimmed)
            else -> parseVttToLrc(trimmed)
        }.takeIf { it.isNotBlank() }
    }

    fun isValidLrcTranscript(lrc: String): Boolean {
        val lines = lrc.lines().filter { it.isNotBlank() }
        if (lines.size < 3) return false
        val timestampedCount = lines.count { it.startsWith("[") && it.contains("]") }
        return timestampedCount >= 3
    }

    fun stripLrcTags(lrc: String): String {
        val regex = Regex("""\[\d+:\d+(?:\.\d+)?\]|<\d+:\d+(?:\.\d+)?>""")
        return lrc.lineSequence()
            .map { it.replace(regex, "").trim() }
            .filter { it.isNotBlank() }
            .joinToString("\n")
    }

    private fun parseVttToLrc(vttText: String): String {
        val lines = vttText.lines()
        val result = StringBuilder()
        val timeRegex = Regex("""(?:(\d{2}):)?(\d{2}):(\d{2})[.,](\d{2,3})\s*-->""")
        var prevText = ""
        var i = 0

        while (i < lines.size) {
            val line = lines[i].trim()
            val match = timeRegex.find(line)
            if (match != null) {
                val hours = match.groupValues[1].toIntOrNull() ?: 0
                val minutes = match.groupValues[2].toIntOrNull() ?: 0
                val seconds = match.groupValues[3].toIntOrNull() ?: 0
                val msStr = match.groupValues[4]
                val ms = msStr.padEnd(3, '0').take(3).toIntOrNull() ?: 0

                val totalSec = hours * 3600 + minutes * 60 + seconds
                val totalMin = totalSec / 60
                val remSec = totalSec % 60
                val centis = ms / 10

                i++
                val textBuilder = StringBuilder()
                while (i < lines.size && lines[i].isNotBlank() && !timeRegex.containsMatchIn(lines[i])) {
                    val clean = cleanCaptionText(lines[i])
                    if (clean.isNotBlank()) {
                        if (textBuilder.isNotEmpty()) textBuilder.append(" ")
                        textBuilder.append(clean)
                    }
                    i++
                }

                val text = textBuilder.toString().trim()
                if (text.isNotBlank() && text != prevText && !isNoiseCue(text)) {
                    result.append(String.format(Locale.US, "[%02d:%02d.%02d]%s\n", totalMin, remSec, centis, text))
                    prevText = text
                }
                continue
            }
            i++
        }
        return result.toString().trim()
    }

    private fun parseXmlToLrc(xmlText: String): String {
        // Format 3: <p t="18640" d="3240">text</p>
        val pRegex = Regex("""<p\s+t="(\d+)"(?:\s+d="\d+")?[^>]*>(.*?)</p>""", RegexOption.DOT_MATCHES_ALL)
        val pMatches = pRegex.findAll(xmlText).toList()
        if (pMatches.isNotEmpty()) {
            val result = StringBuilder()
            var prevText = ""
            for (match in pMatches) {
                val startMs = match.groupValues[1].toLongOrNull() ?: continue
                val rawText = match.groupValues[2]
                val clean = cleanCaptionText(rawText)
                if (clean.isNotBlank() && clean != prevText && !isNoiseCue(clean)) {
                    val minutes = startMs / 60000
                    val seconds = (startMs / 1000) % 60
                    val centis = (startMs % 1000) / 10
                    result.append(String.format(Locale.US, "[%02d:%02d.%02d]%s\n", minutes, seconds, centis, clean))
                    prevText = clean
                }
            }
            val formatted = result.toString().trim()
            if (formatted.isNotBlank()) return formatted
        }

        // Format 1: <text start="18.64" dur="3.24">text</text>
        val textRegex = Regex("""<text\s+start="([\d.]+)"(?:\s+dur="[\d.]+")?[^>]*>(.*?)</text>""", RegexOption.DOT_MATCHES_ALL)
        val textMatches = textRegex.findAll(xmlText).toList()
        if (textMatches.isNotEmpty()) {
            val result = StringBuilder()
            var prevText = ""
            for (match in textMatches) {
                val startSec = match.groupValues[1].toDoubleOrNull() ?: continue
                val rawText = match.groupValues[2]
                val clean = cleanCaptionText(rawText)
                if (clean.isNotBlank() && clean != prevText && !isNoiseCue(clean)) {
                    val timeMs = Math.round(startSec * 1000.0)
                    val minutes = timeMs / 60000
                    val seconds = (timeMs / 1000) % 60
                    val centis = (timeMs % 1000) / 10
                    result.append(String.format(Locale.US, "[%02d:%02d.%02d]%s\n", minutes, seconds, centis, clean))
                    prevText = clean
                }
            }
            return result.toString().trim()
        }

        return ""
    }

    private fun parseJson3ToLrc(jsonText: String): String {
        val result = StringBuilder()
        var prevText = ""
        runCatching {
            val root = JsonParser.parseString(jsonText).asJsonObject
            val events = root.getAsJsonArray("events") ?: return@runCatching
            for (eventElement in events) {
                val event = eventElement.asJsonObject
                val startMs = if (event.has("tStartMs")) event.get("tStartMs").asLong else -1L
                if (startMs < 0) continue
                val segs = if (event.has("segs")) event.getAsJsonArray("segs") else continue
                val segText = StringBuilder()
                for (segElement in segs) {
                    val seg = segElement.asJsonObject
                    if (seg.has("utf8")) {
                        segText.append(seg.get("utf8").asString)
                    }
                }
                val clean = cleanCaptionText(segText.toString())
                if (clean.isNotBlank() && clean != prevText && !isNoiseCue(clean)) {
                    val minutes = startMs / 60000
                    val seconds = (startMs / 1000) % 60
                    val centis = (startMs % 1000) / 10
                    result.append(String.format(Locale.US, "[%02d:%02d.%02d]%s\n", minutes, seconds, centis, clean))
                    prevText = clean
                }
            }
        }
        return result.toString().trim()
    }

    private fun cleanCaptionText(raw: String): String {
        return raw
            .replace(Regex("""<[^>]+>"""), "") // Strip HTML/XML/VTT tags like <s>, <b>, <c>
            .replace("&amp;", "&")
            .replace("&#39;", "'")
            .replace("&quot;", "\"")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&nbsp;", " ")
            .replace("\n", " ")
            .trim('♪', ' ', '\t', '\r', '\n')
            .trim()
    }

    private fun isNoiseCue(text: String): Boolean {
        val lower = text.lowercase().trim()
        return lower in NOISE_CUES
    }
}
