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

package com.sphereon.openid.oid4vci.holder

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.conf.AppConfigService
import com.sphereon.core.api.conf.ConfigLevel
import com.sphereon.core.api.conf.ConfigService
import com.sphereon.core.api.conf.PrincipalConfigService
import com.sphereon.core.api.conf.TenantConfigService
import com.sphereon.core.api.context.ContextConfig
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.encodeToBase64Url
import com.sphereon.core.api.error.IdkErrorType
import com.sphereon.core.api.log.AsyncLogService
import com.sphereon.core.api.log.LogMessage
import com.sphereon.core.api.log.LoggerConfig
import com.sphereon.core.api.log.SessionLogManager
import com.sphereon.core.api.log.SessionLogService
import com.sphereon.crypto.core.generic.DigestAlg
import com.sphereon.crypto.core.generic.hash
import com.sphereon.di.context.NoOpSessionContext
import com.sphereon.di.session.SessionContext
import com.sphereon.di.session.SessionContextManager
import com.sphereon.ktor.http.client.provider.HttpClientEngineType
import com.sphereon.ktor.http.client.provider.HttpClientFactory
import com.sphereon.ktor.http.client.provider.HttpClientOptions
import com.sphereon.oauth2.client.command.CreatePkceArgs
import com.sphereon.oauth2.client.command.CreatePkceCommand
import com.sphereon.oauth2.client.command.ExchangeTokenArgs
import com.sphereon.oauth2.client.command.ExchangeTokenCommand
import com.sphereon.oauth2.client.impl.authorization.CreateAuthorizationRequestUrlCommandImpl
import com.sphereon.oauth2.client.model.PkceData
import com.sphereon.oauth2.client.util.decodeQueryParameters
import com.sphereon.oauth2.common.command.ApplyClientAuthenticationArgs
import com.sphereon.oauth2.common.command.ApplyClientAuthenticationCommand
import com.sphereon.oauth2.common.model.PkceMethod
import com.sphereon.openid.oid4vci.holder.impl.BuildAuthorizationRequestCommandImpl
import com.sphereon.openid.oid4vci.holder.impl.ExchangeAuthorizationCodeCommandImpl
import com.sphereon.openid.oid4vci.holder.impl.ExchangeRefreshTokenCommandImpl
import com.sphereon.oauth2.common.model.ClientAssertion
import com.sphereon.oauth2.common.model.ClientAuthenticationConfig
import com.sphereon.oauth2.common.model.ClientAuthenticationMethod
import com.sphereon.oauth2.common.model.ClientAuthenticationResult
import com.sphereon.oauth2.common.model.TokenResponse
import io.ktor.client.HttpClient
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for the OID4VCI Authorization Code Flow commands.
 *
 * Tests authorization URL construction, PKCE generation, authorization_details JSON structure,
 * PAR mode URL construction, and token exchange form parameters.
 *
 * Pure unit tests — no real HTTP calls are made.
 */
class AuthCodeFlowTest {
    private val json = Json { ignoreUnknownKeys = true }

    // ============================================================================
    // Helpers
    // ============================================================================

    private fun makeExecution(): SessionExecution = TestAcfSessionExecution()

    private fun makePkceCommand(): CreatePkceCommand = TestPkceCommand()

    private fun makeNoOpHttpClientFactory(): HttpClientFactory = ThrowingHttpClientFactory()

    private fun makeBuildCmd(httpClientFactory: HttpClientFactory = makeNoOpHttpClientFactory()): BuildAuthorizationRequestCommandImpl {
        val execution = makeExecution()
        return BuildAuthorizationRequestCommandImpl(
            execution = execution,
            createAuthorizationRequestUrlCommand =
                CreateAuthorizationRequestUrlCommandImpl(
                    execution = execution,
                    createPkceCommand = makePkceCommand(),
                    applyClientAuthenticationCommand = TestApplyClientAuthenticationCommand(),
                    httpClientFactory = httpClientFactory,
                ),
            secureRandom = TestSecureRandom,
        )
    }

    // ============================================================================
    // BuildAuthorizationRequestCommand — URL construction
    // ============================================================================

