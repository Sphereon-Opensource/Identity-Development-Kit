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
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic

/**
 * A color scale representing a single design system palette role: the ten canonical
 * stops (50 to 900) plus the two half-steps 450 and 650.
 * Each value is a hex color string (e.g. "#7C40E8").
 *
 * The half-steps exist because a gradient built from 400 to 600 puts light-mode
 * onPrimary text on a stop that fails WCAG 1.4.3. They are optional: a palette that
 * only supplies the ten canonical stops still resolves 450 and 650, derived from the
 * neighbouring stops by [halfStep].
 *
 * This is intentionally separate from M3's 13-tone system (0 to 100).
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
    /** Half-step between 400 and 500. Derived from those two when not supplied. */
    val s450: String? = null,
    /** Half-step between 600 and 700. Derived from those two when not supplied. */
    val s650: String? = null,
) {
    /**
     * Access a stop by its numeric value (50, 100, 200, ..., 900, plus the half-steps
     * 450 and 650). Half-steps fall back to a derived value when not supplied.
     * @throws IllegalArgumentException if the stop is not one of the standard values
     */
    operator fun get(stop: Int): String =
        when (stop) {
            STOP_50 -> s50
            STOP_100 -> s100
            STOP_200 -> s200
            STOP_300 -> s300
            STOP_400 -> s400
            STOP_450 -> s450 ?: halfStep(s400, s500)
            STOP_500 -> s500
            STOP_600 -> s600
            STOP_650 -> s650 ?: halfStep(s600, s700)
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
        const val STOP_450 = 450
        const val STOP_500 = 500
        const val STOP_600 = 600
        const val STOP_650 = 650
        const val STOP_700 = 700
        const val STOP_800 = 800
        const val STOP_900 = 900
        val STOPS =
            listOf(
                STOP_50,
                STOP_100,
                STOP_200,
                STOP_300,
                STOP_400,
                STOP_450,
                STOP_500,
                STOP_600,
                STOP_650,
                STOP_700,
                STOP_800,
                STOP_900,
            )

        /**
         * Fraction of the way from the lighter neighbour to the darker one that a derived
         * half-step sits at. 0.5 would land a hair under the 4.5:1 floor for the reference
         * brand ramp; 0.63 reproduces the reference half-steps to within one channel step
         * and leaves the top gradient stop real headroom.
         */
        private const val HALF_STEP_BIAS = 0.63

        private const val HEX_RADIX = 16
        private const val CHANNEL_MAX = 255

        /** Interpolate two hex colors channel-wise at [HALF_STEP_BIAS] toward [darker]. */
        @JvmStatic
        fun halfStep(
            lighter: String,
            darker: String,
        ): String {
            val a = channels(lighter)
            val b = channels(darker)
            if (a == null || b == null) {
                return darker
            }
            val mixed =
                IntArray(3) { i ->
                    val v = a[i] + (b[i] - a[i]) * HALF_STEP_BIAS
                    v.toInt().coerceIn(0, CHANNEL_MAX)
                }
            return "#" + mixed.joinToString("") { it.toString(HEX_RADIX).padStart(2, '0').uppercase() }
        }

        private fun channels(hex: String): DoubleArray? {
            val h = hex.removePrefix("#")
            if (h.length != 6) {
                return null
            }
            return DoubleArray(3) { i ->
                h.substring(i * 2, i * 2 + 2).toIntOrNull(HEX_RADIX)?.toDouble() ?: return null
            }
        }

        /**
         * Synthesize a [PaletteScale] from an M3 [TonalPalette] by mapping tones to the nearest 50–900 stops.
         *
         * Mapping: 50 to tone95, 100 to tone90, 200 to tone80, 300 to tone70,
         * 400 to tone60, 450 to tone55, 500 to tone50, 600 to tone40, 650 to tone35,
         * 700 to tone30, 800 to tone20, 900 to tone10
         */
        @JvmStatic
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
                s450 = palette.tone(tone = TONE_55),
                s650 = palette.tone(tone = TONE_35),
            )

        private const val TONE_10 = 10
        private const val TONE_20 = 20
        private const val TONE_30 = 30
        private const val TONE_35 = 35
        private const val TONE_40 = 40
        private const val TONE_50 = 50
        private const val TONE_55 = 55
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
data class DesignSystemPalette
    @JvmOverloads
    constructor(
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
