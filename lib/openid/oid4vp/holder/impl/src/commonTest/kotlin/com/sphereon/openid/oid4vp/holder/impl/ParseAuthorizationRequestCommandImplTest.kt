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

package com.sphereon.openid.oid4vp.holder.impl

import com.sphereon.core.api.Ok
import com.sphereon.core.api.Err
import com.sphereon.ktor.http.client.getOptional
import com.sphereon.ktor.http.client.getOrDefault
import com.sphereon.ktor.http.client.getRequired
import io.ktor.http.*
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for ParseAuthorizationRequestCommandImpl focusing on parameter extraction.
 *
 * These tests verify the Parameters extension functions used by the command implementation.
 * Full integration tests with DI, HTTP mocking, and JWT parsing would require additional setup.
 */
class ParseAuthorizationRequestCommandImplTest {

    @Test
    fun `test parse URI with all required parameters`() = runTest {
        val uri = "openid4vp://?client_id=test-client&redirect_uri=https://example.com/callback&response_type=vp_token&nonce=abc123"

        val params = parseQueryString(uri.substringAfter('?'))

        assertEquals("test-client", params["client_id"])
        assertEquals("https://example.com/callback", params["redirect_uri"])
        assertEquals("vp_token", params["response_type"])
        assertEquals("abc123", params["nonce"])
    }

    @Test
    fun `test Parameters getRequired extension`() = runTest {
        val params = parametersOf(
            "client_id" to listOf("test-client"),
            "redirect_uri" to listOf("https://example.com/callback")
        )

        val result = params.getRequired("client_id")
        assertTrue(result is Ok)
    }

    @Test
    fun `test Parameters getRequired with missing parameter`() = runTest {
        val params = Parameters.Empty

        val result = params.getRequired("client_id")
        assertTrue(result is Err)
    }

    @Test
    fun `test Parameters getOptional with present parameter`() = runTest {
        val params = parametersOf("scope" to listOf("openid"))

        val value = params.getOptional("scope")
        assertEquals("openid", value)
    }

    @Test
    fun `test Parameters getOptional with missing parameter`() = runTest {
        val params = Parameters.Empty

        val value = params.getOptional("scope")
        assertNull(value)
    }

    @Test
    fun `test Parameters getOrDefault with present parameter`() = runTest {
        val params = parametersOf("response_type" to listOf("vp_token"))

        val value = params.getOrDefault("response_type", "code")
        assertEquals("vp_token", value)
    }

    @Test
    fun `test Parameters getOrDefault with missing parameter`() = runTest {
        val params = Parameters.Empty

        val value = params.getOrDefault("response_type", "vp_token")
        assertEquals("vp_token", value)
    }

    @Test
    fun `test parse URI with optional scope parameter`() = runTest {
        val uri = "openid4vp://?client_id=test-client&redirect_uri=https://example.com/callback&response_type=vp_token&nonce=abc&scope=openid"

        val params = parseQueryString(uri.substringAfter('?'))

        assertEquals("openid", params["scope"])
    }

    @Test
    fun `test parse URI with dcql_query in parameters`() = runTest {
        val dcqlQuery = """{"credentials":[{"format":"vc+sd-jwt"}]}"""
        val uri = "openid4vp://?client_id=test-client&redirect_uri=https://example.com/callback&response_type=vp_token&nonce=abc&dcql_query=$dcqlQuery"

        val params = parseQueryString(uri.substringAfter('?'))

        assertNotNull(params["dcql_query"])
        assertTrue(params["dcql_query"]!!.contains("credentials"))
    }

    @Test
    fun `test parse URI with state parameter`() = runTest {
        val uri = "openid4vp://?client_id=test-client&redirect_uri=https://example.com/callback&response_type=vp_token&nonce=abc&state=xyz789"

        val params = parseQueryString(uri.substringAfter('?'))

        assertEquals("xyz789", params["state"])
    }

    @Test
    fun `test parse URI with response_mode parameter`() = runTest {
        val uri = "openid4vp://?client_id=test-client&redirect_uri=https://example.com/callback&response_type=vp_token&nonce=abc&response_mode=direct_post"

        val params = parseQueryString(uri.substringAfter('?'))

        assertEquals("direct_post", params["response_mode"])
    }

    @Test
    fun `test parse URI with URL encoded parameters`() = runTest {
        val uri = "openid4vp://?client_id=test%20client&redirect_uri=https://example.com/callback&response_type=vp_token&nonce=abc"

        val params = parseQueryString(uri.substringAfter('?'))

        // Ktor automatically decodes URL-encoded parameters
        assertEquals("test client", params["client_id"])
    }

    @Test
    fun `test parse URI with multiple custom parameters`() = runTest {
        val uri = "openid4vp://?client_id=test-client&redirect_uri=https://example.com/callback&response_type=vp_token&nonce=abc&custom_param=value1&another_param=value2"

        val params = parseQueryString(uri.substringAfter('?'))

        assertEquals("value1", params["custom_param"])
        assertEquals("value2", params["another_param"])
    }

    @Test
    fun `test parse URI without optional parameters`() = runTest {
        val uri = "openid4vp://?client_id=test-client&redirect_uri=https://example.com/callback&response_type=vp_token&nonce=abc"

        val params = parseQueryString(uri.substringAfter('?'))

        assertNull(params["scope"])
        assertNull(params["state"])
    }

    @Test
    fun `test parse HTTPS URI with path`() = runTest {
        val uri = "https://wallet.example.com/authorize?client_id=test-client&redirect_uri=https://example.com/callback&response_type=vp_token&nonce=abc"

        val params = parseQueryString(uri.substringAfter('?'))

        assertEquals("test-client", params["client_id"])
    }

    @Test
    fun `test parse custom scheme URI`() = runTest {
        val uri = "custom-scheme://?client_id=test-client&redirect_uri=https://example.com/callback&response_type=vp_token&nonce=abc"

        val params = parseQueryString(uri.substringAfter('?'))

        assertEquals("test-client", params["client_id"])
    }
}
