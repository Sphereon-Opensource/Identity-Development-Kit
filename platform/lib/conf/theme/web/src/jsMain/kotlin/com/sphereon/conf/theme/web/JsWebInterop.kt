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

package com.sphereon.conf.theme.web

/**
 * @JsExport wrappers for web utilities.
 * These are the primary entry points for TypeScript consumers.
 */

@JsExport
fun tokenKeyToCssVar(key: String): String = CssTokenMapper.tokenKeyToCssVar(key)

@JsExport
fun convertUnit(value: String): String = CssTokenMapper.convertUnit(value)

@JsExport
fun tokensToCssVars(tokens: Map<String, String>): Map<String, String> = CssTokenMapper.tokensToCssVars(tokens)

@JsExport
fun generateLegacyAliases(tokens: Map<String, String>): Map<String, String> = CssLegacyAliases.generateAliases(tokens)

@JsExport
fun applyCssVarsToRoot(vars: Map<String, String>) = DomCssInjector.applyCssVars(vars)

@JsExport
fun applyTokensToRoot(tokens: Map<String, String>) = DomCssInjector.applyTokens(tokens)

@JsExport
fun generateFoucScript(
    defaultMode: String = "system",
    cookieName: String = "sphereon-theme-mode",
    storageKey: String = "theme-mode",
): String = FoucPreventionScript.generate(defaultMode, cookieName, storageKey)

@JsExport
fun getSystemDefaultTokens(variant: String): Map<String, String> = WebSystemDefaults.forVariantString(variant)

@JsExport
fun hexToRgba(
    hex: String,
    alpha: Double,
): String = CssTokenMapper.hexToRgba(hex, alpha)
