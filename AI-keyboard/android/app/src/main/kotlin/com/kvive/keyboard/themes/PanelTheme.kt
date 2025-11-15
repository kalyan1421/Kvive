package com.kvive.keyboard.themes

import android.graphics.Color
import androidx.core.graphics.ColorUtils

/**
 * Defines the fixed black & white palette used across auxiliary panels
 * (AI assistant, grammar, tone, emoji, settings, etc).
 */
object PanelTheme {
    private val backgroundColor = Color.parseColor("#101214")
    private val surfaceColor = Color.parseColor("#181A1D")
    private val pressedColor = ColorUtils.blendARGB(surfaceColor, Color.WHITE, 0.08f)
    private val borderColor = Color.parseColor("#2A2D32")
    private val accentColor = Color.WHITE
    private val accentContrastColor = Color.parseColor("#101010")
    private val textPrimaryColor = Color.WHITE
    private val textSecondaryColor = ColorUtils.setAlphaComponent(Color.WHITE, 210)
    private val textMutedColor = ColorUtils.setAlphaComponent(Color.WHITE, 160)

    private val staticPalette: ThemePaletteV2 by lazy {
        val base = KeyboardThemeV2.createDefault()
        val staticTheme = base.copy(
            background = base.background.copy(
                type = "solid",
                color = backgroundColor,
                imagePath = null,
                gradient = null,
                overlayEffects = emptyList(),
                adaptive = null
            ),
            keys = base.keys.copy(
                preset = "rounded",
                bg = surfaceColor,
                text = textPrimaryColor,
                pressed = pressedColor,
                border = base.keys.border.copy(
                    enabled = true,
                    color = borderColor,
                    widthDp = 1.0f
                ),
                shadow = base.keys.shadow.copy(
                    enabled = false,
                    elevationDp = 0f,
                    glow = false
                ),
                gradient = emptyList()
            ),
            specialKeys = base.specialKeys.copy(
                accent = accentColor,
                spaceLabelColor = textPrimaryColor
            ),
            effects = base.effects.copy(
                pressAnimation = "none",
                globalEffects = emptyList()
            ),
            stickers = base.stickers.copy(enabled = false),
            advanced = base.advanced.copy(
                dynamicTheme = "none",
                seasonalPack = "none"
            )
        )
        ThemePaletteV2(staticTheme)
    }

    val palette: ThemePaletteV2
        get() = staticPalette

    val background: Int = backgroundColor
    val surface: Int = surfaceColor
    val surfaceMuted: Int = ColorUtils.blendARGB(surfaceColor, Color.WHITE, 0.04f)
    val accent: Int = accentColor
    val accentContrast: Int = accentContrastColor
    val border: Int = borderColor
    val textPrimary: Int = textPrimaryColor
    val textSecondary: Int = textSecondaryColor
    val textMuted: Int = textMutedColor
}
