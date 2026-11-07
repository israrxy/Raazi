package com.israrxy.raazi.data.ai

/**
 * Prompt constants used by [GeminiClient] for song identification.
 * Extracted to keep source files under 500 lines.
 */

internal val PHOTO_IDENTIFY_PROMPT = """
    You identify music from an image. The image may be an album cover, a
    single cover, a screenshot of a song, or a photo of printed lyrics.

    Look carefully at all visible text, artwork, and layout.
    - If it is an album cover, identify the album and its most popular track.
    - If it is a single or song screenshot, identify that song.
    - If it is lyrics, identify the song those lyrics belong to.

    Return ONLY a JSON object with exactly this shape:
    {
      "identified": true or false,
      "title": "song title or null",
      "artist": "artist name or null",
      "album": "album name or null",
      "year": "release year or null",
      "confidence": 0.0 to 1.0,
      "search_query": "best YouTube search query or null",
      "detected_text": "visible lyrics or text excerpt, max 300 chars, or null",
      "alternatives": [{"title": "other match", "artist": "artist"}],
      "message": "short explanation when not identified, else null"
    }

    Rules:
    - Never invent a song. If you cannot identify it, set identified to false.
    - If the image contains lyrics, identify the song from those lyrics and set identified to true whenever you can name it.
    - search_query must be tuned for YouTube (for example "Ed Sheeran Perfect").
    - confidence must reflect your real certainty.
    - detected_text should contain lyrics only when they are visible.
    - Keep alternatives to at most 3 likely matches.
""".trimIndent()

internal val LYRICS_IDENTIFY_PROMPT = """
    You identify a song from lyrics that were detected in a photo or a
    short audio clip. The lyrics may be partial, misheard, or slightly
    wrong.

    Use the words, phrasing, and any distinctive lines to identify the
    most likely song. Prefer the original studio recording by the
    credited artist. If you are not reasonably sure, set identified to
    false instead of inventing a song.

    Return ONLY a JSON object with exactly this shape:
    {
      "identified": true or false,
      "title": "song title or null",
      "artist": "artist name or null",
      "album": "album name or null",
      "year": "release year or null",
      "confidence": 0.0 to 1.0,
      "search_query": "best YouTube search query or null",
      "detected_text": "the lyrics you were given, max 300 chars, or null",
      "alternatives": [{"title": "other match", "artist": "artist"}],
      "message": "short explanation when not identified, else null"
    }

    Rules:
    - If the lyrics clearly match a song, set identified to true and fill title and artist.
    - Never invent lyrics or a song that does not match.
    - confidence must reflect your real certainty.
    - Keep alternatives to at most 3 likely matches.
""".trimIndent()

internal val AUDIO_IDENTIFY_PROMPT = """
    You identify music from a short audio clip. The clip may contain a
    song playing in the background, someone humming or singing, or an
    instrumental passage.

    Listen for lyrics, vocal words, melody, harmony, instruments, and
    production style.
    - If vocals or lyrics are audible, use them to identify the song.
    - If it is hummed or instrumental, match only a melody you recognise
      with real confidence.
    - Prefer the original artist and studio track. Mention a cover, live
      version, or remix only when it is clearly audible.

    Return ONLY a JSON object with exactly this shape:
    {
      "identified": true or false,
      "title": "song title or null",
      "artist": "artist name or null",
      "album": "album name or null",
      "year": "release year or null",
      "confidence": 0.0 to 1.0,
      "search_query": "best YouTube search query or null",
      "detected_text": "lyrics or vocal words you can hear, max 300 chars, or null",
      "alternatives": [{"title": "other match", "artist": "artist"}],
      "message": "short explanation when not identified, else null"
    }

    Rules:
    - Never invent a song. If you cannot identify it, set identified to false.
    - Audio matching is much harder than reading text. Keep confidence honest.
    - search_query must be tuned for YouTube.
    - detected_text should contain lyrics only when they are audible.
    - Keep alternatives to at most 3 likely matches.
""".trimIndent()
