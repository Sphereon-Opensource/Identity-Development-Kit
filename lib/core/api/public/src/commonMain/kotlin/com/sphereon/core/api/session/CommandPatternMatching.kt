/*
 * © 2025 Sphereon International B.V.
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
 *
 */

package com.sphereon.core.api.session

/**
 * Pattern matching for command IDs.
 * Supports:
 * - `*` = match any single segment
 * - `**` = match any remaining segments (any depth)
 * - `{a,b}` = match one of multiple segment values
 *
 * Examples:
 * - matchesAdvancedPattern("kms.**", "kms.keys.get") == true
 * - matchesAdvancedPattern("party.{parties,resources}.create", "party.parties.create") == true
 * - matchesAdvancedPattern("did.*.resolve", "did.manager.resolve") == true
 */
fun matchesAdvancedPattern(pattern: String, commandId: String): Boolean {
    val patternSegments = pattern.split('.')
    val commandSegments = commandId.split('.')

    var patternIdx = 0
    var commandIdx = 0

    while (patternIdx < patternSegments.size && commandIdx < commandSegments.size) {
        val patternSegment = patternSegments[patternIdx]
        val commandSegment = commandSegments[commandIdx]

        when {
            patternSegment == "**" -> {
                // ** matches any remaining segments
                return true
            }
            matchSegment(patternSegment, commandSegment) -> {
                patternIdx++
                commandIdx++
            }
            else -> return false
        }
    }

    // Consume trailing ** if present
    if (patternIdx == patternSegments.size - 1 && patternSegments[patternIdx] == "**") {
        return true
    }

    return patternIdx == patternSegments.size && commandIdx == commandSegments.size
}

/**
 * Matches a single pattern segment against a command segment.
 */
private fun matchSegment(patternSegment: String, commandSegment: String): Boolean {
    return when {
        patternSegment == "*" -> true
        patternSegment.startsWith("{") && patternSegment.endsWith("}") -> {
            val options = patternSegment
                .removePrefix("{")
                .removeSuffix("}")
                .split(',')
                .map { it.trim() }
            commandSegment in options
        }
        else -> patternSegment == commandSegment
    }
}

/**
 * Simple glob match for backwards compatibility.
 * Supports:
 * - Exact match: "module.service.command"
 * - Single wildcard suffix: "module.*" (matches one level)
 * - Double wildcard suffix: "module.**" (matches any depth)
 */
fun matchesSimplePattern(pattern: String, commandId: String): Boolean {
    if (!pattern.contains('*')) return pattern == commandId

    if (pattern.endsWith(".**")) {
        val prefix = pattern.dropLast(3)
        return commandId.startsWith("$prefix.") || commandId == prefix
    }

    if (pattern.endsWith(".*")) {
        val prefix = pattern.dropLast(2)
        val suffix = commandId.removePrefix("$prefix.")
        // Should match exactly one segment (no dots)
        return commandId.startsWith("$prefix.") && !suffix.contains('.')
    }

    return pattern == commandId
}
