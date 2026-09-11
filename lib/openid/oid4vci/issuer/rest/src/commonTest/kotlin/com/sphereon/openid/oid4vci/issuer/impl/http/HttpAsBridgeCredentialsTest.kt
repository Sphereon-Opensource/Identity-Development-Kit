/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.openid.oid4vci.issuer.impl.http

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.conf.ConfigLevel
import com.sphereon.core.api.conf.ConfigService
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.conf.OpaqueSecretResolver
import com.sphereon.core.api.conf.PrincipalConfigService
import com.sphereon.core.api.conf.TenantConfigService
import com.sphereon.ktor.http.client.provider.HttpClientEngineType
import com.sphereon.ktor.http.client.provider.HttpClientFactory
import com.sphereon.ktor.http.client.provider.HttpClientOptions
import com.sphereon.oauth2.common.command.VerifyDpopProofCommand
import com.sphereon.oauth2.common.model.VerifyDpopProofOptions
import com.sphereon.oauth2.common.model.VerifyDpopProofResult
import com.sphereon.oauth2.server.authorization.command.GetUserInfoArgs
import com.sphereon.oauth2.server.authorization.command.UserInfoResponse
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
import com.sphereon.oauth2.server.authorization.dpop.DpopProofJtiCache
import com.sphereon.oauth2.server.resource.command.VerifyJwtArgs
import com.sphereon.oauth2.server.resource.command.VerifyJwtCommand
import com.sphereon.oauth2.server.resource.model.TokenPayload
import com.sphereon.openid.oid4vci.issuer.authorization.Oid4vciAuthorizationServerDeployment
import com.sphereon.openid.oid4vci.issuer.bridge.Oid4vciAuthorizationServerTarget
import com.sphereon.openid.oid4vci.issuer.bridge.RegisterPreAuthCodeArgs
import com.sphereon.openid.oid4vci.issuer.bridge.ValidateAccessTokenArgs
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

class HttpAsBridgeCredentialsTest {
    private class RecordingAsClient(
        private val additionalClaims: JsonObject = JsonObject(emptyMap()),
    ) : Oid4vciAsInternalClient {
        var registrationCalls = 0
        var introspectionCalls = 0

        override suspend fun registerPreAuthorizedCode(request: PreAuthRegistrationRequest): IdkResult<Unit, IdkError> {
            registrationCalls++
            return Ok(Unit)
        }

        override suspend fun introspectAccessToken(
            token: String,
            authorizationServer: Oid4vciAuthorizationServerTarget,
        ): IdkResult<JsonObject, IdkError> {
            introspectionCalls++
            return Ok(
                buildJsonObject {
                    put("active", true)
                    put("sub", "keycloak-user-42")
                    put("client_id", "issuer-client")
                    put("scope", "openid profile email")
                    put("additionalClaims", additionalClaims)
                },
            )
        }
    }

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

    private suspend fun realUserInfoCommand(): GetUserInfoCommandImpl {
        val token = "testtoken"
        val storage = InMemoryTokenStorageImpl(InMemoryOAuth2BackingStorageImpl())
        val now = Clock.System.now()
        storage.storeAccessToken(
            token,
            AccessTokenData(
                accessToken = token,
                tokenType = "Bearer",
                clientId = "issuer-client",
                subject = "keycloak-user-42",
                scope = "openid profile email",
                issuer = "https://as.example",
                issuedAt = now,
                expiresAt = now + 1.hours,
                additionalData = mapOf(SESSION_KEY_OIDC_CLAIMS_USERINFO to listOf("employee_id", "job_title")),
            ),
        )
        return GetUserInfoCommandImpl(
            execution = TestSessionExecution(),
            tokenStorage = storage,
            userAuthenticationProvider =
                FixedUserAuthenticationProvider(
                    UserInfo(
                        userId = "keycloak-user-42",
                        email = "user@example.com",
                        attributes = mapOf(
                            "given_name" to "Ada",
                            "family_name" to "Lovelace",
                            "job_title" to "Engineer",
                            "employee_id" to "EMP-42",
                            "iss" to "https://attacker.example",
                            "exp" to 1L,
                        ),
                    ),
                ),
            scopeClaimsMapper = OidcScopeClaimsMapperImpl(),
        )
    }

