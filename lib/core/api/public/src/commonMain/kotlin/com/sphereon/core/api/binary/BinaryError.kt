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

package com.sphereon.core.api.binary

import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.error.IdkErrorType
import com.sphereon.core.api.session.currentTimeMillis
import kotlinx.serialization.Serializable

/**
 * Serializable error envelope for transport across all codecs (JSON, Protobuf, CBOR).
 *
 * This error type is designed to be transport-neutral and codec-agnostic,
 * providing a consistent error format across HTTP REST APIs, gRPC, and other transports.
 *
 * **Usage:**
 * ```kotlin
 * // Create from IdkError
 * val binaryError = BinaryError.fromIdkError(idkError, traceId = "abc123")
 *
 * // Create directly
 * val error = BinaryError.badRequest("Invalid input format")
 *
 * // Convert back to IdkError for internal handling
 * val idkError = binaryError.toIdkError()
 * ```
 *
 * **Protobuf Schema (for reference):**
 * ```protobuf
 * message BinaryError {
 *   string code = 1;
 *   string message = 2;
 *   map<string, string> details = 3;
 *   int64 timestamp = 4;
 *   optional string trace_id = 5;
 *   optional string path = 6;
 * }
 * ```
 *
 * @property code Machine-readable error code (e.g., "NOT_FOUND", "UNAUTHORIZED")
 * @property message Human-readable error message
 * @property details Additional key-value details about the error
 * @property timestamp Unix timestamp in milliseconds when the error occurred
 * @property traceId Optional trace ID for distributed tracing
 * @property path Optional request path that caused the error
 */
