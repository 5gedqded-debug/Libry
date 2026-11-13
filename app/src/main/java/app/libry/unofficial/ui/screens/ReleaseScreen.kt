@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package app.libry.unofficial.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp
import app.libry.unofficial.ui.Brand
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.libry.unofficial.data.COLLECTION_TYPES
import app.libry.unofficial.data.DAYS_RU
import app.libry.unofficial.data.Episode
import app.libry.unofficial.data.Release
import app.libry.unofficial.data.Tc
import app.libry.unofficial.data.best
import app.libry.unofficial.data.str
import app.libry.unofficial.data.title
import app.libry.unofficial.ui.InfoRow
import app.libry.unofficial.ui.Load
import app.libry.unofficial.ui.LocalApi
import app.libry.unofficial.ui.LocalNav
import app.libry.unofficial.ui.Pill
import app.libry.unofficial.ui.Poster
import app.libry.unofficial.ui.ReleaseRow
import app.libry.unofficial.ui.Screen
import app.libry.unofficial.ui.SectionTitle
import app.libry.unofficial.ui.errText
import app.libry.unofficial.ui.openUrl
import app.libry.unofficial.ui.toast
import coil.compose.AsyncImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun ReleaseScreen(idOrAlias: String) {
    val api = LocalApi.current
    val nav = LocalNav.current
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Load(key = idOrAlias, block = { api.release(idOrAlias) }) { rel -> ReleaseContent(rel) }
        Box(
            Modifier.statusBarsPadding().padding(12.dp).size(40.dp).clip(RoundedCornerShape(50))
                .background(Color(0x33000000)).clickable { nav.popBackStack() },
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад", tint = Color.White) }
    }
}

private fun sortedEpisodes(r: Release): List<Episode> =
    (r.episodes ?: emptyList()).sortedWith(compareBy({ it.sortOrder ?: Int.MAX_VALUE }, { it.ordinal ?: Double.MAX_VALUE }))

