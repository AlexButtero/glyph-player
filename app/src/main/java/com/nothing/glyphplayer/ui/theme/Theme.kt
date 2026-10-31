package com.nothing.glyphplayer.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Red = Color(0xFFD71921)
val Black = Color(0xFF000000)
val White = Color(0xFFFFFFFF)
val Surface = Color(0xFF121212)
val Muted = Color(0xFF8E8E8E)
val Divider = Color(0xFF262626)

private val Colors = darkColorScheme(
    primary = Red,
    background = Black,
    surface = Surface,
    onPrimary = White,
    onBackground = White,
    onSurface = White,
    secondary = Muted,
    outline = Divider
)

private val Mono = FontFamily.Monospace

val Typography = Typography(
    headlineLarge = TextStyle(fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 28.sp, letterSpacing = (-1).sp),
    headlineSmall = TextStyle(fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 18.sp),
    bodyMedium = TextStyle(fontFamily = Mono, fontSize = 14.sp),
    labelSmall = TextStyle(fontFamily = Mono, fontSize = 11.sp, letterSpacing = 2.sp)
)

@Composable
fun GlyphTheme(content: @Composable () -> Unit) =
    MaterialTheme(colorScheme = Colors, typography = Typography, content = content)
