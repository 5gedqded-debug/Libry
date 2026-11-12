@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package app.libry.unofficial.ui.screens

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.libry.unofficial.data.Api
import app.libry.unofficial.data.Torrent
import app.libry.unofficial.data.str
import app.libry.unofficial.data.title
import app.libry.unofficial.ui.InfoRow
import app.libry.unofficial.ui.Load
import app.libry.unofficial.ui.LocalApi
import app.libry.unofficial.ui.LocalNav
import app.libry.unofficial.ui.PagedState
import app.libry.unofficial.ui.Pill
import app.libry.unofficial.ui.Screen
import app.libry.unofficial.ui.formatBytes
import app.libry.unofficial.ui.openUrl
import app.libry.unofficial.ui.shortDate
import app.libry.unofficial.ui.toast

/** Скачивание .torrent через системный DownloadManager (с Bearer-токеном, если вы вошли). */
fun downloadTorrentFile(ctx: Context, api: Api, t: Torrent, passkey: String? = null) {
    val key = t.hash ?: t.id?.toString()
    if (key == null) { ctx.toast("У торрента нет идентификатора"); return }
    val name = (t.filename?.takeIf { it.isNotBlank() } ?: key).let { if (it.endsWith(".torrent")) it else "$it.torrent" }
    try {
        val req = DownloadManager.Request(Uri.parse(api.torrentFileUrl(key, passkey)))
            .setTitle(name)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        api.prefs.token?.takeIf { it.isNotBlank() }?.let { req.addRequestHeader("Authorization", "Bearer $it") }
        if (Build.VERSION.SDK_INT >= 29) req.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name)
        else req.setDestinationInExternalFilesDir(ctx, Environment.DIRECTORY_DOWNLOADS, name)
        (ctx.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(req)
        ctx.toast("Загрузка начата: $name")
    } catch (e: Exception) {
        ctx.toast("Не удалось скачать: ${e.message}")
    }
}

@Composable
fun TorrentCard(t: Torrent, showRelease: Boolean) {
    val api = LocalApi.current
    val nav = LocalNav.current
    val ctx = LocalContext.current
    Card(Modifier.fillMaxWidth().clickable { (t.hash ?: t.id?.toString())?.let { nav.navigate("torrent/${Uri.encode(it)}") } }) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (showRelease) t.release?.let {
                Text(it.title(), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clickable { it.id?.let { id -> nav.navigate("release/$id") } })
            }
            Text(t.label ?: t.filename.orEmpty(), style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                (t.quality?.description ?: t.quality.str)?.let { Pill(it) }
                (t.codec?.label ?: t.codec?.description ?: t.codec.str)?.let { Pill(it) }
                (t.type?.description ?: t.type.str)?.let { Pill(it) }
                if (t.isHardsub == true) Pill("Hardsub")
                Pill(formatBytes(t.size))
            }
            Text(
                "↑ ${t.seeders ?: 0}   ↓ ${t.leechers ?: 0}   ✓ ${t.completedTimes ?: 0}",
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!t.magnet.isNullOrBlank()) FilledTonalButton(onClick = { ctx.openUrl(t.magnet) }) { Text("Magnet") }
                OutlinedButton(onClick = { downloadTorrentFile(ctx, api, t) }) { Text(".torrent") }
            }
        }
    }
}

@Composable
fun TorrentsScreen() {
    val api = LocalApi.current
    val nav = LocalNav.current
    val pager = remember { PagedState { p -> api.torrents(p) } }
    val state = rememberLazyListState()
    LaunchedEffect(pager) { pager.loadNext() }
    val last by remember { derivedStateOf { state.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 } }
    LaunchedEffect(last, pager.items.size) {
        if (pager.items.isNotEmpty() && last >= pager.items.size - 4) pager.loadNext()
    }
    Screen(
        "Торренты", onBack = { nav.popBackStack() },
        actions = { TextButton(onClick = { nav.navigate("rss") }) { Text("RSS") } },
    ) {
        LazyColumn(Modifier.fillMaxSize(), state = state, contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(pager.items.size) { i -> TorrentCard(pager.items[i], showRelease = true) }
            item {
                Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                    val err = pager.error
                    if (pager.loading) CircularProgressIndicator()
                    else if (err != null) Text(err, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
fun TorrentScreen(hashOrId: String) {
    val api = LocalApi.current
    val nav = LocalNav.current
    val ctx = LocalContext.current
    Screen("Торрент", onBack = { nav.popBackStack() }) {
        Load(key = hashOrId, block = { api.torrent(hashOrId) }) { t ->
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                t.release?.let { r ->
                    Text(r.title(), style = MaterialTheme.typography.titleLarge, modifier = Modifier.clickable { r.id?.let { nav.navigate("release/$it") } })
                }
                Text(t.label ?: t.filename.orEmpty())
                InfoRow("Качество", t.quality?.description ?: t.quality.str)
                InfoRow("Кодек", t.codec?.label ?: t.codec?.description ?: t.codec.str)
                InfoRow("Тип", t.type?.description ?: t.type.str)
                InfoRow("Размер", formatBytes(t.size))
                InfoRow("Битрейт", t.bitrate?.toString())
                InfoRow("Сидеры", t.seeders?.toString())
                InfoRow("Личеры", t.leechers?.toString())
                InfoRow("Скачано раз", t.completedTimes?.toString())
                InfoRow("Hardsub", if (t.isHardsub == true) "да" else null)
                InfoRow("Хэш", t.hash)
                InfoRow("Файл", t.filename)
                InfoRow("Создан", shortDate(t.createdAt))
                InfoRow("Обновлён", shortDate(t.updatedAt))
                t.description?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                t.torrentMembers?.takeIf { it.isNotEmpty() }?.let { ms ->
                    Text("Участники", style = MaterialTheme.typography.titleSmall)
                    ms.forEach { m -> InfoRow(m.role?.description ?: m.role.str ?: "—", m.nickname) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!t.magnet.isNullOrBlank()) FilledTonalButton(onClick = { ctx.openUrl(t.magnet) }) { Text("Magnet") }
                    OutlinedButton(onClick = { downloadTorrentFile(ctx, api, t) }) { Text("Скачать .torrent") }
                }
            }
        }
    }
}

@Composable
fun RssScreen(releaseId: Int?) {
    val api = LocalApi.current
    val nav = LocalNav.current
    val ctx = LocalContext.current
    Screen(if (releaseId == null) "Лента торрентов (RSS)" else "RSS релиза", onBack = { nav.popBackStack() }) {
        Load(key = releaseId, block = {
            if (releaseId == null) api.torrentsRss(50) else api.torrentsRssRelease(releaseId)
        }) { rss ->
            LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (rss.isEmpty()) item { Text("Пусто") }
                items(rss.size) { i ->
                    val it = rss[i]
                    Card(Modifier.fillMaxWidth().clickable { ctx.openUrl(it.link) }) {
                        Column(Modifier.padding(12.dp)) {
                            Text(it.title, style = MaterialTheme.typography.titleSmall)
                            it.date?.let { d -> Text(d, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                    }
                }
            }
        }
    }
}
