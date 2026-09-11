/*
 * Copyright 2023-2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.sphereon.conf.theme.core.palette

import com.sphereon.core.compat.JsExportCompat
import com.sphereon.core.compat.JsExportIgnoreCompat
import kotlinx.serialization.Serializable

/**
 * A reference from an M3 semantic token to a specific palette scale + stop.
 */
@JsExportCompat
@Serializable
data class PaletteRef(
    val scale: String,
    val stop: Int,
)

/**
 * Configurable mapping from palette 50–900 stops to M3 semantic token keys.
 * This is pure data — serializable, storable in the theme database, usable in Figma sync.
 */
@JsExportCompat
@Serializable
data class PaletteMapping(
    @JsExportIgnoreCompat
    val light: Map<String, PaletteRef>,
    @JsExportIgnoreCompat
    val dark: Map<String, PaletteRef>,
)

/**
 * Default mapping from design system palette stops to M3 semantic tokens.
 * Follows the DESIGN-SYSTEM.md table.
 */
object DefaultPaletteMapping {
    private const val ROLE_BRAND = "brand"
    private const val ROLE_SECONDARY = "secondary"
    private const val ROLE_NEUTRAL = "neutral"
    private const val ROLE_ERROR = "error"
    private const val ROLE_SUCCESS = "success"
    private const val ROLE_WARNING = "warning"
    private const val ROLE_INFO = "info"

