/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.wallet.wscd

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.wallet.unit.SecureComponentUsage
import kotlinx.serialization.Serializable

/**
 * Wallet Secure Cryptographic Device abstraction (CIR (EU) 2024/2981 Art. 2(5)).
 * THE ONLY LAYER ALLOWED TO TOUCH THE KMS. Callers reach it exclusively through a Wsca
 * (CIR (EU) 2024/2979 Art. 4). Implementations: SoftwareWscd and LocalNativeWscd (P2),
 * RemoteWscd (EDK, P2/P6).
 */
interface Wscd {
    val profile: WscdProfile

    /**
     * Provisions or resolves the key identified by [spec]. When [WscdKeySpec.alias] is set it
     * is honored verbatim as the key's stable identity: repeat calls with the same alias
     * resolve the same key (idempotent). Otherwise a deterministic default alias is derived
     * from [WscdKeySpec.walletUnitId], [WscdKeySpec.usage] and [WscdKeySpec.algorithm], giving
     * one stable key per (unit, usage, algorithm) triple.
     */
    suspend fun generateKey(spec: WscdKeySpec): IdkResult<WscdKeyHandle, IdkError>

    /**
     * Mints a brand-new, uniquely-aliased key on every call, ignoring any [WscdKeySpec.alias]
     * the caller supplied. Unlike [generateKey] this is never idempotent: there is no identity
     * for a caller to reuse. For the per-credential unlinkability path (fresh, non-reused keys
     * per credential instance), callers use this instead of [generateKey].
     */
    suspend fun generateFreshKey(spec: WscdKeySpec): IdkResult<WscdKeyHandle, IdkError>

    /**
     * Digest signing gated by an activation proof: the WSCA's user-auth token locally,
     * or signature-activation data (digest-bound, nonce-checked, one-time) remotely,
     * per the EN 419 241 SAM pattern via ETSI TS 119 431-1.
     */
    suspend fun signDigest(
        handle: WscdKeyHandle,
        digest: ByteArray,
        activation: ActivationProof
    ): IdkResult<ByteArray, IdkError>

    suspend fun deleteKey(handle: WscdKeyHandle): IdkResult<Unit, IdkError>

    /** Custody evidence for Key Attestation claims (platform key attestation where available). */
    suspend fun keyEvidence(handle: WscdKeyHandle): IdkResult<WscdKeyEvidence, IdkError>
}

@Serializable
data class WscdKeySpec(
    val walletUnitId: String,
    val usage: SecureComponentUsage,
    val algorithm: SignatureAlgorithm,
    val alias: String? = null,
)

@Serializable
data class WscdKeyHandle(
    val keyRef: String,
    val profile: WscdProfile,
    val walletUnitId: String,
    /**
     * Public JWK (JSON string) of the generated key pair. The WSCA layer needs this to build
     * WalletAttestedKeyRef, DPoP public-key headers, and client-attestation cnf.jwk claims
     * without ever touching the KMS; the WSCD populates it at generation time.
     */
    val publicKeyJwk: String? = null,
    /** Key identifier (kid) of the generated key pair, falling back to the alias. */
    val keyId: String? = null,
    /**
     * Identifier of the concrete provider that holds this key (e.g. a software KMS provider id,
     * or a platform keystore/HSM provider id). The WSCA layer needs this to populate
     * `WalletKeystoreRef.providerId` when wrapping a handle into a `WalletAttestedKeyRef`, without
     * ever touching the KMS/provider types themselves.
     */
    val providerId: String? = null,
)

/** Proof that key use was authorized. Local: WSCA user-auth token. Remote: SAD equivalent. */
@Serializable
data class ActivationProof(
    val kind: ActivationProofKind,
    val token: String,
    val digestBinding: String? = null,
    val nonce: String? = null,
    /** Secret-free ceremony evidence, for example `factor=pin|biometric`. */
    val evidence: Map<String, String> = emptyMap(),
)

@Serializable
enum class ActivationProofKind { LOCAL_USER_AUTH, REMOTE_ACTIVATION_DECISION, NONE_DEV_ONLY }

@Serializable
data class WscdKeyEvidence(
    val profile: WscdProfile,
    /** Platform attestation chain or equivalent evidence; empty for Software. */
    val evidence: Map<String, String> = emptyMap(),
)
