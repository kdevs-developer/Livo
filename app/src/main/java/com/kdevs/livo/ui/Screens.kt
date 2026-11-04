package com.kdevs.livo.ui

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kdevs.livo.Accent
import com.kdevs.livo.Aspect
import com.kdevs.livo.BufferSize
import com.kdevs.livo.CardSize
import com.kdevs.livo.Filters
import com.kdevs.livo.LivoViewModel
import com.kdevs.livo.Quality
import com.kdevs.livo.R
import com.kdevs.livo.UiState
import com.kdevs.livo.data.Channel
import com.kdevs.livo.data.Playlist
import kotlinx.coroutines.delay

enum class Tab(@StringRes val label: Int, val icon: ImageVector) {
    Live(R.string.tab_live, Icons.Rounded.LiveTv),
    Favorites(R.string.tab_favorites, Icons.Rounded.Favorite),
    Search(R.string.tab_search, Icons.Rounded.Search),
    Settings(R.string.tab_settings, Icons.Rounded.Settings),
}

private fun isTv(ctx: Context) =
    (ctx.getSystemService(Context.UI_MODE_SERVICE) as UiModeManager).currentModeType == Configuration.UI_MODE_TYPE_TELEVISION

@Composable
private fun checkedText(t: Long): String =
    if (t == 0L) stringResource(R.string.not_checked_yet)
    else stringResource(
        R.string.checked_ago,
        DateUtils.getRelativeTimeSpanString(t, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString(),
    )

@Composable
fun LivoApp(vm: LivoViewModel) {
    val s by vm.state.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val wide = LocalConfiguration.current.screenWidthDp >= 600
    val tv = remember { isTv(ctx) }
    var tab by rememberSaveable { mutableStateOf(if (s.settings.openOnFavorites) Tab.Favorites else Tab.Live) }
    var showHidden by rememberSaveable { mutableStateOf(false) }
    var addDialog by rememberSaveable { mutableStateOf(false) }
    val grids = Tab.entries.associateWith { rememberLazyGridState() }
    val onTab: (Tab) -> Unit = { tab = it; showHidden = false }

    BackHandler(enabled = showHidden) { showHidden = false }

    val playing = s.playing
    CompositionLocalProvider(LocalSettings provides s.settings) {
        if (playing != null) {
            PlayerScreen(
                channel = playing, isFav = playing.id in s.favIds, settings = s.settings,
                onBack = vm::stop, onNext = { vm.step(1) }, onPrev = { vm.step(-1) },
                onFav = { vm.toggleFav(playing) },
                onFailed = { vm.markOffline(playing) },
                onPlaying = { vm.markOnline(playing) },
            )
        } else {
            Box(
                Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(LivoColors.HeaderTop, LivoColors.Bg, LivoColors.Bg))),
            ) {
                Row(Modifier.fillMaxSize().systemBarsPadding()) {
                    if (wide) Rail(tab, onTab)
                    Column(Modifier.weight(1f)) {
                        Box(Modifier.weight(1f)) {
                            Crossfade(if (showHidden) null else tab, label = "tab") { t ->
                                val current = t ?: Tab.Live
                                when {
                                    t == null -> HiddenPage(s, vm, wide) { showHidden = false }
                                    t == Tab.Search -> SearchPage(s, vm, wide, tv, grids.getValue(current))
                                    t == Tab.Settings -> SettingsPage(s, vm, wide, onOpenHidden = { showHidden = true }, onAddPlaylist = { addDialog = true })
                                    else -> ChannelPage(current, s, vm, wide, tv, grids.getValue(current)) { addDialog = true }
                                }
                            }
                            androidx.compose.animation.AnimatedVisibility(
                                visible = s.message != null,
                                enter = fadeIn() + slideInVertically { it / 2 }, exit = fadeOut(),
                                modifier = Modifier.align(Alignment.BottomCenter).padding(20.dp),
                            ) {
                                Text(
                                    s.message.orEmpty(), color = LivoColors.Ink, fontSize = 13.sp,
                                    modifier = Modifier.clip(RoundedCornerShape(50)).background(LivoColors.Bg3)
                                        .border(1.dp, LivoColors.Gold, RoundedCornerShape(50))
                                        .padding(horizontal = 18.dp, vertical = 10.dp),
                                )
                            }
                            LaunchedEffect(s.message) { if (s.message != null) { delay(2600); vm.clearMessage() } }
                        }
                        if (!wide) BottomBar(tab, onTab)
                    }
                }
            }
            if (addDialog) AddPlaylistDialog(onDismiss = { addDialog = false }, onAdd = { n, u -> vm.addPlaylist(n, u) })
        }
    }
}

