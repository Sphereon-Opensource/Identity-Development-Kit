/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.sphereon.wallet.interaction.protocol.oid4vci

import com.sphereon.wallet.interaction.WalletInteractionContext
import com.sphereon.wallet.interaction.WalletInteractionExecutionMode
import com.sphereon.wallet.interaction.WalletInteractionPrivateSessionData
import com.sphereon.wallet.interaction.WalletInteractionPrivateSessionStore
import com.sphereon.wallet.interaction.WalletInteractionSessionId
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class Oid4vciPrivateSessionStateTest {
    @Test
    fun fullyPopulatedStateSurvivesJsonRoundTrip() {
        val state = fullyPopulatedState()

        val json = Json.encodeToString(Oid4vciPrivateSessionState.serializer(), state)
        val decoded = Json.decodeFromString(Oid4vciPrivateSessionState.serializer(), json)

        assertEquals(state, decoded)
    }

    @Test
    fun fullyPopulatedStateRoundTripsThroughPrivateSessionStoreWithTypedEquality() =
        runTest {
            val store = InMemoryPrivateSessionStore()
            val sessionId = WalletInteractionSessionId("full-population-session")
            val context =
                WalletInteractionContext(
                    sessionId = sessionId,
                    walletUnitId = "wallet",
                    executionMode = WalletInteractionExecutionMode.LOCAL,
                    privateSessionStore = store,
                )
            val state = fullyPopulatedState()

            context.updateOid4vciState { state }

            val stored = store.get(sessionId, Oid4vciWalletInteractionProtocolAdapter.ADAPTER_ID)
            assertEquals(setOf("state"), stored?.values?.keys)
            assertEquals(state, context.oid4vciState())
        }

    @Test
    fun emptyStateSurvivesJsonRoundTrip() {
        val state = Oid4vciPrivateSessionState()

        val json = Json.encodeToString(Oid4vciPrivateSessionState.serializer(), state)
        val decoded = Json.decodeFromString(Oid4vciPrivateSessionState.serializer(), json)

        assertEquals(state, decoded)
        assertNull(decoded.authorization)
        assertNull(decoded.iae)
        assertNull(decoded.tokens)
        assertNull(decoded.deferred)
        assertNull(decoded.notification)
        assertNull(decoded.refreshTargetCredentialRecordId)
        assertTrue(decoded.holderKeyAliases.isEmpty())
    }

    @Test
    fun updateOid4vciStateRoundTripsThroughSingleStateKeyInPrivateSessionStore() =
        runTest {
            val store = InMemoryPrivateSessionStore()
            val sessionId = WalletInteractionSessionId("round-trip-session")
            val context =
                WalletInteractionContext(
                    sessionId = sessionId,
                    walletUnitId = "wallet",
                    executionMode = WalletInteractionExecutionMode.LOCAL,
                    privateSessionStore = store,
                )

            context.updateOid4vciState {
                it.copy(
                    entryPointRaw = "openid-credential-offer://?credential_offer=secret",
                    txCode = "tx-secret",
                )
            }
            context.updateOid4vciState {
                it.copy(
                    tokens = Oid4vciPrivateSessionState.TokenLeg(accessToken = "access-token-secret"),
                )
            }

            val stored = store.get(sessionId, Oid4vciWalletInteractionProtocolAdapter.ADAPTER_ID)
            assertEquals(setOf("state"), stored?.values?.keys)

            val reloaded = context.oid4vciState()
            assertEquals("openid-credential-offer://?credential_offer=secret", reloaded.entryPointRaw)
            assertEquals("tx-secret", reloaded.txCode)
            assertEquals("access-token-secret", reloaded.tokens?.accessToken)
        }
}

private fun fullyPopulatedState(): Oid4vciPrivateSessionState =
    Oid4vciPrivateSessionState(
        entryPointRaw = "openid-credential-offer://?credential_offer=secret",
        credentialConfigurationId = "identity",
        holderKeyAliases = listOf("key-1", "key-2"),
        txCode = "tx-secret",
        authorizationCallback = "wallet://callback?code=authorization-code-secret",
        refreshTargetCredentialRecordId = "cred-record-1",
        authorization =
            Oid4vciPrivateSessionState.AuthorizationLeg(
                authorizationServerIssuer = "https://as.example",
                tokenEndpoint = "https://as.example/token",
                codeVerifier = "code-verifier-secret",
                state = "oauth-state",
                redirectUri = "wallet://callback",
                clientId = "wallet-client",
            ),
        iae =
            Oid4vciPrivateSessionState.IaeLeg(
                authorizationServerIssuer = "https://as.example",
                tokenEndpoint = "https://as.example/token",
                iaeEndpoint = "https://as.example/iae",
                authSession = "auth-session-secret",
                requestUri = "https://as.example/iae/request/1",
                redirectUri = "wallet://callback",
                clientId = "wallet-client",
                codeVerifier = "iae-code-verifier-secret",
                pending = true,
            ),
        tokens =
            Oid4vciPrivateSessionState.TokenLeg(
                accessToken = "access-token-secret",
                refreshToken = "refresh-token-secret",
                tokenEndpoint = "https://as.example/token",
            ),
        deferred =
            Oid4vciPrivateSessionState.DeferredLeg(
                deferredCredentialEndpoint = "https://issuer.example/deferred",
                transactionId = "deferred-transaction-secret",
            ),
        notification =
            Oid4vciPrivateSessionState.NotificationLeg(
                notificationEndpoint = "https://issuer.example/notification",
                notificationId = "notification-secret",
            ),
    )

private class InMemoryPrivateSessionStore : WalletInteractionPrivateSessionStore {
    private val records = mutableMapOf<Pair<WalletInteractionSessionId, String>, WalletInteractionPrivateSessionData>()

    override suspend fun put(
        sessionId: WalletInteractionSessionId,
        data: WalletInteractionPrivateSessionData,
    ) {
        records[sessionId to data.namespace] = data
    }

    override suspend fun get(
        sessionId: WalletInteractionSessionId,
        namespace: String,
    ): WalletInteractionPrivateSessionData? = records[sessionId to namespace]

    override suspend fun remove(
        sessionId: WalletInteractionSessionId,
        namespace: String,
    ) {
        records.remove(sessionId to namespace)
    }

    override suspend fun removeSession(sessionId: WalletInteractionSessionId) {
        records.keys.filter { it.first == sessionId }.forEach { records.remove(it) }
    }
}
