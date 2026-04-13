package com.sphereon.oauth2.server.authorization.impl.http

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.http.GenericHttpRequest
import com.sphereon.oauth2.common.config.FeaturePolicy
import com.sphereon.oauth2.common.config.OAuth2ServerInstanceConfig
import com.sphereon.oauth2.common.config.OAuth2ServersConfig
import com.sphereon.oauth2.common.config.OAuth2ServersConfigProvider
import com.sphereon.oauth2.common.model.AuthorizationServerMetadata
import com.sphereon.oauth2.server.authorization.command.AuthorizationRequestData
import com.sphereon.oauth2.server.authorization.command.BuildServerMetadataArgs
import com.sphereon.oauth2.server.authorization.command.BuildServerMetadataCommand
import com.sphereon.oauth2.server.authorization.command.CreateAccessTokenArgs
import com.sphereon.oauth2.server.authorization.command.CreateAttestationChallengeArgs
import com.sphereon.oauth2.server.authorization.command.CreateAuthorizationCodeArgs
import com.sphereon.oauth2.server.authorization.command.CreateAuthorizationErrorResponseArgs
import com.sphereon.oauth2.server.authorization.command.CreateAuthorizationResponseArgs
import com.sphereon.oauth2.server.authorization.command.CreateIdTokenArgs
import com.sphereon.oauth2.server.authorization.command.CreatePushedAuthorizationResponseArgs
import com.sphereon.oauth2.server.authorization.command.CreateRefreshTokenArgs
import com.sphereon.oauth2.server.authorization.command.CreateTokenResponseArgs
import com.sphereon.oauth2.server.authorization.command.GetJwksArgs
import com.sphereon.oauth2.server.authorization.command.GetJwksCommand
import com.sphereon.oauth2.server.authorization.command.GetUserInfoArgs
import com.sphereon.oauth2.server.authorization.command.HandleIaeFollowUpArgs
import com.sphereon.oauth2.server.authorization.command.HandleIaeFollowUpCommand
import com.sphereon.oauth2.server.authorization.command.HandleIaeInitialRequestArgs
import com.sphereon.oauth2.server.authorization.command.HandleIaeInitialRequestCommand
import com.sphereon.oauth2.server.authorization.command.IaeResult
import com.sphereon.oauth2.server.authorization.command.IntrospectTokenArgs
import com.sphereon.oauth2.server.authorization.command.JwksResult
import com.sphereon.oauth2.server.authorization.command.ParseAuthorizationRequestArgs
import com.sphereon.oauth2.server.authorization.command.ParseIntrospectionRequestArgs
import com.sphereon.oauth2.server.authorization.command.ParsePushedAuthorizationRequestArgs
import com.sphereon.oauth2.server.authorization.command.ParseRevocationRequestArgs
import com.sphereon.oauth2.server.authorization.command.ParseTokenRequestArgs
import com.sphereon.oauth2.server.authorization.command.RevokeTokenArgs
import com.sphereon.oauth2.server.authorization.command.VerifiedAuthorizationRequest
import com.sphereon.oauth2.server.authorization.command.VerifyAuthorizationCodeGrantArgs
import com.sphereon.oauth2.server.authorization.command.VerifyClientAuthenticationArgs
import com.sphereon.oauth2.server.authorization.command.VerifyClientCredentialsGrantArgs
import com.sphereon.oauth2.server.authorization.command.VerifyPreAuthCodeArgs
import com.sphereon.oauth2.server.authorization.command.VerifyPushedAuthorizationRequestArgs
import com.sphereon.oauth2.server.authorization.command.VerifyRefreshTokenGrantArgs
import com.sphereon.oauth2.server.authorization.command.VerifyTokenExchangeGrantArgs
import com.sphereon.oauth2.server.authorization.error.AuthorizationServerError
import com.sphereon.oauth2.server.authorization.impl.oidc.OidcScopeClaimsMapperImpl
import com.sphereon.oauth2.server.authorization.model.AuthorizationSession
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
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

// ============================================================================
// Local test fakes (avoid cross-module test source dependencies)
// ============================================================================

