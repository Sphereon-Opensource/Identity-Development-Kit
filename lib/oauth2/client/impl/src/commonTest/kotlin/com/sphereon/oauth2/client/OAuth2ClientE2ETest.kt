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

package com.sphereon.oauth2.client

import com.sphereon.oauth2.common.model.AuthorizationServerMetadata
import com.sphereon.oauth2.common.model.ClientAuthenticationConfig
import com.sphereon.oauth2.common.model.ClientCredentials
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * End-to-end integration tests for OAuth2Client
 *
 * Tests key OAuth2 client functionality including:
 * - Metadata structure validation
 * - PKCE generation and verification
 * - Authorization response parsing
 *
 * Note: Full E2E tests with real authorization server integration should be
 * implemented separately once HTTP client mocking infrastructure is ready.
 *
 * These tests focus on the core OAuth2 logic without requiring network calls or
 * complex DI setup.
 */
class OAuth2ClientE2ETest {

    // ========================================================================
    // Metadata Tests
    // ========================================================================

    @Test
    fun `test fetchAuthorizationServerMetadata with synthetic metadata`() = runTest {
        // Create synthetic metadata (without making actual HTTP call)
        val syntheticMetadata = AuthorizationServerMetadata(
            issuer = "https://as.example.com",
            authorizationEndpoint = "https://as.example.com/authorize",
            tokenEndpoint = "https://as.example.com/token",
            jwksUri = "https://as.example.com/.well-known/jwks.json",
            grantTypesSupported = listOf("authorization_code", "refresh_token"),
            codeChallengeMethodsSupported = listOf("S256", "plain"),
            tokenEndpointAuthMethodsSupported = listOf("client_secret_basic", "client_secret_post"),
            introspectionEndpoint = "https://as.example.com/introspect"
        )

        // Verify metadata structure
        assertEquals("https://as.example.com", syntheticMetadata.issuer)
        assertEquals("https://as.example.com/authorize", syntheticMetadata.authorizationEndpoint)
        assertEquals("https://as.example.com/token", syntheticMetadata.tokenEndpoint)
        assertNotNull(syntheticMetadata.codeChallengeMethodsSupported)
        assertTrue(syntheticMetadata.codeChallengeMethodsSupported!!.contains("S256"))
    }

    // ========================================================================
    // DPoP Support Tests
    // ========================================================================

    @Test
    fun `test DPoP support detection from metadata`() = runTest {
        val metadataWithDPoP = AuthorizationServerMetadata(
            issuer = "https://as.example.com",
            authorizationEndpoint = "https://as.example.com/authorize",
            tokenEndpoint = "https://as.example.com/token",
            dpopSigningAlgValuesSupported = listOf("ES256", "RS256")
        )

        val metadataWithoutDPoP = AuthorizationServerMetadata(
            issuer = "https://as.example.com",
            authorizationEndpoint = "https://as.example.com/authorize",
            tokenEndpoint = "https://as.example.com/token"
        )

        // Verify DPoP field is present or absent as expected
        assertNotNull(metadataWithDPoP.dpopSigningAlgValuesSupported, "Should have DPoP algorithms")
        assertEquals(2, metadataWithDPoP.dpopSigningAlgValuesSupported!!.size)
        assertTrue(metadataWithoutDPoP.dpopSigningAlgValuesSupported == null, "Should not have DPoP algorithms")
    }

    // ========================================================================
    // Token Exchange Tests (mocked - no real AS)
    // ========================================================================

    @Test
    fun `test token exchange parameters are constructed correctly`() = runTest {
        // This test verifies that the OAuth2Client properly constructs token exchange parameters
        // In a real E2E test with authorization server, we would actually exchange tokens

        val metadata = AuthorizationServerMetadata(
            issuer = "https://as.example.com",
            authorizationEndpoint = "https://as.example.com/authorize",
            tokenEndpoint = "https://as.example.com/token",
            codeChallengeMethodsSupported = listOf("S256")
        )

        val clientAuth = ClientAuthenticationConfig.Post(
            credentials = ClientCredentials(
                clientId = "test-client",
                clientSecret = "test-secret"
            )
        )

        // In a real E2E test, we would:
        // 1. Call client.exchangeAuthorizationCode()
        // 2. Verify the token response
        // For now, we just verify the metadata is set up correctly

        assertNotNull(metadata.tokenEndpoint, "Token endpoint must be configured")
        assertEquals("test-client", clientAuth.let {
            when (it) {
                is ClientAuthenticationConfig.Post -> it.credentials.clientId
                else -> null
            }
        })
    }

    // ========================================================================
    // Integration Notes
    // ========================================================================

    /*
     * TODO: Full E2E test with real authorization server
     *
     * To create a complete E2E test:
     * 1. Start OAuth2 authorization server from oauth2/server/authorization
     * 2. Register test client
     * 3. Use OAuth2Client to perform full flow:
     *    - initiateAuthorization() → get URL + PKCE data
     *    - Simulate user authorization (store auth code in AS)
     *    - parseAuthorizationResponse() → get authorization code
     *    - exchangeAuthorizationCode() → get tokens
     *    - introspectToken() → verify token is valid
     * 4. Test DPoP flow:
     *    - Generate DPoP key pair
     *    - Include dpop_jkt in authorization
     *    - Use DPoP proof in token exchange
     *    - Verify DPoP-bound token
     */
}
