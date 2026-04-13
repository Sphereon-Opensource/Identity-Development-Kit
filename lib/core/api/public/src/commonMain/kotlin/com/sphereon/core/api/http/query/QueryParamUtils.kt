/*
 * Copyright 2025 Sphereon International B.V.
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

import kotlinx.datetime.Instant
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
    fun parseUuid(value: String): Uuid? {
        return try {
            Uuid.parse(value)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Parse a required UUID from string, throwing if invalid.
     */
    fun parseRequiredUuid(value: String, fieldName: String = "id"): Uuid {
        return parseUuid(value)
            ?: throw IllegalArgumentException("Invalid UUID format for $fieldName: $value")
    }

    /**
     * Parse an ISO-8601 instant from string, returning null if invalid.
     */
    fun parseInstant(value: String): Instant? {
        return try {
            Instant.parse(value)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Extract a header value with case-insensitive fallback.
     */
    fun extractHeaderValue(headers: Map<String, String>, name: String): String? {
        return headers[name] ?: headers[name.lowercase()]
    }

    /**
     * Extract tenant ID from X-Tenant-ID header.
     */
    fun extractTenantId(headers: Map<String, String>): String? {
        return extractHeaderValue(headers, "X-Tenant-ID")?.trim()?.takeIf { it.isNotBlank() }
    }

    /**
     * Parse an enum value from string (case-insensitive).
     * Returns null if the value doesn't match any enum constant.
     */
    inline fun <reified T : Enum<T>> parseEnum(value: String): T? {
        return try {
            enumValueOf<T>(value.uppercase())
        } catch (e: Exception) {
            null
        }
    }
}