private class FakeOAuth2ServersConfigProvider(
    private val config: OAuth2ServersConfig = OAuth2ServersConfig(),
) : OAuth2ServersConfigProvider {
    override fun getConfig(): OAuth2ServersConfig = config

    override fun getServer(id: String): OAuth2ServerInstanceConfig? = config.getServer(id)

    override fun getDefaultServer(): OAuth2ServerInstanceConfig = config.getDefaultServer()

    override fun resolveIssuer(
        serverId: String,
        tenantId: String,
    ): String {
        val server = config.getServer(serverId) ?: return "http://localhost:8080"
        return server.issuer ?: server.issuerTemplate?.replace("{tenant-id}", tenantId) ?: server.baseUrl
    }
}

private class FakeUserAuthenticationProvider : UserAuthenticationProvider {
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

private class NoOpIaeInitialCommand : HandleIaeInitialRequestCommand {
    override val commandId: String get() = HandleIaeInitialRequestCommand.COMMAND_ID
    override val inputTypeToken get() =
        com.sphereon.core.api.binary
            .typeToken<HandleIaeInitialRequestArgs>()
    override val outputTypeToken get() =
        com.sphereon.core.api.binary
            .typeToken<IaeResult>()
    override val isEnabled: Boolean = true

    override suspend fun execute(args: HandleIaeInitialRequestArgs): IdkResult<IaeResult, IdkError> = Err(IdkError.fromString(code = "NOT_IMPLEMENTED", message = "IAE not available in test"))

    override suspend fun supports(args: Any): Boolean = args is HandleIaeInitialRequestArgs
}

private class NoOpIaeFollowUpCommand : HandleIaeFollowUpCommand {
    override val commandId: String get() = HandleIaeFollowUpCommand.COMMAND_ID
    override val inputTypeToken get() =
        com.sphereon.core.api.binary
            .typeToken<HandleIaeFollowUpArgs>()
    override val outputTypeToken get() =
        com.sphereon.core.api.binary
            .typeToken<IaeResult>()
    override val isEnabled: Boolean = true

    override suspend fun execute(args: HandleIaeFollowUpArgs): IdkResult<IaeResult, IdkError> = Err(IdkError.fromString(code = "NOT_IMPLEMENTED", message = "IAE not available in test"))

    override suspend fun supports(args: Any): Boolean = args is HandleIaeFollowUpArgs
}

private class NoOpPreAuthorizedCodeStorage : PreAuthorizedCodeStorage {
    override suspend fun storePreAuthorizedCode(
        code: String,
        data: PreAuthorizedCodeData,
    ) = Err(AuthorizationServerError.StorageError(operation = "noop", details = "Not available in routing test"))

    override suspend fun consumePreAuthorizedCode(code: String) = Err(AuthorizationServerError.StorageError(operation = "noop", details = "Not available in routing test"))

    override suspend fun isCodeUsed(code: String) = Err(AuthorizationServerError.StorageError(operation = "noop", details = "Not available in routing test"))
}

// ============================================================================
// Test helper to build adapter with fakes
// ============================================================================

/**
 * Builds an OAuth2HttpAdapter using a real AuthorizationServerService from the
 * DI graph and lightweight fakes for config, auth, and IAE commands.
 *
 * NOTE: These tests require the impl module's DI graph to resolve
 * AuthorizationServerService. If this is unavailable (cross-module test source
 * visibility), these tests should be moved to the impl module or converted to
 * pure-fake tests. For now we construct the adapter with fakes for the parts
 * we can't resolve here.
 */
private fun buildAdapter(configProvider: OAuth2ServersConfigProvider): OAuth2HttpAdapter =
    OAuth2HttpAdapter(
        // AuthorizationServerService cannot be resolved without the full DI graph.
        // These tests exercise HTTP routing and config-gated feature checks, not
        // deep business logic. We pass a minimal no-op service stub.
        authorizationServerService = NoOpAuthorizationServerService(),
        configProvider = configProvider,
        userAuthProvider = FakeUserAuthenticationProvider(),
        scopeClaimsMapper = OidcScopeClaimsMapperImpl(),
        handleIaeInitialRequestCommand = NoOpIaeInitialCommand(),
        handleIaeFollowUpCommand = NoOpIaeFollowUpCommand(),
        preAuthorizedCodeStorage = NoOpPreAuthorizedCodeStorage(),
    )

/**
 * Minimal stub — the tests below only exercise discovery, userinfo gating,
 * and JWKS routing, none of which require a real AuthorizationServerService.
 * All methods throw; tests that exercise these paths will fail explicitly.
 */
private class NoOpAuthorizationServerService : AuthorizationServerService {
    private fun err(): Nothing = throw NotImplementedError("Not available in routing test")

