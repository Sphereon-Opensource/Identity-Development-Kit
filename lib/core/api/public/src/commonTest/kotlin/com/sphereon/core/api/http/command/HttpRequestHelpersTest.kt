package com.sphereon.core.api.http.command

import com.sphereon.core.api.error.ErrorCategory
import com.sphereon.core.api.http.GenericHttpRequest
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HttpRequestHelpersTest {
    private val json = Json { ignoreUnknownKeys = true }

    private fun makeRequest(
        path: String = "/test",
        method: String = "GET",
        headers: Map<String, String> = emptyMap(),
        pathParams: Map<String, String> = emptyMap(),
        queryParams: Map<String, String?> = emptyMap(),
        body: String? = null,
    ) = GenericHttpRequest(
        path = path,
        method = method,
        headers = headers,
        pathParameters = pathParams,
        queryParameters = queryParams,
        bodySupplier = { body },
    )

    @Test
    fun requirePathParamReturnsValue() {
        val req = makeRequest(pathParams = mapOf("id" to "123"))
        val result = req.requirePathParam("id")
        assertTrue(result.isOk)
        assertEquals("123", result.value)
    }

    @Test
    fun requirePathParamReturnsErrWhenMissing() {
        val req = makeRequest()
        val result = req.requirePathParam("id")
        assertTrue(result.isErr)
        assertEquals(ErrorCategory.VALIDATION, result.error.category)
    }

    @Test
    fun requireQueryParamReturnsValue() {
        val req = makeRequest(queryParams = mapOf("page" to "2"))
        val result = req.requireQueryParam("page")
        assertTrue(result.isOk)
        assertEquals("2", result.value)
    }

    @Test
    fun requireQueryParamReturnsErrWhenMissing() {
        val req = makeRequest()
        val result = req.requireQueryParam("page")
        assertTrue(result.isErr)
        assertEquals(ErrorCategory.VALIDATION, result.error.category)
    }

    @Test
    fun requireHeaderReturnsValue() {
        val req = makeRequest(headers = mapOf("Authorization" to "Bearer token"))
        val result = req.requireHeader("Authorization")
        assertTrue(result.isOk)
        assertEquals("Bearer token", result.value)
    }

    @Test
    fun requireHeaderReturnsErrWhenMissing() {
        val req = makeRequest()
        val result = req.requireHeader("Authorization")
        assertTrue(result.isErr)
    }

    @Test
    fun requireTenantIdReturnsErrWhenMissing() {
        val req = makeRequest()
        val result = req.requireTenantId()
        assertTrue(result.isErr)
        assertEquals(ErrorCategory.UNAUTHORIZED, result.error.category)
    }

    @Test
    fun requireBodyReturnsBody() {
        val req = makeRequest(body = """{"name": "test"}""")
        val result = req.requireBody()
        assertTrue(result.isOk)
        assertEquals("""{"name": "test"}""", result.value)
    }

    @Test
    fun requireBodyReturnsErrWhenEmpty() {
        val req = makeRequest()
        val result = req.requireBody()
        assertTrue(result.isErr)
    }

    @Serializable
    data class TestPayload(
        val name: String,
        val age: Int,
    )

    @Test
    fun requireJsonBodyDecodesSuccessfully() {
        val req = makeRequest(body = """{"name": "Alice", "age": 30}""")
        val result = req.requireJsonBody<TestPayload>(json)
        assertTrue(result.isOk)
        assertEquals("Alice", result.value.name)
        assertEquals(30, result.value.age)
    }

    @Test
    fun requireJsonBodyReturnsErrOnInvalidJson() {
        val req = makeRequest(body = "not json")
        val result = req.requireJsonBody<TestPayload>(json)
        assertTrue(result.isErr)
        assertEquals(ErrorCategory.VALIDATION, result.error.category)
    }

    @Test
    fun requireJsonBodyReturnsErrOnMissingBody() {
        val req = makeRequest()
        val result = req.requireJsonBody<TestPayload>(json)
        assertTrue(result.isErr)
    }
}
