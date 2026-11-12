package app.libry.unofficial.data

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull

/** Терпимый к "5.0" / "5" / "\"5\"" разбор целых чисел (в спецификации много полей "number"). */
object LInt : KSerializer<Int> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("LInt", PrimitiveKind.INT)
    override fun deserialize(decoder: Decoder): Int {
        val el = (decoder as JsonDecoder).decodeJsonElement()
        val p = el as? JsonPrimitive
        return p?.doubleOrNull?.toInt() ?: p?.contentOrNull?.toDoubleOrNull()?.toInt() ?: 0
    }
    override fun serialize(encoder: Encoder, value: Int) = encoder.encodeInt(value)
}

// ---------- Общие ----------

@Serializable
data class Img(
    val preview: String? = null,
    val thumbnail: String? = null,
    val optimized: Img? = null,
)

fun Img?.best(): String? = this?.optimized?.preview ?: this?.preview ?: this?.optimized?.thumbnail ?: this?.thumbnail

/** Универсальная пара {value, description, label ...} (тип, сезон, возраст, роль и т.д.). */
@Serializable
data class ValDesc(
    val value: JsonPrimitive? = null,
    val description: String? = null,
    val label: String? = null,
    val isAdult: Boolean? = null,
    val labelColor: String? = null,
    val labelIsVisible: Boolean? = null,
)

val ValDesc?.str: String? get() = this?.value?.contentOrNull

@Serializable
data class Pagination(
    @Serializable(with = LInt::class) val total: Int? = null,
    @Serializable(with = LInt::class) val count: Int? = null,
    @Serializable(with = LInt::class) val perPage: Int? = null,
    @Serializable(with = LInt::class) val currentPage: Int? = null,
    @Serializable(with = LInt::class) val totalPages: Int? = null,
)

@Serializable
data class Counts(
    val today: Int? = null,
    val yesterday: Int? = null,
    val week: Int? = null,
    val month: Int? = null,
    val unwatched: Int? = null,
    val watched: Int? = null,
)

@Serializable
data class Meta(val pagination: Pagination? = null, val counts: Counts? = null)

@Serializable
data class Paged<T>(val data: List<T> = emptyList(), val meta: Meta? = null)

// ---------- Релизы ----------

@Serializable
data class Name(val main: String? = null, val english: String? = null, val alternative: String? = null)

@Serializable
data class ExtRating(
    val id: Long? = null,
    val url: String? = null,
    val votes: Long? = null,
    val rating: Double? = null,
)

@Serializable
data class Rating(
    val average: Double? = null,
    val votes: Int? = null,
    val distribution: JsonElement? = null,
)

@Serializable
data class Skip(val start: Double? = null, val stop: Double? = null)

@Serializable
data class Episode(
    val id: String? = null,
    val name: String? = null,
    val ordinal: Double? = null,
    val ending: Skip? = null,
    val opening: Skip? = null,
    val preview: Img? = null,
    @SerialName("hls_480") val hls480: String? = null,
    @SerialName("hls_720") val hls720: String? = null,
    @SerialName("hls_1080") val hls1080: String? = null,
    val duration: Double? = null,
    val rutubeId: String? = null,
    val youtubeId: String? = null,
    val updatedAt: String? = null,
    @Serializable(with = LInt::class) val sortOrder: Int? = null,
    @Serializable(with = LInt::class) val releaseId: Int? = null,
    val nameEnglish: String? = null,
    val release: Release? = null,
)

@Serializable
data class Genre(
    @Serializable(with = LInt::class) val id: Int? = null,
    val name: String? = null,
    val image: Img? = null,
    @Serializable(with = LInt::class) val totalReleases: Int? = null,
)

@Serializable
data class MemberUser(val id: Long? = null, val avatar: Img? = null)

@Serializable
data class Member(
    val id: String? = null,
    val role: ValDesc? = null,
    val user: MemberUser? = null,
    val nickname: String? = null,
)

@Serializable
data class Sponsor(
    val id: String? = null,
    val title: String? = null,
    val description: String? = null,
    val urlTitle: String? = null,
    val url: String? = null,
)

@Serializable
data class TorrentMember(
    val id: String? = null,
    val role: ValDesc? = null,
    val nickname: String? = null,
    val externalUrl: String? = null,
    val user: MemberUser? = null,
)

@Serializable
data class Torrent(
    @Serializable(with = LInt::class) val id: Int? = null,
    val hash: String? = null,
    val size: Long? = null,
    val type: ValDesc? = null,
    val color: ValDesc? = null,
    val codec: ValDesc? = null,
    val label: String? = null,
    val quality: ValDesc? = null,
    val magnet: String? = null,
    val filename: String? = null,
    val seeders: Int? = null,
    val bitrate: Int? = null,
    val leechers: Int? = null,
    val sortOrder: Int? = null,
    val updatedAt: String? = null,
    val isHardsub: Boolean? = null,
    val description: String? = null,
    val createdAt: String? = null,
    val completedTimes: Int? = null,
    val torrentMembers: List<TorrentMember>? = null,
    val release: Release? = null,
)

