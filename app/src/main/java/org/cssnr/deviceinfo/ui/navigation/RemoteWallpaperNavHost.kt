package org.cssnr.deviceinfo.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import org.cssnr.deviceinfo.ui.screens.AboutRoute
import org.cssnr.deviceinfo.ui.screens.HomeRoute
import org.cssnr.deviceinfo.ui.screens.SettingsRoute

/**
 * The app's navigation graph.
 *
 * About stays a plain destination rather than a tab: it is pushed over the navigation bar and popped
 * back to, so it gets no entry in [AppDestination] and the bar hides itself while it is on top.
 */
@Composable
fun DeviceInfoNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = Route.Home,
        modifier = modifier,
    ) {
        composable<Route.Home> {
            HomeRoute(
                onNavigateToSettings = {
                    navController.navigateToTopLevel(AppDestination.Settings)
                },
            )
        }
        composable<Route.Settings> {
            SettingsRoute(
                onNavigateToAbout = { navController.navigate(Route.About) },
            )
        }
        composable<Route.About>(
            enterTransition = {
                slideIntoContainer(
                    AnimatedContentTransitionScope.SlideDirection.Left,
                    animationSpec = tween(300),
                )
            },
            popExitTransition = {
                slideOutOfContainer(
                    AnimatedContentTransitionScope.SlideDirection.Right,
                    animationSpec = tween(300),
                )
            },
        ) {
            AboutRoute(
                onBack = { navController.navigateUp() },
            )
        }
    }
}