package com.zionhuang.innertube

import com.zionhuang.innertube.models.AlbumItem
import com.zionhuang.innertube.models.Artist
import com.zionhuang.innertube.models.ArtistItem
import com.zionhuang.innertube.models.GridRenderer
import com.zionhuang.innertube.models.MusicCarouselShelfRenderer
import com.zionhuang.innertube.models.MusicShelfRenderer
import com.zionhuang.innertube.models.PlaylistItem
import com.zionhuang.innertube.models.SearchSuggestions
import com.zionhuang.innertube.models.SongItem
import com.zionhuang.innertube.models.WatchEndpoint
import com.zionhuang.innertube.models.WatchEndpoint.WatchEndpointMusicSupportedConfigs.WatchEndpointMusicConfig.Companion.MUSIC_VIDEO_TYPE_ATV
import com.zionhuang.innertube.models.getContinuation
import com.zionhuang.innertube.models.getItems
import com.zionhuang.innertube.models.oddElements
import com.zionhuang.innertube.models.response.BrowseResponse
import com.zionhuang.innertube.models.response.GetSearchSuggestionsResponse
import com.zionhuang.innertube.models.response.NextResponse
import com.zionhuang.innertube.models.response.SearchResponse
import com.zionhuang.innertube.pages.AlbumPage
import com.zionhuang.innertube.pages.ArtistItemsContinuationPage
import com.zionhuang.innertube.pages.ArtistItemsPage
import com.zionhuang.innertube.pages.ArtistPage
import com.zionhuang.innertube.pages.BrowseResult
import com.zionhuang.innertube.pages.ExplorePage
import com.zionhuang.innertube.pages.HistoryPage
import com.zionhuang.innertube.pages.HomePage
import com.zionhuang.innertube.pages.LibraryContinuationPage
import com.zionhuang.innertube.pages.LibraryPage
import com.zionhuang.innertube.pages.MoodAndGenres
import com.zionhuang.innertube.pages.NewReleaseAlbumPage
import com.zionhuang.innertube.pages.NextPage
import com.zionhuang.innertube.pages.NextResult
import com.zionhuang.innertube.pages.PlaylistContinuationPage
import com.zionhuang.innertube.pages.PlaylistPage
import com.zionhuang.innertube.pages.RelatedPage
import com.zionhuang.innertube.pages.SearchPage
import com.zionhuang.innertube.pages.SearchResult
import com.zionhuang.innertube.pages.SearchSuggestionPage
import com.zionhuang.innertube.pages.SearchSummary
import com.zionhuang.innertube.pages.SearchSummaryPage

internal fun parseSearchSuggestions(response: GetSearchSuggestionsResponse): SearchSuggestions {
    val allContents = response.contents.orEmpty()
        .flatMap { it.searchSuggestionsSectionRenderer.contents }

    val queries = allContents.mapNotNull { content ->
        val renderer = content.searchSuggestionRenderer ?: return@mapNotNull null
        renderer.navigationEndpoint.searchEndpoint?.query?.takeIf { it.isNotBlank() }
            ?: renderer.suggestion.runs?.joinToString(separator = "") { it.text }?.takeIf { it.isNotBlank() }
    }.distinct()

    val recommendedItems = allContents.mapNotNull { content ->
        content.musicResponsiveListItemRenderer?.let { renderer ->
            SearchSuggestionPage.fromMusicResponsiveListItemRenderer(renderer)
        }
    }

    return SearchSuggestions(
        queries = queries,
        recommendedItems = recommendedItems
    )
}


