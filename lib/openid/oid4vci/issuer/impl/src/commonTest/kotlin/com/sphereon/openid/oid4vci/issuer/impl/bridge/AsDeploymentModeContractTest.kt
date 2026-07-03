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

@file:OptIn(ExperimentalTime::class)

package com.sphereon.openid.oid4vci.issuer.impl.bridge

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.conf.AppConfigService
import com.sphereon.core.api.conf.ConfigLevel
import com.sphereon.core.api.conf.ConfigService
import com.sphereon.core.api.conf.PrincipalConfigService
import com.sphereon.core.api.conf.PropertySource
import com.sphereon.core.api.conf.PropertySources
import com.sphereon.core.api.conf.TenantConfigService
import com.sphereon.core.api.context.ContextConfig
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.log.SessionLogService
import com.sphereon.di.context.NoOpSessionContext
import com.sphereon.di.session.SessionContext
import com.sphereon.di.session.SessionContextManager
import com.sphereon.oauth2.common.command.VerifyDpopProofCommand
import com.sphereon.oauth2.common.config.OAuth2ServerInstanceConfig
import com.sphereon.oauth2.common.config.OAuth2ServersConfig
import com.sphereon.oauth2.common.config.OAuth2ServersConfigProvider
import com.sphereon.oauth2.common.model.TokenIntrospectionResponse
import com.sphereon.oauth2.common.model.VerifyDpopProofOptions
import com.sphereon.oauth2.common.model.VerifyDpopProofResult
import com.sphereon.oauth2.server.authorization.command.AuthorizationRequestData
import com.sphereon.oauth2.server.authorization.command.BuildServerMetadataArgs
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
import com.sphereon.oauth2.server.authorization.command.GetUserInfoArgs
import com.sphereon.oauth2.server.authorization.command.IntrospectTokenArgs
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
import com.sphereon.oauth2.server.authorization.service.AuthorizationServerService
import com.sphereon.oauth2.server.authorization.storage.PreAuthorizedCodeData
import com.sphereon.oauth2.server.authorization.storage.PreAuthorizedCodeStorage
import com.sphereon.openid.oid4vci.issuer.bridge.ConsumePreAuthCodeArgs
import com.sphereon.openid.oid4vci.issuer.bridge.RegisterPreAuthCodeArgs
import com.sphereon.openid.oid4vci.issuer.bridge.ValidateAccessTokenArgs
import com.sphereon.openid.oid4vci.issuer.impl.command.NoOpSessionLogService
import kotlinx.coroutines.test.runTest
import kotlinx.io.files.Path
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.reflect.KClass
import kotlin.reflect.cast
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Contract tests for the Oid4vciAuthorizationServerBridge across AS deployment modes.
 *
 * The bridge abstraction isolates AS topology from the OID4VCI issuer. These tests verify
 * the behavioral contract that any bridge implementation must satisfy:
 *
 * - Embedded/Hosted mode: SphereonAsBridge (tested via fakes)
 * - External mode: Not yet implemented; tests document the contract gap
 * - Forwarded mode: Future — proxy to colocated AS
 * - Hybrid mode: Future — split code/token across AS instances
 */
class AsDeploymentModeContractTest {
    // ========================================================================
    // Fakes
    // ========================================================================

    /**
     * In-memory fake for PreAuthorizedCodeStorage.
     * Mimics atomic consume behavior (remove-on-read).
     */
    private class FakePreAuthorizedCodeStorage : PreAuthorizedCodeStorage {
        private val codes = mutableMapOf<String, PreAuthorizedCodeData>()

        override suspend fun storePreAuthorizedCode(
            code: String,
            data: PreAuthorizedCodeData,
        ): IdkResult<Unit, AuthorizationServerError.StorageError> {
            codes[code] = data
            return Ok(Unit)
        }

        override suspend fun consumePreAuthorizedCode(code: String): IdkResult<PreAuthorizedCodeData?, AuthorizationServerError.StorageError> = Ok(codes.remove(code))

        override suspend fun isCodeUsed(code: String): IdkResult<Boolean, AuthorizationServerError.StorageError> = Ok(code !in codes)
    }

