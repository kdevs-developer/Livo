package com.kdevs.livo.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import com.kdevs.livo.Accent
import com.kdevs.livo.AppSettings
import com.kdevs.livo.R

class AccentColors(val main: Color, val on: Color)

val LocalAccent = staticCompositionLocalOf<AccentColors> { error("LivoTheme is missing") }
val LocalSettings = staticCompositionLocalOf { AppSettings() }

/** Every color below comes from res/values/colors.xml. Edit the XML to re-skin the app. */
object LivoColors {
    val Bg: Color @Composable get() = colorResource(R.color.livo_bg)
    val Bg2: Color @Composable get() = colorResource(R.color.livo_bg2)
    val Bg3: Color @Composable get() = colorResource(R.color.livo_bg3)
    val Ink: Color @Composable get() = colorResource(R.color.livo_ink)
    val InkSoft: Color @Composable get() = colorResource(R.color.livo_ink_soft)
    val Muted: Color @Composable get() = colorResource(R.color.livo_muted)
    val Line: Color @Composable get() = colorResource(R.color.livo_line)
    val Live: Color @Composable get() = colorResource(R.color.livo_live)
    val White: Color @Composable get() = colorResource(R.color.livo_white)
    val Scrim: Color @Composable get() = colorResource(R.color.livo_scrim)
    val ScrimSoft: Color @Composable get() = colorResource(R.color.livo_scrim_soft)
    val ScrimHeavy: Color @Composable get() = colorResource(R.color.livo_scrim_heavy)
    val Glass: Color @Composable get() = colorResource(R.color.livo_glass)
    val GlassButton: Color @Composable get() = colorResource(R.color.livo_glass_button)
    val Rail: Color @Composable get() = colorResource(R.color.livo_rail)
    val Bar: Color @Composable get() = colorResource(R.color.livo_bar)
    val HeaderTop: Color @Composable get() = colorResource(R.color.livo_header_top)
    val CardEnd: Color @Composable get() = colorResource(R.color.livo_card_end)
    val Hero1: Color @Composable get() = colorResource(R.color.livo_hero_1)
    val Hero2: Color @Composable get() = colorResource(R.color.livo_hero_2)
    val Hero3: Color @Composable get() = colorResource(R.color.livo_hero_3)

    /** The accent follows the "Accent color" setting. */
    val Gold: Color @Composable get() = LocalAccent.current.main
    val OnGold: Color @Composable get() = LocalAccent.current.on
}

@Composable
fun LivoTheme(accent: Accent = Accent.Gold, content: @Composable () -> Unit) {
    val colors = when (accent) {
        Accent.Gold -> AccentColors(colorResource(R.color.livo_gold), colorResource(R.color.livo_on_gold))
        Accent.Teal -> AccentColors(colorResource(R.color.accent_teal), colorResource(R.color.accent_teal_on))
        Accent.Rose -> AccentColors(colorResource(R.color.accent_rose), colorResource(R.color.accent_rose_on))
    }
    CompositionLocalProvider(LocalAccent provides colors) {
        MaterialTheme(
            colorScheme = darkColorScheme(
                primary = colors.main, onPrimary = colors.on,
                background = LivoColors.Bg, onBackground = LivoColors.Ink,
                surface = LivoColors.Bg2, onSurface = LivoColors.Ink,
                onSurfaceVariant = LivoColors.Muted,
            ),
            content = content,
        )
    }
}
