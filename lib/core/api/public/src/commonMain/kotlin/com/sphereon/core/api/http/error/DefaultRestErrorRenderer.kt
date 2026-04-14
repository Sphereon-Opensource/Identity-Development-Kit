package com.sphereon.core.api.http.error

import com.sphereon.core.api.error.ErrorCategory
import com.sphereon.core.api.error.IdkErrorType
import com.sphereon.core.api.http.GenericHttpRequest
import com.sphereon.core.api.http.GenericHttpResponse
import com.sphereon.core.compat.JsExportCompat
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Standard REST error response envelope.
 */
@JsExportCompat
@Serializable
data class RestErrorBody(
    val error: RestErrorDetail,
)

/**
 * Error detail within the REST error envelope.
 */
@JsExportCompat
@Serializable
data class RestErrorDetail(
    val code: String,
    val message: String,
    val details: Map<String, String> = emptyMap(),
)

/**
 * Canonical mapping from [ErrorCategory] to HTTP status code.
 *
 * This is the SINGLE SOURCE OF TRUTH for category-to-HTTP-status mapping.
 * All HTTP error rendering should use this function.
 */
fun categoryToHttpStatus(category: ErrorCategory): Int =
    when (category) {
        ErrorCategory.VALIDATION -> HTTP_BAD_REQUEST
        ErrorCategory.UNAUTHORIZED -> HTTP_UNAUTHORIZED
        ErrorCategory.FORBIDDEN -> HTTP_FORBIDDEN
        ErrorCategory.NOT_FOUND -> HTTP_NOT_FOUND
        ErrorCategory.CONFLICT -> HTTP_CONFLICT
        ErrorCategory.PRECONDITION_FAILED -> HTTP_PRECONDITION_FAILED
        ErrorCategory.RATE_LIMITED -> HTTP_TOO_MANY_REQUESTS
        ErrorCategory.UNAVAILABLE -> HTTP_SERVICE_UNAVAILABLE
        ErrorCategory.INTERNAL -> HTTP_INTERNAL_SERVER_ERROR
        ErrorCategory.PROTOCOL -> HTTP_NOT_IMPLEMENTED
    }

private const val HTTP_BAD_REQUEST = 400
private const val HTTP_UNAUTHORIZED = 401
private const val HTTP_FORBIDDEN = 403
private const val HTTP_NOT_FOUND = 404
private const val HTTP_CONFLICT = 409
private const val HTTP_PRECONDITION_FAILED = 412
private const val HTTP_TOO_MANY_REQUESTS = 429
private const val HTTP_INTERNAL_SERVER_ERROR = 500
private const val HTTP_NOT_IMPLEMENTED = 501
private const val HTTP_SERVICE_UNAVAILABLE = 503

/**
 * Default REST error renderer.
 *
 * Produces responses with standard JSON error envelope and appropriate HTTP status
 * derived from the error's [ErrorCategory].
 */
@JsExportCompat
class DefaultRestErrorRenderer : HttpErrorRenderer {
    private val json =
        Json {
            encodeDefaults = false
            ignoreUnknownKeys = true
        }

    override fun render(
        error: IdkErrorType,
        request: GenericHttpRequest?,
    ): GenericHttpResponse {
        val status = categoryToHttpStatus(error.category)
        val details =
            error.meta
                .filterValues { it != null }
                .mapValues { it.value.toString() }
                .ifEmpty { emptyMap() }
        val body =
            RestErrorBody(
                error =
                    RestErrorDetail(
                        code = error.code,
                        message = error.message.defaultMessage,
                        details = details,
                    ),
            )
        return GenericHttpResponse(
            statusCode = status,
            headers = mapOf("Content-Type" to "application/json"),
            body = json.encodeToString(body),
        )
    }
}
