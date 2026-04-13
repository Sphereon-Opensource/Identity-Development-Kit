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

package com.sphereon.oauth2.server.authorization.impl.http

import com.sphereon.core.api.http.GenericHttpRequest
import com.sphereon.oauth2.common.model.PkceMethod
import com.sphereon.oauth2.server.authorization.impl.TestFixtures
import com.sphereon.oauth2.server.authorization.impl.storage.memory.InMemoryClientRegistryImpl
import com.sphereon.oauth2.server.authorization.impl.test.OAuth2TestAppComponent
import com.sphereon.oauth2.server.authorization.impl.test.createOAuth2TestAppComponent
import com.sphereon.oauth2.server.authorization.model.AuthorizationCodeData
import com.sphereon.oauth2.server.authorization.storage.AuthorizationCodeStorage
import com.sphereon.oauth2.server.authorization.storage.ClientRegistry
import com.sphereon.oauth2.server.authorization.storage.SessionStorage
import com.sphereon.oauth2.server.authorization.storage.TokenStorage
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.*
import kotlin.time.Duration.Companion.minutes

/**
 * Full E2E integration tests for OAuth2HttpAdapter using proper DI.
 *
 * These tests use the kotlin-inject DI container to wire up the complete
 * authorization server with real implementations, proving that:
 * 1. The DI setup works correctly
 * 2. The HTTP adapter integrates properly with the authorization server
 * 3. The universal HTTP abstraction works end-to-end
 *
 * This is the REAL integration test - no mocks, no shortcuts.
 */
class OAuth2HttpAdapterDITest {

    private lateinit var adapter: OAuth2HttpAdapter
    private lateinit var clientRegistry: ClientRegistry
    private lateinit var tokenStorage: TokenStorage
    private lateinit var authorizationCodeStorage: AuthorizationCodeStorage
    private lateinit var sessionStorage: SessionStorage

    private val json = Json { ignoreUnknownKeys = true }

    private lateinit var appComponent: OAuth2TestAppComponent
    private lateinit var sessionInstance: com.sphereon.di.session.SessionInstance

    @BeforeTest
    fun setup() {
        runBlocking {

            // Create DI component hierarchy through proper context managers
            appComponent = createOAuth2TestAppComponent()

            // Create anonymous user context and session (like JwsIntegrationTest)
            val userInstance = appComponent.userContextManager.getAnonymous()
            sessionInstance = userInstance.getOrCreateAnonymousSession()

            // Get OAuth2HttpAdapter from DI (all constructor params are wired by the container)
            adapter = (sessionInstance.component as com.sphereon.oauth2.server.authorization.impl.http.OAuth2HttpAdapter.Component).oAuth2HttpAdapter

            // Get storage from app component via Component interfaces (like JwsIntegrationTest pattern)
            val storageComponent = appComponent as com.sphereon.oauth2.server.authorization.impl.storage.memory.InMemoryClientRegistryImpl.Component
            clientRegistry = storageComponent.clientRegistry
            tokenStorage = (appComponent as com.sphereon.oauth2.server.authorization.impl.storage.memory.InMemoryTokenStorageImpl.Component).tokenStorage
            authorizationCodeStorage = (appComponent as com.sphereon.oauth2.server.authorization.impl.storage.memory.InMemoryAuthorizationCodeStorageImpl.Component).authorizationCodeStorage
            sessionStorage = (appComponent as com.sphereon.oauth2.server.authorization.impl.storage.memory.InMemorySessionStorageImpl.Component).sessionStorage

            // Register test clients
            clientRegistry.registerClient(TestFixtures.confidentialClient)
            clientRegistry.registerClient(TestFixtures.publicClient)
        }
    }

    // ========================================================================
    // Token Endpoint - Authorization Code Grant with PKCE
    // ========================================================================

