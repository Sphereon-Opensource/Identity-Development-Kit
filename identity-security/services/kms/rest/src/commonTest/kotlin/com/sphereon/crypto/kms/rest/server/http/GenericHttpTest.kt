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

package com.sphereon.crypto.kms.rest.server.http

import com.sphereon.core.api.http.GenericHttpRequest
import com.sphereon.core.api.http.GenericHttpResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GenericHttpTest {
    @Test
    fun `GenericHttpRequest matches method case-insensitively`() {
        val request =
            GenericHttpRequest(
                method = "GET",
                path = "/keys",
            )

        assertTrue(request.matches("GET", "/keys"))
        assertTrue(request.matches("get", "/keys"))
        assertTrue(request.matches("Get", "/keys"))
    }

    @Test
    fun `GenericHttpRequest matches exact path`() {
        val request =
            GenericHttpRequest(
                method = "GET",
                path = "/keys",
            )

        assertTrue(request.matches("GET", "/keys"))
        assertFalse(request.matches("GET", "/providers"))
        assertFalse(request.matches("GET", "/keys/abc"))
    }

    @Test
    fun `GenericHttpRequest matches path with single parameter`() {
        val request =
            GenericHttpRequest(
                method = "GET",
                path = "/keys/key123",
            )

        assertTrue(request.matches("GET", "/keys/{id}"))
        assertTrue(request.matches("GET", "/keys/{aliasOrKid}"))
        assertFalse(request.matches("GET", "/keys"))
        assertFalse(request.matches("GET", "/keys/key123/extra"))
    }

    @Test
    fun `GenericHttpRequest matches path with multiple parameters`() {
        val request =
            GenericHttpRequest(
                method = "GET",
                path = "/providers/p1/keys/k1",
            )

        assertTrue(request.matches("GET", "/providers/{providerId}/keys/{keyId}"))
        assertFalse(request.matches("GET", "/providers/{providerId}"))
        assertFalse(request.matches("GET", "/providers/{providerId}/keys"))
    }

    @Test
    fun `GenericHttpRequest extracts single path parameter`() {
        val request =
            GenericHttpRequest(
                method = "GET",
                path = "/keys/key123",
            )

        val withParams = request.withExtractedParams("/keys/{aliasOrKid}")

        assertEquals("key123", withParams.pathParams["aliasOrKid"])
        assertEquals(1, withParams.pathParams.size)
    }

    @Test
    fun `GenericHttpRequest extracts multiple path parameters`() {
        val request =
            GenericHttpRequest(
                method = "GET",
                path = "/providers/provider1/keys/key123",
            )

        val withParams = request.withExtractedParams("/providers/{providerId}/keys/{keyId}")

        assertEquals("provider1", withParams.pathParams["providerId"])
        assertEquals("key123", withParams.pathParams["keyId"])
        assertEquals(2, withParams.pathParams.size)
    }

    @Test
    fun `GenericHttpRequest preserves existing path parameters when extracting`() {
        val request =
            GenericHttpRequest(
                method = "GET",
                path = "/keys/key123",
                pathParameters = mapOf("existing" to "value"),
            )

        val withParams = request.withExtractedParams("/keys/{aliasOrKid}")

        assertEquals("value", withParams.pathParams["existing"])
        assertEquals("key123", withParams.pathParams["aliasOrKid"])
        assertEquals(2, withParams.pathParams.size)
    }

    @Test
    fun `GenericHttpRequest handles trailing slashes in path`() {
        val request1 = GenericHttpRequest(method = "GET", path = "/keys/")
        val request2 = GenericHttpRequest(method = "GET", path = "/keys")

        // Both should match /keys pattern (trailing slash filtered out)
        assertTrue(request1.matches("GET", "/keys"))
        assertTrue(request2.matches("GET", "/keys"))
    }

    @Test
    fun `GenericHttpRequest provides convenience accessors`() {
        val request =
            GenericHttpRequest(
                method = "GET",
                path = "/keys",
                pathParameters = mapOf("id" to "123"),
                queryParameters = mapOf("filter" to "active"),
            )

        assertEquals(request.pathParameters, request.pathParams)
        assertEquals(request.queryParameters, request.queryParams)
    }

    /*@Test
    fun `matchesPathPattern handles complex patterns`() {
        assertTrue(matchesPathPattern("/keys", "/keys"))
        assertTrue(matchesPathPattern("/keys/123", "/keys/{id}"))
        assertTrue(matchesPathPattern("/a/b/c/d", "/a/{x}/c/{y}"))

        assertFalse(matchesPathPattern("/keys", "/providers"))
        assertFalse(matchesPathPattern("/keys/123/extra", "/keys/{id}"))
        assertFalse(matchesPathPattern("/keys", "/keys/{id}"))
    }

    @Test
    fun `extractPathParams returns empty map for non-matching paths`() {
        val params = extractPathParams("/keys", "/providers/{id}")
        assertTrue(params.isEmpty())
    }

    @Test
    fun `extractPathParams handles mixed static and dynamic segments`() {
        val params = extractPathParams(
            "/api/v1/providers/p1/keys/k1/metadata",
            "/api/v1/providers/{providerId}/keys/{keyId}/metadata"
        )

        assertEquals("p1", params["providerId"])
        assertEquals("k1", params["keyId"])
        assertEquals(2, params.size)
    }
*/
    @Test
    fun `GenericHttpResponse can be created with all fields`() {
        val response =
            GenericHttpResponse(
                statusCode = 200,
                headers = mapOf("Content-Type" to "application/json"),
                body = """{"status": "ok"}""",
            )

        assertEquals(200, response.statusCode)
        assertEquals("application/json", response.headers["Content-Type"])
        assertEquals("""{"status": "ok"}""", response.body)
    }

    @Test
    fun `GenericHttpResponse can be created with minimal fields`() {
        val response = GenericHttpResponse(statusCode = 204)

        assertEquals(204, response.statusCode)
        assertTrue(response.headers.isEmpty())
        assertEquals(null, response.body)
    }
}
