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

package com.sphereon.oauth2.server.authorization.impl.command.federation

import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.oauth2.common.model.AuthorizationServerMetadata
import com.sphereon.oauth2.server.authorization.command.federation.FederatedExchangeResult
import com.sphereon.oauth2.server.authorization.command.federation.HandleFederationOutcomeArgs
import com.sphereon.oauth2.server.authorization.config.FederationProviderConfig
import com.sphereon.oauth2.server.authorization.impl.provider.storage.InMemoryFederationSessionStore
import com.sphereon.oauth2.server.authorization.impl.testutil.OAuth2ServerTestContext
import com.sphereon.oauth2.server.authorization.provider.FederatedClaimMapper
import com.sphereon.oauth2.server.authorization.provider.FederatedIdentityLinker
import com.sphereon.oauth2.server.authorization.provider.FederationFlowConfig
import com.sphereon.oauth2.server.authorization.provider.LinkFederatedSessionRequest
import com.sphereon.oauth2.server.authorization.provider.LinkedFederatedSession
import com.sphereon.oauth2.server.authorization.storage.PendingFederation
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class HandleFederationOutcomeCommandImplTest {
    private val ctx = OAuth2ServerTestContext("handle-federation-outcome-test", this)

    private val providerConfig =
        FederationProviderConfig(
            id = "test-idp",
            name = "Test IdP",
            issuerUrl = "https://idp.example.com",
            clientId = "client-1",
            identifierClaimName = "sub",
        )

    private val pendingFederation =
        PendingFederation(
            sessionId = "sess-federation-outcome",
            state = "state-abc",
            nonce = "nonce-abc",
            pkceData = null,
            metadata =
                AuthorizationServerMetadata(
                    issuer = "https://as.example.com",
                    tokenEndpoint = "https://as.example.com/token",
                    authorizationEndpoint = "https://as.example.com/authorize",
                ),
            returnUrl = "https://rp.example.com/return",
            callbackRedirectUri = "https://as.example.com/federation/callback",
        )

    // Passes claims through unchanged.
    private val passthroughClaimMapper = FederatedClaimMapper { rawClaims -> rawClaims }

    // Returns a fixed local identity id so tests can assert on userId.
    private val stubbedLinker =
        object : FederatedIdentityLinker {
            override suspend fun linkFederatedSession(request: LinkFederatedSessionRequest,) = Ok(LinkedFederatedSession(localIdentityId = "local-user-42"))
        }

    private val flowConfig =
        object : FederationFlowConfig {
            override val pendingTtl: Duration = 10.minutes
            override val claimsCacheTtl: Duration = 5.minutes
            override val sessionTtl: Duration = 8.hours
        }

    private fun buildCommand(
        sessionStore: InMemoryFederationSessionStore,
        clock: Clock = Clock.System,
    ) = HandleFederationOutcomeCommandImpl(
        execution = ctx.execution,
        claimMapper = passthroughClaimMapper,
        federatedIdentityLinker = stubbedLinker,
        sessionStore = sessionStore,
        flowConfig = flowConfig,
        clock = clock,
    )

    // Upstream claims map that includes the "sub" identifier claim.
    private fun upstreamClaims(sub: String = "upstream-user-99") =
        mapOf<String, Any>(
            "sub" to sub,
            "email" to "upstream@example.com",
        )

    @Test
    fun upstreamIdentityClaimsAreWrittenIntoCachedUserInfo() =
        runTest {
            val clock = FakeClock()
            val store = InMemoryFederationSessionStore(clock)
            store.storePendingFederation(pendingFederation, flowConfig.pendingTtl)

            val command = buildCommand(store, clock)
            val exchange =
                FederatedExchangeResult(
                    claims = upstreamClaims("upstream-user-99"),
                    upstreamAcr = "urn:example:acr:high",
                    upstreamAmr = listOf("pwd", "totp"),
                    upstreamSid = null,
                )

            val result =
                command.execute(
                    HandleFederationOutcomeArgs(
                        exchange = exchange,
                        state = pendingFederation.state,
                        pending = pendingFederation,
                        providerConfig = providerConfig,
                    ),
                )

            assertTrue(result.isOk)

            val cached = store.retrieveCachedUserClaims("local-user-42")
            assertTrue(cached.isOk)
            val claims = cached.value
            assertNotNull(claims)

            // upstream_sub carries the identifier claim value resolved from the merged claims map.
            assertEquals(JsonPrimitive("upstream-user-99"), claims.claims["upstream_sub"])
            // upstream_iss carries the provider issuerUrl.
            assertEquals(JsonPrimitive("https://idp.example.com"), claims.claims["upstream_iss"])
            // upstream_acr carries the string ACR from the exchange.
            assertEquals(JsonPrimitive("urn:example:acr:high"), claims.claims["upstream_acr"])
            // upstream_amr is a JSON array of method strings.
            val amr = claims.claims["upstream_amr"]
            assertTrue(amr is JsonArray, "upstream_amr must be a JsonArray")
            assertEquals(2, amr.size)
            assertEquals(JsonPrimitive("pwd"), amr[0])
            assertEquals(JsonPrimitive("totp"), amr[1])
        }

    @Test
    fun upstreamAcrAndAmrAbsentFromCachedClaimsWhenNull() =
        runTest {
            val clock = FakeClock()
            val store = InMemoryFederationSessionStore(clock)

            // Use a distinct pending record so this test's state key does not collide.
            val pending = pendingFederation.copy(state = "state-null-acr-amr")
            store.storePendingFederation(pending, flowConfig.pendingTtl)

            val command = buildCommand(store, clock)
            val exchange =
                FederatedExchangeResult(
                    claims = upstreamClaims("upstream-user-77"),
                    upstreamAcr = null,
                    upstreamAmr = null,
                    upstreamSid = null,
                )

            val result =
                command.execute(
                    HandleFederationOutcomeArgs(
                        exchange = exchange,
                        state = pending.state,
                        pending = pending,
                        providerConfig = providerConfig,
                    ),
                )

            assertTrue(result.isOk)

            val cached = store.retrieveCachedUserClaims("local-user-42")
            assertTrue(cached.isOk)
            val claims = cached.value
            assertNotNull(claims)

            // upstream_sub and upstream_iss are always present.
            assertEquals(JsonPrimitive("upstream-user-77"), claims.claims["upstream_sub"])
            assertEquals(JsonPrimitive("https://idp.example.com"), claims.claims["upstream_iss"])

            // upstream_acr and upstream_amr are absent when the exchange carried null.
            assertFalse("upstream_acr" in claims.claims, "upstream_acr must be absent when null")
            assertFalse("upstream_amr" in claims.claims, "upstream_amr must be absent when null")
        }

    // Minimal Clock implementation for deterministic time in tests.
    private class FakeClock(
        start: Instant = Instant.fromEpochSeconds(0)
    ) : Clock {
        private var current: Instant = start

        override fun now(): Instant = current

        fun advance(by: Duration) {
            current = current + by
        }
    }
}
