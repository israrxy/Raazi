package com.zionhuang.innertube.pages

import com.zionhuang.innertube.models.MusicResponsiveListItemRenderer
import com.zionhuang.innertube.models.YTItem

object SearchSuggestionPage {
    fun fromMusicResponsiveListItemRenderer(renderer: MusicResponsiveListItemRenderer): YTItem? {
        return SearchSummaryPage.fromMusicResponsiveListItemRenderer(renderer)
    }
}

