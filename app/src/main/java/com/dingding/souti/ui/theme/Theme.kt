package com.dingding.souti.ui.theme

import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * 主题模式：跟随系统 / 浅色 / 深色。
 * 由主页日月开关驱动，持久化到独立 prefs（ui 包内，不碰 repository 的 SettingsStore）。
 */
enum class SoutiThemeMode { SYSTEM, LIGHT, DARK }

object SoutiThemeController {
    private const val PREFS = "souti_ui"
    private const val KEY_MODE = "theme_mode"

    fun mode(ctx: Context): SoutiThemeMode = when (
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_MODE, "system")
    ) {
        "light" -> SoutiThemeMode.LIGHT
        "dark" -> SoutiThemeMode.DARK
        else -> SoutiThemeMode.SYSTEM
    }

    fun setMode(ctx: Context, mode: SoutiThemeMode) {
        val s = when (mode) {
            SoutiThemeMode.LIGHT -> "light"
            SoutiThemeMode.DARK -> "dark"
            SoutiThemeMode.SYSTEM -> "system"
        }
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_MODE, s).apply()
    }
}

/**
 * 玻璃拟态配色 token（对应 APP 图标：深蓝黑玻璃 + 青绿发光 + 顶部高光）。
 * 浅色 = 白天基调，深色 = 夜晚基调。
 */
data class SoutiGlass(
    val bgTop: Color, val bgMid: Color, val bgBottom: Color,
    val surface: Color, val surfaceBorder: Color, val surfaceHighlight: Color,
    val textPrimary: Color, val textSecondary: Color,
    val primary: Color, val primaryVariant: Color, val onPrimary: Color,
    val glowA: Color, val glowB: Color,
)

val GlassLight = SoutiGlass(
    bgTop = Color(0xFFF3F9FC), bgMid = Color(0xFFE8F1F6), bgBottom = Color(0xFFDCEBF2),
    surface = Color(0xA8FFFFFF),            // rgba(255,255,255,0.66)
    surfaceBorder = Color(0xD9FFFFFF),      // rgba(255,255,255,0.85)
    surfaceHighlight = Color(0xE6FFFFFF),   // rgba(255,255,255,0.90)
    textPrimary = Color(0xFF16324A), textSecondary = Color(0x9916324A),
    primary = Color(0xFF1D9E75), primaryVariant = Color(0xFF2BBDA0), onPrimary = Color.White,
    glowA = Color(0x423ECFCF), glowB = Color(0x381D9E75),
)

val GlassDark = SoutiGlass(
    bgTop = Color(0xFF123A52), bgMid = Color(0xFF0D1B2A), bgBottom = Color(0xFF0A1428),
    surface = Color(0x7314283C),            // rgba(20,40,60,0.45)
    surfaceBorder = Color(0x385AA0BE),      // rgba(90,160,190,0.22)
    surfaceHighlight = Color(0x14FFFFFF),   // rgba(255,255,255,0.08)
    textPrimary = Color(0xFFE8F2FB), textSecondary = Color(0x99E8F2FB),
    primary = Color(0xFF34D399), primaryVariant = Color(0xFF3ECFCF), onPrimary = Color(0xFF04342C),
    glowA = Color(0x423ECFCF), glowB = Color(0x401D9E75),
)

val LocalGlass = staticCompositionLocalOf { GlassLight }

private val LightColors = lightColorScheme(
    primary = Color(0xFF1D9E75),
    onPrimary = Color.White,
    background = Color(0xFFF3F9FC),
    surface = Color.White,
    error = Color(0xFFE24B4A)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF34D399),
    onPrimary = Color(0xFF04342C),
    background = Color(0xFF0D1B2A),
    surface = Color(0xFF14283C),
    error = Color(0xFFF09595)
)

@Composable
fun SoutiAssistantTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val glass = if (darkTheme) GlassDark else GlassLight
    CompositionLocalProvider(LocalGlass provides glass) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            content = content
        )
    }
}
