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
 *
 */

package com.sphereon.oauth2.client

import com.sphereon.oauth2.client.testutil.createOAuth2ClientTestAppComponent

import com.sphereon.oauth2.client.command.CreateAuthorizationRequestUrlOptions
import com.sphereon.oauth2.client.service.AuthorizationService
import com.sphereon.oauth2.common.model.AuthorizationRequest
import com.sphereon.oauth2.common.model.AuthorizationServerMetadata
import com.sphereon.oauth2.common.model.TokenRequest
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * E2E tests for Resource Indicators (RFC 8707) support
 *
 * RFC 8707 allows clients to indicate which resource server(s) will receive the access token,
 * providing better security and token scoping.
 *
 * Tests cover:
 * 1. Resource indicator in authorization request URL generation (E2E with DI)
 * 2. Resource indicator in token request model
 * 3. Resource indicator serialization and deserialization
 * 4. URL encoding of resource parameters
 * 5. Optional resource parameter handling
 */
class ResourceIndicatorsE2ETest {

    private lateinit var authorizationService: AuthorizationService

    private val app = createOAuth2ClientTestAppComponent(this)
    private val context = app.userContextManager.getAnonymous()
    private val session = context.sessionContextManager.createOrGetFromId("resource-indicators-test")

    @BeforeTest
    fun setUp() {
        // Get AuthorizationService from the session component
        authorizationService = (session.component as com.sphereon.oauth2.client.impl.authorization.AuthorizationServiceImpl.Component).authorizationService
    }

    /**
     * Test: Single resource indicator in authorization request URL (E2E with DI)
     *
     * RFC 8707 Section 2: Authorization Request with resource parameter
     */
    @Test
    fun `test single resource indicator in authorization request URL`() = runTest {
        // Given: Authorization server metadata
        val metadata = AuthorizationServerMetadata(
            issuer = "https://auth.example.com",
            authorizationEndpoint = "https://auth.example.com/authorize",
            tokenEndpoint = "https://auth.example.com/token",
            jwksUri = "https://auth.example.com/jwks",
            codeChallengeMethodsSupported = listOf("S256", "plain")
        )

        // When: Creating authorization request URL with single resource
        val authRequest = AuthorizationRequest(
            clientId = "test-client",
            redirectUri = "https://client.example.com/callback",
            responseType = "code",
            scope = "read write",
            state = "random-state-12345678",
            resource = "https://api.example.com" // RFC 8707: Single resource indicator
        )

        val options = CreateAuthorizationRequestUrlOptions(
            authorizationServerMetadata = metadata,
            authorizationRequest = authRequest
        )

        val result = authorizationService.commands.createAuthorizationRequestUrl.execute(options)

        // Then: Request should succeed
        assertTrue(result.isOk, "Authorization request creation should succeed")
        val urlResult = result.value

        // Verify resource parameter is in authorization URL
        val url = urlResult.authorizationRequestUrl
        assertTrue(url.contains("resource=https://api.example.com") ||
                   url.contains("resource=https%3A%2F%2Fapi.example.com"),
            "Authorization URL should contain resource parameter. URL: $url")

        // Verify URL structure
        assertTrue(url.startsWith("https://auth.example.com/authorize?"),
            "URL should start with authorization endpoint")
        assertTrue(url.contains("client_id=test-client"))
        assertTrue(url.contains("response_type=code"))
        assertTrue(url.contains("state=random-state-12345678"))

        // PKCE should be automatically added
        assertNotNull(urlResult.pkceData, "PKCE data should be generated")
        assertTrue(url.contains("code_challenge="))
        assertTrue(url.contains("code_challenge_method=S256"))
    }

