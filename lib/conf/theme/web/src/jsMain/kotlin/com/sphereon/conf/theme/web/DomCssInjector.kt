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

import kotlinx.browser.document

/**
 * Applies CSS custom properties to the document root element.
 * JS-only — uses kotlinx.browser.document.
 */
object DomCssInjector {
    /**
     * Apply a map of CSS custom properties to document.documentElement.
     */
    fun applyCssVars(vars: Map<String, String>) {
        val root = document.documentElement ?: return
        for ((prop, value) in vars) {
            root.asDynamic().style.setProperty(prop, value)
        }
    }

    /**
     * Convert IDK tokens to CSS vars (including legacy aliases) and apply to :root.
     */
    fun applyTokens(tokens: Map<String, String>) {
        val cssVars = CssTokenMapper.tokensToCssVars(tokens)
        val legacyAliases = CssLegacyAliases.generateAliases(tokens)
        applyCssVars(cssVars + legacyAliases)
    }
}