    @Test
    fun `E2E - authorization code flow with PKCE through HTTP adapter`() = runTest {
        // Setup: Store authorization code (simulating completed authorization flow)
        val now = Clock.System.now()
        val authCode = AuthorizationCodeData(
            code = "test-code-pkce",
            clientId = TestFixtures.publicClient.clientId,
            subject = "user123",
            redirectUri = TestFixtures.publicClient.redirectUris.first(),
            scope = "read",
            codeChallenge = TestFixtures.Pkce.codeChallengeS256,
            codeChallengeMethod = PkceMethod.S256,
            dpopJkt = null,
            issuedAt = now,
            expiresAt = now + 10.minutes,
            used = false,
            additionalData = emptyMap()
        )
        authorizationCodeStorage.storeAuthorizationCode(authCode.code, authCode)

        // HTTP Request: POST /token
        val request = GenericHttpRequest(
            method = "POST",
            path = "/token",
            headers = mapOf("content-type" to "application/x-www-form-urlencoded"),
            bodySupplier = {
                "grant_type=authorization_code" +
                        "&code=test-code-pkce" +
                        "&redirect_uri=${authCode.redirectUri}" +
                        "&client_id=${TestFixtures.publicClient.clientId}" +
                        "&code_verifier=${TestFixtures.Pkce.codeVerifier}"
            }
        )

        // Execute through HTTP adapter (with full DI-wired authorization server)
        val response = adapter.handleRequest(request)

        // Verify complete OAuth2 token response
        assertEquals(200, response.statusCode, "Should return 200 OK")
        assertEquals("application/json", response.headers["Content-Type"])
        assertEquals("no-store", response.headers["Cache-Control"])
        assertEquals("no-cache", response.headers["Pragma"])

        val body = json.parseToJsonElement(response.body!!)
        assertNotNull(body.jsonObject["access_token"], "Must have access_token")
        assertNotNull(body.jsonObject["refresh_token"], "Must have refresh_token")
        assertEquals("Bearer", body.jsonObject["token_type"]?.jsonPrimitive?.content)
        assertNotNull(body.jsonObject["expires_in"], "Must have expires_in")

        // Verify code was marked as used (single-use requirement)
        val codeUsedResult = authorizationCodeStorage.isCodeUsed(authCode.code)
        assertTrue(codeUsedResult.isOk, "Should successfully check if code is used")
        assertTrue(codeUsedResult.value, "Authorization code must be marked as used")
    }

    @Test
    fun `E2E - PKCE verification failure returns proper error`() = runTest {
        // Setup: Code with PKCE challenge
        val now = Clock.System.now()
        val authCode = AuthorizationCodeData(
            code = "test-code-pkce-fail",
            clientId = TestFixtures.publicClient.clientId,
            subject = "user456",
            redirectUri = TestFixtures.publicClient.redirectUris.first(),
            scope = "read",
            codeChallenge = TestFixtures.Pkce.codeChallengeS256,
            codeChallengeMethod = PkceMethod.S256,
            dpopJkt = null,
            issuedAt = now,
            expiresAt = now + 10.minutes,
            used = false,
            additionalData = emptyMap()
        )
        authorizationCodeStorage.storeAuthorizationCode(authCode.code, authCode)

        // HTTP Request with WRONG code verifier (but valid format - 43+ chars)
        val request = GenericHttpRequest(
            method = "POST",
            path = "/token",
            headers = mapOf("content-type" to "application/x-www-form-urlencoded"),
            bodySupplier = {
                "grant_type=authorization_code" +
                        "&code=test-code-pkce-fail" +
                        "&redirect_uri=${authCode.redirectUri}" +
                        "&client_id=${TestFixtures.publicClient.clientId}" +
                        "&code_verifier=wrongverifierwrongverifierwrongverifierwrong" // 43 chars - valid format but wrong value
            }
        )

        // Execute
        val response = adapter.handleRequest(request)

        // Verify proper OAuth2 error response
        assertEquals(400, response.statusCode)
        val body = json.parseToJsonElement(response.body!!)
        assertEquals("invalid_grant", body.jsonObject["error"]?.jsonPrimitive?.content)
        assertTrue(body.jsonObject["error_description"]?.jsonPrimitive?.content?.contains("PKCE") ?: false)
    }

