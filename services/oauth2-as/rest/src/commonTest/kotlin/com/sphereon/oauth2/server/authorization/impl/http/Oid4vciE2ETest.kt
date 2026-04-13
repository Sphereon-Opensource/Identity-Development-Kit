/*
 * (c) 2026 Sphereon International B.V.
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

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.binary.TypeToken
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.encodeToBase64Url
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.http.GenericHttpRequest
import com.sphereon.core.api.service.StringResult
import com.sphereon.crypto.core.generic.DigestAlg
import com.sphereon.crypto.core.generic.hash
import com.sphereon.oauth2.common.config.FeaturePolicy
import com.sphereon.oauth2.common.config.OAuth2ServerInstanceConfig
import com.sphereon.oauth2.common.config.OAuth2ServersConfig
import com.sphereon.oauth2.common.config.OAuth2ServersConfigProvider
import com.sphereon.oauth2.common.model.AuthorizationServerMetadata
import com.sphereon.oauth2.common.model.ClientAuthenticationConfig
import com.sphereon.oauth2.common.model.ClientAuthenticationMethod
import com.sphereon.oauth2.common.model.GrantType
import com.sphereon.oauth2.common.model.PkceMethod
import com.sphereon.oauth2.common.model.TokenIntrospectionResponse
import com.sphereon.oauth2.common.model.TokenResponse
import com.sphereon.oauth2.server.authorization.command.AttestationChallengeResponse
import com.sphereon.oauth2.server.authorization.command.AuthorizationErrorResponseData
import com.sphereon.oauth2.server.authorization.command.AuthorizationRequestData
import com.sphereon.oauth2.server.authorization.command.AuthorizationResponseData
import com.sphereon.oauth2.server.authorization.command.BuildServerMetadataArgs
import com.sphereon.oauth2.server.authorization.command.BuildServerMetadataCommand
import com.sphereon.oauth2.server.authorization.command.CreateAccessTokenArgs
import com.sphereon.oauth2.server.authorization.command.CreateAccessTokenCommand
import com.sphereon.oauth2.server.authorization.command.CreateAttestationChallengeArgs
import com.sphereon.oauth2.server.authorization.command.CreateAttestationChallengeCommand
import com.sphereon.oauth2.server.authorization.command.CreateAuthorizationCodeArgs
import com.sphereon.oauth2.server.authorization.command.CreateAuthorizationCodeCommand
import com.sphereon.oauth2.server.authorization.command.CreateAuthorizationErrorResponseArgs
import com.sphereon.oauth2.server.authorization.command.CreateAuthorizationErrorResponseCommand
import com.sphereon.oauth2.server.authorization.command.CreateAuthorizationResponseArgs
import com.sphereon.oauth2.server.authorization.command.CreateAuthorizationResponseCommand
import com.sphereon.oauth2.server.authorization.command.CreateAuthorizationSessionCommand
import com.sphereon.oauth2.server.authorization.command.CreateIdTokenArgs
import com.sphereon.oauth2.server.authorization.command.CreateIdTokenCommand
import com.sphereon.oauth2.server.authorization.command.CreatePushedAuthorizationResponseArgs
import com.sphereon.oauth2.server.authorization.command.CreatePushedAuthorizationResponseCommand
import com.sphereon.oauth2.server.authorization.command.CreateRefreshTokenArgs
import com.sphereon.oauth2.server.authorization.command.CreateRefreshTokenCommand
import com.sphereon.oauth2.server.authorization.command.CreateRequestUriCommand
import com.sphereon.oauth2.server.authorization.command.CreateTokenResponseArgs
import com.sphereon.oauth2.server.authorization.command.CreateTokenResponseCommand
import com.sphereon.oauth2.server.authorization.command.GetJwksArgs
import com.sphereon.oauth2.server.authorization.command.GetJwksCommand
import com.sphereon.oauth2.server.authorization.command.GetUserInfoArgs
import com.sphereon.oauth2.server.authorization.command.GetUserInfoCommand
import com.sphereon.oauth2.server.authorization.command.GrantParameters
import com.sphereon.oauth2.server.authorization.command.HandleIaeFollowUpArgs
import com.sphereon.oauth2.server.authorization.command.HandleIaeFollowUpCommand
import com.sphereon.oauth2.server.authorization.command.HandleIaeInitialRequestArgs
import com.sphereon.oauth2.server.authorization.command.HandleIaeInitialRequestCommand
import com.sphereon.oauth2.server.authorization.command.IaeResult
import com.sphereon.oauth2.server.authorization.command.IntrospectTokenArgs
import com.sphereon.oauth2.server.authorization.command.IntrospectTokenCommand
import com.sphereon.oauth2.server.authorization.command.IntrospectionRequestData
import com.sphereon.oauth2.server.authorization.command.JwksResult
import com.sphereon.oauth2.server.authorization.command.ParseAuthorizationRequestArgs
import com.sphereon.oauth2.server.authorization.command.ParseAuthorizationRequestCommand
import com.sphereon.oauth2.server.authorization.command.ParseIntrospectionRequestArgs
import com.sphereon.oauth2.server.authorization.command.ParseIntrospectionRequestCommand
import com.sphereon.oauth2.server.authorization.command.ParsePushedAuthorizationRequestArgs
import com.sphereon.oauth2.server.authorization.command.ParsePushedAuthorizationRequestCommand
import com.sphereon.oauth2.server.authorization.command.ParseRevocationRequestArgs
import com.sphereon.oauth2.server.authorization.command.ParseRevocationRequestCommand
import com.sphereon.oauth2.server.authorization.command.ParseTokenRequestArgs
import com.sphereon.oauth2.server.authorization.command.ParseTokenRequestCommand
import com.sphereon.oauth2.server.authorization.command.PushedAuthorizationResponse
import com.sphereon.oauth2.server.authorization.command.RequestUriData
import com.sphereon.oauth2.server.authorization.command.RetrieveAuthorizationRequestByUriCommand
import com.sphereon.oauth2.server.authorization.command.RetrieveByRequestUriArgs
import com.sphereon.oauth2.server.authorization.command.RevocationRequestData
import com.sphereon.oauth2.server.authorization.command.RevokeTokenArgs
import com.sphereon.oauth2.server.authorization.command.RevokeTokenCommand
import com.sphereon.oauth2.server.authorization.command.TokenRequestData
import com.sphereon.oauth2.server.authorization.command.UserInfoResponse
import com.sphereon.oauth2.server.authorization.command.VerifiedAuthorizationCodeGrant
import com.sphereon.oauth2.server.authorization.command.VerifiedAuthorizationRequest
import com.sphereon.oauth2.server.authorization.command.VerifiedClientAuthentication
import com.sphereon.oauth2.server.authorization.command.VerifiedClientCredentialsGrant
import com.sphereon.oauth2.server.authorization.command.VerifiedPreAuthCodeGrant
import com.sphereon.oauth2.server.authorization.command.VerifiedRefreshTokenGrant
import com.sphereon.oauth2.server.authorization.command.VerifiedTokenExchangeGrant
import com.sphereon.oauth2.server.authorization.command.VerifyAuthorizationCodeGrantArgs
import com.sphereon.oauth2.server.authorization.command.VerifyAuthorizationCodeGrantCommand
import com.sphereon.oauth2.server.authorization.command.VerifyAuthorizationRequestCommand
import com.sphereon.oauth2.server.authorization.command.VerifyClientAuthenticationArgs
import com.sphereon.oauth2.server.authorization.command.VerifyClientAuthenticationCommand
import com.sphereon.oauth2.server.authorization.command.VerifyClientCredentialsGrantArgs
import com.sphereon.oauth2.server.authorization.command.VerifyClientCredentialsGrantCommand
import com.sphereon.oauth2.server.authorization.command.VerifyPreAuthCodeArgs
import com.sphereon.oauth2.server.authorization.command.VerifyPreAuthorizedCodeGrantCommand
import com.sphereon.oauth2.server.authorization.command.VerifyPushedAuthorizationRequestArgs
import com.sphereon.oauth2.server.authorization.command.VerifyPushedAuthorizationRequestCommand
import com.sphereon.oauth2.server.authorization.command.VerifyRefreshTokenGrantArgs
import com.sphereon.oauth2.server.authorization.command.VerifyRefreshTokenGrantCommand
import com.sphereon.oauth2.server.authorization.command.VerifyTokenExchangeGrantArgs
import com.sphereon.oauth2.server.authorization.command.VerifyTokenExchangeGrantCommand
import com.sphereon.oauth2.server.authorization.error.AuthorizationServerError
import com.sphereon.oauth2.server.authorization.impl.oidc.OidcScopeClaimsMapperImpl
import com.sphereon.oauth2.server.authorization.model.AuthorizationCodeData
import com.sphereon.oauth2.server.authorization.model.AuthorizationSession
import com.sphereon.oauth2.server.authorization.model.ClientRegistration
import com.sphereon.oauth2.server.authorization.model.ClientType
import com.sphereon.oauth2.server.authorization.provider.AuthenticatedUser
import com.sphereon.oauth2.server.authorization.provider.AuthenticationError
import com.sphereon.oauth2.server.authorization.provider.AuthenticationHint
import com.sphereon.oauth2.server.authorization.provider.AuthenticationMethod
import com.sphereon.oauth2.server.authorization.provider.UserAuthenticationProvider
import com.sphereon.oauth2.server.authorization.provider.UserCredentials
import com.sphereon.oauth2.server.authorization.provider.UserInfo
import com.sphereon.oauth2.server.authorization.service.AuthorizationServerService
import com.sphereon.oauth2.server.authorization.storage.PreAuthorizedCodeData
import com.sphereon.oauth2.server.authorization.storage.PreAuthorizedCodeStorage
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.putJsonArray
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

// ============================================================================
// OID4VCI E2E tests through the OAuth2HttpAdapter
//
// These tests exercise the full HTTP adapter: form parsing, routing, grant
// verification, access-token issuance, and RFC-compliant response formatting.
//
// The AuthorizationServerService is a purpose-built test implementation that
// uses real in-memory storage for pre-auth codes and auth codes, producing
// realistic token responses. Command-level business logic is already covered
// by Oid4vciAsIntegrationTest in the impl module; here we focus on the HTTP
// adapter contract.
// ============================================================================

/**
 * End-to-end tests for OID4VCI flows through the OAuth2HttpAdapter.
 *
 * Each test:
 * 1. Seeds storage with test data (pre-auth codes, auth codes, client registrations)
 * 2. Constructs a GenericHttpRequest (form-encoded body, correct headers)
 * 3. Calls adapter.handleRequest(request)
 * 4. Asserts HTTP status, headers, and JSON response body
 */