    @Test
    fun activeTenantAuthorizationServerIssuerClientTakesPrecedence() {
        val properties = mapOf(
            "oauth2.servers.default-server" to "acme",
            "oauth2.servers.acme.internal-clients.issuer.client-id" to "issuer-service:tenant-123",
            "oauth2.servers.acme.internal-clients.issuer.client-secret-id" to "secret_tenant_issuer_01",
            "oid4vci.issuer.as-bridge.client-id" to "bootstrap-issuer",
            "oid4vci.issuer.as-bridge.client-secret-id" to "secret_bootstrap_issuer_01",
        )

        assertEquals(
            AsBridgeClientCredentialReference("issuer-service:tenant-123", "secret_tenant_issuer_01"),
            resolveAsBridgeClientCredentialReference(properties::get),
        )
    }

    @Test
    fun explicitBridgeCredentialRemainsStandaloneFallback() {
        val properties = mapOf(
            "oid4vci.issuer.as-bridge.client-id" to "standalone-issuer",
            "oid4vci.issuer.as-bridge.client-secret-id" to "secret_standalone_issuer_01",
        )

        assertEquals(
            AsBridgeClientCredentialReference("standalone-issuer", "secret_standalone_issuer_01"),
            resolveAsBridgeClientCredentialReference(properties::get),
        )
    }

    @Test
    fun scopedClientIdIsFormEncodedBeforeBasicAuthJoining() {
        val credentials = AsBridgeClientCredentials("issuer-service:tenant-123", "tenant-secret")

        assertEquals(
            "Basic aXNzdWVyLXNlcnZpY2UlM0F0ZW5hbnQtMTIzOnRlbmFudC1zZWNyZXQ=",
            credentials.toBasicAuthHeader(),
        )
    }

    @Test
    fun hostedHttpBridgeUsesRealUserInfoCommandWithExactBearerToken() =
        runTest {
            val userInfoCommand = realUserInfoCommand()
            var userInfoCalls = 0
            val httpClient =
                HttpClient(
                    MockEngine { request ->
                        assertEquals("/userinfo", request.url.encodedPath)
                        assertEquals("Bearer testtoken", request.headers[HttpHeaders.Authorization])
                        userInfoCalls++
                        val response =
                            userInfoCommand.execute(GetUserInfoArgs("testtoken")).getOrElse {
                                error("real UserInfo command failed: $it")
                            }
                        respond(
                            Json.encodeToString(UserInfoResponse.serializer(), response),
                            HttpStatusCode.OK,
                            headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                        )
                    },
                )
            try {
                val bridge =
                    HttpAsBridge(
                        execution =
                            TestSessionExecution(
                                principalConfigService =
                                    RecordingPrincipalConfigService(
                                        mapOf(HttpAsBridge.SURFACE_LOCAL_USERINFO_KEY to "true"),
                                    ),
                            ),
                        httpClientFactory = FixedHttpClientFactory(httpClient),
                        verifyDpopProofCommand = UnusedVerifyDpopProofCommand,
                        dpopProofJtiCache = UnusedDpopProofJtiCache,
                        asBaseUrlResolver = FixedAsBaseUrlResolver,
                        asInternalClient = RecordingAsClient(),
                        verifyJwtCommand = UnusedVerifyJwtCommand,
                    )

                val result =
                    bridge.validateAccessToken(
                        ValidateAccessTokenArgs(
                            authorizationServer =
                                Oid4vciAuthorizationServerTarget(
                                    id = "as",
                                    issuer = "https://as.example",
                                    deployment = Oid4vciAuthorizationServerDeployment.HOSTED,
                                    runtimeServerKey = "default",
                                    tokenEndpoint = "https://as.example/token",
                                ),
                            expectedAudience = "https://issuer.example",
                            accessToken = "testtoken",
                            ),
                    )

                assertTrue(result.isOk)
                assertEquals("Ada", result.value.userinfoClaims!!["given_name"]?.toString()?.trim('"'))
                assertEquals("Lovelace", result.value.userinfoClaims!!["family_name"]?.toString()?.trim('"'))
                assertEquals("Engineer", result.value.userinfoClaims!!["job_title"]?.toString()?.trim('"'))
                assertEquals("user@example.com", result.value.userinfoClaims!!["email"]?.toString()?.trim('"'))
                assertEquals("EMP-42", result.value.userinfoClaims!!["employee_id"]?.toString()?.trim('"'))
                assertTrue("iss" !in result.value.userinfoClaims.orEmpty())
                assertTrue("exp" !in result.value.userinfoClaims.orEmpty())
                assertTrue("sub" !in result.value.userinfoClaims.orEmpty())
                assertEquals(1, userInfoCalls)
            } finally {
                httpClient.close()
            }
        }

