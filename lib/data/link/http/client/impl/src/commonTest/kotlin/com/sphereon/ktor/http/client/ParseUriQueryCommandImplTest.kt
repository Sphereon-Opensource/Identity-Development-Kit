/*
 * © 2025 Sphereon International B.V.
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

package com.sphereon.ktor.http.client

import com.sphereon.core.api.Ok
import com.sphereon.core.api.session.asCoreApiServiceComponent
import com.sphereon.core.defaults.app.staticMinimalTestAppComponent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Comprehensive tests for ParseUriQueryCommandImpl with proper 3-level DI setup.
 *
 * Uses Application → User → Session component hierarchy.
 */
class ParseUriQueryCommandImplTest {

    // 3-level DI hierarchy: App → User → Session
    private val app = staticMinimalTestAppComponent(this, "test-app", "test", "1.0.0")
    private val user = app.userContextManager.getAnonymous()
    private val session = user.sessionContextManager.getAnonymous()

    private suspend fun parse(uri: String): ParsedUri {
        val sessionContext = session.sessionContext
        val execution = session.asCoreApiServiceComponent().serviceExecution
        val command = ParseUriQueryCommandImpl(execution)
        val result = command.execute(uri)
        assertTrue(result is Ok, "Expected Ok result but got $result")
        return result.value
    }

    @Test
    fun testBasicUriWithScheme() = runTest {
        val result = parse("openid4vp://?client_id=foo&state=bar")

        assertEquals("openid4vp://?client_id=foo&state=bar", result.uri)
        assertEquals("openid4vp", result.scheme)
        assertNull(result.host)
        assertEquals("//", result.path)
        assertEquals("foo", result.queryParameters["client_id"])
        assertEquals("bar", result.queryParameters["state"])
    }

    @Test
    fun testHttpsUriWithHostAndPath() = runTest {
        val result = parse("https://example.com/auth?client_id=test&redirect_uri=https://callback")

        assertEquals("https://example.com/auth?client_id=test&redirect_uri=https://callback", result.uri)
        assertEquals("https", result.scheme)
        assertEquals("example.com", result.host)
        assertEquals("/auth", result.path)
        assertEquals("test", result.queryParameters["client_id"])
        assertEquals("https://callback", result.queryParameters["redirect_uri"])
    }

    @Test
    fun testMultipleValuesForSameKey() = runTest {
        val result = parse("openid4vp://?scope=openid&scope=profile&scope=email")

        assertEquals(1, result.queryParameters.names().size)
        val scopes = result.queryParameters.getAll("scope")
        assertNotNull(scopes)
        assertEquals(3, scopes.size)
        assertEquals(listOf("openid", "profile", "email"), scopes)
        assertEquals("openid", result.queryParameters["scope"]) // First value
    }

    @Test
    fun testEmptyValues() = runTest {
        val result = parse("openid4vp://?foo=&bar=baz")

        assertEquals("", result.queryParameters["foo"])
        assertEquals("baz", result.queryParameters["bar"])
    }

    @Test
    fun testUrlDecoding() = runTest {
        val result = parse("openid4vp://?name=John%20Doe&message=Hello%2C%20World%21")

        assertEquals("John Doe", result.queryParameters["name"])
        assertEquals("Hello, World!", result.queryParameters["message"])
    }

    @Test
    fun testSpecialCharacters() = runTest {
        val result = parse("openid4vp://?param=%3D%26%3F%23")

        // Should decode to =&?#
        assertEquals("=&?#", result.queryParameters["param"])
    }

    @Test
    fun testPlusAsSpace() = runTest {
        val result = parse("openid4vp://?message=Hello+World")

        // Ktor's parseQueryString treats + as space
        assertEquals("Hello World", result.queryParameters["message"])
    }

    @Test
    fun testNoQueryString() = runTest {
        val result = parse("openid4vp://")

        assertEquals("openid4vp://", result.uri)
        assertEquals("openid4vp", result.scheme)
        assertEquals("", result.path)
        assertTrue(result.queryParameters.isEmpty())
    }

    @Test
    fun testNoScheme() = runTest {
        val result = parse("/auth?client_id=test")

        assertNull(result.scheme)
        assertNull(result.host)
        assertEquals("/auth", result.path)
        assertEquals("test", result.queryParameters["client_id"])
    }

    @Test
    fun testQueryOnly() = runTest {
        val result = parse("?client_id=foo&state=bar")

        assertNull(result.scheme)
        assertNull(result.host)
        assertEquals("", result.path)
        assertEquals("foo", result.queryParameters["client_id"])
        assertEquals("bar", result.queryParameters["state"])
    }

