package app.libry.unofficial.ui

import android.net.Uri
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.sp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.libry.unofficial.ui.screens.AdsScreen
import app.libry.unofficial.ui.screens.CatalogScreen
import app.libry.unofficial.ui.screens.ForgotScreen
import app.libry.unofficial.ui.screens.FranchiseScreen
import app.libry.unofficial.ui.screens.FranchisesScreen
import app.libry.unofficial.ui.screens.GenreScreen
import app.libry.unofficial.ui.screens.GenresScreen
import app.libry.unofficial.ui.screens.HistoryScreen
import app.libry.unofficial.ui.screens.HomeScreen
import app.libry.unofficial.ui.screens.LatestScreen
import app.libry.unofficial.ui.screens.LibraryScreen
import app.libry.unofficial.ui.screens.LoginScreen
import app.libry.unofficial.ui.screens.MoreScreen
import app.libry.unofficial.ui.screens.OtpAcceptScreen
import app.libry.unofficial.ui.screens.PlayerScreen
import app.libry.unofficial.ui.screens.ProfileScreen
import app.libry.unofficial.ui.screens.ReleaseScreen
import app.libry.unofficial.ui.screens.ResetScreen
import app.libry.unofficial.ui.screens.RssScreen
import app.libry.unofficial.ui.screens.ScheduleScreen
import app.libry.unofficial.ui.screens.SearchScreen
import app.libry.unofficial.ui.screens.SettingsScreen
import app.libry.unofficial.ui.screens.StatusScreen
import app.libry.unofficial.ui.screens.TeamScreen
import app.libry.unofficial.ui.screens.TorrentScreen
import app.libry.unofficial.ui.screens.TorrentsScreen
import app.libry.unofficial.ui.screens.VideosScreen

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val TABS = listOf(
    Tab("home", "Главная", Icons.Filled.Home),
    Tab("catalog", "Каталог", Icons.Filled.GridView),
    Tab("schedule", "Расписание", Icons.Filled.CalendarMonth),
    Tab("library", "Библиотека", Icons.Filled.VideoLibrary),
    Tab("more", "Ещё", Icons.Filled.Menu),
)

@Composable
fun AppRoot() {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val showBar = TABS.any { it.route == route }

    CompositionLocalProvider(LocalNav provides nav) {
        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                if (showBar) NavigationBar {
                    TABS.forEach { t ->
                        NavigationBarItem(
                            selected = route == t.route,
                            onClick = {
                                nav.navigate(t.route) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(t.icon, contentDescription = t.label) },
                            label = { Text(t.label, maxLines = 1, softWrap = false, fontSize = 11.sp) },
                        )
                    }
                }
            },
        ) { padding ->
            NavHost(nav, startDestination = "home", modifier = Modifier.padding(padding)) {
                composable("home") { HomeScreen() }
                composable("catalog") { CatalogScreen() }
                composable("schedule") { ScheduleScreen() }
                composable("library") { LibraryScreen() }
                composable("more") { MoreScreen() }

                composable("search") { SearchScreen() }
                composable("latest") { LatestScreen() }
                composable(
                    "release/{id}", arguments = listOf(navArgument("id") { type = NavType.StringType }),
                ) { ReleaseScreen(Uri.decode(it.arguments?.getString("id").orEmpty())) }
                composable(
                    "player/{rid}/{eid}",
                    arguments = listOf(
                        navArgument("rid") { type = NavType.StringType },
                        navArgument("eid") { type = NavType.StringType },
                    ),
                ) {
                    PlayerScreen(
                        Uri.decode(it.arguments?.getString("rid").orEmpty()),
                        Uri.decode(it.arguments?.getString("eid").orEmpty()),
                    )
                }

                composable("genres") { GenresScreen() }
                composable("genre/{id}", arguments = listOf(navArgument("id") { type = NavType.IntType })) {
                    GenreScreen(it.arguments?.getInt("id") ?: 0)
                }
                composable("franchises") { FranchisesScreen() }
                composable("franchise/{id}", arguments = listOf(navArgument("id") { type = NavType.StringType })) {
                    FranchiseScreen(Uri.decode(it.arguments?.getString("id").orEmpty()))
                }

                composable("torrents") { TorrentsScreen() }
                composable("torrent/{id}", arguments = listOf(navArgument("id") { type = NavType.StringType })) {
                    TorrentScreen(Uri.decode(it.arguments?.getString("id").orEmpty()))
                }
                composable("rss") { RssScreen(null) }
                composable("rss/{rid}", arguments = listOf(navArgument("rid") { type = NavType.IntType })) {
                    RssScreen(it.arguments?.getInt("rid"))
                }

                composable("videos") { VideosScreen() }
                composable("team") { TeamScreen() }
                composable("status") { StatusScreen() }
                composable("ads") { AdsScreen() }
                composable("settings") { SettingsScreen() }

                composable("login") { LoginScreen() }
                composable("forgot") { ForgotScreen() }
                composable("reset") { ResetScreen() }
                composable("profile") { ProfileScreen() }
                composable("history") { HistoryScreen() }
                composable("otp_accept") { OtpAcceptScreen() }
            }
        }
    }
}
