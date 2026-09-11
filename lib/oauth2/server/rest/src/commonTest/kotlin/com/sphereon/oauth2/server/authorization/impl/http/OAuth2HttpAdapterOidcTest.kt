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

package com.sphereon.oauth2.server.authorization.impl.http

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.http.GenericHttpRequest
import com.sphereon.core.defaults.context.DefaultResolvedTenantIdProvider
import com.sphereon.core.defaults.http.NoOpRoutableSlugLookup
import com.sphereon.oauth2.common.config.DefaultOAuth2ServerInstanceIdProvider
import com.sphereon.oauth2.common.config.DefaultOAuth2ServerInstanceResolver
import com.sphereon.oauth2.common.config.FeaturePolicy
import com.sphereon.oauth2.common.config.MutableOAuth2ServerInstanceIdProvider
import com.sphereon.oauth2.common.config.OAuth2ServerInstanceConfig
import com.sphereon.oauth2.common.config.OAuth2ServerInstanceResolver
import com.sphereon.oauth2.common.config.OAuth2ServersConfig
import com.sphereon.oauth2.common.config.OAuth2ServersConfigProvider
import com.sphereon.oauth2.common.model.AuthorizationServerMetadata
import com.sphereon.oauth2.server.authorization.command.BuildServerMetadataArgs
import com.sphereon.oauth2.server.authorization.command.GetJwksArgs
import com.sphereon.oauth2.server.authorization.command.GetUserInfoArgs
import com.sphereon.oauth2.server.authorization.command.JwksResult
import com.sphereon.oauth2.server.authorization.command.UserInfoResponse
import com.sphereon.oauth2.server.authorization.command.discovery.HandleDiscoveryRequestArgs
import com.sphereon.oauth2.server.authorization.command.discovery.HandleDiscoveryRequestCommand
import com.sphereon.oauth2.server.authorization.command.jwks.HandleJwksRequestArgs
import com.sphereon.oauth2.server.authorization.command.jwks.HandleJwksRequestCommand
import com.sphereon.oauth2.server.authorization.command.userinfo.HandleUserInfoRequestArgs
import com.sphereon.oauth2.server.authorization.command.userinfo.HandleUserInfoRequestCommand
import com.sphereon.oauth2.server.authorization.error.AuthorizationServerError
import com.sphereon.oauth2.server.authorization.impl.http.DefaultOAuth2ServerBaseUrlResolver
import com.sphereon.oauth2.server.authorization.impl.http.command.TestSessionExecution
import com.sphereon.oauth2.server.authorization.impl.http.command.discovery.JwksHttpEndpointCommandImpl
import com.sphereon.oauth2.server.authorization.impl.http.command.discovery.OAuth2ServerMetadataHttpEndpointCommandImpl
import com.sphereon.oauth2.server.authorization.impl.http.command.discovery.OpenidDiscoveryHttpEndpointCommandImpl
import com.sphereon.oauth2.server.authorization.impl.http.command.userinfo.UserInfoHttpEndpointCommandImpl
import com.sphereon.oauth2.server.resource.command.ValidateAccessTokenArgs
import com.sphereon.oauth2.server.resource.command.ValidateAccessTokenCommand
import com.sphereon.oauth2.server.resource.error.ResourceServerError
import com.sphereon.oauth2.server.resource.model.VerifiedResourceRequest
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
        val server =
            config.getServer(serverId)
                ?: error("OAuth2 server '$serverId' not found in configuration")
        return server.issuer
            ?: server.issuerTemplate?.replace("{tenant-id}", tenantId)
            ?: error("OAuth2 server '$serverId' has no issuer or issuerTemplate")
    }
}

private class FakeHandleDiscoveryRequestCommand(
    private val configProvider: OAuth2ServersConfigProvider,
) : HandleDiscoveryRequestCommand {
    override val commandId: String get() = HandleDiscoveryRequestCommand.COMMAND_ID
    override val inputTypeToken get() =
        com.sphereon.core.api.binary
            .typeToken<HandleDiscoveryRequestArgs>()
    override val outputTypeToken get() =
        com.sphereon.core.api.binary
            .typeToken<AuthorizationServerMetadata>()
    override val isEnabled: Boolean = true

    override suspend fun supports(args: Any): Boolean = args is HandleDiscoveryRequestArgs

    override suspend fun execute(args: HandleDiscoveryRequestArgs): IdkResult<AuthorizationServerMetadata, IdkError> {
        val server = configProvider.getDefaultServer()
        val base =
            server.issuer ?: args.baseUrlOverride
                ?: return Err(
                    IdkError.fromString(
                        code = "server_error",
                        message = "OAuth2 server has no issuer configured and no request-time baseUrl override",
                    ),
                )
        return Ok(
            AuthorizationServerMetadata(
                issuer = base,
                tokenEndpoint = "$base/token",
                authorizationEndpoint = "$base/authorize",
                jwksUri = "$base/.well-known/jwks.json",
            ),
        )
    }
}

