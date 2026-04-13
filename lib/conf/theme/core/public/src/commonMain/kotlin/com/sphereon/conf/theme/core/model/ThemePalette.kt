package com.sphereon.conf.theme.core.model

import com.sphereon.core.compat.JsExportCompat
import kotlinx.serialization.Serializable

/**
 * A generated tonal palette based on Material Design 3.
 * Contains the 5 key color role palettes plus individual tones.
 *
 * @property seedColor The original seed color in hex (e.g. "#6750A4")
 * @property primary Tonal palette for the primary color role
 * @property secondary Tonal palette for the secondary color role
 * @property tertiary Tonal palette for the tertiary color role
 * @property neutral Tonal palette for neutral surfaces
 * @property error Tonal palette for error states
 */
@JsExportCompat
@Serializable
data class ThemePalette(
    val seedColor: String,
    val primary: TonalPaletteResult,
    val secondary: TonalPaletteResult,
    val tertiary: TonalPaletteResult,
    val neutral: TonalPaletteResult,
    val error: TonalPaletteResult
)

/**
 * Extended palette with additional utility color roles for success, warning, and info states.
 */
@JsExportCompat
@Serializable
data class ExtendedThemePalette(
    val base: ThemePalette,
    val success: TonalPaletteResult,
    val warning: TonalPaletteResult,
    val info: TonalPaletteResult
)

/**
 * A set of tonal values at standard M3 tone stops.
 * Each value is a hex color string.
 */
@JsExportCompat
@Serializable
data class TonalPaletteResult(
    val tone0: String,
    val tone10: String,
    val tone20: String,
    val tone30: String,
    val tone40: String,
    val tone50: String,
    val tone60: String,
    val tone70: String,
    val tone80: String,
    val tone90: String,
    val tone95: String,
    val tone99: String,
    val tone100: String
)