@Composable
private fun Rail(tab: Tab, onTab: (Tab) -> Unit) {
    Column(
        Modifier.fillMaxHeight().width(92.dp).background(LivoColors.Rail).padding(vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(42.dp).clip(RoundedCornerShape(13.dp)).background(LivoColors.White), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.PlayArrow, stringResource(R.string.app_name), tint = LivoColors.Bg, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.height(14.dp))
        Tab.entries.forEach { NavItem(it, it == tab, { onTab(it) }, Modifier.width(70.dp)) }
    }
}

@Composable
private fun BottomBar(tab: Tab, onTab: (Tab) -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(LivoColors.Bar).padding(horizontal = 6.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceAround,
    ) { Tab.entries.forEach { NavItem(it, it == tab, { onTab(it) }, Modifier.weight(1f)) } }
}

@Composable
private fun NavItem(t: Tab, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val src = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(14.dp)
    val tint by animateColorAsState(if (selected) LivoColors.Gold else LivoColors.Muted, label = "tint")
    Column(
        modifier.premiumFocus(shape, src, 1.08f, showIdleBorder = false).clip(shape)
            .background(if (selected) LivoColors.Gold.copy(alpha = 0.1f) else androidx.compose.ui.graphics.Color.Transparent)
            .clickable(interactionSource = src, indication = null, onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(t.icon, stringResource(t.label), tint = tint, modifier = Modifier.size(24.dp))
        Text(stringResource(t.label), color = tint, fontSize = 10.sp, maxLines = 1)
    }
}

@Composable
private fun Header(title: String, s: UiState, compact: Boolean, onRefresh: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = LivoColors.Ink, fontSize = if (compact) 22.sp else 28.sp, fontWeight = FontWeight.Light, modifier = Modifier.weight(1f))
            PillButton(
                if (s.checking) stringResource(R.string.checking_progress, s.checked, s.toCheck)
                else if (compact) stringResource(R.string.refresh) else stringResource(R.string.refresh_channels),
                Icons.Rounded.Refresh, false, onRefresh, spin = s.checking,
            )
        }
        Text(
            stringResource(R.string.offline_hidden_checked, s.offlineCount, checkedText(s.lastChecked)),
            color = LivoColors.Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp),
        )
    }
}

private val FullSpan: LazyGridItemSpanScope.() -> GridItemSpan = { GridItemSpan(maxLineSpan) }

@Composable
private fun cellMin(wide: Boolean, tv: Boolean): Dp {
    val base = if (tv) 200.dp else if (wide) 176.dp else 150.dp
    return when (LocalSettings.current.cardSize) {
        CardSize.Compact -> base * 0.85f
        CardSize.Regular -> base
        CardSize.Large -> base * 1.2f
    }
}

@Composable
private fun PlaylistChips(s: UiState, onSelect: (String) -> Unit, onAdd: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        s.playlists.forEach { p -> Chip(p.name, p.id == s.activeId, null) { onSelect(p.id) } }
        Chip(stringResource(R.string.add), false, Icons.Rounded.Add, onAdd)
    }
}

@Composable
private fun Chip(text: String, selected: Boolean, icon: ImageVector?, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(50)
    Row(
        Modifier.premiumFocus(shape, src, 1.05f, showIdleBorder = false).clip(shape)
            .background(if (selected) LivoColors.Gold.copy(alpha = 0.12f) else LivoColors.Glass)
            .border(1.dp, if (selected) LivoColors.Gold else LivoColors.Line, shape)
            .clickable(interactionSource = src, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = LivoColors.Gold, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, color = if (selected) LivoColors.Gold else LivoColors.Ink, fontSize = 12.sp, maxLines = 1)
    }
}