private class FakeHandleUserInfoRequestCommand : HandleUserInfoRequestCommand {
    override val commandId: String get() = HandleUserInfoRequestCommand.COMMAND_ID
    override val inputTypeToken get() =
        com.sphereon.core.api.binary
            .typeToken<HandleUserInfoRequestArgs>()
    override val outputTypeToken get() =
        com.sphereon.core.api.binary
            .typeToken<UserInfoResponse>()
    override val isEnabled: Boolean = true

    override suspend fun supports(args: Any): Boolean = args is HandleUserInfoRequestArgs

    override suspend fun execute(args: HandleUserInfoRequestArgs): IdkResult<UserInfoResponse, IdkError> = Err(IdkError.fromString(code = "invalid_token", message = "Test user info command"))
}

private class FakeHandleJwksRequestCommand : HandleJwksRequestCommand {
    override val commandId: String get() = HandleJwksRequestCommand.COMMAND_ID
    override val inputTypeToken get() =
        com.sphereon.core.api.binary
            .typeToken<HandleJwksRequestArgs>()
    override val outputTypeToken get() =
        com.sphereon.core.api.binary
            .typeToken<JwksResult>()
    override val isEnabled: Boolean = true

    override suspend fun supports(args: Any): Boolean = args is HandleJwksRequestArgs

    override suspend fun execute(args: HandleJwksRequestArgs): IdkResult<JwksResult, IdkError> = Ok(JwksResult(keys = emptyList()))
}

/**
 * Fake [ValidateAccessTokenCommand] for the userinfo adapter tests in this file. Always rejects
 * with a generic `invalid_token` so the negative-path assertions can verify the HTTP shell maps
 * resource-server errors to the right wire shape; the positive-path assertions in this file do
 * not invoke userinfo through validate.
 */
private object FakeRejectingValidateAccessToken : ValidateAccessTokenCommand {
    override val commandId: String get() = ValidateAccessTokenCommand.COMMAND_ID
    override val inputTypeToken get() =
        com.sphereon.core.api.binary
            .typeToken<ValidateAccessTokenArgs>()
    override val outputTypeToken get() =
        com.sphereon.core.api.binary
            .typeToken<VerifiedResourceRequest>()
    override val isEnabled: Boolean = true

    override suspend fun supports(args: Any): Boolean = args is ValidateAccessTokenArgs

    override suspend fun execute(args: ValidateAccessTokenArgs): IdkResult<VerifiedResourceRequest, IdkError> =
        Err(
            IdkError.fromDTO(
                ResourceServerError.InvalidToken(reason = "test stub: validate not configured"),
            ),
        )
}

private object FakeNoOpDpopNonceManager : com.sphereon.oauth2.server.authorization.dpop.DpopNonceManager {
    override suspend fun currentNonce(): String = "test-nonce-current"

    override suspend fun rotate(): String = "test-nonce-rotated"

    override suspend fun isValid(nonce: String): Boolean = true
}

private object FakeNoOpClientCertExtractor :
    com.sphereon.oauth2.server.authorization.command.clientauth.ClientCertificateExtractor {
    override suspend fun extractCertificate(request: GenericHttpRequest): IdkResult<ByteArray?, com.sphereon.oauth2.server.authorization.error.AuthorizationServerError> = Ok(null)
}

// ============================================================================
// Helpers
// ============================================================================

private fun execution(): SessionExecution = TestSessionExecution()

private fun resolverFor(configProvider: OAuth2ServersConfigProvider): OAuth2ServerInstanceResolver = DefaultOAuth2ServerInstanceResolver(configProvider)

private fun idProvider(): MutableOAuth2ServerInstanceIdProvider = DefaultOAuth2ServerInstanceIdProvider()

