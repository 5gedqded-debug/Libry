@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package app.libry.unofficial.ui.screens

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.libry.unofficial.data.CatalogFilter
import app.libry.unofficial.data.Release
import app.libry.unofficial.data.ScheduleItem
import app.libry.unofficial.data.RefItem
import app.libry.unofficial.data.best
import app.libry.unofficial.data.str
import app.libry.unofficial.data.title
import app.libry.unofficial.ui.ChipsRow
import app.libry.unofficial.ui.DropdownField
import app.libry.unofficial.ui.ErrorBox
import app.libry.unofficial.ui.Load
import app.libry.unofficial.ui.LocalApi
import app.libry.unofficial.ui.LocalNav
import app.libry.unofficial.ui.PagedGrid
import app.libry.unofficial.ui.PagedState
import app.libry.unofficial.ui.Pill
import app.libry.unofficial.ui.Poster
import app.libry.unofficial.ui.ReleaseCard
import app.libry.unofficial.ui.ReleaseRow
import app.libry.unofficial.ui.Screen
import app.libry.unofficial.ui.SectionTitle
import app.libry.unofficial.ui.errText
import app.libry.unofficial.ui.openUrl
import coil.compose.AsyncImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ============================ Главная ============================

@Composable
fun HomeScreen() {
    val api = LocalApi.current
    val nav = LocalNav.current
    val ctx = LocalContext.current
    var randomKey by remember { mutableStateOf(0) }

    Screen(
        title = "Libry",
        actions = {
            IconButton(onClick = { nav.navigate("search") }) { Icon(Icons.Filled.Search, "Поиск") }
        },
    ) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
            // Промо
            item {
                Load(key = "promo", modifier = Modifier.fillMaxWidth().height(60.dp), block = { api.promotions() }) { list ->
                    if (list.isNotEmpty()) LazyRow(
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(list.size) { i ->
                            val p = list[i]
                            Card(
                                Modifier.width(300.dp).clickable {
                                    val rid = p.release?.id
                                    if (rid != null) nav.navigate("release/$rid") else ctx.openUrl(p.url)
                                },
                            ) {
                                Box {
                                    AsyncImage(
                                        model = api.img(p.image.best()), contentDescription = null,
                                        contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().height(160.dp),
                                    )
                                    Column(
                                        Modifier.fillMaxWidth().align(Alignment.BottomStart)
                                            .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000))))
                                            .padding(10.dp),
                                    ) {
                                        Text(p.title.orEmpty(), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                                        if (!p.description.isNullOrBlank()) Text(p.description, color = Color(0xCCFFFFFF), maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                                        if (p.isAd == true) Text(
                                            "Реклама" + (p.adOrigin?.let { " • $it" } ?: "") + (p.adErid?.let { " • erid: $it" } ?: ""),
                                            color = Color(0x99FFFFFF), style = MaterialTheme.typography.labelSmall,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            // Последние
            item {
                SectionTitle("Последние релизы", action = "Все", onAction = { nav.navigate("latest") })
                Load(key = "latest", modifier = Modifier.fillMaxWidth().height(220.dp), block = { api.releasesLatest(20) }) { list ->
                    ReleaseRow(list) { r -> r.latestEpisode?.title() }
                }
            }
            // Продолжить просмотр (если авторизован)
            if (api.isLoggedIn) item {
                SectionTitle("Продолжить просмотр", action = "История", onAction = { nav.navigate("history") })
                Load(key = "hist", modifier = Modifier.fillMaxWidth().height(160.dp), block = { api.viewHistory(1, 12, status = "unwatched").data }) { list ->
                    LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(list.size) { i ->
                            val v = list[i]
                            val ep = v.releaseEpisode
                            val rel = ep?.release
                            Column(
                                Modifier.width(210.dp).clickable {
                                    val rid = rel?.id ?: ep?.releaseId
                                    if (rid != null && v.releaseEpisodeId != null) nav.navigate("player/$rid/${Uri.encode(v.releaseEpisodeId)}")
                                },
                            ) {
                                Poster(api.img(ep?.preview.best() ?: rel?.poster.best()), Modifier.fillMaxWidth().height(118.dp))
                                Text(rel?.title().orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge)
                                Text(ep?.title().orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
            // Рекомендованные
            item {
                SectionTitle("Рекомендуем")
                Load(key = "rec", modifier = Modifier.fillMaxWidth().height(220.dp), block = { api.releasesRecommended(null, 12) }) { ReleaseRow(it) }
            }
            // Случайные
            item {
                SectionTitle("Случайные", action = "Другие", onAction = { randomKey++ })
                Load(key = "rnd$randomKey", modifier = Modifier.fillMaxWidth().height(220.dp), block = { api.releasesRandom(12) }) { ReleaseRow(it) }
            }
            // Жанры
            item {
                SectionTitle("Жанры", action = "Все", onAction = { nav.navigate("genres") })
                Load(key = "gr", modifier = Modifier.fillMaxWidth().height(60.dp), block = { api.genresRandom(12) }) { list ->
                    LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(list.size) { i ->
                            val g = list[i]
                            Pill(g.name.orEmpty(), Modifier.clickable { g.id?.let { nav.navigate("genre/$it") } })
                        }
                    }
                }
            }
            // Франшизы
            item {
                SectionTitle("Франшизы", action = "Все", onAction = { nav.navigate("franchises") })
                Load(key = "fr", modifier = Modifier.fillMaxWidth().height(190.dp), block = { api.franchisesRandom(10) }) { list ->
                    LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(list.size) { i ->
                            val f = list[i]
                            Column(Modifier.width(118.dp).clickable { f.id?.let { nav.navigate("franchise/${Uri.encode(it)}") } }) {
                                Poster(api.img(f.image.best()), Modifier.fillMaxWidth().height(160.dp))
                                Text(f.name.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
            }
            // Видео
            item {
                SectionTitle("Видео", action = "Все", onAction = { nav.navigate("videos") })
                Load(key = "vid", modifier = Modifier.fillMaxWidth().height(150.dp), block = { api.videos(10) }) { list ->
                    LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(list.size) { i ->
                            val v = list[i]
                            Column(Modifier.width(220.dp).clickable { ctx.openUrl(v.url) }) {
                                Poster(api.img(v.image.best()), Modifier.fillMaxWidth().height(124.dp))
                                Text(v.title.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LatestScreen() {
    val api = LocalApi.current
    val nav = LocalNav.current
    val pager = remember { PagedState { p -> api.releasesLatestPaged(p) } }
    Screen("Последние релизы", onBack = { nav.popBackStack() }) {
        PagedGrid(pager, cardContent = { ReleaseCard(it, extra = it.latestEpisode?.title()) })
    }
}

// ============================ Поиск ============================

@Composable
fun SearchScreen() {
    val api = LocalApi.current
    val nav = LocalNav.current
    var q by remember { mutableStateOf("") }
    var submitted by remember { mutableStateOf("") }

    Screen("Поиск", onBack = { nav.popBackStack() }) {
        Column(Modifier.fillMaxSize()) {
            OutlinedTextField(
                value = q, onValueChange = { q = it },
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                singleLine = true, placeholder = { Text("Название, alias, или id через запятую") },
                trailingIcon = { if (q.isNotEmpty()) IconButton(onClick = { q = ""; submitted = "" }) { Icon(Icons.Filled.Clear, "Очистить") } },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { submitted = q.trim(); api.prefs.addSearch(submitted) }),
            )
            // живой поиск с задержкой
            LaunchedEffect(q) {
                delay(450)
                if (q.isBlank()) submitted = ""
                else if (q.trim().length >= 2 && q.trim() != submitted) submitted = q.trim()
            }
            if (submitted.isBlank()) {
                val hist = api.prefs.searchHistory
                if (hist.isNotEmpty()) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("История запросов", style = MaterialTheme.typography.titleSmall)
                        TextButton(onClick = { api.prefs.clearSearch() }) { Text("Очистить") }
                    }
                    LazyColumn {
                        items(hist.size) { i ->
                            Row(
                                Modifier.fillMaxWidth().clickable { q = hist[i]; submitted = hist[i] }.padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Filled.History, null)
                                Text(hist[i], Modifier.padding(start = 12.dp))
                            }
                        }
                    }
                }
            } else {
                // "12, 34, some-alias" -> /anime/releases/list; иначе обычный поиск
                val tokens = submitted.split(',').map { it.trim() }.filter { it.isNotEmpty() }
                val listMode = tokens.size > 1 && tokens.all { it.matches(Regex("[A-Za-z0-9_-]+")) }
                Load(key = submitted, block = {
                    if (listMode) {
                        val ids = tokens.mapNotNull { it.toIntOrNull() }
                        val aliases = tokens.filter { it.toIntOrNull() == null }
                        api.releasesList(ids, aliases, 1, 50).data
                    } else api.searchReleases(submitted)
                }) { list ->
                    if (list.isEmpty()) ErrorBox("Ничего не найдено")
                    else androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                        columns = androidx.compose.foundation.lazy.grid.GridCells.Adaptive(112.dp),
                        contentPadding = PaddingValues(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(list.size) { i -> ReleaseCard(list[i]) }
                    }
                }
            }
        }
    }
}

// ============================ Каталог ============================

@Composable
fun CatalogScreen() {
    val api = LocalApi.current
    val nav = LocalNav.current
    var filter by remember { mutableStateOf(CatalogFilter()) }
    var showFilters by remember { mutableStateOf(false) }
    var queryText by remember { mutableStateOf("") }
    val pager = remember(filter, api.prefs.usePost) { PagedState { p -> api.catalogReleases(p, filter) } }

    Screen(
        title = "Каталог",
        actions = {
            IconButton(onClick = { nav.navigate("search") }) { Icon(Icons.Filled.Search, "Поиск") }
            IconButton(onClick = { showFilters = true }) { Icon(Icons.Filled.FilterList, "Фильтры") }
        },
    ) {
        Column(Modifier.fillMaxSize()) {
            OutlinedTextField(
                value = queryText, onValueChange = { queryText = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                singleLine = true, placeholder = { Text("Поиск в каталоге") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { filter = filter.copy(search = queryText.trim()) }),
                trailingIcon = {
                    if (filter.search.isNotBlank()) IconButton(onClick = { queryText = ""; filter = filter.copy(search = "") }) { Icon(Icons.Filled.Clear, "Сброс") }
                },
            )
            Box(Modifier.weight(1f)) { PagedGrid(pager) }
        }
    }

    if (showFilters) CatalogFilterDialog(filter, onDismiss = { showFilters = false }, onApply = { filter = it; showFilters = false })
}

@Composable
private fun <T> RefLoad(key: String, block: suspend () -> List<T>, content: @Composable (List<T>) -> Unit) {
    Load(key = key, modifier = Modifier.fillMaxWidth().height(40.dp), block = block) { content(it) }
}

@Composable
fun CatalogFilterDialog(initial: CatalogFilter, onDismiss: () -> Unit, onApply: (CatalogFilter) -> Unit) {
    val api = LocalApi.current
    var f by remember { mutableStateOf(initial) }
    var fromYear by remember { mutableStateOf(initial.fromYear?.toString().orEmpty()) }
    var toYear by remember { mutableStateOf(initial.toYear?.toString().orEmpty()) }

    fun <T> Set<T>.toggle(v: T): Set<T> = if (v in this) this - v else this + v

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Фильтры каталога") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                item {
                    Text("Сортировка", style = MaterialTheme.typography.titleSmall)
                    RefLoad("sorting", { api.catalogRefSorting() }) { list ->
                        DropdownField("Не выбрано", list.map { (it.value) to (it.label ?: it.value.orEmpty()) }, f.sorting) { f = f.copy(sorting = it) }
                    }
                }
                item {
                    Text("Тип", style = MaterialTheme.typography.titleSmall)
                    Load("types", Modifier.fillMaxWidth().height(40.dp), { api.catalogRefTypes() }) { list ->
                        ChipsRow(list.map { (it.value.orEmpty()) to (it.description ?: it.value.orEmpty()) }, f.types) { f = f.copy(types = f.types.toggle(it)) }
                    }
                }
                item {
                    Text("Сезон", style = MaterialTheme.typography.titleSmall)
                    Load("seasons", Modifier.fillMaxWidth().height(40.dp), { api.catalogRefSeasons() }) { list ->
                        ChipsRow(list.map { (it.value.orEmpty()) to (it.description ?: it.value.orEmpty()) }, f.seasons) { f = f.copy(seasons = f.seasons.toggle(it)) }
                    }
                }
                item {
                    Text("Годы выхода", style = MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(fromYear, { fromYear = it.filter(Char::isDigit).take(4) }, Modifier.weight(1f), singleLine = true, label = { Text("От") })
                        OutlinedTextField(toYear, { toYear = it.filter(Char::isDigit).take(4) }, Modifier.weight(1f), singleLine = true, label = { Text("До") })
                    }
                }
                item {
                    Text("Возрастной рейтинг", style = MaterialTheme.typography.titleSmall)
                    Load("ages", Modifier.fillMaxWidth().height(40.dp), { api.catalogRefAgeRatings() }) { list ->
                        ChipsRow(list.map { (it.value.orEmpty()) to (it.label ?: it.value.orEmpty()) }, f.ageRatings) { f = f.copy(ageRatings = f.ageRatings.toggle(it)) }
                    }
                }
                item {
                    Text("Статус выхода", style = MaterialTheme.typography.titleSmall)
                    Load("pub", Modifier.fillMaxWidth().height(40.dp), { api.catalogRefPublishStatuses() }) { list ->
                        ChipsRow(list.map { (it.value.orEmpty()) to (it.description ?: it.value.orEmpty()) }, f.publishStatuses) { f = f.copy(publishStatuses = f.publishStatuses.toggle(it)) }
                    }
                }
                item {
                    Text("Статус озвучки", style = MaterialTheme.typography.titleSmall)
                    Load("prod", Modifier.fillMaxWidth().height(40.dp), { api.catalogRefProductionStatuses() }) { list ->
                        ChipsRow(list.map { (it.value.orEmpty()) to (it.description ?: it.value.orEmpty()) }, f.productionStatuses) { f = f.copy(productionStatuses = f.productionStatuses.toggle(it)) }
                    }
                }
                item {
                    Text("Жанры", style = MaterialTheme.typography.titleSmall)
                    Load("genres", Modifier.fillMaxWidth().height(40.dp), { api.catalogRefGenres() }) { list ->
                        ChipsRow(list.mapNotNull { g -> g.id?.let { it to (g.name ?: it.toString()) } }, f.genres) { f = f.copy(genres = f.genres.toggle(it)) }
                    }
                }
                item {
                    // справочник годов — подсказка диапазона
                    Load("years", Modifier.fillMaxWidth().height(24.dp), { api.catalogRefYears() }) { years ->
                        if (years.isNotEmpty()) Text("Доступно: ${years.min()}–${years.max()}", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onApply(f.copy(fromYear = fromYear.toIntOrNull(), toYear = toYear.toIntOrNull(), search = initial.search)) }) { Text("Применить") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { onApply(CatalogFilter(search = initial.search)) }) { Text("Сбросить") }
                TextButton(onClick = onDismiss) { Text("Отмена") }
            }
        },
    )
}

// ============================ Расписание ============================

@Composable
fun ScheduleScreen() {
    val api = LocalApi.current
    var week by remember { mutableStateOf(true) }
    Screen(
        title = "Расписание",
        actions = {
            TextButton(onClick = { week = !week }) { Text(if (week) "Сегодня" else "Неделя") }
        },
    ) {
        if (week) Load(key = "week", block = { api.scheduleWeek() }) { items ->
            val byDay = items.groupBy { it.release?.publishDay.str?.toIntOrNull() ?: 0 }
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
                for (d in 1..7) {
                    val list = byDay[d].orEmpty()
                    if (list.isEmpty()) continue
                    item { SectionTitle(app.libry.unofficial.data.DAYS_RU[d - 1]) }
                    item { ScheduleRow(list) }
                }
                val other = byDay[0].orEmpty()
                if (other.isNotEmpty()) {
                    item { SectionTitle("Без дня") }
                    item { ScheduleRow(other) }
                }
            }
        } else Load(key = "now", block = { api.scheduleNow() }) { now ->
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
                item { SectionTitle("Вчера") }; item { ScheduleRow(now.yesterday) }
                item { SectionTitle("Сегодня") }; item { ScheduleRow(now.today) }
                item { SectionTitle("Завтра") }; item { ScheduleRow(now.tomorrow) }
            }
        }
    }
}

@Composable
private fun ScheduleRow(list: List<ScheduleItem>) {
    if (list.isEmpty()) {
        Text("Нет релизов", Modifier.padding(horizontal = 12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    val rels: List<Release> = list.mapNotNull { it.release }
    LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(list.size) { i ->
            val it = list[i]
            val r = it.release ?: return@items
            val note = when {
                it.fullSeasonIsReleased == true -> "Сезон вышел"
                it.publishedReleaseEpisode != null -> "Вышла " + (it.publishedReleaseEpisode.title())
                it.nextReleaseEpisodeNumber != null -> "Скоро: серия ${it.nextReleaseEpisodeNumber}"
                else -> null
            }
            ReleaseCard(r, Modifier.width(118.dp), note)
        }
    }
    if (rels.isEmpty()) Text("—")
}