@Serializable
data class BinaryError(
    val code: String,
    val message: String,
    val details: Map<String, String> = emptyMap(),
    val timestamp: Long = currentTimeMillis(),
    val traceId: String? = null,
    val path: String? = null,
) {
    /**
     * Converts this BinaryError to an IdkError for internal error handling.
     */
    fun toIdkError(): IdkError =
        when (code) {
            "UNAUTHORIZED" -> {
                IdkError.UNAUTHORIZED_ERROR(message = message)
            }

            "FORBIDDEN" -> {
                IdkError.FORBIDDEN_ERROR(message = message)
            }

            "NOT_FOUND" -> {
                IdkError.NOT_FOUND_ERROR(message = message)
            }

            "BAD_REQUEST" -> {
                IdkError.ILLEGAL_ARGUMENT_ERROR(message = message)
            }

            "INVALID_STATE" -> {
                IdkError.INVALID_STATE(message = message)
            }

            "COMMAND_DISABLED" -> {
                IdkError.COMMAND_DISABLED_ERROR(
                    commandId = details["commandId"] ?: "unknown",
                )
            }

            "COMMAND_NOT_AUTHORIZED" -> {
                IdkError.COMMAND_NOT_AUTHORIZED_ERROR(
                    commandId = details["commandId"] ?: "unknown",
                    reason = message,
                )
            }

            else -> {
                IdkError.UNKNOWN_ERROR(message = message)
            }
        }

    /**
     * Creates a copy with additional details.
     */
    fun withDetails(additionalDetails: Map<String, String>): BinaryError = copy(details = details + additionalDetails)

    /**
     * Creates a copy with a trace ID.
     */
    fun withTraceId(traceId: String): BinaryError = copy(traceId = traceId)

    /**
     * Creates a copy with a path.
     */
    fun withPath(path: String): BinaryError = copy(path = path)

    companion object {
        /**
         * Creates a BinaryError from an IdkError.
         *
         * @param error The IdkError to convert
         * @param traceId Optional trace ID for distributed tracing
         * @param path Optional request path
         */
        fun fromIdkError(
            error: IdkErrorType,
            traceId: String? = null,
            path: String? = null,
        ): BinaryError =
            BinaryError(
                code = error.code,
                message = error.message.defaultMessage,
                details = error.meta.mapValues { it.value?.toString() ?: "" },
                traceId = traceId,
                path = path,
            )

        /**
         * Creates an unauthorized error (401).
         */
        fun unauthorized(
            message: String = "Unauthorized",
            traceId: String? = null,
        ): BinaryError =
            BinaryError(
                code = "UNAUTHORIZED",
                message = message,
                traceId = traceId,
            )

        /**
         * Creates a forbidden error (403).
         */
        fun forbidden(
            message: String = "Forbidden",
            traceId: String? = null,
        ): BinaryError =
            BinaryError(
                code = "FORBIDDEN",
                message = message,
                traceId = traceId,
            )

        /**
         * Creates a not found error (404).
         */
        fun notFound(
            message: String = "Not found",
            resource: String? = null,
            traceId: String? = null,
        ): BinaryError =
            BinaryError(
                code = "NOT_FOUND",
                message = message,
                details = resource?.let { mapOf("resource" to it) } ?: emptyMap(),
                traceId = traceId,
            )

        /**
         * Creates a bad request error (400).
         */
        fun badRequest(
            message: String = "Bad request",
            traceId: String? = null,
        ): BinaryError =
            BinaryError(
                code = "BAD_REQUEST",
                message = message,
                traceId = traceId,
            )

        /**
         * Creates an internal server error (500).
         */
        fun internalError(
            message: String = "Internal server error",
            traceId: String? = null,
        ): BinaryError =
            BinaryError(
                code = "INTERNAL_ERROR",
                message = message,
                traceId = traceId,
            )

        /**
         * Creates a service unavailable error (503).
         */
        fun serviceUnavailable(
            message: String = "Service unavailable",
            traceId: String? = null,
        ): BinaryError =
            BinaryError(
                code = "SERVICE_UNAVAILABLE",
                message = message,
                traceId = traceId,
            )

        /**
         * Maps a BinaryError code to an HTTP status code.
         */
        fun codeToHttpStatus(code: String): Int =
            when (code) {
                "UNAUTHORIZED" -> HTTP_UNAUTHORIZED
                "FORBIDDEN" -> HTTP_FORBIDDEN
                "NOT_FOUND" -> HTTP_NOT_FOUND
                "BAD_REQUEST" -> HTTP_BAD_REQUEST
                "ILLEGAL_ARGUMENT" -> HTTP_BAD_REQUEST
                "INVALID_STATE" -> HTTP_CONFLICT
                "COMMAND_DISABLED" -> HTTP_SERVICE_UNAVAILABLE
                "COMMAND_NOT_AUTHORIZED" -> HTTP_FORBIDDEN
                "SERVICE_UNAVAILABLE" -> HTTP_SERVICE_UNAVAILABLE
                "INTERNAL_ERROR" -> HTTP_INTERNAL_SERVER_ERROR
                else -> HTTP_INTERNAL_SERVER_ERROR
            }

        /**
         * Maps an HTTP status code to a BinaryError code.
         */
        fun httpStatusToCode(status: Int): String =
            when (status) {
                HTTP_BAD_REQUEST -> "BAD_REQUEST"
                HTTP_UNAUTHORIZED -> "UNAUTHORIZED"
                HTTP_FORBIDDEN -> "FORBIDDEN"
                HTTP_NOT_FOUND -> "NOT_FOUND"
                HTTP_CONFLICT -> "INVALID_STATE"
                HTTP_INTERNAL_SERVER_ERROR -> "INTERNAL_ERROR"
                HTTP_SERVICE_UNAVAILABLE -> "SERVICE_UNAVAILABLE"
                else -> "UNKNOWN_ERROR"
            }

        private const val HTTP_BAD_REQUEST = 400
        private const val HTTP_UNAUTHORIZED = 401
        private const val HTTP_FORBIDDEN = 403
        private const val HTTP_NOT_FOUND = 404
        private const val HTTP_CONFLICT = 409
        private const val HTTP_INTERNAL_SERVER_ERROR = 500
        private const val HTTP_SERVICE_UNAVAILABLE = 503
    }
}
