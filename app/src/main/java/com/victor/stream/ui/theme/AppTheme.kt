package com.victor.stream.ui.theme

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * The glossy dark design system for Stream.
 *
 * Everything visual (accent colour, surfaces, glass panels) comes from the
 * currently selected [ThemeSpec]. Screens read `AppTheme.c` inside composition,
 * so picking a new theme in Settings restyles the whole app instantly.
 *
 * This file deliberately keeps its own tiny SharedPreferences and touches
 * nothing in the data / streaming layers.
 */
data class ThemeSpec(
    val id: String,
    val label: String,
    /** Main brand/accent colour (buttons, highlights, selected items). */
    val accent: Color,
    /** Darker companion of the accent, used in gradients. */
    val accentDeep: Color,
    /** Text/icon colour drawn on top of the accent. */
    val onAccent: Color,
    /** App background. */
    val background: Color,
    /** Cards and panels. */
    val surface: Color,
    /** Slightly raised surfaces (chips, fields). */
    val surfaceHigh: Color,
    /** Translucent "glass" panel fill. */
    val glass: Color,
    /** Hairline border for glass panels and cards. */
    val glassBorder: Color,
    val textPrimary: Color = Color.White,
    val textSecondary: Color = Color(0xFFB3B3B8),
    val textFaint: Color = Color(0xFF7A7A82)
) {
    /** Soft vertical sheen used on glass panels (cheap — no real blur). */
    val glassSheen: Brush
        get() = Brush.verticalGradient(
            colors = listOf(Color(0x1FFFFFFF), Color(0x0AFFFFFF), Color(0x05FFFFFF))
        )

    /** Accent gradient for filled buttons / selected pills. */
    val accentGradient: Brush
        get() = Brush.verticalGradient(colors = listOf(accent, accentDeep))
}

object AppThemes {
    val Crimson = ThemeSpec(
        id = "crimson", label = "Crimson",
        accent = Color(0xFFE50914), accentDeep = Color(0xFFB00710), onAccent = Color.White,
        background = Color(0xFF0A0A0E), surface = Color(0xFF16161C), surfaceHigh = Color(0xFF21212A),
        glass = Color(0x14FFFFFF), glassBorder = Color(0x26FFFFFF)
    )
    val Ocean = ThemeSpec(
        id = "ocean", label = "Ocean",
        accent = Color(0xFF2E8BFF), accentDeep = Color(0xFF1B5FC4), onAccent = Color.White,
        background = Color(0xFF070B12), surface = Color(0xFF131A24), surfaceHigh = Color(0xFF1C2632),
        glass = Color(0x14AFD4FF), glassBorder = Color(0x2E9CC8FF)
    )
    val Emerald = ThemeSpec(
        id = "emerald", label = "Emerald",
        accent = Color(0xFF1FC779), accentDeep = Color(0xFF139255), onAccent = Color(0xFF04210F),
        background = Color(0xFF080D0B), surface = Color(0xFF131B18), surfaceHigh = Color(0xFF1C2823),
        glass = Color(0x14B8FFD9), glassBorder = Color(0x2E9CF0C4)
    )
    val Violet = ThemeSpec(
        id = "violet", label = "Violet",
        accent = Color(0xFF9B5CFF), accentDeep = Color(0xFF6E35C9), onAccent = Color.White,
        background = Color(0xFF0B0812), surface = Color(0xFF181325), surfaceHigh = Color(0xFF231C33),
        glass = Color(0x14D9C2FF), glassBorder = Color(0x2EC4A5FF)
    )
    val Amber = ThemeSpec(
        id = "amber", label = "Amber",
        accent = Color(0xFFFFB23E), accentDeep = Color(0xFFD98A16), onAccent = Color(0xFF241400),
        background = Color(0xFF0D0A06), surface = Color(0xFF1C1710), surfaceHigh = Color(0xFF282115),
        glass = Color(0x14FFE2B8), glassBorder = Color(0x2EFFD49C)
    )
    val Frost = ThemeSpec(
        id = "frost", label = "Frost",
        accent = Color(0xFFE8ECF2), accentDeep = Color(0xFFAEB6C2), onAccent = Color(0xFF14161A),
        background = Color(0xFF0B0C0E), surface = Color(0xFF17191D), surfaceHigh = Color(0xFF22252B),
        glass = Color(0x16FFFFFF), glassBorder = Color(0x2EFFFFFF)
    )

    val all = listOf(Crimson, Ocean, Emerald, Violet, Amber, Frost)

    fun byId(id: String?): ThemeSpec = all.firstOrNull { it.id == id } ?: Crimson
}

/**
 * Holds the active theme as Compose state. Reading [c] inside a composable
 * subscribes that composable to theme changes automatically.
 */
object AppTheme {
    private const val PREFS_NAME = "stream_theme_prefs"
    private const val KEY_THEME = "theme_id"

    var c: ThemeSpec by mutableStateOf(AppThemes.Crimson)
        private set

    fun init(context: Context) {
        val saved = context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_THEME, null)
        c = AppThemes.byId(saved)
    }

    fun select(context: Context, spec: ThemeSpec) {
        c = spec
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_THEME, spec.id)
            .apply()
    }
}
