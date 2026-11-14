package com.victor.stream.ui.theme

import android.content.Context
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * The app's one fixed theme: pure black with the classic red accent,
 * matching the reference design. No user-selectable themes.
 */
data class ThemeSpec(
    val id: String,
    val label: String,
    val accent: Color,
    val accentDeep: Color,
    val onAccent: Color,
    val background: Color,
    val surface: Color,
    val surfaceHigh: Color,
    val glass: Color,
    val glassBorder: Color,
    val textPrimary: Color = Color.White,
    val textSecondary: Color = Color(0xFFB3B3B3),
    val textFaint: Color = Color(0xFF808080)
) {
    /** Accent gradient for filled buttons / selected pills. */
    val accentGradient: Brush
        get() = Brush.verticalGradient(colors = listOf(accent, accentDeep))
}

object AppTheme {
    /** The single app-wide theme. */
    val c: ThemeSpec = ThemeSpec(
        id = "stream",
        label = "Stream",
        accent = Color(0xFFE50914),
        accentDeep = Color(0xFFB20710),
        onAccent = Color.White,
        background = Color(0xFF000000),
        surface = Color(0xFF141414),
        surfaceHigh = Color(0xFF1F1F1F),
        glass = Color(0x14FFFFFF),
        glassBorder = Color(0x26FFFFFF)
    )

    /** Kept for call-site compatibility; the theme is fixed. */
    fun init(@Suppress("UNUSED_PARAMETER") context: Context) = Unit
}
