/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.wallet.interaction

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable

interface WalletInteractionClient {
    suspend fun start(input: WalletInteractionInput): WalletInteractionSession

    /**
     * Rehydrates [sessionId] from the session store without starting a new phase. Get/dispatch/cancel
     * use this so reading a Failed session cannot un-fail it.
     */
    suspend fun load(sessionId: WalletInteractionSessionId): WalletInteractionSession

    /**
     * Holder action that picks a [WalletFailureDisposition.RESUMABLE] Failed session up where it
     * stopped. Live sessions are rehydrated as [load]. Failed sessions that are not RESUMABLE are
     * rejected. The default forwards to [load] so a client cannot un-fail by implementing only
     * [resume] and forgetting [load].
     */
    suspend fun resume(sessionId: WalletInteractionSessionId): WalletInteractionSession = load(sessionId)

    suspend fun dispatch(
        sessionId: WalletInteractionSessionId,
        action: WalletInteractionAction,
    )

    suspend fun cancel(sessionId: WalletInteractionSessionId)

    fun observe(sessionId: WalletInteractionSessionId): StateFlow<WalletInteractionState>
}

/**
 * Protocol-agnostic ingress for a backend-owned interaction. Implementations verify the digest and
 * policy revision before the backend performs the first semantic interpretation. There is no
 * app-owned fallback on failure.
 */
interface CapturedInteractionClient : WalletInteractionClient {
    suspend fun startCaptured(input: CapturedInteractionInput): WalletInteractionSession
}

/**
 * Resolves and locks the protocol execution owner from authoritative runtime policy while the
 * capture remains opaque. Implementations must not inspect [CapturedInteractionInput.rawPayload].
 */
fun interface CapturedInteractionRuntimePlanResolver {
    suspend fun resolve(input: CapturedInteractionInput): IdkResult<LockedCapturedInteractionRuntimePlan, IdkError>
}

interface WalletInteractionEngine : WalletInteractionClient

interface WalletInteractionStateEventSource {
    suspend fun events(
        sessionId: WalletInteractionSessionId,
        afterRevision: Long? = null,
    ): List<WalletInteractionStateEvent> = emptyList()

    fun observeEvents(
        sessionId: WalletInteractionSessionId,
        afterRevision: Long? = null,
    ): Flow<WalletInteractionStateEvent>
}

interface WalletInteractionProtocolAdapter {
    val capability: WalletProtocolCapability

    suspend fun canHandle(entryPoint: WalletEntryPoint): WalletProtocolMatch

    suspend fun start(
        context: WalletInteractionContext,
        entryPoint: WalletEntryPoint,
    ): WalletInteractionSession

    suspend fun handle(
        context: WalletInteractionContext,
        sessionState: WalletInteractionState,
        action: WalletInteractionAction,
    ): WalletInteractionState
}

@Serializable
data class WalletProtocolCapability(
    val adapterId: String,
    val protocol: WalletProtocol,
    val flowKinds: List<WalletInteractionFlowKind>,
    val priority: Int = 0,
    val labelKey: String = "wallet.interaction.adapter.$adapterId",
    val arguments: Map<String, String> = emptyMap(),
) {
    init {
        require(adapterId.isNotBlank()) { "wallet_interaction_adapter_id_blank" }
        requireWalletInteractionLocalizationKey("labelKey", labelKey)
    }

    fun toChoice(): WalletImplementationChoice =
        WalletImplementationChoice(
            adapterId = adapterId,
            protocol = protocol,
            flowKinds = flowKinds,
            labelKey = labelKey,
            arguments = arguments,
        )
}

@Serializable
data class WalletProtocolMatch(
    val strength: WalletProtocolMatchStrength,
    val priority: Int = 0,
    val reasonCode: String? = null,
) {
    val canHandle: Boolean get() = strength != WalletProtocolMatchStrength.NONE

    companion object {
        val none: WalletProtocolMatch = WalletProtocolMatch(WalletProtocolMatchStrength.NONE)

        fun weak(
            priority: Int = 0,
            reasonCode: String? = null
        ): WalletProtocolMatch = WalletProtocolMatch(WalletProtocolMatchStrength.WEAK, priority, reasonCode)

        fun strong(
            priority: Int = 0,
            reasonCode: String? = null
        ): WalletProtocolMatch = WalletProtocolMatch(WalletProtocolMatchStrength.STRONG, priority, reasonCode)
    }
}

