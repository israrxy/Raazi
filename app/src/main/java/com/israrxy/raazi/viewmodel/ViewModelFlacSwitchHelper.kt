package com.israrxy.raazi.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.viewModelScope
import com.israrxy.raazi.model.MusicItem
import com.israrxy.raazi.player.StreamResolver
import com.israrxy.raazi.player.flac.FlacResolutionResult
import com.israrxy.raazi.player.flac.FlacStreamResolver
import com.israrxy.raazi.service.switchCurrentTrackToFlac
import com.israrxy.raazi.service.switchCurrentTrackToStandard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "ViewModelFlacSwitch"

/**
 * Checks whether the current audio stream is FLAC lossless.
 */
fun MusicPlayerViewModel.isCurrentStreamFlac(): Boolean {
    val quality = streamQuality.value
    return quality?.contains("FLAC", ignoreCase = true) == true ||
        StreamResolver.sessionFlacEnabled == true
}

/**
 * Attempts to switch the current track's playback stream to lossless FLAC.
 * If FLAC can be resolved, ExoPlayer switches seamlessly at the exact current position.
 * If FLAC cannot be resolved, [onResult] is called with the exact honest failure reason
 * and standard playback continues uninterrupted.
 */
fun MusicPlayerViewModel.switchCurrentTrackStreamToFlac(
    context: Context,
    track: MusicItem,
    onResult: (FlacResolutionResult) -> Unit
) {
    viewModelScope.launch(Dispatchers.IO) {
        Log.i(TAG, "Requesting FLAC stream switch for track: '${track.title}' by '${track.artist}' (ISRC: ${track.isrc})")

        val flacQuality = try {
            settingsDataStore.flacQuality.first()
        } catch (_: Exception) {
            "HI_RES_LOSSLESS"
        }

        val result = FlacStreamResolver.resolveFlacStreamDetailed(
            context = context,
            isrc = track.isrc,
            title = track.title,
            artist = track.artist,
            quality = flacQuality
        )

        withContext(Dispatchers.Main) {
            when (result) {
                is FlacResolutionResult.Success -> {
                    val service = playbackService
                    if (service != null) {
                        val switched = service.switchCurrentTrackToFlac(result.flacResult)
                        if (switched) {
                            _audioSourceLabel.value = result.flacResult.qualityLabel
                            Log.i(TAG, "Switched to FLAC successfully: ${result.flacResult.qualityLabel}")
                            onResult(result)
                        } else {
                            onResult(FlacResolutionResult.Failure("Playback service failed to switch audio stream"))
                        }
                    } else {
                        onResult(FlacResolutionResult.Failure("Playback service is not connected"))
                    }
                }
                is FlacResolutionResult.Failure -> {
                    Log.w(TAG, "FLAC resolution failed: ${result.reason}")
                    onResult(result)
                }
            }
        }
    }
}

/**
 * Switches the current track back to the standard YouTube stream.
 */
fun MusicPlayerViewModel.switchCurrentTrackStreamToStandard(
    onComplete: () -> Unit
) {
    val service = playbackService
    if (service != null) {
        service.switchCurrentTrackToStandard()
    } else {
        StreamResolver.sessionFlacEnabled = false
        StreamResolver.setCurrentStreamQuality(null)
    }
    _audioSourceLabel.value = "YouTube"
    onComplete()
}
