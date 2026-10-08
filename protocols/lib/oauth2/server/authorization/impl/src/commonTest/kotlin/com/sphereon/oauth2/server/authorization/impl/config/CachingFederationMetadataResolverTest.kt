/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.oauth2.server.authorization.impl.config

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.oauth2.client.client.AuthorizationResult
import com.sphereon.oauth2.client.client.DpopContext
import com.sphereon.oauth2.client.client.OAuth2Client
import com.sphereon.oauth2.client.command.FetchUserInfoResult
import com.sphereon.oauth2.client.model.PkceData
import com.sphereon.oauth2.common.model.AuthorizationResponse
import com.sphereon.oauth2.common.model.AuthorizationServerMetadata
import com.sphereon.oauth2.common.model.ClientAuthenticationConfig
import com.sphereon.oauth2.common.model.IdTokenValidationOptions
import com.sphereon.oauth2.common.model.TokenIntrospectionResponse
import com.sphereon.oauth2.common.model.TokenResponse
import com.sphereon.oauth2.common.model.ValidatedIdToken
import com.sphereon.oauth2.server.authorization.config.FederationProviderConfig
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class CachingFederationMetadataResolverTest {
    @Test
    fun firstCallFetchesAndSecondCallIsCached() =
        runTest {
            val client = CountingOAuth2Client()
            val resolver =
                CachingFederationMetadataResolver(
                    oauth2Client = client,
                    providersProvider = { listOf(keycloakConfig) },
                    ttl = 1.hours,
                    clock = Clock.System,
                )

            val first = resolver.resolve(keycloakConfig)
            val second = resolver.resolve(keycloakConfig)

            assertTrue(first.isOk && second.isOk)
            assertEquals(keycloakConfig.issuerUrl, first.value.issuer)
            assertEquals(1, client.fetchCount, "second resolve should hit the cache, not the upstream")
        }

    @Test
    fun invalidateCausesRefetch() =
        runTest {
            val client = CountingOAuth2Client()
            val resolver =
                CachingFederationMetadataResolver(
                    oauth2Client = client,
                    providersProvider = { listOf(keycloakConfig) },
                    ttl = 1.hours,
                    clock = Clock.System,
                )

            resolver.resolve(keycloakConfig)
            resolver.invalidate(keycloakConfig)
            resolver.resolve(keycloakConfig)

            assertEquals(2, client.fetchCount, "invalidate should evict + force next fetch")
        }

    @Test
    fun expiredEntryCausesRefetch() =
        runTest {
            val client = CountingOAuth2Client()
            val fakeClock = AdvancingClock(Instant.fromEpochSeconds(0))
            val resolver =
                CachingFederationMetadataResolver(
                    oauth2Client = client,
                    providersProvider = { listOf(keycloakConfig) },
                    ttl = 1.minutes,
                    clock = fakeClock,
                )

            resolver.resolve(keycloakConfig)
            fakeClock.advance(2.minutes)
            resolver.resolve(keycloakConfig)

            assertEquals(2, client.fetchCount, "entry past TTL should trigger refetch")
        }

    @Test
    fun discoveryDisabledBypassesFetch() =
        runTest {
            val client = CountingOAuth2Client()
            val manualConfig =
                keycloakConfig.copy(
                    discoveryEnabled = false,
                    tokenEndpointOverride = "https://manual.example/token",
                )
            val resolver =
                CachingFederationMetadataResolver(
                    oauth2Client = client,
                    providersProvider = { listOf(manualConfig) },
                )

            val result = resolver.resolve(manualConfig)

            assertTrue(result.isOk)
            assertEquals(0, client.fetchCount, "discoveryEnabled=false must not call upstream")
            assertEquals("https://manual.example/token", result.value.tokenEndpoint)
        }

    @Test
    fun findByIssuerReturnsConfigAndMetadata() =
        runTest {
            val client = CountingOAuth2Client()
            val resolver =
                CachingFederationMetadataResolver(
                    oauth2Client = client,
                    providersProvider = { listOf(keycloakConfig) },
                )

            val resolved = resolver.findByIssuer(keycloakConfig.issuerUrl)

            assertNotNull(resolved)
            assertEquals(keycloakConfig.id, resolved.config.id)
            assertEquals(keycloakConfig.issuerUrl, resolved.metadata.issuer)
        }

    @Test
    fun findByIssuerUnknownReturnsNull() =
        runTest {
            val client = CountingOAuth2Client()
            val resolver =
                CachingFederationMetadataResolver(
                    oauth2Client = client,
                    providersProvider = { listOf(keycloakConfig) },
                )

            assertNull(resolver.findByIssuer("https://other.example/realms/foo"))
            assertEquals(0, client.fetchCount, "unknown issuer should not trigger any fetch")
        }

    private val keycloakConfig =
        FederationProviderConfig(
            id = "keycloak",
            name = "Keycloak",
            issuerUrl = "https://keycloak.example/realms/vdx",
            clientId = "vdx-sts",
        )

    private class CountingOAuth2Client : OAuth2Client {
        var fetchCount: Int = 0

        override suspend fun fetchAuthorizationServerMetadata(issuer: String): IdkResult<AuthorizationServerMetadata, IdkError> {
            fetchCount += 1
            return Ok(
                AuthorizationServerMetadata(
                    issuer = issuer,
                    tokenEndpoint = "$issuer/token",
                    authorizationEndpoint = "$issuer/auth",
                    jwksUri = "$issuer/jwks",
                    idTokenSigningAlgValuesSupported = listOf("RS256"),
                    endSessionEndpoint = "$issuer/logout",
                    backchannelLogoutSupported = true,
                ),
            )
        }

        override fun isDpopSupported(authorizationServerMetadata: AuthorizationServerMetadata): Boolean = false

        override suspend fun initiateAuthorization(
            authorizationServerMetadata: AuthorizationServerMetadata,
            clientId: String,
            redirectUri: String,
            scope: String?,
            state: String?,
            resource: List<String>?,
            clientAuthentication: ClientAuthenticationConfig?,
            dpopContext: DpopContext?,
            additionalParameters: Map<String, String>,
        ): IdkResult<AuthorizationResult, IdkError> = notImpl()

        override suspend fun parseAuthorizationResponse(redirectUrl: String): IdkResult<AuthorizationResponse, IdkError> = notImpl()

        override suspend fun exchangeAuthorizationCode(
            authorizationServerMetadata: AuthorizationServerMetadata,
            clientAuthentication: ClientAuthenticationConfig,
            authorizationCode: String,
            redirectUri: String,
            pkceData: PkceData?,
            resource: List<String>?,
            dpopContext: DpopContext?,
            audience: List<String>?,
        ): IdkResult<TokenResponse, IdkError> = notImpl()

        override suspend fun exchangePreAuthorizedCode(
            authorizationServerMetadata: AuthorizationServerMetadata,
            clientAuthentication: ClientAuthenticationConfig,
            preAuthorizedCode: String,
            txCode: String?,
            resource: List<String>?,
            dpopContext: DpopContext?,
        ): IdkResult<TokenResponse, IdkError> = notImpl()

        override suspend fun refreshAccessToken(
            authorizationServerMetadata: AuthorizationServerMetadata,
            clientAuthentication: ClientAuthenticationConfig,
            refreshToken: String,
            scope: String?,
            resource: List<String>?,
            dpopContext: DpopContext?,
            audience: List<String>?,
        ): IdkResult<TokenResponse, IdkError> = notImpl()

        override suspend fun introspectToken(
            authorizationServerMetadata: AuthorizationServerMetadata,
            clientAuthentication: ClientAuthenticationConfig,
            token: String,
            tokenTypeHint: String?,
        ): IdkResult<TokenIntrospectionResponse, IdkError> = notImpl()

        override suspend fun validateIdToken(
            idToken: String,
            options: IdTokenValidationOptions,
        ): IdkResult<ValidatedIdToken, IdkError> = notImpl()

        override suspend fun fetchUserInfo(
            accessToken: String,
            metadata: AuthorizationServerMetadata,
        ): IdkResult<FetchUserInfoResult, IdkError> = notImpl()

        override suspend fun initiateOidcLogin(
            issuer: String,
            clientId: String,
            redirectUri: String,
            scopes: Set<String>,
            responseMode: com.sphereon.oauth2.common.model.OAuth2ResponseMode,
            prompt: String?,
            loginHint: String?,
            tenantId: String?,
            resource: String?,
            audience: String?,
            ownerHandleDigest: String?,
            grantBinding: String?,
            clientCorrelation: String?,
        ): IdkResult<com.sphereon.oauth2.client.client.OidcLoginInitiation, IdkError> = notImpl()

        override suspend fun initiateOidcLogin(
            authorizationServerMetadata: AuthorizationServerMetadata,
            clientId: String,
            redirectUri: String,
            scopes: Set<String>,
            responseMode: com.sphereon.oauth2.common.model.OAuth2ResponseMode,
            prompt: String?,
            loginHint: String?,
            tenantId: String?,
            resource: String?,
            audience: String?,
            ownerHandleDigest: String?,
            grantBinding: String?,
            clientCorrelation: String?,
        ): IdkResult<com.sphereon.oauth2.client.client.OidcLoginInitiation, IdkError> = notImpl()

        override val oidcLogin: com.sphereon.oauth2.client.client.OidcLoginApi
            get() = error("not implemented in CountingOAuth2Client")

        private fun <V> notImpl(): IdkResult<V, IdkError> = Err(IdkError(code = "not_implemented", message = IdkError.Message(i18nKey = "", defaultMessage = "stub")))
    }

    private class AdvancingClock(
        private var current: Instant
    ) : Clock {
        override fun now(): Instant = current

        fun advance(by: kotlin.time.Duration) {
            current = current + by
        }
    }
}