@Serializable
enum class WalletProtocolMatchStrength {
    NONE,
    WEAK,
    STRONG,
}

data class WalletInteractionContext(
    val sessionId: WalletInteractionSessionId,
    val walletUnitId: String,
    val executionOwner: ProtocolExecutionOwner,
    val protocolExecutor: WalletProtocolExecutor = WalletProtocolExecutor.walletApp,
    val trustResolver: WalletCounterpartyTrustResolver = WalletCounterpartyTrustResolver.unresolved,
    /** Resolves issuer-bound verification keys before OID4VCI credential storage. */
    val issuerAuthenticationResolver: WalletIssuerAuthenticationResolver = WalletIssuerAuthenticationResolver.none,
    val trustPolicy: WalletTrustPolicy = WalletTrustPolicy.warn,
    val securityGate: WalletSecurityGate = WalletSecurityGate.deny,
    val privateSessionStore: WalletInteractionPrivateSessionStore = WalletInteractionPrivateSessionStore.none,
    val sensitiveInputAuthority: WalletInteractionSensitiveInputAuthority,
    val attributes: Map<String, String> = emptyMap(),
    val counterpartyEncounterRegistry: WalletCounterpartyEncounterRegistry = WalletCounterpartyEncounterRegistry.none,
) {
    suspend fun recordCounterpartyEncounter(
        protocol: WalletProtocol,
        counterparty: WalletCounterpartySummary,
    ): WalletCounterpartyEncounterResult {
        val result =
            counterpartyEncounterRegistry.encounter(
                WalletCounterpartyEncounterRequest(
                    walletUnitId = walletUnitId,
                    protocol = protocol,
                    counterparty = counterparty,
                ),
            )
        require(result.counterparty.role == counterparty.role) { "wallet_counterparty_encounter_role_changed" }
        require(result.counterparty.identifier == counterparty.identifier) { "wallet_counterparty_encounter_identifier_changed" }
        return result
    }

    fun baseState(
        status: WalletInteractionStatus,
        flowKind: WalletInteractionFlowKind?,
        protocol: WalletProtocol?,
        adapterId: String?,
        entryPoint: WalletEntryPoint,
    ): WalletInteractionState =
        WalletInteractionState(
            sessionId = sessionId,
            walletUnitId = walletUnitId,
            executionOwner = executionOwner,
            status = status,
            flowKind = flowKind,
            protocol = protocol,
            adapterId = adapterId,
            entryPoint = entryPoint.summary(),
        )

    suspend fun authorizeProtocolOperation(request: WalletProtocolExecutionRequest): WalletSecurityGateResult {
        require(request.sessionId == sessionId) { "wallet_interaction_protocol_request_session_mismatch" }
        require(request.sessionWalletUnitId == walletUnitId) { "wallet_interaction_protocol_request_wallet_mismatch" }

        val decision = protocolExecutor.plan(request)
        if (!decision.securityGateRequired) {
            return WalletSecurityGateResult.Authorized(
                WalletSecurityGrant(
                    grantId = request.operationId,
                    assurance = decision.requiredAssurance,
                    evidence =
                        mapOf(
                            "executionOwner" to decision.executionOwner.name,
                            "placement" to decision.placement.name,
                        ),
                ),
            )
        }

        return securityGate.authorize(
            WalletSecurityGateRequest(
                operationId = request.operationId,
                operation = decision.securityOperation ?: request.operation,
                audience = decision.audience ?: request.audience,
                keyRef = decision.keyRef ?: request.keyRef,
                walletUnitId = decision.walletUnitId ?: request.walletUnitId ?: request.sessionWalletUnitId,
                walletAccountId = decision.walletAccountId ?: request.walletAccountId,
                activationDecisionId = decision.activationDecisionId ?: request.activationDecisionId,
                operationType = decision.operationType ?: request.operationType,
                operationBinding = decision.operationBinding ?: request.operationBinding,
                operationHash = decision.operationHash ?: request.operationHash,
                nonce = decision.nonce ?: request.nonce,
                requiredAssurance = decision.requiredAssurance,
            ),
        )
    }
}

interface WalletProtocolExecutor {
    val executionOwner: ProtocolExecutionOwner