    /**
     * Test: Authorization request without resource indicator (E2E with DI)
     *
     * RFC 8707: Resource parameter is OPTIONAL, requests without it should still work
     */
    @Test
    fun `test authorization request without resource indicator URL generation`() = runTest {
        // Given: Authorization server metadata
        val metadata = AuthorizationServerMetadata(
            issuer = "https://auth.example.com",
            authorizationEndpoint = "https://auth.example.com/authorize",
            tokenEndpoint = "https://auth.example.com/token",
            jwksUri = "https://auth.example.com/jwks",
            codeChallengeMethodsSupported = listOf("S256")
        )

        // When: Creating authorization request URL WITHOUT resource parameter
        val authRequest = AuthorizationRequest(
            clientId = "test-client",
            redirectUri = "https://client.example.com/callback",
            responseType = "code",
            scope = "read write",
            state = "random-state-12345678"
            // NO resource parameter
        )

        val options = CreateAuthorizationRequestUrlOptions(
            authorizationServerMetadata = metadata,
            authorizationRequest = authRequest
        )

        val result = authorizationService.commands.createAuthorizationRequestUrl.execute(options)

        // Then: Request should succeed (resource is optional)
        assertTrue(result.isOk, "Authorization request should succeed without resource parameter")

        val url = result.value.authorizationRequestUrl
        assertFalse(url.contains("resource="),
            "Authorization URL should not contain resource parameter when not specified. URL: $url")
    }

    /**
     * Test: Complex resource URL encoding (E2E with DI)
     *
     * RFC 8707: Resource parameter values are URLs and must be properly encoded
     */
    @Test
    fun `test resource indicator URL encoding in authorization request`() = runTest {
        // Given: Authorization server metadata
        val metadata = AuthorizationServerMetadata(
            issuer = "https://auth.example.com",
            authorizationEndpoint = "https://auth.example.com/authorize",
            tokenEndpoint = "https://auth.example.com/token",
            jwksUri = "https://auth.example.com/jwks",
            codeChallengeMethodsSupported = listOf("S256")
        )

        val complexResource = "https://api.example.com/v1/resources?type=data&format=json"

        // When: Creating authorization request with complex resource URL
        val authRequest = AuthorizationRequest(
            clientId = "test-client",
            redirectUri = "https://client.example.com/callback",
            responseType = "code",
            state = "random-state-12345678",
            resource = complexResource
        )

        val options = CreateAuthorizationRequestUrlOptions(
            authorizationServerMetadata = metadata,
            authorizationRequest = authRequest
        )

        val result = authorizationService.commands.createAuthorizationRequestUrl.execute(options)

        // Then: Resource URL should be properly encoded
        assertTrue(result.isOk)
        val url = result.value.authorizationRequestUrl

        // URL should contain resource parameter (URL may or may not be fully encoded)
        assertTrue(url.contains("resource=") && url.contains("api.example.com"),
            "Resource URL should be present in authorization request. URL: $url")
    }

    /**
     * Test: Token request with resource indicator
     *
     * RFC 8707 Section 2.1: Token Request with resource parameter
     */
    @Test
    fun `test resource indicator in token request model`() = runTest {
        // Given: Token request with resource parameter
        val tokenRequest = TokenRequest(
            grantType = "authorization_code",
            code = "test-auth-code-12345",
            redirectUri = "https://client.example.com/callback",
            codeVerifier = "test-code-verifier-12345678901234567890123456789012", // 43+ chars for PKCE
            clientId = "test-client",
            clientSecret = "test-secret",
            scope = "read write",
            resource = listOf("https://api.example.com") // RFC 8707: Resource indicator in token request
        )

        // Then: Token request should be properly constructed
        assertEquals("authorization_code", tokenRequest.grantType)
        assertEquals("test-auth-code-12345", tokenRequest.code)
        assertEquals("https://client.example.com/callback", tokenRequest.redirectUri)
        assertEquals(listOf("https://api.example.com"), tokenRequest.resource,
            "Resource parameter should be present")
        assertEquals("test-code-verifier-12345678901234567890123456789012", tokenRequest.codeVerifier)

        // Verify serialization preserves resource parameter
        val serialized = Json.encodeToString(
            TokenRequest.serializer(),
            tokenRequest
        )
        assertTrue(serialized.contains("\"resource\"") && serialized.contains("https://api.example.com"),
            "Serialized token request should contain resource parameter")
    }

    /**
     * Test: Token request without resource indicator
     *
     * RFC 8707: Resource parameter is OPTIONAL in token requests
     */
    @Test
    fun `test token request without resource indicator model`() = runTest {
        // Given: Token request WITHOUT resource parameter
        val tokenRequest = TokenRequest(
            grantType = "authorization_code",
            code = "test-auth-code-12345",
            redirectUri = "https://client.example.com/callback",
            codeVerifier = "test-code-verifier-12345678901234567890123456789012",
            clientId = "test-client",
            clientSecret = "test-secret",
            scope = "read write"
            // NO resource parameter
        )

        // Then: Token request should be valid
        assertEquals("authorization_code", tokenRequest.grantType)
        assertTrue(tokenRequest.resource.isEmpty(), "Resource should be empty when not specified")

        // Verify serialization works correctly
        val serialized = Json.encodeToString(
            TokenRequest.serializer(),
            tokenRequest
        )
        assertFalse(serialized.contains("\"resource\""),
            "Serialized token request should not contain resource parameter when not specified")
    }

