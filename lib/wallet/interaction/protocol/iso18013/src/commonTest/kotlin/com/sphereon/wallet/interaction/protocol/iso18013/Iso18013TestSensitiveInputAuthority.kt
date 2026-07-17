/* Copyright 2026 Sphereon International B.V. Licensed under the Apache License, Version 2.0. */
package com.sphereon.wallet.interaction.protocol.iso18013

import com.sphereon.wallet.interaction.*
import com.sphereon.wallet.interaction.WalletInteractionContext as CoreWalletInteractionContext

internal object Iso18013TestSensitiveInputAuthority : WalletInteractionSensitiveInputAuthority {
    private var sequence = 0L
    private val values = mutableMapOf<Triple<WalletInteractionSessionId, WalletInteractionSensitiveInputPurpose, String>, Any>()
    override suspend fun register(sessionId: WalletInteractionSessionId, purpose: WalletInteractionSensitiveInputPurpose, value: String): WalletInteractionSensitiveInputRef =
        WalletInteractionSensitiveInputRef("iso-ref-${++sequence}").also { values[Triple(sessionId, purpose, it.value)] = value }
    override suspend fun consume(sessionId: WalletInteractionSessionId, purpose: WalletInteractionSensitiveInputPurpose, ref: WalletInteractionSensitiveInputRef): String? =
        values.remove(Triple(sessionId, purpose, ref.value)) as? String
    override suspend fun registerSecurityGrant(sessionId: WalletInteractionSessionId, grant: WalletSecurityGrant): WalletInteractionSensitiveInputRef =
        WalletInteractionSensitiveInputRef("iso-ref-${++sequence}").also { values[Triple(sessionId, WalletInteractionSensitiveInputPurpose.INTERACTION_SECURITY_GRANT, it.value)] = grant }
    override suspend fun consumeSecurityGrant(sessionId: WalletInteractionSessionId, ref: WalletInteractionSensitiveInputRef): WalletSecurityGrant? =
        values.remove(Triple(sessionId, WalletInteractionSensitiveInputPurpose.INTERACTION_SECURITY_GRANT, ref.value)) as? WalletSecurityGrant
    override suspend fun clear(sessionId: WalletInteractionSessionId) { values.keys.removeAll { it.first == sessionId } }
}

@Suppress("FunctionName")
internal fun WalletInteractionContext(
    sessionId: WalletInteractionSessionId,
    walletUnitId: String,
    executionMode: WalletInteractionExecutionMode,
    protocolExecutor: WalletProtocolExecutor = WalletProtocolExecutor.local,
    trustResolver: WalletCounterpartyTrustResolver = WalletCounterpartyTrustResolver.unresolved,
    trustPolicy: WalletTrustPolicy = WalletTrustPolicy.warn,
    securityGate: WalletSecurityGate = WalletSecurityGate.allow,
    privateSessionStore: WalletInteractionPrivateSessionStore = WalletInteractionPrivateSessionStore.none,
    attributes: Map<String, String> = emptyMap(),
): CoreWalletInteractionContext = CoreWalletInteractionContext(
    sessionId, walletUnitId, executionMode, protocolExecutor, trustResolver, trustPolicy, securityGate,
    privateSessionStore, Iso18013TestSensitiveInputAuthority, attributes,
)

internal suspend fun CoreWalletInteractionContext.securityGrantAction(grant: WalletSecurityGrant): WalletInteractionAction =
    WalletInteractionAction.approveSecurityChallenge(sensitiveInputAuthority.registerSecurityGrant(sessionId, grant))
