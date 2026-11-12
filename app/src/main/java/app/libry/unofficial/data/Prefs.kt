package app.libry.unofficial.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.UUID

const val DEFAULT_BASE_URL = "https://aniliberty.top/api/v1"

/** Настройки и сессия. Значения — Compose-состояние, поэтому UI обновляется сам. */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("libry", Context.MODE_PRIVATE)

    var token by mutableStateOf<String?>(sp.getString("token", null))
        private set
    var baseUrl by mutableStateOf(sp.getString("base_url", DEFAULT_BASE_URL) ?: DEFAULT_BASE_URL)
        private set
    /** 0 — системная, 1 — светлая, 2 — тёмная */
    var theme by mutableIntStateOf(sp.getInt("theme", 0))
        private set
    /** "480" | "720" | "1080" */
    var quality by mutableStateOf(sp.getString("quality", "1080") ?: "1080")
        private set
    var autoNext by mutableStateOf(sp.getBoolean("auto_next", true))
        private set
    var autoSkip by mutableStateOf(sp.getBoolean("auto_skip", false))
        private set
    var showAds by mutableStateOf(sp.getBoolean("show_ads", false))
        private set
    var usePost by mutableStateOf(sp.getBoolean("use_post", false))
        private set
    var hideAdult by mutableStateOf(sp.getBoolean("hide_adult", false))
        private set
    var searchHistory by mutableStateOf(
        (sp.getString("search_history", "") ?: "").split('\n').filter { it.isNotBlank() }
    )
        private set

    val deviceId: String
        get() {
            var id = sp.getString("device_id", null)
            if (id == null) {
                id = UUID.randomUUID().toString()
                sp.edit().putString("device_id", id).apply()
            }
            return id
        }

    /** Origin сайта (для относительных ссылок на картинки): https://host */
    val origin: String
        get() {
            val m = Regex("^(https?://[^/]+)").find(baseUrl)
            return m?.value ?: "https://aniliberty.top"
        }

    fun setToken(v: String?) { token = v; sp.edit().putString("token", v).apply() }
    fun setBaseUrl(v: String) {
        val t = v.trim().trimEnd('/').ifBlank { DEFAULT_BASE_URL }
        baseUrl = t; sp.edit().putString("base_url", t).apply()
    }
    fun setTheme(v: Int) { theme = v; sp.edit().putInt("theme", v).apply() }
    fun setQuality(v: String) { quality = v; sp.edit().putString("quality", v).apply() }
    fun setAutoNext(v: Boolean) { autoNext = v; sp.edit().putBoolean("auto_next", v).apply() }
    fun setAutoSkip(v: Boolean) { autoSkip = v; sp.edit().putBoolean("auto_skip", v).apply() }
    fun setShowAds(v: Boolean) { showAds = v; sp.edit().putBoolean("show_ads", v).apply() }
    fun setUsePost(v: Boolean) { usePost = v; sp.edit().putBoolean("use_post", v).apply() }
    fun setHideAdult(v: Boolean) { hideAdult = v; sp.edit().putBoolean("hide_adult", v).apply() }

    fun addSearch(q: String) {
        val t = q.trim()
        if (t.isEmpty()) return
        searchHistory = (listOf(t) + searchHistory.filter { it != t }).take(15)
        sp.edit().putString("search_history", searchHistory.joinToString("\n")).apply()
    }

    fun clearSearch() {
        searchHistory = emptyList()
        sp.edit().remove("search_history").apply()
    }

    // ---- локальные таймкоды (для гостей и как кэш): episodeId -> "время|просмотрено" ----
    fun localTimecode(episodeId: String): Tc? {
        val s = sp.getString("tc_$episodeId", null) ?: return null
        val p = s.split('|')
        val t = p.getOrNull(0)?.toDoubleOrNull() ?: return null
        return Tc(episodeId, t, p.getOrNull(1) == "1")
    }

    fun saveLocalTimecode(episodeId: String, time: Double, watched: Boolean) {
        sp.edit().putString("tc_$episodeId", "$time|${if (watched) 1 else 0}").apply()
    }

    fun clearLocalTimecode(episodeId: String) {
        sp.edit().remove("tc_$episodeId").apply()
    }
}