class Oid4vciE2ETest {
    private lateinit var adapter: OAuth2HttpAdapter
    private lateinit var service: TestOid4vciAuthorizationServerService

    private val json = Json { ignoreUnknownKeys = true }

    private val configProvider =
        TestOid4vciConfigProvider(
            OAuth2ServersConfig(
                servers =
                    mapOf(
                        "default" to
                            OAuth2ServerInstanceConfig(
                                baseUrl = "https://auth.example.com",
                                oidc = FeaturePolicy.SUPPORTED,
                                introspection = FeaturePolicy.SUPPORTED,
                                grantTypesEnabled =
                                    setOf(
                                        "authorization_code",
                                        "urn:ietf:params:oauth:grant-type:pre-authorized_code",
                                    ),
                            ),
                    ),
            ),
        )

    /** Public client supporting pre-authorized code and authorization code grant */
    private val oid4vciClient =
        ClientRegistration(
            clientId = "oid4vci-wallet",
            clientSecret = null,
            clientType = ClientType.PUBLIC,
            grantTypes = listOf(GrantType.PRE_AUTHORIZED_CODE, GrantType.AUTHORIZATION_CODE),
            redirectUris = listOf("https://wallet.example.com/callback"),
            tokenEndpointAuthMethod = ClientAuthenticationMethod.NONE,
            requirePkce = true,
            requirePushedAuthorizationRequests = false,
            accessTokenLifetime = 3600,
            refreshTokenLifetime = 86400,
            authorizationCodeLifetime = 600,
            allowedScopes = listOf("openid"),
            clientName = "OID4VCI E2E Wallet Client",
        )

    @BeforeTest
    fun setup() {
        service = TestOid4vciAuthorizationServerService()
        service.registerClient(oid4vciClient)

        adapter =
            OAuth2HttpAdapter(
                authorizationServerService = service,
                configProvider = configProvider,
                userAuthProvider = NoOpOid4vciUserAuthProvider(),
                scopeClaimsMapper = OidcScopeClaimsMapperImpl(),
                handleIaeInitialRequestCommand = NoOpOid4vciIaeInitialCommand(),
                handleIaeFollowUpCommand = NoOpOid4vciIaeFollowUpCommand(),
                preAuthorizedCodeStorage = NoOpOid4vciPreAuthorizedCodeStorage(),
            )
    }

    // =========================================================================
    // Test 1: Pre-auth code E2E through HTTP adapter with credential_identifiers
    // =========================================================================

