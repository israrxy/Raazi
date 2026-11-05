package com.israrxy.raazi.viewmodel

import android.content.Context
import android.util.Log
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Reconstructs the user's algorithmic feed ("gives them back their feed")
 * after an account restore or Google Drive cloud sync.
 */
object FeedRecoveryEngine {
    private const val TAG = "FeedRecoveryEngine"

    /**
     * Executes feed reconstruction pipeline:
     * 1. Hydrates Quick Picks, Keep Listening, and Forgotten Favorites from newly restored Room database.
     * 2. Runs recommendation engine to generate "More from [Artist]" and "Because you listened to [Track]".
     * 3. Rebuilds the unified HomeFeedState so the UI renders the user's personalized algorithm instantly.
     */
    suspend fun recoverPersonalizedFeed(
        viewModel: MusicPlayerViewModel,
        context: Context? = null,
        showFeedback: Boolean = true
    ) = withContext(Dispatchers.Main) {
        try {
            Log.d(TAG, "Starting personalized feed recovery from restored sync data...")

            // 1. Instant local state hydration from Room SQLite
            val restoredQuickPicks = viewModel.repository.quickPicks.first().shuffled().take(50)
            if (restoredQuickPicks.isNotEmpty()) {
                viewModel._quickPicks.value = restoredQuickPicks
            }

            val restoredHistory = viewModel.repository.playbackHistory.first().take(30)
            if (restoredHistory.isNotEmpty()) {
                viewModel._keepListening.value = restoredHistory
            }

            val restoredForgotten = viewModel.repository.getForgottenFavorites().first()
            if (restoredForgotten.isNotEmpty()) {
                viewModel._forgottenFavorites.value = restoredForgotten
            }

            viewModel.rebuildHomeFeedState()

            // 2. Hydrate dynamic cloud recommendations based on restored seed history & top artists
            if (restoredHistory.isNotEmpty() || restoredQuickPicks.isNotEmpty()) {
                withContext(Dispatchers.IO) {
                    try {
                        viewModel.loadSimilarRecommendations()
                    } catch (e: Exception) {
                        Log.w(TAG, "Error generating similar recommendations during recovery", e)
                    }
                }
                viewModel.rebuildHomeFeedState()
            }

            if (showFeedback && context != null && (restoredQuickPicks.isNotEmpty() || restoredHistory.isNotEmpty())) {
                Toast.makeText(context, "Personalized feed & Quick Picks restored!", Toast.LENGTH_SHORT).show()
            }

            Log.d(TAG, "Personalized feed recovery completed: ${restoredQuickPicks.size} picks, ${restoredHistory.size} history items.")
        } catch (e: Exception) {
            Log.e(TAG, "Error recovering personalized feed", e)
        }
    }
}