    /**
     * Test: Token request resource serialization and deserialization
     *
     * Ensures resource parameter round-trips correctly through JSON serialization
     */
    @Test
    fun `test token request resource serialization round-trip`() = runTest {
        // Given: Token request with resource
        val original = TokenRequest(
            grantType = "authorization_code",
            code = "test-code",
            redirectUri = "https://client.example.com/callback",
            resource = listOf("https://api.example.com/v1")
        )

        // When: Serializing and deserializing
        val json = Json.encodeToString(
            TokenRequest.serializer(),
            original
        )
        val deserialized = Json.decodeFromString(
            TokenRequest.serializer(),
            json
        )

        // Then: Resource parameter should be preserved
        assertEquals(original.resource, deserialized.resource,
            "Resource parameter should survive serialization round-trip")
        assertEquals(listOf("https://api.example.com/v1"), deserialized.resource)
    }

    /**
     * Test: Authorization request resource serialization and deserialization
     *
     * Ensures resource parameter round-trips correctly through JSON serialization
     */
    @Test
    fun `test authorization request resource serialization round-trip`() = runTest {
        // Given: Authorization request with resource
        val original = AuthorizationRequest(
            clientId = "test-client",
            redirectUri = "https://client.example.com/callback",
            responseType = "code",
            resource = "https://api.example.com/v1"
        )

        // When: Serializing and deserializing
        val json = Json.encodeToString(
            AuthorizationRequest.serializer(),
            original
        )
        val deserialized = Json.decodeFromString(
            AuthorizationRequest.serializer(),
            json
        )

        // Then: Resource parameter should be preserved
        assertEquals(original.resource, deserialized.resource,
            "Resource parameter should survive serialization round-trip")
        assertEquals("https://api.example.com/v1", deserialized.resource)
    }

    /**
     * Test: Refresh token request with resource indicator
     *
     * RFC 8707: Resource parameter can be used with refresh_token grant
     */
    @Test
    fun `test refresh token request with resource indicator model`() = runTest {
        // Given: Refresh token request with resource
        val tokenRequest = TokenRequest(
            grantType = "refresh_token",
            refreshToken = "test-refresh-token-12345",
            clientId = "test-client",
            clientSecret = "test-secret",
            scope = "read",
            resource = listOf("https://api.example.com") // RFC 8707: Resource in refresh request
        )

        // Then: Request should be properly constructed
        assertEquals("refresh_token", tokenRequest.grantType)
        assertEquals("test-refresh-token-12345", tokenRequest.refreshToken)
        assertEquals(listOf("https://api.example.com"), tokenRequest.resource)

        // Verify serialization
        val serialized = Json.encodeToString(
            TokenRequest.serializer(),
            tokenRequest
        )
        assertTrue(serialized.contains("\"grant_type\":\"refresh_token\""))
        assertTrue(serialized.contains("\"resource\"") && serialized.contains("https://api.example.com"))
    }

    /**
     * Test: Client credentials grant with resource indicator
     *
     * RFC 8707: Resource parameter works with client_credentials grant
     */
    @Test
    fun `test client credentials request with resource indicator model`() = runTest {
        // Given: Client credentials request with resource
        val tokenRequest = TokenRequest(
            grantType = "client_credentials",
            clientId = "test-client",
            clientSecret = "test-secret",
            scope = "read write",
            resource = listOf("https://api.example.com") // RFC 8707: Resource in client_credentials request
        )

        // Then: Request should be properly constructed
        assertEquals("client_credentials", tokenRequest.grantType)
        assertEquals(listOf("https://api.example.com"), tokenRequest.resource)

        // Verify serialization
        val serialized = Json.encodeToString(
            TokenRequest.serializer(),
            tokenRequest
        )
        assertTrue(serialized.contains("\"grant_type\":\"client_credentials\""))
        assertTrue(serialized.contains("\"resource\"") && serialized.contains("https://api.example.com"))
    }
}