    @Test
    fun preAuthorizedCodeWithCredentialIdentifiers() =
        runTest {
            val now = Clock.System.now()
            service.storePreAuthorizedCode(
                "pre-auth-001",
                PreAuthorizedCodeData(
                    sessionId = "session-preauth-1",
                    credentialConfigurationIds = listOf("UniversityDegreeCredential", "EmployeeIDCredential"),
                    subject = "did:example:holder123",
                    txCodeRequired = false,
                    txCodeHash = null,
                    clientId = oid4vciClient.clientId,
                    createdAt = now,
                    expiresAt = now + 10.minutes,
                ),
            )

            val request =
                GenericHttpRequest(
                    method = "POST",
                    path = "/token",
                    headers = mapOf("content-type" to "application/x-www-form-urlencoded"),
                    bodySupplier = {
                        "grant_type=urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Apre-authorized_code" +
                            "&pre-authorized_code=pre-auth-001" +
                            "&client_id=${oid4vciClient.clientId}"
                    },
                )

            val response = adapter.handleRequest(request)

            assertEquals(200, response.statusCode, "Pre-auth code grant should succeed")
            val body = json.parseToJsonElement(response.body!!).jsonObject

            assertNotNull(body["access_token"], "Response must have access_token")
            assertEquals("Bearer", body["token_type"]?.jsonPrimitive?.content)

            // OID4VCI 1.1 Section 7.2: authorization_details with credential_identifiers
            val authDetails = body["authorization_details"]?.jsonArray
            assertNotNull(authDetails, "Response must have authorization_details for OID4VCI")
            assertEquals(2, authDetails.size, "Should have one entry per credential configuration")

            val firstDetail = authDetails[0].jsonObject
            assertEquals("openid_credential", firstDetail["type"]?.jsonPrimitive?.content)
            assertNotNull(firstDetail["credential_configuration_id"])
            assertNotNull(firstDetail["credential_identifiers"], "Each detail must have credential_identifiers")
        }

    // =========================================================================
    // Test 2: Auth-code + PKCE E2E through HTTP adapter
    // =========================================================================

    @Test
    fun authorizationCodeWithPkceAndCredentialData() =
        runTest {
            val now = Clock.System.now()
            service.storeAuthorizationCode(
                "auth-code-pkce-001",
                AuthorizationCodeData(
                    code = "auth-code-pkce-001",
                    clientId = oid4vciClient.clientId,
                    subject = "did:example:holder456",
                    redirectUri = oid4vciClient.redirectUris.first(),
                    scope = "openid",
                    codeChallenge = PKCE_CHALLENGE_S256,
                    codeChallengeMethod = PkceMethod.S256,
                    dpopJkt = null,
                    issuedAt = now,
                    expiresAt = now + 10.minutes,
                    used = false,
                    additionalData =
                        mapOf(
                            "credential_configuration_ids" to listOf("UniversityDegreeCredential"),
                        ),
                ),
            )

            val request =
                GenericHttpRequest(
                    method = "POST",
                    path = "/token",
                    headers = mapOf("content-type" to "application/x-www-form-urlencoded"),
                    bodySupplier = {
                        "grant_type=authorization_code" +
                            "&code=auth-code-pkce-001" +
                            "&redirect_uri=${urlEncode(oid4vciClient.redirectUris.first())}" +
                            "&client_id=${oid4vciClient.clientId}" +
                            "&code_verifier=$PKCE_VERIFIER"
                    },
                )

            val response = adapter.handleRequest(request)

            assertEquals(200, response.statusCode, "Auth code + PKCE flow should succeed")
            val body = json.parseToJsonElement(response.body!!).jsonObject

            assertNotNull(body["access_token"], "Must have access_token")
            assertNotNull(body["refresh_token"], "Must have refresh_token")
            assertEquals("Bearer", body["token_type"]?.jsonPrimitive?.content)

            // authorization_details from additionalData
            val authDetails = body["authorization_details"]?.jsonArray
            assertNotNull(authDetails, "Response must include authorization_details from auth code")
            assertTrue(authDetails.size > 0)

            val detail = authDetails[0].jsonObject
            assertEquals("openid_credential", detail["type"]?.jsonPrimitive?.content)
            assertNotNull(detail["credential_identifiers"])
        }

    // =========================================================================
    // Test 3: Auth-code PKCE failure E2E
    // =========================================================================

    @Test
    fun authorizationCodePkceFailure() =
        runTest {
            val now = Clock.System.now()
            service.storeAuthorizationCode(
                "auth-code-pkce-fail",
                AuthorizationCodeData(
                    code = "auth-code-pkce-fail",
                    clientId = oid4vciClient.clientId,
                    subject = "did:example:holder789",
                    redirectUri = oid4vciClient.redirectUris.first(),
                    scope = "openid",
                    codeChallenge = PKCE_CHALLENGE_S256,
                    codeChallengeMethod = PkceMethod.S256,
                    dpopJkt = null,
                    issuedAt = now,
                    expiresAt = now + 10.minutes,
                    used = false,
                    additionalData = emptyMap(),
                ),
            )

            val request =
                GenericHttpRequest(
                    method = "POST",
                    path = "/token",
                    headers = mapOf("content-type" to "application/x-www-form-urlencoded"),
                    bodySupplier = {
                        "grant_type=authorization_code" +
                            "&code=auth-code-pkce-fail" +
                            "&redirect_uri=${urlEncode(oid4vciClient.redirectUris.first())}" +
                            "&client_id=${oid4vciClient.clientId}" +
                            "&code_verifier=wrongverifierwrongverifierwrongverifierwrong"
                    },
                )

            val response = adapter.handleRequest(request)

            assertEquals(400, response.statusCode, "PKCE failure should return 400")
            val body = json.parseToJsonElement(response.body!!).jsonObject
            assertEquals("invalid_grant", body["error"]?.jsonPrimitive?.content)
        }

    // =========================================================================
    // Test 4: Pre-auth code single-use E2E
    // =========================================================================

    @Test
    fun preAuthorizedCodeSingleUse() =
        runTest {
            val now = Clock.System.now()
            service.storePreAuthorizedCode(
                "pre-auth-replay",
                PreAuthorizedCodeData(
                    sessionId = "session-replay",
                    credentialConfigurationIds = listOf("VerifiableCredential"),
                    subject = "did:example:replaytest",
                    txCodeRequired = false,
                    txCodeHash = null,
                    clientId = oid4vciClient.clientId,
                    createdAt = now,
                    expiresAt = now + 10.minutes,
                ),
            )

            val makeRequest = {
                GenericHttpRequest(
                    method = "POST",
                    path = "/token",
                    headers = mapOf("content-type" to "application/x-www-form-urlencoded"),
                    bodySupplier = {
                        "grant_type=urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Apre-authorized_code" +
                            "&pre-authorized_code=pre-auth-replay" +
                            "&client_id=${oid4vciClient.clientId}"
                    },
                )
            }

            // First exchange -- success
            val first = adapter.handleRequest(makeRequest())
            assertEquals(200, first.statusCode, "First exchange should succeed")

            // Second exchange with same code -- failure (single-use)
            val second = adapter.handleRequest(makeRequest())
            assertEquals(400, second.statusCode, "Second exchange must fail (single-use enforcement)")
            val body = json.parseToJsonElement(second.body!!).jsonObject
            assertEquals("invalid_grant", body["error"]?.jsonPrimitive?.content)
        }

