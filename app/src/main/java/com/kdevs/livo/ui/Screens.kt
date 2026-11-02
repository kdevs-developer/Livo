package com.kdevs.livo.ui

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import android.text.format.DateUtils
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kdevs.livo.Filters
import com.kdevs.livo.LivoViewModel
import com.kdevs.livo.UiState
import com.kdevs.livo.data.Channel
import kotlinx.coroutines.delay

enum class Tab(val label: String, val icon: ImageVector) {
    Live("Live", Icons.Rounded.LiveTv),
    Favorites("Favorites", Icons.Rounded.Favorite),
    Search("Search", Icons.Rounded.Search),
    Settings("Settings", Icons.Rounded.Settings),
}

private fun isTv(ctx: Context) =
    (ctx.getSystemService(Context.UI_MODE_SERVICE) as UiModeManager).currentModeType == Configuration.UI_MODE_TYPE_TELEVISION

private fun checkedText(t: Long) =
    if (t == 0L) "not checked yet"
    else "checked " + DateUtils.getRelativeTimeSpanString(t, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS)

@Composable
fun LivoApp(vm: LivoViewModel) {
    val s by vm.state.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val wide = LocalConfiguration.current.screenWidthDp >= 600
    val tv = remember { isTv(ctx) }
    var tab by rememberSaveable { mutableStateOf(Tab.Live) }
    val grids = Tab.entries.associateWith { rememberLazyGridState() }

    val playing = s.playing
    if (playing != null) {
        PlayerScreen(
            channel = playing, isFav = playing.id in s.favIds,
            onBack = vm::stop, onNext = { vm.step(1) }, onPrev = { vm.step(-1) },
            onFav = { vm.toggleFav(playing) }, onFailed = { vm.markOffline(playing) },
        )
        return
    }

    Box(
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF132A33), LivoColors.Bg, LivoColors.Bg))),
    ) {
        Row(Modifier.fillMaxSize().systemBarsPadding()) {
            if (wide) Rail(tab) { tab = it }
            Column(Modifier.weight(1f)) {
                Box(Modifier.weight(1f)) {
                    Crossfade(tab, label = "tab") { t ->
                        when (t) {
                            Tab.Search -> SearchPage(s, vm, wide, tv, grids.getValue(t))
                            Tab.Settings -> SettingsPage(s, vm, wide)
                            else -> ChannelPage(t, s, vm, wide, tv, grids.getValue(t))
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
                if (!wide) BottomBar(tab) { tab = it }
            }
        }
    }
}

@Composable
private fun Rail(tab: Tab, onTab: (Tab) -> Unit) {
    Column(
        Modifier.fillMaxHeight().width(92.dp).background(Color(0x2E000000)).padding(vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(42.dp).clip(RoundedCornerShape(13.dp)).background(Color.White), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.PlayArrow, "Livo", tint = LivoColors.Bg, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.height(14.dp))
        Tab.entries.forEach { NavItem(it, it == tab, { onTab(it) }, Modifier.width(70.dp)) }
    }
}

@Composable
private fun BottomBar(tab: Tab, onTab: (Tab) -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(Color(0xCC0D161A)).padding(horizontal = 6.dp, vertical = 6.dp),
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
            .background(if (selected) LivoColors.Gold.copy(alpha = 0.1f) else Color.Transparent)
            .clickable(interactionSource = src, indication = null, onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(t.icon, t.label, tint = tint, modifier = Modifier.size(24.dp))
        Text(t.label, color = tint, fontSize = 10.sp, maxLines = 1)
    }
}

@Composable
private fun Header(title: String, s: UiState, compact: Boolean, onRefresh: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = LivoColors.Ink, fontSize = if (compact) 22.sp else 28.sp, fontWeight = FontWeight.Light, modifier = Modifier.weight(1f))
            PillButton(
                if (s.checking) "Checking ${s.checked}/${s.toCheck}" else if (compact) "Refresh" else "Refresh channels",
                Icons.Rounded.Refresh, false, onRefresh, spin = s.checking,
            )
        }
        Text("${s.offlineCount} offline hidden · ${checkedText(s.lastChecked)}", color = LivoColors.Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
    }
}

private val FullSpan: LazyGridItemSpanScope.() -> GridItemSpan = { GridItemSpan(maxLineSpan) }

@Composable
private fun cellMin(wide: Boolean, tv: Boolean): Dp = if (tv) 200.dp else if (wide) 176.dp else 150.dp

