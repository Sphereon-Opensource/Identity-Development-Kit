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

package com.sphereon.core.api.http

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LazyMapTest {
    @Test
    fun sizeReturnsCorrectValue() {
        val map = LazyMap { mapOf("a" to 1, "b" to 2) }
        assertEquals(2, map.size)
    }

    @Test
    fun isEmptyReturnsTrueForEmptyMap() {
        val map = LazyMap<String, Int> { emptyMap() }
        assertTrue(map.isEmpty())
    }

    @Test
    fun isEmptyReturnsFalseForNonEmptyMap() {
        val map = LazyMap { mapOf("a" to 1) }
        assertFalse(map.isEmpty())
    }

    @Test
    fun containsKeyReturnsTrueForExistingKey() {
        val map = LazyMap { mapOf("key" to "value") }
        assertTrue(map.containsKey("key"))
    }

    @Test
    fun containsKeyReturnsFalseForMissingKey() {
        val map = LazyMap { mapOf("key" to "value") }
        assertFalse(map.containsKey("other"))
    }

    @Test
    fun containsValueReturnsTrueForExistingValue() {
        val map = LazyMap { mapOf("key" to "value") }
        assertTrue(map.containsValue("value"))
    }

    @Test
    fun getReturnsValueForExistingKey() {
        val map = LazyMap { mapOf("key" to "value") }
        assertEquals("value", map["key"])
    }

    @Test
    fun getReturnsNullForMissingKey() {
        val map = LazyMap { mapOf("key" to "value") }
        assertNull(map["other"])
    }

    @Test
    fun keysReturnsAllKeys() {
        val map = LazyMap { mapOf("a" to 1, "b" to 2) }
        assertEquals(setOf("a", "b"), map.keys)
    }

    @Test
    fun valuesReturnsAllValues() {
        val map = LazyMap { mapOf("a" to 1, "b" to 2) }
        assertTrue(map.values.contains(1))
        assertTrue(map.values.contains(2))
    }

    @Test
    fun entriesReturnsAllEntries() {
        val map = LazyMap { mapOf("a" to 1) }
        assertEquals(1, map.entries.size)
        assertEquals("a", map.entries.first().key)
        assertEquals(1, map.entries.first().value)
    }
}

class GenericHttpRequestTest {
    @Test
    fun requestHasMethod() {
        val request = GenericHttpRequest(method = "GET", path = "/test")
        assertEquals("GET", request.method)
    }

    @Test
    fun requestHasPath() {
        val request = GenericHttpRequest(method = "GET", path = "/test/path")
        assertEquals("/test/path", request.path)
    }

    @Test
    fun pathParametersDefaultsToEmpty() {
        val request = GenericHttpRequest(method = "GET", path = "/test")
        assertTrue(request.pathParameters.isEmpty())
    }

    @Test
    fun queryParametersDefaultsToEmpty() {
        val request = GenericHttpRequest(method = "GET", path = "/test")
        assertTrue(request.queryParameters.isEmpty())
    }

    @Test
    fun headersDefaultsToEmpty() {
        val request = GenericHttpRequest(method = "GET", path = "/test")
        assertTrue(request.headers.isEmpty())
    }

    @Test
    fun pathParamsAliasReturnsSameValue() {
        val request =
            GenericHttpRequest(
                method = "GET",
                path = "/test",
                pathParameters = mapOf("id" to "123"),
            )
        assertEquals(request.pathParameters, request.pathParams)
    }

    @Test
    fun queryParamsAliasReturnsSameValue() {
        val request =
            GenericHttpRequest(
                method = "GET",
                path = "/test",
                queryParameters = mapOf("limit" to "10"),
            )
        assertEquals(request.queryParameters, request.queryParams)
    }

    @Test
    fun contentTypeReturnsHeaderValue() {
        val request =
            GenericHttpRequest(
                method = "POST",
                path = "/test",
                headers = mapOf("Content-Type" to "application/json"),
            )
        assertEquals("application/json", request.contentType)
    }

    @Test
    fun contentTypeLowercaseReturnsHeaderValue() {
        val request =
            GenericHttpRequest(
                method = "POST",
                path = "/test",
                headers = mapOf("content-type" to "application/json"),
            )
        assertEquals("application/json", request.contentType)
    }

