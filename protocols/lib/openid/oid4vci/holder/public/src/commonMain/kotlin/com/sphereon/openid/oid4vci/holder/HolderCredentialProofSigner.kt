/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package com.sphereon.openid.oid4vci.holder

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.wallet.unit.SecureComponentUsage
import com.sphereon.wallet.unit.WalletAttestedKeyRef

/**
 * Neutral holder signing/key-provision port for OID4VCI credential-request proofs.
 *
 * Protocols depend on this contract only. Wallet implements it by adapting WSCA/WSCD so the
 * holder stack does not take a direct dependency on wallet-owned WSCA modules (Aug 13 port inversion).
 */
interface HolderCredentialProofSigner {
    suspend fun ensureKey(
        walletUnitId: String,
        usage: SecureComponentUsage,
        algorithm: SignatureAlgorithm,
        keyAlias: String? = null,
    ): IdkResult<WalletAttestedKeyRef, IdkError>

    suspend fun prepareSign(request: HolderSigningRequest): IdkResult<HolderPreparedSigning, IdkError>

    suspend fun sign(
        prepared: HolderPreparedSigning,
        request: HolderSigningRequest,
    ): IdkResult<ByteArray, IdkError>
}

/** Caller-supplied facts for one holder credential-proof signing operation. */
data class HolderSigningRequest(
    val walletUnitId: String,
    val keyRef: WalletAttestedKeyRef,
    val signingInput: ByteArray,
    val operationBinding: String,
    val walletAccountId: String? = keyRef.walletAccountId,
    val audience: String? = null,
    val nonce: String? = null,
) {
    init {
        require(walletUnitId.isNotBlank()) { "holder_sign_wallet_unit_id_blank" }
        require(operationBinding.isNotBlank()) { "holder_sign_operation_binding_blank" }
        require(signingInput.isNotEmpty()) { "holder_sign_input_empty" }
        require(walletAccountId == null || walletAccountId.isNotBlank()) { "holder_sign_wallet_account_id_blank" }
        require(audience == null || audience.isNotBlank()) { "holder_sign_audience_blank" }
        require(nonce == null || nonce.isNotBlank()) { "holder_sign_nonce_blank" }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false
        other as HolderSigningRequest
        return walletUnitId == other.walletUnitId &&
            keyRef == other.keyRef &&
            signingInput.contentEquals(other.signingInput) &&
            operationBinding == other.operationBinding &&
            walletAccountId == other.walletAccountId &&
            audience == other.audience &&
            nonce == other.nonce
    }

    override fun hashCode(): Int {
        var result = walletUnitId.hashCode()
        result = 31 * result + keyRef.hashCode()
        result = 31 * result + signingInput.contentHashCode()
        result = 31 * result + operationBinding.hashCode()
        result = 31 * result + (walletAccountId?.hashCode() ?: 0)
        result = 31 * result + (audience?.hashCode() ?: 0)
        result = 31 * result + (nonce?.hashCode() ?: 0)
        return result
    }
}

/**
 * Implementation-prepared signing context returned by [HolderCredentialProofSigner.prepareSign].
 * Signing input is defensively copied so callers cannot mutate what was authorized.
 */
class HolderPreparedSigning(
    val walletUnitId: String,
    val keyRef: WalletAttestedKeyRef,
    val walletAccountId: String?,
    val operationBinding: String,
    val operationType: String,
    val digestBinding: String,
    val nonce: String,
    val audience: String,
    signingInput: ByteArray,
) {
    private val exactSigningInput = signingInput.copyOf()

    val signingInput: ByteArray
        get() = exactSigningInput.copyOf()
}
