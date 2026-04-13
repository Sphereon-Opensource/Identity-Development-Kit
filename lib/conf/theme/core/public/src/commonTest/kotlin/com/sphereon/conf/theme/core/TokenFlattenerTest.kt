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

package com.sphereon.conf.theme.core

import com.sphereon.conf.theme.core.defaults.SystemDefaults
import com.sphereon.conf.theme.core.model.ThemeDefinition
import com.sphereon.conf.theme.core.model.ThemeScope
import com.sphereon.conf.theme.core.model.ThemeTokenType
import com.sphereon.conf.theme.core.palette.M3PaletteGenerator
import com.sphereon.conf.theme.core.token.TokenFlattener
import com.sphereon.conf.theme.core.token.TokenKeyConstants
import com.sphereon.conf.theme.core.token.TokenReferenceResolver
import com.sphereon.conf.theme.core.token.buildTokens
import com.sphereon.conf.theme.core.validation.ThemeValidator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TokenFlattenerTest {
    @Test
    fun mergeShouldOverrideEarlierWithLater() {
        val base =
            ThemeDefinition(
                id = "base",
                name = "Base",
                scope = ThemeScope.SYSTEM,
                tokens =
                    buildTokens {
                        color("color.primary", "#111111")
                        color("color.secondary", "#222222")
                    },
            )
        val overlay =
            ThemeDefinition(
                id = "overlay",
                name = "Overlay",
                scope = ThemeScope.APP,
                tokens =
                    buildTokens {
                        color("color.primary", "#AAAAAA")
                    },
            )
        val merged = TokenFlattener.merge(listOf(base, overlay))

        assertEquals("#AAAAAA", merged["color.primary"])
        assertEquals("#222222", merged["color.secondary"])
    }

    @Test
    fun systemDefaultsShouldHaveRequiredTokens() {
        val tokens = TokenFlattener.merge(listOf(SystemDefaults.baseline))
        assertTrue(tokens.containsKey(TokenKeyConstants.COLOR_PRIMARY))
        assertTrue(tokens.containsKey(TokenKeyConstants.COLOR_SURFACE))
        assertTrue(tokens.containsKey(TokenKeyConstants.COLOR_ERROR))
    }
}

class TokenReferenceResolverTest {
    @Test
    fun shouldResolveSimpleReferences() {
        val tokens =
            mapOf(
                "color.primary" to "#6750A4",
                "color.brand" to "{color.primary}",
            )
        val resolved = TokenReferenceResolver.resolve(tokens)
        assertEquals("#6750A4", resolved["color.brand"])
    }

    @Test
    fun shouldHandleChainedReferences() {
        val tokens =
            mapOf(
                "color.primary" to "#6750A4",
                "color.brand" to "{color.primary}",
                "color.accent" to "{color.brand}",
            )
        val resolved = TokenReferenceResolver.resolve(tokens)
        assertEquals("#6750A4", resolved["color.accent"])
    }

    @Test
    fun shouldHandleCircularReferences() {
        val tokens =
            mapOf(
                "a" to "{b}",
                "b" to "{a}",
            )
        // Should not throw, circular refs left as-is
        val resolved = TokenReferenceResolver.resolve(tokens)
        assertTrue(resolved["a"]!!.contains("{"))
    }

    @Test
    fun shouldLeaveUnknownReferences() {
        val tokens = mapOf("x" to "{unknown.key}")
        val resolved = TokenReferenceResolver.resolve(tokens)
        assertEquals("{unknown.key}", resolved["x"])
    }
}

class ThemeValidatorTest {
    @Test
    fun shouldAcceptValidHexColors() {
        assertTrue(ThemeValidator.isValidHexColor("#FF5722"))
        assertTrue(ThemeValidator.isValidHexColor("#fff"))
        assertTrue(ThemeValidator.isValidHexColor("#FF572280"))
    }

    @Test
    fun shouldRejectInvalidHexColors() {
        assertFalse(ThemeValidator.isValidHexColor("FF5722"))
        assertFalse(ThemeValidator.isValidHexColor("#GG5722"))
        assertFalse(ThemeValidator.isValidHexColor("#12345"))
    }

    @Test
    fun shouldAcceptValidTokenKeys() {
        assertTrue(ThemeValidator.isValidTokenKey("color.primary"))
        assertTrue(ThemeValidator.isValidTokenKey("typography.body.fontSize"))
        assertTrue(ThemeValidator.isValidTokenKey("shape"))
    }

    @Test
    fun shouldRejectInvalidTokenKeys() {
        assertFalse(ThemeValidator.isValidTokenKey(""))
        assertFalse(ThemeValidator.isValidTokenKey(".color"))
        assertFalse(ThemeValidator.isValidTokenKey("color."))
    }
}

class M3PaletteGeneratorTest {
    @Test
    fun shouldGeneratePaletteFromSeedColor() {
        val palette = M3PaletteGenerator.generate("#6750A4")
        // Basic structural checks
        assertTrue(palette.primary.tone0.startsWith("#"))
        assertTrue(palette.primary.tone100.startsWith("#"))
        assertTrue(palette.error.tone40.startsWith("#"))
        assertEquals("#6750A4", palette.seedColor)
    }
}
