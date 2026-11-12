@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package app.libry.unofficial.ui.screens

import android.net.Uri
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.libry.unofficial.BuildConfig
import app.libry.unofficial.data.DEFAULT_BASE_URL
import app.libry.unofficial.data.best
import app.libry.unofficial.ui.Avatar
import app.libry.unofficial.ui.InfoRow
import app.libry.unofficial.ui.Load
import app.libry.unofficial.ui.LocalApi
import app.libry.unofficial.ui.LocalNav
import app.libry.unofficial.ui.PagedGrid
import app.libry.unofficial.ui.PagedState
import app.libry.unofficial.ui.Pill
import app.libry.unofficial.ui.Poster
import app.libry.unofficial.ui.ReleaseCard
import app.libry.unofficial.ui.Screen
import app.libry.unofficial.ui.openUrl
import app.libry.unofficial.ui.shortDate
import app.libry.unofficial.ui.toast

// ============================ Ещё ============================

@Composable
fun MoreScreen() {
    val api = LocalApi.current
    val nav = LocalNav.current
    data class Item(val title: String, val route: String)
    val items = listOf(
        Item("Жанры", "genres"), Item("Франшизы", "franchises"), Item("Торренты", "torrents"),
        Item("Видео", "videos"), Item("Команда", "team"), Item("Реклама и партнёры", "ads"),
        Item("Статус сервера", "status"), Item("Настройки", "settings"),
    )
    Screen("Ещё") {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Row(
                Modifier.fillMaxWidth().clickable { nav.navigate(if (api.isLoggedIn) "profile" else "login") }.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(if (api.isLoggedIn) "Профиль" else "Войти в аккаунт", style = MaterialTheme.typography.titleMedium)
            }
            HorizontalDivider()
            items.forEach { it ->
                Text(
                    it.title, style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.fillMaxWidth().clickable { nav.navigate(it.route) }.padding(16.dp),
                )
                HorizontalDivider()
            }
        }
    }
}

// ============================ Жанры ============================

