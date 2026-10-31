package com.israrxy.raazi.data.lyrics

import com.israrxy.raazi.data.remote.Lyrics

enum class LyricsScriptFilter(val label: String) {
    ALL("All"),
    ENGLISH("English"),
    HINDI("Hindi"),
    MIXED("Mixed"),
    OTHER("Other");

    companion object {
        val visibleFilters = entries.toList()
    }
}

object LyricsScriptDetector {
    private val latinRegex = Regex("""[A-Za-z]""")
    private val devanagariRegex = Regex("""\p{InDevanagari}""")
    private val letterRegex = Regex("""\p{L}""")

    fun detect(lyrics: Lyrics): LyricsScriptFilter {
        val text = buildString {
            append(lyrics.syncedLyrics.orEmpty())
            append('\n')
            append(lyrics.plainLyrics.orEmpty())
        }

        val latinCount = latinRegex.findAll(text).count()
        val devanagariCount = devanagariRegex.findAll(text).count()
        val totalLetters = letterRegex.findAll(text).count()
        val otherCount = (totalLetters - latinCount - devanagariCount).coerceAtLeast(0)

        return when {
            totalLetters == 0 -> LyricsScriptFilter.OTHER
            latinCount > 0 && devanagariCount == 0 && otherCount == 0 -> LyricsScriptFilter.ENGLISH
            devanagariCount > 0 && latinCount == 0 && otherCount == 0 -> LyricsScriptFilter.HINDI
            (latinCount > 0 && devanagariCount > 0) || otherCount > 0 -> LyricsScriptFilter.MIXED
            else -> LyricsScriptFilter.OTHER
        }
    }

    fun matches(filter: LyricsScriptFilter, lyrics: Lyrics): Boolean {
        return filter == LyricsScriptFilter.ALL || detect(lyrics) == filter
    }
}