    @Test
    fun authorizationUrlContainsAllRequiredParams() =
        runTest {
            val cmd = makeBuildCmd()
            val args =
                BuildAuthorizationRequestArgs(
                    authorizationEndpoint = "https://as.example.com/authorize",
                    clientId = "wallet-client",
                    redirectUri = "https://wallet.example.com/callback",
                    credentialConfigurationIds = listOf("UniversityDegreeCredential"),
                )

            val result = cmd.execute(args)

            assertTrue(result.isOk, "Expected Ok but got: ${result.errorOrNull()}")
            val url = result.value.authorizationUrl
            val params = extractQueryParams(url)

            assertEquals("code", params["response_type"])
            assertEquals("wallet-client", params["client_id"])
            assertEquals("https://wallet.example.com/callback", params["redirect_uri"])
            assertEquals("test-state-32", params["state"])
            assertNotNull(params["code_challenge"])
            assertNotNull(params["code_challenge_method"])
            assertNotNull(params["authorization_details"])
        }

    @Test
    fun authorizationUrlContainsOptionalScopeAndIssuerState() =
        runTest {
            val cmd = makeBuildCmd()
            val args =
                BuildAuthorizationRequestArgs(
                    authorizationEndpoint = "https://as.example.com/authorize",
                    clientId = "wallet-client",
                    redirectUri = "https://wallet.example.com/callback",
                    credentialConfigurationIds = listOf("PIDCredential"),
                    scope = "openid",
                    issuerState = "server-state-abc",
                )

            val result = cmd.execute(args)

            assertTrue(result.isOk)
            val params = extractQueryParams(result.value.authorizationUrl)
            assertEquals("openid", params["scope"])
            assertEquals("server-state-abc", params["issuer_state"])
        }

    @Test
    fun authorizationUrlExcludesNullOptionalParams() =
        runTest {
            val cmd = makeBuildCmd()
            val args =
                BuildAuthorizationRequestArgs(
                    authorizationEndpoint = "https://as.example.com/authorize",
                    clientId = "wallet-client",
                    redirectUri = "https://wallet.example.com/callback",
                    credentialConfigurationIds = listOf("SomeCredential"),
                )

            val result = cmd.execute(args)

            assertTrue(result.isOk)
            val params = extractQueryParams(result.value.authorizationUrl)
            assertFalse(params.containsKey("scope"), "scope should not be present when null")
            assertFalse(params.containsKey("issuer_state"), "issuer_state should not be present when null")
        }

    // ============================================================================
    // BuildAuthorizationRequestCommand — PKCE validation
    // ============================================================================

    @Test
    fun pkceCodeChallengeIsBase64urlSha256OfCodeVerifier() =
        runTest {
            val cmd = makeBuildCmd()
            val args =
                BuildAuthorizationRequestArgs(
                    authorizationEndpoint = "https://as.example.com/authorize",
                    clientId = "wallet-client",
                    redirectUri = "https://wallet.example.com/callback",
                    credentialConfigurationIds = listOf("UniversityDegreeCredential"),
                )

            val result = cmd.execute(args)
            assertTrue(result.isOk)

            val codeVerifier = result.value.codeVerifier
            val params = extractQueryParams(result.value.authorizationUrl)
            val challengeInUrl = params["code_challenge"]!!

            // Independently compute what the challenge should be
            val expectedChallenge = computeS256Challenge(codeVerifier)
            assertEquals(expectedChallenge, challengeInUrl, "code_challenge must be BASE64URL(SHA256(code_verifier))")
            assertEquals("S256", params["code_challenge_method"])
        }

    @Test
    fun codeVerifierAndStateAreReturnedInResult() =
        runTest {
            val cmd = makeBuildCmd()
            val args =
                BuildAuthorizationRequestArgs(
                    authorizationEndpoint = "https://as.example.com/authorize",
                    clientId = "wallet-client",
                    redirectUri = "https://wallet.example.com/callback",
                    credentialConfigurationIds = listOf("PIDCredential"),
                )

            val result = cmd.execute(args)
            assertTrue(result.isOk)

            val authResult = result.value
            assertTrue(authResult.codeVerifier.isNotBlank(), "codeVerifier must be non-blank")
            assertTrue(authResult.state.isNotBlank(), "state must be non-blank")
            // State embedded in URL must match returned state
            val params = extractQueryParams(authResult.authorizationUrl)
            assertEquals(authResult.state, params["state"])
        }

    // ============================================================================
    // BuildAuthorizationRequestCommand — authorization_details JSON structure
    // ============================================================================

