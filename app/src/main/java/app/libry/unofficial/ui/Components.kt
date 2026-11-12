@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package app.libry.unofficial.ui

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import app.libry.unofficial.data.Api
import app.libry.unofficial.data.Paged
import app.libry.unofficial.data.Release
import app.libry.unofficial.data.best
import app.libry.unofficial.data.subtitle
import app.libry.unofficial.data.title
import coil.compose.AsyncImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

val LocalApi = staticCompositionLocalOf<Api> { error("Api not provided") }
val LocalNav = staticCompositionLocalOf<NavHostController> { error("NavController not provided") }

// ---------------- Загрузка данных ----------------

sealed interface Res<out T> {
    data object Loading : Res<Nothing>
    data class Ok<T>(val value: T) : Res<T>
    data class Err(val message: String) : Res<Nothing>
}

fun errText(e: Throwable): String = e.message?.takeIf { it.isNotBlank() } ?: "Ошибка сети"

/** Грузит данные при смене key, показывает спиннер / ошибку с повтором / контент. */
@Composable
fun <T> Load(
    key: Any? = Unit,
    modifier: Modifier = Modifier.fillMaxSize(),
    block: suspend () -> T,
    content: @Composable (T) -> Unit,
) {
    var tick by remember { mutableIntStateOf(0) }
    var state by remember(key, tick) { mutableStateOf<Res<T>>(Res.Loading) }
    LaunchedEffect(key, tick) {
        state = Res.Loading
        state = try {
            Res.Ok(block())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Res.Err(errText(e))
        }
    }
    when (val s = state) {
        is Res.Loading -> Box(modifier, contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        is Res.Err -> ErrorBox(s.message, modifier) { tick++ }
        is Res.Ok -> content(s.value)
    }
}

@Composable
fun ErrorBox(message: String, modifier: Modifier = Modifier.fillMaxSize(), onRetry: (() -> Unit)? = null) {
    Box(modifier.padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(message, color = MaterialTheme.colorScheme.error)
            if (onRetry != null) Button(onClick = onRetry) { Text("Повторить") }
        }
    }
}

/** Постраничный загрузчик (бесконечная прокрутка). */
class PagedState<T>(private val fetch: suspend (Int) -> Paged<T>) {
    var items by mutableStateOf(listOf<T>())
        private set
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var ended by mutableStateOf(false)
        private set
    private var page = 0

    suspend fun loadNext() {
        if (loading || ended) return
        loading = true
        error = null
        try {
            val r = fetch(page + 1)
            page += 1
            items = items + r.data
            val tp = r.meta?.pagination?.totalPages
            val cp = r.meta?.pagination?.currentPage ?: page
            ended = r.data.isEmpty() || (tp != null && cp >= tp)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = errText(e)
        } finally {
            loading = false
        }
    }
}

/** Бесконечная сетка карточек релизов. */
@Composable
fun PagedGrid(
    pager: PagedState<Release>,
    modifier: Modifier = Modifier,
    header: (LazyGridScope.() -> Unit)? = null,
    cardContent: @Composable (Release) -> Unit = { ReleaseCard(it) },
) {
    val state = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    LaunchedEffect(pager) { pager.loadNext() }
    val lastIndex by remember { derivedStateOf { state.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 } }
    LaunchedEffect(lastIndex, pager.items.size) {
        if (lastIndex >= pager.items.size - 6 && pager.items.isNotEmpty()) pager.loadNext()
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(112.dp),
        state = state,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        header?.invoke(this)
        items(pager.items, key = null) { cardContent(it) }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                val err = pager.error
                when {
                    pager.loading -> CircularProgressIndicator()
                    err != null -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(err, color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = { scope.launch { pager.loadNext() } }) { Text("Повторить") }
                    }
                    pager.items.isEmpty() && pager.ended -> Text("Ничего не найдено", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

// ---------------- Каркас экрана ----------------

@Composable
fun Screen(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable () -> Unit = {},
    snackbar: SnackbarHostState? = null,
    content: @Composable () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    if (onBack != null) IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = { actions() },
            )
        },
        snackbarHost = { if (snackbar != null) SnackbarHost(snackbar) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) { content() }
    }
}