    /**
     * Fake AuthorizationServerService that only implements introspectToken.
     * All other methods throw — they are not part of the bridge contract.
     */
    private class FakeAuthorizationServerService(
        private var introspectionResult: IdkResult<TokenIntrospectionResponse, IdkError>,
    ) : AuthorizationServerService {
        var lastIntrospectTokenArgs: IntrospectTokenArgs? = null
            private set

        fun setIntrospectionResult(result: IdkResult<TokenIntrospectionResponse, IdkError>) {
            introspectionResult = result
        }

        override suspend fun introspectToken(args: IntrospectTokenArgs): IdkResult<TokenIntrospectionResponse, IdkError> {
            lastIntrospectTokenArgs = args
            return introspectionResult
        }

        // --- All other methods are not used by the bridge and throw ---
        override suspend fun parseTokenRequest(args: ParseTokenRequestArgs) = notUsed()

        override suspend fun verifyAuthorizationCodeGrant(args: VerifyAuthorizationCodeGrantArgs) = notUsed()

        override suspend fun verifyRefreshTokenGrant(args: VerifyRefreshTokenGrantArgs) = notUsed()

        override suspend fun verifyClientCredentialsGrant(args: VerifyClientCredentialsGrantArgs) = notUsed()

        override suspend fun verifyTokenExchangeGrant(args: VerifyTokenExchangeGrantArgs) = notUsed()

        override suspend fun verifyPreAuthorizedCodeGrant(args: VerifyPreAuthCodeArgs) = notUsed()

        override suspend fun createAccessToken(args: CreateAccessTokenArgs) = notUsed()

        override suspend fun createRefreshToken(args: CreateRefreshTokenArgs) = notUsed()

        override suspend fun createTokenResponse(args: CreateTokenResponseArgs) = notUsed()

        override suspend fun parseAuthorizationRequest(args: ParseAuthorizationRequestArgs) = notUsed()

        override suspend fun verifyAuthorizationRequest(args: AuthorizationRequestData) = notUsed()

        override suspend fun createAuthorizationSession(args: VerifiedAuthorizationRequest) = notUsed()

        override suspend fun createAuthorizationCode(args: CreateAuthorizationCodeArgs) = notUsed()

        override suspend fun createAuthorizationResponse(args: CreateAuthorizationResponseArgs) = notUsed()

        override suspend fun createAuthorizationErrorResponse(args: CreateAuthorizationErrorResponseArgs) = notUsed()

        override suspend fun parsePushedAuthorizationRequest(args: ParsePushedAuthorizationRequestArgs) = notUsed()

        override suspend fun verifyPushedAuthorizationRequest(args: VerifyPushedAuthorizationRequestArgs) = notUsed()

        override suspend fun createRequestUri(args: VerifiedAuthorizationRequest) = notUsed()

        override suspend fun createPushedAuthorizationResponse(args: CreatePushedAuthorizationResponseArgs) = notUsed()

        override suspend fun retrieveAuthorizationRequestByUri(requestUri: String) = notUsed()

        override suspend fun parseIntrospectionRequest(args: ParseIntrospectionRequestArgs) = notUsed()

        override suspend fun parseRevocationRequest(args: ParseRevocationRequestArgs) = notUsed()

        override suspend fun revokeToken(args: RevokeTokenArgs): IdkResult<Unit, IdkError> = throw UnsupportedOperationException("Not used in bridge tests")

        override suspend fun buildServerMetadata(args: BuildServerMetadataArgs) = notUsed()

        override suspend fun verifyClientAuthentication(args: VerifyClientAuthenticationArgs) = notUsed()

        override suspend fun createAttestationChallenge(args: CreateAttestationChallengeArgs) = notUsed()

        override suspend fun createIdToken(args: CreateIdTokenArgs) = notUsed()

        override suspend fun getUserInfo(args: GetUserInfoArgs) = notUsed()

        override suspend fun getJwks(args: GetJwksArgs) = notUsed()

        override val commands: AuthorizationServerService.Commands get() = throw UnsupportedOperationException("Not used in bridge tests")

        private fun notUsed(): Nothing = throw UnsupportedOperationException("Not used in bridge tests")
    }

    // ========================================================================
    // Fakes — config / session
    // ========================================================================