    @Test
    fun authorizationDetailsMatchesSpec() =
        runTest {
            val cmd = makeBuildCmd()
            val args =
                BuildAuthorizationRequestArgs(
                    authorizationEndpoint = "https://as.example.com/authorize",
                    clientId = "wallet-client",
                    redirectUri = "https://wallet.example.com/callback",
                    credentialConfigurationIds = listOf("UniversityDegreeCredential", "DriverLicense"),
                )

            val result = cmd.execute(args)
            assertTrue(result.isOk)

            val params = extractQueryParams(result.value.authorizationUrl)
            val authDetailsRaw = params["authorization_details"]!!

            val authDetailsArray = json.decodeFromString(JsonArray.serializer(), authDetailsRaw)
            assertEquals(2, authDetailsArray.size, "One entry per credential_configuration_id")

            val first = authDetailsArray[0].jsonObject
            assertEquals("openid_credential", first["type"]?.jsonPrimitive?.content)
            assertEquals("UniversityDegreeCredential", first["credential_configuration_id"]?.jsonPrimitive?.content)

            val second = authDetailsArray[1].jsonObject
            assertEquals("openid_credential", second["type"]?.jsonPrimitive?.content)
            assertEquals("DriverLicense", second["credential_configuration_id"]?.jsonPrimitive?.content)
        }

    // ============================================================================
    // BuildAuthorizationRequestCommand — PAR mode
    // ============================================================================

    @Test
    fun parModeRequiresParEndpoint() =
        runTest {
            val cmd = makeBuildCmd()
            val args =
                BuildAuthorizationRequestArgs(
                    authorizationEndpoint = "https://as.example.com/authorize",
                    clientId = "wallet-client",
                    redirectUri = "https://wallet.example.com/callback",
                    credentialConfigurationIds = listOf("PIDCredential"),
                    usePar = true,
                    parEndpoint = null, // intentionally missing
                )

            val result = cmd.execute(args)

            assertTrue(result.isErr, "Expected Err when usePar=true but parEndpoint=null")
        }

    @Test
    fun parModeWithNetworkErrorReturnsErr() =
        runTest {
            // ThrowingHttpClientFactory simulates a network failure at the PAR endpoint.
            // We verify the command propagates the error rather than swallowing it.
            val cmd = makeBuildCmd(httpClientFactory = makeNoOpHttpClientFactory())
            val args =
                BuildAuthorizationRequestArgs(
                    authorizationEndpoint = "https://as.example.com/authorize",
                    clientId = "wallet-client",
                    redirectUri = "https://wallet.example.com/callback",
                    credentialConfigurationIds = listOf("PIDCredential"),
                    usePar = true,
                    parEndpoint = "https://as.example.com/par",
                )

            val result = cmd.execute(args)
            assertTrue(result.isErr, "Expected Err when HTTP client creation fails")
        }

    // ============================================================================
    // ExchangeAuthorizationCodeCommand — args and grant type
    // ============================================================================

    @Test
    fun exchangeArgsWithAllOptionalFields() {
        val args =
            ExchangeAuthorizationCodeArgs(
                tokenEndpoint = "https://as.example.com/token",
                code = "SplxlOBeZQQYbYS6WxSbIA",
                codeVerifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk",
                redirectUri = "https://wallet.example.com/callback",
                clientId = "wallet-client",
                clientAuthentication =
                    ClientAuthenticationConfig.PrivateKeyJwt(
                        ClientAssertion(
                            clientId = "wallet-client",
                            assertionType = "urn:ietf:params:oauth:client-assertion-type:jwt-bearer",
                            assertion = "jwt.assertion",
                        ),
                    ),
            )

        assertEquals("SplxlOBeZQQYbYS6WxSbIA", args.code)
        assertEquals("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk", args.codeVerifier)
        assertEquals("wallet-client", args.clientId)
        val auth = args.clientAuthentication as ClientAuthenticationConfig.PrivateKeyJwt
        assertEquals("urn:ietf:params:oauth:client-assertion-type:jwt-bearer", auth.assertion.assertionType)
        assertEquals("jwt.assertion", auth.assertion.assertion)
    }

    @Test
    fun exchangeArgsClientIdIsOptional() {
        val args =
            ExchangeAuthorizationCodeArgs(
                tokenEndpoint = "https://as.example.com/token",
                code = "SplxlOBeZQQYbYS6WxSbIA",
                codeVerifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk",
                redirectUri = "https://wallet.example.com/callback",
            )

        assertNull(args.clientId)
    }

    @Test
    fun exchangeArgsClientAuthenticationIsOptional() {
        val args =
            ExchangeAuthorizationCodeArgs(
                tokenEndpoint = "https://as.example.com/token",
                code = "SplxlOBeZQQYbYS6WxSbIA",
                codeVerifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk",
                redirectUri = "https://wallet.example.com/callback",
            )

        assertNull(args.clientAuthentication)
    }

