package org.cssnr.deviceinfo.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import org.cssnr.deviceinfo.ui.navigation.DeviceInfoNavHost
import org.cssnr.deviceinfo.ui.navigation.navigateToTopLevel
import org.cssnr.deviceinfo.ui.navigation.topLevelDestinations

@Composable
fun DeviceInfoApp(
    navController: NavHostController = rememberNavController(),
) {
    val currentEntry = navController.currentBackStackEntryAsState().value
    val selectedDestination =
        topLevelDestinations.firstOrNull { it.isCurrent(currentEntry?.destination) }

    Scaffold(
        // The top inset is left out because every screen draws its own TopAppBar, which consumes
        // it. Handing it out here as well would push the app bar down twice.
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets
            .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
        bottomBar = {
            // Only top level destinations show the bar. About is not one, so the bar goes away while
            // it is on top, which stops the user switching tabs from a screen meant to be popped back
            // from. An empty bottomBar also makes Scaffold hand the navigation bar insets to the
            // content slot itself, since there is no bar left to consume them.
            // AnimatedVisibility keeps the bar laid out while it slides away, so the Scaffold
            // content padding animates with it instead of jumping the moment navigation starts.
            AnimatedVisibility(
                visible = selectedDestination != null,
                enter = slideInVertically(animationSpec = tween(300)) { it } +
                    fadeIn(animationSpec = tween(300)),
                exit = slideOutVertically(animationSpec = tween(300)) { it } +
                    fadeOut(animationSpec = tween(300)),
            ) {
                NavigationBar {
                    topLevelDestinations.forEach { destination ->
                        val selected = destination == selectedDestination
                        NavigationBarItem(
                            selected = selected,
                            onClick = { navController.navigateToTopLevel(destination) },
                            icon = {
                                Icon(
                                    imageVector =
                                        ImageVector.vectorResource(
                                            if (selected) {
                                                destination.selectedIconRes
                                            } else {
                                                destination.unselectedIconRes
                                            },
                                        ),
                                    // The label below already carries the name, so repeating it here
                                    // would have it announced twice.
                                    contentDescription = null,
                                )
                            },
                            label = { Text(stringResource(destination.labelRes)) },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        DeviceInfoNavHost(
            navController = navController,
            // Consuming the insets the Scaffold already turned into padding stops every screen
            // below from applying them a second time.
            modifier = Modifier
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding),
        )
    }
}