    // =========================================================================
    // Test 5: Discovery metadata E2E
    // =========================================================================

    @Test
    fun discoveryMetadata() =
        runTest {
            val request =
                GenericHttpRequest(
                    method = "GET",
                    path = "/.well-known/oauth-authorization-server",
                    headers = mapOf("host" to "auth.example.com"),
                )

            val response = adapter.handleRequest(request)

            assertEquals(200, response.statusCode)
            val body = json.parseToJsonElement(response.body!!).jsonObject

            assertNotNull(body["issuer"], "Metadata must have issuer")
            assertNotNull(body["token_endpoint"], "Metadata must have token_endpoint")

            // OID4VCI: pre-authorized_grant_anonymous_access_supported
            val preAuthAnonymous = body["pre-authorized_grant_anonymous_access_supported"]
            assertNotNull(preAuthAnonymous, "Metadata should include pre-authorized_grant_anonymous_access_supported")
        }

    // =========================================================================
    // Test 6: Pre-auth code with tx_code E2E
    // =========================================================================

    @Test
    fun preAuthorizedCodeWithTxCodeSuccess() =
        runTest {
            val txCodeValue = "123456"
            val txCodeHashValue = hash(txCodeValue.encodeToByteArray(), DigestAlg.SHA256).encodeToBase64Url()

            val now = Clock.System.now()
            service.storePreAuthorizedCode(
                "pre-auth-txcode",
                PreAuthorizedCodeData(
                    sessionId = "session-txcode",
                    credentialConfigurationIds = listOf("IdentityCredential"),
                    subject = "did:example:txcode-user",
                    txCodeRequired = true,
                    txCodeHash = txCodeHashValue,
                    clientId = oid4vciClient.clientId,
                    createdAt = now,
                    expiresAt = now + 10.minutes,
                ),
            )

            val request =
                GenericHttpRequest(
                    method = "POST",
                    path = "/token",
                    headers = mapOf("content-type" to "application/x-www-form-urlencoded"),
                    bodySupplier = {
                        "grant_type=urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Apre-authorized_code" +
                            "&pre-authorized_code=pre-auth-txcode" +
                            "&tx_code=$txCodeValue" +
                            "&client_id=${oid4vciClient.clientId}"
                    },
                )

            val response = adapter.handleRequest(request)

            assertEquals(200, response.statusCode, "Correct tx_code should succeed")
            val body = json.parseToJsonElement(response.body!!).jsonObject
            assertNotNull(body["access_token"])
        }

    @Test
    fun preAuthorizedCodeWithWrongTxCodeFails() =
        runTest {
            val correctTxCode = "123456"
            val txCodeHashValue = hash(correctTxCode.encodeToByteArray(), DigestAlg.SHA256).encodeToBase64Url()

            val now = Clock.System.now()
            service.storePreAuthorizedCode(
                "pre-auth-txcode-wrong",
                PreAuthorizedCodeData(
                    sessionId = "session-txcode-wrong",
                    credentialConfigurationIds = listOf("IdentityCredential"),
                    subject = "did:example:txcode-wrong",
                    txCodeRequired = true,
                    txCodeHash = txCodeHashValue,
                    clientId = oid4vciClient.clientId,
                    createdAt = now,
                    expiresAt = now + 10.minutes,
                ),
            )

            val request =
                GenericHttpRequest(
                    method = "POST",
                    path = "/token",
                    headers = mapOf("content-type" to "application/x-www-form-urlencoded"),
                    bodySupplier = {
                        "grant_type=urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Apre-authorized_code" +
                            "&pre-authorized_code=pre-auth-txcode-wrong" +
                            "&tx_code=999999" +
                            "&client_id=${oid4vciClient.clientId}"
                    },
                )

            val response = adapter.handleRequest(request)

            assertEquals(400, response.statusCode, "Wrong tx_code should return 400")
            val body = json.parseToJsonElement(response.body!!).jsonObject
            assertEquals("invalid_grant", body["error"]?.jsonPrimitive?.content)
        }

    // =========================================================================
    // Helpers
    // =========================================================================

    companion object {
        // RFC 7636 test vectors
        const val PKCE_VERIFIER = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"
        const val PKCE_CHALLENGE_S256 = "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM"
    }

    private fun urlEncode(value: String): String {
        val sb = StringBuilder()
        for (c in value) {
            when {
                c.isLetterOrDigit() || c in "-_.~" -> {
                    sb.append(c)
                }

                c == ' ' -> {
                    sb.append('+')
                }

                else -> {
                    val bytes = c.toString().encodeToByteArray()
                    for (b in bytes) {
                        sb.append('%')
                        sb.append(((b.toInt() shr 4) and 0xF).digitToChar(16).uppercaseChar())
                        sb.append((b.toInt() and 0xF).digitToChar(16).uppercaseChar())
                    }
                }
            }
        }
        return sb.toString()
    }
}

// ============================================================================
// Test AuthorizationServerService with real in-memory storage and commands
// ============================================================================

/**
 * Test service that implements both the AuthorizationServerService interface and its
 * Commands interface. Uses real in-memory storage for pre-auth/auth codes and real
 * PKCE/tx_code verification logic, producing realistic token responses.
 *
 * The OAuth2Handlers class uses `authorizationServerService.commands.xxx.execute()`,
 * so we must provide working command implementations.
 */
private class TestOid4vciAuthorizationServerService : AuthorizationServerService {
    // Storage
    private val clients = mutableMapOf<String, ClientRegistration>()
    private val preAuthCodes = mutableMapOf<String, PreAuthorizedCodeData>()
    private val authCodes = mutableMapOf<String, AuthorizationCodeData>()
    private var tokenCounter = 0

    fun registerClient(client: ClientRegistration) {
        clients[client.clientId] = client
    }

    fun storePreAuthorizedCode(
        code: String,
        data: PreAuthorizedCodeData,
    ) {
        preAuthCodes[code] = data
    }

    fun storeAuthorizationCode(
        code: String,
        data: AuthorizationCodeData,
    ) {
        authCodes[code] = data
    }

    // -- Service method implementations (delegate to the same logic as commands) --

    override suspend fun parseTokenRequest(args: ParseTokenRequestArgs) = doParseTokenRequest(args)

    override suspend fun verifyClientAuthentication(args: VerifyClientAuthenticationArgs) = doVerifyClientAuth(args)

