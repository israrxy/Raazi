package com.israrxy.raazi.ui

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import com.israrxy.raazi.ui.theme.Emerald500

internal val topLevelDestinations = listOf(
    BottomNavItem("Home", Icons.Default.Home, Icons.Outlined.Home, "home"),
    BottomNavItem("Search", Icons.Default.Search, Icons.Outlined.Search, "search"),
    BottomNavItem("Library", Icons.Default.LibraryMusic, Icons.Outlined.LibraryMusic, "library"),
    BottomNavItem("Audio", Icons.Default.GraphicEq, Icons.Outlined.GraphicEq, "equalizer"),
    BottomNavItem("Settings", Icons.Default.Settings, Icons.Outlined.Settings, "settings")
)

internal val bottomBarBaseHeight = 76.dp
internal val miniPlayerGap = 10.dp

internal fun cleanPlaylistId(id: String): String {
    val trimmed = id.trim()
    val listId = Regex("[?&]list=([^&#]+)").find(trimmed)?.groupValues?.getOrNull(1)
    return listId?.let(Uri::decode) ?: trimmed
}

internal fun NavHostController.navigateToPlaylist(id: String) {
    val cleanId = cleanPlaylistId(id)
    if (cleanId.isNotBlank()) {
        navigate("playlist/${Uri.encode(cleanId)}")
    }
}

@Composable
internal fun ConnectivityStatusBanner(
    isOnline: Boolean,
    justReconnected: Boolean,
    currentDestination: NavDestination?,
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = !isOnline || justReconnected,
        enter = fadeIn(tween(220)),
        exit = fadeOut(tween(220)),
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        val containerColor = if (isOnline) {
            Emerald500.copy(alpha = 0.95f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.96f)
        }
        val contentColor = if (isOnline) {
            Color.Black
        } else {
            MaterialTheme.colorScheme.onSurface
        }

        Surface(
            color = containerColor,
            contentColor = contentColor,
            shape = RoundedCornerShape(20.dp),
            tonalElevation = 8.dp,
            shadowElevation = 10.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = if (isOnline) Icons.Default.CloudDone else Icons.Default.CloudOff,
                    contentDescription = null,
                    tint = if (isOnline) Color.Black else MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = if (isOnline) {
                        "Back online • Refreshing music feed..."
                    } else {
                        "Offline mode • Playing from downloads & cache"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                if (!isOnline && currentDestination?.route != "downloads") {
                    Text(
                        text = "Downloads",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { navController.navigate("downloads") }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
internal fun SimpleBottomNavBar(
    navController: NavHostController,
    currentDestination: NavDestination?,
    haptic: com.israrxy.raazi.utils.HapticController = com.israrxy.raazi.utils.rememberHapticController()
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars),
        contentAlignment = Alignment.BottomCenter
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
            tonalElevation = 10.dp,
            shadowElevation = 18.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(bottomBarBaseHeight)
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                topLevelDestinations.forEach { item ->
                    val isSelected = currentDestination.matchesRoute(item.route)
                    val itemWeight by animateFloatAsState(
                        targetValue = if (isSelected) 1.45f else 0.9f,
                        label = "bottom_nav_weight"
                    )
                    val containerColor by animateColorAsState(
                        targetValue = if (isSelected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            Color.Transparent
                        },
                        label = "bottom_nav_container"
                    )
                    val contentColor by animateColorAsState(
                        targetValue = if (isSelected) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        label = "bottom_nav_content"
                    )

                    val interactionSource = remember { MutableInteractionSource() }
                    Row(
                        modifier = Modifier
                            .weight(itemWeight, fill = true)
                            .clip(RoundedCornerShape(20.dp))
                            .background(containerColor)
                            .clickable(
                                interactionSource = interactionSource,
                                indication = LocalIndication.current
                            ) {
                                haptic.lightTap()
                                navController.navigate(item.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                            .animateContentSize()
                            .padding(horizontal = 10.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isSelected) item.selectedIcon else item.unselectedIcon,
                            contentDescription = item.title,
                            tint = contentColor,
                            modifier = Modifier.size(22.dp)
                        )
                        AnimatedVisibility(
                            visible = isSelected,
                            enter = fadeIn() + slideInHorizontally(initialOffsetX = { it / 3 }),
                            exit = fadeOut() + slideOutHorizontally(targetOffsetX = { it / 3 })
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.padding(start = 8.dp)) {
                                    Text(
                                        text = item.title,
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        color = contentColor,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

internal fun NavDestination?.matchesRoute(route: String): Boolean {
    return this?.hierarchy?.any { it.route == route } == true
}

internal fun NavDestination?.isTopLevelDestination(): Boolean {
    return topLevelDestinations.any { matchesRoute(it.route) }
}

data class BottomNavItem(
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val route: String
)
