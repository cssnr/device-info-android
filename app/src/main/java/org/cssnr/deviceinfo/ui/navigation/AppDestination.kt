package org.cssnr.deviceinfo.ui.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavOptionsBuilder
import org.cssnr.deviceinfo.R

/**
 * A destination the navigation bar offers, with everything the bar needs to draw and drive it.
 *
 * Keeping the label, both icons and the navigation call on the enum entry is what lets the bar be a
 * plain loop: adding a tab is one constant here, and there is no per-entry `when` to keep in sync.
 */
enum class AppDestination(
    @StringRes val labelRes: Int,
    // Resource ids rather than resolved vectors: an enum entry is built at class initialization,
    // outside any composition, so it has no Context to turn a drawable into an ImageVector. The bar
    // resolves them where it draws, which is why every navigation icon ships as a drawable.
    @DrawableRes val selectedIconRes: Int,
    @DrawableRes val unselectedIconRes: Int,
    val isCurrent: (NavDestination?) -> Boolean,
    val navigate: (NavHostController, NavOptionsBuilder.() -> Unit) -> Unit,
) {
    Home(
        labelRes = R.string.home,
        selectedIconRes = R.drawable.md_memory_filled_24px,
        unselectedIconRes = R.drawable.md_memory_24px,
        // Matching on the whole hierarchy rather than the destination alone is what keeps the tab
        // lit while the user is on a screen nested underneath it.
        isCurrent = { it.hierarchyHas<Route.Home>() },
        navigate = { controller, options -> controller.navigate(Route.Home, options) },
    ),
    Settings(
        labelRes = R.string.settings,
        selectedIconRes = R.drawable.md_settings_filled_24px,
        unselectedIconRes = R.drawable.md_settings_24px,
        isCurrent = { it.hierarchyHas<Route.Settings>() },
        navigate = { controller, options -> controller.navigate(Route.Settings, options) },
    ),
}

val topLevelDestinations: List<AppDestination> = AppDestination.entries

/**
 * Switches tabs without losing each tab's state.
 *
 * Popping everything above the start destination with `saveState` and turning `restoreState` back on
 * is what lets the Settings scroll position survive a trip to Home and back, and what stops the back
 * stack from growing by one entry per tab tap.
 */
fun NavHostController.navigateToTopLevel(destination: AppDestination) {
    destination.navigate(this) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

private inline fun <reified T : Any> NavDestination?.hierarchyHas(): Boolean =
    this?.hierarchy?.any { it.hasRoute<T>() } == true