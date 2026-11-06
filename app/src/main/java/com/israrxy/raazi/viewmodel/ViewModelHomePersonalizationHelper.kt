package com.israrxy.raazi.viewmodel

import android.util.Log
import androidx.lifecycle.viewModelScope
import com.israrxy.raazi.data.db.HomeInteractionEntity
import com.israrxy.raazi.data.recommendation.HomePersonalizationEngine
import com.israrxy.raazi.data.recommendation.ListeningProfileBuilder
import com.israrxy.raazi.data.recommendation.PersonalizedSection
import kotlinx.coroutines.launch

/**
 * Rebuilds the local-first personalized rails (My Mix, Because you listened
 * to…, Trending). These are generated from Room signals only, so they work for
 * Google Drive users who never sign in to YouTube Music.
 */
internal fun MusicPlayerViewModel.refreshPersonalizedSections(force: Boolean = false) {
    if (!force && personalizedSections.isNotEmpty()) return
    personalizedSectionsJob?.cancel()
    personalizedSectionsJob = viewModelScope.launch {
        try {
            val profile = ListeningProfileBuilder(repository, musicDao).build()
            val charts = runCatching { repository.getAllCharts() }.getOrDefault(emptyMap())
            val generated = HomePersonalizationEngine.build(profile, charts)
            personalizedSections = generated
                .map { it.toHomeSection() }
                .filter { it.items.isNotEmpty() }
            rebuildHomeFeedState()
        } catch (e: Exception) {
            Log.e("MusicVM", "Error generating personalized sections", e)
        }
    }
}

private fun PersonalizedSection.toHomeSection(): HomeSection = HomeSection(
    id = "personalized_$id",
    title = title,
    type = HomeSectionType.RECOMMENDATION,
    sourceType = HomeInteractionEntity.SOURCE_LOCAL,
    items = items.map(HomeFeedItem::Music)
)