@Composable
private fun ReleaseContent(rel: Release) {
    val api = LocalApi.current
    val nav = LocalNav.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val rid = rel.id ?: return
    val episodes = remember(rel) { sortedEpisodes(rel) }

    var favorite by remember { mutableStateOf(false) }
    var collection by remember { mutableStateOf<String?>(null) }
    var progress by remember { mutableStateOf(mapOf<String, Tc>()) }
    var tick by remember { mutableStateOf(0) }

    // избранное / коллекция
    LaunchedEffect(rid, api.prefs.token) {
        if (api.isLoggedIn) {
            try { favorite = rid in api.favoriteIds() } catch (e: CancellationException) { throw e } catch (_: Exception) {}
            try { collection = api.collectionIds()[rid] } catch (e: CancellationException) { throw e } catch (_: Exception) {}
        } else { favorite = false; collection = null }
    }
    // прогресс просмотра
    LaunchedEffect(rid, api.prefs.token, tick) {
        val map = HashMap<String, Tc>()
        episodes.forEach { e -> e.id?.let { id -> api.prefs.localTimecode(id)?.let { map[id] = it } } }
        if (api.isLoggedIn) {
            try {
                api.releaseEpisodesTimecodes(rid.toString()).forEach { v ->
                    val id = v.releaseEpisodeId
                    if (id != null) map[id] = Tc(id, v.time ?: 0.0, v.isWatched ?: false)
                }
            } catch (e: CancellationException) { throw e } catch (_: Exception) {}
        }
        progress = map
    }

    fun requireLogin(): Boolean {
        if (api.isLoggedIn) return true
        ctx.toast("Войдите в аккаунт")
        nav.navigate("login")
        return false
    }

    val next: Episode? = episodes.firstOrNull { e -> progress[e.id]?.watched != true } ?: episodes.firstOrNull()

    Box(Modifier.fillMaxSize()) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 120.dp)) {
        // ---- шапка ----
        item {
            val genreLine = rel.genres.orEmpty().mapNotNull { it.name }.joinToString(", ")
            Box(Modifier.fillMaxWidth()) {
                Box(Modifier.fillMaxWidth().height(485.dp)) {
                    AsyncImage(
                        model = api.img(rel.poster.best()), contentDescription = null, contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant),
                    )
                    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xB3000000)))))
                    Column(
                        Modifier.align(Alignment.BottomStart).padding(start = 13.dp, end = 90.dp, bottom = 36.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(rel.title(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 30.sp)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            val tags = listOfNotNull(
                                rel.type.str, rel.year?.toString(), rel.ageRating?.let { a -> a.label ?: a.str },
                                rel.episodesTotal?.takeIf { it > 0 }?.let { "$it эп." },
                                if (rel.isOngoing == true) "Онгоинг" else null,
                            )
                            tags.forEach { t ->
                                Box(Modifier.clip(RoundedCornerShape(8.dp)).background(Color.White).padding(horizontal = 12.dp, vertical = 8.dp)) {
                                    Text(t, color = Brand.Secondary, fontSize = 12.sp, maxLines = 1)
                                }
                            }
                        }
                        if (genreLine.isNotBlank()) Text(genreLine, color = Brand.Dust, fontSize = 10.sp, modifier = Modifier.padding(top = 4.dp))
                    }
                }
                Column(
                    Modifier.fillMaxWidth().padding(top = 461.dp)
                        .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                        .background(MaterialTheme.colorScheme.background)
                        .padding(horizontal = 13.dp, vertical = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("Описание", style = MaterialTheme.typography.titleLarge)
                    rel.notification?.takeIf { it.isNotBlank() }?.let { Text(it, color = Brand.Primary, style = MaterialTheme.typography.bodyMedium) }
                    if (rel.isBlockedByGeo == true) Text("Релиз заблокирован в вашем регионе", color = MaterialTheme.colorScheme.error)
                    if (rel.isBlockedByCopyrights == true) Text("Релиз заблокирован по требованию правообладателя", color = MaterialTheme.colorScheme.error)
                    rel.description?.takeIf { it.isNotBlank() }?.let { d ->
                        var expanded by remember { mutableStateOf(false) }
                        Text(
                            buildAnnotatedString {
                                append(if (expanded || d.length <= 150) d else d.take(150).trimEnd() + "… ")
                                if (!expanded && d.length > 150) withStyle(SpanStyle(color = Brand.Primary)) { append("Читать далее") }
                            },
                            color = Brand.Secondary, style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.clickable { expanded = !expanded },
                        )
                    }
                }
                Column(
                    Modifier.align(Alignment.TopEnd).padding(end = 13.dp, top = 431.dp).shadow(7.dp, RoundedCornerShape(8.dp))
                        .clip(RoundedCornerShape(8.dp)).background(Brand.Primary).padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("Оценка", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    Text(
                        rel.rating?.average?.takeIf { it > 0 }?.let { String.format(java.util.Locale.US, "%.2f", it) } ?: "—",
                        color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold,
                    )
                }
                Box(
                    Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(12.dp).size(40.dp).clip(RoundedCornerShape(50))
                        .background(Color(0x33000000)).clickable {
                            if (!requireLogin()) return@clickable
                            scope.launch {
                                try {
                                    if (favorite) api.favoriteDelete(rid) else api.favoriteAdd(rid)
                                    favorite = !favorite
                                } catch (e: CancellationException) { throw e } catch (e: Exception) { ctx.toast(errText(e)) }
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) { Icon(if (favorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, "Избранное", tint = if (favorite) Color(0xFFFF4D6D) else Color.White) }
            }
        }

        // ---- действия ----
        item {
            Column(Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        onClick = { next?.id?.let { nav.navigate("player/$rid/${Uri.encode(it)}") } },
                        enabled = next?.id != null,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Filled.PlayArrow, null)
                        val label = when {
                            next == null -> "Нет серий"
                            progress.isNotEmpty() && progress[next.id] != null -> "Продолжить: " + next.title()
                            else -> "Смотреть: " + next.title()
                        }
                        Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    IconButton(onClick = {
                        if (!requireLogin()) return@IconButton
                        scope.launch {
                            try {
                                if (favorite) api.favoriteDelete(rid) else api.favoriteAdd(rid)
                                favorite = !favorite
                            } catch (e: CancellationException) { throw e } catch (e: Exception) { ctx.toast(errText(e)) }
                        }
                    }) {
                        Icon(if (favorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, "Избранное", tint = if (favorite) Color(0xFFFF4D6D) else MaterialTheme.colorScheme.onSurface)
                    }
                    IconButton(onClick = {
                        val link = "${api.prefs.origin}/anime/releases/release/${rel.alias ?: rid}/episodes"
                        ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"; putExtra(Intent.EXTRA_TEXT, "${rel.title()}\n$link")
                        }, "Поделиться"))
                    }) { Icon(Icons.Filled.Share, "Поделиться") }
                }
                // коллекции
                var menu by remember { mutableStateOf(false) }
                Box {
                    FilledTonalButton(onClick = { menu = true }) {
                        Text(COLLECTION_TYPES.firstOrNull { it.first == collection }?.second?.let { "В списке: $it" } ?: "Добавить в список")
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        COLLECTION_TYPES.forEach { (type, label) ->
                            DropdownMenuItem(text = { Text(label) }, onClick = {
                                menu = false
                                if (!requireLogin()) return@DropdownMenuItem
                                scope.launch {
                                    try { api.collectionAdd(rid, type); collection = type }
                                    catch (e: CancellationException) { throw e } catch (e: Exception) { ctx.toast(errText(e)) }
                                }
                            })
                        }
                        if (collection != null) DropdownMenuItem(text = { Text("Убрать из списка") }, onClick = {
                            menu = false
                            scope.launch {
                                try { api.collectionDelete(rid); collection = null }
                                catch (e: CancellationException) { throw e } catch (e: Exception) { ctx.toast(errText(e)) }
                            }
                        })
                    }
                }
            }
        }

        // ---- рейтинг ----
        item { RatingBlock(rel) { requireLogin() } }

        // ---- ссылки ----
        item {
            FlowRow(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rel.externalPlayer?.takeIf { it.isNotBlank() }?.let { OutlinedButton(onClick = { ctx.openUrl(api.url(it)) }) { Text("Внешний плеер") } }
                rel.shikimori?.url?.takeIf { it.isNotBlank() }?.let { OutlinedButton(onClick = { ctx.openUrl(it) }) { Text("Shikimori") } }
                rel.mal?.url?.takeIf { it.isNotBlank() }?.let { OutlinedButton(onClick = { ctx.openUrl(it) }) { Text("MyAnimeList") } }
            }
        }

        // ---- эпизоды ----
        item { Text("Список серий", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 13.dp, top = 8.dp, bottom = 12.dp)) }
        items(episodes.size) { i ->
            val e = episodes[i]
            EpisodeRow(rid, e, progress[e.id], onChanged = { tick++ })
        }

        // ---- команда релиза ----
        item {
            SectionTitle("Над релизом работали")
            Load(key = "members$rid", modifier = Modifier.fillMaxWidth().height(48.dp), block = { api.releaseMembers(rid.toString()) }) { list ->
                val groups = list.groupBy { it.role?.description ?: it.role.str ?: "Участники" }
                Column(Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (groups.isEmpty()) Text("Нет данных", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    groups.forEach { (role, ms) ->
                        InfoRow(role, ms.mapNotNull { it.nickname }.joinToString(", "))
                    }
                }
            }
        }

        // ---- франшизы ----
        item {
            Load(key = "fr$rid", modifier = Modifier.fillMaxWidth().height(1.dp), block = { api.franchisesByRelease(rid) }) { list ->
                if (list.isNotEmpty()) Column {
                    SectionTitle("Франшиза")
                    list.forEach { f ->
                        Row(
                            Modifier.fillMaxWidth().clickable { f.id?.let { nav.navigate("franchise/${Uri.encode(it)}") } }.padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Poster(api.img(f.image.best()), Modifier.width(56.dp).height(80.dp))
                            Column {
                                Text(f.name.orEmpty(), style = MaterialTheme.typography.titleSmall)
                                Text(
                                    listOfNotNull(
                                        f.totalReleases?.let { "$it релизов" }, f.totalEpisodes?.let { "$it эп." },
                                        f.firstYear?.let { y -> f.lastYear?.let { "$y–$it" } ?: y.toString() },
                                    ).joinToString(" • "),
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }

        // ---- торренты ----
        item {
            SectionTitle("Торренты", action = "RSS", onAction = { nav.navigate("rss/$rid") })
            Load(key = "tor$rid", modifier = Modifier.fillMaxWidth().height(48.dp), block = { api.torrentsByRelease(rid) }) { list ->
                Column(Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (list.isEmpty()) Text("Нет торрентов", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    list.forEach { TorrentCard(it, showRelease = false) }
                }
            }
        }

        // ---- спонсоры ----
        item {
            val sp = rel.sponsors.orEmpty()
            if (sp.isNotEmpty()) Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Поддержка", style = MaterialTheme.typography.titleMedium)
                sp.forEach { s ->
                    Column(Modifier.clickable { ctx.openUrl(s.url) }) {
                        Text(s.title.orEmpty(), style = MaterialTheme.typography.titleSmall)
                        s.description?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                        s.urlTitle?.takeIf { it.isNotBlank() }?.let { Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
        }

        // ---- статистика ----
        item {
            Column(Modifier.padding(12.dp)) {
                Text("Статистика", style = MaterialTheme.typography.titleMedium)
                InfoRow("В избранном", rel.addedInUsersFavorites?.toString())
                InfoRow("Смотрят", rel.addedInWatchingCollection?.toString())
                InfoRow("В планах", rel.addedInPlannedCollection?.toString())
                InfoRow("Просмотрено", rel.addedInWatchedCollection?.toString())
                InfoRow("Отложено", rel.addedInPostponedCollection?.toString())
                InfoRow("Брошено", rel.addedInAbandonedCollection?.toString())
                InfoRow("Комментариев", if (rel.isCommentsClosed == true) "закрыты" else rel.commentsCount?.toString())
            }
        }

        // ---- рекомендации ----
        item {
            SectionTitle("Похожие")
            Load(key = "rec$rid", modifier = Modifier.fillMaxWidth().height(220.dp), block = { api.releasesRecommended(rid, 12) }) { ReleaseRow(it) }
        }
    }
}

private fun Modifier.matchParentSizeCompat(): Modifier = this.fillMaxWidth().height(190.dp)

@Composable
private fun RatingBlock(rel: Release, requireLogin: () -> Boolean) {
    val api = LocalApi.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val rid = rel.id ?: return
    var own by remember { mutableStateOf<Int?>(null) }
    var siteAvg by remember { mutableStateOf(rel.rating?.average) }
    var siteVotes by remember { mutableStateOf(rel.rating?.votes) }

    LaunchedEffect(rid, api.prefs.token) {
        if (api.isLoggedIn) try { own = api.ratingGet(rid).score } catch (e: CancellationException) { throw e } catch (_: Exception) {}
        else own = null
    }

    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Filled.Star, null, tint = Color(0xFFFFC107))
            Text(
                (siteAvg?.let { String.format(java.util.Locale.US, "%.2f", it) } ?: "—") + (siteVotes?.let { " ($it)" } ?: ""),
                style = MaterialTheme.typography.titleMedium,
            )
            rel.shikimori?.rating?.let { Text("Shikimori $it", style = MaterialTheme.typography.bodySmall) }
            rel.mal?.rating?.let { Text("MAL $it", style = MaterialTheme.typography.bodySmall) }
        }
        Text(if (own != null) "Ваша оценка: $own" else "Поставьте оценку", style = MaterialTheme.typography.labelLarge)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(10) { i ->
                val score = i + 1
                val sel = own == score
                Box(
                    Modifier.clip(RoundedCornerShape(8.dp))
                        .background(if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                        .clickable {
                            if (!requireLogin()) return@clickable
                            scope.launch {
                                try { own = api.ratingSet(rid, score).score }
                                catch (e: CancellationException) { throw e } catch (e: Exception) { ctx.toast(errText(e)) }
                            }
                        }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Text(score.toString(), color = if (sel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
                }
            }
        }
        if (own != null) TextButton(onClick = {
            scope.launch {
                try { own = api.ratingDelete(rid).score }
                catch (e: CancellationException) { throw e } catch (e: Exception) { ctx.toast(errText(e)) }
            }
        }) { Text("Снять оценку") }
    }
    }
}

@Composable
private fun EpisodeRow(releaseId: Int, e: Episode, tc: Tc?, onChanged: () -> Unit) {
    val api = LocalApi.current
    val nav = LocalNav.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val id = e.id ?: return
    var menu by remember { mutableStateOf(false) }
    val dur = e.duration ?: 0.0
    val frac = if (tc != null && dur > 0) (tc.time / dur).coerceIn(0.0, 1.0).toFloat() else 0f

    fun setWatched(watched: Boolean) {
        scope.launch {
            try {
                val t = if (watched) dur.coerceAtLeast(1.0) else 0.0
                if (api.isLoggedIn) api.saveTimecodes(listOf(Tc(id, t, watched)))
                api.prefs.saveLocalTimecode(id, t, watched)
                onChanged()
            } catch (ex: CancellationException) { throw ex } catch (ex: Exception) { ctx.toast(errText(ex)) }
        }
    }

    Row(
        Modifier.padding(horizontal = 13.dp, vertical = 7.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable { nav.navigate("player/$releaseId/${Uri.encode(id)}") }
            .padding(horizontal = 12.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(64.dp).clip(RoundedCornerShape(8.dp))) {
            AsyncImage(api.img(e.preview.best()), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant))
            if (frac > 0f) LinearProgressIndicator(progress = { frac }, color = Brand.Primary, modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(e.title(), maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, lineHeight = 22.sp)
            val meta = listOfNotNull(
                e.duration?.takeIf { it > 0 }?.let { "${(it / 60).toInt()} мин" },
                if (tc?.watched == true) "просмотрено" else null,
            ).joinToString(" • ")
            if (meta.isNotBlank()) Text(meta, fontSize = 14.sp, color = Brand.Secondary)
        }
        Box(
            Modifier.size(48.dp).border(2.dp, Brand.Primary, RoundedCornerShape(50)),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Filled.PlayArrow, "Смотреть", tint = Brand.Primary, modifier = Modifier.size(26.dp)) }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Меню") }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text(if (tc?.watched == true) "Отметить непросмотренной" else "Отметить просмотренной") },
                    onClick = { menu = false; setWatched(tc?.watched != true) },
                )
                if (tc != null) DropdownMenuItem(text = { Text("Сбросить прогресс") }, onClick = {
                    menu = false
                    scope.launch {
                        try {
                            if (api.isLoggedIn) api.deleteTimecodes(listOf(id))
                            api.prefs.clearLocalTimecode(id)
                            onChanged()
                        } catch (ex: CancellationException) { throw ex } catch (ex: Exception) { ctx.toast(errText(ex)) }
                    }
                })
                e.rutubeId?.takeIf { it.isNotBlank() }?.let { r ->
                    DropdownMenuItem(text = { Text("Открыть в Rutube") }, onClick = { menu = false; ctx.openUrl("https://rutube.ru/video/$r/") })
                }
                e.youtubeId?.takeIf { it.isNotBlank() }?.let { y ->
                    DropdownMenuItem(text = { Text("Открыть в YouTube") }, onClick = { menu = false; ctx.openUrl("https://www.youtube.com/watch?v=$y") })
                }
            }
        }
    }
}