@Serializable
data class Release(
    @Serializable(with = LInt::class) val id: Int? = null,
    val type: ValDesc? = null,
    @Serializable(with = LInt::class) val year: Int? = null,
    val name: Name? = null,
    val alias: String? = null,
    val season: ValDesc? = null,
    val shikimori: ExtRating? = null,
    val mal: ExtRating? = null,
    val rating: Rating? = null,
    val poster: Img? = null,
    val freshAt: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val isOngoing: Boolean? = null,
    val ageRating: ValDesc? = null,
    val publishDay: ValDesc? = null,
    val description: String? = null,
    val notification: String? = null,
    @Serializable(with = LInt::class) val episodesTotal: Int? = null,
    val externalPlayer: String? = null,
    val isInProduction: Boolean? = null,
    val isBlockedByGeo: Boolean? = null,
    val isBlockedByCopyrights: Boolean? = null,
    val isCommentsClosed: Boolean? = null,
    @Serializable(with = LInt::class) val commentsCount: Int? = null,
    @Serializable(with = LInt::class) val addedInUsersFavorites: Int? = null,
    @Serializable(with = LInt::class) val averageDurationOfEpisode: Int? = null,
    @Serializable(with = LInt::class) val addedInPlannedCollection: Int? = null,
    @Serializable(with = LInt::class) val addedInWatchedCollection: Int? = null,
    @Serializable(with = LInt::class) val addedInWatchingCollection: Int? = null,
    @Serializable(with = LInt::class) val addedInPostponedCollection: Int? = null,
    @Serializable(with = LInt::class) val addedInAbandonedCollection: Int? = null,
    // расширения разных ответов
    val genres: List<Genre>? = null,
    val members: List<Member>? = null,
    val episodes: List<Episode>? = null,
    val torrents: List<Torrent>? = null,
    val sponsors: List<Sponsor>? = null,
    val backgroundCovers: List<Img>? = null,
    val latestEpisode: Episode? = null,
)

fun Release.title(): String = name?.main ?: name?.english ?: alias ?: id?.toString() ?: "—"

fun Release.subtitle(): String = listOfNotNull(
    year?.toString(),
    type.str,
    episodesTotal?.takeIf { it > 0 }?.let { "$it эп." },
).joinToString(" • ")

fun Episode.title(): String {
    val n = ordinal?.let { if (it % 1.0 == 0.0) it.toInt().toString() else it.toString() }
    return listOfNotNull(n?.let { "Серия $it" }, name?.takeIf { it.isNotBlank() }).joinToString(": ").ifBlank { "Серия" }
}

@Serializable
data class RatingOwn(val releaseId: Int? = null, val score: Int? = null)

// ---------- Франшизы ----------

@Serializable
data class FranchiseRelease(
    val id: String? = null,
    val sortOrder: Int? = null,
    val releaseId: Int? = null,
    val franchiseId: String? = null,
    val release: Release? = null,
)

@Serializable
data class Franchise(
    val id: String? = null,
    val name: String? = null,
    val nameEnglish: String? = null,
    val image: Img? = null,
    val rating: Double? = null,
    val lastYear: Int? = null,
    val firstYear: Int? = null,
    val totalReleases: Int? = null,
    val totalEpisodes: Int? = null,
    val totalDuration: String? = null,
    val totalDurationInSeconds: Long? = null,
    val franchiseReleases: List<FranchiseRelease>? = null,
)

// ---------- Расписание ----------

@Serializable
data class ScheduleItem(
    val release: Release? = null,
    val fullSeasonIsReleased: Boolean? = null,
    val publishedReleaseEpisode: Episode? = null,
    val nextReleaseEpisodeNumber: Int? = null,
)

@Serializable
data class ScheduleNow(
    val today: List<ScheduleItem> = emptyList(),
    val tomorrow: List<ScheduleItem> = emptyList(),
    val yesterday: List<ScheduleItem> = emptyList(),
)

@Serializable
data class ScheduleWeek(val data: List<ScheduleItem> = emptyList())

// ---------- Аккаунт ----------

@Serializable
data class UserTorrents(val passkey: String? = null, val uploaded: Long? = null, val downloaded: Long? = null)

@Serializable
data class User(
    val id: Int? = null,
    val login: String? = null,
    val email: String? = null,
    val nickname: String? = null,
    val avatar: Img? = null,
    val torrents: UserTorrents? = null,
    val isBanned: Boolean? = null,
    val isNew: Boolean? = null,
    val isAdmin: Boolean? = null,
    val permissions: List<String>? = null,
    val createdAt: String? = null,
    val isWithAds: Boolean? = null,
)

