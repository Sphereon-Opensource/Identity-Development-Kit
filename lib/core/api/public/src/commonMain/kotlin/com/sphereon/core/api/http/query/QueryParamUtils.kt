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
 */

@file:OptIn(ExperimentalUuidApi::class)

package com.sphereon.core.api.http.query

import com.sphereon.core.api.auth.AuthHeaders
import com.sphereon.core.api.http.util.RequestUtils
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Generic HTTP query parameter parsing utilities.
 * These are framework-agnostic helpers for parsing common query parameter types.
 */
object QueryParamUtils {
    /**
     * Parse a UUID from string, returning null if invalid.
     */
    fun parseUuid(value: String): Uuid? =
        try {
            Uuid.parse(value)
        } catch (_: Exception) {
            null
        }

    /**
     * Parse a required UUID from string, throwing if invalid.
     */
    fun parseRequiredUuid(
        value: String,
        fieldName: String = "id",
    ): Uuid =
        parseUuid(value)
            ?: throw IllegalArgumentException("Invalid UUID format for $fieldName: $value")

    /**
     * Parse an ISO-8601 instant from string, returning null if invalid.
     */
    fun parseInstant(value: String): Instant? =
        try {
            Instant.parse(value)
        } catch (_: Exception) {
            null
        }

    /**
     * Extract a header value with case-insensitive lookup. (backwards compatibility link)
     * Tries direct key access first (fast path), then iterates entries for a case-insensitive match.
     */
    fun extractHeaderValue(
        headers: Map<String, String>,
        name: String
    ): String? = RequestUtils.extractHeaderValue(headers, name)

    /**
     * Extract tenant ID from headers.  (backwards compatibility link)
     * Uses the canonical [AuthHeaders.X_TENANT_ID] header with case-insensitive fallback.
     */
    fun extractTenantId(headers: Map<String, String>): String? = RequestUtils.extractTenantId(headers)

    /**
     * Parse an enum value from string (case-insensitive).
     * Returns null if the value doesn't match any enum constant.
     */
    inline fun <reified T : Enum<T>> parseEnum(value: String): T? =
        try {
            enumValueOf<T>(value.uppercase())
        } catch (_: Exception) {
            null
        }
}