    val LIGHT: Map<String, PaletteRef> =
        mapOf(
            // Primary
            "color.primary" to PaletteRef(ROLE_BRAND, stop = PaletteScale.STOP_500),
            "color.onPrimary" to PaletteRef(ROLE_BRAND, stop = PaletteScale.STOP_50),
            "color.primaryContainer" to PaletteRef(ROLE_BRAND, stop = PaletteScale.STOP_100),
            "color.onPrimaryContainer" to PaletteRef(ROLE_BRAND, stop = PaletteScale.STOP_900),
            // Accent — interactive brand accent (mirrors primary)
            "color.accent" to PaletteRef(ROLE_BRAND, stop = PaletteScale.STOP_500),
            // Secondary
            "color.secondary" to PaletteRef(ROLE_SECONDARY, stop = PaletteScale.STOP_600),
            "color.onSecondary" to PaletteRef(ROLE_SECONDARY, stop = PaletteScale.STOP_50),
            "color.secondaryContainer" to PaletteRef(ROLE_SECONDARY, stop = PaletteScale.STOP_100),
            "color.onSecondaryContainer" to PaletteRef(ROLE_SECONDARY, stop = PaletteScale.STOP_900),
            // Tertiary — mapped from brand with offset stops
            "color.tertiary" to PaletteRef(ROLE_BRAND, stop = PaletteScale.STOP_400),
            "color.onTertiary" to PaletteRef(ROLE_BRAND, stop = PaletteScale.STOP_50),
            "color.tertiaryContainer" to PaletteRef(ROLE_BRAND, stop = PaletteScale.STOP_100),
            "color.onTertiaryContainer" to PaletteRef(ROLE_BRAND, stop = PaletteScale.STOP_800),
            // Error
            "color.error" to PaletteRef(ROLE_ERROR, stop = PaletteScale.STOP_500),
            "color.onError" to PaletteRef(ROLE_ERROR, stop = PaletteScale.STOP_50),
            "color.errorContainer" to PaletteRef(ROLE_ERROR, stop = PaletteScale.STOP_100),
            "color.onErrorContainer" to PaletteRef(ROLE_ERROR, stop = PaletteScale.STOP_900),
            // Surface / Neutral
            "color.surface" to PaletteRef(ROLE_NEUTRAL, stop = PaletteScale.STOP_50),
            "color.onSurface" to PaletteRef(ROLE_NEUTRAL, stop = PaletteScale.STOP_800),
            "color.surfaceVariant" to PaletteRef(ROLE_NEUTRAL, stop = PaletteScale.STOP_100),
            "color.onSurfaceVariant" to PaletteRef(ROLE_NEUTRAL, stop = PaletteScale.STOP_700),
            "color.surfaceContainer" to PaletteRef(ROLE_NEUTRAL, stop = PaletteScale.STOP_100),
            "color.surfaceContainerHigh" to PaletteRef(ROLE_NEUTRAL, stop = PaletteScale.STOP_100),
            "color.surfaceContainerHighest" to PaletteRef(ROLE_NEUTRAL, stop = PaletteScale.STOP_200),
            "color.surfaceContainerLow" to PaletteRef(ROLE_NEUTRAL, stop = PaletteScale.STOP_50),
            "color.surfaceContainerLowest" to PaletteRef(ROLE_NEUTRAL, stop = PaletteScale.STOP_50),
            "color.background" to PaletteRef(ROLE_NEUTRAL, stop = PaletteScale.STOP_50),
            "color.onBackground" to PaletteRef(ROLE_NEUTRAL, stop = PaletteScale.STOP_800),
            "color.outline" to PaletteRef(ROLE_NEUTRAL, stop = PaletteScale.STOP_400),
            "color.outlineVariant" to PaletteRef(ROLE_NEUTRAL, stop = PaletteScale.STOP_200),
            "color.inverseSurface" to PaletteRef(ROLE_NEUTRAL, stop = PaletteScale.STOP_800),
            "color.inverseOnSurface" to PaletteRef(ROLE_NEUTRAL, stop = PaletteScale.STOP_50),
            "color.inversePrimary" to PaletteRef(ROLE_BRAND, stop = PaletteScale.STOP_200),
            // Feedback
            "color.feedback.success" to PaletteRef(ROLE_SUCCESS, stop = PaletteScale.STOP_600),
            "color.feedback.successContainer" to PaletteRef(ROLE_SUCCESS, stop = PaletteScale.STOP_100),
            "color.feedback.onSuccess" to PaletteRef(ROLE_SUCCESS, stop = PaletteScale.STOP_50),
            "color.feedback.onSuccessContainer" to PaletteRef(ROLE_SUCCESS, stop = PaletteScale.STOP_900),
            "color.feedback.warning" to PaletteRef(ROLE_WARNING, stop = PaletteScale.STOP_600),
            "color.feedback.warningContainer" to PaletteRef(ROLE_WARNING, stop = PaletteScale.STOP_100),
            "color.feedback.onWarning" to PaletteRef(ROLE_WARNING, stop = PaletteScale.STOP_50),
            "color.feedback.onWarningContainer" to PaletteRef(ROLE_WARNING, stop = PaletteScale.STOP_900),
            "color.feedback.info" to PaletteRef(ROLE_INFO, stop = PaletteScale.STOP_600),
            "color.feedback.infoContainer" to PaletteRef(ROLE_INFO, stop = PaletteScale.STOP_100),
            "color.feedback.onInfo" to PaletteRef(ROLE_INFO, stop = PaletteScale.STOP_50),
            "color.feedback.onInfoContainer" to PaletteRef(ROLE_INFO, stop = PaletteScale.STOP_900),
            // Text
            "color.text.primary" to PaletteRef(ROLE_NEUTRAL, stop = PaletteScale.STOP_800),
            "color.text.secondary" to PaletteRef(ROLE_NEUTRAL, stop = PaletteScale.STOP_600),
            "color.text.disabled" to PaletteRef(ROLE_NEUTRAL, stop = PaletteScale.STOP_400),
            "color.text.inverse" to PaletteRef(ROLE_NEUTRAL, stop = PaletteScale.STOP_50),
            // Border
            "color.border.default" to PaletteRef(ROLE_NEUTRAL, stop = PaletteScale.STOP_300),
            "color.border.strong" to PaletteRef(ROLE_NEUTRAL, stop = PaletteScale.STOP_500),
            "color.border.subtle" to PaletteRef(ROLE_NEUTRAL, stop = PaletteScale.STOP_200),
            "color.border.disabled" to PaletteRef(ROLE_NEUTRAL, stop = PaletteScale.STOP_200),
            // Interactive
            "color.interactive.hover" to PaletteRef(ROLE_NEUTRAL, stop = PaletteScale.STOP_100),
            "color.interactive.pressed" to PaletteRef(ROLE_NEUTRAL, stop = PaletteScale.STOP_200),
            "color.interactive.disabled" to PaletteRef(ROLE_NEUTRAL, stop = PaletteScale.STOP_200),
            "color.interactive.focus" to PaletteRef(ROLE_BRAND, stop = PaletteScale.STOP_500),
        )