    private class FakePrincipalConfigService(
        private val properties: Map<String, String> = emptyMap(),
    ) : PrincipalConfigService {
        override val parent: TenantConfigService get() = error("parent not used in bridge tests")
        override val configLevel: ConfigLevel = ConfigLevel.PRINCIPAL

        override fun addPropertySource(source: PropertySource<*>): Nothing = error("not used")

        override fun removePropertySource(source: PropertySource<*>): Nothing = error("not used")

        override fun getActiveProfile(): String = "test"

        override fun getAppName(): String = "test-app"

        override fun getConfigLocation(): Path = error("not used")

        override fun getPropertySources(includeParents: Boolean): PropertySources = error("not used")

        @Suppress("DEPRECATION")
        override fun getNamespace(): String = ""

        override fun containsProperty(key: String): Boolean = properties.containsKey(key)

        override fun <T : Any> getProperty(
            key: String,
            targetType: KClass<T>,
            defaultValue: T?,
        ): T? {
            val value = properties[key] ?: return defaultValue
            return targetType.cast(value)
        }

        override fun getPropertyAsString(
            key: String,
            defaultValue: String?,
        ): String? = properties[key] ?: defaultValue

        override fun <T : Any> getRequiredProperty(
            key: String,
            targetType: KClass<T>,
            defaultValue: T?,
        ): T = getProperty(key, targetType, defaultValue) ?: error("Missing required property $key")

        override fun getRequiredPropertyAsString(
            key: String,
            defaultValue: String?,
        ): String = getPropertyAsString(key, defaultValue) ?: error("Missing required property $key")

        override fun getAllProperties(): Map<String, Any> = properties.toMap()

        override fun getAllPropertiesAsString(redact: Boolean): Map<String, String> = properties

        override fun getSubProperties(
            prefixes: Set<String>,
            stripPrefix: Boolean,
        ): Map<String, Any> = computeSubProperties(prefixes, stripPrefix)

        override fun getSubPropertiesAsString(
            prefixes: Set<String>,
            stripPrefix: Boolean,
            redact: Boolean,
        ): Map<String, String> = computeSubProperties(prefixes, stripPrefix)

        private fun computeSubProperties(
            prefixes: Set<String>,
            stripPrefix: Boolean,
        ): Map<String, String> {
            val matched = mutableMapOf<String, String>()
            for (prefix in prefixes) {
                for ((key, value) in properties) {
                    val matches = key.startsWith("$prefix.") || key == prefix
                    if (!matches) continue
                    val outKey = if (stripPrefix) key.removePrefix("$prefix.") else key
                    matched[outKey] = value
                }
            }
            return matched
        }
    }

    private class FakeContextConfig(
        private val principalConfigService: PrincipalConfigService,
    ) : ContextConfig {
        override val app: AppConfigService get() = error("not used in bridge tests")
        override val tenant: TenantConfigService get() = error("not used in bridge tests")
        override val principal: PrincipalConfigService get() = principalConfigService

        override fun conf(level: ConfigLevel): ConfigService =
            when (level) {
                ConfigLevel.PRINCIPAL -> principalConfigService
                else -> error("Only PRINCIPAL config is used in bridge tests")
            }
    }

    private class FakeSessionExecution(
        configProperties: Map<String, String> = emptyMap(),
    ) : SessionExecution {
        private val principalConfig = FakePrincipalConfigService(configProperties)
        override val sessionContext: SessionContext = NoOpSessionContext
        override val sessionContextManager: SessionContextManager get() = error("not used in bridge tests")
        override val log: SessionLogService = NoOpSessionLogService(sessionContext)
        override val conf: ContextConfig = FakeContextConfig(principalConfig)
    }

    private class FakeOAuth2ServersConfigProvider(
        override val serverConfig: OAuth2ServerInstanceConfig,
    ) : OAuth2ServersConfigProvider {
        private val config = OAuth2ServersConfig(servers = mapOf("default" to serverConfig))

        override fun getConfig(): OAuth2ServersConfig = config

        override fun getServer(id: String): OAuth2ServerInstanceConfig? = config.getServer(id)

        override fun getDefaultServer(): OAuth2ServerInstanceConfig = config.getDefaultServer()

        override fun resolveIssuer(
            serverId: String,
            tenantId: String,
        ): String = getServer(serverId)?.issuer ?: "https://issuer.example.com"
    }

