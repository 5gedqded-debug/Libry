package app.libry.unofficial.ui

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Icon
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
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                if (showBar) Box(
                    Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp),
                ) {
                    Surface(
                        shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainer,
                        shadowElevation = 10.dp, modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(Modifier.padding(horizontal = 8.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceAround) {
                            TABS.forEach { t ->
                                val sel = route == t.route
                                Column(
                                    Modifier.clip(RoundedCornerShape(20.dp))
                                        .clickable {
                                            nav.navigate(t.route) {
                                                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        }
                                        .padding(horizontal = 10.dp, vertical = 4.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Icon(
                                        t.icon, contentDescription = t.label,
                                        tint = if (sel) Brand.Primary else Brand.Secondary,
                                    )
                                    Text(
                                        t.label, maxLines = 1, softWrap = false, fontSize = 10.sp,
                                        color = if (sel) Brand.Primary else Brand.Secondary,
                                    )
                                }
                            }
                        }
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
