package com.israrxy.raazi.data.lyrics

import java.util.Locale
import kotlin.math.max

data class LyricsLookupCandidate(
    val trackName: String,
    val artistName: String
) {
    val queryText: String
        get() = listOf(artistName, trackName)
            .filter { it.isNotBlank() }
            .joinToString(" ")
}

data class PreparedLyricsRequest(
    val cacheKey: String,
    val titleComparisons: List<String>,
    val artistComparisons: List<String>,
    val candidates: List<LyricsLookupCandidate>,
    val searchQueries: List<String>
)

object LyricsMetadataSanitizer {
    private const val EM_DASH = '\u2014'
    private const val EN_DASH = '\u2013'
    private const val FULLWIDTH_VERTICAL_LINE = '\uFF5C'
    private const val BULLET = '\u2022'

    private val bracketNoiseRegex = Regex(
        """[\(\[\{][^)\]}]*(?:official|video|audio|lyrics|lyric|visualizer|visualiser|music\s+video|mv|hd|hq|4k|8k|1080p|720p|full\s+audio|remaster|remastered|anniversary|live|\b(?:19|20)\d{2}\b)[^)\]}]*[\)\]\}]""",
        RegexOption.IGNORE_CASE
    )
    private val genericBracketRegex = Regex("""[\(\[\{][^)\]}]*[\)\]\}]""")
    private val trailingNoiseRegex = Regex(
        """(?:\s*[-|:]\s*|\s+)(?:official|official video|official audio|official music video|music video|video|audio|lyrics|lyric video|lyrics video|visualizer|visualiser|mv|hd|hq|4k|8k|1080p|720p|full audio|remaster(?:ed)?|live(?: at [^-]+)?|(?:19|20)\d{2}|(?:\d{1,2}(?:st|nd|rd|th)\s+)?anniversary(?: edition)?)\s*$""",
        RegexOption.IGNORE_CASE
    )
    private val separatorRegex = Regex("""\s*(?:[|:]+|[\u2014\u2013]+| - | \u2022 )\s*""", RegexOption.IGNORE_CASE)
    private val repeatedWhitespaceRegex = Regex("""\s+""")
    private val leadingTrailingPunctuationRegex = Regex("""^[\s\-|:;,./\\]+|[\s\-|:;,./\\]+$""")
    private val yearRegex = Regex("""\b(?:19|20)\d{2}\b""")
    private val artistNoiseRegex = Regex("""\b(?:vevo|official|topic)\b""", RegexOption.IGNORE_CASE)
    private val genericArtistRegex = Regex("""^(?:unknown(?: artist)?|various artists?)$""", RegexOption.IGNORE_CASE)
    private val specialCharacterRegex = Regex("""[^\p{L}\p{N}\s]""")
    private val featuringRegex = Regex(
        """(?:\s*[\(\[]?\s*(?:feat\.?|ft\.?|featuring|with)\s+[^)\]]+[\)\]]?)$""",
        RegexOption.IGNORE_CASE
    )
    private val collaborationSplitRegex = Regex("""\s*(?:,|&| and | x | feat\.?|ft\.?|featuring|with)\s*""", RegexOption.IGNORE_CASE)

