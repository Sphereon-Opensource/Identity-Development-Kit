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

package com.sphereon.oauth2.server.authorization.impl.command.introspection

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.oauth2.common.config.FeaturePolicy
import com.sphereon.oauth2.common.config.InternalClientConfig
import com.sphereon.oauth2.common.config.OAuth2ServerInstanceConfig
import com.sphereon.oauth2.common.config.OAuth2ServersConfig
import com.sphereon.oauth2.server.authorization.command.IntrospectTokenArgs
import com.sphereon.oauth2.server.authorization.error.AuthorizationServerError
import com.sphereon.oauth2.server.authorization.impl.testutil.OAuth2ServerTestContext
import com.sphereon.oauth2.server.authorization.impl.testutil.TestOAuth2ServersConfigProvider
import com.sphereon.oauth2.server.authorization.model.AccessTokenData
import com.sphereon.oauth2.server.authorization.model.RefreshTokenData
import com.sphereon.oauth2.server.authorization.storage.TokenStorage
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

class AuthServerIntrospectTokenCommandImplTest {
    private val ctx = OAuth2ServerTestContext("introspect-test", this)

    private fun command(
        storage: TokenStorage,
        internalClients: Map<String, InternalClientConfig> = emptyMap(),
        opaqueInternalClientIds: Set<String> = emptySet(),
    ): AuthServerIntrospectTokenCommandImpl =
        AuthServerIntrospectTokenCommandImpl(
            execution = ctx.execution,
            tokenStorage = storage,
            configProvider =
                TestOAuth2ServersConfigProvider(
                    OAuth2ServersConfig(
                        servers =
                            mapOf(
                                "default" to
                                    OAuth2ServerInstanceConfig(
                                        issuer = "https://auth.example.com",
                                        introspection = FeaturePolicy.SUPPORTED,
                                        internalClients = internalClients,
                                    ),
                            ),
                    ),
                ),
            internalClientAuthorizer =
                InternalIntrospectionClientAuthorizer { clientId ->
                    Ok(
                        internalClients.values.any { it.clientId == clientId } ||
                            opaqueInternalClientIds.contains(clientId),
                    )
                },
        )

    private fun accessToken(clientId: String): AccessTokenData {
        val now = Clock.System.now()
        return AccessTokenData(
            accessToken = "opaque-access",
            tokenType = "Bearer",
            clientId = clientId,
            subject = "user-1",
            scope = "read write",
            issuer = "https://auth.example.com",
            issuedAt = now,
            expiresAt = now + 10.minutes,
        )
    }

    @Test
    fun introspect_wrongClient_returnsInactive() =
        runTest {
            val store = FixedTokenStorage(accessToken = accessToken(clientId = "owner-client"))
            val cmd = command(store)

            val result = cmd.execute(IntrospectTokenArgs(token = "opaque-access", clientId = "impostor-client"))

            assertTrue(result.isOk)
            assertEquals(false, result.value.active, "RFC 7662 §2.2 — foreign caller sees active=false")
        }

    @Test
    fun introspect_ownClient_returnsActiveWithClaims() =
        runTest {
            val store = FixedTokenStorage(accessToken = accessToken(clientId = "owner-client"))
            val cmd = command(store)

            val result = cmd.execute(IntrospectTokenArgs(token = "opaque-access", clientId = "owner-client"))

            assertTrue(result.isOk)
            val response = result.value
            assertEquals(true, response.active)
            assertEquals("owner-client", response.clientId)
            assertEquals("user-1", response.sub)
            assertEquals("read write", response.scope)
            assertTrue(response.additionalClaims.isEmpty(), "Internal token metadata must not be disclosed to ordinary clients")
        }

    @Test
    fun introspect_internalResourceServer_receivesStoredExtensionClaims() =
        runTest {
            val token =
                accessToken(clientId = "owner-client").copy(
                    additionalData =
                        mapOf(
                            "oid4vci.internal.issuer_state" to "offer-session-exact",
                            "authorization_details" to JsonPrimitive("details-marker"),
                        ),
                )
            val store = FixedTokenStorage(accessToken = token)
            val internalClient =
                InternalClientConfig(
                    clientId = "credential-issuer",
                    clientSecret = "credential-issuer-secret",
                )
            val cmd = command(store, internalClients = mapOf("issuer" to internalClient))

            val result = cmd.execute(IntrospectTokenArgs(token = "opaque-access", clientId = "credential-issuer"))

            assertTrue(result.isOk)
            assertEquals(true, result.value.active)
            assertEquals(
                JsonPrimitive("offer-session-exact"),
                result.value.additionalClaims["oid4vci.internal.issuer_state"],
            )
            assertEquals(JsonPrimitive("details-marker"), result.value.additionalClaims["authorization_details"])
        }

    @Test
    fun introspect_opaqueInternalResourceServer_receivesStoredExtensionClaimsWithoutLegacyServerConfig() =
        runTest {
            val token =
                accessToken(clientId = "").copy(
                    additionalData =
                        mapOf(
                            "oid4vci.internal.issuer_state" to "opaque-offer-session",
                            "authorization_details" to JsonPrimitive("opaque-details-marker"),
                        ),
                )
            val store = FixedTokenStorage(accessToken = token)
            val cmd =
                command(
                    storage = store,
                    internalClients = emptyMap(),
                    opaqueInternalClientIds = setOf("issuer-service"),
                )

            val result = cmd.execute(IntrospectTokenArgs(token = "opaque-access", clientId = "issuer-service"))

            assertTrue(result.isOk)
            assertEquals(true, result.value.active)
            assertEquals(
                JsonPrimitive("opaque-offer-session"),
                result.value.additionalClaims["oid4vci.internal.issuer_state"],
            )
            assertEquals(JsonPrimitive("opaque-details-marker"), result.value.additionalClaims["authorization_details"])
        }

    // ========================================================================
    // Stubs
    // ========================================================================

    private class FixedTokenStorage(
        private val accessToken: AccessTokenData? = null,
        private val refreshToken: RefreshTokenData? = null,
    ) : TokenStorage {
        override suspend fun storeAccessToken(
            token: String,
            data: AccessTokenData,
        ) = Ok(Unit)

        override suspend fun getAccessToken(token: String) = Ok(accessToken)

        override suspend fun revokeAccessToken(token: String) = Ok(Unit)

        override suspend fun findAccessTokensBySubject(subject: String) = Ok(emptyList<AccessTokenData>())

        override suspend fun findAccessTokensByClient(clientId: String) = Ok(emptyList<AccessTokenData>())

        override suspend fun cleanupExpiredAccessTokens() = Ok(0)

        override suspend fun storeRefreshToken(
            token: String,
            data: RefreshTokenData,
        ) = Ok(Unit)

        override suspend fun getRefreshToken(token: String) = Ok(refreshToken)

        override suspend fun consumeRefreshToken(
            token: String,
            revoke: Boolean,
        ) = Ok(refreshToken)

        override suspend fun revokeRefreshToken(token: String) = Ok(Unit)

        override suspend fun findRefreshTokensBySubject(subject: String) = Ok(emptyList<RefreshTokenData>())

        override suspend fun cleanupExpiredRefreshTokens() = Ok(0)

        override suspend fun revokeAllTokensForSubject(subject: String) = Ok(0)

        override suspend fun revokeAllTokensForClient(clientId: String) = Ok(0)
    }
}
