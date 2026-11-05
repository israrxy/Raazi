package com.israrxy.raazi.ui.player

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.israrxy.raazi.utils.ThumbnailUtils

/**
 * Shared square artwork container that displays album art or video thumbnails.
 * Non-square video thumbnails (e.g. 16:9) are displayed using ContentScale.Fit
 * centered over a blurred, scaled-up copy to eliminate black letterbox bars.
 */
@Composable
fun PlayerArtworkImage(
    thumbnailUrl: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(20.dp),
    dominantColor: Color = MaterialTheme.colorScheme.surfaceVariant
) {
    val candidates = remember(thumbnailUrl) {
        ThumbnailUtils.getVideoThumbnailCandidates(thumbnailUrl)
    }
    var candidateIndex by remember(thumbnailUrl) { mutableIntStateOf(0) }
    val currentUrl = remember(candidates, candidateIndex, thumbnailUrl) {
        candidates.getOrElse(candidateIndex) {
            ThumbnailUtils.getHighQualityThumbnail(thumbnailUrl)
        }
    }
    val isKnownVideoUrl = remember(thumbnailUrl) {
        thumbnailUrl?.contains("ytimg.com/vi/") == true
    }
    var isNonSquare by remember(thumbnailUrl) {
        mutableStateOf(isKnownVideoUrl)
    }

    Box(
        modifier = modifier.clip(shape),
        contentAlignment = Alignment.Center
    ) {
        if (isNonSquare) {
            val isAndroid12 = remember { Build.VERSION.SDK_INT >= Build.VERSION_CODES.S }
            if (isAndroid12) {
                AsyncImage(
                    model = currentUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = 1.35f
                            scaleY = 1.35f
                        }
                        .blur(24.dp),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(dominantColor.copy(alpha = 0.85f))
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.35f))
            )
            AsyncImage(
                model = currentUrl,
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
                onSuccess = { state ->
                    val d = state.result.drawable
                    val w = d.intrinsicWidth
                    val h = d.intrinsicHeight
                    if (w > 0 && h > 0) {
                        val aspect = w.toFloat() / h.toFloat()
                        isNonSquare = aspect > 1.2f || aspect < 0.83f
                    }
                },
                onError = {
                    if (candidateIndex < candidates.lastIndex) {
                        candidateIndex++
                    }
                }
            )
        } else {
            AsyncImage(
                model = currentUrl,
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                onSuccess = { state ->
                    val d = state.result.drawable
                    val w = d.intrinsicWidth
                    val h = d.intrinsicHeight
                    if (w > 0 && h > 0) {
                        val aspect = w.toFloat() / h.toFloat()
                        if (aspect > 1.2f || aspect < 0.83f) {
                            isNonSquare = true
                        }
                    }
                },
                onError = {
                    if (candidateIndex < candidates.lastIndex) {
                        candidateIndex++
                    }
                }
            )
        }
    }
}
