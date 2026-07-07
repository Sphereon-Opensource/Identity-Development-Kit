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

package com.sphereon.conf.theme.core.validation

import com.sphereon.conf.theme.core.model.CssPolicyConfig
import com.sphereon.conf.theme.core.model.ThemeDefinition
import com.sphereon.conf.theme.core.model.ThemeScope
import com.sphereon.conf.theme.core.model.ThemeToken
import com.sphereon.conf.theme.core.model.ThemeTokenType
import com.sphereon.core.compat.JsExportCompat

/**
 * Validates theme definitions and individual tokens.
 */
@JsExportCompat
object ThemeValidator {
    private val HEX_COLOR_PATTERN = Regex("^#([0-9A-Fa-f]{3}|[0-9A-Fa-f]{6}|[0-9A-Fa-f]{8})$")
    private val TOKEN_KEY_PATTERN = Regex("^[a-zA-Z0-9][a-zA-Z0-9]*(\\.[a-zA-Z0-9][a-zA-Z0-9]*)*$")
    private val CSS_LENGTH_PATTERN = Regex("^-?\\d+(\\.\\d+)?(px|rem|em|%|vh|vw|pt|cm|mm|in)$")

    // Shadow: "none", or one or more layers of "<offset-x> <offset-y> [blur] [spread] <color>"
    // Each layer separated by commas. Allows px/rem/em units, rgba(), hex colors, inset keyword.
    private const val CSS_SHADOW_LAYER = "(?:inset\\s+)?(?:-?\\d+(?:\\.\\d+)?(?:px|rem|em)?\\s+){2,4}(?:#[0-9A-Fa-f]{3,8}|rgba?\\([^)]+\\))"
    private val CSS_SHADOW_PATTERN = Regex("^(?:none|$CSS_SHADOW_LAYER(?:\\s*,\\s*$CSS_SHADOW_LAYER)*)$")

    private val EXPRESSION_PATTERN = Regex("""expression\s*\(""")
    private val JAVASCRIPT_URL_PATTERN = Regex("""javascript\s*:""")
    private val IMPORT_PATTERN = Regex("""@import\b""")
    private val URL_FUNCTION_PATTERN = Regex("""url\s*\(""")
    private val MOZ_BINDING_PATTERN = Regex("""-moz-binding\s*:""")
    private val BEHAVIOR_PATTERN = Regex("""behavior\s*:""")

    data class ValidationResult(
        val valid: Boolean,
        val errors: List<String> = emptyList(),
    )

    /**
     * Validate a theme definition.
     */
    fun validate(definition: ThemeDefinition): ValidationResult {
        val errors = mutableListOf<String>()

        if (definition.id.isBlank()) {
            errors.add("Theme ID must not be blank")
        }
        if (definition.name.isBlank()) {
            errors.add("Theme name must not be blank")
        }

        errors.addAll(validateScopeKeys(definition))

        for (token in definition.tokens) {
            val tokenErrors = validateToken(token)
            errors.addAll(tokenErrors.errors)
        }

        return ValidationResult(valid = errors.isEmpty(), errors = errors)
    }

    /**
     * Enforce the key rules per scope: PRODUCT definitions require a productType,
     * APPLICATION definitions require an applicationId, and no other scope carries either key.
     */
    private fun validateScopeKeys(definition: ThemeDefinition): List<String> {
        val errors = mutableListOf<String>()

        when (definition.scope) {
            ThemeScope.PRODUCT -> {
                if (definition.productType == null) {
                    errors.add("PRODUCT-scoped definitions require a productType")
                }
                if (definition.applicationId != null) {
                    errors.add("PRODUCT-scoped definitions must not carry an applicationId")
                }
            }

            ThemeScope.APPLICATION -> {
                if (definition.applicationId == null) {
                    errors.add("APPLICATION-scoped definitions require an applicationId")
                }
                if (definition.productType != null) {
                    errors.add("APPLICATION-scoped definitions must not carry a productType")
                }
            }

            ThemeScope.SYSTEM, ThemeScope.TENANT, ThemeScope.PRINCIPAL -> {
                if (definition.productType != null) {
                    errors.add("${definition.scope}-scoped definitions must not carry a productType")
                }
                if (definition.applicationId != null) {
                    errors.add("${definition.scope}-scoped definitions must not carry an applicationId")
                }
            }
        }

        return errors
    }

