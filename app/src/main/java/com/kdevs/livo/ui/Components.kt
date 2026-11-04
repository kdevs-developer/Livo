package com.kdevs.livo.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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

@Composable
private fun FilterSlot(
    icon: ImageVector, label: String, current: String?, options: List<Pair<String?, String>>,
    showIcon: Boolean, onSelect: (String?) -> Unit, modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    val src = remember { MutableInteractionSource() }
    val focused by src.collectIsFocusedAsState()
    val rot by animateFloatAsState(if (open) 180f else 0f, label = "chev")
    val value = options.firstOrNull { it.first == current }?.second ?: stringResource(R.string.all)
    Box(modifier) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                .background(if (focused) LivoColors.Gold.copy(alpha = 0.16f) else Color.Transparent)
                .clickable(interactionSource = src, indication = null) { open = true }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showIcon) {
                Icon(icon, null, tint = LivoColors.Gold, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(label, color = LivoColors.Muted, fontSize = 10.sp, maxLines = 1)
                Text(
                    value, color = if (current != null) LivoColors.Gold else LivoColors.Ink, fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(Icons.Rounded.ExpandMore, null, tint = LivoColors.Muted, modifier = Modifier.size(18.dp).rotate(rot))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = LivoColors.Bg3) {
            options.forEach { (key, text) ->
                DropdownMenuItem(
                    text = { Text(text, color = if (key == current) LivoColors.Gold else LivoColors.Ink) },
                    onClick = { open = false; onSelect(key) },
                )
            }
        }
    }
}

@Composable
fun FilterBar(f: Filters, languages: List<String>, categories: List<String>, compact: Boolean, onChange: (Filters) -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    val none = listOf<Pair<String?, String>>(null to stringResource(R.string.all))
    Row(
        Modifier.fillMaxWidth().widthIn(max = 680.dp).clip(shape).background(LivoColors.Glass).border(1.dp, LivoColors.Line, shape),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilterSlot(Icons.Rounded.Language, stringResource(R.string.language), f.language, none + languages.map { it to it }, !compact, { onChange(f.copy(language = it)) }, Modifier.weight(1f))
        Box(Modifier.width(1.dp).height(28.dp).background(LivoColors.Line))
        FilterSlot(Icons.Rounded.Category, stringResource(R.string.category), f.category, none + categories.map { it to it }, !compact, { onChange(f.copy(category = it)) }, Modifier.weight(1f))
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
