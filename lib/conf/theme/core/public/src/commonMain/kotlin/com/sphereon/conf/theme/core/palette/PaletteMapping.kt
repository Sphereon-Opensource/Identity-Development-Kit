package com.sphereon.conf.theme.core.palette

import com.sphereon.core.compat.JsExportCompat
import kotlinx.serialization.Serializable

/**
 * A reference from an M3 semantic token to a specific palette scale + stop.
 */
@JsExportCompat
@Serializable
data class PaletteRef(val scale: String, val stop: Int)

/**
 * Configurable mapping from palette 50–900 stops to M3 semantic token keys.
 * This is pure data — serializable, storable in the theme database, usable in Figma sync.
 */
@JsExportCompat
@Serializable
data class PaletteMapping(
    val light: Map<String, PaletteRef>,
    val dark: Map<String, PaletteRef>,
)

/**
 * Default mapping from design system palette stops to M3 semantic tokens.
 * Follows the DESIGN-SYSTEM.md table.
 */
object DefaultPaletteMapping {

    val LIGHT: Map<String, PaletteRef> = mapOf(
        // Primary
        "color.primary" to PaletteRef("brand", 500),
        "color.onPrimary" to PaletteRef("brand", 50),
        "color.primaryContainer" to PaletteRef("brand", 100),
        "color.onPrimaryContainer" to PaletteRef("brand", 900),

        // Secondary
        "color.secondary" to PaletteRef("secondary", 500),
        "color.onSecondary" to PaletteRef("secondary", 50),
        "color.secondaryContainer" to PaletteRef("secondary", 100),
        "color.onSecondaryContainer" to PaletteRef("secondary", 900),

        // Tertiary — mapped from brand with offset stops
        "color.tertiary" to PaletteRef("brand", 400),
        "color.onTertiary" to PaletteRef("brand", 50),
        "color.tertiaryContainer" to PaletteRef("brand", 100),
        "color.onTertiaryContainer" to PaletteRef("brand", 800),

        // Error
        "color.error" to PaletteRef("error", 500),
        "color.onError" to PaletteRef("error", 50),
        "color.errorContainer" to PaletteRef("error", 100),
        "color.onErrorContainer" to PaletteRef("error", 900),

        // Surface / Neutral
        "color.surface" to PaletteRef("neutral", 50),
        "color.onSurface" to PaletteRef("neutral", 800),
        "color.surfaceVariant" to PaletteRef("neutral", 100),
        "color.onSurfaceVariant" to PaletteRef("neutral", 700),
        "color.surfaceContainer" to PaletteRef("neutral", 100),
        "color.surfaceContainerHigh" to PaletteRef("neutral", 100),
        "color.surfaceContainerHighest" to PaletteRef("neutral", 200),
        "color.surfaceContainerLow" to PaletteRef("neutral", 50),
        "color.surfaceContainerLowest" to PaletteRef("neutral", 50),
        "color.background" to PaletteRef("neutral", 50),
        "color.onBackground" to PaletteRef("neutral", 800),
        "color.outline" to PaletteRef("neutral", 400),
        "color.outlineVariant" to PaletteRef("neutral", 200),
        "color.inverseSurface" to PaletteRef("neutral", 800),
        "color.inverseOnSurface" to PaletteRef("neutral", 50),
        "color.inversePrimary" to PaletteRef("brand", 200),

        // Feedback
        "color.feedback.success" to PaletteRef("success", 600),
        "color.feedback.successContainer" to PaletteRef("success", 100),
        "color.feedback.onSuccess" to PaletteRef("success", 50),
        "color.feedback.onSuccessContainer" to PaletteRef("success", 900),
        "color.feedback.warning" to PaletteRef("warning", 600),
        "color.feedback.warningContainer" to PaletteRef("warning", 100),
        "color.feedback.onWarning" to PaletteRef("warning", 50),
        "color.feedback.onWarningContainer" to PaletteRef("warning", 900),
        "color.feedback.info" to PaletteRef("info", 600),
        "color.feedback.infoContainer" to PaletteRef("info", 100),
        "color.feedback.onInfo" to PaletteRef("info", 50),
        "color.feedback.onInfoContainer" to PaletteRef("info", 900),

        // Text
        "color.text.primary" to PaletteRef("neutral", 800),
        "color.text.secondary" to PaletteRef("neutral", 600),
        "color.text.disabled" to PaletteRef("neutral", 400),
        "color.text.inverse" to PaletteRef("neutral", 50),

        // Border
        "color.border.default" to PaletteRef("neutral", 300),
        "color.border.strong" to PaletteRef("neutral", 500),
        "color.border.subtle" to PaletteRef("neutral", 200),
        "color.border.disabled" to PaletteRef("neutral", 200),

        // Interactive
        "color.interactive.hover" to PaletteRef("brand", 600),
        "color.interactive.pressed" to PaletteRef("brand", 700),
        "color.interactive.disabled" to PaletteRef("neutral", 300),
        "color.interactive.focus" to PaletteRef("brand", 500),
    )