@Composable
fun GenresScreen() {
    val api = LocalApi.current
    val nav = LocalNav.current
    var randomKey by remember { mutableStateOf(0) }
    Screen(
        "Жанры", onBack = { nav.popBackStack() },
        actions = {
            IconButton(onClick = { randomKey++ }) { Icon(Icons.Filled.Casino, "Случайные жанры") }
        },
    ) {
        Column(Modifier.fillMaxSize()) {
            if (randomKey > 0) Load(key = "gr$randomKey", modifier = Modifier.fillMaxWidth().height(48.dp), block = { api.genresRandom(5) }) { list ->
                Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Случайные:", Modifier.padding(top = 4.dp))
                    list.forEach { g -> Pill(g.name.orEmpty(), Modifier.clickable { g.id?.let { nav.navigate("genre/$it") } }) }
                }
            }
            Load(key = "genres", block = { api.genres() }) { list ->
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(150.dp), contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(list.size) { i ->
                        val g = list[i]
                        Column(Modifier.clickable { g.id?.let { nav.navigate("genre/$it") } }) {
                            Poster(api.img(g.image.best()), Modifier.fillMaxWidth().height(110.dp))
                            Text(g.name.orEmpty(), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            g.totalReleases?.let { Text("$it релизов", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun GenreScreen(id: Int) {
    val api = LocalApi.current
    val nav = LocalNav.current
    val pager = remember(id) { PagedState { p -> api.genreReleases(id, p) } }
    Load(key = id, block = { api.genre(id) }) { g ->
        Screen(g.name ?: "Жанр", onBack = { nav.popBackStack() }) { PagedGrid(pager) }
    }
}

// ============================ Франшизы ============================

@Composable
fun FranchisesScreen() {
    val api = LocalApi.current
    val nav = LocalNav.current
    var randomMode by remember { mutableStateOf(false) }
    var key by remember { mutableStateOf(0) }
    Screen(
        "Франшизы", onBack = { nav.popBackStack() },
        actions = {
            TextButton(onClick = { randomMode = true; key++ }) { Text("Случайные") }
            if (randomMode) TextButton(onClick = { randomMode = false }) { Text("Все") }
        },
    ) {
        Load(key = "fr$randomMode$key", block = { if (randomMode) api.franchisesRandom(20) else api.franchises() }) { list ->
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(list.size) { i ->
                    val f = list[i]
                    Row(
                        Modifier.fillMaxWidth().clickable { f.id?.let { nav.navigate("franchise/${Uri.encode(it)}") } },
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Poster(api.img(f.image.best()), Modifier.width(72.dp).height(104.dp))
                        Column(Modifier.weight(1f)) {
                            Text(f.name.orEmpty(), style = MaterialTheme.typography.titleSmall)
                            f.nameEnglish?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            Text(
                                listOfNotNull(
                                    f.totalReleases?.let { "$it релизов" }, f.totalEpisodes?.let { "$it эп." },
                                    f.totalDuration, f.rating?.let { "★ $it" },
                                ).joinToString(" • "),
                                style = MaterialTheme.typography.labelMedium,
                            )
                            val years = f.firstYear?.let { a -> f.lastYear?.let { b -> if (a == b) "$a" else "$a–$b" } ?: "$a" }
                            years?.let { Text(it, style = MaterialTheme.typography.labelSmall) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FranchiseScreen(id: String) {
    val api = LocalApi.current
    val nav = LocalNav.current
    Screen("Франшиза", onBack = { nav.popBackStack() }) {
        Load(key = id, block = { api.franchise(id) }) { f ->
            val rels = f.franchiseReleases.orEmpty().sortedBy { it.sortOrder ?: Int.MAX_VALUE }.mapNotNull { it.release }
            LazyVerticalGrid(
                columns = GridCells.Adaptive(112.dp), contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(f.name.orEmpty(), style = MaterialTheme.typography.titleLarge)
                        f.nameEnglish?.takeIf { it.isNotBlank() }?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        InfoRow("Релизов", f.totalReleases?.toString())
                        InfoRow("Эпизодов", f.totalEpisodes?.toString())
                        InfoRow("Длительность", f.totalDuration)
                        InfoRow("Рейтинг", f.rating?.toString())
                        InfoRow("Годы", f.firstYear?.let { a -> f.lastYear?.let { "$a–$it" } ?: "$a" })
                    }
                }
                items(rels.size) { i -> ReleaseCard(rels[i]) }
            }
        }
    }
}

// ============================ Видео ============================

@Composable
fun VideosScreen() {
    val api = LocalApi.current
    val nav = LocalNav.current
    val ctx = LocalContext.current
    val pager = remember { PagedState { p -> api.videosPaged(p) } }
    val state = androidx.compose.foundation.lazy.rememberLazyListState()
    androidx.compose.runtime.LaunchedEffect(pager) { pager.loadNext() }
    val last by remember { androidx.compose.runtime.derivedStateOf { state.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 } }
    androidx.compose.runtime.LaunchedEffect(last, pager.items.size) {
        if (pager.items.isNotEmpty() && last >= pager.items.size - 3) pager.loadNext()
    }
    Screen("Видео", onBack = { nav.popBackStack() }) {
        LazyColumn(Modifier.fillMaxSize(), state = state, contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(pager.items.size) { i ->
                val v = pager.items[i]
                Column(Modifier.fillMaxWidth().clickable { ctx.openUrl(v.url) }) {
                    Poster(api.img(v.image.best()), Modifier.fillMaxWidth().height(190.dp))
                    Text(v.title.orEmpty(), style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOfNotNull(
                            v.origin?.title, v.views?.let { "$it просмотров" }, v.comments?.let { "$it комм." }, shortDate(v.createdAt),
                            if (v.isAnnounce == true) "анонс" else null,
                        ).joinToString(" • "),
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item {
                Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                    val err = pager.error
                    if (pager.loading) androidx.compose.material3.CircularProgressIndicator()
                    else if (err != null) Text(err, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

// ============================ Команда ============================

@Composable
fun TeamScreen() {
    val api = LocalApi.current
    val nav = LocalNav.current
    Screen("Команда", onBack = { nav.popBackStack() }) {
        Load(key = "team", block = {
            val teams = api.teams()
            val roles = api.teamRoles()
            val users = api.teamUsers()
            Triple(teams, roles, users)
        }) { (teams, roles, users) ->
            val byTeam = users.groupBy { it.team?.id }
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    if (roles.isNotEmpty()) Text(
                        "Роли: " + roles.sortedBy { it.sortOrder ?: 0 }.joinToString(", ") { it.title.orEmpty() },
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                teams.sortedBy { it.sortOrder ?: 0 }.forEach { team ->
                    val members = byTeam[team.id].orEmpty().sortedBy { it.sortOrder ?: 0 }
                    if (members.isEmpty()) return@forEach
                    item {
                        Text(team.title.orEmpty(), style = MaterialTheme.typography.titleMedium)
                        team.description?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    }
                    items(members.size) { i ->
                        val m = members[i]
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Avatar(api.img(m.user?.avatar.best()), 40)
                            Column {
                                Text((m.nickname ?: m.user?.nickname).orEmpty() + (if (m.isIntern == true) " (стажёр)" else "") + (if (m.isVacation == true) " 🌴" else ""))
                                val rs = m.roles.orEmpty().joinToString(", ") { it.title.orEmpty() }
                                if (rs.isNotBlank()) Text(rs, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                // участники без команды
                val orphan = users.filter { u -> teams.none { it.id == u.team?.id } }
                if (orphan.isNotEmpty()) {
                    item { Text("Остальные", style = MaterialTheme.typography.titleMedium) }
                    items(orphan.size) { i -> Text(orphan[i].nickname.orEmpty()) }
                }
            }
        }
    }
}

// ============================ Статус / реклама ============================

@Composable
fun StatusScreen() {
    val api = LocalApi.current
    val nav = LocalNav.current
    Screen("Статус сервера", onBack = { nav.popBackStack() }) {
        Load(key = "status", block = { api.status() }) { s ->
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                InfoRow("Сервер жив", if (s.isAlive == true) "да" else "нет")
                InfoRow("Ваш IP", s.request?.ip)
                InfoRow("Страна", listOfNotNull(s.request?.country, s.request?.isoCode).joinToString(" / "))
                InfoRow("Часовой пояс", s.request?.timezone)
                InfoRow("API", api.prefs.baseUrl)
                val ep = s.availableApiEndpoints.orEmpty()
                if (ep.isNotEmpty()) {
                    Text("Доступные API-серверы", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
                    ep.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
    }
}

@Composable
fun AdsScreen() {
    val api = LocalApi.current
    val nav = LocalNav.current
    val ctx = LocalContext.current
    Screen("Реклама и партнёры", onBack = { nav.popBackStack() }) {
        Load(key = "vasts", block = { api.vasts() }) { list ->
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    Text("Рекламные кампании (VAST). Показ роликов в плеере включается в настройках.", style = MaterialTheme.typography.bodyMedium)
                }
                if (list.isEmpty()) item { Text("Кампаний нет") }
                items(list.size) { i ->
                    val v = list[i]
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text(v.adCompanyName ?: "Реклама", style = MaterialTheme.typography.titleSmall)
                            InfoRow("ИНН", v.adCompanyItn)
                            InfoRow("erid", v.adErid)
                            InfoRow("VAST", v.url)
                        }
                    }
                }
            }
        }
    }
}

// ============================ Настройки ============================

@Composable
fun SettingsScreen() {
    val api = LocalApi.current
    val nav = LocalNav.current
    val ctx = LocalContext.current
    val p = api.prefs
    var base by remember { mutableStateOf(p.baseUrl) }

    @Composable
    fun SwitchRow(title: String, sub: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title)
                if (sub != null) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = checked, onCheckedChange = onChange)
        }
    }

    @Composable
    fun Radio(label: String, selected: Boolean, onClick: () -> Unit) {
        Row(Modifier.fillMaxWidth().clickable(onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = selected, onClick = onClick)
            Text(label)
        }
    }

    Screen("Настройки", onBack = { nav.popBackStack() }) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Тема", style = MaterialTheme.typography.titleSmall)
            Radio("Системная", p.theme == 0) { p.updateTheme(0) }
            Radio("Светлая", p.theme == 1) { p.updateTheme(1) }
            Radio("Тёмная", p.theme == 2) { p.updateTheme(2) }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            Text("Качество по умолчанию", style = MaterialTheme.typography.titleSmall)
            Row {
                listOf("1080", "720", "480").forEach { q ->
                    Row(Modifier.clickable { p.updateQuality(q) }, verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = p.quality == q, onClick = { p.updateQuality(q) })
                        Text("${q}p", Modifier.padding(end = 12.dp))
                    }
                }
            }
            SwitchRow("Автопереход на следующую серию", null, p.autoNext) { p.updateAutoNext(it) }
            SwitchRow("Автопропуск опенинга", "Перематывает заставку, если известны её границы", p.autoSkip) { p.updateAutoSkip(it) }
            SwitchRow("Показывать рекламу (VAST)", "Короткий ролик перед первой серией. Список кампаний — в разделе «Реклама»", p.showAds) { p.updateShowAds(it) }
            SwitchRow("Фильтры списков через POST", "Использовать POST-варианты каталога, избранного и коллекций", p.usePost) { p.updateUsePost(it) }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            Text("Адрес API", style = MaterialTheme.typography.titleSmall)
            OutlinedTextField(base, { base = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Base URL") })
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { p.updateBaseUrl(base); base = p.baseUrl; ctx.toast("Адрес сохранён") }) { Text("Сохранить") }
                TextButton(onClick = { p.updateBaseUrl(DEFAULT_BASE_URL); base = p.baseUrl }) { Text("По умолчанию") }
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            Text("О приложении", style = MaterialTheme.typography.titleSmall)
            InfoRow("Название", "Libry (неофициальный клиент AniLiberty)")
            InfoRow("Пакет", "app.libry.unofficial")
            InfoRow("Версия", BuildConfig.VERSION_NAME)
            InfoRow("Устройство", p.deviceId.take(8) + "…")
        }
    }
}