@Composable
private fun ChannelPage(mode: Tab, s: UiState, vm: LivoViewModel, wide: Boolean, tv: Boolean, state: LazyGridState, onAddPlaylist: () -> Unit) {
    val compact = !wide
    val pad = if (compact) 16.dp else 28.dp
    val title = if (mode == Tab.Live) stringResource(R.string.live_channels) else stringResource(R.string.tab_favorites)
    LazyVerticalGrid(
        columns = GridCells.Adaptive(cellMin(wide, tv)), state = state,
        contentPadding = PaddingValues(horizontal = pad, vertical = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(span = FullSpan) { Header(title, s, compact) { vm.refresh() } }
        if (mode == Tab.Live) {
            item(span = FullSpan) { PlaylistChips(s, { vm.selectPlaylist(it) }, onAddPlaylist) }
        }

        if (s.loading && s.all.isEmpty()) {
            item(span = FullSpan) {
                Column(Modifier.fillMaxWidth().padding(48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = LivoColors.Gold)
                    Text(stringResource(R.string.loading_channels), color = LivoColors.Muted, modifier = Modifier.padding(top = 12.dp))
                }
            }
            return@LazyVerticalGrid
        }
        if (s.error != null && s.all.isEmpty()) {
            item(span = FullSpan) {
                Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.load_failed), color = LivoColors.Ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Text(s.error, color = LivoColors.Muted, fontSize = 12.sp, modifier = Modifier.padding(vertical = 8.dp))
                    PillButton(stringResource(R.string.try_again), Icons.Rounded.Refresh, true, { vm.load() })
                }
            }
            return@LazyVerticalGrid
        }

        if (mode == Tab.Live) {
            item(span = FullSpan) {
                val featured = s.favorites.firstOrNull() ?: s.visible.firstOrNull()
                HeroBanner(featured, compact) { featured?.let { vm.play(it, s.visible) } }
            }
        }
        if (mode != Tab.Favorites) {
            item(span = FullSpan) { FilterBar(s.filters, s.languages, s.categories, compact) { vm.setFilters(it) } }
        }

        if (mode == Tab.Favorites) {
            if (s.favorites.isEmpty()) {
                item(span = FullSpan) {
                    Text(stringResource(R.string.favorites_empty), color = LivoColors.Muted, modifier = Modifier.padding(vertical = 32.dp))
                }
            }
            items(s.favorites, key = { "f_" + it.id }) { c -> ChannelCard(c, true, { vm.play(c, s.favorites) }, { vm.toggleFav(c) }) }
        } else {
            if (mode == Tab.Live && s.favorites.isNotEmpty()) {
                item(span = FullSpan) { SectionTitle(stringResource(R.string.your_favorites), stringResource(R.string.n_channels, s.favorites.size)) }
                items(s.favorites, key = { "h_" + it.id }) { c -> ChannelCard(c, true, { vm.play(c, s.favorites) }, { vm.toggleFav(c) }) }
            }
            item(span = FullSpan) { SectionTitle(stringResource(R.string.all_channels), stringResource(R.string.n_online, s.visible.size)) }
            if (s.visible.isEmpty()) {
                item(span = FullSpan) {
                    Column(Modifier.padding(vertical = 24.dp)) {
                        Text(stringResource(R.string.no_match), color = LivoColors.Muted, modifier = Modifier.padding(bottom = 12.dp))
                        PillButton(stringResource(R.string.clear_filters), Icons.Rounded.Refresh, false, { vm.setFilters(Filters()) })
                    }
                }
            }
            items(s.visible, key = { "a_" + it.id }) { c -> ChannelCard(c, c.id in s.favIds, { vm.play(c, s.visible) }, { vm.toggleFav(c) }) }
        }
    }
}