@Serializable
data class View(
    val id: Long? = null,
    val time: Double? = null,
    val userId: Int? = null,
    val isWatched: Boolean? = null,
    val updatedAt: String? = null,
    val releaseEpisodeId: String? = null,
    val releaseEpisode: Episode? = null,
)

@Serializable
data class Otp(val code: String? = null, val userId: Int? = null, val deviceId: String? = null, val expiredAt: String? = null)

@Serializable
data class OtpGet(val otp: Otp? = null, val remainingTime: Int? = null)

@Serializable
data class TokenResp(val token: String? = null)

@Serializable
data class SocialLogin(val url: String? = null, val state: String? = null)

/** Универсальный элемент справочников: {value,label,description} или {id,name}. */
@Serializable
data class RefItem(
    val value: String? = null,
    val label: String? = null,
    val description: String? = null,
    @Serializable(with = LInt::class) val id: Int? = null,
    val name: String? = null,
)

// ---------- Реклама / медиа / прочее ----------

@Serializable
data class Vast(
    val id: String? = null,
    val url: String? = null,
    val adErid: String? = null,
    val adCompanyItn: String? = null,
    val adCompanyName: String? = null,
)

@Serializable
data class Promotion(
    val id: String? = null,
    val url: String? = null,
    val urlLabel: String? = null,
    val image: Img? = null,
    val title: String? = null,
    val description: String? = null,
    val isAd: Boolean? = null,
    val adErid: String? = null,
    val adOrigin: String? = null,
    val release: Release? = null,
    val hasOverlay: Boolean? = null,
)

@Serializable
data class Promotions(val data: List<Promotion> = emptyList())

@Serializable
data class VideoOrigin(
    val id: String? = null,
    val url: String? = null,
    val type: ValDesc? = null,
    val title: String? = null,
    val description: String? = null,
    val isAnnounce: Boolean? = null,
)

@Serializable
data class Video(
    val id: Int? = null,
    val url: String? = null,
    val title: String? = null,
    val views: Long? = null,
    val image: Img? = null,
    val comments: Int? = null,
    val videoId: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val isAnnounce: Boolean? = null,
    val origin: VideoOrigin? = null,
)

@Serializable
data class Team(val id: String? = null, val title: String? = null, val sortOrder: Int? = null, val description: String? = null)

@Serializable
data class TeamRole(val id: String? = null, val title: String? = null, val color: String? = null, val sortOrder: Int? = null)

@Serializable
data class TeamAccount(val id: Int? = null, val nickname: String? = null, val avatar: Img? = null)

@Serializable
data class TeamUser(
    val id: String? = null,
    val nickname: String? = null,
    val isIntern: Boolean? = null,
    val sortOrder: Int? = null,
    val isVacation: Boolean? = null,
    val team: Team? = null,
    val user: TeamAccount? = null,
    val roles: List<TeamRole>? = null,
)

@Serializable
data class ReqInfo(val ip: String? = null, val country: String? = null, val isoCode: String? = null, val timezone: String? = null)

@Serializable
data class AppStatus(
    val request: ReqInfo? = null,
    val isAlive: Boolean? = null,
    val availableApiEndpoints: List<String>? = null,
)

// ---------- Локальные типы ----------

data class Tc(val episodeId: String, val time: Double, val watched: Boolean)

data class RssItem(val title: String, val link: String, val date: String?, val description: String?)

data class VastAd(
    val mediaUrl: String,
    val skipAfterSec: Int?,
    val clickUrl: String?,
    val impressions: List<String>,
)

/** Фильтры каталога (GET/POST /anime/catalog/releases). */
data class CatalogFilter(
    val genres: Set<Int> = emptySet(),
    val types: Set<String> = emptySet(),
    val seasons: Set<String> = emptySet(),
    val fromYear: Int? = null,
    val toYear: Int? = null,
    val search: String = "",
    val sorting: String? = null,
    val ageRatings: Set<String> = emptySet(),
    val publishStatuses: Set<String> = emptySet(),
    val productionStatuses: Set<String> = emptySet(),
) {
    val isEmpty get() = this == CatalogFilter()
}

/** Фильтры списков пользователя (избранное / коллекции). */
data class UserFilter(
    val genres: Set<Int> = emptySet(),
    val types: Set<String> = emptySet(),
    val years: Set<Int> = emptySet(),
    val search: String = "",
    val sorting: String? = null,
    val ageRatings: Set<String> = emptySet(),
)

val COLLECTION_TYPES = listOf(
    "WATCHING" to "Смотрю",
    "PLANNED" to "В планах",
    "WATCHED" to "Просмотрено",
    "POSTPONED" to "Отложено",
    "ABANDONED" to "Брошено",
)

val DAYS_RU = listOf("Понедельник", "Вторник", "Среда", "Четверг", "Пятница", "Суббота", "Воскресенье")
