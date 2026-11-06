package com.kdevs.livo.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import com.kdevs.livo.Filters
import com.kdevs.livo.R
import com.kdevs.livo.data.Channel
import com.kdevs.livo.data.Playlist

/** Spring-scale + gold ring when focused (D-pad on TV, keyboard, or touch focus). */
@Composable
fun Modifier.premiumFocus(
    shape: Shape,
    source: MutableInteractionSource,
    focusScale: Float = 1.06f,
    showIdleBorder: Boolean = true,
): Modifier {
    val focused by source.collectIsFocusedAsState()
    val s by animateFloatAsState(
        if (focused) focusScale else 1f,
        spring(dampingRatio = 0.65f, stiffness = Spring.StiffnessMediumLow), label = "focus",
    )
    val m = this.scale(s)
    return when {
        focused -> m.border(2.dp, LivoColors.Gold, shape)
        showIdleBorder -> m.border(1.dp, LivoColors.Line, shape)
        else -> m
    }
}

private val tintRes = listOf(
    R.color.tint_1, R.color.tint_2, R.color.tint_3, R.color.tint_4,
    R.color.tint_5, R.color.tint_6, R.color.tint_7, R.color.tint_8,
)

@Composable
private fun tint(name: String): Color = colorResource(tintRes[(name.hashCode() and 0x7fffffff) % tintRes.size])

/** "Category · Language, Language" */
fun Channel.subtitle(): String = "$category · ${languages.joinToString(", ")}"
private fun initials(name: String) =
    name.split(" ").filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }

@Composable
fun LiveDot() {
    val t = rememberInfiniteTransition(label = "live")
    val a by t.animateFloat(0.35f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "a")
    Box(Modifier.size(7.dp).alpha(a).background(LivoColors.Live, CircleShape))
}

@Composable
fun PillButton(
    text: String, icon: ImageVector?, filled: Boolean, onClick: () -> Unit,
    modifier: Modifier = Modifier, spin: Boolean = false,
) {
    val src = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(50)
    val t = rememberInfiniteTransition(label = "spin")
    val r by t.animateFloat(0f, 360f, infiniteRepeatable(tween(1000, easing = LinearEasing)), label = "r")
    Row(
        modifier.premiumFocus(shape, src, 1.05f, showIdleBorder = !filled).clip(shape)
            .background(if (filled) LivoColors.Gold else LivoColors.Bg2)
            .clickable(interactionSource = src, indication = null, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                icon, null, tint = if (filled) LivoColors.OnGold else LivoColors.Gold,
                modifier = Modifier.size(18.dp).rotate(if (spin) r else 0f),
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text, color = if (filled) LivoColors.OnGold else LivoColors.Ink,
            fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
        )
    }
}