    @Test
    fun invalidExpiryIsRejectedBeforeSeparateProcessRegistrationCall() =
        runTest {
            val asClient = RecordingAsClient()
            val bridge =
                HttpAsBridge(
                    execution = TestSessionExecution(),
                    httpClientFactory = UnusedHttpClientFactory,
                    verifyDpopProofCommand = UnusedVerifyDpopProofCommand,
                    dpopProofJtiCache = UnusedDpopProofJtiCache,
                    asBaseUrlResolver = UnusedAsBaseUrlResolver,
                    asInternalClient = asClient,
                    verifyJwtCommand = UnusedVerifyJwtCommand,
                )
            val target =
                Oid4vciAuthorizationServerTarget(
                    id = "as",
                    issuer = "https://as.example",
                    deployment = Oid4vciAuthorizationServerDeployment.HOSTED,
                    runtimeServerKey = "default",
                    tokenEndpoint = "https://as.example/token",
                )

            for (expiry in listOf(1L, Long.MAX_VALUE)) {
                val result =
                    bridge.registerPreAuthorizedCode(
                        RegisterPreAuthCodeArgs(
                            authorizationServer = target,
                            sessionId = "session",
                            credentialConfigurationIds = listOf("cfg"),
                            expiresAtEpochSeconds = expiry,
                            txCodeRequired = false,
                        ),
                    )
                assertTrue(result.isErr)
                assertEquals("ILLEGAL_ARGUMENT_ERROR", result.error.code)
            }
            assertEquals(0, asClient.registrationCalls)
        }

    @Test
    fun hostedFederationMetadataIsProjectedOnlyWithExactIssuerOptIn() = runTest {
        val issuer = "https://idp.example.test"
        val extensions = buildJsonObject {
            put("upstream_iss", "https://conflicting-flat.example.test")
            put("oidc.internal.federation_claims", buildJsonObject {
                put("upstream_iss", issuer)
                put("upstream_sub", "idp-user-42")
                put("userinfo", buildJsonObject {
                    put("given_name", "Ada")
                    put("employee_id", "EMP-42")
                    put("iss", "https://untrusted.example.test")
                    put("sub", "untrusted-subject")
                    put("exp", 1)
                })
            })
        }
        for (optIn in listOf(true, false)) {
            val httpFactory = RecordingHttpClientFactory()
            val bridge = HttpAsBridge(
                execution = TestSessionExecution(principalConfigService = RecordingPrincipalConfigService(mapOf(
                    "tenant.idp.[$issuer].surface-userinfo-to-issuance" to optIn.toString(),
                    // A local-user opt-in must not bypass a federated provider's opt-out.
                    HttpAsBridge.SURFACE_LOCAL_USERINFO_KEY to "true",
                ))),
                httpClientFactory = httpFactory,
                verifyDpopProofCommand = UnusedVerifyDpopProofCommand,
                dpopProofJtiCache = UnusedDpopProofJtiCache,
                asBaseUrlResolver = FixedAsBaseUrlResolver,
                asInternalClient = RecordingAsClient(extensions),
                verifyJwtCommand = UnusedVerifyJwtCommand,
            )
            val result = bridge.validateAccessToken(ValidateAccessTokenArgs(
                authorizationServer = Oid4vciAuthorizationServerTarget(
                    id = "as", issuer = "https://as.example",
                    deployment = Oid4vciAuthorizationServerDeployment.HOSTED,
                    runtimeServerKey = "default", tokenEndpoint = "https://as.example/token",
                ),
                expectedAudience = "https://issuer.example", accessToken = "testtoken",
            ))
            assertTrue(result.isOk)
            assertEquals(issuer, result.value.upstreamIssuer)
            assertEquals("idp-user-42", result.value.upstreamSubject)
            if (optIn) {
                assertEquals(buildJsonObject { put("given_name", "Ada"); put("employee_id", "EMP-42") }, result.value.userinfoClaims)
            } else {
                assertNull(result.value.userinfoClaims)
            }
            assertEquals(0, httpFactory.createCalls, "Federated opt-out must not fall back to local UserInfo")
        }
    }

