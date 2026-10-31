package com.israrxy.raazi.data.remote

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs

internal object MusixmatchTtmlParser {
    private val LANG_REGEX = Regex("""xml:lang="([^"]+)"""")
    private val BODY_DUR_REGEX = Regex("""<body[^>]*\bdur="([^"]+)"""")
    private val P_TAG_REGEX = Regex("""<p\b([^>]*)>(.*?)</p>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
    private val BEGIN_ATTR_REGEX = Regex("""\bbegin="([^"]+)"""")
    private val SPAN_TOKEN_REGEX = Regex(
        """<span\b([^>]*)>(.*?)</span>(\s*)""",
        setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
    )
    private val ANY_XML_TAG_REGEX = Regex("""<[^>]+>""")
    private val MULTI_SPACE_REGEX = Regex("""\s+""")
    private val LRC_TAG_STRIP_REGEX = Regex("""\[\d+:\d+(?:\.\d+)?\]|<\d+:\d+(?:\.\d+)?>""")

    fun parseTtmlResponseToLyrics(
        rawJson: String,
        trackName: String,
        artistName: String,
        fallbackDurationSeconds: Double
    ): Lyrics? {
        return runCatching {
            val element = Json.parseToJsonElement(rawJson).jsonObject
            val ttml = element["ttml"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
                ?: JSONObject(rawJson).optString("ttml").takeIf { it.isNotBlank() }
                ?: return null
            parseTtmlXml(ttml, trackName, artistName, fallbackDurationSeconds)
        }.getOrNull()
    }

    fun parseTtmlXml(
        ttml: String,
        trackName: String,
        artistName: String,
        fallbackDurationSeconds: Double
    ): Lyrics? {
        val lang = LANG_REGEX.find(ttml)?.groupValues?.getOrNull(1)?.takeIf { it.isNotBlank() }
        val bodyDurSec = BODY_DUR_REGEX.find(ttml)?.groupValues?.getOrNull(1)?.let(::parseTimeSeconds)
        val resolvedDuration = bodyDurSec?.takeIf { it > 0.0 } ?: fallbackDurationSeconds

        // Guard against wildly mismatched durations when both durations are known
        if (fallbackDurationSeconds > 20.0 && bodyDurSec != null && bodyDurSec > 20.0) {
            if (abs(bodyDurSec - fallbackDurationSeconds) > 45.0) {
                return null
            }
        }

        val syncedLines = mutableListOf<String>()
        val plainLines = mutableListOf<String>()

        for (pMatch in P_TAG_REGEX.findAll(ttml)) {
            val pAttrs = pMatch.groupValues[1]
            val pInner = pMatch.groupValues[2]
            val lineStartSec = BEGIN_ATTR_REGEX.find(pAttrs)?.groupValues?.getOrNull(1)?.let(::parseTimeSeconds)
                ?: continue

            val lineTag = formatLrcTimestamp(lineStartSec)
            val spanMatches = SPAN_TOKEN_REGEX.findAll(pInner).toList()

            if (spanMatches.isNotEmpty()) {
                val wordSyncedBuilder = StringBuilder()
                val plainLineBuilder = StringBuilder()
                var startNewWord = true

                for (spanMatch in spanMatches) {
                    val spanAttrs = spanMatch.groupValues[1]
                    if (spanAttrs.contains("ttm:role=\"x-translation\"") ||
                        spanAttrs.contains("ttm:role=\"x-roman\"")
                    ) {
                        continue
                    }
                    val rawToken = decodeXmlEntities(spanMatch.groupValues[2].replace(ANY_XML_TAG_REGEX, ""))
                    val trailingSpace = spanMatch.groupValues[3]
                    if (rawToken.isBlank()) {
                        if (trailingSpace.isNotEmpty() && !startNewWord) {
                            wordSyncedBuilder.append(" ")
                            plainLineBuilder.append(" ")
                            startNewWord = true
                        }
                        continue
                    }

                    val spanStartSec = BEGIN_ATTR_REGEX.find(spanAttrs)?.groupValues?.getOrNull(1)
                        ?.let(::parseTimeSeconds)
                        ?: lineStartSec

                    val hasLeadingSpace = rawToken.first().isWhitespace()
                    val hasInternalTrailingSpace = rawToken.last().isWhitespace()
                    val cleanSyllable = rawToken.trim()

                    if (cleanSyllable.isEmpty()) continue

                    if (startNewWord || hasLeadingSpace) {
                        if (wordSyncedBuilder.isNotEmpty() && !wordSyncedBuilder.endsWith(" ")) {
                            wordSyncedBuilder.append(" ")
                            plainLineBuilder.append(" ")
                        }
                        wordSyncedBuilder.append(formatWordTimestamp(spanStartSec)).append(cleanSyllable)
                        plainLineBuilder.append(cleanSyllable)
                    } else {
                        wordSyncedBuilder.append(cleanSyllable)
                        plainLineBuilder.append(cleanSyllable)
                    }

                    if (trailingSpace.isNotEmpty() || hasInternalTrailingSpace) {
                        wordSyncedBuilder.append(" ")
                        plainLineBuilder.append(" ")
                        startNewWord = true
                    } else {
                        startNewWord = false
                    }
                }

                val builtSynced = wordSyncedBuilder.toString().trim()
                val builtPlain = plainLineBuilder.toString().replace(MULTI_SPACE_REGEX, " ").trim()
                if (builtSynced.isNotBlank()) {
                    syncedLines.add("$lineTag$builtSynced")
                }
                if (builtPlain.isNotBlank()) {
                    plainLines.add(builtPlain)
                }
            } else {
                val cleanLine = decodeXmlEntities(pInner.replace(ANY_XML_TAG_REGEX, ""))
                    .replace(MULTI_SPACE_REGEX, " ")
                    .trim()
                if (cleanLine.isNotBlank()) {
                    syncedLines.add("$lineTag$cleanLine")
                    plainLines.add(cleanLine)
                }
            }
        }

        if (syncedLines.isEmpty() && plainLines.isEmpty()) return null

        return Lyrics(
            id = ("mxm_ttml_${trackName.lowercase()}_${artistName.lowercase()}").hashCode(),
            trackName = trackName,
            artistName = artistName,
            plainLyrics = plainLines.joinToString("\n").takeIf { it.isNotBlank() },
            syncedLyrics = syncedLines.joinToString("\n").takeIf { it.isNotBlank() },
            duration = resolvedDuration,
            language = lang,
            source = "Musixmatch"
        )
    }

    internal fun convertRichSyncToEnhancedLrc(richSyncJson: String): String? {
        return runCatching {
            val array = JSONArray(richSyncJson)
            if (array.length() == 0) return@runCatching null
            val lines = mutableListOf<String>()
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val lineStartSec = item.optDouble("ts", -1.0)
                if (lineStartSec < 0) continue
                val lineText = item.optString("x").trim()
                val wordsArray = item.optJSONArray("l")
                val lineTag = formatLrcTimestamp(lineStartSec)

                if (wordsArray != null && wordsArray.length() > 0) {
                    val wordBuilder = StringBuilder()
                    for (w in 0 until wordsArray.length()) {
                        val wordObj = wordsArray.optJSONObject(w) ?: continue
                        val token = wordObj.optString("c")
                        if (token.isEmpty()) continue
                        val offsetSec = wordObj.optDouble("o", 0.0)
                        val wordTimeSec = (lineStartSec + offsetSec).coerceAtLeast(0.0)
                        if (token.isBlank()) {
                            wordBuilder.append(token)
                        } else {
                            wordBuilder.append(formatWordTimestamp(wordTimeSec)).append(token)
                        }
                    }
                    val built = wordBuilder.toString().trim()
                    if (built.isNotBlank()) {
                        lines.add("$lineTag$built")
                    } else if (lineText.isNotBlank()) {
                        lines.add("$lineTag$lineText")
                    }
                } else if (lineText.isNotBlank()) {
                    lines.add("$lineTag$lineText")
                }
            }
            lines.joinToString("\n").takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    internal fun cleanMusixmatchLyricsBody(raw: String): String {
        return raw.lineSequence()
            .filterNot { line ->
                val trimmed = line.trim()
                trimmed.startsWith("*******") ||
                    trimmed.contains("This Lyrics is NOT for Commercial use", ignoreCase = true) ||
                    trimmed.matches(Regex("^\\(\\d+\\)$"))
            }
            .joinToString("\n")
            .trim()
    }

    internal fun stripLrcTimestamps(lrc: String): String {
        return lrc.lineSequence()
            .map { it.replace(LRC_TAG_STRIP_REGEX, "").trim() }
            .filter { it.isNotBlank() }
            .joinToString("\n")
    }

    internal fun parseTimeSeconds(raw: String): Double? {
        val text = raw.trim().removeSuffix("s")
        if (text.isBlank()) return null
        val parts = text.split(":")
        return runCatching {
            when (parts.size) {
                1 -> parts[0].toDouble()
                2 -> parts[0].toLong() * 60.0 + parts[1].toDouble()
                3 -> parts[0].toLong() * 3600.0 + parts[1].toLong() * 60.0 + parts[2].toDouble()
                else -> null
            }
        }.getOrNull()
    }

    internal fun formatLrcTimestamp(seconds: Double): String {
        val totalMs = (seconds * 1000.0).toLong().coerceAtLeast(0L)
        val min = totalMs / 60000
        val sec = (totalMs / 1000) % 60
        val cs = (totalMs % 1000) / 10
        return "[%02d:%02d.%02d]".format(min, sec, cs)
    }

    internal fun formatWordTimestamp(seconds: Double): String {
        val totalMs = (seconds * 1000.0).toLong().coerceAtLeast(0L)
        val min = totalMs / 60000
        val sec = (totalMs / 1000) % 60
        val cs = (totalMs % 1000) / 10
        return "<%02d:%02d.%02d>".format(min, sec, cs)
    }

    private fun decodeXmlEntities(input: String): String {
        return input
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&#x27;", "'")
            .replace("&#39;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
    }
}