    suspend fun plan(request: WalletProtocolExecutionRequest): WalletProtocolExecutionDecision = WalletProtocolExecutionDecision.forOwner(executionOwner, request)

    fun withExecutionOwner(owner: ProtocolExecutionOwner): WalletProtocolExecutor =
        if (owner == executionOwner) {
            this
        } else {
            ExecutionOwnerOverrideWalletProtocolExecutor(this, owner)
        }

    companion object {
        val walletApp: WalletProtocolExecutor = forOwner(ProtocolExecutionOwner.WALLET_APP)
        val walletBackend: WalletProtocolExecutor = forOwner(ProtocolExecutionOwner.WALLET_BACKEND)

        fun forOwner(owner: ProtocolExecutionOwner): WalletProtocolExecutor = DefaultWalletProtocolExecutor(owner)
    }
}

@Serializable
data class WalletProtocolExecutionRequest(
    val operationId: String,
    val sessionId: WalletInteractionSessionId,
    val sessionWalletUnitId: String,
    val protocol: WalletProtocol,
    val operation: WalletSecurityOperation,
    val audience: String? = null,
    val keyRef: String? = null,
    val walletUnitId: String? = null,
    val walletAccountId: String? = null,
    val activationDecisionId: String? = null,
    val operationType: String? = null,
    val operationBinding: String? = null,
    val operationHash: String? = null,
    val nonce: String? = null,
    val requiredAssurance: WalletSecurityAssurance = WalletSecurityAssurance.USER_PRESENT,
) {
    init {
        require(operationId.isNotBlank()) { "wallet_interaction_operation_id_blank" }
        require(sessionWalletUnitId.isNotBlank()) { "wallet_interaction_wallet_unit_id_blank" }
    }
}

@Serializable
data class WalletProtocolExecutionDecision(
    val executionOwner: ProtocolExecutionOwner,
    val placement: WalletProtocolExecutionPlacement,
    val securityGateRequired: Boolean = true,
    val securityOperation: WalletSecurityOperation? = null,
    val requiredAssurance: WalletSecurityAssurance = WalletSecurityAssurance.USER_PRESENT,
    val audience: String? = null,
    val keyRef: String? = null,
    val walletUnitId: String? = null,
    val walletAccountId: String? = null,
    val activationDecisionId: String? = null,
    val operationType: String? = null,
    val operationBinding: String? = null,
    val operationHash: String? = null,
    val nonce: String? = null,
) {
    companion object {
        fun forOwner(
            owner: ProtocolExecutionOwner,
            request: WalletProtocolExecutionRequest,
        ): WalletProtocolExecutionDecision =
            WalletProtocolExecutionDecision(
                executionOwner = owner,
                placement =
                    when (owner) {
                        ProtocolExecutionOwner.WALLET_APP -> {
                            WalletProtocolExecutionPlacement.WALLET_APP
                        }

                        ProtocolExecutionOwner.WALLET_BACKEND -> {
                            WalletProtocolExecutionPlacement.WALLET_BACKEND
                        }
                    },
                securityOperation = request.operation,
                requiredAssurance = request.requiredAssurance,
                audience = request.audience,
                keyRef = request.keyRef,
                walletUnitId = request.walletUnitId,
                walletAccountId = request.walletAccountId,
                activationDecisionId = request.activationDecisionId,
                operationType = request.operationType,
                operationBinding = request.operationBinding,
                operationHash = request.operationHash,
                nonce = request.nonce,
            )
    }
}

@Serializable
enum class WalletProtocolExecutionPlacement {
    WALLET_APP,
    WALLET_BACKEND,
}

private class DefaultWalletProtocolExecutor(
    override val executionOwner: ProtocolExecutionOwner,
) : WalletProtocolExecutor

private class ExecutionOwnerOverrideWalletProtocolExecutor(
    private val delegate: WalletProtocolExecutor,
    override val executionOwner: ProtocolExecutionOwner,
) : WalletProtocolExecutor {
    override suspend fun plan(request: WalletProtocolExecutionRequest): WalletProtocolExecutionDecision {
        val delegated = delegate.plan(request)
        if (delegated.executionOwner == executionOwner) return delegated

        val ownerDefault = WalletProtocolExecutionDecision.forOwner(executionOwner, request)
        return delegated.copy(
            executionOwner = executionOwner,
            placement = ownerDefault.placement,
        )
    }
}
