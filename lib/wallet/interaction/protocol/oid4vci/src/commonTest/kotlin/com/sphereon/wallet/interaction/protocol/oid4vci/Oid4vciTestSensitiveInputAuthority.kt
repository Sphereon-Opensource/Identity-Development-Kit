/*
 * Copyright 2026 Sphereon International B.V.
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.sphereon.wallet.interaction.protocol.oid4vci

import com.sphereon.wallet.interaction.WalletCounterpartyTrustResolver
import com.sphereon.wallet.interaction.WalletCounterpartyEncounterRegistry
import com.sphereon.wallet.interaction.WalletInteractionAction
import com.sphereon.wallet.interaction.WalletInteractionContext as CoreWalletInteractionContext
import com.sphereon.wallet.interaction.WalletInteractionExecutionMode
import com.sphereon.wallet.interaction.WalletInteractionPrivateSessionStore
import com.sphereon.wallet.interaction.WalletInteractionSensitiveInputAuthority
import com.sphereon.wallet.interaction.WalletInteractionSensitiveInputPurpose
import com.sphereon.wallet.interaction.WalletInteractionSensitiveInputRef
import com.sphereon.wallet.interaction.WalletInteractionSessionId
import com.sphereon.wallet.interaction.WalletInteractionState
import com.sphereon.wallet.interaction.WalletCredentialSelection
import com.sphereon.wallet.interaction.WalletProtocolExecutor
import com.sphereon.wallet.interaction.WalletSecurityGate
import com.sphereon.wallet.interaction.WalletSecurityGrant
import com.sphereon.wallet.interaction.WalletTrustPolicy

/** Explicit OID4VCI test fixture authority. Values are session/purpose-bound and one-use. */
internal object Oid4vciTestSensitiveInputAuthority : WalletInteractionSensitiveInputAuthority {
    private var sequence = 0L
    private val values = mutableMapOf<Triple<WalletInteractionSessionId, WalletInteractionSensitiveInputPurpose, String>, Any>()

    override suspend fun register(
        sessionId: WalletInteractionSessionId,
        purpose: WalletInteractionSensitiveInputPurpose,
        value: String,
    ): WalletInteractionSensitiveInputRef {
        require(value.isNotBlank())
        val ref = WalletInteractionSensitiveInputRef("test-sensitive-${++sequence}")
        values[Triple(sessionId, purpose, ref.value)] = value
        return ref
    }

    override suspend fun consume(
        sessionId: WalletInteractionSessionId,
        purpose: WalletInteractionSensitiveInputPurpose,
        ref: WalletInteractionSensitiveInputRef,
    ): String? = values.remove(Triple(sessionId, purpose, ref.value)) as? String

    override suspend fun registerSecurityGrant(
        sessionId: WalletInteractionSessionId,
        grant: WalletSecurityGrant,
    ): WalletInteractionSensitiveInputRef {
        val ref = WalletInteractionSensitiveInputRef("test-sensitive-${++sequence}")
        values[Triple(sessionId, WalletInteractionSensitiveInputPurpose.INTERACTION_SECURITY_GRANT, ref.value)] = grant
        return ref
    }

    override suspend fun consumeSecurityGrant(
        sessionId: WalletInteractionSessionId,
        ref: WalletInteractionSensitiveInputRef,
    ): WalletSecurityGrant? =
        values.remove(Triple(sessionId, WalletInteractionSensitiveInputPurpose.INTERACTION_SECURITY_GRANT, ref.value)) as? WalletSecurityGrant

    override suspend fun clear(sessionId: WalletInteractionSessionId) {
        values.keys.removeAll { it.first == sessionId }
    }
}

@Suppress("FunctionName")
internal fun WalletInteractionContext(
    sessionId: WalletInteractionSessionId,
    walletUnitId: String,
    executionMode: WalletInteractionExecutionMode,
    protocolExecutor: WalletProtocolExecutor = WalletProtocolExecutor.local,
    counterpartyEncounterRegistry: WalletCounterpartyEncounterRegistry = WalletCounterpartyEncounterRegistry.none,
    trustResolver: WalletCounterpartyTrustResolver = WalletCounterpartyTrustResolver.unresolved,
    trustPolicy: WalletTrustPolicy = WalletTrustPolicy.allow,
    securityGate: WalletSecurityGate = WalletSecurityGate.allow,
    privateSessionStore: WalletInteractionPrivateSessionStore = WalletInteractionPrivateSessionStore.none,
    attributes: Map<String, String> = emptyMap(),
): CoreWalletInteractionContext =
    CoreWalletInteractionContext(
        sessionId = sessionId,
        walletUnitId = walletUnitId,
        executionMode = executionMode,
        protocolExecutor = protocolExecutor,
        counterpartyEncounterRegistry = counterpartyEncounterRegistry,
        trustResolver = trustResolver,
        trustPolicy = trustPolicy,
        securityGate = securityGate,
        privateSessionStore = privateSessionStore,
        sensitiveInputAuthority = Oid4vciTestSensitiveInputAuthority,
        attributes = attributes,
    )

internal suspend fun CoreWalletInteractionContext.txCodeAction(value: String): WalletInteractionAction =
    WalletInteractionAction.submitTxCode(
        sensitiveInputAuthority.register(
            sessionId,
            WalletInteractionSensitiveInputPurpose.OID4VCI_TRANSACTION_CODE,
            value,
        ),
    )

internal suspend fun CoreWalletInteractionContext.authorizationCallbackAction(value: String): WalletInteractionAction =
    WalletInteractionAction.authCallback(
        sensitiveInputAuthority.register(
            sessionId,
            WalletInteractionSensitiveInputPurpose.OID4VCI_AUTHORIZATION_CALLBACK,
            value,
        ),
    )

internal fun WalletInteractionState.acceptOfferAction(): WalletInteractionAction =
    WalletInteractionAction.selectCredentials(
        WalletCredentialSelection(
            selectedCredentialIdsByRequirement =
                mapOf("oid4vci-offer" to requireNotNull(credentialOffer).credentialConfigurationIds),
        ),
    )
