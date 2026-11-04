package com.kdevs.livo

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kdevs.livo.data.Channel
import com.kdevs.livo.data.Playlist
import com.kdevs.livo.data.PlaylistRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** The original playlist. Hard-coded on purpose: it is always available and can't be removed. */
const val DEFAULT_URL = "https://iptv-org.github.io/iptv/countries/in.m3u"   // only used to migrate older saved links
const val DEFAULT_ID = "lang:eng"   // first language list, same as LiveTV

/** Same language playlists as LiveTV: https://iptv-org.github.io/iptv/languages/<code>.m3u */
private val LANGUAGE_LISTS = listOf(
    "eng" to "English", "tam" to "Tamil", "hin" to "Hindi", "tel" to "Telugu", "mal" to "Malayalam",
    "kan" to "Kannada", "ben" to "Bengali", "mar" to "Marathi", "spa" to "Spanish", "ara" to "Arabic",
)

enum class Aspect { Fit, Fill, Zoom }
enum class Quality { Auto, P1080, P720, P480 }
enum class BufferSize { Low, Medium, High }
enum class CardSize { Compact, Regular, Large }
enum class Accent { Gold, Teal, Rose }

data class AppSettings(
    val resumeLast: Boolean = false,
    val aspect: Aspect = Aspect.Fit,
    val quality: Quality = Quality.Auto,
    val buffer: BufferSize = BufferSize.Medium,
    val hideOffline: Boolean = true,
    val checkOnLaunch: Boolean = false,
    val showNumbers: Boolean = true,
    val retryBeforeHiding: Boolean = true,
    val cardSize: CardSize = CardSize.Regular,
    val openOnFavorites: Boolean = false,
    val accent: Accent = Accent.Gold,
)

data class Filters(val language: String? = null, val category: String? = null)

data class UiState(
    val loading: Boolean = true,
    val error: String? = null,
    val all: List<Channel> = emptyList(),
    val online: List<Channel> = emptyList(),
    val visible: List<Channel> = emptyList(),
    val favorites: List<Channel> = emptyList(),
    val hidden: List<Channel> = emptyList(),
    val hiddenWhy: Map<String, String> = emptyMap(),
    val languages: List<String> = emptyList(),
    val categories: List<String> = emptyList(),
    val filters: Filters = Filters(),
    val favIds: Set<String> = emptySet(),
    val offlineCount: Int = 0,
    val checking: Boolean = false,
    val checked: Int = 0,
    val toCheck: Int = 0,
    val lastChecked: Long = 0L,
    val playlists: List<Playlist> = emptyList(),
    val activeId: String = DEFAULT_ID,
    val settings: AppSettings = AppSettings(),
    val playing: Channel? = null,
    val message: String? = null,
) {
    val active: Playlist get() = playlists.firstOrNull { it.id == activeId } ?: playlists.first()
}

fun Channel.matches(f: Filters): Boolean =
    (f.language == null || f.language in languages) &&
            (f.category == null || f.category == category)

private const val SEP = "\u0001"

private inline fun <reified T : Enum<T>> SharedPreferences.readEnum(key: String, default: T): T =
    getString(key, null)?.let { n -> enumValues<T>().firstOrNull { it.name == n } } ?: default

class LivoViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = PlaylistRepository(app)
    private val prefs = app.getSharedPreferences("livo", Context.MODE_PRIVATE)
    private val offline: MutableSet<String> = (prefs.getStringSet("offline", emptySet()) ?: emptySet()).toMutableSet()
    private val reasons: MutableMap<String, String> = (prefs.getStringSet("offline_why", emptySet()) ?: emptySet())
        .mapNotNull { e -> e.split(SEP).takeIf { it.size == 2 }?.let { it[0] to it[1] } }
        .toMap().toMutableMap()
    private val custom: MutableList<Playlist> = loadCustom().toMutableList().also { migrateOldUrl(it) }
    private var loadJob: Job? = null
    private var checkJob: Job? = null
    private var playContext: List<Channel> = emptyList()
    private var firstLoadDone = false

    private fun str(@StringRes id: Int, vararg args: Any): String = getApplication<Application>().getString(id, *args)

    private val builtIns: List<Playlist> = LANGUAGE_LISTS.map { (code, label) ->
        Playlist("lang:$code", label, "https://iptv-org.github.io/iptv/languages/$code.m3u", builtIn = true, language = label)
    }

    private val _state = MutableStateFlow(
        UiState(
            playlists = builtIns + custom,
            activeId = prefs.getString("active", null)
                ?.takeIf { id -> (builtIns + custom).any { it.id == id } } ?: DEFAULT_ID,
            favIds = (prefs.getStringSet("fav", emptySet()) ?: emptySet()).toSet(),
            filters = Filters(
                language = prefs.getString("filter_language", null),
                category = prefs.getString("filter_category", null),
            ),
            lastChecked = prefs.getLong("last", 0L),
            settings = loadSettings(),
        )
    )
    val state: StateFlow<UiState> = _state.asStateFlow()

    init { load() }

    // ---------- playlists ----------

    private fun loadCustom(): List<Playlist> = runCatching {
        val arr = JSONArray(prefs.getString("playlists", "[]"))
        (0 until arr.length()).map { i ->
            arr.getJSONObject(i).let { Playlist(it.getString("id"), it.getString("name"), it.getString("url")) }
        }
    }.getOrDefault(emptyList())

    private fun saveCustom() {
        val arr = JSONArray()
        custom.forEach { arr.put(JSONObject().put("id", it.id).put("name", it.name).put("url", it.url)) }
        prefs.edit().putString("playlists", arr.toString()).apply()
    }

    /** Older versions stored one editable link. Keep it as an extra playlist so nothing is lost. */
    private fun migrateOldUrl(list: MutableList<Playlist>) {
        val old = prefs.getString("url", null) ?: return
        val e = prefs.edit().remove("url")
        if (old != DEFAULT_URL && list.none { it.url == old }) {
            list.add(Playlist("legacy", str(R.string.playlist_legacy_name), old))
            e.putString("playlists", JSONArray().also { arr ->
                list.forEach { arr.put(JSONObject().put("id", it.id).put("name", it.name).put("url", it.url)) }
            }.toString())
            e.putString("active", "legacy")
        }
        e.apply()
    }

    /** Adds a new playlist next to the existing ones and switches to it. The built-in one is never touched. */
    fun addPlaylist(name: String, url: String): Boolean {
        val u = url.trim()
        if (!u.startsWith("http", ignoreCase = true)) {
            _state.update { it.copy(message = str(R.string.msg_invalid_url)) }
            return false
        }
        val existing = (builtIns + custom).firstOrNull { it.url == u }
        if (existing != null) {
            selectPlaylist(existing.id)
            return true
        }
        val p = Playlist(UUID.randomUUID().toString().take(8), name.trim().ifBlank { str(R.string.playlist_default_name, custom.size + 1) }, u)
        custom.add(p)
        saveCustom()
        _state.update { it.copy(playlists = builtIns + custom, message = str(R.string.msg_playlist_added)) }
        selectPlaylist(p.id)
        return true
    }

    fun selectPlaylist(id: String) {
        if (_state.value.activeId == id && _state.value.all.isNotEmpty()) return
        prefs.edit().putString("active", id).apply()
        checkJob?.cancel()
        _state.update { it.copy(activeId = id, message = str(R.string.msg_loading)) }
        setChannels(emptyList())
        load()
    }

    fun deletePlaylist(id: String) {
        if (builtIns.any { it.id == id }) return
        custom.removeAll { it.id == id }
        saveCustom()
        _state.update { it.copy(playlists = builtIns + custom) }
        if (_state.value.activeId == id) selectPlaylist(DEFAULT_ID)
    }

    // ---------- loading ----------

    fun load() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                setChannels(repo.load(_state.value.active))
                _state.update { it.copy(loading = false) }
                val s = _state.value
                if (s.settings.checkOnLaunch && s.favorites.isNotEmpty()) runCheck(s.favorites, announce = false)
                resumeLastIfWanted()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.message ?: str(R.string.err_load)) }
            }
        }
    }

    private fun resumeLastIfWanted() {
        if (firstLoadDone) return
        firstLoadDone = true
        val s = _state.value
        if (!s.settings.resumeLast || s.playing != null) return
        val id = prefs.getString("last_channel", null) ?: return
        s.visible.firstOrNull { it.id == id }?.let { play(it, s.visible) }
    }

    private fun setChannels(list: List<Channel>) {
        val languages = list.flatMap { c -> c.languages }.distinct().sorted()
        val categories = list.map { c -> c.category }.distinct().sorted()
        _state.update { s ->
            // A remembered filter that no longer exists in this playlist is ignored for now.
            // The saved choice is kept, so it comes back when the option is available again.
            var f = s.filters
            if (list.isNotEmpty()) {
                if (f.language != null && f.language !in languages) f = f.copy(language = null)
                if (f.category != null && f.category !in categories) f = f.copy(category = null)
            }
            s.copy(all = list, languages = languages, categories = categories, filters = f)
        }
        recompute()
    }

    private fun saveFilters(f: Filters) {
        prefs.edit()
            .putString("filter_language", f.language)
            .putString("filter_category", f.category)
            .apply()
    }

    private fun recompute() {
        _state.update { s ->
            val shown = if (s.settings.hideOffline) s.all.filter { it.id !in offline } else s.all
            val hidden = s.all.filter { it.id in offline }
            // Language playlists are already one language, so the saved language filter is ignored there.
            val f = if (s.active.language != null) s.filters.copy(language = null) else s.filters
            s.copy(
                online = shown,
                visible = shown.filter { it.matches(f) },
                favorites = shown.filter { it.id in s.favIds },
                hidden = hidden,
                hiddenWhy = reasons.toMap(),
                offlineCount = hidden.size,
            )
        }
    }

    fun setFilters(f: Filters) {
        saveFilters(f)
        _state.update { it.copy(filters = f) }
        recompute()
    }

    fun toggleFav(c: Channel) {
        val next = _state.value.favIds.let { if (c.id in it) it - c.id else it + c.id }
        prefs.edit().putStringSet("fav", next).apply()
        _state.update { it.copy(favIds = next) }
        recompute()
    }

    // ---------- availability ----------

    private fun saveOffline() {
        prefs.edit()
            .putStringSet("offline", offline.toSet())
            .putStringSet("offline_why", reasons.map { (k, v) -> k + SEP + v }.toSet())
            .apply()
    }

    /** Reloads the playlist, then re-checks favorites and the channels in the current filter, hidden ones included. */
    fun refresh() {
        checkJob?.cancel()
        checkJob = viewModelScope.launch {
            try { setChannels(repo.load(_state.value.active)) } catch (_: Exception) {}
            val s = _state.value
            val scope = (s.all.filter { it.id in s.favIds } + s.all.filter { it.matches(s.filters) })
                .distinct().take(400)
            runCheck(scope, announce = true)
        }
    }

    /** Re-checks only the hidden channels. */
    fun recheckHidden() {
        checkJob?.cancel()
        checkJob = viewModelScope.launch { runCheck(_state.value.hidden, announce = true) }
    }

    private suspend fun runCheck(list: List<Channel>, announce: Boolean) {
        _state.update { it.copy(checking = true, checked = 0, toCheck = list.size) }
        val retry = _state.value.settings.retryBeforeHiding
        var back = 0
        var gone = 0
        try {
            val gate = Semaphore(8)
            coroutineScope {
                list.map { c ->
                    async {
                        gate.withPermit {
                            val why = repo.failureReason(c, retry)
                            if (why == null) {
                                if (offline.remove(c.id)) { reasons.remove(c.id); back++ }
                            } else {
                                if (offline.add(c.id)) gone++
                                reasons[c.id] = why
                            }
                            _state.update { it.copy(checked = it.checked + 1) }
                        }
                    }
                }.awaitAll()
            }
        } finally {
            val now = System.currentTimeMillis()
            saveOffline()
            prefs.edit().putLong("last", now).apply()
            recompute()
            _state.update {
                it.copy(
                    checking = false, lastChecked = now,
                    message = if (!announce) it.message
                    else if (back == 0 && gone == 0) str(R.string.msg_all_online)
                    else str(R.string.msg_check_result, back, gone),
                )
            }
        }
    }

    fun markOffline(c: Channel, why: String = "player") {
        offline.add(c.id)
        reasons[c.id] = why
        saveOffline()
        recompute()
    }

    /** Called when a channel plays fine: if it was hidden, it comes back. */
    fun markOnline(c: Channel) {
        if (offline.remove(c.id)) {
            reasons.remove(c.id)
            saveOffline()
            recompute()
        }
    }

    fun unhide(id: String) {
        if (offline.remove(id)) {
            reasons.remove(id)
            saveOffline()
            recompute()
        }
    }

    fun unhideAll() {
        offline.clear()
        reasons.clear()
        saveOffline()
        recompute()
        _state.update { it.copy(message = str(R.string.msg_unhidden)) }
    }

    // ---------- settings ----------

    private fun loadSettings() = AppSettings(
        resumeLast = prefs.getBoolean("s_resume", false),
        aspect = prefs.readEnum("s_aspect", Aspect.Fit),
        quality = prefs.readEnum("s_quality", Quality.Auto),
        buffer = prefs.readEnum("s_buffer", BufferSize.Medium),
        hideOffline = prefs.getBoolean("s_hide_offline", true),
        checkOnLaunch = prefs.getBoolean("s_check_launch", false),
        showNumbers = prefs.getBoolean("s_numbers", true),
        retryBeforeHiding = prefs.getBoolean("s_retry", true),
        cardSize = prefs.readEnum("s_card", CardSize.Regular),
        openOnFavorites = prefs.getBoolean("s_open_fav", false),
        accent = prefs.readEnum("s_accent", Accent.Gold),
    )

    fun updateSettings(change: (AppSettings) -> AppSettings) {
        val s = change(_state.value.settings)
        prefs.edit()
            .putBoolean("s_resume", s.resumeLast).putString("s_aspect", s.aspect.name)
            .putString("s_quality", s.quality.name).putString("s_buffer", s.buffer.name)
            .putBoolean("s_hide_offline", s.hideOffline).putBoolean("s_check_launch", s.checkOnLaunch)
            .putBoolean("s_numbers", s.showNumbers).putBoolean("s_retry", s.retryBeforeHiding)
            .putString("s_card", s.cardSize.name).putBoolean("s_open_fav", s.openOnFavorites)
            .putString("s_accent", s.accent.name)
            .apply()
        _state.update { it.copy(settings = s) }
        recompute()
    }

    fun exportFavorites(): String {
        val text = _state.value.favIds.joinToString("\n")
        _state.update { it.copy(message = str(R.string.msg_fav_exported, it.favIds.size)) }
        return text
    }

    fun importFavorites(text: String?) {
        val ids = text.orEmpty().lines().map { it.trim() }.filter { it.startsWith("http", ignoreCase = true) }.toSet()
        if (ids.isEmpty()) {
            _state.update { it.copy(message = str(R.string.msg_clipboard_empty)) }
            return
        }
        val next = _state.value.favIds + ids
        prefs.edit().putStringSet("fav", next).apply()
        _state.update { it.copy(favIds = next, message = str(R.string.msg_fav_imported, ids.size)) }
        recompute()
    }

    // ---------- playback ----------

    fun play(c: Channel, list: List<Channel>) {
        playContext = list
        prefs.edit().putString("last_channel", c.id).apply()
        _state.update { it.copy(playing = c) }
    }

    fun stop() { _state.update { it.copy(playing = null) } }

    /** Switches to the channel with this number in the current playlist. Returns false when there isn't one. */
    fun tuneNumber(n: Int): Boolean {
        val s = _state.value
        val c = s.all.firstOrNull { it.number == n } ?: return false
        if (s.settings.hideOffline && c.id in offline) return false
        prefs.edit().putString("last_channel", c.id).apply()
        _state.update { it.copy(playing = c) }
        return true
    }

    fun step(delta: Int) {
        val s = _state.value
        val cur = s.playing ?: return
        val list = playContext.filter { !s.settings.hideOffline || it.id !in offline || it.id == cur.id }
        if (list.isEmpty()) return
        val i = list.indexOfFirst { it.id == cur.id }.coerceAtLeast(0)
        val next = list[(i + delta + list.size) % list.size]
        prefs.edit().putString("last_channel", next.id).apply()
        _state.update { it.copy(playing = next) }
    }

    fun clearMessage() { _state.update { it.copy(message = null) } }
}