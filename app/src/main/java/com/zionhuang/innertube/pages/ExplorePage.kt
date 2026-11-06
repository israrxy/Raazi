package com.zionhuang.innertube.pages

import com.zionhuang.innertube.models.AlbumItem

data class ExplorePage(
    val newReleaseAlbums: List<AlbumItem>,
    val moodAndGenres: List<MoodAndGenres.Item>,
    /** Every carousel/shelf on the anonymous explore page, in page order. */
    val sections: List<HomePage.Section> = emptyList(),
)