    // ========================================================================
    // Token Endpoint - Client Credentials Grant
    // ========================================================================

    @Test
    fun `E2E - client credentials flow through HTTP adapter`() = runTest {
        // HTTP Request: POST /token
        val request = GenericHttpRequest(
            method = "POST",
            path = "/token",
            headers = mapOf("content-type" to "application/x-www-form-urlencoded"),
            bodySupplier = {
                "grant_type=client_credentials" +
                        "&client_id=${TestFixtures.confidentialClient.clientId}" +
                        "&client_secret=secret123" +
                        "&scope=read"
            }
        )

        // Execute
        val response = adapter.handleRequest(request)

        // Verify
        assertEquals(200, response.statusCode)
        val body = json.parseToJsonElement(response.body!!)
        assertNotNull(body.jsonObject["access_token"])
        assertEquals("Bearer", body.jsonObject["token_type"]?.jsonPrimitive?.content)
        assertFalse(body.jsonObject.containsKey("refresh_token"), "No refresh token for client credentials")

        // Verify token was actually stored
        val accessToken = body.jsonObject["access_token"]?.jsonPrimitive?.content!!
        val tokenResult = tokenStorage.getAccessToken(accessToken)
        assertTrue(tokenResult.isOk, "Should successfully retrieve token")
        assertNotNull(tokenResult.value, "Token should be stored")
        assertEquals(TestFixtures.confidentialClient.clientId, tokenResult.value!!.clientId)
    }

    // ========================================================================
    // Token Endpoint - Refresh Token Grant
    // ========================================================================

    @Test
    fun `E2E - refresh token flow through HTTP adapter`() = runTest {
        // Setup: Store refresh token
        val refreshTokenData = TestFixtures.createRefreshToken(
            refreshToken = "refresh-123",
            clientId = TestFixtures.confidentialClient.clientId,
            subject = "user789",
            scope = "read write"
        )
        tokenStorage.storeRefreshToken(refreshTokenData.refreshToken, refreshTokenData)

        // HTTP Request: POST /token
        val request = GenericHttpRequest(
            method = "POST",
            path = "/token",
            headers = mapOf("content-type" to "application/x-www-form-urlencoded"),
            bodySupplier = {
                "grant_type=refresh_token" +
                        "&refresh_token=refresh-123" +
                        "&client_id=${TestFixtures.confidentialClient.clientId}" +
                        "&client_secret=secret123"
            }
        )

        // Execute
        val response = adapter.handleRequest(request)

        // Verify new access token issued
        assertEquals(200, response.statusCode)
        val body = json.parseToJsonElement(response.body!!)
        assertNotNull(body.jsonObject["access_token"])

        // Verify the new access token is different and stored
        val newAccessToken = body.jsonObject["access_token"]?.jsonPrimitive?.content!!
        val tokenResult = tokenStorage.getAccessToken(newAccessToken)
        assertTrue(tokenResult.isOk, "Should successfully retrieve token")
        assertNotNull(tokenResult.value)
        assertEquals("user789", tokenResult.value!!.subject)
    }

    // ========================================================================
    // Authorization Endpoint
    // ========================================================================