    @Test
    fun malformedNestedFederationMetadataCannotUseLegacyIssuerOrLocalFallback() = runTest {
        val issuer = "https://idp.example.test"
        for (metadata in listOf(
            JsonPrimitive("not-an-object"),
            buildJsonObject { put("upstream_iss", 42) },
            buildJsonObject { put("upstream_iss", " ") },
            buildJsonObject { put("userinfo", buildJsonObject { put("given_name", "Ada") }) },
        )) {
            val httpFactory = RecordingHttpClientFactory()
            val bridge = HttpAsBridge(
                execution = TestSessionExecution(principalConfigService = RecordingPrincipalConfigService(mapOf(
                    "tenant.idp.[$issuer].surface-userinfo-to-issuance" to "true",
                    HttpAsBridge.SURFACE_LOCAL_USERINFO_KEY to "true",
                ))),
                httpClientFactory = httpFactory,
                verifyDpopProofCommand = UnusedVerifyDpopProofCommand,
                dpopProofJtiCache = UnusedDpopProofJtiCache,
                asBaseUrlResolver = FixedAsBaseUrlResolver,
                asInternalClient = RecordingAsClient(buildJsonObject {
                    put("oidc.internal.federation_claims", metadata)
                    put("upstream_iss", issuer)
                    put("given_name", "Legacy")
                }),
                verifyJwtCommand = UnusedVerifyJwtCommand,
            )
            val result = bridge.validateAccessToken(ValidateAccessTokenArgs(
                authorizationServer = Oid4vciAuthorizationServerTarget(
                    id = "as", issuer = "https://as.example",
                    deployment = Oid4vciAuthorizationServerDeployment.HOSTED,
                    runtimeServerKey = "default", tokenEndpoint = "https://as.example/token",
                ), expectedAudience = "https://issuer.example", accessToken = "testtoken",
            ))
            assertTrue(result.isOk)
            assertNull(result.value.upstreamIssuer)
            assertNull(result.value.userinfoClaims)
            assertEquals(0, httpFactory.createCalls)
        }
    }

