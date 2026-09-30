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

package com.sphereon.catalog.eu.error

import com.sphereon.core.api.error.ErrorCategory
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.error.IdkErrorType
import kotlinx.serialization.Serializable

@Serializable
enum class CatalogErrorCode(
    val code: String,
) {
    MALFORMED_XML("CATALOG_MALFORMED_XML"),
    SCHEMA_VIOLATION("CATALOG_SCHEMA_VIOLATION"),
    UNEXPECTED_ROOT("CATALOG_UNEXPECTED_ROOT"),
    INDEX_UNAVAILABLE("CATALOG_INDEX_UNAVAILABLE"),
    FETCH_FAILED("CATALOG_FETCH_FAILED"),
    INVALID_ENTRY_PATH("CATALOG_INVALID_ENTRY_PATH"),
    SIGNATURE_INVALID("CATALOG_SIGNATURE_INVALID"),
}

/**
 * Serializable error for EU catalogue parsing and lookup. [path] locates the offending element when known.
 */
@Serializable
data class CatalogError(
    val errorCode: CatalogErrorCode,
    val reason: String,
    val path: String? = null,
) : IdkErrorType {
    override val code: String get() = errorCode.code
    override val message: IdkError.Message
        get() =
            IdkError.Message(
                i18nKey = "com.sphereon.catalog.eu.error.${errorCode.name.lowercase().replace('_', '-')}",
                i18nParams = mapOf("reason" to reason, "path" to path),
                defaultMessage = if (path == null) reason else "$reason (at $path)",
            )
    override val severity: IdkError.Severity get() = IdkError.Severity.ERROR
    override val category: ErrorCategory
        get() =
            when (errorCode) {
                CatalogErrorCode.INDEX_UNAVAILABLE, CatalogErrorCode.FETCH_FAILED -> ErrorCategory.UNAVAILABLE
                else -> ErrorCategory.VALIDATION
            }
    override val exception: Throwable? get() = null
    override val causes: List<IdkErrorType> get() = emptyList()
    override val meta: Map<String, Any?> get() = mapOf("reason" to reason, "path" to path)
}