    override suspend fun parseTokenRequest(args: ParseTokenRequestArgs) = err()

    override suspend fun verifyAuthorizationCodeGrant(args: VerifyAuthorizationCodeGrantArgs) = err()

    override suspend fun verifyRefreshTokenGrant(args: VerifyRefreshTokenGrantArgs) = err()

    override suspend fun verifyClientCredentialsGrant(args: VerifyClientCredentialsGrantArgs) = err()

    override suspend fun verifyTokenExchangeGrant(args: VerifyTokenExchangeGrantArgs) = err()

    override suspend fun verifyPreAuthorizedCodeGrant(args: VerifyPreAuthCodeArgs) = err()

    override suspend fun createAccessToken(args: CreateAccessTokenArgs) = err()

    override suspend fun createRefreshToken(args: CreateRefreshTokenArgs) = err()

    override suspend fun createTokenResponse(args: CreateTokenResponseArgs) = err()

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

    override suspend fun buildServerMetadata(args: BuildServerMetadataArgs): IdkResult<AuthorizationServerMetadata, IdkError> {
        val config = FakeOAuth2ServersConfigProvider().getDefaultServer()
        return Ok(
            AuthorizationServerMetadata(
                issuer = config.baseUrl,
                tokenEndpoint = "${config.baseUrl}/token",
                authorizationEndpoint = "${config.baseUrl}/authorize",
                jwksUri = "${config.baseUrl}/.well-known/jwks.json",
            ),
        )
    }

    override suspend fun verifyClientAuthentication(args: VerifyClientAuthenticationArgs) = err()

    override suspend fun createAttestationChallenge(args: CreateAttestationChallengeArgs) = err()

    override suspend fun createIdToken(args: CreateIdTokenArgs) = err()

    override suspend fun getUserInfo(args: GetUserInfoArgs) = err()

    override suspend fun getJwks(args: GetJwksArgs): IdkResult<JwksResult, IdkError> = Ok(JwksResult(keys = emptyList()))

    override val commands: AuthorizationServerService.Commands =
        object : AuthorizationServerService.Commands {
            override val parseTokenRequest get() = err()
            override val verifyAuthorizationCodeGrant get() = err()
            override val verifyRefreshTokenGrant get() = err()
            override val verifyClientCredentialsGrant get() = err()
            override val verifyTokenExchangeGrant get() = err()
            override val verifyPreAuthorizedCodeGrant get() = err()
            override val createAccessToken get() = err()
            override val createRefreshToken get() = err()
            override val createTokenResponse get() = err()
            override val parseAuthorizationRequest get() = err()
            override val verifyAuthorizationRequest get() = err()
            override val createAuthorizationSession get() = err()
            override val createAuthorizationCode get() = err()
            override val createAuthorizationResponse get() = err()
            override val createAuthorizationErrorResponse get() = err()
            override val parsePushedAuthorizationRequest get() = err()
            override val verifyPushedAuthorizationRequest get() = err()
            override val createRequestUri get() = err()
            override val createPushedAuthorizationResponse get() = err()
            override val retrieveAuthorizationRequestByUri get() = err()
            override val parseIntrospectionRequest get() = err()
            override val introspectToken get() = err()
            override val parseRevocationRequest get() = err()
            override val revokeToken get() = err()
            override val buildServerMetadata: BuildServerMetadataCommand =
                object : BuildServerMetadataCommand {
                    override val commandId: String get() = BuildServerMetadataCommand.COMMAND_ID
                    override val inputTypeToken get() =
                        com.sphereon.core.api.binary
                            .typeToken<BuildServerMetadataArgs>()
                    override val outputTypeToken get() =
                        com.sphereon.core.api.binary
                            .typeToken<AuthorizationServerMetadata>()
                    override val isEnabled: Boolean = true