@Composable
private fun SearchPage(s: UiState, vm: LivoViewModel, wide: Boolean, tv: Boolean, state: LazyGridState) {
    var q by rememberSaveable { mutableStateOf("") }
    val results = remember(q, s.online) {
        if (q.trim().length < 2) emptyList() else s.online.filter { it.name.contains(q.trim(), ignoreCase = true) }.take(200)
    }
    val pad = if (wide) 28.dp else 16.dp
    Column(Modifier.fillMaxSize().padding(top = 20.dp)) {
        OutlinedTextField(
            value = q, onValueChange = { q = it }, singleLine = true, shape = RoundedCornerShape(18.dp),
            placeholder = { Text(stringResource(R.string.search_hint), color = LivoColors.Muted) },
            leadingIcon = { Icon(Icons.Rounded.Search, null, tint = LivoColors.Gold) },
            colors = fieldColors(),
            modifier = Modifier.fillMaxWidth().padding(horizontal = pad),
        )
        LazyVerticalGrid(
            columns = GridCells.Adaptive(cellMin(wide, tv)), state = state,
            contentPadding = PaddingValues(horizontal = pad, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            if (results.isEmpty()) {
                item(span = FullSpan) {
                    Text(
                        if (q.trim().length < 2) stringResource(R.string.search_min) else stringResource(R.string.search_none),
                        color = LivoColors.Muted,
                    )
                }
            }
            items(results, key = { "s_" + it.id }) { c -> ChannelCard(c, c.id in s.favIds, { vm.play(c, results) }, { vm.toggleFav(c) }) }
        }
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = LivoColors.Gold, unfocusedBorderColor = LivoColors.Line, cursorColor = LivoColors.Gold,
    focusedTextColor = LivoColors.Ink, unfocusedTextColor = LivoColors.Ink,
)

// ---------- Hidden channels ----------

@Composable
private fun reasonText(code: String?): String = stringResource(
    when (code) {
        "not_found" -> R.string.why_not_found
        "server" -> R.string.why_server
        "invalid" -> R.string.why_invalid
        "host" -> R.string.why_host
        "conn" -> R.string.why_conn
        "player" -> R.string.why_player
        else -> R.string.why_unknown
    }
)

@Composable
private fun HiddenPage(s: UiState, vm: LivoViewModel, wide: Boolean, onBack: () -> Unit) {
    val pad = if (wide) 28.dp else 16.dp
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = pad, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PillButton(stringResource(R.string.back), Icons.Rounded.ArrowBack, false, onBack)
                    Spacer(Modifier.width(14.dp))
                    Text(stringResource(R.string.hidden_title), color = LivoColors.Ink, fontSize = if (wide) 28.sp else 22.sp, fontWeight = FontWeight.Light)
                }
                Text(stringResource(R.string.hidden_hint, s.hidden.size), color = LivoColors.Muted, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PillButton(stringResource(R.string.unhide_all), Icons.Rounded.Visibility, true, { vm.unhideAll() })
                    PillButton(stringResource(R.string.recheck_all), Icons.Rounded.Refresh, false, { vm.recheckHidden() }, spin = s.checking)
                }
            }
        }
        if (s.hidden.isEmpty()) {
            item { Text(stringResource(R.string.hidden_empty), color = LivoColors.Muted, modifier = Modifier.padding(vertical = 24.dp)) }
        }
        items(s.hidden, key = { "x_" + it.id }) { c ->
            val shape = RoundedCornerShape(16.dp)
            Column(
                Modifier.fillMaxWidth().clip(shape).background(LivoColors.Bg2).border(1.dp, LivoColors.Line, shape).padding(14.dp),
            ) {
                Text(c.name, color = LivoColors.Ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${c.languages.joinToString(", ")} · ${reasonText(s.hiddenWhy[c.id])}",
                    color = LivoColors.Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp),
                )
                Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PillButton(stringResource(R.string.play_anyway), Icons.Rounded.PlayArrow, true, { vm.play(c, s.hidden) })
                    PillButton(stringResource(R.string.unhide), Icons.Rounded.Visibility, false, { vm.unhide(c.id) })
                }
            }
        }
    }
}

// ---------- Settings ----------

@StringRes private fun Aspect.label() = when (this) { Aspect.Fit -> R.string.aspect_fit; Aspect.Fill -> R.string.aspect_fill; Aspect.Zoom -> R.string.aspect_zoom }
@StringRes private fun Quality.label() = when (this) { Quality.Auto -> R.string.quality_auto; Quality.P1080 -> R.string.quality_1080; Quality.P720 -> R.string.quality_720; Quality.P480 -> R.string.quality_480 }
@StringRes private fun BufferSize.label() = when (this) { BufferSize.Low -> R.string.buffer_low; BufferSize.Medium -> R.string.buffer_medium; BufferSize.High -> R.string.buffer_high }
@StringRes private fun CardSize.label() = when (this) { CardSize.Compact -> R.string.card_compact; CardSize.Regular -> R.string.card_regular; CardSize.Large -> R.string.card_large }
@StringRes private fun Accent.label() = when (this) { Accent.Gold -> R.string.accent_gold; Accent.Teal -> R.string.accent_teal; Accent.Rose -> R.string.accent_rose }

private inline fun <reified T : Enum<T>> T.next(): T {
    val all = enumValues<T>()
    return all[(ordinal + 1) % all.size]
}

@Composable
private fun SectionLabel(text: String) {
    Text(text.uppercase(), color = LivoColors.Gold, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 10.dp))
}

@Composable
private fun Group(content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        Modifier.fillMaxWidth().widthIn(max = 720.dp).clip(shape).background(LivoColors.Bg2).border(1.dp, LivoColors.Line, shape),
        content = content,
    )
}

