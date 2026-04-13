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