    @Test
    fun exchangeGrantTypeIsAuthorizationCode() {
        assertEquals("authorization_code", ExchangeAuthorizationCodeCommandImpl.AUTHORIZATION_CODE_GRANT_TYPE)
    }

    @Test
    fun refreshGrantIsOwnedByOid4vciHolderAndDelegatesGenericTokenExchange() =
        runTest {
            val authentication =
                ClientAuthenticationConfig.PrivateKeyJwt(
                    ClientAssertion(
                        clientId = "wallet-client",
                        assertionType = "urn:ietf:params:oauth:client-assertion-type:jwt-bearer",
                        assertion = "signed-client-assertion",
                    ),
                )
            val applyClientAuthentication =
                RecordingApplyClientAuthenticationCommand(
                    result =
                        ClientAuthenticationResult(
                            headers = mapOf("X-Authenticated-Client" to "wallet-client"),
                            bodyParameters =
                                mapOf(
                                    "client_id" to "authenticated-wallet-client",
                                    "client_assertion_type" to "urn:ietf:params:oauth:client-assertion-type:jwt-bearer",
                                    "client_assertion" to "signed-client-assertion",
                                    "wallet_provider" to "provider-a",
                                ),
                        ),
                )
            val exchangeToken =
                RecordingExchangeTokenCommand(
                    response =
                        TokenResponse(
                            accessToken = "new-access-token",
                            tokenType = "DPoP",
                            expiresIn = 300,
                            refreshToken = "rotated-refresh-token",
                            cNonce = "new-c-nonce",
                            cNonceExpiresIn = 60,
                            authorizationDetails = JsonArray(listOf(JsonPrimitive("credential-detail"))),
                            additionalParameters = mapOf("issuer_state" to JsonPrimitive("state-2")),
                        ),
                )
            val command =
                ExchangeRefreshTokenCommandImpl(
                    execution = makeExecution(),
                    applyClientAuthentication = applyClientAuthentication,
                    exchangeToken = exchangeToken,
                )

            val result =
                command.execute(
                    ExchangeRefreshTokenArgs(
                        tokenEndpoint = "https://issuer.example/token",
                        refreshToken = "current-refresh-token",
                        clientId = "untrusted-fallback-client",
                        dpopProofJwt = "dpop-proof",
                        clientAttestationJwt = "client-attestation",
                        clientAttestationPopJwt = "client-attestation-pop",
                        clientAuthentication = authentication,
                    ),
                )

            assertTrue(result.isOk, "Expected Ok but got: ${result.errorOrNull()}")
            assertEquals(authentication, applyClientAuthentication.receivedArgs?.config)
            assertEquals("https://issuer.example/token", applyClientAuthentication.receivedArgs?.tokenEndpoint)

            val exchangeArgs = assertNotNull(exchangeToken.receivedArgs)
            assertEquals("https://issuer.example/token", exchangeArgs.tokenEndpoint)
            with(exchangeArgs.request) {
                assertEquals("refresh_token", grantType)
                assertEquals("current-refresh-token", refreshToken)
                assertEquals("authenticated-wallet-client", clientId)
                assertEquals("signed-client-assertion", clientAssertion)
                assertEquals("dpop-proof", dpop)
                assertEquals(ClientAuthenticationMethod.PRIVATE_KEY_JWT, tokenEndpointAuthMethod)
                assertEquals(JsonPrimitive("provider-a"), additionalParameters["wallet_provider"])
                assertEquals("client-attestation", additionalHeaders["OAuth-Client-Attestation"])
                assertEquals("client-attestation-pop", additionalHeaders["OAuth-Client-Attestation-PoP"])
                assertEquals("wallet-client", additionalHeaders["X-Authenticated-Client"])
            }

            with(result.value) {
                assertEquals("new-access-token", accessToken)
                assertEquals("DPoP", tokenType)
                assertEquals("rotated-refresh-token", refreshToken)
                assertEquals("new-c-nonce", cNonce)
                assertEquals(listOf(JsonPrimitive("credential-detail")), authorizationDetails)
                assertEquals(JsonPrimitive("state-2"), additionalParameters["issuer_state"])
            }
        }

