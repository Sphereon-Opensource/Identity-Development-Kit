/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.wallet.wsca

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.wallet.unit.SecureComponentUsage
import com.sphereon.wallet.unit.WalletAttestedKeyRef
import com.sphereon.wallet.unit.WalletProviderAttestationSignerRef
import com.sphereon.wallet.unit.attestation.KeyAttestationIssueRequest
import com.sphereon.wallet.unit.attestation.KeyAttestationIssueResult
import com.sphereon.wallet.wscd.WscdProfile
import kotlin.time.Instant

data class WscaDpopProofRequest(
    val walletUnitId: String,
    val operationBinding: String,
    val keyRef: WalletAttestedKeyRef,
    val httpMethod: String,
    val httpUrl: String,
    val nonce: String? = null,
    val accessToken: String? = null,
    val issuedAtEpochSeconds: Long? = null,
) {
    init {
        require(operationBinding.isNotBlank()) { "wallet_wsca_dpop_operation_binding_blank" }
    }
}

data class WscaDpopProofResult(
    val proofJwt: String,
    val jwkThumbprint: String,
)

data class WscaClientAttestationAuthRequest(
    val walletUnitId: String,
    val operationBinding: String,
    val walletAccountId: String,
    val clientId: String,
    val audience: String,
    val clientInstanceKey: WalletAttestedKeyRef,
    val walletName: String,
    val walletVersion: String,
    val walletLink: String? = null,
    val signer: WalletProviderAttestationSignerRef? = null,
    val expiresAt: Instant? = null,
    val challenge: String? = null,
    val evidence: Map<String, String> = emptyMap(),
) {
    init {
        require(operationBinding.isNotBlank()) { "wallet_wsca_client_attestation_operation_binding_blank" }
    }
}

data class WscaClientAttestationAuthResult(
    val clientId: String,
    val audience: String,
    val clientAttestationJwt: String,
    val clientAttestationPopJwt: String,
    val clientInstanceJwkThumbprint: String,
)

/**
 * The wallet-unit secure-component cryptographic surface: the holder-facing facade into a
 * WSCA/WSCD chain through which ALL holder-side crypto (key provisioning and proof/PoP/DPoP signing)
 * is performed. Wsca is the Wallet Secure Cryptographic Application per CIR (EU) 2024/2981 Art. 2(4)
 * and CIR (EU) 2024/2979 Art. 5(1): the only component linked to the WSCD, performing operations on
 * critical assets only after wallet-user authentication.
 *
 * ## Layering (non-negotiable)
 * ```
 * holder / wallet code  ->  Wsca (this holder-facing facade)
 *                       ->  WSCA policy / activation / wallet-unit binding
 *                       ->  WSCD (local OSS, mobile platform, WebCrypto, remote HSM/QSCD)
 *                       ->  KeyManagerService abstraction owned by the WSCD
 * ```
 * Wallet / holder code MUST route holder key generation and signing through this surface and MUST
 * NOT call the crypto-core KMS's `KeyManagerService` directly. The KMS is an implementation
 * detail of the WSCD that sits BEHIND the WSCA/WSCD boundary: local OSS, mobile, and WebCrypto
 * profiles use their configured local cryptographic provider while remote profiles use backend WSCD
 * abstractions and commercial profiles route to the backend WSCA/WSCD/HSM path. Callers never see or
 * touch the KMS.
 *
 * The returned [WalletAttestedKeyRef] is the ONLY handle a caller receives. Its
 * [WalletAttestedKeyRef.keyRef] is an opaque, secure-component-scoped signing identifier that only
 * the WSCA/WSCD implementation knows how to resolve; callers treat it as a black box and hand it
 * back to [sign] (or, on the OSS local profile, forward it as the credential-request proof signing
 * key identifier). The mapping from that opaque reference to concrete KMS key material lives
 * entirely inside the implementation.
 */
interface Wsca {
    /**
     * The custody profile of the WSCD backing this WSCA (Software, LocalNative, Remote, ...).
     *
     * Exposed so capability-aware callers (e.g. the interaction-layer execution planner) can select
     * a user-authorization path (remote key-authorization gate, local HSM unlock, or no gate at all
     * for the software profile) without parsing string prefixes or otherwise reaching past this
     * facade toward the WSCD/KMS.
     */
    val wscdProfile: WscdProfile

    /**
     * The user-authentication ceremony surface (CIR (EU) 2024/2979 Art. 5(1)) this WSCA gates its
     * crypto operations on. See [WscaUserAuthentication] KDoc for what "authentication" concretely
     * means per implementation.
     *
     * Gating boundary: the ceremony gates key USE - every signing path ([sign], [createDpopProof],
     * and everything built on them, incl. [attestKeys]/[createClientAttestationAuth]). Key
     * PROVISIONING ([ensureKey]/[createCredentialKey]) is not gated today: a freshly provisioned
     * key is inert until its first ceremony-gated signature.
     */
    val userAuthentication: WscaUserAuthentication

