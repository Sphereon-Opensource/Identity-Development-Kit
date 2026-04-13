/*
 * © 2026 Sphereon International B.V.
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

package com.sphereon.core.api.conf

import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * Parses property keys to extract protection prefixes.
 *
 * Different implementations handle different key formats:
 * - [DotPrefixProtectionParser]: Handles dot-separated prefixes (e.g., "final.db.host")
 * - [EnvPrefixProtectionParser]: Handles underscore-separated prefixes (e.g., "FINAL_DB_HOST")
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("ProtectionKeyParser", exact = true)
interface ProtectionKeyParser {
    /**
     * Parse a key and extract any protection prefixes.
     *
     * @param key The raw property key (e.g., "final.protected.db.password")
     * @return ParsedProtectedKey with canonical key and protection metadata
     */
    fun parse(key: String): ParsedProtectedKey
}

/**
 * Default implementation that handles dot-separated prefixes.
 *
 * Recognizes the following patterns:
 * - "final.protected.key" → canonicalKey="key", isFinal=true, isProtected=true
 * - "protected.final.key" → canonicalKey="key", isFinal=true, isProtected=true
 * - "final.key" → canonicalKey="key", isFinal=true
 * - "protected.key" → canonicalKey="key", isProtected=true
 * - "key" → canonicalKey="key", no protection
 *
 * Parsing is case-insensitive.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("DotPrefixProtectionParser", exact = true)
class DotPrefixProtectionParser : ProtectionKeyParser {
    override fun parse(key: String): ParsedProtectedKey {
        var remaining = key
        var isFinal = false
        var isProtected = false

        // Check for "final.protected." first (longest match)
        if (remaining.lowercase().startsWith(ProtectionPrefixes.FINAL_PROTECTED.lowercase())) {
            remaining = remaining.substring(ProtectionPrefixes.FINAL_PROTECTED.length)
            isFinal = true
            isProtected = true
            // Check for "protected.final." (alternate order)
        } else if (remaining.lowercase().startsWith(ProtectionPrefixes.PROTECTED_FINAL.lowercase())) {
            remaining = remaining.substring(ProtectionPrefixes.PROTECTED_FINAL.length)
            isFinal = true
            isProtected = true
            // Check for "final." alone
        } else if (remaining.lowercase().startsWith(ProtectionPrefixes.FINAL.lowercase())) {
            remaining = remaining.substring(ProtectionPrefixes.FINAL.length)
            isFinal = true
            // Check for "protected." alone
        } else if (remaining.lowercase().startsWith(ProtectionPrefixes.PROTECTED.lowercase())) {
            remaining = remaining.substring(ProtectionPrefixes.PROTECTED.length)
            isProtected = true
        }

        return ParsedProtectedKey(
            canonicalKey = remaining,
            protection =
                PropertyProtection(
                    isFinal = isFinal,
                    isInterpolationProtected = isProtected,
                ),
        )
    }
}

/**
 * Parser for environment variables using underscore prefixes.
 *
 * Recognizes the following patterns:
 * - "FINAL_PROTECTED_DB_HOST" → canonicalKey="DB_HOST", isFinal=true, isProtected=true
 * - "PROTECTED_FINAL_DB_HOST" → canonicalKey="DB_HOST", isFinal=true, isProtected=true
 * - "FINAL_DB_HOST" → canonicalKey="DB_HOST", isFinal=true
 * - "PROTECTED_DB_HOST" → canonicalKey="DB_HOST", isProtected=true
 * - "DB_HOST" → canonicalKey="DB_HOST", no protection
 *
 * Parsing is case-insensitive.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("EnvPrefixProtectionParser", exact = true)
class EnvPrefixProtectionParser : ProtectionKeyParser {
    override fun parse(key: String): ParsedProtectedKey {
        var remaining = key
        var isFinal = false
        var isProtected = false

        // Check for "FINAL_PROTECTED_" first (longest match)
        if (remaining.uppercase().startsWith(ProtectionPrefixes.FINAL_PROTECTED_ENV)) {
            remaining = remaining.substring(ProtectionPrefixes.FINAL_PROTECTED_ENV.length)
            isFinal = true
            isProtected = true
            // Check for "PROTECTED_FINAL_" (alternate order)
        } else if (remaining.uppercase().startsWith(ProtectionPrefixes.PROTECTED_FINAL_ENV)) {
            remaining = remaining.substring(ProtectionPrefixes.PROTECTED_FINAL_ENV.length)
            isFinal = true
            isProtected = true
            // Check for "FINAL_" alone
        } else if (remaining.uppercase().startsWith(ProtectionPrefixes.FINAL_ENV)) {
            remaining = remaining.substring(ProtectionPrefixes.FINAL_ENV.length)
            isFinal = true
            // Check for "PROTECTED_" alone
        } else if (remaining.uppercase().startsWith(ProtectionPrefixes.PROTECTED_ENV)) {
            remaining = remaining.substring(ProtectionPrefixes.PROTECTED_ENV.length)
            isProtected = true
        }

        return ParsedProtectedKey(
            canonicalKey = remaining,
            protection =
                PropertyProtection(
                    isFinal = isFinal,
                    isInterpolationProtected = isProtected,
                ),
        )
    }
}

/** Default parser for dot-separated keys (properties files, YAML) */
val DefaultProtectionKeyParser: ProtectionKeyParser = DotPrefixProtectionParser()

/** Default parser for environment variables */
val DefaultEnvProtectionKeyParser: ProtectionKeyParser = EnvPrefixProtectionParser()