internal fun parseSearchSummaryPage(response: SearchResponse): SearchSummaryPage {
    val sectionContents = response.contents?.tabbedSearchResultsRenderer?.tabs?.firstOrNull()
        ?.tabRenderer?.content?.sectionListRenderer?.contents.orEmpty()
    return SearchSummaryPage(
        summaries = sectionContents.mapNotNull { it ->
            val cardShelf = it.musicCardShelfRenderer
                ?: it.itemSectionRenderer?.contents?.firstOrNull { c -> c.musicCardShelfRenderer != null }?.musicCardShelfRenderer
            val musicShelf = it.musicShelfRenderer
                ?: it.itemSectionRenderer?.contents?.firstOrNull { c -> c.musicShelfRenderer != null }?.musicShelfRenderer

            if (cardShelf != null) {
                val mainItem = SearchSummaryPage.fromMusicCardShelfRenderer(cardShelf)
                val fallbackArtist = (mainItem as? ArtistItem)?.let { artist ->
                    Artist(name = artist.title, id = artist.id)
                }
                val subItems = cardShelf.contents
                    ?.mapNotNull { c -> c.musicResponsiveListItemRenderer }
                    ?.mapNotNull(SearchSummaryPage.Companion::fromMusicResponsiveListItemRenderer)
                    ?.map { subItem ->
                        if (subItem is SongItem && subItem.artists.isEmpty() && fallbackArtist != null) {
                            subItem.copy(artists = listOf(fallbackArtist))
                        } else {
                            subItem
                        }
                    }
                    .orEmpty()
                val allCardItems = (listOfNotNull(mainItem) + subItems).distinctBy { item -> item.id }
                if (allCardItems.isEmpty()) return@mapNotNull null
                SearchSummary(
                    title = cardShelf.header?.musicCardShelfHeaderBasicRenderer?.title?.runs?.firstOrNull()?.text ?: "Top result",
                    items = allCardItems,
                    mainItem = mainItem,
                    moreItems = subItems.filter { it.id != mainItem?.id }.distinctBy { it.id }
                )
            } else if (musicShelf != null) {
                SearchSummary(
                    title = musicShelf.title?.runs?.firstOrNull()?.text ?: "",
                    items = musicShelf.contents?.getItems()
                        ?.mapNotNull { renderer ->
                            SearchSummaryPage.fromMusicResponsiveListItemRenderer(renderer)
                        }
                        ?.distinctBy { item -> item.id }
                        ?.ifEmpty { null } ?: return@mapNotNull null
                )
            } else {
                val directItems = it.itemSectionRenderer?.contents
                    ?.mapNotNull { c -> c.musicResponsiveListItemRenderer }
                    ?.mapNotNull(SearchSummaryPage.Companion::fromMusicResponsiveListItemRenderer)
                    ?.distinctBy { item -> item.id }
                    .orEmpty()
                if (directItems.isNotEmpty()) {
                    SearchSummary(
                        title = "",
                        items = directItems
                    )
                } else {
                    null
                }
            }
        }
    )
}

internal fun parseSearchResult(response: SearchResponse): SearchResult {
    val sectionContents = response.contents?.tabbedSearchResultsRenderer?.tabs?.firstOrNull()
        ?.tabRenderer?.content?.sectionListRenderer?.contents.orEmpty()
    val shelves = sectionContents.mapNotNull {
        it.musicShelfRenderer ?: it.itemSectionRenderer?.contents?.firstOrNull { c -> c.musicShelfRenderer != null }?.musicShelfRenderer
    }
    val shelfItems = shelves.flatMap { shelf ->
        shelf.contents?.getItems()?.mapNotNull { SearchPage.toYTItem(it) }.orEmpty()
    }
    val directItems = sectionContents.flatMap { section ->
        section.itemSectionRenderer?.contents
            ?.mapNotNull { c -> c.musicResponsiveListItemRenderer }
            ?.mapNotNull { renderer -> SearchPage.toYTItem(renderer) }
            .orEmpty()
    }
    val items = (shelfItems + directItems).distinctBy { it.id }
    val continuation = shelves.lastOrNull()?.continuations?.getContinuation()
    return SearchResult(
        items = items,
        continuation = continuation
    )
}

internal fun parseAlbumPage(
    browseId: String,
    response: BrowseResponse,
    songs: List<SongItem>
): AlbumPage {
    val playlistId = response.microformat?.microformatDataRenderer?.urlCanonical?.substringAfterLast('=')!!
    val header = response.contents?.twoColumnBrowseResultsRenderer?.tabs?.firstOrNull()?.tabRenderer?.content?.sectionListRenderer?.contents?.firstOrNull()?.musicResponsiveHeaderRenderer
    return AlbumPage(
        album = AlbumItem(
            browseId = browseId,
            playlistId = playlistId,
            title = header?.title?.runs?.firstOrNull()?.text!!,
            artists = header.straplineTextOne?.runs?.oddElements()?.map {
                Artist(
                    name = it.text,
                    id = it.navigationEndpoint?.browseEndpoint?.browseId
                )
            }!!,
            year = header.subtitle?.runs?.lastOrNull()?.text?.toIntOrNull(),
            thumbnail = header.thumbnail?.musicThumbnailRenderer?.thumbnail?.thumbnails?.lastOrNull()?.url!!,
        ),
        songs = songs,
        otherVersions = response.contents.twoColumnBrowseResultsRenderer.secondaryContents?.sectionListRenderer?.contents?.getOrNull(1)?.musicCarouselShelfRenderer?.contents
            ?.mapNotNull { it.musicTwoRowItemRenderer }
            ?.mapNotNull(NewReleaseAlbumPage::fromMusicTwoRowItemRenderer)
            .orEmpty()
    )
}

