@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package app.libry.unofficial.ui.screens

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.libry.unofficial.data.COLLECTION_TYPES
import app.libry.unofficial.data.UserFilter
import app.libry.unofficial.data.best
import app.libry.unofficial.data.title
import app.libry.unofficial.ui.ChipsRow
import app.libry.unofficial.ui.DropdownField
import app.libry.unofficial.ui.ErrorBox
import app.libry.unofficial.ui.Load
import app.libry.unofficial.ui.LocalApi
import app.libry.unofficial.ui.LocalNav
import app.libry.unofficial.ui.PagedGrid
import app.libry.unofficial.ui.PagedState
import app.libry.unofficial.ui.Poster
import app.libry.unofficial.ui.ReleaseCard
import app.libry.unofficial.ui.Screen
import app.libry.unofficial.ui.shortDate

// ============================ Библиотека ============================

@Composable
fun LibraryScreen() {
    val api = LocalApi.current
    val nav = LocalNav.current
    var tab by remember { mutableStateOf(0) } // 0 избранное, 1..5 коллекции
    var filter by remember { mutableStateOf(UserFilter()) }
    var showFilters by remember { mutableStateOf(false) }
    val titles = listOf("Избранное") + COLLECTION_TYPES.map { it.second }

    Screen(
        title = "Библиотека",
        actions = {
            if (api.isLoggedIn) {
                IconButton(onClick = { showFilters = true }) { Icon(Icons.Filled.FilterList, "Фильтры") }
            }
            TextButton(onClick = { nav.navigate("history") }) { Text("История") }
        },
    ) {
        if (!api.isLoggedIn) {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Войдите, чтобы пользоваться избранным и списками")
                Button(onClick = { nav.navigate("login") }, modifier = Modifier.padding(top = 12.dp)) { Text("Войти") }
            }
            return@Screen
        }
        Column(Modifier.fillMaxSize()) {
            ScrollableTabRow(selectedTabIndex = tab, edgePadding = 8.dp) {
                titles.forEachIndexed { i, t -> Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t) }) }
            }
            val pager = remember(tab, filter, api.prefs.usePost, api.prefs.token) {
                if (tab == 0) PagedState { p -> api.favoriteReleases(p, filter) }
                else PagedState { p -> api.collectionReleases(COLLECTION_TYPES[tab - 1].first, p, filter) }
            }
            Box(Modifier.weight(1f)) { PagedGrid(pager) }
        }
    }

    if (showFilters) UserFilterDialog(favorites = tab == 0, initial = filter, onDismiss = { showFilters = false }, onApply = { filter = it; showFilters = false })
}

@Composable
fun UserFilterDialog(favorites: Boolean, initial: UserFilter, onDismiss: () -> Unit, onApply: (UserFilter) -> Unit) {
    val api = LocalApi.current
    var f by remember { mutableStateOf(initial) }
    fun <T> Set<T>.toggle(v: T): Set<T> = if (v in this) this - v else this + v

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Фильтры списка") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                item {
                    OutlinedTextField(f.search, { f = f.copy(search = it) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Поиск") })
                }
                if (favorites) item {
                    Text("Сортировка", style = MaterialTheme.typography.titleSmall)
                    Load("fav_sort", Modifier.fillMaxWidth().height(40.dp), { api.favoriteRefSorting() }) { list ->
                        DropdownField("Не выбрано", list.map { it.value to (it.label ?: it.value.orEmpty()) }, f.sorting) { f = f.copy(sorting = it) }
                    }
                }
                item {
                    Text("Тип", style = MaterialTheme.typography.titleSmall)
                    Load("ut$favorites", Modifier.fillMaxWidth().height(40.dp), { if (favorites) api.favoriteRefTypes() else api.collectionRefTypes() }) { list ->
                        ChipsRow(list.map { it.value.orEmpty() to (it.description ?: it.value.orEmpty()) }, f.types) { f = f.copy(types = f.types.toggle(it)) }
                    }
                }
                item {
                    Text("Возрастной рейтинг", style = MaterialTheme.typography.titleSmall)
                    Load("ua$favorites", Modifier.fillMaxWidth().height(40.dp), { if (favorites) api.favoriteRefAgeRatings() else api.collectionRefAgeRatings() }) { list ->
                        ChipsRow(list.map { it.value.orEmpty() to (it.label ?: it.value.orEmpty()) }, f.ageRatings) { f = f.copy(ageRatings = f.ageRatings.toggle(it)) }
                    }
                }
                item {
                    Text("Жанры", style = MaterialTheme.typography.titleSmall)
                    Load("ug$favorites", Modifier.fillMaxWidth().height(40.dp), { if (favorites) api.favoriteRefGenres() else api.collectionRefGenres() }) { list ->
                        ChipsRow(list.mapNotNull { g -> g.id?.let { it to (g.name ?: it.toString()) } }, f.genres) { f = f.copy(genres = f.genres.toggle(it)) }
                    }
                }
                item {
                    Text("Годы", style = MaterialTheme.typography.titleSmall)
                    Load("uy$favorites", Modifier.fillMaxWidth().height(40.dp), { if (favorites) api.favoriteRefYears() else api.collectionRefYears() }) { years ->
                        ChipsRow(years.sortedDescending().map { it to it.toString() }, f.years) { f = f.copy(years = f.years.toggle(it)) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onApply(f) }) { Text("Применить") } },
        dismissButton = {
            Row {
                TextButton(onClick = { onApply(UserFilter()) }) { Text("Сбросить") }
                TextButton(onClick = onDismiss) { Text("Отмена") }
            }
        },
    )
}

