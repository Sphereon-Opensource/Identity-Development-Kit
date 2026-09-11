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

package com.sphereon.conf.theme.ui.compose

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import com.sphereon.conf.theme.compose.LocalThemeTokens
import com.sphereon.conf.theme.core.defaults.SystemDefaults
import com.sphereon.conf.theme.core.token.TokenFlattener
import com.sphereon.conf.theme.core.token.TokenReferenceResolver
import com.sphereon.conf.theme.ui.compose.button.Button
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The call-site half of the parse-site guard.
 *
 * ComponentTokenParseSiteTest can only prove that a token value is compatible with the parser its
 * field is declared to use. It cannot see which parser a component actually calls, which is exactly
 * what the original defect was: `parseColor(tokens.primaryBackground)` on a gradient token, yielding
 * [Color.Unspecified] and a button with no fill, with every token-level test still green.
 *
 * The only way to observe a call site is to render it, so this renders the real design system
 * primary button with the real resolved tokens and reads the pixels back.
 *
 * COVERAGE. This asserts the resting primary button fill, which is the field named in
 * BRUSH_BACKED_FIELDS. It does not assert the Outline variant text and border, nor the segmented
 * control, nor the two blob explorer buttons: those consume flat colour roles, which the token-level
 * guard already covers, and asserting a one pixel antialiased border stroke would be flaky enough to
 * be worse than no assertion. If another field is ever added to BRUSH_BACKED_FIELDS, it needs its own
 * assertion here.
 */
@OptIn(ExperimentalTestApi::class)
class ComponentFillRenderTest {
    /** Fraction of the button height sampled from each end, inset to stay clear of the rounded corners. */
    private val sampleInset = 0.2f

    /** Minimum per-channel distance between the two samples for the fill to count as a gradient. */
    private val minimumRamp = 0.05f

    @Composable
    private fun themed(content: @Composable () -> Unit) {
        val tokens = TokenReferenceResolver.resolve(TokenFlattener.merge(listOf(SystemDefaults.baseline)))
        CompositionLocalProvider(LocalThemeTokens provides tokens) {
            ComponentTheme {
                MaterialTheme {
                    content()
                }
            }
        }
    }

    @Test
    fun theBrandGradientReachesThePrimaryButtonFill() =
        runComposeUiTest {
            setContent {
                themed {
                    Button(onClick = {}, modifier = Modifier.testTag(TAG)) { Text("Continue") }
                }
            }

            val image = onNodeWithTag(TAG).captureToImage()
            val pixels = image.toPixelMap()
            val x = image.width / 2
            val top = pixels[x, (image.height * sampleInset).toInt()]
            val bottom = pixels[x, (image.height * (1f - sampleInset)).toInt()]

            // A missing fill shows up as a transparent or unpainted button rather than a flat one,
            // so check opacity before checking the ramp; the two failures want different fixes.
            assertTrue(
                top.alpha > OPAQUE_ENOUGH && bottom.alpha > OPAQUE_ENOUGH,
                "The primary button has no fill (top $top, bottom $bottom). The background token is a " +
                    "gradient; a call site reading it with parseColor gets Color.Unspecified and paints nothing",
            )

            val ramp = maxOf(abs(top.red - bottom.red), abs(top.green - bottom.green), abs(top.blue - bottom.blue))
            assertTrue(
                ramp > minimumRamp,
                "The primary button fill is flat (top $top, bottom $bottom, largest channel delta $ramp). " +
                    "comp.button.primary.background is the brand gradient and must be painted through " +
                    "tokenFill and Modifier.tokenFill, not resolved with parseColor",
            )

            // Top lighter than bottom, which is what a 180deg gradient means. Catches an inverted ramp.
            assertTrue(
                top.red + top.green + top.blue > bottom.red + bottom.green + bottom.blue,
                "The primary button gradient runs the wrong way (top $top, bottom $bottom)",
            )
        }

    private companion object {
        const val TAG = "ds-primary-button"
        const val OPAQUE_ENOUGH = 0.9f
    }
}