    @Test
    fun externalJwtCannotAssertHostedFederationProvenance() = runTest {
        val issuer = "https://idp.example.test"
        val asClient = RecordingAsClient()
        val verifier = object : VerifyJwtCommand {
            override val isEnabled = true
            override val inputTypeToken = typeToken<VerifyJwtArgs>()
            override val outputTypeToken = typeToken<TokenPayload.Jwt>()
            override suspend fun execute(args: VerifyJwtArgs): IdkResult<TokenPayload.Jwt, IdkError> = Ok(TokenPayload.Jwt(
                sub = "external-subject", iss = args.authorizationServer,
                aud = listOf(requireNotNull(args.expectedAudience)),
                exp = Instant.fromEpochSeconds(1_900_000_000), iat = Instant.fromEpochSeconds(1_800_000_000),
                scope = "openid", clientId = "wallet", dpopJkt = null, jti = "external-token-id",
                additionalClaims = mapOf("oidc.internal.federation_claims" to buildJsonObject {
                    put("upstream_iss", issuer)
                    put("userinfo", buildJsonObject { put("given_name", "Injected") })
                }),
            ))
        }
        val bridge = HttpAsBridge(
            execution = TestSessionExecution(principalConfigService = RecordingPrincipalConfigService(mapOf(
                "tenant.idp.[$issuer].surface-userinfo-to-issuance" to "true",
            ))),
            httpClientFactory = UnusedHttpClientFactory,
            verifyDpopProofCommand = UnusedVerifyDpopProofCommand, dpopProofJtiCache = UnusedDpopProofJtiCache,
            asBaseUrlResolver = UnusedAsBaseUrlResolver, asInternalClient = asClient, verifyJwtCommand = verifier,
        )
        val result = bridge.validateAccessToken(ValidateAccessTokenArgs(
            authorizationServer = Oid4vciAuthorizationServerTarget(
                id = "external", issuer = "https://external-as.example",
                deployment = Oid4vciAuthorizationServerDeployment.EXTERNAL,
                tokenEndpoint = "https://external-as.example/token", jwksUri = "https://external-as.example/jwks",
            ), expectedAudience = "https://issuer.example", accessToken = "external-token",
        ))
        assertTrue(result.isOk)
        assertNull(result.value.upstreamIssuer)
        assertNull(result.value.userinfoClaims)
        assertEquals(0, asClient.introspectionCalls)
    }

    @Test
    fun invalidExpiryIsRejectedBeforeLegacyCredentialLookupOrHttpCall() =
        runTest {
            val secretResolver = RecordingOpaqueSecretResolver()
            val httpClientFactory = RecordingHttpClientFactory()
            val bridge =
                HttpAsBridge(
                    execution =
                        TestSessionExecution(
                            principalConfigService =
                                RecordingPrincipalConfigService(
                                    mapOf(
                                        "oid4vci.issuer.as-bridge.client-secret-id" to "secret-id",
                                    ),
                                ),
                        ),
                    httpClientFactory = UnusedHttpClientFactory,
                    verifyDpopProofCommand = UnusedVerifyDpopProofCommand,
                    dpopProofJtiCache = UnusedDpopProofJtiCache,
                    asBaseUrlResolver = UnusedAsBaseUrlResolver,
                    asInternalClient =
                        LegacyBasicAuthOid4vciAsInternalClient(
                            execution =
                                TestSessionExecution(
                                    principalConfigService =
                                        RecordingPrincipalConfigService(
                                            mapOf(
                                                "oid4vci.issuer.as-bridge.client-secret-id" to "secret-id",
                                            ),
                                        ),
                                ),
                            httpClientFactory = httpClientFactory,
                            asBaseUrlResolver = UnusedAsBaseUrlResolver,
                            opaqueSecretResolver = secretResolver,
                        ),
                    verifyJwtCommand = UnusedVerifyJwtCommand,
                )
            val target =
                Oid4vciAuthorizationServerTarget(
                    id = "as",
                    issuer = "https://as.example",
                    deployment = Oid4vciAuthorizationServerDeployment.HOSTED,
                    runtimeServerKey = "default",
                    tokenEndpoint = "https://as.example/token",
                )

            for (expiry in listOf(1L, Long.MAX_VALUE)) {
                val result =
                    bridge.registerPreAuthorizedCode(
                        RegisterPreAuthCodeArgs(
                            authorizationServer = target,
                            sessionId = "session",
                            credentialConfigurationIds = listOf("cfg"),
                            expiresAtEpochSeconds = expiry,
                            txCodeRequired = false,
                        ),
                    )
                assertTrue(result.isErr)
                assertEquals("ILLEGAL_ARGUMENT_ERROR", result.error.code)
            }
            assertEquals(0, secretResolver.lookupCalls)
            assertEquals(0, httpClientFactory.createCalls)
        }