    @Test
    fun exchangeTokenResponseDeserializesCorrectly() {
        val raw =
            """
            {
              "access_token": "eyJhbGciOiJSUzI1NiJ9",
              "token_type": "Bearer",
              "expires_in": 3600,
              "c_nonce": "tZignsnFbp",
              "authorization_details": [
                {"type": "openid_credential", "credential_configuration_id": "UniversityDegreeCredential"}
              ]
            }
            """.trimIndent()

        val tokenResponse = json.decodeFromString(TokenResponseWithContext.serializer(), raw)

        assertEquals("eyJhbGciOiJSUzI1NiJ9", tokenResponse.accessToken)
        assertEquals("Bearer", tokenResponse.tokenType)
        assertEquals(3600, tokenResponse.expiresIn)
        assertEquals("tZignsnFbp", tokenResponse.cNonce)
        assertNotNull(tokenResponse.authorizationDetails)
        assertEquals(1, tokenResponse.authorizationDetails!!.size)
    }

    // ============================================================================
    // Helpers
    // ============================================================================

    /** Extracts query parameters from a URL string. */
    private fun extractQueryParams(url: String): Map<String, String> {
        val queryStart = url.indexOf('?')
        if (queryStart == -1) return emptyMap()
        val query = url.substring(queryStart + 1)
        return decodeQueryParameters(query)
    }

    /** Computes BASE64URL(SHA256(verifier)) — the expected S256 code_challenge. */
    private fun computeS256Challenge(verifier: String): String {
        val hashBytes = hash(verifier.encodeToByteArray(), DigestAlg.SHA256)
        return hashBytes.encodeToBase64Url()
    }
}

// ============================================================================
// Test support types
// ============================================================================

private class AcfNoOpSessionLogService(
    override val sessionContext: SessionContext = NoOpSessionContext,
) : SessionLogService {
    override val id: String = "test-auth-code-flow-log"
    override val isEnabled: Boolean = false
    override val scope = com.sphereon.core.api.context.IdkScope.SESSION
    override val logManager: SessionLogManager
        get() = throw NotImplementedError("Not needed for unit tests")

    override suspend fun setConfig(config: LoggerConfig): com.sphereon.core.api.log.LogService = this

    override fun executeAsync(message: LogMessage): IdkResult<Unit, IdkErrorType> = Ok(Unit)

    override fun toAsync(): AsyncLogService = throw NotImplementedError("Not needed for unit tests")
}

private class AcfNoOpContextConfig : ContextConfig {
    override val app: AppConfigService get() = throw NotImplementedError("Not needed for unit tests")
    override val tenant: TenantConfigService get() = throw NotImplementedError("Not needed for unit tests")
    override val principal: PrincipalConfigService get() = throw NotImplementedError("Not needed for unit tests")

    override fun conf(level: ConfigLevel): ConfigService = throw NotImplementedError("Not needed for unit tests")
}

private class TestAcfSessionExecution(
    override val sessionContext: SessionContext = NoOpSessionContext,
) : SessionExecution {
    override val sessionContextManager: SessionContextManager
        get() = throw NotImplementedError("Not needed for unit tests")
    override val log: SessionLogService = AcfNoOpSessionLogService(sessionContext)
    override val conf: ContextConfig = AcfNoOpContextConfig()
}

private object TestSecureRandom : com.sphereon.core.api.random.SecureRandom {
    override val commands: com.sphereon.core.api.random.SecureRandom.Commands
        get() = throw NotImplementedError("Not needed for unit tests")

    override suspend fun generateToken(args: com.sphereon.core.api.random.GenerateTokenArgs): IdkResult<com.sphereon.core.api.service.StringResult, com.sphereon.core.api.error.IdkError> =
        Ok(com.sphereon.core.api.service.StringResult("test-state-${args.lengthBytes}"))

    override suspend fun nextBytes(args: com.sphereon.core.api.random.NextBytesArgs): IdkResult<com.sphereon.core.api.service.ByteArrayResult, com.sphereon.core.api.error.IdkError> =
        Ok(com.sphereon.core.api.service.ByteArrayResult(ByteArray(args.length) { index -> index.toByte() }))
}

/**
 * HttpClientFactory that always throws — used for tests that do not trigger HTTP calls.
 */
private class ThrowingHttpClientFactory : HttpClientFactory {
    override fun createClient(options: HttpClientOptions): HttpClient = throw UnsupportedOperationException("ThrowingHttpClientFactory does not create real HTTP clients")

    override fun isSupportedOptions(options: HttpClientOptions): Boolean = false

    override fun getEngineTypesSupported(): List<HttpClientEngineType> = emptyList()

    override fun getEngineTypeDefault(): HttpClientEngineType = HttpClientEngineType.CIO
}

