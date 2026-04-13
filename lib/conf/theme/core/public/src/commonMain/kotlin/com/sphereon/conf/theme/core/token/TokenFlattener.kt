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