private fun discoveryAdapter(configProvider: OAuth2ServersConfigProvider): TestHttpAdapterRoute {
    val exec = execution()
    val handleDiscoveryCommand = FakeHandleDiscoveryRequestCommand(configProvider)
    val oauth2Metadata = OAuth2ServerMetadataHttpEndpointCommandImpl(exec, handleDiscoveryCommand, configProvider, DefaultOAuth2ServerBaseUrlResolver())
    val openidMetadata = OpenidDiscoveryHttpEndpointCommandImpl(exec, handleDiscoveryCommand, configProvider, DefaultOAuth2ServerBaseUrlResolver())
    val jwks = JwksHttpEndpointCommandImpl(exec, FakeHandleJwksRequestCommand())
    val registry = TestHttpEndpointCommandRegistry(oauth2Metadata, openidMetadata, jwks)
    val adapter =
        OAuth2DiscoveryHttpAdapter(
            execution = exec,
            endpointCommandRegistry = registry,
            asInstanceResolver = resolverFor(configProvider),
            asInstanceIdProvider = idProvider(),
            slugLookup = NoOpRoutableSlugLookup(),
            tenantIdProvider = DefaultResolvedTenantIdProvider(),
        )
    return TestHttpAdapterRoute(adapter, registry)
}

private fun userInfoAdapter(configProvider: OAuth2ServersConfigProvider): TestHttpAdapterRoute {
    val exec = execution()
    val endpoint =
        UserInfoHttpEndpointCommandImpl(
            execution = exec,
            handleUserInfoRequestCommand = FakeHandleUserInfoRequestCommand(),
            validateAccessTokenCommand = FakeRejectingValidateAccessToken,
            configProvider = configProvider,
            baseUrlResolver = DefaultOAuth2ServerBaseUrlResolver(),
            dpopNonceManager = FakeNoOpDpopNonceManager,
            clientCertificateExtractor = FakeNoOpClientCertExtractor,
        )
    val registry = TestHttpEndpointCommandRegistry(endpoint)
    val adapter =
        OAuth2UserInfoHttpAdapter(
            execution = exec,
            endpointCommandRegistry = registry,
            asInstanceResolver = resolverFor(configProvider),
            asInstanceIdProvider = idProvider(),
            slugLookup = NoOpRoutableSlugLookup(),
            tenantIdProvider = DefaultResolvedTenantIdProvider(),
        )
    return TestHttpAdapterRoute(adapter, registry)
}

/**
 * Tests for OIDC-specific HTTP endpoints, covering the discovery and user-info surfaces:
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
                                        issuer = "https://auth.example.com",
                                        oidc = FeaturePolicy.SUPPORTED,
                                        introspection = FeaturePolicy.SUPPORTED,
                                    ),
                            ),
                    ),
                )
            val adapter = discoveryAdapter(configProvider)

            val request =
                GenericHttpRequest(
                    method = "GET",
                    path = "/.well-known/openid-configuration",
                    headers = mapOf("host" to "auth.example.com"),
                )

            val response = dispatchForTest(listOf(adapter), request)

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
                                        issuer = "https://auth.example.com",
                                        oidc = FeaturePolicy.DISABLED,
                                    ),
                            ),
                    ),
                )
            val adapter = discoveryAdapter(configProvider)

            val request =
                GenericHttpRequest(
                    method = "GET",
                    path = "/.well-known/openid-configuration",
                    headers = mapOf("host" to "auth.example.com"),
                )

            val response = dispatchForTest(listOf(adapter), request)

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
                                        issuer = "https://auth.example.com",
                                        oidc = FeaturePolicy.DISABLED,
                                    ),
                            ),
                    ),
                )
            val adapter = userInfoAdapter(configProvider)

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

            val response = dispatchForTest(listOf(adapter), request)
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
                                        issuer = "https://auth.example.com",
                                        oidc = FeaturePolicy.SUPPORTED,
                                    ),
                            ),
                    ),
                )
            val adapter = userInfoAdapter(configProvider)

            val request =
                GenericHttpRequest(
                    method = "GET",
                    path = "/userinfo",
                    headers = mapOf("host" to "auth.example.com"),
                )

            val response = dispatchForTest(listOf(adapter), request)
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
            val adapter = discoveryAdapter(configProvider)

            val request =
                GenericHttpRequest(
                    method = "GET",
                    path = "/.well-known/jwks.json",
                    headers = mapOf("host" to "auth.example.com"),
                )

            val response = dispatchForTest(listOf(adapter), request)

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
                                        issuer = "https://auth.example.com",
                                        oidc = FeaturePolicy.DISABLED,
                                    ),
                            ),
                    ),
                )
            val adapter = discoveryAdapter(configProvider)

            val request =
                GenericHttpRequest(
                    method = "GET",
                    path = "/.well-known/jwks.json",
                    headers = mapOf("host" to "auth.example.com"),
                )

            val response = dispatchForTest(listOf(adapter), request)
            assertEquals(200, response.statusCode)
        }
}
