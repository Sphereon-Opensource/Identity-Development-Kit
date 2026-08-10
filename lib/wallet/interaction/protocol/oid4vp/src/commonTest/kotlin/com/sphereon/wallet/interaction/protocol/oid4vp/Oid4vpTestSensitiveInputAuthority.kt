/* Copyright 2026 Sphereon International B.V. Licensed under the Apache License, Version 2.0. */
package com.sphereon.wallet.interaction.protocol.oid4vp

import com.sphereon.wallet.interaction.*
import com.sphereon.wallet.interaction.WalletInteractionContext as CoreWalletInteractionContext

internal object Oid4vpTestSensitiveInputAuthority : WalletInteractionSensitiveInputAuthority {
    private var sequence = 0L
    private val values = mutableMapOf<Triple<WalletInteractionSessionId, WalletInteractionSensitiveInputPurpose, String>, Any>()

    override suspend fun register(sessionId: WalletInteractionSessionId, purpose: WalletInteractionSensitiveInputPurpose, value: String): WalletInteractionSensitiveInputRef {
        require(value.isNotBlank())
        return WalletInteractionSensitiveInputRef("oid4vp-ref-${++sequence}").also { values[Triple(sessionId, purpose, it.value)] = value }
    }
    override suspend fun consume(sessionId: WalletInteractionSessionId, purpose: WalletInteractionSensitiveInputPurpose, ref: WalletInteractionSensitiveInputRef): String? =
        values.remove(Triple(sessionId, purpose, ref.value)) as? String
    override suspend fun registerSecurityGrant(sessionId: WalletInteractionSessionId, grant: WalletSecurityGrant): WalletInteractionSensitiveInputRef =
        WalletInteractionSensitiveInputRef("oid4vp-ref-${++sequence}").also {
            values[Triple(sessionId, WalletInteractionSensitiveInputPurpose.INTERACTION_SECURITY_GRANT, it.value)] = grant
        }
    override suspend fun consumeSecurityGrant(sessionId: WalletInteractionSessionId, ref: WalletInteractionSensitiveInputRef): WalletSecurityGrant? =
        values.remove(Triple(sessionId, WalletInteractionSensitiveInputPurpose.INTERACTION_SECURITY_GRANT, ref.value)) as? WalletSecurityGrant
    override suspend fun clear(sessionId: WalletInteractionSessionId) { values.keys.removeAll { it.first == sessionId } }
}

@Suppress("FunctionName")
internal fun WalletInteractionContext(
    sessionId: WalletInteractionSessionId,
    walletUnitId: String,
    executionOwner: ProtocolExecutionOwner,
    protocolExecutor: WalletProtocolExecutor = WalletProtocolExecutor.walletApp,
    counterpartyEncounterRegistry: WalletCounterpartyEncounterRegistry = WalletCounterpartyEncounterRegistry.none,
    trustResolver: WalletCounterpartyTrustResolver = WalletCounterpartyTrustResolver.unresolved,
    trustPolicy: WalletTrustPolicy = WalletTrustPolicy.allow,
    securityGate: WalletSecurityGate = WalletSecurityGate.allow,
    privateSessionStore: WalletInteractionPrivateSessionStore = WalletInteractionPrivateSessionStore.none,
    attributes: Map<String, String> = emptyMap(),
): CoreWalletInteractionContext = CoreWalletInteractionContext(
    sessionId = sessionId,
    walletUnitId = walletUnitId,
    executionOwner = executionOwner,
    protocolExecutor = protocolExecutor,
    trustResolver = trustResolver,
    trustPolicy = trustPolicy,
    securityGate = securityGate,
    privateSessionStore = privateSessionStore,
    sensitiveInputAuthority = Oid4vpTestSensitiveInputAuthority,
    attributes = attributes,
    counterpartyEncounterRegistry = counterpartyEncounterRegistry,
)

internal suspend fun CoreWalletInteractionContext.securityGrantAction(grant: WalletSecurityGrant): WalletInteractionAction =
    WalletInteractionAction.approveSecurityChallenge(sensitiveInputAuthority.registerSecurityGrant(sessionId, grant))