// ---------------- Карточки ----------------

@Composable
fun Poster(url: String?, modifier: Modifier = Modifier) {
    AsyncImage(
        model = url,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier.clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
    )
}

fun Context.openUrl(url: String?) {
    if (url.isNullOrBlank()) return
    try {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: Exception) {
        Toast.makeText(this, "Не удалось открыть ссылку", Toast.LENGTH_SHORT).show()
    }
}

fun Context.toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

fun Context.findActivity(): ComponentActivity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is ComponentActivity) return c
        c = c.baseContext
    }
    return null
}

@Composable
fun ReleaseCard(r: Release, modifier: Modifier = Modifier, extra: String? = null) {
    val nav = LocalNav.current
    val api = LocalApi.current
    Column(
        modifier.clickable { nav.navigate("release/${Uri.encode(r.id?.toString() ?: r.alias ?: "")}") },
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Poster(api.img(r.poster.best()), Modifier.fillMaxWidth().aspectRatio(2f / 3f))
        Text(r.title(), maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge)
        val sub = listOfNotNull(extra, r.subtitle().ifBlank { null }).joinToString(" • ")
        if (sub.isNotBlank()) Text(
            sub, maxLines = 1, overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Горизонтальная лента релизов. */
@Composable
fun ReleaseRow(items: List<Release>, extra: (Release) -> String? = { null }) {
    LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(items.size) { i -> ReleaseCard(items[i], Modifier.width(118.dp), extra(items[i])) }
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium)
        if (action != null && onAction != null) TextButton(onClick = onAction) { Text(action) }
    }
}

@Composable
fun <T> ChipsRow(
    options: List<Pair<T, String>>,
    selected: Set<T>,
    onToggle: (T) -> Unit,
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        options.forEach { (v, label) ->
            FilterChip(selected = v in selected, onClick = { onToggle(v) }, label = { Text(label) })
        }
    }
}

@Composable
fun <T> DropdownField(
    label: String,
    options: List<Pair<T?, String>>,
    selected: T?,
    onSelect: (T?) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val text = options.firstOrNull { it.first == selected }?.second ?: label
    Box {
        TextButton(onClick = { open = true }) { Text(text) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { (v, l) ->
                DropdownMenuItem(text = { Text(l) }, onClick = { onSelect(v); open = false })
            }
        }
    }
}

@Composable
fun InfoRow(label: String, value: String?) {
    if (value.isNullOrBlank()) return
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, modifier = Modifier.padding(start = 12.dp))
    }
}

@Composable
fun Pill(text: String, modifier: Modifier = Modifier) {
    Surface(
        modifier, shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Text(
            text, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

@Composable
fun Avatar(url: String?, size: Int = 40) {
    AsyncImage(
        model = url, contentDescription = null, contentScale = ContentScale.Crop,
        modifier = Modifier.size(size.dp).clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surfaceVariant),
    )
}

fun formatBytes(b: Long?): String {
    if (b == null) return "—"
    val units = listOf("Б", "КБ", "МБ", "ГБ", "ТБ")
    var v = b.toDouble(); var i = 0
    while (v >= 1024 && i < units.lastIndex) { v /= 1024; i++ }
    return String.format(java.util.Locale.US, if (i == 0) "%.0f %s" else "%.1f %s", v, units[i])
}

fun formatTime(sec: Long): String {
    val s = if (sec < 0) 0 else sec
    val h = s / 3600; val m = (s % 3600) / 60; val ss = s % 60
    return if (h > 0) String.format(java.util.Locale.US, "%d:%02d:%02d", h, m, ss)
    else String.format(java.util.Locale.US, "%d:%02d", m, ss)
}

fun shortDate(iso: String?): String? = iso?.take(10)?.takeIf { it.length == 10 }?.let {
    val p = it.split('-'); if (p.size == 3) "${p[2]}.${p[1]}.${p[0]}" else it
}
