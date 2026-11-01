package com.israrxy.raazi.ui.playlist

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.israrxy.raazi.model.MusicItem
import com.israrxy.raazi.viewmodel.MusicPlayerViewModel

@Composable
internal fun PlaylistSelectionActionBar(
    tracks: List<MusicItem>,
    playlistId: String?,
    isEditablePlaylist: Boolean,
    viewModel: MusicPlayerViewModel,
    onComplete: () -> Unit
) {
    val context = LocalContext.current
    val canRemove = isEditablePlaylist || playlistId == "history" || playlistId == "favorites"
    PlaylistSelectionBottomBar(
        selectedCount = tracks.size,
        canRemoveSelected = canRemove,
        likeActionLabel = if (playlistId == "favorites") "Unlike" else "Like",
        onLikeSelected = {
            viewModel.setTracksFavorite(tracks, liked = playlistId != "favorites")
            Toast.makeText(context, "Updated liked songs", Toast.LENGTH_SHORT).show()
            onComplete()
        },
        onDownloadSelected = {
            viewModel.downloadPlaylist(tracks)
            Toast.makeText(context, "Downloading ${tracks.size} tracks", Toast.LENGTH_SHORT).show()
            onComplete()
        },
        onQueueSelected = {
            viewModel.addToQueue(tracks)
            Toast.makeText(context, "Added ${tracks.size} tracks to queue", Toast.LENGTH_SHORT).show()
            onComplete()
        },
        onRemoveSelected = {
            when (playlistId) {
                "history" -> viewModel.removeTracksFromHistory(tracks)
                "favorites" -> viewModel.setTracksFavorite(tracks, liked = false)
                else -> playlistId?.let { viewModel.removeTracksFromPlaylist(it, tracks) }
            }
            Toast.makeText(context, "Removed ${tracks.size} tracks", Toast.LENGTH_SHORT).show()
            onComplete()
        }
    )
}