    @Test
    fun testComplexRealWorldExample() = runTest {
        val uri = "openid4vp://?client_id=https://verifier.example.com" +
                "&response_type=vp_token" +
                "&state=af0ifjsldkj" +
                "&nonce=n-0S6_WzA2Mj" +
                "&response_uri=https://verifier.example.com/callback" +
                "&client_id_scheme=redirect_uri"

        val result = parse(uri)

        assertEquals("openid4vp", result.scheme)
        assertEquals("https://verifier.example.com", result.queryParameters["client_id"])
        assertEquals("vp_token", result.queryParameters["response_type"])
        assertEquals("af0ifjsldkj", result.queryParameters["state"])
        assertEquals("n-0S6_WzA2Mj", result.queryParameters["nonce"])
        assertEquals("https://verifier.example.com/callback", result.queryParameters["response_uri"])
        assertEquals("redirect_uri", result.queryParameters["client_id_scheme"])
    }

    @Test
    fun testUtf8Characters() = runTest {
        val result = parse("openid4vp://?name=%E3%81%82%E3%81%84%E3%81%86") // Japanese "あいう"

        assertEquals("あいう", result.queryParameters["name"])
    }

    @Test
    fun testEmptyKeyWithValue() = runTest {
        val result = parse("openid4vp://?=value&key=test")

        // Ktor's parseQueryString doesn't preserve empty keys (returns null)
        // This is acceptable behavior as empty keys are non-standard
        assertNull(result.queryParameters[""])
        assertEquals("test", result.queryParameters["key"])
    }

    // ============================================
    // OpenID4VP 1.0 Spec Examples - All 3 Modes
    // ============================================

    @Test
    fun testOid4vpMode1_DirectInlineParameters() = runTest {
        // OID4VP Mode 1: Direct request parameters (same-device flow)
        // All parameters passed directly in URI query string
        val uri = "openid4vp://?response_type=vp_token" +
                "&client_id=https%3A%2F%2Fclient.example.org%2Fcb" +
                "&redirect_uri=https%3A%2F%2Fclient.example.org%2Fcb" +
                "&presentation_definition=%7B%22id%22%3A%22example%22%7D" +
                "&nonce=n-0S6_WzA2Mj" +
                "&state=af0ifjsldkj"

        val result = parse(uri)

        assertEquals("openid4vp", result.scheme)
        assertEquals("//", result.path)
        assertNull(result.host)

        // Verify all OID4VP parameters are correctly extracted
        assertEquals("vp_token", result.queryParameters["response_type"])
        assertEquals("https://client.example.org/cb", result.queryParameters["client_id"])
        assertEquals("https://client.example.org/cb", result.queryParameters["redirect_uri"])
        assertEquals("{\"id\":\"example\"}", result.queryParameters["presentation_definition"])
        assertEquals("n-0S6_WzA2Mj", result.queryParameters["nonce"])
        assertEquals("af0ifjsldkj", result.queryParameters["state"])
    }

    @Test
    fun testOid4vpMode2_RequestByReference() = runTest {
        // OID4VP Mode 2: Request by reference (cross-device flow)
        // Request object is fetched from request_uri
        val uri = "openid4vp://?client_id=https%3A%2F%2Fclient.example.org%2Fcb" +
                "&request_uri=https%3A%2F%2Fverifier.example.com%2Frequest%2F12345"

        val result = parse(uri)

        assertEquals("openid4vp", result.scheme)
        assertEquals("//", result.path)
        assertNull(result.host)

        // Verify request_uri is correctly extracted
        assertEquals("https://client.example.org/cb", result.queryParameters["client_id"])
        assertEquals("https://verifier.example.com/request/12345", result.queryParameters["request_uri"])
    }

    @Test
    fun testOid4vpMode3_RequestObject() = runTest {
        // OID4VP Mode 3: Request object (signed JWT)
        // Request parameters are in a signed JWT passed via request parameter
        val mockJwt = "eyJhbGciOiJSUzI1NiIsImtpZCI6ImsyYmRjIn0.eyJpc3MiOiJodHRwczovL2NsaWVudC5leGFtcGxlLm9yZy9jYiIsImF1ZCI6Imh0dHBzOi8vc2VydmVyLmV4YW1wbGUuY29tIn0.signature"

        val uri = "openid4vp://?client_id=https%3A%2F%2Fclient.example.org%2Fcb" +
                "&request=$mockJwt"

        val result = parse(uri)

        assertEquals("openid4vp", result.scheme)
        assertEquals("//", result.path)
        assertNull(result.host)

        // Verify JWT request object is correctly extracted
        assertEquals("https://client.example.org/cb", result.queryParameters["client_id"])
        assertEquals(mockJwt, result.queryParameters["request"])

        // Verify JWT structure is preserved (contains dots)
        assertTrue(result.queryParameters["request"]!!.contains("."))
    }

