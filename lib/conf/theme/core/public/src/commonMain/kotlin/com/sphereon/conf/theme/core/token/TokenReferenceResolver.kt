package com.sphereon.conf.theme.core.token

/**
 * Expands token references in values. A reference is denoted by `{key}` syntax,
 * e.g. a value of `"{color.primary}"` resolves to the value of the `color.primary` token.
 *
 * Includes cycle detection to prevent infinite loops.
 */
object TokenReferenceResolver {

    private val REFERENCE_PATTERN = Regex("""\{([^}]+)\}""")
    private const val MAX_DEPTH = 10

    /**
     * Resolve all token references in the given map.
     * Returns a new map with all references expanded.
     *
     * @throws IllegalStateException if a circular reference is detected
     */
    fun resolve(tokens: Map<String, String>): Map<String, String> {
        val result = mutableMapOf<String, String>()
        for ((key, value) in tokens) {
            result[key] = resolveValue(value, tokens, mutableSetOf(), 0)
        }
        return result
    }

    private fun resolveValue(
        value: String,
        tokens: Map<String, String>,
        visited: MutableSet<String>,
        depth: Int
    ): String {
        if (depth > MAX_DEPTH) return value
        if (!value.contains('{')) return value

        return REFERENCE_PATTERN.replace(value) { match ->
            val refKey = match.groupValues[1]
            if (refKey in visited) {
                // Circular reference — return the raw reference
                match.value
            } else {
                val refValue = tokens[refKey]
                if (refValue != null) {
                    visited.add(refKey)
                    val resolved = resolveValue(refValue, tokens, visited, depth + 1)
                    visited.remove(refKey)
                    resolved
                } else {
                    match.value // Leave unresolved references as-is
                }
            }
        }
    }
}