internal fun parseArtistPage(browseId: String, response: BrowseResponse): ArtistPage {
    return ArtistPage(
        artist = ArtistItem(
            id = browseId,
            title = response.header?.musicImmersiveHeaderRenderer?.title?.runs?.firstOrNull()?.text
                ?: response.header?.musicVisualHeaderRenderer?.title?.runs?.firstOrNull()?.text
                ?: response.header?.musicHeaderRenderer?.title?.runs?.firstOrNull()?.text!!,
            thumbnail = response.header?.musicImmersiveHeaderRenderer?.thumbnail?.musicThumbnailRenderer?.getThumbnailUrl()
                ?: response.header?.musicVisualHeaderRenderer?.foregroundThumbnail?.musicThumbnailRenderer?.getThumbnailUrl()
                ?: response.header?.musicDetailHeaderRenderer?.thumbnail?.musicThumbnailRenderer?.getThumbnailUrl(),
            channelId = response.header?.musicImmersiveHeaderRenderer?.subscriptionButton?.subscribeButtonRenderer?.channelId,
            playEndpoint = response.contents?.singleColumnBrowseResultsRenderer?.tabs?.firstOrNull()
                ?.tabRenderer?.content?.sectionListRenderer?.contents?.firstOrNull()?.musicShelfRenderer
                ?.contents?.firstOrNull()?.musicResponsiveListItemRenderer?.overlay?.musicItemThumbnailOverlayRenderer
                ?.content?.musicPlayButtonRenderer?.playNavigationEndpoint?.watchEndpoint,
            shuffleEndpoint = response.header?.musicImmersiveHeaderRenderer?.playButton?.buttonRenderer?.navigationEndpoint?.watchEndpoint
                ?: response.contents?.singleColumnBrowseResultsRenderer?.tabs?.firstOrNull()?.tabRenderer?.content?.sectionListRenderer
                    ?.contents?.firstOrNull()?.musicShelfRenderer?.contents?.firstOrNull()?.musicResponsiveListItemRenderer?.navigationEndpoint?.watchPlaylistEndpoint,
            radioEndpoint = response.header?.musicImmersiveHeaderRenderer?.startRadioButton?.buttonRenderer?.navigationEndpoint?.watchEndpoint
        ),
        sections = response.contents?.singleColumnBrowseResultsRenderer?.tabs?.firstOrNull()
            ?.tabRenderer?.content?.sectionListRenderer?.contents
            ?.mapNotNull(ArtistPage::fromSectionListRendererContent)!!,
        description = response.header?.musicImmersiveHeaderRenderer?.description?.runs?.firstOrNull()?.text
    )
}

internal fun parseArtistItemsPage(response: BrowseResponse): ArtistItemsPage {
    val gridRenderer = response.contents?.singleColumnBrowseResultsRenderer?.tabs?.firstOrNull()
        ?.tabRenderer?.content?.sectionListRenderer?.contents?.firstOrNull()
        ?.gridRenderer
    return if (gridRenderer != null) {
        ArtistItemsPage(
            title = gridRenderer.header?.gridHeaderRenderer?.title?.runs?.firstOrNull()?.text.orEmpty(),
            items = gridRenderer.items.mapNotNull {
                it.musicTwoRowItemRenderer?.let { renderer ->
                    ArtistItemsPage.fromMusicTwoRowItemRenderer(renderer)
                }
            },
            continuation = gridRenderer.continuations?.getContinuation()
        )
    } else {
        val musicPlaylistShelfRenderer = response.contents?.singleColumnBrowseResultsRenderer?.tabs?.firstOrNull()
            ?.tabRenderer?.content?.sectionListRenderer?.contents?.firstOrNull()
            ?.musicPlaylistShelfRenderer
        ArtistItemsPage(
            title = response.header?.musicHeaderRenderer?.title?.runs?.firstOrNull()?.text!!,
            items = musicPlaylistShelfRenderer?.contents?.getItems()?.mapNotNull {
                ArtistItemsPage.fromMusicResponsiveListItemRenderer(it)
            }!!,
            continuation = musicPlaylistShelfRenderer.contents.getContinuation()
        )
    }
}

