package com.sphereon.core.api.http.command

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.ErrorCategory
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.http.GenericHttpRequest
import com.sphereon.core.api.http.query.QueryParamUtils
import kotlinx.serialization.json.Json

/*
 * Request extraction helpers that return [IdkResult].
 *
 * ```kotlin
 * val key = req.requirePathParam("key").getOrElse { return Err(it) }
 * val body = req.requireJsonBody<MyInput>(json).getOrElse { return Err(it) }
 * ```
 *
 * All helpers return [ErrorCategory.VALIDATION] errors except [requireTenantId]
 * which returns [ErrorCategory.UNAUTHORIZED].
 */

/**
 * Extract a required path parameter.
 * Returns [Err] with [ErrorCategory.VALIDATION] if the parameter is missing.
 */
fun GenericHttpRequest.requirePathParam(name: String): IdkResult<String, IdkError> {
    val value = pathParams[name]
    return if (value != null) {
        Ok(value)
    } else {
        Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Missing required path parameter: $name"))
    }
}

/**
 * Extract a required query parameter.
 * Returns [Err] with [ErrorCategory.VALIDATION] if the parameter is missing or null.
 */
fun GenericHttpRequest.requireQueryParam(name: String): IdkResult<String, IdkError> {
    val value = queryParams[name]
    return if (value != null) {
        Ok(value)
    } else {
        Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Missing required query parameter: $name"))
    }
}

/**
 * Extract an optional query parameter.
 * Returns [Ok] with null if the parameter is missing — never returns [Err].
 */
fun GenericHttpRequest.optionalQueryParam(name: String): IdkResult<String?, IdkError> = Ok(queryParams[name])

/**
 * Extract a required header value.
 * Returns [Err] with [ErrorCategory.VALIDATION] if the header is missing.
 */
fun GenericHttpRequest.requireHeader(name: String): IdkResult<String, IdkError> {
    val value = headers[name] ?: headers[name.lowercase()]
    return if (value != null) {
        Ok(value)
    } else {
        Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Missing required header: $name"))
    }
}

/**
 * Extract the tenant ID from request headers.
 * Returns [Err] with [ErrorCategory.UNAUTHORIZED] if tenant ID is missing.
 */
fun GenericHttpRequest.requireTenantId(): IdkResult<String, IdkError> {
    val tenantId = QueryParamUtils.extractTenantId(headers)
    return if (tenantId != null) {
        Ok(tenantId)
    } else {
        Err(IdkError.UNAUTHORIZED_ERROR(message = "Missing tenant identification"))
    }
}

/**
 * Extract the required text body.
 * Returns [Err] with [ErrorCategory.VALIDATION] if the body is missing or empty.
 */
fun GenericHttpRequest.requireBody(): IdkResult<String, IdkError> {
    val text = body
    return if (!text.isNullOrBlank()) {
        Ok(text)
    } else {
        Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Missing request body"))
    }
}

/**
 * Decode the request body as JSON.
 * Returns [Err] with [ErrorCategory.VALIDATION] if the body is missing or invalid JSON.
 */
inline fun <reified T> GenericHttpRequest.requireJsonBody(json: Json): IdkResult<T, IdkError> {
    val text =
        body
            ?: return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Missing request body"))
    return try {
        Ok(json.decodeFromString<T>(text))
    } catch (expected: Exception) {
        Err(
            IdkError.ILLEGAL_ARGUMENT_ERROR(
                message = "Invalid request body: ${expected.message}",
                throwable = expected,
            ),
        )
    }
}
