package com.israrxy.raazi.ui

import android.net.Uri
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.israrxy.raazi.ui.downloads.DownloadsScreen
import com.israrxy.raazi.ui.home.HomeScreen
import com.israrxy.raazi.ui.library.LibraryScreen
import com.israrxy.raazi.ui.ringtone.RingtoneTrimmerScreen
import com.israrxy.raazi.viewmodel.MusicPlayerViewModel

@Composable
internal fun MainNavHost(
    navController: NavHostController,
    viewModel: MusicPlayerViewModel,
    openPlayerSheet: () -> Unit,
    contentBottomPadding: Dp,
    modifier: Modifier = Modifier
) {
    val homeLazyListState = androidx.compose.foundation.lazy.rememberLazyListState()

    NavHost(
        navController = navController,
        startDestination = "home",
        modifier = modifier
            .fillMaxSize()
            .padding(bottom = contentBottomPadding),
        enterTransition = {
            if (initialState.destination.isTopLevelDestination() && targetState.destination.isTopLevelDestination()) {
                fadeIn(tween(150))
            } else {
                slideInHorizontally(
                    initialOffsetX = { (it * 0.08f).toInt() },
                    animationSpec = tween(250, easing = FastOutSlowInEasing)
                ) + fadeIn(tween(250))
            }
        },
        exitTransition = {
            if (initialState.destination.isTopLevelDestination() && targetState.destination.isTopLevelDestination()) {
                fadeOut(tween(150))
            } else {
                slideOutHorizontally(
                    targetOffsetX = { -(it * 0.08f).toInt() },
                    animationSpec = tween(250, easing = FastOutSlowInEasing)
                ) + fadeOut(tween(250))
            }
        },
        popEnterTransition = {
            if (initialState.destination.isTopLevelDestination() && targetState.destination.isTopLevelDestination()) {
                fadeIn(tween(150))
            } else {
                slideInHorizontally(
                    initialOffsetX = { -(it * 0.08f).toInt() },
                    animationSpec = tween(250, easing = FastOutSlowInEasing)
                ) + fadeIn(tween(250))
            }
        },
        popExitTransition = {
            if (initialState.destination.isTopLevelDestination() && targetState.destination.isTopLevelDestination()) {
                fadeOut(tween(150))
            } else {
                slideOutHorizontally(
                    targetOffsetX = { (it * 0.08f).toInt() },
                    animationSpec = tween(250, easing = FastOutSlowInEasing)
                ) + fadeOut(tween(250))
            }
        }
    ) {
        composable("home") {
            HomeScreen(
                viewModel = viewModel,
                onNavigateToPlayer = openPlayerSheet,
                onNavigateToPlaylist = { id ->
                    navController.navigateToPlaylist(id)
                },
                onNavigateToArtist = { artistId, artistName ->
                    navController.navigate("artist/$artistId?name=${Uri.encode(artistName)}")
                },
                onNavigateToSearch = {
                    navController.navigate("search") {
                        popUpTo(navController.graph.findStartDestination().id)
                        launchSingleTop = true
                    }
                },
                contentBottomPadding = contentBottomPadding,
                lazyListState = homeLazyListState
            )
        }
        composable("search") {
            SearchScreen(
                playerViewModel = viewModel,
                onNavigateToPlayer = openPlayerSheet,
                onNavigateToArtist = { artistId, artistName ->
                    navController.navigate("artist/$artistId?name=${Uri.encode(artistName)}")
                },
                onNavigateToPlaylist = { id ->
                    navController.navigateToPlaylist(id)
                }
            )
        }
        composable("library") {
            LibraryScreen(
                viewModel = viewModel,
                onNavigateToPlayer = openPlayerSheet,
                onNavigateToPlaylist = { id ->
                    navController.navigateToPlaylist(id)
                },
                onNavigateToArtist = { artistId, artistName ->
                    navController.navigate("artist/$artistId?name=${Uri.encode(artistName)}")
                },
                onNavigateToSettings = {
                    navController.navigate("settings") {
                        popUpTo(navController.graph.findStartDestination().id)
                        launchSingleTop = true
                    }
                },
                onNavigateToSpotifyImport = { navController.navigate("spotify_import") },
                onNavigateToYouTubeLogin = { navController.navigate("youtube_login") }
            )
        }
        composable("settings") {
            SettingsScreen(
                viewModel = viewModel,
                onNavigateToYouTubeLogin = { navController.navigate("youtube_login") }
            )
        }
        composable("youtube_login") {
            YouTubeLoginScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable("equalizer") {
            EnhancedEqualizerScreen(viewModel = viewModel)
        }
        composable("playlist/{playlistId}") { backStackEntry ->
            val playlistId = backStackEntry.arguments?.getString("playlistId") ?: ""
            com.israrxy.raazi.ui.playlist.PlaylistDetailScreen(
                playlistId = playlistId,
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToPlayer = openPlayerSheet,
                onNavigateToPlaylist = { id -> navController.navigateToPlaylist(id) }
            )
        }
        composable("artist/{artistId}?name={artistName}") { backStackEntry ->
            val artistId = backStackEntry.arguments?.getString("artistId") ?: ""
            val artistName = backStackEntry.arguments?.getString("artistName")
            ArtistScreen(
                artistId = artistId,
                artistName = artistName,
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable("downloads") {
            DownloadsScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onNavigateToPlayer = openPlayerSheet
            )
        }
        composable("ringtone_trimmer") {
            RingtoneTrimmerScreen(
                viewModel = viewModel,
                onClose = { viewModel.resetRingtoneState() }
            )
        }
        composable("spotify_import") {
            com.israrxy.raazi.ui.playlist.SpotifyImportScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToPlaylist = { id ->
                    val cleanId = cleanPlaylistId(id)
                    navController.navigate("playlist/${Uri.encode(cleanId)}") {
                        popUpTo("library")
                    }
                }
            )
        }
    }
}