@Composable
fun FavButton(fav: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val src = remember { MutableInteractionSource() }
    val pop by animateFloatAsState(
        if (fav) 1f else 0.85f, spring(Spring.DampingRatioHighBouncy, Spring.StiffnessMedium), label = "pop",
    )
    Box(
        modifier.size(32.dp).premiumFocus(CircleShape, src, 1.15f, showIdleBorder = false).clip(CircleShape)
            .background(LivoColors.ScrimSoft)
            .clickable(interactionSource = src, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (fav) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, stringResource(R.string.cd_favorite),
            tint = if (fav) LivoColors.Gold else LivoColors.Ink,
            modifier = Modifier.size(18.dp).scale(pop),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChannelCard(c: Channel, fav: Boolean, onClick: () -> Unit, onFav: () -> Unit, modifier: Modifier = Modifier) {
    val src = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier.premiumFocus(shape, src).clip(shape).background(LivoColors.Bg2)
            .combinedClickable(interactionSource = src, indication = null, onClick = onClick, onLongClick = onFav)
            .padding(10.dp),
    ) {
        Box(
            Modifier.fillMaxWidth().height(72.dp).clip(RoundedCornerShape(11.dp))
                .background(Brush.linearGradient(listOf(tint(c.name), LivoColors.CardEnd))),
            contentAlignment = Alignment.Center,
        ) {
            SubcomposeAsyncImage(
                model = c.logo, contentDescription = c.name, contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().padding(10.dp),
            ) {
                if (painter.state is AsyncImagePainter.State.Success) {
                    SubcomposeAsyncImageContent()
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(initials(c.name), color = LivoColors.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            FavButton(fav, onFav, Modifier.align(Alignment.TopEnd).padding(2.dp))
            if (LocalSettings.current.showNumbers && c.number > 0) {
                Text(
                    c.number.toString(), color = LivoColors.Ink, fontSize = 10.sp,
                    modifier = Modifier.align(Alignment.TopStart).padding(4.dp)
                        .clip(RoundedCornerShape(8.dp)).background(LivoColors.ScrimSoft)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(c.name, color = LivoColors.Ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            c.subtitle(),
            color = LivoColors.Muted,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun SectionTitle(text: String, note: String) {
    Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.Bottom) {
        Text(text, color = LivoColors.Ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        Text(note, color = LivoColors.Muted, fontSize = 12.sp)
    }
}

@Composable
fun HeroBanner(c: Channel?, compact: Boolean, onWatch: () -> Unit) {
    val t = rememberInfiniteTransition(label = "hero")
    val glow by t.animateFloat(0.15f, 0.4f, infiniteRepeatable(tween(3500, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "g")
    Box(
        Modifier.fillMaxWidth().height(if (compact) 150.dp else 200.dp).clip(RoundedCornerShape(20.dp))
            .background(Brush.linearGradient(listOf(LivoColors.Hero1, LivoColors.Hero2, LivoColors.Hero3))),
    ) {
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Color.Transparent, LivoColors.Gold.copy(alpha = glow)))))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, LivoColors.ScrimHeavy))))
        Column(Modifier.align(Alignment.BottomStart).padding(if (compact) 16.dp else 24.dp)) {
            if (c == null) {
                Text(stringResource(R.string.hero_empty), color = LivoColors.Ink, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LiveDot(); Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.live_now), color = LivoColors.Gold, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
                Text(c.name, color = LivoColors.White, fontSize = if (compact) 20.sp else 26.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    c.subtitle(),
                    color = LivoColors.Muted,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(10.dp))
                PillButton(stringResource(R.string.watch_live), Icons.Rounded.PlayArrow, true, onWatch)
            }
        }
    }
}

/** Tappable card that shows the current choice and opens a picker dialog. Focus = solid gold fill. */
@Composable
private fun PickerButton(
    icon: ImageVector, label: String, value: String, active: Boolean,
    onClick: () -> Unit, modifier: Modifier = Modifier,
) {
    val src = remember { MutableInteractionSource() }
    val focused by src.collectIsFocusedAsState()
    val shape = RoundedCornerShape(16.dp)
    val bg by animateColorAsState(if (focused) LivoColors.Gold else LivoColors.Glass, label = "pkBg")
    val main = if (focused) LivoColors.OnGold else if (active) LivoColors.Gold else LivoColors.Ink
    val sub = if (focused) LivoColors.OnGold.copy(alpha = 0.7f) else LivoColors.Muted
    Row(
        modifier.clip(shape).background(bg)
            .border(1.dp, if (focused) LivoColors.Gold else LivoColors.Line, shape)
            .clickable(interactionSource = src, indication = null, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(32.dp).clip(RoundedCornerShape(10.dp))
                .background(if (focused) LivoColors.OnGold.copy(alpha = 0.12f) else LivoColors.Gold.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = if (focused) LivoColors.OnGold else LivoColors.Gold, modifier = Modifier.size(18.dp)) }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(label, color = sub, fontSize = 10.sp, maxLines = 1)
            Text(value, color = main, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.Rounded.UnfoldMore, null, tint = sub, modifier = Modifier.size(18.dp))
    }
}

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
private fun PickerRow(text: String, selected: Boolean, focusOnOpen: Boolean, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val focused by src.collectIsFocusedAsState()
    val fr = remember { FocusRequester() }
    val shape = RoundedCornerShape(14.dp)
    val bg by animateColorAsState(
        when { focused -> LivoColors.Gold; selected -> LivoColors.Gold.copy(alpha = 0.14f); else -> LivoColors.Glass },
        label = "rowBg",
    )
    val fg by animateColorAsState(
        when { focused -> LivoColors.OnGold; selected -> LivoColors.Gold; else -> LivoColors.Ink },
        label = "rowFg",
    )
    val keyboard = LocalInputModeManager.current.inputMode == InputMode.Keyboard
    if (focusOnOpen && keyboard) LaunchedEffect(Unit) { runCatching { fr.requestFocus() } }
    Row(
        Modifier.fillMaxWidth().then(if (focusOnOpen && keyboard) Modifier.focusRequester(fr) else Modifier)
            .clip(shape).background(bg)
            .border(1.dp, if (focused || selected) LivoColors.Gold else LivoColors.Line, shape)
            .clickable(interactionSource = src, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text, color = fg, fontSize = 14.sp, fontWeight = if (selected || focused) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
        )
        if (selected) Icon(Icons.Rounded.CheckCircle, null, tint = fg, modifier = Modifier.size(20.dp))
    }
}

/** List picker dialog. Opens scrolled to, and focused on, the last selected option. */
@Composable
private fun PickerDialog(
    title: String, icon: ImageVector, options: List<Pair<String?, String>>, current: String?,
    onSelect: (String?) -> Unit, onDismiss: () -> Unit, footer: (@Composable () -> Unit)? = null,
) {
    val shape = RoundedCornerShape(24.dp)
    val selectedIndex = options.indexOfFirst { it.first == current }.coerceAtLeast(0)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (selectedIndex - 2).coerceAtLeast(0))
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.fillMaxWidth(0.92f).widthIn(max = 460.dp).heightIn(max = 520.dp)
                .clip(shape).background(LivoColors.Bg2).border(1.dp, LivoColors.Line, shape).padding(vertical = 16.dp),
        ) {
            Row(Modifier.padding(horizontal = 20.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = LivoColors.Gold, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
                Text(title, color = LivoColors.Ink, fontSize = 18.sp, fontWeight = FontWeight.Light, modifier = Modifier.weight(1f))
                Text("${options.size}", color = LivoColors.Muted, fontSize = 12.sp)
            }
            LazyColumn(
                Modifier.weight(1f, fill = false), state = listState,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                itemsIndexed(options, key = { _, o -> o.first ?: "__all__" }) { i, (key, text) ->
                    PickerRow(text, key == current, focusOnOpen = i == selectedIndex) { onSelect(key) }
                }
            }
            if (footer != null) Box(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) { footer() }
        }
    }
}

/** Playlist and Category pickers. Each dialog opens on the last selected option. */
@Composable
fun FilterBar(
    f: Filters, @Suppress("UNUSED_PARAMETER") languages: List<String>, categories: List<String>,
    playlists: List<Playlist>, activeId: String, compact: Boolean, @Suppress("UNUSED_PARAMETER") showLanguage: Boolean = true,
    onPlaylist: (String) -> Unit, onAddPlaylist: () -> Unit, onChange: (Filters) -> Unit,
) {
    val all = stringResource(R.string.all)
    var picker by remember { mutableStateOf<String?>(null) } // "playlist" | "category"
    val playlistName = playlists.firstOrNull { it.id == activeId }?.name ?: all

    Row(Modifier.fillMaxWidth().widthIn(max = 560.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        PickerButton(
            Icons.Rounded.PlaylistPlay, stringResource(R.string.filter_playlist), playlistName, false,
            { picker = "playlist" }, Modifier.weight(1f),
        )
        PickerButton(
            Icons.Rounded.GridView, stringResource(R.string.category), f.category ?: all, f.category != null,
            { picker = "category" }, Modifier.weight(1f),
        )
    }

    when (picker) {
        "playlist" -> PickerDialog(
            stringResource(R.string.filter_playlist), Icons.Rounded.PlaylistPlay,
            playlists.map<Playlist, Pair<String?, String>> { it.id to it.name }, activeId,
            onSelect = { key -> picker = null; if (key != null) onPlaylist(key) },
            onDismiss = { picker = null },
            footer = { PillButton(stringResource(R.string.add_playlist), Icons.Rounded.Add, false, { picker = null; onAddPlaylist() }) },
        )
        "category" -> PickerDialog(
            stringResource(R.string.category), Icons.Rounded.GridView,
            listOf<Pair<String?, String>>(null to all) + categories.map { it to it }, f.category,
            onSelect = { picker = null; onChange(f.copy(category = it)) }, onDismiss = { picker = null },
        )
    }
}

/** Round icon button used by the player controls. */
@Composable
fun RoundButton(
    icon: ImageVector, description: String, onClick: () -> Unit,
    modifier: Modifier = Modifier, size: Dp = 48.dp, primary: Boolean = false,
) {
    val src = remember { MutableInteractionSource() }
    Box(
        modifier.size(size).premiumFocus(CircleShape, src, 1.12f, showIdleBorder = false).clip(CircleShape)
            .background(if (primary) LivoColors.Gold else LivoColors.GlassButton)
            .clickable(interactionSource = src, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon, description, tint = if (primary) LivoColors.OnGold else LivoColors.White,
            modifier = Modifier.size(size * 0.5f),
        )
    }
}