package com.sphereon.core.api.http.error

import com.sphereon.core.api.error.ErrorCategory
import com.sphereon.core.api.error.IdkError
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class HttpErrorRendererTest {
    private val renderer = DefaultRestErrorRenderer()
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun categoryToHttpStatusMapsCorrectly() {
        assertEquals(400, categoryToHttpStatus(ErrorCategory.VALIDATION))
        assertEquals(401, categoryToHttpStatus(ErrorCategory.UNAUTHORIZED))
        assertEquals(403, categoryToHttpStatus(ErrorCategory.FORBIDDEN))
        assertEquals(404, categoryToHttpStatus(ErrorCategory.NOT_FOUND))
        assertEquals(409, categoryToHttpStatus(ErrorCategory.CONFLICT))
        assertEquals(412, categoryToHttpStatus(ErrorCategory.PRECONDITION_FAILED))
        assertEquals(429, categoryToHttpStatus(ErrorCategory.RATE_LIMITED))
        assertEquals(503, categoryToHttpStatus(ErrorCategory.UNAVAILABLE))
        assertEquals(500, categoryToHttpStatus(ErrorCategory.INTERNAL))
        assertEquals(501, categoryToHttpStatus(ErrorCategory.PROTOCOL))
    }

    @Test
    fun renderNotFoundError() {
        val error = IdkError.NOT_FOUND_ERROR(message = "User not found: 123")
        val response = renderer.render(error)
        assertEquals(404, response.statusCode)
        assertEquals("application/json", response.headers["Content-Type"])
        assertNotNull(response.body)
        val body = json.decodeFromString<RestErrorBody>(response.body!!)
        assertEquals("NOT_FOUND_ERROR", body.error.code)
        assertEquals("User not found: 123", body.error.message)
    }

    @Test
    fun renderValidationError() {
        val error = IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Invalid input")
        val response = renderer.render(error)
        assertEquals(400, response.statusCode)
        val body = json.decodeFromString<RestErrorBody>(response.body!!)
        assertEquals("ILLEGAL_ARGUMENT_ERROR", body.error.code)
    }

    @Test
    fun renderUnauthorizedError() {
        val error = IdkError.UNAUTHORIZED_ERROR(message = "Token expired")
        val response = renderer.render(error)
        assertEquals(401, response.statusCode)
        val body = json.decodeFromString<RestErrorBody>(response.body!!)
        assertEquals("UNAUTHORIZED", body.error.code)
    }

    @Test
    fun renderForbiddenError() {
        val error = IdkError.FORBIDDEN_ERROR(message = "Insufficient permissions")
        val response = renderer.render(error)
        assertEquals(403, response.statusCode)
    }

    @Test
    fun renderConflictError() {
        val error = IdkError.ALREADY_EXISTS_ERROR(resource = "Theme")
        val response = renderer.render(error)
        assertEquals(409, response.statusCode)
        val body = json.decodeFromString<RestErrorBody>(response.body!!)
        assertEquals("ALREADY_EXISTS_ERROR", body.error.code)
    }

    @Test
    fun renderErrorWithMeta() {
        val error =
            IdkError(
                code = "CUSTOM_ERROR",
                message = IdkError.Message(i18nKey = "test", defaultMessage = "Something went wrong"),
                category = ErrorCategory.INTERNAL,
                meta = mapOf("requestId" to "abc-123", "detail" to "extra info"),
            )
        val response = renderer.render(error)
        assertEquals(500, response.statusCode)
        val body = json.decodeFromString<RestErrorBody>(response.body!!)
        assertEquals("abc-123", body.error.details["requestId"])
        assertEquals("extra info", body.error.details["detail"])
    }

    @Test
    fun renderUnavailableError() {
        val error = IdkError.COMMAND_DISABLED_ERROR(commandId = "kms.keys.generate")
        val response = renderer.render(error)
        assertEquals(503, response.statusCode)
    }

    @Test
    fun renderServiceUnavailableRetryAfterHeader() {
        val error =
            IdkError.SERVICE_UNAVAILABLE_ERROR(
                message = "Service is temporarily overloaded; slow down and retry later",
                retryAfter = 3.seconds,
            )
        val response = renderer.render(error)
        assertEquals(503, response.statusCode)
        assertEquals("3", response.headers["Retry-After"])
        val body = json.decodeFromString<RestErrorBody>(response.body!!)
        assertEquals("SERVICE_UNAVAILABLE", body.error.code)
        assertEquals("3", body.error.details["retry_after"])
    }

    @Test
    fun allFactoryMethodsHaveCorrectCategory() {
        assertEquals(ErrorCategory.INTERNAL, IdkError.UNKNOWN_ERROR().category)
        assertEquals(ErrorCategory.VALIDATION, IdkError.ILLEGAL_ARGUMENT_ERROR().category)
        assertEquals(ErrorCategory.NOT_FOUND, IdkError.NOT_FOUND_ERROR().category)
        assertEquals(ErrorCategory.UNAVAILABLE, IdkError.COMMAND_DISABLED_ERROR(commandId = "test").category)
        assertEquals(ErrorCategory.FORBIDDEN, IdkError.COMMAND_NOT_AUTHORIZED_ERROR(commandId = "test", reason = "denied").category)
        assertEquals(ErrorCategory.INTERNAL, IdkError.ALL_HANDLERS_FAILED_ERROR(errors = emptyList()).category)
        assertEquals(ErrorCategory.UNAUTHORIZED, IdkError.UNAUTHORIZED_ERROR().category)
        assertEquals(ErrorCategory.FORBIDDEN, IdkError.FORBIDDEN_ERROR().category)
        assertEquals(ErrorCategory.CONFLICT, IdkError.ALREADY_EXISTS_ERROR().category)
        assertEquals(ErrorCategory.CONFLICT, IdkError.INVALID_STATE().category)
    }
}
