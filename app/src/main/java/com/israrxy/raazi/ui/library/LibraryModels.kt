package com.israrxy.raazi.ui.library

import com.israrxy.raazi.data.db.PlaylistWithTracks
import com.israrxy.raazi.model.MusicContentType
import com.israrxy.raazi.model.MusicItem
import com.israrxy.raazi.model.SavedCollectionItem

/**
 * Which sections the user asked to see. The filter is applied while the screen state is
 * built, so the UI never has to re-implement section visibility rules.
 */
enum class LibraryFilter(val label: String) {
    ALL("All"),
    SONGS("Songs"),
    PLAYLISTS("Playlists"),
    ALBUMS("Albums"),
    ARTISTS("Artists");

    val showsTracks: Boolean get() = this == ALL || this == SONGS
    val showsUserPlaylists: Boolean get() = this == ALL || this == PLAYLISTS

    fun showsSaved(type: MusicContentType): Boolean = when (this) {
        ALL -> true
        SONGS -> false
        PLAYLISTS -> type == MusicContentType.PLAYLIST
        ALBUMS -> type == MusicContentType.ALBUM
        ARTISTS -> type == MusicContentType.ARTIST
    }
}

/**
 * One ordering rule shared by every section, so sorting the screen never feels random.
 * [RECENT] keeps the order the database already returns (newest first for liked songs,
 * history and saved collections, insertion order for local playlists).
 */
enum class LibrarySort(val label: String) {
    RECENT("Recently added"),
    TITLE_ASC("Name A-Z"),
    TITLE_DESC("Name Z-A");
}

enum class LibraryLayout(val label: String) {
    GRID("Grid"),
    LIST("List");
}

/** A single row of the overflow (three-dot) menu. */
data class LibraryMenuItem(
    val label: String,
    val onClick: () -> Unit,
    val destructive: Boolean = false
)

/** Everything the sync / account card needs to render itself. */
data class LibrarySyncState(
    val loggedIn: Boolean,
    val accountName: String?,
    val syncing: Boolean
)

/** Callbacks the grid emits. Kept in one place so screens and grids stay in sync. */
data class LibraryActions(
    val search: (String) -> Unit,
    val selectFilter: (LibraryFilter) -> Unit,
    val selectSort: (LibrarySort) -> Unit,
    val selectLayout: (LibraryLayout) -> Unit,
    val openPlaylist: (String) -> Unit,
    val openArtist: (String, String) -> Unit,
    val openDownloads: () -> Unit,
    val openSettings: () -> Unit,
    val openSpotifyImport: () -> Unit,
    val syncLibrary: () -> Unit,
    val createPlaylist: () -> Unit,
    val renamePlaylist: (playlistId: String) -> Unit,
    val deletePlaylist: (playlistId: String) -> Unit,
    val removeSavedCollection: (SavedCollectionItem) -> Unit,
    val removeSavedArtist: (SavedCollectionItem) -> Unit,
    val playTracks: (tracks: List<MusicItem>, startIndex: Int) -> Unit
)

/**
 * Immutable snapshot of everything the library grid draws.
 *
 * All filtering and sorting happens once, here, when any input changes - the composition
 * only ever reads finished lists. That keeps recomposition cheap and makes the rules
 * testable without a UI.
 */
