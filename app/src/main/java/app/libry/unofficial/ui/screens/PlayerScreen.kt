@file:OptIn(ExperimentalMaterial3Api::class, UnstableApi::class)

package app.libry.unofficial.ui.screens

import android.content.pm.ActivityInfo
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import app.libry.unofficial.data.Episode
import app.libry.unofficial.data.Release
import app.libry.unofficial.data.Tc
import app.libry.unofficial.data.VastAd
import app.libry.unofficial.data.title
import app.libry.unofficial.ui.Load
import app.libry.unofficial.ui.LocalApi
import app.libry.unofficial.ui.LocalNav
import app.libry.unofficial.ui.findActivity
import app.libry.unofficial.ui.formatTime
import app.libry.unofficial.ui.openUrl
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun PlayerScreen(releaseId: String, episodeId: String) {
    val api = LocalApi.current
    Load(key = releaseId, block = { api.release(releaseId) }) { rel -> PlayerContent(rel, episodeId) }
}

private val SPEEDS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)

@Composable
private fun PlayerContent(rel: Release, startEpisodeId: String) {
    val api = LocalApi.current
    val nav = LocalNav.current
    val ctx = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    val episodes = remember(rel) {
        (rel.episodes ?: emptyList()).sortedWith(compareBy({ it.sortOrder ?: Int.MAX_VALUE }, { it.ordinal ?: Double.MAX_VALUE }))
    }
    var episodeId by remember { mutableStateOf(startEpisodeId) }
    val current: Episode? = episodes.firstOrNull { it.id == episodeId }
    val index = episodes.indexOfFirst { it.id == episodeId }

    val exo = remember { ExoPlayer.Builder(ctx).build() }

    var quality by remember { mutableStateOf(api.prefs.quality) }
    var activeQuality by remember { mutableStateOf<String?>(null) }
    var speed by remember { mutableFloatStateOf(1f) }
    var showControls by remember { mutableStateOf(true) }
    var playing by remember { mutableStateOf(false) }
    var buffering by remember { mutableStateOf(true) }
    var pos by remember { mutableLongStateOf(0L) }
    var dur by remember { mutableLongStateOf(0L) }
    var seeking by remember { mutableStateOf(false) }
    var seekValue by remember { mutableFloatStateOf(0f) }
    var error by remember { mutableStateOf<String?>(null) }
    var adMode by remember { mutableStateOf(false) }
    var ad by remember { mutableStateOf<VastAd?>(null) }
    var adShown by remember { mutableStateOf(false) }
    var showEpisodes by remember { mutableStateOf(false) }
    var qualityMenu by remember { mutableStateOf(false) }
    var speedMenu by remember { mutableStateOf(false) }
    var landscape by remember { mutableStateOf(true) }
    var triedFallback by remember { mutableStateOf(false) }

    // -------- выбор потока --------
    fun streamFor(ep: Episode, q: String): Pair<String, String>? {
        val all = listOf("1080" to ep.hls1080, "720" to ep.hls720, "480" to ep.hls480)
            .filter { !it.second.isNullOrBlank() }
        if (all.isEmpty()) return null
        val want = q.toIntOrNull() ?: 1080
        val pick = all.firstOrNull { it.first == q } ?: all.firstOrNull { it.first.toInt() <= want } ?: all.last()
        val url = api.url(pick.second) ?: return null
        return pick.first to url
    }

    // -------- прогресс --------
    fun saveProgress(forceWatched: Boolean = false) {
        val ep = current ?: return
        val id = ep.id ?: return
        if (adMode) return
        val position = exo.currentPosition / 1000.0
        val d = exo.duration.let { if (it > 0) it / 1000.0 else (ep.duration ?: 0.0) }
        if (position <= 1.0 && !forceWatched) return
        val pastEnding = ep.ending?.start?.let { it > 0 && position >= it } == true
        val watched = forceWatched || (d > 0 && position >= d * 0.92) || pastEnding
        val t = if (forceWatched && d > 0) d else position
        api.prefs.saveLocalTimecode(id, t, watched)
        if (api.isLoggedIn) api.appScope.launch {
            try { api.saveTimecodes(listOf(Tc(id, t, watched))) } catch (_: Exception) {}
        }
    }

    val currentState = rememberUpdatedState(current)
    val saveCb = rememberUpdatedState<() -> Unit>({ saveProgress() })

    suspend fun resumeMs(ep: Episode): Long {
        val id = ep.id ?: return 0L
        var tc: Tc? = api.prefs.localTimecode(id)
        if (api.isLoggedIn) {
            try {
                val v = api.episodeTimecode(id)
                tc = Tc(id, v.time ?: 0.0, v.isWatched ?: false)
            } catch (e: CancellationException) { throw e } catch (_: Exception) {}
        }
        val d = ep.duration ?: 0.0
        val t = tc ?: return 0L
        return if (!t.watched && t.time > 5.0 && (d == 0.0 || t.time < d - 20.0)) (t.time * 1000).toLong() else 0L
    }

    fun loadContent(ep: Episode, startMs: Long) {
        error = null
        val s = streamFor(ep, quality)
        if (s == null) {
            error = "Для этой серии нет потока. Попробуйте Rutube/YouTube или внешний плеер."
            return
        }
        activeQuality = s.first
        val item = MediaItem.Builder().setUri(s.second).setMimeType(MimeTypes.APPLICATION_M3U8).build()
        exo.setMediaItem(item, startMs)
        exo.setPlaybackSpeed(speed)
        exo.prepare()
        exo.playWhenReady = true
    }

    suspend fun startEpisode(ep: Episode) {
        triedFallback = false
        if (api.prefs.showAds && !adShown) {
            adShown = true
            val a = try { api.loadVastAd() } catch (e: CancellationException) { throw e } catch (_: Exception) { null }
            if (a != null) {
                ad = a
                adMode = true
                exo.setMediaItem(MediaItem.fromUri(a.mediaUrl), 0L)
                exo.setPlaybackSpeed(1f)
                exo.prepare()
                exo.playWhenReady = true
                a.impressions.forEach { api.ping(it) }
                return
            }
        }
        loadContent(ep, resumeMs(ep))
    }

    fun finishAd() {
        adMode = false
        ad = null
        val ep = current ?: return
        scope.launch { loadContent(ep, resumeMs(ep)) }
    }

    fun switchEpisode(id: String?) {
        if (id == null || id == episodeId) return
        saveProgress()
        episodeId = id
    }

    fun onEnded() {
        if (adMode) { finishAd(); return }
        saveProgress(forceWatched = true)
        val nid = episodes.getOrNull(index + 1)?.id
        if (api.prefs.autoNext && nid != null) episodeId = nid else showControls = true
    }

    val endedCb = rememberUpdatedState { onEnded() }
    val errorCb = rememberUpdatedState<(PlaybackException) -> Unit>({ e ->
        val ep = current
        val lower = ep?.let { x ->
            listOf("1080" to x.hls1080, "720" to x.hls720, "480" to x.hls480).filter { !it.second.isNullOrBlank() && it.first.toInt() < (activeQuality?.toIntOrNull() ?: 0) }
        }.orEmpty()
        if (!adMode && !triedFallback && lower.isNotEmpty() && ep != null) {
            triedFallback = true
            val pos0 = exo.currentPosition
            quality = lower.first().first
            loadContent(ep, pos0)
        } else if (adMode) {
            finishAd()
        } else {
            error = "Ошибка воспроизведения (${e.errorCodeName})"
        }
    })

    // -------- слушатель плеера --------
    DisposableEffect(exo) {
        val l = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                buffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_ENDED) endedCb.value()
            }
            override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
            override fun onPlayerError(e: PlaybackException) { errorCb.value(e) }
        }
        exo.addListener(l)
        onDispose {
            exo.removeListener(l)
        }
    }

    // сохранение и пауза при уходе в фон
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_PAUSE) { saveCb.value(); exo.pause() }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    // полноэкранный режим, ориентация, экран не гаснет
    DisposableEffect(Unit) {
        val act = ctx.findActivity()
        val window = act?.window
        val ctrl = window?.let { WindowCompat.getInsetsController(it, view) }
        ctrl?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        ctrl?.hide(WindowInsetsCompat.Type.systemBars())
        act?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        view.keepScreenOn = true
        onDispose {
            saveCb.value()
            exo.release()
            ctrl?.show(WindowInsetsCompat.Type.systemBars())
            act?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            view.keepScreenOn = false
        }
    }

    // запуск серии при смене episodeId
    LaunchedEffect(episodeId) {
        val ep = current ?: return@LaunchedEffect
        startEpisode(ep)
    }

    // тикер позиции, авто-пропуск опенинга
    LaunchedEffect(exo) {
        while (true) {
            if (!seeking) pos = exo.currentPosition.coerceAtLeast(0L)
            dur = exo.duration.let { if (it > 0) it else 0L }
            val ep = currentState.value
            if (api.prefs.autoSkip && !adMode && ep != null) {
                val op = ep.opening
                val p = exo.currentPosition / 1000.0
                if (op?.start != null && op.stop != null && p >= op.start && p < op.stop - 1) exo.seekTo((op.stop * 1000).toLong())
            }
            delay(300)
        }
    }

    // периодическое сохранение прогресса (лимит API — 120 записей в минуту)
    LaunchedEffect(exo) {
        while (true) {
            delay(15_000)
            if (exo.isPlaying) saveCb.value()
        }
    }

    // автоскрытие управления
    LaunchedEffect(showControls, playing, qualityMenu, speedMenu) {
        if (showControls && playing && !qualityMenu && !speedMenu) {
            delay(3500)
            showControls = false
        }
    }

    // ============== UI ==============
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { c -> PlayerView(c).apply { useController = false; player = exo } },
            modifier = Modifier.fillMaxSize(),
        )

        // жесты: тап — показать/скрыть, двойной тап слева/справа — перемотка на 10 с
        Box(
            Modifier.fillMaxSize().pointerInput(adMode) {
                detectTapGestures(
                    onTap = { showControls = !showControls },
                    onDoubleTap = { offset ->
                        if (!adMode) {
                            val left = offset.x < size.width / 2
                            val target = (exo.currentPosition + if (left) -10_000 else 10_000).coerceAtLeast(0L)
                            exo.seekTo(target)
                        }
                    },
                )
            },
        )

        if (buffering && error == null) CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White)

        val op = current?.opening
        val ed = current?.ending
        val sec = pos / 1000.0
        val inOpening = !adMode && op?.start != null && op.stop != null && sec >= op.start && sec < op.stop
        val inEnding = !adMode && ed?.start != null && ed.stop != null && sec >= ed.start && sec < ed.stop

        // кнопки пропуска
        if (inOpening || inEnding) {
            FilledTonalButton(
                onClick = {
                    val stop = if (inOpening) op?.stop else ed?.stop
                    if (stop != null) exo.seekTo((stop * 1000).toLong())
                },
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 24.dp, bottom = 96.dp),
            ) { Text(if (inOpening) "Пропустить опенинг" else "Пропустить титры") }
        }

        // реклама
        val adNow = ad
        if (adMode && adNow != null) {
            val skipAt = adNow.skipAfterSec ?: 5
            Column(Modifier.align(Alignment.TopEnd).padding(16.dp), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Реклама", color = Color.White, modifier = Modifier.background(Color(0x99000000), RoundedCornerShape(6.dp)).padding(6.dp))
                if (adNow.clickUrl != null) OutlinedButton(onClick = { ctx.openUrl(adNow.clickUrl) }) { Text("Подробнее", color = Color.White) }
                if (sec >= skipAt) Button(onClick = { exo.pause(); finishAd() }) { Text("Пропустить рекламу") }
                else Text("Пропуск через ${(skipAt - sec).toInt() + 1} с", color = Color.White, modifier = Modifier.background(Color(0x99000000), RoundedCornerShape(6.dp)).padding(6.dp))
            }
        }

        // ошибка
        val err = error
        if (err != null) Column(
            Modifier.align(Alignment.Center).background(Color(0xCC000000), RoundedCornerShape(12.dp)).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(err, color = Color.White)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { current?.let { e -> scope.launch { startEpisode(e) } } }) { Text("Повторить") }
                current?.rutubeId?.takeIf { it.isNotBlank() }?.let { r -> OutlinedButton(onClick = { ctx.openUrl("https://rutube.ru/video/$r/") }) { Text("Rutube") } }
                current?.youtubeId?.takeIf { it.isNotBlank() }?.let { y -> OutlinedButton(onClick = { ctx.openUrl("https://www.youtube.com/watch?v=$y") }) { Text("YouTube") } }
                rel.externalPlayer?.takeIf { it.isNotBlank() }?.let { x -> OutlinedButton(onClick = { ctx.openUrl(api.url(x)) }) { Text("Браузер") } }
            }
        }

        // панель управления
        AnimatedVisibility(visible = showControls && !adMode, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().background(Color(0x66000000))) {
                // верх
                Row(
                    Modifier.fillMaxWidth().align(Alignment.TopCenter).padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад", tint = Color.White) }
                    Column(Modifier.weight(1f)) {
                        Text(rel.title(), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                        Text(current?.title().orEmpty(), color = Color(0xCCFFFFFF), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                    }
                    Box {
                        TextButton(onClick = { speedMenu = true }) {
                            Icon(Icons.Filled.Speed, null, tint = Color.White)
                            Text(" ${speed}x", color = Color.White)
                        }
                        DropdownMenu(expanded = speedMenu, onDismissRequest = { speedMenu = false }) {
                            SPEEDS.forEach { s ->
                                DropdownMenuItem(text = { Text("${s}x") }, onClick = { speed = s; exo.setPlaybackSpeed(s); speedMenu = false })
                            }
                        }
                    }
                    Box {
                        TextButton(onClick = { qualityMenu = true }) {
                            Icon(Icons.Filled.Settings, null, tint = Color.White)
                            Text(" ${activeQuality ?: quality}p", color = Color.White)
                        }
                        DropdownMenu(expanded = qualityMenu, onDismissRequest = { qualityMenu = false }) {
                            listOf("1080", "720", "480").forEach { q ->
                                val avail = when (q) { "1080" -> current?.hls1080; "720" -> current?.hls720; else -> current?.hls480 }
                                if (!avail.isNullOrBlank()) DropdownMenuItem(
                                    text = { Text("${q}p" + if (q == activeQuality) "  ✓" else "") },
                                    onClick = {
                                        qualityMenu = false
                                        quality = q
                                        api.prefs.updateQuality(q)
                                        val ep = current
                                        if (ep != null && !adMode) loadContent(ep, exo.currentPosition)
                                    },
                                )
                            }
                        }
                    }
                    IconButton(onClick = { showEpisodes = true }) { Icon(Icons.Filled.VideoLibrary, "Серии", tint = Color.White) }
                }

                // центр
                Row(Modifier.align(Alignment.Center), horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { switchEpisode(episodes.getOrNull(index - 1)?.id) }, enabled = index > 0) {
                        Icon(Icons.Filled.SkipPrevious, "Предыдущая", tint = Color.White, modifier = Modifier.size(36.dp))
                    }
                    IconButton(onClick = { exo.seekTo((exo.currentPosition - 10_000).coerceAtLeast(0L)) }) {
                        Icon(Icons.Filled.Replay10, "−10 с", tint = Color.White, modifier = Modifier.size(36.dp))
                    }
                    IconButton(onClick = { if (exo.isPlaying) exo.pause() else exo.play() }, modifier = Modifier.size(64.dp)) {
                        Icon(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, "Пауза/Играть", tint = Color.White, modifier = Modifier.size(52.dp))
                    }
                    IconButton(onClick = { exo.seekTo(exo.currentPosition + 10_000) }) {
                        Icon(Icons.Filled.Forward10, "+10 с", tint = Color.White, modifier = Modifier.size(36.dp))
                    }
                    IconButton(onClick = { switchEpisode(episodes.getOrNull(index + 1)?.id) }, enabled = index in 0 until episodes.size - 1) {
                        Icon(Icons.Filled.SkipNext, "Следующая", tint = Color.White, modifier = Modifier.size(36.dp))
                    }
                }

                // низ
                Row(
                    Modifier.fillMaxWidth().align(Alignment.BottomCenter).padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(formatTime((if (seeking) seekValue.toLong() else pos) / 1000), color = Color.White, style = MaterialTheme.typography.labelMedium)
                    Slider(
                        value = if (seeking) seekValue else pos.toFloat(),
                        onValueChange = { seeking = true; seekValue = it },
                        onValueChangeFinished = { exo.seekTo(seekValue.toLong()); pos = seekValue.toLong(); seeking = false },
                        valueRange = 0f..(if (dur > 0) dur.toFloat() else 1f),
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                    )
                    Text(formatTime(dur / 1000), color = Color.White, style = MaterialTheme.typography.labelMedium)
                    IconButton(onClick = {
                        landscape = !landscape
                        ctx.findActivity()?.requestedOrientation =
                            if (landscape) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                    }) { Icon(Icons.Filled.ScreenRotation, "Ориентация", tint = Color.White) }
                }
            }
        }
    }

    if (showEpisodes) AlertDialog(
        onDismissRequest = { showEpisodes = false },
        title = { Text("Серии") },
        text = {
            LazyColumn {
                items(episodes.size) { i ->
                    val e = episodes[i]
                    Text(
                        e.title(),
                        modifier = Modifier.fillMaxWidth().clickable { showEpisodes = false; switchEpisode(e.id) }.padding(vertical = 10.dp),
                        color = if (e.id == episodeId) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = { showEpisodes = false }) { Text("Закрыть") } },
    )
}