    @Test
    fun `E2E - authorization request creates session through HTTP adapter`() = runTest {
        // HTTP Request: GET /authorize
        val request = GenericHttpRequest(
            method = "GET",
            path = "/authorize",
            queryParameters = mapOf(
                "response_type" to "code",
                "client_id" to TestFixtures.confidentialClient.clientId,
                "redirect_uri" to TestFixtures.confidentialClient.redirectUris.first(),
                "scope" to "read write",
                "state" to "test-state-xyz"
            )
        )

        // Execute
        val response = adapter.handleRequest(request)

        // Verify session created
        assertEquals(200, response.statusCode)
        val body = json.parseToJsonElement(response.body!!)
        val sessionId = body.jsonObject["sessionId"]?.jsonPrimitive?.content
        assertNotNull(sessionId, "Session ID must be returned")
        assertEquals(TestFixtures.confidentialClient.clientId,
            body.jsonObject["clientId"]?.jsonPrimitive?.content)
        assertEquals("test-state-xyz", body.jsonObject["state"]?.jsonPrimitive?.content)

        // Verify session stored
        val sessionResult = sessionStorage.getSession(sessionId)
        assertTrue(sessionResult.isOk, "Should successfully retrieve session")
        assertNotNull(sessionResult.value, "Session should be stored")
        assertEquals(TestFixtures.confidentialClient.clientId, sessionResult.value!!.clientId)
    }

    // ========================================================================
    // Token Introspection Endpoint
    // ========================================================================

    @Test
    fun `E2E - token introspection for active token through HTTP adapter`() = runTest {
        // Setup: Store access token
        val accessToken = TestFixtures.createAccessToken(
            accessToken = "active-token-xyz",
            clientId = TestFixtures.confidentialClient.clientId,
            subject = "user999"
        )
        tokenStorage.storeAccessToken(accessToken.accessToken, accessToken)

        // HTTP Request: POST /introspect
        val request = GenericHttpRequest(
            method = "POST",
            path = "/introspect",
            headers = mapOf("content-type" to "application/x-www-form-urlencoded"),
            bodySupplier = {
                "token=active-token-xyz" +
                        "&client_id=${TestFixtures.confidentialClient.clientId}"
            }
        )

        // Execute
        val response = adapter.handleRequest(request)

        // Verify introspection response
        assertEquals(200, response.statusCode)
        val body = json.parseToJsonElement(response.body!!)
        assertEquals(true, body.jsonObject["active"]?.jsonPrimitive?.content?.toBoolean())
        assertEquals("user999", body.jsonObject["sub"]?.jsonPrimitive?.content)
        assertEquals(TestFixtures.confidentialClient.clientId,
            body.jsonObject["client_id"]?.jsonPrimitive?.content)
    }

    @Test
    fun `E2E - token introspection for invalid token returns active false`() = runTest {
        // HTTP Request with non-existent token
        val request = GenericHttpRequest(
            method = "POST",
            path = "/introspect",
            headers = mapOf("content-type" to "application/x-www-form-urlencoded"),
            bodySupplier = {
                "token=non-existent-token" +
                        "&client_id=${TestFixtures.confidentialClient.clientId}"
            }
        )

        // Execute
        val response = adapter.handleRequest(request)

        // Verify
        assertEquals(200, response.statusCode)
        val body = json.parseToJsonElement(response.body!!)
        assertEquals(false, body.jsonObject["active"]?.jsonPrimitive?.content?.toBoolean())
    }

    // ========================================================================
    // Error Handling
    // ========================================================================

    @Test
    fun `E2E - unknown endpoint returns 404`() = runTest {
        val request = GenericHttpRequest(
            method = "GET",
            path = "/unknown-endpoint"
        )

        val response = adapter.handleRequest(request)

        assertEquals(404, response.statusCode)
        val body = json.parseToJsonElement(response.body!!)
        assertTrue(body.jsonObject["message"]?.jsonPrimitive?.content?.contains("Not found") == true,
            "Should contain 'Not found' in message")
    }

    @Test
    fun `E2E - invalid client credentials returns proper error`() = runTest {
        val request = GenericHttpRequest(
            method = "POST",
            path = "/token",
            headers = mapOf("content-type" to "application/x-www-form-urlencoded"),
            bodySupplier = {
                "grant_type=client_credentials" +
                        "&client_id=invalid-client" +
                        "&client_secret=wrong"
            }
        )

        val response = adapter.handleRequest(request)

        assertTrue(response.statusCode >= 400)
        val body = json.parseToJsonElement(response.body!!)
        assertTrue(body.jsonObject.containsKey("error"))
    }
}