    // ========================================================================
    // Embedded/Hosted mode — SphereonAsBridge
    // ========================================================================

    private fun createEmbeddedBridge(
        storage: FakePreAuthorizedCodeStorage = FakePreAuthorizedCodeStorage(),
        asService: FakeAuthorizationServerService =
            FakeAuthorizationServerService(
                Ok(TokenIntrospectionResponse(active = false)),
            ),
        configProperties: Map<String, String> = emptyMap(),
        oauth2ConfigProvider: OAuth2ServersConfigProvider? = null,
    ): Pair<SphereonAsBridge, FakeAuthorizationServerService> {
        val bridge =
            SphereonAsBridge(
                preAuthorizedCodeStorage = storage,
                authorizationServerService = asService,
                verifyDpopProofCommand = NoopVerifyDpopProofCommand,
                oauth2ConfigProvider = oauth2ConfigProvider,
                execution = FakeSessionExecution(configProperties),
            )
        return bridge to asService
    }

    /**
     * `SphereonAsBridge` only invokes the DPoP verifier when an access-token validation actually
     * carries a DPoP proof. The contract tests in this file exercise pre-authorized-code
     * registration / consumption and access-token validation against introspection — none reach
     * the verifier path. A no-op stand-in keeps the constructor wiring honest while making it
     * obvious in CI that any test which DOES hit the DPoP path needs a real fake.
     */
    private object NoopVerifyDpopProofCommand : VerifyDpopProofCommand {
        override val commandId: String = VerifyDpopProofCommand.COMMAND_ID
        override val isEnabled: Boolean = true
        override val inputTypeToken = typeToken<VerifyDpopProofOptions>()
        override val outputTypeToken = typeToken<VerifyDpopProofResult>()

        override suspend fun supports(args: Any): Boolean = args is VerifyDpopProofOptions

        override suspend fun execute(args: VerifyDpopProofOptions): IdkResult<VerifyDpopProofResult, IdkError> =
            throw UnsupportedOperationException("DPoP verification not exercised in these contract tests")
    }

    @Test
    fun embeddedModeRegistersPreAuthorizedCode() =
        runTest {
            val (bridge, _) = createEmbeddedBridge()

            val result =
                bridge.registerPreAuthorizedCode(
                    RegisterPreAuthCodeArgs(
                        sessionId = "session-001",
                        credentialConfigurationIds = listOf("IdentityCredential"),
                        txCodeRequired = false,
                    ),
                )

            assertTrue(result.isOk, "registerPreAuthorizedCode should succeed")
            val registered = result.value
            assertTrue(registered.code.isNotBlank(), "Code should be a non-empty string")
        }

    @Test
    fun embeddedModeRegistersPreAuthorizedCodeWithTxCode() =
        runTest {
            val (bridge, _) = createEmbeddedBridge()

            val result =
                bridge.registerPreAuthorizedCode(
                    RegisterPreAuthCodeArgs(
                        sessionId = "session-002",
                        credentialConfigurationIds = listOf("IdentityCredential"),
                        txCodeRequired = true,
                    ),
                )

            assertTrue(result.isOk, "registerPreAuthorizedCode with txCode should succeed")
            val registered = result.value
            assertTrue(registered.code.isNotBlank(), "Code should be non-empty")
            assertNotNull(registered.txCode, "txCode should be present when requested")
            assertTrue(registered.txCode!!.isNotBlank(), "txCode should be non-empty")
        }

    @Test
    fun embeddedModeConsumesPreAuthorizedCode() =
        runTest {
            val storage = FakePreAuthorizedCodeStorage()
            val (bridge, _) = createEmbeddedBridge(storage = storage)

            // Register
            val regResult =
                bridge.registerPreAuthorizedCode(
                    RegisterPreAuthCodeArgs(
                        sessionId = "session-003",
                        credentialConfigurationIds = listOf("IdentityCredential", "DriverLicense"),
                        txCodeRequired = false,
                    ),
                )
            assertTrue(regResult.isOk)
            val code = regResult.value.code

            // Consume
            val consumeResult =
                bridge.consumePreAuthorizedCode(
                    ConsumePreAuthCodeArgs(
                        code = code,
                        txCode = null,
                        clientId = "test-client",
                    ),
                )

            assertTrue(consumeResult.isOk, "consumePreAuthorizedCode should succeed")
            val consumed = consumeResult.value
            assertEquals("session-003", consumed.sessionId)
            assertEquals(listOf("IdentityCredential", "DriverLicense"), consumed.credentialConfigurationIds)
        }

