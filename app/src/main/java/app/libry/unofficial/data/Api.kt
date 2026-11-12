package app.libry.unofficial.data

import android.util.Xml
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNamingStrategy
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import java.util.concurrent.TimeUnit

class ApiException(val code: Int, message: String, val body: String? = null) : Exception(message)

@OptIn(ExperimentalSerializationApi::class)
val AppJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
    explicitNulls = false
    namingStrategy = JsonNamingStrategy.SnakeCase
}

/**
 * Клиент AniLiberty API v1 (spec: /api/v1). Здесь реализованы ВСЕ эндпоинты документации.
 */
class Api(val prefs: Prefs) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(40, TimeUnit.SECONDS)
        .writeTimeout(40, TimeUnit.SECONDS)
        .build()

    private val jsonType = "application/json; charset=utf-8".toMediaType()

    val isLoggedIn: Boolean get() = !prefs.token.isNullOrBlank()

    /** Относительные ссылки (картинки) -> абсолютные. */
    fun img(path: String?): String? {
        if (path.isNullOrBlank()) return null
        return if (path.startsWith("http://") || path.startsWith("https://")) path
        else if (path.startsWith("//")) "https:$path"
        else prefs.origin + (if (path.startsWith("/")) path else "/$path")
    }

    fun url(path: String?): String? = img(path)

    // ======================= Ядро =======================

    private fun buildUrl(path: String, query: List<Pair<String, String>>): String {
        val b = (prefs.baseUrl.trimEnd('/') + path).toHttpUrl().newBuilder()
        for ((k, v) in query) b.addQueryParameter(k, v)
        return b.build().toString()
    }

    suspend fun raw(
        method: String,
        path: String,
        query: List<Pair<String, String>> = emptyList(),
        body: JsonElement? = null,
        absoluteUrl: String? = null,
    ): String = withContext(Dispatchers.IO) {
        val url = absoluteUrl ?: buildUrl(path, query)
        val rb = Request.Builder().url(url).header("Accept", "application/json, application/xml;q=0.9, */*;q=0.8")
        // токен отправляем только на сам API, а не на сторонние (рекламные) адреса
        if (absoluteUrl == null) {
            prefs.token?.takeIf { it.isNotBlank() }?.let { rb.header("Authorization", "Bearer $it") }
        }
        val reqBody = when {
            body != null -> AppJson.encodeToString(JsonElement.serializer(), body).toRequestBody(jsonType)
            method == "POST" || method == "PUT" || method == "PATCH" -> "".toRequestBody(jsonType)
            method == "DELETE" -> null
            else -> null
        }
        rb.method(method, reqBody)
        http.newCall(rb.build()).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw ApiException(resp.code, errorMessage(resp.code, text), text)
            text
        }
    }

    private fun errorMessage(code: Int, body: String): String {
        val extra = try {
            val o = AppJson.parseToJsonElement(body) as? JsonObject
            val errs = o?.get("errors") as? JsonObject
            errs?.values?.joinToString("; ") { v ->
                if (v is JsonArray) v.joinToString(", ") { (it as? JsonPrimitive)?.contentOrNull ?: it.toString() }
                else (v as? JsonPrimitive)?.contentOrNull ?: v.toString()
            } ?: (o?.get("message") as? JsonPrimitive)?.contentOrNull
        } catch (_: Exception) { null }
        val base = when (code) {
            401 -> "Не авторизован или неверные данные"
            403 -> "Необходимо авторизоваться"
            404 -> "Не найдено"
            422 -> "Некорректные данные"
            429 -> "Слишком много запросов"
            else -> "Ошибка сервера ($code)"
        }
        return if (extra.isNullOrBlank()) base else "$base: $extra"
    }

    private suspend inline fun <reified T> get(path: String, query: List<Pair<String, String>> = emptyList()): T =
        AppJson.decodeFromString(raw("GET", path, query))

    private suspend inline fun <reified T> send(method: String, path: String, body: JsonElement?, query: List<Pair<String, String>> = emptyList()): T =
        AppJson.decodeFromString(raw(method, path, query, body))

    private fun q(vararg p: Pair<String, Any?>): List<Pair<String, String>> = p.mapNotNull { (k, v) ->
        when (v) {
            null -> null
            is Collection<*> -> if (v.isEmpty()) null else k to v.joinToString(",")
            is String -> if (v.isBlank()) null else k to v
            else -> k to v.toString()
        }
    }

    private fun page(page: Int?, limit: Int?) = q("page" to page, "limit" to limit)

    // ======================= Аккаунты: OTP =======================

    suspend fun otpGet(deviceId: String = prefs.deviceId): OtpGet =
        send("POST", "/accounts/otp/get", buildJsonObject { put("device_id", deviceId) })

    suspend fun otpAccept(code: Int) {
        raw("POST", "/accounts/otp/accept", body = buildJsonObject { put("code", code) })
    }

    suspend fun otpLogin(code: Int, deviceId: String = prefs.deviceId): String {
        val t: TokenResp = send("POST", "/accounts/otp/login", buildJsonObject { put("code", code); put("device_id", deviceId) })
        val token = t.token ?: throw ApiException(0, "Сервер не вернул токен")
        prefs.setToken(token)
        return token
    }

    // ======================= Аккаунты: авторизация =======================

    suspend fun login(login: String, password: String): String {
        val t: TokenResp = send("POST", "/accounts/users/auth/login", buildJsonObject { put("login", login); put("password", password) })
        val token = t.token ?: throw ApiException(0, "Сервер не вернул токен")
        prefs.setToken(token)
        return token
    }

    /** provider: vk | google | patreon | discord */
    suspend fun socialLogin(provider: String): SocialLogin = get("/accounts/users/auth/social/$provider/login")

    suspend fun socialAuthenticate(state: String): String {
        val t: TokenResp = get("/accounts/users/auth/social/authenticate", q("state" to state))
        val token = t.token ?: throw ApiException(0, "Сервер не вернул токен")
        prefs.setToken(token)
        return token
    }

    suspend fun logout() {
        try { raw("POST", "/accounts/users/auth/logout") } finally { prefs.setToken(null) }
    }

    suspend fun passwordForget(email: String) {
        raw("POST", "/accounts/users/auth/password/forget", body = buildJsonObject { put("email", email) })
    }

    suspend fun passwordReset(token: String, password: String, confirmation: String) {
        raw("POST", "/accounts/users/auth/password/reset", body = buildJsonObject {
            put("token", token); put("password", password); put("password_confirmation", confirmation)
        })
    }

    // ======================= Мои коллекции =======================

    suspend fun collectionRefAgeRatings(): List<RefItem> = get("/accounts/users/me/collections/references/age-ratings")
    suspend fun collectionRefGenres(): List<RefItem> = get("/accounts/users/me/collections/references/genres")
    suspend fun collectionRefTypes(): List<RefItem> = get("/accounts/users/me/collections/references/types")
    suspend fun collectionRefYears(): List<Int> = get("/accounts/users/me/collections/references/years")

    /** releaseId -> тип коллекции. Формат ответа в документации неоднозначен, разбираем терпимо. */
    suspend fun collectionIds(): Map<Int, String> {
        val el = AppJson.parseToJsonElement(raw("GET", "/accounts/users/me/collections/ids"))
        val res = LinkedHashMap<Int, String>()
        var pendingId: Int? = null
        var pendingType: String? = null
        fun flush() {
            val i = pendingId; val t = pendingType
            if (i != null && t != null) res[i] = t
            pendingId = null; pendingType = null
        }
        fun take(p: JsonPrimitive) {
            val d = p.doubleOrNull
            if (d != null && !p.isString) { if (pendingId != null) flush(); pendingId = d.toInt() }
            else { val s = p.contentOrNull ?: return; if (s.toIntOrNull() != null) { if (pendingId != null) flush(); pendingId = s.toInt() } else { pendingType = s; flush() } }
        }
        val arr = el as? JsonArray ?: return res
        for (e in arr) {
            when (e) {
                is JsonArray -> { pendingId = null; pendingType = null; e.forEach { (it as? JsonPrimitive)?.let(::take) }; flush() }
                is JsonObject -> {
                    val id = (e["release_id"] as? JsonPrimitive)?.doubleOrNull?.toInt() ?: (e["id"] as? JsonPrimitive)?.doubleOrNull?.toInt()
                    val t = (e["type_of_collection"] as? JsonPrimitive)?.contentOrNull ?: (e["type"] as? JsonPrimitive)?.contentOrNull
                    if (id != null && t != null) res[id] = t
                }
                is JsonPrimitive -> take(e)
                else -> {}
            }
        }
        flush()
        return res
    }

    suspend fun collectionReleasesGet(type: String, page: Int, limit: Int = 24, f: UserFilter = UserFilter()): Paged<Release> =
        get(
            "/accounts/users/me/collections/releases",
            page(page, limit) + q(
                "type_of_collection" to type,
                "f[genres]" to f.genres, "f[types]" to f.types, "f[years]" to f.years,
                "f[search]" to f.search, "f[age_ratings]" to f.ageRatings,
            ),
        )

    suspend fun collectionReleasesPost(type: String, page: Int, limit: Int = 24, f: UserFilter = UserFilter()): Paged<Release> =
        send("POST", "/accounts/users/me/collections/releases", buildJsonObject {
            put("page", page); put("limit", limit); put("type_of_collection", type)
            putJsonObject("f") { userFilterJson(f, withSorting = false) }
        })

    suspend fun collectionReleases(type: String, page: Int, f: UserFilter = UserFilter()): Paged<Release> =
        if (prefs.usePost) collectionReleasesPost(type, page, 24, f) else collectionReleasesGet(type, page, 24, f)

    suspend fun collectionAdd(releaseId: Int, type: String) {
        raw("POST", "/accounts/users/me/collections", body = buildJsonArray {
            add(buildJsonObject { put("release_id", releaseId); put("type_of_collection", type) })
        })
    }

    suspend fun collectionDelete(releaseId: Int) {
        raw("DELETE", "/accounts/users/me/collections", body = buildJsonArray {
            add(buildJsonObject { put("release_id", releaseId) })
        })
    }

    private fun kotlinx.serialization.json.JsonObjectBuilder.userFilterJson(f: UserFilter, withSorting: Boolean) {
        if (f.genres.isNotEmpty()) put("genres", f.genres.joinToString(","))
        if (f.types.isNotEmpty()) putJsonArray("types") { f.types.forEach { add(JsonPrimitive(it)) } }
        if (f.years.isNotEmpty()) put("years", f.years.joinToString(","))
        if (f.search.isNotBlank()) put("search", f.search)
        if (withSorting && !f.sorting.isNullOrBlank()) put("sorting", f.sorting)
        if (f.ageRatings.isNotEmpty()) putJsonArray("age_ratings") { f.ageRatings.forEach { add(JsonPrimitive(it)) } }
    }

    // ======================= Моё избранное =======================

    suspend fun favoriteRefAgeRatings(): List<RefItem> = get("/accounts/users/me/favorites/references/age-ratings")
    suspend fun favoriteRefGenres(): List<RefItem> = get("/accounts/users/me/favorites/references/genres")
    suspend fun favoriteRefSorting(): List<RefItem> = get("/accounts/users/me/favorites/references/sorting")
    suspend fun favoriteRefTypes(): List<RefItem> = get("/accounts/users/me/favorites/references/types")
    suspend fun favoriteRefYears(): List<Int> = get("/accounts/users/me/favorites/references/years")

    suspend fun favoriteIds(): List<Int> {
        val el = AppJson.parseToJsonElement(raw("GET", "/accounts/users/me/favorites/ids"))
        return (el as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.doubleOrNull?.toInt() } ?: emptyList()
    }

    suspend fun favoriteReleasesGet(page: Int, limit: Int = 24, f: UserFilter = UserFilter()): Paged<Release> =
        get(
            "/accounts/users/me/favorites/releases",
            page(page, limit) + q(
                "f[years]" to f.years, "f[types]" to f.types, "f[genres]" to f.genres,
                "f[search]" to f.search, "f[sorting]" to f.sorting, "f[age_ratings]" to f.ageRatings,
            ),
        )

    suspend fun favoriteReleasesPost(page: Int, limit: Int = 24, f: UserFilter = UserFilter()): Paged<Release> =
        send("POST", "/accounts/users/me/favorites/releases", buildJsonObject {
            put("page", page); put("limit", limit)
            putJsonObject("f") { userFilterJson(f, withSorting = true) }
        })

    suspend fun favoriteReleases(page: Int, f: UserFilter = UserFilter()): Paged<Release> =
        if (prefs.usePost) favoriteReleasesPost(page, 24, f) else favoriteReleasesGet(page, 24, f)

    suspend fun favoriteAdd(releaseId: Int) {
        raw("POST", "/accounts/users/me/favorites", body = buildJsonArray { add(buildJsonObject { put("release_id", releaseId) }) })
    }

    suspend fun favoriteDelete(releaseId: Int) {
        raw("DELETE", "/accounts/users/me/favorites", body = buildJsonArray { add(buildJsonObject { put("release_id", releaseId) }) })
    }

    // ======================= Профиль / просмотры =======================

    suspend fun profile(): User = get("/accounts/users/me/profile")

    suspend fun viewHistory(page: Int, limit: Int = 24, period: String? = null, status: String? = null): Paged<View> =
        get("/accounts/users/me/views/history", page(page, limit) + q("f[period]" to period, "f[status]" to status))

    /** Все таймкоды пользователя; since — ISO date-time. Формат в документации неоднозначен — разбираем терпимо. */
    suspend fun myTimecodes(since: String? = null): List<Tc> {
        val el = AppJson.parseToJsonElement(raw("GET", "/accounts/users/me/views/timecodes", q("since" to since)))
        val res = ArrayList<Tc>()
        var id: String? = null; var time: Double? = null; var watched: Boolean? = null
        fun flush() {
            val i = id
            if (i != null) res.add(Tc(i, time ?: 0.0, watched ?: false))
            id = null; time = null; watched = null
        }
        fun take(p: JsonPrimitive) {
            val b = if (p.isString) null else p.booleanOrNull
            val d = if (p.isString) null else p.doubleOrNull
            when {
                b != null -> watched = b
                d != null -> time = d
                else -> { if (id != null) flush(); id = p.contentOrNull }
            }
        }
        val arr = el as? JsonArray ?: return res
        for (e in arr) {
            when (e) {
                is JsonArray -> { flush(); e.forEach { (it as? JsonPrimitive)?.let(::take) }; flush() }
                is JsonObject -> {
                    flush()
                    id = (e["release_episode_id"] as? JsonPrimitive)?.contentOrNull
                    time = (e["time"] as? JsonPrimitive)?.doubleOrNull
                    watched = (e["is_watched"] as? JsonPrimitive)?.booleanOrNull
                    flush()
                }
                is JsonPrimitive -> take(e)
                else -> {}
            }
        }
        flush()
        return res
    }

    suspend fun saveTimecodes(items: List<Tc>) {
        if (items.isEmpty()) return
        raw("POST", "/accounts/users/me/views/timecodes", body = buildJsonArray {
            items.forEach { add(buildJsonObject { put("time", it.time); put("is_watched", it.watched); put("release_episode_id", it.episodeId) }) }
        })
    }

    suspend fun deleteTimecodes(episodeIds: List<String>) {
        if (episodeIds.isEmpty()) return
        raw("DELETE", "/accounts/users/me/views/timecodes", body = buildJsonArray {
            episodeIds.forEach { add(buildJsonObject { put("release_episode_id", it) }) }
        })
    }

    // ======================= Реклама (VAST) =======================

    suspend fun vasts(): List<Vast> = get("/media/vasts")

    suspend fun vastManifestXml(): String = raw("GET", "/media/manifest.xml")

    /** Разбирает VAST (идёт по wrapper-цепочке до 3 уровней) и возвращает ролик для пре-ролла. */
    suspend fun loadVastAd(): VastAd? {
        var xml = try { vastManifestXml() } catch (_: Exception) { return null }
        val impressions = ArrayList<String>()
        var click: String? = null
        repeat(4) {
            val p = parseVast(xml)
            impressions += p.impressions
            if (click == null) click = p.click
            if (p.mediaUrl != null) return VastAd(p.mediaUrl, p.skipSec, click, impressions)
            val next = p.wrapperUrl ?: return null
            xml = try { raw("GET", "", absoluteUrl = next) } catch (_: Exception) { return null }
        }
        return null
    }

    private class VastParsed(
        val mediaUrl: String?, val wrapperUrl: String?, val click: String?, val skipSec: Int?, val impressions: List<String>,
    )

    private fun parseVast(xml: String): VastParsed {
        val parser = Xml.newPullParser()
        parser.setInput(StringReader(xml))
        var media: String? = null; var best = -1
        var wrapper: String? = null; var click: String? = null; var skip: Int? = null
        val imps = ArrayList<String>()
        var ev = parser.eventType
        while (ev != XmlPullParser.END_DOCUMENT) {
            if (ev == XmlPullParser.START_TAG) {
                when (parser.name) {
                    "Linear" -> parser.getAttributeValue(null, "skipoffset")?.let { skip = parseOffset(it) }
                    "MediaFile" -> {
                        val type = parser.getAttributeValue(null, "type").orEmpty()
                        val h = parser.getAttributeValue(null, "height")?.toIntOrNull() ?: 0
                        val text = readText(parser)
                        if (text.isNotBlank() && (type.contains("mp4") || type.contains("mpegurl") || type.isEmpty()) && h >= best) {
                            media = text; best = h
                        }
                    }
                    "VASTAdTagURI" -> wrapper = readText(parser)
                    "ClickThrough" -> click = readText(parser)
                    "Impression" -> readText(parser).takeIf { it.isNotBlank() }?.let { imps.add(it) }
                }
            }
            ev = parser.next()
        }
        return VastParsed(media, wrapper, click, skip, imps)
    }

    private fun readText(p: XmlPullParser): String {
        val sb = StringBuilder()
        var ev = p.next()
        while (ev != XmlPullParser.END_TAG && ev != XmlPullParser.END_DOCUMENT) {
            if (ev == XmlPullParser.TEXT || ev == XmlPullParser.CDSECT) sb.append(p.text)
            ev = p.next()
        }
        return sb.toString().trim()
    }

    private fun parseOffset(s: String): Int? {
        if (s.endsWith("%")) return null
        val parts = s.split(':')
        if (parts.size != 3) return null
        val h = parts[0].toIntOrNull() ?: return null
        val m = parts[1].toIntOrNull() ?: return null
        val sec = parts[2].toDoubleOrNull() ?: return null
        return h * 3600 + m * 60 + sec.toInt()
    }

    /** Пинг трекера (impression и т.п.), ошибки игнорируем. */
    fun ping(url: String) {
        appScope.launchSafe { raw("GET", "", absoluteUrl = url) }
    }

    private fun CoroutineScope.launchSafe(block: suspend () -> Unit) {
        launch { try { block() } catch (_: Exception) {} }
    }

    // ======================= Каталог =======================

    suspend fun catalogReleasesGet(page: Int, limit: Int = 24, f: CatalogFilter = CatalogFilter()): Paged<Release> =
        get(
            "/anime/catalog/releases",
            page(page, limit) + q(
                "f[genres]" to f.genres, "f[types]" to f.types, "f[seasons]" to f.seasons,
                "f[years][from_year]" to f.fromYear, "f[years][to_year]" to f.toYear,
                "f[search]" to f.search, "f[sorting]" to f.sorting, "f[age_ratings]" to f.ageRatings,
                "f[publish_statuses]" to f.publishStatuses, "f[production_statuses]" to f.productionStatuses,
            ),
        )

    suspend fun catalogReleasesPost(page: Int, limit: Int = 24, f: CatalogFilter = CatalogFilter()): Paged<Release> =
        send("POST", "/anime/catalog/releases", buildJsonObject {
            put("page", page); put("limit", limit)
            putJsonObject("f") {
                if (f.genres.isNotEmpty()) putJsonArray("genres") { f.genres.forEach { add(JsonPrimitive(it)) } }
                if (f.types.isNotEmpty()) putJsonArray("types") { f.types.forEach { add(JsonPrimitive(it)) } }
                if (f.seasons.isNotEmpty()) putJsonArray("seasons") { f.seasons.forEach { add(JsonPrimitive(it)) } }
                if (f.fromYear != null || f.toYear != null) putJsonObject("years") {
                    f.fromYear?.let { put("from_year", it) }
                    f.toYear?.let { put("to_year", it) }
                }
                if (f.search.isNotBlank()) put("search", f.search)
                if (!f.sorting.isNullOrBlank()) put("sorting", f.sorting)
                if (f.ageRatings.isNotEmpty()) putJsonArray("age_ratings") { f.ageRatings.forEach { add(JsonPrimitive(it)) } }
                if (f.publishStatuses.isNotEmpty()) putJsonArray("publish_statuses") { f.publishStatuses.forEach { add(JsonPrimitive(it)) } }
                if (f.productionStatuses.isNotEmpty()) putJsonArray("production_statuses") { f.productionStatuses.forEach { add(JsonPrimitive(it)) } }
            }
        })

    suspend fun catalogReleases(page: Int, f: CatalogFilter = CatalogFilter()): Paged<Release> =
        if (prefs.usePost) catalogReleasesPost(page, 24, f) else catalogReleasesGet(page, 24, f)

    suspend fun catalogRefAgeRatings(): List<RefItem> = get("/anime/catalog/references/age-ratings")
    suspend fun catalogRefGenres(): List<RefItem> = get("/anime/catalog/references/genres")
    suspend fun catalogRefProductionStatuses(): List<RefItem> = get("/anime/catalog/references/production-statuses")
    suspend fun catalogRefPublishStatuses(): List<RefItem> = get("/anime/catalog/references/publish-statuses")
    suspend fun catalogRefSeasons(): List<RefItem> = get("/anime/catalog/references/seasons")
    suspend fun catalogRefSorting(): List<RefItem> = get("/anime/catalog/references/sorting")
    suspend fun catalogRefTypes(): List<RefItem> = get("/anime/catalog/references/types")
    suspend fun catalogRefYears(): List<Int> = get("/anime/catalog/references/years")

    // ======================= Франшизы =======================

    suspend fun franchises(): List<Franchise> = get("/anime/franchises")
    suspend fun franchise(id: String): Franchise = get("/anime/franchises/$id")
    suspend fun franchisesRandom(limit: Int = 10): List<Franchise> = get("/anime/franchises/random", q("limit" to limit))
    suspend fun franchisesByRelease(releaseId: Int): List<Franchise> = get("/anime/franchises/release/$releaseId")

    // ======================= Жанры =======================

    suspend fun genres(): List<Genre> = get("/anime/genres")
    suspend fun genre(id: Int): Genre = get("/anime/genres/$id")
    suspend fun genresRandom(limit: Int = 10): List<Genre> = get("/anime/genres/random", q("limit" to limit))
    suspend fun genreReleases(id: Int, page: Int, limit: Int = 24): Paged<Release> =
        get("/anime/genres/$id/releases", page(page, limit))

    // ======================= Релизы =======================

    suspend fun releasesRandom(limit: Int = 12): List<Release> = get("/anime/releases/random", q("limit" to limit))

    suspend fun releasesRecommended(releaseId: Int? = null, limit: Int = 12): List<Release> =
        get("/anime/releases/recommended", q("limit" to limit, "release_id" to releaseId))

    /** Без page — простой массив. */
    suspend fun releasesLatest(limit: Int = 20): List<Release> = get("/anime/releases/latest", q("limit" to limit))

    /** С page — постраничный ответ (data + meta). */
    suspend fun releasesLatestPaged(page: Int, limit: Int = 24): Paged<Release> =
        get("/anime/releases/latest", page(page, limit))

    suspend fun releasesList(ids: List<Int> = emptyList(), aliases: List<String> = emptyList(), page: Int = 1, limit: Int = 24): Paged<Release> =
        get("/anime/releases/list", page(page, limit) + q("ids" to ids, "aliases" to aliases))

    suspend fun release(idOrAlias: String): Release = get("/anime/releases/$idOrAlias")
    suspend fun releaseMembers(idOrAlias: String): List<Member> = get("/anime/releases/$idOrAlias/members")
    suspend fun releaseEpisodesTimecodes(idOrAlias: String): List<View> = get("/anime/releases/$idOrAlias/episodes/timecodes")
    suspend fun episode(id: String): Episode = get("/anime/releases/episodes/$id")
    suspend fun episodeTimecode(id: String): View = get("/anime/releases/episodes/$id/timecode")

    suspend fun ratingGet(releaseId: Int): RatingOwn = get("/anime/releases/$releaseId/rating")
    suspend fun ratingSet(releaseId: Int, score: Int): RatingOwn =
        send("POST", "/anime/releases/$releaseId/rating", buildJsonObject { put("score", score) })
    suspend fun ratingDelete(releaseId: Int): RatingOwn = send("DELETE", "/anime/releases/$releaseId/rating", null)

    // ======================= Расписание =======================

    suspend fun scheduleNow(): ScheduleNow = get("/anime/schedule/now")
    suspend fun scheduleWeek(): ScheduleWeek = get("/anime/schedule/week")

    // ======================= Торренты =======================

    suspend fun torrents(page: Int, limit: Int = 24): Paged<Torrent> = get("/anime/torrents", page(page, limit))
    suspend fun torrent(hashOrId: String): Torrent = get("/anime/torrents/$hashOrId")
    suspend fun torrentsByRelease(releaseId: Int): List<Torrent> = get("/anime/torrents/release/$releaseId")

    /** Ссылка на .torrent файл. pk — passkey пользователя (пусто — собственный, если авторизован). */
    fun torrentFileUrl(hashOrId: String, pk: String? = null): String =
        buildUrl("/anime/torrents/$hashOrId/file", q("pk" to pk))

    suspend fun torrentsRss(limit: Int = 20, pk: String? = null): List<RssItem> =
        parseRss(raw("GET", "/anime/torrents/rss", q("limit" to limit, "pk" to pk)))

    suspend fun torrentsRssRelease(releaseId: Int, pk: String? = null): List<RssItem> =
        parseRss(raw("GET", "/anime/torrents/rss/release/$releaseId", q("pk" to pk)))

    private fun parseRss(xml: String): List<RssItem> {
        val parser = Xml.newPullParser()
        parser.setInput(StringReader(xml))
        val items = ArrayList<RssItem>()
        var inItem = false
        var title = ""; var link = ""; var date: String? = null; var desc: String? = null
        var ev = parser.eventType
        while (ev != XmlPullParser.END_DOCUMENT) {
            when (ev) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "item" -> { inItem = true; title = ""; link = ""; date = null; desc = null }
                    "title" -> if (inItem) title = readText(parser)
                    "link" -> if (inItem) link = readText(parser)
                    "pubDate" -> if (inItem) date = readText(parser)
                    "description" -> if (inItem) desc = readText(parser)
                    "enclosure" -> if (inItem && link.isBlank()) link = parser.getAttributeValue(null, "url").orEmpty()
                }
                XmlPullParser.END_TAG -> if (parser.name == "item") {
                    inItem = false
                    items.add(RssItem(title, link, date, desc))
                }
            }
            ev = parser.next()
        }
        return items
    }

    // ======================= Приложение / поиск / медиа / команда =======================

    suspend fun searchReleases(query: String): List<Release> = get("/app/search/releases", q("query" to query))
    suspend fun status(): AppStatus = get("/app/status")

    suspend fun promotions(): List<Promotion> = get<Promotions>("/media/promotions").data

    suspend fun videos(limit: Int = 12): List<Video> = get("/media/videos", q("limit" to limit))
    suspend fun videosPaged(page: Int, limit: Int = 24): Paged<Video> = get("/media/videos", page(page, limit))

    suspend fun teams(): List<Team> = get("/teams/")
    suspend fun teamRoles(): List<TeamRole> = get("/teams/roles")
    suspend fun teamUsers(): List<TeamUser> = get("/teams/users")
}
