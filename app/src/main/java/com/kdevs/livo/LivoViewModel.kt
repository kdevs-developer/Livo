package com.kdevs.livo

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kdevs.livo.data.Channel
import com.kdevs.livo.data.PlaylistRepository
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

const val DEFAULT_URL = "https://iptv-org.github.io/iptv/countries/in.m3u"

data class Filters(val language: String? = null, val category: String? = null)

data class UiState(
    val loading: Boolean = true,
    val error: String? = null,
    val all: List<Channel> = emptyList(),
    val online: List<Channel> = emptyList(),
    val visible: List<Channel> = emptyList(),
    val favorites: List<Channel> = emptyList(),
    val languages: List<String> = emptyList(),
    val categories: List<String> = emptyList(),
    val filters: Filters = Filters(),
    val favIds: Set<String> = emptySet(),
    val offlineCount: Int = 0,
    val checking: Boolean = false,
    val checked: Int = 0,
    val toCheck: Int = 0,
    val lastChecked: Long = 0L,
    val playlistUrl: String = DEFAULT_URL,
    val playing: Channel? = null,
    val message: String? = null,
)

fun Channel.matches(f: Filters): Boolean =
    (f.language == null || f.language in languages) &&
            (f.category == null || f.category == category)

class LivoViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = PlaylistRepository(app)
    private val prefs = app.getSharedPreferences("livo", Context.MODE_PRIVATE)
    private val offline: MutableSet<String> = (prefs.getStringSet("offline", emptySet()) ?: emptySet()).toMutableSet()
    private var checkJob: Job? = null
    private var playContext: List<Channel> = emptyList()

    private val _state = MutableStateFlow(
        UiState(
            playlistUrl = prefs.getString("url", DEFAULT_URL) ?: DEFAULT_URL,
            favIds = (prefs.getStringSet("fav", emptySet()) ?: emptySet()).toSet(),
            filters = Filters(
                language = prefs.getString("filter_language", null),
                category = prefs.getString("filter_category", null),
            ),
            lastChecked = prefs.getLong("last", 0L),
        )
    )
    val state: StateFlow<UiState> = _state.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                setChannels(repo.load(_state.value.playlistUrl))
                _state.update { it.copy(loading = false) }
                val favs = _state.value.favorites
                if (favs.isNotEmpty()) runCheck(favs, announce = false)
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.message ?: "Could not load the playlist") }
            }
        }
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
            val online = s.all.filter { it.id !in offline }
            s.copy(
                online = online,
                visible = online.filter { it.matches(s.filters) },
                favorites = online.filter { it.id in s.favIds },
                offlineCount = s.all.size - online.size,
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

    /** Reloads the playlist, then re-checks favorites and the channels in the current filter, offline ones included. */
    fun refresh() {
        checkJob?.cancel()
        checkJob = viewModelScope.launch {
            try { setChannels(repo.load(_state.value.playlistUrl)) } catch (_: Exception) {}
            val s = _state.value
            val scope = (s.all.filter { it.id in s.favIds } + s.all.filter { it.matches(s.filters) })
                .distinct().take(400)
            runCheck(scope, announce = true)
        }
    }

    private suspend fun runCheck(list: List<Channel>, announce: Boolean) {
        _state.update { it.copy(checking = true, checked = 0, toCheck = list.size) }
        var back = 0
        var gone = 0
        try {
            val gate = Semaphore(24)
            coroutineScope {
                list.map { c ->
                    async {
                        gate.withPermit {
                            val ok = repo.isOnline(c)
                            if (ok) { if (offline.remove(c.id)) back++ } else { if (offline.add(c.id)) gone++ }
                            _state.update { it.copy(checked = it.checked + 1) }
                        }
                    }
                }.awaitAll()
            }
        } finally {
            val now = System.currentTimeMillis()
            prefs.edit().putStringSet("offline", offline.toSet()).putLong("last", now).apply()
            recompute()
            _state.update {
                it.copy(
                    checking = false, lastChecked = now,
                    message = if (!announce) it.message
                    else if (back == 0 && gone == 0) "All checked channels are online"
                    else "$back back online · $gone hidden",
                )
            }
        }
    }

    fun markOffline(c: Channel) {
        offline.add(c.id)
        prefs.edit().putStringSet("offline", offline.toSet()).apply()
        recompute()
    }

    fun setPlaylistUrl(url: String) {
        val u = url.trim().ifBlank { DEFAULT_URL }
        prefs.edit().putString("url", u).putStringSet("offline", emptySet()).apply()
        offline.clear()
        _state.update { it.copy(playlistUrl = u, message = "Loading playlist…") }
        load()
    }

    fun play(c: Channel, list: List<Channel>) { playContext = list; _state.update { it.copy(playing = c) } }
    fun stop() { _state.update { it.copy(playing = null) } }

    fun step(delta: Int) {
        val cur = _state.value.playing ?: return
        val list = playContext.filter { it.id !in offline || it.id == cur.id }
        if (list.isEmpty()) return
        val i = list.indexOfFirst { it.id == cur.id }.coerceAtLeast(0)
        val next = list[(i + delta + list.size) % list.size]
        _state.update { it.copy(playing = next) }
    }

    fun clearMessage() { _state.update { it.copy(message = null) } }
}