package com.israrxy.raazi.ui.library

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import com.israrxy.raazi.model.MusicContentType
import com.israrxy.raazi.model.MusicItem
import com.israrxy.raazi.model.SavedCollectionItem

// ---------------------------------------------------------------------------------------
// The library screen body. A LazyListScope extension so the screen stays a thin shell:
// state is built and remembered by the screen, this file only decides what to draw.
// ---------------------------------------------------------------------------------------

internal fun LazyListScope.LibraryListContent(
    state: LibraryUiState,
    actions: LibraryActions,
    sync: LibrarySyncState
) {
    item("header") {
        LibraryHeader(
            summary = state.summary,
            onOpenDownloads = { actions.openPlaylist("downloads") },
            onOpenHistory = { actions.openPlaylist("history") },
            onOpenSpotifyImport = actions.openSpotifyImport,
            isLoggedIn = sync.loggedIn,
            isSyncing = sync.syncing,
            onSync = if (sync.loggedIn) actions.syncLibrary else actions.openYouTubeLogin
        )
    }
    item("search") { LibrarySearchField(query = state.query, onQueryChange = actions.search) }
    item("filters") {
        LibraryFilterBar(
            filter = state.filter,
            onFilterChange = actions.selectFilter,
            sort = state.sort,
            onSortChange = actions.selectSort
        )
    }

    if (state.isNoResults) {
        item("no_results") { LibraryNoResultsState(query = state.query, actions = actions) }
        return
    }

    renderPlaylists(state, actions)

    if (state.isEmptyLibrary) {
        item("empty") { LibraryEmptyState(actions = actions, isLoggedIn = sync.loggedIn) }
        return
    }

    if (state.filter == LibraryFilter.SONGS || state.query.isNotBlank()) {
        renderSongs(
            title = "Liked songs",
            preview = state.likedPreview,
            queue = state.visibleLikedSongs,
            keyPrefix = "liked",
            capped = state.likedPreviewCapped,
            openAll = { actions.openPlaylist("favorites") },
            actions = actions
        )
    }

    renderSavedGroup(
        title = "Artists",
        items = state.visibleArtists,
        actions = actions
    )
    renderSavedGroup(
        title = "Saved playlists",
        items = state.visibleSavedPlaylists,
        actions = actions
    )
    renderSavedGroup(
        title = "Albums",
        items = state.visibleAlbums,
        actions = actions
    )
    renderSongs(
        title = "Recently played",
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

private fun LazyListScope.renderPlaylists(
    state: LibraryUiState,
    actions: LibraryActions
) {
    if (state.filter != LibraryFilter.ALL && state.filter != LibraryFilter.PLAYLISTS) return

    val banners = buildList {
        if (state.query.isBlank() || "liked songs".contains(state.query, ignoreCase = true)) {
            add(
                likedPlaylistBanner(
                    likedSongs = state.likedSongs,
                    playCounts = state.playCounts,
                    onOpen = { actions.openPlaylist("favorites") }
                )
            )
        }
        if (state.query.isBlank() || "downloads".contains(state.query, ignoreCase = true)) {
            add(
                downloadedPlaylistBanner(
                    downloadCount = state.downloadCount,
                    downloadedSongs = state.downloadedSongs,
                    playCounts = state.playCounts,
                    onOpen = { actions.openPlaylist("downloads") }
                )
            )
        }
        state.visibleUserPlaylists.forEach { entry ->
            val isSelected = actions.isPlaylistSelected(entry.playlist.id)
            add(
                entry.toPlaylistBannerUi(
                    playCounts = state.playCounts,
                    isSelected = isSelected,
                    isSelectionMode = actions.isPlaylistSelectionMode,
                    onOpen = {
                        if (actions.isPlaylistSelectionMode) {
                            actions.onPlaylistLongClick?.invoke(entry.playlist.id)
                        } else {
                            actions.openPlaylist(entry.playlist.id)
                        }
                    },
                    onRename = { actions.renamePlaylist(entry.playlist.id) },
                    onDelete = { actions.deletePlaylist(entry.playlist.id) },
                    onLongClick = actions.onPlaylistLongClick?.let { { it(entry.playlist.id) } }
                )
            )
        }
    }
    if (banners.isEmpty()) return

    item("header_playlists") {
        LibrarySectionHeader(title = "Playlists", count = banners.size)
    }
    items(banners, key = { it.key }) { banner ->
        LibraryPlaylistBanner(item = banner)
    }
}

private fun LazyListScope.renderSavedGroup(
    title: String,
    items: List<SavedCollectionItem>,
    actions: LibraryActions
) {
    if (items.isEmpty()) return

    item("header_$title") { LibrarySectionHeader(title = title, count = items.size) }
    val rows = items.map { saved ->
        saved.toCollectionUi(
            onOpen = actions.savedOpener(saved),
            onRemove = actions.savedRemover(saved)
        )
    }
    collectionItems(rows)
}

private fun LazyListScope.renderSongs(
    title: String,
    preview: List<MusicItem>,
    queue: List<MusicItem>,
    keyPrefix: String,
    capped: Boolean,
    openAll: () -> Unit,
    actions: LibraryActions
) {
    if (queue.isEmpty()) return

    item("header_$keyPrefix") {
        LibrarySectionHeader(
            title = title,
            count = queue.size,
            actionLabel = if (capped) "See all" else null,
            onAction = if (capped) openAll else null
        )
    }
    val rows = buildSongUi(
        preview = preview,
        queue = queue,
        keyPrefix = keyPrefix,
        isSelected = actions.isTrackSelected,
        isSelectionMode = actions.isTrackSelectionMode,
        onPlay = actions.playTracks,
        onLongClick = actions.onTrackLongClick,
        onAddToPlaylist = actions.addToPlaylist,
        onRemove = if (keyPrefix == "history") actions.removeHistoryTrack else null
    )
    items(rows, key = { it.key }) { item ->
        LibrarySongRow(item = item)
    }
}

private fun LazyListScope.collectionItems(rows: List<LibraryCollectionUi>) {
    items(rows, key = { it.key }) { item -> LibraryCollectionRow(item = item) }
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