    /**
     * Provisions (once) and returns a secure-component-held holder key.
     *
     * Idempotency is keyed by the effective key reference:
     * - When [keyAlias] is `null`, the WSCA/WSCD implementation derives a single, stable key per
     *   [walletUnitId], so repeated calls return a reference to the SAME secure-component-held
     *   key (same public key). This is the default the runner uses: one holder key per wallet
     *   instance.
     * - When [keyAlias] is non-null, the caller OWNS the key identity: repeated calls with the same
     *   [keyAlias] return the same key, while distinct aliases provision distinct keys. This lets a
     *   caller that manages its own key aliases (e.g. multiple named holder keys, or a fresh key per
     *   request) still route provisioning through the secure component rather than the KMS.
     *
     * @param walletUnitId the wallet unit the key is bound to.
     * @param usage what the key is provisioned for (e.g. [SecureComponentUsage.WALLET_CREDENTIAL_PROOF]).
     * @param algorithm the signature algorithm the key must support.
     * @param keyAlias optional caller-supplied key identity hint; when provided the unit honors it as
     *   the stable identity for the key (mapped to concrete key material internally). When `null` the
     *   implementation derives a deterministic per-[walletUnitId] identity.
     * @return a [WalletAttestedKeyRef] carrying the public JWK and an opaque, secure-component-scoped
     *   [WalletAttestedKeyRef.keyRef]; never the raw KMS internals.
     */
    suspend fun ensureKey(
        walletUnitId: String,
        usage: SecureComponentUsage,
        algorithm: SignatureAlgorithm,
        keyAlias: String? = null,
    ): IdkResult<WalletAttestedKeyRef, IdkError>

    /**
     * Mints a FRESH, secure-component-held key pair for binding to a single issued credential
     * instance.
     *
     * Unlike [ensureKey] (which is idempotent and returns a stable, reusable key), this method is
     * NON-idempotent: EVERY call generates a brand-new, unique key pair with a fresh,
     * secure-component-generated alias. Callers do not supply (and cannot influence) the key
     * identity, because the whole point of this primitive is that no two calls ever resolve to the
     * same key.
     *
     * This is the credential-binding key primitive: one call = one fresh holder key for one credential
     * instance. It exists because the ARF/OID4VCI unlinkability model (ARF v2.9.0 ISSU_12b) requires a
     * distinct, non-exportable holder key per issued credential, so that credentials from the same
     * wallet cannot be correlated by a shared holder key. `ensureKey` remains the right choice for
     * genuinely-stable keys (e.g. a wallet attestation key that must stay the same across calls);
     * `createCredentialKey` is for the opposite case, a key that must never repeat.
     *
     * Callers MUST retain the returned [WalletAttestedKeyRef] and bind it to the specific credential
     * instance being issued (e.g. by persisting it alongside the issued credential record), since the
     * unit does not otherwise track which credential a given fresh key belongs to.
     *
     * @param walletUnitId the wallet unit the key is bound to.
     * @param usage what the key is provisioned for (e.g. [SecureComponentUsage.WALLET_CREDENTIAL_PROOF]).
     * @param algorithm the signature algorithm the key must support.
     * @return a [WalletAttestedKeyRef] carrying the public JWK and an opaque, secure-component-scoped
     *   [WalletAttestedKeyRef.keyRef] for the newly minted key; never the raw KMS internals.
     */
    suspend fun createCredentialKey(
        walletUnitId: String,
        usage: SecureComponentUsage,
        algorithm: SignatureAlgorithm,
    ): IdkResult<WalletAttestedKeyRef, IdkError>

    /**
     * Produces a raw signature over caller-supplied [signingInput] using the secure-component-held key referenced
     * by [keyRef] (which must have been obtained from [ensureKey] for the same [walletUnitId]).
     *
     * The signing happens behind the WSCA/WSCD boundary; the private key never leaves the WSCD.
     *
     * @return the raw signature bytes.
     */
    suspend fun sign(
        walletUnitId: String,
        keyRef: WalletAttestedKeyRef,
        signingInput: ByteArray,
        operationBinding: String,
    ): IdkResult<ByteArray, IdkError>

    /**
     * Produces an RFC 9449 DPoP proof JWT using a secure-component-held key.
     */
    suspend fun createDpopProof(request: WscaDpopProofRequest): IdkResult<WscaDpopProofResult, IdkError>

    /**
     * Produces the OAuth attestation-based client-auth header pair used by HAIP/OID4VCI:
     * `OAuth-Client-Attestation` and `OAuth-Client-Attestation-PoP`.
     */
    suspend fun createClientAttestationAuth(request: WscaClientAttestationAuthRequest,): IdkResult<WscaClientAttestationAuthResult, IdkError>

    /**
     * Issues a key-attestation JWT for one or more secure-component-held keys.
     *
     * Holder code calls this surface instead of constructing attestations directly. The concrete
     * WSCD/WSCA implementation owns how the attestation signer is selected, how its private key is
     * protected, and how any x5c material is bound into the JOSE header.
     */
    suspend fun attestKeys(request: KeyAttestationIssueRequest): IdkResult<KeyAttestationIssueResult, IdkError>
}