    @Test
    fun contentTypeReturnsNullWhenMissing() {
        val request = GenericHttpRequest(method = "GET", path = "/test")
        assertNull(request.contentType)
    }

    @Test
    fun acceptParsesMultipleValues() {
        val request =
            GenericHttpRequest(
                method = "GET",
                path = "/test",
                headers = mapOf("Accept" to "application/json, text/plain"),
            )
        assertEquals(listOf("application/json", "text/plain"), request.accept)
    }

    @Test
    fun acceptRemovesCharsetParameter() {
        val request =
            GenericHttpRequest(
                method = "GET",
                path = "/test",
                headers = mapOf("Accept" to "application/json; charset=utf-8"),
            )
        assertEquals(listOf("application/json"), request.accept)
    }

    @Test
    fun acceptReturnsEmptyForMissingHeader() {
        val request = GenericHttpRequest(method = "GET", path = "/test")
        assertTrue(request.accept.isEmpty())
    }

    @Test
    fun matchesReturnsTrueForMatchingMethodAndPath() {
        val request = GenericHttpRequest(method = "GET", path = "/users")
        assertTrue(request.matches("GET", "/users"))
    }

    @Test
    fun matchesIsCaseInsensitiveForMethod() {
        val request = GenericHttpRequest(method = "GET", path = "/users")
        assertTrue(request.matches("get", "/users"))
    }

    @Test
    fun matchesReturnsFalseForDifferentMethod() {
        val request = GenericHttpRequest(method = "GET", path = "/users")
        assertFalse(request.matches("POST", "/users"))
    }

    @Test
    fun matchesReturnsFalseForDifferentPath() {
        val request = GenericHttpRequest(method = "GET", path = "/users")
        assertFalse(request.matches("GET", "/other"))
    }

    @Test
    fun matchesWithPathParameterPattern() {
        val request = GenericHttpRequest(method = "GET", path = "/users/123")
        assertTrue(request.matches("GET", "/users/{id}"))
    }

    @Test
    fun withExtractedParamsExtractsPathParameters() {
        val request = GenericHttpRequest(method = "GET", path = "/users/123")
        val withParams = request.withExtractedParams("/users/{id}")
        assertEquals("123", withParams.pathParams["id"])
    }

    @Test
    fun withExtractedParamsMergesWithExistingParams() {
        val request =
            GenericHttpRequest(
                method = "GET",
                path = "/users/123",
                pathParameters = mapOf("existing" to "value"),
            )
        val withParams = request.withExtractedParams("/users/{id}")
        assertEquals("value", withParams.pathParams["existing"])
        assertEquals("123", withParams.pathParams["id"])
    }

    @Test
    fun copyPreservesBodyContent() {
        val request =
            GenericHttpRequest.withTextBody(
                method = "POST",
                path = "/test",
                body = "test body",
            )
        val copied = request.copy(path = "/new-path")
        assertEquals("test body", copied.body)
    }

    @Test
    fun withTextBodyCreatesRequestWithBody() {
        val request =
            GenericHttpRequest.withTextBody(
                method = "POST",
                path = "/test",
                body = "hello",
            )
        assertEquals("hello", request.body)
    }

    @Test
    fun withBinaryBodyCreatesRequestWithBytes() {
        val bytes = "hello".encodeToByteArray()
        val request =
            GenericHttpRequest.withBinaryBody(
                method = "POST",
                path = "/test",
                body = bytes,
            )
        assertNotNull(request.bodyBytes)
        assertEquals("hello", request.bodyBytes!!.decodeToString())
    }
}

class GenericHttpResponseTest {
    @Test
    fun responseHasStatusCode() {
        val response = GenericHttpResponse(statusCode = 200)
        assertEquals(200, response.statusCode)
    }

    @Test
    fun headersDefaultsToEmpty() {
        val response = GenericHttpResponse(statusCode = 200)
        assertTrue(response.headers.isEmpty())
    }

    @Test
    fun bodyDefaultsToNull() {
        val response = GenericHttpResponse(statusCode = 200)
        assertNull(response.body)
    }

