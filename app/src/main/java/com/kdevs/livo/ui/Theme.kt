package com.kdevs.livo.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object LivoColors {
    val Bg = Color(0xFF0A1114)
    val Bg2 = Color(0xFF121C20)
    val Bg3 = Color(0xFF16242A)
    val Ink = Color(0xFFEEE9DF)
    val Muted = Color(0xFF8B979B)
    val Gold = Color(0xFFE3B873)
    val OnGold = Color(0xFF1A1408)
    val Line = Color(0x17EEE9DF)
    val Live = Color(0xFFFF5D5D)
}

@Composable
fun LivoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = LivoColors.Gold, onPrimary = LivoColors.OnGold,
            background = LivoColors.Bg, onBackground = LivoColors.Ink,
            surface = LivoColors.Bg2, onSurface = LivoColors.Ink,
            onSurfaceVariant = LivoColors.Muted,
        ),
        content = content,
    )
}