@Composable
private fun ChannelPage(mode: Tab, s: UiState, vm: LivoViewModel, wide: Boolean, tv: Boolean, state: LazyGridState) {
    val compact = !wide
    val pad = if (compact) 16.dp else 28.dp
    val title = when (mode) {Tab.Live -> "Live channels"; else -> "Favorites" }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(cellMin(wide, tv)), state = state,
        contentPadding = PaddingValues(horizontal = pad, vertical = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(span = FullSpan) { Header(title, s, compact) { vm.refresh() } }

        if (s.loading && s.all.isEmpty()) {
            item(span = FullSpan) {
                Column(Modifier.fillMaxWidth().padding(48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = LivoColors.Gold)
                    Text("Loading channels…", color = LivoColors.Muted, modifier = Modifier.padding(top = 12.dp))
                }
            }
            return@LazyVerticalGrid
        }
        if (s.error != null && s.all.isEmpty()) {
            item(span = FullSpan) {
                Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Couldn't load your playlist", color = LivoColors.Ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Text(s.error, color = LivoColors.Muted, fontSize = 12.sp, modifier = Modifier.padding(vertical = 8.dp))
                    PillButton("Try again", Icons.Rounded.Refresh, true, { vm.load() })
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
                    Text("No favorites yet. Tap the heart on any channel, or press and hold it, to add it here.", color = LivoColors.Muted, modifier = Modifier.padding(vertical = 32.dp))
                }
            }
            items(s.favorites, key = { "f_" + it.id }) { c -> ChannelCard(c, true, { vm.play(c, s.favorites) }, { vm.toggleFav(c) }) }
        } else {
            if (mode == Tab.Live && s.favorites.isNotEmpty()) {
                item(span = FullSpan) { SectionTitle("Your favorites", "${s.favorites.size} channels") }
                items(s.favorites, key = { "h_" + it.id }) { c -> ChannelCard(c, true, { vm.play(c, s.favorites) }, { vm.toggleFav(c) }) }
            }
            item(span = FullSpan) { SectionTitle("All channels", "${s.visible.size} online") }
            if (s.visible.isEmpty()) {
                item(span = FullSpan) {
                    Column(Modifier.padding(vertical = 24.dp)) {
                        Text("No online channels match these filters.", color = LivoColors.Muted, modifier = Modifier.padding(bottom = 12.dp))
                        PillButton("Clear filters", Icons.Rounded.Refresh, false, { vm.setFilters(Filters()) })
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
            placeholder = { Text("Search channels", color = LivoColors.Muted) },
            leadingIcon = { Icon(Icons.Rounded.Search, null, tint = LivoColors.Gold) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = LivoColors.Gold, unfocusedBorderColor = LivoColors.Line, cursorColor = LivoColors.Gold,
                focusedTextColor = LivoColors.Ink, unfocusedTextColor = LivoColors.Ink,
            ),
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
                    Text(if (q.trim().length < 2) "Type at least two letters." else "No online channel found.", color = LivoColors.Muted)
                }
            }
            items(results, key = { "s_" + it.id }) { c -> ChannelCard(c, c.id in s.favIds, { vm.play(c, results) }, { vm.toggleFav(c) }) }
        }
    }
}

@Composable
private fun SettingsPage(s: UiState, vm: LivoViewModel, wide: Boolean) {
    var url by remember(s.playlistUrl) { mutableStateOf(s.playlistUrl) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = if (wide) 28.dp else 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Settings", color = LivoColors.Ink, fontSize = 28.sp, fontWeight = FontWeight.Light)
        Text("Playlist (M3U link)", color = LivoColors.Muted, fontSize = 12.sp)
        OutlinedTextField(
            value = url, onValueChange = { url = it }, singleLine = true, shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = LivoColors.Gold, unfocusedBorderColor = LivoColors.Line, cursorColor = LivoColors.Gold,
                focusedTextColor = LivoColors.Ink, unfocusedTextColor = LivoColors.Ink,
            ),
            modifier = Modifier.fillMaxWidth().widthIn(max = 720.dp),
        )
        PillButton("Save and reload", Icons.Rounded.Refresh, true, { vm.setPlaylistUrl(url) })
        Text(
            "${s.all.size} channels in playlist · ${s.online.size} online · ${s.offlineCount} offline hidden",
            color = LivoColors.Ink, fontSize = 14.sp,
        )
        Text(
            "Tip: press and hold a channel to add or remove it from favorites. " +
                "Use Refresh on the Home or Live screen to check whether hidden channels are back.",
            color = LivoColors.Muted, fontSize = 12.sp, modifier = Modifier.widthIn(max = 520.dp),
        )
    }
}
