@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package app.libry.unofficial.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.libry.unofficial.data.Otp
import app.libry.unofficial.ui.Avatar
import app.libry.unofficial.ui.ErrorBox
import app.libry.unofficial.ui.InfoRow
import app.libry.unofficial.ui.Load
import app.libry.unofficial.ui.LocalApi
import app.libry.unofficial.ui.LocalNav
import app.libry.unofficial.ui.Pill
import app.libry.unofficial.ui.Screen
import app.libry.unofficial.ui.errText
import app.libry.unofficial.ui.formatBytes
import app.libry.unofficial.ui.openUrl
import app.libry.unofficial.ui.shortDate
import app.libry.unofficial.ui.toast
import app.libry.unofficial.data.best
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ============================ Вход ============================

@Composable
fun LoginScreen() {
    val api = LocalApi.current
    val nav = LocalNav.current
    val ctx = LocalContext.current
    var tab by remember { mutableStateOf(0) }
    val done = { ctx.toast("Вы вошли в аккаунт"); nav.popBackStack(); Unit }

    Screen("Вход", onBack = { nav.popBackStack() }) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            TabRow(selectedTabIndex = tab) {
                listOf("Пароль", "Код (OTP)", "Соцсети").forEachIndexed { i, t ->
                    Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t) })
                }
            }
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when (tab) {
                    0 -> PasswordLogin(done)
                    1 -> OtpLogin(done)
                    else -> SocialLoginPane(done)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { nav.navigate("forgot") }) { Text("Забыли пароль?") }
                    TextButton(onClick = { nav.navigate("reset") }) { Text("Сброс по токену") }
                }
            }
        }
    }
}