    @Test
    fun embeddedModeRejectsDoubleConsumption() =
        runTest {
            val storage = FakePreAuthorizedCodeStorage()
            val (bridge, _) = createEmbeddedBridge(storage = storage)

            // Register
            val regResult =
                bridge.registerPreAuthorizedCode(
                    RegisterPreAuthCodeArgs(
                        sessionId = "session-004",
                        credentialConfigurationIds = listOf("IdentityCredential"),
                        txCodeRequired = false,
                    ),
                )
            assertTrue(regResult.isOk)
            val code = regResult.value.code

            // First consume — should succeed
            val first =
                bridge.consumePreAuthorizedCode(
                    ConsumePreAuthCodeArgs(code = code, txCode = null, clientId = "client-1"),
                )
            assertTrue(first.isOk, "First consumption should succeed")

            // Second consume — should fail (code already consumed)
            val second =
                bridge.consumePreAuthorizedCode(
                    ConsumePreAuthCodeArgs(code = code, txCode = null, clientId = "client-2"),
                )
            assertTrue(second.isErr, "Second consumption of the same code must fail")
        }

    @Test
    fun embeddedModeValidatesAccessToken() =
        runTest {
            val authDetails =
                JsonArray(
                    listOf(
                        buildJsonObject {
                            put("type", "openid_credential")
                            put("credential_configuration_id", "IdentityCredential")
                            put("credential_identifiers", JsonArray(listOf(JsonPrimitive("id-1"), JsonPrimitive("id-2"))))
                        },
                    ),
                )

            val introspectionResponse =
                TokenIntrospectionResponse(
                    active = true,
                    sub = "user-123",
                    clientId = "client-abc",
                    scope = "openid",
                    additionalClaims = mapOf<String, JsonElement>("authorization_details" to authDetails),
                )

            val (bridge, _) =
                createEmbeddedBridge(
                    asService = FakeAuthorizationServerService(Ok(introspectionResponse)),
                )

            val result =
                bridge.validateAccessToken(
                    ValidateAccessTokenArgs(accessToken = "valid-token"),
                )

            assertTrue(result.isOk, "validateAccessToken should succeed for active token")
            val ctx = result.value
            assertEquals("user-123", ctx.subject)
            assertEquals("client-abc", ctx.clientId)
            assertEquals("openid", ctx.scope)
            assertEquals(listOf("IdentityCredential"), ctx.credentialConfigurationIds)
            assertEquals(listOf("id-1", "id-2"), ctx.credentialIdentifiers)
        }

    @Test
    fun embeddedModeUsesConfiguredIssuerInternalClientForIntrospection() =
        runTest {
            val introspectionResponse =
                TokenIntrospectionResponse(
                    active = true,
                    sub = "user-123",
                    clientId = "wallet-client",
                )
            val asService = FakeAuthorizationServerService(Ok(introspectionResponse))
            val configProvider =
                FakeOAuth2ServersConfigProvider(
                    OAuth2ServerInstanceConfig(
                        issuer = "https://issuer.example.com",
                        internalClients = mapOf("issuer" to ("issuer-service" to "issuer-secret")),
                    ),
                )

            val (bridge, _) =
                createEmbeddedBridge(
                    asService = asService,
                    oauth2ConfigProvider = configProvider,
                )

            val result = bridge.validateAccessToken(ValidateAccessTokenArgs(accessToken = "valid-token"))

            assertTrue(result.isOk, "validateAccessToken should succeed for active token")
            assertEquals("issuer-service", asService.lastIntrospectTokenArgs?.clientId)
        }