    @Test
    fun testOid4vpWithDcqlQuery() = runTest {
        // OID4VP with DCQL query (replaces presentation_definition in OID4VP 1.0 Final)
        val dcqlQuery = "%7B%22credentials%22%3A%5B%7B%22id%22%3A%22pid%22%2C%22format%22%3A%22dc%2Bsd-jwt%22%7D%5D%7D"

        val uri = "openid4vp://?response_type=vp_token" +
                "&client_id=https%3A%2F%2Fverifier.example.com" +
                "&redirect_uri=https%3A%2F%2Fverifier.example.com%2Fcallback" +
                "&dcql_query=$dcqlQuery" +
                "&nonce=abc123" +
                "&state=xyz789"

        val result = parse(uri)

        assertEquals("openid4vp", result.scheme)

        // Verify DCQL query is correctly extracted and URL-decoded
        val decodedDcql = result.queryParameters["dcql_query"]
        assertNotNull(decodedDcql)
        assertEquals("{\"credentials\":[{\"id\":\"pid\",\"format\":\"dc+sd-jwt\"}]}", decodedDcql)
    }

    @Test
    fun testOid4vpWithResponseMode() = runTest {
        // OID4VP with response_mode=direct_post
        val uri = "openid4vp://?response_type=vp_token" +
                "&client_id=https%3A%2F%2Fverifier.example.com" +
                "&response_uri=https%3A%2F%2Fverifier.example.com%2Fresponse" +
                "&response_mode=direct_post" +
                "&nonce=n-0S6_WzA2Mj" +
                "&state=af0ifjsldkj"

        val result = parse(uri)

        assertEquals("vp_token", result.queryParameters["response_type"])
        assertEquals("https://verifier.example.com", result.queryParameters["client_id"])
        assertEquals("https://verifier.example.com/response", result.queryParameters["response_uri"])
        assertEquals("direct_post", result.queryParameters["response_mode"])
        assertEquals("n-0S6_WzA2Mj", result.queryParameters["nonce"])
        assertEquals("af0ifjsldkj", result.queryParameters["state"])
    }

    @Test
    fun testOid4vpWithClientIdScheme() = runTest {
        // OID4VP with client_id_scheme parameter
        val uri = "openid4vp://?response_type=vp_token" +
                "&client_id=did%3Aexample%3A123456789abcdefghi" +
                "&client_id_scheme=did" +
                "&redirect_uri=https%3A%2F%2Fclient.example.org%2Fcb" +
                "&nonce=n-0S6_WzA2Mj"

        val result = parse(uri)

        assertEquals("did:example:123456789abcdefghi", result.queryParameters["client_id"])
        assertEquals("did", result.queryParameters["client_id_scheme"])
        assertEquals("https://client.example.org/cb", result.queryParameters["redirect_uri"])
    }

    @Test
    fun testOid4vpComplexRealWorld() = runTest {
        // Real-world example with multiple OID4VP parameters
        val uri = "openid4vp://?response_type=vp_token" +
                "&client_id=https%3A%2F%2Fverifier.example.com" +
                "&client_id_scheme=redirect_uri" +
                "&redirect_uri=https%3A%2F%2Fverifier.example.com%2Fcallback" +
                "&response_mode=direct_post" +
                "&response_uri=https%3A%2F%2Fverifier.example.com%2Fdirect_post" +
                "&nonce=1234567890" +
                "&state=abcdefghij" +
                "&request_uri=https%3A%2F%2Fverifier.example.com%2Frequest%2Fdef456"

        val result = parse(uri)

        // Verify all parameters extracted correctly
        assertEquals("vp_token", result.queryParameters["response_type"])
        assertEquals("https://verifier.example.com", result.queryParameters["client_id"])
        assertEquals("redirect_uri", result.queryParameters["client_id_scheme"])
        assertEquals("https://verifier.example.com/callback", result.queryParameters["redirect_uri"])
        assertEquals("direct_post", result.queryParameters["response_mode"])
        assertEquals("https://verifier.example.com/direct_post", result.queryParameters["response_uri"])
        assertEquals("1234567890", result.queryParameters["nonce"])
        assertEquals("abcdefghij", result.queryParameters["state"])
        assertEquals("https://verifier.example.com/request/def456", result.queryParameters["request_uri"])
    }

    @Test
    fun testHostnameExtraction() = runTest {
        val result = parse("https://api.example.com:8443/v1/auth?key=value")

        assertEquals("https", result.scheme)
        assertEquals("api.example.com:8443", result.host)
        assertEquals("/v1/auth", result.path)
        assertEquals("value", result.queryParameters["key"])
    }

    @Test
    fun testParametersApiUsage() = runTest {
        val result = parse("openid4vp://?foo=bar&baz=qux")

        // Test Ktor Parameters API
        assertTrue(result.queryParameters.contains("foo"))
        assertTrue(result.queryParameters.contains("baz"))
        assertNotNull(result.queryParameters.names())
        assertEquals(2, result.queryParameters.names().size)
    }
}
