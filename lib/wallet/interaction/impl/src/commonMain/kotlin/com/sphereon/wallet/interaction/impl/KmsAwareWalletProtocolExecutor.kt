/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.sphereon.wallet.interaction.impl

import com.sphereon.crypto.core.kms.KeyManagerService
import com.sphereon.crypto.core.kms.KmsProviderOperation
import com.sphereon.crypto.core.kms.KmsProviderQuery
import com.sphereon.wallet.interaction.WalletInteractionExecutionMode
import com.sphereon.wallet.interaction.WalletProtocolExecutionDecision
import com.sphereon.wallet.interaction.WalletProtocolExecutionRequest
import com.sphereon.wallet.interaction.WalletProtocolExecutor
import com.sphereon.wallet.interaction.WalletSecurityAssurance
import com.sphereon.wallet.interaction.WalletSecurityOperation

fun interface WalletKmsCapabilityResolver {
    suspend fun resolve(request: WalletProtocolExecutionRequest): WalletKmsCapability?
}

data class WalletKmsCapability(
    val providerId: String,
    val providerType: String,
    val hardwareBacked: Boolean,
    val keyAttestationSupported: Boolean = false,
    val digestSigningSupported: Boolean = false,
    val signingSupported: Boolean = false,
) {
    val remoteProvider: Boolean
        get() {
            val value = providerType.lowercase()
            return listOf("aws", "azure", "rest", "remote", "cloud", "keyvault").any { it in value }
        }
}

class KeyManagerWalletKmsCapabilityResolver(
    private val keyManagerService: KeyManagerService,
    private val queryFactory: (WalletProtocolExecutionRequest) -> KmsProviderQuery = ::defaultKmsProviderQuery,
) : WalletKmsCapabilityResolver {
    override suspend fun resolve(request: WalletProtocolExecutionRequest): WalletKmsCapability? {
        if (request.keyRef.isNullOrBlank()) return null
        val result = keyManagerService.queryProvider(queryFactory(request))
        if (result.isErr) return null
        val match = result.value.match ?: return null
        val capabilities = match.capabilities ?: return null
        return WalletKmsCapability(
            providerId = match.providerId,
            providerType = capabilities.providerType,
            hardwareBacked = capabilities.supportsHardwareBacking,
            keyAttestationSupported = capabilities.supportsKeyAttestation(),
            digestSigningSupported = capabilities.supportsDigestSigning(),
            signingSupported = capabilities.supportsSigning(),
        )
    }
}

class KmsAwareWalletProtocolExecutor(
    private val capabilityResolver: WalletKmsCapabilityResolver,
    private val delegate: WalletProtocolExecutor = WalletProtocolExecutor.split,
) : WalletProtocolExecutor {
    override val executionMode: WalletInteractionExecutionMode
        get() = delegate.executionMode

    override fun withExecutionMode(mode: WalletInteractionExecutionMode): WalletProtocolExecutor = KmsAwareWalletProtocolExecutor(capabilityResolver, delegate.withExecutionMode(mode))

    override suspend fun plan(request: WalletProtocolExecutionRequest): WalletProtocolExecutionDecision {
        val decision = delegate.plan(request)
        val capability = capabilityResolver.resolve(request) ?: return decision
        return decision.copy(
            securityGateRequired = true,
            securityOperation = capability.securityOperation(request.operation),
            requiredAssurance = capability.requiredAssurance(decision.requiredAssurance),
            keyRef = decision.keyRef ?: request.keyRef,
            walletUnitId = decision.walletUnitId ?: request.walletUnitId,
            walletAccountId = decision.walletAccountId ?: request.walletAccountId,
            activationDecisionId = decision.activationDecisionId ?: request.activationDecisionId,
            operationType = decision.operationType ?: request.operationType,
            operationHash = decision.operationHash ?: request.operationHash,
            nonce = decision.nonce ?: request.nonce,
        )
    }

    private fun WalletKmsCapability.securityOperation(defaultOperation: WalletSecurityOperation): WalletSecurityOperation =
        when {
            remoteProvider && hardwareBacked -> WalletSecurityOperation.REMOTE_KEY_AUTHORIZATION
            remoteProvider -> WalletSecurityOperation.REMOTE_KEY_AUTHORIZATION
            hardwareBacked && "mobile" in providerType.lowercase() -> WalletSecurityOperation.LOCAL_HSM_UNLOCK
            hardwareBacked -> WalletSecurityOperation.LOCAL_HSM_UNLOCK
            else -> defaultOperation
        }

    private fun WalletKmsCapability.requiredAssurance(defaultAssurance: WalletSecurityAssurance): WalletSecurityAssurance =
        when {
            hardwareBacked -> WalletSecurityAssurance.HARDWARE_BACKED
            remoteProvider -> WalletSecurityAssurance.REMOTE_AUTHORIZED
            else -> defaultAssurance
        }
}

private fun defaultKmsProviderQuery(request: WalletProtocolExecutionRequest): KmsProviderQuery =
    KmsProviderQuery(
        operation =
            when (request.operation) {
                WalletSecurityOperation.CREDENTIAL_STORAGE -> KmsProviderOperation.GENERATE_KEY

                WalletSecurityOperation.LOCAL_HSM_UNLOCK,
                WalletSecurityOperation.REMOTE_KEY_AUTHORIZATION,
                WalletSecurityOperation.HOLDER_PROOF,
                WalletSecurityOperation.PRESENTATION_SHARING,
                -> KmsProviderOperation.SIGN_DIGEST
            },
    )