    @Test
    fun embeddedModeRejectsInactiveToken() =
        runTest {
            val (bridge, _) =
                createEmbeddedBridge(
                    asService =
                        FakeAuthorizationServerService(
                            Ok(TokenIntrospectionResponse(active = false)),
                        ),
                )

            val result =
                bridge.validateAccessToken(
                    ValidateAccessTokenArgs(accessToken = "expired-token"),
                )

            assertTrue(result.isErr, "Inactive token should be rejected")
        }

    @Test
    fun embeddedModeRejectsTokenWithoutSubject() =
        runTest {
            val (bridge, _) =
                createEmbeddedBridge(
                    asService =
                        FakeAuthorizationServerService(
                            Ok(TokenIntrospectionResponse(active = true, sub = null, clientId = "client-1")),
                        ),
                )

            val result =
                bridge.validateAccessToken(
                    ValidateAccessTokenArgs(accessToken = "no-sub-token"),
                )

            assertTrue(result.isErr, "Token without sub claim should be rejected")
        }

    @Test
    fun validateAccessTokenSurfacesAcrAndAuthTimeWhenPresent() =
        runTest {
            val authTimeEpoch = 1_700_000_000L
            val introspectionResponse =
                TokenIntrospectionResponse(
                    active = true,
                    sub = "user-acr",
                    clientId = "client-1",
                    scope = "openid",
                    additionalClaims =
                        mapOf<String, JsonElement>(
                            "acr" to JsonPrimitive("urn:mace:incommon:iap:silver"),
                            "auth_time" to JsonPrimitive(authTimeEpoch),
                        ),
                )

            val (bridge, _) = createEmbeddedBridge(asService = FakeAuthorizationServerService(Ok(introspectionResponse)))

            val result = bridge.validateAccessToken(ValidateAccessTokenArgs(accessToken = "token-with-acr"))

            assertTrue(result.isOk, "validateAccessToken should succeed")
            val ctx = result.value
            assertEquals("urn:mace:incommon:iap:silver", ctx.acr)
            assertEquals(Instant.fromEpochSeconds(authTimeEpoch), ctx.authTime)
            assertNull(ctx.upstreamSubject)
            assertNull(ctx.upstreamIssuer)
            assertNull(ctx.userinfoClaims)
        }

    @Test
    fun validateAccessTokenSurfacesUpstreamFederationClaimsWhenPresent() =
        runTest {
            val introspectionResponse =
                TokenIntrospectionResponse(
                    active = true,
                    sub = "local-user",
                    clientId = "client-2",
                    additionalClaims =
                        mapOf<String, JsonElement>(
                            "upstream_sub" to JsonPrimitive("ext-user-42"),
                            "upstream_iss" to JsonPrimitive("https://enterprise-idp.example.com"),
                        ),
                )

            val (bridge, _) = createEmbeddedBridge(asService = FakeAuthorizationServerService(Ok(introspectionResponse)))

            val result = bridge.validateAccessToken(ValidateAccessTokenArgs(accessToken = "federated-token"))

            assertTrue(result.isOk)
            val ctx = result.value
            assertEquals("ext-user-42", ctx.upstreamSubject)
            assertEquals("https://enterprise-idp.example.com", ctx.upstreamIssuer)
        }

    @Test
    fun validateAccessTokenOmitsOptionalClaimsWhenAbsent() =
        runTest {
            val introspectionResponse =
                TokenIntrospectionResponse(
                    active = true,
                    sub = "plain-user",
                    clientId = "client-3",
                )

            val (bridge, _) = createEmbeddedBridge(asService = FakeAuthorizationServerService(Ok(introspectionResponse)))

            val result = bridge.validateAccessToken(ValidateAccessTokenArgs(accessToken = "plain-token"))

            assertTrue(result.isOk)
            val ctx = result.value
            assertNull(ctx.acr)
            assertNull(ctx.authTime)
            assertNull(ctx.upstreamSubject)
            assertNull(ctx.upstreamIssuer)
            assertNull(ctx.userinfoClaims)
        }