    @Test
    fun bodyContentDefaultsToEmpty() {
        val response = GenericHttpResponse(statusCode = 200)
        assertNull(response.bodyBytes)
    }

    @Test
    fun contentTypeReturnsHeaderValue() {
        val response =
            GenericHttpResponse(
                statusCode = 200,
                headers = mapOf("Content-Type" to "application/json"),
            )
        assertEquals("application/json", response.contentType)
    }

    @Test
    fun contentTypeLowercaseReturnsHeaderValue() {
        val response =
            GenericHttpResponse(
                statusCode = 200,
                headers = mapOf("content-type" to "text/plain"),
            )
        assertEquals("text/plain", response.contentType)
    }

    @Test
    fun withTextBodyCreatesResponseWithBody() {
        val response =
            GenericHttpResponse.withTextBody(
                statusCode = 200,
                body = "success",
            )
        assertEquals("success", response.body)
    }

    @Test
    fun withBinaryBodyCreatesResponseWithBytes() {
        val bytes = "binary".encodeToByteArray()
        val response =
            GenericHttpResponse.withBinaryBody(
                statusCode = 200,
                body = bytes,
            )
        assertNotNull(response.bodyBytes)
        assertEquals("binary", response.bodyBytes!!.decodeToString())
    }
}

class CompiledPathPatternTest {
    @Test
    fun compileReturnsPattern() {
        val pattern = CompiledPathPattern.compile("/users")
        assertEquals("/users", pattern.pattern)
    }

    @Test
    fun matchesExactPath() {
        val pattern = CompiledPathPattern.compile("/users")
        assertTrue(pattern.matches("/users"))
    }

    @Test
    fun matchesReturnsFalseForDifferentPath() {
        val pattern = CompiledPathPattern.compile("/users")
        assertFalse(pattern.matches("/other"))
    }

    @Test
    fun matchesWithParameter() {
        val pattern = CompiledPathPattern.compile("/users/{id}")
        assertTrue(pattern.matches("/users/123"))
    }

    @Test
    fun matchesReturnsFalseForDifferentSegmentCount() {
        val pattern = CompiledPathPattern.compile("/users/{id}")
        assertFalse(pattern.matches("/users/123/details"))
    }

    @Test
    fun extractParamsReturnsParameterValue() {
        val pattern = CompiledPathPattern.compile("/users/{id}")
        val params = pattern.extractParams("/users/123")
        assertEquals("123", params["id"])
    }

    @Test
    fun extractParamsReturnsMultipleParameters() {
        val pattern = CompiledPathPattern.compile("/users/{userId}/posts/{postId}")
        val params = pattern.extractParams("/users/1/posts/2")
        assertEquals("1", params["userId"])
        assertEquals("2", params["postId"])
    }

    @Test
    fun extractParamsReturnsEmptyForMismatchedPath() {
        val pattern = CompiledPathPattern.compile("/users/{id}")
        val params = pattern.extractParams("/users/1/2")
        assertTrue(params.isEmpty())
    }

    @Test
    fun compileCachesPatterns() {
        val pattern1 = CompiledPathPattern.compile("/test")
        val pattern2 = CompiledPathPattern.compile("/test")
        // Same pattern should be cached
        assertEquals(pattern1, pattern2)
    }
}

class PathMatchingFunctionsTest {
    @Test
    fun matchesPathPatternExactMatch() {
        assertTrue(matchesPathPattern("/users", "/users"))
    }

    @Test
    fun matchesPathPatternWithParameter() {
        assertTrue(matchesPathPattern("/users/123", "/users/{id}"))
    }

    @Test
    fun matchesPathPatternReturnsFalseForDifferentPath() {
        assertFalse(matchesPathPattern("/users", "/other"))
    }

    @Test
    fun extractPathParamsReturnsParameters() {
        val params = extractPathParams("/users/abc", "/users/{id}")
        assertEquals("abc", params["id"])
    }

    @Test
    fun extractPathParamsMultipleSegments() {
        val params = extractPathParams("/providers/p1/keys/k1", "/providers/{providerId}/keys/{keyId}")
        assertEquals("p1", params["providerId"])
        assertEquals("k1", params["keyId"])
    }
}
