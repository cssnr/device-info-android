package org.cssnr.deviceinfo.ui.navigation

import kotlinx.serialization.Serializable

/**
 * The app's own destinations.
 *
 * Routes are kept separate from [AppDestination] on purpose: [AppDestination] carries `ImageVector`s
 * and string resource ids for the navigation bar, and no feature code that just wants to navigate
 * somewhere should have to pull that UI vocabulary in with it. A feature that owns several screens
 * should declare its own routes in its own package instead of adding them here.
 */
sealed interface Route {

    @Serializable
    data object Home : Route

    @Serializable
    data object Settings : Route

    @Serializable
    data object About : Route
}