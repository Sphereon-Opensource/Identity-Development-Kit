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

import com.sphereon.conf.theme.core.defaults.SystemDefaults
import com.sphereon.conf.theme.core.model.ThemeVariant
import com.sphereon.conf.theme.core.token.TokenFlattener
import com.sphereon.conf.theme.core.token.TokenReferenceResolver
import com.sphereon.core.compat.JsExportCompat

/**
 * Pre-resolved flat token maps for system defaults.
 * Calls SystemDefaults + TokenFlattener + TokenReferenceResolver to produce
 * ready-to-use token maps without requiring the full resolution pipeline.
 */
@JsExportCompat
object WebSystemDefaults {
    /** Pre-resolved light theme tokens */
    val light: Map<String, String> by lazy { resolveDefaults(ThemeVariant.LIGHT) }

    /** Pre-resolved dark theme tokens */
    val dark: Map<String, String> by lazy { resolveDefaults(ThemeVariant.DARK) }

    /** Pre-resolved high-contrast theme tokens */
    val highContrast: Map<String, String> by lazy { resolveDefaults(ThemeVariant.HIGH_CONTRAST) }

    /**
     * Get pre-resolved token map for a variant.
     */
    fun forVariant(variant: ThemeVariant): Map<String, String> =
        when (variant) {
            ThemeVariant.LIGHT -> light
            ThemeVariant.DARK -> dark
            ThemeVariant.HIGH_CONTRAST -> highContrast
        }

    /**
     * Get pre-resolved token map for a variant string ("light", "dark", "high_contrast").
     */
    fun forVariantString(variant: String): Map<String, String> =
        when (variant.lowercase()) {
            "dark" -> dark
            "high_contrast" -> highContrast
            else -> light
        }

    private fun resolveDefaults(variant: ThemeVariant): Map<String, String> {
        val definition =
            when (variant) {
                ThemeVariant.DARK -> SystemDefaults.baselineDark
                ThemeVariant.HIGH_CONTRAST -> SystemDefaults.baselineHighContrast
                ThemeVariant.LIGHT -> SystemDefaults.baseline
            }
        val flat = TokenFlattener.merge(listOf(definition))
        return TokenReferenceResolver.resolve(flat)
    }
}
