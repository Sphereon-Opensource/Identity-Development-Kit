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

@file:OptIn(ExperimentalJsExport::class)

package com.sphereon.conf.theme.core

import com.sphereon.conf.theme.core.defaults.SystemDefaults
import com.sphereon.conf.theme.core.model.ResolvedTheme
import com.sphereon.conf.theme.core.model.ThemeVariant
import com.sphereon.conf.theme.core.token.TokenFlattener
import com.sphereon.conf.theme.core.token.TokenReferenceResolver
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val json =
    Json {
        prettyPrint = false
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

/**
 * Deserialize a ResolvedTheme from JSON string.
 */
@JsExport
fun resolvedThemeFromJson(jsonStr: String): ResolvedTheme = json.decodeFromString<ResolvedTheme>(jsonStr)

/**
 * Serialize a ResolvedTheme to JSON string.
 */
@JsExport
fun resolvedThemeToJson(theme: ResolvedTheme): String = json.encodeToString(theme)

/**
 * Get the serialized M3 baseline light theme as JSON.
 */
@JsExport
fun systemDefaultsLightJson(): String = json.encodeToString(SystemDefaults.baseline)

/**
 * Get the serialized M3 baseline dark theme as JSON.
 */
@JsExport
fun systemDefaultsDarkJson(): String = json.encodeToString(SystemDefaults.baselineDark)

/**
 * Get a flat token map for a given variant ("light", "dark", or "high_contrast").
 * Merges and resolves references from the system default definitions.
 */
@JsExport
fun systemDefaultsTokens(variant: String): Map<String, String> {
    val definition =
        when (variant.lowercase()) {
            "dark" -> SystemDefaults.baselineDark
            "high_contrast" -> SystemDefaults.baselineHighContrast
            else -> SystemDefaults.baseline
        }
    val flat = TokenFlattener.merge(listOf(definition))
    return TokenReferenceResolver.resolve(flat)
}
