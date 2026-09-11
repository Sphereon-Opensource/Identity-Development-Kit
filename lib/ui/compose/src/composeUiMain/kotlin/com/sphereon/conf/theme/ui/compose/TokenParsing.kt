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

import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sphereon.conf.theme.compose.LocalThemeTokens

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

// ---------------------------------------------------------------------------
// Gradients
//
// Some design system tokens hold a CSS gradient rather than a flat colour, so a
// component that only ever calls parseColor would render nothing at all: parseColor
// returns its fallback for anything it cannot read, and the default fallback is
// Color.Unspecified. Anything painting from a token that MAY be a gradient should
// resolve it through tokenFill and paint it with Modifier.tokenGradient or Modifier.tokenBackground.
// ---------------------------------------------------------------------------

private val linearGradientRegex = Regex("""^linear-gradient\((.*)\)$""", RegexOption.DOT_MATCHES_ALL)

/** Axis-aligned gradient directions, keyed by the CSS angle or keyword that selects them. */
private enum class GradientAxis { TopToBottom, BottomToTop, LeftToRight, RightToLeft }

private const val FULL_PERCENT = 100f

private fun gradientAxis(token: String): GradientAxis? =
    when (token.trim().lowercase()) {
        "180deg", "to bottom" -> GradientAxis.TopToBottom
        "0deg", "360deg", "to top" -> GradientAxis.BottomToTop
        "90deg", "to right" -> GradientAxis.LeftToRight
        "270deg", "to left" -> GradientAxis.RightToLeft
        else -> null
    }

/**
 * Parse a CSS `linear-gradient(...)` into a Compose [Brush].
 *
 * Supports the axis-aligned directions only (`180deg` / `to bottom` and the other three, plus the
 * implicit default of top to bottom). Compose cannot express an arbitrary CSS angle without knowing
 * the drawn size, so a gradient at any other angle returns null rather than being silently rendered
 * at the wrong angle. Returns null for radial gradients and for any stop that is not a plain colour,
 * for example one built with `color-mix`. Callers fall back to a flat colour when this returns null,
 * which is why the design system pins its own gradient tokens with a test.
 */
fun parseBrush(value: String): Brush? {
    val match = linearGradientRegex.find(value.trim()) ?: return null
    val body = match.groupValues[1]
    val parts = splitTopLevel(body)
    if (parts.isEmpty()) {
        return null
    }
    val axis = gradientAxis(parts.first())
    val stopParts = if (axis != null) parts.drop(1) else parts
    val direction = axis ?: GradientAxis.TopToBottom
    if (stopParts.size < 2) {
        return null
    }
    val stops = stopParts.mapIndexed { index, part -> colorStop(part, index, stopParts.size) ?: return null }
    val reversedAxis = direction == GradientAxis.BottomToTop || direction == GradientAxis.RightToLeft
    val ordered =
        if (reversedAxis) {
            stops.map { (position, color) -> (1f - position) to color }.reversed()
        } else {
            stops
        }
    return when (direction) {
        GradientAxis.TopToBottom, GradientAxis.BottomToTop -> Brush.verticalGradient(colorStops = ordered.toTypedArray())
        GradientAxis.LeftToRight, GradientAxis.RightToLeft -> Brush.horizontalGradient(colorStops = ordered.toTypedArray())
    }
}

/** Split on commas that are not inside parentheses, so `rgba(0, 0, 0)` stays one part. */
private fun splitTopLevel(body: String): List<String> {
    val parts = mutableListOf<String>()
    val current = StringBuilder()
    var depth = 0
    for (ch in body) {
        when {
            ch == '(' -> {
                depth++
                current.append(ch)
            }

            ch == ')' -> {
                depth--
                current.append(ch)
            }

            ch == ',' && depth == 0 -> {
                parts.add(current.toString().trim())
                current.clear()
            }

            else -> {
                current.append(ch)
            }
        }
    }
    if (current.isNotBlank()) {
        parts.add(current.toString().trim())
    }
    return parts.filter { it.isNotEmpty() }
}

/** Parse one `<color> [<position>%]` stop. Returns null when the colour is not readable. */
private fun colorStop(
    part: String,
    index: Int,
    count: Int,
): Pair<Float, Color>? {
    val trimmed = part.trim()
    val percentIndex = trimmed.lastIndexOf('%')
    val hasPosition = percentIndex == trimmed.lastIndex && trimmed.contains(' ')
    val colorText = if (hasPosition) trimmed.substringBeforeLast(' ').trim() else trimmed
    val positionText = trimmed.substringAfterLast(' ').removeSuffix("%")
    val evenlySpaced = if (count <= 1) 0f else index.toFloat() / (count - 1).toFloat()
    val position =
        if (hasPosition) {
            val percent = positionText.toFloatOrNull() ?: return null
            percent / FULL_PERCENT
        } else {
            evenlySpaced
        }
    val color = parseColor(colorText)
    if (color == Color.Unspecified) {
        return null
    }
    return position to color
}

/**
 * A token that may hold either a flat colour or a gradient, resolved into both forms.
 *
 * Hand [containerColor] to Material, which only takes a [Color], and paint the gradient with
 * [Modifier.tokenFill]. When the token is a flat colour the modifier is a no-op and Material paints
 * it as before.
 */
@Immutable
data class TokenFill(
    val brush: Brush?,
    val color: Color,
) {
    val containerColor: Color get() = if (brush != null) Color.Transparent else color
}

/** Resolve a token that may hold a gradient. [fallback] is used when the value is a colour it cannot read. */
fun tokenFill(
    value: String,
    fallback: Color,
): TokenFill {
    val brush = parseBrush(value)
    if (brush == null) {
        // Silent for a plain colour; loud, once, for a value that announces itself as a gradient
        // and then cannot be rendered. See TokenDiagnostics for why the noise lives here and not
        // in the paint path.
        TokenDiagnostics.reportUnrenderableGradient(
            value,
            "only axis-aligned linear-gradient with plain colour stops is supported",
        )
    }
    return TokenFill(brush = brush, color = parseColor(value, fallback))
}

/**
 * Paint ONLY the gradient of [fill]. A flat fill leaves the modifier untouched.
 *
 * Use with a Material container that already paints [TokenFill.containerColor], so the flat case is
 * not painted twice. For a plain surface that nothing else paints, use [tokenBackground].
 */
fun Modifier.tokenGradient(
    fill: TokenFill,
    shape: Shape,
): Modifier = if (fill.brush != null) this.background(fill.brush, shape) else this

/**
 * Paint the whole of [fill], gradient or flat colour.
 *
 * Use where nothing else paints the surface. Painting this over a Material container would paint the
 * flat case twice; use [tokenGradient] there instead.
 */
fun Modifier.tokenBackground(
    fill: TokenFill,
    shape: Shape,
): Modifier = if (fill.brush != null) this.background(fill.brush, shape) else this.background(fill.color, shape)

/**
 * Resolve a theme token that may hold a flat colour or a gradient into something paintable.
 *
 * This is the public entry point to the fill seam. Read a token by key rather than reconstructing
 * its value from palette stops: a hand-rolled copy stops following the light and dark variants, and
 * stops following a tenant palette under white-labelling, the moment the token itself moves.
 *
 * [fallback] is used when the token is absent from the resolved map, or holds a value neither parser
 * can read.
 */
@Composable
fun rememberTokenFill(
    tokenKey: String,
    fallback: Color,
): TokenFill {
    val value = LocalThemeTokens.current[tokenKey]
    return remember(value, fallback) {
        if (value == null) TokenFill(brush = null, color = fallback) else tokenFill(value, fallback)
    }
}