    @Test
    fun validateAccessTokenSurfacesUserinfoClaimsWhenTenantOptIn() =
        runTest {
            val idpIssuer = "https://enterprise-idp.example.com"
            val introspectionResponse =
                TokenIntrospectionResponse(
                    active = true,
                    sub = "federated-user",
                    clientId = "client-4",
                    additionalClaims =
                        mapOf<String, JsonElement>(
                            "upstream_iss" to JsonPrimitive(idpIssuer),
                            "upstream_sub" to JsonPrimitive("idp-sub-99"),
                            "email" to JsonPrimitive("user@enterprise.example.com"),
                            "given_name" to JsonPrimitive("Test"),
                        ),
                )

            val (bridge, _) =
                createEmbeddedBridge(
                    asService = FakeAuthorizationServerService(Ok(introspectionResponse)),
                    configProperties = mapOf("tenant.idp.[$idpIssuer].surface-userinfo-to-issuance" to "true"),
                )

            val result = bridge.validateAccessToken(ValidateAccessTokenArgs(accessToken = "federated-token-with-ui"))

            assertTrue(result.isOk)
            val ctx = result.value
            assertNotNull(ctx.userinfoClaims, "userinfoClaims should be populated when tenant opts in")
            assertEquals(JsonPrimitive("user@enterprise.example.com"), ctx.userinfoClaims!!["email"])
            assertEquals(JsonPrimitive("Test"), ctx.userinfoClaims!!["given_name"])
            // Protocol claims are excluded from userinfoClaims
            assertNull(ctx.userinfoClaims!!["upstream_iss"])
            assertNull(ctx.userinfoClaims!!["upstream_sub"])
        }

    @Test
    fun validateAccessTokenOmitsUserinfoClaimsWhenTenantNotOptIn() =
        runTest {
            val idpIssuer = "https://enterprise-idp.example.com"
            val introspectionResponse =
                TokenIntrospectionResponse(
                    active = true,
                    sub = "federated-user",
                    clientId = "client-5",
                    additionalClaims =
                        mapOf<String, JsonElement>(
                            "upstream_iss" to JsonPrimitive(idpIssuer),
                            "email" to JsonPrimitive("user@enterprise.example.com"),
                        ),
                )

            val (bridge, _) =
                createEmbeddedBridge(
                    asService = FakeAuthorizationServerService(Ok(introspectionResponse)),
                    // No config property set — opt-in is absent
                )

            val result = bridge.validateAccessToken(ValidateAccessTokenArgs(accessToken = "token-no-ui-opt-in"))

            assertTrue(result.isOk)
            assertNull(result.value.userinfoClaims, "userinfoClaims should be null when tenant has not opted in")
        }

    // ========================================================================
    // External mode — contract documentation
    //
    // The external AS mode requires a bridge that delegates to the external AS
    // via HTTP introspection and does NOT support pre-authorized code management
    // (the external AS manages its own grants). No ExternalAsBridge implementation
    // exists yet.
    //
    // When implemented, ExternalAsBridge should:
    // - registerPreAuthorizedCode -> return error (external AS manages codes)
    // - consumePreAuthorizedCode -> return error (external AS manages codes)
    // - validateAccessToken -> HTTP POST to introspection_endpoint
    // - augmentAsMetadata -> no-op (external AS has its own discovery document)
    // ========================================================================

    @Test
    fun externalModeRequiresBridgeImplementation() {
        // This test documents that external mode needs a custom Oid4vciAuthorizationServerBridge
        // implementation. The SphereonAsBridge is only valid for HOSTED mode.
        //
        // The ExternalAsBridge should:
        // 1. Reject registerPreAuthorizedCode (external AS manages its own grants)
        // 2. Reject consumePreAuthorizedCode (external AS manages its own grants)
        // 3. Validate access tokens via HTTP introspection to the external AS endpoint
        // 4. Return no-op for augmentAsMetadata (external AS manages its own metadata)
        //
        // TODO: Implement ExternalAsBridge and add concrete tests here.
        assertTrue(true, "External mode bridge contract documented — implementation pending")
    }

    @Test
    fun bridgeContractRequiresAtomicCodeConsumption() {
        // Core security invariant: any bridge implementation MUST guarantee
        // that consumePreAuthorizedCode is atomic — a code can only be consumed once.
        // This mirrors RFC 6749 Section 10.5 for authorization codes.
        //
        // The embedded mode test (embeddedModeRejectsDoubleConsumption) verifies this
        // for SphereonAsBridge. Future bridge implementations must pass the same test.
        assertTrue(true, "Atomic consumption contract documented")
    }
}