    override suspend fun verifyPreAuthorizedCodeGrant(args: VerifyPreAuthCodeArgs) = doVerifyPreAuthCode(args)

    override suspend fun verifyAuthorizationCodeGrant(args: VerifyAuthorizationCodeGrantArgs) = doVerifyAuthCode(args)

    override suspend fun createAccessToken(args: CreateAccessTokenArgs) = doCreateAccessToken(args)

    override suspend fun createRefreshToken(args: CreateRefreshTokenArgs) = doCreateRefreshToken(args)

    override suspend fun createTokenResponse(args: CreateTokenResponseArgs) = doCreateTokenResponse(args)

    override suspend fun buildServerMetadata(args: BuildServerMetadataArgs) = doBuildServerMetadata(args)

    override suspend fun getJwks(args: GetJwksArgs) = Ok(JwksResult(keys = emptyList()))

    // -- Stubs for unexercised endpoints --

    private fun err(): Nothing = throw NotImplementedError("Not exercised in OID4VCI E2E test")

    override suspend fun verifyRefreshTokenGrant(args: VerifyRefreshTokenGrantArgs) = err()

    override suspend fun verifyClientCredentialsGrant(args: VerifyClientCredentialsGrantArgs) = err()

    override suspend fun verifyTokenExchangeGrant(args: VerifyTokenExchangeGrantArgs) = err()

    override suspend fun parseAuthorizationRequest(args: ParseAuthorizationRequestArgs) = err()

    override suspend fun verifyAuthorizationRequest(args: AuthorizationRequestData) = err()

    override suspend fun createAuthorizationSession(args: VerifiedAuthorizationRequest) = err()

    override suspend fun createAuthorizationCode(args: CreateAuthorizationCodeArgs) = err()

    override suspend fun createAuthorizationResponse(args: CreateAuthorizationResponseArgs) = err()

    override suspend fun createAuthorizationErrorResponse(args: CreateAuthorizationErrorResponseArgs) = err()

    override suspend fun parsePushedAuthorizationRequest(args: ParsePushedAuthorizationRequestArgs) = err()

    override suspend fun verifyPushedAuthorizationRequest(args: VerifyPushedAuthorizationRequestArgs) = err()

    override suspend fun createRequestUri(args: VerifiedAuthorizationRequest) = err()

    override suspend fun createPushedAuthorizationResponse(args: CreatePushedAuthorizationResponseArgs) = err()

    override suspend fun retrieveAuthorizationRequestByUri(requestUri: String) = err()

    override suspend fun parseIntrospectionRequest(args: ParseIntrospectionRequestArgs) = err()

    override suspend fun introspectToken(args: IntrospectTokenArgs) = err()

    override suspend fun parseRevocationRequest(args: ParseRevocationRequestArgs) = err()

    override suspend fun revokeToken(args: RevokeTokenArgs) = err()

    override suspend fun createAttestationChallenge(args: CreateAttestationChallengeArgs) = err()

    override suspend fun createIdToken(args: CreateIdTokenArgs) = err()

    override suspend fun getUserInfo(args: GetUserInfoArgs) = err()

    // -- Business logic shared between service methods and commands --

    private fun doParseTokenRequest(args: ParseTokenRequestArgs): IdkResult<TokenRequestData, IdkError> {
        val body = args.requestBody
        val grantTypeStr =
            body["grant_type"]?.firstOrNull()
                ?: return Err(IdkError.fromString(code = "invalid_request", message = "Missing grant_type"))

        val clientId =
            body["client_id"]?.firstOrNull()
                ?: return Err(IdkError.fromString(code = "invalid_request", message = "Missing client_id"))

        val grantType =
            when (grantTypeStr) {
                "authorization_code" -> GrantType.AUTHORIZATION_CODE
                "urn:ietf:params:oauth:grant-type:pre-authorized_code" -> GrantType.PRE_AUTHORIZED_CODE
                "client_credentials" -> GrantType.CLIENT_CREDENTIALS
                "refresh_token" -> GrantType.REFRESH_TOKEN
                else -> return Err(IdkError.fromString(code = "unsupported_grant_type", message = "Unsupported grant_type: $grantTypeStr"))
            }

        val grantParams =
            when (grantType) {
                GrantType.PRE_AUTHORIZED_CODE -> {
                    val preAuthCode =
                        body["pre-authorized_code"]?.firstOrNull()
                            ?: return Err(IdkError.fromString(code = "invalid_request", message = "Missing pre-authorized_code"))
                    GrantParameters.PreAuthorizedCode(
                        preAuthorizedCode = preAuthCode,
                        txCode = body["tx_code"]?.firstOrNull(),
                    )
                }

                GrantType.AUTHORIZATION_CODE -> {
                    val code =
                        body["code"]?.firstOrNull()
                            ?: return Err(IdkError.fromString(code = "invalid_request", message = "Missing code"))
                    GrantParameters.AuthorizationCode(
                        code = code,
                        redirectUri = body["redirect_uri"]?.firstOrNull() ?: "",
                        codeVerifier = body["code_verifier"]?.firstOrNull(),
                    )
                }

                else -> {
                    return Err(IdkError.fromString(code = "unsupported_grant_type", message = "Unsupported in test"))
                }
            }

        return Ok(
            TokenRequestData(
                grantType = grantType,
                clientId = clientId,
                clientAuthentication = ClientAuthenticationConfig.None(clientId = clientId),
                httpUrl = "https://auth.example.com/token",
                grantParameters = grantParams,
            ),
        )
    }

    private fun doVerifyClientAuth(args: VerifyClientAuthenticationArgs): IdkResult<VerifiedClientAuthentication, IdkError> {
        val client = clients[args.clientId]
        if (client != null && client.tokenEndpointAuthMethod == ClientAuthenticationMethod.NONE) {
            return Ok(VerifiedClientAuthentication(clientId = args.clientId, method = ClientAuthenticationMethod.NONE))
        }
        return Err(IdkError.fromString(code = "invalid_client", message = "Client not found"))
    }