    val DARK: Map<String, PaletteRef> = mapOf(
        // Primary
        "color.primary" to PaletteRef("brand", 400),
        "color.onPrimary" to PaletteRef("brand", 50),
        "color.primaryContainer" to PaletteRef("brand", 700),
        "color.onPrimaryContainer" to PaletteRef("brand", 100),

        // Secondary
        "color.secondary" to PaletteRef("secondary", 300),
        "color.onSecondary" to PaletteRef("secondary", 50),
        "color.secondaryContainer" to PaletteRef("secondary", 700),
        "color.onSecondaryContainer" to PaletteRef("secondary", 100),

        // Tertiary — mapped from brand with offset stops
        "color.tertiary" to PaletteRef("brand", 300),
        "color.onTertiary" to PaletteRef("brand", 900),
        "color.tertiaryContainer" to PaletteRef("brand", 700),
        "color.onTertiaryContainer" to PaletteRef("brand", 200),

        // Error
        "color.error" to PaletteRef("error", 300),
        "color.onError" to PaletteRef("error", 50),
        "color.errorContainer" to PaletteRef("error", 700),
        "color.onErrorContainer" to PaletteRef("error", 100),

        // Surface / Neutral
        "color.surface" to PaletteRef("neutral", 900),
        "color.onSurface" to PaletteRef("neutral", 100),
        "color.surfaceVariant" to PaletteRef("neutral", 700),
        "color.onSurfaceVariant" to PaletteRef("neutral", 200),
        "color.surfaceContainer" to PaletteRef("neutral", 800),
        "color.surfaceContainerHigh" to PaletteRef("neutral", 700),
        "color.surfaceContainerHighest" to PaletteRef("neutral", 600),
        "color.surfaceContainerLow" to PaletteRef("neutral", 900),
        "color.surfaceContainerLowest" to PaletteRef("neutral", 900),
        "color.background" to PaletteRef("neutral", 900),
        "color.onBackground" to PaletteRef("neutral", 100),
        "color.outline" to PaletteRef("neutral", 500),
        "color.outlineVariant" to PaletteRef("neutral", 700),
        "color.inverseSurface" to PaletteRef("neutral", 100),
        "color.inverseOnSurface" to PaletteRef("neutral", 800),
        "color.inversePrimary" to PaletteRef("brand", 600),

        // Feedback
        "color.feedback.success" to PaletteRef("success", 400),
        "color.feedback.successContainer" to PaletteRef("success", 800),
        "color.feedback.onSuccess" to PaletteRef("success", 50),
        "color.feedback.onSuccessContainer" to PaletteRef("success", 100),
        "color.feedback.warning" to PaletteRef("warning", 400),
        "color.feedback.warningContainer" to PaletteRef("warning", 800),
        "color.feedback.onWarning" to PaletteRef("warning", 50),
        "color.feedback.onWarningContainer" to PaletteRef("warning", 100),
        "color.feedback.info" to PaletteRef("info", 400),
        "color.feedback.infoContainer" to PaletteRef("info", 800),
        "color.feedback.onInfo" to PaletteRef("info", 50),
        "color.feedback.onInfoContainer" to PaletteRef("info", 100),

        // Text
        "color.text.primary" to PaletteRef("neutral", 100),
        "color.text.secondary" to PaletteRef("neutral", 300),
        "color.text.disabled" to PaletteRef("neutral", 500),
        "color.text.inverse" to PaletteRef("neutral", 800),

        // Border
        "color.border.default" to PaletteRef("neutral", 600),
        "color.border.strong" to PaletteRef("neutral", 400),
        "color.border.subtle" to PaletteRef("neutral", 700),
        "color.border.disabled" to PaletteRef("neutral", 700),

        // Interactive
        "color.interactive.hover" to PaletteRef("brand", 300),
        "color.interactive.pressed" to PaletteRef("brand", 400),
        "color.interactive.disabled" to PaletteRef("neutral", 600),
        "color.interactive.focus" to PaletteRef("brand", 400),
    )

    val DEFAULT = PaletteMapping(light = LIGHT, dark = DARK)
}
