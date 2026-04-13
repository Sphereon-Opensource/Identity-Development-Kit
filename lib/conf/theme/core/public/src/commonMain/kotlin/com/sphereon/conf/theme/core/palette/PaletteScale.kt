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
import kotlinx.serialization.Serializable

/**
 * A 10-stop color scale (50–900) representing a single design system palette role.
 * Each value is a hex color string (e.g. "#7C40E8").
 *
 * This is intentionally separate from M3's 13-tone system (0–100).
 * A mapper bridges between the two models.
 */
@JsExportCompat
@Serializable
data class PaletteScale(
    val s50: String,
    val s100: String,
    val s200: String,
    val s300: String,
    val s400: String,
    val s500: String,
    val s600: String,
    val s700: String,
    val s800: String,
    val s900: String,
) {
    /**
     * Access a stop by its numeric value (50, 100, 200, ..., 900).
     * @throws IllegalArgumentException if the stop is not one of the standard values
     */
    operator fun get(stop: Int): String =
        when (stop) {
            STOP_50 -> s50
            STOP_100 -> s100
            STOP_200 -> s200
            STOP_300 -> s300
            STOP_400 -> s400
            STOP_500 -> s500
            STOP_600 -> s600
            STOP_700 -> s700
            STOP_800 -> s800
            STOP_900 -> s900
            else -> throw IllegalArgumentException("Invalid palette stop: $stop. Must be one of $STOPS")
        }

    companion object {
        const val STOP_50 = 50
        const val STOP_100 = 100
        const val STOP_200 = 200
        const val STOP_300 = 300
        const val STOP_400 = 400
        const val STOP_500 = 500
        const val STOP_600 = 600
        const val STOP_700 = 700
        const val STOP_800 = 800
        const val STOP_900 = 900
        val STOPS = listOf(STOP_50, STOP_100, STOP_200, STOP_300, STOP_400, STOP_500, STOP_600, STOP_700, STOP_800, STOP_900)

        /**
         * Synthesize a [PaletteScale] from an M3 [TonalPalette] by mapping tones to the nearest 50–900 stops.
         *
         * Mapping: 50→tone95, 100→tone90, 200→tone80, 300→tone70, 400→tone60,
         * 500→tone50, 600→tone40, 700→tone30, 800→tone20, 900→tone10
         */
        fun fromTonalPalette(palette: TonalPalette): PaletteScale =
            PaletteScale(
                s50 = palette.tone(tone = TONE_95),
                s100 = palette.tone(tone = TONE_90),
                s200 = palette.tone(tone = TONE_80),
                s300 = palette.tone(tone = TONE_70),
                s400 = palette.tone(tone = TONE_60),
                s500 = palette.tone(tone = TONE_50),
                s600 = palette.tone(tone = TONE_40),
                s700 = palette.tone(tone = TONE_30),
                s800 = palette.tone(tone = TONE_20),
                s900 = palette.tone(tone = TONE_10),
            )

        private const val TONE_10 = 10
        private const val TONE_20 = 20
        private const val TONE_30 = 30
        private const val TONE_40 = 40
        private const val TONE_50 = 50
        private const val TONE_60 = 60
        private const val TONE_70 = 70
        private const val TONE_80 = 80
        private const val TONE_90 = 90
        private const val TONE_95 = 95
    }
}

/**
 * A complete design system palette with role-based named scales.
 *
 * All names are role-based, never color-descriptive (no "blue", "grey", "purple").
 * A Figma design system with "Blue" and "Grey" must rename to [secondary] and [neutral].
 */
@JsExportCompat
@Serializable
data class DesignSystemPalette(
    val brand: PaletteScale,
    val secondary: PaletteScale? = null,
    val neutral: PaletteScale? = null,
    val error: PaletteScale? = null,
    val success: PaletteScale? = null,
    val warning: PaletteScale? = null,
    val info: PaletteScale? = null,
    val pending: PaletteScale? = null,
) {
    /** All defined (non-null) role names */
    val definedRoles: List<String>
        get() =
            buildList {
                add("brand")
                if (secondary != null) {
                    add("secondary")
                }
                if (neutral != null) {
                    add("neutral")
                }
                if (error != null) {
                    add("error")
                }
                if (success != null) {
                    add("success")
                }
                if (warning != null) {
                    add("warning")
                }
                if (info != null) {
                    add("info")
                }
                if (pending != null) {
                    add("pending")
                }
            }

    /**
     * Get a scale by its role name.
     * @return The [PaletteScale] for the given role, or null if not defined
     */
    fun getScale(role: String): PaletteScale? =
        when (role) {
            "brand" -> brand
            "secondary" -> secondary
            "neutral" -> neutral
            "error" -> error
            "success" -> success
            "warning" -> warning
            "info" -> info
            "pending" -> pending
            else -> null
        }
}