internal data class LibraryUiState(
    val query: String,
    val filter: LibraryFilter,
    val sort: LibrarySort,
    val layout: LibraryLayout,
    val likedSongs: List<MusicItem>,
    val history: List<MusicItem>,
    val userPlaylists: List<PlaylistWithTracks>,
    val savedPlaylists: List<SavedCollectionItem>,
    val albums: List<SavedCollectionItem>,
    val artists: List<SavedCollectionItem>,
    val downloadCount: Int
) {
    val visibleUserPlaylists: List<PlaylistWithTracks> =
        if (filter.showsUserPlaylists) userPlaylists.applyLibraryPlaylists(query, sort) else emptyList()

    val visibleSavedPlaylists: List<SavedCollectionItem> =
        savedPlaylists.applyLibrarySaved(query, filter, sort)

    val visibleAlbums: List<SavedCollectionItem> =
        albums.applyLibrarySaved(query, filter, sort)

    val visibleArtists: List<SavedCollectionItem> =
        artists.applyLibrarySaved(query, filter, sort)

    val visibleLikedSongs: List<MusicItem> =
        if (filter.showsTracks) likedSongs.applyLibraryTracks(query, sort) else emptyList()

    val visibleHistory: List<MusicItem> =
        if (filter.showsTracks) history.distinctBy { it.id }.applyLibraryTracks(query, sort) else emptyList()

    /** While browsing (no query, no filter) the song lists stay short and offer "See all". */
    private val isBrowsing: Boolean = query.isBlank() && filter == LibraryFilter.ALL

    val likedPreview: List<MusicItem> =
        if (isBrowsing) visibleLikedSongs.take(LIKED_PREVIEW_LIMIT) else visibleLikedSongs

    val historyPreview: List<MusicItem> =
        if (isBrowsing) visibleHistory.take(HISTORY_PREVIEW_LIMIT) else visibleHistory

    val likedPreviewCapped: Boolean = likedPreview.size < visibleLikedSongs.size
    val historyPreviewCapped: Boolean = historyPreview.size < visibleHistory.size

    /** The user has something stored, ignoring the current query and filter. */
    val hasLibraryContent: Boolean =
        likedSongs.isNotEmpty() || history.isNotEmpty() || userPlaylists.isNotEmpty() ||
            savedPlaylists.isNotEmpty() || albums.isNotEmpty() || artists.isNotEmpty()

    /** The current query + filter matched at least one section. */
    val hasVisibleContent: Boolean =
        visibleUserPlaylists.isNotEmpty() || visibleSavedPlaylists.isNotEmpty() ||
            visibleAlbums.isNotEmpty() || visibleArtists.isNotEmpty() ||
            visibleLikedSongs.isNotEmpty() || visibleHistory.isNotEmpty()

    /** Downloads live behind their own screen, so they count as content but not as sections. */
    val isEmptyLibrary: Boolean = !hasLibraryContent && downloadCount == 0

    val isNoResults: Boolean = hasLibraryContent && !hasVisibleContent

    val totalCollections: Int =
        userPlaylists.size + savedPlaylists.size + albums.size + artists.size

    /** Short line under the title: "12 playlists • 48 liked • 6 downloads". */
    val summary: String = buildList {
        val savedCount = totalCollections - userPlaylists.size
        if (userPlaylists.isNotEmpty()) add(counted(userPlaylists.size, "playlist"))
        if (likedSongs.isNotEmpty()) add(counted(likedSongs.size, "liked song"))
        if (savedCount > 0) add(counted(savedCount, "saved"))
        if (downloadCount > 0) add(counted(downloadCount, "download"))
    }.joinToString("  •  ")

    companion object {
        const val LIKED_PREVIEW_LIMIT = 10
        const val HISTORY_PREVIEW_LIMIT = 6
    }
}

// ---------------------------------------------------------------------------------------
// Pure helpers - no Compose, no Android, safe to unit test.
// ---------------------------------------------------------------------------------------

internal fun matchesQuery(query: String, vararg fields: String): Boolean {
    if (query.isEmpty()) return true
    return fields.any { it.contains(query, ignoreCase = true) }
}

private fun <T> List<T>.sortedFor(sort: LibrarySort, titleOf: (T) -> String): List<T> = when (sort) {
    LibrarySort.TITLE_ASC -> sortedBy { titleOf(it).lowercase() }
    LibrarySort.TITLE_DESC -> sortedByDescending { titleOf(it).lowercase() }
    LibrarySort.RECENT -> this
}

internal fun List<SavedCollectionItem>.applyLibrarySaved(
    query: String,
    filter: LibraryFilter,
    sort: LibrarySort
): List<SavedCollectionItem> = asSequence()
    .filter { filter.showsSaved(it.contentType) }
    .filter { matchesQuery(query, it.title, it.subtitle) }
    .toList()
    .sortedFor(sort) { it.title }

internal fun List<PlaylistWithTracks>.applyLibraryPlaylists(
    query: String,
    sort: LibrarySort
): List<PlaylistWithTracks> = asSequence()
    .filter { matchesQuery(query, it.playlist.displayTitle, it.playlist.title) }
    .toList()
    .sortedFor(sort) { it.playlist.displayTitle }

internal fun List<MusicItem>.applyLibraryTracks(
    query: String,
    sort: LibrarySort
): List<MusicItem> = asSequence()
    .filter { matchesQuery(query, it.title, it.artist) }
    .toList()
    .sortedFor(sort) { it.title }

/** "1 playlist" / "12 playlists". */
internal fun counted(size: Int, noun: String): String = "$size $noun" + if (size == 1) "" else "s"

/** Compact numbers for the quick-access tiles: 999, 1.2k, 3.4M. */
internal fun compactCount(size: Int): String = when {
    size < 1_000 -> size.toString()
    size < 1_000_000 -> trimZero(size / 1_000.0) + "k"
    else -> trimZero(size / 1_000_000.0) + "M"
}

private fun trimZero(value: Double): String {
    val rounded = "%.1f".format(value)
    return if (rounded.endsWith(".0")) rounded.dropLast(2) else rounded
}

/** m:ss for track durations, blank when unknown. */
internal fun formatDuration(millis: Long): String {
    if (millis <= 0L) return ""
    val totalSeconds = millis / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}