    val DARK: Map<String, PaletteRef> =
        mapOf(
            // Primary
            "color.primary" to PaletteRef(ROLE_BRAND, stop = PaletteScale.STOP_500),
            "color.onPrimary" to PaletteRef(ROLE_BRAND, stop = PaletteScale.STOP_50),
            "color.primaryContainer" to PaletteRef(ROLE_BRAND, stop = PaletteScale.STOP_700),
            "color.onPrimaryContainer" to PaletteRef(ROLE_BRAND, stop = PaletteScale.STOP_100),
            // Accent — interactive brand accent (mirrors primary)
            "color.accent" to PaletteRef(ROLE_BRAND, stop = PaletteScale.STOP_400),
            // Secondary
            "color.secondary" to PaletteRef(ROLE_SECONDARY, stop = PaletteScale.STOP_300),
            "color.onSecondary" to PaletteRef(ROLE_SECONDARY, stop = PaletteScale.STOP_900),
            "color.secondaryContainer" to PaletteRef(ROLE_SECONDARY, stop = PaletteScale.STOP_700),
            "color.onSecondaryContainer" to PaletteRef(ROLE_SECONDARY, stop = PaletteScale.STOP_100),
            // Tertiary — mapped from brand with offset stops
            "color.tertiary" to PaletteRef(ROLE_BRAND, stop = PaletteScale.STOP_300),
            "color.onTertiary" to PaletteRef(ROLE_BRAND, stop = PaletteScale.STOP_900),
            "color.tertiaryContainer" to PaletteRef(ROLE_BRAND, stop = PaletteScale.STOP_700),
            "color.onTertiaryContainer" to PaletteRef(ROLE_BRAND, stop = PaletteScale.STOP_200),
            // Error
            "color.error" to PaletteRef(ROLE_ERROR, stop = PaletteScale.STOP_300),
            "color.onError" to PaletteRef(ROLE_ERROR, stop = PaletteScale.STOP_50),
            "color.errorContainer" to PaletteRef(ROLE_ERROR, stop = PaletteScale.STOP_700),
            "color.onErrorContainer" to PaletteRef(ROLE_ERROR, stop = PaletteScale.STOP_100),
            // Surface / Neutral
            "color.surface" to PaletteRef(ROLE_NEUTRAL, stop = PaletteScale.STOP_900),
            "color.onSurface" to PaletteRef(ROLE_NEUTRAL, stop = PaletteScale.STOP_100),
            "color.surfaceVariant" to PaletteRef(ROLE_NEUTRAL, stop = PaletteScale.STOP_700),
            "color.onSurfaceVariant" to PaletteRef(ROLE_NEUTRAL, stop = PaletteScale.STOP_200),
            "color.surfaceContainer" to PaletteRef(ROLE_NEUTRAL, stop = PaletteScale.STOP_800),
            "color.surfaceContainerHigh" to PaletteRef(ROLE_NEUTRAL, stop = PaletteScale.STOP_700),
            "color.surfaceContainerHighest" to PaletteRef(ROLE_NEUTRAL, stop = PaletteScale.STOP_600),
            "color.surfaceContainerLow" to PaletteRef(ROLE_NEUTRAL, stop = PaletteScale.STOP_900),
            "color.surfaceContainerLowest" to PaletteRef(ROLE_NEUTRAL, stop = PaletteScale.STOP_900),
            "color.background" to PaletteRef(ROLE_NEUTRAL, stop = PaletteScale.STOP_900),
            "color.onBackground" to PaletteRef(ROLE_NEUTRAL, stop = PaletteScale.STOP_100),
            "color.outline" to PaletteRef(ROLE_NEUTRAL, stop = PaletteScale.STOP_500),
            "color.outlineVariant" to PaletteRef(ROLE_NEUTRAL, stop = PaletteScale.STOP_700),
            "color.inverseSurface" to PaletteRef(ROLE_NEUTRAL, stop = PaletteScale.STOP_100),
            "color.inverseOnSurface" to PaletteRef(ROLE_NEUTRAL, stop = PaletteScale.STOP_800),
            "color.inversePrimary" to PaletteRef(ROLE_BRAND, stop = PaletteScale.STOP_600),
            // Feedback
            "color.feedback.success" to PaletteRef(ROLE_SUCCESS, stop = PaletteScale.STOP_600),
            "color.feedback.successContainer" to PaletteRef(ROLE_SUCCESS, stop = PaletteScale.STOP_800),
            "color.feedback.onSuccess" to PaletteRef(ROLE_SUCCESS, stop = PaletteScale.STOP_50),
            "color.feedback.onSuccessContainer" to PaletteRef(ROLE_SUCCESS, stop = PaletteScale.STOP_100),
            "color.feedback.warning" to PaletteRef(ROLE_WARNING, stop = PaletteScale.STOP_400),
            "color.feedback.warningContainer" to PaletteRef(ROLE_WARNING, stop = PaletteScale.STOP_800),
            "color.feedback.onWarning" to PaletteRef(ROLE_WARNING, stop = PaletteScale.STOP_50),
            "color.feedback.onWarningContainer" to PaletteRef(ROLE_WARNING, stop = PaletteScale.STOP_100),
            "color.feedback.info" to PaletteRef(ROLE_INFO, stop = PaletteScale.STOP_400),
            "color.feedback.infoContainer" to PaletteRef(ROLE_INFO, stop = PaletteScale.STOP_800),
            "color.feedback.onInfo" to PaletteRef(ROLE_INFO, stop = PaletteScale.STOP_50),
            "color.feedback.onInfoContainer" to PaletteRef(ROLE_INFO, stop = PaletteScale.STOP_100),
            // Text
            "color.text.primary" to PaletteRef(ROLE_NEUTRAL, stop = PaletteScale.STOP_100),
            "color.text.secondary" to PaletteRef(ROLE_NEUTRAL, stop = PaletteScale.STOP_300),
            "color.text.disabled" to PaletteRef(ROLE_NEUTRAL, stop = PaletteScale.STOP_500),
            "color.text.inverse" to PaletteRef(ROLE_NEUTRAL, stop = PaletteScale.STOP_800),
            // Border
            "color.border.default" to PaletteRef(ROLE_NEUTRAL, stop = PaletteScale.STOP_600),
            "color.border.strong" to PaletteRef(ROLE_NEUTRAL, stop = PaletteScale.STOP_400),
            "color.border.subtle" to PaletteRef(ROLE_NEUTRAL, stop = PaletteScale.STOP_700),
            "color.border.disabled" to PaletteRef(ROLE_NEUTRAL, stop = PaletteScale.STOP_700),
            // Interactive
            "color.interactive.hover" to PaletteRef(ROLE_BRAND, stop = PaletteScale.STOP_300),
            "color.interactive.pressed" to PaletteRef(ROLE_BRAND, stop = PaletteScale.STOP_400),
            "color.interactive.disabled" to PaletteRef(ROLE_NEUTRAL, stop = PaletteScale.STOP_600),
            "color.interactive.focus" to PaletteRef(ROLE_BRAND, stop = PaletteScale.STOP_400),
        )

    val DEFAULT = PaletteMapping(light = LIGHT, dark = DARK)
}
