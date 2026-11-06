package com.israrxy.raazi.viewmodel

import android.content.Context
import com.israrxy.raazi.data.recommendation.HomeShelfCache
import com.israrxy.raazi.data.recommendation.PlaybackContextStore
import com.israrxy.raazi.data.recommendation.TasteProfile
import com.israrxy.raazi.model.MusicItem
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Holder for the signed-out taste/shelf state. Kept out of
 * [MusicPlayerViewModel] so that class stays under the project file limit.
 */
internal class HomeTasteState(context: Context) {
    val playbackContextStore = PlaybackContextStore(context)
    val shelfCache = HomeShelfCache(context)

    val shelves = MutableStateFlow<List<HomeSection>>(emptyList())
    val candidates = MutableStateFlow<List<MusicItem>>(emptyList())
    val showBuilder = MutableStateFlow(false)
    val builderDismissed = MutableStateFlow(false)
    val language = MutableStateFlow(TasteProfile.LANGUAGE_AUTO)
    val isColdStart = MutableStateFlow(false)

    @Volatile
    var profile: TasteProfile? = null

    @Volatile
    var job: Job? = null

    @Volatile
    var candidatesJob: Job? = null

    @Volatile
    var lastRefreshMs: Long = 0L

    @Volatile
    var lastSeed: Int = 0
}
