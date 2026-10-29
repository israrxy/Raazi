package com.israrxy.raazi.ui.library

import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import com.israrxy.raazi.model.MusicContentType
import com.israrxy.raazi.model.MusicItem
import com.israrxy.raazi.model.SavedCollectionItem

// ---------------------------------------------------------------------------------------
// The library screen body. A LazyGridScope extension so the screen stays a thin shell:
// state is built and remembered by the screen, this file only decides what to draw.
// ---------------------------------------------------------------------------------------

internal fun LazyGridScope.LibraryGridContent(
    state: LibraryUiState,
    actions: LibraryActions,
    sync: LibrarySyncState
) {
    fullSpan("header") { LibraryHeader(summary = state.summary, onOpenSettings = actions.openSettings) }
    fullSpan("search") { LibrarySearchField(query = state.query, onQueryChange = actions.search) }
    fullSpan("filters") {
        LibraryFilterBar(
            filter = state.filter,
            onFilterChange = actions.selectFilter,
            sort = state.sort,
            onSortChange = actions.selectSort,
            layout = state.layout,
            onLayoutChange = actions.selectLayout
        )
    }
    fullSpan("sync") { LibrarySyncCard(sync = sync, actions = actions) }

    if (state.isEmptyLibrary) {
        fullSpan("empty") { LibraryEmptyState(actions = actions) }
        fullSpan("pills_empty") { LibraryActionPills(actions = actions) }
        return
    }

    fullSpan("quick") { LibraryQuickAccessGrid(state = state, actions = actions) }
    fullSpan("pills") { LibraryActionPills(actions = actions) }

    if (state.isNoResults) {
        fullSpan("no_results") { LibraryNoResultsState(query = state.query, actions = actions) }
        return
    }

    renderUserPlaylists(state, actions)
    renderSavedGroup(
        title = "Saved playlists",
        items = state.visibleSavedPlaylists,
        layout = state.layout,
        actions = actions
    )
    renderSavedGroup(
        title = "Albums",
        items = state.visibleAlbums,
        layout = state.layout,
        actions = actions
    )
    renderSavedGroup(
        title = "Artists",
        items = state.visibleArtists,
        layout = state.layout,
        actions = actions
    )
    renderSongs(
        title = "Liked songs",
        state = state,
        preview = state.likedPreview,
        queue = state.visibleLikedSongs,
        keyPrefix = "liked",
        capped = state.likedPreviewCapped,
        openAll = { actions.openPlaylist("favorites") },
        actions = actions
    )
    renderSongs(
        title = "Recently played",
        state = state,
        preview = state.historyPreview,
        queue = state.visibleHistory,
        keyPrefix = "history",
        capped = state.historyPreviewCapped,
        openAll = { actions.openPlaylist("history") },
        actions = actions
    )
}

// ---------------------------------------------------------------------------------------
// Sections
// ---------------------------------------------------------------------------------------

private fun LazyGridScope.renderUserPlaylists(
    state: LibraryUiState,
    actions: LibraryActions
) {
    val playlists = state.visibleUserPlaylists
    if (playlists.isEmpty()) return

    fullSpan("header_playlists") {
        LibrarySectionHeader(title = "Playlists", count = playlists.size)
    }
    val rows = playlists.map { entry ->
        entry.toCollectionUi(
            onOpen = { actions.openPlaylist(entry.playlist.id) },
            onRename = { actions.renamePlaylist(entry.playlist.id) },
            onDelete = { actions.deletePlaylist(entry.playlist.id) }
        )
    }
    collectionItems(rows, state.layout)
}

private fun LazyGridScope.renderSavedGroup(
    title: String,
    items: List<SavedCollectionItem>,
    layout: LibraryLayout,
    actions: LibraryActions
) {
    if (items.isEmpty()) return

    fullSpan("header_$title") { LibrarySectionHeader(title = title, count = items.size) }
    val rows = items.map { saved ->
        saved.toCollectionUi(
            onOpen = actions.savedOpener(saved),
            onRemove = actions.savedRemover(saved)
        )
    }
    collectionItems(rows, layout)
}

private fun LazyGridScope.renderSongs(
    title: String,
    state: LibraryUiState,
    preview: List<MusicItem>,
    queue: List<MusicItem>,
    keyPrefix: String,
    capped: Boolean,
    openAll: () -> Unit,
    actions: LibraryActions
) {
    if (queue.isEmpty()) return

    fullSpan("header_$keyPrefix") {
        LibrarySectionHeader(
            title = title,
            count = queue.size,
            actionLabel = if (capped) "See all" else null,
            onAction = if (capped) openAll else null
        )
    }
    val rows = buildSongUi(preview = preview, queue = queue, keyPrefix = keyPrefix, onPlay = actions.playTracks)
    items(rows, key = { it.key }, span = { GridItemSpan(maxLineSpan) }) { item ->
        LibrarySongRow(item = item)
    }
}

private fun LazyGridScope.collectionItems(
    rows: List<LibraryCollectionUi>,
    layout: LibraryLayout
) {
    if (layout == LibraryLayout.GRID) {
        items(rows, key = { it.key }) { item -> LibraryCollectionCard(item = item) }
    } else {
        items(
            rows,
            key = { it.key },
            span = { GridItemSpan(maxLineSpan) }
        ) { item -> LibraryCollectionRow(item = item) }
    }
}

// ---------------------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------------------

private fun LibraryActions.savedOpener(saved: SavedCollectionItem): () -> Unit =
    if (saved.contentType == MusicContentType.ARTIST) {
        { openArtist(saved.sourceId, saved.title) }
    } else {
        { openPlaylist(saved.sourceId) }
    }

private fun LibraryActions.savedRemover(saved: SavedCollectionItem): () -> Unit =
    if (saved.contentType == MusicContentType.ARTIST) {
        { removeSavedArtist(saved) }
    } else {
        { removeSavedCollection(saved) }
    }

/** A grid item that should always occupy a whole row (headers, bars, tiles, songs). */
private fun LazyGridScope.fullSpan(
    key: Any,
    content: @Composable () -> Unit
) {
    item(
        key = key,
        span = { GridItemSpan(maxLineSpan) },
        contentType = key
    ) { content() }
}