internal fun parseArtistItemsContinuationPage(response: BrowseResponse): ArtistItemsContinuationPage {
    return when {
        response.continuationContents?.gridContinuation != null -> {
            val gridContinuation = response.continuationContents.gridContinuation
            ArtistItemsContinuationPage(
                items = gridContinuation.items.mapNotNull {
                    it.musicTwoRowItemRenderer?.let { renderer ->
                        ArtistItemsPage.fromMusicTwoRowItemRenderer(renderer)
                    }
                },
                continuation = gridContinuation.continuations?.getContinuation()
            )
        }
        response.continuationContents?.musicPlaylistShelfContinuation != null -> {
            val musicPlaylistShelfContinuation = response.continuationContents.musicPlaylistShelfContinuation
            ArtistItemsContinuationPage(
                items = musicPlaylistShelfContinuation.contents.getItems().mapNotNull {
                    ArtistItemsPage.fromMusicResponsiveListItemRenderer(it)
                },
                continuation = musicPlaylistShelfContinuation.continuations?.getContinuation()
            )
        }
        else -> {
            val continuationItems = response.onResponseReceivedActions?.firstOrNull()
                ?.appendContinuationItemsAction?.continuationItems
            ArtistItemsContinuationPage(
                items = continuationItems?.getItems()?.mapNotNull {
                    ArtistItemsPage.fromMusicResponsiveListItemRenderer(it)
                }!!,
                continuation = continuationItems.getContinuation()
            )
        }
    }
}

internal fun parsePlaylistPage(playlistId: String, response: BrowseResponse): PlaylistPage {
    val base = response.contents?.twoColumnBrowseResultsRenderer?.tabs?.firstOrNull()?.tabRenderer?.content?.sectionListRenderer?.contents?.firstOrNull()
    val header = base?.musicResponsiveHeaderRenderer ?: base?.musicEditablePlaylistDetailHeaderRenderer?.header?.musicResponsiveHeaderRenderer
    val editable = base?.musicEditablePlaylistDetailHeaderRenderer != null

    return PlaylistPage(
        playlist = PlaylistItem(
            id = playlistId,
            title = header?.title?.runs?.firstOrNull()?.text ?: "",
            author = header?.straplineTextOne?.runs?.firstOrNull()?.let {
                Artist(
                    name = it.text,
                    id = it.navigationEndpoint?.browseEndpoint?.browseId
                )
            },
            songCountText = header?.secondSubtitle?.runs?.firstOrNull()?.text,
            thumbnail = response.background?.musicThumbnailRenderer?.getThumbnailUrl(),
            playEndpoint = header?.buttons?.getOrNull(1)?.musicPlayButtonRenderer
                ?.playNavigationEndpoint?.watchEndpoint,
            shuffleEndpoint = header?.buttons?.getOrNull(2)?.menuRenderer?.items?.find {
                it.menuNavigationItemRenderer?.icon?.iconType == "MUSIC_SHUFFLE"
            }?.menuNavigationItemRenderer?.navigationEndpoint?.watchPlaylistEndpoint,
            radioEndpoint = header?.buttons?.getOrNull(2)?.menuRenderer?.items?.find {
                it.menuNavigationItemRenderer?.icon?.iconType == "MIX"
            }?.menuNavigationItemRenderer?.navigationEndpoint?.watchPlaylistEndpoint,
            isEditable = editable
        ),
        songs = response.contents?.twoColumnBrowseResultsRenderer?.secondaryContents?.sectionListRenderer
            ?.contents?.firstOrNull()?.musicPlaylistShelfRenderer?.contents?.getItems()?.mapNotNull {
                PlaylistPage.fromMusicResponsiveListItemRenderer(it)
            }.orEmpty(),
        songsContinuation = response.contents?.twoColumnBrowseResultsRenderer?.secondaryContents?.sectionListRenderer
            ?.contents?.firstOrNull()?.musicPlaylistShelfRenderer?.contents?.getContinuation(),
        continuation = response.contents?.twoColumnBrowseResultsRenderer?.secondaryContents?.sectionListRenderer
            ?.continuations?.getContinuation()
    )
}

