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
 * Extract an optional integer query parameter with inclusive `[min, max]` bounds. Returns
 * [Ok] with `null` when the parameter is absent; returns [Err] with [ErrorCategory.VALIDATION]
 * when the value is non-numeric or out of range.
 */
fun GenericHttpRequest.optionalIntQueryParam(
    name: String,
    min: Int = Int.MIN_VALUE,
    max: Int = Int.MAX_VALUE,
): IdkResult<Int?, IdkError> {
    val raw = optionalQueryParam(name).getOrElse { return Err(it) } ?: return Ok(null)
    val parsed =
        raw.toIntOrNull()
            ?: return Err(
                IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Invalid integer for query parameter '$name': '$raw'."),
            )
    if (parsed < min) {
        return Err(
            IdkError.ILLEGAL_ARGUMENT_ERROR(
                message = "Query parameter '$name' must be >= $min (got $parsed).",
            ),
        )
    }
    if (parsed > max) {
        return Err(
            IdkError.ILLEGAL_ARGUMENT_ERROR(
                message = "Query parameter '$name' must be <= $max (got $parsed).",
            ),
        )
    }
    return Ok(parsed)
}

/**
 * Extract an optional boolean query parameter. Accepts only `"true"` / `"false"`
 * (case-insensitive). Returns [Ok] with `null` when absent; [Err] with
 * [ErrorCategory.VALIDATION] for any other value.
 */
fun GenericHttpRequest.optionalBoolQueryParam(name: String): IdkResult<Boolean?, IdkError> {
    val raw = optionalQueryParam(name).getOrElse { return Err(it) } ?: return Ok(null)
    return when (raw.lowercase()) {
        "true" -> {
            Ok(true)
        }

        "false" -> {
            Ok(false)
        }

        else -> {
            Err(
                IdkError.ILLEGAL_ARGUMENT_ERROR(
                    message = "Invalid boolean for query parameter '$name': '$raw'. Expected 'true' or 'false'.",
                ),
            )
        }
    }
}

/**
 * Extract a required header value case-insensitively per RFC 9110 §5.1. Returns [Err] with
 * [ErrorCategory.VALIDATION] when no header matches [name] under case-insensitive comparison.
 */
fun GenericHttpRequest.requireHeader(name: String): IdkResult<String, IdkError> {
    val value = headerIgnoreCase(name)
    return if (value != null) {
        Ok(value)
    } else {
        Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Missing required header: $name"))
    }
}

/**
 * Look up a header value case-insensitively per RFC 9110 §5.1. Returns `null` when no header
 * with [name] (case-insensitive) is present. Prefer this over `headers[name]` for every read so
 * the lookup is robust to any case-normalisation that may happen in front of the application.
 */
fun GenericHttpRequest.headerIgnoreCase(name: String): String? = headers.headerIgnoreCase(name)

/**
 * All values for a header, case-insensitive, preserving multi-occurrence order. Returns an
 * empty list when the header is absent. Use this when the call site needs to enforce
 * single-occurrence semantics (e.g. RFC 9449 §4.1: a single `DPoP` HTTP header is REQUIRED) —
 * the scalar [headers] map collapses duplicates by joining with `,`, which loses the original
 * count.
 */
fun GenericHttpRequest.headerValuesIgnoreCase(name: String): List<String> =
    multiValueHeaders.entries
        .firstOrNull { it.key.equals(name, ignoreCase = true) }
        ?.value
        ?: headerIgnoreCase(name)?.let { listOf(it) }
        ?: emptyList()

/**
 * Case-insensitive (RFC 9110 §5.1) header lookup on a plain `Map<String, String>`. Use when a
 * caller carries headers as a flat map (not wrapped in [GenericHttpRequest]), e.g. across
 * service-command boundaries where only primitives flow.
 */
fun Map<String, String>.headerIgnoreCase(name: String): String? = entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value

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