@Composable
private fun PasswordLogin(onDone: () -> Unit) {
    val api = LocalApi.current
    val scope = rememberCoroutineScope()
    var login by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    OutlinedTextField(login, { login = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Логин или e-mail") })
    OutlinedTextField(
        pass, { pass = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Пароль") },
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
    )
    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    Button(
        onClick = {
            busy = true; error = null
            scope.launch {
                try { api.login(login.trim(), pass); onDone() }
                catch (e: CancellationException) { throw e } catch (e: Exception) { error = errText(e) }
                finally { busy = false }
            }
        },
        enabled = !busy && login.isNotBlank() && pass.isNotBlank(),
        modifier = Modifier.fillMaxWidth(),
    ) { Text(if (busy) "Вход…" else "Войти") }
}

@Composable
private fun OtpLogin(onDone: () -> Unit) {
    val api = LocalApi.current
    var otp by remember { mutableStateOf<Otp?>(null) }
    var remaining by remember { mutableStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    var request by remember { mutableStateOf(0) }

    Text("Получите код и подтвердите его в другом клиенте, где вы уже вошли (Профиль → Принять OTP-код).", style = MaterialTheme.typography.bodyMedium)
    val code = otp?.code
    if (code == null) {
        Button(onClick = { request++ }, modifier = Modifier.fillMaxWidth()) { Text("Получить код") }
    } else {
        Text(code, style = MaterialTheme.typography.displayMedium, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        Text("Осталось: ${remaining} с. Ожидаем подтверждения…", style = MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick = { otp = null; request++ }, modifier = Modifier.fillMaxWidth()) { Text("Новый код") }
    }
    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

    // запрос кода
    LaunchedEffect(request) {
        if (request == 0) return@LaunchedEffect
        error = null
        try {
            val r = api.otpGet()
            otp = r.otp
            remaining = r.remainingTime ?: 0
        } catch (e: CancellationException) { throw e } catch (e: Exception) { error = errText(e) }
    }
    // опрос: пока код не подтвердят, otp/login будет возвращать ошибку
    LaunchedEffect(otp?.code) {
        val c = otp?.code?.toIntOrNull() ?: return@LaunchedEffect
        var left = remaining.coerceAtLeast(60)
        while (left > 0) {
            delay(3000); left -= 3; remaining = (remaining - 3).coerceAtLeast(0)
            try { api.otpLogin(c); onDone(); return@LaunchedEffect }
            catch (e: CancellationException) { throw e } catch (_: Exception) { /* ещё не подтверждён */ }
        }
        otp = null
        error = "Время действия кода истекло"
    }
}

@Composable
private fun SocialLoginPane(onDone: () -> Unit) {
    val api = LocalApi.current
    val ctx = LocalContext.current
    var state by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val providers = listOf("vk" to "VK", "google" to "Google", "patreon" to "Patreon", "discord" to "Discord")

    Text("Откроется браузер для авторизации. После входа вернитесь в приложение.")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        providers.forEach { (id, label) ->
            Button(onClick = {
                error = null
                scope.launch {
                    try {
                        val r = api.socialLogin(id)
                        state = r.state
                        ctx.openUrl(r.url)
                    } catch (e: CancellationException) { throw e } catch (e: Exception) { error = errText(e) }
                }
            }) { Text(label) }
        }
    }
    if (state != null) Text("Ожидаем завершения входа…")
    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

    LaunchedEffect(state) {
        val s = state ?: return@LaunchedEffect
        repeat(60) {
            delay(3000)
            try { api.socialAuthenticate(s); onDone(); return@LaunchedEffect }
            catch (e: CancellationException) { throw e } catch (_: Exception) { }
        }
        state = null
        error = "Вход не подтверждён. Попробуйте ещё раз."
    }
}

// ============================ Восстановление пароля ============================

@Composable
fun ForgotScreen() {
    val api = LocalApi.current
    val nav = LocalNav.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var email by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Screen("Восстановление пароля", onBack = { nav.popBackStack() }) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Укажите e-mail аккаунта — на него придёт токен для сброса пароля.")
            OutlinedTextField(email, { email = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("E-mail") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(
                onClick = {
                    busy = true; error = null
                    scope.launch {
                        try { api.passwordForget(email.trim()); ctx.toast("Письмо отправлено"); nav.navigate("reset") }
                        catch (e: CancellationException) { throw e } catch (e: Exception) { error = errText(e) }
                        finally { busy = false }
                    }
                },
                enabled = !busy && email.contains("@"), modifier = Modifier.fillMaxWidth(),
            ) { Text("Отправить") }
        }
    }
}

@Composable
fun ResetScreen() {
    val api = LocalApi.current
    val nav = LocalNav.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var token by remember { mutableStateOf("") }
    var p1 by remember { mutableStateOf("") }
    var p2 by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Screen("Новый пароль", onBack = { nav.popBackStack() }) {
        Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(token, { token = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Токен из письма") })
            OutlinedTextField(p1, { p1 = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Новый пароль") }, visualTransformation = PasswordVisualTransformation())
            OutlinedTextField(p2, { p2 = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Повторите пароль") }, visualTransformation = PasswordVisualTransformation())
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(
                onClick = {
                    busy = true; error = null
                    scope.launch {
                        try { api.passwordReset(token.trim(), p1, p2); ctx.toast("Пароль изменён"); nav.navigate("login") { popUpTo("login") { inclusive = true } } }
                        catch (e: CancellationException) { throw e } catch (e: Exception) { error = errText(e) }
                        finally { busy = false }
                    }
                },
                enabled = !busy && token.isNotBlank() && p1.length >= 6 && p1 == p2, modifier = Modifier.fillMaxWidth(),
            ) { Text("Сменить пароль") }
        }
    }
}

// ============================ OTP: принять код ============================

@Composable
fun OtpAcceptScreen() {
    val api = LocalApi.current
    val nav = LocalNav.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var code by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Screen("Принять OTP-код", onBack = { nav.popBackStack() }) {
        if (!api.isLoggedIn) { ErrorBox("Сначала войдите в аккаунт"); return@Screen }
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Введите 6-значный код, показанный на устройстве, которое хочет войти в ваш аккаунт.")
            OutlinedTextField(code, { code = it.filter(Char::isDigit).take(6) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Код") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(
                onClick = {
                    busy = true; error = null
                    scope.launch {
                        try { api.otpAccept(code.toInt()); ctx.toast("Устройство подтверждено"); nav.popBackStack() }
                        catch (e: CancellationException) { throw e } catch (e: Exception) { error = errText(e) }
                        finally { busy = false }
                    }
                },
                enabled = !busy && code.length == 6, modifier = Modifier.fillMaxWidth(),
            ) { Text("Подтвердить") }
        }
    }
}

// ============================ Профиль ============================

@Composable
fun ProfileScreen() {
    val api = LocalApi.current
    val nav = LocalNav.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    Screen("Профиль", onBack = { nav.popBackStack() }) {
        if (!api.isLoggedIn) {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Вы не вошли в аккаунт")
                Button(onClick = { nav.navigate("login") }, modifier = Modifier.padding(top = 12.dp)) { Text("Войти") }
            }
            return@Screen
        }
        Load(key = api.prefs.token, block = { api.profile() }) { u ->
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Avatar(api.img(u.avatar.best()), 72)
                    Column {
                        Text(u.nickname ?: u.login ?: "—", style = MaterialTheme.typography.titleLarge)
                        Text(u.login.orEmpty(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (u.isAdmin == true) Pill("Администратор")
                    if (u.isBanned == true) Pill("Заблокирован")
                    if (u.isNew == true) Pill("Новый")
                    if (u.isWithAds == true) Pill("С рекламой")
                }
                InfoRow("ID", u.id?.toString())
                InfoRow("E-mail", u.email)
                InfoRow("Регистрация", shortDate(u.createdAt))
                u.torrents?.let { t ->
                    InfoRow("Passkey", t.passkey)
                    InfoRow("Отдано", formatBytes(t.uploaded))
                    InfoRow("Скачано", formatBytes(t.downloaded))
                }
                u.permissions?.takeIf { it.isNotEmpty() }?.let { InfoRow("Права", it.joinToString(", ")) }

                OutlinedButton(onClick = { nav.navigate("history") }, modifier = Modifier.fillMaxWidth()) { Text("История просмотров") }
                OutlinedButton(onClick = { nav.navigate("otp_accept") }, modifier = Modifier.fillMaxWidth()) { Text("Принять OTP-код для другого устройства") }
                Button(
                    onClick = {
                        scope.launch {
                            try { api.logout() } catch (e: CancellationException) { throw e } catch (_: Exception) {}
                            ctx.toast("Вы вышли")
                            nav.popBackStack()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Выйти") }
            }
        }
    }
}
