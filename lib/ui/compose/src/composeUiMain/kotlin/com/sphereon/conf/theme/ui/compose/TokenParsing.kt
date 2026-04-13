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

@file:Suppress("TooGenericExceptionCaught") // CSS token parsing must gracefully handle any malformed input

package com.sphereon.conf.theme.ui.compose

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val rgbaRegex = Regex("""rgba?\(\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)\s*(?:,\s*([\d.]+))?\s*\)""")

/**
 * Parse CSS-like color strings to Compose Color.
 * Supports: #RGB, #RRGGBB, #RRGGBBAA, rgb(r,g,b), rgba(r,g,b,a), "transparent"
 */
internal fun parseColor(
    value: String,
    fallback: Color = Color.Unspecified,
): Color {
    val trimmed = value.trim()
    if (trimmed == "transparent") {
        return Color.Transparent
    }
    return try {
        when {
            trimmed.startsWith("#") -> parseHexColor(trimmed, fallback)
            trimmed.startsWith("rgb") -> parseRgbColor(trimmed, fallback)
            else -> fallback
        }
    } catch (_: Exception) {
        // Ignored: color string could not be parsed
        fallback
    }
}

@Suppress("MagicNumber")
private fun parseHexColor(
    value: String,
    fallback: Color,
): Color {
    val hex = value.removePrefix("#")
    return when (hex.length) {
        3 -> {
            val r = hex[0].toString().repeat(2).toInt(16)
            val g = hex[1].toString().repeat(2).toInt(16)
            val b = hex[2].toString().repeat(2).toInt(16)
            Color(r, g, b)
        }

        6 -> {
            Color(
                hex.substring(0, 2).toInt(16),
                hex.substring(2, 4).toInt(16),
                hex.substring(4, 6).toInt(16),
            )
        }

        8 -> {
            Color(
                hex.substring(0, 2).toInt(16),
                hex.substring(2, 4).toInt(16),
                hex.substring(4, 6).toInt(16),
                hex.substring(6, 8).toInt(16),
            )
        }

        else -> {
            fallback
        }
    }
}

@Suppress("MagicNumber")
private fun parseRgbColor(
    value: String,
    fallback: Color,
): Color {
    val match = rgbaRegex.find(value) ?: return fallback
    val r = match.groupValues[1].toIntOrNull() ?: return fallback
    val g = match.groupValues[2].toIntOrNull() ?: return fallback
    val b = match.groupValues[3].toIntOrNull() ?: return fallback
    val a =
        match.groupValues[4].let {
            if (it.isEmpty()) {
                1f
            } else {
                it.toFloatOrNull() ?: 1f
            }
        }
    return Color(
        red = r.coerceIn(0, 255),
        green = g.coerceIn(0, 255),
        blue = b.coerceIn(0, 255),
        alpha = (a * 255).toInt().coerceIn(0, 255),
    )
}

/**
 * Parse CSS dimension string to Compose Dp.
 * Supports: "16px", "16dp", "16sp", "16", "-8px" (negative values)
 */
internal fun parseDp(
    value: String,
    fallback: Dp = 0.dp,
): Dp =
    try {
        val trimmed = value.trim()
        val num = trimmed.replace(Regex("[^0-9.\\-]"), "").toFloat()
        num.dp
    } catch (_: Exception) {
        // Ignored: dimension string could not be parsed
        fallback
    }