    fun prepare(trackName: String, artistName: String): PreparedLyricsRequest {
        val cleanedArtist = sanitizeArtist(artistName)
        val cleanedTitle = sanitizeTrackTitle(trackName)
        val parsedCandidate = splitArtistAndTrack(trackName, cleanedArtist)
        val titleVariants = buildTitleVariants(cleanedTitle)
        val artistVariants = buildArtistVariants(cleanedArtist)

        val candidates = linkedSetOf<LyricsLookupCandidate>()
        parsedCandidate?.let(candidates::add)
        candidates += LyricsLookupCandidate(
            trackName = stripArtistPrefix(cleanedTitle, cleanedArtist),
            artistName = cleanedArtist
        )
        titleVariants.take(2).forEach { titleVariant ->
            artistVariants.take(2).forEach { artistVariant ->
                candidates += LyricsLookupCandidate(
                    trackName = titleVariant,
                    artistName = artistVariant
                )
            }
        }
        titleVariants.take(2).forEach { titleVariant ->
            candidates += LyricsLookupCandidate(
                trackName = titleVariant,
                artistName = ""
            )
        }

        val distinctCandidates = candidates
            .mapNotNull { candidate ->
                val title = sanitizeTrackTitle(candidate.trackName)
                val artist = sanitizeArtist(candidate.artistName)
                if (title.isBlank()) {
                    null
                } else {
                    LyricsLookupCandidate(
                        trackName = title,
                        artistName = artist
                    )
                }
            }
            .distinctBy { "${it.artistName.lowercase(Locale.ROOT)}|${it.trackName.lowercase(Locale.ROOT)}" }
            .ifEmpty {
                listOf(
                    LyricsLookupCandidate(
                        trackName = fallbackIfEmpty(cleanedTitle, trackName),
                        artistName = fallbackIfEmpty(cleanedArtist, artistName)
                    )
                )
            }

        val titleComparisons = distinctCandidates
            .map { normalizeForComparison(it.trackName) }
            .filter { it.isNotBlank() }
            .distinct()
        val artistComparisons = buildList {
            addAll(distinctCandidates.map { normalizeForComparison(it.artistName) })
            add(normalizeForComparison(cleanedArtist))
        }
            .filter { it.isNotBlank() }
            .distinct()
        val searchQueries = buildList {
            addAll(distinctCandidates.map { it.queryText })
            addAll(titleVariants)
            titleVariants.take(2).forEach { titleVariant ->
                artistVariants.take(2).forEach { artistVariant ->
                    add(
                        listOf(artistVariant, titleVariant)
                            .filter { it.isNotBlank() }
                            .joinToString(" ")
                    )
                }
            }
        }
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()

        val primary = distinctCandidates.first()
        return PreparedLyricsRequest(
            cacheKey = buildCacheKey(primary.trackName, primary.artistName),
            titleComparisons = titleComparisons,
            artistComparisons = artistComparisons,
            candidates = distinctCandidates,
            searchQueries = searchQueries
        )
    }

    fun sanitizeTrackTitle(value: String): String {
        return sanitizePreservingOriginal(
            value = value,
            artistMode = false
        )
    }

    fun sanitizeArtist(value: String): String {
        val sanitized = sanitizePreservingOriginal(
            value = value,
            artistMode = true
        )
            .replace(featuringRegex, " ")
            .replace(artistNoiseRegex, " ")
            .replace(repeatedWhitespaceRegex, " ")
            .trim()

        if (genericArtistRegex.matches(sanitized)) {
            return ""
        }

        return sanitized
    }

    fun normalizeForComparison(value: String): String {
        val sanitized = sanitizePreservingOriginal(value, artistMode = false)
            .replace(yearRegex, " ")
            .replace(specialCharacterRegex, " ")
            .replace(repeatedWhitespaceRegex, " ")
            .trim()
            .lowercase(Locale.ROOT)

        return fallbackIfEmpty(sanitized, value.trim().lowercase(Locale.ROOT))
    }

    fun similarityScore(left: String, right: String): Int {
        val normalizedLeft = normalizeForComparison(left)
        val normalizedRight = normalizeForComparison(right)
        if (normalizedLeft.isBlank() || normalizedRight.isBlank()) {
            return 0
        }

        val distance = damerauLevenshtein(normalizedLeft, normalizedRight)
        val maxLength = max(normalizedLeft.length, normalizedRight.length)
        if (maxLength == 0) {
            return 100
        }

        val similarity = (1.0 - distance.toDouble() / maxLength.toDouble()) * 100.0
        return similarity.coerceIn(0.0, 100.0).toInt()
    }

    fun buildCacheKey(trackName: String, artistName: String): String {
        return "${normalizeForComparison(artistName)}|${normalizeForComparison(trackName)}"
    }

    private fun buildTitleVariants(cleanedTitle: String): List<String> {
        val strippedFeaturing = cleanedTitle.replace(featuringRegex, " ").replace(repeatedWhitespaceRegex, " ").trim()
        return listOf(cleanedTitle, strippedFeaturing)
            .map { fallbackIfEmpty(it, cleanedTitle) }
            .distinct()
    }

    private fun buildArtistVariants(cleanedArtist: String): List<String> {
        if (cleanedArtist.isBlank()) {
            return listOf("")
        }

        val primaryArtist = collaborationSplitRegex.split(cleanedArtist)
            .map { it.trim() }
            .firstOrNull { it.isNotBlank() }
            .orEmpty()
        val strippedFeaturing = cleanedArtist.replace(featuringRegex, " ").replace(repeatedWhitespaceRegex, " ").trim()

        return listOf(cleanedArtist, strippedFeaturing, primaryArtist)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
    }

