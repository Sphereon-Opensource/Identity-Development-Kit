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

import androidx.compose.ui.graphics.Color
import com.sphereon.conf.theme.core.defaults.SystemDefaults
import com.sphereon.conf.theme.core.model.ThemeDefinition
import com.sphereon.conf.theme.core.token.TokenFlattener
import com.sphereon.conf.theme.core.token.TokenReferenceResolver
import com.sphereon.conf.theme.ui.compose.tokens.ButtonTokens
import com.sphereon.conf.theme.ui.compose.tokens.ComponentTokenMapper
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Guards the seam between the token layer and the Compose parsers.
 *
 * [parseColor] returns its fallback for any value it cannot read, and the default fallback is
 * [Color.Unspecified]. That makes a type mismatch silent: a component asks for a colour, the token
 * holds a gradient, the parser shrugs, and the fill disappears with nothing failing anywhere.
 * Component tests do not catch it because they inject literal hex fixtures, so the real token value
 * never reaches the parser.
 *
 * These tests run the REAL resolved SystemDefaults through the mapper and the parsers.
 *
 * WHAT THIS FILE COVERS: that every token value is compatible with the parser its field is declared
 * to use. WHAT IT DOES NOT COVER: which parser a call site actually reaches for. Only rendering can
 * see a call site, so that half is asserted at the pixel level in ComponentFillRenderTest.
 */
class ComponentTokenParseSiteTest {
    private fun resolved(definition: ThemeDefinition): Map<String, String> = TokenReferenceResolver.resolve(TokenFlattener.merge(listOf(definition)))

    private fun variants(): List<Pair<String, ButtonTokens>> =
        listOf(
            "light" to ComponentTokenMapper.toButtonTokens(resolved(SystemDefaults.baseline)),
            "dark" to ComponentTokenMapper.toButtonTokens(resolved(SystemDefaults.baselineDark)),
            "highContrast" to ComponentTokenMapper.toButtonTokens(resolved(SystemDefaults.baselineHighContrastLight)),
        )

    @Test
    fun everyStringFieldOfTheTypeIsClassified() {
        // The point of deriving the field list from the type rather than writing it out: a colour
        // field added to ButtonTokens tomorrow cannot slip past these tests unnoticed. It either
        // matches a known suffix and gets guarded, or it fails here asking to be classified.
        val unclassified =
            stringPropertyNames().filter { name ->
                !isColorField(name) && NON_COLOR_SUFFIXES.none { suffix -> name.lowercase().endsWith(suffix) }
            }
        assertEquals(
            emptyList(),
            unclassified,
            "ButtonTokens has string fields this guard cannot classify. Add their suffix to " +
                "COLOR_SUFFIXES if they hold colours, or to NON_COLOR_SUFFIXES if they do not",
        )
        assertTrue(stringPropertyNames().isNotEmpty(), "Reflection found no string fields on ButtonTokens")
    }

    @Test
    fun brushBackedFieldsArePinnedByComposition() {
        // Pinned so that widening the allowlist is a deliberate, reviewable edit rather than a
        // one-word change that quietly removes a field from the flat-colour guard. Any name added
        // here must also be covered by a render-level assertion in ComponentFillRenderTest.
        assertEquals(setOf("primaryBackground"), BRUSH_BACKED_FIELDS)
        assertTrue(
            BRUSH_BACKED_FIELDS.all { it in stringPropertyNames() },
            "BRUSH_BACKED_FIELDS names a field that does not exist on ButtonTokens",
        )
    }

    @Test
    fun everyFlatColorFieldResolvesToASpecifiedColor() {
        for ((variant, tokens) in variants()) {
            for ((field, value) in colorFields(tokens)) {
                if (field in BRUSH_BACKED_FIELDS) {
                    continue
                }
                assertTrue(
                    parseColor(value) != Color.Unspecified,
                    "$variant ButtonTokens.$field is consumed as a flat colour but parseColor cannot read " +
                        "'$value', so the component silently renders nothing",
                )
            }
        }
    }

    @Test
    fun everyBrushBackedFieldParsesAsABrush() {
        for ((variant, tokens) in variants()) {
            for ((field, value) in colorFields(tokens)) {
                if (field !in BRUSH_BACKED_FIELDS) {
                    continue
                }
                assertNotNull(
                    parseBrush(value),
                    "$variant ButtonTokens.$field is painted through a Brush but parseBrush cannot read " +
                        "'$value'. Either it stopped being a gradient, or it uses a form the parser does " +
                        "not support such as a non axis-aligned angle or a color-mix stop",
                )
            }
        }
    }

    @Test
    fun theBrandGradientIsAGradientAndNotAFlatColor() {
        // Pins the premise of the split above: if the brand gradient ever became a flat colour this
        // would fail rather than quietly leaving dead Brush plumbing behind.
        val tokens = ComponentTokenMapper.toButtonTokens(resolved(SystemDefaults.baseline))
        assertTrue(
            parseColor(tokens.primaryBackground) == Color.Unspecified,
            "primaryBackground was expected to be a gradient that parseColor cannot read",
        )
        assertNotNull(parseBrush(tokens.primaryBackground))
    }

    companion object {
        /**
         * Fields whose consuming components resolve the token through `tokenFill` and paint it with
         * `Modifier.tokenFill`, so a gradient renders correctly. Adding a name here is a claim that
         * the Brush path exists, and ComponentFillRenderTest is what checks that claim by rendering.
         */
        val BRUSH_BACKED_FIELDS = setOf("primaryBackground")

        private val COLOR_SUFFIXES = listOf("background", "foreground", "border", "color")
        private val NON_COLOR_SUFFIXES = listOf("width", "radius", "shadow", "paddingx", "paddingy", "height", "size", "gap")

        private fun stringGetters() =
            ButtonTokens::class.java.methods
                .filter { it.name.startsWith("get") && it.parameterCount == 0 && it.returnType == String::class.java }
                .map { it.name.removePrefix("get").replaceFirstChar { first -> first.lowercaseChar() } to it }
                .sortedBy { (name, _) -> name }

        /** Every String-valued property of the token type, derived from the type itself. */
        fun stringPropertyNames(): List<String> = stringGetters().map { (name, _) -> name }

        fun isColorField(name: String): Boolean = COLOR_SUFFIXES.any { name.lowercase().endsWith(it) }

        /** Colour-typed fields of [tokens], paired with the value the variant resolves to. */
        fun colorFields(tokens: ButtonTokens): List<Pair<String, String>> =
            stringGetters()
                .filter { (name, _) -> isColorField(name) }
                .map { (name, getter) -> name to (getter.invoke(tokens) as String) }
    }
}