internal fun parsePlaylistContinuationPage(response: BrowseResponse): PlaylistContinuationPage {
    val musicPlaylistShelfContinuation = response.continuationContents?.musicPlaylistShelfContinuation
    return if (musicPlaylistShelfContinuation != null) {
        PlaylistContinuationPage(
            songs = musicPlaylistShelfContinuation.contents.getItems().mapNotNull {
                PlaylistPage.fromMusicResponsiveListItemRenderer(it)
            },
            continuation = musicPlaylistShelfContinuation.continuations?.getContinuation()
        )
    } else {
        val continuationItems = response.onResponseReceivedActions?.firstOrNull()
            ?.appendContinuationItemsAction?.continuationItems
        PlaylistContinuationPage(
            songs = continuationItems?.getItems()?.mapNotNull {
                PlaylistPage.fromMusicResponsiveListItemRenderer(it)
            } ?: emptyList(),
            continuation = continuationItems?.getContinuation()
        )
    }
}

internal fun parseHomePage(response: BrowseResponse): HomePage {
    val continuation = response.contents?.singleColumnBrowseResultsRenderer?.tabs?.firstOrNull()
        ?.tabRenderer?.content?.sectionListRenderer?.continuations?.getContinuation()
    val sectionListRender = response.contents?.singleColumnBrowseResultsRenderer?.tabs?.firstOrNull()
        ?.tabRenderer?.content?.sectionListRenderer
    val sections = sectionListRender?.contents!!
        .mapNotNull { content ->
            content.musicCarouselShelfRenderer?.let {
                HomePage.Section.fromMusicCarouselShelfRenderer(it)
            } ?: content.musicShelfRenderer?.let {
                HomePage.Section.fromMusicShelfRenderer(it)
            } ?: content.gridRenderer?.let {
                HomePage.Section.fromGridRenderer(it)
            }
        }.toMutableList()
    val chips = sectionListRender.header?.chipCloudRenderer?.chips?.mapNotNull { HomePage.Chip.fromChipCloudChipRenderer(it) }
    return HomePage(chips, sections, continuation)
}

internal fun parseExplorePage(response: BrowseResponse): ExplorePage {
    return ExplorePage(
        newReleaseAlbums = response.contents?.singleColumnBrowseResultsRenderer?.tabs?.firstOrNull()?.tabRenderer?.content?.sectionListRenderer?.contents?.find {
            it.musicCarouselShelfRenderer?.header?.musicCarouselShelfBasicHeaderRenderer?.moreContentButton?.buttonRenderer?.navigationEndpoint?.browseEndpoint?.browseId == "FEmusic_new_releases_albums"
        }?.musicCarouselShelfRenderer?.contents
            ?.mapNotNull { it.musicTwoRowItemRenderer }
            ?.mapNotNull(NewReleaseAlbumPage::fromMusicTwoRowItemRenderer).orEmpty(),
        moodAndGenres = response.contents?.singleColumnBrowseResultsRenderer?.tabs?.firstOrNull()?.tabRenderer?.content?.sectionListRenderer?.contents?.find {
            it.musicCarouselShelfRenderer?.header?.musicCarouselShelfBasicHeaderRenderer?.moreContentButton?.buttonRenderer?.navigationEndpoint?.browseEndpoint?.browseId == "FEmusic_moods_and_genres"
        }?.musicCarouselShelfRenderer?.contents
            ?.mapNotNull { it.musicNavigationButtonRenderer }
            ?.mapNotNull(MoodAndGenres.Companion::fromMusicNavigationButtonRenderer)
            .orEmpty()
    )
}