    private fun stripArtistPrefix(trackName: String, artistName: String): String {
        if (artistName.isBlank()) {
            return trackName
        }

        val candidate = trackName.trim()
        val artistPrefix = Regex(
            "^${Regex.escape(artistName)}\\s*(?:-|\\u2014|\\u2013|:|\\|)\\s*",
            RegexOption.IGNORE_CASE
        )
        val stripped = candidate.replace(artistPrefix, "").trim()
        return fallbackIfEmpty(stripped, candidate)
    }

    private fun splitArtistAndTrack(rawTitle: String, cleanedArtist: String): LyricsLookupCandidate? {
        val normalizedTitle = rawTitle
            .replace(EM_DASH, '-')
            .replace(EN_DASH, '-')
            .replace(FULLWIDTH_VERTICAL_LINE, '|')
            .trim()

        val separators = listOf(" - ", " | ", " : ", " by ")
        val separator = separators.firstOrNull { normalizedTitle.contains(it, ignoreCase = true) } ?: return null

        val left = sanitizeArtist(normalizedTitle.substringBefore(separator))
        val right = sanitizeTrackTitle(normalizedTitle.substringAfter(separator))
        if (left.isBlank() || right.isBlank()) {
            return null
        }

        val leftArtistScore = if (cleanedArtist.isBlank()) 0 else similarityScore(left, cleanedArtist)
        val rightArtistScore = if (cleanedArtist.isBlank()) 0 else similarityScore(right, cleanedArtist)

        return if (leftArtistScore >= rightArtistScore) {
            LyricsLookupCandidate(trackName = right, artistName = left)
        } else {
            LyricsLookupCandidate(trackName = left, artistName = right)
        }
    }

    private fun sanitizePreservingOriginal(value: String, artistMode: Boolean): String {
        val original = value.trim()
        if (original.isBlank()) {
            return ""
        }

        var current = original
            .replace(EM_DASH, '-')
            .replace(EN_DASH, '-')
            .replace(FULLWIDTH_VERTICAL_LINE, '|')
            .replace(BULLET, '-')
            .replace(separatorRegex, " - ")

        repeat(4) {
            val stripped = current.replace(bracketNoiseRegex, " ").trim()
            if (stripped != current) {
                current = stripped
            }
        }

        if (!artistMode) {
            repeat(4) {
                val stripped = current.replace(genericBracketRegex, " ").trim()
                if (stripped != current) {
                    current = stripped
                }
            }
        }

        repeat(4) {
            val stripped = current.replace(trailingNoiseRegex, "").trim()
            if (stripped != current) {
                current = stripped
            }
        }

        current = current
            .replace(repeatedWhitespaceRegex, " ")
            .replace(leadingTrailingPunctuationRegex, "")
            .trim()

        if (artistMode) {
            current = current
                .replace(Regex("""\s*-\s*$"""), "")
                .trim()
        }

        return fallbackIfEmpty(current, original)
    }

    private fun fallbackIfEmpty(value: String, fallback: String): String {
        return value.takeIf { it.isNotBlank() } ?: fallback.trim()
    }

    private fun damerauLevenshtein(left: String, right: String): Int {
        val leftLength = left.length
        val rightLength = right.length
        val matrix = Array(leftLength + 1) { IntArray(rightLength + 1) }

        for (i in 0..leftLength) {
            matrix[i][0] = i
        }
        for (j in 0..rightLength) {
            matrix[0][j] = j
        }

        for (i in 1..leftLength) {
            for (j in 1..rightLength) {
                val cost = if (left[i - 1] == right[j - 1]) 0 else 1
                matrix[i][j] = minOf(
                    matrix[i - 1][j] + 1,
                    matrix[i][j - 1] + 1,
                    matrix[i - 1][j - 1] + cost
                )

                if (i > 1 && j > 1 &&
                    left[i - 1] == right[j - 2] &&
                    left[i - 2] == right[j - 1]
                ) {
                    matrix[i][j] = minOf(matrix[i][j], matrix[i - 2][j - 2] + cost)
                }
            }
        }

        return matrix[leftLength][rightLength]
    }
}
