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

package com.sphereon.conf.theme.web

import com.sphereon.core.compat.JsExportCompat

/**
 * Converts IDK token dot-keys to CSS custom properties.
 * Matches IDK's CssVariableGenerator convention: dots become hyphens.
 */
@JsExportCompat
object CssTokenMapper {
    /**
     * Convert an IDK token key to a CSS custom property name. camelCase segments
     * are split to kebab-case and dots become hyphens, so the emitted variables
     * are idiomatic kebab CSS that hand-written stylesheets reference directly.
     * Example: "color.primary" → "--color-primary",
     *          "color.onSurface" → "--color-on-surface".
     */
    fun tokenKeyToCssVar(key: String): String {
        val kebab =
            key
                .replace(CAMEL_BOUNDARY, "$1-$2")
                .lowercase()
                .replace('.', '-')
        return "--$kebab"
    }

    /**
     * Convert IDK dimension units (sp/dp) to CSS-compatible px values.
     * Web doesn't have sp or dp — they map 1:1 to px at baseline density.
     */
    fun convertUnit(value: String): String = value.replace(UNIT_PATTERN, "$1px")

    /**
     * Convert a flat IDK token map to CSS custom properties map.
     * Keys become CSS var names, values have units converted.
     */
    fun tokensToCssVars(tokens: Map<String, String>): Map<String, String> {
        val vars = mutableMapOf<String, String>()
        for ((key, value) in tokens) {
            // Shadow values use CSS box-shadow syntax — pass through without unit conversion
            vars[tokenKeyToCssVar(key)] =
                if (key.startsWith("shadow.")) {
                    value
                } else {
                    convertUnit(value)
                }
        }
        return vars
    }

    /**
     * Convert a hex color to rgba with given opacity.
     */
    fun hexToRgba(
        hex: String,
        alpha: Double,
    ): String {
        val clean = hex.removePrefix("#")
        val r = clean.substring(0, 2).toInt(16)
        val g = clean.substring(2, 4).toInt(16)
        val b = clean.substring(4, 6).toInt(16)
        return "rgba($r, $g, $b, $alpha)"
    }

    private val UNIT_PATTERN = Regex("""(\d+(?:\.\d+)?)\s*(?:sp|dp)""")

    // Boundary between a lowercase/digit and an uppercase letter, e.g. the "nS"
    // in "onSurface" — used to split camelCase token segments to kebab-case.
    private val CAMEL_BOUNDARY = Regex("""([a-z0-9])([A-Z])""")
}
