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

package com.sphereon.conf.theme.core.token

import com.sphereon.conf.theme.core.model.ThemeDefinition

/**
 * Merges a stack of ThemeDefinitions into a flat token map.
 * Later definitions in the list override earlier ones (higher precedence wins).
 */
object TokenFlattener {
    /**
     * Merge a list of definitions in precedence order (first = lowest priority).
     * Returns a flat map of token key → token value.
     */
    fun merge(definitions: List<ThemeDefinition>): Map<String, String> {
        val result = mutableMapOf<String, String>()
        for (definition in definitions) {
            for (token in definition.tokens) {
                result[token.key] = token.value
            }
        }
        return result
    }
}