                    override suspend fun execute(args: BuildServerMetadataArgs): IdkResult<AuthorizationServerMetadata, IdkError> {
                        val config = FakeOAuth2ServersConfigProvider().getDefaultServer()
                        return Ok(
                            AuthorizationServerMetadata(
                                issuer = config.baseUrl,
                                tokenEndpoint = "${config.baseUrl}/token",
                                authorizationEndpoint = "${config.baseUrl}/authorize",
                                jwksUri = "${config.baseUrl}/.well-known/jwks.json",
                            ),
                        )
                    }

                    override suspend fun supports(args: Any): Boolean = args is BuildServerMetadataArgs
                }
            override val verifyClientAuthentication get() = err()
            override val createAttestationChallenge get() = err()
            override val createIdToken get() = err()
            override val getUserInfo get() = err()
            override val getJwks: GetJwksCommand =
                object : GetJwksCommand {
                    override val commandId: String get() = GetJwksCommand.COMMAND_ID
                    override val inputTypeToken get() =
                        com.sphereon.core.api.binary
                            .typeToken<GetJwksArgs>()
                    override val outputTypeToken get() =
                        com.sphereon.core.api.binary
                            .typeToken<JwksResult>()
                    override val isEnabled: Boolean = true

                    override suspend fun execute(args: GetJwksArgs): IdkResult<JwksResult, IdkError> = Ok(JwksResult(keys = emptyList()))

                    override suspend fun supports(args: Any): Boolean = args is GetJwksArgs
                }
        }
}

/**
 * Tests for OIDC-specific HTTP endpoints:
 * - GET /.well-known/openid-configuration
 * - GET /userinfo
 * - GET /.well-known/jwks.json
 */
class OAuth2HttpAdapterOidcTest {
    private val json = Json { ignoreUnknownKeys = true }

    // ========================================================================
    // OIDC Discovery
    // ========================================================================

    @Test
    fun oidcDiscoveryReturns200WhenEnabled() =
        runTest {
            val configProvider =
                FakeOAuth2ServersConfigProvider(
                    OAuth2ServersConfig(
                        servers =
                            mapOf(
                                "default" to
                                    OAuth2ServerInstanceConfig(
                                        baseUrl = "https://auth.example.com",
                                        oidc = FeaturePolicy.SUPPORTED,
                                        introspection = FeaturePolicy.SUPPORTED,
                                    ),
                            ),
                    ),
                )
            val adapter = buildAdapter(configProvider)

            val request =
                GenericHttpRequest(
                    method = "GET",
                    path = "/.well-known/openid-configuration",
                    headers = mapOf("host" to "auth.example.com"),
                )

            val response = adapter.handleRequest(request)

            assertEquals(200, response.statusCode)
            val body = json.parseToJsonElement(response.body!!)
            assertNotNull(body.jsonObject["issuer"])
            assertNotNull(body.jsonObject["token_endpoint"])
        }

    @Test
    fun oidcDiscoveryReturns404WhenDisabled() =
        runTest {
            val configProvider =
                FakeOAuth2ServersConfigProvider(
                    OAuth2ServersConfig(
                        servers =
                            mapOf(
                                "default" to
                                    OAuth2ServerInstanceConfig(
                                        baseUrl = "https://auth.example.com",
                                        oidc = FeaturePolicy.DISABLED,
                                    ),
                            ),
                    ),
                )
            val adapter = buildAdapter(configProvider)

            val request =
                GenericHttpRequest(
                    method = "GET",
                    path = "/.well-known/openid-configuration",
                    headers = mapOf("host" to "auth.example.com"),
                )

            val response = adapter.handleRequest(request)

            assertEquals(404, response.statusCode)
            val body = json.parseToJsonElement(response.body!!)
            assertEquals("not_found", body.jsonObject["error"]?.jsonPrimitive?.content)
        }

