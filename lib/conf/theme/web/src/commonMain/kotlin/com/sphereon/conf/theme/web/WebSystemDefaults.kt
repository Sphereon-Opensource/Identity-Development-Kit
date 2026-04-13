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
    fun forVariant(variant: ThemeVariant): Map<String, String> = when (variant) {
        ThemeVariant.LIGHT -> light
        ThemeVariant.DARK -> dark
        ThemeVariant.HIGH_CONTRAST -> highContrast
    }

    /**
     * Get pre-resolved token map for a variant string ("light", "dark", "high_contrast").
     */
    fun forVariantString(variant: String): Map<String, String> = when (variant.lowercase()) {
        "dark" -> dark
        "high_contrast" -> highContrast
        else -> light
    }

    private fun resolveDefaults(variant: ThemeVariant): Map<String, String> {
        val definition = when (variant) {
            ThemeVariant.DARK -> SystemDefaults.baselineDark
            ThemeVariant.HIGH_CONTRAST -> SystemDefaults.baselineHighContrast
            ThemeVariant.LIGHT -> SystemDefaults.baseline
        }
        val flat = TokenFlattener.merge(listOf(definition))
        return TokenReferenceResolver.resolve(flat)
    }
}