// ============================ История просмотров ============================

@Composable
fun HistoryScreen() {
    val api = LocalApi.current
    val nav = LocalNav.current
    var status by remember { mutableStateOf<String?>(null) }
    var period by remember { mutableStateOf<String?>(null) }
    val pager = remember(status, period) { PagedState { p -> api.viewHistory(p, 24, period, status) } }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    androidx.compose.runtime.LaunchedEffect(pager) { pager.loadNext() }
    val last by androidx.compose.runtime.remember {
        androidx.compose.runtime.derivedStateOf { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
    }
    androidx.compose.runtime.LaunchedEffect(last, pager.items.size) {
        if (pager.items.isNotEmpty() && last >= pager.items.size - 4) pager.loadNext()
    }

    Screen("История", onBack = { nav.popBackStack() }) {
        if (!api.isLoggedIn) { ErrorBox("Войдите в аккаунт"); return@Screen }
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                DropdownField("Статус", listOf(null to "Все", "watched" to "Просмотренные", "unwatched" to "Недосмотренные"), status) { status = it }
                DropdownField("Период", listOf(null to "Всё время", "today" to "Сегодня", "yesterday" to "Вчера", "week" to "Неделя", "month" to "Месяц"), period) { period = it }
            }
            LazyColumn(Modifier.weight(1f), state = listState) {
                items(pager.items.size) { i ->
                    val v = pager.items[i]
                    val ep = v.releaseEpisode
                    val rel = ep?.release
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            val rid = rel?.id ?: ep?.releaseId
                            val eid = v.releaseEpisodeId
                            if (rid != null && eid != null) nav.navigate("player/$rid/${Uri.encode(eid)}")
                        }.padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Poster(api.img(ep?.preview.best() ?: rel?.poster.best()), Modifier.width(120.dp).height(68.dp))
                        Column(Modifier.weight(1f)) {
                            Text(rel?.title().orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                            Text(ep?.title().orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                            val d = ep?.duration ?: 0.0
                            val t = v.time ?: 0.0
                            Text(
                                listOfNotNull(
                                    if (v.isWatched == true) "просмотрено" else if (d > 0) "${(t / 60).toInt()} из ${(d / 60).toInt()} мин" else null,
                                    shortDate(v.updatedAt),
                                ).joinToString(" • "),
                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                item {
                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        val err = pager.error
                        when {
                            pager.loading -> androidx.compose.material3.CircularProgressIndicator()
                            err != null -> Text(err, color = MaterialTheme.colorScheme.error)
                            pager.items.isEmpty() && pager.ended -> Text("История пуста")
                        }
                    }
                }
            }
        }
    }
}