/**
 * Minimal PKCE command for tests — generates S256 PKCE pairs using the same
 * SHA-256 + base64url algorithm as the real CreatePkceCommandImpl, but without
 * needing the oauth2-client-impl dependency in the test classpath.
 */
private class TestPkceCommand : CreatePkceCommand {
    override val commandId: String get() = CreatePkceCommand.COMMAND_ID
    override val id: String get() = commandId
    override val isEnabled: Boolean = true
    override val inputTypeToken: com.sphereon.core.api.binary.TypeToken<CreatePkceArgs> =
        com.sphereon.core.api.binary
            .typeToken()
    override val outputTypeToken: com.sphereon.core.api.binary.TypeToken<PkceData> =
        com.sphereon.core.api.binary
            .typeToken()

    override suspend fun execute(args: CreatePkceArgs): com.sphereon.core.api.IdkResult<PkceData, com.sphereon.core.api.error.IdkError> {
        val verifier = args.codeVerifier ?: generateVerifier()
        val hashBytes = hash(verifier.encodeToByteArray(), DigestAlg.SHA256)
        val challenge = hashBytes.encodeToBase64Url()
        return Ok(PkceData(codeVerifier = verifier, codeChallenge = challenge, codeChallengeMethod = PkceMethod.S256))
    }

    override suspend fun supports(args: Any): Boolean = args is CreatePkceArgs

    private fun generateVerifier(): String = Random.Default.nextBytes(64).encodeToBase64Url()
}

private class TestApplyClientAuthenticationCommand : ApplyClientAuthenticationCommand {
    override val commandId: String get() = ApplyClientAuthenticationCommand.COMMAND_ID
    override val id: String get() = commandId
    override val isEnabled: Boolean = true
    override val inputTypeToken: com.sphereon.core.api.binary.TypeToken<ApplyClientAuthenticationArgs> =
        com.sphereon.core.api.binary.typeToken()
    override val outputTypeToken: com.sphereon.core.api.binary.TypeToken<ClientAuthenticationResult> =
        com.sphereon.core.api.binary.typeToken()

    override suspend fun execute(args: ApplyClientAuthenticationArgs): IdkResult<ClientAuthenticationResult, com.sphereon.core.api.error.IdkError> =
        Ok(ClientAuthenticationResult(headers = emptyMap(), bodyParameters = emptyMap()))

    override suspend fun supports(args: Any): Boolean = args is ApplyClientAuthenticationArgs
}

private class RecordingApplyClientAuthenticationCommand(
    private val result: ClientAuthenticationResult,
) : ApplyClientAuthenticationCommand {
    var receivedArgs: ApplyClientAuthenticationArgs? = null
        private set

    override val commandId: String get() = ApplyClientAuthenticationCommand.COMMAND_ID
    override val id: String get() = commandId
    override val isEnabled: Boolean = true
    override val inputTypeToken: com.sphereon.core.api.binary.TypeToken<ApplyClientAuthenticationArgs> =
        com.sphereon.core.api.binary.typeToken()
    override val outputTypeToken: com.sphereon.core.api.binary.TypeToken<ClientAuthenticationResult> =
        com.sphereon.core.api.binary.typeToken()

    override suspend fun execute(args: ApplyClientAuthenticationArgs): IdkResult<ClientAuthenticationResult, com.sphereon.core.api.error.IdkError> {
        receivedArgs = args
        return Ok(result)
    }

    override suspend fun supports(args: Any): Boolean = args is ApplyClientAuthenticationArgs
}

private class RecordingExchangeTokenCommand(
    private val response: TokenResponse,
) : ExchangeTokenCommand {
    var receivedArgs: ExchangeTokenArgs? = null
        private set

    override val commandId: String get() = ExchangeTokenCommand.COMMAND_ID
    override val id: String get() = commandId
    override val isEnabled: Boolean = true
    override val inputTypeToken: com.sphereon.core.api.binary.TypeToken<ExchangeTokenArgs> =
        com.sphereon.core.api.binary.typeToken()
    override val outputTypeToken: com.sphereon.core.api.binary.TypeToken<TokenResponse> =
        com.sphereon.core.api.binary.typeToken()

    override suspend fun execute(args: ExchangeTokenArgs): IdkResult<TokenResponse, com.sphereon.core.api.error.IdkError> {
        receivedArgs = args
        return Ok(response)
    }

    override suspend fun supports(args: Any): Boolean = args is ExchangeTokenArgs
}