internal fun parseBrowseResult(response: BrowseResponse): BrowseResult {
    return BrowseResult(
        title = response.header?.musicHeaderRenderer?.title?.runs?.firstOrNull()?.text,
        items = response.contents?.singleColumnBrowseResultsRenderer?.tabs?.firstOrNull()?.tabRenderer?.content?.sectionListRenderer?.contents?.mapNotNull { content ->
            when {
                content.gridRenderer != null -> {
                    BrowseResult.Item(
                        title = content.gridRenderer.header?.gridHeaderRenderer?.title?.runs?.firstOrNull()?.text,
                        items = content.gridRenderer.items
                            .mapNotNull(GridRenderer.Item::musicTwoRowItemRenderer)
                            .mapNotNull(RelatedPage.Companion::fromMusicTwoRowItemRenderer)
                    )
                }
                content.musicCarouselShelfRenderer != null -> {
                    BrowseResult.Item(
                        title = content.musicCarouselShelfRenderer.header?.musicCarouselShelfBasicHeaderRenderer?.title?.runs?.firstOrNull()?.text,
                        items = content.musicCarouselShelfRenderer.contents
                            .mapNotNull(MusicCarouselShelfRenderer.Content::musicTwoRowItemRenderer)
                            .mapNotNull(RelatedPage.Companion::fromMusicTwoRowItemRenderer)
                    )
                }
                else -> null
            }
        }.orEmpty()
    )
}

internal fun parseLibraryPage(response: BrowseResponse, tabIndex: Int): LibraryPage {
    val tabs = response.contents?.singleColumnBrowseResultsRenderer?.tabs
    val contents = if (tabs != null && tabs.size >= tabIndex) {
        tabs[tabIndex].tabRenderer.content?.sectionListRenderer?.contents?.firstOrNull()
    } else {
        null
    }

    return when {
        contents?.gridRenderer != null -> {
            LibraryPage(
                items = contents.gridRenderer.items
                    .mapNotNull(GridRenderer.Item::musicTwoRowItemRenderer)
                    .mapNotNull { LibraryPage.fromMusicTwoRowItemRenderer(it) },
                continuation = contents.gridRenderer.continuations?.getContinuation()
            )
        }
        else -> {
            LibraryPage(
                items = contents?.musicShelfRenderer?.contents!!
                    .mapNotNull(MusicShelfRenderer.Content::musicResponsiveListItemRenderer)
                    .mapNotNull { LibraryPage.fromMusicResponsiveListItemRenderer(it) },
                continuation = contents.musicShelfRenderer.continuations?.getContinuation()
            )
        }
    }
}

internal fun parseLibraryContinuationPage(response: BrowseResponse): LibraryContinuationPage {
    val contents = response.continuationContents
    return when {
        contents?.gridContinuation != null -> {
            LibraryContinuationPage(
                items = contents.gridContinuation.items
                    .mapNotNull(GridRenderer.Item::musicTwoRowItemRenderer)
                    .mapNotNull { LibraryPage.fromMusicTwoRowItemRenderer(it) },
                continuation = contents.gridContinuation.continuations?.getContinuation()
            )
        }
        else -> {
            LibraryContinuationPage(
                items = contents?.musicShelfContinuation?.contents!!
                    .mapNotNull(MusicShelfRenderer.Content::musicResponsiveListItemRenderer)
                    .mapNotNull { LibraryPage.fromMusicResponsiveListItemRenderer(it) },
                continuation = contents.musicShelfContinuation.continuations?.getContinuation()
            )
        }
    }
}

internal fun parseRelatedPage(response: BrowseResponse): RelatedPage {
    val songs = mutableListOf<SongItem>()
    val albums = mutableListOf<AlbumItem>()
    val artists = mutableListOf<ArtistItem>()
    val playlists = mutableListOf<PlaylistItem>()
    response.contents?.sectionListRenderer?.contents?.forEach { sectionContent ->
        sectionContent.musicCarouselShelfRenderer?.contents?.forEach { content ->
            when (
                val item = content.musicResponsiveListItemRenderer?.let(RelatedPage.Companion::fromMusicResponsiveListItemRenderer)
                    ?: content.musicTwoRowItemRenderer?.let(RelatedPage.Companion::fromMusicTwoRowItemRenderer)
            ) {
                is SongItem -> if (
                    content.musicResponsiveListItemRenderer?.overlay
                        ?.musicItemThumbnailOverlayRenderer?.content
                        ?.musicPlayButtonRenderer?.playNavigationEndpoint
                        ?.watchEndpoint?.watchEndpointMusicSupportedConfigs
                        ?.watchEndpointMusicConfig?.musicVideoType == MUSIC_VIDEO_TYPE_ATV
                ) songs.add(item)
                is AlbumItem -> albums.add(item)
                is ArtistItem -> artists.add(item)
                is PlaylistItem -> playlists.add(item)
                null -> {}
            }
        }
    }
    return RelatedPage(songs, albums, artists, playlists)
}
