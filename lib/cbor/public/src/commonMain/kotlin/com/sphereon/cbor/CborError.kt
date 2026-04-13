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

package com.sphereon.cbor

import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.error.IdkError.Severity
import com.sphereon.core.api.error.IdkErrorType
import com.sphereon.core.compat.JsExportCompat

/**
 * CBOR-specific error definitions following the IdkError pattern.
 *
 * These errors are used by the result-based decoder ([CborDecoder]) to provide
 * structured error information instead of throwing exceptions.
 */
@JsExportCompat
object CborError {
    /**
     * Error when the maximum recursion depth is exceeded during decoding.
     *
     * This typically indicates either maliciously crafted CBOR designed to cause
     * stack overflow, or genuinely deeply nested data structures.
     *
     * @param current The current depth when the limit was hit
     * @param max The configured maximum depth limit
     */
    fun MAX_DEPTH_EXCEEDED(
        current: Int,
        max: Int,
        severity: Severity = Severity.ERROR,
        causes: List<IdkErrorType> = emptyList(),
    ) = IdkError(
        code = "CBOR_MAX_DEPTH_EXCEEDED",
        message =
            IdkError.Message(
                i18nKey = "com.sphereon.cbor.error.max-depth-exceeded",
                i18nParams = mapOf("current" to current, "max" to max),
                defaultMessage = "Maximum CBOR nesting depth exceeded: current=$current, max=$max",
            ),
        severity = severity,
        causes = causes,
        meta = mapOf("currentDepth" to current, "maxDepth" to max),
    )

    /**
     * Error when the maximum number of items is exceeded during decoding.
     *
     * This protects against memory exhaustion attacks using indefinite-length
     * arrays or maps with extremely large item counts.
     *
     * @param current The current item count when the limit was hit
     * @param max The configured maximum item limit
     */
    fun MAX_ITEMS_EXCEEDED(
        current: Int,
        max: Int,
        severity: Severity = Severity.ERROR,
        causes: List<IdkErrorType> = emptyList(),
    ) = IdkError(
        code = "CBOR_MAX_ITEMS_EXCEEDED",
        message =
            IdkError.Message(
                i18nKey = "com.sphereon.cbor.error.max-items-exceeded",
                i18nParams = mapOf("current" to current, "max" to max),
                defaultMessage = "Maximum CBOR item count exceeded: current=$current, max=$max",
            ),
        severity = severity,
        causes = causes,
        meta = mapOf("currentItems" to current, "maxItems" to max),
    )

    /**
     * Error when the maximum string/byte string length is exceeded during decoding.
     *
     * This protects against memory exhaustion attacks using extremely large strings.
     *
     * @param length The length that exceeded the limit
     * @param max The configured maximum string length
     */
    fun MAX_STRING_LENGTH_EXCEEDED(
        length: Int,
        max: Int,
        severity: Severity = Severity.ERROR,
        causes: List<IdkErrorType> = emptyList(),
    ) = IdkError(
        code = "CBOR_MAX_STRING_LENGTH_EXCEEDED",
        message =
            IdkError.Message(
                i18nKey = "com.sphereon.cbor.error.max-string-length-exceeded",
                i18nParams = mapOf("length" to length, "max" to max),
                defaultMessage = "Maximum CBOR string length exceeded: length=$length, max=$max",
            ),
        severity = severity,
        causes = causes,
        meta = mapOf("stringLength" to length, "maxLength" to max),
    )

    /**
     * Error when there are leftover bytes after decoding a complete CBOR item.
     *
     * This indicates either malformed input or multiple concatenated CBOR items
     * when only one was expected.
     *
     * @param count The number of leftover bytes
     */
    fun LEFTOVER_BYTES(
        count: Int,
        severity: Severity = Severity.ERROR,
        causes: List<IdkErrorType> = emptyList(),
    ) = IdkError(
        code = "CBOR_LEFTOVER_BYTES",
        message =
            IdkError.Message(
                i18nKey = "com.sphereon.cbor.error.leftover-bytes",
                i18nParams = mapOf("count" to count),
                defaultMessage = "$count bytes leftover after decoding CBOR",
            ),
        severity = severity,
        causes = causes,
        meta = mapOf("leftoverBytes" to count),
    )

