/*
 * (c) 2026 Sphereon International B.V.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sphereon.openid.oid4vci.issuer.impl.command

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.openid.oid4vci.issuer.store.CredentialIssuanceSessionStore
import com.sphereon.openid.oid4vci.issuer.store.IssuanceSession
import com.sphereon.openid.oid4vci.issuer.store.IssuanceSessionStatus
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class CredentialIdentifierValidationTest {
    @Test
    fun offerLinkedAuthorizationCodeResolvesOnlyExactCredentialIdentifier() =
        runTest {
            val first = session("offer-session-a")
            val second = session("offer-session-b")
            val store = RecordingStore(first, second)

            val result =
                resolveCredentialRequestCorrelation(
                    requestedIdentifier = second.sessionId,
                    tokenIdentifiers = listOf(second.sessionId),
                    tokenId = "token-jti-offer",
                    sessionStore = store,
                ).getOrThrow()

            assertSame(second, result.issuanceSession)
            assertEquals(second.sessionId, result.protocolSessionId)
            assertEquals(listOf(second.sessionId), store.exactGets)
            assertEquals(0, store.configurationLookups)
        }

    @Test
    fun walletInitiatedConfigurationFlowUsesTokenJtiWithoutSessionLookup() =
        runTest {
            val store = RecordingStore(session("unrelated-offer-session"))

            val result =
                resolveCredentialRequestCorrelation(
                    requestedIdentifier = null,
                    tokenIdentifiers = null,
                    tokenId = "wallet-token-jti-a",
                    sessionStore = store,
                ).getOrThrow()

            assertNull(result.issuanceSession)
            assertEquals("oid4vci:token-jti:wallet-token-jti-a", result.protocolSessionId)
            assertTrue(store.exactGets.isEmpty())
            assertEquals(0, store.configurationLookups)
        }

    @Test
    fun sameConfigurationFromDifferentTokenJtisProducesDistinctHistorySessions() =
        runTest {
            val store = RecordingStore()
            val first = resolveCredentialRequestCorrelation(null, null, "token-jti-1", store).getOrThrow()
            val second = resolveCredentialRequestCorrelation(null, emptyList(), "token-jti-2", store).getOrThrow()

            assertEquals("oid4vci:token-jti:token-jti-1", first.protocolSessionId)
            assertEquals("oid4vci:token-jti:token-jti-2", second.protocolSessionId)
            assertTrue(first.protocolSessionId != second.protocolSessionId)
            assertEquals(0, store.configurationLookups)
        }

    @Test
    fun externalTokenWithoutJtiGetsOpaqueRequestScopedProtocolSession() =
        runTest {
            val result =
                resolveCredentialRequestCorrelation(
                    requestedIdentifier = null,
                    tokenIdentifiers = null,
                    tokenId = null,
                    sessionStore = RecordingStore(),
                    opaqueIdProvider = { "opaque-123" },
                ).getOrThrow()

            assertEquals("oid4vci:credential-request:opaque-123", result.protocolSessionId)
        }

    private fun session(id: String) =
        IssuanceSession(
            sessionId = id,
            instanceId = "issuer-instance-credential-correlation",
            issuerId = "issuer",
            credentialConfigurationIds = listOf("shared-config"),
            status = IssuanceSessionStatus.OFFER_CREATED,
            createdAt = 1,
            expiresAt = Long.MAX_VALUE,
        )

    private class RecordingStore(vararg sessions: IssuanceSession) : CredentialIssuanceSessionStore {
        private val byId = sessions.associateBy { it.sessionId }.toMutableMap()
        val exactGets = mutableListOf<String>()
        var configurationLookups = 0

        override suspend fun create(session: IssuanceSession): IdkResult<IssuanceSession, IdkError> = Ok(session.also { byId[it.sessionId] = it })

        override suspend fun get(sessionId: String): IdkResult<IssuanceSession?, IdkError> = Ok(byId[sessionId].also { exactGets += sessionId })

        override suspend fun getByIssuerState(state: String): IdkResult<IssuanceSession?, IdkError> = Ok(null)

        override suspend fun findByCredentialConfigurationId(configId: String): IdkResult<IssuanceSession?, IdkError> {
            configurationLookups++
            return Ok(byId.values.firstOrNull { configId in it.credentialConfigurationIds })
        }

        override suspend fun update(session: IssuanceSession): IdkResult<IssuanceSession, IdkError> = Ok(session.also { byId[it.sessionId] = it })
    }
}