    // ========================================================================
    // UserInfo Endpoint
    // ========================================================================

    @Test
    fun userinfoReturns404WhenOidcDisabled() =
        runTest {
            val configProvider =
                FakeOAuth2ServersConfigProvider(
                    OAuth2ServersConfig(
                        servers =
                            mapOf(
                                "default" to
                                    OAuth2ServerInstanceConfig(
                                        baseUrl = "https://auth.example.com",
                                        oidc = FeaturePolicy.DISABLED,
                                    ),
                            ),
                    ),
                )
            val adapter = buildAdapter(configProvider)

            val request =
                GenericHttpRequest(
                    method = "GET",
                    path = "/userinfo",
                    headers =
                        mapOf(
                            "host" to "auth.example.com",
                            "authorization" to "Bearer some-token",
                        ),
                )

            val response = adapter.handleRequest(request)
            assertEquals(404, response.statusCode)
        }

    @Test
    fun userinfoReturns401WithoutBearerToken() =
        runTest {
            val configProvider =
                FakeOAuth2ServersConfigProvider(
                    OAuth2ServersConfig(
                        servers =
                            mapOf(
                                "default" to
                                    OAuth2ServerInstanceConfig(
                                        baseUrl = "https://auth.example.com",
                                        oidc = FeaturePolicy.SUPPORTED,
                                    ),
                            ),
                    ),
                )
            val adapter = buildAdapter(configProvider)

            val request =
                GenericHttpRequest(
                    method = "GET",
                    path = "/userinfo",
                    headers = mapOf("host" to "auth.example.com"),
                )

            val response = adapter.handleRequest(request)
            assertEquals(401, response.statusCode)
            val body = json.parseToJsonElement(response.body!!)
            assertEquals("invalid_token", body.jsonObject["error"]?.jsonPrimitive?.content)
        }

    // ========================================================================
    // JWKS Endpoint
    // ========================================================================

    @Test
    fun jwksEndpointReturns200() =
        runTest {
            val configProvider = FakeOAuth2ServersConfigProvider()
            val adapter = buildAdapter(configProvider)

            val request =
                GenericHttpRequest(
                    method = "GET",
                    path = "/.well-known/jwks.json",
                    headers = mapOf("host" to "auth.example.com"),
                )

            val response = adapter.handleRequest(request)

            assertEquals(200, response.statusCode)
            assertEquals("application/json", response.headers["Content-Type"])
            assertEquals("max-age=3600", response.headers["Cache-Control"])

            val body = json.parseToJsonElement(response.body!!)
            assertNotNull(body.jsonObject["keys"])
        }

    @Test
    fun jwksEndpointAvailableEvenWithOidcDisabled() =
        runTest {
            val configProvider =
                FakeOAuth2ServersConfigProvider(
                    OAuth2ServersConfig(
                        servers =
                            mapOf(
                                "default" to
                                    OAuth2ServerInstanceConfig(
                                        baseUrl = "https://auth.example.com",
                                        oidc = FeaturePolicy.DISABLED,
                                    ),
                            ),
                    ),
                )
            val adapter = buildAdapter(configProvider)

            val request =
                GenericHttpRequest(
                    method = "GET",
                    path = "/.well-known/jwks.json",
                    headers = mapOf("host" to "auth.example.com"),
                )

            val response = adapter.handleRequest(request)
            assertEquals(200, response.statusCode)
        }

    // ========================================================================
    // Federation Callback
    // ========================================================================

    @Test
    fun federationCallbackReturns404WithoutProvider() =
        runTest {
            val configProvider = FakeOAuth2ServersConfigProvider()
            val adapter = buildAdapter(configProvider)

            val request =
                GenericHttpRequest(
                    method = "GET",
                    path = "/federation/callback",
                    queryParameters = mapOf("code" to "test-code", "state" to "test-state"),
                    headers = mapOf("host" to "auth.example.com"),
                )

            val response = adapter.handleRequest(request)

            assertEquals(404, response.statusCode)
            val body = json.parseToJsonElement(response.body!!)
            assertEquals("not_found", body.jsonObject["error"]?.jsonPrimitive?.content)
        }
}