    /**
     * Error when attempting to read beyond the available data.
     *
     * @param offset The offset where the read was attempted
     * @param required The number of bytes required
     * @param available The number of bytes actually available
     */
    fun OUT_OF_BOUNDS(
        offset: Int,
        required: Int,
        available: Int,
        severity: Severity = Severity.ERROR,
        causes: List<IdkErrorType> = emptyList(),
    ) = IdkError(
        code = "CBOR_OUT_OF_BOUNDS",
        message =
            IdkError.Message(
                i18nKey = "com.sphereon.cbor.error.out-of-bounds",
                i18nParams = mapOf("offset" to offset, "required" to required, "available" to available),
                defaultMessage = "CBOR out of bounds: tried to read $required bytes at offset $offset, but only $available bytes available",
            ),
        severity = severity,
        causes = causes,
        meta = mapOf("offset" to offset, "required" to required, "available" to available),
    )

    /**
     * Error when a string contains invalid UTF-8 encoding.
     *
     * @param offset The offset where the invalid UTF-8 was found
     */
    fun INVALID_UTF8(
        offset: Int,
        severity: Severity = Severity.ERROR,
        causes: List<IdkErrorType> = emptyList(),
    ) = IdkError(
        code = "CBOR_INVALID_UTF8",
        message =
            IdkError.Message(
                i18nKey = "com.sphereon.cbor.error.invalid-utf8",
                i18nParams = mapOf("offset" to offset),
                defaultMessage = "Invalid UTF-8 encoding at offset $offset",
            ),
        severity = severity,
        causes = causes,
        meta = mapOf("offset" to offset),
    )

    /**
     * Error when the additional information field has an invalid value.
     *
     * @param value The invalid additional information value
     * @param offset The offset where it was found
     */
    fun INVALID_ADDITIONAL_INFO(
        value: Int,
        offset: Int,
        severity: Severity = Severity.ERROR,
        causes: List<IdkErrorType> = emptyList(),
    ) = IdkError(
        code = "CBOR_INVALID_ADDITIONAL_INFO",
        message =
            IdkError.Message(
                i18nKey = "com.sphereon.cbor.error.invalid-additional-info",
                i18nParams = mapOf("value" to value, "offset" to offset),
                defaultMessage = "Invalid CBOR additional information value $value at offset $offset",
            ),
        severity = severity,
        causes = causes,
        meta = mapOf("additionalInfo" to value, "offset" to offset),
    )

    /**
     * Error when a BREAK code is found outside an indefinite-length item.
     *
     * @param offset The offset where the unexpected BREAK was found
     */
    fun UNEXPECTED_BREAK(
        offset: Int,
        severity: Severity = Severity.ERROR,
        causes: List<IdkErrorType> = emptyList(),
    ) = IdkError(
        code = "CBOR_UNEXPECTED_BREAK",
        message =
            IdkError.Message(
                i18nKey = "com.sphereon.cbor.error.unexpected-break",
                i18nParams = mapOf("offset" to offset),
                defaultMessage = "Unexpected BREAK code outside indefinite-length item at offset $offset",
            ),
        severity = severity,
        causes = causes,
        meta = mapOf("offset" to offset),
    )

    /**
     * Error when indefinite length is not allowed for a specific major type.
     *
     * @param majorType The major type where indefinite length was incorrectly used
     * @param offset The offset where it was found
     */
    fun INDEFINITE_LENGTH_NOT_ALLOWED(
        majorType: Int,
        offset: Int,
        severity: Severity = Severity.ERROR,
        causes: List<IdkErrorType> = emptyList(),
    ) = IdkError(
        code = "CBOR_INDEFINITE_LENGTH_NOT_ALLOWED",
        message =
            IdkError.Message(
                i18nKey = "com.sphereon.cbor.error.indefinite-length-not-allowed",
                i18nParams = mapOf("majorType" to majorType, "offset" to offset),
                defaultMessage = "Indefinite length (additional information 31) not allowed for major type $majorType at offset $offset",
            ),
        severity = severity,
        causes = causes,
        meta = mapOf("majorType" to majorType, "offset" to offset),
    )

    /**
     * General CBOR decoding error for unexpected situations.
     *
     * @param message A description of what went wrong
     * @param throwable The original exception if one was caught
     */
    fun DECODE_ERROR(
        message: String,
        throwable: Throwable? = null,
        severity: Severity = Severity.ERROR,
        causes: List<IdkErrorType> = emptyList(),
    ) = IdkError(
        code = "CBOR_DECODE_ERROR",
        message =
            IdkError.Message(
                i18nKey = "com.sphereon.cbor.error.decode-error",
                i18nParams = mapOf("message" to message),
                defaultMessage = "CBOR decode error: $message",
            ),
        severity = severity,
        causes = causes,
        exception = throwable,
    )
}