    private fun doVerifyPreAuthCode(args: VerifyPreAuthCodeArgs): IdkResult<VerifiedPreAuthCodeGrant, IdkError> {
        val data =
            preAuthCodes.remove(args.preAuthorizedCode)
                ?: return Err(IdkError.fromString(code = "invalid_grant", message = "Pre-authorized code not found or already used"))

        if (Clock.System.now() > data.expiresAt) {
            return Err(IdkError.fromString(code = "invalid_grant", message = "Pre-authorized code expired"))
        }

        if (data.txCodeRequired) {
            val txCode =
                args.txCode
                    ?: return Err(IdkError.fromString(code = "invalid_grant", message = "tx_code required but not provided"))
            val providedHash = hash(txCode.encodeToByteArray(), DigestAlg.SHA256).encodeToBase64Url()
            if (providedHash != data.txCodeHash) {
                return Err(IdkError.fromString(code = "invalid_grant", message = "Invalid tx_code"))
            }
        }

        return Ok(
            VerifiedPreAuthCodeGrant(
                sessionId = data.sessionId,
                subject = data.subject,
                credentialConfigurationIds = data.credentialConfigurationIds,
            ),
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun doVerifyAuthCode(args: VerifyAuthorizationCodeGrantArgs): IdkResult<VerifiedAuthorizationCodeGrant, IdkError> {
        val data =
            authCodes.remove(args.code)
                ?: return Err(IdkError.fromString(code = "invalid_grant", message = "Authorization code not found or already used"))

        if (args.redirectUri != data.redirectUri) {
            return Err(IdkError.fromString(code = "invalid_grant", message = "redirect_uri mismatch"))
        }

        if (data.codeChallenge != null && data.codeChallengeMethod != null) {
            val verifier =
                args.codeVerifier
                    ?: return Err(IdkError.fromString(code = "invalid_grant", message = "PKCE code_verifier required"))
            val computed =
                when (data.codeChallengeMethod) {
                    PkceMethod.S256 -> hash(verifier.encodeToByteArray(), DigestAlg.SHA256).encodeToBase64Url()
                    PkceMethod.PLAIN -> verifier
                    else -> return Err(IdkError.fromString(code = "invalid_grant", message = "Unsupported PKCE method"))
                }
            if (computed != data.codeChallenge) {
                return Err(IdkError.fromString(code = "invalid_grant", message = "PKCE verification failed"))
            }
        }

        return Ok(
            VerifiedAuthorizationCodeGrant(
                codeData = data,
                subject = data.subject,
                clientId = data.clientId,
                scope = data.scope,
                dpopJkt = data.dpopJkt,
                additionalData = data.additionalData as Map<String, Any>,
            ),
        )
    }

    private fun doCreateAccessToken(args: CreateAccessTokenArgs): IdkResult<StringResult, IdkError> {
        tokenCounter++
        return Ok(StringResult("test-access-token-$tokenCounter"))
    }

    private fun doCreateRefreshToken(args: CreateRefreshTokenArgs): IdkResult<StringResult, IdkError> {
        tokenCounter++
        return Ok(StringResult("test-refresh-token-$tokenCounter"))
    }

    private fun doCreateTokenResponse(args: CreateTokenResponseArgs): IdkResult<TokenResponse, IdkError> =
        Ok(
            TokenResponse(
                accessToken = args.accessToken,
                tokenType = args.tokenType,
                expiresIn = args.expiresIn,
                refreshToken = args.refreshToken,
                scope = args.scope,
                idToken = args.idToken,
                authorizationDetails = args.authorizationDetails,
                cNonce = args.cNonce,
                cNonceExpiresIn = args.cNonceExpiresIn,
                issuedTokenType = args.issuedTokenType,
            ),
        )

    private fun doBuildServerMetadata(args: BuildServerMetadataArgs): IdkResult<AuthorizationServerMetadata, IdkError> {
        val baseUrl = args.baseUrlOverride ?: "https://auth.example.com"
        return Ok(
            AuthorizationServerMetadata(
                issuer = baseUrl,
                tokenEndpoint = "$baseUrl/token",
                authorizationEndpoint = "$baseUrl/authorize",
                jwksUri = "$baseUrl/.well-known/jwks.json",
                grantTypesSupported =
                    listOf(
                        "authorization_code",
                        "urn:ietf:params:oauth:grant-type:pre-authorized_code",
                    ),
                preAuthorizedGrantAnonymousAccessSupported = true,
            ),
        )
    }

    // -- Commands: each command delegates to the shared business-logic methods above --
    // The Commands interface requires specific typed command interfaces, so we create
    // anonymous objects implementing each specific interface.

    override val commands: AuthorizationServerService.Commands = TestCommands()

    private inner class TestCommands : AuthorizationServerService.Commands {
        override val parseTokenRequest =
            object : ParseTokenRequestCommand {
                override val inputTypeToken = typeToken<ParseTokenRequestArgs>()
                override val outputTypeToken = typeToken<TokenRequestData>()
                override val isEnabled = true

                override suspend fun execute(args: ParseTokenRequestArgs) = doParseTokenRequest(args)
            }
        override val verifyAuthorizationCodeGrant =
            object : VerifyAuthorizationCodeGrantCommand {
                override val inputTypeToken = typeToken<VerifyAuthorizationCodeGrantArgs>()
                override val outputTypeToken = typeToken<VerifiedAuthorizationCodeGrant>()
                override val isEnabled = true

                override suspend fun execute(args: VerifyAuthorizationCodeGrantArgs) = doVerifyAuthCode(args)
            }
        override val verifyRefreshTokenGrant =
            object : VerifyRefreshTokenGrantCommand {
                override val inputTypeToken = typeToken<VerifyRefreshTokenGrantArgs>()
                override val outputTypeToken = typeToken<VerifiedRefreshTokenGrant>()
                override val isEnabled = true

                override suspend fun execute(args: VerifyRefreshTokenGrantArgs): IdkResult<VerifiedRefreshTokenGrant, IdkError> =
                    Err(IdkError.fromString(code = "unsupported_grant_type", message = "Not supported in test"))
            }
        override val verifyClientCredentialsGrant =
            object : VerifyClientCredentialsGrantCommand {
                override val inputTypeToken = typeToken<VerifyClientCredentialsGrantArgs>()
                override val outputTypeToken = typeToken<VerifiedClientCredentialsGrant>()
                override val isEnabled = true

                override suspend fun execute(args: VerifyClientCredentialsGrantArgs): IdkResult<VerifiedClientCredentialsGrant, IdkError> = err()
            }
        override val verifyTokenExchangeGrant =
            object : VerifyTokenExchangeGrantCommand {
                override val inputTypeToken = typeToken<VerifyTokenExchangeGrantArgs>()
                override val outputTypeToken = typeToken<VerifiedTokenExchangeGrant>()
                override val isEnabled = true

                override suspend fun execute(args: VerifyTokenExchangeGrantArgs): IdkResult<VerifiedTokenExchangeGrant, IdkError> = err()
            }
        override val verifyPreAuthorizedCodeGrant =
            object : VerifyPreAuthorizedCodeGrantCommand {
                override val inputTypeToken = typeToken<VerifyPreAuthCodeArgs>()
                override val outputTypeToken = typeToken<VerifiedPreAuthCodeGrant>()
                override val isEnabled = true

                override suspend fun execute(args: VerifyPreAuthCodeArgs) = doVerifyPreAuthCode(args)
            }
        override val createAccessToken =
            object : CreateAccessTokenCommand {
                override val inputTypeToken = typeToken<CreateAccessTokenArgs>()
                override val outputTypeToken = typeToken<StringResult>()
                override val isEnabled = true

                override suspend fun execute(args: CreateAccessTokenArgs) = doCreateAccessToken(args)
            }
        override val createRefreshToken =
            object : CreateRefreshTokenCommand {
                override val inputTypeToken = typeToken<CreateRefreshTokenArgs>()
                override val outputTypeToken = typeToken<StringResult>()
                override val isEnabled = true

                override suspend fun execute(args: CreateRefreshTokenArgs) = doCreateRefreshToken(args)
            }
        override val createTokenResponse =
            object : CreateTokenResponseCommand {
                override val inputTypeToken = typeToken<CreateTokenResponseArgs>()
                override val outputTypeToken = typeToken<TokenResponse>()
                override val isEnabled = true

                override suspend fun execute(args: CreateTokenResponseArgs) = doCreateTokenResponse(args)
            }
        override val parseAuthorizationRequest =
            object : ParseAuthorizationRequestCommand {
                override val inputTypeToken = typeToken<ParseAuthorizationRequestArgs>()
                override val outputTypeToken = typeToken<AuthorizationRequestData>()
                override val isEnabled = true

                override suspend fun execute(args: ParseAuthorizationRequestArgs): IdkResult<AuthorizationRequestData, IdkError> = err()
            }
        override val verifyAuthorizationRequest =
            object : VerifyAuthorizationRequestCommand {
                override val inputTypeToken = typeToken<AuthorizationRequestData>()
                override val outputTypeToken = typeToken<VerifiedAuthorizationRequest>()
                override val isEnabled = true

                override suspend fun execute(args: AuthorizationRequestData): IdkResult<VerifiedAuthorizationRequest, IdkError> = err()
            }
        override val createAuthorizationSession =
            object : CreateAuthorizationSessionCommand {
                override val inputTypeToken = typeToken<VerifiedAuthorizationRequest>()
                override val outputTypeToken = typeToken<AuthorizationSession>()
                override val isEnabled = true

                override suspend fun execute(args: VerifiedAuthorizationRequest): IdkResult<AuthorizationSession, IdkError> = err()
            }
        override val createAuthorizationCode =
            object : CreateAuthorizationCodeCommand {
                override val inputTypeToken = typeToken<CreateAuthorizationCodeArgs>()
                override val outputTypeToken = typeToken<StringResult>()
                override val isEnabled = true

                override suspend fun execute(args: CreateAuthorizationCodeArgs): IdkResult<StringResult, IdkError> = err()
            }
        override val createAuthorizationResponse =
            object : CreateAuthorizationResponseCommand {
                override val inputTypeToken = typeToken<CreateAuthorizationResponseArgs>()
                override val outputTypeToken = typeToken<AuthorizationResponseData>()
                override val isEnabled = true

                override suspend fun execute(args: CreateAuthorizationResponseArgs): IdkResult<AuthorizationResponseData, IdkError> = err()
            }
        override val createAuthorizationErrorResponse =
            object : CreateAuthorizationErrorResponseCommand {
                override val inputTypeToken = typeToken<CreateAuthorizationErrorResponseArgs>()
                override val outputTypeToken = typeToken<AuthorizationErrorResponseData>()
                override val isEnabled = true

                override suspend fun execute(args: CreateAuthorizationErrorResponseArgs): IdkResult<AuthorizationErrorResponseData, IdkError> = err()
            }
        override val parsePushedAuthorizationRequest =
            object : ParsePushedAuthorizationRequestCommand {
                override val inputTypeToken = typeToken<ParsePushedAuthorizationRequestArgs>()
                override val outputTypeToken = typeToken<AuthorizationRequestData>()
                override val isEnabled = true

                override suspend fun execute(args: ParsePushedAuthorizationRequestArgs): IdkResult<AuthorizationRequestData, IdkError> = err()
            }
        override val verifyPushedAuthorizationRequest =
            object : VerifyPushedAuthorizationRequestCommand {
                override val inputTypeToken = typeToken<VerifyPushedAuthorizationRequestArgs>()
                override val outputTypeToken = typeToken<VerifiedAuthorizationRequest>()
                override val isEnabled = true

                override suspend fun execute(args: VerifyPushedAuthorizationRequestArgs): IdkResult<VerifiedAuthorizationRequest, IdkError> = err()
            }
        override val createRequestUri =
            object : CreateRequestUriCommand {
                override val inputTypeToken = typeToken<VerifiedAuthorizationRequest>()
                override val outputTypeToken = typeToken<RequestUriData>()
                override val isEnabled = true

                override suspend fun execute(args: VerifiedAuthorizationRequest): IdkResult<RequestUriData, IdkError> = err()
            }
        override val createPushedAuthorizationResponse =
            object : CreatePushedAuthorizationResponseCommand {
                override val inputTypeToken = typeToken<CreatePushedAuthorizationResponseArgs>()
                override val outputTypeToken = typeToken<PushedAuthorizationResponse>()
                override val isEnabled = true

                override suspend fun execute(args: CreatePushedAuthorizationResponseArgs): IdkResult<PushedAuthorizationResponse, IdkError> = err()
            }
        override val retrieveAuthorizationRequestByUri =
            object : RetrieveAuthorizationRequestByUriCommand {
                override val inputTypeToken = typeToken<RetrieveByRequestUriArgs>()
                override val outputTypeToken = typeToken<VerifiedAuthorizationRequest>()
                override val isEnabled = true

                override suspend fun execute(args: RetrieveByRequestUriArgs): IdkResult<VerifiedAuthorizationRequest, IdkError> = err()
            }
        override val parseIntrospectionRequest =
            object : ParseIntrospectionRequestCommand {
                override val inputTypeToken = typeToken<ParseIntrospectionRequestArgs>()
                override val outputTypeToken = typeToken<IntrospectionRequestData>()
                override val isEnabled = true

                override suspend fun execute(args: ParseIntrospectionRequestArgs): IdkResult<IntrospectionRequestData, IdkError> = err()
            }
        override val introspectToken =
            object : IntrospectTokenCommand {
                override val inputTypeToken = typeToken<IntrospectTokenArgs>()
                override val outputTypeToken = typeToken<TokenIntrospectionResponse>()
                override val isEnabled = true

                override suspend fun execute(args: IntrospectTokenArgs): IdkResult<TokenIntrospectionResponse, IdkError> = err()
            }
        override val parseRevocationRequest =
            object : ParseRevocationRequestCommand {
                override val inputTypeToken = typeToken<ParseRevocationRequestArgs>()
                override val outputTypeToken = typeToken<RevocationRequestData>()
                override val isEnabled = true

                override suspend fun execute(args: ParseRevocationRequestArgs): IdkResult<RevocationRequestData, IdkError> = err()
            }
        override val revokeToken =
            object : RevokeTokenCommand {
                override val inputTypeToken = typeToken<RevokeTokenArgs>()
                override val outputTypeToken = typeToken<Unit>()
                override val isEnabled = true

                override suspend fun execute(args: RevokeTokenArgs): IdkResult<Unit, IdkError> = err()
            }
        override val buildServerMetadata =
            object : BuildServerMetadataCommand {
                override val inputTypeToken = typeToken<BuildServerMetadataArgs>()
                override val outputTypeToken = typeToken<AuthorizationServerMetadata>()
                override val isEnabled = true

                override suspend fun execute(args: BuildServerMetadataArgs) = doBuildServerMetadata(args)
            }
        override val verifyClientAuthentication =
            object : VerifyClientAuthenticationCommand {
                override val inputTypeToken = typeToken<VerifyClientAuthenticationArgs>()
                override val outputTypeToken = typeToken<VerifiedClientAuthentication>()
                override val isEnabled = true

                override suspend fun execute(args: VerifyClientAuthenticationArgs) = doVerifyClientAuth(args)
            }
        override val createAttestationChallenge =
            object : CreateAttestationChallengeCommand {
                override val inputTypeToken = typeToken<CreateAttestationChallengeArgs>()
                override val outputTypeToken = typeToken<AttestationChallengeResponse>()
                override val isEnabled = true

                override suspend fun execute(args: CreateAttestationChallengeArgs): IdkResult<AttestationChallengeResponse, IdkError> = err()
            }
        override val createIdToken =
            object : CreateIdTokenCommand {
                override val inputTypeToken = typeToken<CreateIdTokenArgs>()
                override val outputTypeToken = typeToken<StringResult>()
                override val isEnabled = true

                override suspend fun execute(args: CreateIdTokenArgs): IdkResult<StringResult, IdkError> = Ok(StringResult("test-id-token-${++tokenCounter}"))
            }
        override val getUserInfo =
            object : GetUserInfoCommand {
                override val inputTypeToken = typeToken<GetUserInfoArgs>()
                override val outputTypeToken = typeToken<UserInfoResponse>()
                override val isEnabled = true

                override suspend fun execute(args: GetUserInfoArgs): IdkResult<UserInfoResponse, IdkError> = err()
            }
        override val getJwks =
            object : GetJwksCommand {
                override val inputTypeToken = typeToken<GetJwksArgs>()
                override val outputTypeToken = typeToken<JwksResult>()
                override val isEnabled = true

                override suspend fun execute(args: GetJwksArgs) = Ok(JwksResult(keys = emptyList()))
            }
    }
}

// ============================================================================
// Test fakes
// ============================================================================

private class TestOid4vciConfigProvider(
    private val config: OAuth2ServersConfig = OAuth2ServersConfig(),
) : OAuth2ServersConfigProvider {
    override fun getConfig(): OAuth2ServersConfig = config

    override fun getServer(id: String): OAuth2ServerInstanceConfig? = config.getServer(id)

    override fun getDefaultServer(): OAuth2ServerInstanceConfig = config.getDefaultServer()

    override fun resolveIssuer(
        serverId: String,
        tenantId: String,
    ): String {
        val server = config.getServer(serverId) ?: return "https://auth.example.com"
        return server.issuer ?: server.issuerTemplate?.replace("{tenant-id}", tenantId) ?: server.baseUrl
    }
}

private class NoOpOid4vciUserAuthProvider : UserAuthenticationProvider {
    override suspend fun getAuthenticatedUser(sessionId: String): IdkResult<AuthenticatedUser?, AuthenticationError> = Ok(null)

    override suspend fun initiateAuthentication(
        sessionId: String,
        returnUrl: String,
        hint: AuthenticationHint?,
    ): IdkResult<String, AuthenticationError> = Err(AuthenticationError.Generic(message = "Not implemented in test"))

    override suspend fun authenticateWithCredentials(credentials: UserCredentials): IdkResult<String?, AuthenticationError> = Err(AuthenticationError.Generic(message = "Not implemented in test"))

    override suspend fun logout(userId: String): IdkResult<Unit, AuthenticationError> = Ok(Unit)

    override suspend fun getUserInfo(userId: String): IdkResult<UserInfo, AuthenticationError> =
        Ok(UserInfo(userId = userId, username = "test-user", displayName = "Test User", email = "test@example.com", emailVerified = true))

    override suspend fun isAuthenticationMethodAvailable(method: AuthenticationMethod): IdkResult<Boolean, AuthenticationError> = Ok(method == AuthenticationMethod.PASSWORD)
}

private class NoOpOid4vciIaeInitialCommand : HandleIaeInitialRequestCommand {
    override val commandId: String get() = HandleIaeInitialRequestCommand.COMMAND_ID
    override val inputTypeToken get() = typeToken<HandleIaeInitialRequestArgs>()
    override val outputTypeToken get() = typeToken<IaeResult>()
    override val isEnabled: Boolean = true

    override suspend fun execute(args: HandleIaeInitialRequestArgs): IdkResult<IaeResult, IdkError> = Err(IdkError.fromString(code = "NOT_IMPLEMENTED", message = "IAE not available in test"))

    override suspend fun supports(args: Any): Boolean = args is HandleIaeInitialRequestArgs
}

private class NoOpOid4vciIaeFollowUpCommand : HandleIaeFollowUpCommand {
    override val commandId: String get() = HandleIaeFollowUpCommand.COMMAND_ID
    override val inputTypeToken get() = typeToken<HandleIaeFollowUpArgs>()
    override val outputTypeToken get() = typeToken<IaeResult>()
    override val isEnabled: Boolean = true

    override suspend fun execute(args: HandleIaeFollowUpArgs): IdkResult<IaeResult, IdkError> = Err(IdkError.fromString(code = "NOT_IMPLEMENTED", message = "IAE not available in test"))

    override suspend fun supports(args: Any): Boolean = args is HandleIaeFollowUpArgs
}

private class NoOpOid4vciPreAuthorizedCodeStorage : PreAuthorizedCodeStorage {
    override suspend fun storePreAuthorizedCode(
        code: String,
        data: PreAuthorizedCodeData,
    ) = Err(AuthorizationServerError.StorageError(operation = "noop", details = "Not available in E2E test"))

    override suspend fun consumePreAuthorizedCode(code: String) = Err(AuthorizationServerError.StorageError(operation = "noop", details = "Not available in E2E test"))

    override suspend fun isCodeUsed(code: String) = Err(AuthorizationServerError.StorageError(operation = "noop", details = "Not available in E2E test"))
}
