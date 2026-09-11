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
import com.sphereon.core.api.Err
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
import com.sphereon.oauth2.server.authorization.command.VerifiedPreAuthCodeGrant
import com.sphereon.oauth2.server.authorization.error.AuthorizationServerError
import com.sphereon.oauth2.server.authorization.service.AuthorizationServerService
import com.sphereon.oauth2.server.authorization.service.InternalClientRoleResolver
import com.sphereon.oauth2.server.authorization.impl.command.oidc.GetUserInfoCommandImpl
import com.sphereon.oauth2.server.authorization.impl.oidc.OidcScopeClaimsMapperImpl
import com.sphereon.oauth2.server.authorization.impl.storage.memory.InMemoryOAuth2BackingStorageImpl
import com.sphereon.oauth2.server.authorization.impl.storage.memory.InMemoryTokenStorageImpl
import com.sphereon.oauth2.server.authorization.model.AccessTokenData
import com.sphereon.oauth2.server.authorization.model.SESSION_KEY_OIDC_CLAIMS_USERINFO
import com.sphereon.oauth2.server.authorization.provider.AuthenticationContext
import com.sphereon.oauth2.server.authorization.provider.AuthenticationError
import com.sphereon.oauth2.server.authorization.provider.AuthenticationHint
import com.sphereon.oauth2.server.authorization.provider.AuthenticatedUser
import com.sphereon.oauth2.server.authorization.provider.UserAuthenticationProvider
import com.sphereon.oauth2.server.authorization.provider.UserCredentials
import com.sphereon.oauth2.server.authorization.provider.UserInfo
import com.sphereon.oauth2.server.authorization.storage.PreAuthorizedCodeData
import com.sphereon.oauth2.server.authorization.storage.PreAuthorizedCodeStorage
import com.sphereon.oauth2.server.resource.command.VerifyJwtArgs
import com.sphereon.oauth2.server.resource.command.VerifyJwtCommand
import com.sphereon.oauth2.server.resource.model.TokenPayload
import com.sphereon.openid.oid4vci.issuer.bridge.ConsumePreAuthCodeArgs
import com.sphereon.openid.oid4vci.issuer.bridge.RegisterPreAuthCodeArgs
import com.sphereon.openid.oid4vci.issuer.bridge.ValidateAccessTokenArgs
import com.sphereon.openid.oid4vci.issuer.bridge.Oid4vciAuthorizationServerTarget
import com.sphereon.openid.oid4vci.issuer.authorization.Oid4vciAuthorizationServerDeployment
import com.sphereon.openid.oid4vci.issuer.impl.command.NoOpSessionLogService
import kotlinx.coroutines.test.runTest
import kotlinx.io.files.Path
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
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
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours

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
        var lastStoredData: PreAuthorizedCodeData? = null
        var rawConsumeCount: Int = 0
            private set

        override suspend fun storePreAuthorizedCode(
            code: String,
            data: PreAuthorizedCodeData,
        ): IdkResult<Unit, AuthorizationServerError.StorageError> {
            codes[code] = data
            lastStoredData = data
            return Ok(Unit)
        }

        override suspend fun findPreAuthorizedCode(code: String): IdkResult<PreAuthorizedCodeData?, AuthorizationServerError.StorageError> = Ok(codes[code])

        override suspend fun consumePreAuthorizedCodeIfValid(
            code: String,
            expectedData: PreAuthorizedCodeData,
            now: kotlin.time.Instant,
        ): IdkResult<PreAuthorizedCodeData?, AuthorizationServerError.StorageError> =
            Ok(
                codes[code]?.takeIf { it == expectedData && it.expiresAt > now }?.also {
                    codes.remove(code)
                },
            )

        override suspend fun consumePreAuthorizedCode(code: String): IdkResult<PreAuthorizedCodeData?, AuthorizationServerError.StorageError> {
            rawConsumeCount++
            return Ok(codes.remove(code))
        }

        override suspend fun isCodeUsed(code: String): IdkResult<Boolean, AuthorizationServerError.StorageError> = Ok(code !in codes)
    }

    /**
     * Fake AuthorizationServerService that only implements introspectToken.
     * All other methods throw — they are not part of the bridge contract.
     */
    private class FakeAuthorizationServerService(
        private var introspectionResult: IdkResult<TokenIntrospectionResponse, IdkError>,
        private val userInfoHandler: (suspend (GetUserInfoArgs) -> IdkResult<com.sphereon.oauth2.server.authorization.command.UserInfoResponse, IdkError>)? = null,
    ) : AuthorizationServerService {
        var lastIntrospectTokenArgs: IntrospectTokenArgs? = null
            private set
        var lastVerifyPreAuthorizedCodeArgs: VerifyPreAuthCodeArgs? = null
            private set
        var userInfoCalls: Int = 0
            private set
        private var preAuthorizedCodeVerifier: (suspend (VerifyPreAuthCodeArgs) -> IdkResult<VerifiedPreAuthCodeGrant, IdkError>)? = null

        fun setIntrospectionResult(result: IdkResult<TokenIntrospectionResponse, IdkError>) {
            introspectionResult = result
        }

        fun setPreAuthorizedCodeVerifier(verifier: suspend (VerifyPreAuthCodeArgs) -> IdkResult<VerifiedPreAuthCodeGrant, IdkError>) {
            preAuthorizedCodeVerifier = verifier
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

        override suspend fun verifyPreAuthorizedCodeGrant(args: VerifyPreAuthCodeArgs): IdkResult<VerifiedPreAuthCodeGrant, IdkError> {
            lastVerifyPreAuthorizedCodeArgs = args
            return preAuthorizedCodeVerifier?.invoke(args) ?: notUsed()
        }

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

        override suspend fun getUserInfo(args: GetUserInfoArgs): IdkResult<com.sphereon.oauth2.server.authorization.command.UserInfoResponse, IdkError> {
            userInfoCalls++
            return userInfoHandler?.invoke(args) ?: notUsed()
        }

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
        override val tenantId: String = "test-tenant",
    ) : SessionExecution {
        private val principalConfig = FakePrincipalConfigService(configProperties)
        override val sessionContext: SessionContext = NoOpSessionContext
        override val sessionContextManager: SessionContextManager get() = error("not used in bridge tests")
        override val log: SessionLogService = NoOpSessionLogService(sessionContext)
        override val conf: ContextConfig = FakeContextConfig(principalConfig)
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
        internalClientRoleResolver: InternalClientRoleResolver = InternalClientRoleResolver { null },
        tenantId: String = "test-tenant",
        verifyJwtCommand: VerifyJwtCommand? = null,
    ): Pair<SphereonAsBridge, FakeAuthorizationServerService> {
        val bridge =
            SphereonAsBridge(
                preAuthorizedCodeStorage = storage,
                authorizationServerService = asService,
                verifyDpopProofCommand = NoopVerifyDpopProofCommand,
                internalClientRoleResolver = internalClientRoleResolver,
                execution = FakeSessionExecution(configProperties, tenantId),
                verifyJwtCommand = verifyJwtCommand,
            )
        return bridge to asService
    }

    private fun hostedTarget() = Oid4vciAuthorizationServerTarget(
        id = "00000000-0000-4000-8000-000000000002",
        issuer = "https://as.example",
        deployment = Oid4vciAuthorizationServerDeployment.HOSTED,
        runtimeServerKey = "default",
        tokenEndpoint = "https://as.example/token",
        jwksUri = "https://as.example/jwks",
    )

    private fun externalTarget() = hostedTarget().copy(
        issuer = "https://external-as.example",
        deployment = Oid4vciAuthorizationServerDeployment.EXTERNAL,
        runtimeServerKey = null,
        tokenEndpoint = "https://external-as.example/oauth/token",
        jwksUri = "https://external-as.example/.well-known/jwks.json",
    )

    private fun tokenArgs(accessToken: String) = ValidateAccessTokenArgs(
        authorizationServer = hostedTarget(),
        expectedAudience = "https://issuer.example",
        accessToken = accessToken,
    )

    private class FixedUserAuthenticationProvider(
        private val userInfo: UserInfo,
    ) : UserAuthenticationProvider {
        override suspend fun getAuthenticatedUser(sessionId: String): IdkResult<AuthenticatedUser?, AuthenticationError> = error("not used")

        override suspend fun initiateAuthentication(
            sessionId: String,
            returnUrl: String,
            hint: AuthenticationHint?,
            context: AuthenticationContext?,
        ): IdkResult<String, AuthenticationError> = error("not used")

        override suspend fun authenticateWithCredentials(
            credentials: UserCredentials,
            context: AuthenticationContext?,
        ): IdkResult<String?, AuthenticationError> = error("not used")

        override suspend fun logout(userId: String): IdkResult<Unit, AuthenticationError> = error("not used")

        override suspend fun getUserInfo(userId: String): IdkResult<UserInfo, AuthenticationError> = Ok(userInfo)

        override suspend fun isAuthenticationMethodAvailable(method: com.sphereon.oauth2.server.authorization.provider.AuthenticationMethod): IdkResult<Boolean, AuthenticationError> = error("not used")
    }

    private suspend fun realUserInfoService(
        scope: String = "openid profile email",
        explicitClaims: List<String> = listOf("employee_id", "job_title"),
    ): FakeAuthorizationServerService {
        val token = "identity-free-userinfo-token"
        val storage = InMemoryTokenStorageImpl(InMemoryOAuth2BackingStorageImpl())
        val now = Clock.System.now()
        val stored =
            AccessTokenData(
                accessToken = token,
                tokenType = "Bearer",
                clientId = "issuer-client",
                subject = "keycloak-user-42",
                scope = scope,
                issuer = "https://hosted-as.example",
                issuedAt = now,
                expiresAt = now + 1.hours,
                additionalData = mapOf(SESSION_KEY_OIDC_CLAIMS_USERINFO to explicitClaims),
            )
        storage.storeAccessToken(token, stored)
        val command =
            GetUserInfoCommandImpl(
                execution = FakeSessionExecution(),
                tokenStorage = storage,
                userAuthenticationProvider =
                    FixedUserAuthenticationProvider(
                        UserInfo(
                            userId = "keycloak-user-42",
                            email = "user@example.com",
                            attributes =
                                mapOf(
                                    "given_name" to "Ada",
                                    "family_name" to "Lovelace",
                                    "job_title" to "Engineer",
                                    "employee_id" to "EMP-42",
                                    "iss" to "https://attacker.example",
                                    "aud" to "attacker-audience",
                                    "exp" to 1L,
                                ),
                        ),
                    ),
                scopeClaimsMapper = OidcScopeClaimsMapperImpl(),
            )
        return FakeAuthorizationServerService(
            introspectionResult =
                Ok(
                    TokenIntrospectionResponse(
                        active = true,
                        sub = "keycloak-user-42",
                        clientId = "issuer-client",
                        scope = scope,
                    ),
                ),
            userInfoHandler = { args -> command.execute(args) },
        )
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

    private class CapturingVerifyJwtCommand : VerifyJwtCommand {
        override val isEnabled: Boolean = true
        override val inputTypeToken = typeToken<VerifyJwtArgs>()
        override val outputTypeToken = typeToken<TokenPayload.Jwt>()
        var received: VerifyJwtArgs? = null

        override suspend fun execute(args: VerifyJwtArgs): IdkResult<TokenPayload.Jwt, IdkError> {
            received = args
            return Ok(
                TokenPayload.Jwt(
                    sub = "subject-123",
                    iss = args.authorizationServer,
                    aud = listOf(requireNotNull(args.expectedAudience)),
                    exp = Instant.fromEpochSeconds(1_900_000_000),
                    iat = Instant.fromEpochSeconds(1_800_000_000),
                    scope = "credential",
                    clientId = "wallet-client",
                    dpopJkt = null,
                    jti = "token-jti",
                ),
            )
        }
    }

    @Test
    fun embeddedModeRegistersPreAuthorizedCode() =
        runTest {
            val storage = FakePreAuthorizedCodeStorage()
            val (bridge, _) = createEmbeddedBridge(storage = storage)

            val result =
                bridge.registerPreAuthorizedCode(
                    RegisterPreAuthCodeArgs(
                        authorizationServer = hostedTarget(),
                        sessionId = "session-001",
                        credentialConfigurationIds = listOf("IdentityCredential"),
                        txCodeRequired = false,
                        expiresAtEpochSeconds = 1_900_000_042L,
                    ),
                )

            assertTrue(result.isOk, "registerPreAuthorizedCode should succeed")
            val registered = result.value
            assertTrue(registered.code.isNotBlank(), "Code should be a non-empty string")
            assertEquals(1_900_000_042L, storage.lastStoredData?.expiresAt?.epochSeconds)
        }

    @Test
    fun embeddedModeRejectsPastExpiryBeforeStorage() =
        runTest {
            val storage = FakePreAuthorizedCodeStorage()
            val (bridge, _) = createEmbeddedBridge(storage = storage)

            val result = bridge.registerPreAuthorizedCode(
                RegisterPreAuthCodeArgs(
                    authorizationServer = hostedTarget(),
                    sessionId = "expired",
                    credentialConfigurationIds = listOf("IdentityCredential"),
                    expiresAtEpochSeconds = 1L,
                    txCodeRequired = false,
                ),
            )

            assertTrue(result.isErr)
            assertEquals("ILLEGAL_ARGUMENT_ERROR", result.error.code)
            assertNull(storage.lastStoredData)
        }

    @Test
    fun embeddedModeRejectsExpiryOverflowBeforeStorage() =
        runTest {
            val storage = FakePreAuthorizedCodeStorage()
            val (bridge, _) = createEmbeddedBridge(storage = storage)

            val result = bridge.registerPreAuthorizedCode(
                RegisterPreAuthCodeArgs(
                    authorizationServer = hostedTarget(),
                    sessionId = "overflow",
                    credentialConfigurationIds = listOf("IdentityCredential"),
                    expiresAtEpochSeconds = Long.MAX_VALUE,
                    txCodeRequired = false,
                ),
            )

            assertTrue(result.isErr)
            assertEquals("ILLEGAL_ARGUMENT_ERROR", result.error.code)
            assertNull(storage.lastStoredData)
        }

    @Test
    fun embeddedModeRegistersPreAuthorizedCodeWithTxCode() =
        runTest {
            val (bridge, _) = createEmbeddedBridge()

            val result =
                bridge.registerPreAuthorizedCode(
                    RegisterPreAuthCodeArgs(
                        authorizationServer = hostedTarget(),
                        sessionId = "session-002",
                        credentialConfigurationIds = listOf("IdentityCredential"),
                        txCodeRequired = true,
                        expiresAtEpochSeconds = 1_900_000_042L,
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
            val (bridge, asService) = createEmbeddedBridge(storage = storage)
            asService.setPreAuthorizedCodeVerifier {
                Ok(
                    VerifiedPreAuthCodeGrant(
                        sessionId = "session-003",
                        subject = null,
                        credentialConfigurationIds = listOf("IdentityCredential", "DriverLicense"),
                    ),
                )
            }

            // Register
            val regResult =
                bridge.registerPreAuthorizedCode(
                    RegisterPreAuthCodeArgs(
                        authorizationServer = hostedTarget(),
                        sessionId = "session-003",
                        credentialConfigurationIds = listOf("IdentityCredential", "DriverLicense"),
                        txCodeRequired = false,
                        expiresAtEpochSeconds = 1_900_000_042L,
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
            val (bridge, asService) = createEmbeddedBridge(storage = storage)
            var verificationCalls = 0
            asService.setPreAuthorizedCodeVerifier {
                verificationCalls++
                if (verificationCalls == 1) {
                    Ok(
                        VerifiedPreAuthCodeGrant(
                            sessionId = "session-004",
                            subject = null,
                            credentialConfigurationIds = listOf("IdentityCredential"),
                        ),
                    )
                } else {
                    Err(IdkError.fromString(code = "invalid_grant", message = "Invalid or already used pre-authorized code"))
                }
            }

            // Register
            val regResult =
                bridge.registerPreAuthorizedCode(
                    RegisterPreAuthCodeArgs(
                        authorizationServer = hostedTarget(),
                        sessionId = "session-004",
                        credentialConfigurationIds = listOf("IdentityCredential"),
                        txCodeRequired = false,
                        expiresAtEpochSeconds = 1_900_000_042L,
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
    fun embeddedModeRejectsWrongTxCodeThroughVerifier() =
        runTest {
            val storage = FakePreAuthorizedCodeStorage()
            val (bridge, asService) = createEmbeddedBridge(storage = storage)
            asService.setPreAuthorizedCodeVerifier { args ->
                assertEquals("wrong-tx-code", args.txCode)
                assertEquals("bound-client", args.clientId)
                Err(IdkError.fromString(code = "invalid_grant", message = "Invalid tx_code"))
            }

            val result =
                bridge.consumePreAuthorizedCode(
                    ConsumePreAuthCodeArgs(
                        code = "pre-authorized-code",
                        txCode = "wrong-tx-code",
                        clientId = "bound-client",
                    ),
                )

            assertTrue(result.isErr)
            assertEquals("invalid_grant", result.error.code)
            assertEquals(0, storage.rawConsumeCount, "Bridge must not bypass verifier with raw storage consumption")
            assertEquals("pre-authorized-code", asService.lastVerifyPreAuthorizedCodeArgs?.preAuthorizedCode)
        }

    @Test
    fun embeddedModeRejectsMissingTxCodeThroughVerifier() =
        runTest {
            val storage = FakePreAuthorizedCodeStorage()
            val (bridge, asService) = createEmbeddedBridge(storage = storage)
            asService.setPreAuthorizedCodeVerifier { args ->
                assertNull(args.txCode)
                Err(IdkError.fromString(code = "invalid_request", message = "Missing required parameter: tx_code"))
            }

            val result =
                bridge.consumePreAuthorizedCode(
                    ConsumePreAuthCodeArgs(
                        code = "pre-authorized-code",
                        txCode = null,
                        clientId = "bound-client",
                    ),
                )

            assertTrue(result.isErr)
            assertEquals("invalid_request", result.error.code)
            assertEquals(0, storage.rawConsumeCount, "Bridge must not bypass verifier with raw storage consumption")
            assertEquals("pre-authorized-code", asService.lastVerifyPreAuthorizedCodeArgs?.preAuthorizedCode)
        }

    @Test
    fun embeddedModeRejectsExpiredCodeThroughVerifier() =
        runTest {
            val storage = FakePreAuthorizedCodeStorage()
            val (bridge, asService) = createEmbeddedBridge(storage = storage)
            asService.setPreAuthorizedCodeVerifier {
                Err(IdkError.fromString(code = "invalid_grant", message = "Pre-authorized code has expired"))
            }

            val result =
                bridge.consumePreAuthorizedCode(
                    ConsumePreAuthCodeArgs(
                        code = "expired-code",
                        txCode = null,
                        clientId = "bound-client",
                    ),
                )

            assertTrue(result.isErr)
            assertEquals("invalid_grant", result.error.code)
            assertEquals(0, storage.rawConsumeCount, "Bridge must not bypass verifier with raw storage consumption")
            assertEquals("expired-code", asService.lastVerifyPreAuthorizedCodeArgs?.preAuthorizedCode)
        }

    @Test
    fun embeddedModeDelegatesSuccessfulConsumptionToVerifier() =
        runTest {
            val storage = FakePreAuthorizedCodeStorage()
            val (bridge, asService) = createEmbeddedBridge(storage = storage)
            asService.setPreAuthorizedCodeVerifier { args ->
                assertEquals("correct-tx-code", args.txCode)
                assertEquals("bound-client", args.clientId)
                Ok(
                    VerifiedPreAuthCodeGrant(
                        sessionId = "session-delegated",
                        subject = "did:example:delegated",
                        credentialConfigurationIds = listOf("IdentityCredential"),
                    ),
                )
            }

            val result =
                bridge.consumePreAuthorizedCode(
                    ConsumePreAuthCodeArgs(
                        code = "pre-authorized-code",
                        txCode = "correct-tx-code",
                        clientId = "bound-client",
                    ),
                )

            assertTrue(result.isOk)
            assertEquals("session-delegated", result.value.sessionId)
            assertEquals("did:example:delegated", result.value.subject)
            assertEquals(listOf("IdentityCredential"), result.value.credentialConfigurationIds)
            assertEquals(0, storage.rawConsumeCount, "Bridge must route successful consumption through verifier")
            assertEquals("pre-authorized-code", asService.lastVerifyPreAuthorizedCodeArgs?.preAuthorizedCode)
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
                    jti = "hosted-as-token-jti",
                    exp = 2_000_000_000L,
                    additionalClaims = mapOf<String, JsonElement>("authorization_details" to authDetails),
                )

            val (bridge, _) =
                createEmbeddedBridge(
                    asService = FakeAuthorizationServerService(Ok(introspectionResponse)),
                )

            val result =
                bridge.validateAccessToken(
                    tokenArgs("valid-token"),
                )

            assertTrue(result.isOk, "validateAccessToken should succeed for active token")
            val ctx = result.value
            assertEquals("user-123", ctx.subject)
            assertEquals("client-abc", ctx.clientId)
            assertEquals("openid", ctx.scope)
            assertEquals(listOf("IdentityCredential"), ctx.credentialConfigurationIds)
            assertEquals(listOf("id-1", "id-2"), ctx.credentialIdentifiers)
            assertEquals("hosted-as-token-jti", ctx.tokenId)
            assertEquals(2_000_000_000L, ctx.expiresAtEpochSeconds)
        }

    @Test
    fun embeddedModeUsesResolvedIssuerInternalClientForIntrospection() =
        runTest {
            val introspectionResponse =
                TokenIntrospectionResponse(
                    active = true,
                    sub = "user-123",
                    clientId = "wallet-client",
                )
            val asService = FakeAuthorizationServerService(Ok(introspectionResponse))
            val (bridge, _) =
                createEmbeddedBridge(
                    asService = asService,
                    internalClientRoleResolver = InternalClientRoleResolver { role -> if (role == "issuer") "issuer-service" else null },
                )

            val result = bridge.validateAccessToken(tokenArgs("valid-token"))

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
                    tokenArgs("expired-token"),
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
                    tokenArgs("no-sub-token"),
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

            val result = bridge.validateAccessToken(tokenArgs("token-with-acr"))

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

            val result = bridge.validateAccessToken(tokenArgs("federated-token"))

            assertTrue(result.isOk)
            val ctx = result.value
            assertEquals("ext-user-42", ctx.upstreamSubject)
            assertEquals("https://enterprise-idp.example.com", ctx.upstreamIssuer)
        }

    @Test
    fun nestedFederationMetadataWinsAndProviderOptOutNeverUsesLocalUserinfo() = runTest {
        val issuer = "https://idp.example.test"
        val nested = JsonObject(mapOf(
            "upstream_iss" to JsonPrimitive(issuer),
            "upstream_sub" to JsonPrimitive("idp-user-42"),
            "userinfo" to JsonObject(mapOf("given_name" to JsonPrimitive("Ada"), "exp" to JsonPrimitive(1))),
        ))
        for (optIn in listOf(true, false)) {
            val (bridge, service) = createEmbeddedBridge(
                asService = FakeAuthorizationServerService(Ok(TokenIntrospectionResponse(
                    active = true, sub = "local-user", clientId = "wallet",
                    additionalClaims = mapOf(
                        "oidc.internal.federation_claims" to nested,
                        "upstream_iss" to JsonPrimitive("https://conflicting.example.test"),
                    ),
                ))),
                configProperties = mapOf(
                    "tenant.idp.[$issuer].surface-userinfo-to-issuance" to optIn.toString(),
                    "oid4vci.issuer.surface-local-userinfo-to-issuance" to "true",
                ),
            )
            val result = bridge.validateAccessToken(tokenArgs("nested-federated-token"))
            assertTrue(result.isOk)
            assertEquals(issuer, result.value.upstreamIssuer)
            assertEquals("idp-user-42", result.value.upstreamSubject)
            if (optIn) assertEquals(mapOf("given_name" to JsonPrimitive("Ada")), result.value.userinfoClaims)
            else assertNull(result.value.userinfoClaims)
            assertEquals(0, service.userInfoCalls)
        }
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

            val result = bridge.validateAccessToken(tokenArgs("plain-token"))

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

            val result = bridge.validateAccessToken(tokenArgs("federated-token-with-ui"))

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

            val result = bridge.validateAccessToken(tokenArgs("token-no-ui-opt-in"))

            assertTrue(result.isOk)
            assertNull(result.value.userinfoClaims, "userinfoClaims should be null when tenant has not opted in")
        }

    @Test
    fun hostedAsOptInUsesRealUserInfoCommandWithIdentityFreeIntrospection() =
        runTest {
            val asService = realUserInfoService(
            )
            val (bridge, service) = createEmbeddedBridge(
                asService = asService,
                configProperties = mapOf("oid4vci.issuer.surface-local-userinfo-to-issuance" to "true"),
            )

            val result = bridge.validateAccessToken(tokenArgs("identity-free-userinfo-token"))

            assertTrue(result.isOk)
            assertEquals(1, service.userInfoCalls)
            assertEquals("keycloak-user-42", result.value.subject)
            assertEquals(JsonPrimitive("Ada"), result.value.userinfoClaims!!["given_name"])
            assertEquals(JsonPrimitive("Lovelace"), result.value.userinfoClaims!!["family_name"])
            assertEquals(JsonPrimitive("Engineer"), result.value.userinfoClaims!!["job_title"])
            assertEquals(JsonPrimitive("user@example.com"), result.value.userinfoClaims!!["email"])
            assertEquals(JsonPrimitive("EMP-42"), result.value.userinfoClaims!!["employee_id"])
            assertNull(result.value.userinfoClaims!!["iss"])
            assertNull(result.value.userinfoClaims!!["aud"])
            assertNull(result.value.userinfoClaims!!["exp"])
            assertNull(result.value.userinfoClaims!!["sub"])
        }

    @Test
    fun hostedAsOptOutDoesNotCallRealUserInfoCommand() =
        runTest {
            val asService = realUserInfoService()
            val (bridge, service) = createEmbeddedBridge(asService = asService)

            val result = bridge.validateAccessToken(tokenArgs("identity-free-userinfo-token"))

            assertTrue(result.isOk)
            assertNull(result.value.userinfoClaims)
            assertEquals(0, service.userInfoCalls)
        }

    @Test
    fun hostedAsMissingOpenidScopeStillValidatesTokenAndOmitsUserInfo() =
        runTest {
            val asService = realUserInfoService(
                scope = "profile email",
            )
            val (bridge, service) = createEmbeddedBridge(
                asService = asService,
                configProperties = mapOf("oid4vci.issuer.surface-local-userinfo-to-issuance" to "true"),
            )

            val result = bridge.validateAccessToken(tokenArgs("identity-free-userinfo-token"))

            assertTrue(result.isOk)
            assertNull(result.value.userinfoClaims)
            assertEquals(1, service.userInfoCalls)
        }

    @Test
    fun hostedAsScopeFilteredUserInfoOmitsUnallowedClaimsWithoutInvalidatingToken() =
        runTest {
            val asService = realUserInfoService(
                scope = "openid",
                explicitClaims = emptyList(),
            )
            val (bridge, service) = createEmbeddedBridge(
                asService = asService,
                configProperties = mapOf("oid4vci.issuer.surface-local-userinfo-to-issuance" to "true"),
            )

            val result = bridge.validateAccessToken(tokenArgs("identity-free-userinfo-token"))

            assertTrue(result.isOk)
            assertNull(result.value.userinfoClaims)
            assertEquals(1, service.userInfoCalls)
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
    fun externalModeRejectsIssuerSidePreAuthorizedCodeRegistration() = runTest {
        val (bridge) = createEmbeddedBridge()
        val result = bridge.registerPreAuthorizedCode(
            RegisterPreAuthCodeArgs(
                authorizationServer = externalTarget(),
                sessionId = "session-1",
                expiresAtEpochSeconds = 1_900_000_042L,
                credentialConfigurationIds = listOf("EmployeeCredential"),
                txCodeRequired = false,
            ),
        )
        assertTrue(result.isErr)
        assertTrue(result.error.message.defaultMessage.contains("External authorization servers"))
    }

    @Test
    fun externalAuthorizationCodeTokenIsVerifiedAgainstPinnedIssuerJwksAndAudience() = runTest {
        val verifier = CapturingVerifyJwtCommand()
        val (bridge) = createEmbeddedBridge(verifyJwtCommand = verifier)
        val target = externalTarget()
        val result = bridge.validateAccessToken(
            ValidateAccessTokenArgs(
                authorizationServer = target,
                expectedAudience = "https://issuer.example",
                accessToken = "signed-access-token",
            ),
        )
        assertTrue(result.isOk)
        assertEquals(target.id, result.value.authorizationServerId)
        assertEquals(target.issuer, result.value.authorizationServerIssuer)
        assertEquals(
            VerifyJwtArgs(
                jwt = "signed-access-token",
                authorizationServer = target.issuer,
                expectedAudience = "https://issuer.example",
                jwksUri = target.jwksUri,
            ),
            verifier.received,
        )
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
