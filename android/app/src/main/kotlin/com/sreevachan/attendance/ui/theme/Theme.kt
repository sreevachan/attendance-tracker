package com.sreevachan.attendance.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class AttendanceColors(
    val bg: Color,
    val card: Color,
    val ink: Color,
    val mute: Color,
    val line: Color,
    val good: Color,
    val goodBg: Color,
    val bad: Color,
    val badBg: Color,
    val warn: Color,
    val warnBg: Color,
    val hol: Color,
    val holBg: Color,
    val accent: Color,
)

private val LightAttendanceColors = AttendanceColors(
    bg = LightBg,
    card = LightCard,
    ink = LightInk,
    mute = LightMute,
    line = LightLine,
    good = LightGood,
    goodBg = LightGoodBg,
    bad = LightBad,
    badBg = LightBadBg,
    warn = LightWarn,
    warnBg = LightWarnBg,
    hol = LightHol,
    holBg = LightHolBg,
    accent = LightAccent,
)

private val DarkAttendanceColors = AttendanceColors(
    bg = DarkBg,
    card = DarkCard,
    ink = DarkInk,
    mute = DarkMute,
    line = DarkLine,
    good = DarkGood,
    goodBg = DarkGoodBg,
    bad = DarkBad,
    badBg = DarkBadBg,
    warn = DarkWarn,
    warnBg = DarkWarnBg,
    hol = DarkHol,
    holBg = DarkHolBg,
    accent = DarkAccent,
)

val LocalAttendanceColors = staticCompositionLocalOf { LightAttendanceColors }

private val LightColorScheme = lightColorScheme(
    primary = LightAccent,
    onPrimary = Color.White,
    background = LightBg,
    onBackground = LightInk,
    surface = LightCard,
    onSurface = LightInk,
    surfaceVariant = LightBg,
    onSurfaceVariant = LightMute,
    outline = LightLine,
    error = LightBad,
)

private val DarkColorScheme = darkColorScheme(
    primary = DarkAccent,
    onPrimary = DarkBg,
    background = DarkBg,
    onBackground = DarkInk,
    surface = DarkCard,
    onSurface = DarkInk,
    surfaceVariant = DarkBg,
    onSurfaceVariant = DarkMute,
    outline = DarkLine,
    error = DarkBad,
)

@Composable
fun AttendanceTheme(
    themeSetting: String = "system",
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeSetting) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }
    val attColors = if (darkTheme) DarkAttendanceColors else LightAttendanceColors
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    CompositionLocalProvider(LocalAttendanceColors provides attColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content,
        )
    }
}