@Composable
private fun SettingRow(
    icon: ImageVector, title: String, subtitle: String, onClick: (() -> Unit)?,
    trailing: @Composable () -> Unit = {},
) {
    val src = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier.fillMaxWidth().premiumFocus(shape, src, 1.01f, showIdleBorder = false).clip(shape)
            .then(if (onClick != null) Modifier.clickable(interactionSource = src, indication = null, onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).background(LivoColors.Gold.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = LivoColors.Gold, modifier = Modifier.size(18.dp)) }
        Column(Modifier.weight(1f)) {
            Text(title, color = LivoColors.Ink, fontSize = 14.sp)
            if (subtitle.isNotBlank()) Text(subtitle, color = LivoColors.Muted, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        trailing()
    }
}

@Composable
private fun SwitchRow(icon: ImageVector, title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    SettingRow(icon, title, subtitle, { onChange(!checked) }) {
        Switch(
            checked = checked, onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedTrackColor = LivoColors.Gold, checkedThumbColor = LivoColors.OnGold,
                uncheckedTrackColor = LivoColors.Bg3, uncheckedThumbColor = LivoColors.Muted,
                uncheckedBorderColor = LivoColors.Line,
            ),
        )
    }
}

@Composable
private fun ChoiceRow(icon: ImageVector, title: String, subtitle: String, value: String, onClick: () -> Unit) {
    SettingRow(icon, title, subtitle, onClick) { Text(value, color = LivoColors.Gold, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
}

@Composable
private fun PlaylistRow(p: Playlist, selected: Boolean, onSelect: () -> Unit, onDelete: () -> Unit) {
    SettingRow(Icons.Rounded.PlaylistPlay, p.name, if (p.builtIn) stringResource(R.string.playlist_builtin_note) else p.url, onSelect) {
        if (p.builtIn) {
            Text(stringResource(R.string.built_in), color = LivoColors.Muted, fontSize = 11.sp)
        } else {
            IconButton(onClick = onDelete) {
                Icon(Icons.Rounded.Close, stringResource(R.string.delete), tint = LivoColors.Muted, modifier = Modifier.size(18.dp))
            }
        }
        RadioButton(
            selected = selected, onClick = null,
            colors = RadioButtonDefaults.colors(selectedColor = LivoColors.Gold, unselectedColor = LivoColors.Muted),
        )
    }
}

@Composable
private fun SettingsPage(s: UiState, vm: LivoViewModel, wide: Boolean, onOpenHidden: () -> Unit, onAddPlaylist: () -> Unit) {
    val set = s.settings
    val ctx = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val version = remember { runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull().orEmpty() }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = if (wide) 28.dp else 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(stringResource(R.string.tab_settings), color = LivoColors.Ink, fontSize = 28.sp, fontWeight = FontWeight.Light)
        Text(
            stringResource(R.string.playlist_summary, s.all.size, s.online.size, s.offlineCount),
            color = LivoColors.Muted, fontSize = 12.sp,
        )

        SectionLabel(stringResource(R.string.sec_playlists))
        Group {
            s.playlists.forEach { p -> PlaylistRow(p, p.id == s.activeId, { vm.selectPlaylist(p.id) }, { vm.deletePlaylist(p.id) }) }
            SettingRow(Icons.Rounded.Add, stringResource(R.string.add_playlist), stringResource(R.string.add_playlist_note), onAddPlaylist)
        }

        SectionLabel(stringResource(R.string.sec_playback))
        Group {
            SwitchRow(Icons.Rounded.PlayCircle, stringResource(R.string.set_resume), stringResource(R.string.set_resume_note), set.resumeLast) { v -> vm.updateSettings { it.copy(resumeLast = v) } }
            ChoiceRow(Icons.Rounded.AspectRatio, stringResource(R.string.set_aspect), stringResource(R.string.set_aspect_note), stringResource(set.aspect.label())) { vm.updateSettings { it.copy(aspect = it.aspect.next()) } }
            ChoiceRow(Icons.Rounded.HighQuality, stringResource(R.string.set_quality), stringResource(R.string.set_quality_note), stringResource(set.quality.label())) { vm.updateSettings { it.copy(quality = it.quality.next()) } }
            ChoiceRow(Icons.Rounded.Speed, stringResource(R.string.set_buffer), stringResource(R.string.set_buffer_note), stringResource(set.buffer.label())) { vm.updateSettings { it.copy(buffer = it.buffer.next()) } }
        }

        SectionLabel(stringResource(R.string.sec_channels))
        Group {
            SwitchRow(Icons.Rounded.VisibilityOff, stringResource(R.string.set_hide_offline), stringResource(R.string.set_hide_offline_note), set.hideOffline) { v -> vm.updateSettings { it.copy(hideOffline = v) } }
            SwitchRow(Icons.Rounded.Sync, stringResource(R.string.set_check_launch), stringResource(R.string.set_check_launch_note), set.checkOnLaunch) { v -> vm.updateSettings { it.copy(checkOnLaunch = v) } }
            SwitchRow(Icons.Rounded.Tag, stringResource(R.string.set_numbers), stringResource(R.string.set_numbers_note), set.showNumbers) { v -> vm.updateSettings { it.copy(showNumbers = v) } }
            SwitchRow(Icons.Rounded.Replay, stringResource(R.string.set_retry), stringResource(R.string.set_retry_note), set.retryBeforeHiding) { v -> vm.updateSettings { it.copy(retryBeforeHiding = v) } }
            SettingRow(Icons.Rounded.Block, stringResource(R.string.hidden_title), stringResource(R.string.hidden_row_note, s.offlineCount), onOpenHidden) {
                PillButton(stringResource(R.string.review), null, false, onOpenHidden)
            }
        }

        SectionLabel(stringResource(R.string.sec_interface))
        Group {
            ChoiceRow(Icons.Rounded.GridView, stringResource(R.string.set_card_size), stringResource(R.string.set_card_size_note), stringResource(set.cardSize.label())) { vm.updateSettings { it.copy(cardSize = it.cardSize.next()) } }
            SwitchRow(Icons.Rounded.Favorite, stringResource(R.string.set_open_fav), stringResource(R.string.set_open_fav_note), set.openOnFavorites) { v -> vm.updateSettings { it.copy(openOnFavorites = v) } }
            ChoiceRow(Icons.Rounded.Palette, stringResource(R.string.set_accent), stringResource(R.string.set_accent_note), stringResource(set.accent.label())) { vm.updateSettings { it.copy(accent = it.accent.next()) } }
        }

        SectionLabel(stringResource(R.string.sec_data))
        Group {
            SettingRow(Icons.Rounded.DeleteSweep, stringResource(R.string.set_clear_hidden), stringResource(R.string.set_clear_hidden_note), { vm.unhideAll() }) {
                PillButton(stringResource(R.string.reset), null, false, { vm.unhideAll() })
            }
            SettingRow(Icons.Rounded.Backup, stringResource(R.string.set_backup), stringResource(R.string.set_backup_note), null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PillButton(stringResource(R.string.export), null, false, { clipboard.setText(AnnotatedString(vm.exportFavorites())) })
                    PillButton(stringResource(R.string.import_), null, false, { vm.importFavorites(clipboard.getText()?.text) })
                }
            }
            SettingRow(Icons.Rounded.Info, stringResource(R.string.set_about), stringResource(R.string.set_about_note, version), null)
        }

        Text(
            stringResource(R.string.tip), color = LivoColors.Muted, fontSize = 12.sp,
            modifier = Modifier.padding(top = 8.dp).widthIn(max = 520.dp),
        )
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun AddPlaylistDialog(onDismiss: () -> Unit, onAdd: (String, String) -> Boolean) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = LivoColors.Bg3,
        title = { Text(stringResource(R.string.add_playlist), color = LivoColors.Ink) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.add_playlist_hint), color = LivoColors.Muted, fontSize = 12.sp)
                OutlinedTextField(
                    value = name, onValueChange = { name = it }, singleLine = true, shape = RoundedCornerShape(14.dp),
                    label = { Text(stringResource(R.string.playlist_name), color = LivoColors.Muted) },
                    colors = fieldColors(), modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = url, onValueChange = { url = it }, singleLine = true, shape = RoundedCornerShape(14.dp),
                    label = { Text(stringResource(R.string.playlist_link), color = LivoColors.Muted) },
                    placeholder = { Text(stringResource(R.string.playlist_link_hint), color = LivoColors.Muted) },
                    colors = fieldColors(), modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { if (onAdd(name, url)) onDismiss() }) {
                Text(stringResource(R.string.add_and_load), color = LivoColors.Gold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel), color = LivoColors.Muted) }
        },
    )
}