    /**
     * Validate a single token.
     */
    fun validateToken(token: ThemeToken): ValidationResult {
        val errors = mutableListOf<String>()

        if (!TOKEN_KEY_PATTERN.matches(token.key)) {
            errors.add("Invalid token key format: '${token.key}' (must be dot-separated identifiers)")
        }

        if (token.value.isBlank()) {
            errors.add("Token '${token.key}' has blank value")
        }

        // Skip reference values (e.g., "{spacing.4}")
        if (!token.value.startsWith("{")) {
            when (token.type) {
                ThemeTokenType.COLOR -> {
                    if (!HEX_COLOR_PATTERN.matches(token.value)) {
                        errors.add("Token '${token.key}' has invalid hex color: '${token.value}'")
                    }
                }

                ThemeTokenType.SPACING, ThemeTokenType.BORDER_WIDTH -> {
                    if (!CSS_LENGTH_PATTERN.matches(token.value)) {
                        errors.add("Token '${token.key}' has invalid CSS length value: '${token.value}'")
                    }
                }

                ThemeTokenType.SHADOW -> {
                    if (!CSS_SHADOW_PATTERN.matches(token.value)) {
                        errors.add("Token '${token.key}' has invalid shadow value: '${token.value}'")
                    }
                }

                else -> { /* no specific validation */ }
            }
        }

        return ValidationResult(valid = errors.isEmpty(), errors = errors)
    }

    /**
     * Validate a hex color string.
     */
    fun isValidHexColor(hex: String): Boolean = HEX_COLOR_PATTERN.matches(hex)

    /**
     * Validate a token key format.
     */
    fun isValidTokenKey(key: String): Boolean = TOKEN_KEY_PATTERN.matches(key)

    /**
     * Validate custom CSS against a policy configuration.
     * Returns a list of validation error strings (empty = valid).
     */
    fun validateCustomCss(
        css: String,
        policy: CssPolicyConfig = CssPolicyConfig(),
    ): List<String> {
        val errors = mutableListOf<String>()

        // Size check
        if (css.encodeToByteArray().size > policy.maxSizeBytes) {
            errors.add("Custom CSS exceeds maximum size of ${policy.maxSizeBytes} bytes")
        }

        // Blocked selectors
        val lowerCss = css.lowercase()
        for (selector in policy.blockedSelectors) {
            if (lowerCss.contains("<$selector") || lowerCss.contains("</$selector")) {
                errors.add("Custom CSS contains blocked HTML element: $selector")
            }
        }

        // Reject expression() -- IE XSS vector
        if (EXPRESSION_PATTERN.containsMatchIn(lowerCss)) {
            errors.add("Custom CSS contains disallowed expression() function")
        }

        // Reject javascript: URLs
        if (JAVASCRIPT_URL_PATTERN.containsMatchIn(lowerCss)) {
            errors.add("Custom CSS contains disallowed javascript: URL")
        }

        // Reject @import (prevent external resource loading)
        if (IMPORT_PATTERN.containsMatchIn(lowerCss)) {
            errors.add("Custom CSS contains disallowed @import rule")
        }

        // Reject url() -- prevents data: URIs, external resource loading, javascript: in url context
        if (URL_FUNCTION_PATTERN.containsMatchIn(lowerCss)) {
            errors.add("Custom CSS contains disallowed url() function")
        }

        // Reject -moz-binding -- Mozilla XSS vector
        if (MOZ_BINDING_PATTERN.containsMatchIn(lowerCss)) {
            errors.add("Custom CSS contains disallowed -moz-binding property")
        }

        // Reject behavior -- IE XSS vector (.htc files)
        if (BEHAVIOR_PATTERN.containsMatchIn(lowerCss)) {
            errors.add("Custom CSS contains disallowed behavior property")
        }

        // Optional property allowlist
        if (policy.allowedProperties != null) {
            val propertyPattern = Regex("""([a-z-]+)\s*:""")
            val usedProperties = propertyPattern.findAll(lowerCss).map { it.groupValues[1] }.toSet()
            val disallowed = usedProperties - policy.allowedProperties
            if (disallowed.isNotEmpty()) {
                errors.add("Custom CSS uses disallowed properties: ${disallowed.joinToString(", ")}")
            }
        }

        return errors
    }
}
