package lab.sabily.pulseid.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.EventNote
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.EventNote
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(
    val route: String,
    val label: String? = null,
    val icon: ImageVector? = null,
    val outlinedIcon: ImageVector? = null
) {
    object Home : Screen(
        route = "home",
        label = "Home",
        icon = Icons.Default.Home,
        outlinedIcon = Icons.Outlined.Home
    )
    object Activity : Screen(
        route = "activity",
        label = "Activity",
        icon = Icons.Default.DateRange,
        outlinedIcon = Icons.Outlined.DateRange
    )
    object Requests : Screen(
        route = "requests",
        label = "Requests",
        icon = Icons.Default.EventNote,
        outlinedIcon = Icons.Outlined.EventNote
    )
    object Profile : Screen(
        route = "profile",
        label = "Profile",
        icon = Icons.Default.Person,
        outlinedIcon = Icons.Outlined.Person
    )

    object EditProfile : Screen("edit_profile")
    object Settings : Screen("settings")
    object Notifications : Screen("notifications")
    object AdminPanel : Screen("admin_panel")
    object AdminScreenWrapper : Screen("admin_screen/{initialSubScreen}") {
        fun createRoute(initialSubScreen: String) = "admin_screen/$initialSubScreen"
    }

    companion object {
        val bottomNavItems by lazy { listOf(Home, Activity, Requests, Profile) }
    }
}