    private object UnusedHttpClientFactory : HttpClientFactory {
        override fun createClient(options: HttpClientOptions): HttpClient =
            error("HTTP client must not be created for invalid expiry")

        override fun isSupportedOptions(options: HttpClientOptions): Boolean = false

        override fun getEngineTypesSupported(): List<HttpClientEngineType> = emptyList()

        override fun getEngineTypeDefault(): HttpClientEngineType = HttpClientEngineType.CIO
    }

    private class FixedHttpClientFactory(
        private val client: HttpClient,
    ) : HttpClientFactory {
        override fun createClient(options: HttpClientOptions): HttpClient = client

        override fun isSupportedOptions(options: HttpClientOptions): Boolean = true

        override fun getEngineTypesSupported(): List<HttpClientEngineType> = listOf(HttpClientEngineType.CIO)

        override fun getEngineTypeDefault(): HttpClientEngineType = HttpClientEngineType.CIO
    }

    private object FixedAsBaseUrlResolver : Oid4vciAsBridgeBaseUrlResolver {
        override suspend fun resolveAsBaseUrl(): String = "https://as.example"
    }

    private object UnusedVerifyDpopProofCommand : VerifyDpopProofCommand {
        override val isEnabled: Boolean = true
        override val inputTypeToken = typeToken<VerifyDpopProofOptions>()
        override val outputTypeToken = typeToken<VerifyDpopProofResult>()

        override suspend fun execute(args: VerifyDpopProofOptions): IdkResult<VerifyDpopProofResult, IdkError> =
            error("DPoP verification must not run for invalid expiry")
    }

    private object UnusedDpopProofJtiCache : DpopProofJtiCache {
        override suspend fun hasBeenUsed(jti: String): Boolean =
            error("DPoP replay lookup must not run for invalid expiry")

        override suspend fun markAsUsed(jti: String, expiresAt: Instant) =
            error("DPoP replay recording must not run for invalid expiry")

        override suspend fun clear() = Unit
    }

    private object UnusedAsBaseUrlResolver : Oid4vciAsBridgeBaseUrlResolver {
        override suspend fun resolveAsBaseUrl(): String? =
            error("AS base URL resolution must not run for invalid expiry")
    }

    private object UnusedVerifyJwtCommand : VerifyJwtCommand {
        override val isEnabled: Boolean = true
        override val inputTypeToken = typeToken<VerifyJwtArgs>()
        override val outputTypeToken = typeToken<TokenPayload.Jwt>()

        override suspend fun execute(args: VerifyJwtArgs): IdkResult<TokenPayload.Jwt, IdkError> =
            error("JWT verification must not run for invalid expiry")
    }

    private class RecordingOpaqueSecretResolver : OpaqueSecretResolver {
        var lookupCalls = 0

        override suspend fun resolve(secretId: String): IdkResult<String, IdkError> {
            lookupCalls++
            return Ok("unused-secret")
        }
    }

    private class RecordingHttpClientFactory : HttpClientFactory {
        var createCalls = 0

        override fun createClient(options: HttpClientOptions): HttpClient {
            createCalls++
            error("HTTP client must not be created for invalid expiry")
        }

        override fun isSupportedOptions(options: HttpClientOptions): Boolean = false

        override fun getEngineTypesSupported(): List<HttpClientEngineType> = emptyList()

        override fun getEngineTypeDefault(): HttpClientEngineType = HttpClientEngineType.CIO
    }

    private class RecordingPrincipalConfigService(
        private val properties: Map<String, String>,
    ) : PrincipalConfigService,
        ConfigService by NoOpAppConfigService {
        override val parent: TenantConfigService
            get() = error("parent not used in invalid-expiry test")

        override val configLevel: ConfigLevel = ConfigLevel.PRINCIPAL

        override fun containsProperty(key: String): Boolean = properties.containsKey(key)

        override fun getPropertyAsString(key: String, defaultValue: String?): String? =
            properties[key] ?: defaultValue
    }
}
